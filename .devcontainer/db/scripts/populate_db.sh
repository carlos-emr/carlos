#!/usr/bin/env sh
set -e
echo 'Setting up all databases...'

MIG=/database/mysql/migration
# Use the MariaDB image's native root-password variable, with MYSQL_ROOT_PASSWORD retained
# as a compatibility fallback for older local.env files. The password travels via MYSQL_PWD
# (off-argv: -p<pw> would be visible in the process list).
export MYSQL_PWD="${MARIADB_ROOT_PASSWORD:-${MYSQL_ROOT_PASSWORD:-password}}"
# MariaDB 11.x dropped the mysql* client symlinks (mysql/mysqladmin/mysqldump); use mariadb.
SQL="mariadb -u root"

# Build oscar + oscar_test from the Flyway migration set — the SAME files production applies via
# `carlos-ctl db migrate` (common + Ontario locations): a complete, dead-pruned schema + reference
# data. Loaded here with the mariadb CLI (not the Flyway CLI) because the MariaDB initdb temp server
# is socket-only and Flyway needs TCP; dev databases are disposable, so a flyway_schema_history is
# not required.
# Forward migrations (V1.0.N, N>=3) are DISCOVERED from the common + on locations and applied in
# version order — mirroring Flyway's scan — so a newly added migration can never be silently
# missed here. V1.0.1/V1.0.2 are the Ontario genesis files, loaded explicitly after V1 below.
# (Filenames contain no whitespace — the repo's migration hook enforces V1.0.N__desc.sql.)
FORWARD=$(for f in "${MIG}/common/"V*.sql "${MIG}/on/"V*.sql; do
    [ -f "$f" ] || continue
    case "$f" in
      */V1__*|*/V1.0.1__*|*/V1.0.2__*) continue ;;
    esac
    printf '%s\n' "$f"
  done \
  | awk -F'/V1\\.0\\.' '{ n=$2; sub(/__.*/,"",n); print n "\t" $0 }' \
  | sort -n | cut -f2)
if [ -z "${FORWARD}" ]; then
  echo "No forward migrations (V1.0.3+) discovered under ${MIG}; loading genesis baseline only." >&2
fi
# Flyway rejects duplicate versions across co-applied locations (common + on); fail fast the same
# way instead of silently loading both files. The repo's migration hook blocks duplicates at
# authoring time — this guards files that bypass the hook (plain git add, external tools).
if [ -n "${FORWARD}" ]; then
  DUP_VERSIONS=$(echo "${FORWARD}" | awk -F'/V1\\.0\\.' '{ n=$2; sub(/__.*/,"",n); print n }' | sort -n | uniq -d)
  if [ -n "${DUP_VERSIONS}" ]; then
    echo "ERROR: duplicate forward migration version(s) across common+on: $(printf '%s\n' "${DUP_VERSIONS}" | sed 's/^/V1.0./')" >&2
    exit 1
  fi
fi
# Assemble the load into a temp file first: /bin/sh has no pipefail, so `cat ... | mariadb`
# would mask a missing migration file (mariadb exits 0 on the truncated stream) — a redirect
# from a fully-assembled file makes any cat failure abort under set -e instead.
LOAD_SQL=$(mktemp)
trap 'rm -f "${LOAD_SQL}"' EXIT
{
  echo "SET FOREIGN_KEY_CHECKS=0;"
  cat "${MIG}/common/V1__baseline_schema.sql" \
      "${MIG}/on/V1.0.1__on_schema.sql" \
      "${MIG}/on/V1.0.2__on_data.sql"
  # The genesis files issue a bare SET NAMES utf8mb4, whose default collation is
  # uca1400 on current MariaDB images. Re-pin the connection before the forward
  # chain so the checksum-frozen V1.0.7 migration can compare against its
  # utf8mb4_general_ci table and the later repair migration remains reachable.
  echo "SET NAMES utf8mb4 COLLATE utf8mb4_general_ci;"
  if [ -n "${FORWARD}" ]; then
    for f in ${FORWARD}; do
      echo "-- including $(basename "$f")" >&2
      cat "$f"
    done
  fi
  echo "SET FOREIGN_KEY_CHECKS=1;"
} > "${LOAD_SQL}"
for DB in oscar oscar_test; do
  echo "Creating ${DB} from the Flyway baseline (common + on)..."
  # Explicit charset so the DATABASE default matches the fully-utf8mb4 schema even if the
  # server default ever changes (all baseline tables also set it per-table).
  $SQL -e "CREATE DATABASE IF NOT EXISTS ${DB} CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
  $SQL "${DB}" < "${LOAD_SQL}"
done

# drugref2 is a separate database (not part of the oscar schema).
echo 'Creating drugref2 database...'
$SQL -e "CREATE DATABASE IF NOT EXISTS drugref2;"
$SQL drugref2 < /database/mysql/development-drugref.sql
echo 'Applying drugref2 schema patches...'
$SQL drugref2 < /database/mysql/drugref/2026-04-19-drugref-tc-atc-f.sql

# --- Development-only demo data (oscar only; never applied to a production database) ---
# development.sql is a full demo snapshot (truncate+reload) filtered to the live schema. It replaces
# the baseline reference rows with the demo dataset (patients, appointments, notes, etc.).
echo 'Loading demo data for development...'
$SQL oscar < /scripts/development.sql
echo 'Removing HRM rows whose source fixtures are not distributed...'
$SQL oscar < /scripts/development_hrm_cleanup.sql
echo 'Restoring current Administration privileges...'
$SQL oscar < /scripts/development_privileges.sql
echo 'Preparing demographic names for development environment...'
$SQL oscar < /database/mysql/updates/update-2025-11-06-demo-name-sanitization.sql
echo 'Seeding Rich Text Letter eForm...'
$SQL oscar < /database/mysql/updates/update-2012-07-12.sql
echo 'Modernizing Rich Text Letter eForm to 2026.3.0...'
$SQL oscar < /database/mysql/updates/update-2026-03-22-rtl-2026.3.0-modernize.sql
$SQL oscar < /database/mysql/updates/update-2026-03-12-rtl-enable-direct.sql
echo 'Database initialization complete!'
