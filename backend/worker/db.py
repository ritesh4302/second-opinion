"""Sync SQLAlchemy session for Celery tasks (the API side stays async)."""

from functools import lru_cache

from sqlalchemy import create_engine
from sqlalchemy.orm import Session, sessionmaker

from app.settings import get_settings


def _sync_url(url: str) -> str:
    # Settings hold the async URL; workers use sync drivers on the same DB.
    return url.replace("+asyncpg", "+psycopg").replace("+aiosqlite", "")


@lru_cache
def get_session_factory() -> sessionmaker[Session]:
    engine = create_engine(_sync_url(get_settings().database_url))
    return sessionmaker(engine, expire_on_commit=False)
