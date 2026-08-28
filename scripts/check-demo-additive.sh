#!/usr/bin/env bash
#
# check-demo-additive.sh — validation gate for the additive demo artifact.
#
# Run by debian/rules after build-demo-additive.sh; a violation fails the
# package build. Enforces the demo-data contract:
#   * additive only: the artifact may contain nothing but INSERT IGNORE
#     statements and SET framing — no TRUNCATE/UPDATE/DELETE/REPLACE and no
#     DDL, so it structurally cannot modify, remove, or create anything
#     Flyway owns;
#   * every insert target exists in the target province's Flyway schema and
#     is not on the exclusion list;
#   * the known real-person names are gone;
#   * (with the source snapshot given) every table development.sql inserts
#     into is either transformable or consciously excluded — a snapshot
#     refresh that adds an unclassified table fails here.
#
# Usage: scripts/check-demo-additive.sh <artifact.sql> <on|bc> [<development.sql>]

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
MIGRATION_DIR="${REPO_ROOT}/database/mysql/migration"
EXCLUDE_FILE="${SCRIPT_DIR}/demo-additive-exclude.txt"

ARTIFACT="${1:?usage: check-demo-additive.sh <artifact.sql> <on|bc> [<development.sql>]}"
PROVINCE="${2:?usage: check-demo-additive.sh <artifact.sql> <on|bc> [<development.sql>]}"
SOURCE="${3:-}"

case "${PROVINCE}" in
  on|bc) ;;
  *) echo "ERROR: province must be 'on' or 'bc', got '${PROVINCE}'" >&2; exit 1 ;;
esac

fail=0
err() { echo "check-demo-additive: FAIL: $*" >&2; fail=1; }

# --- statement-type allowlist -------------------------------------------------
# Statement starters are only inspected at the start of a line: inside a
# multi-line INSERT every line is either a VALUES row ('(...),') or the
# terminating ');', so a leading-keyword scan cannot false-positive on data.
if grep -qE '^[[:space:]]*TRUNCATE[[:space:]]' "$ARTIFACT"; then
  err "artifact contains TRUNCATE (must be additive only)"
fi
if grep -qE '^[[:space:]]*(UPDATE|DELETE|REPLACE)[[:space:]]' "$ARTIFACT"; then
  err "artifact contains UPDATE/DELETE/REPLACE (Flyway data must win)"
fi
# DDL is forbidden — the demo load must never add persistent schema beyond
# what Flyway baselines. Narrow exception: the dev generator blocks use
# session-scoped scaffolding that leaves no schema behind — TEMPORARY tables
# named _tmp_* (vanish with the session) and procedures named dev_* that the
# artifact itself drops after CALLing. Everything else fails.
#
# COUNT the violations rather than `grep pattern | grep -qv allowlist`: under
# pipefail, -q exits on the first hit and can SIGPIPE the producer, turning
# the whole pipeline non-zero and silently SKIPPING the err branch (live-
# reproduced: an artifact whose first INSERT was plain passed the old check).
# grep -c always reads all input, so the pipeline status is deterministic;
# `|| true` covers the count-of-zero exit-1 case.
bad_ddl=$(grep -E '^[[:space:]]*(CREATE|ALTER|DROP)[[:space:]]' "$ARTIFACT" \
  | grep -cvE '^[[:space:]]*((CREATE|DROP)[[:space:]]+TEMPORARY[[:space:]]+TABLE[[:space:]]+(IF[[:space:]]+(NOT[[:space:]]+)?EXISTS[[:space:]]+)?`?_tmp_|(CREATE|DROP)[[:space:]]+PROCEDURE[[:space:]]+(IF[[:space:]]+EXISTS[[:space:]]+)?`?dev_)' || true)
if [ "${bad_ddl}" -gt 0 ]; then
  err "artifact contains ${bad_ddl} DDL statement(s) outside the _tmp_*/dev_* scaffolding (must not add schema Flyway does not baseline)"
fi
# Every dev_* procedure must be dropped after use — a leftover procedure
# would be persistent schema. Aggregate-count heuristic: fail only when there
# are fewer drops than creates (the DDL allowlist above admits DROP PROCEDURE
# with or without IF EXISTS, so count both forms; equal counts mean every
# created procedure has a matching drop).
creates=$(grep -cE '^[[:space:]]*CREATE[[:space:]]+PROCEDURE[[:space:]]+`?dev_' "$ARTIFACT" || true)
drops=$(grep -cE '^[[:space:]]*DROP[[:space:]]+PROCEDURE[[:space:]]+(IF[[:space:]]+EXISTS[[:space:]]+)?`?dev_' "$ARTIFACT" || true)
if [ "$creates" -gt 0 ] && [ "$drops" -lt "$creates" ]; then
  err "a dev_* procedure is created but not dropped afterwards"
fi
plain_inserts=$(grep -E '^[[:space:]]*INSERT[[:space:]]' "$ARTIFACT" \
  | grep -cvE '^[[:space:]]*INSERT[[:space:]]+IGNORE[[:space:]]+INTO[[:space:]]' || true)
if [ "${plain_inserts}" -gt 0 ]; then
  err "artifact contains ${plain_inserts} plain INSERT statement(s) (must be INSERT IGNORE so Flyway rows win)"
fi

# --- real-name blocklist ------------------------------------------------------
if grep -qE 'Pomedli|AZIZI|Jacky Jones' "$ARTIFACT"; then
  err "artifact still contains a blocklisted real name (Pomedli/AZIZI/Jacky Jones)"
fi

# --- target tables ------------------------------------------------------------
LIVE_TABLES="$(mktemp)"
EXCLUDES="$(mktemp)"
trap 'rm -f "$LIVE_TABLES" "$EXCLUDES"' EXIT

for schema in "${MIGRATION_DIR}/common/"V*.sql \
              "${MIGRATION_DIR}/${PROVINCE}/"V*.sql; do
  [ -f "$schema" ] || continue
  sed -nE 's/^[[:space:]]*CREATE[[:space:]]+TABLE([[:space:]]+IF[[:space:]]+NOT[[:space:]]+EXISTS)?[[:space:]]+`?([A-Za-z0-9_]+)`?[[:space:]].*/\2/p' "$schema"
done | LC_ALL=C sort -u > "$LIVE_TABLES"

grep -vE '^[[:space:]]*(#|$)' "$EXCLUDE_FILE" | LC_ALL=C sort -u > "$EXCLUDES"

while IFS= read -r table; do
  case "$table" in
    _tmp_*) continue ;;  # session-scoped generator scaffolding, not schema
  esac
  if ! grep -qx "$table" "$LIVE_TABLES"; then
    err "artifact inserts into '$table' which does not exist in the ${PROVINCE} Flyway schema"
  fi
  if grep -qx "$table" "$EXCLUDES"; then
    err "artifact inserts into excluded table '$table'"
  fi
done < <(grep -oP '^[[:space:]]*INSERT[[:space:]]+IGNORE[[:space:]]+INTO[[:space:]]+`\K[A-Za-z0-9_]+' "$ARTIFACT" | LC_ALL=C sort -u)

# --- classification completeness ---------------------------------------------
# Every table the snapshot inserts into must exist in SOME province schema or
# be excluded; anything else means the snapshot gained a table nobody
# classified (or a table left the schema and the exclusion list is stale).
if [ -n "$SOURCE" ]; then
  UNION_TABLES="$(mktemp)"
  trap 'rm -f "$LIVE_TABLES" "$EXCLUDES" "$UNION_TABLES"' EXIT
  for schema in "${MIGRATION_DIR}/common/"V*.sql \
                "${MIGRATION_DIR}/on/"V*.sql \
                "${MIGRATION_DIR}/bc/"V*.sql; do
    [ -f "$schema" ] || continue
    sed -nE 's/^[[:space:]]*CREATE[[:space:]]+TABLE([[:space:]]+IF[[:space:]]+NOT[[:space:]]+EXISTS)?[[:space:]]+`?([A-Za-z0-9_]+)`?[[:space:]].*/\2/p' "$schema"
  done | LC_ALL=C sort -u > "$UNION_TABLES"

  while IFS= read -r table; do
    case "$table" in
      _tmp_*) continue ;;  # session-scoped generator scaffolding, not schema
    esac
    if ! grep -qx "$table" "$UNION_TABLES" && ! grep -qx "$table" "$EXCLUDES"; then
      err "development.sql inserts into unclassified table '$table' (not in any province schema, not excluded) — classify it in scripts/demo-additive-exclude.txt or refresh the snapshot"
    fi
  done < <(grep -oP '^[[:space:]]*INSERT[[:space:]]+INTO[[:space:]]+`\K[A-Za-z0-9_]+' "$SOURCE" | LC_ALL=C sort -u)
fi

if [ "$fail" -ne 0 ]; then
  exit 1
fi
echo "check-demo-additive: OK (${ARTIFACT}, province=${PROVINCE})"
