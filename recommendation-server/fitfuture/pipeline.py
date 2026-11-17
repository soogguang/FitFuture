import hashlib
import json
from collections import Counter
from datetime import date
from pathlib import Path

from .errors import RecommendationError
from .allergens import conflicts
from .schemas import Alternative, FoodPortion, RecommendResponse


class Rejected(Exception):
    pass


def contains(term, values):
    return any(term.casefold() in v.casefold() or v.casefold() in term.casefold() for v in values)


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
            if request.profile.avoided:
                if f.ingredients is None or not f.ingredient_source or not f.ingredient_valid_until \
                        or f.ingredient_valid_until < today:
                    raise Rejected("INGREDIENT_UNVERIFIABLE")
                if any(contains(a, [f.name, *f.ingredients]) for a in request.profile.avoided):
                    raise Rejected("AVOIDED")
        offers = [o for o in self.repo.catalog.offers
                  if o.environment == request.profile.environment and o.valid_until >= today
                  and candidate.kind in o.allowed_kinds
                  and all(k in o.max_grams and v <= o.max_grams[k] for k, v in after.items())]
        if request.profile.allergies:
            offers = [o for o in offers if o.allergens is not None and o.allergen_source
                      and conflicts(request.profile.allergies, o.allergens) is False]
        if not offers:
            raise Rejected("PRICE_ENVIRONMENT_ALLERGEN_UNVERIFIABLE")
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

    async def recommend(self, request):
        original = self.repo.portions(request.meal.foods)
        original_nutrition = self.repo.calculate(original)
        if original_nutrition.calories_kcal <= 0:
            raise RecommendationError("NUTRITION", "원래 식사의 영양정보를 확인해 주세요.")
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
                    name = self.repo.foods[k].name
                    actions.append(f"{name} {old:g}g → {new:g}g" if old and new
                                   else f"{name} {new:g}g 추가" if new else f"{name} 덜어두기")
                portions = [FoodPortion(name=f.name, grams=p.grams) for p, f in zip(candidate.portions, foods)]
                signature = "|".join(f"{k}:{v:.2f}" for k, v in sorted(after.items()))
                fixture = self.repo.fixture or self.generator.source.startswith("fixture")
                results.append(Alternative(
                    id=hashlib.sha256(signature.encode()).hexdigest()[:20], index=0,
                    # Derive titles/instructions from verified changes; never repeat unsupported AI claims.
                    title=" · ".join(f"{self.repo.foods[k].name} 양 조절" for k in sorted(before.keys() | after.keys())
                                     if abs(before.get(k, 0) - after.get(k, 0)) > 1),
                    instructions=" / ".join(actions), foods=portions, nutrition=n,
                    composition=" · ".join(f"{f.name} {f.grams:g} g" for f in portions),
                    calorie_difference=round(n.calories_kcal - original_nutrition.calories_kcal, 2),
                    score=score, scores=scores, weights=self.scorer.weights,
                    reason=f"{request.meal.name}, 이렇게도 드실 수 있어요! "
                           + " / ".join(actions) + ". 목표 열량과 취향, 예산, 실천의 간편함을 함께 살펴봤어요.",
                    price_won=offer.price_won,
                    verification=("검증용 예시 데이터 · 실제 영양·알레르기·가격 보장 아님" if fixture
                                  else f"식약처 영양 재계산 · 가격/환경 근거: {offer.source} · 유효일 {offer.valid_until}"
                                  + (" · 알레르기 조건 대조 완료 (안전 보장 아님)" if request.profile.allergies
                                     else " · 알레르기 조건 미입력")),
                    source=f"{self.generator.source} + {self.repo.source}"))
                accepted.append(after)
            except Rejected as exc:
                rejected[str(exc)] += 1
        if len(results) < 3:
            raise RecommendationError("SHORTAGE", "조건을 확인할 수 있는 대안이 3개보다 적어요. 음식과 조건을 확인해 주세요.",
                                      available=len(results), rejected=dict(rejected))
        results.sort(key=lambda a: (-a.score, a.id))
        top = [a.model_copy(update={"index": i}) for i, a in enumerate(results[:3])]
        return RecommendResponse(alternatives=top, original_nutrition=original_nutrition,
                                 generation_source=self.generator.source, nutrition_source=self.repo.source,
                                 rejected=dict(rejected))
