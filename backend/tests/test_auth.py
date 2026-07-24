import uuid

from httpx import AsyncClient

from tests.conftest import TEST_EMAIL, FakeStorage, auth_header
from tests.test_assessments import seed_completed_recording
from tests.test_recordings import upload_payload


async def test_missing_token_is_401_problem(client: AsyncClient) -> None:
    del client.headers["Authorization"]
    response = await client.get(f"/v1/recordings/{uuid.uuid4()}")
    assert response.status_code == 401
    assert response.headers["content-type"] == "application/problem+json"
    assert response.json()["title"] == "Unauthorized"


async def test_invalid_token_is_401(client: AsyncClient) -> None:
    response = await client.get(
        f"/v1/recordings/{uuid.uuid4()}",
        headers={"Authorization": "Bearer not-a-valid-token"},
    )
    assert response.status_code == 401


async def test_health_endpoints_stay_open(client: AsyncClient) -> None:
    del client.headers["Authorization"]
    response = await client.get("/healthz")
    assert response.status_code == 200


async def test_me_auto_provisions_pharmacist(client: AsyncClient) -> None:
    first = await client.get("/v1/auth/me")
    assert first.status_code == 200
    body = first.json()
    assert body["email"] == TEST_EMAIL
    assert body["role"] == "pharmacist"

    # Second call finds the same user instead of provisioning a duplicate
    second = await client.get("/v1/auth/me")
    assert second.json()["id"] == body["id"]


async def test_recording_is_scoped_to_owner(client: AsyncClient) -> None:
    recording_id = uuid.uuid4()
    await client.post("/v1/recordings", **upload_payload(recording_id))

    response = await client.get(
        f"/v1/recordings/{recording_id}", headers=auth_header(uid="pharm-2")
    )
    assert response.status_code == 404


async def test_delete_is_scoped_to_owner(client: AsyncClient, storage: FakeStorage) -> None:
    recording_id = uuid.uuid4()
    await client.post("/v1/recordings", **upload_payload(recording_id))

    response = await client.delete(
        f"/v1/recordings/{recording_id}", headers=auth_header(uid="pharm-2")
    )
    assert response.status_code == 404
    # The owner's audio is untouched
    assert f"recordings/{recording_id}.m4a" in storage.objects


async def test_reupload_by_other_user_is_conflict(client: AsyncClient) -> None:
    recording_id = uuid.uuid4()
    await client.post("/v1/recordings", **upload_payload(recording_id))

    response = await client.post(
        "/v1/recordings", **upload_payload(recording_id), headers=auth_header(uid="pharm-2")
    )
    assert response.status_code == 409
    assert response.headers["content-type"] == "application/problem+json"


async def test_assessment_is_scoped_to_owner(client: AsyncClient, session_factory) -> None:
    recording_id, _ = await seed_completed_recording(session_factory)

    response = await client.get(
        f"/v1/recordings/{recording_id}/assessment", headers=auth_header(uid="pharm-2")
    )
    assert response.status_code == 404


async def test_feedback_is_scoped_to_owner(client: AsyncClient, session_factory) -> None:
    _, assessment_id = await seed_completed_recording(session_factory)

    response = await client.post(
        f"/v1/assessments/{assessment_id}/feedback",
        json={"decision": "accepted"},
        headers=auth_header(uid="pharm-2"),
    )
    assert response.status_code == 404
