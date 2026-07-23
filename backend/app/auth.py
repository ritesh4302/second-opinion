"""Authentication: Firebase phone-auth ID tokens verified server-side.

The app signs the pharmacist in with Firebase Auth (phone/OTP) and sends the
resulting ID token as a Bearer token. The backend verifies the RS256 signature
against Google's public JWKS and auto-provisions a `users` row on first sight
(same port/adapter pattern as the speech/nlp/assessment providers).
"""

from dataclasses import dataclass
from functools import lru_cache
from typing import Annotated, Protocol

import jwt
from fastapi import Depends
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.db import get_session
from app.models import User, UserRole
from app.problems import Problem
from app.settings import get_settings

FIREBASE_JWKS_URL = (
    "https://www.googleapis.com/service_accounts/v1/jwk/"
    "securetoken@system.gserviceaccount.com"
)


class InvalidToken(Exception):
    pass


@dataclass(frozen=True)
class VerifiedIdentity:
    uid: str
    phone_number: str | None


class TokenVerifier(Protocol):
    def verify(self, token: str) -> VerifiedIdentity: ...


class FirebaseTokenVerifier:
    """Verifies Firebase ID tokens against Google's JWKS (needs pyjwt[crypto])."""

    def __init__(self, project_id: str) -> None:
        self._project_id = project_id
        self._jwk_client = jwt.PyJWKClient(FIREBASE_JWKS_URL)

    def verify(self, token: str) -> VerifiedIdentity:
        try:
            key = self._jwk_client.get_signing_key_from_jwt(token)
            claims = jwt.decode(
                token,
                key.key,
                algorithms=["RS256"],
                audience=self._project_id,
                issuer=f"https://securetoken.google.com/{self._project_id}",
            )
        except jwt.PyJWTError as exc:
            raise InvalidToken(str(exc)) from exc
        uid = claims.get("sub")
        if not uid:
            raise InvalidToken("token has no subject claim")
        return VerifiedIdentity(uid=uid, phone_number=claims.get("phone_number"))


class FakeTokenVerifier:
    """Dev/test verifier: accepts "fake:<uid>[:<phone>]" bearer tokens."""

    def verify(self, token: str) -> VerifiedIdentity:
        parts = token.split(":", 2)
        if parts[0] != "fake" or len(parts) < 2 or not parts[1]:
            raise InvalidToken('fake verifier expects "fake:<uid>[:<phone>]"')
        return VerifiedIdentity(uid=parts[1], phone_number=parts[2] if len(parts) == 3 else None)


@lru_cache
def get_token_verifier() -> TokenVerifier:
    settings = get_settings()
    if settings.auth_provider == "fake":
        return FakeTokenVerifier()
    return FirebaseTokenVerifier(settings.firebase_project_id)


_bearer = HTTPBearer(auto_error=False)


async def get_current_user(
    credentials: Annotated[HTTPAuthorizationCredentials | None, Depends(_bearer)],
    session: Annotated[AsyncSession, Depends(get_session)],
    verifier: Annotated[TokenVerifier, Depends(get_token_verifier)],
) -> User:
    if credentials is None:
        raise Problem(401, "Unauthorized", "missing bearer token")
    try:
        identity = verifier.verify(credentials.credentials)
    except InvalidToken as exc:
        raise Problem(401, "Unauthorized", f"invalid token: {exc}") from exc

    result = await session.execute(select(User).where(User.firebase_uid == identity.uid))
    user = result.scalar_one_or_none()
    if user is None:
        # Auto-provision on first verified token; everyone starts as pharmacist
        user = User(
            firebase_uid=identity.uid,
            phone_number=identity.phone_number,
            role=UserRole.PHARMACIST,
        )
        session.add(user)
        await session.commit()
    return user


CurrentUser = Annotated[User, Depends(get_current_user)]
