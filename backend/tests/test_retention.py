"""Retention sweep tests: sync session + in-memory storage (no Celery or S3)."""

import uuid
from datetime import UTC, datetime, timedelta

import pytest
from sqlalchemy import create_engine, select
from sqlalchemy.orm import Session, sessionmaker
from sqlalchemy.pool import StaticPool

from app.db import Base
from app.models import Assessment, Recording, RecordingStatus, TranscriptSegment
from worker.retention import run_retention_sweep


class SyncFakeStorage:
    def __init__(self) -> None:
        self.objects: dict[str, bytes] = {}

    def delete(self, key: str) -> None:
        self.objects.pop(key, None)


@pytest.fixture
def sync_session_factory():
    engine = create_engine(
        "sqlite://", connect_args={"check_same_thread": False}, poolclass=StaticPool
    )
    Base.metadata.create_all(engine)
    yield sessionmaker(engine, expire_on_commit=False)
    engine.dispose()


def make_recording(
    session: Session,
    age_days: int,
    purged: bool = False,
    with_transcript: bool = False,
    with_assessment: bool = False,
) -> Recording:
    recording_id = uuid.uuid4()
    now = datetime.now(UTC)
    recording = Recording(
        id=recording_id,
        audio_key=f"recordings/{recording_id}.m4a",
        duration_ms=4000,
        locale="hi-IN",
        consent_confirmed=True,
        status=RecordingStatus.COMPLETED,
        created_at=now - timedelta(days=age_days),
        audio_purged_at=now if purged else None,
    )
    session.add(recording)
    if with_transcript:
        session.add(
            TranscriptSegment(
                recording_id=recording_id,
                speaker_label="0",
                segment_index=0,
                text="मुझे बुखार है।",
                start_ms=0,
                end_ms=1500,
            )
        )
    if with_assessment:
        session.add(
            Assessment(
                recording_id=recording_id,
                conditions=[],
                red_flags=[],
                otc_guidance=[],
                model_id="stub-model",
                prompt_version="v0",
            )
        )
    session.commit()
    return recording


def test_sweep_purges_expired_audio_and_transcripts(sync_session_factory) -> None:
    storage = SyncFakeStorage()
    with sync_session_factory() as session:
        expired = make_recording(session, age_days=40, with_transcript=True, with_assessment=True)
    storage.objects[expired.audio_key] = b"fake-aac-bytes"

    purged = run_retention_sweep(sync_session_factory, storage, retention_days=30)

    assert purged == 1
    assert storage.objects == {}
    with sync_session_factory() as session:
        row = session.get(Recording, expired.id)
        assert row is not None  # the row survives; only raw data is purged
        assert row.audio_purged_at is not None
        transcripts = session.execute(
            select(TranscriptSegment).where(TranscriptSegment.recording_id == expired.id)
        )
        assert transcripts.scalars().all() == []
        # Derived output is kept for the pilot's quality loop
        assessment = session.execute(
            select(Assessment).where(Assessment.recording_id == expired.id)
        )
        assert assessment.scalar_one_or_none() is not None


def test_sweep_skips_recent_and_already_purged(sync_session_factory) -> None:
    storage = SyncFakeStorage()
    with sync_session_factory() as session:
        recent = make_recording(session, age_days=5, with_transcript=True)
        already_purged = make_recording(session, age_days=40, purged=True)
    storage.objects[recent.audio_key] = b"recent-bytes"

    purged = run_retention_sweep(sync_session_factory, storage, retention_days=30)

    assert purged == 0
    assert storage.objects == {recent.audio_key: b"recent-bytes"}
    with sync_session_factory() as session:
        transcripts = session.execute(
            select(TranscriptSegment).where(TranscriptSegment.recording_id == recent.id)
        )
        assert len(transcripts.scalars().all()) == 1
        assert session.get(Recording, already_purged.id) is not None


def test_sweep_is_idempotent(sync_session_factory) -> None:
    storage = SyncFakeStorage()
    with sync_session_factory() as session:
        expired = make_recording(session, age_days=40)
    storage.objects[expired.audio_key] = b"fake-aac-bytes"

    assert run_retention_sweep(sync_session_factory, storage, retention_days=30) == 1
    assert run_retention_sweep(sync_session_factory, storage, retention_days=30) == 0
