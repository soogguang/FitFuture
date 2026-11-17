"""Exact reviewed matches only; an official nutrient row is NOT an allergen/price source."""
import json
from datetime import date
from pathlib import Path

from pydantic import Field, model_validator

from .errors import RecommendationError
from .schemas import FoodPortion, Kind, Name, Nutrition, Portion, Positive, StrictModel


class Food(StrictModel):
    id: Name
    name: Name
    aliases: list[Name] = Field(default_factory=list)
    basis_grams: Positive
    nutrition: Nutrition
    source: Name
    source_record_id: Name
    role: str = "food"
    # None means unknown, [] means independently verified empty list.
    allergens: list[Name] | None = None
    allergen_source: str | None = None
    allergen_valid_until: date | None = None
    ingredients: list[Name] | None = None
    ingredient_source: str | None = None
    ingredient_valid_until: date | None = None


class Offer(StrictModel):
    id: Name
    environment: Name
    max_grams: dict[str, Positive]
    price_won: int = Field(gt=0)
    source: Name
    valid_until: date
    allowed_kinds: list[Kind]
    additional_purchase: bool
    cooking_required: bool
    # These apply to the actual served recipe, including cross-contact information.
    allergen_source: str | None = None
    allergens: list[Name] | None = None


class Catalog(StrictModel):
    provenance: str
    foods: list[Food]
    offers: list[Offer] = Field(default_factory=list)

    @model_validator(mode="after")
    def unique(self):
        ids = [f.id for f in self.foods]
        if len(set(ids)) != len(ids):
            raise ValueError("duplicate food IDs")
        if any(set(o.max_grams) - set(ids) for o in self.offers):
            raise ValueError("offer references unknown food")
        return self


class NutritionRepository:
    def __init__(self, path: Path, fixture: bool = False):
        try:
            self.catalog = Catalog.model_validate_json(path.read_text())
        except (OSError, ValueError) as exc:
            raise RecommendationError("CONFIGURATION", "영양 DB 파일을 확인해 주세요.", 503) from exc
        self.fixture = fixture
        if not fixture and (self.catalog.provenance != "mfds"
                            or any(f.source != "mfds" for f in self.catalog.foods)):
            raise RecommendationError("CONFIGURATION", "실제 모드에는 식약처 출처 데이터가 필요해요.", 503)
        self.foods = {f.id: f for f in self.catalog.foods}

    @property
    def source(self):
        return "fixture (NOT official MFDS data)" if self.fixture else "mfds reviewed snapshot"

    def resolve(self, name: str) -> Food:
        matched = [f for f in self.foods.values() if name in [f.name, *f.aliases]]
        if len(matched) != 1:
            raise RecommendationError("NUTRITION", f"'{name}'의 영양정보를 정확하게 매칭하지 못했어요.")
        return matched[0]

    def portions(self, foods: list[FoodPortion]) -> list[Portion]:
        combined = {}
        for item in foods:
            food = self.resolve(item.name)
            combined[food.id] = combined.get(food.id, 0) + item.grams
        return [Portion(food_id=k, grams=v) for k, v in combined.items()]

    def calculate(self, portions: list[Portion]) -> Nutrition:
        total = dict.fromkeys(Nutrition.model_fields, 0.0)
        for p in portions:
            f = self.foods.get(p.food_id)
            if f is None:
                raise RecommendationError("NUTRITION", "DB에 없는 식품이에요.")
            for key, value in f.nutrition.model_dump().items():
                total[key] += value * p.grams / f.basis_grams
        return Nutrition(**{k: round(v, 4) for k, v in total.items()})

    def public_foods(self):
        return [{"food_id": f.id, "name": f.name, "role": f.role} for f in self.foods.values()]
