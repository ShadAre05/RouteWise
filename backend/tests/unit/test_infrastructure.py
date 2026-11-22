import json
import logging
import uuid
from datetime import UTC, datetime
from decimal import Decimal

import httpx
import pytest

from app.clients.osrm import OsrmClient
from app.core import logging as app_logging
from app.core.errors import NoRouteFound, Unauthorized, UpstreamUnavailable
from app.core.security import create_access_token, decode_access_token, hash_refresh_token, new_refresh_token

SECRET = "unit-test-secret-unit-test-secret-0123456789"


# ---------- tokens
def test_access_token_round_trip_and_tampering():
    uid = uuid.uuid4()
    token = create_access_token(uid, "admin", secret=SECRET, algorithm="HS256", ttl_minutes=5)
    claims = decode_access_token(token, secret=SECRET, algorithm="HS256")
    assert (claims.user_id, claims.role) == (uid, "admin")
    with pytest.raises(Unauthorized):
        decode_access_token(token + "x", secret=SECRET, algorithm="HS256")
    with pytest.raises(Unauthorized):
        decode_access_token(token, secret="another-secret-another-secret-0123456789", algorithm="HS256")


def test_expired_token_is_rejected():
    token = create_access_token(
        uuid.uuid4(), "user", secret=SECRET, algorithm="HS256", ttl_minutes=1, now=datetime(2020, 1, 1, tzinfo=UTC)
    )
    with pytest.raises(Unauthorized):
        decode_access_token(token, secret=SECRET, algorithm="HS256")


def test_refresh_token_hash_is_stable_and_not_the_token():
    raw, digest = new_refresh_token()
    assert digest == hash_refresh_token(raw) and digest != raw and len(digest) == 64


# ---------- OSRM client (HTTP is mocked with httpx.MockTransport; the client code is real)
def osrm(handler) -> OsrmClient:
    return OsrmClient("http://osrm.test", 1.0, httpx.Client(transport=httpx.MockTransport(handler)))


def route_body(distance, toll=False):
    return {
        "code": "Ok",
        "routes": [
            {
                "distance": distance,
                "duration": 3600.0,
                "geometry": {"coordinates": [[25.0, 54.0], [21.0, 52.0]]},
                "legs": [{"steps": [{"distance": 10000.0, "intersections": [{"classes": ["toll"] if toll else []}]}]}],
            }
        ],
    }


def test_osrm_parses_route_and_toll_distance():
    client = osrm(lambda request: httpx.Response(200, json=route_body(100000.0, toll=True)))
    result = client.route((25.0, 54.0), (21.0, 52.0), "fastest")
    assert result.distance_km == Decimal("100.0") and result.duration_min == Decimal("60.0")
    assert result.toll_km == Decimal("10.0") and result.coordinates[0] == (25.0, 54.0)


def test_osrm_variants_change_the_request_and_pick_the_shortest():
    seen = {}

    def handler(request):
        seen["query"] = dict(request.url.params)
        body = route_body(200000.0)
        body["routes"].append({**body["routes"][0], "distance": 150000.0})
        return httpx.Response(200, json=body)

    client = osrm(handler)
    assert client.route((1, 2), (3, 4), "shortest").distance_km == Decimal("150.0")
    client.route((1, 2), (3, 4), "no_tolls")
    assert seen["query"]["exclude"] == "toll"


@pytest.mark.parametrize(
    "response,error",
    [
        (httpx.Response(200, json={"code": "NoRoute", "routes": []}), NoRouteFound),
        (httpx.Response(200, json={"code": "InvalidQuery", "routes": [{}]}), UpstreamUnavailable),
        (httpx.Response(502, text="bad gateway"), UpstreamUnavailable),
    ],
)
def test_osrm_errors_are_translated(response, error):
    with pytest.raises(error):
        osrm(lambda request: response).route((1, 2), (3, 4), "fastest")


def test_osrm_network_failure_is_upstream_unavailable():
    def boom(request):
        raise httpx.ConnectError("refused")

    with pytest.raises(UpstreamUnavailable):
        osrm(boom).route((1, 2), (3, 4), "fastest")


# ---------- logging
def test_json_log_line_has_request_id_and_extra_fields():
    import io

    stream = io.StringIO()
    handler = logging.StreamHandler(stream)
    handler.setFormatter(app_logging.JsonFormatter())
    logger = logging.getLogger("json-test")
    logger.addHandler(handler)
    logger.setLevel(logging.INFO)
    token = app_logging.request_id_var.set("req-123")
    try:
        logger.info("hello", extra={"trip_id": "abc"})
        try:
            raise RuntimeError("boom")
        except RuntimeError:
            logger.exception("failed")
    finally:
        app_logging.request_id_var.reset(token)
        logger.removeHandler(handler)
    lines = [json.loads(line) for line in stream.getvalue().splitlines()]
    assert lines[0]["request_id"] == "req-123" and lines[0]["trip_id"] == "abc" and lines[0]["level"] == "INFO"
    assert "RuntimeError" in lines[1]["exception"]


def test_configure_logging_installs_a_json_handler():
    root = logging.getLogger()
    saved = root.handlers[:]
    try:
        app_logging.configure_logging("WARNING")
        assert isinstance(root.handlers[0].formatter, app_logging.JsonFormatter) and root.level == logging.WARNING
    finally:
        root.handlers[:] = saved
        root.setLevel(logging.INFO)
