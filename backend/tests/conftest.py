from collections.abc import AsyncIterator

import pytest
from httpx import ASGITransport, AsyncClient
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine
from sqlalchemy.pool import StaticPool

from app.auth import FakeTokenVerifier, get_token_verifier
from app.db import Base, get_session
from app.main import create_app
from app.queue import get_enqueue
from app.storage import get_storage

TEST_UID = "pharm-1"
TEST_PHONE = "+919876543210"


def auth_header(uid: str = TEST_UID, phone: str = TEST_PHONE) -> dict[str, str]:
    """Bearer header accepted by FakeTokenVerifier ("fake:<uid>:<phone>")."""
    return {"Authorization": f"Bearer fake:{uid}:{phone}"}


class FakeStorage:
    def __init__(self) -> None:
        self.objects: dict[str, bytes] = {}

    async def put(self, key: str, data: bytes, content_type: str) -> None:
        self.objects[key] = data

    def get(self, key: str) -> bytes:
        return self.objects[key]


class FakeQueue:
    def __init__(self) -> None:
        self.enqueued: list[str] = []

    def __call__(self, recording_id: str) -> None:
        self.enqueued.append(recording_id)


@pytest.fixture
async def session_factory() -> AsyncIterator[async_sessionmaker[AsyncSession]]:
    engine = create_async_engine(
        "sqlite+aiosqlite://",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    yield async_sessionmaker(engine, expire_on_commit=False)
    await engine.dispose()


@pytest.fixture
def storage() -> FakeStorage:
    return FakeStorage()


@pytest.fixture
def queue() -> FakeQueue:
    return FakeQueue()


@pytest.fixture
async def client(
    session_factory: async_sessionmaker[AsyncSession],
    storage: FakeStorage,
    queue: FakeQueue,
) -> AsyncIterator[AsyncClient]:
    app = create_app()

    async def override_get_session() -> AsyncIterator[AsyncSession]:
        async with session_factory() as session:
            yield session

    app.dependency_overrides[get_session] = override_get_session
    app.dependency_overrides[get_storage] = lambda: storage
    app.dependency_overrides[get_enqueue] = lambda: queue
    app.dependency_overrides[get_token_verifier] = FakeTokenVerifier

    transport = ASGITransport(app=app)
    async with AsyncClient(
        transport=transport, base_url="http://test", headers=auth_header()
    ) as client:
        yield client
