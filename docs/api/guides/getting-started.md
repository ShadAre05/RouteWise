# Getting started

This guide takes you from nothing to your first saved trip calculation in about five minutes.

## 1. What the API does
RouteWise calculates what a car trip across Europe costs before you leave: fuel, road charges (tolls and
vignettes) and lodging for multi-day trips. It covers Lithuania, Latvia, Estonia, Poland, Germany, Denmark,
Czechia and Slovakia. Every calculation can be saved to a per-user history.

| Fact | Value |
|---|---|
| Base URL (local stack) | `http://localhost:8000` |
| API path | `/api/v1` (the version is in the path) |
| Format | JSON, UTF-8, `Content-Type: application/json` |
| Authentication | `Authorization: Bearer <access token>` (see [authentication](authentication.md)) |
| Interactive documentation | `/docs` (Swagger UI), `/redoc` (Redoc), `/openapi.json` |
| Specification file | [`../openapi.yaml`](../openapi.yaml) |

## 2. Start the API
From the repository root:

```bash
cp .env.example .env        # put your own passwords and JWT_SECRET into .env; never commit it
docker compose up -d --build
curl http://localhost:8000/health/ready      # {"status":"ready"}
```

Open `http://localhost:8000/docs`: every endpoint can be tried there ("Try it out"). Protected endpoints need a token: register or sign in, copy the `access_token` from your own response and press **Authorize**.

For demo data (fuel prices, test users) add `SEED_FLAG=--test-data` to `.env` before the first start; without it the database has only the countries and the demo rates, and `/fuel-prices` is empty. Real prices: `docker compose --profile import run --rm fuel-import` (see `backend/README.md`, "Fuel price import").

Trip calculation needs the routing engine (OSRM) with map data. Without it every other endpoint works and
`POST /trips` answers `502 upstream_unavailable`; see [`backend/README.md`](../../../backend/README.md). For a quick look set `OSRM_BASE_URL=https://router.project-osrm.org` in `.env` (public demo server, not for production) and run `docker compose up -d`.

## 3. Look at public data (no token)
```bash
curl http://localhost:8000/api/v1/countries
curl "http://localhost:8000/api/v1/fuel-prices?fuel_type=petrol95"
```
Reference data is public: countries, latest fuel prices, the 12-week price trend, speed profiles and typical
lodging prices.

## 4. Create an account
```bash
curl -X POST http://localhost:8000/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"driver@example.com","password":"correct-horse-battery","privacy_accepted":true}'
```
Answer `201`:
```json
{
  "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refresh_token": "5ceIFcTIm95XFCvLwSZy4tZPOONIK4T3f7nYpJD9OxVp3VxwBd662d1j4zchTofv",
  "token_type": "bearer",
  "expires_in": 900
}
```
`expires_in` is in seconds: the access token is valid for 15 minutes. Keep the `refresh_token` to renew it.

## 5. Add a vehicle and calculate a trip
```bash
TOKEN=eyJhbGciOi...            # the access_token from step 4

curl -X POST http://localhost:8000/api/v1/vehicles \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"Family car","fuel_type":"petrol95","consumption_l100":7.5,"tank_l":50,"is_default":true}'

curl -X POST http://localhost:8000/api/v1/trips \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"origin_label":"Vilnius","destination_label":"Warsaw",
       "origin":{"lon":25.28,"lat":54.69},"destination":{"lon":21.01,"lat":52.23},
       "daily_limit_h":8,"start_fuel":"half"}'
```
The answer (`201`) is the saved trip: distance, duration, arrival time, the cost lines per country and the total
in euro. A shortened example:
```json
{
  "id": "6a1d0a76-0929-4e21-8aab-57a495838da8",
  "title": "Vilnius - Warsaw",
  "distance_km": "460.0",
  "duration_min": 335,
  "nights": 0,
  "total_eur": "50.46",
  "lines": [
    {"kind": "fuel", "country": "LT", "distance_km": "187.5", "unit_price": "1.554", "amount_eur": "21.85", "status": "ok"},
    {"kind": "fuel", "country": "PL", "distance_km": "272.5", "unit_price": "1.400", "amount_eur": "28.61", "status": "ok"}
  ],
  "refuel_stops": [{"seq": 1, "country": "PL", "km_from_start": "187.5", "liters": "14.5", "price_eur_l": "1.400"}]
}
```
> The numbers above were produced with a stand-in routing engine that returns a fixed 460 km route; with the
> real engine the distance and duration of a real road route are returned. The fuel prices are the demo data
> of the repository, not real market prices.

## 6. Things to know before you integrate
- **Numbers are strings.** Money, prices, distances and litres are decimals sent as JSON strings (`"50.46"`),
  so no precision is lost. Parse them with a decimal type, not a binary float.
- **Coordinates are not stored.** `origin` and `destination` are used for routing only; the history keeps the
  text labels.
- **Errors have one shape** and a `request_id`; see [errors](errors.md).
- **Your data is private.** Another user's resource answers `404`, never `403`, so its existence is not leaked.

## Next
- [Integration tutorial](tutorial.md): the full workflow step by step, also as a runnable script.
- [Authentication](authentication.md), [errors](errors.md), [conventions](conventions.md), [architecture](architecture.md).
