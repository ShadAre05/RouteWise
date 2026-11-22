"""Travel time per country from the speed profile (US-26); routing-engine duration is the fallback."""

from decimal import Decimal

from app.core.constants import MINUTES_PER_HOUR
from app.domain.models import CountryLeg, SpeedProfile


def driving_minutes(legs: list[CountryLeg], profiles: dict[str, SpeedProfile], osrm_duration_min: Decimal) -> Decimal:
    """Country with a profile: km / average speed. Country without one: its share of the engine's duration."""
    total_km = sum((leg.km for leg in legs), Decimal(0))
    if total_km <= 0:
        return Decimal(0)
    minutes = Decimal(0)
    for leg in legs:
        profile = profiles.get(leg.country)
        if profile:
            minutes += leg.km / profile.avg_speed_kmh * MINUTES_PER_HOUR
        else:
            minutes += osrm_duration_min * leg.km / total_km
    return minutes
