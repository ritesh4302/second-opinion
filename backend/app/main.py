from fastapi import FastAPI, HTTPException

from app.problems import problem_handler
from app.routers import assessments, health, recordings


def create_app() -> FastAPI:
    app = FastAPI(
        title="Second Opinion API",
        version="0.1.0",
        description="AI-powered triage pipeline for pharmacists (see docs/BACKEND.md)",
    )
    app.add_exception_handler(HTTPException, problem_handler)
    app.include_router(health.router)
    app.include_router(recordings.router)
    app.include_router(assessments.router)
    return app


app = create_app()
