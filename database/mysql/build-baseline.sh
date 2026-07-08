#!/usr/bin/env bash
#
# build-baseline.sh — regenerate the consolidated Flyway V1 baseline for CARLOS.
#
# WHY: fresh CARLOS installs should not replay 20 years of dated update-*.sql patches. This tool
# builds a known-good database the legacy way (createdatabase_<prov>.sh + the currently-required
# recent updates), then captures it as a clean, checksummed Flyway baseline:
#
#   common/V1__baseline_core_schema.sql   province-neutral DDL
#   common/V1.1__baseline_core_seed.sql   app-REQUIRED seed (reference/lookup rows the app needs)
#   common/V1.2__reference_icd.sql        large static ICD reference
#   on/V1.0.1__on_schema.sql              Ontario DDL
#   on/V1.1.1__on_seed.sql                Ontario seed (incl. OLIS)
#   bc/V1.0.1__bc_schema.sql              British Columbia DDL
#   bc/V1.1.1__bc_seed.sql                British Columbia seed (billing codes, specialists, pharmacies)
#
# It is a MAINTAINER tool. Run it in the devcontainer (which has MariaDB) whenever the baseline
# needs to be regenerated (e.g. after folding a batch of deltas into a new baseline). Commit the
# regenerated V1* files. It never runs as part of the normal build.
#
# Usage:
#   database/mysql/build-baseline.sh [--host HOST] [--user USER] [--password PW]
#   MYSQL_HOST / MYSQL_USER / MYSQL_ROOT_PASSWORD env vars are honoured as defaults.
#
# The dumps are normalized (no dump date, no AUTO_INCREMENT counters, no wrapping comments) so that
# a byte-for-byte diff against the legacy-built schema is meaningful — see
# docs/database-schema-management.md, "Verification".

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

HOST="${MYSQL_HOST:-db}"
USER="${MYSQL_USER:-root}"
PASSWORD="${MYSQL_ROOT_PASSWORD:-password}"

while [ "$#" -gt 0 ]; do
  case "$1" in
    --host) HOST="$2"; shift 2 ;;
    --user) USER="$2"; shift 2 ;;
    --password) PASSWORD="$2"; shift 2 ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
done

# The recent updates that populate_db.sh replays on top of the init scripts today. Keep this list
# in sync with .devcontainer/db/scripts/populate_db.sh until the cutover retires that block.
REQUIRED_UPDATES="
update-2025-01-29.sql
update-2025-02-27.sql
update-2025-05-27.sql
update-2025-08-14-study-removal.sql
update-2025-12-16-provider-module-singular.sql
update-2026-01-02-add-flowsheet-admin-privilege.sql
update-2026-01-26-tickler-indexes.sql
update-2026-02-10-fax-provider-type.sql
update-2026-02-14-facility-integrator-removal.sql
update-2026-03-25-security-mfa-default.sql
update-2026-04-30-sec-obj-missing-privileges.sql
"

mysql_cmd() { mysql -h "$HOST" -u "$USER" -p"$PASSWORD" "$@"; }

# Normalize a mysqldump stream so diffs are stable across runs and across the legacy vs Flyway path.
normalize() {
  sed -E \
    -e 's/ AUTO_INCREMENT=[0-9]+//g' \
    -e '/^-- Dump completed/d' \
    -e '/^-- Server version/d' \
    -e '/^\/\*![0-9]+ SET /d'
}

build_and_dump() {
  province="$1"   # on | bc
  icd="$2"        # 9 | 10
  tmp_db="carlos_baseline_${province}"

  echo ">> building throwaway ${tmp_db} from createdatabase_${province}.sh + required updates"
  mysql_cmd -e "DROP DATABASE IF EXISTS ${tmp_db};"
  "./createdatabase_${province}.sh" "$USER" "$PASSWORD" "$tmp_db" suppressPwdGen >/dev/null

  for u in $REQUIRED_UPDATES; do
    if [ -f "updates/$u" ]; then
      mysql_cmd "$tmp_db" < "updates/$u"
    fi
  done

  echo ">> dumping ${province} schema + seed"
  # Schema only (province-neutral + province tables together; we split by province by building two
  # DBs and diffing table sets in the verification step, not here).
  mysqldump -h "$HOST" -u "$USER" -p"$PASSWORD" \
    --no-data --skip-comments --skip-dump-date --single-transaction \
    "$tmp_db" | normalize > "migration/_generated_${province}_schema.sql"

  # Required seed + reference (data). --hex-blob keeps encrypted/binary columns intact.
  mysqldump -h "$HOST" -u "$USER" -p"$PASSWORD" \
    --no-create-info --skip-comments --skip-dump-date --hex-blob --single-transaction \
    "$tmp_db" | normalize > "migration/_generated_${province}_data.sql"

  mysql_cmd -e "DROP DATABASE IF EXISTS ${tmp_db};"
}

echo "== CARLOS baseline generator =="
echo "This produces migration/_generated_*.sql. Review, then split into the V1* files per"
echo "docs/database-schema-management.md before committing. Existing V1* files are NOT overwritten"
echo "automatically — this guards against clobbering a reviewed baseline."
echo

build_and_dump on 9
build_and_dump bc 10

echo
echo ">> raw generated dumps written to migration/_generated_*.sql"
echo ">> Next: split province-neutral vs province-specific tables into:"
echo "     common/V1__baseline_core_schema.sql, common/V1.1__baseline_core_seed.sql,"
echo "     common/V1.2__reference_icd.sql, on/V1.0.1__on_schema.sql, on/V1.1.1__on_seed.sql,"
echo "     bc/V1.0.1__bc_schema.sql, bc/V1.1.1__bc_seed.sql"
echo ">> Then run the verification diff (docs/database-schema-management.md) and commit."
