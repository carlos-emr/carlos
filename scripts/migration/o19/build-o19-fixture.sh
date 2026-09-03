#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
#
# build-o19-fixture.sh — build an authentic OSCAR 19 rehearsal database and
# emit the three turnkey migration inputs (dump, documents tar, properties).
#
# The database is created LATIN1 (the MySQL 5.x default real O19 installs
# run) so the importer's charset-conversion path is genuinely exercised.
# Init/reference SQL comes from an OSCAR checkout (--oscar-src, in
# createdatabase_generic.sh order); the demo dataset and document rows are
# the vendored fixtures in fixtures/ (see fixtures/PROVENANCE.md).
#
# Usage:
#   build-o19-fixture.sh --oscar-src /path/to/oscar --out /path/out \
#       [--db o19_fixture] [--with-olis] [--with-updates] \
#       [--mysql-cmd mariadb] \
#       [--mysql-arg -uroot] [--mysql-arg --host=127.0.0.1] ... \
#       [--mysql-password-file /path/to/passfile]
#
# --with-olis loads olis/olisinit.sql (not in the stock createdatabase order,
# but present on real OLIS sites; exercises the OLIS-dropped path).
# --with-updates additionally replays database/mysql/updates/*.sql in name
# order (best effort: a real clinic database carries years of these patches,
# which add ~280 privilege rows and extra roles; a script that fails on a
# modern server is reported and skipped). Default off: the stock init set
# keeps the fixture's CONTENT stable from run to run (the dump itself is
# never byte-reproducible - see fixtures/PROVENANCE.md).
# Repeatable --mysql-arg values pass client options through. The password
# never travels on the command line (visible in process listings and shell
# history): give it via --mysql-password-file (exported as MYSQL_PWD into
# this script's environment, so every client/dump tool it spawns sees it),
# a client defaults file (--mysql-arg --defaults-extra-file=FILE), or an
# already-set MYSQL_PWD. A bare -p/--password would prompt interactively
# and hang the script, so it is refused as well.

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

OSCAR_SRC="" OUT="" DB="o19_fixture" WITH_OLIS=0 WITH_UPDATES=0 MYSQL_CMD="mariadb"
MYSQL_ARGS=()
while [ $# -gt 0 ]; do
  case "$1" in
    --oscar-src) OSCAR_SRC="$2"; shift 2 ;;
    --out) OUT="$2"; shift 2 ;;
    --db) DB="$2"; shift 2 ;;
    --with-olis) WITH_OLIS=1; shift ;;
    --with-updates) WITH_UPDATES=1; shift ;;
    --mysql-cmd) MYSQL_CMD="$2"; shift 2 ;;
    --mysql-arg) MYSQL_ARGS+=("$2"); shift 2 ;;
    --mysql-password-file) MYSQL_PWD="$(head -c 4096 "$2" | tr -d '\r\n')"; export MYSQL_PWD; shift 2 ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
done
[ -n "$OSCAR_SRC" ] && [ -n "$OUT" ] || {
  echo "usage: build-o19-fixture.sh --oscar-src DIR --out DIR [options]" >&2
  exit 2
}
SQLDIR="$OSCAR_SRC/database/mysql"
[ -f "$SQLDIR/oscarinit.sql" ] || {
  echo "ERROR: $OSCAR_SRC is not an OSCAR checkout (no database/mysql/oscarinit.sql)" >&2
  exit 1
}
for a in ${MYSQL_ARGS+"${MYSQL_ARGS[@]}"}; do
  case "$a" in
    -p?*|--password=*)
      # never echo the offending argument: it IS the password
      echo "ERROR: do not pass the password in argv (-p... / --password=...) — use --mysql-password-file" >&2
      exit 2 ;;
    -p|--password)
      echo "ERROR: a bare -p/--password would prompt and hang this script — use --mysql-password-file" >&2
      exit 2 ;;
  esac
done
case "$DB" in
  (*[!A-Za-z0-9_]*|"") echo "ERROR: invalid database name '$DB'" >&2; exit 1 ;;
esac

run_sql() { "$MYSQL_CMD" ${MYSQL_ARGS+"${MYSQL_ARGS[@]}"} "$@"; }

echo "creating latin1 database $DB ..."
run_sql -e "DROP DATABASE IF EXISTS \`$DB\`;
            CREATE DATABASE \`$DB\` CHARACTER SET latin1 COLLATE latin1_swedish_ci;"

# createdatabase_generic.sh order (Ontario, ICD-9), permissive session so the
# 2006-era SQL loads on a modern MariaDB exactly as it did on MySQL 5.x.
# default_storage_engine=MyISAM is authenticity, not convenience: the era's
# MySQL default was MyISAM, and wide forms like formONAREnhancedRecord
# exceed InnoDB's row-size limit — exactly why real O19 databases hold them
# as MyISAM (their dumps carry explicit ENGINE clauses, so the importer's
# staging restore is unaffected by the modern InnoDB default).
load() {
  echo "loading $1 ..."
  run_sql --init-command="SET SESSION sql_mode='', FOREIGN_KEY_CHECKS=0, default_storage_engine=MyISAM" \
          "$DB" < "$2"
}
load oscarinit.sql          "$SQLDIR/oscarinit.sql"
load oscarinit_on.sql       "$SQLDIR/oscarinit_on.sql"
load oscardata.sql          "$SQLDIR/oscardata.sql"
load oscardata_on.sql       "$SQLDIR/oscardata_on.sql"
load icd9.sql               "$SQLDIR/icd9.sql"
load caisi/initcaisi.sql    "$SQLDIR/caisi/initcaisi.sql"
# initcaisidata SOURCEs sibling files by relative path — the client must
# run from the caisi directory, as createdatabase_generic.sh did
(cd "$SQLDIR/caisi" &&
 run_sql --init-command="SET SESSION sql_mode='', FOREIGN_KEY_CHECKS=0, default_storage_engine=MyISAM" \
         "$DB" < initcaisidata.sql)
echo "loading caisi/initcaisidata.sql ... done"
load icd9_issue_groups.sql  "$SQLDIR/icd9_issue_groups.sql"
load measurementMapData.sql "$SQLDIR/measurementMapData.sql"
load expire_oscardoc.sql    "$SQLDIR/expire_oscardoc.sql"
if [ "$WITH_OLIS" = 1 ]; then
  # olisinit LOAD DATA LOCAL INFILEs its sibling CSVs — client cwd must be
  # the olis directory, and local-infile must be enabled
  (cd "$SQLDIR/olis" &&
   run_sql --local-infile=1 \
           --init-command="SET SESSION sql_mode='', FOREIGN_KEY_CHECKS=0, default_storage_engine=MyISAM" \
           "$DB" < olisinit.sql)
  echo "loading olis/olisinit.sql ... done"
fi

if [ "$WITH_UPDATES" = 1 ]; then
  # best-effort by design: 2006-era patches routinely fail on a modern
  # server. The client stops at the first failing statement, so a file
  # that fails may have applied its earlier statements (MyISAM has no
  # transactions) — the fixture is a rehearsal input, not a clinic, and
  # every failure is named with its diagnostic so a systematic cause
  # (wrong client options, wrong server) is visible.
  UPDATE_FAILURES=0
  for f in "$SQLDIR"/updates/*.sql; do   # glob: paths with spaces survive
    [ -e "$f" ] || continue
    if ! err=$(run_sql --init-command="SET SESSION sql_mode='', FOREIGN_KEY_CHECKS=0, default_storage_engine=MyISAM" \
               "$DB" < "$f" 2>&1 >/dev/null); then
      UPDATE_FAILURES=$((UPDATE_FAILURES + 1))
      printf 'warning: %s stopped at its first error (earlier statements of the file may have applied):\n%s\n' \
        "$(basename "$f")" "$err" >&2
    fi
  done
  echo "loading updates/*.sql ... done ($UPDATE_FAILURES file(s) failed, see warnings)"
  # the replay rewrites secUserRole (update-2008-04-07 drops and recreates
  # it); the anchors the rehearsal depends on must have survived
  anchors=$(run_sql -N -B "$DB" -e "SELECT (SELECT COUNT(*) FROM secUserRole WHERE provider_no='999998'), (SELECT COUNT(*) FROM Facility), (SELECT COUNT(*) FROM clinic)")
  case "$anchors" in
    2$'\t'[1-9]*$'\t'[1-9]*) ;;
    *) echo "error: after --with-updates the seed clinician's roles, Facility or clinic rows are gone ($anchors)" >&2
       exit 1 ;;
  esac
fi

load "vendored demo.sql" "$SCRIPT_DIR/fixtures/demo-data/demo.sql"
load "fixture-document-rows.sql" \
     "$SCRIPT_DIR/fixtures/documents/fixture-document-rows.sql"
# clinic-custom role, role-hygiene cases and legacy data the M8 roles
# post-step reconciles (synthetic; see fixtures/PROVENANCE.md)
load "fixture roles.sql" "$SCRIPT_DIR/fixtures/demo-data/roles.sql"

mkdir -p "$OUT"
# mariadb pairs with mariadb-dump (mariadbdump nowhere); mysql pairs with
# mysqldump, which older MariaDB hosts ship under that name only
DUMP_CMD=""
for candidate in "${MYSQL_CMD}-dump" "${MYSQL_CMD}dump" mysqldump; do
  if command -v "$candidate" >/dev/null 2>&1; then
    DUMP_CMD="$candidate"
    break
  fi
done
if [ -z "$DUMP_CMD" ]; then
  echo "no dump client found (tried ${MYSQL_CMD}-dump, ${MYSQL_CMD}dump," \
       "mysqldump)" >&2
  exit 1
fi
echo "dumping to $OUT/o19-fixture.sql.gz (via $DUMP_CMD) ..."
"$DUMP_CMD" ${MYSQL_ARGS+"${MYSQL_ARGS[@]}"} \
  --single-transaction --quick --default-character-set=latin1 "$DB" \
  | gzip > "$OUT/o19-fixture.sql.gz"

"$SCRIPT_DIR/fixtures/documents/make-documents-tar.sh" "$OUT"
cp "$SCRIPT_DIR/fixtures/properties/oscar-clinic-example.properties" \
   "$OUT/oscar.properties"

echo
echo "fixture inputs ready in $OUT:"
echo "  o19-fixture.sql.gz     (mysqldump of $DB, latin1)"
echo "  o19-documents.tar.gz   (OscarDocument tree)"
echo "  oscar.properties       (clinic-example properties)"
