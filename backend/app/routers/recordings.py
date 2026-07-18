import uuid
from collections.abc import Callable
from typing import Annotated

from fastapi import APIRouter, Depends, Form, Response, UploadFile, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.db import get_session
from app.models import Assessment, Recording, RecordingStatus
from app.problems import Problem
from app.queue import get_enqueue
from app.schemas import AssessmentOut, RecordingOut
from app.settings import get_settings
from app.storage import ObjectStorage, get_storage

router = APIRouter(prefix="/v1/recordings", tags=["recordings"])

SessionDep = Annotated[AsyncSession, Depends(get_session)]
StorageDep = Annotated[ObjectStorage, Depends(get_storage)]
EnqueueDep = Annotated[Callable[[str], None], Depends(get_enqueue)]


@router.post("", status_code=status.HTTP_202_ACCEPTED, response_model=RecordingOut)
async def upload_recording(
    response: Response,
    audio: UploadFile,
    id: Annotated[uuid.UUID, Form()],
    duration_ms: Annotated[int, Form(ge=0)],
    locale: Annotated[str, Form(min_length=2, max_length=35)],
    session: SessionDep,
    storage: StorageDep,
    enqueue: EnqueueDep,
) -> Recording:
    # Idempotency: the client-generated UUID is the primary key (BACKEND.md §3)
    existing = await session.get(Recording, id)
    if existing is not None:
        response.status_code = status.HTTP_200_OK
        return existing

    data = await audio.read()
    if len(data) == 0:
        raise Problem(422, "Invalid upload", "audio file is empty")
    if len(data) > get_settings().max_upload_bytes:
        raise Problem(413, "Payload too large", "audio file exceeds the upload limit")

    audio_key = f"recordings/{id}.m4a"
    await storage.put(audio_key, data, audio.content_type or "audio/mp4")

    recording = Recording(
        id=id,
        audio_key=audio_key,
        duration_ms=duration_ms,
        locale=locale,
        status=RecordingStatus.QUEUED,
    )
    session.add(recording)
    await session.commit()

    enqueue(str(id))
    return recording


@router.get("/{recording_id}", response_model=RecordingOut)
async def get_recording(
    recording_id: uuid.UUID,
    session: SessionDep,
) -> Recording:
    recording = await session.get(Recording, recording_id)
    if recording is None:
        raise Problem(404, "Not found", f"recording {recording_id} does not exist")
    return recording


@router.get("/{recording_id}/assessment", response_model=AssessmentOut)
async def get_assessment(
    recording_id: uuid.UUID,
    session: SessionDep,
) -> Assessment:
    recording = await session.get(Recording, recording_id)
    if recording is None:
        raise Problem(404, "Not found", f"recording {recording_id} does not exist")
    result = await session.execute(
        select(Assessment).where(Assessment.recording_id == recording_id)
    )
    assessment = result.scalar_one_or_none()
    if assessment is None:
        raise Problem(
            404, "Assessment not ready", f"recording {recording_id} has no assessment yet"
        )
    return assessment
