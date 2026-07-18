"""Speech stage tests: sync session + fakes (no Celery, S3, or Sarvam)."""

import uuid

import pytest
from sqlalchemy import create_engine, select
from sqlalchemy.orm import Session, sessionmaker
from sqlalchemy.pool import StaticPool

from app.db import Base
from app.models import Recording, RecordingStatus, TranscriptSegment
from worker.pipeline import run_speech_stage
from worker.transcription import Segment, parse_sarvam_output

SEGMENTS = [
    Segment("0", "मुझे बुखार है।", 0, 1500),
    Segment("1", "कब से?", 1700, 2400),
]


class SyncFakeStorage:
    def __init__(self) -> None:
        self.objects: dict[str, bytes] = {}

    def get(self, key: str) -> bytes:
        return self.objects[key]


class StubTranscriber:
    def __init__(self, segments: list[Segment] | None = None, error: Exception | None = None):
        self.segments = segments or SEGMENTS
        self.error = error
        self.calls: list[tuple[str, str]] = []

    def transcribe(self, audio: bytes, filename: str, locale: str) -> list[Segment]:
        self.calls.append((filename, locale))
        if self.error:
            raise self.error
        return self.segments


@pytest.fixture
def sync_session_factory():
    engine = create_engine(
        "sqlite://", connect_args={"check_same_thread": False}, poolclass=StaticPool
    )
    Base.metadata.create_all(engine)
    yield sessionmaker(engine, expire_on_commit=False)
    engine.dispose()


def make_recording(session: Session) -> Recording:
    recording = Recording(
        id=uuid.uuid4(),
        audio_key="recordings/test.m4a",
        duration_ms=4000,
        locale="hi-IN",
        status=RecordingStatus.QUEUED,
    )
    session.add(recording)
    session.commit()
    return recording


def run(session_factory, storage, transcriber, recording_id, enqueued):
    run_speech_stage(
        str(recording_id),
        session_factory=session_factory,
        storage=storage,
        transcriber=transcriber,
        enqueue_next=enqueued.append,
    )


def test_speech_stage_persists_segments_and_advances_status(sync_session_factory):
    storage = SyncFakeStorage()
    storage.objects["recordings/test.m4a"] = b"fake-audio"
    transcriber = StubTranscriber()
    enqueued: list[str] = []
    with sync_session_factory() as session:
        recording = make_recording(session)

    run(sync_session_factory, storage, transcriber, recording.id, enqueued)

    assert transcriber.calls == [("test.m4a", "hi-IN")]
    assert enqueued == [str(recording.id)]
    with sync_session_factory() as session:
        assert session.get(Recording, recording.id).status == RecordingStatus.FILTERING
        rows = session.scalars(
            select(TranscriptSegment).order_by(TranscriptSegment.segment_index)
        ).all()
        assert [(r.speaker_label, r.text, r.start_ms, r.end_ms) for r in rows] == [
            ("0", "मुझे बुखार है।", 0, 1500),
            ("1", "कब से?", 1700, 2400),
        ]


def test_speech_stage_failure_marks_failed(sync_session_factory):
    storage = SyncFakeStorage()
    storage.objects["recordings/test.m4a"] = b"fake-audio"
    transcriber = StubTranscriber(error=RuntimeError("provider down"))
    enqueued: list[str] = []
    with sync_session_factory() as session:
        recording = make_recording(session)

    with pytest.raises(RuntimeError):
        run(sync_session_factory, storage, transcriber, recording.id, enqueued)

    assert enqueued == []
    with sync_session_factory() as session:
        row = session.get(Recording, recording.id)
        assert row.status == RecordingStatus.FAILED
        assert row.failure_stage == "transcribing"


def test_speech_stage_retry_replaces_segments(sync_session_factory):
    storage = SyncFakeStorage()
    storage.objects["recordings/test.m4a"] = b"fake-audio"
    enqueued: list[str] = []
    with sync_session_factory() as session:
        recording = make_recording(session)

    run(sync_session_factory, storage, StubTranscriber(), recording.id, enqueued)
    run(
        sync_session_factory,
        storage,
        StubTranscriber(segments=SEGMENTS[:1]),
        recording.id,
        enqueued,
    )

    with sync_session_factory() as session:
        rows = session.scalars(select(TranscriptSegment)).all()
        assert len(rows) == 1


def test_speech_stage_missing_recording_is_dropped(sync_session_factory):
    enqueued: list[str] = []
    run(sync_session_factory, SyncFakeStorage(), StubTranscriber(), uuid.uuid4(), enqueued)
    assert enqueued == []


def test_parse_sarvam_output_diarized():
    data = {
        "transcript": "Hello. I have a question.",
        "diarized_transcript": {
            "entries": [
                {
                    "transcript": "Hello, how can I help you today?",
                    "start_time_seconds": 0.01,
                    "end_time_seconds": 2.5,
                    "speaker_id": "0",
                },
                {
                    "transcript": "I have a question.",
                    "start_time_seconds": 2.8,
                    "end_time_seconds": 4.2,
                    "speaker_id": "1",
                },
            ]
        },
    }
    assert parse_sarvam_output(data) == [
        Segment("0", "Hello, how can I help you today?", 10, 2500),
        Segment("1", "I have a question.", 2800, 4200),
    ]


def test_parse_sarvam_output_falls_back_to_flat_transcript():
    assert parse_sarvam_output({"transcript": "sirf ek awaaz"}) == [
        Segment("unknown", "sirf ek awaaz", 0, 0)
    ]
