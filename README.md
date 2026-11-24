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

Further parts (the Android app) will be added as separate folders.

## Quick start (whole back-end stack)
```bash
cp .env.example .env              # put your own passwords and JWT_SECRET into .env (never commit it)
docker compose up -d --build      # PostgreSQL + PostGIS, database setup, API
```
API documentation: `http://localhost:8000/docs`. Details: [`backend/README.md`](backend/README.md) and [`database/README.md`](database/README.md).

## Status
The database and the back-end are implemented and tested. The Android app is in preparation.

## Security note
No keys or passwords are stored in this repository. Credentials are read from environment variables (see `.env.example`, `backend/.env.example`, `database/.env.example`).
