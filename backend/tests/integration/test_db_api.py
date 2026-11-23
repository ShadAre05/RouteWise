"""End-to-end tests against a real PostgreSQL + PostGIS database prepared by database/scripts (migrations + seeds).
Run with INTEGRATION_DATABASE_URL pointing at the application role; skipped when it is not set.
Only the external routing engine is replaced (an HTTP mock in OSRM format); everything else is real."""

import os
import uuid

import httpx
import pytest

from app.clients.osrm import OsrmClient
from app.core.config import Settings
from app.main import create_app
from tests.contract import ContractClient

URL = os.environ.get("INTEGRATION_DATABASE_URL")
pytestmark = pytest.mark.skipif(not URL, reason="INTEGRATION_DATABASE_URL is not set")

LT_PL_LINE = [[25.28, 54.69], [23.9, 54.9], [23.35, 54.56], [22.93, 54.11], [21.9, 53.2], [21.01, 52.23]]
PASSWORD = "integration-test-pass"
TRIP = {
    "origin_label": "Vilnius",
    "destination_label": "Warsaw",
    "origin": {"lon": 25.28, "lat": 54.69},
    "destination": {"lon": 21.01, "lat": 52.23},
    "start_fuel": "full",
}


def fake_osrm(request: httpx.Request) -> httpx.Response:
    return httpx.Response(
        200,
        json={
            "code": "Ok",
            "routes": [
                {
                    "distance": 460000.0,
                    "duration": 18000.0,
                    "geometry": {"coordinates": LT_PL_LINE},
                    "legs": [{"steps": [{"distance": 1000.0, "intersections": [{"classes": []}]}]}],
                }
            ],
        },
    )


@pytest.fixture(scope="module")
def client():
    settings = Settings(database_url=URL, jwt_secret="integration-secret-integration-secret-01")
    app = create_app(settings)
    with ContractClient(app) as c:
        app.state.routing = OsrmClient("http://osrm.test", 1.0, httpx.Client(transport=httpx.MockTransport(fake_osrm)))
        yield c


def register(client) -> dict:
    email = f"it-{uuid.uuid4().hex[:10]}@example.com"
    r = client.post("/api/v1/auth/register", json={"email": email, "password": PASSWORD, "privacy_accepted": True})
    assert r.status_code == 201, r.text
    return {"email": email, "headers": {"Authorization": f"Bearer {r.json()['access_token']}"}, "tokens": r.json()}


def test_ready_and_reference_data(client):
    assert client.get("/health/ready").json() == {"status": "ready"}
    assert len(client.get("/api/v1/countries").json()) == 8
    prices = client.get("/api/v1/fuel-prices?fuel_type=diesel").json()
    assert {p["country"] for p in prices} >= {"LT", "PL", "DE"}
    assert len(client.get("/api/v1/fuel-prices/DE/trend?fuel_type=diesel").json()) <= 12
    assert len(client.get("/api/v1/speed-profiles").json()) == 8
    assert client.get("/api/v1/lodging-rates").json()


def test_account_lifecycle_with_real_password_hashing(client):
    user = register(client)
    duplicate = client.post(
        "/api/v1/auth/register", json={"email": user["email"], "password": PASSWORD, "privacy_accepted": True}
    )
    assert duplicate.status_code == 409
    assert (
        client.post("/api/v1/auth/login", json={"email": user["email"], "password": "wrong-password"}).status_code
        == 401
    )
    login = client.post("/api/v1/auth/login", json={"email": user["email"].upper(), "password": PASSWORD})
    assert login.status_code == 200
    refreshed = client.post("/api/v1/auth/refresh", json={"refresh_token": login.json()["refresh_token"]})
    assert refreshed.status_code == 200
    assert client.post("/api/v1/auth/refresh", json={"refresh_token": login.json()["refresh_token"]}).status_code == 401
    changed = client.post(
        "/api/v1/auth/change-password",
        headers=user["headers"],
        json={"old_password": PASSWORD, "new_password": "brand-new-password"},
    )
    assert changed.status_code == 204
    assert (
        client.post("/api/v1/auth/refresh", json={"refresh_token": refreshed.json()["refresh_token"]}).status_code
        == 401
    )
    assert (
        client.post("/api/v1/auth/login", json={"email": user["email"], "password": "brand-new-password"}).status_code
        == 200
    )
    assert client.delete("/api/v1/users/me", headers=user["headers"]).status_code == 204
    assert (
        client.post("/api/v1/auth/login", json={"email": user["email"], "password": "brand-new-password"}).status_code
        == 401
    )


def test_five_failed_logins_lock_the_account(client):
    user = register(client)
    for _ in range(5):
        client.post("/api/v1/auth/login", json={"email": user["email"], "password": "wrong-password"})
    assert client.post("/api/v1/auth/login", json={"email": user["email"], "password": PASSWORD}).status_code == 401


def test_vehicles_trips_and_row_level_security(client):
    anna, boris = register(client), register(client)
    car = client.post(
        "/api/v1/vehicles",
        headers=anna["headers"],
        json={"name": "Octavia", "fuel_type": "petrol95", "consumption_l100": "7.0", "tank_l": "50"},
    )
    assert car.status_code == 201 and car.json()["is_default"] is True
    trip = client.post("/api/v1/trips", headers=anna["headers"], json=TRIP)
    assert trip.status_code == 201, trip.text
    body = trip.json()
    fuel = [line for line in body["lines"] if line["kind"] == "fuel"]
    assert {line["country"] for line in fuel} == {"LT", "PL"}
    assert abs(sum(float(line["distance_km"]) for line in fuel) - 460.0) < 0.3
    assert float(body["total_eur"]) == pytest.approx(
        sum(float(line["amount_eur"]) for line in body["lines"] if line["amount_eur"]), abs=0.01
    )
    assert body["destination_country"] == "PL" and body["vehicle_name"] == "Octavia"

    # Boris cannot see or touch Anna's data: row-level security answers "not found"
    assert client.get(f"/api/v1/trips/{body['id']}", headers=boris["headers"]).status_code == 404
    assert client.delete(f"/api/v1/trips/{body['id']}", headers=boris["headers"]).status_code == 404
    assert client.get(f"/api/v1/vehicles/{car.json()['id']}", headers=boris["headers"]).status_code == 404
    assert client.get("/api/v1/trips", headers=boris["headers"]).json()["total"] == 0

    assert (
        client.patch(f"/api/v1/trips/{body['id']}", headers=anna["headers"], json={"title": "Warsaw run"}).json()[
            "title"
        ]
        == "Warsaw run"
    )
    assert client.get("/api/v1/trips", headers=anna["headers"]).json()["total"] == 1

    # deleting the vehicle keeps the trip with its snapshot (US-04)
    assert client.delete(f"/api/v1/vehicles/{car.json()['id']}", headers=anna["headers"]).status_code == 204
    kept = client.get(f"/api/v1/trips/{body['id']}", headers=anna["headers"]).json()
    assert kept["vehicle_id"] is None and kept["vehicle_name"] == "Octavia"

    assert client.delete(f"/api/v1/trips/{body['id']}", headers=anna["headers"]).status_code == 204
    assert client.get(f"/api/v1/trips/{body['id']}", headers=anna["headers"]).status_code == 404


def test_default_vehicle_switches_by_database_trigger(client):
    user = register(client)
    first = client.post(
        "/api/v1/vehicles",
        headers=user["headers"],
        json={"name": "A", "fuel_type": "diesel", "consumption_l100": "5.0"},
    ).json()
    second = client.post(
        "/api/v1/vehicles",
        headers=user["headers"],
        json={"name": "B", "fuel_type": "diesel", "consumption_l100": "6.0", "is_default": True},
    ).json()
    assert first["is_default"] is True and second["is_default"] is True
    flags = {v["name"]: v["is_default"] for v in client.get("/api/v1/vehicles", headers=user["headers"]).json()}
    assert flags == {"A": False, "B": True}


def test_routing_engine_failure_is_a_clean_502(client):
    user = register(client)
    client.post(
        "/api/v1/vehicles",
        headers=user["headers"],
        json={"name": "A", "fuel_type": "diesel", "consumption_l100": "5.0", "tank_l": "50"},
    )
    working = client.app.state.routing
    client.app.state.routing = OsrmClient(
        "http://osrm.test",
        1.0,
        httpx.Client(transport=httpx.MockTransport(lambda request: (_ for _ in ()).throw(httpx.ConnectError("down")))),
    )
    try:
        r = client.post("/api/v1/trips", headers=user["headers"], json=TRIP)
    finally:
        client.app.state.routing = working
    assert r.status_code == 502 and r.json()["error"]["code"] == "upstream_unavailable"
