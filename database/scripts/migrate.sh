#!/usr/bin/env bash
# Applies db/migrations/V*.sql in version order as rw_owner. Each file runs in ONE transaction.
# Applied files are recorded in rw.schema_migrations with a SHA-256 checksum; changing an applied file is an error.
. "$(dirname "$0")/_env.sh"
export PGPASSWORD="${RW_OWNER_PASSWORD:-}"
P=(psql -v ON_ERROR_STOP=1 -X -q -U rw_owner -d "$PGDATABASE")
"${P[@]}" -c "CREATE TABLE IF NOT EXISTS rw.schema_migrations (
  version text PRIMARY KEY, filename text NOT NULL, checksum text NOT NULL, applied_at timestamptz NOT NULL DEFAULT now())"
for f in $(ls "$ROOT"/db/migrations/V*.sql | sort); do
  name="$(basename "$f")"; ver="${name%%__*}"; sum="$(sha256sum "$f" | cut -d' ' -f1)"
  old="$("${P[@]}" -At -c "SELECT checksum FROM rw.schema_migrations WHERE version='$ver'")"
  if [ -n "$old" ]; then
    [ "$old" = "$sum" ] || { echo "ERROR: $name was changed after it was applied" >&2; exit 1; }
    echo "skip   $name"; continue
  fi
  echo "apply  $name"
  "${P[@]}" --single-transaction -f "$f" \
    -c "INSERT INTO rw.schema_migrations(version, filename, checksum) VALUES ('$ver','$name','$sum')"
done
