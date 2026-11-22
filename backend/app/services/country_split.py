"""Normalising the per-country distances measured on simplified borders."""

from decimal import Decimal

from app.domain.models import CountryLeg


def normalize_legs(raw: list[CountryLeg], route_km: Decimal) -> list[CountryLeg]:
    """Border polygons are simplified, so the measured km differ slightly from the route length.
    Scale the legs so that they add up exactly to the length reported by the routing engine."""
    measured = sum((leg.km for leg in raw), Decimal(0))
    if measured <= 0:
        return []
    factor = route_km / measured
    return [CountryLeg(leg.country, (leg.km * factor).quantize(Decimal("0.1"))) for leg in raw if leg.km > 0]
