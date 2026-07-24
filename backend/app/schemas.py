import uuid
from datetime import datetime

from pydantic import BaseModel, ConfigDict

from app.models import FeedbackDecision, RecordingStatus, UserRole


class UserOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    email: str | None
    display_name: str | None
    role: UserRole
    created_at: datetime


class RecordingOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    status: RecordingStatus
    failure_stage: str | None
    duration_ms: int
    locale: str
    created_at: datetime
    updated_at: datetime


class AssessmentOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    recording_id: uuid.UUID
    # Short recap of the extracted symptoms so the app can show what was heard
    symptom_summary: str = ""
    conditions: list
    red_flags: list
    otc_guidance: list
    model_id: str
    prompt_version: str
    created_at: datetime


class FeedbackIn(BaseModel):
    decision: FeedbackDecision
    note: str | None = None


class FeedbackOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    assessment_id: uuid.UUID
    decision: FeedbackDecision
    note: str | None
    created_at: datetime
