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

**Coverage:** Lithuania, Latvia, Estonia, Poland, Germany, Denmark, Czechia, Slovakia; passenger cars; petrol 95 and diesel. Fuel prices come from the EC Weekly Oil Bulletin (weekly import, history kept).

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
| [`database/`](database/) | PostgreSQL 16 + PostGIS: migrations, seeds, test data, 93 automated tests, data dictionary, logical schema, security, indexing and versioning documents | [`database/README.md`](database/README.md) |

Further parts (back-end, Android app, project documents) will be added as separate folders.

## Status
The database layer is complete and tested. Other layers are in preparation.

## Security note
No keys or passwords are stored in this repository. Credentials are read from environment variables (see `database/.env.example`).
