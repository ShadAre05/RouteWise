#!/usr/bin/env bash
# DEVELOPMENT ONLY: drops the rw schema and recreates it empty. Refuses to run when RW_ENV=production.
. "$(dirname "$0")/_env.sh"
[ "${RW_ENV:-dev}" != "production" ] || { echo "refusing to reset production" >&2; exit 1; }
PGPASSWORD="${PG_SUPERUSER_PASSWORD:-}" psql -v ON_ERROR_STOP=1 -U "${PG_SUPERUSER:-postgres}" -d "$PGDATABASE" \
  -c "DROP SCHEMA IF EXISTS rw CASCADE" -c "CREATE SCHEMA rw AUTHORIZATION rw_owner" \
  -c "GRANT USAGE ON SCHEMA rw TO app_read, app_write, rw_admin"
