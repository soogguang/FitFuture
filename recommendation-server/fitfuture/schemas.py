from typing import Annotated, Literal

from pydantic import BaseModel, ConfigDict, Field, model_validator


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid", allow_inf_nan=False, str_strip_whitespace=True)


Name = Annotated[str, Field(min_length=1, max_length=120)]
Positive = Annotated[float, Field(gt=0, le=5000)]
Kind = Literal["portion", "sauce", "substitution", "balance", "menu"]


class Nutrition(StrictModel):
    calories_kcal: float = Field(ge=0, le=20000)
    carbohydrate_grams: float = Field(ge=0, le=5000)
    protein_grams: float = Field(ge=0, le=5000)
    fat_grams: float = Field(ge=0, le=5000)


class FoodPortion(StrictModel):
    name: Name
    grams: Positive


class Meal(StrictModel):
    name: Name
    confirmed: Literal[True]
    foods: list[FoodPortion] = Field(min_length=1, max_length=20)
    nutrition: Nutrition


class Profile(StrictModel):
    goal: Literal["체중 감량", "유지", "체중 유지", "근육 증가", "건강한 식습관", "체중 증량"] = "체중 감량"
    gender: Literal["여성", "남성"] = "여성"
    age: int = Field(ge=18, le=100)
    height_cm: float = Field(ge=100, le=250)
    weight_kg: float = Field(ge=30, le=300)
    target_weight_kg: float = Field(ge=30, le=300)
    activity: Name
    preferences: list[Name] = Field(default_factory=list, max_length=40)
    avoided: list[Name] = Field(default_factory=list, max_length=40)
    allergies: list[Name] = Field(default_factory=list, max_length=40)
    budget_won: int = Field(gt=0, le=1000000)
    environment: Name
    target_meal_kcal: float | None = Field(default=None, ge=300, le=1800)


class Feedback(StrictModel):
    planned_id: str = Field(default="", max_length=120)
    actual_id: str = Field(default="", max_length=120)
    actual_foods: list[FoodPortion] = Field(default_factory=list, max_length=20)
    satisfaction: int = Field(ge=1, le=5)
    note: str = Field(default="", max_length=500)


class RecommendRequest(StrictModel):
    meal: Meal
    profile: Profile
    feedback: list[Feedback] = Field(default_factory=list, max_length=100)


class Portion(StrictModel):
    food_id: Name
    grams: Positive


class Candidate(StrictModel):
    # Only a proposal. No nutrition, prices, allergy claims, or free-form instructions accepted.
    title: str = Field(min_length=1, max_length=80)
    kind: Kind
    portions: list[Portion] = Field(min_length=1, max_length=20)

    @model_validator(mode="after")
    def unique_foods(self):
        if len({p.food_id for p in self.portions}) != len(self.portions):
            raise ValueError("duplicate food IDs")
        return self


class CandidateBatch(StrictModel):
    # Gemini may find fewer distinct feasible changes (e.g. a 3-food meal with no verified offers).
    # Accept what it returns; the pipeline verifies each one and reports SHORTAGE when none survive.
    candidates: list[Candidate] = Field(max_length=10)


class Alternative(StrictModel):
    id: str
    index: int
    title: str
    instructions: str
    foods: list[FoodPortion]
    nutrition: Nutrition
    composition: str
    calorie_difference: float
    score: float
    scores: dict[str, float]
    weights: dict[str, float]
    reason: str
    price_won: int
    verification: str
    source: str


class RecommendResponse(StrictModel):
    alternatives: list[Alternative] = Field(min_length=1, max_length=3)
    original_nutrition: Nutrition
    generation_source: str
    nutrition_source: str
    rejected: dict[str, int]
