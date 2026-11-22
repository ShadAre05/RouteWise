"""Tokens. JWT is created and verified by the standard PyJWT library (no custom crypto)."""

import hashlib
import secrets
import uuid
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta

import jwt

from app.core.constants import TOKEN_TYPE_ACCESS
from app.core.errors import Unauthorized


@dataclass(frozen=True)
class TokenClaims:
    user_id: uuid.UUID
    role: str


def create_access_token(
    user_id: uuid.UUID, role: str, *, secret: str, algorithm: str, ttl_minutes: int, now: datetime | None = None
) -> str:
    issued = now or datetime.now(UTC)
    payload = {
        "sub": str(user_id),
        "role": role,
        "typ": TOKEN_TYPE_ACCESS,
        "iat": issued,
        "exp": issued + timedelta(minutes=ttl_minutes),
    }
    return jwt.encode(payload, secret, algorithm=algorithm)


def decode_access_token(token: str, *, secret: str, algorithm: str) -> TokenClaims:
    try:
        data = jwt.decode(token, secret, algorithms=[algorithm], options={"require": ["exp", "sub"]})
        if data.get("typ") != TOKEN_TYPE_ACCESS:
            raise Unauthorized("wrong token type")
        return TokenClaims(user_id=uuid.UUID(data["sub"]), role=str(data["role"]))
    except (jwt.PyJWTError, ValueError, KeyError) as exc:
        raise Unauthorized("invalid or expired token") from exc


def new_refresh_token() -> tuple[str, str]:
    """Returns (raw token for the client, SHA-256 hash for the database). A random 384-bit token needs no slow hash."""
    raw = secrets.token_urlsafe(48)
    return raw, hash_refresh_token(raw)


def hash_refresh_token(raw: str) -> str:
    return hashlib.sha256(raw.encode()).hexdigest()
