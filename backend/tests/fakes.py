"""In-memory fakes of the repository contracts: they let the business logic be tested without a database."""

import uuid
from dataclasses import replace
from datetime import UTC, date, datetime, timedelta
from decimal import Decimal

from app.core.errors import Conflict
from app.domain.models import CountryLeg, RouteResult, SpeedProfile, Tariff, Trip, Vehicle


class FakeUsers:
    def __init__(self):
        self.users: dict[str, tuple[uuid.UUID, str, str]] = {}  # email -> (id, password, role)
        self.deleted: list[uuid.UUID] = []

    def register(self, email, password):
        if email in self.users:
            raise Conflict("this e-mail is already registered")
        uid = uuid.uuid4()
        self.users[email] = (uid, password, "user")
        return uid

    def authenticate(self, email, password):
        user = self.users.get(email)
        return user[0] if user and user[1] == password else None

    def get_role(self, user_id):
        return next((u[2] for u in self.users.values() if u[0] == user_id), None)

    def change_password(self, user_id, old, new):
        for email, (uid, pw, role) in self.users.items():
            if uid == user_id and pw == old:
                self.users[email] = (uid, new, role)
                return True
        return False

    def delete_account(self, user_id):
        self.deleted.append(user_id)


class FakeTokens:
    def __init__(self):
        self.store: dict[str, uuid.UUID] = {}
        self.revoked: set[str] = set()

    def add(self, user_id, token_hash, expires_at):
        self.store[token_hash] = user_id

    def consume(self, token_hash):
        if token_hash in self.store and token_hash not in self.revoked:
            self.revoked.add(token_hash)
            return self.store[token_hash]
        return None

    def revoke(self, token_hash):
        self.revoked.add(token_hash)


class FakeVehicles:
    def __init__(self):
        self.items: dict[uuid.UUID, Vehicle] = {}

    def list(self, user_id):
        return [v for v in self.items.values() if v.user_id == user_id]

    def get(self, vehicle_id):
        return self.items.get(vehicle_id)

    def get_default(self, user_id):
        return next((v for v in self.list(user_id) if v.is_default), None)

    def add(self, vehicle):
        if not self.list(vehicle.user_id):
            vehicle = replace(vehicle, is_default=True)  # same rule as the database trigger
        self.items[vehicle.id] = vehicle
        return vehicle

    def update(self, vehicle):
        self.items[vehicle.id] = vehicle
        return vehicle

    def delete(self, vehicle_id):
        self.items.pop(vehicle_id, None)


class FakeTrips:
    def __init__(self):
        self.items: dict[uuid.UUID, Trip] = {}

    def add(self, trip):
        trip.created_at = datetime.now(UTC)
        self.items[trip.id] = trip
        return trip

    def list(self, user_id, limit, offset):
        mine = sorted(
            (t for t in self.items.values() if t.user_id == user_id), key=lambda t: t.created_at, reverse=True
        )
        return mine[offset : offset + limit], len(mine)

    def get(self, trip_id):
        return self.items.get(trip_id)

    def rename(self, trip_id, title):
        trip = self.items.get(trip_id)
        if trip:
            trip.title = title
        return trip

    def delete(self, trip_id):
        return self.items.pop(trip_id, None) is not None


class FakeReference:
    def __init__(self):
        today = date(2026, 11, 9)
        self.prices = {
            "petrol95": {
                "DE": (Decimal("1.90"), today),
                "PL": (Decimal("1.40"), today),
                "LT": (Decimal("1.55"), today - timedelta(days=7)),
            }
        }
        self.profiles = {
            "LT": SpeedProfile("LT", Decimal("80"), 110, None),
            "PL": SpeedProfile("PL", Decimal("84"), 140, None),
        }
        self.tariff_rows = [
            Tariff("PL", "toll", Decimal("0.10"), "per_km"),
            Tariff("CZ", "vignette", Decimal("12"), "per_trip"),
        ]
        self.nightly = {"PL": Decimal("50.00"), "LT": Decimal("55.00")}
        self.trend = [(date(2026, 11, 2), Decimal("1.50")), (date(2026, 11, 9), Decimal("1.55"))]

    def countries(self):
        return [{"code": "LT", "name": "Lithuania", "timezone": "Europe/Vilnius"}]

    def latest_prices(self, fuel_type):
        return self.prices.get(fuel_type, {})

    def price_trend(self, country, fuel_type):
        return self.trend if country == "LT" else []

    def speed_profiles(self, season):
        return self.profiles

    def tariffs(self, countries):
        return [t for t in self.tariff_rows if t.country in countries]

    def nightly_rates(self, lodging_class):
        return self.nightly

    def lodging_rates(self):
        return [
            {"country": "PL", "class": "standard", "nightly_eur": Decimal("50.00"), "checked_on": date(2026, 11, 1)}
        ]


class FakeGeo:
    def __init__(self, legs=None):
        self.legs = legs if legs is not None else [CountryLeg("LT", Decimal("190")), CountryLeg("PL", Decimal("270"))]

    def split_route(self, coordinates):
        return self.legs


class FakeRouting:
    def __init__(self, distance="460.0", duration="330.0", toll_km="0"):
        self.result = RouteResult(
            Decimal(distance), Decimal(duration), [(25.28, 54.69), (21.01, 52.23)], Decimal(toll_km)
        )
        self.calls = []

    def route(self, origin, destination, variant):
        self.calls.append(variant)
        return self.result
