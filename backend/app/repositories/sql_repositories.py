"""SQLAlchemy implementations of the repository contracts (data-access layer)."""

import uuid
from datetime import date, datetime
from decimal import Decimal

from sqlalchemy import delete, func, select, text
from sqlalchemy.exc import DBAPIError, IntegrityError
from sqlalchemy.orm import Session

from app.core.constants import (
    PG_CHECK_VIOLATION,
    PG_UNIQUE_VIOLATION,
)
from app.core.errors import Conflict, ValidationFailed
from app.db import models as m
from app.db.session import set_rls_user
from app.domain.models import CostLine, CountryLeg, RefuelStop, SpeedProfile, Tariff, Trip, Vehicle

TRIP_HISTORY_MAX_TREND_WEEKS = 12


def _sqlstate(exc: DBAPIError) -> str | None:
    return getattr(exc.orig, "sqlstate", None)


class SqlUserRepository:
    def __init__(self, session: Session) -> None:
        self._s = session

    def register(self, email: str, password: str) -> uuid.UUID:
        try:
            return self._s.execute(
                text("SELECT rw.register_user(:e, :p, true)"), {"e": email, "p": password}
            ).scalar_one()
        except IntegrityError as exc:
            if _sqlstate(exc) == PG_UNIQUE_VIOLATION:
                raise Conflict("this e-mail is already registered") from exc
            raise
        except DBAPIError as exc:
            if _sqlstate(exc) == PG_CHECK_VIOLATION:
                raise ValidationFailed("the e-mail or the password is not acceptable") from exc
            raise

    def authenticate(self, email: str, password: str) -> uuid.UUID | None:
        user_id = self._s.execute(text("SELECT rw.authenticate(:e, :p)"), {"e": email, "p": password}).scalar_one()
        # A failed login updates the failure counter (and may lock the account). The caller raises an error afterwards,
        # which rolls the request transaction back, so this must be committed here to survive.
        self._s.commit()
        return user_id

    def get_role(self, user_id: uuid.UUID) -> str | None:
        set_rls_user(self._s, user_id)  # users are visible only to themselves (RLS)
        role = self._s.execute(text("SELECT role::text FROM rw.users WHERE id = :id"), {"id": user_id}).scalar()
        return role or None

    def change_password(self, user_id: uuid.UUID, old: str, new: str) -> bool:
        try:
            return bool(
                self._s.execute(
                    text("SELECT rw.change_password(:u, :o, :n)"), {"u": user_id, "o": old, "n": new}
                ).scalar_one()
            )
        except DBAPIError as exc:
            if _sqlstate(exc) == PG_CHECK_VIOLATION:
                raise ValidationFailed("the new password is not acceptable") from exc
            raise

    def delete_account(self, user_id: uuid.UUID) -> None:
        self._s.execute(text("SELECT rw.delete_account(:u)"), {"u": user_id})


class SqlRefreshTokenRepository:
    def __init__(self, session: Session) -> None:
        self._s = session

    def add(self, user_id: uuid.UUID, token_hash: str, expires_at: datetime) -> None:
        self._s.execute(
            text("INSERT INTO rw.refresh_tokens (user_id, token_hash, expires_at) VALUES (:u, :h, :e)"),
            {"u": user_id, "h": token_hash, "e": expires_at},
        )

    def consume(self, token_hash: str) -> uuid.UUID | None:
        return self._s.execute(text("SELECT rw.consume_refresh_token(:h)"), {"h": token_hash}).scalar_one()

    def revoke(self, token_hash: str) -> None:
        self._s.execute(text("SELECT rw.revoke_refresh_token(:h)"), {"h": token_hash})


def _vehicle(row: m.VehicleRow) -> Vehicle:
    return Vehicle(
        row.id,
        row.user_id,
        row.name,
        row.fuel_type,
        row.consumption_l100,
        row.tank_l,
        row.is_default,
        row.created_at,
        row.updated_at,
    )


class SqlVehicleRepository:
    def __init__(self, session: Session) -> None:
        self._s = session

    def list(self, user_id: uuid.UUID) -> list[Vehicle]:
        rows = self._s.scalars(
            select(m.VehicleRow)
            .where(m.VehicleRow.user_id == user_id)
            .order_by(m.VehicleRow.created_at, m.VehicleRow.id)
        )
        return [_vehicle(r) for r in rows]

    def get(self, vehicle_id: uuid.UUID) -> Vehicle | None:
        row = self._s.get(m.VehicleRow, vehicle_id)
        return _vehicle(row) if row else None

    def get_default(self, user_id: uuid.UUID) -> Vehicle | None:
        row = self._s.scalars(
            select(m.VehicleRow).where(m.VehicleRow.user_id == user_id, m.VehicleRow.is_default)
        ).first()
        return _vehicle(row) if row else None

    def add(self, vehicle: Vehicle) -> Vehicle:
        row = m.VehicleRow(
            id=vehicle.id,
            user_id=vehicle.user_id,
            name=vehicle.name,
            fuel_type=vehicle.fuel_type,
            consumption_l100=vehicle.consumption_l100,
            tank_l=vehicle.tank_l,
            is_default=vehicle.is_default,
        )
        self._s.add(row)
        self._s.flush()
        self._s.refresh(row)  # the database trigger may change is_default and sets the timestamps
        return _vehicle(row)

    def update(self, vehicle: Vehicle) -> Vehicle:
        row = self._s.get(m.VehicleRow, vehicle.id)
        row.name, row.fuel_type = vehicle.name, vehicle.fuel_type
        row.consumption_l100, row.tank_l, row.is_default = vehicle.consumption_l100, vehicle.tank_l, vehicle.is_default
        self._s.flush()
        self._s.refresh(row)
        return _vehicle(row)

    def delete(self, vehicle_id: uuid.UUID) -> None:
        self._s.execute(delete(m.VehicleRow).where(m.VehicleRow.id == vehicle_id))


def _trip(row: m.TripRow) -> Trip:
    return Trip(
        id=row.id,
        user_id=row.user_id,
        vehicle_id=row.vehicle_id,
        title=row.title,
        origin_label=row.origin_label,
        destination_label=row.destination_label,
        departed_at=row.departed_at,
        variant=row.variant,
        distance_km=row.distance_km,
        duration_min=row.duration_min,
        daily_limit_h=row.daily_limit_h,
        nights=row.nights,
        arrival_at=row.arrival_at,
        destination_country=row.destination_country,
        vehicle_name=row.vehicle_name,
        fuel_type=row.fuel_type,
        consumption_l100=row.consumption_l100,
        tank_l=row.tank_l,
        start_fuel=row.start_fuel,
        refuel_saving_eur=row.refuel_saving_eur,
        price_date=row.price_date,
        lodging_entered_eur=row.lodging_entered_eur,
        total_eur=row.total_eur,
        created_at=row.created_at,
        lines=[
            CostLine(line.kind, line.country_code, line.distance_km, line.unit_price, line.amount_eur, line.status)
            for line in row.lines
        ],
        refuel_stops=[
            RefuelStop(s.seq, s.country_code, s.km_from_start, s.liters, s.price_eur_l) for s in row.refuel_stops
        ],
    )


class SqlTripRepository:
    def __init__(self, session: Session) -> None:
        self._s = session

    def add(self, trip: Trip) -> Trip:
        row = m.TripRow(
            id=trip.id,
            user_id=trip.user_id,
            vehicle_id=trip.vehicle_id,
            title=trip.title,
            origin_label=trip.origin_label,
            destination_label=trip.destination_label,
            departed_at=trip.departed_at,
            variant=trip.variant,
            distance_km=trip.distance_km,
            duration_min=trip.duration_min,
            daily_limit_h=trip.daily_limit_h,
            nights=trip.nights,
            arrival_at=trip.arrival_at,
            destination_country=trip.destination_country,
            vehicle_name=trip.vehicle_name,
            fuel_type=trip.fuel_type,
            consumption_l100=trip.consumption_l100,
            tank_l=trip.tank_l,
            start_fuel=trip.start_fuel,
            refuel_saving_eur=trip.refuel_saving_eur,
            price_date=trip.price_date,
            lodging_entered_eur=trip.lodging_entered_eur,
            total_eur=trip.total_eur,
            lines=[
                m.CostLineRow(
                    kind=line.kind,
                    country_code=line.country,
                    distance_km=line.distance_km,
                    unit_price=line.unit_price,
                    amount_eur=line.amount_eur,
                    status=line.status,
                )
                for line in trip.lines
            ],
            refuel_stops=[
                m.RefuelStopRow(
                    seq=s.seq,
                    country_code=s.country,
                    km_from_start=s.km_from_start,
                    liters=s.liters,
                    price_eur_l=s.price_eur_l,
                )
                for s in trip.refuel_stops
            ],
        )
        self._s.add(row)
        self._s.flush()
        self._s.refresh(row)
        return _trip(row)

    def list(self, user_id: uuid.UUID, limit: int, offset: int) -> tuple[list[Trip], int]:
        total = self._s.scalar(select(func.count()).select_from(m.TripRow).where(m.TripRow.user_id == user_id)) or 0
        rows = self._s.scalars(
            select(m.TripRow)
            .where(m.TripRow.user_id == user_id)
            .order_by(m.TripRow.created_at.desc())
            .limit(limit)
            .offset(offset)
        )
        return [_trip(r) for r in rows], total

    def get(self, trip_id: uuid.UUID) -> Trip | None:
        row = self._s.get(m.TripRow, trip_id)
        return _trip(row) if row else None

    def rename(self, trip_id: uuid.UUID, title: str) -> Trip | None:
        row = self._s.get(m.TripRow, trip_id)
        if row is None:
            return None
        row.title = title
        self._s.flush()
        return _trip(row)

    def delete(self, trip_id: uuid.UUID) -> bool:
        return self._s.execute(delete(m.TripRow).where(m.TripRow.id == trip_id)).rowcount > 0


class SqlReferenceRepository:
    def __init__(self, session: Session) -> None:
        self._s = session

    def countries(self) -> list[dict]:
        return [
            {"code": r.code, "name": r.name, "timezone": r.timezone}
            for r in self._s.scalars(select(m.CountryRow).order_by(m.CountryRow.code))
        ]

    def latest_prices(self, fuel_type: str) -> dict[str, tuple[Decimal, date]]:
        rows = self._s.execute(
            text(
                "SELECT country_code, price_eur_l, bulletin_date FROM rw.mv_latest_fuel_prices "
                "WHERE fuel_type = CAST(:f AS rw.fuel_type)"
            ),
            {"f": fuel_type},
        )
        return {r.country_code: (r.price_eur_l, r.bulletin_date) for r in rows}

    def price_trend(self, country: str, fuel_type: str) -> list[tuple[date, Decimal]]:
        rows = self._s.execute(
            text(
                "SELECT bulletin_date, price_eur_l FROM rw.v_fuel_price_trend "
                "WHERE country_code = :c AND fuel_type = CAST(:f AS rw.fuel_type) "
                "ORDER BY bulletin_date"
            ),
            {"c": country, "f": fuel_type},
        )
        return [(r.bulletin_date, r.price_eur_l) for r in rows]

    def speed_profiles(self, season: str) -> dict[str, SpeedProfile]:
        rows = self._s.scalars(select(m.SpeedProfileRow).where(m.SpeedProfileRow.season == season))
        return {
            r.country_code: SpeedProfile(r.country_code, r.avg_speed_kmh, r.motorway_limit_kmh, r.limit_note)
            for r in rows
        }

    def tariffs(self, countries: list[str]) -> list[Tariff]:
        rows = self._s.scalars(select(m.TollTariffRow).where(m.TollTariffRow.country_code.in_(countries)))
        return [Tariff(r.country_code, r.kind, r.rate_eur, r.unit) for r in rows]

    def nightly_rates(self, lodging_class: str) -> dict[str, Decimal]:
        rows = self._s.scalars(select(m.LodgingRateRow).where(m.LodgingRateRow.class_ == lodging_class))
        return {r.country_code: r.nightly_eur for r in rows}

    def lodging_rates(self) -> list[dict]:
        rows = self._s.scalars(
            select(m.LodgingRateRow).order_by(m.LodgingRateRow.country_code, m.LodgingRateRow.nightly_eur)
        )
        return [
            {"country": r.country_code, "class": r.class_, "nightly_eur": r.nightly_eur, "checked_on": r.checked_on}
            for r in rows
        ]


_SPLIT_SQL = text("""
WITH line AS (SELECT ST_SetSRID(ST_GeomFromGeoJSON(:geojson), 4326) AS g),
parts AS (
  SELECT c.code,
         ST_CollectionExtract(ST_Intersection(c.geom, l.g), 2) AS inside,
         l.g
    FROM rw.countries c, line l
   WHERE ST_Intersects(c.geom, l.g)
)
SELECT code,
       ST_Length(inside::geography) / 1000.0 AS km,
       (SELECT min(ST_LineLocatePoint(g, ST_StartPoint(d.geom))) FROM ST_Dump(inside) d) AS first_fraction
  FROM parts
 WHERE NOT ST_IsEmpty(inside)
 ORDER BY first_fraction
""")


class PostgisGeoRepository:
    """Splits the route line by country borders with PostGIS (US-07)."""

    def __init__(self, session: Session) -> None:
        self._s = session

    def split_route(self, coordinates: list[tuple[float, float]]) -> list[CountryLeg]:
        import json

        geojson = json.dumps({"type": "LineString", "coordinates": coordinates})
        rows = self._s.execute(_SPLIT_SQL, {"geojson": geojson})
        return [CountryLeg(r.code, Decimal(str(r.km))) for r in rows]
