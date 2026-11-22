"""Dependency wiring (composition root): builds repositories and services for each request."""

import uuid
from collections.abc import Iterator

from fastapi import Depends, Request
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy.orm import Session

from app.clients.osrm import OsrmClient
from app.core.config import Settings, get_settings
from app.core.constants import ROLE_ADMIN
from app.core.errors import Forbidden, Unauthorized
from app.core.security import TokenClaims, decode_access_token
from app.db.session import transactional_session
from app.repositories import sql_repositories as sql
from app.services.auth_service import AuthService
from app.services.reference_service import ReferenceService
from app.services.trip_service import TripService
from app.services.vehicle_service import VehicleService

bearer_scheme = HTTPBearer(auto_error=False, description="Access token from /auth/login")


def settings_dep() -> Settings:
    return get_settings()


def current_claims(
    creds: HTTPAuthorizationCredentials | None = Depends(bearer_scheme), settings: Settings = Depends(settings_dep)
) -> TokenClaims:
    if creds is None:
        raise Unauthorized("missing bearer token")
    return decode_access_token(creds.credentials, secret=settings.jwt_secret, algorithm=settings.jwt_algorithm)


def require_admin(claims: TokenClaims = Depends(current_claims)) -> TokenClaims:
    if claims.role != ROLE_ADMIN:
        raise Forbidden("administrator rights are required")
    return claims


def anonymous_session(request: Request) -> Iterator[Session]:
    yield from transactional_session(request.app.state.session_factory)


def user_session(request: Request, claims: TokenClaims = Depends(current_claims)) -> Iterator[Session]:
    yield from transactional_session(request.app.state.session_factory, claims.user_id)


def auth_service(
    session: Session = Depends(anonymous_session), settings: Settings = Depends(settings_dep)
) -> AuthService:
    return AuthService(sql.SqlUserRepository(session), sql.SqlRefreshTokenRepository(session), settings)


def user_auth_service(
    session: Session = Depends(user_session), settings: Settings = Depends(settings_dep)
) -> AuthService:
    return AuthService(sql.SqlUserRepository(session), sql.SqlRefreshTokenRepository(session), settings)


def vehicle_service(session: Session = Depends(user_session)) -> VehicleService:
    return VehicleService(sql.SqlVehicleRepository(session))


def trip_service(
    request: Request, session: Session = Depends(user_session), settings: Settings = Depends(settings_dep)
) -> TripService:
    routing = getattr(request.app.state, "routing", None) or OsrmClient(
        settings.osrm_base_url, settings.osrm_timeout_seconds
    )
    return TripService(
        sql.SqlTripRepository(session),
        sql.SqlVehicleRepository(session),
        sql.SqlReferenceRepository(session),
        sql.PostgisGeoRepository(session),
        routing,
    )


def reference_service(session: Session = Depends(anonymous_session)) -> ReferenceService:
    return ReferenceService(sql.SqlReferenceRepository(session))


def user_id_of(claims: TokenClaims = Depends(current_claims)) -> uuid.UUID:
    return claims.user_id
