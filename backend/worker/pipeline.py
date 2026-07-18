"""Speech stage: download audio, diarize+transcribe, persist segments.

Dependencies are injected so tests run the stage with fakes; worker/main.py
wires the real S3 storage, Sarvam transcriber, and Celery enqueue.
"""

import logging
import uuid
from collections.abc import Callable

from sqlalchemy import delete
from sqlalchemy.orm import Session, sessionmaker

from app.models import Recording, RecordingStatus, TranscriptSegment
from app.storage import ObjectStorage
from worker.transcription import Transcriber

logger = logging.getLogger(__name__)

SPEECH_STAGE = "transcribing"


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
