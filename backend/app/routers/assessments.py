import uuid
from typing import Annotated

from fastapi import APIRouter, Depends, status
from sqlalchemy.ext.asyncio import AsyncSession

from app.auth import CurrentUser
from app.db import get_session
from app.models import Assessment, Feedback, Recording
from app.problems import Problem
from app.schemas import FeedbackIn, FeedbackOut

router = APIRouter(prefix="/v1/assessments", tags=["assessments"])

SessionDep = Annotated[AsyncSession, Depends(get_session)]


@router.post(
    "/{assessment_id}/feedback",
    status_code=status.HTTP_201_CREATED,
    response_model=FeedbackOut,
)
async def submit_feedback(
    assessment_id: uuid.UUID,
    body: FeedbackIn,
    session: SessionDep,
    user: CurrentUser,
) -> Feedback:
    assessment = await session.get(Assessment, assessment_id)
    if assessment is None:
        raise Problem(404, "Not found", f"assessment {assessment_id} does not exist")
    recording = await session.get(Recording, assessment.recording_id)
    # Another user's assessment looks like 404 (no existence leak)
    if recording is None or recording.owner_id != user.id:
        raise Problem(404, "Not found", f"assessment {assessment_id} does not exist")

    feedback = Feedback(assessment_id=assessment_id, decision=body.decision, note=body.note)
    session.add(feedback)
    await session.commit()
    return feedback
