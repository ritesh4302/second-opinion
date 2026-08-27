"""Bounded Celery retry orchestration for pipeline stages."""

from collections.abc import Callable

import structlog
from celery import Task
from sqlalchemy.orm import Session, sessionmaker

from app.settings import get_settings
from worker.errors import SanitizedRetryError
from worker.retry import (
    error_type,
    is_retryable,
    mark_dead_lettered,
    mark_retry,
    retry_countdown,
)

logger = structlog.get_logger(__name__)


def run_with_retry(
    task: Task,
    recording_id: str,
    stage: str,
    operation: Callable[[], None],
    session_factory: sessionmaker[Session],
    send_dead_letter: Callable[[str, str, str, str], None],
) -> None:
    try:
        operation()
    except Exception as failure:
        _handle_failure(
            task,
            recording_id,
            stage,
            failure,
            session_factory,
            send_dead_letter,
        )


def _handle_failure(
    task: Task,
    recording_id: str,
    stage: str,
    failure: Exception,
    session_factory: sessionmaker[Session],
    send_dead_letter: Callable[[str, str, str, str], None],
) -> None:
    settings = get_settings()
    safe_type = error_type(failure)
    retries = task.request.retries
    if is_retryable(failure) and retries < settings.worker_max_retries:
        effective_stage = _persist_safely(
            mark_retry, stage, session_factory, recording_id, stage, safe_type
        )
        countdown = retry_countdown(
            retries,
            settings.worker_retry_backoff_seconds,
            settings.worker_retry_backoff_max_seconds,
            settings.worker_retry_jitter_seconds,
        )
        logger.warning(
            "stage_retry_scheduled",
            recording_id=recording_id,
            stage=effective_stage,
            error_type=safe_type,
            retry_number=retries + 1,
            countdown_seconds=countdown,
        )
        raise task.retry(
            exc=SanitizedRetryError(safe_type),
            countdown=countdown,
            max_retries=settings.worker_max_retries,
        ) from None

    effective_stage = _persist_safely(
        mark_dead_lettered, stage, session_factory, recording_id, stage, safe_type
    )
    send_dead_letter(recording_id, effective_stage, safe_type, task.name)
    logger.error(
        "stage_dead_lettered",
        recording_id=recording_id,
        stage=effective_stage,
        error_type=safe_type,
        retries=retries,
    )


def _persist_safely(update: Callable[..., str], fallback: str, *args) -> str:
    try:
        return update(*args)
    except Exception as failure:
        logger.error("retry_metadata_write_failed", error_type=error_type(failure))
        return fallback
