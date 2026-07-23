import uuid

from httpx import AsyncClient
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker

from app.models import Assessment, Extraction, Recording, RecordingStatus, User
from tests.conftest import TEST_PHONE, TEST_UID


async def seed_completed_recording(
    session_factory: async_sessionmaker[AsyncSession],
    with_extraction: bool = False,
) -> tuple[uuid.UUID, uuid.UUID]:
    recording_id = uuid.uuid4()
    assessment_id = uuid.uuid4()
    owner_id = uuid.uuid4()
    async with session_factory() as session:
        # Owner matches the default test token so the client can read it back
        session.add(User(id=owner_id, firebase_uid=TEST_UID, phone_number=TEST_PHONE))
        session.add(
            Recording(
                id=recording_id,
                owner_id=owner_id,
                audio_key=f"recordings/{recording_id}.m4a",
                duration_ms=4200,
                locale="hi-IN",
                status=RecordingStatus.COMPLETED,
            )
        )
        if with_extraction:
            session.add(
                Extraction(
                    recording_id=recording_id,
                    symptoms={"items": ["fever", "headache"]},
                )
            )
        session.add(
            Assessment(
                id=assessment_id,
                recording_id=recording_id,
                conditions=[{"name": "viral URI", "confidence": 0.72}],
                red_flags=[],
                otc_guidance=[{"category": "antipyretic"}],
                model_id="stub-model",
                prompt_version="v0",
            )
        )
        await session.commit()
    return recording_id, assessment_id


async def test_get_assessment_when_completed(client: AsyncClient, session_factory) -> None:
    recording_id, assessment_id = await seed_completed_recording(session_factory)

    response = await client.get(f"/v1/recordings/{recording_id}/assessment")
    assert response.status_code == 200
    body = response.json()
    assert body["id"] == str(assessment_id)
    assert body["conditions"] == [{"name": "viral URI", "confidence": 0.72}]
    assert body["symptom_summary"] == ""


async def test_get_assessment_includes_symptom_summary(
    client: AsyncClient, session_factory
) -> None:
    recording_id, _ = await seed_completed_recording(session_factory, with_extraction=True)

    response = await client.get(f"/v1/recordings/{recording_id}/assessment")
    assert response.status_code == 200
    assert response.json()["symptom_summary"] == "fever, headache"


async def test_get_assessment_before_ready_is_404(client: AsyncClient) -> None:
    recording_id = uuid.uuid4()
    await client.post(
        "/v1/recordings",
        data={"id": str(recording_id), "duration_ms": "4200", "locale": "hi-IN"},
        files={"audio": ("symptom.m4a", b"fake-aac-bytes", "audio/mp4")},
    )

    response = await client.get(f"/v1/recordings/{recording_id}/assessment")
    assert response.status_code == 404
    assert response.json()["title"] == "Assessment not ready"


async def test_submit_feedback(client: AsyncClient, session_factory) -> None:
    _, assessment_id = await seed_completed_recording(session_factory)

    response = await client.post(
        f"/v1/assessments/{assessment_id}/feedback",
        json={"decision": "overridden", "note": "suggested ORS instead"},
    )
    assert response.status_code == 201
    body = response.json()
    assert body["assessment_id"] == str(assessment_id)
    assert body["decision"] == "overridden"
    assert body["note"] == "suggested ORS instead"


async def test_feedback_for_unknown_assessment_is_404(client: AsyncClient) -> None:
    response = await client.post(
        f"/v1/assessments/{uuid.uuid4()}/feedback",
        json={"decision": "accepted"},
    )
    assert response.status_code == 404
    assert response.headers["content-type"] == "application/problem+json"
