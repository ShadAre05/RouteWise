"""Contract check: what the API really answers must match the committed OpenAPI file.

`ContractClient` is a TestClient that validates every JSON answer against the schema of the documented response,
and fails when a status code is not documented. Used by the HTTP-level unit tests and by the integration tests.
"""

import copy
import re
from functools import lru_cache
from pathlib import Path

import yaml
from fastapi.testclient import TestClient
from jsonschema import Draft202012Validator

SPEC_FILE = Path(__file__).resolve().parents[2] / "docs" / "api" / "openapi.yaml"
JSON = "application/json"
UNDOCUMENTED_OK = {"500"}  # unexpected server errors are not part of the contract


@lru_cache
def spec() -> dict:
    return yaml.safe_load(SPEC_FILE.read_text(encoding="utf-8"))


def resolve(node):
    """Inline every local $ref of the specification (the schemas are not recursive)."""
    if isinstance(node, dict):
        if "$ref" in node:
            target = spec()
            for part in node["$ref"].removeprefix("#/").split("/"):
                target = target[part]
            return resolve(copy.deepcopy(target))
        return {key: resolve(value) for key, value in node.items()}
    if isinstance(node, list):
        return [resolve(item) for item in node]
    return node


def _path_regex(template: str) -> re.Pattern:
    return re.compile("^" + re.sub(r"\{[^}/]+\}", "[^/]+", template) + "$")


def find_operation(method: str, path: str) -> dict | None:
    for template, item in spec()["paths"].items():
        if _path_regex(template).match(path) and method.lower() in item:
            return item[method.lower()]
    return None


def validate_value(schema: dict, value, where: str) -> None:
    errors = sorted(Draft202012Validator(resolve(schema)).iter_errors(value), key=lambda e: list(e.path))
    assert not errors, f"{where}: {errors[0].message} at {list(errors[0].path)}"


def check_response(method: str, path: str, status: int, body) -> None:
    operation = find_operation(method, path)
    if operation is None:
        return
    documented = operation["responses"]
    if str(status) not in documented:
        assert str(status) in UNDOCUMENTED_OK, f"{method} {path}: status {status} is not documented"
        return
    content = documented[str(status)].get("content", {}).get(JSON)
    if content is not None and body is not None:
        validate_value(content["schema"], body, f"{method} {path} -> {status}")


class ContractClient(TestClient):
    def request(self, method, url, **kwargs):
        response = super().request(method, url, **kwargs)
        is_json = response.headers.get("content-type", "").startswith(JSON)
        check_response(method, response.request.url.path, response.status_code, response.json() if is_json else None)
        return response
