import asyncio
import os
from contextlib import asynccontextmanager
from pathlib import Path

import httpx
from fastapi import FastAPI, Request
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
    path = ROOT / "tests/fixtures/catalog.json" if fixture else Path(os.getenv("MFDS_CATALOG_PATH", "data/mfds.json"))
    repo = NutritionRepository(path, fixture)
    generator = (FixtureGenerator(ROOT / "tests/fixtures/candidates.json") if fixture else
                 GeminiGenerator(client, os.getenv("GEMINI_API_KEY", ""), os.getenv("GEMINI_MODEL", "gemini-2.5-flash")))
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
        return {"ready": ready, "mode": p.generator.source if p else "unconfigured"}

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
