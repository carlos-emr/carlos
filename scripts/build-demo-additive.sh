#!/usr/bin/env bash
#
# build-demo-additive.sh — convert the dev demo snapshot into an ADDITIVE,
# per-province demo artifact for the deb installer (carlos-ctl demo-data).
#
# The devcontainer loads development.sql as a truncate-and-reload snapshot.
# The deb demo load must instead ADD to a freshly Flyway-migrated database
# without touching anything Flyway seeded, so this tool:
#   1. drops every TRUNCATE statement,
#   2. rewrites INSERT INTO -> INSERT IGNORE INTO (on any key collision the
#      Flyway row wins and the dev row is discarded),
#   3. keeps only tables that exist in the TARGET PROVINCE's Flyway schema
#      (common + <province>; never creates or references tables Flyway does
#      not baseline),
#   4. drops the tables listed in scripts/demo-additive-exclude.txt (Flyway
#      -owned catalogs, security data, ops noise),
#   5. replaces the known real-person names so the shipped artifact is clean
#      on disk (the post-load demo-name-sanitization.sql adds the FAKE-
#      prefixes to the remaining synthetic names).
#
# Statement handling mirrors database/mysql/build-demo.sh (statement-aware:
# multi-line INSERTs are kept or dropped wholesale).
#
# Usage: scripts/build-demo-additive.sh <development.sql> <on|bc> <output.sql>
# Validate the result with scripts/check-demo-additive.sh before shipping.

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
MIGRATION_DIR="${REPO_ROOT}/database/mysql/migration"
EXCLUDE_FILE="${SCRIPT_DIR}/demo-additive-exclude.txt"

IN="${1:?usage: build-demo-additive.sh <development.sql> <on|bc> <output.sql>}"
PROVINCE="${2:?usage: build-demo-additive.sh <development.sql> <on|bc> <output.sql>}"
OUT="${3:?usage: build-demo-additive.sh <development.sql> <on|bc> <output.sql>}"

case "${PROVINCE}" in
  on|bc) ;;
  *) echo "ERROR: province must be 'on' or 'bc', got '${PROVINCE}'" >&2; exit 1 ;;
esac

# The `> "$OUT"` redirect truncates before awk reads: identical paths would
# destroy the input.
if [ "$(readlink -f "$IN")" = "$(readlink -f "$OUT")" ]; then
  echo "ERROR: input and output must be different files" >&2
  exit 1
fi

LIVE_TABLES="$(mktemp)"
KEEP_TABLES="$(mktemp)"
trap 'rm -f "$LIVE_TABLES" "$KEEP_TABLES"' EXIT

# Live tables for THIS province only: CREATE TABLE names across common plus
# the selected province's migrations (including forward migrations and the
# IF NOT EXISTS form). Unlike build-demo.sh this is deliberately NOT the
# both-province union — an ON-only table must not appear in the bc artifact.
for schema in "${MIGRATION_DIR}/common/"V*.sql \
              "${MIGRATION_DIR}/${PROVINCE}/"V*.sql; do
  [ -f "$schema" ] || continue
  sed -nE 's/^[[:space:]]*CREATE[[:space:]]+TABLE([[:space:]]+IF[[:space:]]+NOT[[:space:]]+EXISTS)?[[:space:]]+`?([A-Za-z0-9_]+)`?[[:space:]].*/\2/p' "$schema"
done | LC_ALL=C sort -u > "$LIVE_TABLES"

# Keep set = live set minus the exclusion list.
grep -vE '^[[:space:]]*(#|$)' "$EXCLUDE_FILE" | LC_ALL=C sort -u \
  | LC_ALL=C comm -23 "$LIVE_TABLES" - > "$KEEP_TABLES"

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
    # _tmp_* TEMPORARY tables are session-scoped scaffolding for the dev
    # generator blocks (bulk ticklers); they are not schema tables and are
    # always kept so the generators keep working.
    skip=((t in keep || t ~ /^`_tmp_/)?0:1); in_stmt=1; if ($0 ~ /;[[:space:]]*$/) in_stmt=0;
    if (!skip) { sub(/^([[:space:]]*)INSERT[[:space:]]+INTO/, "INSERT IGNORE INTO"); print }
    next
  }
  # TRUNCATE is dropped unconditionally: the demo load must never remove
  # Flyway-seeded rows.
  if ($0 ~ /^[[:space:]]*TRUNCATE[[:space:]]+TABLE[[:space:]]+/) { next }
  print
}' "$KEEP_TABLES" "$IN" \
  | sed \
      -e 's/Stephen Pomedli/FAKE-Provider FAKE-VaxDoc/g' \
      -e 's/Pomedli(Vaccine)/FAKE-VaxDoc/g' \
      -e 's/Pomedli/FAKE-VaxDoc/g' \
      -e 's/P\. AZIZI NAMINI/FAKE-REQUESTING-MD/g' \
      -e 's/AZIZI NAMINI/FAKE-REQUESTING-MD/g' \
      -e 's/Jacky Jones/FAKE-Jacky FAKE-Jones/g' \
  > "$OUT"

echo ">> wrote ${OUT} (province=${PROVINCE}, $(grep -c '^INSERT IGNORE INTO' "$OUT") insert statements, $(wc -l < "$KEEP_TABLES") loadable tables)"
