"""Application error hierarchy. Every error carries an HTTP status and a stable machine-readable code."""

from http import HTTPStatus


class AppError(Exception):
    status_code: int = HTTPStatus.INTERNAL_SERVER_ERROR
    code: str = "internal_error"

    def __init__(self, message: str, *, details: object | None = None) -> None:
        super().__init__(message)
        self.message = message
        self.details = details


class ValidationFailed(AppError):
    status_code = HTTPStatus.UNPROCESSABLE_ENTITY
    code = "validation_failed"


class Unauthorized(AppError):
    status_code = HTTPStatus.UNAUTHORIZED
    code = "unauthorized"


class Forbidden(AppError):
    status_code = HTTPStatus.FORBIDDEN
    code = "forbidden"


class NotFound(AppError):
    status_code = HTTPStatus.NOT_FOUND
    code = "not_found"


class Conflict(AppError):
    status_code = HTTPStatus.CONFLICT
    code = "conflict"


class UpstreamUnavailable(AppError):
    """The routing engine (or another dependency) did not answer."""

    status_code = HTTPStatus.BAD_GATEWAY
    code = "upstream_unavailable"


class NoRouteFound(AppError):
    status_code = HTTPStatus.UNPROCESSABLE_ENTITY
    code = "no_route"
