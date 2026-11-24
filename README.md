# RouteWise
**RouteWise** is an Android app that estimates the full cost of a car trip across Europe before you leave: fuel, tolls and vignettes, and lodging for multi-day trips. It was developed as a BA diploma project at the European Humanities University (EHU).

## What it does
- Builds a route between two places and offers three variants: fastest, shortest, and without toll roads.
- Splits the route by country and calculates the cost per country: **fuel** (national average prices), **road charges** (tolls and vignettes) and **lodging** for overnight stops.
- Plans the driving days from a daily driving limit and shows the arrival time in the destination's local time, with a hotel check-in hint.
- Suggests where to refuel when the tank size and the starting fuel level are known (a tip; it does not change the total).
- Shows the fuel price trend of the last 12 weeks, the speed profile of each country by season, and a weather note for origin and destination.
- Saves every calculation to the trip history with a snapshot of the vehicle, so old trips never change.
- Accounts with privacy-by-design: no coordinates are stored, the account and all its data can be deleted in the app.

**Coverage:** Lithuania, Latvia, Estonia, Poland, Germany, Denmark, Czechia, Slovakia; passenger cars; petrol 95 and diesel. Fuel prices are national weekly averages in the shape of the EC Weekly Oil Bulletin, with history kept. The database provides the import procedures (`import_fuel_price`, `finish_fuel_import`), and the back-end reads the bulletin workbook with `python -m app.importers.run` (see [`backend/README.md`](backend/README.md)); the weekly schedule is not set up yet, and the prices in the demo data are loaded from SQL files.

## Architecture
```
Android app (Kotlin, Jetpack Compose)
        │  HTTPS / JSON
FastAPI back-end ──► OSRM (self-hosted routing)
        │          └► Mapbox (map tiles and geocoding only)
PostgreSQL 16 + PostGIS   (borders, prices, tariffs, users, history)
```
Everything on the server side runs in Docker Compose.

## Repository
| Folder | What it is | Start here |
|---|---|---|
| [`database/`](database/) | PostgreSQL 16 + PostGIS: migrations, seeds, test data, 97 automated tests, data dictionary, logical schema, security, indexing and versioning documents | [`database/README.md`](database/README.md) |
| [`backend/`](backend/) | FastAPI REST API: authentication, vehicles, trip cost calculation and history, OpenAPI documentation, unit and integration tests | [`backend/README.md`](backend/README.md) |
| [`docs/api/`](docs/api/) | API documentation: OpenAPI 3.1 specification, Getting Started, integration tutorial, authentication, errors, architecture, changelog | [`docs/api/README.md`](docs/api/README.md) |
| [`android/`](android/) | Android client (Kotlin, Jetpack Compose): trip planning, cost budget, history, vehicles, profile | [`android/README.md`](android/README.md) |

## Quick start (whole back-end stack)
Requirements: Docker with Compose v2.
```bash
cp .env.example .env              # Windows PowerShell: Copy-Item .env.example .env
```
Open `.env` and set your own values (never commit this file):
- the passwords and `JWT_SECRET` (at least 32 characters; `python -c "import secrets; print(secrets.token_hex(32))"`);
- for a demo also `SEED_FLAG=--test-data`: it loads synthetic users, fuel prices and trips. **Leave it empty in production.**

```bash
docker compose up -d --build      # PostgreSQL + PostGIS, database setup (migrations, seeds), API
docker compose ps                 # db and api must be "healthy"
```
API documentation and a place to try every endpoint: `http://localhost:8000/docs`. If you change the passwords in `.env`
after the first start, remove the old database volume first: `docker compose down -v` (this deletes the local data).

## How to review each part
| Part | How to see it |
|---|---|
| Database | `database/README.md`: tables, roles, 97 tests; the stack above creates it from the migrations |
| Back-end API | `http://localhost:8000/docs`: `POST /api/v1/auth/register`, copy `access_token` from **your** response into **Authorize**, then try the endpoints. Public data (`/countries`, `/fuel-prices`) needs no token. Details: `backend/README.md` |
| Trip calculation | needs a routing engine (OSRM). For a quick look put `OSRM_BASE_URL=https://router.project-osrm.org` into `.env` (public demo server, not for production) and run `docker compose up -d`; the self-hosted OSRM with map data is part of the deployment stage (`backend/README.md`, "Routing engine") |
| Real fuel prices | `docker compose --profile import run --rm fuel-import` downloads the EC Weekly Oil Bulletin and stores the prices (`backend/README.md`, "Fuel price import") |
| API documentation | `docs/api/README.md` (guides, reference, `openapi.yaml`); a mock server needs no database |
| Android app | demo mode needs no server: download the APK from the latest run of the **Android** workflow (Actions, artifact `routewise-debug-apk`), log in with `demo@routewise.app` / `demo-password-1`. With the real back-end: `android/README.md`, section 2.3 |

## Status
The database, the back-end and the Android client are implemented and tested. Deployment to a server (hosted routing
engine, HTTPS, scheduled price import) is the next stage.

## Security note
No keys or passwords are stored in this repository. Credentials are read from environment variables (see `.env.example`, `backend/.env.example`, `database/.env.example`).
