#!/usr/bin/env bash
# One-time setup as superuser: roles, database, extensions, schema. Passwords come from the environment.
. "$(dirname "$0")/_env.sh"
PGPASSWORD="${PG_SUPERUSER_PASSWORD:-}" psql -v ON_ERROR_STOP=1 -U "${PG_SUPERUSER:-postgres}" -d postgres \
  -v RW_DB="$PGDATABASE" -v RW_OWNER_PASSWORD="$RW_OWNER_PASSWORD" -v RW_APP_PASSWORD="$RW_APP_PASSWORD" \
  -v RW_REPORTING_PASSWORD="$RW_REPORTING_PASSWORD" -v RW_ADMIN_PASSWORD="$RW_ADMIN_PASSWORD" \
  -f "$ROOT/db/bootstrap.sql"
