"""Import of the EC Weekly Oil Bulletin: the parser on a workbook with the real layout, and the storing logic."""

import datetime as dt
import io
from contextlib import contextmanager
from decimal import Decimal

import pytest
from openpyxl import Workbook

from app.importers.oil_bulletin import COUNTRIES, BulletinError, parse_bulletin
from app.importers.run import import_source

HEADERS = [
    "in EUR",
    "Euro-super 95  (I)",
    "Gas oil automobile Automotive gas oil Dieselkraftstoff (I)",
    " Gas oil de chauffage Heating gas oil Heizöl (II)",
]


def workbook(rows=None, unit="1000 l", date=dt.datetime(2026, 11, 16)) -> io.BytesIO:
    book = Workbook()
    sheet = book.active
    sheet.append(HEADERS)
    sheet.append([date, unit, unit, unit])
    for name in COUNTRIES if rows is None else rows:
        sheet.append([name, 1764.3, 2165.95, 1513.42])
    sheet.append(["Austria", 1607, 2035, 1739.03])
    out = io.BytesIO()
    book.save(out)
    out.seek(0)
    return out


def test_parses_supported_countries_and_converts_to_litres():
    bulletin = parse_bulletin(workbook())
    assert bulletin.date == dt.date(2026, 11, 16)
    assert len(bulletin.rows) == 2 * len(COUNTRIES)
    lt = {r.fuel_type: r.price_eur_l for r in bulletin.rows if r.country == "LT"}
    assert lt == {"petrol95": Decimal("1.764"), "diesel": Decimal("2.166")}


def test_other_countries_are_ignored():
    assert all(r.country in COUNTRIES.values() for r in parse_bulletin(workbook()).rows)


def test_missing_country_fails_the_whole_file():
    with pytest.raises(BulletinError, match="Slovakia: no row"):
        parse_bulletin(workbook(rows=[c for c in COUNTRIES if c != "Slovakia"]))


def test_unexpected_unit_is_refused():
    with pytest.raises(BulletinError, match="unit"):
        parse_bulletin(workbook(unit="litre"))


def test_future_date_is_refused():
    with pytest.raises(BulletinError, match="future"):
        parse_bulletin(workbook(), today=dt.date(2026, 1, 1))


def test_not_an_xlsx_file_is_refused():
    with pytest.raises(BulletinError, match="cannot be read"):
        parse_bulletin(io.BytesIO(b"not a workbook"))


class FakeConnection:
    def __init__(self):
        self.calls = []
        self.fail_on = None

    @contextmanager
    def transaction(self):
        yield

    def execute(self, sql, params=()):
        if self.fail_on and self.fail_on in sql:
            raise RuntimeError("database refused")
        self.calls.append((sql, params))


def test_import_calls_the_procedures_and_finishes_with_ok():
    conn = FakeConnection()
    bulletin = import_source(conn, workbook())
    prices = [c for c in conn.calls if c[0].startswith("CALL import_fuel_price")]
    assert len(prices) == len(bulletin.rows) == 16
    assert conn.calls[-1][0].startswith("CALL finish_fuel_import('ok'")
    assert conn.calls[-1][1][0] == dt.date(2026, 11, 16)


def test_bad_file_is_logged_as_failed_and_nothing_is_stored():
    conn = FakeConnection()
    with pytest.raises(BulletinError):
        import_source(conn, workbook(unit="litre"))
    assert [c[0] for c in conn.calls] == ["CALL finish_fuel_import('failed', NULL, %s)"]


def test_database_error_is_logged_as_failed():
    conn = FakeConnection()
    conn.fail_on = "import_fuel_price"
    with pytest.raises(RuntimeError):
        import_source(conn, workbook())
    assert conn.calls[-1][0].startswith("CALL finish_fuel_import('failed'")
