# Endpoint reference

<!-- Generated from openapi.yaml by backend/scripts/render_reference.py. Do not edit by hand. -->

Every endpoint with its parameters, request body, responses and examples. Base path of the versioned
endpoints is `/api/v1`. Models are described in [data-models.md](data-models.md); errors in
[the error guide](../guides/errors.md).

## service

Health checks for monitoring.

### GET /health

**Liveness: the process is running**

Answers 200 while the process runs. Use it for container liveness probes; it does not touch the database.

Authentication: none (public).

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 200 | Success | object |

Example, status 200:

*Process is alive*

```json
{
  "status": "ok"
}
```

### GET /health/ready

**Readiness: the database answers**

Answers 200 when the database accepts a query, otherwise 500. Use it for readiness probes.

Authentication: none (public).

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 200 | Success | object |

Example, status 200:

*Database answers*

```json
{
  "status": "ready"
}
```

## auth

Accounts, tokens and password.

### POST /api/v1/auth/change-password

**Change the password (US-19)**

Changes the password of the signed-in user and revokes all refresh tokens. Requires the old password.

Authentication: `Authorization: Bearer <access token>` required.

**Request body** - [ChangePasswordRequest](data-models.md#changepasswordrequest)

| Field | Type | Required | Description |
|---|---|---|---|
| `old_password` | string | yes | Min length 1; max length 128 Example: `"correct-horse-battery"`. |
| `new_password` | string | yes | Min length 8; max length 128 Example: `"even-better-passphrase"`. |

*Change the password*

```json
{
  "old_password": "correct-horse-battery",
  "new_password": "even-better-passphrase"
}
```

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 204 | Success, no content | empty |
| 401 | Missing, malformed or expired access token, or wrong login credentials | [ErrorResponse](data-models.md#errorresponse) |
| 422 | Invalid input (`details` lists the fields) or no route between the points | [ErrorResponse](data-models.md#errorresponse) |

Example, status 401:

*The old password is wrong*

```json
{"error": {"code": "unauthorized", "message": "the current password is wrong", "details": null, "request_id": "fb956027107b4a219803813b9a854540"}}
```

*No Authorization header*

```json
{"error": {"code": "unauthorized", "message": "missing bearer token", "details": null, "request_id": "fb956027107b4a219803813b9a854540"}}
```

*Expired or malformed token: refresh it, then sign in again*

```json
{"error": {"code": "unauthorized", "message": "invalid or expired token", "details": null, "request_id": "fb956027107b4a219803813b9a854540"}}
```

Example, status 422:

*New password too short*

```json
{"error": {"code": "validation_failed", "message": "request validation failed", "details": [{"field": "new_password", "message": "String should have at least 8 characters"}], "request_id": "fb956027107b4a219803813b9a854540"}}
```

### POST /api/v1/auth/login

**Sign in**

Checks the e-mail and password and returns a token pair.

After 5 failed attempts the account is locked for 10 minutes; the answer is then the same 401 as for a
wrong password, so an attacker learns nothing.

Authentication: none (public).

**Request body** - [LoginRequest](data-models.md#loginrequest)

| Field | Type | Required | Description |
|---|---|---|---|
| `email` | string (email) | yes | Example: `"driver@example.com"`. |
| `password` | string | yes | Min length 1; max length 128 Example: `"correct-horse-battery"`. |

*Sign in*

```json
{
  "email": "driver@example.com",
  "password": "correct-horse-battery"
}
```

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 200 | Success | [TokenResponse](data-models.md#tokenresponse) |
| 401 | Missing, malformed or expired access token, or wrong login credentials | [ErrorResponse](data-models.md#errorresponse) |
| 422 | Invalid input (`details` lists the fields) or no route between the points | [ErrorResponse](data-models.md#errorresponse) |

Example, status 200:

*Signed in*

```json
{
  "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOi...",
  "refresh_token": "5ceIFcTIm95XFCvLwSZy4tZPOONIK4T3f7nYpJD9OxVp3VxwBd662d1j4zchTofv",
  "token_type": "bearer",
  "expires_in": 900
}
```

Example, status 401:

*Wrong e-mail or password (also while the account is locked)*

```json
{"error": {"code": "unauthorized", "message": "invalid e-mail or password", "details": null, "request_id": "fb956027107b4a219803813b9a854540"}}
```

Example, status 422:

*Example*

```json
{"error": {"code": "validation_failed", "message": "request validation failed", "details": null, "request_id": "fb956027107b4a219803813b9a854540"}}
```

### POST /api/v1/auth/logout

**Revoke a refresh token**

Revokes a refresh token. Always answers 204, also for an unknown or already revoked token, so the call
is safe to repeat.

Authentication: none (public).

**Request body** - [RefreshRequest](data-models.md#refreshrequest)

| Field | Type | Required | Description |
|---|---|---|---|
| `refresh_token` | string | yes | The refresh token received at login; it can be used once (min length 20; max length 200) Example: `"5ceIFcTIm95XFCvLwSZy4tZPOONIK4T3f7nYpJD9OxVp3VxwBd662d1j4zchTofv"`. |

*Revoke a refresh token*

```json
{
  "refresh_token": "5ceIFcTIm95XFCvLwSZy4tZPOONIK4T3f7nYpJD9OxVp3VxwBd662d1j4zchTofv"
}
```

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 204 | Success, no content | empty |
| 422 | Invalid input (`details` lists the fields) or no route between the points | [ErrorResponse](data-models.md#errorresponse) |

Example, status 422:

*Example*

```json
{"error": {"code": "validation_failed", "message": "request validation failed", "details": null, "request_id": "fb956027107b4a219803813b9a854540"}}
```

### POST /api/v1/auth/refresh

**Exchange a refresh token**

Exchanges a refresh token for a new token pair. Every refresh token works **once** (rotation): the old
one is revoked, so a stolen and already used token is useless.

Authentication: none (public).

**Request body** - [RefreshRequest](data-models.md#refreshrequest)

| Field | Type | Required | Description |
|---|---|---|---|
| `refresh_token` | string | yes | The refresh token received at login; it can be used once (min length 20; max length 200) Example: `"5ceIFcTIm95XFCvLwSZy4tZPOONIK4T3f7nYpJD9OxVp3VxwBd662d1j4zchTofv"`. |

*Renew the tokens*

```json
{
  "refresh_token": "5ceIFcTIm95XFCvLwSZy4tZPOONIK4T3f7nYpJD9OxVp3VxwBd662d1j4zchTofv"
}
```

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 200 | Success | [TokenResponse](data-models.md#tokenresponse) |
| 401 | Missing, malformed or expired access token, or wrong login credentials | [ErrorResponse](data-models.md#errorresponse) |
| 422 | Invalid input (`details` lists the fields) or no route between the points | [ErrorResponse](data-models.md#errorresponse) |

Example, status 200:

*New token pair; the old refresh token is dead*

```json
{
  "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOi...",
  "refresh_token": "5ceIFcTIm95XFCvLwSZy4tZPOONIK4T3f7nYpJD9OxVp3VxwBd662d1j4zchTofv",
  "token_type": "bearer",
  "expires_in": 900
}
```

Example, status 401:

*Unknown, used or expired refresh token*

```json
{"error": {"code": "unauthorized", "message": "invalid or expired refresh token", "details": null, "request_id": "fb956027107b4a219803813b9a854540"}}
```

Example, status 422:

*Example*

```json
{"error": {"code": "validation_failed", "message": "request validation failed", "details": null, "request_id": "fb956027107b4a219803813b9a854540"}}
```

### POST /api/v1/auth/register

**Create an account**

Creates an account and signs the user in. The password is hashed inside PostgreSQL (bcrypt); it is never
stored or logged.

Returns an access token (15 minutes) and a single-use refresh token. Errors: 409 when the e-mail is
taken, 422 when a field is invalid.

Authentication: none (public).

**Request body** - [RegisterRequest](data-models.md#registerrequest)

| Field | Type | Required | Description |
|---|---|---|---|
| `email` | string (email) | yes | Account e-mail; stored in lower case, must be unique Example: `"driver@example.com"`. |
| `password` | string | yes | 8-128 characters (min length 8; max length 128) Example: `"correct-horse-battery"`. |
| `privacy_accepted` | `true` | yes | Must be true: the privacy notice has to be accepted (US-01) |

*New account*

```json
{
  "email": "driver@example.com",
  "password": "correct-horse-battery",
  "privacy_accepted": true
}
```

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 201 | Created | [TokenResponse](data-models.md#tokenresponse) |
| 409 | Conflict with existing data (for example the e-mail is already registered) | [ErrorResponse](data-models.md#errorresponse) |
| 422 | Invalid input (`details` lists the fields) or no route between the points | [ErrorResponse](data-models.md#errorresponse) |

Example, status 201:

*Account created*

```json
{
  "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOi...",
  "refresh_token": "5ceIFcTIm95XFCvLwSZy4tZPOONIK4T3f7nYpJD9OxVp3VxwBd662d1j4zchTofv",
  "token_type": "bearer",
  "expires_in": 900
}
```

Example, status 409:

*E-mail already registered*

```json
{"error": {"code": "conflict", "message": "this e-mail is already registered", "details": null, "request_id": "fb956027107b4a219803813b9a854540"}}
```

Example, status 422:

*Several invalid fields*

```json
{"error": {"code": "validation_failed", "message": "request validation failed", "details": [{"field": "email", "message": "value is not a valid email address: An email address must have an @-sign."}, {"field": "password", "message": "String should have at least 8 characters"}, {"field": "privacy_accepted", "message": "Input should be True"}], "request_id": "fb956027107b4a219803813b9a854540"}}
```

### DELETE /api/v1/users/me

**Delete the account and all its data (US-20)**

Permanently deletes the account with its vehicles, trips and tokens (US-20). Cannot be undone.

Authentication: `Authorization: Bearer <access token>` required.

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 204 | Success, no content | empty |
| 401 | Missing, malformed or expired access token, or wrong login credentials | [ErrorResponse](data-models.md#errorresponse) |

Example, status 401:

*No Authorization header*

```json
{"error": {"code": "unauthorized", "message": "missing bearer token", "details": null, "request_id": "fb956027107b4a219803813b9a854540"}}
```

*Expired or malformed token: refresh it, then sign in again*

```json
{"error": {"code": "unauthorized", "message": "invalid or expired token", "details": null, "request_id": "fb956027107b4a219803813b9a854540"}}
```

## vehicles

The user's vehicles (consumption, tank, default).

### GET /api/v1/vehicles

**List my vehicles**

The signed-in user's vehicles.

Authentication: `Authorization: Bearer <access token>` required.

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 200 | Success | array of [VehicleOut](data-models.md#vehicleout) |
| 401 | Missing, malformed or expired access token, or wrong login credentials | [ErrorResponse](data-models.md#errorresponse) |

Example, status 200:

*Two vehicles*

```json
[
  {
    "id": "7f59951e-fa3a-48e6-97f8-bcaeaadf44b9",
    "name": "Family car",
    "fuel_type": "petrol95",
    "consumption_l100": "7.5",
    "tank_l": "50.0",
    "is_default": true,
    "created_at": "2026-11-23T14:51:02.755411+02:00",
    "updated_at": "2026-11-23T14:51:02.755411+02:00"
  },
  {
    "id": "335b9b1c-9a9d-449b-bb2a-0f4e4974e1f9",
    "name": "Diesel van",
    "fuel_type": "diesel",
    "consumption_l100": "8.2",
    "tank_l": null,
    "is_default": false,
    "created_at": "2026-11-23T14:51:02.782024+02:00",
    "updated_at": "2026-11-23T14:51:02.803658+02:00"
  }
]
```

Example, status 401:

*No Authorization header*

```json
{"error": {"code": "unauthorized", "message": "missing bearer token", "details": null, "request_id": "fb956027107b4a219803813b9a854540"}}
```

*Expired or malformed token: refresh it, then sign in again*

```json
{"error": {"code": "unauthorized", "message": "invalid or expired token", "details": null, "request_id": "fb956027107b4a219803813b9a854540"}}
```

### POST /api/v1/vehicles

**Add a vehicle (US-03)**

Adds a vehicle. If `is_default` is true, the previous default vehicle stops being the default. The first
vehicle becomes the default automatically.

Authentication: `Authorization: Bearer <access token>` required.

**Request body** - [VehicleCreate](data-models.md#vehiclecreate)

| Field | Type | Required | Description |
|---|---|---|---|
| `name` | string | yes | Display name, 1-60 characters (min length 1; max length 60) Example: `"Family car"`. |
| `fuel_type` | one of `petrol95`, `diesel` | yes | petrol95 or diesel |
| `consumption_l100` | decimal (number or numeric string) | yes | Litres per 100 km, 2.0-30.0 (min 2.0; max 30.0) Example: `7.5`. |
| `tank_l` | decimal (number or numeric string), nullable | no | Tank size in litres, 20-120; enables the refuel plan (min 20.0; max 120.0) Example: `50`. |
| `is_default` | boolean | no | Use this vehicle when a trip names none; at most one vehicle is the default (default False) |

*Petrol car with a known tank*

```json
{
  "name": "Family car",
  "fuel_type": "petrol95",
  "consumption_l100": 7.5,
  "tank_l": 50,
  "is_default": true
}
```

*Diesel van without a tank size*

```json
{
  "name": "Diesel van",
  "fuel_type": "diesel",
  "consumption_l100": 8.2
}
```

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 201 | Created | [VehicleOut](data-models.md#vehicleout) |
| 401 | Missing, malformed or expired access token, or wrong login credentials | [ErrorResponse](data-models.md#errorresponse) |
| 422 | Invalid input (`details` lists the fields) or no route between the points | [ErrorResponse](data-models.md#errorresponse) |

Example, status 201:

*Vehicle created*

```json
{
  "id": "7f59951e-fa3a-48e6-97f8-bcaeaadf44b9",
  "name": "Family car",
  "fuel_type": "petrol95",
  "consumption_l100": "7.5",
  "tank_l": "50.0",
  "is_default": true,
  "created_at": "2026-11-23T14:51:02.755411+02:00",
  "updated_at": "2026-11-23T14:51:02.755411+02:00"
}
```

Example, status 401:

*No Authorization header*

```json
{"error": {"code": "unauthorized", "message": "missing bearer token", "details": null, "request_id": "fb956027107b4a219803813b9a854540"}}
```

*Expired or malformed token: refresh it, then sign in again*

```json
{"error": {"code": "unauthorized", "message": "invalid or expired token", "details": null, "request_id": "fb956027107b4a219803813b9a854540"}}
```

Example, status 422:

*Invalid vehicle*

```json
{"error": {"code": "validation_failed", "message": "request validation failed", "details": [{"field": "name", "message": "String should have at least 1 character"}, {"field": "fuel_type", "message": "Input should be 'petrol95' or 'diesel'"}, {"field": "consumption_l100", "message": "Input should be greater than or equal to 2.0"}], "request_id": "fb956027107b4a219803813b9a854540"}}
```

### GET /api/v1/vehicles/{vehicle_id}

**Get a vehicle**

Returns one vehicle. A vehicle of another user answers 404.

Authentication: `Authorization: Bearer <access token>` required.

| Parameter | In | Required | Type | Description |
|---|---|---|---|---|
| `vehicle_id` | path | yes | string (uuid) |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 200 | Success | [VehicleOut](data-models.md#vehicleout) |
| 401 | Missing, malformed or expired access token, or wrong login credentials | [ErrorResponse](data-models.md#errorresponse) |
| 404 | The resource does not exist or belongs to another user | [ErrorResponse](data-models.md#errorresponse) |
| 422 | Invalid input (`details` lists the fields) or no route between the points | [ErrorResponse](data-models.md#errorresponse) |

Example, status 200:

*One vehicle*

```json
{
  "id": "7f59951e-fa3a-48e6-97f8-bcaeaadf44b9",
  "name": "Family car",
  "fuel_type": "petrol95",
  "consumption_l100": "7.5",
  "tank_l": "50.0",
  "is_default": true,
  "created_at": "2026-11-23T14:51:02.755411+02:00",
  "updated_at": "2026-11-23T14:51:02.755411+02:00"
}
```

Example, status 401:

*No Authorization header*

```json
{"error": {"code": "unauthorized", "message": "missing bearer token", "details": null, "request_id": "fb956027107b4a219803813b9a854540"}}
```

*Expired or malformed token: refresh it, then sign in again*

```json
{"error": {"code": "unauthorized", "message": "invalid or expired token", "details": null, "request_id": "fb956027107b4a219803813b9a854540"}}
```

Example, status 404:

*Unknown vehicle or one of another user*

```json
{"error": {"code": "not_found", "message": "vehicle not found", "details": null, "request_id": "fb956027107b4a219803813b9a854540"}}
```

Example, status 422:

*Example*

```json
{"error": {"code": "validation_failed", "message": "request validation failed", "details": null, "request_id": "fb956027107b4a219803813b9a854540"}}
```

### PATCH /api/v1/vehicles/{vehicle_id}

**Edit a vehicle**

Partial update: send only the fields that change.

Authentication: `Authorization: Bearer <access token>` required.

| Parameter | In | Required | Type | Description |
|---|---|---|---|---|
| `vehicle_id` | path | yes | string (uuid) |  |

**Request body** - [VehicleUpdate](data-models.md#vehicleupdate)

| Field | Type | Required | Description |
|---|---|---|---|
| `name` | string, nullable | no | Min length 1; max length 60 |
| `fuel_type` | one of `petrol95`, `diesel`, nullable | no |  |
| `consumption_l100` | decimal (number or numeric string), nullable | no | Min 2.0; max 30.0 |
| `tank_l` | decimal (number or numeric string), nullable | no | Min 20.0; max 120.0 |
| `is_default` | boolean, nullable | no |  |

*Change the consumption and make it the default*

```json
{
  "consumption_l100": 8.0,
  "is_default": true
}
```

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 200 | Success | [VehicleOut](data-models.md#vehicleout) |
| 401 | Missing, malformed or expired access token, or wrong login credentials | [ErrorResponse](data-models.md#errorresponse) |
| 404 | The resource does not exist or belongs to another user | [ErrorResponse](data-models.md#errorresponse) |
| 422 | Invalid input (`details` lists the fields) or no route between the points | [ErrorResponse](data-models.md#errorresponse) |

Example, status 200:

*Updated vehicle*

```json
{
  "id": "335b9b1c-9a9d-449b-bb2a-0f4e4974e1f9",
  "name": "Diesel van",
  "fuel_type": "diesel",
  "consumption_l100": "8.0",
  "tank_l": null,
  "is_default": true,
  "created_at": "2026-11-23T14:51:02.782024+02:00",
  "updated_at": "2026-11-23T14:51:02.803658+02:00"
}
```

Example, status 401:

*No Authorization header*

```json
{"error": {"code": "unauthorized", "message": "missing bearer token", "details": null, "request_id": "fb956027107b4a219803813b9a854540"}}
```

*Expired or malformed token: refresh it, then sign in again*

```json
{"error": {"code": "unauthorized", "message": "invalid or expired token", "details": null, "request_id": "fb956027107b4a219803813b9a854540"}}
```

Example, status 404:

*Unknown vehicle or one of another user*

```json
{"error": {"code": "not_found", "message": "vehicle not found", "details": null, "request_id": "fb956027107b4a219803813b9a854540"}}
```

Example, status 422:

*Example*

```json
{"error": {"code": "validation_failed", "message": "request validation failed", "details": null, "request_id": "fb956027107b4a219803813b9a854540"}}
```

### DELETE /api/v1/vehicles/{vehicle_id}

**Delete a vehicle (its trips stay in the history, US-04)**

Deletes a vehicle. Saved trips stay in the history with their own copy of the vehicle data (US-04).

Authentication: `Authorization: Bearer <access token>` required.

| Parameter | In | Required | Type | Description |
|---|---|---|---|---|
| `vehicle_id` | path | yes | string (uuid) |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 204 | Success, no content | empty |
| 401 | Missing, malformed or expired access token, or wrong login credentials | [ErrorResponse](data-models.md#errorresponse) |
| 404 | The resource does not exist or belongs to another user | [ErrorResponse](data-models.md#errorresponse) |
| 422 | Invalid input (`details` lists the fields) or no route between the points | [ErrorResponse](data-models.md#errorresponse) |

Example, status 401:

*No Authorization header*

```json
{"error": {"code": "unauthorized", "message": "missing bearer token", "details": null, "request_id": "fb956027107b4a219803813b9a854540"}}
```

*Expired or malformed token: refresh it, then sign in again*

```json
{"error": {"code": "unauthorized", "message": "invalid or expired token", "details": null, "request_id": "fb956027107b4a219803813b9a854540"}}
```

Example, status 404:

*Unknown vehicle or one of another user*

```json
{"error": {"code": "not_found", "message": "vehicle not found", "details": null, "request_id": "fb956027107b4a219803813b9a854540"}}
```

Example, status 422:

*Example*

```json
{"error": {"code": "validation_failed", "message": "request validation failed", "details": null, "request_id": "fb956027107b4a219803813b9a854540"}}
```

## trips

Cost calculation and the history of saved trips.

### GET /api/v1/trips

**Trip history, newest first (US-17)**

Returns the signed-in user's saved trips, newest first, in pages (`limit` 1-100, default 20; `offset` from 0).

Authentication: `Authorization: Bearer <access token>` required.

| Parameter | In | Required | Type | Description |
|---|---|---|---|---|
| `limit` | query | no | integer |  (min 1; max 100; default 20) |
| `offset` | query | no | integer |  (min 0; default 0) |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 200 | Success | [TripPage](data-models.md#trippage) |
| 401 | Missing, malformed or expired access token, or wrong login credentials | [ErrorResponse](data-models.md#errorresponse) |
| 422 | Invalid input (`details` lists the fields) or no route between the points | [ErrorResponse](data-models.md#errorresponse) |

Example, status 200:

*History page*

```json
{
  "items": [
    {
      "id": "fb05908d-3144-4e7b-93f3-3bb4b1d28814",
      "title": "Warsaw to Berlin in two days",
      "origin_label": "Warsaw",
      "destination_label": "Berlin",
      "departed_at": "2026-11-23T14:51:02.860132+02:00",
      "arrival_at": "2026-11-24T16:15:09.568207+02:00",
      "destination_country": "DE",
      "variant": "no_tolls",
      "distance_km": "460.0",
      "duration_min": 324,
      "daily_limit_h": "4.0",
      "nights": 1,
      "vehicle_id": "335b9b1c-9a9d-449b-bb2a-0f4e4974e1f9",
      "vehicle_name": "Diesel van",
      "fuel_type": "diesel",
      "consumption_l100": "8.0",
      "start_fuel": null,
      "refuel_saving_eur": null,
      "price_date": "2026-11-09",
      "total_eur": "121.84",
      "created_at": "2026-11-23T14:51:02.823060+02:00",
      "lines": [
        {
          "kind": "fuel",
          "country": "PL",
          "distance_km": "388.2",
          "unit_price": "1.360",
          "amount_eur": "42.24",
          "status": "ok"
        },
        {
          "kind": "fuel",
          "country": "DE",
          "distance_km": "71.8",
          "unit_price": "1.672",
          "amount_eur": "9.60",
          "status": "ok"
        },
        {
          "kind": "lodging",
          "country": "DE",
          "distance_km": null,
          "unit_price": null,
          "amount_eur": "70.00",
          "status": "user_entered"
        }
      ],
      "refuel_stops": []
    },
    {
      "id": "6a1d0a76-0929-4e21-8aab-57a495838da8",
      "title": "Vilnius - Warsaw",
      "origin_label": "Vilnius",
      "destination_label": "Warsaw",
      "departed_at": "2026-11-23T14:36:57.556210+02:00",
      "arrival_at": "2026-11-23T20:12:13.627639+02:00",
      "destination_country": "PL",
      "variant": "fastest",
      "distance_km": "460.0",
      "duration_min": 335,
      "daily_limit_h": "8.0",
      "nights": 0,
      "vehicle_id": "0389ba65-4fb7-453b-bf24-696b087c62d7",
      "vehicle_name": "Family car",
      "fuel_type": "petrol95",
      "consumption_l100": "7.5",
      "start_fuel": "half",
      "refuel_saving_eur": "0.00",
      "price_date": "2026-11-09",
      "total_eur": "50.46",
      "created_at": "2026-11-23T14:36:57.485214+02:00",
      "lines": [
        {
          "kind": "fuel",
          "country": "LT",
          "distance_km": "187.5",
          "unit_price": "1.554",
          "amount_eur": "21.85",
          "status": "ok"
        },
        {
          "kind": "fuel",
          "country": "PL",
          "distance_km": "272.5",
          "unit_price": "1.400",
          "amount_eur": "28.61",
          "status": "ok"
        }
      ],
      "refuel_stops": [
        {
          "seq": 1,
          "country": "PL",
          "km_from_start": "187.5",
          "liters": "14.5",
          "price_eur_l": "1.400"
        }
      ]
    }
  ],
  "total": 2,
  "limit": 20,
  "offset": 0
}
```

Example, status 401:

*No Authorization header*

```json
{"error": {"code": "unauthorized", "message": "missing bearer token", "details": null, "request_id": "fb956027107b4a219803813b9a854540"}}
```

*Expired or malformed token: refresh it, then sign in again*

```json
{"error": {"code": "unauthorized", "message": "invalid or expired token", "details": null, "request_id": "fb956027107b4a219803813b9a854540"}}
```

Example, status 422:

*Page size too large*

```json
{"error": {"code": "validation_failed", "message": "request validation failed", "details": [{"field": "limit", "message": "Input should be less than or equal to 100"}], "request_id": "fb956027107b4a219803813b9a854540"}}
```

### POST /api/v1/trips

**Calculate the cost of a trip and save it to the history (US-05 ... US-16)**

Asks the routing engine for the route, splits it by country, prices fuel, road charges and lodging,
plans the days and an optional refuel stop, and saves the result to the history in one transaction.

The coordinates are used for routing only and are never stored. The response is the saved trip with its
cost lines. Errors: 422 for invalid input, when no route exists, or when there is no vehicle (none added,
or `vehicle_id` is unknown or belongs to someone else), 502 when the routing engine is not available.

Authentication: `Authorization: Bearer <access token>` required.

**Request body** - [TripCreate](data-models.md#tripcreate)

| Field | Type | Required | Description |
|---|---|---|---|
| `origin_label` | string | yes | Text shown for the start; only the label is stored, not the coordinates (min length 1; max length 120) Example: `"Vilnius"`. |
| `destination_label` | string | yes | Min length 1; max length 120 Example: `"Warsaw"`. |
| `origin` | [Point](data-models.md#point) | yes | Start point; used for routing only and never stored |
| `destination` | [Point](data-models.md#point) | yes | End point; used for routing only and never stored |
| `variant` | one of `fastest`, `shortest`, `no_tolls` | no | fastest, shortest (shortest of the alternatives) or no_tolls (toll roads avoided) (default fastest) |
| `vehicle_id` | string (uuid), nullable | no | Omit to use the default vehicle |
| `daily_limit_h` | decimal (number or numeric string), nullable | no | Maximum driving hours per day (4.0-10.0); splits the trip into days and nights (min 4.0; max 10.0) Example: `8`. |
| `lodging_class` | one of `economy`, `standard`, `comfort` | no | Class of the typical nightly price: economy, standard or comfort (default standard) |
| `lodging_entered_eur` | decimal (number or numeric string), nullable | no | Your own price per night; replaces the estimate (greater than 0.0) Example: `65.0`. |
| `start_fuel` | one of `full`, `three_quarter`, `half`, `quarter`, nullable | no | Fuel level at departure (full, three_quarter, half, quarter); enables the refuel plan |
| `title` | string, nullable | no | Name in the history; defaults to 'origin - destination' (min length 1; max length 120) |

*One day, refuel advice*

```json
{
  "origin_label": "Vilnius",
  "destination_label": "Warsaw",
  "origin": {
    "lon": 25.28,
    "lat": 54.69
  },
  "destination": {
    "lon": 21.01,
    "lat": 52.23
  },
  "variant": "fastest",
  "daily_limit_h": 8,
  "lodging_class": "standard",
  "start_fuel": "half"
}
```

*Two days, own price for the night, no toll roads*

```json
{
  "origin_label": "Warsaw",
  "destination_label": "Berlin",
  "origin": {
    "lon": 21.01,
    "lat": 52.23
  },
  "destination": {
    "lon": 13.4,
    "lat": 52.52
  },
  "variant": "no_tolls",
  "daily_limit_h": 4,
  "lodging_class": "comfort",
  "lodging_entered_eur": 70,
  "title": "Warsaw to Berlin in two days"
}
```

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 201 | Created | [TripOut](data-models.md#tripout) |
| 401 | Missing, malformed or expired access token, or wrong login credentials | [ErrorResponse](data-models.md#errorresponse) |
| 422 | Invalid input (`details` lists the fields) or no route between the points | [ErrorResponse](data-models.md#errorresponse) |
| 502 | The routing engine is not reachable | [ErrorResponse](data-models.md#errorresponse) |

Example, status 201:

*Vilnius - Warsaw in one day with a refuel stop*

```json
{
  "id": "6a1d0a76-0929-4e21-8aab-57a495838da8",
  "title": "Vilnius - Warsaw",
  "origin_label": "Vilnius",
  "destination_label": "Warsaw",
  "departed_at": "2026-11-23T14:36:57.556210+02:00",
  "arrival_at": "2026-11-23T20:12:13.627639+02:00",
  "destination_country": "PL",
  "variant": "fastest",
  "distance_km": "460.0",
  "duration_min": 335,
  "daily_limit_h": "8.0",
  "nights": 0,
  "vehicle_id": "0389ba65-4fb7-453b-bf24-696b087c62d7",
  "vehicle_name": "Family car",
  "fuel_type": "petrol95",
  "consumption_l100": "7.5",
  "start_fuel": "half",
  "refuel_saving_eur": "0.00",
  "price_date": "2026-11-09",
  "total_eur": "50.46",
  "created_at": "2026-11-23T14:36:57.485214+02:00",
  "lines": [
    {
      "kind": "fuel",
      "country": "LT",
      "distance_km": "187.5",
      "unit_price": "1.554",
      "amount_eur": "21.85",
      "status": "ok"
    },
    {
      "kind": "fuel",
      "country": "PL",
      "distance_km": "272.5",
      "unit_price": "1.400",
      "amount_eur": "28.61",
      "status": "ok"
    }
  ],
  "refuel_stops": [
    {
      "seq": 1,
      "country": "PL",
      "km_from_start": "187.5",
      "liters": "14.5",
      "price_eur_l": "1.400"
    }
  ]
}
```

*Warsaw - Berlin with one night and the user's own price*

```json
{
  "id": "fb05908d-3144-4e7b-93f3-3bb4b1d28814",
  "title": "Warsaw to Berlin in two days",
  "origin_label": "Warsaw",
  "destination_label": "Berlin",
  "departed_at": "2026-11-23T14:51:02.860132+02:00",
  "arrival_at": "2026-11-24T16:15:09.568207+02:00",
  "destination_country": "DE",
  "variant": "no_tolls",
  "distance_km": "460.0",
  "duration_min": 324,
  "daily_limit_h": "4.0",
  "nights": 1,
  "vehicle_id": "335b9b1c-9a9d-449b-bb2a-0f4e4974e1f9",
  "vehicle_name": "Diesel van",
  "fuel_type": "diesel",
  "consumption_l100": "8.0",
  "start_fuel": null,
  "refuel_saving_eur": null,
  "price_date": "2026-11-09",
  "total_eur": "121.84",
  "created_at": "2026-11-23T14:51:02.823060+02:00",
  "lines": [
    {
      "kind": "fuel",
      "country": "PL",
      "distance_km": "388.2",
      "unit_price": "1.360",
      "amount_eur": "42.24",
      "status": "ok"
    },
    {
      "kind": "fuel",
      "country": "DE",
      "distance_km": "71.8",
      "unit_price": "1.672",
      "amount_eur": "9.60",
      "status": "ok"
    },
    {
      "kind": "lodging",
      "country": "DE",
      "distance_km": null,
      "unit_price": null,
      "amount_eur": "70.00",
      "status": "user_entered"
    }
  ],
  "refuel_stops": []
}
```

Example, status 401:

*No Authorization header*

```json
{"error": {"code": "unauthorized", "message": "missing bearer token", "details": null, "request_id": "fb956027107b4a219803813b9a854540"}}
```

*Expired or malformed token: refresh it, then sign in again*

```json
{"error": {"code": "unauthorized", "message": "invalid or expired token", "details": null, "request_id": "fb956027107b4a219803813b9a854540"}}
```

Example, status 422:

*Invalid coordinates and daily limit*

```json
{"error": {"code": "validation_failed", "message": "request validation failed", "details": [{"field": "origin.lon", "message": "Input should be less than or equal to 180"}, {"field": "daily_limit_h", "message": "Input should be greater than or equal to 4.0"}], "request_id": "fb956027107b4a219803813b9a854540"}}
```

*No vehicle added and none named*

```json
{"error": {"code": "validation_failed", "message": "no vehicle: add a vehicle or pass an existing vehicle_id", "details": null, "request_id": "fb956027107b4a219803813b9a854540"}}
```

Example, status 502:

*Routing engine not reachable; nothing is saved*

```json
{"error": {"code": "upstream_unavailable", "message": "routing engine is not available", "details": null, "request_id": "fb956027107b4a219803813b9a854540"}}
```

### GET /api/v1/trips/{trip_id}

**Trip details**

Returns one saved trip with its cost lines and refuel stops. A trip of another user answers 404.

Authentication: `Authorization: Bearer <access token>` required.

| Parameter | In | Required | Type | Description |
|---|---|---|---|---|
| `trip_id` | path | yes | string (uuid) |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 200 | Success | [TripOut](data-models.md#tripout) |
| 401 | Missing, malformed or expired access token, or wrong login credentials | [ErrorResponse](data-models.md#errorresponse) |
| 404 | The resource does not exist or belongs to another user | [ErrorResponse](data-models.md#errorresponse) |
| 422 | Invalid input (`details` lists the fields) or no route between the points | [ErrorResponse](data-models.md#errorresponse) |

Example, status 200:

*One saved trip*

```json
{
  "id": "6a1d0a76-0929-4e21-8aab-57a495838da8",
  "title": "Vilnius - Warsaw",
  "origin_label": "Vilnius",
  "destination_label": "Warsaw",
  "departed_at": "2026-11-23T14:36:57.556210+02:00",
  "arrival_at": "2026-11-23T20:12:13.627639+02:00",
  "destination_country": "PL",
  "variant": "fastest",
  "distance_km": "460.0",
  "duration_min": 335,
  "daily_limit_h": "8.0",
  "nights": 0,
  "vehicle_id": "0389ba65-4fb7-453b-bf24-696b087c62d7",
  "vehicle_name": "Family car",
  "fuel_type": "petrol95",
  "consumption_l100": "7.5",
  "start_fuel": "half",
  "refuel_saving_eur": "0.00",
  "price_date": "2026-11-09",
  "total_eur": "50.46",
  "created_at": "2026-11-23T14:36:57.485214+02:00",
  "lines": [
    {
      "kind": "fuel",
      "country": "LT",
      "distance_km": "187.5",
      "unit_price": "1.554",
      "amount_eur": "21.85",
      "status": "ok"
    },
    {
      "kind": "fuel",
      "country": "PL",
      "distance_km": "272.5",
      "unit_price": "1.400",
      "amount_eur": "28.61",
      "status": "ok"
    }
  ],
  "refuel_stops": [
    {
      "seq": 1,
      "country": "PL",
      "km_from_start": "187.5",
      "liters": "14.5",
      "price_eur_l": "1.400"
    }
  ]
}
```

Example, status 401:

*No Authorization header*

```json
{"error": {"code": "unauthorized", "message": "missing bearer token", "details": null, "request_id": "fb956027107b4a219803813b9a854540"}}
```

*Expired or malformed token: refresh it, then sign in again*

```json
{"error": {"code": "unauthorized", "message": "invalid or expired token", "details": null, "request_id": "fb956027107b4a219803813b9a854540"}}
```

Example, status 404:

*Unknown trip, deleted trip or one of another user*

```json
{"error": {"code": "not_found", "message": "trip not found", "details": null, "request_id": "fb956027107b4a219803813b9a854540"}}
```

Example, status 422:

*Example*

```json
{"error": {"code": "validation_failed", "message": "request validation failed", "details": null, "request_id": "fb956027107b4a219803813b9a854540"}}
```

### PATCH /api/v1/trips/{trip_id}

**Rename a saved trip**

Changes only the title of a saved trip. The calculation itself never changes.

Authentication: `Authorization: Bearer <access token>` required.

| Parameter | In | Required | Type | Description |
|---|---|---|---|---|
| `trip_id` | path | yes | string (uuid) |  |

**Request body** - [TripRename](data-models.md#triprename)

| Field | Type | Required | Description |
|---|---|---|---|
| `title` | string | yes | Min length 1; max length 120 Example: `"Warsaw business trip"`. |

*New title*

```json
{
  "title": "Warsaw business trip"
}
```

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 200 | Success | [TripOut](data-models.md#tripout) |
| 401 | Missing, malformed or expired access token, or wrong login credentials | [ErrorResponse](data-models.md#errorresponse) |
| 404 | The resource does not exist or belongs to another user | [ErrorResponse](data-models.md#errorresponse) |
| 422 | Invalid input (`details` lists the fields) or no route between the points | [ErrorResponse](data-models.md#errorresponse) |

Example, status 200:

*Renamed trip*

```json
{
  "id": "6a1d0a76-0929-4e21-8aab-57a495838da8",
  "title": "Warsaw business trip",
  "origin_label": "Vilnius",
  "destination_label": "Warsaw",
  "departed_at": "2026-11-23T14:36:57.556210+02:00",
  "arrival_at": "2026-11-23T20:12:13.627639+02:00",
  "destination_country": "PL",
  "variant": "fastest",
  "distance_km": "460.0",
  "duration_min": 335,
  "daily_limit_h": "8.0",
  "nights": 0,
  "vehicle_id": "0389ba65-4fb7-453b-bf24-696b087c62d7",
  "vehicle_name": "Family car",
  "fuel_type": "petrol95",
  "consumption_l100": "7.5",
  "start_fuel": "half",
  "refuel_saving_eur": "0.00",
  "price_date": "2026-11-09",
  "total_eur": "50.46",
  "created_at": "2026-11-23T14:36:57.485214+02:00",
  "lines": [
    {
      "kind": "fuel",
      "country": "LT",
      "distance_km": "187.5",
      "unit_price": "1.554",
      "amount_eur": "21.85",
      "status": "ok"
    },
    {
      "kind": "fuel",
      "country": "PL",
      "distance_km": "272.5",
      "unit_price": "1.400",
      "amount_eur": "28.61",
      "status": "ok"
    }
  ],
  "refuel_stops": [
    {
      "seq": 1,
      "country": "PL",
      "km_from_start": "187.5",
      "liters": "14.5",
      "price_eur_l": "1.400"
    }
  ]
}
```

Example, status 401:

*No Authorization header*

```json
{"error": {"code": "unauthorized", "message": "missing bearer token", "details": null, "request_id": "fb956027107b4a219803813b9a854540"}}
```

*Expired or malformed token: refresh it, then sign in again*

```json
{"error": {"code": "unauthorized", "message": "invalid or expired token", "details": null, "request_id": "fb956027107b4a219803813b9a854540"}}
```

Example, status 404:

*Unknown trip*

```json
{"error": {"code": "not_found", "message": "trip not found", "details": null, "request_id": "fb956027107b4a219803813b9a854540"}}
```

Example, status 422:

*Example*

```json
{"error": {"code": "validation_failed", "message": "request validation failed", "details": null, "request_id": "fb956027107b4a219803813b9a854540"}}
```

### DELETE /api/v1/trips/{trip_id}

**Delete a trip (US-18)**

Deletes a saved trip.

Authentication: `Authorization: Bearer <access token>` required.

| Parameter | In | Required | Type | Description |
|---|---|---|---|---|
| `trip_id` | path | yes | string (uuid) |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 204 | Success, no content | empty |
| 401 | Missing, malformed or expired access token, or wrong login credentials | [ErrorResponse](data-models.md#errorresponse) |
| 404 | The resource does not exist or belongs to another user | [ErrorResponse](data-models.md#errorresponse) |
| 422 | Invalid input (`details` lists the fields) or no route between the points | [ErrorResponse](data-models.md#errorresponse) |

Example, status 401:

*No Authorization header*

```json
{"error": {"code": "unauthorized", "message": "missing bearer token", "details": null, "request_id": "fb956027107b4a219803813b9a854540"}}
```

*Expired or malformed token: refresh it, then sign in again*

```json
{"error": {"code": "unauthorized", "message": "invalid or expired token", "details": null, "request_id": "fb956027107b4a219803813b9a854540"}}
```

Example, status 404:

*Unknown or already deleted trip*

```json
{"error": {"code": "not_found", "message": "trip not found", "details": null, "request_id": "fb956027107b4a219803813b9a854540"}}
```

Example, status 422:

*Example*

```json
{"error": {"code": "validation_failed", "message": "request validation failed", "details": null, "request_id": "fb956027107b4a219803813b9a854540"}}
```

## reference data

Public data: countries, fuel prices, speed profiles, lodging rates.

### GET /api/v1/countries

**Supported countries**

The eight countries the service covers. Public, no token needed.

Authentication: none (public).

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 200 | Success | array of [CountryOut](data-models.md#countryout) |

Example, status 200:

*Supported countries (first three of eight)*

```json
[
  {
    "code": "CZ",
    "name": "Czechia",
    "timezone": "Europe/Prague"
  },
  {
    "code": "DE",
    "name": "Germany",
    "timezone": "Europe/Berlin"
  },
  {
    "code": "DK",
    "name": "Denmark",
    "timezone": "Europe/Copenhagen"
  }
]
```

### GET /api/v1/fuel-prices

**Latest national fuel prices (US-09)**

The newest weekly national average price of the chosen fuel in every country. Public.

Authentication: none (public).

| Parameter | In | Required | Type | Description |
|---|---|---|---|---|
| `fuel_type` | query | yes | one of `petrol95`, `diesel` |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 200 | Success | array of [FuelPriceOut](data-models.md#fuelpriceout) |
| 422 | Invalid input (`details` lists the fields) or no route between the points | [ErrorResponse](data-models.md#errorresponse) |

Example, status 200:

*Latest petrol 95 prices (first three of eight)*

```json
[
  {
    "country": "CZ",
    "price_eur_l": "1.476",
    "bulletin_date": "2026-11-09"
  },
  {
    "country": "DE",
    "price_eur_l": "1.712",
    "bulletin_date": "2026-11-09"
  },
  {
    "country": "DK",
    "price_eur_l": "1.854",
    "bulletin_date": "2026-11-09"
  }
]
```

Example, status 422:

*Unknown fuel type*

```json
{"error": {"code": "validation_failed", "message": "request validation failed", "details": [{"field": "fuel_type", "message": "Input should be 'petrol95' or 'diesel'"}], "request_id": "fb956027107b4a219803813b9a854540"}}
```

### GET /api/v1/fuel-prices/{country}/trend

**Price trend of up to 12 weeks (US-31)**

Weekly prices of the last 12 weeks for one country; `country` is an ISO code such as `PL`. 404 when
there is no history.

Authentication: none (public).

| Parameter | In | Required | Type | Description |
|---|---|---|---|---|
| `country` | path | yes | string |  |
| `fuel_type` | query | yes | one of `petrol95`, `diesel` |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 200 | Success | array of [TrendPoint](data-models.md#trendpoint) |
| 404 | The resource does not exist or belongs to another user | [ErrorResponse](data-models.md#errorresponse) |
| 422 | Invalid input (`details` lists the fields) or no route between the points | [ErrorResponse](data-models.md#errorresponse) |

Example, status 200:

*Diesel in Poland (first four of twelve weeks)*

```json
[
  {
    "bulletin_date": "2026-08-24",
    "price_eur_l": "1.368"
  },
  {
    "bulletin_date": "2026-08-31",
    "price_eur_l": "1.360"
  },
  {
    "bulletin_date": "2026-09-07",
    "price_eur_l": "1.352"
  },
  {
    "bulletin_date": "2026-09-14",
    "price_eur_l": "1.364"
  }
]
```

Example, status 404:

*Unknown country or no history*

```json
{"error": {"code": "not_found", "message": "no price history for this country and fuel", "details": null, "request_id": "fb956027107b4a219803813b9a854540"}}
```

Example, status 422:

*Unknown fuel type*

```json
{"error": {"code": "validation_failed", "message": "request validation failed", "details": [{"field": "fuel_type", "message": "Input should be 'petrol95' or 'diesel'"}], "request_id": "fb956027107b4a219803813b9a854540"}}
```

### GET /api/v1/lodging-rates

**Typical nightly lodging prices (US-14)**

Typical price per night by country and class. Public.

Authentication: none (public).

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 200 | Success | array of [LodgingRateOut](data-models.md#lodgingrateout) |

Example, status 200:

*Typical nightly prices (first three of 24)*

```json
[
  {
    "country": "CZ",
    "class": "economy",
    "nightly_eur": "42.00",
    "checked_on": "2026-11-22"
  },
  {
    "country": "CZ",
    "class": "standard",
    "nightly_eur": "60.00",
    "checked_on": "2026-11-22"
  },
  {
    "country": "CZ",
    "class": "comfort",
    "nightly_eur": "108.00",
    "checked_on": "2026-11-22"
  }
]
```

### GET /api/v1/speed-profiles

**Average speed and motorway limit of the current season (US-26)**

Average driving speed and motorway limit of every country for the current season (winter: November-
March). Public.

Authentication: none (public).

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 200 | Success | array of [SpeedProfileOut](data-models.md#speedprofileout) |

Example, status 200:

*Winter profiles (first two of eight)*

```json
[
  {
    "country": "CZ",
    "season": "winter",
    "avg_speed_kmh": "84.0",
    "motorway_limit_kmh": 130,
    "limit_note": null
  },
  {
    "country": "DE",
    "season": "winter",
    "avg_speed_kmh": "92.0",
    "motorway_limit_kmh": null,
    "limit_note": "no general limit, advised 130"
  }
]
```
