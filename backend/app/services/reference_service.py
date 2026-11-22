"""Public reference data (US-09, US-12, US-26, US-31)."""

from datetime import date
from decimal import Decimal

from app.core.errors import NotFound
from app.repositories.interfaces import ReferenceRepository
from app.services.schedule import season_for


class ReferenceService:
    def __init__(self, reference: ReferenceRepository, today=date.today) -> None:
        self._ref, self._today = reference, today

    def countries(self) -> list[dict]:
        return self._ref.countries()

    def latest_prices(self, fuel_type: str) -> list[dict]:
        return [
            {"country": c, "price_eur_l": p, "bulletin_date": d}
            for c, (p, d) in sorted(self._ref.latest_prices(fuel_type).items())
        ]

    def price_trend(self, country: str, fuel_type: str) -> list[tuple[date, Decimal]]:
        trend = self._ref.price_trend(country.upper(), fuel_type)
        if not trend:
            raise NotFound("no price history for this country and fuel")
        return trend

    def speed_profiles(self) -> list[dict]:
        season = season_for(self._today())
        return [
            {
                "country": c,
                "season": season,
                "avg_speed_kmh": p.avg_speed_kmh,
                "motorway_limit_kmh": p.motorway_limit_kmh,
                "limit_note": p.limit_note,
            }
            for c, p in sorted(self._ref.speed_profiles(season).items())
        ]

    def lodging_rates(self) -> list[dict]:
        return self._ref.lodging_rates()
