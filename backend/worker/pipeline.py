"""Pipeline stages: speech (download, diarize+transcribe, persist segments)
and NLP (relevance filter + structured extraction).

Dependencies are injected so tests run the stages with fakes; worker/main.py
wires the real S3 storage, Sarvam transcriber/NLP model, and Celery enqueue.
"""

import logging
import uuid
from collections.abc import Callable

from sqlalchemy import delete, select
from sqlalchemy.orm import Session, sessionmaker

from app.models import Extraction, Recording, RecordingStatus, TranscriptSegment
from app.storage import ObjectStorage
from worker.nlp import NlpError, NlpModel, TranscriptLine
from worker.transcription import Transcriber

logger = logging.getLogger(__name__)

SPEECH_STAGE = "transcribing"
FILTER_STAGE = "filtering"
EXTRACT_STAGE = "extracting"


def run_speech_stage(
    recording_id: str,
    *,
    session_factory: sessionmaker[Session],
    storage: ObjectStorage,
    transcriber: Transcriber,
    enqueue_next: Callable[[str], None],
) -> None:
    rid = uuid.UUID(recording_id)
    with session_factory() as session:
        recording = session.get(Recording, rid)
        if recording is None:
            logger.warning("recording %s not found; dropping task", recording_id)
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
            logger.exception("speech stage failed for recording %s", recording_id)
            raise

    logger.info("speech stage done for recording %s: %d segments", recording_id, len(segments))
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
    with session_factory() as session:
        recording = session.get(Recording, rid)
        if recording is None:
            logger.warning("recording %s not found; dropping task", recording_id)
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
                logger.warning(
                    "all segments discarded for recording %s; extracting from full transcript",
                    recording_id,
                )
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
            logger.exception("NLP stage (%s) failed for recording %s", stage, recording_id)
            raise

    logger.info(
        "NLP stage done for recording %s: %d/%d segments kept",
        recording_id,
        len(kept),
        len(segments),
    )
    enqueue_next(recording_id)
