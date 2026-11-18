"""Replaceable deterministic scorer. This module does not train a model or simulate weight."""
import json
from pathlib import Path
from typing import Protocol

from .schemas import Profile


def clamp(value):
    return max(0.0, min(100.0, value))


def target_meal(profile: Profile):
    if profile.target_meal_kcal:
        return profile.target_meal_kcal
    bmr = 10 * profile.weight_kg + 6.25 * profile.height_cm - 5 * profile.age
    bmr += 5 if profile.gender == "남성" else -161
    factor = 1.725 if "많" in profile.activity else 1.55 if "보통" in profile.activity else 1.375
    if "앉" in profile.activity:
        factor = 1.2
    adjustment = -300 if profile.goal == "체중 감량" else 200 if profile.goal in ("근육 증가", "체중 증량") else 0
    return max(300, min(1800, max(bmr, bmr * factor + adjustment) / 3))


class Scorer(Protocol):
    weights: dict[str, float]

    def score(self, request, original, candidate, nutrition, foods, offer): ...


class RuleScorer:
    def __init__(self, path: Path):
        self.config = json.loads(path.read_text())
        self.weights = self.config["weights"]
        if set(self.weights) != {"N", "P", "C", "F"} or any(v < 0 for v in self.weights.values()) \
                or abs(sum(self.weights.values()) - 1) > 1e-6:
            raise ValueError("Scoring weights must be N/P/C/F, nonnegative and sum to 1")

    def score(self, request, original, candidate, nutrition, foods, offer):
        profile = request.profile
        target = target_meal(profile)
        energy = clamp(100 * (1 - abs(nutrition.calories_kcal - target) / target))
        macro_energy = (nutrition.carbohydrate_grams * 4 + nutrition.protein_grams * 4
                        + nutrition.fat_grams * 9)
        macro_targets = [self.config["macro_targets"][k] for k in ("carbohydrate", "protein", "fat")]
        fractions = [nutrition.carbohydrate_grams * 4, nutrition.protein_grams * 4, nutrition.fat_grams * 9]
        balance = clamp(100 * (1 - sum(abs(v / max(1, macro_energy) - t)
                                      for v, t in zip(fractions, macro_targets)) / 2))
        n = .65 * energy + .35 * balance
        before = {p.food_id: p.grams for p in original}
        after = {p.food_id: p.grams for p in candidate.portions}
        retention = sum(min(after.get(k, 0), v) for k, v in before.items()) / sum(before.values())
        names = [f.name for f in foods]
        # Preference mass is bounded; keeping more of a preferred food increases P.
        preferred_ratio = sum(portion.grams for portion, food in zip(candidate.portions, foods)
                              if any(pref in food.name for pref in profile.preferences)) / sum(after.values())
        p = 40 + 25 * retention + (25 * preferred_ratio if profile.preferences else 12.5)
        # Avoided candidates were rejected; +10 reflects passing this constraint.
        p += 10
        if "기름진 음식" in profile.avoided:
            # Prefer an actual reduction in total fat, calculated from the DB.
            # Do not classify a meal as oily from a substring of its ingredients.
            original_fat = request.meal.nutrition.fat_grams  # Recalculated by the pipeline.
            fat_reduction = max(0, min(1, 1 - nutrition.fat_grams / original_fat)) if original_fat > 0 else 0
            p -= 25 * (1 - fat_reduction)
        history = []
        for feedback in request.feedback:
            actual = {f.name for f in feedback.actual_foods}
            union = actual | set(names)
            similarity = len(actual & set(names)) / len(union) if union else 0
            if similarity:
                history.append((feedback.satisfaction - 3) * 5 * similarity)
        if history:
            p += sum(history) / len(history)
        c = .6 * clamp(100 * (1 - .5 * offer.price_won / profile.budget_won)) + .4 * 100
        changed = sum(abs(after.get(k, 0) - before.get(k, 0)) > 1 for k in before.keys() | after.keys())
        f = clamp(100 - 25 * offer.additional_purchase - 30 * offer.cooking_required - 5 * max(0, changed - 1))
        scores = {k: round(clamp(v), 2) for k, v in zip("NPCF", (n, p, c, f))}
        return round(sum(scores[k] * self.weights[k] for k in scores), 2), scores
