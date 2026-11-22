"""Global error handling: every error leaves the API in the same JSON shape and with the right HTTP status."""

import logging
from http import HTTPStatus

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException

from app.core.errors import AppError
from app.core.logging import request_id_var

log = logging.getLogger(__name__)


def _response(status: int, code: str, message: str, details: object | None = None) -> JSONResponse:
    body = {"error": {"code": code, "message": message, "details": details, "request_id": request_id_var.get()}}
    return JSONResponse(status_code=status, content=body)


def register_error_handlers(app: FastAPI) -> None:
    @app.exception_handler(AppError)
    async def app_error(_: Request, exc: AppError) -> JSONResponse:
        level = logging.ERROR if exc.status_code >= HTTPStatus.INTERNAL_SERVER_ERROR else logging.INFO
        log.log(level, "request rejected", extra={"code": exc.code, "status": int(exc.status_code)})
        return _response(int(exc.status_code), exc.code, exc.message, exc.details)

    @app.exception_handler(RequestValidationError)
    async def validation_error(_: Request, exc: RequestValidationError) -> JSONResponse:
        details = [{"field": ".".join(str(p) for p in e["loc"][1:]), "message": e["msg"]} for e in exc.errors()]
        return _response(HTTPStatus.UNPROCESSABLE_ENTITY, "validation_failed", "request validation failed", details)

    @app.exception_handler(StarletteHTTPException)
    async def http_error(_: Request, exc: StarletteHTTPException) -> JSONResponse:
        return _response(exc.status_code, HTTPStatus(exc.status_code).name.lower(), str(exc.detail))

    @app.exception_handler(Exception)
    async def unexpected(_: Request, exc: Exception) -> JSONResponse:
        log.exception("unhandled error")
        return _response(HTTPStatus.INTERNAL_SERVER_ERROR, "internal_error", "internal server error")
