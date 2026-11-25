import hashlib
import json
from collections import Counter
from dataclasses import dataclass
from datetime import date
from pathlib import Path

from .errors import RecommendationError
from .allergens import conflicts
from .schemas import Alternative, FoodPortion, RecommendResponse


class Rejected(Exception):
    pass


@dataclass(frozen=True)
class OwnMealOffer:
    """Price basis for eating less of the meal the user already has: nothing new is bought.

    Used only for reduction-only candidates when no verified offer exists for the environment.
    price_won is a reference price of the original meal (0 when unknown); it is never a quote.
    """
    price_won: int
    source: str
    valid_until: date | None
    additional_purchase: bool = False
    cooking_required: bool = False


def contains(term, values):
    return any(term.casefold() in v.casefold() or v.casefold() in term.casefold() for v in values)


# A cooking-style preference is not an ingredient exclusion. In particular,
# "기름" must not exclude every reduced-fat alternative to a fried meal.
def avoided_ingredients(profile):
    return [term for term in profile.avoided if term != "기름진 음식"]


class RecommendationPipeline:
    def __init__(self, repo, generator, scorer, safety=None):
        self.repo, self.generator, self.scorer = repo, generator, scorer
        self.safety = safety or json.loads(Path(__file__).with_name("scoring.json").read_text())["safety"]

    def validate(self, request, candidate, original, original_nutrition):
        before = {p.food_id: p.grams for p in original}
        after = {p.food_id: p.grams for p in candidate.portions}
        if set(after) - self.repo.foods.keys():
            raise Rejected("UNKNOWN_FOOD")
        changes = {k for k in before.keys() | after.keys() if abs(before.get(k, 0) - after.get(k, 0)) > 1}
        if not changes:
            raise Rejected("UNCHANGED")
        increases = {k for k in changes if after.get(k, 0) > before.get(k, 0)}
        removed = before.keys() - after.keys()
        added = after.keys() - before.keys()
        if candidate.kind in ("portion", "sauce") and increases:
            raise Rejected("IMPOSSIBLE_OPERATION")
        if candidate.kind == "sauce" and any(self.repo.foods[k].role != "sauce" for k in changes):
            raise Rejected("ABSENT_SAUCE")
        if candidate.kind == "substitution" and not (len(added) == 1 and len(removed) == 1):
            raise Rejected("IMPOSSIBLE_OPERATION")
        if candidate.kind == "balance" and not increases:
            raise Rejected("IMPOSSIBLE_OPERATION")
        foods = [self.repo.foods[p.food_id] for p in candidate.portions]
        # portion/sauce candidates passed the no-increase check above, so they only reduce or drop
        # foods from the original meal.
        reduction_only = candidate.kind in ("portion", "sauce")
        excluded_ingredients = avoided_ingredients(request.profile)
        today = date.today()
        for f in foods:
            if request.profile.allergies:
                if f.allergens is None or not f.allergen_source or not f.allergen_valid_until \
                        or f.allergen_valid_until < today:
                    raise Rejected("ALLERGEN_UNVERIFIABLE")
                conflict = conflicts(request.profile.allergies, f.allergens)
                if conflict is None:
                    raise Rejected("ALLERGEN_UNVERIFIABLE")
                if conflict:
                    raise Rejected("ALLERGY")
            if excluded_ingredients:
                verified = f.ingredients is not None and f.ingredient_source and f.ingredient_valid_until \
                    and f.ingredient_valid_until >= today
                # Reducing the user's own meal adds nothing they chose to avoid; unknown recipes
                # only block candidates that would introduce or keep other foods.
                if not verified and not reduction_only:
                    raise Rejected("INGREDIENT_UNVERIFIABLE")
                if any(contains(a, [f.name, *(f.ingredients if verified else [])]) for a in excluded_ingredients):
                    raise Rejected("AVOIDED")
        offers = [o for o in self.repo.catalog.offers
                  if o.environment == request.profile.environment and o.valid_until >= today
                  and candidate.kind in o.allowed_kinds
                  and all(k in o.max_grams and v <= o.max_grams[k] for k, v in after.items())]
        if request.profile.allergies:
            offers = [o for o in offers if o.allergens is not None and o.allergen_source
                      and conflicts(request.profile.allergies, o.allergens) is False]
        if not offers:
            if not reduction_only or self.environment_has_offers(request.profile.environment, today):
                raise Rejected("PRICE_ENVIRONMENT_ALLERGEN_UNVERIFIABLE")
            offers = [self.own_meal_offer(request, before)]
        offer = min(offers, key=lambda o: o.price_won)
        if offer.price_won > request.profile.budget_won:
            raise Rejected("BUDGET")
        nutrition = self.repo.calculate(candidate.portions)
        safety = self.safety
        if not safety["min_kcal"] <= nutrition.calories_kcal <= safety["max_kcal"]:
            raise Rejected("EXCESSIVE_RESTRICTION")
        if candidate.kind != "menu" and nutrition.calories_kcal < original_nutrition.calories_kcal * safety["min_original_ratio"]:
            raise Rejected("EXCESSIVE_RESTRICTION")
        return nutrition, foods, offer

    def environment_has_offers(self, environment, today):
        # A verified menu price for this environment must not be bypassed by the own-meal basis.
        return any(o.environment == environment and o.valid_until >= today for o in self.repo.catalog.offers)

    def own_meal_offer(self, request, before):
        prices = getattr(self.repo, "reference_prices", {})
        if request.profile.environment == "외식":
            known = [prices[k] for k in before if k in prices and prices[k].valid_until >= date.today()]
            if known:
                main = max(known, key=lambda p: p.price_won)
                return OwnMealOffer(main.price_won, main.source + " · 원래 식사 양 조절", main.valid_until)
        return OwnMealOffer(0, "원래 식사 양 조절 (추가 구매 없음 · 가격 근거 없음)", None)

    def display(self, food_id):
        return self.repo.foods[food_id].name.replace("_", " ")

    async def recommend(self, request):
        original = self.repo.portions(request.meal.foods)
        original_nutrition = self.repo.calculate(original)
        if original_nutrition.calories_kcal <= 0:
            raise RecommendationError("NUTRITION", "원래 식사의 영양정보를 확인해 주세요.")
        request = request.model_copy(update={"meal": request.meal.model_copy(
            update={"nutrition": original_nutrition})})
        batch = await self.generator.generate(request, self.repo)
        rejected, results, accepted = Counter(), [], []
        before = {p.food_id: p.grams for p in original}
        for candidate in batch.candidates:
            try:
                n, foods, offer = self.validate(request, candidate, original, original_nutrition)
                after = {p.food_id: p.grams for p in candidate.portions}
                # Relative L1 distance rejects near-identical portions (<5% total mass difference).
                if any(sum(abs(after.get(k, 0) - old.get(k, 0)) for k in after.keys() | old.keys())
                       / max(sum(after.values()), sum(old.values())) < .05 for old in accepted):
                    raise Rejected("DUPLICATE")
                score, scores = self.scorer.score(request, original, candidate, n, foods, offer)
                actions = []
                for k in sorted(before.keys() | after.keys()):
                    old, new = before.get(k, 0), after.get(k, 0)
                    if abs(old - new) <= 1:
                        continue
                    name = self.display(k)
                    actions.append(f"{name} {old:g}g → {new:g}g" if old and new
                                   else f"{name} {new:g}g 추가" if new else f"{name} 덜어두기")
                portions = [FoodPortion(name=f.name, grams=p.grams) for p, f in zip(candidate.portions, foods)]
                signature = "|".join(f"{k}:{v:.2f}" for k, v in sorted(after.items()))
                fixture = self.repo.fixture or self.generator.source.startswith("fixture")
                difference = round(n.calories_kcal - original_nutrition.calories_kcal, 2)
                energy_reason = (f"원래 식사보다 {abs(difference):g}kcal "
                                 + ("줄인" if difference < 0 else "늘린") + " 구성이에요. "
                                 if difference else "원래 식사와 열량이 같은 구성 변경이에요. ")
                feasibility = ("추가 구매와 조리 없이 실천할 수 있는 조건이에요. "
                               if not offer.additional_purchase and not offer.cooking_required else "")
                results.append(Alternative(
                    id=hashlib.sha256(signature.encode()).hexdigest()[:20], index=0,
                    # Derive titles/instructions from verified changes; never repeat unsupported AI claims.
                    title=" · ".join(f"{self.display(k)} 양 조절" for k in sorted(before.keys() | after.keys())
                                     if abs(before.get(k, 0) - after.get(k, 0)) > 1),
                    instructions=" / ".join(actions), foods=portions, nutrition=n,
                    composition=" · ".join(f"{f.name.replace('_', ' ')} {f.grams:g} g" for f in portions),
                    calorie_difference=difference,
                    score=score, scores=scores, weights=self.scorer.weights,
                    reason=f"{request.meal.name}, 이렇게도 드실 수 있어요! "
                           + " / ".join(actions) + ". " + energy_reason
                           + (f"한 끼 {offer.price_won:,}원으로 예산 안에 들어요. " if offer.price_won
                              else "원래 식사에서 양만 줄여 추가 비용이 들지 않아요. ") + feasibility
                           + "목표 열량·영양 균형, 음식 선호와 과거 피드백, 가격·접근성, 실천 가능성을 점수에 반영했어요.",
                    price_won=offer.price_won,
                    verification=("검증용 예시 데이터 · 실제 영양·알레르기·가격 보장 아님" if fixture
                                  else f"식약처 영양 재계산 · 가격/환경 근거: {offer.source}"
                                  + (f" · 유효일 {offer.valid_until}" if offer.valid_until else "")
                                  + (" · 알레르기 근거: " + " / ".join(dict.fromkeys(
                                         f.allergen_source.split(" · ")[0] for f in foods))
                                     + " 대조 완료 (안전 보장 아님)" if request.profile.allergies
                                     else " · 알레르기 조건 미입력")),
                    source=f"{self.generator.source} + {self.repo.source}"))
                accepted.append(after)
            except Rejected as exc:
                rejected[str(exc)] += 1
        if not results:
            reasons = {
                "AVOIDED": "피하고 싶은 재료가 포함된 후보를 제외했어요.",
                "ALLERGY": "알레르기 성분이 포함된 후보를 제외했어요.",
                "ALLERGEN_UNVERIFIABLE": "알레르기 성분을 확인할 근거가 부족해요.",
                "INGREDIENT_UNVERIFIABLE": "기피 재료를 확인할 근거가 부족해요.",
                "BUDGET": "한 끼 예산을 초과한 후보를 제외했어요.",
                "PRICE_ENVIRONMENT_ALLERGEN_UNVERIFIABLE": "선택한 식사 환경의 가격·제공 조건을 확인할 근거가 부족해요.",
                "EXCESSIVE_RESTRICTION": "열량을 지나치게 줄이는 후보를 제외했어요.",
            }
            explanation = " ".join(reasons[code] for code in rejected if code in reasons)
            raise RecommendationError("SHORTAGE", "조건에 맞는 대안을 찾지 못했어요. "
                                      + (explanation or "음식과 조건을 확인해 주세요."),
                                      available=len(results), rejected=dict(rejected))
        results.sort(key=lambda a: (-a.score, a.id))
        top = [a.model_copy(update={"index": i}) for i, a in enumerate(results[:3])]
        return RecommendResponse(alternatives=top, original_nutrition=original_nutrition,
                                 generation_source=self.generator.source, nutrition_source=self.repo.source,
                                 rejected=dict(rejected))
