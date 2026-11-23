# Integration tutorial: from sign-up to a saved trip

**Goal.** A developer who has never seen the API signs a user up, stores a vehicle, calculates and saves a trip,
reads the history and cleans up. Each step shows the request, the response and what to check.
The same workflow runs as a script: [`../examples/walkthrough.py`](../examples/walkthrough.py).

**You need:** a running API (see [getting started](getting-started.md)) and `curl`, or Python 3.10+ with `httpx`.
The examples use `http://localhost:8000`; replace it with your server. All responses below were recorded from the
running API and shortened where marked with `...`.

```bash
export BASE=http://localhost:8000/api/v1
```

---

## Step 1. Check that the API is alive
```bash
curl http://localhost:8000/health/ready
```
`{"status":"ready"}` means the process runs **and** the database answers. `/health` only checks the process.

## Step 2. Look at the reference data (public)
```bash
curl $BASE/countries
curl "$BASE/fuel-prices?fuel_type=petrol95"
```
```json
[{"country": "PL", "price_eur_l": "1.400", "bulletin_date": "2026-11-09"}, ...]
```
`fuel_type` is `petrol95` or `diesel`. Prices are euro per litre.

## Step 3. Register
```bash
curl -X POST $BASE/auth/register -H "Content-Type: application/json" \
  -d '{"email":"tutorial@example.com","password":"correct-horse-battery","privacy_accepted":true}'
```
Save both tokens:
```bash
export TOKEN=<access_token from the answer>
export REFRESH=<refresh_token from the answer>
```
**Check:** status `201`, `expires_in` is `900`. Registering the same e-mail again returns `409 conflict`.

## Step 4. Call a protected endpoint without a token (to see the error)
```bash
curl -i $BASE/vehicles
```
```
HTTP/1.1 401 Unauthorized
x-request-id: 5b1a22d952fe434b8337ad101c3db651
{"error":{"code":"unauthorized","message":"missing bearer token","details":null,"request_id":"5b1a22d952fe434b8337ad101c3db651"}}
```
Note that the `request_id` in the body equals the `x-request-id` header.

## Step 5. Add a vehicle
```bash
curl -X POST $BASE/vehicles -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"Family car","fuel_type":"petrol95","consumption_l100":7.5,"tank_l":50,"is_default":true}'
```
```json
{"id": "becde9e5-73f7-4827-8e98-ae9466b06814", "name": "Family car", "fuel_type": "petrol95",
 "consumption_l100": "7.5", "tank_l": "50.0", "is_default": true,
 "created_at": "2026-11-23T14:40:48.749011+02:00", "updated_at": "2026-11-23T14:40:48.749011+02:00"}
```
**Check:** status `201`. The first vehicle of a user becomes the default automatically; a trip that names no
`vehicle_id` uses the default one. `consumption_l100` must be 2.0-30.0 and `tank_l` 20-120, otherwise `422`.

## Step 6. Calculate and save a trip
```bash
curl -X POST $BASE/trips -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"origin_label":"Vilnius","destination_label":"Warsaw",
       "origin":{"lon":25.28,"lat":54.69},"destination":{"lon":21.01,"lat":52.23},
       "variant":"fastest","daily_limit_h":8,"lodging_class":"standard","start_fuel":"half"}'
```
```json
{"id": "59448440-1514-46c9-9006-447aa4b5946f", "title": "Vilnius - Warsaw",
 "departed_at": "2026-11-23T14:40:48.803769+02:00", "arrival_at": "2026-11-23T20:16:...+02:00",
 "destination_country": "PL", "variant": "fastest", "distance_km": "460.0", "duration_min": 335,
 "daily_limit_h": "8.0", "nights": 0, "vehicle_name": "Family car", "fuel_type": "petrol95",
 "consumption_l100": "7.5", "start_fuel": "half", "price_date": "2026-11-09", "total_eur": "50.46",
 "lines": [
   {"kind": "fuel", "country": "LT", "distance_km": "187.5", "unit_price": "1.554", "amount_eur": "21.85", "status": "ok"},
   {"kind": "fuel", "country": "PL", "distance_km": "272.5", "unit_price": "1.400", "amount_eur": "28.61", "status": "ok"}],
 "refuel_stops": [{"seq": 1, "country": "PL", "km_from_start": "187.5", "liters": "14.5", "price_eur_l": "1.400"}]}
```
How to read it:
- `total_eur` is exactly the sum of the `amount_eur` of all `lines` (the database enforces this).
- Each line has a `status`: `ok` (from the data), `eu_average` (no price for that country, the average of the
  others was used), `estimate` (typical value), `user_entered` (you gave it), `no_data` (unknown).
- `nights` is the number of overnight stops that `daily_limit_h` forces; for them a `lodging` line is added.
  Pass `lodging_entered_eur` to replace the estimate with your own price per night.
- `refuel_stops` is advice and is not part of the total. It appears when the vehicle has `tank_l` and the request
  has `start_fuel`.
- `variant`: `fastest`, `shortest`, or `no_tolls` (avoids toll roads).

> This sample was produced with a stand-in routing engine (a fixed 460 km route) and the demo price data.

**Errors to expect:** `422` for invalid input or when no vehicle exists, `502` when the routing engine is down.
Both are described in [errors](errors.md). The call is **not idempotent**: sending it twice saves two trips.

```bash
export TRIP=59448440-1514-46c9-9006-447aa4b5946f      # the id from the answer
```

## Step 7. Read the history
```bash
curl "$BASE/trips?limit=10&offset=0" -H "Authorization: Bearer $TOKEN"
```
```json
{"items": [ { ...the trip... } ], "total": 1, "limit": 10, "offset": 0}
```
Newest first. `limit` is 1-100 (default 20). Fetch the next page with `offset=10`, and stop when
`offset + limit >= total`.

## Step 8. Open, rename and delete a trip
```bash
curl $BASE/trips/$TRIP -H "Authorization: Bearer $TOKEN"
curl -X PATCH $BASE/trips/$TRIP -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"title":"Warsaw business trip"}'
curl -X DELETE $BASE/trips/$TRIP -H "Authorization: Bearer $TOKEN"        # 204, no body
curl -i $BASE/trips/$TRIP -H "Authorization: Bearer $TOKEN"               # 404 not_found
```
Renaming changes only the title; the calculation never changes. Deleting a vehicle later does not change saved
trips either: each trip keeps its own copy of the vehicle data.

## Step 9. Renew the access token
When a protected call answers `401` after 15 minutes:
```bash
curl -X POST $BASE/auth/refresh -H "Content-Type: application/json" -d "{\"refresh_token\":\"$REFRESH\"}"
```
Store **both** new tokens. The old refresh token is now dead: sending it again returns `401`.

## Step 10. Sign out and clean up
```bash
curl -X POST $BASE/auth/logout -H "Content-Type: application/json" -d "{\"refresh_token\":\"$REFRESH\"}"   # 204
curl -X DELETE $BASE/users/me -H "Authorization: Bearer $TOKEN"        # 204, deletes the account and all its data
```

---

## The same in Python
```python
import httpx

api = httpx.Client(base_url="http://localhost:8000/api/v1")
tokens = api.post("/auth/register", json={"email": "tutorial@example.com",
                  "password": "correct-horse-battery", "privacy_accepted": True}).json()
auth = {"Authorization": f"Bearer {tokens['access_token']}"}
api.post("/vehicles", headers=auth, json={"name": "Family car", "fuel_type": "petrol95",
         "consumption_l100": 7.5, "tank_l": 50, "is_default": True}).raise_for_status()
trip = api.post("/trips", headers=auth, json={
    "origin_label": "Vilnius", "destination_label": "Warsaw",
    "origin": {"lon": 25.28, "lat": 54.69}, "destination": {"lon": 21.01, "lat": 52.23}}).json()
print(trip["total_eur"], [(l["country"], l["amount_eur"]) for l in trip["lines"]])
```
For the full version with error handling and cleanup run `python docs/api/examples/walkthrough.py`.

## Troubleshooting
| Symptom | Likely cause |
|---|---|
| `401 missing bearer token` | the `Authorization: Bearer ...` header is missing or misspelled |
| `401` after some time | the access token expired (15 min); refresh it |
| `422 validation_failed` | read `details`: it names each wrong field |
| `422 no vehicle` | add a vehicle first or pass an existing `vehicle_id` |
| `502 upstream_unavailable` | the routing engine (OSRM) is not running or has no map data |
| `404` on your own trip | the id belongs to another account, or the trip was deleted |
