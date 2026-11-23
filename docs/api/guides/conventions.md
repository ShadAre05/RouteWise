# Conventions and advanced topics

## Naming and formats
| Topic | Rule |
|---|---|
| Paths | lower-case, plural nouns, kebab-case: `/fuel-prices`, `/speed-profiles`, `/lodging-rates` |
| Fields | `snake_case`; units are in the name: `distance_km`, `consumption_l100`, `price_eur_l`, `daily_limit_h` |
| Identifiers | UUIDs (version 4) written as text |
| Money and measures | decimals as **strings** (`"50.46"`), euro, kilometres, litres |
| Dates | `date`: `2026-11-09`; timestamps: ISO 8601 with a UTC offset |
| Countries | ISO 3166-1 alpha-2 codes in upper case: `LT`, `PL`, `DE` |
| Enumerations | lower-case words: `petrol95`, `diesel`; `fastest`, `shortest`, `no_tolls` |

## Request flow and lifecycle
1. The request enters; the server assigns a **request id** (or keeps the `X-Request-ID` you sent).
2. For protected endpoints the access token is verified (signature, expiry). The user id is placed into the
   database transaction so row-level security limits every query to that user.
3. The body, path and query are validated; invalid input stops here with `422`.
4. The business logic runs (for a trip: routing, country split, prices, schedule, saving).
5. One database transaction commits, or everything is rolled back.
6. The answer carries `X-Request-ID`; one structured log line is written.

## Pagination
Only the trip history is paged: `GET /trips?limit=20&offset=0`. `limit` is 1-100 (default 20), `offset` starts
at 0. The answer contains `items`, `total`, `limit` and `offset`. The list is ordered newest first and the
`total` counts all trips of the user, so the client can compute the number of pages. Offset paging can show a
trip twice or skip one if trips are added while the client pages; for a history screen that is acceptable.

## Idempotency
| Call | Repeating it |
|---|---|
| `GET` | safe, no effect |
| `PATCH /trips/{id}`, `PATCH /vehicles/{id}` | same result every time |
| `DELETE ...` | first call `204`, later calls `404` (the resource is gone) |
| `POST /auth/logout` | always `204`, safe to repeat |
| `POST /auth/refresh` | **not repeatable**: the refresh token works once |
| `POST /trips`, `POST /vehicles`, `POST /auth/register` | **not idempotent**: each call creates a new resource (register answers `409` the second time). The API has no idempotency-key header; a client that retries after a timeout should first read the history. |

## Rate limits
The service does **not** limit the request rate yet. The only protection against abuse today is the account
lockout after 5 failed logins (10 minutes). When limits are added they will be announced in the
[changelog](../CHANGELOG.md) and signalled with `429` and a `Retry-After` header.

## CORS
Browsers may call the API only from origins listed in the `CORS_ORIGINS` setting of the server. The mobile
application is not affected by CORS.

## Versioning
- The major version is part of the path: `/api/v1`.
- **Compatible changes** (new endpoints, new optional request fields, new response fields, new enumeration
  values in responses) are made inside `v1`. Clients must ignore response fields they do not know.
- **Breaking changes** (removing or renaming a field or endpoint, changing a type or a meaning) require a new
  path `/api/v2`; `v1` keeps working for a transition period that is announced in the changelog.
- The OpenAPI document carries the specification version (`info.version`); every change of the API is listed in
  [`CHANGELOG.md`](../CHANGELOG.md).

## Observability for integrators
- Every response has `X-Request-ID`; quote it in bug reports.
- `/health` (liveness) and `/health/ready` (readiness, checks the database) are intended for monitors and
  container health checks.
