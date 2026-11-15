#!/usr/bin/env bash
# Loads .env (if present). Never commit .env.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [ -f "$ROOT/.env" ]; then set -a; . "$ROOT/.env"; set +a; fi
: "${PGHOST:=localhost}" "${PGPORT:=5432}" "${PGDATABASE:=routewise}"
export PGHOST PGPORT PGDATABASE
