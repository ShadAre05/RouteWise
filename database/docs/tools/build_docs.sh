#!/usr/bin/env bash
# Regenerates diagram + data dictionary from the live database. Needs psql and graphviz (dot).
set -euo pipefail
D="$(cd "$(dirname "$0")/.." && pwd)"
python3 "$D/tools/gen_docs.py"
cat "$D/tools/parts/dict_head.md" "$D/data_dictionary_tables.md" "$D/tools/parts/dict_tail.md" > "$D/data_dictionary.md"
sed -i "s/^# Data dictionary$/## Tables/" "$D/data_dictionary.md"
rm "$D/data_dictionary_tables.md"
