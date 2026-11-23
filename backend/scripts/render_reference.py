"""Renders the Markdown API reference (docs/api/reference/) from docs/api/openapi.yaml.

Run from the backend folder after `python scripts/export_openapi.py`:  python scripts/render_reference.py
The reference is generated, never edited by hand; a unit test fails when it is out of date.
"""

import json
from pathlib import Path

import yaml

DOCS = Path(__file__).resolve().parents[2] / "docs" / "api"
SPEC_PATH = DOCS / "openapi.yaml"
OUT_DIR = DOCS / "reference"
JSON = "application/json"
METHOD_ORDER = {"get": 0, "post": 1, "patch": 2, "delete": 3}
GENERATED = "<!-- Generated from openapi.yaml by backend/scripts/render_reference.py. Do not edit by hand. -->"


class Spec:
    def __init__(self, data: dict) -> None:
        self.data = data
        self.schemas = data["components"]["schemas"]

    def schema(self, node: dict) -> dict:
        if "$ref" in node:
            return self.schemas[node["$ref"].rsplit("/", 1)[1]]
        return node

    def type_of(self, node: dict) -> str:
        if "$ref" in node:
            name = node["$ref"].rsplit("/", 1)[1]
            return f"[{name}](data-models.md#{name.lower()})"
        if "anyOf" in node:
            variants = [v for v in node["anyOf"] if v.get("type") != "null"]
            nullable = len(variants) != len(node["anyOf"])
            kinds = {v.get("type") for v in variants}
            text = (
                "decimal (number or numeric string)"
                if kinds == {"number", "string"}
                else " or ".join(self.type_of(v) for v in variants)
            )
            return text + (", nullable" if nullable else "")
        if "const" in node:
            return f"`{json.dumps(node['const'])}`"
        if "enum" in node:
            return "one of " + ", ".join(f"`{v}`" for v in node["enum"])
        kind = node.get("type", "object")
        if kind == "array":
            return f"array of {self.type_of(node['items'])}"
        if kind == "string" and node.get("format"):
            return f"string ({node['format']})"
        return kind

    @staticmethod
    def limits(node: dict) -> str:
        pieces = []
        variants = [node] + [v for v in node.get("anyOf", []) if isinstance(v, dict)]
        for v in variants:
            for key, label in (
                ("minLength", "min length"),
                ("maxLength", "max length"),
                ("minimum", "min"),
                ("maximum", "max"),
                ("exclusiveMinimum", "greater than"),
                ("default", "default"),
            ):
                if key in v and f"{label} {v[key]}" not in pieces and v[key] is not None:
                    pieces.append(f"{label} {v[key]}")
        return "; ".join(pieces)


def md_escape(text: str) -> str:
    return " ".join(str(text).split()).replace("|", "\\|")


def property_rows(spec: Spec, schema: dict) -> list[str]:
    required = set(schema.get("required", []))
    rows = ["| Field | Type | Required | Description |", "|---|---|---|---|"]
    for name, node in schema.get("properties", {}).items():
        description = md_escape(node.get("description", "") or spec.schema(node).get("description", ""))
        limits = spec.limits(node)
        if limits:
            description = f"{description} ({limits})" if description else limits[0].upper() + limits[1:]
        example = node.get("examples", [None])[0] if node.get("examples") else None
        if example is not None:
            description = f"{description} Example: `{json.dumps(example)}`.".strip()
        rows.append(f"| `{name}` | {spec.type_of(node)} | {'yes' if name in required else 'no'} | {description} |")
    return rows


def render_models(spec: Spec) -> str:
    lines = [
        "# Data models",
        "",
        GENERATED,
        "",
        "All models of the API, as they appear in `openapi.yaml`. Decimal numbers are sent",
        "as JSON strings. Fields marked nullable can be `null`.",
        "",
    ]
    for name, schema in sorted(spec.schemas.items()):
        lines += [f"## {name}", ""]
        if schema.get("description"):
            lines += [schema["description"].strip(), ""]
        if schema.get("properties"):
            lines += property_rows(spec, schema) + [""]
        else:
            lines += [f"Type: {spec.type_of(schema)}", ""]
    return "\n".join(lines).rstrip() + "\n"


def example_blocks(media: dict, compact: bool) -> list[str]:
    examples = media.get("examples") or (
        {"example": {"summary": "Example", "value": media["example"]}} if "example" in media else {}
    )
    lines = []
    for example in examples.values():
        lines.append(f"*{example['summary']}*")
        text = (
            json.dumps(example["value"], ensure_ascii=False)
            if compact
            else json.dumps(example["value"], indent=2, ensure_ascii=False)
        )
        lines += ["", "```json", text, "```", ""]
    return lines


def render_operation(spec: Spec, method: str, path: str, op: dict) -> list[str]:
    secure = bool(op.get("security"))
    lines = [f"### {method.upper()} {path}", "", f"**{op['summary']}**", "", op.get("description", "").strip(), ""]
    lines += [
        f"Authentication: {'`Authorization: Bearer <access token>` required' if secure else 'none (public)'}.",
        "",
    ]
    params = op.get("parameters", [])
    if params:
        lines += ["| Parameter | In | Required | Type | Description |", "|---|---|---|---|---|"]
        for p in params:
            description = md_escape(p.get("description", ""))
            limits = spec.limits(p["schema"])
            lines.append(
                f"| `{p['name']}` | {p['in']} | {'yes' if p.get('required') else 'no'} | {spec.type_of(p['schema'])}"
                f" | {description}{' (' + limits + ')' if limits else ''} |"
            )
        lines.append("")
    body = op.get("requestBody", {}).get("content", {}).get(JSON)
    if body:
        lines += ["**Request body** - " + spec.type_of(body["schema"]), ""]
        lines += property_rows(spec, spec.schema(body["schema"])) + [""]
        lines += example_blocks(body, compact=False)
    lines += ["**Responses**", "", "| Status | Meaning | Body |", "|---|---|---|"]
    for code, response in op["responses"].items():
        media = response.get("content", {}).get(JSON)
        lines.append(
            f"| {code} | {md_escape(response['description'])} | {spec.type_of(media['schema']) if media else 'empty'} |"
        )
    lines.append("")
    for code, response in op["responses"].items():
        media = response.get("content", {}).get(JSON)
        if media:
            lines += [f"Example, status {code}:", ""] + example_blocks(media, compact=not code.startswith("2"))
    return lines


def render_endpoints(spec: Spec) -> str:
    lines = [
        "# Endpoint reference",
        "",
        GENERATED,
        "",
        "Every endpoint with its parameters, request body, responses and examples. Base path of the versioned",
        "endpoints is `/api/v1`. Models are described in [data-models.md](data-models.md); errors in",
        "[the error guide](../guides/errors.md).",
        "",
    ]
    by_tag: dict[str, list] = {}
    for path, item in spec.data["paths"].items():
        for method, op in item.items():
            by_tag.setdefault(op["tags"][0], []).append((path, method, op))
    for tag in spec.data["tags"]:
        lines += [f"## {tag['name']}", "", tag.get("description", ""), ""]
        for path, method, op in sorted(
            by_tag.get(tag["name"], []), key=lambda t: (t[0].split("{")[0], METHOD_ORDER[t[1]])
        ):
            lines += render_operation(spec, method, path, op)
    return "\n".join(lines).rstrip() + "\n"


def render_all() -> dict[str, str]:
    spec = Spec(yaml.safe_load(SPEC_PATH.read_text(encoding="utf-8")))
    return {"endpoints.md": render_endpoints(spec), "data-models.md": render_models(spec)}


if __name__ == "__main__":
    OUT_DIR.mkdir(exist_ok=True)
    for name, text in render_all().items():
        (OUT_DIR / name).write_text(text, encoding="utf-8", newline="\n")
        print(f"written {OUT_DIR / name}")
