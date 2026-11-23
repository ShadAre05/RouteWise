"""Request and response examples shown in the OpenAPI document (Swagger UI, Redoc, docs/api/openapi.yaml).

The values were recorded from the running API on a real PostgreSQL/PostGIS database; the trip examples were
produced with a stand-in routing engine (a fixed 460 km route) and the demo price data of the repository.
A unit test validates every example against the schema of the operation, so an example cannot go stale.
"""

from typing import Any

from app.api.schemas import TRIP_EXAMPLE

Example = dict[str, Any]
REQUEST_ID = "fb956027107b4a219803813b9a854540"
TOKEN_REQUEST = {"refresh_token": "5ceIFcTIm95XFCvLwSZy4tZPOONIK4T3f7nYpJD9OxVp3VxwBd662d1j4zchTofv"}
TOKENS = {
    "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOi...",
    "refresh_token": "5ceIFcTIm95XFCvLwSZy4tZPOONIK4T3f7nYpJD9OxVp3VxwBd662d1j4zchTofv",
    "token_type": "bearer",
    "expires_in": 900,
}
VEHICLE = {
    "id": "7f59951e-fa3a-48e6-97f8-bcaeaadf44b9",
    "name": "Family car",
    "fuel_type": "petrol95",
    "consumption_l100": "7.5",
    "tank_l": "50.0",
    "is_default": True,
    "created_at": "2026-11-23T14:51:02.755411+02:00",
    "updated_at": "2026-11-23T14:51:02.755411+02:00",
}
VEHICLE_VAN = {
    "id": "335b9b1c-9a9d-449b-bb2a-0f4e4974e1f9",
    "name": "Diesel van",
    "fuel_type": "diesel",
    "consumption_l100": "8.0",
    "tank_l": None,
    "is_default": True,
    "created_at": "2026-11-23T14:51:02.782024+02:00",
    "updated_at": "2026-11-23T14:51:02.803658+02:00",
}
TRIP_TWO_DAYS = {
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
    "vehicle_id": VEHICLE_VAN["id"],
    "vehicle_name": "Diesel van",
    "fuel_type": "diesel",
    "consumption_l100": "8.0",
    "start_fuel": None,
    "refuel_saving_eur": None,
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
            "status": "ok",
        },
        {
            "kind": "fuel",
            "country": "DE",
            "distance_km": "71.8",
            "unit_price": "1.672",
            "amount_eur": "9.60",
            "status": "ok",
        },
        {
            "kind": "lodging",
            "country": "DE",
            "distance_km": None,
            "unit_price": None,
            "amount_eur": "70.00",
            "status": "user_entered",
        },
    ],
    "refuel_stops": [],
}
TRIP_REQUEST = {
    "origin_label": "Vilnius",
    "destination_label": "Warsaw",
    "origin": {"lon": 25.28, "lat": 54.69},
    "destination": {"lon": 21.01, "lat": 52.23},
    "variant": "fastest",
    "daily_limit_h": 8,
    "lodging_class": "standard",
    "start_fuel": "half",
}
TRIP_TWO_DAYS_REQUEST = {
    "origin_label": "Warsaw",
    "destination_label": "Berlin",
    "origin": {"lon": 21.01, "lat": 52.23},
    "destination": {"lon": 13.4, "lat": 52.52},
    "variant": "no_tolls",
    "daily_limit_h": 4,
    "lodging_class": "comfort",
    "lodging_entered_eur": 70,
    "title": "Warsaw to Berlin in two days",
}


def error(code: str, message: str, details: object | None = None) -> Example:
    return {"error": {"code": code, "message": message, "details": details, "request_id": REQUEST_ID}}


def one(summary: str, value: object) -> dict[str, Example]:
    return {"default": {"summary": summary, "value": value}}


def named(**cases: tuple[str, object]) -> dict[str, Example]:
    return {key: {"summary": summary, "value": value} for key, (summary, value) in cases.items()}


UNAUTHORIZED_TOKEN = named(
    missing=("No Authorization header", error("unauthorized", "missing bearer token")),
    expired=(
        "Expired or malformed token: refresh it, then sign in again",
        error("unauthorized", "invalid or expired token"),
    ),
)

# operation (function name of the endpoint) -> named request examples
REQUEST_EXAMPLES: dict[str, dict[str, Example]] = {
    "register": one(
        "New account",
        {"email": "driver@example.com", "password": "correct-horse-battery", "privacy_accepted": True},
    ),
    "login": one("Sign in", {"email": "driver@example.com", "password": "correct-horse-battery"}),
    "refresh": one("Renew the tokens", TOKEN_REQUEST),
    "logout": one("Revoke a refresh token", TOKEN_REQUEST),
    "change_password": one(
        "Change the password", {"old_password": "correct-horse-battery", "new_password": "even-better-passphrase"}
    ),
    "create_vehicle": named(
        petrol=(
            "Petrol car with a known tank",
            {"name": "Family car", "fuel_type": "petrol95", "consumption_l100": 7.5, "tank_l": 50, "is_default": True},
        ),
        diesel=(
            "Diesel van without a tank size",
            {"name": "Diesel van", "fuel_type": "diesel", "consumption_l100": 8.2},
        ),
    ),
    "update_vehicle": one(
        "Change the consumption and make it the default", {"consumption_l100": 8.0, "is_default": True}
    ),
    "create_trip": named(
        typical=("One day, refuel advice", TRIP_REQUEST),
        two_days=("Two days, own price for the night, no toll roads", TRIP_TWO_DAYS_REQUEST),
    ),
    "rename_trip": one("New title", {"title": "Warsaw business trip"}),
}

# (operation, status code) -> named response examples; used instead of the generic error example
RESPONSE_EXAMPLES: dict[tuple[str, str], dict[str, Example]] = {
    ("health", "200"): one("Process is alive", {"status": "ok"}),
    ("ready", "200"): one("Database answers", {"status": "ready"}),
    ("register", "201"): one("Account created", TOKENS),
    ("register", "409"): one("E-mail already registered", error("conflict", "this e-mail is already registered")),
    ("register", "422"): one(
        "Several invalid fields",
        error(
            "validation_failed",
            "request validation failed",
            [
                {
                    "field": "email",
                    "message": "value is not a valid email address: An email address must have an @-sign.",
                },
                {"field": "password", "message": "String should have at least 8 characters"},
                {"field": "privacy_accepted", "message": "Input should be True"},
            ],
        ),
    ),
    ("login", "200"): one("Signed in", TOKENS),
    ("login", "401"): one(
        "Wrong e-mail or password (also while the account is locked)",
        error("unauthorized", "invalid e-mail or password"),
    ),
    ("refresh", "200"): one("New token pair; the old refresh token is dead", TOKENS),
    ("refresh", "401"): one(
        "Unknown, used or expired refresh token", error("unauthorized", "invalid or expired refresh token")
    ),
    ("change_password", "401"): {
        **named(wrong_old=("The old password is wrong", error("unauthorized", "the current password is wrong"))),
        **UNAUTHORIZED_TOKEN,
    },
    ("change_password", "422"): one(
        "New password too short",
        error(
            "validation_failed",
            "request validation failed",
            [{"field": "new_password", "message": "String should have at least 8 characters"}],
        ),
    ),
    ("delete_account", "401"): UNAUTHORIZED_TOKEN,
    ("list_vehicles", "200"): one(
        "Two vehicles", [VEHICLE, {**VEHICLE_VAN, "consumption_l100": "8.2", "is_default": False}]
    ),
    ("list_vehicles", "401"): UNAUTHORIZED_TOKEN,
    ("create_vehicle", "201"): one("Vehicle created", VEHICLE),
    ("create_vehicle", "401"): UNAUTHORIZED_TOKEN,
    ("create_vehicle", "422"): one(
        "Invalid vehicle",
        error(
            "validation_failed",
            "request validation failed",
            [
                {"field": "name", "message": "String should have at least 1 character"},
                {"field": "fuel_type", "message": "Input should be 'petrol95' or 'diesel'"},
                {"field": "consumption_l100", "message": "Input should be greater than or equal to 2.0"},
            ],
        ),
    ),
    ("get_vehicle", "200"): one("One vehicle", VEHICLE),
    ("get_vehicle", "401"): UNAUTHORIZED_TOKEN,
    ("get_vehicle", "404"): one("Unknown vehicle or one of another user", error("not_found", "vehicle not found")),
    ("update_vehicle", "200"): one("Updated vehicle", VEHICLE_VAN),
    ("update_vehicle", "401"): UNAUTHORIZED_TOKEN,
    ("update_vehicle", "404"): one("Unknown vehicle or one of another user", error("not_found", "vehicle not found")),
    ("delete_vehicle", "401"): UNAUTHORIZED_TOKEN,
    ("delete_vehicle", "404"): one("Unknown vehicle or one of another user", error("not_found", "vehicle not found")),
    ("create_trip", "201"): named(
        typical=("Vilnius - Warsaw in one day with a refuel stop", TRIP_EXAMPLE),
        two_days=("Warsaw - Berlin with one night and the user's own price", TRIP_TWO_DAYS),
    ),
    ("create_trip", "401"): UNAUTHORIZED_TOKEN,
    ("create_trip", "422"): named(
        invalid=(
            "Invalid coordinates and daily limit",
            error(
                "validation_failed",
                "request validation failed",
                [
                    {"field": "origin.lon", "message": "Input should be less than or equal to 180"},
                    {"field": "daily_limit_h", "message": "Input should be greater than or equal to 4.0"},
                ],
            ),
        ),
        no_vehicle=(
            "No vehicle added and none named",
            error("validation_failed", "no vehicle: add a vehicle or pass an existing vehicle_id"),
        ),
    ),
    ("create_trip", "502"): one(
        "Routing engine not reachable; nothing is saved",
        error("upstream_unavailable", "routing engine is not available"),
    ),
    ("list_trips", "200"): one(
        "History page", {"items": [TRIP_TWO_DAYS, TRIP_EXAMPLE], "total": 2, "limit": 20, "offset": 0}
    ),
    ("list_trips", "401"): UNAUTHORIZED_TOKEN,
    ("list_trips", "422"): one(
        "Page size too large",
        error(
            "validation_failed",
            "request validation failed",
            [{"field": "limit", "message": "Input should be less than or equal to 100"}],
        ),
    ),
    ("get_trip", "200"): one("One saved trip", TRIP_EXAMPLE),
    ("get_trip", "401"): UNAUTHORIZED_TOKEN,
    ("get_trip", "404"): one("Unknown trip, deleted trip or one of another user", error("not_found", "trip not found")),
    ("rename_trip", "200"): one("Renamed trip", {**TRIP_EXAMPLE, "title": "Warsaw business trip"}),
    ("rename_trip", "401"): UNAUTHORIZED_TOKEN,
    ("rename_trip", "404"): one("Unknown trip", error("not_found", "trip not found")),
    ("delete_trip", "401"): UNAUTHORIZED_TOKEN,
    ("delete_trip", "404"): one("Unknown or already deleted trip", error("not_found", "trip not found")),
    ("countries", "200"): one(
        "Supported countries (first three of eight)",
        [
            {"code": "CZ", "name": "Czechia", "timezone": "Europe/Prague"},
            {"code": "DE", "name": "Germany", "timezone": "Europe/Berlin"},
            {"code": "DK", "name": "Denmark", "timezone": "Europe/Copenhagen"},
        ],
    ),
    ("fuel_prices", "200"): one(
        "Latest petrol 95 prices (first three of eight)",
        [
            {"country": "CZ", "price_eur_l": "1.476", "bulletin_date": "2026-11-09"},
            {"country": "DE", "price_eur_l": "1.712", "bulletin_date": "2026-11-09"},
            {"country": "DK", "price_eur_l": "1.854", "bulletin_date": "2026-11-09"},
        ],
    ),
    ("fuel_prices", "422"): one(
        "Unknown fuel type",
        error(
            "validation_failed",
            "request validation failed",
            [{"field": "fuel_type", "message": "Input should be 'petrol95' or 'diesel'"}],
        ),
    ),
    ("fuel_price_trend", "200"): one(
        "Diesel in Poland (first four of twelve weeks)",
        [
            {"bulletin_date": "2026-08-24", "price_eur_l": "1.368"},
            {"bulletin_date": "2026-08-31", "price_eur_l": "1.360"},
            {"bulletin_date": "2026-09-07", "price_eur_l": "1.352"},
            {"bulletin_date": "2026-09-14", "price_eur_l": "1.364"},
        ],
    ),
    ("fuel_price_trend", "404"): one(
        "Unknown country or no history", error("not_found", "no price history for this country and fuel")
    ),
    ("fuel_price_trend", "422"): one(
        "Unknown fuel type",
        error(
            "validation_failed",
            "request validation failed",
            [{"field": "fuel_type", "message": "Input should be 'petrol95' or 'diesel'"}],
        ),
    ),
    ("speed_profiles", "200"): one(
        "Winter profiles (first two of eight)",
        [
            {
                "country": "CZ",
                "season": "winter",
                "avg_speed_kmh": "84.0",
                "motorway_limit_kmh": 130,
                "limit_note": None,
            },
            {
                "country": "DE",
                "season": "winter",
                "avg_speed_kmh": "92.0",
                "motorway_limit_kmh": None,
                "limit_note": "no general limit, advised 130",
            },
        ],
    ),
    ("lodging_rates", "200"): one(
        "Typical nightly prices (first three of 24)",
        [
            {"country": "CZ", "class": "economy", "nightly_eur": "42.00", "checked_on": "2026-11-22"},
            {"country": "CZ", "class": "standard", "nightly_eur": "60.00", "checked_on": "2026-11-22"},
            {"country": "CZ", "class": "comfort", "nightly_eur": "108.00", "checked_on": "2026-11-22"},
        ],
    ),
}
