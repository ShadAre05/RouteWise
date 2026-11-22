"""Calculating and saving a trip (US-05 ... US-18, US-26, US-29, US-30)."""

import logging
import uuid
from collections.abc import Callable
from datetime import UTC, datetime
from decimal import Decimal

from app.core.constants import DEFAULT_DAILY_LIMIT_H, START_FUEL_FRACTION
from app.core.errors import NotFound, ValidationFailed
from app.domain.models import CostLine, Trip, Vehicle
from app.repositories.interfaces import (
    GeoRepository,
    ReferenceRepository,
    RoutingClient,
    TripRepository,
    VehicleRepository,
)
from app.services import cost_calculator as calc
from app.services.country_split import normalize_legs
from app.services.refuel_planner import plan_refuel
from app.services.schedule import plan_schedule, season_for
from app.services.travel_time import driving_minutes

log = logging.getLogger(__name__)


class TripRequest:
    """Input of a calculation (already validated by the API layer)."""

    def __init__(
        self,
        *,
        origin_label: str,
        destination_label: str,
        origin: tuple[float, float],
        destination: tuple[float, float],
        variant: str,
        vehicle_id: uuid.UUID | None = None,
        daily_limit_h: Decimal | None = None,
        lodging_class: str = "standard",
        lodging_entered_eur: Decimal | None = None,
        start_fuel: str | None = None,
        title: str | None = None,
    ) -> None:
        self.origin_label, self.destination_label = origin_label, destination_label
        self.origin, self.destination, self.variant = origin, destination, variant
        self.vehicle_id, self.lodging_class = vehicle_id, lodging_class
        self.daily_limit_h = daily_limit_h if daily_limit_h is not None else Decimal(str(DEFAULT_DAILY_LIMIT_H))
        self.lodging_entered_eur, self.start_fuel, self.title = lodging_entered_eur, start_fuel, title


class TripService:
    def __init__(
        self,
        trips: TripRepository,
        vehicles: VehicleRepository,
        reference: ReferenceRepository,
        geo: GeoRepository,
        routing: RoutingClient,
        clock: Callable[[], datetime] = lambda: datetime.now(UTC),
    ) -> None:
        self._trips, self._vehicles, self._ref, self._geo, self._routing, self._clock = (
            trips,
            vehicles,
            reference,
            geo,
            routing,
            clock,
        )

    def calculate_and_save(self, user_id: uuid.UUID, req: TripRequest) -> Trip:
        vehicle = self._pick_vehicle(user_id, req.vehicle_id)
        if req.start_fuel and vehicle.tank_l is None:
            raise ValidationFailed("start_fuel needs a vehicle with a tank size")
        departed = self._clock()

        route = self._routing.route(req.origin, req.destination, req.variant)
        legs = normalize_legs(self._geo.split_route(route.coordinates), route.distance_km)
        if not legs:
            raise ValidationFailed("the route is outside the supported countries")
        countries = [leg.country for leg in legs]

        latest = self._ref.latest_prices(vehicle.fuel_type)
        prices = {c: latest[c][0] for c in countries if c in latest}
        price_date = min((latest[c][1] for c in countries if c in latest), default=departed.date())

        profiles = self._ref.speed_profiles(season_for(departed.date()))
        minutes = driving_minutes(legs, {c: p for c, p in profiles.items() if c in countries}, route.duration_min)
        schedule = plan_schedule(minutes, req.daily_limit_h, departed)

        destination_country = legs[-1].country
        lines: list[CostLine] = [
            *calc.fuel_lines(legs, vehicle.consumption_l100, prices),
            *calc.road_lines(legs, self._ref.tariffs(countries), req.variant, route.toll_km),
            *calc.lodging_lines(
                schedule.nights,
                destination_country,
                self._ref.nightly_rates(req.lodging_class),
                req.lodging_entered_eur,
            ),
        ]

        plan = None
        if req.start_fuel and vehicle.tank_l:
            plan = plan_refuel(
                legs,
                consumption_l100=vehicle.consumption_l100,
                tank_l=vehicle.tank_l,
                start_fraction=START_FUEL_FRACTION[req.start_fuel],
                prices=prices,
            )

        trip = Trip(
            id=uuid.uuid4(),
            user_id=user_id,
            vehicle_id=vehicle.id,
            title=(req.title or f"{req.origin_label} - {req.destination_label}")[:120],
            origin_label=req.origin_label,
            destination_label=req.destination_label,
            departed_at=departed,
            variant=req.variant,
            distance_km=route.distance_km,
            duration_min=int(minutes.to_integral_value()),
            daily_limit_h=req.daily_limit_h,
            nights=schedule.nights,
            arrival_at=schedule.arrival_at,
            destination_country=destination_country,
            vehicle_name=vehicle.name,
            fuel_type=vehicle.fuel_type,
            consumption_l100=vehicle.consumption_l100,
            tank_l=vehicle.tank_l,
            start_fuel=req.start_fuel,
            refuel_saving_eur=plan.saving_eur if plan else None,
            price_date=price_date,
            lodging_entered_eur=req.lodging_entered_eur,
            total_eur=calc.total(lines),
            lines=lines,
            refuel_stops=plan.stops if plan else [],
        )
        saved = self._trips.add(trip)
        log.info(
            "trip calculated",
            extra={"trip_id": str(saved.id), "distance_km": str(saved.distance_km), "total_eur": str(saved.total_eur)},
        )
        return saved

    def list(self, user_id: uuid.UUID, limit: int, offset: int) -> tuple[list[Trip], int]:
        return self._trips.list(user_id, limit, offset)

    def get(self, trip_id: uuid.UUID) -> Trip:
        trip = self._trips.get(trip_id)
        if trip is None:
            raise NotFound("trip not found")
        return trip

    def rename(self, trip_id: uuid.UUID, title: str) -> Trip:
        trip = self._trips.rename(trip_id, title.strip())
        if trip is None:
            raise NotFound("trip not found")
        return trip

    def delete(self, trip_id: uuid.UUID) -> None:
        if not self._trips.delete(trip_id):
            raise NotFound("trip not found")

    def _pick_vehicle(self, user_id: uuid.UUID, vehicle_id: uuid.UUID | None) -> Vehicle:
        vehicle = self._vehicles.get(vehicle_id) if vehicle_id else self._vehicles.get_default(user_id)
        if vehicle is None:
            raise ValidationFailed("no vehicle: add a vehicle or pass an existing vehicle_id")
        return vehicle
