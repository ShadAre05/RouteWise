"""Finding the workbook link on the bulletin page (excerpt of the page saved on 24 November 2026)."""

import pytest

from app.importers.discovery import LinkNotFound, find_latest_link

WITH = (
    "/document/download/264c2d0f-f161-4ea3-a777-78faae59bea0_en"
    "?filename=Weekly%20Oil%20Bulletin%20Weekly%20prices%20with%20Taxes%20-%202026-09-21.xlsx"
)
WITHOUT = (
    "/document/download/78311f92-68f8-4b82-b5cf-1293beeaae77_en"
    "?filename=Weekly%20Oil%20Bulletin%20Weekly%20prices%20without%20taxes%20-%202024-02-19.xlsx"
)
BASE = "https://energy.ec.europa.eu"

PAGE = f"""
<div class="ecl-file__title">Prices with taxes latest prices (xlsx)</div>
<div class="ecl-file__primary-meta-item">19 November 2026</div>
<footer><a href="{WITH}" class="ecl-link ecl-file__download">Download (16.51 KB - XLSX)</a></footer>
<div class="ecl-file__title">Prices without taxes latest prices(xlsx)</div>
<footer><a href="{WITHOUT}" class="ecl-link ecl-file__download">Download (16.96 KB - XLSX)</a></footer>
"""


def test_finds_the_with_taxes_workbook_after_its_title():
    assert find_latest_link(PAGE) == BASE + WITH


def test_does_not_take_the_without_taxes_file():
    assert "without" not in find_latest_link(PAGE).lower()


def test_falls_back_to_the_file_name_when_the_title_changes():
    page = f'<a href="{WITHOUT}">x</a><a href="{WITH}">y</a>'
    assert find_latest_link(page) == BASE + WITH


def test_unknown_page_is_an_error_not_a_guess():
    with pytest.raises(LinkNotFound):
        find_latest_link("<html><a href='/other.pdf'>x</a></html>")
