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
#       [--db o19_fixture] [--with-olis] [--mysql-cmd mariadb] \
#       [--mysql-arg -uroot] [--mysql-arg -pSECRET] ...
#
# --with-olis loads olis/olisinit.sql (not in the stock createdatabase order,
# but present on real OLIS sites; exercises the OLIS-dropped path).
# Repeatable --mysql-arg values pass client credentials/host through.

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

OSCAR_SRC="" OUT="" DB="o19_fixture" WITH_OLIS=0 MYSQL_CMD="mariadb"
MYSQL_ARGS=()
while [ $# -gt 0 ]; do
  case "$1" in
    --oscar-src) OSCAR_SRC="$2"; shift 2 ;;
    --out) OUT="$2"; shift 2 ;;
    --db) DB="$2"; shift 2 ;;
    --with-olis) WITH_OLIS=1; shift ;;
    --mysql-cmd) MYSQL_CMD="$2"; shift 2 ;;
    --mysql-arg) MYSQL_ARGS+=("$2"); shift 2 ;;
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
case "$DB" in (*[!A-Za-z0-9_]*|"")
  echo "ERROR: invalid database name '$DB'" >&2; exit 1 ;;
esac

run_sql() { "$MYSQL_CMD" ${MYSQL_ARGS+"${MYSQL_ARGS[@]}"} "$@"; }

echo "creating latin1 database $DB ..."
run_sql -e "DROP DATABASE IF EXISTS \`$DB\`;
            CREATE DATABASE \`$DB\` CHARACTER SET latin1 COLLATE latin1_swedish_ci;"

# createdatabase_generic.sh order (Ontario, ICD-9), permissive session so the
# 2006-era SQL loads on a modern MariaDB exactly as it did on MySQL 5.x.
load() {
  echo "loading $1 ..."
  run_sql --init-command="SET SESSION sql_mode='', FOREIGN_KEY_CHECKS=0" \
          "$DB" < "$2"
}
load oscarinit.sql          "$SQLDIR/oscarinit.sql"
load oscarinit_on.sql       "$SQLDIR/oscarinit_on.sql"
load oscardata.sql          "$SQLDIR/oscardata.sql"
load oscardata_on.sql       "$SQLDIR/oscardata_on.sql"
load icd9.sql               "$SQLDIR/icd9.sql"
load caisi/initcaisi.sql    "$SQLDIR/caisi/initcaisi.sql"
load caisi/initcaisidata.sql "$SQLDIR/caisi/initcaisidata.sql"
load icd9_issue_groups.sql  "$SQLDIR/icd9_issue_groups.sql"
load measurementMapData.sql "$SQLDIR/measurementMapData.sql"
load expire_oscardoc.sql    "$SQLDIR/expire_oscardoc.sql"
if [ "$WITH_OLIS" = 1 ]; then
  load olis/olisinit.sql "$SQLDIR/olis/olisinit.sql"
fi

load "vendored demo.sql" "$SCRIPT_DIR/fixtures/demo-data/demo.sql"
load "fixture-document-rows.sql" \
     "$SCRIPT_DIR/fixtures/documents/fixture-document-rows.sql"

mkdir -p "$OUT"
echo "dumping to $OUT/o19-fixture.sql.gz ..."
"${MYSQL_CMD}dump" ${MYSQL_ARGS+"${MYSQL_ARGS[@]}"} \
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
