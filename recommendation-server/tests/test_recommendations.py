import asyncio
import copy
import json
from datetime import date, timedelta
from pathlib import Path

import httpx
import pytest
from fastapi.testclient import TestClient
from pydantic import ValidationError

from fitfuture.app import ROOT, build_pipeline, create_app
from fitfuture.errors import RecommendationError
from fitfuture.generator import FixtureGenerator, GeminiGenerator
from fitfuture.import_mfds import convert, normalize
from fitfuture.nutrition import NutritionRepository
from fitfuture.pipeline import RecommendationPipeline
from fitfuture.schemas import CandidateBatch, Feedback, RecommendRequest
from fitfuture.scoring import RuleScorer


@pytest.fixture
def request_data():
    return json.loads((ROOT / "examples/request.json").read_text())


@pytest.fixture
def pipeline():
    return RecommendationPipeline(NutritionRepository(ROOT / "tests/fixtures/catalog.json", True),
                                  FixtureGenerator(ROOT / "tests/fixtures/candidates.json"),
                                  RuleScorer(ROOT / "fitfuture/scoring.json"))


def recommend(pipeline, data):
    return asyncio.run(pipeline.recommend(RecommendRequest.model_validate(data)))


def test_cutlet_three_distinct_recalculated(pipeline, request_data):
    request_data["meal"]["nutrition"]["calories_kcal"] = 12345  # never trust client totals
    r = recommend(pipeline, request_data)
    assert len(r.alternatives) == len({a.id for a in r.alternatives}) == 3
    assert len({a.nutrition.calories_kcal for a in r.alternatives}) == 3
    assert r.original_nutrition.calories_kcal == 850
    assert [a.index for a in r.alternatives] == [0, 1, 2]
    assert r.generation_source.startswith("fixture")
    for a in r.alternatives:
        assert a.score == round(sum(a.scores[k] * a.weights[k] for k in a.scores), 2)
        assert a.calorie_difference == a.nutrition.calories_kcal - 850
        assert "NOT" in a.source and "예시" in a.verification
    assert r.rejected["ABSENT_SAUCE"] == 1
    assert r.rejected["ALLERGY"] == 1


def test_goal_changes_rank(pipeline, request_data):
    before = recommend(pipeline, request_data)
    request_data["profile"]["goal"] = "근육 증가"
    after = recommend(pipeline, request_data)
    assert [a.id for a in before.alternatives] != [a.id for a in after.alternatives]


def test_preference_changes_rank(pipeline, request_data):
    before = recommend(pipeline, request_data)
    request_data["profile"]["preferences"] = ["밥"]
    assert [a.id for a in before.alternatives] != [a.id for a in recommend(pipeline, request_data).alternatives]


def test_oily_style_prefers_reduced_fat_without_excluding_oil(pipeline, request_data):
    # The actual saved Android profile used to reject all six candidates as AVOIDED.
    request_data["profile"].update(goal="유지", age=22, height_cm=165, weight_kg=50,
                                   target_weight_kg=50, activity="보통 활동", budget_won=11200,
                                   preferences=["비빔밥", "샐러드"], avoided=["기름진 음식"], allergies=["견과류"])
    request_data["meal"]["nutrition"]["fat_grams"] = 0  # Ignore client nutrition for scoring.
    result = recommend(pipeline, request_data)
    assert len(result.alternatives) == 3
    assert "AVOIDED" not in result.rejected
    assert result.alternatives[0].nutrition.fat_grams < result.original_nutrition.fat_grams
    request_data["meal"]["nutrition"]["fat_grams"] = 31
    assert result == recommend(pipeline, request_data)
    request_data["profile"]["avoided"].append("기름")  # Explicit ingredient exclusion still applies.
    with pytest.raises(RecommendationError) as error:
        recommend(pipeline, request_data)
    assert error.value.details["rejected"]["AVOIDED"] > 0


@pytest.mark.parametrize("query,label", [("유제품", "우유"), ("견과류", "땅콩"), ("갑각류", "새우")])
def test_android_allergen_groups(pipeline, request_data, query, label):
    request_data["profile"]["allergies"] = [query]
    pipeline.repo.foods["cutlet"].allergens = [label]
    with pytest.raises(RecommendationError) as e:
        recommend(pipeline, request_data)
    assert e.value.code == "SHORTAGE" and e.value.details["rejected"]["ALLERGY"] > 0


def test_unknown_allergy_is_unverifiable(pipeline, request_data):
    request_data["profile"]["allergies"] = ["분류되지 않은 알레르기"]
    with pytest.raises(RecommendationError) as e:
        recommend(pipeline, request_data)
    assert e.value.code == "SHORTAGE" and e.value.details["rejected"]["ALLERGEN_UNVERIFIABLE"] > 0


def test_feedback_adjusts_preferences(pipeline, request_data):
    before = recommend(pipeline, request_data)
    request_data["feedback"] = [{"satisfaction": 5, "actual_foods": request_data["meal"]["foods"]}]
    after = recommend(pipeline, request_data)
    assert after.alternatives[0].scores["P"] > before.alternatives[0].scores["P"]


@pytest.mark.parametrize("field,value,reason", [
    ("allergies", ["밀"], "ALLERGY"), ("avoided", ["돼지고기"], "AVOIDED"),
    ("budget_won", 5000, "BUDGET"), ("environment", "편의점", "PRICE_ENVIRONMENT_ALLERGEN_UNVERIFIABLE")])
def test_constraints_shortage(pipeline, request_data, field, value, reason):
    request_data["profile"][field] = value
    with pytest.raises(RecommendationError) as error:
        recommend(pipeline, request_data)
    assert error.value.code == "SHORTAGE"
    assert error.value.details["rejected"][reason] > 0


@pytest.mark.parametrize("mutation", ["unknown_allergen", "missing_allergen_evidence", "expired_allergen", "unknown_ingredient", "expired_price", "missing_offer", "missing_offer_allergens"])
def test_unverifiable_not_assumed_safe(pipeline, request_data, mutation):
    food = pipeline.repo.foods["cutlet"]
    if mutation == "unknown_allergen": food.allergens = None
    if mutation == "missing_allergen_evidence": food.allergen_source = None
    if mutation == "expired_allergen": food.allergen_valid_until = date.today() - timedelta(days=1)
    if mutation == "unknown_ingredient": food.ingredients = None
    if mutation == "expired_price": pipeline.repo.catalog.offers[0].valid_until = date.today() - timedelta(days=1)
    if mutation == "missing_offer": pipeline.repo.catalog.offers.clear()
    if mutation == "missing_offer_allergens": pipeline.repo.catalog.offers[0].allergens = None
    with pytest.raises(RecommendationError, match="찾지 못했어요") as error:
        recommend(pipeline, request_data)
    assert error.value.code == "SHORTAGE"


def test_unknown_original_no_fabrication(pipeline, request_data):
    request_data["meal"]["foods"][0]["name"] = "알 수 없는 음식"
    with pytest.raises(RecommendationError) as error:
        recommend(pipeline, request_data)
    assert error.value.code == "NUTRITION"


def replace_candidates(pipeline, candidates):
    class Generator:
        source = "fixture"
        async def generate(self, request, repo):
            return CandidateBatch.model_validate({"candidates": candidates})
    pipeline.generator = Generator()


def test_duplicate_leaves_one_valid_alternative(pipeline, request_data):
    rows = json.loads((ROOT / "tests/fixtures/candidates.json").read_text())["candidates"]
    replace_candidates(pipeline, [rows[0]] * 5)
    result = recommend(pipeline, request_data)
    assert len(result.alternatives) == 1
    assert result.rejected["DUPLICATE"] == 4


@pytest.mark.parametrize("count", [1, 2, 3])
def test_api_returns_all_available_up_to_three(pipeline, request_data, count):
    rows = json.loads((ROOT / "tests/fixtures/candidates.json").read_text())["candidates"][:count]
    replace_candidates(pipeline, [rows[i % count] for i in range(6)])
    with TestClient(create_app(pipeline)) as client:
        response = client.post("/recommend", json=request_data)
    assert response.status_code == 200
    alternatives = response.json()["alternatives"]
    assert len(alternatives) == count
    assert [a["index"] for a in alternatives] == list(range(count))
    assert [a["score"] for a in alternatives] == sorted((a["score"] for a in alternatives), reverse=True)


def test_actual_edited_portions_return_two_instead_of_shortage(pipeline, request_data):
    for food, grams in zip(request_data["meal"]["foods"], [220, 260, 30]):
        food["grams"] = grams
    request_data["profile"].update(goal="유지", preferences=["비빔밥", "샐러드"],
                                   avoided=["기름진 음식"], allergies=["견과류"], budget_won=11200)
    result = recommend(pipeline, request_data)
    assert len(result.alternatives) == 2
    assert result.rejected["IMPOSSIBLE_OPERATION"] == 5
    assert result.original_nutrition.calories_kcal == 1110


def test_unknown_candidate_and_excessive_restriction(pipeline, request_data):
    rows = json.loads((ROOT / "tests/fixtures/candidates.json").read_text())["candidates"]
    rows[3]["portions"][0]["food_id"] = "invented"
    rows[4]["portions"] = [{"food_id": "rice", "grams": 2}]
    replace_candidates(pipeline, rows)
    r = recommend(pipeline, request_data)
    assert r.rejected["UNKNOWN_FOOD"] == r.rejected["EXCESSIVE_RESTRICTION"] == 1


@pytest.mark.parametrize("grams", [0, -10, float("nan"), float("inf")])
def test_invalid_portions(request_data, grams):
    request_data["meal"]["foods"][0]["grams"] = grams
    with pytest.raises(ValidationError): RecommendRequest.model_validate(request_data)


def test_real_mode_refuses_fixture():
    with pytest.raises(RecommendationError) as e:
        NutritionRepository(ROOT / "tests/fixtures/catalog.json")
    assert e.value.code == "CONFIGURATION"


def test_import_basis_no_missing_values():
    row = dict(FOOD_CD="123", DESC_KOR="검증 행", SERVING_WT="150", NUTR_CONT1="450",
               NUTR_CONT2="35", NUTR_CONT3="27", NUTR_CONT4="27")
    f = convert(row)
    assert f.basis_grams == 150 and f.nutrition.calories_kcal == 450
    assert f.allergens is None and f.allergen_source is None
    row["NUTR_CONT1"] = "N/A"
    with pytest.raises(ValueError): convert(row)


def test_import_rejects_all_ambiguous_versions():
    row = dict(FOOD_CD="123", DESC_KOR="검증 행", SERVING_WT="100", NUTR_CONT1="300",
               NUTR_CONT2="35", NUTR_CONT3="27", NUTR_CONT4="27")
    another = {**row, "FOOD_CD": "456"}
    result, rejected = normalize({"I0750": {"RESULT": {"CODE": "INFO-000"}, "row": [row, row, another]}})
    assert len(result["foods"]) == 1 and result["foods"][0]["id"] == "456"
    assert rejected == 2


def test_api_success_shortage_nutrition_schema(pipeline, request_data):
    with TestClient(create_app(pipeline)) as client:
        assert client.post("/recommend", json=request_data).status_code == 200
        assert client.post("/nutrition", json={"foods": request_data["meal"]["foods"]}).json()["nutrition"]["calories_kcal"] == 850
        request_data["profile"]["budget_won"] = 1
        assert client.post("/recommend", json=request_data).json()["error"]["code"] == "SHORTAGE"
        request_data["meal"]["foods"][0]["grams"] = 0
        assert client.post("/recommend", json=request_data).status_code == 422


def test_api_validation_identifies_invalid_profile_field(pipeline, request_data):
    request_data["profile"]["age"] = 17
    with TestClient(create_app(pipeline)) as client:
        response = client.post("/recommend", json=request_data)
    assert response.status_code == 422
    assert response.json()["error"]["code"] == "VALIDATION"
    assert "나이 (18~100세)" in response.json()["error"]["message"]
    assert "input" not in response.json()


@pytest.mark.parametrize("failure,expected,status", [("timeout", "TIMEOUT", 504), ("http", "AI_RATE_LIMIT", 429),
                                                  ("invalid", "INVALID_AI_RESPONSE", 502), ("blocked", "INVALID_AI_RESPONSE", 502)])
def test_gemini_failures(pipeline, request_data, failure, expected, status):
    async def handler(request):
        assert request.headers["x-goog-api-key"] == "test-key"
        assert "test-key" not in str(request.url)
        payload = json.loads(request.content)
        assert "responseJsonSchema" in payload["generationConfig"]
        if failure == "timeout": raise httpx.ReadTimeout("test")
        if failure == "http": return httpx.Response(429)
        if failure == "blocked": return httpx.Response(200, json={"candidates": [{"finishReason": "SAFETY"}]})
        return httpx.Response(200, json={"candidates": [{"finishReason": "STOP", "content": {"parts": [{"text": "not json"}]}}]})
    async def run():
        async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
            pipeline.generator = GeminiGenerator(client, "test-key", "gemini-2.5-flash")
            with pytest.raises(RecommendationError) as e:
                await pipeline.recommend(RecommendRequest.model_validate(request_data))
            assert e.value.code == expected and e.value.status == status
    asyncio.run(run())


def test_gemini_mock_structured_success(pipeline, request_data):
    raw = (ROOT / "tests/fixtures/candidates.json").read_text()
    async def run():
        async with httpx.AsyncClient(transport=httpx.MockTransport(lambda r: httpx.Response(200, json={
            "candidates": [{"finishReason": "STOP", "content": {"parts": [{"text": raw}]}}]}))) as client:
            pipeline.generator = GeminiGenerator(client, "test-key", "gemini-2.5-flash")
            result = await pipeline.recommend(RecommendRequest.model_validate(request_data))
            assert len(result.alternatives) == 3
            assert "fixture" in result.alternatives[0].source
    asyncio.run(run())


def test_gemini_uses_current_edited_portions(pipeline, request_data):
    for food, grams in zip(request_data["meal"]["foods"], [220, 260, 30]):
        food["grams"] = grams
    rows = {"candidates": [{"title": "실제 분량 조절", "kind": "portion", "portions": [
        {"food_id": "cutlet", "grams": cutlet}, {"food_id": "rice", "grams": rice},
        {"food_id": "salad", "grams": 30}]} for cutlet, rice in
        [(150, 200), (145, 190), (140, 180), (135, 170), (130, 160)]]}
    def handler(request):
        payload = json.loads(request.content)
        assert payload["generationConfig"]["thinkingConfig"]["thinkingLevel"] == "LOW"
        prompt = payload["contents"][0]["parts"][0]["text"]
        data = json.loads(prompt.split("\nDATA:\n", 1)[1])
        assert {p["food_id"]: p["grams"] for p in data["original_portions"]} == {
            "cutlet": 220, "rice": 260, "salad": 30}
        assert "THIS request" in prompt
        return httpx.Response(200, json={"candidates": [{"finishReason": "STOP", "content": {
            "parts": [{"text": json.dumps(rows)}]}}]})
    async def run():
        async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
            pipeline.generator = GeminiGenerator(client, "test-key", "gemini-3.8-flash")
            result = await pipeline.recommend(RecommendRequest.model_validate(request_data))
            assert len(result.alternatives) == 3
            assert result.original_nutrition.calories_kcal == 1110
            assert result.rejected.get("IMPOSSIBLE_OPERATION", 0) == 0
            assert all("원래 식사보다" in a.reason for a in result.alternatives)
    asyncio.run(run())


def test_gemini_can_connect_without_changing_nutrition_mode(monkeypatch):
    monkeypatch.setenv("RECOMMENDATION_MODE", "fixture")
    monkeypatch.setenv("CANDIDATE_GENERATOR", "gemini")
    monkeypatch.setenv("GEMINI_API_KEY", "test-key")
    # The existing nutrition repository and its result must remain unchanged.
    async def check():
        async with httpx.AsyncClient() as client:
            configured = build_pipeline(client)
            assert isinstance(configured.generator, GeminiGenerator)
            assert configured.repo.fixture
            request = RecommendRequest.model_validate(json.loads((ROOT / "examples/request.json").read_text()))
            assert configured.repo.calculate(configured.repo.portions(request.meal.foods)).calories_kcal == 850
    asyncio.run(check())


def test_missing_config_returns_service_error(monkeypatch):
    monkeypatch.setenv("RECOMMENDATION_MODE", "real")
    monkeypatch.setenv("MFDS_CATALOG_PATH", "/nonexistent/catalog.json")
    with TestClient(create_app()) as client:
        assert client.get("/health").json()["ready"] is False
        assert client.post("/nutrition", json={"foods": [{"name": "밥", "grams": 100}]}).status_code == 503


def test_ai_cannot_inject_nutrients_or_price():
    rows = json.loads((ROOT / "tests/fixtures/candidates.json").read_text())
    rows["candidates"][0]["nutrition"] = {"calories_kcal": 1}
    with pytest.raises(ValidationError): CandidateBatch.model_validate(rows)


def test_gemini_schema_keeps_nested_bounds_in_local_validation():
    # Regression: Gemini 3.8 rejects the full bounded grammar with HTTP 400.
    schema = GeminiGenerator.response_schema()
    assert schema["properties"]["candidates"]["type"] == "array"
    assert schema["$defs"]["Candidate"]["required"] == ["title", "kind", "portions"]
    assert schema["$defs"]["Candidate"]["properties"]["title"]["type"] == "string"
    assert "minimum" not in json.dumps(schema) and "maxItems" not in json.dumps(schema)
    rows = json.loads((ROOT / "tests/fixtures/candidates.json").read_text())
    rows["candidates"][0]["portions"][0]["grams"] = 0
    with pytest.raises(ValidationError): CandidateBatch.model_validate(rows)
    rows["candidates"] = rows["candidates"][:4]
    with pytest.raises(ValidationError): CandidateBatch.model_validate(rows)


@pytest.mark.parametrize("first_error", ["busy", "connection"])
def test_gemini_retries_transient_failure_once(pipeline, request_data, first_error):
    calls = []
    raw = (ROOT / "tests/fixtures/candidates.json").read_text()
    def handler(request):
        calls.append(request)
        if len(calls) == 1:
            if first_error == "connection": raise httpx.ConnectError("temporary connection error")
            return httpx.Response(503)
        return httpx.Response(200, json={"candidates": [{"finishReason": "STOP", "content": {
            "parts": [{"text": raw}]}}]})
    async def run():
        async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
            pipeline.generator = GeminiGenerator(client, "test-key", "gemini-3.8-flash")
            assert len((await pipeline.recommend(RecommendRequest.model_validate(request_data))).alternatives) == 3
    asyncio.run(run())
    assert len(calls) == 2


@pytest.mark.parametrize("http_status,code,expected_status,calls_expected", [
    (429, "AI_RATE_LIMIT", 429, 1), (403, "AI_AUTH", 503, 1),
    (404, "AI_MODEL", 503, 1), (400, "INVALID_AI_REQUEST", 502, 1), (503, "UPSTREAM", 503, 2)])
def test_gemini_errors_are_specific_bounded_and_do_not_log_secrets(
        pipeline, request_data, caplog, http_status, code, expected_status, calls_expected):
    calls = []
    def handler(request):
        calls.append(request)
        return httpx.Response(http_status, json={"error": {"message": "test-key private request content"}})
    async def run():
        async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
            pipeline.generator = GeminiGenerator(client, "test-key", "gemini-3.8-flash")
            with pytest.raises(RecommendationError) as error:
                await pipeline.recommend(RecommendRequest.model_validate(request_data))
            assert error.value.code == code and error.value.status == expected_status
    asyncio.run(run())
    assert len(calls) == calls_expected
    assert "test-key" not in caplog.text and "private request content" not in caplog.text
