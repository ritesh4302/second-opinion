from collections.abc import AsyncIterator

from sqlalchemy.ext.asyncio import (
    AsyncEngine,
    AsyncSession,
    async_sessionmaker,
    create_async_engine,
)
from sqlalchemy.orm import DeclarativeBase

from app.settings import get_settings


class Base(DeclarativeBase):
    pass


_engine: AsyncEngine | None = None
_session_factory: async_sessionmaker[AsyncSession] | None = None


def _get_session_factory() -> async_sessionmaker[AsyncSession]:
    # Lazy so importing the app never connects; tests override get_session entirely.
    global _engine, _session_factory
    if _session_factory is None:
        _engine = create_async_engine(get_settings().database_url)
        _session_factory = async_sessionmaker(_engine, expire_on_commit=False)
    return _session_factory


async def get_session() -> AsyncIterator[AsyncSession]:
    async with _get_session_factory()() as session:
        yield session
