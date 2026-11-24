"""Finds the link of the latest "Prices with taxes" workbook on the Weekly Oil Bulletin page of the European Commission.

Checked on the saved page of 24 November 2026: the file is a block whose title is "Prices with taxes latest prices
(xlsx)", followed by a link `/document/download/<id>_en?filename=...xlsx`. The same id was used for this file in an
older (2024) bulletin mail, so the link appears to stay the same while the file is replaced every week; the search
does not rely on that and reads the page each time.
"""

from __future__ import annotations

import re
from urllib.parse import unquote, urljoin

PAGE_URL = "https://energy.ec.europa.eu/data-and-analysis/weekly-oil-bulletin_en"
_TITLE = re.compile(r"Prices with taxes latest prices", re.IGNORECASE)
_HREF = re.compile(r'href="([^"]*/document/download/[^"]+)"')


class LinkNotFound(Exception):
    pass


def find_latest_link(html: str, page_url: str = PAGE_URL) -> str:
    # 1. the block titled "Prices with taxes latest prices": the first download link after the title
    title = _TITLE.search(html)
    if title:
        link = _HREF.search(html, title.end())
        if link:
            return urljoin(page_url, link.group(1).replace("&amp;", "&"))
    # 2. otherwise any download link whose file name says "with taxes" (not "without taxes")
    for link in _HREF.finditer(html):
        name = unquote(link.group(1)).lower()
        if "prices with taxes" in name and name.endswith(".xlsx"):
            return urljoin(page_url, link.group(1).replace("&amp;", "&"))
    raise LinkNotFound("no 'Prices with taxes' workbook link on the bulletin page; the page layout may have changed")
