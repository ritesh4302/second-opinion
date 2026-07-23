import asyncio
import uuid
from collections.abc import Callable
from typing import Annotated

from fastapi import APIRouter, Depends, Form, Response, UploadFile, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.auth import CurrentUser
from app.db import get_session
from app.models import Assessment, Extraction, Recording, RecordingStatus
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
    consent_confirmed: Annotated[bool, Form()],
    session: SessionDep,
    storage: StorageDep,
    enqueue: EnqueueDep,
    user: CurrentUser,
) -> Recording:
    # DPDP: no audio enters the system without the patient's attested consent
    if not consent_confirmed:
        raise Problem(422, "Consent required", "patient consent must be confirmed before upload")

    # Idempotency: the client-generated UUID is the primary key (BACKEND.md §3)
    existing = await session.get(Recording, id)
    if existing is not None:
        if existing.owner_id != user.id:
            raise Problem(409, "Conflict", f"recording {id} belongs to another user")
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
        owner_id=user.id,
        audio_key=audio_key,
        duration_ms=duration_ms,
        locale=locale,
        consent_confirmed=consent_confirmed,
        status=RecordingStatus.QUEUED,
    )
    session.add(recording)
    await session.commit()

    enqueue(str(id))
    return recording


@router.delete("/{recording_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_recording(
    recording_id: uuid.UUID,
    session: SessionDep,
    storage: StorageDep,
    user: CurrentUser,
) -> None:
    """DPDP erasure: removes the audio blob and every derived row (cascade)."""
    recording = await session.get(Recording, recording_id)
    # Another user's recording looks like 404 (no existence leak)
    if recording is None or recording.owner_id != user.id:
        raise Problem(404, "Not found", f"recording {recording_id} does not exist")

    if recording.audio_purged_at is None:
        await asyncio.to_thread(storage.delete, recording.audio_key)
    await session.delete(recording)
    await session.commit()


@router.get("/{recording_id}", response_model=RecordingOut)
async def get_recording(
    recording_id: uuid.UUID,
    session: SessionDep,
    user: CurrentUser,
) -> Recording:
    recording = await session.get(Recording, recording_id)
    # Another user's recording looks like 404 (no existence leak)
    if recording is None or recording.owner_id != user.id:
        raise Problem(404, "Not found", f"recording {recording_id} does not exist")
    return recording


@router.get("/{recording_id}/assessment", response_model=AssessmentOut)
async def get_assessment(
    recording_id: uuid.UUID,
    session: SessionDep,
    user: CurrentUser,
) -> AssessmentOut:
    recording = await session.get(Recording, recording_id)
    if recording is None or recording.owner_id != user.id:
        raise Problem(404, "Not found", f"recording {recording_id} does not exist")
    result = await session.execute(
        select(Assessment).where(Assessment.recording_id == recording_id)
    )
    assessment = result.scalar_one_or_none()
    if assessment is None:
        raise Problem(
            404, "Assessment not ready", f"recording {recording_id} has no assessment yet"
        )
    extraction = (
        await session.execute(select(Extraction).where(Extraction.recording_id == recording_id))
    ).scalar_one_or_none()
    symptoms = extraction.symptoms.get("items", []) if extraction is not None else []
    out = AssessmentOut.model_validate(assessment)
    out.symptom_summary = ", ".join(symptoms)
    return out
