from collections.abc import Callable

from celery import Celery

from app.settings import get_settings

PROCESS_RECORDING_TASK = "pipeline.process_recording"
PROCESS_TRANSCRIPT_TASK = "pipeline.process_transcript"
PROCESS_EXTRACTION_TASK = "pipeline.process_extraction"
RETENTION_SWEEP_TASK = "pipeline.retention_sweep"
DEAD_LETTER_TASK = "pipeline.dead_letter"
REPLAY_DEAD_LETTER_TASK = "pipeline.replay_dead_letter"
PIPELINE_QUEUE = "pipeline"
DLQ_QUEUE = "pipeline.dlq"


def make_celery() -> Celery:
    settings = get_settings()
    app = Celery(
        "second_opinion",
        broker=settings.redis_url,
        backend=settings.redis_url,
    )
    app.conf.update(
        task_serializer="json",
        result_serializer="json",
        accept_content=["json"],
        task_default_queue=PIPELINE_QUEUE,
        task_routes={DEAD_LETTER_TASK: {"queue": DLQ_QUEUE}},
        task_acks_late=True,
        task_reject_on_worker_lost=True,
        worker_prefetch_multiplier=1,
        broker_transport_options={"visibility_timeout": 3600},
        timezone="UTC",
    )
    return app


def _enqueue_processing(recording_id: str) -> None:
    # send_task by name: the API never imports worker code (separate deployable).
    make_celery().send_task(PROCESS_RECORDING_TASK, args=[recording_id])


def get_enqueue() -> Callable[[str], None]:
    """FastAPI dependency; overridden in tests."""
    return _enqueue_processing
