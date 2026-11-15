#!/usr/bin/env bash
# bootstrap -> migrate -> seed [-> test data] -> tests
set -euo pipefail
D="$(dirname "$0")"
"$D/bootstrap.sh"; "$D/migrate.sh"; "$D/seed.sh" --test-data; "$D/test.sh"
