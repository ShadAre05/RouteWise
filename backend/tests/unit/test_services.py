import uuid
from datetime import UTC, datetime
from decimal import Decimal as D

import pytest

from app.core.errors import Conflict, NotFound, Unauthorized, ValidationFailed
from app.core.security import decode_access_token
from app.domain.models import CountryLeg as L
from app.services.auth_service import AuthService
from app.services.reference_service import ReferenceService
from app.services.trip_service import TripRequest, TripService
from app.services.vehicle_service import VehicleService
from tests import fakes

NOW = datetime(2026, 11, 22, 9, 0, tzinfo=UTC)


@pytest.fixture
def auth(settings):
    return AuthService(fakes.FakeUsers(), fakes.FakeTokens(), settings)


# ---------- auth
def test_register_then_login_returns_valid_tokens(auth, settings):
    pair = auth.register("  Anna@Example.com ", "correct horse")
    claims = decode_access_token(pair.access_token, secret=settings.jwt_secret, algorithm=settings.jwt_algorithm)
    assert claims.role == "user"
    assert auth.login("anna@example.com", "correct horse").access_token


def test_register_duplicate_is_conflict(auth):
    auth.register("a@example.com", "correct horse")
    with pytest.raises(Conflict):
        auth.register("a@example.com", "correct horse")


@pytest.mark.parametrize("email,pw", [("a@example.com", "wrong"), ("nobody@example.com", "x")])
def test_login_failure_is_unauthorized(auth, email, pw):
    auth.register("a@example.com", "correct horse")
    with pytest.raises(Unauthorized):
        auth.login(email, pw)


def test_refresh_token_is_single_use(auth):
    pair = auth.register("a@example.com", "correct horse")
    assert auth.refresh(pair.refresh_token).refresh_token != pair.refresh_token
    with pytest.raises(Unauthorized):
        auth.refresh(pair.refresh_token)


def test_logout_revokes_refresh_token(auth):
    pair = auth.register("a@example.com", "correct horse")
    auth.logout(pair.refresh_token)
    with pytest.raises(Unauthorized):
        auth.refresh(pair.refresh_token)


def test_change_password_and_delete_account(auth, settings):
    pair = auth.register("a@example.com", "correct horse")
    uid = decode_access_token(pair.access_token, secret=settings.jwt_secret, algorithm=settings.jwt_algorithm).user_id
    with pytest.raises(Unauthorized):
        auth.change_password(uid, "wrong", "another pass")
    auth.change_password(uid, "correct horse", "another pass")
    assert auth.login("a@example.com", "another pass")
    auth.delete_account(uid)
    assert auth._users.deleted == [uid]


def test_issue_fails_for_unknown_user(auth):
    with pytest.raises(Unauthorized):
        auth._issue(uuid.uuid4())


# ---------- vehicles
def test_vehicle_crud_and_first_is_default():
    service, uid = VehicleService(fakes.FakeVehicles()), uuid.uuid4()
    first = service.create(
        uid, name=" Octavia ", fuel_type="diesel", consumption_l100=D("5.5"), tank_l=None, is_default=False
    )
    assert first.name == "Octavia" and first.is_default
    updated = service.update(first.id, {"name": " Passat ", "tank_l": D("60")})
    assert updated.name == "Passat" and updated.tank_l == D("60")
    assert [v.id for v in service.list(uid)] == [first.id]
    service.delete(first.id)
    with pytest.raises(NotFound):
        service.get(first.id)
    with pytest.raises(NotFound):
        service.delete(first.id)


# ---------- reference
def test_reference_service():
    service = ReferenceService(fakes.FakeReference(), today=lambda: datetime(2026, 11, 22).date())
    assert service.countries()[0]["code"] == "LT"
    assert [p["country"] for p in service.latest_prices("petrol95")] == ["DE", "LT", "PL"]
    assert len(service.price_trend("lt", "diesel")) == 2
    with pytest.raises(NotFound):
        service.price_trend("xx", "diesel")
    assert service.speed_profiles()[0]["season"] == "winter"
    assert service.lodging_rates()[0]["country"] == "PL"


# ---------- trips
def make_trip_service(vehicles=None, geo=None, routing=None):
    vehicles = vehicles or fakes.FakeVehicles()
    return TripService(
        fakes.FakeTrips(),
        vehicles,
        fakes.FakeReference(),
        geo or fakes.FakeGeo(),
        routing or fakes.FakeRouting(),
        clock=lambda: NOW,
    ), vehicles


def request(**overrides):
    base = dict(
        origin_label="Vilnius",
        destination_label="Warsaw",
        origin=(25.28, 54.69),
        destination=(21.01, 52.23),
        variant="fastest",
    )
    return TripRequest(**{**base, **overrides})


def add_vehicle(vehicles, uid, tank="50"):
    return VehicleService(vehicles).create(
        uid,
        name="Octavia",
        fuel_type="petrol95",
        consumption_l100=D("7.0"),
        tank_l=D(tank) if tank else None,
        is_default=True,
    )


def test_trip_is_calculated_and_saved():
    service, vehicles = make_trip_service()
    uid = uuid.uuid4()
    add_vehicle(vehicles, uid)
    trip = service.calculate_and_save(uid, request(start_fuel="full"))
    assert trip.total_eur == sum(line.amount_eur for line in trip.lines if line.amount_eur is not None)
    assert trip.destination_country == "PL" and trip.vehicle_name == "Octavia"
    assert trip.price_date.isoformat() == "2026-11-02"  # the oldest bulletin of the countries used
    assert trip.refuel_saving_eur is not None
    assert service.get(trip.id) is trip
    assert service.list(uid, 10, 0)[1] == 1


def test_trip_without_vehicle_is_rejected():
    service, _ = make_trip_service()
    with pytest.raises(ValidationFailed):
        service.calculate_and_save(uuid.uuid4(), request())


def test_start_fuel_needs_a_tank():
    service, vehicles = make_trip_service()
    uid = uuid.uuid4()
    add_vehicle(vehicles, uid, tank=None)
    with pytest.raises(ValidationFailed):
        service.calculate_and_save(uid, request(start_fuel="half"))


def test_route_outside_supported_countries_is_rejected():
    service, vehicles = make_trip_service(geo=fakes.FakeGeo(legs=[]))
    uid = uuid.uuid4()
    add_vehicle(vehicles, uid)
    with pytest.raises(ValidationFailed):
        service.calculate_and_save(uid, request())


def test_long_trip_has_nights_and_lodging_line():
    routing = fakes.FakeRouting(distance="1400.0", duration="1000.0")
    service, vehicles = make_trip_service(geo=fakes.FakeGeo([L("LT", D("500")), L("PL", D("900"))]), routing=routing)
    uid = uuid.uuid4()
    add_vehicle(vehicles, uid)
    trip = service.calculate_and_save(uid, request(title="Long one", lodging_entered_eur=D("120")))
    assert trip.nights >= 1 and trip.title == "Long one"
    assert any(line.kind == "lodging" and line.status == "user_entered" for line in trip.lines)


def test_rename_and_delete_trip():
    service, vehicles = make_trip_service()
    uid = uuid.uuid4()
    add_vehicle(vehicles, uid)
    trip = service.calculate_and_save(uid, request())
    assert service.rename(trip.id, " New title ").title == "New title"
    service.delete(trip.id)
    for action in (lambda: service.get(trip.id), lambda: service.rename(trip.id, "x"), lambda: service.delete(trip.id)):
        with pytest.raises(NotFound):
            action()
