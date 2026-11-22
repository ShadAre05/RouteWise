"""Vehicles of the signed-in user (US-03, US-04)."""

import uuid
from datetime import UTC, datetime
from decimal import Decimal

from app.core.errors import NotFound
from app.domain.models import Vehicle
from app.repositories.interfaces import VehicleRepository


class VehicleService:
    def __init__(self, vehicles: VehicleRepository) -> None:
        self._vehicles = vehicles

    def list(self, user_id: uuid.UUID) -> list[Vehicle]:
        return self._vehicles.list(user_id)

    def get(self, vehicle_id: uuid.UUID) -> Vehicle:
        vehicle = self._vehicles.get(vehicle_id)
        if vehicle is None:
            raise NotFound("vehicle not found")  # also returned for another user's vehicle (row-level security)
        return vehicle

    def create(
        self,
        user_id: uuid.UUID,
        *,
        name: str,
        fuel_type: str,
        consumption_l100: Decimal,
        tank_l: Decimal | None,
        is_default: bool,
    ) -> Vehicle:
        now = datetime.now(UTC)
        return self._vehicles.add(
            Vehicle(uuid.uuid4(), user_id, name.strip(), fuel_type, consumption_l100, tank_l, is_default, now, now)
        )

    def update(self, vehicle_id: uuid.UUID, changes: dict) -> Vehicle:
        current = self.get(vehicle_id)
        updated = Vehicle(**{**current.__dict__, **{k: (v.strip() if k == "name" else v) for k, v in changes.items()}})
        return self._vehicles.update(updated)

    def delete(self, vehicle_id: uuid.UUID) -> None:
        self.get(vehicle_id)
        self._vehicles.delete(vehicle_id)
