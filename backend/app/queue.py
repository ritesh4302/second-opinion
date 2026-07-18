from collections.abc import Callable

from celery import Celery

from app.settings import get_settings

PROCESS_RECORDING_TASK = "pipeline.process_recording"
PROCESS_TRANSCRIPT_TASK = "pipeline.process_transcript"


def make_celery() -> Celery:
    settings = get_settings()
    return Celery(
        "second_opinion",
        broker=settings.redis_url,
        backend=settings.redis_url,
    )


def _enqueue_processing(recording_id: str) -> None:
    # send_task by name: the API never imports worker code (separate deployable).
    make_celery().send_task(PROCESS_RECORDING_TASK, args=[recording_id])


def get_enqueue() -> Callable[[str], None]:
    """FastAPI dependency; overridden in tests."""
    return _enqueue_processing
