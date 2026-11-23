# Errors

Every failure, whatever its cause, has the same JSON shape:

```json
{
  "error": {
    "code": "not_found",
    "message": "trip not found",
    "details": null,
    "request_id": "8173e5fc155d4f08ba633d9eb52b45c1"
  }
}
```

| Field | Meaning |
|---|---|
| `code` | Stable machine-readable code. Branch on this, not on `message`. |
| `message` | Human-readable explanation in English; may be reworded, do not parse it. |
| `details` | For `validation_failed`: a list of `{ "field", "message" }`; otherwise `null`. |
| `request_id` | Correlation id, the same value as the `X-Request-ID` response header. Quote it when you report a problem; it appears in every server log line of the request. |

You can send your own `X-Request-ID` header (any unique string); the server uses and echoes it, so one id can
follow a call from your app through the API into the logs.

## Error taxonomy

| HTTP | `code` | When | What the client should do |
|---|---|---|---|
| 401 | `unauthorized` | Missing, malformed or expired access token; wrong e-mail or password (also while the account is locked); used or unknown refresh token | Refresh once; if that fails, sign in again. Do not retry a failed login in a loop. |
| 403 | `forbidden` | The role is not allowed to do this (reserved for administrator functions) | Do not retry. |
| 404 | `not_found` | The resource does not exist **or belongs to another user**; no price history for the country | Check the id. |
| 409 | `conflict` | The e-mail is already registered | Sign in instead, or use another e-mail. |
| 422 | `validation_failed` | A field is missing, has the wrong type or is out of range; the request names no usable vehicle | Fix the fields listed in `details`. |
| 422 | `no_route` | The routing engine finds no road between the two points | Choose points that are connected by road. |
| 502 | `upstream_unavailable` | The routing engine is not reachable or failed | Retry later with a pause (for example 5 s, then 30 s). |
| 500 | `internal_error` | An unexpected server error; the details are only in the server log | Retry once; if it persists, report the `request_id`. |

## Examples

**Validation (422)** - `POST /api/v1/auth/register` with an invalid body:
```json
{"error": {"code": "validation_failed", "message": "request validation failed",
  "details": [
    {"field": "email", "message": "value is not a valid email address: An email address must have an @-sign."},
    {"field": "password", "message": "String should have at least 8 characters"},
    {"field": "privacy_accepted", "message": "Input should be True"}
  ], "request_id": "98f7360ef372470c9a0e9b2d76b4eb51"}}
```
`field` is the path inside the body (`origin.lon` for a nested field) or the name of the path or query parameter.

**Missing token (401)** - `GET /api/v1/vehicles` without a header:
```json
{"error": {"code": "unauthorized", "message": "missing bearer token", "details": null, "request_id": "5b1a22d952fe434b8337ad101c3db651"}}
```

**Duplicate e-mail (409)**:
```json
{"error": {"code": "conflict", "message": "this e-mail is already registered", "details": null, "request_id": "6546e79962824a289cd311999fe8356c"}}
```

**Used refresh token (401)**:
```json
{"error": {"code": "unauthorized", "message": "invalid or expired refresh token", "details": null, "request_id": "8ea54903549c4b6abd9473027d5d4265"}}
```

**No vehicle for a trip (422)**:
```json
{"error": {"code": "validation_failed", "message": "no vehicle: add a vehicle or pass an existing vehicle_id", "details": null, "request_id": "9ac1d4b1f2394073b40d79683f1e3610"}}
```

**Routing engine down (502)**:
```json
{"error": {"code": "upstream_unavailable", "message": "routing engine is not available", "details": null, "request_id": "f568e0012f764565b4e281d22dec0284"}}
```

**Unknown country (404)** - `GET /api/v1/fuel-prices/XX/trend?fuel_type=petrol95`:
```json
{"error": {"code": "not_found", "message": "no price history for this country and fuel", "details": null, "request_id": "cc196d562a174482978049c78bf71243"}}
```

## Notes
- A request that is not JSON, or a path that does not exist, is answered in the same shape (for example
  `{"code": "not_found", "message": "Not Found"}` for an unknown path).
- Status codes follow HTTP semantics: `201` for creation, `204` with an empty body for deletion and logout.
- Failures of the server are logged with the full stack trace; the client only ever sees `internal_error` and the
  `request_id`, never internal details.
