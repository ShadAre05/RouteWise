"""Reads the EC Weekly Oil Bulletin ("Prices with taxes" workbook) into fuel prices of the supported countries.

Layout of the workbook (checked on the file of 2026-11-16, sheet "Sheet1"):
  row 1   headers; one column per product, e.g. "Euro-super 95  (I)" and "Gas oil automobile ... (I)"
  row 2   the bulletin date in column A and the unit of each column ("1000 l")
  rows 3+ one row per country (English name in column A), price in EUR per 1000 litres, empty when not reported

The date inside the file is used, not the file name. Prices are converted to EUR per litre.
"""

from __future__ import annotations

import datetime as dt
from dataclasses import dataclass
from decimal import ROUND_HALF_UP, Decimal
from pathlib import Path
from typing import IO

COUNTRIES = {
    "Lithuania": "LT",
    "Latvia": "LV",
    "Estonia": "EE",
    "Poland": "PL",
    "Germany": "DE",
    "Denmark": "DK",
    "Czechia": "CZ",
    "Slovakia": "SK",
}
# header text (lower case, a part of it) -> fuel_type of the database
PRODUCTS = {"euro-super 95": "petrol95", "gas oil automobile": "diesel"}
MIN_PRICE = Decimal("0.500")  # the same sanity range as the CHECK constraint of fuel_prices
MAX_PRICE = Decimal("4.000")


class BulletinError(Exception):
    """The file is not a bulletin we understand; nothing must be imported from it."""


@dataclass(frozen=True)
class PriceRow:
    country: str
    fuel_type: str
    price_eur_l: Decimal


@dataclass(frozen=True)
class Bulletin:
    date: dt.date
    rows: list[PriceRow]


def parse_bulletin(source: str | Path | IO[bytes], today: dt.date | None = None) -> Bulletin:
    from openpyxl import load_workbook

    try:
        sheet = load_workbook(source, read_only=True, data_only=True).worksheets[0]
    except Exception as exc:  # unreadable or not an xlsx file
        raise BulletinError(f"the file cannot be read as an xlsx workbook: {exc}") from exc
    table = [tuple(row) for row in sheet.iter_rows(values_only=True)]
    if len(table) < 3:
        raise BulletinError("the sheet has too few rows")

    headers, units = table[0], table[1]
    date = units[0] if isinstance(units[0], dt.datetime) else None
    if date is None:
        raise BulletinError("the bulletin date is not in cell A2")
    bulletin_date = date.date()
    if bulletin_date > (today or dt.date.today()) + dt.timedelta(days=1):
        raise BulletinError(f"the bulletin date {bulletin_date} is in the future")

    columns: dict[str, int] = {}
    for index, header in enumerate(headers):
        text = " ".join(str(header or "").lower().split())
        for needle, fuel in PRODUCTS.items():
            if needle in text:
                columns[fuel] = index
    for fuel in PRODUCTS.values():
        if fuel not in columns:
            raise BulletinError(f"the column of {fuel} was not found in the header row")
        if str(units[columns[fuel]]).strip().lower() != "1000 l":
            raise BulletinError(f"unexpected unit {units[columns[fuel]]!r} for {fuel}, expected '1000 l'")

    by_name = {str(row[0]).strip(): row for row in table[2:] if row and row[0]}
    rows: list[PriceRow] = []
    problems: list[str] = []
    for name, code in COUNTRIES.items():
        row = by_name.get(name)
        if row is None:
            problems.append(f"{name}: no row")
            continue
        for fuel, index in columns.items():
            value = row[index] if index < len(row) else None
            if not isinstance(value, int | float):
                problems.append(f"{name}/{fuel}: no price")
                continue
            price = (Decimal(str(value)) / 1000).quantize(Decimal("0.001"), rounding=ROUND_HALF_UP)
            if not MIN_PRICE <= price <= MAX_PRICE:
                problems.append(f"{name}/{fuel}: {price} EUR/l is outside {MIN_PRICE}-{MAX_PRICE}")
                continue
            rows.append(PriceRow(code, fuel, price))
    if problems:
        raise BulletinError("; ".join(problems))
    return Bulletin(bulletin_date, rows)
