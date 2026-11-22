"""Where to refuel (US-30). A tip only: it never changes the trip total.

Candidate stations are the country borders (first point of every country after the origin). The rule is the classic
"cheapest next station" greedy: if a cheaper station can be reached on one tank, buy only enough to get there;
otherwise fill up (or buy just enough to arrive with the reserve)."""

from decimal import ROUND_HALF_UP, Decimal

from app.core.constants import FUEL_RESERVE_FRACTION, LITRE_QUANT, MONEY_QUANT
from app.domain.models import CountryLeg, RefuelPlan, RefuelStop

HUNDRED = Decimal(100)


def plan_refuel(
    legs: list[CountryLeg],
    *,
    consumption_l100: Decimal,
    tank_l: Decimal,
    start_fraction: Decimal,
    prices: dict[str, Decimal],
) -> RefuelPlan | None:
    """Returns None when the trip cannot be driven with the reserve left (a plan is impossible)."""
    if not legs or any(leg.country not in prices for leg in legs):
        return None
    reserve = tank_l * FUEL_RESERVE_FRACTION
    km_per_litre = HUNDRED / consumption_l100
    offsets, acc = [], Decimal(0)
    for leg in legs:
        offsets.append(acc)
        acc += leg.km
    total_km = acc
    stations = [(offsets[i], prices[legs[i].country], legs[i].country) for i in range(1, len(legs))]

    fuel = tank_l * start_fraction
    position = Decimal(0)
    stops: list[RefuelStop] = []
    for idx, (offset, price, country) in enumerate(stations):
        fuel -= (offset - position) / km_per_litre
        position = offset
        if fuel < reserve:
            return None  # could not even reach this station
        reach_km = (tank_l - reserve) * km_per_litre  # distance one full tank allows
        cheaper = next((o for o, p, _ in stations[idx + 1 :] if o - offset <= reach_km and p < price), None)
        target_km = cheaper if cheaper is not None else None
        if target_km is not None:
            needed = (target_km - offset) / km_per_litre + reserve
        elif total_km - offset <= reach_km:
            needed = (total_km - offset) / km_per_litre + reserve
        else:
            needed = tank_l
        buy = min(needed, tank_l) - fuel
        if buy > Decimal("0.05"):
            fuel += buy
            stops.append(
                RefuelStop(
                    len(stops) + 1,
                    country,
                    offset.quantize(LITRE_QUANT),
                    buy.quantize(LITRE_QUANT, rounding=ROUND_HALF_UP),
                    price,
                )
            )
    fuel -= (total_km - position) / km_per_litre
    if fuel < reserve - Decimal("0.05"):
        return None

    origin_price, arrival_price = prices[legs[0].country], prices[legs[-1].country]
    start_liters = tank_l * start_fraction
    baseline = sum((leg.km * consumption_l100 / HUNDRED * prices[leg.country] for leg in legs), Decimal(0))
    plan_cost = (
        start_liters * origin_price + sum((s.liters * s.price_eur_l for s in stops), Decimal(0)) - fuel * arrival_price
    )
    saving = max(Decimal(0), baseline - plan_cost).quantize(MONEY_QUANT, rounding=ROUND_HALF_UP)
    return RefuelPlan(stops=stops, saving_eur=saving)
