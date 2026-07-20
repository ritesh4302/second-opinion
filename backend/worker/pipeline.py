"""Pipeline stages: speech (download, diarize+transcribe, persist segments),
NLP (relevance filter + structured extraction), and assessment (triage output).

Dependencies are injected so tests run the stages with fakes; worker/main.py
wires the real S3 storage, Sarvam transcriber/NLP/assessor, and Celery enqueue.
"""

import time
import uuid
from collections.abc import Callable

import structlog
from sqlalchemy import delete, select
from sqlalchemy.orm import Session, sessionmaker

from app.models import Assessment, Extraction, Recording, RecordingStatus, TranscriptSegment
from app.storage import ObjectStorage
from worker.assessment import PROMPT_VERSION, AssessmentError, Assessor, CaseSummary
from worker.nlp import NlpError, NlpModel, TranscriptLine
from worker.transcription import Transcriber

# Structured stage events (stage, duration_ms, outcome) are the log-based
# metrics baseline (docs/BACKEND.md §7); recording_id also arrives via the
# task-level contextvars binding in worker/main.py.
logger = structlog.get_logger(__name__)

SPEECH_STAGE = "transcribing"
FILTER_STAGE = "filtering"
EXTRACT_STAGE = "extracting"
ASSESS_STAGE = "assessing"


def _elapsed_ms(start: float) -> float:
    return round((time.perf_counter() - start) * 1000, 1)


def run_speech_stage(
    recording_id: str,
    *,
    session_factory: sessionmaker[Session],
    storage: ObjectStorage,
    transcriber: Transcriber,
    enqueue_next: Callable[[str], None],
) -> None:
    rid = uuid.UUID(recording_id)
    start = time.perf_counter()
    with session_factory() as session:
        recording = session.get(Recording, rid)
        if recording is None:
            logger.warning("recording_missing", recording_id=recording_id)
            return

        # Sarvam bundles diarization with ASR, so both map to one stage.
        recording.status = RecordingStatus.TRANSCRIBING
        session.commit()

        try:
            audio = storage.get(recording.audio_key)
            filename = recording.audio_key.rsplit("/", 1)[-1]
            segments = transcriber.transcribe(audio, filename, recording.locale)

            # Idempotent on retry: replace any segments from a previous attempt.
            session.execute(delete(TranscriptSegment).where(TranscriptSegment.recording_id == rid))
            session.add_all(
                TranscriptSegment(
                    recording_id=rid,
                    speaker_label=segment.speaker_label,
                    segment_index=index,
                    text=segment.text,
                    start_ms=segment.start_ms,
                    end_ms=segment.end_ms,
                )
                for index, segment in enumerate(segments)
            )
            recording.status = RecordingStatus.FILTERING
            session.commit()
        except Exception:
            session.rollback()
            recording.status = RecordingStatus.FAILED
            recording.failure_stage = SPEECH_STAGE
            session.commit()
            # No health data in logs: IDs only (docs/BACKEND.md §8)
            logger.exception(
                "stage_failed",
                stage=SPEECH_STAGE,
                recording_id=recording_id,
                duration_ms=_elapsed_ms(start),
            )
            raise

    logger.info(
        "stage_done",
        stage=SPEECH_STAGE,
        recording_id=recording_id,
        duration_ms=_elapsed_ms(start),
        segments=len(segments),
    )
    enqueue_next(recording_id)


def run_nlp_stage(
    recording_id: str,
    *,
    session_factory: sessionmaker[Session],
    nlp: NlpModel,
    relevance_threshold: float,
    enqueue_next: Callable[[str], None],
) -> None:
    rid = uuid.UUID(recording_id)
    start = time.perf_counter()
    with session_factory() as session:
        recording = session.get(Recording, rid)
        if recording is None:
            logger.warning("recording_missing", recording_id=recording_id)
            return

        stage = FILTER_STAGE
        try:
            segments = session.scalars(
                select(TranscriptSegment)
                .where(TranscriptSegment.recording_id == rid)
                .order_by(TranscriptSegment.segment_index)
            ).all()
            if not segments:
                raise NlpError("no transcript segments to filter")

            lines = [TranscriptLine(s.segment_index, s.speaker_label, s.text) for s in segments]
            relevance = nlp.weigh_relevance(lines)
            weights = {r.index: r.relevance for r in relevance.segments}
            for segment in segments:
                weight = weights.get(segment.segment_index)
                segment.relevance_weight = weight
                segment.discarded = weight is not None and weight < relevance_threshold
            recording.status = RecordingStatus.EXTRACTING
            session.commit()

            stage = EXTRACT_STAGE
            kept = [s for s in segments if not s.discarded]
            if not kept:
                # Better to extract from everything than to lose the recording.
                logger.warning("all_segments_discarded", recording_id=recording_id)
                kept = segments
            extraction = nlp.extract("\n".join(s.text for s in kept))

            # Idempotent on retry: replace any extraction from a previous attempt.
            session.execute(delete(Extraction).where(Extraction.recording_id == rid))
            session.add(
                Extraction(
                    recording_id=rid,
                    symptoms={"items": extraction.symptoms},
                    age=extraction.age,
                    gender=extraction.gender,
                    location=extraction.location,
                    duration_days=extraction.duration_days,
                    severity=extraction.severity,
                    raw_llm_output={"relevance": relevance.raw, "extraction": extraction.raw},
                )
            )
            recording.status = RecordingStatus.ASSESSING
            session.commit()
        except Exception:
            session.rollback()
            recording.status = RecordingStatus.FAILED
            recording.failure_stage = stage
            session.commit()
            # No health data in logs: IDs only (docs/BACKEND.md §8)
            logger.exception(
                "stage_failed",
                stage=stage,
                recording_id=recording_id,
                duration_ms=_elapsed_ms(start),
            )
            raise

    logger.info(
        "stage_done",
        stage=EXTRACT_STAGE,
        recording_id=recording_id,
        duration_ms=_elapsed_ms(start),
        segments_kept=len(kept),
        segments_total=len(segments),
    )
    enqueue_next(recording_id)


def run_assessment_stage(
    recording_id: str,
    *,
    session_factory: sessionmaker[Session],
    assessor: Assessor,
) -> None:
    rid = uuid.UUID(recording_id)
    start = time.perf_counter()
    with session_factory() as session:
        recording = session.get(Recording, rid)
        if recording is None:
            logger.warning("recording_missing", recording_id=recording_id)
            return

        recording.status = RecordingStatus.ASSESSING
        session.commit()

        try:
            extraction = session.scalars(
                select(Extraction).where(Extraction.recording_id == rid)
            ).one_or_none()
            if extraction is None:
                raise AssessmentError("no extraction to assess")

            kept = session.scalars(
                select(TranscriptSegment)
                .where(TranscriptSegment.recording_id == rid)
                .where(TranscriptSegment.discarded.is_(False))
                .order_by(TranscriptSegment.segment_index)
            ).all()
            case = CaseSummary(
                symptoms=list((extraction.symptoms or {}).get("items", [])),
                age=extraction.age,
                gender=extraction.gender,
                location=extraction.location,
                duration_days=extraction.duration_days,
                severity=extraction.severity,
                transcript="\n".join(s.text for s in kept),
            )
            result = assessor.assess(case)

            # Idempotent on retry: replace any assessment from a previous attempt.
            session.execute(delete(Assessment).where(Assessment.recording_id == rid))
            session.add(
                Assessment(
                    recording_id=rid,
                    conditions=[c.model_dump() for c in result.conditions],
                    red_flags=[f.model_dump() for f in result.red_flags],
                    otc_guidance=[o.model_dump() for o in result.otc_guidance],
                    model_id=assessor.model_id,
                    prompt_version=PROMPT_VERSION,
                    raw_llm_output=result.raw,
                )
            )
            recording.status = RecordingStatus.COMPLETED
            session.commit()
        except Exception:
            session.rollback()
            recording.status = RecordingStatus.FAILED
            recording.failure_stage = ASSESS_STAGE
            session.commit()
            # No health data in logs: IDs only (docs/BACKEND.md §8)
            logger.exception(
                "stage_failed",
                stage=ASSESS_STAGE,
                recording_id=recording_id,
                duration_ms=_elapsed_ms(start),
            )
            raise

    logger.info(
        "stage_done",
        stage=ASSESS_STAGE,
        recording_id=recording_id,
        duration_ms=_elapsed_ms(start),
        conditions=len(result.conditions),
        red_flags=len(result.red_flags),
    )
