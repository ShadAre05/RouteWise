#!/usr/bin/env bash
# Reference seeds (always) and test data (only when called with --test-data). Idempotent.
. "$(dirname "$0")/_env.sh"
export PGPASSWORD="${RW_OWNER_PASSWORD:-}"
for f in $(ls "$ROOT"/db/seed/S*.sql | sort); do echo "seed   $(basename "$f")"; psql -v ON_ERROR_STOP=1 -X -q -U rw_owner -d "$PGDATABASE" --single-transaction -f "$f"; done
if [ "${1:-}" = "--test-data" ]; then
  for f in $(ls "$ROOT"/db/testdata/T*.sql | sort); do echo "test   $(basename "$f")"; psql -v ON_ERROR_STOP=1 -X -q -U rw_owner -d "$PGDATABASE" --single-transaction -f "$f"; done
fi
