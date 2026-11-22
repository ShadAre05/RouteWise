"""Cost lines of a trip (US-09 ... US-14, US-24). Pure functions, no database access."""

from decimal import ROUND_HALF_UP, Decimal

from app.core.constants import (
    KIND_FUEL,
    KIND_LODGING,
    KIND_ROAD,
    MONEY_QUANT,
    STATUS_ESTIMATE,
    STATUS_EU_AVERAGE,
    STATUS_NO_DATA,
    STATUS_OK,
    STATUS_USER_ENTERED,
    TARIFF_TOLL,
    TARIFF_VIGNETTE,
    UNIT_PER_KM,
    UNIT_PER_TRIP,
    VARIANT_NO_TOLLS,
)
from app.domain.models import CostLine, CountryLeg, Tariff

HUNDRED = Decimal(100)


def money(value: Decimal) -> Decimal:
    return value.quantize(MONEY_QUANT, rounding=ROUND_HALF_UP)


def fuel_price_for(country: str, prices: dict[str, Decimal]) -> tuple[Decimal | None, str]:
    """National price; if the country has none, the average of the others (status eu_average); otherwise no data."""
    if country in prices:
        return prices[country], STATUS_OK
    if prices:
        return (sum(prices.values()) / len(prices)).quantize(Decimal("0.001")), STATUS_EU_AVERAGE
    return None, STATUS_NO_DATA


def fuel_lines(legs: list[CountryLeg], consumption_l100: Decimal, prices: dict[str, Decimal]) -> list[CostLine]:
    lines = []
    for leg in legs:
        price, status = fuel_price_for(leg.country, prices)
        amount = money(leg.km * consumption_l100 / HUNDRED * price) if price is not None else None
        lines.append(CostLine(KIND_FUEL, leg.country, leg.km, price, amount, status))
    return lines


def road_lines(legs: list[CountryLeg], tariffs: list[Tariff], variant: str, toll_km: Decimal) -> list[CostLine]:
    """Vignettes are charged once per country the route enters (not with the no-tolls variant).
    Per-km tolls use the km on charged roads reported by the routing engine, shared between the countries that
    have a per-km tariff in proportion to their route length. A country without a tariff row gives a "no data"
    line, but only when the route uses charged roads at all."""
    if variant == VARIANT_NO_TOLLS:
        return []
    by_country: dict[str, list[Tariff]] = {}
    for tariff in tariffs:
        by_country.setdefault(tariff.country, []).append(tariff)
    per_km_countries = [leg for leg in legs if any(t.unit == UNIT_PER_KM for t in by_country.get(leg.country, []))]
    per_km_total = sum((leg.km for leg in per_km_countries), Decimal(0))
    lines: list[CostLine] = []
    for leg in legs:
        country_tariffs = by_country.get(leg.country, [])
        if not country_tariffs:
            if toll_km > 0:
                lines.append(CostLine(KIND_ROAD, leg.country, None, None, None, STATUS_NO_DATA))
            continue
        for tariff in country_tariffs:
            if tariff.unit == UNIT_PER_TRIP and tariff.kind in (TARIFF_VIGNETTE, TARIFF_TOLL):
                lines.append(CostLine(KIND_ROAD, leg.country, None, tariff.rate_eur, money(tariff.rate_eur), STATUS_OK))
            elif tariff.unit == UNIT_PER_KM and toll_km > 0 and per_km_total > 0:
                km_on_toll = (toll_km * leg.km / per_km_total).quantize(Decimal("0.1"))
                lines.append(
                    CostLine(
                        KIND_ROAD,
                        leg.country,
                        km_on_toll,
                        tariff.rate_eur,
                        money(km_on_toll * tariff.rate_eur),
                        STATUS_ESTIMATE,
                    )
                )
    return lines


def lodging_lines(
    nights: int, country: str | None, nightly_rates: dict[str, Decimal], entered_total: Decimal | None
) -> list[CostLine]:
    """No nights: no lodging. The user's own total replaces the estimate (US-24)."""
    if nights <= 0:
        return []
    if entered_total is not None:
        return [CostLine(KIND_LODGING, country, None, None, money(entered_total), STATUS_USER_ENTERED)]
    rate = nightly_rates.get(country) if country else None
    if rate is None:
        return [CostLine(KIND_LODGING, country, None, None, None, STATUS_NO_DATA)]
    return [CostLine(KIND_LODGING, country, None, rate, money(rate * nights), STATUS_ESTIMATE)]


def total(lines: list[CostLine]) -> Decimal:
    """Sum of the visible amounts; lines without data are not counted (US-11)."""
    return money(sum((line.amount_eur for line in lines if line.amount_eur is not None), Decimal(0)))
