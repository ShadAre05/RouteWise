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
    return _tokens(service.register(body.email, body.password))


@router.post("/auth/login", response_model=schemas.TokenResponse, summary="Sign in", responses=ERRORS)
def login(body: schemas.LoginRequest, service: AuthService = Depends(deps.auth_service)):
    return _tokens(service.login(body.email, body.password))


@router.post(
    "/auth/refresh", response_model=schemas.TokenResponse, summary="Exchange a refresh token", responses=ERRORS
)
def refresh(body: schemas.RefreshRequest, service: AuthService = Depends(deps.auth_service)):
    return _tokens(service.refresh(body.refresh_token))


@router.post("/auth/logout", status_code=status.HTTP_204_NO_CONTENT, summary="Revoke a refresh token")
def logout(body: schemas.RefreshRequest, service: AuthService = Depends(deps.auth_service)):
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
    service.change_password(claims.user_id, body.old_password, body.new_password)
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.delete(
    "/users/me",
    status_code=status.HTTP_204_NO_CONTENT,
    summary="Delete the account and all its data (US-20)",
    responses={401: {"model": ErrorResponse}},
)
def delete_account(claims=Depends(deps.current_claims), service: AuthService = Depends(deps.user_auth_service)):
    service.delete_account(claims.user_id)
    return Response(status_code=status.HTTP_204_NO_CONTENT)
