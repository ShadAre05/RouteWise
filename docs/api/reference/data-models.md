# Data models

<!-- Generated from openapi.yaml by backend/scripts/render_reference.py. Do not edit by hand. -->

All models of the API, as they appear in `openapi.yaml`. Decimal numbers are sent
as JSON strings. Fields marked nullable can be `null`.

## ChangePasswordRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `old_password` | string | yes | Min length 1; max length 128 Example: `"correct-horse-battery"`. |
| `new_password` | string | yes | Min length 8; max length 128 Example: `"even-better-passphrase"`. |

## CostLineOut

One line of the cost breakdown. The total of a trip is the sum of its lines.

| Field | Type | Required | Description |
|---|---|---|---|
| `kind` | one of `fuel`, `road`, `lodging` | yes |  |
| `country` | string, nullable | yes | ISO 3166-1 alpha-2 code Example: `"PL"`. |
| `distance_km` | string, nullable | yes | Kilometres driven in this country (fuel lines) Example: `"272.5"`. |
| `unit_price` | string, nullable | yes | EUR per litre, per kilometre or per night Example: `"1.400"`. |
| `amount_eur` | string, nullable | yes | Example: `"28.61"`. |
| `status` | one of `ok`, `no_data`, `estimate`, `eu_average`, `user_entered` | yes | ok: from the data; eu_average: no price for the country, average of the others used; estimate: typical value; user_entered: given by the caller; no_data: amount unknown |

## CountryOut

| Field | Type | Required | Description |
|---|---|---|---|
| `code` | string | yes |  |
| `name` | string | yes |  |
| `timezone` | string | yes |  |

## ErrorBody

| Field | Type | Required | Description |
|---|---|---|---|
| `code` | string | yes | Stable machine-readable code: validation_failed, unauthorized, forbidden, not_found, conflict, no_route, upstream_unavailable, internal_error Example: `"not_found"`. |
| `message` | string | yes | Human-readable explanation; may change, do not parse it Example: `"trip not found"`. |
| `details` | object, nullable | no | For validation_failed: a list of {field, message}; otherwise null |
| `request_id` | string | yes | Correlation id, also returned in the X-Request-ID header; quote it when reporting a problem Example: `"fb956027107b4a219803813b9a854540"`. |

## ErrorResponse

The single error format of the whole API.

| Field | Type | Required | Description |
|---|---|---|---|
| `error` | [ErrorBody](data-models.md#errorbody) | yes |  |

## FuelPriceOut

| Field | Type | Required | Description |
|---|---|---|---|
| `country` | string | yes |  |
| `price_eur_l` | string | yes |  |
| `bulletin_date` | string (date) | yes |  |

## LodgingRateOut

| Field | Type | Required | Description |
|---|---|---|---|
| `country` | string | yes |  |
| `class` | one of `economy`, `standard`, `comfort` | yes |  |
| `nightly_eur` | string | yes |  |
| `checked_on` | string (date) | yes |  |

## LoginRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `email` | string (email) | yes | Example: `"driver@example.com"`. |
| `password` | string | yes | Min length 1; max length 128 Example: `"correct-horse-battery"`. |

## Point

A WGS84 coordinate. Longitude comes first (GeoJSON order).

| Field | Type | Required | Description |
|---|---|---|---|
| `lon` | number | yes | Min -180.0; max 180.0 Example: `25.28`. |
| `lat` | number | yes | Min -90.0; max 90.0 Example: `54.69`. |

## RefreshRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `refresh_token` | string | yes | The refresh token received at login; it can be used once (min length 20; max length 200) Example: `"5ceIFcTIm95XFCvLwSZy4tZPOONIK4T3f7nYpJD9OxVp3VxwBd662d1j4zchTofv"`. |

## RefuelStopOut

A suggested refuelling stop. Advice only; it is not part of the total.

| Field | Type | Required | Description |
|---|---|---|---|
| `seq` | integer | yes | Order of the stop, starting at 1 Example: `1`. |
| `country` | string | yes | Example: `"PL"`. |
| `km_from_start` | string | yes | Example: `"187.5"`. |
| `liters` | string | yes | Example: `"14.5"`. |
| `price_eur_l` | string | yes | Example: `"1.400"`. |

## RegisterRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `email` | string (email) | yes | Account e-mail; stored in lower case, must be unique Example: `"driver@example.com"`. |
| `password` | string | yes | 8-128 characters (min length 8; max length 128) Example: `"correct-horse-battery"`. |
| `privacy_accepted` | `true` | yes | Must be true: the privacy notice has to be accepted (US-01) |

## SpeedProfileOut

| Field | Type | Required | Description |
|---|---|---|---|
| `country` | string | yes |  |
| `season` | one of `summer`, `winter` | yes |  |
| `avg_speed_kmh` | string | yes |  |
| `motorway_limit_kmh` | integer, nullable | yes |  |
| `limit_note` | string, nullable | yes |  |

## TokenResponse

| Field | Type | Required | Description |
|---|---|---|---|
| `access_token` | string | yes | JWT; send as 'Authorization: Bearer <token>' Example: `"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."`. |
| `refresh_token` | string | yes | Opaque single-use token for POST /auth/refresh |
| `token_type` | `"bearer"` | no | Default bearer |
| `expires_in` | integer | yes | Access token lifetime in seconds Example: `900`. |

## TrendPoint

| Field | Type | Required | Description |
|---|---|---|---|
| `bulletin_date` | string (date) | yes |  |
| `price_eur_l` | string | yes |  |

## TripCreate

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

## TripOut

A saved calculation. Vehicle data is a snapshot, so old trips never change.

| Field | Type | Required | Description |
|---|---|---|---|
| `id` | string (uuid) | yes |  |
| `title` | string | yes |  |
| `origin_label` | string | yes |  |
| `destination_label` | string | yes |  |
| `departed_at` | string (date-time) | yes |  |
| `arrival_at` | string (date-time), nullable | yes |  |
| `destination_country` | string, nullable | yes |  |
| `variant` | one of `fastest`, `shortest`, `no_tolls` | yes |  |
| `distance_km` | string | yes |  |
| `duration_min` | integer | yes |  |
| `daily_limit_h` | string, nullable | yes |  |
| `nights` | integer | yes |  |
| `vehicle_id` | string (uuid), nullable | yes |  |
| `vehicle_name` | string | yes |  |
| `fuel_type` | one of `petrol95`, `diesel` | yes |  |
| `consumption_l100` | string | yes |  |
| `start_fuel` | one of `full`, `three_quarter`, `half`, `quarter`, nullable | yes |  |
| `refuel_saving_eur` | string, nullable | yes |  |
| `price_date` | string (date) | yes | Date of the fuel prices used |
| `total_eur` | string | yes | Sum of all cost lines Example: `"50.46"`. |
| `created_at` | string (date-time), nullable | yes |  |
| `lines` | array of [CostLineOut](data-models.md#costlineout) | yes |  |
| `refuel_stops` | array of [RefuelStopOut](data-models.md#refuelstopout) | yes |  |

## TripPage

| Field | Type | Required | Description |
|---|---|---|---|
| `items` | array of [TripOut](data-models.md#tripout) | yes |  |
| `total` | integer | yes | All trips of the user, regardless of the page |
| `limit` | integer | yes |  |
| `offset` | integer | yes |  |

## TripRename

| Field | Type | Required | Description |
|---|---|---|---|
| `title` | string | yes | Min length 1; max length 120 Example: `"Warsaw business trip"`. |

## VehicleCreate

| Field | Type | Required | Description |
|---|---|---|---|
| `name` | string | yes | Display name, 1-60 characters (min length 1; max length 60) Example: `"Family car"`. |
| `fuel_type` | one of `petrol95`, `diesel` | yes | petrol95 or diesel |
| `consumption_l100` | decimal (number or numeric string) | yes | Litres per 100 km, 2.0-30.0 (min 2.0; max 30.0) Example: `7.5`. |
| `tank_l` | decimal (number or numeric string), nullable | no | Tank size in litres, 20-120; enables the refuel plan (min 20.0; max 120.0) Example: `50`. |
| `is_default` | boolean | no | Use this vehicle when a trip names none; at most one vehicle is the default (default False) |

## VehicleOut

| Field | Type | Required | Description |
|---|---|---|---|
| `id` | string (uuid) | yes |  |
| `name` | string | yes | Example: `"Family car"`. |
| `fuel_type` | one of `petrol95`, `diesel` | yes |  |
| `consumption_l100` | string | yes | Litres per 100 km (decimal, serialised as a string) Example: `"7.5"`. |
| `tank_l` | string, nullable | yes | Tank size in litres or null Example: `"50.0"`. |
| `is_default` | boolean | yes |  |
| `created_at` | string (date-time) | yes |  |
| `updated_at` | string (date-time) | yes |  |

## VehicleUpdate

Partial update: send only the fields that change.

| Field | Type | Required | Description |
|---|---|---|---|
| `name` | string, nullable | no | Min length 1; max length 60 |
| `fuel_type` | one of `petrol95`, `diesel`, nullable | no |  |
| `consumption_l100` | decimal (number or numeric string), nullable | no | Min 2.0; max 30.0 |
| `tank_l` | decimal (number or numeric string), nullable | no | Min 20.0; max 120.0 |
| `is_default` | boolean, nullable | no |  |
