from datetime import UTC, datetime
from decimal import Decimal as D

import pytest

from app.domain.models import CostLine, SpeedProfile, Tariff
from app.domain.models import CountryLeg as L
from app.services import cost_calculator as calc
from app.services.country_split import normalize_legs
from app.services.refuel_planner import plan_refuel
from app.services.schedule import plan_schedule, season_for
from app.services.travel_time import driving_minutes

START = datetime(2026, 11, 1, 9, 0, tzinfo=UTC)


# ---------- schedule
@pytest.mark.parametrize(
    "day,expected",
    [("2026-11-01", "winter"), ("2026-03-31", "winter"), ("2026-04-01", "summer"), ("2026-10-31", "summer")],
)
def test_season_boundaries(day, expected):
    from datetime import date

    assert season_for(date.fromisoformat(day)) == expected


def test_schedule_single_day():
    s = plan_schedule(D(300), D(8), START)
    assert (s.days, s.nights) == (1, 0)
    assert s.arrival_at == datetime(2026, 11, 1, 14, 0, tzinfo=UTC)


def test_schedule_multi_day_starts_each_morning_at_the_same_time():
    s = plan_schedule(D(1200), D(8), START)  # 20 h of driving, 8 h a day
    assert (s.days, s.nights) == (3, 2)
    assert s.arrival_at == datetime(2026, 11, 3, 13, 0, tzinfo=UTC)  # 2 nights + 4 h on the last day


def test_schedule_exact_multiple_has_no_extra_day():
    assert plan_schedule(D(960), D(8), START).days == 2


@pytest.mark.parametrize("minutes,limit", [(0, 8), (100, 0)])
def test_schedule_rejects_non_positive_input(minutes, limit):
    with pytest.raises(ValueError):
        plan_schedule(D(minutes), D(limit), START)


# ---------- travel time and normalisation
def test_driving_minutes_uses_profile_and_falls_back_to_osrm_share():
    legs = [L("LT", D(100)), L("XX", D(100))]
    minutes = driving_minutes(legs, {"LT": SpeedProfile("LT", D(100), 130, None)}, D(200))
    assert minutes == D(60) + D(100)  # 100 km at 100 km/h + half of 200 minutes


def test_driving_minutes_empty_route_is_zero():
    assert driving_minutes([], {}, D(10)) == 0


def test_normalize_legs_scales_to_route_length():
    legs = normalize_legs([L("LT", D(90)), L("PL", D(90))], D(200))
    assert [leg.km for leg in legs] == [D("100.0"), D("100.0")]
    assert normalize_legs([], D(10)) == []


# ---------- cost lines
def test_fuel_line_amount_and_rounding():
    (line,) = calc.fuel_lines([L("DE", D("100"))], D("7.0"), {"DE": D("1.859")})
    assert line.amount_eur == D("13.01") and line.status == "ok"


def test_fuel_price_missing_uses_average_then_no_data():
    (line,) = calc.fuel_lines([L("EE", D(100))], D("5.0"), {"DE": D("1.8"), "PL": D("1.4")})
    assert line.status == "eu_average" and line.unit_price == D("1.600")
    (line,) = calc.fuel_lines([L("EE", D(100))], D("5.0"), {})
    assert line.status == "no_data" and line.amount_eur is None


def test_road_vignette_per_country_and_no_tolls_variant():
    legs = [L("CZ", D(200)), L("DE", D(300))]
    tariffs = [Tariff("CZ", "vignette", D("12"), "per_trip")]
    assert calc.road_lines(legs, tariffs, "fastest", D(0))[0].amount_eur == D("12.00")
    assert calc.road_lines(legs, tariffs, "no_tolls", D(0)) == []


def test_road_per_km_toll_is_shared_and_missing_country_is_no_data_only_when_tolls_are_used():
    legs = [L("PL", D(300)), L("DE", D(100))]
    tariffs = [Tariff("PL", "toll", D("0.10"), "per_km")]
    lines = calc.road_lines(legs, tariffs, "fastest", D(50))
    assert lines[0].amount_eur == D("5.00") and lines[0].status == "estimate"
    assert lines[1].status == "no_data"
    assert calc.road_lines(legs, tariffs, "fastest", D(0)) == []


def test_lodging_rules():
    rates = {"PL": D("50.00")}
    assert calc.lodging_lines(0, "PL", rates, None) == []
    assert calc.lodging_lines(2, "PL", rates, None)[0].amount_eur == D("100.00")
    assert calc.lodging_lines(2, "PL", rates, D("80"))[0].status == "user_entered"
    assert calc.lodging_lines(1, "EE", rates, None)[0].status == "no_data"
    assert calc.lodging_lines(1, None, rates, None)[0].status == "no_data"


def test_total_ignores_lines_without_data():
    lines = [CostLine("fuel", "PL", None, None, D("10.005"), "ok"), CostLine("road", "DE", None, None, None, "no_data")]
    assert calc.total(lines) == D("10.01")


# ---------- refuel planner
PRICES = {"PL": D("1.40"), "DE": D("1.90"), "DK": D("1.80")}


def test_refuel_plan_buys_in_the_cheap_country():
    legs = [L("PL", D(400)), L("DE", D(400)), L("DK", D(200))]
    plan = plan_refuel(legs, consumption_l100=D(7), tank_l=D(50), start_fraction=D(1), prices=PRICES)
    assert plan is not None
    assert plan.stops and all(s.liters > 0 for s in plan.stops)
    assert plan.stops[0].country == "DE"  # the first border: DE is dearer, so only a little is bought
    assert plan.saving_eur >= 0


def test_refuel_plan_none_when_start_fuel_is_not_enough():
    legs = [L("DE", D(300)), L("PL", D(500))]
    assert plan_refuel(legs, consumption_l100=D(7), tank_l=D(50), start_fraction=D("0.25"), prices=PRICES) is None


def test_refuel_plan_none_without_prices_or_legs():
    assert plan_refuel([], consumption_l100=D(7), tank_l=D(50), start_fraction=D(1), prices=PRICES) is None
    assert (
        plan_refuel([L("EE", D(10))], consumption_l100=D(7), tank_l=D(50), start_fraction=D(1), prices=PRICES) is None
    )


def test_refuel_plan_single_country_needs_no_stops():
    plan = plan_refuel([L("PL", D(200))], consumption_l100=D(7), tank_l=D(50), start_fraction=D(1), prices=PRICES)
    assert plan.stops == [] and plan.saving_eur == 0
