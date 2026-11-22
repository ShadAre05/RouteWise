"""Plain domain objects used by the business logic. They know nothing about HTTP or SQLAlchemy."""

import uuid
from dataclasses import dataclass, field
from datetime import date, datetime
from decimal import Decimal


@dataclass(frozen=True)
class CountryLeg:
    country: str
    km: Decimal


@dataclass(frozen=True)
class CostLine:
    kind: str
    country: str | None
    distance_km: Decimal | None
    unit_price: Decimal | None
    amount_eur: Decimal | None
    status: str


@dataclass(frozen=True)
class RefuelStop:
    seq: int
    country: str
    km_from_start: Decimal
    liters: Decimal
    price_eur_l: Decimal


@dataclass(frozen=True)
class RefuelPlan:
    stops: list[RefuelStop]
    saving_eur: Decimal


@dataclass(frozen=True)
class Tariff:
    country: str
    kind: str  # toll | vignette
    rate_eur: Decimal
    unit: str  # per_km | per_trip


@dataclass(frozen=True)
class SpeedProfile:
    country: str
    avg_speed_kmh: Decimal
    motorway_limit_kmh: int | None
    limit_note: str | None


@dataclass(frozen=True)
class Schedule:
    days: int
    nights: int
    arrival_at: datetime


@dataclass(frozen=True)
class RouteResult:
    distance_km: Decimal
    duration_min: Decimal
    coordinates: list[tuple[float, float]]  # (lon, lat) of the route line
    toll_km: Decimal = Decimal(0)  # km on charged roads reported by the routing engine


@dataclass(frozen=True)
class Vehicle:
    id: uuid.UUID
    user_id: uuid.UUID
    name: str
    fuel_type: str
    consumption_l100: Decimal
    tank_l: Decimal | None
    is_default: bool
    created_at: datetime
    updated_at: datetime


@dataclass
class Trip:
    id: uuid.UUID
    user_id: uuid.UUID
    vehicle_id: uuid.UUID | None
    title: str
    origin_label: str
    destination_label: str
    departed_at: datetime
    variant: str
    distance_km: Decimal
    duration_min: int
    daily_limit_h: Decimal | None
    nights: int
    arrival_at: datetime | None
    destination_country: str | None
    vehicle_name: str
    fuel_type: str
    consumption_l100: Decimal
    tank_l: Decimal | None
    start_fuel: str | None
    refuel_saving_eur: Decimal | None
    price_date: date
    lodging_entered_eur: Decimal | None
    total_eur: Decimal
    created_at: datetime | None = None
    lines: list[CostLine] = field(default_factory=list)
    refuel_stops: list[RefuelStop] = field(default_factory=list)
