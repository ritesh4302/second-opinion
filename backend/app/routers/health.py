from typing import Annotated

from fastapi import APIRouter, Depends
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession

from app.db import get_session
from app.problems import Problem

router = APIRouter(tags=["health"])

SessionDep = Annotated[AsyncSession, Depends(get_session)]


@router.get("/healthz")
async def healthz() -> dict[str, str]:
    return {"status": "ok"}


@router.get("/readyz")
async def readyz(session: SessionDep) -> dict[str, str]:
    try:
        await session.execute(text("SELECT 1"))
    except Exception as exc:
        raise Problem(503, "Not ready", "database unreachable") from exc
    return {"status": "ready"}
