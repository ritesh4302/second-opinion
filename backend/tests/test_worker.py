"""Speech + NLP + assessment stage tests: sync session + fakes (no Celery, S3, or Sarvam)."""

import uuid

import pytest
from sqlalchemy import create_engine, select
from sqlalchemy.orm import Session, sessionmaker
from sqlalchemy.pool import StaticPool

from app.db import Base
from app.models import Assessment, Extraction, Recording, RecordingStatus, TranscriptSegment
from worker.assessment import (
    PROMPT_VERSION,
    AssessmentError,
    AssessmentResult,
    CaseSummary,
    ConditionHypothesis,
    OtcAdvice,
    RedFlag,
)
from worker.nlp import (
    ExtractionResult,
    NlpError,
    RelevanceResult,
    SegmentRelevance,
    parse_llm_json,
)
from worker.pipeline import run_assessment_stage, run_nlp_stage, run_speech_stage
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


RELEVANCE = RelevanceResult(
    patient_speaker="0",
    segments=[SegmentRelevance(index=0, relevance=0.9), SegmentRelevance(index=1, relevance=0.2)],
)
EXTRACTION = ExtractionResult(symptoms=["fever"], duration_days=2, severity="mild")


class StubNlp:
    def __init__(
        self,
        relevance: RelevanceResult = RELEVANCE,
        extraction: ExtractionResult = EXTRACTION,
        filter_error: Exception | None = None,
        extract_error: Exception | None = None,
    ):
        self.relevance = relevance
        self.extraction = extraction
        self.filter_error = filter_error
        self.extract_error = extract_error
        self.extract_calls: list[str] = []

    def weigh_relevance(self, lines):
        if self.filter_error:
            raise self.filter_error
        return self.relevance

    def extract(self, text: str) -> ExtractionResult:
        self.extract_calls.append(text)
        if self.extract_error:
            raise self.extract_error
        return self.extraction


def make_filtering_recording(session: Session) -> Recording:
    recording = Recording(
        id=uuid.uuid4(),
        audio_key="recordings/test.m4a",
        duration_ms=4000,
        locale="hi-IN",
        status=RecordingStatus.FILTERING,
    )
    session.add(recording)
    session.add_all(
        [
            TranscriptSegment(
                recording_id=recording.id,
                speaker_label="0",
                segment_index=0,
                text="मुझे बुखार है।",
                start_ms=0,
                end_ms=1500,
            ),
            TranscriptSegment(
                recording_id=recording.id,
                speaker_label="1",
                segment_index=1,
                text="कब से?",
                start_ms=1700,
                end_ms=2400,
            ),
        ]
    )
    session.commit()
    return recording


def run_nlp(session_factory, nlp, recording_id, enqueued, threshold=0.35):
    run_nlp_stage(
        str(recording_id),
        session_factory=session_factory,
        nlp=nlp,
        relevance_threshold=threshold,
        enqueue_next=enqueued.append,
    )


def test_nlp_stage_persists_weights_extraction_and_advances_status(sync_session_factory):
    nlp = StubNlp()
    enqueued: list[str] = []
    with sync_session_factory() as session:
        recording = make_filtering_recording(session)

    run_nlp(sync_session_factory, nlp, recording.id, enqueued)

    assert enqueued == [str(recording.id)]
    assert nlp.extract_calls == ["मुझे बुखार है।"]  # discarded segment excluded
    with sync_session_factory() as session:
        assert session.get(Recording, recording.id).status == RecordingStatus.ASSESSING
        rows = session.scalars(
            select(TranscriptSegment).order_by(TranscriptSegment.segment_index)
        ).all()
        assert [(r.relevance_weight, r.discarded) for r in rows] == [(0.9, False), (0.2, True)]
        extraction = session.scalars(select(Extraction)).one()
        assert extraction.symptoms == {"items": ["fever"]}
        assert extraction.duration_days == 2
        assert extraction.severity == "mild"
        assert extraction.age is None
        assert set(extraction.raw_llm_output) == {"relevance", "extraction"}


def test_nlp_stage_filter_failure_marks_failed(sync_session_factory):
    nlp = StubNlp(filter_error=RuntimeError("LLM down"))
    enqueued: list[str] = []
    with sync_session_factory() as session:
        recording = make_filtering_recording(session)

    with pytest.raises(RuntimeError):
        run_nlp(sync_session_factory, nlp, recording.id, enqueued)

    assert enqueued == []
    with sync_session_factory() as session:
        row = session.get(Recording, recording.id)
        assert row.status == RecordingStatus.FAILED
        assert row.failure_stage == "filtering"


def test_nlp_stage_extract_failure_marks_failed(sync_session_factory):
    nlp = StubNlp(extract_error=NlpError("bad JSON"))
    enqueued: list[str] = []
    with sync_session_factory() as session:
        recording = make_filtering_recording(session)

    with pytest.raises(NlpError):
        run_nlp(sync_session_factory, nlp, recording.id, enqueued)

    assert enqueued == []
    with sync_session_factory() as session:
        row = session.get(Recording, recording.id)
        assert row.status == RecordingStatus.FAILED
        assert row.failure_stage == "extracting"


def test_nlp_stage_retry_replaces_extraction(sync_session_factory):
    enqueued: list[str] = []
    with sync_session_factory() as session:
        recording = make_filtering_recording(session)

    run_nlp(sync_session_factory, StubNlp(), recording.id, enqueued)
    run_nlp(
        sync_session_factory,
        StubNlp(extraction=ExtractionResult(symptoms=["cough"])),
        recording.id,
        enqueued,
    )

    with sync_session_factory() as session:
        extraction = session.scalars(select(Extraction)).one()
        assert extraction.symptoms == {"items": ["cough"]}


def test_nlp_stage_missing_recording_is_dropped(sync_session_factory):
    enqueued: list[str] = []
    run_nlp(sync_session_factory, StubNlp(), uuid.uuid4(), enqueued)
    assert enqueued == []


def test_nlp_stage_without_segments_fails_in_filtering(sync_session_factory):
    enqueued: list[str] = []
    with sync_session_factory() as session:
        recording = make_recording(session)  # no transcript segments

    with pytest.raises(NlpError):
        run_nlp(sync_session_factory, StubNlp(), recording.id, enqueued)

    with sync_session_factory() as session:
        row = session.get(Recording, recording.id)
        assert row.status == RecordingStatus.FAILED
        assert row.failure_stage == "filtering"


def test_nlp_stage_all_discarded_falls_back_to_full_transcript(sync_session_factory):
    low = RelevanceResult(
        segments=[
            SegmentRelevance(index=0, relevance=0.1),
            SegmentRelevance(index=1, relevance=0.1),
        ]
    )
    nlp = StubNlp(relevance=low)
    enqueued: list[str] = []
    with sync_session_factory() as session:
        recording = make_filtering_recording(session)

    run_nlp(sync_session_factory, nlp, recording.id, enqueued)

    assert nlp.extract_calls == ["मुझे बुखार है।\nकब से?"]
    with sync_session_factory() as session:
        assert session.get(Recording, recording.id).status == RecordingStatus.ASSESSING


def test_parse_llm_json_tolerates_fences_and_prose():
    reply = 'Here you go:\n```json\n{"symptoms": ["fever"], "age": null}\n```'
    assert parse_llm_json(reply) == {"symptoms": ["fever"], "age": None}


def test_parse_llm_json_rejects_non_json():
    with pytest.raises(NlpError):
        parse_llm_json("sorry, I cannot help with that")


ASSESSMENT = AssessmentResult(
    conditions=[ConditionHypothesis(name="Viral fever", confidence_percent=70, rationale="ok")],
    red_flags=[RedFlag(description="fever > 3 days", action="refer to doctor")],
    otc_guidance=[OtcAdvice(medicine="Paracetamol 500 mg", dosage="1 tab q6h", note="after food")],
    raw={"conditions": []},
)


class StubAssessor:
    model_id = "stub-model"

    def __init__(self, result: AssessmentResult = ASSESSMENT, error: Exception | None = None):
        self.result = result
        self.error = error
        self.cases: list[CaseSummary] = []

    def assess(self, case: CaseSummary) -> AssessmentResult:
        self.cases.append(case)
        if self.error:
            raise self.error
        return self.result


def make_assessing_recording(session: Session) -> Recording:
    recording = make_filtering_recording(session)
    recording.status = RecordingStatus.ASSESSING
    session.get(
        TranscriptSegment,
        session.scalars(
            select(TranscriptSegment.id)
            .where(TranscriptSegment.recording_id == recording.id)
            .where(TranscriptSegment.segment_index == 1)
        ).one(),
    ).discarded = True
    session.add(
        Extraction(
            recording_id=recording.id,
            symptoms={"items": ["fever"]},
            age=35,
            duration_days=2,
        )
    )
    session.commit()
    return recording


def run_assess(session_factory, assessor, recording_id):
    run_assessment_stage(
        str(recording_id),
        session_factory=session_factory,
        assessor=assessor,
    )


def test_assessment_stage_persists_assessment_and_completes(sync_session_factory):
    assessor = StubAssessor()
    with sync_session_factory() as session:
        recording = make_assessing_recording(session)

    run_assess(sync_session_factory, assessor, recording.id)

    case = assessor.cases[0]
    assert case.symptoms == ["fever"]
    assert case.age == 35
    assert case.transcript == "मुझे बुखार है।"  # discarded segment excluded
    with sync_session_factory() as session:
        assert session.get(Recording, recording.id).status == RecordingStatus.COMPLETED
        row = session.scalars(select(Assessment)).one()
        assert row.conditions == [
            {"name": "Viral fever", "confidence_percent": 70, "rationale": "ok"}
        ]
        assert row.red_flags == [{"description": "fever > 3 days", "action": "refer to doctor"}]
        assert row.otc_guidance[0]["medicine"] == "Paracetamol 500 mg"
        assert row.model_id == "stub-model"
        assert row.prompt_version == PROMPT_VERSION
        assert row.raw_llm_output == {"conditions": []}


def test_assessment_stage_failure_marks_failed(sync_session_factory):
    assessor = StubAssessor(error=AssessmentError("LLM down"))
    with sync_session_factory() as session:
        recording = make_assessing_recording(session)

    with pytest.raises(AssessmentError):
        run_assess(sync_session_factory, assessor, recording.id)

    with sync_session_factory() as session:
        row = session.get(Recording, recording.id)
        assert row.status == RecordingStatus.FAILED
        assert row.failure_stage == "assessing"
        assert session.scalars(select(Assessment)).one_or_none() is None


def test_assessment_stage_retry_replaces_assessment(sync_session_factory):
    with sync_session_factory() as session:
        recording = make_assessing_recording(session)

    run_assess(sync_session_factory, StubAssessor(), recording.id)
    replacement = AssessmentResult(
        conditions=[ConditionHypothesis(name="Common cold", confidence_percent=50)]
    )
    run_assess(sync_session_factory, StubAssessor(result=replacement), recording.id)

    with sync_session_factory() as session:
        row = session.scalars(select(Assessment)).one()
        assert row.conditions[0]["name"] == "Common cold"


def test_assessment_stage_missing_recording_is_dropped(sync_session_factory):
    assessor = StubAssessor()
    run_assess(sync_session_factory, assessor, uuid.uuid4())
    assert assessor.cases == []


def test_assessment_stage_without_extraction_fails(sync_session_factory):
    with sync_session_factory() as session:
        recording = make_filtering_recording(session)  # no extraction row

    with pytest.raises(AssessmentError):
        run_assess(sync_session_factory, StubAssessor(), recording.id)

    with sync_session_factory() as session:
        row = session.get(Recording, recording.id)
        assert row.status == RecordingStatus.FAILED
        assert row.failure_stage == "assessing"
