import time

import structlog
from fastapi import FastAPI, HTTPException, Request

from app.observability import configure_logging
from app.problems import problem_handler
from app.routers import assessments, health, recordings

access_logger = structlog.get_logger("app.http")


def create_app() -> FastAPI:
    configure_logging()
    app = FastAPI(
        title="Second Opinion API",
        version="0.1.0",
        description="AI-powered triage pipeline for pharmacists (see docs/BACKEND.md)",
    )
    app.add_exception_handler(HTTPException, problem_handler)
    app.include_router(health.router)
    app.include_router(recordings.router)
    app.include_router(assessments.router)

    @app.middleware("http")
    async def log_requests(request: Request, call_next):
        start = time.perf_counter()
        response = await call_next(request)
        # No health data in logs: method/path/status/timing only (docs/BACKEND.md §8)
        access_logger.info(
            "http_request",
            method=request.method,
            path=request.url.path,
            status_code=response.status_code,
            duration_ms=round((time.perf_counter() - start) * 1000, 1),
        )
        return response

    return app


app = create_app()
