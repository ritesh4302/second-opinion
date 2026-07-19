"""Celery worker entrypoint: `celery -A worker.main worker`.

Speech stage (step 3, Sarvam Batch API with native diarization) and NLP
stage (step 4, relevance filter + extraction via sarvam-m) are implemented.
The assessment stage (step 5) is still a stub.
"""

import logging

from app.queue import (
    PROCESS_EXTRACTION_TASK,
    PROCESS_RECORDING_TASK,
    PROCESS_TRANSCRIPT_TASK,
    make_celery,
)
from app.settings import get_settings
from app.storage import get_storage
from worker.db import get_session_factory
from worker.nlp import get_nlp_model
from worker.pipeline import run_nlp_stage, run_speech_stage
from worker.transcription import get_transcriber

logger = logging.getLogger(__name__)

celery_app = make_celery()


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
    # Assessment stage lands in build-order step 5 (blocked on Q3: medical model).
    logger.info("received recording %s for assessment stage (stub)", recording_id)
    return recording_id
