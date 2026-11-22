# RouteWise back-end

REST API of RouteWise (FastAPI + SQLAlchemy 2 + PostgreSQL/PostGIS). It authenticates users, keeps their vehicles and trip history, and calculates the cost of a trip: it asks the OSRM routing engine for the route, splits it by country with PostGIS, and prices fuel, road charges and lodging from the reference data in the database.

Interactive API documentation (OpenAPI / Swagger UI) is served by the application itself at **`/docs`** (ReDoc at `/redoc`, raw schema at `/openapi.json`).

## Where each requirement is met
| Requirement | Where |
|---|---|
| Modern framework | FastAPI (`app/main.py`) |
| Database + ORM | PostgreSQL 16 + PostGIS (see `../database`), SQLAlchemy 2 ORM (`app/db/models.py`, `app/repositories/sql_repositories.py`) |
| Layered architecture | `api/` (presentation) → `services/` (business logic) → `repositories/` + `db/` (data access), `clients/` (external routing engine), `domain/` (plain objects), `core/` (config, logging, security, errors) |
| SOLID | see the table below |
| API description | OpenAPI generated from typed request/response models; every endpoint has a summary, status codes and an error model; section "Endpoints" below |
| Global error handling | `app/api/error_handlers.py`: one JSON error shape for domain errors, validation errors, HTTP errors and unexpected exceptions |
| Structured logging | `app/core/logging.py`: JSON lines to stdout with a correlation id (`X-Request-ID`) in every line |
| Production deployment | `../docker-compose.yml`, `Dockerfile`, CI workflow `../.github/workflows/backend.yml` (see "Deployment") |
| Unit tests, coverage ≥ 70 % | `tests/`; measured with `pytest-cov`; the CI gate is 80 % |
| No secrets in the repository | all settings come from environment variables; `.env.example` files contain placeholders only |
| Dependencies checked for vulnerabilities | `pip-audit` (CI step and local) |

## Architecture
```
HTTP request
   │  middleware: request id, access log
   ▼
api/routers  ──► api/schemas (validation)         presentation layer
   │
   ▼
services/    ◄── pure calculation modules          business logic
   │  depends on interfaces only
   ▼
repositories/interfaces.py  (Protocols)
   ├── repositories/sql_repositories.py  ──► PostgreSQL + PostGIS (row-level security per user)
   └── clients/osrm.py                    ──► OSRM routing engine (HTTP)
```
Pure business rules (no I/O, easy to test): `services/cost_calculator.py`, `schedule.py`, `travel_time.py`, `country_split.py`, `refuel_planner.py`.

### SOLID in this code
| Principle | Example |
|---|---|
| Single responsibility | one module per rule: the schedule, the cost lines and the refuel plan are separate; routers only translate HTTP, repositories only talk to the database |
| Open/closed | a new cost kind or a new routing engine is a new class that satisfies an existing Protocol; services do not change |
| Liskov substitution | `FakeVehicles`/`SqlVehicleRepository` (and `FakeRouting`/`OsrmClient`) are interchangeable behind the same contract; the unit tests run the real services on the fakes |
| Interface segregation | small contracts per aggregate (`UserRepository`, `RefreshTokenRepository`, `VehicleRepository`, `TripRepository`, `ReferenceRepository`, `GeoRepository`, `RoutingClient`) instead of one large "database" interface |
| Dependency inversion | services receive their repositories in the constructor (`TripService(trips, vehicles, reference, geo, routing)`); `api/deps.py` is the only place that chooses concrete classes |

## Security
- Passwords are never handled in Python: hashing (bcrypt, cost 12) and checking happen inside PostgreSQL functions (`rw.register_user`, `rw.authenticate`), see `../database/docs/security.md`.
- Access token: JWT (HS256) created and verified by the standard PyJWT library, 15 minutes. Refresh token: random, stored only as a SHA-256 hash, single use (rotation), revoked on logout and on password change.
- The application connects as the database role `rw_app` (not a superuser, not the owner). Every request runs in one transaction, and the user id is passed to PostgreSQL (`SET LOCAL`), so **row-level security** limits all queries to the caller's own rows. Another user's resource answers `404`.
- All input is validated by Pydantic models (types, ranges, lengths) and all SQL is parameterised (SQLAlchemy / bound parameters); there is no string-built SQL.
- Five failed logins lock the account for 10 minutes (database function); the counter is committed even though the request fails.
- Logs never contain passwords, tokens or e-mail addresses.

## Configuration (environment variables)
| Variable | Meaning | Default |
|---|---|---|
| `DATABASE_URL` | SQLAlchemy URL, role `rw_app` | required |
| `JWT_SECRET` | HMAC secret, at least 32 characters | required |
| `OSRM_BASE_URL` | routing engine | `http://localhost:5000` |
| `OSRM_TIMEOUT_SECONDS` | timeout of one routing request | `10` |
| `ACCESS_TOKEN_TTL_MINUTES` / `REFRESH_TOKEN_TTL_DAYS` | token lifetimes | `15` / `30` |
| `LOG_LEVEL` | `DEBUG` … `ERROR` | `INFO` |
| `CORS_ORIGINS` | JSON list of allowed origins | none |

## Run
### Whole stack with Docker (recommended)
```bash
cp .env.example .env            # in the repository root; set your own passwords and JWT_SECRET
docker compose up -d --build    # database, one-shot setup (migrations + reference data), API
curl http://localhost:8000/health/ready
```
Open `http://localhost:8000/docs`. The `db-init` container creates the roles, applies `database/db/migrations` and loads `database/db/seed` on every start (all idempotent).

**Routing engine.** The trip calculation needs OSRM with map data. The optional compose profile starts it: download an extract of your region (for example from download.geofabrik.de), prepare it once with `osrm-extract`, `osrm-partition` and `osrm-customize` into `./osrm-data/region.osrm`, then `docker compose --profile routing up -d`. All other endpoints work without it; `POST /trips` answers `502 upstream_unavailable` while the engine is not reachable.

### Locally (development)
```bash
cd backend
python -m venv .venv && . .venv/bin/activate
pip install -r requirements-dev.txt
cp .env.example .env            # edit values; the database must be prepared by ../database/scripts/setup_all.sh
uvicorn app.main:create_app --factory --reload
```

## Tests
```bash
cd backend
pytest tests/unit --cov=app                      # business logic, API contract, OSRM client, logging; no database needed
INTEGRATION_DATABASE_URL=postgresql+psycopg://rw_app:<password>@localhost:5432/routewise pytest --cov=app
ruff check .                                     # lint
pip-audit -r requirements.txt                    # known vulnerabilities in dependencies
```
- **Unit tests** (`tests/unit`) run the real services on in-memory fakes and the real FastAPI app through its HTTP interface (routing, validation, status codes, error format). The OSRM client is tested with a mocked HTTP transport.
- **Integration tests** (`tests/integration`) use the real database prepared by the migrations: registration and login with real bcrypt, lockout, token rotation, row-level security between two users, trip calculation with the PostGIS country split, vehicle snapshot after deletion, default-vehicle trigger. Only the external routing engine is replaced by an HTTP mock in OSRM format.
- Coverage at the time of writing: unit tests alone 90 %, unit + integration 96 % (gate in CI: 80 %).

## Endpoints
Base path `/api/v1`. Protected endpoints need `Authorization: Bearer <access token>`.

| Method and path | Purpose | Success | Errors |
|---|---|---|---|
| `POST /auth/register` | create an account (privacy notice must be accepted) | 201 tokens | 409 e-mail taken, 422 |
| `POST /auth/login` | sign in | 200 tokens | 401, 422 |
| `POST /auth/refresh` | exchange a refresh token (single use) | 200 tokens | 401 |
| `POST /auth/logout` | revoke a refresh token | 204 | 422 |
| `POST /auth/change-password` | change password, revokes all refresh tokens | 204 | 401, 422 |
| `DELETE /users/me` | delete the account and all its data | 204 | 401 |
| `GET/POST /vehicles`, `GET/PATCH/DELETE /vehicles/{id}` | vehicles | 200/201/204 | 401, 404, 422 |
| `POST /trips` | calculate a trip and save it to the history | 201 trip with cost lines and refuel stops | 401, 422, 502 routing engine down |
| `GET /trips?limit&offset` | history, newest first | 200 page | 401, 422 |
| `GET/PATCH/DELETE /trips/{id}` | details, rename, delete | 200/204 | 401, 404 |
| `GET /countries`, `/fuel-prices?fuel_type=`, `/fuel-prices/{country}/trend?fuel_type=`, `/speed-profiles`, `/lodging-rates` | public reference data | 200 | 404, 422 |
| `GET /health`, `/health/ready` | liveness, readiness (database) | 200 | |

Errors always look like `{"error": {"code": "not_found", "message": "...", "details": null, "request_id": "..."}}`.

## Calculation rules (what `POST /trips` does)
1. Picks the vehicle (given `vehicle_id` or the default one) and asks OSRM for the route (`fastest`, `shortest` = shortest of the alternatives, `no_tolls` = toll roads excluded).
2. Splits the route by country with PostGIS and scales the lengths to the route length (the border data is simplified).
3. Driving time: per country `km / average speed` of the current season (summer/winter by today's date); a country without a speed profile uses its share of the routing engine's duration.
4. Days and arrival: at most `daily_limit_h` of driving per day, every day starts at the same time of day; only driving time counts.
5. Fuel per country from the latest national price (a missing price uses the average of the others, status `eu_average`); road charges from the tariff table; lodging for the nights (the user's own amount replaces the estimate).
6. Optional refuel plan when the vehicle has a tank size and `start_fuel` is given: a tip, never part of the total.
7. Everything is saved in one transaction; the database checks that `total_eur` equals the sum of the cost lines.

## Known limitations (stated openly)
- The routing engine is a separate service that needs map data; a hosted instance is not part of this repository.
- Vignettes are charged once for every country the route enters (except with `no_tolls`); per-kilometre tolls use the kilometres on charged roads reported by OSRM and are shared between the countries that have a per-km tariff. The overnight stop city is not determined; lodging is priced in the destination country.
- Administration of reference data (tariffs, lodging, speed profiles) is done in the database with the `rw_admin_user` role; there is no admin HTTP endpoint yet.
- The Docker image, the compose stack and the CI workflow are written to run from a clean checkout, but were not executed on GitHub/Docker in the environment where this code was written; the application, the database scripts and all tests were run against a real PostgreSQL 16 + PostGIS.
