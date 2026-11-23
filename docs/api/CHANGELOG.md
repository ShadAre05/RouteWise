# API changelog

The API follows the rules of [versioning](guides/conventions.md#versioning): compatible changes happen inside
`v1`, breaking changes need `v2`. The version of the specification is `info.version` in
[`openapi.yaml`](openapi.yaml).

## 1.0.0 - 2026-11-23
First published version of the specification.

**Accounts:** `POST /auth/register`, `POST /auth/login`, `POST /auth/refresh`, `POST /auth/logout`,
`POST /auth/change-password`, `DELETE /users/me`.
**Vehicles:** `GET/POST /vehicles`, `GET/PATCH/DELETE /vehicles/{vehicle_id}`.
**Trips:** `POST /trips`, `GET /trips`, `GET/PATCH/DELETE /trips/{trip_id}`.
**Reference data:** `GET /countries`, `/fuel-prices`, `/fuel-prices/{country}/trend`, `/speed-profiles`,
`/lodging-rates`.
**Service:** `GET /health`, `/health/ready`.

Documentation: request and response examples for every operation, generated endpoint reference and data models,
guides, mock-server instructions.

Known limits of this version: no rate limiting; no idempotency keys; no administration endpoints for tariffs and
rates (they are maintained in the database); fuel prices are loaded from files, not downloaded automatically.
