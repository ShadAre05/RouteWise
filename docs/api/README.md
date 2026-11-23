# RouteWise API documentation

Reference and guides for the RouteWise REST API (version 1.0.0, base path `/api/v1`).

## Where to start
| I want to... | Read |
|---|---|
| make my first call in five minutes | [Getting started](guides/getting-started.md) |
| follow the whole workflow step by step | [Integration tutorial](guides/tutorial.md) (also as a script: [`examples/walkthrough.py`](examples/walkthrough.py)) |
| sign users in and renew tokens | [Authentication and security](guides/authentication.md) |
| handle failures | [Errors](guides/errors.md) |
| understand formats, paging, idempotency, versioning | [Conventions and advanced topics](guides/conventions.md) |
| see how the system is built | [Architecture overview](guides/architecture.md) |
| look up every endpoint, parameter, example and model | [Endpoint reference](reference/endpoints.md) and [Data models](reference/data-models.md); the machine-readable [`openapi.yaml`](openapi.yaml); Swagger UI at `/docs` and Redoc at `/redoc` of a running server |
| try the API without a database or a routing engine | [Mock server](#mock-server-and-sandbox) |
| see what changed | [Changelog](CHANGELOG.md) |
| learn how this documentation is produced | [Documentation strategy](documentation-strategy.md) |

## Folder structure
```
docs/api/
  README.md                  this page
  openapi.yaml               the specification (OpenAPI 3.1), generated from the code and version-controlled
  CHANGELOG.md               release notes
  documentation-strategy.md  tools, standards, conventions, known gaps
  reference/                 endpoint reference and data models, generated from openapi.yaml
  guides/                    getting started, tutorial, authentication, errors, conventions, architecture
  examples/                  runnable walkthrough script
../../.spectral.yaml         lint ruleset for the specification
```

## Published form
The documentation exists in three developer-accessible forms:
1. **Swagger UI** (`/docs`) and **Redoc** (`/redoc`) served by the running API, always generated from the live code;
2. **the specification file** `openapi.yaml` in this repository (import it into Postman, Insomnia or a code
   generator);
3. **this Markdown set**, readable on GitHub.

## Mock server and sandbox
- **Mock server.** The examples in the specification drive a mock server that needs no database and no routing
  engine (Node.js required):
  ```bash
  npx @stoplight/prism-cli@5.14.2 mock docs/api/openapi.yaml -p 4010
  curl http://localhost:4010/api/v1/countries
  curl -H "Prefer: code=401" http://localhost:4010/api/v1/vehicles      # ask for a documented error
  ```
- **Sandbox.** The complete local stack (`docker compose up -d --build`, see the root README) is the sandbox:
  it has its own database with demo data, and [`examples/walkthrough.py`](examples/walkthrough.py) creates and removes a
  throw-away account.

## Check the specification yourself
```bash
# regenerate the file from the code (run in the backend folder)
python scripts/export_openapi.py
# validate it
python -m openapi_spec_validator ../docs/api/openapi.yaml
npx @stoplight/spectral-cli@6.15.0 lint docs/api/openapi.yaml
```
The tests and the CI pipeline fail when the committed files are out of date or invalid, when an operation has no
summary, description, example or response description, or when an example does not match its schema. Both reference pages are regenerated with
`python scripts/render_reference.py`.

## Contract checks
The API is tested against its own specification: every HTTP test of the project runs through a client that
validates each JSON answer against the documented schema and fails on an undocumented status code
(`backend/tests/contract.py`). This runs against the in-memory services (unit tests) and against the real database
(integration tests), so a mismatch between the documentation and the behaviour breaks the build.
