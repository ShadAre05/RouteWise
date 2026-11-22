# RouteWise — database (folder `database/`)

PostgreSQL 16 + PostGIS 3 database of **RouteWise**, an Android app that estimates the cost of a car trip in 8 European countries (fuel, tolls/vignettes, lodging). Track: **OLTP**.

This folder contains everything needed to build the database from nothing: migrations, reference seeds, test data, automated tests and documentation. **No keys or passwords are stored here**; credentials come from environment variables (`.env` is ignored by Git, see `.env.example`).

## Repository layout
```
db/bootstrap.sql        roles, database, extensions, schema (run once by a superuser)
db/migrations/V*.sql    numbered DDL migrations (the only way the structure changes)
db/seed/S*.sql          reference data (countries with borders, demo rates) - idempotent
db/testdata/T*.sql      test data with edge cases (users, vehicles, prices, trips)
db/tests/*.sql          automated tests (97 checks: constraints, triggers, roles, RLS, masking)
scripts/                bootstrap.sh, migrate.sh, seed.sh, test.sh, setup_all.sh, reset_dev.sh
docs/                   data dictionary, logical schema, normalization, integrity, security, indexes, versioning
docker-compose.yml      PostgreSQL 16 + PostGIS for local work
```

## Quick start
```bash
cd database
cp .env.example .env            # then put your own passwords into .env
docker compose up -d            # PostgreSQL 16 + PostGIS on localhost:5432
scripts/setup_all.sh            # bootstrap -> migrations -> seeds + test data -> tests
```
Step by step: `scripts/bootstrap.sh`, `scripts/migrate.sh`, `scripts/seed.sh [--test-data]`, `scripts/test.sh`.
Requirements: `psql` client 16, Bash. Production: run `seed.sh` **without** `--test-data` and replace the demo rows from `db/seed/S02_reference_demo_rates.sql` with verified values.

## Documentation
| Document | Content |
|---|---|
| [docs/data_dictionary.md](docs/data_dictionary.md) | layers, tables, columns, types, keys, constraints, data-quality expectations, views, functions |
| [docs/logical_schema.png](docs/logical_schema.png) (`.svg`, `.dot`) | logical schema, colour = layer |
| [docs/normalization.md](docs/normalization.md) | 3NF analysis and the documented denormalization of history |
| [docs/integrity_and_transactions.md](docs/integrity_and_transactions.md) | keys, constraints, triggers, transactions |
| [docs/security.md](docs/security.md) | roles, RLS, password hashing, masking of personal data |
| [docs/indexes.md](docs/indexes.md) | each index and the query it serves, `EXPLAIN` evidence |
| [docs/versioning.md](docs/versioning.md) | migrations + Git workflow |
| [docs/test_report.txt](docs/test_report.txt) | output of the last test run |

## Requirements → where they are met
| Requirement | Where |
|---|---|
| Repository with README, no secrets | this file, `.gitignore`, `.env.example` |
| Data dictionary, logical schema, DDL/migrations in Git | `docs/data_dictionary.md`, `docs/logical_schema.png`, `db/migrations` |
| 3NF, keys and constraints | `docs/normalization.md`, `V001–V003` (PK/FK/NOT NULL/UNIQUE/CHECK) |
| Meaningful types | `numeric` money, `timestamptz`, `geometry`, ENUM |
| Roles; application is not superuser; permissions for new objects | `db/bootstrap.sql`, `V006`, `docs/security.md`, default privileges |
| Passwords only hashed (no MD5/SHA-1) | bcrypt via pgcrypto, CHECK on hash format, no read access to the hash |
| Test data with edge cases; reference seeds | `db/testdata`, `db/seed` |
| Indexes, views / materialized view | `docs/indexes.md`, `V005` |
| Triggers / procedures / functions | `V004` (login lock, default vehicle, immutable history, total check, import procedures) |
| Masking of personal data | `mask_email`, `v_users_masked`, `v_trips_anonymized`, `delete_account` |
| Structure versioning | `docs/versioning.md`, `rw.schema_migrations` with checksums |

## Notes on data
- Country borders: Natural Earth 1:110m (public domain), simplified.
- Fuel prices in the test data are **synthetic**; real prices are meant to come from the EC Weekly Oil Bulletin through the import procedures `import_fuel_price` and `finish_fuel_import`; the downloader that calls them is not implemented yet.
- Tariffs, lodging and speed values in `S02` are **demo values marked "not verified"**; the administrator verifies them before production.
