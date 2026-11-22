"""Driving days, nights and arrival time (US-08, US-29). Pure functions."""

import math
from datetime import date, datetime, timedelta
from decimal import Decimal

from app.core.constants import HOURS_PER_DAY, MINUTES_PER_HOUR, WINTER_MONTHS
from app.domain.models import Schedule


def season_for(day: date) -> str:
    """Same rule as rw.season_for() in the database: winter = 1 Nov - 31 Mar."""
    return "winter" if day.month in WINTER_MONTHS else "summer"


def plan_schedule(driving_minutes: Decimal, daily_limit_h: Decimal, departed_at: datetime) -> Schedule:
    """Every day starts at the departure time of day and drives at most `daily_limit_h` hours.
    Only driving time is counted: the stop lasts until the same time of day the next morning."""
    if driving_minutes <= 0 or daily_limit_h <= 0:
        raise ValueError("driving time and daily limit must be positive")
    daily_minutes = daily_limit_h * MINUTES_PER_HOUR
    days = max(1, math.ceil(driving_minutes / daily_minutes))
    nights = days - 1
    last_day_minutes = driving_minutes - daily_minutes * nights
    arrival = departed_at + timedelta(hours=HOURS_PER_DAY * nights) + timedelta(minutes=float(last_day_minutes))
    return Schedule(days=days, nights=nights, arrival_at=arrival)
