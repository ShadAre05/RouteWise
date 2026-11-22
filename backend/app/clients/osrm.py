"""Client of the self-hosted OSRM routing engine (HTTP, JSON)."""

import logging
from decimal import Decimal

import httpx

from app.core.constants import METERS_PER_KM, VARIANT_NO_TOLLS, VARIANT_SHORTEST
from app.core.errors import NoRouteFound, UpstreamUnavailable
from app.domain.models import RouteResult

log = logging.getLogger(__name__)
OSRM_OK = "Ok"
OSRM_NO_ROUTE = "NoRoute"
SECONDS_PER_MINUTE = 60
TOLL_CLASS = "toll"


class OsrmClient:
    def __init__(self, base_url: str, timeout: float, http: httpx.Client | None = None) -> None:
        self._base = base_url.rstrip("/")
        self._http = http or httpx.Client(timeout=timeout)

    def route(self, origin: tuple[float, float], destination: tuple[float, float], variant: str) -> RouteResult:
        coords = f"{origin[0]},{origin[1]};{destination[0]},{destination[1]}"
        params = {"overview": "full", "geometries": "geojson", "steps": "true", "alternatives": "true"}
        if variant == VARIANT_NO_TOLLS:
            params["exclude"] = TOLL_CLASS
        try:
            response = self._http.get(f"{self._base}/route/v1/driving/{coords}", params=params)
            body = response.json()
        except (httpx.HTTPError, ValueError) as exc:
            log.error("routing engine unavailable", extra={"error": str(exc)})
            raise UpstreamUnavailable("routing engine is not available") from exc
        if body.get("code") == OSRM_NO_ROUTE or not body.get("routes"):
            raise NoRouteFound("no route between these points")
        if body.get("code") != OSRM_OK:
            raise UpstreamUnavailable(f"routing engine error: {body.get('code')}")
        routes = body["routes"]
        chosen = min(routes, key=lambda r: r["distance"]) if variant == VARIANT_SHORTEST else routes[0]
        return self._parse(chosen)

    @staticmethod
    def _parse(route: dict) -> RouteResult:
        toll_m = sum(
            step["distance"]
            for leg in route.get("legs", [])
            for step in leg.get("steps", [])
            if any(TOLL_CLASS in inter.get("classes", []) for inter in step.get("intersections", []))
        )
        return RouteResult(
            distance_km=(Decimal(str(route["distance"])) / METERS_PER_KM).quantize(Decimal("0.1")),
            duration_min=(Decimal(str(route["duration"])) / SECONDS_PER_MINUTE).quantize(Decimal("0.1")),
            coordinates=[(float(x), float(y)) for x, y in route["geometry"]["coordinates"]],
            toll_km=(Decimal(str(toll_m)) / METERS_PER_KM).quantize(Decimal("0.1")),
        )
