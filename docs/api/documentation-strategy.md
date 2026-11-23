# Documentation strategy

## Goal and audience
Give a developer of a client (the Android application, a tester, a reviewer) everything needed to use the API
without reading its source: what each endpoint does, what to send, what comes back, which errors can occur, how
to authenticate, and how the pieces fit together.

## Approach: the code is the source of truth
The specification is **generated from the application code** (FastAPI and its Pydantic models), not written by
hand. Descriptions, constraints and examples live next to the code they describe, so the specification cannot
silently disagree with the implementation. The generated file is committed to the repository as
`docs/api/openapi.yaml`, and a unit test fails when the file differs from what the code would generate.

## Tools and standards
| Purpose | Tool / standard |
|---|---|
| Specification format | OpenAPI 3.1, stored as YAML |
| Generation | FastAPI + Pydantic v2, exporter `backend/scripts/export_openapi.py` |
| Validation of the schema | `openapi-spec-validator` (unit test and local command) |
| Style and completeness lint | Spectral with the `spectral:oas` ruleset (CI step, fails on warnings) |
| Reference pages | Markdown generated from the specification by `backend/scripts/render_reference.py` |
| Interactive documentation | Swagger UI (`/docs`), Redoc (`/redoc`) |
| Contract testing | the project's HTTP tests validate every answer against the specification (`backend/tests/contract.py`) |
| Mock server | Prism (`@stoplight/prism-cli`) serving the examples of the specification |
| Guides | Markdown in `docs/api/guides`, diagrams as Mermaid (rendered by GitHub) |
| Runnable examples | `examples/walkthrough.py` (Python, `httpx`) |
| Continuous check | GitHub Actions: lint, tests (including the up-to-date test), vulnerability scan |

## Conventions
- **Naming:** paths lower-case kebab-case plural nouns; fields `snake_case` with the unit in the name; enumerations
  lower-case; error codes lower-case snake_case. See [conventions](guides/conventions.md).
- **Every operation** has a summary, a description, documented request and response models, and a description for
  every status code; error responses use the single `ErrorResponse` model with an example.
- **Examples** are recorded from the running API, not invented; where a stand-in or demo data was used, the text
  says so.
- **Formatting:** Markdown with sentence-case headings, one H1 per file, tables for reference material, fenced code
  blocks with a language, relative links between files, English only.
- **Folder structure:** `reference/` for the generated endpoint and model reference, `guides/` for explanations,
  `examples/` for runnable code, the specification and the changelog at the top level.

## Versioning of the documentation
- The API version is the major number in the path (`/api/v1`); the specification carries a semantic version in
  `info.version`.
- Compatible changes are made inside `v1`; breaking changes need `/api/v2` (rules in
  [conventions](guides/conventions.md#versioning)).
- Every change of the API is described in the [changelog](CHANGELOG.md) in the same commit that changes the
  code, and the specification file is regenerated in that commit.

## How accuracy is checked
- The examples in the guides were produced by running `examples/walkthrough.py` and by hand-made calls against the
  running API with its real PostgreSQL/PostGIS database; claims about behaviour (lockout, one-time refresh tokens,
  the 404 for foreign resources, page limits, the error for a missing vehicle) were tested the same way.
- The specification is validated automatically on every push (OpenAPI validator, Spectral, a test that the file is
  current, a test that every example is valid against its schema, and a test that every operation has a
  summary, description and examples).
- Contract check: all HTTP tests, unit and integration, run through a client that validates each answer against the
  documented schema and fails on undocumented status codes.

## Known gaps and limitations
- The examples of trip calculation were recorded with a stand-in routing engine returning a fixed 460 km route and
  with the demo price data; real routes will give other distances and prices.
- There is no rate limiting and no idempotency-key mechanism yet (documented in
  [conventions](guides/conventions.md)).
- The guides are not published as a separate website; they are Markdown in the repository next to the
  Swagger UI and Redoc pages of the running server. The public address of a deployed instance is not part of this
  repository yet.
- The mock server is the Prism tool run locally; there is no hosted sandbox, and no consumer-driven contract test
  (for example Pact) because the Android application, the only consumer, is not written yet.
- The reference pages list the first rows of long reference lists (for example three of eight countries) in
  the examples.
- The documentation is in English only.
