"""Celery worker entrypoint: `celery -A worker.main worker`.

Speech stage (build-order step 3) is implemented: Sarvam Batch API with
native diarization. NLP/assessment stages (steps 4-5) are still stubs.
"""

import logging

from app.queue import PROCESS_RECORDING_TASK, PROCESS_TRANSCRIPT_TASK, make_celery
from app.storage import get_storage
from worker.db import get_session_factory
from worker.pipeline import run_speech_stage
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
    # NLP stage (relevance filter + extraction) lands in build-order step 4.
    logger.info("received recording %s for NLP stage (stub)", recording_id)
    return recording_id
