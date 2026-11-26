"""Exact reviewed matches only; an official nutrient row is NOT an allergen/price source.

Allergen and price evidence come from separate public datasets (evidence/*.json) and attach to
an MFDS food only when the dataset's dish name matches it exactly (see foodnames.py).
"""
import json
from dataclasses import dataclass
from datetime import date, timedelta
from pathlib import Path

from pydantic import Field, model_validator

from .errors import RecommendationError
from .foodnames import ALIASES_TSV, MFDS_TSV, NameIndex, load_aliases, load_mfds
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


EVIDENCE_DIR = Path(__file__).resolve().parents[1] / "evidence"
MFDS_SOURCE = "식약처 음식 DB (2026-08-28)"


@dataclass(frozen=True)
class ReferencePrice:
    price_won: int
    count: int
    source: str
    valid_until: date


class NutritionRepository:
    def __init__(self, path: Path | None, fixture: bool = False, catalog: Catalog | None = None,
                 index: NameIndex | None = None):
        try:
            self.catalog = catalog or Catalog.model_validate_json(path.read_text())
        except (OSError, ValueError) as exc:
            raise RecommendationError("CONFIGURATION", "영양 DB 파일을 확인해 주세요.", 503) from exc
        self.fixture = fixture
        self.index = index
        self.reference_prices: dict[str, ReferencePrice] = {}
        self.evidence_summary: dict[str, int] = {}
        if not fixture and (self.catalog.provenance != "mfds"
                            or any(f.source != "mfds" for f in self.catalog.foods)):
            raise RecommendationError("CONFIGURATION", "실제 모드에는 식약처 출처 데이터가 필요해요.", 503)
        self.foods = {f.id: f for f in self.catalog.foods}

    @classmethod
    def from_mfds(cls, tsv: Path = MFDS_TSV, aliases: Path = ALIASES_TSV,
                  allergens: Path = EVIDENCE_DIR / "neis-allergens.json",
                  prices: Path = EVIDENCE_DIR / "seoul-prices.json",
                  min_allergen_count: int = 3, min_price_count: int = 2):
        """The app's MFDS food DB plus optional allergen/price evidence files."""
        try:
            index = NameIndex(load_mfds(tsv), load_aliases(aliases))
        except (OSError, ValueError) as exc:
            raise RecommendationError("CONFIGURATION", "식약처 음식 DB 파일을 확인해 주세요.", 503) from exc
        foods = [Food(id=row[0], name=row[1], basis_grams=100,
                      nutrition=Nutrition(calories_kcal=row[3], carbohydrate_grams=row[4],
                                          protein_grams=row[5], fat_grams=row[6]),
                      source="mfds", source_record_id=row[0])
                 for row in index.by_key.values()]
        repo = cls(None, catalog=Catalog(provenance="mfds", foods=foods), index=index)
        repo._attach_allergens(allergens, min_allergen_count)
        repo._attach_prices(prices, min_price_count)
        return repo

    @staticmethod
    def _evidence(path: Path):
        if not path.exists():
            return None
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
            return data, date.fromisoformat(data["collected"])
        except (OSError, ValueError, KeyError) as exc:
            raise RecommendationError("CONFIGURATION", f"근거 데이터 파일을 확인해 주세요: {path.name}", 503) from exc

    def _attach_allergens(self, path: Path, min_count: int):
        loaded = self._evidence(path)
        if loaded is None:
            return
        data, collected = loaded
        numbers = data["numbers"]
        matched: dict[str, dict] = {}
        for dish, entry in data["dishes"].items():
            row = self.index.lookup(dish)
            if row is None:
                continue
            m = matched.setdefault(row[0], {"count": 0, "labels": set(), "dishes": []})
            m["count"] += entry["count"]
            m["labels"].update(numbers[n] for n in entry["allergens"])
            m["dishes"].append(dish)
        for code, m in matched.items():
            # A dish must be labelled often enough that an omitted label is unlikely.
            if m["count"] < min_count:
                continue
            food = self.foods[code]
            food.allergens = sorted(m["labels"])
            food.allergen_source = (f"NEIS 학교급식 알레르기 표시 {m['count']}건 합집합 "
                                    f"({', '.join(sorted(m['dishes'])[:3])}) · 수집 {collected} · "
                                    "학교 조리법 기준이라 식당 조리법과 다를 수 있음")
            food.allergen_valid_until = collected + timedelta(days=365)
        self.evidence_summary["allergen_foods"] = sum(m["count"] >= min_count for m in matched.values())

    def _attach_prices(self, path: Path, min_count: int):
        loaded = self._evidence(path)
        if loaded is None:
            return
        data, collected = loaded
        for dish, entry in data["dishes"].items():
            row = self.index.lookup(dish)
            if row is None or entry["count"] < min_count:
                continue
            current = self.reference_prices.get(row[0])
            if current is None or entry["count"] > current.count:
                self.reference_prices[row[0]] = ReferencePrice(
                    entry["median_won"], entry["count"],
                    f"서울시 가격안정 모범업소 '{dish}' {entry['count']}곳 중앙값 (수집 {collected})",
                    collected + timedelta(days=180))
        self.evidence_summary["priced_foods"] = len(self.reference_prices)

    @property
    def source(self):
        if self.fixture:
            return "fixture (NOT official MFDS data)"
        return MFDS_SOURCE if self.index else "mfds reviewed snapshot"

    def resolve(self, name: str) -> Food:
        if self.index is not None:
            row = self.index.lookup(name)
            if row is None:
                raise RecommendationError("NUTRITION", f"'{name}'의 영양정보를 정확하게 매칭하지 못했어요.")
            return self.foods[row[0]]
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

    def public_foods(self, ids=None):
        foods = self.foods.values() if ids is None else [self.foods[i] for i in ids if i in self.foods]
        return [{"food_id": f.id, "name": f.name, "role": f.role} for f in foods]
