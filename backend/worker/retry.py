"""Retry classification, backoff, and recording-state persistence."""

import uuid
from datetime import UTC, datetime
from random import uniform

import httpx
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session, sessionmaker

from app.models import Recording, RecordingStatus
from worker.errors import PermanentPipelineError


def error_type(error: Exception) -> str:
    """Return a bounded, non-sensitive error identifier."""
    return type(error).__name__[:100]


def is_retryable(error: Exception) -> bool:
    if isinstance(error, (PermanentPipelineError, IntegrityError, KeyError, ValueError, TypeError)):
        return False
    if isinstance(error, httpx.HTTPStatusError):
        status = error.response.status_code
        return status in {408, 429} or status >= 500
    response = getattr(error, "response", None)
    metadata = response.get("ResponseMetadata", {}) if isinstance(response, dict) else {}
    status = metadata.get("HTTPStatusCode")
    if isinstance(status, int):
        return status in {408, 429} or status >= 500
    return True


def retry_countdown(retries: int, base: int, maximum: int, jitter: int) -> int:
    delay = min(base * (2**retries), maximum)
    return delay + int(uniform(0, jitter)) if jitter > 0 else delay


def mark_retry(
    session_factory: sessionmaker[Session],
    recording_id: str,
    stage: str,
    last_error_type: str,
) -> str:
    with session_factory() as session:
        recording = _get_recording(session, recording_id)
        if recording is None:
            return stage
        recording.status = RecordingStatus.RETRYING
        recording.failure_stage = recording.failure_stage or stage
        recording.retry_count += 1
        recording.last_error_type = last_error_type
        recording.dead_lettered_at = None
        session.commit()
        return recording.failure_stage


def mark_dead_lettered(
    session_factory: sessionmaker[Session],
    recording_id: str,
    stage: str,
    last_error_type: str,
) -> str:
    with session_factory() as session:
        recording = _get_recording(session, recording_id)
        if recording is None:
            return stage
        recording.status = RecordingStatus.DEAD_LETTERED
        recording.failure_stage = recording.failure_stage or stage
        recording.last_error_type = last_error_type
        recording.dead_lettered_at = datetime.now(UTC)
        session.commit()
        return recording.failure_stage


def _get_recording(session: Session, recording_id: str) -> Recording | None:
    try:
        parsed_id = uuid.UUID(recording_id)
    except ValueError:
        return None
    return session.get(Recording, parsed_id)
