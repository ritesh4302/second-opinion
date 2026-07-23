"""DPDP retention sweep: purges raw personal data past the retention window.

The voice recording and its transcript are the raw personal data; they are
only needed while the case is being processed and reviewed. Derived rows
(extraction, assessment, feedback) are kept for the pilot's quality loop.
The whole row disappears only via DELETE /v1/recordings/{id} (erasure).
"""

import logging
from datetime import UTC, datetime, timedelta

from sqlalchemy import delete, select
from sqlalchemy.orm import Session, sessionmaker

from app.models import Recording, TranscriptSegment
from app.storage import ObjectStorage

logger = logging.getLogger(__name__)


def run_retention_sweep(
    session_factory: sessionmaker[Session],
    storage: ObjectStorage,
    retention_days: int,
) -> int:
    """Purges audio blobs + transcripts of recordings older than the window.

    Returns the number of recordings purged. Idempotent: already-purged rows
    (audio_purged_at set) are skipped.
    """
    cutoff = datetime.now(UTC) - timedelta(days=retention_days)
    with session_factory() as session:
        expired = (
            session.execute(
                select(Recording).where(
                    Recording.created_at < cutoff,
                    Recording.audio_purged_at.is_(None),
                )
            )
            .scalars()
            .all()
        )
        for recording in expired:
            storage.delete(recording.audio_key)
            session.execute(
                delete(TranscriptSegment).where(TranscriptSegment.recording_id == recording.id)
            )
            recording.audio_purged_at = datetime.now(UTC)
        session.commit()

    if expired:
        # IDs only, never content (docs/BACKEND.md §8)
        logger.info("retention sweep purged audio for %d recording(s)", len(expired))
    return len(expired)
