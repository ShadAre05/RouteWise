"""Registration, sessions and account management (US-01, US-02, US-19, US-20)."""

import logging
import uuid
from collections.abc import Callable
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta

from app.core.config import Settings
from app.core.errors import Unauthorized
from app.core.security import create_access_token, hash_refresh_token, new_refresh_token
from app.repositories.interfaces import RefreshTokenRepository, UserRepository

log = logging.getLogger(__name__)
INVALID_CREDENTIALS = "invalid e-mail or password"


@dataclass(frozen=True)
class TokenPair:
    access_token: str
    refresh_token: str
    expires_in: int


class AuthService:
    def __init__(
        self,
        users: UserRepository,
        tokens: RefreshTokenRepository,
        settings: Settings,
        clock: Callable[[], datetime] = lambda: datetime.now(UTC),
    ) -> None:
        self._users, self._tokens, self._settings, self._clock = users, tokens, settings, clock

    def register(self, email: str, password: str) -> TokenPair:
        user_id = self._users.register(email.strip().lower(), password)
        log.info("user registered", extra={"user_id": str(user_id)})
        return self._issue(user_id)

    def login(self, email: str, password: str) -> TokenPair:
        user_id = self._users.authenticate(email.strip().lower(), password)
        if user_id is None:
            log.warning("login failed")  # never log the e-mail or the password
            raise Unauthorized(INVALID_CREDENTIALS)
        return self._issue(user_id)

    def refresh(self, refresh_token: str) -> TokenPair:
        user_id = self._tokens.consume(hash_refresh_token(refresh_token))
        if user_id is None:
            raise Unauthorized("invalid or expired refresh token")
        return self._issue(user_id)

    def logout(self, refresh_token: str) -> None:
        self._tokens.revoke(hash_refresh_token(refresh_token))

    def change_password(self, user_id: uuid.UUID, old: str, new: str) -> None:
        if not self._users.change_password(user_id, old, new):
            raise Unauthorized("the current password is wrong")
        log.info("password changed", extra={"user_id": str(user_id)})

    def delete_account(self, user_id: uuid.UUID) -> None:
        self._users.delete_account(user_id)
        log.info("account deleted", extra={"user_id": str(user_id)})

    def _issue(self, user_id: uuid.UUID) -> TokenPair:
        role = self._users.get_role(user_id)
        if role is None:
            raise Unauthorized("user does not exist")
        s = self._settings
        access = create_access_token(
            user_id,
            role,
            secret=s.jwt_secret,
            algorithm=s.jwt_algorithm,
            ttl_minutes=s.access_token_ttl_minutes,
            now=self._clock(),
        )
        raw, digest = new_refresh_token()
        self._tokens.add(user_id, digest, self._clock() + timedelta(days=s.refresh_token_ttl_days))
        return TokenPair(access, raw, s.access_token_ttl_minutes * 60)
