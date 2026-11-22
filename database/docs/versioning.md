# Versioning of the database structure (migrations + Git)

The structure is changed **only** through numbered SQL files in `db/migrations/`:

```
V001__accounts_and_vehicles.sql    types, users, tokens, vehicles
V002__reference_data.sql           countries, prices, tariffs, lodging, speed
V003__trips.sql                    trips, cost lines, refuel stops
V004__functions_and_triggers.sql   business logic
V005__views_and_indexes.sql        views, materialized view, masking views
V006__security_rls_and_grants.sql  RLS policies, GRANTs, default privileges
V007__refresh_token_functions.sql  refresh-token lookup for the back-end (SECURITY DEFINER)
```

## Rules
1. **One change = one new file** `V<next number>__<description>.sql`, committed to Git together with the application change that needs it. Pull request review = review of the SQL.
2. **Applied files are never edited.** `scripts/migrate.sh` stores a SHA-256 checksum of each applied file in `rw.schema_migrations` and stops with an error if an applied file differs. A mistake is fixed by a *new* migration (forward-only).
3. Each file is applied in **one transaction** (`psql --single-transaction`) together with its row in `schema_migrations`: a file is either fully applied or not at all.
4. Order is the numeric order of the prefix; the database knows its version: `SELECT * FROM rw.schema_migrations`.
5. Reference data is not a migration: `db/seed/S*.sql` are idempotent (`ON CONFLICT`) and can be re-run. Test data (`db/testdata/T*.sql`) is loaded only on development databases.
6. Rollback: forward-only. To undo V007, write V008 that reverses it; for a failed deployment the single-transaction rule leaves the database at the previous version. A development database can be rebuilt from zero by `scripts/reset_dev.sh` + `scripts/migrate.sh` (refuses to run when `RW_ENV=production`).

## Example: adding a column
```sql
-- V007__trips_add_comment.sql
ALTER TABLE rw.trips ADD COLUMN comment varchar(200);
GRANT SELECT (comment) ON rw.trips TO app_write;   -- the grants of new objects belong in the same file
```
Git history of `db/migrations/` is the full history of the database structure.

## Multiple DBMS
One DBMS (PostgreSQL) is used on purpose: PostGIS (borders, km per country), RLS, ENUM, pgcrypto and materialized views are all needed, and a second DBMS would only add synchronisation problems. Routing data lives in OSRM's own files (not in our database).
