#!/usr/bin/env bash
#
# build-demo.sh — filter a full demo snapshot down to the live (pruned) baseline schema.
#
# development.sql is a full demo snapshot (SET FK=0; then TRUNCATE + extended INSERT per table). The
# Flyway baseline prunes dead tables (migration/pruned-tables.txt), so the raw snapshot would fail on
# TRUNCATE/INSERT of tables that no longer exist. This tool removes every statement whose table is
# not in the baseline schema (statement-aware — it handles multi-line INSERTs), producing a demo file
# that loads cleanly on the pruned schema. Keep the non-table lines (SET, headers) intact.
#
# Usage: database/mysql/build-demo.sh <input-full-demo.sql> <output-filtered.sql>
#   e.g. build-demo.sh /path/to/full-development.sql .devcontainer/db/scripts/development.sql

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
IN="${1:?usage: build-demo.sh <input-full-demo.sql> <output-filtered.sql>}"
OUT="${2:?usage: build-demo.sh <input-full-demo.sql> <output-filtered.sql>}"

# The `> "$OUT"` redirect truncates before awk reads: identical paths would destroy the input.
if [ "$(readlink -f "$IN")" = "$(readlink -f "$OUT")" ]; then
  echo "ERROR: input and output must be different files" >&2
  exit 1
fi

# Per-run temp file (predictable /tmp names collide across concurrent runs and are symlink-attackable).
LIVE_TABLES="$(mktemp)"
trap 'rm -f "$LIVE_TABLES"' EXIT

# Live tables = the CREATE TABLE names across the baseline schema files (common + both provinces).
# Deliberately the ON∪BC union even though the devcontainer loads the result into an Ontario-only
# schema: the demo snapshot is Ontario-sourced, so BC-only names never match in practice, and the
# broader keep-set means a future BC devcontainer needs no filter change.
grep -hoE 'CREATE TABLE +`[^`]+`' \
  "${SCRIPT_DIR}/migration/common/V1__baseline_schema.sql" \
  "${SCRIPT_DIR}/migration/on/V1.0.1__on_schema.sql" \
  "${SCRIPT_DIR}/migration/bc/V1.0.1__bc_schema.sql" \
  | sed -E 's/CREATE TABLE +`([^`]+)`/\1/' | LC_ALL=C sort -u > "$LIVE_TABLES"

awk '
function table_target(line, rest) {
  rest=line
  sub(/^[[:space:]]+/, "", rest)
  if (rest ~ /^INSERT[[:space:]]+INTO[[:space:]]+/) {
    sub(/^INSERT[[:space:]]+INTO[[:space:]]+/, "", rest)
  } else if (rest ~ /^TRUNCATE[[:space:]]+TABLE[[:space:]]+/) {
    sub(/^TRUNCATE[[:space:]]+TABLE[[:space:]]+/, "", rest)
  } else {
    return ""
  }
  if (rest ~ /^`/) {
    sub(/^`/, "", rest)
    sub(/`.*/, "", rest)
  } else {
    sub(/[[:space:](].*/, "", rest)
  }
  return "`" rest "`"
}
NR==FNR{keep["`"$1"`"]=1; next}
{
  if (in_stmt) { if (!skip) print; if ($0 ~ /;[[:space:]]*$/) in_stmt=0; next }
  if ($0 ~ /^[[:space:]]*INSERT[[:space:]]+INTO[[:space:]]+/) {
    t=table_target($0)
    skip=((t in keep)?0:1); in_stmt=1; if ($0 ~ /;[[:space:]]*$/) in_stmt=0;
    if (!skip) print; next
  }
  if ($0 ~ /^[[:space:]]*TRUNCATE[[:space:]]+TABLE[[:space:]]+/) { t=table_target($0); if (t in keep) print; next }
  print
}' "$LIVE_TABLES" "$IN" > "$OUT"

echo ">> wrote ${OUT} (filtered to $(wc -l < "$LIVE_TABLES") live tables)"
