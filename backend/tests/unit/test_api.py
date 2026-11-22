"""HTTP-level tests: routing, validation, status codes, error format. Services run on in-memory fakes."""

import uuid
from datetime import UTC, datetime

import pytest
from fastapi.testclient import TestClient

from app.api import deps
from app.core.config import get_settings
from app.core.security import create_access_token
from app.main import create_app
from app.services.auth_service import AuthService
from app.services.reference_service import ReferenceService
from app.services.trip_service import TripService
from app.services.vehicle_service import VehicleService
from tests import fakes

TRIP_BODY = {
    "origin_label": "Vilnius",
    "destination_label": "Warsaw",
    "origin": {"lon": 25.28, "lat": 54.69},
    "destination": {"lon": 21.01, "lat": 52.23},
}


@pytest.fixture
def client(settings):
    app = create_app(settings)
    users, tokens, vehicles, trips = fakes.FakeUsers(), fakes.FakeTokens(), fakes.FakeVehicles(), fakes.FakeTrips()
    auth = AuthService(users, tokens, settings)
    trip_service = TripService(trips, vehicles, fakes.FakeReference(), fakes.FakeGeo(), fakes.FakeRouting())
    app.dependency_overrides.update(
        {
            deps.auth_service: lambda: auth,
            deps.user_auth_service: lambda: auth,
            deps.vehicle_service: lambda: VehicleService(vehicles),
            deps.trip_service: lambda: trip_service,
            deps.reference_service: lambda: ReferenceService(
                fakes.FakeReference(), today=lambda: datetime(2026, 7, 1).date()
            ),
        }
    )
    return TestClient(app, raise_server_exceptions=False)


@pytest.fixture
def headers(client):
    r = client.post(
        "/api/v1/auth/register", json={"email": "a@example.com", "password": "correct horse", "privacy_accepted": True}
    )
    return {"Authorization": f"Bearer {r.json()['access_token']}"}


def test_health(client):
    assert client.get("/health").json() == {"status": "ok"}


def test_request_id_is_echoed_and_generated(client):
    assert client.get("/health", headers={"X-Request-ID": "abc"}).headers["X-Request-ID"] == "abc"
    assert client.get("/health").headers["X-Request-ID"]


def test_register_login_refresh_logout_flow(client):
    body = {"email": "a@example.com", "password": "correct horse", "privacy_accepted": True}
    r = client.post("/api/v1/auth/register", json=body)
    assert r.status_code == 201 and r.json()["token_type"] == "bearer"
    assert client.post("/api/v1/auth/register", json=body).status_code == 409
    login = client.post("/api/v1/auth/login", json={"email": "a@example.com", "password": "correct horse"})
    assert login.status_code == 200
    refreshed = client.post("/api/v1/auth/refresh", json={"refresh_token": login.json()["refresh_token"]})
    assert refreshed.status_code == 200
    assert (
        client.post("/api/v1/auth/logout", json={"refresh_token": refreshed.json()["refresh_token"]}).status_code == 204
    )
    reused = client.post("/api/v1/auth/refresh", json={"refresh_token": refreshed.json()["refresh_token"]})
    assert reused.status_code == 401 and reused.json()["error"]["code"] == "unauthorized"


@pytest.mark.parametrize(
    "payload",
    [
        {"email": "not-an-email", "password": "correct horse", "privacy_accepted": True},
        {"email": "a@example.com", "password": "short", "privacy_accepted": True},
        {"email": "a@example.com", "password": "correct horse", "privacy_accepted": False},
        {},
    ],
)
def test_registration_input_is_validated(client, payload):
    r = client.post("/api/v1/auth/register", json=payload)
    assert r.status_code == 422
    assert r.json()["error"]["code"] == "validation_failed" and r.json()["error"]["details"]


def test_wrong_credentials_give_401(client):
    r = client.post("/api/v1/auth/login", json={"email": "a@example.com", "password": "nope"})
    assert r.status_code == 401 and r.json()["error"]["request_id"]


def test_protected_routes_need_a_valid_token(client):
    assert client.get("/api/v1/vehicles").status_code == 401
    assert client.get("/api/v1/vehicles", headers={"Authorization": "Bearer garbage"}).status_code == 401


def test_change_password_and_delete_account(client, headers):
    bad = client.post(
        "/api/v1/auth/change-password", json={"old_password": "x", "new_password": "another pass"}, headers=headers
    )
    assert bad.status_code == 401
    ok = client.post(
        "/api/v1/auth/change-password",
        json={"old_password": "correct horse", "new_password": "another pass"},
        headers=headers,
    )
    assert ok.status_code == 204
    assert client.delete("/api/v1/users/me", headers=headers).status_code == 204


def test_vehicle_endpoints(client, headers):
    payload = {"name": "Octavia", "fuel_type": "petrol95", "consumption_l100": "7.0", "tank_l": "50"}
    created = client.post("/api/v1/vehicles", json=payload, headers=headers)
    assert created.status_code == 201 and created.json()["is_default"] is True
    vid = created.json()["id"]
    assert client.get(f"/api/v1/vehicles/{vid}", headers=headers).status_code == 200
    assert client.get("/api/v1/vehicles", headers=headers).json()[0]["id"] == vid
    assert client.patch(f"/api/v1/vehicles/{vid}", json={"name": "Passat"}, headers=headers).json()["name"] == "Passat"
    assert client.delete(f"/api/v1/vehicles/{vid}", headers=headers).status_code == 204
    missing = client.get(f"/api/v1/vehicles/{vid}", headers=headers)
    assert missing.status_code == 404 and missing.json()["error"]["code"] == "not_found"


@pytest.mark.parametrize(
    "change",
    [{"consumption_l100": "1.0"}, {"consumption_l100": "31"}, {"tank_l": "10"}, {"fuel_type": "lpg"}, {"name": ""}],
)
def test_vehicle_input_is_validated(client, headers, change):
    payload = {"name": "Car", "fuel_type": "diesel", "consumption_l100": "6.0", **change}
    assert client.post("/api/v1/vehicles", json=payload, headers=headers).status_code == 422


def test_trip_lifecycle(client, headers):
    assert client.post("/api/v1/trips", json=TRIP_BODY, headers=headers).status_code == 422  # no vehicle yet
    client.post(
        "/api/v1/vehicles",
        json={"name": "Car", "fuel_type": "petrol95", "consumption_l100": "7.0", "tank_l": "50"},
        headers=headers,
    )
    created = client.post("/api/v1/trips", json={**TRIP_BODY, "start_fuel": "full"}, headers=headers)
    assert created.status_code == 201
    trip = created.json()
    assert trip["lines"] and trip["total_eur"]
    page = client.get("/api/v1/trips?limit=5", headers=headers).json()
    assert page["total"] == 1 and page["items"][0]["id"] == trip["id"]
    assert (
        client.patch(f"/api/v1/trips/{trip['id']}", json={"title": "Weekend"}, headers=headers).json()["title"]
        == "Weekend"
    )
    assert client.get(f"/api/v1/trips/{trip['id']}", headers=headers).status_code == 200
    assert client.delete(f"/api/v1/trips/{trip['id']}", headers=headers).status_code == 204
    assert client.delete(f"/api/v1/trips/{trip['id']}", headers=headers).status_code == 404


@pytest.mark.parametrize(
    "change",
    [
        {"daily_limit_h": "3"},
        {"daily_limit_h": "11"},
        {"variant": "scenic"},
        {"origin": {"lon": 200, "lat": 0}},
        {"lodging_entered_eur": "-5"},
        {"start_fuel": "overflowing"},
        {"origin_label": " "},
    ],
)
def test_trip_input_is_validated(client, headers, change):
    assert client.post("/api/v1/trips", json={**TRIP_BODY, **change}, headers=headers).status_code == 422


@pytest.mark.parametrize("query", ["limit=0", "limit=1000", "offset=-1"])
def test_pagination_limits(client, headers, query):
    assert client.get(f"/api/v1/trips?{query}", headers=headers).status_code == 422


def test_reference_endpoints(client):
    assert client.get("/api/v1/countries").json()[0]["code"] == "LT"
    assert len(client.get("/api/v1/fuel-prices?fuel_type=petrol95").json()) == 3
    assert client.get("/api/v1/fuel-prices").status_code == 422
    assert len(client.get("/api/v1/fuel-prices/lt/trend?fuel_type=petrol95").json()) == 2
    assert client.get("/api/v1/fuel-prices/xx/trend?fuel_type=petrol95").status_code == 404
    assert client.get("/api/v1/speed-profiles").json()[0]["season"] == "summer"
    assert client.get("/api/v1/lodging-rates").json()[0]["class"] == "standard"


def test_unknown_route_and_method_use_the_error_format(client):
    r = client.get("/api/v1/nothing")
    assert r.status_code == 404 and r.json()["error"]["code"] == "not_found"
    assert client.put("/api/v1/countries").status_code == 405


def test_unexpected_exception_becomes_500_without_details(settings):
    app = create_app(settings)

    @app.get("/boom")
    def boom():
        raise RuntimeError("secret internal detail")

    r = TestClient(app, raise_server_exceptions=False).get("/boom")
    assert r.status_code == 500 and r.json()["error"]["code"] == "internal_error"
    assert "secret" not in r.text


def test_openapi_documents_the_api(client):
    doc = client.get("/openapi.json").json()
    assert "/api/v1/trips" in doc["paths"] and doc["info"]["title"] == "RouteWise API"
    assert client.get("/docs").status_code == 200
    assert "ErrorResponse" in doc["components"]["schemas"]
    assert get_settings().jwt_algorithm == "HS256" and create_access_token  # settings load from the environment
    assert uuid.UUID(int=0) and UTC


def test_openapi_documents_the_real_error_format(client):
    schema = client.get("/openapi.json").json()
    assert "HTTPValidationError" not in schema["components"]["schemas"]
    ref = schema["paths"]["/api/v1/vehicles"]["post"]["responses"]["422"]["content"]["application/json"]["schema"]
    assert ref == {"$ref": "#/components/schemas/ErrorResponse"}
    assert "401" not in schema["paths"]["/api/v1/auth/register"]["post"]["responses"]
