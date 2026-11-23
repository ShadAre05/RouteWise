"""Application factory: wires middleware, routers, error handlers and the database engine."""

import logging
import time
import uuid
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware

from app.api.error_handlers import register_error_handlers
from app.api.openapi_docs import build_openapi
from app.api.routers import auth, health, reference, trips, vehicles
from app.core.config import Settings, get_settings
from app.core.constants import API_PREFIX, REQUEST_ID_HEADER
from app.core.logging import configure_logging, request_id_var
from app.db.session import make_engine, make_session_factory

log = logging.getLogger("app.request")

API_DESCRIPTION = """
REST API of RouteWise: it calculates what a car trip across Europe costs (fuel, road charges, lodging)
and keeps the history of the user's calculations.

**Base path** `/api/v1` (the version is part of the path). **Format** JSON, UTF-8.

**Authentication.** Register or sign in, then send `Authorization: Bearer <access_token>`.
Access tokens live 15 minutes; renew them with the single-use refresh token. Reference data
(`/countries`, `/fuel-prices`, ...) is public.

**Numbers.** Money, prices and distances are decimal numbers serialised as JSON **strings**
(`"50.46"`) so that no precision is lost. Amounts are in euro. Timestamps are ISO 8601 with offset.

**Errors.** Every error has the same shape:
`{"error": {"code", "message", "details", "request_id"}}`. The `request_id` is also returned in the
`X-Request-ID` header; send your own `X-Request-ID` to trace a call end to end.

Guides: Getting Started, integration tutorial, authentication, errors and architecture are in the
repository folder `docs/api/`.
"""

TAGS = [
    {"name": "service", "description": "Health checks for monitoring."},
    {"name": "auth", "description": "Accounts, tokens and password."},
    {"name": "vehicles", "description": "The user's vehicles (consumption, tank, default)."},
    {"name": "trips", "description": "Cost calculation and the history of saved trips."},
    {"name": "reference data", "description": "Public data: countries, fuel prices, speed profiles, lodging rates."},
]


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
        description=API_DESCRIPTION,
        contact={"name": "RouteWise (EHU diploma project)", "url": "https://github.com/ShadAre05/RouteWise"},
        license_info={"name": "See the repository"},
        openapi_tags=TAGS,
        servers=[{"url": "http://localhost:8000", "description": "Local Docker Compose stack"}],
        generate_unique_id_function=lambda route: route.name,
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
    app.openapi = lambda: build_openapi(app)
    return app
