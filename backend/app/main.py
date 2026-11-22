"""Application factory: wires middleware, routers, error handlers and the database engine."""

import logging
import time
import uuid
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware

from app.api.error_handlers import register_error_handlers
from app.api.routers import auth, health, reference, trips, vehicles
from app.core.config import Settings, get_settings
from app.core.constants import API_PREFIX, REQUEST_ID_HEADER
from app.core.logging import configure_logging, request_id_var
from app.db.session import make_engine, make_session_factory

log = logging.getLogger("app.request")


def create_app(settings: Settings | None = None) -> FastAPI:
    settings = settings or get_settings()
    configure_logging(settings.log_level)

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        engine = make_engine(settings.database_url)
        app.state.session_factory = make_session_factory(engine)
        log.info("application started")
        yield
        engine.dispose()

    app = FastAPI(
        title="RouteWise API",
        version="1.0.0",
        description="Back-end of RouteWise: trip cost calculation across 8 European countries. "
        "Authenticate with /auth/login and send the access token as `Authorization: Bearer <token>`.",
        lifespan=lifespan,
    )
    if settings.cors_origins:
        app.add_middleware(
            CORSMiddleware, allow_origins=settings.cors_origins, allow_methods=["*"], allow_headers=["*"]
        )

    @app.middleware("http")
    async def request_context(request: Request, call_next):
        rid = request.headers.get(REQUEST_ID_HEADER) or uuid.uuid4().hex
        token = request_id_var.set(rid)
        started = time.perf_counter()
        try:
            response = await call_next(request)
        finally:
            request_id_var.reset(token)
        response.headers[REQUEST_ID_HEADER] = rid
        token = request_id_var.set(rid)
        log.info(
            "request",
            extra={
                "method": request.method,
                "path": request.url.path,
                "status": response.status_code,
                "duration_ms": round((time.perf_counter() - started) * 1000, 1),
            },
        )
        request_id_var.reset(token)
        return response

    register_error_handlers(app)
    app.include_router(health.router)
    for router in (auth.router, vehicles.router, trips.router, reference.router):
        app.include_router(router, prefix=API_PREFIX)
    return app
