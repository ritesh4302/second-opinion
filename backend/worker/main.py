"""Celery worker entrypoint: `celery -A worker.main worker`.

Speech stage (step 3, Sarvam Batch API with native diarization), NLP stage
(step 4, relevance filter + extraction), and assessment stage (step 5,
triage output) are implemented.
"""

import logging

import structlog
from celery import signals

from app.observability import configure_logging
from app.queue import (
    PROCESS_EXTRACTION_TASK,
    PROCESS_RECORDING_TASK,
    PROCESS_TRANSCRIPT_TASK,
    RETENTION_SWEEP_TASK,
    make_celery,
)
from app.settings import get_settings
from app.storage import get_storage
from worker.assessment import get_assessor
from worker.db import get_session_factory
from worker.nlp import get_nlp_model
from worker.pipeline import run_assessment_stage, run_nlp_stage, run_speech_stage
from worker.retention import run_retention_sweep
from worker.transcription import get_transcriber

logger = logging.getLogger(__name__)

celery_app = make_celery()

# DPDP retention: sweep daily (worker runs with embedded beat, `worker --beat`)
celery_app.conf.beat_schedule = {
    "retention-sweep-daily": {
        "task": RETENTION_SWEEP_TASK,
        "schedule": 24 * 60 * 60,
    }
}


@signals.setup_logging.connect
def _setup_logging(**_kwargs) -> None:
    # Replaces Celery's own logging setup so worker output matches the API.
    configure_logging()


@signals.task_prerun.connect
def _bind_task_context(task_id=None, args=None, **_kwargs) -> None:
    # Every pipeline task takes recording_id as its first argument; binding it
    # here puts it on every log line of the stage (docs/BACKEND.md §7).
    structlog.contextvars.clear_contextvars()
    context = {"task_id": task_id}
    if args:
        context["recording_id"] = args[0]
    structlog.contextvars.bind_contextvars(**context)


@signals.task_postrun.connect
def _clear_task_context(**_kwargs) -> None:
    structlog.contextvars.clear_contextvars()


@celery_app.task(name=PROCESS_RECORDING_TASK)
def process_recording(recording_id: str) -> str:
    # No health data in logs: IDs only (docs/BACKEND.md §8)
    logger.info("received recording %s for speech stage", recording_id)
    run_speech_stage(
        recording_id,
        session_factory=get_session_factory(),
        storage=get_storage(),
        transcriber=get_transcriber(),
        enqueue_next=_enqueue_transcript_processing,
    )
    return recording_id


def _enqueue_transcript_processing(recording_id: str) -> None:
    celery_app.send_task(PROCESS_TRANSCRIPT_TASK, args=[recording_id])


@celery_app.task(name=PROCESS_TRANSCRIPT_TASK)
def process_transcript(recording_id: str) -> str:
    logger.info("received recording %s for NLP stage", recording_id)
    run_nlp_stage(
        recording_id,
        session_factory=get_session_factory(),
        nlp=get_nlp_model(),
        relevance_threshold=get_settings().relevance_threshold,
        enqueue_next=_enqueue_assessment,
    )
    return recording_id


def _enqueue_assessment(recording_id: str) -> None:
    celery_app.send_task(PROCESS_EXTRACTION_TASK, args=[recording_id])


@celery_app.task(name=PROCESS_EXTRACTION_TASK)
def process_extraction(recording_id: str) -> str:
    logger.info("received recording %s for assessment stage", recording_id)
    run_assessment_stage(
        recording_id,
        session_factory=get_session_factory(),
        assessor=get_assessor(),
    )
    return recording_id


@celery_app.task(name=RETENTION_SWEEP_TASK)
def retention_sweep() -> int:
    days = get_settings().retention_days
    logger.info("running retention sweep (window: %d days)", days)
    return run_retention_sweep(
        session_factory=get_session_factory(),
        storage=get_storage(),
        retention_days=days,
    )
