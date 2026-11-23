"""The version-controlled OpenAPI file must describe the application as it is now."""

import importlib.util
from pathlib import Path

import yaml
from openapi_spec_validator import validate

ROOT = Path(__file__).resolve().parents[3]
SPEC_FILE = ROOT / "docs" / "api" / "openapi.yaml"


def _exporter():
    spec = importlib.util.spec_from_file_location("export_openapi", ROOT / "backend" / "scripts" / "export_openapi.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def test_committed_spec_is_up_to_date():
    assert SPEC_FILE.read_text(encoding="utf-8") == _exporter().render_spec(), (
        "docs/api/openapi.yaml is out of date: run `python scripts/export_openapi.py` in the backend folder"
    )


def test_committed_spec_is_valid_openapi():
    validate(yaml.safe_load(SPEC_FILE.read_text(encoding="utf-8")))


def test_every_operation_is_documented():
    spec = yaml.safe_load(SPEC_FILE.read_text(encoding="utf-8"))
    for path, item in spec["paths"].items():
        for method, operation in item.items():
            where = f"{method.upper()} {path}"
            assert operation.get("summary"), f"{where}: no summary"
            assert operation.get("description"), f"{where}: no description"
            assert operation.get("responses"), f"{where}: no responses"
            assert all(r.get("description") for r in operation["responses"].values()), f"{where}: undescribed response"


def test_every_example_matches_its_schema():
    """The recorded examples must be valid against the schema of the operation they document."""
    from tests.contract import JSON, spec, validate_value

    checked = 0
    for path, item in spec()["paths"].items():
        for method, operation in item.items():
            where = f"{method.upper()} {path}"
            media = operation.get("requestBody", {}).get("content", {}).get(JSON)
            for name, example in (media or {}).get("examples", {}).items():
                validate_value(media["schema"], example["value"], f"{where} request example {name}")
                checked += 1
            for code, response in operation["responses"].items():
                media = response.get("content", {}).get(JSON)
                if media is None:
                    continue
                examples = media.get("examples") or {"single": {"value": media["example"]}}
                for name, example in examples.items():
                    validate_value(media["schema"], example["value"], f"{where} {code} example {name}")
                    checked += 1
    assert checked > 60


def test_success_responses_have_examples():
    from tests.contract import JSON, spec

    for path, item in spec()["paths"].items():
        for method, operation in item.items():
            for code, response in operation["responses"].items():
                media = response.get("content", {}).get(JSON)
                if code.startswith("2") and media is not None:
                    assert media.get("examples") or media.get("example"), f"{method.upper()} {path} {code}: no example"
            if "requestBody" in operation:
                body = operation["requestBody"]["content"][JSON]
                assert body.get("examples"), f"{method.upper()} {path}: no request example"


def test_contract_check_detects_a_mismatch():
    import pytest

    from tests.contract import check_response

    check_response("GET", "/api/v1/countries", 200, [{"code": "LT", "name": "Lithuania", "timezone": "Europe/Vilnius"}])
    with pytest.raises(AssertionError):
        check_response("GET", "/api/v1/countries", 200, [{"code": "LT"}])
    with pytest.raises(AssertionError, match="not documented"):
        check_response("GET", "/api/v1/countries", 418, None)


def test_markdown_reference_is_up_to_date():
    spec = importlib.util.spec_from_file_location(
        "render_reference", ROOT / "backend" / "scripts" / "render_reference.py"
    )
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    for name, text in module.render_all().items():
        assert (ROOT / "docs" / "api" / "reference" / name).read_text(encoding="utf-8") == text, (
            f"docs/api/reference/{name} is out of date: run `python scripts/render_reference.py` in the backend folder"
        )
