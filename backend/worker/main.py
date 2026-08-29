"""Celery worker entrypoint: `celery -A worker.main worker`.

Speech stage (step 3, Sarvam Batch API with native diarization), NLP stage
(step 4, relevance filter + extraction), and assessment stage (step 5,
triage output) are implemented.
"""

import logging
import uuid

import structlog
from celery import Task, signals

from app.models import Recording, RecordingStatus
from app.observability import configure_logging
from app.queue import (
    DEAD_LETTER_TASK,
    DLQ_QUEUE,
    PROCESS_EXTRACTION_TASK,
    PROCESS_RECORDING_TASK,
    PROCESS_TRANSCRIPT_TASK,
    REPLAY_DEAD_LETTER_TASK,
    RETENTION_SWEEP_TASK,
    make_celery,
)
from app.settings import get_settings
from app.storage import get_storage
from worker.assessment import get_assessor
from worker.db import get_session_factory
from worker.errors import SanitizedRetryError
from worker.nlp import get_nlp_model
from worker.pipeline import (
    ASSESS_STAGE,
    EXTRACT_STAGE,
    FILTER_STAGE,
    SPEECH_STAGE,
    run_assessment_stage,
    run_nlp_stage,
    run_speech_stage,
)
from worker.retention import run_retention_sweep
from worker.retry import error_type
from worker.task_resilience import run_with_retry
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


@celery_app.task(bind=True, name=PROCESS_RECORDING_TASK)
def process_recording(task: Task, recording_id: str) -> str:
    # No health data in logs: IDs only (docs/BACKEND.md §8)
    logger.info("received recording %s for speech stage", recording_id)
    session_factory = get_session_factory()
    run_with_retry(
        task,
        recording_id,
        SPEECH_STAGE,
        lambda: run_speech_stage(
            recording_id,
            session_factory=session_factory,
            storage=get_storage(),
            transcriber=get_transcriber(),
            enqueue_next=_enqueue_transcript_processing,
        ),
        session_factory,
        _send_dead_letter,
    )
    return recording_id


def _enqueue_transcript_processing(recording_id: str) -> None:
    celery_app.send_task(PROCESS_TRANSCRIPT_TASK, args=[recording_id])


@celery_app.task(bind=True, name=PROCESS_TRANSCRIPT_TASK)
def process_transcript(task: Task, recording_id: str) -> str:
    logger.info("received recording %s for NLP stage", recording_id)
    session_factory = get_session_factory()
    run_with_retry(
        task,
        recording_id,
        FILTER_STAGE,
        lambda: run_nlp_stage(
            recording_id,
            session_factory=session_factory,
            nlp=get_nlp_model(),
            relevance_threshold=get_settings().relevance_threshold,
            enqueue_next=_enqueue_assessment,
        ),
        session_factory,
        _send_dead_letter,
    )
    return recording_id


def _enqueue_assessment(recording_id: str) -> None:
    celery_app.send_task(PROCESS_EXTRACTION_TASK, args=[recording_id])


@celery_app.task(bind=True, name=PROCESS_EXTRACTION_TASK)
def process_extraction(task: Task, recording_id: str) -> str:
    logger.info("received recording %s for assessment stage", recording_id)
    session_factory = get_session_factory()
    run_with_retry(
        task,
        recording_id,
        ASSESS_STAGE,
        lambda: run_assessment_stage(
            recording_id,
            session_factory=session_factory,
            assessor=get_assessor(),
        ),
        session_factory,
        _send_dead_letter,
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


def _send_dead_letter(
    recording_id: str, stage: str, safe_error_type: str, source_task: str
) -> None:
    celery_app.send_task(
        DEAD_LETTER_TASK,
        args=[recording_id, stage, safe_error_type, source_task],
        queue=DLQ_QUEUE,
    )


@celery_app.task(name=DEAD_LETTER_TASK)
def dead_letter(recording_id: str, stage: str, safe_error_type: str, source_task: str) -> None:
    """Inspection sink; runs only when an operator consumes the DLQ."""
    logger.warning(
        "dead letter inspected recording_id=%s stage=%s error_type=%s source_task=%s",
        recording_id,
        stage,
        safe_error_type,
        source_task,
    )


@celery_app.task(bind=True, name=REPLAY_DEAD_LETTER_TASK, max_retries=5)
def replay_dead_letter(task: Task, recording_id: str, stage: str | None = None) -> bool:
    """Re-enqueue a failed stage after its root cause has been resolved."""
    if stage is None:
        with get_session_factory()() as session:
            recording = session.get(Recording, uuid.UUID(recording_id))
            if recording is None or recording.status != RecordingStatus.DEAD_LETTERED:
                return False
            stage = recording.failure_stage
            recording.status = _status_for_replay(stage)
            recording.failure_stage = None
            recording.last_error_type = None
            recording.dead_lettered_at = None
            session.commit()
    try:
        celery_app.send_task(_task_for_stage(stage), args=[recording_id])
    except Exception as failure:
        raise task.retry(
            exc=SanitizedRetryError(error_type(failure)),
            countdown=30,
            kwargs={"recording_id": recording_id, "stage": stage},
        ) from None
    return True


def _task_for_stage(stage: str | None) -> str:
    if stage == SPEECH_STAGE:
        return PROCESS_RECORDING_TASK
    if stage in {FILTER_STAGE, EXTRACT_STAGE}:
        return PROCESS_TRANSCRIPT_TASK
    if stage == ASSESS_STAGE:
        return PROCESS_EXTRACTION_TASK
    raise ValueError(f"unknown pipeline stage: {stage}")


def _status_for_replay(stage: str | None) -> RecordingStatus:
    if stage == SPEECH_STAGE:
        return RecordingStatus.QUEUED
    if stage in {FILTER_STAGE, EXTRACT_STAGE}:
        return RecordingStatus.FILTERING
    if stage == ASSESS_STAGE:
        return RecordingStatus.ASSESSING
    raise ValueError(f"unknown pipeline stage: {stage}")
