"""Authentication: Firebase ID tokens (Google Sign-In provider) verified server-side.

The app signs the pharmacist in with Google via Firebase Auth (Credential
Manager -> Firebase Auth SDK) and sends the resulting Firebase ID token as a
Bearer token. The backend verifies the RS256 signature against Firebase's
securetoken JWKS and auto-provisions a `users` row on first sight (same
port/adapter pattern as the speech/nlp/assessment providers).
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

# Firebase's securetoken signing keys. Same keys as the x509 endpoint
# (robot/v1/metadata/x509/securetoken@...), served in JWKS form for PyJWKClient.
FIREBASE_JWKS_URL = (
    "https://www.googleapis.com/service_accounts/v1/jwk/"
    "securetoken@system.gserviceaccount.com"
)


class InvalidToken(Exception):
    pass


@dataclass(frozen=True)
class VerifiedIdentity:
    uid: str
    email: str | None
    display_name: str | None


class TokenVerifier(Protocol):
    def verify(self, token: str) -> VerifiedIdentity: ...


class FirebaseTokenVerifier:
    """Verifies Firebase ID tokens against the securetoken JWKS (needs pyjwt[crypto])."""

    def __init__(self, project_id: str) -> None:
        self._project_id = project_id
        self._issuer = f"https://securetoken.google.com/{project_id}"
        self._jwk_client = jwt.PyJWKClient(FIREBASE_JWKS_URL)

    def verify(self, token: str) -> VerifiedIdentity:
        try:
            key = self._jwk_client.get_signing_key_from_jwt(token)
            claims = jwt.decode(
                token,
                key.key,
                algorithms=["RS256"],
                audience=self._project_id,
                issuer=self._issuer,
            )
        except jwt.PyJWTError as exc:
            raise InvalidToken(str(exc)) from exc
        uid = claims.get("sub")
        if not uid:
            raise InvalidToken("token has no subject claim")
        return VerifiedIdentity(
            uid=uid, email=claims.get("email"), display_name=claims.get("name")
        )


class FakeTokenVerifier:
    """Dev/test verifier: accepts "fake:<uid>[:<email>[:<name>]]" bearer tokens."""

    def verify(self, token: str) -> VerifiedIdentity:
        parts = token.split(":", 3)
        if parts[0] != "fake" or len(parts) < 2 or not parts[1]:
            raise InvalidToken('fake verifier expects "fake:<uid>[:<email>[:<name>]]"')
        return VerifiedIdentity(
            uid=parts[1],
            email=parts[2] if len(parts) > 2 else None,
            display_name=parts[3] if len(parts) > 3 else None,
        )


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

    result = await session.execute(select(User).where(User.google_sub == identity.uid))
    user = result.scalar_one_or_none()
    if user is None:
        # Auto-provision on first verified token; everyone starts as pharmacist
        user = User(
            google_sub=identity.uid,
            email=identity.email,
            display_name=identity.display_name,
            role=UserRole.PHARMACIST,
        )
        session.add(user)
        await session.commit()
    return user


CurrentUser = Annotated[User, Depends(get_current_user)]
