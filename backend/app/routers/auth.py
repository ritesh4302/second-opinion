from fastapi import APIRouter

from app.auth import CurrentUser
from app.models import User
from app.schemas import UserOut

router = APIRouter(prefix="/v1/auth", tags=["auth"])


@router.get("/me", response_model=UserOut)
async def me(user: CurrentUser) -> User:
    """Confirms the token is valid; auto-provisions the user on first call."""
    return user
