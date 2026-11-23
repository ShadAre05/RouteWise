"""Builds the OpenAPI document: unified error format, status descriptions and the recorded examples."""

from fastapi import FastAPI
from fastapi.openapi.utils import get_openapi

from app.api.openapi_examples import REQUEST_EXAMPLES, RESPONSE_EXAMPLES, error

JSON = "application/json"

ERROR_EXAMPLES = {
    "401": ("unauthorized", "missing bearer token"),
    "404": ("not_found", "trip not found"),
    "409": ("conflict", "this e-mail is already registered"),
    "422": ("validation_failed", "request validation failed"),
    "502": ("upstream_unavailable", "routing engine is not available"),
}

STATUS_DESCRIPTIONS = {
    "200": "Success",
    "201": "Created",
    "204": "Success, no content",
    "401": "Missing, malformed or expired access token, or wrong login credentials",
    "404": "The resource does not exist or belongs to another user",
    "409": "Conflict with existing data (for example the e-mail is already registered)",
    "422": "Invalid input (`details` lists the fields) or no route between the points",
    "502": "The routing engine is not reachable",
}


def build_openapi(app: FastAPI) -> dict:
    """Document every error in the format the API really returns (the framework default differs)."""
    if app.openapi_schema:
        return app.openapi_schema
    schema = get_openapi(
        title=app.title,
        version=app.version,
        description=app.description,
        routes=app.routes,
        tags=app.openapi_tags,
        servers=app.servers,
        contact=app.contact,
        license_info=app.license_info,
    )
    error_ref = {"$ref": "#/components/schemas/ErrorResponse"}
    for path_item in schema["paths"].values():
        for operation in path_item.values():
            name = operation["operationId"]
            body = operation.get("requestBody")
            if body and name in REQUEST_EXAMPLES:
                body["content"][JSON]["examples"] = REQUEST_EXAMPLES[name]
            for code, response in operation["responses"].items():
                if code == "422":
                    response["content"] = {JSON: {"schema": error_ref}}
                response["description"] = STATUS_DESCRIPTIONS.get(code, response.get("description", ""))
                media = response.get("content", {}).get(JSON)
                if media is None:
                    continue
                if (name, code) in RESPONSE_EXAMPLES:
                    media["examples"] = RESPONSE_EXAMPLES[(name, code)]
                elif code in ERROR_EXAMPLES:
                    media["example"] = error(*ERROR_EXAMPLES[code])
    components = schema.get("components", {}).get("schemas", {})
    if "ErrorResponse" in components:
        components.pop("HTTPValidationError", None)
        components.pop("ValidationError", None)
    app.openapi_schema = schema
    return schema
