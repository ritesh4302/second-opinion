import enum
import uuid
from datetime import UTC, datetime

from sqlalchemy import JSON, Boolean, DateTime, Enum, Float, ForeignKey, Integer, String, Text
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db import Base

# jsonb on Postgres, plain JSON elsewhere (SQLite in unit tests)
JsonDict = JSON().with_variant(JSONB(), "postgresql")


def _utcnow() -> datetime:
    return datetime.now(UTC)


class RecordingStatus(enum.StrEnum):
    UPLOADED = "uploaded"
    QUEUED = "queued"
    DIARIZING = "diarizing"
    TRANSCRIBING = "transcribing"
    FILTERING = "filtering"
    EXTRACTING = "extracting"
    ASSESSING = "assessing"
    COMPLETED = "completed"
    FAILED = "failed"


class FeedbackDecision(enum.StrEnum):
    ACCEPTED = "accepted"
    REJECTED = "rejected"
    OVERRIDDEN = "overridden"


class UserRole(enum.StrEnum):
    PHARMACIST = "pharmacist"
    ADMIN = "admin"


class User(Base):
    __tablename__ = "users"

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    # Firebase Auth UID (phone sign-in); the stable identity across devices
    firebase_uid: Mapped[str] = mapped_column(String(128), unique=True, index=True)
    phone_number: Mapped[str | None] = mapped_column(String(20), default=None)
    role: Mapped[UserRole] = mapped_column(
        Enum(UserRole, native_enum=False, length=20), default=UserRole.PHARMACIST
    )
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_utcnow)

    recordings: Mapped[list["Recording"]] = relationship(back_populates="owner")


class Recording(Base):
    __tablename__ = "recordings"

    # Client-generated UUID (idempotency key for uploads)
    id: Mapped[uuid.UUID] = mapped_column(primary_key=True)
    # Pharmacist who uploaded the recording (nullable for pre-auth rows)
    owner_id: Mapped[uuid.UUID | None] = mapped_column(
        ForeignKey("users.id", ondelete="SET NULL"), default=None, index=True
    )
    audio_key: Mapped[str] = mapped_column(String(255))
    duration_ms: Mapped[int] = mapped_column(Integer)
    locale: Mapped[str] = mapped_column(String(35))
    # DPDP: pharmacist attested the patient's consent before recording
    consent_confirmed: Mapped[bool] = mapped_column(Boolean, default=False)
    # Set by the retention sweep once the audio blob + transcripts are purged
    audio_purged_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), default=None)
    status: Mapped[RecordingStatus] = mapped_column(
        Enum(RecordingStatus, native_enum=False, length=20),
        default=RecordingStatus.UPLOADED,
        index=True,
    )
    failure_stage: Mapped[str | None] = mapped_column(String(20), default=None)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_utcnow)
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=_utcnow, onupdate=_utcnow
    )

    owner: Mapped["User | None"] = relationship(back_populates="recordings")
    transcripts: Mapped[list["TranscriptSegment"]] = relationship(
        back_populates="recording", cascade="all, delete-orphan"
    )
    extraction: Mapped["Extraction | None"] = relationship(
        back_populates="recording", cascade="all, delete-orphan"
    )
    assessment: Mapped["Assessment | None"] = relationship(
        back_populates="recording", cascade="all, delete-orphan"
    )


class TranscriptSegment(Base):
    __tablename__ = "transcripts"

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    recording_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("recordings.id", ondelete="CASCADE"), index=True
    )
    speaker_label: Mapped[str] = mapped_column(String(50))
    segment_index: Mapped[int] = mapped_column(Integer)
    text: Mapped[str] = mapped_column(Text)
    start_ms: Mapped[int] = mapped_column(Integer)
    end_ms: Mapped[int] = mapped_column(Integer)
    relevance_weight: Mapped[float | None] = mapped_column(Float, default=None)
    discarded: Mapped[bool] = mapped_column(Boolean, default=False)

    recording: Mapped[Recording] = relationship(back_populates="transcripts")


class Extraction(Base):
    __tablename__ = "extractions"

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    recording_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("recordings.id", ondelete="CASCADE"), unique=True, index=True
    )
    symptoms: Mapped[dict] = mapped_column(JsonDict)
    age: Mapped[int | None] = mapped_column(Integer, default=None)
    gender: Mapped[str | None] = mapped_column(String(20), default=None)
    location: Mapped[str | None] = mapped_column(String(100), default=None)
    duration_days: Mapped[int | None] = mapped_column(Integer, default=None)
    severity: Mapped[str | None] = mapped_column(String(20), default=None)
    raw_llm_output: Mapped[dict | None] = mapped_column(JsonDict, default=None)

    recording: Mapped[Recording] = relationship(back_populates="extraction")


class Assessment(Base):
    __tablename__ = "assessments"

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    recording_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("recordings.id", ondelete="CASCADE"), unique=True, index=True
    )
    conditions: Mapped[list] = mapped_column(JsonDict)
    red_flags: Mapped[list] = mapped_column(JsonDict)
    otc_guidance: Mapped[list] = mapped_column(JsonDict)
    model_id: Mapped[str] = mapped_column(String(100))
    prompt_version: Mapped[str] = mapped_column(String(50))
    raw_llm_output: Mapped[dict | None] = mapped_column(JsonDict, default=None)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_utcnow)

    recording: Mapped[Recording] = relationship(back_populates="assessment")
    feedback: Mapped[list["Feedback"]] = relationship(
        back_populates="assessment", cascade="all, delete-orphan"
    )


class Feedback(Base):
    __tablename__ = "feedback"

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    assessment_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("assessments.id", ondelete="CASCADE"), index=True
    )
    decision: Mapped[FeedbackDecision] = mapped_column(
        Enum(FeedbackDecision, native_enum=False, length=20)
    )
    note: Mapped[str | None] = mapped_column(Text, default=None)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_utcnow)

    assessment: Mapped[Assessment] = relationship(back_populates="feedback")
