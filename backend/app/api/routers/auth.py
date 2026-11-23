from fastapi import APIRouter, Depends, Response, status

from app.api import deps, schemas
from app.api.schemas import ErrorResponse
from app.services.auth_service import AuthService, TokenPair

router = APIRouter(tags=["auth"])
ERRORS = {401: {"model": ErrorResponse}, 422: {"model": ErrorResponse}}


def _tokens(pair: TokenPair) -> schemas.TokenResponse:
    return schemas.TokenResponse(
        access_token=pair.access_token, refresh_token=pair.refresh_token, expires_in=pair.expires_in
    )


@router.post(
    "/auth/register",
    response_model=schemas.TokenResponse,
    status_code=status.HTTP_201_CREATED,
    summary="Create an account",
    responses={409: {"model": ErrorResponse}, 422: {"model": ErrorResponse}},
)
def register(body: schemas.RegisterRequest, service: AuthService = Depends(deps.auth_service)):
    """
    Creates an account and signs the user in. The password is hashed inside PostgreSQL (bcrypt); it is never
    stored or logged.

    Returns an access token (15 minutes) and a single-use refresh token. Errors: 409 when the e-mail is
    taken, 422 when a field is invalid.
    """
    return _tokens(service.register(body.email, body.password))


@router.post("/auth/login", response_model=schemas.TokenResponse, summary="Sign in", responses=ERRORS)
def login(body: schemas.LoginRequest, service: AuthService = Depends(deps.auth_service)):
    """
    Checks the e-mail and password and returns a token pair.

    After 5 failed attempts the account is locked for 10 minutes; the answer is then the same 401 as for a
    wrong password, so an attacker learns nothing.
    """
    return _tokens(service.login(body.email, body.password))


@router.post(
    "/auth/refresh", response_model=schemas.TokenResponse, summary="Exchange a refresh token", responses=ERRORS
)
def refresh(body: schemas.RefreshRequest, service: AuthService = Depends(deps.auth_service)):
    """
    Exchanges a refresh token for a new token pair. Every refresh token works **once** (rotation): the old
    one is revoked, so a stolen and already used token is useless.
    """
    return _tokens(service.refresh(body.refresh_token))


@router.post("/auth/logout", status_code=status.HTTP_204_NO_CONTENT, summary="Revoke a refresh token")
def logout(body: schemas.RefreshRequest, service: AuthService = Depends(deps.auth_service)):
    """
    Revokes a refresh token. Always answers 204, also for an unknown or already revoked token, so the call
    is safe to repeat.
    """
    service.logout(body.refresh_token)
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.post(
    "/auth/change-password",
    status_code=status.HTTP_204_NO_CONTENT,
    summary="Change the password (US-19)",
    responses=ERRORS,
)
def change_password(
    body: schemas.ChangePasswordRequest,
    claims=Depends(deps.current_claims),
    service: AuthService = Depends(deps.user_auth_service),
):
    """Changes the password of the signed-in user and revokes all refresh tokens. Requires the old password."""
    service.change_password(claims.user_id, body.old_password, body.new_password)
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.delete(
    "/users/me",
    status_code=status.HTTP_204_NO_CONTENT,
    summary="Delete the account and all its data (US-20)",
    responses={401: {"model": ErrorResponse}},
)
def delete_account(claims=Depends(deps.current_claims), service: AuthService = Depends(deps.user_auth_service)):
    """Permanently deletes the account with its vehicles, trips and tokens (US-20). Cannot be undone."""
    service.delete_account(claims.user_id)
    return Response(status_code=status.HTTP_204_NO_CONTENT)
