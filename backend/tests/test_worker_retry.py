import uuid
from datetime import UTC, datetime
from types import SimpleNamespace

import httpx
import pytest
from celery.exceptions import Retry
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

from app.models import Base, Recording, RecordingStatus
from app.queue import PROCESS_TRANSCRIPT_TASK
from app.settings import get_settings
from worker.errors import PermanentPipelineError, SanitizedRetryError
from worker.retry import is_retryable, retry_countdown
from worker.task_resilience import run_with_retry


class FakeTask:
    name = "pipeline.test_stage"

    def __init__(self, retries: int = 0):
        self.request = SimpleNamespace(retries=retries)
        self.retry_kwargs: dict | None = None

    def retry(self, **kwargs):
        self.retry_kwargs = kwargs
        raise Retry()


@pytest.fixture
def sync_session_factory():
    engine = create_engine(
        "sqlite://", connect_args={"check_same_thread": False}, poolclass=StaticPool
    )
    Base.metadata.create_all(engine)
    yield sessionmaker(engine, expire_on_commit=False)
    engine.dispose()


def make_recording(session, *, status=RecordingStatus.QUEUED) -> Recording:
    recording = Recording(
        id=uuid.uuid4(),
        audio_key="recordings/retry-test.m4a",
        duration_ms=1000,
        locale="hi-IN",
        status=status,
    )
    session.add(recording)
    session.commit()
    return recording


def test_transient_failure_schedules_sanitized_retry(sync_session_factory, monkeypatch):
    with sync_session_factory() as session:
        recording = make_recording(session)
        recording_id = str(recording.id)
    task = FakeTask()
    dead_letters: list[tuple] = []
    monkeypatch.setattr("worker.task_resilience.retry_countdown", lambda *args: 42)

    with pytest.raises(Retry):
        run_with_retry(
            task,
            recording_id,
            "transcribing",
            lambda: (_ for _ in ()).throw(RuntimeError("patient data must not persist")),
            sync_session_factory,
            lambda *args: dead_letters.append(args),
        )

    assert task.retry_kwargs["countdown"] == 42
    assert isinstance(task.retry_kwargs["exc"], SanitizedRetryError)
    assert str(task.retry_kwargs["exc"]) == "RuntimeError"
    assert dead_letters == []
    with sync_session_factory() as session:
        persisted = session.get(Recording, recording.id)
        assert persisted.status == RecordingStatus.RETRYING
        assert persisted.retry_count == 1
        assert persisted.last_error_type == "RuntimeError"


def test_permanent_failure_routes_to_dlq(sync_session_factory):
    with sync_session_factory() as session:
        recording = make_recording(session)
        recording.failure_stage = "extracting"
        session.commit()
        recording_id = str(recording.id)
    task = FakeTask()
    dead_letters: list[tuple] = []

    run_with_retry(
        task,
        recording_id,
        "filtering",
        lambda: (_ for _ in ()).throw(PermanentPipelineError("missing input")),
        sync_session_factory,
        lambda *args: dead_letters.append(args),
    )

    assert dead_letters == [(recording_id, "extracting", "PermanentPipelineError", task.name)]
    with sync_session_factory() as session:
        persisted = session.get(Recording, recording.id)
        assert persisted.status == RecordingStatus.DEAD_LETTERED
        assert persisted.dead_lettered_at is not None


def test_exhausted_transient_failure_routes_to_dlq(sync_session_factory):
    with sync_session_factory() as session:
        recording = make_recording(session)
        recording_id = str(recording.id)
    task = FakeTask(retries=get_settings().worker_max_retries)
    dead_letters: list[tuple] = []

    run_with_retry(
        task,
        recording_id,
        "transcribing",
        lambda: (_ for _ in ()).throw(TimeoutError()),
        sync_session_factory,
        lambda *args: dead_letters.append(args),
    )

    assert len(dead_letters) == 1
    with sync_session_factory() as session:
        assert session.get(Recording, recording.id).status == RecordingStatus.DEAD_LETTERED


def test_replay_resets_metadata_and_enqueues_failed_stage(sync_session_factory, monkeypatch):
    from worker import main

    with sync_session_factory() as session:
        recording = make_recording(session, status=RecordingStatus.DEAD_LETTERED)
        recording.failure_stage = "extracting"
        recording.last_error_type = "TimeoutError"
        recording.dead_lettered_at = datetime.now(UTC)
        session.commit()
        recording_id = str(recording.id)
    sent: list[tuple] = []
    monkeypatch.setattr(main, "get_session_factory", lambda: sync_session_factory)
    monkeypatch.setattr(main.celery_app, "send_task", lambda name, args: sent.append((name, args)))

    assert main.replay_dead_letter.run(recording_id) is True

    assert sent == [(PROCESS_TRANSCRIPT_TASK, [recording_id])]
    with sync_session_factory() as session:
        persisted = session.get(Recording, recording.id)
        assert persisted.status == RecordingStatus.FILTERING
        assert persisted.failure_stage is None
        assert persisted.last_error_type is None
        assert persisted.dead_lettered_at is None


def test_retry_classification_and_backoff():
    request = httpx.Request("GET", "https://example.invalid")
    retryable = httpx.HTTPStatusError(
        "server error", request=request, response=httpx.Response(503, request=request)
    )
    permanent = httpx.HTTPStatusError(
        "bad request", request=request, response=httpx.Response(400, request=request)
    )

    assert is_retryable(retryable) is True
    assert is_retryable(permanent) is False
    assert retry_countdown(0, 30, 900, 0) == 30
    assert retry_countdown(8, 30, 900, 0) == 900
