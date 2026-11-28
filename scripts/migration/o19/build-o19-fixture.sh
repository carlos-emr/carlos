#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
#
# build-o19-fixture.sh — build an authentic OSCAR 19 rehearsal database and
# emit the three turnkey migration inputs (dump, documents tar, properties).
#
# The database is created LATIN1 (the MySQL 5.x default real O19 installs
# run). That alone exercises nothing while every byte of clinic data is
# ASCII, which is what the vendored dataset is — so fixtures/demo-data/
# clinical.sql adds accented text in both the correctly-encoded and the
# deliberately double-encoded form, which is what actually drives the
# charset scan, the per-row repair and the charset-repair sign-off.
# Init/reference SQL comes from an OSCAR checkout (--oscar-src, in
# createdatabase_generic.sh order); the demo dataset and document rows are
# the vendored fixtures in fixtures/ (see fixtures/PROVENANCE.md).
#
# --province picks which OSCAR 19 install to build, exactly as OSCAR's own
# createdatabase_<p>.sh does: the _on or _bc init/data pair, OLIS only for
# Ontario (it is the Ontario lab network), and for BC the extra
# rourke2009_from_oscarinit_bc.sql that createdatabase_bc.sh loads. The
# provincial clinical rows follow (clinical-on.sql / clinical-bc.sql) --
# Ontario OHIP claims or BC MSP claims, a WorkSafeBC report and a BCAR
# form, since neither province's billing tables exist in the other's
# database. Must match the province of the CARLOS host the fixture is
# imported into: the import asserts the manifest profile against it.
#
# Usage:
#   build-o19-fixture.sh --oscar-src /path/to/oscar --out /path/out \
#       [--province on|bc] [--db o19_fixture] [--with-olis] \
#       [--with-updates] \
#       [--carlos-ctl-dir /path/to/carlos-ctl] \
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
PROVINCE="on"
CARLOS_CTL_DIR="${CARLOS_CTL_SRC:-}"
MYSQL_ARGS=()
while [ $# -gt 0 ]; do
  case "$1" in
    --oscar-src) OSCAR_SRC="$2"; shift 2 ;;
    --out) OUT="$2"; shift 2 ;;
    --db) DB="$2"; shift 2 ;;
    --province) PROVINCE="$2"; shift 2 ;;
    --with-olis) WITH_OLIS=1; shift ;;
    --with-updates) WITH_UPDATES=1; shift ;;
    --carlos-ctl-dir) CARLOS_CTL_DIR="$2"; shift 2 ;;
    --mysql-cmd) MYSQL_CMD="$2"; shift 2 ;;
    --mysql-arg) MYSQL_ARGS+=("$2"); shift 2 ;;
    --mysql-password-file) MYSQL_PWD="$(head -c 4096 "$2" | tr -d '\r\n')"; export MYSQL_PWD; shift 2 ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
done
if [ -z "$OSCAR_SRC" ] || [ -z "$OUT" ]; then
  echo "usage: build-o19-fixture.sh --oscar-src DIR --out DIR [options]" >&2
  exit 2
fi
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
case "$PROVINCE" in
  on|bc) ;;
  *) echo "ERROR: --province must be 'on' or 'bc' (got '$PROVINCE')" >&2
     exit 2 ;;
esac
[ -f "$SQLDIR/oscarinit_$PROVINCE.sql" ] || {
  echo "ERROR: $SQLDIR has no oscarinit_$PROVINCE.sql" >&2; exit 1; }
if [ "$PROVINCE" = "bc" ] && [ "$WITH_OLIS" = 1 ]; then
  # OLIS is the ONTARIO lab network; loading it into a BC database would
  # build a clinic that cannot exist and exercise the OLIS-dropped path
  # against the wrong profile
  echo "ERROR: --with-olis is Ontario-only" >&2
  exit 2
fi

# The rehearsal dump embeds the exact medication FLOAT supplement (export-medication-floats.py),
# whose UPDATE statements are rejected by carlos-ctl 1.3.6 (digest format 3). Verify a compatible
# importer (digest format >= 4 and medication_float_table) before touching MariaDB or creating $OUT.
check_compatible_importer() {
  python3 - "$CARLOS_CTL_DIR" <<'PY'
import ast
import importlib.util
import sys
from pathlib import Path


def locate_package(explicit_dir: str):
    if explicit_dir:
        base = Path(explicit_dir)
        for candidate in (base / "carlos_ctl", base):
            if (candidate / "o19sources.py").is_file() and (candidate / "o19digest.py").is_file():
                return candidate
        return None
    installed = Path("/usr/lib/carlos-ctl/carlos_ctl")
    if (installed / "o19sources.py").is_file() and (installed / "o19digest.py").is_file():
        return installed
    spec = importlib.util.find_spec("carlos_ctl")
    if spec and spec.submodule_search_locations:
        for loc in spec.submodule_search_locations:
            candidate = Path(loc)
            if (candidate / "o19sources.py").is_file() and (candidate / "o19digest.py").is_file():
                return candidate
    return None


def digest_format(path: Path):
    tree = ast.parse(path.read_text(encoding="utf-8"), filename=str(path))
    for node in tree.body:
        if isinstance(node, ast.Assign) and isinstance(node.value, ast.Constant) and isinstance(node.value.value, int):
            if any(isinstance(t, ast.Name) and t.id == "DIGEST_FORMAT" for t in node.targets):
                return node.value.value
    return None


def supports_medication_float_supplement(path: Path) -> bool:
    tree = ast.parse(path.read_text(encoding="utf-8"), filename=str(path))
    return any(isinstance(node, ast.FunctionDef) and node.name == "medication_float_table" for node in tree.body)


explicit = sys.argv[1]
pkg = locate_package(explicit)
if pkg is None:
    detail = f"in {explicit}" if explicit else "in /usr/lib/carlos-ctl or PYTHONPATH"
    sys.stderr.write(
        "ERROR: no compatible carlos-ctl importer found " + detail + ".\n"
        "build-o19-fixture.sh emits an OSCAR19 dump with exact medication FLOAT UPDATE statements\n"
        "(export-medication-floats.py), which carlos-ctl 1.3.6 rejects during staging.\n"
        "Install a released carlos-ctl supporting digest format 4 and the medication FLOAT supplement\n"
        "(tracked in CARLOS #6420 / carlos-emr/carlos-ctl#32), or pass --carlos-ctl-dir DIR.\n"
    )
    sys.exit(1)

fmt = digest_format(pkg / "o19digest.py")
has_supplement = supports_medication_float_supplement(pkg / "o19sources.py")
if fmt is None or fmt < 4 or not has_supplement:
    sys.stderr.write(
        f"ERROR: incompatible carlos-ctl importer at {pkg} "
        f"(DIGEST_FORMAT={fmt}, medication_float_supplement={has_supplement}).\n"
        "build-o19-fixture.sh emits an OSCAR19 dump with exact medication FLOAT UPDATE statements\n"
        "(export-medication-floats.py), which carlos-ctl 1.3.6 rejects during staging.\n"
        "Install a released carlos-ctl supporting digest format 4 and the medication FLOAT supplement\n"
        "(tracked in CARLOS #6420 / carlos-emr/carlos-ctl#32), or pass --carlos-ctl-dir DIR.\n"
    )
    sys.exit(1)
PY
}
check_compatible_importer

run_sql() { "$MYSQL_CMD" ${MYSQL_ARGS+"${MYSQL_ARGS[@]}"} "$@"; }

echo "creating latin1 database $DB (province: $PROVINCE) ..."
run_sql -e "DROP DATABASE IF EXISTS \`$DB\`;
            CREATE DATABASE \`$DB\` CHARACTER SET latin1 COLLATE latin1_swedish_ci;"

# createdatabase_generic.sh order (Ontario, ICD-9), permissive session so the
# 2006-era SQL loads on a modern MariaDB exactly as it did on MySQL 5.x.
# default_storage_engine=MyISAM is authenticity, not convenience: the era's
# MySQL default was MyISAM, and wide forms like formONAREnhancedRecord
# exceed InnoDB's row-size limit — exactly why real O19 databases hold them
# as MyISAM (their dumps carry explicit ENGINE clauses, so the importer's
# staging restore is unaffected by the modern InnoDB default).
# $3 (optional) pins --default-character-set for THIS file. The vendored
# OSCAR init/data files are latin1-era bytes, so they are loaded under
# whatever the client is configured for, exactly as createdatabase_generic.sh
# did. clinical.sql is different: it is UTF-8 source whose whole purpose is
# to land one correctly-encoded and one deliberately double-encoded row in a
# latin1 database. Without a pinned charset that outcome depends on the
# client's default (latin1 on classic MySQL, utf8mb4 on a modern MariaDB) --
# under a latin1 client 'COTE' with accents is stored as its own UTF-8 bytes,
# which IS the mojibake case, collapsing the two cases into one and leaving
# the "correctly encoded text survives verbatim" branch never exercised.
load() {
  echo "loading $1 ..."
  run_sql ${3+--default-character-set="$3"} \
          --init-command="SET SESSION sql_mode='', FOREIGN_KEY_CHECKS=0, default_storage_engine=MyISAM" \
          "$DB" < "$2"
}
load oscarinit.sql          "$SQLDIR/oscarinit.sql"
load "oscarinit_$PROVINCE.sql" "$SQLDIR/oscarinit_$PROVINCE.sql"
load oscardata.sql          "$SQLDIR/oscardata.sql"
load "oscardata_$PROVINCE.sql" "$SQLDIR/oscardata_$PROVINCE.sql"
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
if [ "$PROVINCE" = "bc" ]; then
  # createdatabase_bc.sh loads this after the generic set; it is where a
  # BC install's formRourke2009 comes from
  load rourke2009_from_oscarinit_bc.sql \
       "$SQLDIR/rourke2009_from_oscarinit_bc.sql"
fi
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
# encounter notes, appointments, ticklers, consultations, billing and the
# three text encodings the charset path exists for — none of which the
# vendored dataset carries (synthetic; see fixtures/PROVENANCE.md)
load "fixture clinical.sql" "$SCRIPT_DIR/fixtures/demo-data/clinical.sql" \
     utf8mb4
# the provincial billing rows: neither province's billing tables exist in
# the other's database, so this file is the one that cannot be shared
load "fixture clinical-$PROVINCE.sql" \
     "$SCRIPT_DIR/fixtures/demo-data/clinical-$PROVINCE.sql" utf8mb4

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
# Read both exports while this disposable source is quiescent. A stock dump rounds FLOATs.
float_args=()
for option in ${MYSQL_ARGS+"${MYSQL_ARGS[@]}"}; do float_args+=("--mysql-arg=$option"); done
dump_tmp=$(mktemp "$OUT/.o19-dump.XXXXXX")
if "$DUMP_CMD" ${MYSQL_ARGS+"${MYSQL_ARGS[@]}"} --single-transaction --quick --skip-triggers \
     --default-character-set=latin1 "$DB" |
   python3 "$SCRIPT_DIR/export-medication-floats.py" --complete-dump --mysql-cmd "$MYSQL_CMD" --db "$DB" ${float_args+"${float_args[@]}"} |
   gzip > "$dump_tmp"; then
  mv "$dump_tmp" "$OUT/o19-fixture.sql.gz"
else
  rm -f "$dump_tmp"
  exit 1
fi

"$SCRIPT_DIR/fixtures/documents/make-documents-tar.sh" "$OUT"
cp "$SCRIPT_DIR/fixtures/properties/oscar-clinic-example.properties" \
   "$OUT/oscar.properties"

echo
echo "fixture inputs ready in $OUT:"
echo "  o19-fixture.sql.gz     (mysqldump of $DB, latin1)"
echo "  o19-documents.tar.gz   (OscarDocument tree)"
echo "  oscar.properties       (clinic-example properties)"
