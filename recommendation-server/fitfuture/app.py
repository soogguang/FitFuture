import asyncio
import os
from contextlib import asynccontextmanager
from pathlib import Path

import httpx
from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import Field

from .errors import RecommendationError
from .generator import FixtureGenerator, GeminiGenerator
from .nutrition import NutritionRepository
from .pipeline import RecommendationPipeline
from .schemas import FoodPortion, Nutrition, RecommendRequest, RecommendResponse, StrictModel
from .scoring import RuleScorer

ROOT = Path(__file__).resolve().parents[1]


def build_pipeline(client):
    mode = os.getenv("RECOMMENDATION_MODE", "real")
    if mode not in ("real", "fixture"):
        raise RecommendationError("CONFIGURATION", "RECOMMENDATION_MODE는 real 또는 fixture여야 해요.", 503)
    fixture = mode == "fixture"
    if fixture:
        repo = NutritionRepository(ROOT / "tests/fixtures/catalog.json", True)
    elif os.getenv("MFDS_CATALOG_PATH"):
        # Reviewed JSON snapshot from `python -m fitfuture.import_mfds` (with curated offers).
        repo = NutritionRepository(Path(os.environ["MFDS_CATALOG_PATH"]))
    else:
        # Default: the app's MFDS food DB + aliases, with NEIS allergen / Seoul price evidence.
        repo = NutritionRepository.from_mfds()
    # Candidate generation can be connected separately from the upstream nutrition module.
    candidate_mode = os.getenv("CANDIDATE_GENERATOR", "fixture" if fixture else "gemini")
    if candidate_mode not in ("gemini", "fixture") or (not fixture and candidate_mode == "fixture"):
        raise RecommendationError("CONFIGURATION", "후보 생성 설정을 확인해 주세요. 실제 모드에는 Gemini가 필요해요.", 503)
    generator = (FixtureGenerator(ROOT / "tests/fixtures/candidates.json") if candidate_mode == "fixture" else
                 GeminiGenerator(client, os.getenv("GEMINI_API_KEY", ""), os.getenv("GEMINI_MODEL", "gemini-3.5-flash-lite")))
    try:
        scorer = RuleScorer(Path(os.getenv("SCORING_CONFIG_PATH", str(ROOT / "fitfuture/scoring.json"))))
    except (ValueError, OSError, KeyError, TypeError) as exc:
        raise RecommendationError("CONFIGURATION", "추천 점수 설정 파일을 확인해 주세요.", 503) from exc
    return RecommendationPipeline(repo, generator, scorer, scorer.config["safety"])


class NutritionRequest(StrictModel):
    foods: list[FoodPortion] = Field(min_length=1, max_length=20)


class NutritionResponse(StrictModel):
    nutrition: Nutrition
    source: str


def create_app(pipeline=None):
    @asynccontextmanager
    async def lifespan(app):
        async with httpx.AsyncClient() as client:
            app.state.pipeline = pipeline
            app.state.configuration_error = None
            if pipeline is None:
                try:
                    app.state.pipeline = build_pipeline(client)
                except RecommendationError as exc:
                    app.state.configuration_error = exc
            yield

    app = FastAPI(title="FitFuture meal recommendations", lifespan=lifespan)

    @app.exception_handler(RequestValidationError)
    async def validation_handler(request, exc):
        labels = {"age": "나이 (18~100세)", "height_cm": "키 (100~250cm)",
                  "weight_kg": "현재 체중 (30~300kg)", "target_weight_kg": "목표 체중 (30~300kg)",
                  "budget_won": "한 끼 예산", "environment": "식사 환경", "goal": "목표",
                  "foods": "음식 목록", "grams": "섭취량 (0g 초과~5000g)", "name": "음식 이름"}
        fields = list(dict.fromkeys(labels[part] for error in exc.errors()
                                   for part in error["loc"] if part in labels))
        message = (", ".join(fields) + "을 확인해 주세요." if fields
                   else "음식과 프로필의 입력 내용을 확인해 주세요.")
        return JSONResponse(status_code=422, content={"error": {"code": "VALIDATION", "message": message}})

    @app.exception_handler(RecommendationError)
    async def error_handler(request, exc):
        return JSONResponse(status_code=exc.status, content={"error": {"code": exc.code,
                            "message": exc.message, **exc.details}})

    def configured():
        if app.state.configuration_error:
            raise app.state.configuration_error
        return app.state.pipeline

    @app.get("/health")
    async def health():
        p = app.state.pipeline
        ready = p is not None and (not isinstance(p.generator, GeminiGenerator) or bool(p.generator.key))
        return {"ready": ready, "mode": p.generator.source if p else "unconfigured",
                "nutrition": p.repo.source if p else None, "foods": len(p.repo.foods) if p else 0,
                **(p.repo.evidence_summary if p else {})}

    @app.post("/nutrition", response_model=NutritionResponse)
    async def nutrition(request: NutritionRequest):
        repo = configured().repo
        return NutritionResponse(nutrition=repo.calculate(repo.portions(request.foods)), source=repo.source)

    @app.post("/recommend", response_model=RecommendResponse)
    async def recommend(request: RecommendRequest):
        try:
            async with asyncio.timeout(35):
                return await configured().recommend(request)
        except TimeoutError as exc:
            raise RecommendationError("TIMEOUT", "추천 요청 시간이 초과됐어요.", 504) from exc

    return app


app = create_app()
