import uuid

from httpx import AsyncClient
from sqlalchemy import select

from app.models import Assessment, Recording, TranscriptSegment
from tests.conftest import FakeQueue, FakeStorage


def upload_payload(recording_id: uuid.UUID) -> dict:
    return {
        "data": {
            "id": str(recording_id),
            "duration_ms": "4200",
            "locale": "hi-IN",
            "consent_confirmed": "true",
        },
        "files": {"audio": ("symptom.m4a", b"fake-aac-bytes", "audio/mp4")},
    }


async def test_upload_returns_202_and_stores_audio(
    client: AsyncClient, storage: FakeStorage, queue: FakeQueue
) -> None:
    recording_id = uuid.uuid4()
    response = await client.post("/v1/recordings", **upload_payload(recording_id))

    assert response.status_code == 202
    body = response.json()
    assert body["id"] == str(recording_id)
    assert body["status"] == "queued"
    assert storage.objects == {f"recordings/{recording_id}.m4a": b"fake-aac-bytes"}
    assert queue.enqueued == [str(recording_id)]


async def test_upload_is_idempotent_on_client_uuid(
    client: AsyncClient, storage: FakeStorage, queue: FakeQueue
) -> None:
    recording_id = uuid.uuid4()
    first = await client.post("/v1/recordings", **upload_payload(recording_id))
    second = await client.post("/v1/recordings", **upload_payload(recording_id))

    assert first.status_code == 202
    assert second.status_code == 200
    assert second.json()["id"] == str(recording_id)
    # No duplicate storage write or enqueue
    assert len(storage.objects) == 1
    assert queue.enqueued == [str(recording_id)]


async def test_upload_empty_file_is_rejected(client: AsyncClient, queue: FakeQueue) -> None:
    payload = upload_payload(uuid.uuid4())
    payload["files"] = {"audio": ("symptom.m4a", b"", "audio/mp4")}
    response = await client.post("/v1/recordings", **payload)

    assert response.status_code == 422
    assert response.headers["content-type"] == "application/problem+json"
    assert queue.enqueued == []


async def test_upload_oversized_file_is_rejected(
    client: AsyncClient, queue: FakeQueue, monkeypatch
) -> None:
    from app import settings as settings_module

    monkeypatch.setattr(settings_module.get_settings(), "max_upload_bytes", 10, raising=True)
    response = await client.post("/v1/recordings", **upload_payload(uuid.uuid4()))

    assert response.status_code == 413
    assert response.headers["content-type"] == "application/problem+json"
    assert queue.enqueued == []


async def test_get_recording_status(client: AsyncClient) -> None:
    recording_id = uuid.uuid4()
    await client.post("/v1/recordings", **upload_payload(recording_id))

    response = await client.get(f"/v1/recordings/{recording_id}")
    assert response.status_code == 200
    assert response.json()["status"] == "queued"


async def test_get_unknown_recording_is_404_problem(client: AsyncClient) -> None:
    response = await client.get(f"/v1/recordings/{uuid.uuid4()}")
    assert response.status_code == 404
    assert response.headers["content-type"] == "application/problem+json"
    assert response.json()["title"] == "Not found"


async def test_upload_without_consent_is_rejected(
    client: AsyncClient, storage: FakeStorage, queue: FakeQueue
) -> None:
    payload = upload_payload(uuid.uuid4())
    payload["data"]["consent_confirmed"] = "false"
    response = await client.post("/v1/recordings", **payload)

    assert response.status_code == 422
    assert response.headers["content-type"] == "application/problem+json"
    assert response.json()["title"] == "Consent required"
    assert storage.objects == {}
    assert queue.enqueued == []


async def test_upload_missing_consent_field_is_422(client: AsyncClient) -> None:
    payload = upload_payload(uuid.uuid4())
    del payload["data"]["consent_confirmed"]
    response = await client.post("/v1/recordings", **payload)

    assert response.status_code == 422


async def test_upload_persists_consent_flag(client: AsyncClient, session_factory) -> None:
    recording_id = uuid.uuid4()
    await client.post("/v1/recordings", **upload_payload(recording_id))

    async with session_factory() as session:
        recording = await session.get(Recording, recording_id)
    assert recording is not None
    assert recording.consent_confirmed is True


async def test_delete_removes_audio_and_cascades(
    client: AsyncClient, storage: FakeStorage, session_factory
) -> None:
    recording_id = uuid.uuid4()
    await client.post("/v1/recordings", **upload_payload(recording_id))
    # Seed derived pipeline rows to prove the cascade wipes them too
    async with session_factory() as session:
        session.add(
            TranscriptSegment(
                recording_id=recording_id,
                speaker_label="0",
                segment_index=0,
                text="मुझे बुखार है।",
                start_ms=0,
                end_ms=1500,
            )
        )
        session.add(
            Assessment(
                recording_id=recording_id,
                conditions=[],
                red_flags=[],
                otc_guidance=[],
                model_id="stub-model",
                prompt_version="v0",
            )
        )
        await session.commit()

    response = await client.delete(f"/v1/recordings/{recording_id}")

    assert response.status_code == 204
    assert storage.objects == {}
    async with session_factory() as session:
        assert await session.get(Recording, recording_id) is None
        transcripts = await session.execute(
            select(TranscriptSegment).where(TranscriptSegment.recording_id == recording_id)
        )
        assert transcripts.scalars().all() == []
        assessments = await session.execute(
            select(Assessment).where(Assessment.recording_id == recording_id)
        )
        assert assessments.scalars().all() == []


async def test_delete_unknown_recording_is_404(client: AsyncClient) -> None:
    response = await client.delete(f"/v1/recordings/{uuid.uuid4()}")
    assert response.status_code == 404
    assert response.headers["content-type"] == "application/problem+json"
