"""Weekly fuel price import: python -m app.importers.run [--file bulletin.xlsx | --url https://...]

Without --file and --url the link of the latest workbook is read from the bulletin page of the European Commission.

Runs as the database role rw_admin_user (connection string in ADMIN_DATABASE_URL). It calls the existing procedures
import_fuel_price and finish_fuel_import, so a failed import is logged and the old prices stay (US-21).
"""

from __future__ import annotations

import argparse
import io
import os
import sys
from typing import Any

from app.importers.discovery import PAGE_URL, find_latest_link
from app.importers.oil_bulletin import Bulletin, parse_bulletin


def store(conn: Any, bulletin: Bulletin) -> None:
    """All prices of one bulletin in ONE transaction; a duplicate week is ignored by the procedure itself."""
    with conn.transaction():
        for row in bulletin.rows:
            conn.execute(
                "CALL import_fuel_price(%s, %s::fuel_type, %s, %s)",
                (row.country, row.fuel_type, row.price_eur_l, bulletin.date),
            )
        conn.execute("CALL finish_fuel_import('ok', %s, %s)", (bulletin.date, f"{len(bulletin.rows)} prices"))


def log_failure(conn: Any, message: str) -> None:
    with conn.transaction():
        conn.execute("CALL finish_fuel_import('failed', NULL, %s)", (message[:500],))


def import_source(conn: Any, source: Any) -> Bulletin:
    """Parse, then store. On any problem the failure is logged and the exception goes on to the caller."""
    try:
        bulletin = parse_bulletin(source)
        store(conn, bulletin)
    except Exception as exc:
        log_failure(conn, str(exc))
        raise
    return bulletin


def download(url: str) -> io.BytesIO:  # pragma: no cover - network
    import httpx

    response = httpx.get(url, follow_redirects=True, timeout=60, headers={"User-Agent": "RouteWise-fuel-import/1.0"})
    response.raise_for_status()
    return io.BytesIO(response.content)


def discover_and_download() -> io.BytesIO:  # pragma: no cover - network
    import httpx

    page = httpx.get(PAGE_URL, follow_redirects=True, timeout=60, headers={"User-Agent": "RouteWise-fuel-import/1.0"})
    page.raise_for_status()
    return download(find_latest_link(page.text, PAGE_URL))


def main(argv: list[str] | None = None) -> int:  # pragma: no cover - needs a database
    import psycopg

    parser = argparse.ArgumentParser(description="Import the EC Weekly Oil Bulletin (prices with taxes).")
    group = parser.add_mutually_exclusive_group()
    group.add_argument("--file", help="path of a downloaded .xlsx file")
    group.add_argument("--url", default=os.environ.get("FUEL_BULLETIN_URL"), help="direct link to the .xlsx file")
    args = parser.parse_args(argv)
    dsn = os.environ.get("ADMIN_DATABASE_URL")
    if not dsn:
        print("ADMIN_DATABASE_URL is not set (login of rw_admin_user)", file=sys.stderr)
        return 2
    with psycopg.connect(dsn, autocommit=True) as conn:
        try:
            if args.file:
                source = args.file
            elif args.url:
                source = download(args.url)
            else:
                source = discover_and_download()
            bulletin = import_source(conn, source)
        except Exception as exc:
            print(f"import failed: {exc}", file=sys.stderr)
            return 1
    print(f"imported {len(bulletin.rows)} prices of the bulletin of {bulletin.date}")
    return 0


if __name__ == "__main__":  # pragma: no cover
    raise SystemExit(main())
