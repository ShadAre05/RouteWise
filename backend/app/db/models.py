"""SQLAlchemy ORM mapping of the database/ schema `rw`. Tables are created by the SQL migrations, never by the ORM."""

import uuid
from datetime import date, datetime
from decimal import Decimal

from sqlalchemy import BigInteger, Boolean, Date, DateTime, ForeignKey, Integer, Numeric, SmallInteger, String
from sqlalchemy.dialects.postgresql import ENUM, UUID
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column, relationship

SCHEMA = "rw"


class Base(DeclarativeBase):
    pass


def pg_enum(name: str, *values: str) -> ENUM:
    return ENUM(*values, name=name, schema=SCHEMA, create_type=False)


FuelTypeEnum = pg_enum("fuel_type", "petrol95", "diesel")
VariantEnum = pg_enum("route_variant", "fastest", "shortest", "no_tolls")
FuelLevelEnum = pg_enum("fuel_level", "full", "three_quarter", "half", "quarter")
CostKindEnum = pg_enum("cost_kind", "fuel", "road", "lodging")
LineStatusEnum = pg_enum("line_status", "ok", "no_data", "estimate", "eu_average", "user_entered")
SeasonEnum = pg_enum("season", "summer", "winter")
LodgingClassEnum = pg_enum("lodging_class", "economy", "standard", "comfort")
TariffKindEnum = pg_enum("tariff_kind", "toll", "vignette")
TariffUnitEnum = pg_enum("tariff_unit", "per_km", "per_trip")


class VehicleRow(Base):
    __tablename__ = "vehicles"
    __table_args__ = {"schema": SCHEMA}
    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True))
    name: Mapped[str] = mapped_column(String(60))
    fuel_type: Mapped[str] = mapped_column(FuelTypeEnum)
    consumption_l100: Mapped[Decimal] = mapped_column(Numeric(3, 1))
    tank_l: Mapped[Decimal | None] = mapped_column(Numeric(4, 1))
    is_default: Mapped[bool] = mapped_column(Boolean, default=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default="now()")
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default="now()")


class TripRow(Base):
    __tablename__ = "trips"
    __table_args__ = {"schema": SCHEMA}
    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True))
    vehicle_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True))
    title: Mapped[str] = mapped_column(String(120))
    origin_label: Mapped[str] = mapped_column(String(120))
    destination_label: Mapped[str] = mapped_column(String(120))
    departed_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    variant: Mapped[str] = mapped_column(VariantEnum)
    distance_km: Mapped[Decimal] = mapped_column(Numeric(7, 1))
    duration_min: Mapped[int] = mapped_column(Integer)
    daily_limit_h: Mapped[Decimal | None] = mapped_column(Numeric(3, 1))
    nights: Mapped[int] = mapped_column(SmallInteger, default=0)
    stop_city: Mapped[str | None] = mapped_column(String(80))
    stop_country: Mapped[str | None] = mapped_column(String(2))
    arrival_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    destination_country: Mapped[str | None] = mapped_column(String(2))
    vehicle_name: Mapped[str] = mapped_column(String(60))
    fuel_type: Mapped[str] = mapped_column(FuelTypeEnum)
    consumption_l100: Mapped[Decimal] = mapped_column(Numeric(3, 1))
    tank_l: Mapped[Decimal | None] = mapped_column(Numeric(4, 1))
    start_fuel: Mapped[str | None] = mapped_column(FuelLevelEnum)
    refuel_saving_eur: Mapped[Decimal | None] = mapped_column(Numeric(7, 2))
    price_date: Mapped[date] = mapped_column(Date)
    lodging_entered_eur: Mapped[Decimal | None] = mapped_column(Numeric(8, 2))
    total_eur: Mapped[Decimal] = mapped_column(Numeric(9, 2))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default="now()")
    lines: Mapped[list["CostLineRow"]] = relationship(
        cascade="all, delete-orphan", passive_deletes=True, order_by="CostLineRow.id"
    )
    refuel_stops: Mapped[list["RefuelStopRow"]] = relationship(
        cascade="all, delete-orphan", passive_deletes=True, order_by="RefuelStopRow.seq"
    )


class CostLineRow(Base):
    __tablename__ = "trip_cost_lines"
    __table_args__ = {"schema": SCHEMA}
    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    trip_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey(f"{SCHEMA}.trips.id", ondelete="CASCADE"))
    kind: Mapped[str] = mapped_column(CostKindEnum)
    country_code: Mapped[str | None] = mapped_column(String(2))
    distance_km: Mapped[Decimal | None] = mapped_column(Numeric(7, 1))
    unit_price: Mapped[Decimal | None] = mapped_column(Numeric(7, 3))
    amount_eur: Mapped[Decimal | None] = mapped_column(Numeric(9, 2))
    status: Mapped[str] = mapped_column(LineStatusEnum)


class RefuelStopRow(Base):
    __tablename__ = "trip_refuel_stops"
    __table_args__ = {"schema": SCHEMA}
    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    trip_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey(f"{SCHEMA}.trips.id", ondelete="CASCADE"))
    seq: Mapped[int] = mapped_column(SmallInteger)
    country_code: Mapped[str] = mapped_column(String(2))
    km_from_start: Mapped[Decimal] = mapped_column(Numeric(7, 1))
    liters: Mapped[Decimal] = mapped_column(Numeric(5, 1))
    price_eur_l: Mapped[Decimal] = mapped_column(Numeric(5, 3))


class FuelPriceRow(Base):
    __tablename__ = "fuel_prices"
    __table_args__ = {"schema": SCHEMA}
    id: Mapped[int] = mapped_column(BigInteger, primary_key=True)
    country_code: Mapped[str] = mapped_column(String(2))
    fuel_type: Mapped[str] = mapped_column(FuelTypeEnum)
    price_eur_l: Mapped[Decimal] = mapped_column(Numeric(5, 3))
    bulletin_date: Mapped[date] = mapped_column(Date)


class CountryRow(Base):
    __tablename__ = "countries"  # the geometry column is used only inside SQL (see GeoRepository)
    __table_args__ = {"schema": SCHEMA}
    code: Mapped[str] = mapped_column(String(2), primary_key=True)
    name: Mapped[str] = mapped_column(String(40))
    timezone: Mapped[str] = mapped_column(String(40))


class TollTariffRow(Base):
    __tablename__ = "toll_tariffs"
    __table_args__ = {"schema": SCHEMA}
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    country_code: Mapped[str] = mapped_column(String(2))
    kind: Mapped[str] = mapped_column(TariffKindEnum)
    name: Mapped[str] = mapped_column(String(80))
    rate_eur: Mapped[Decimal] = mapped_column(Numeric(7, 3))
    unit: Mapped[str] = mapped_column(TariffUnitEnum)
    checked_on: Mapped[date] = mapped_column(Date)


class LodgingRateRow(Base):
    __tablename__ = "lodging_rates"
    __table_args__ = {"schema": SCHEMA}
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    country_code: Mapped[str] = mapped_column(String(2))
    class_: Mapped[str] = mapped_column("class", LodgingClassEnum)
    nightly_eur: Mapped[Decimal] = mapped_column(Numeric(7, 2))
    checked_on: Mapped[date] = mapped_column(Date)


class SpeedProfileRow(Base):
    __tablename__ = "speed_profiles"
    __table_args__ = {"schema": SCHEMA}
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    country_code: Mapped[str] = mapped_column(String(2))
    season: Mapped[str] = mapped_column(SeasonEnum)
    avg_speed_kmh: Mapped[Decimal] = mapped_column(Numeric(5, 1))
    motorway_limit_kmh: Mapped[int | None] = mapped_column(SmallInteger)
    limit_note: Mapped[str | None] = mapped_column(String(80))
    source: Mapped[str] = mapped_column(String(160))
    checked_on: Mapped[date] = mapped_column(Date)
