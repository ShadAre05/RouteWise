#!/usr/bin/env bash
# Runs db/tests/*.sql. SET ROLE needs a superuser session, so the tests use the bootstrap superuser;
# the last step then logs in as the real application role to prove that it is not a superuser.
. "$(dirname "$0")/_env.sh"
for f in "$ROOT"/db/tests/*.sql; do
  echo "== $(basename "$f")"
  PGPASSWORD="${PG_SUPERUSER_PASSWORD:-}" psql -v ON_ERROR_STOP=1 -X -q -U "${PG_SUPERUSER:-postgres}" -d "$PGDATABASE" -f "$f" 2>&1 | sed -e 's/^psql:[^ ]* //' -e 's/^NOTICE:  //' -e '/^[[:space:]]*$/d'
  [ "${PIPESTATUS[0]}" -eq 0 ] || { echo "TESTS FAILED" >&2; exit 1; }
done
echo "== login as the application role"
PGPASSWORD="$RW_APP_PASSWORD" psql -X -At -U rw_app -d "$PGDATABASE" -c "SELECT 'rw_app superuser: ' || rolsuper FROM pg_roles WHERE rolname = current_user"
[ "$(PGPASSWORD="$RW_APP_PASSWORD" psql -X -At -U rw_app -d "$PGDATABASE" -c "SELECT rolsuper FROM pg_roles WHERE rolname = current_user")" = "f" ] || { echo "rw_app must not be superuser" >&2; exit 1; }
echo "ALL TESTS PASSED"