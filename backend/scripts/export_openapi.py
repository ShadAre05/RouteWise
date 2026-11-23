"""Writes the OpenAPI specification of the application to docs/api/openapi.yaml.

Run from the backend folder:  python scripts/export_openapi.py
The file is version-controlled; a unit test and the CI pipeline fail when it is out of date.
"""

import sys
from pathlib import Path

import yaml

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.core.config import Settings  # noqa: E402
from app.main import create_app  # noqa: E402

SPEC_PATH = Path(__file__).resolve().parents[2] / "docs" / "api" / "openapi.yaml"
PLACEHOLDER_SETTINGS = Settings(database_url="postgresql+psycopg://x:x@localhost/x", jwt_secret="x" * 32)


def _block_style(dumper: yaml.SafeDumper, value: str) -> yaml.ScalarNode:
    style = "|" if "\n" in value else None
    return dumper.represent_scalar("tag:yaml.org,2002:str", value.strip("\n") + "\n" if style else value, style=style)


yaml.SafeDumper.add_representer(str, _block_style)


def render_spec() -> str:
    spec = create_app(PLACEHOLDER_SETTINGS).openapi()
    return yaml.safe_dump(spec, sort_keys=False, allow_unicode=True, width=110)


if __name__ == "__main__":
    SPEC_PATH.write_text(render_spec(), encoding="utf-8", newline="\n")
    print(f"written {SPEC_PATH}")
