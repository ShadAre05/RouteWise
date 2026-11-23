"""Request and response models (presentation layer). All input is validated here before it reaches the services."""

import uuid
from datetime import date, datetime
from decimal import Decimal
from typing import Annotated, Literal

from pydantic import BaseModel, ConfigDict, EmailStr, Field, StringConstraints

from app.core.constants import MAX_DAILY_LIMIT_H, MAX_PASSWORD_LENGTH, MIN_DAILY_LIMIT_H, MIN_PASSWORD_LENGTH

FuelType = Literal["petrol95", "diesel"]
Variant = Literal["fastest", "shortest", "no_tolls"]
LodgingClass = Literal["economy", "standard", "comfort"]
StartFuel = Literal["full", "three_quarter", "half", "quarter"]
Password = Annotated[str, StringConstraints(min_length=MIN_PASSWORD_LENGTH, max_length=MAX_PASSWORD_LENGTH)]
Label = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=120)]


TRIP_EXAMPLE = {
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
            "status": "ok",
        },
        {
            "kind": "fuel",
            "country": "PL",
            "distance_km": "272.5",
            "unit_price": "1.400",
            "amount_eur": "28.61",
            "status": "ok",
        },
    ],
    "refuel_stops": [{"seq": 1, "country": "PL", "km_from_start": "187.5", "liters": "14.5", "price_eur_l": "1.400"}],
}


class ORMModel(BaseModel):
    model_config = ConfigDict(from_attributes=True)


class ErrorBody(BaseModel):
    code: str = Field(
        description="Stable machine-readable code: validation_failed, unauthorized, forbidden, not_found, "
        "conflict, no_route, upstream_unavailable, internal_error",
        examples=["not_found"],
    )
    message: str = Field(
        description="Human-readable explanation; may change, do not parse it", examples=["trip not found"]
    )
    details: object | None = Field(
        default=None,
        description="For validation_failed: a list of {field, message}; otherwise null",
    )
    request_id: str = Field(
        description="Correlation id, also returned in the X-Request-ID header; quote it when reporting a problem",
        examples=["fb956027107b4a219803813b9a854540"],
    )


class ErrorResponse(BaseModel):
    """The single error format of the whole API."""

    error: ErrorBody


# ---------- auth
class RegisterRequest(BaseModel):
    email: EmailStr = Field(
        description="Account e-mail; stored in lower case, must be unique", examples=["driver@example.com"]
    )
    password: Password = Field(
        description=f"{MIN_PASSWORD_LENGTH}-{MAX_PASSWORD_LENGTH} characters", examples=["correct-horse-battery"]
    )
    privacy_accepted: Literal[True] = Field(description="Must be true: the privacy notice has to be accepted (US-01)")


class LoginRequest(BaseModel):
    email: EmailStr = Field(examples=["driver@example.com"])
    password: Annotated[str, StringConstraints(min_length=1, max_length=MAX_PASSWORD_LENGTH)] = Field(
        examples=["correct-horse-battery"]
    )


class RefreshRequest(BaseModel):
    refresh_token: Annotated[str, StringConstraints(min_length=20, max_length=200)] = Field(
        description="The refresh token received at login; it can be used once",
        examples=["5ceIFcTIm95XFCvLwSZy4tZPOONIK4T3f7nYpJD9OxVp3VxwBd662d1j4zchTofv"],
    )


class ChangePasswordRequest(BaseModel):
    old_password: Annotated[str, StringConstraints(min_length=1, max_length=MAX_PASSWORD_LENGTH)] = Field(
        examples=["correct-horse-battery"]
    )
    new_password: Password = Field(examples=["even-better-passphrase"])


class TokenResponse(BaseModel):
    model_config = ConfigDict(
        json_schema_extra={
            "examples": [
                {
                    "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOi...",
                    "refresh_token": "5ceIFcTIm95XFCvLwSZy4tZPOONIK4T3f7nYpJD9OxVp3VxwBd662d1j4zchTofv",
                    "token_type": "bearer",
                    "expires_in": 900,
                }
            ]
        }
    )

    access_token: str = Field(
        description="JWT; send as 'Authorization: Bearer <token>'", examples=["eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."]
    )
    refresh_token: str = Field(description="Opaque single-use token for POST /auth/refresh")
    token_type: Literal["bearer"] = "bearer"
    expires_in: int = Field(description="Access token lifetime in seconds", examples=[900])


# ---------- vehicles
class VehicleCreate(BaseModel):
    name: Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=60)] = Field(
        description="Display name, 1-60 characters", examples=["Family car"]
    )
    fuel_type: FuelType = Field(description="petrol95 or diesel")
    consumption_l100: Decimal = Field(
        ge=Decimal("2.0"),
        le=Decimal("30.0"),
        decimal_places=1,
        description="Litres per 100 km, 2.0-30.0",
        examples=[7.5],
    )
    tank_l: Decimal | None = Field(
        default=None,
        ge=20,
        le=120,
        decimal_places=1,
        description="Tank size in litres, 20-120; enables the refuel plan",
        examples=[50],
    )
    is_default: bool = Field(
        default=False, description="Use this vehicle when a trip names none; at most one vehicle is the default"
    )


class VehicleUpdate(BaseModel):
    """Partial update: send only the fields that change."""

    name: Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=60)] | None = None
    fuel_type: FuelType | None = None
    consumption_l100: Decimal | None = Field(default=None, ge=Decimal("2.0"), le=Decimal("30.0"), decimal_places=1)
    tank_l: Decimal | None = Field(default=None, ge=20, le=120, decimal_places=1)
    is_default: bool | None = None


class VehicleOut(ORMModel):
    id: uuid.UUID
    name: str = Field(examples=["Family car"])
    fuel_type: FuelType
    consumption_l100: Decimal = Field(
        description="Litres per 100 km (decimal, serialised as a string)", examples=["7.5"]
    )
    tank_l: Decimal | None = Field(description="Tank size in litres or null", examples=["50.0"])
    is_default: bool
    created_at: datetime
    updated_at: datetime


# ---------- trips
class Point(BaseModel):
    """A WGS84 coordinate. Longitude comes first (GeoJSON order)."""

    lon: float = Field(ge=-180, le=180, examples=[25.28])
    lat: float = Field(ge=-90, le=90, examples=[54.69])

    def as_tuple(self) -> tuple[float, float]:
        return self.lon, self.lat


class TripCreate(BaseModel):
    model_config = ConfigDict(
        json_schema_extra={
            "examples": [
                {
                    "origin_label": "Vilnius",
                    "destination_label": "Warsaw",
                    "origin": {"lon": 25.28, "lat": 54.69},
                    "destination": {"lon": 21.01, "lat": 52.23},
                    "variant": "fastest",
                    "daily_limit_h": 8,
                    "lodging_class": "standard",
                    "start_fuel": "half",
                }
            ]
        }
    )

    origin_label: Label = Field(
        description="Text shown for the start; only the label is stored, not the coordinates", examples=["Vilnius"]
    )
    destination_label: Label = Field(examples=["Warsaw"])
    origin: Point = Field(description="Start point; used for routing only and never stored")
    destination: Point = Field(description="End point; used for routing only and never stored")
    variant: Variant = Field(
        default="fastest",
        description="fastest, shortest (shortest of the alternatives) or no_tolls (toll roads avoided)",
    )
    vehicle_id: uuid.UUID | None = Field(default=None, description="Omit to use the default vehicle")
    daily_limit_h: Decimal | None = Field(
        default=None,
        ge=Decimal(str(MIN_DAILY_LIMIT_H)),
        le=Decimal(str(MAX_DAILY_LIMIT_H)),
        decimal_places=1,
        description=f"Maximum driving hours per day ({MIN_DAILY_LIMIT_H}-{MAX_DAILY_LIMIT_H}); "
        "splits the trip into days and nights",
        examples=[8],
    )
    lodging_class: LodgingClass = Field(
        default="standard", description="Class of the typical nightly price: economy, standard or comfort"
    )
    lodging_entered_eur: Decimal | None = Field(
        default=None,
        gt=0,
        decimal_places=2,
        description="Your own price per night; replaces the estimate",
        examples=[65.00],
    )
    start_fuel: StartFuel | None = Field(
        default=None,
        description="Fuel level at departure (full, three_quarter, half, quarter); enables the refuel plan",
    )
    title: Label | None = Field(default=None, description="Name in the history; defaults to 'origin - destination'")


class TripRename(BaseModel):
    title: Label = Field(examples=["Warsaw business trip"])


class CostLineOut(ORMModel):
    """One line of the cost breakdown. The total of a trip is the sum of its lines."""

    kind: Literal["fuel", "road", "lodging"]
    country: str | None = Field(description="ISO 3166-1 alpha-2 code", examples=["PL"])
    distance_km: Decimal | None = Field(
        description="Kilometres driven in this country (fuel lines)", examples=["272.5"]
    )
    unit_price: Decimal | None = Field(description="EUR per litre, per kilometre or per night", examples=["1.400"])
    amount_eur: Decimal | None = Field(examples=["28.61"])
    status: Literal["ok", "no_data", "estimate", "eu_average", "user_entered"] = Field(
        description="ok: from the data; eu_average: no price for the country, average of the others used; "
        "estimate: typical value; user_entered: given by the caller; no_data: amount unknown"
    )


class RefuelStopOut(ORMModel):
    """A suggested refuelling stop. Advice only; it is not part of the total."""

    seq: int = Field(description="Order of the stop, starting at 1", examples=[1])
    country: str = Field(examples=["PL"])
    km_from_start: Decimal = Field(examples=["187.5"])
    liters: Decimal = Field(examples=["14.5"])
    price_eur_l: Decimal = Field(examples=["1.400"])


class TripOut(ORMModel):
    """A saved calculation. Vehicle data is a snapshot, so old trips never change."""

    model_config = ConfigDict(json_schema_extra={"examples": [TRIP_EXAMPLE]})

    id: uuid.UUID
    title: str
    origin_label: str
    destination_label: str
    departed_at: datetime
    arrival_at: datetime | None
    destination_country: str | None
    variant: Variant
    distance_km: Decimal
    duration_min: int
    daily_limit_h: Decimal | None
    nights: int
    vehicle_id: uuid.UUID | None
    vehicle_name: str
    fuel_type: FuelType
    consumption_l100: Decimal
    start_fuel: StartFuel | None
    refuel_saving_eur: Decimal | None
    price_date: date = Field(description="Date of the fuel prices used")
    total_eur: Decimal = Field(description="Sum of all cost lines", examples=["50.46"])
    created_at: datetime | None
    lines: list[CostLineOut]
    refuel_stops: list[RefuelStopOut]


class TripPage(BaseModel):
    items: list[TripOut]
    total: int = Field(description="All trips of the user, regardless of the page")
    limit: int
    offset: int


# ---------- reference data
class CountryOut(BaseModel):
    code: str
    name: str
    timezone: str


class FuelPriceOut(BaseModel):
    country: str
    price_eur_l: Decimal
    bulletin_date: date


class TrendPoint(BaseModel):
    bulletin_date: date
    price_eur_l: Decimal


class SpeedProfileOut(BaseModel):
    country: str
    season: Literal["summer", "winter"]
    avg_speed_kmh: Decimal
    motorway_limit_kmh: int | None
    limit_note: str | None


class LodgingRateOut(BaseModel):
    country: str
    class_: LodgingClass = Field(alias="class")
    nightly_eur: Decimal
    checked_on: date

    model_config = ConfigDict(populate_by_name=True)
