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


class ORMModel(BaseModel):
    model_config = ConfigDict(from_attributes=True)


class ErrorBody(BaseModel):
    code: str
    message: str
    details: object | None = None
    request_id: str


class ErrorResponse(BaseModel):
    error: ErrorBody


# ---------- auth
class RegisterRequest(BaseModel):
    email: EmailStr
    password: Password
    privacy_accepted: Literal[True] = Field(description="the privacy notice must be accepted (US-01)")


class LoginRequest(BaseModel):
    email: EmailStr
    password: Annotated[str, StringConstraints(min_length=1, max_length=MAX_PASSWORD_LENGTH)]


class RefreshRequest(BaseModel):
    refresh_token: Annotated[str, StringConstraints(min_length=20, max_length=200)]


class ChangePasswordRequest(BaseModel):
    old_password: Annotated[str, StringConstraints(min_length=1, max_length=MAX_PASSWORD_LENGTH)]
    new_password: Password


class TokenResponse(BaseModel):
    access_token: str
    refresh_token: str
    token_type: Literal["bearer"] = "bearer"
    expires_in: int = Field(description="access token lifetime in seconds")


# ---------- vehicles
class VehicleCreate(BaseModel):
    name: Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=60)]
    fuel_type: FuelType
    consumption_l100: Decimal = Field(ge=Decimal("2.0"), le=Decimal("30.0"), decimal_places=1)
    tank_l: Decimal | None = Field(default=None, ge=20, le=120, decimal_places=1)
    is_default: bool = False


class VehicleUpdate(BaseModel):
    name: Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=60)] | None = None
    fuel_type: FuelType | None = None
    consumption_l100: Decimal | None = Field(default=None, ge=Decimal("2.0"), le=Decimal("30.0"), decimal_places=1)
    tank_l: Decimal | None = Field(default=None, ge=20, le=120, decimal_places=1)
    is_default: bool | None = None


class VehicleOut(ORMModel):
    id: uuid.UUID
    name: str
    fuel_type: FuelType
    consumption_l100: Decimal
    tank_l: Decimal | None
    is_default: bool
    created_at: datetime
    updated_at: datetime


# ---------- trips
class Point(BaseModel):
    lon: float = Field(ge=-180, le=180)
    lat: float = Field(ge=-90, le=90)

    def as_tuple(self) -> tuple[float, float]:
        return self.lon, self.lat


class TripCreate(BaseModel):
    origin_label: Label
    destination_label: Label
    origin: Point
    destination: Point
    variant: Variant = "fastest"
    vehicle_id: uuid.UUID | None = Field(default=None, description="omit to use the default vehicle")
    daily_limit_h: Decimal | None = Field(
        default=None, ge=Decimal(str(MIN_DAILY_LIMIT_H)), le=Decimal(str(MAX_DAILY_LIMIT_H)), decimal_places=1
    )
    lodging_class: LodgingClass = "standard"
    lodging_entered_eur: Decimal | None = Field(default=None, gt=0, decimal_places=2)
    start_fuel: StartFuel | None = None
    title: Label | None = None


class TripRename(BaseModel):
    title: Label


class CostLineOut(ORMModel):
    kind: Literal["fuel", "road", "lodging"]
    country: str | None
    distance_km: Decimal | None
    unit_price: Decimal | None
    amount_eur: Decimal | None
    status: Literal["ok", "no_data", "estimate", "eu_average", "user_entered"]


class RefuelStopOut(ORMModel):
    seq: int
    country: str
    km_from_start: Decimal
    liters: Decimal
    price_eur_l: Decimal


class TripOut(ORMModel):
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
    price_date: date
    total_eur: Decimal
    created_at: datetime | None
    lines: list[CostLineOut]
    refuel_stops: list[RefuelStopOut]


class TripPage(BaseModel):
    items: list[TripOut]
    total: int
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
