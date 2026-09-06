#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
#
# Run the migrated-database UI smoke end to end: build CARLOS, put a
# Tomcat in a scratch directory in front of a database an
# `import-o19` produced, and drive
# scripts/o19-migrated-smoke-playwright-checks.js against it.
#
# Everything the importer proves today it proves in SQL. This is the one
# check that opens the application, and it is where three defects that
# every SQL gate passed were found: a NULL in a column CARLOS maps as a
# Java primitive (HTTP 500 on the schedule, and on every chart's notes
# pane), a program row the import writes that Hibernate cannot hydrate,
# and encounter-form menu entries naming forms CARLOS removed.
#
# WHAT IT NEEDS, and it checks each before doing anything:
#   * a database an import-o19 has already finished against, with its
#     state directory intact (the smoke reads the break-glass
#     credentials from admin-credentials.txt);
#   * a JDK and Maven able to build this checkout;
#   * node with `playwright` installed (npm install playwright) and a
#     Chromium to point CHROME_PATH at;
#   * a Tomcat 11 tarball, either already unpacked at $TOMCAT_HOME or
#     downloadable from $TOMCAT_URL.
#
# WHAT IT WRITES: a scratch Tomcat under $SMOKE_HOME (default
# /opt/smoke), a deployed copy of this build, and -- through the Node
# script -- the `security` rows of the two accounts it logs in as, which
# that script snapshots and restores. Point it at a REHEARSAL copy of a
# migration, never at a clinic's live post-go-live database.
#
# Usage:
#   scripts/migration/o19/rehearsal/ui-smoke.sh
#   SKIP_BUILD=1 scripts/migration/o19/rehearsal/ui-smoke.sh   # reuse target/
set -euo pipefail

REPO_ROOT=$(cd "$(dirname "$0")/../../../.." && pwd)
SMOKE_HOME=${SMOKE_HOME:-/opt/smoke}
TOMCAT_HOME=${TOMCAT_HOME:-$SMOKE_HOME/tomcat}
TOMCAT_VERSION=${TOMCAT_VERSION:-11.0.24}
TOMCAT_URL=${TOMCAT_URL:-https://archive.apache.org/dist/tomcat/tomcat-11/v${TOMCAT_VERSION}/bin/apache-tomcat-${TOMCAT_VERSION}.tar.gz}
BASE_URL=${BASE_URL:-http://127.0.0.1:8080/carlos}
MYSQL_DATABASE=${MYSQL_DATABASE:-oscar}
O19_STATE_DIR=${O19_STATE_DIR:-/var/lib/carlos-emr/o19-import}
DB_USER=${DB_USER:-carlos}
DB_PASSWORD=${DB_PASSWORD:-}
DB_URI=${DB_URI:-jdbc:mysql://127.0.0.1:3306/}

say() { printf 'ui-smoke: %s\n' "$*"; }
die() { printf 'ui-smoke: ERROR: %s\n' "$*" >&2; exit 1; }

# -- refuse to run against anything but a local, migrated database ----------
case "$BASE_URL" in
  http://127.0.0.1:*|http://localhost:*|http://[::1]:*) ;;
  *) die "BASE_URL $BASE_URL is not local; this script deploys a build and
     rewrites two security rows, so it only ever targets a local scratch
     instance" ;;
esac
[ -f "$O19_STATE_DIR/admin-credentials.txt" ] \
  || die "no $O19_STATE_DIR/admin-credentials.txt: run import-o19 first
     (the smoke logs in as the break-glass account that file names)"
[ -f "$O19_STATE_DIR/state.json" ] \
  || die "no $O19_STATE_DIR/state.json: this is not an import-o19 state
     directory"
command -v node >/dev/null || die "node is not on PATH"
node -e "require('playwright')" 2>/dev/null \
  || die "the playwright package is not installed (npm install playwright)"

# -- Tomcat ----------------------------------------------------------------
if [ ! -x "$TOMCAT_HOME/bin/catalina.sh" ]; then
  say "fetching Tomcat $TOMCAT_VERSION into $TOMCAT_HOME"
  mkdir -p "$SMOKE_HOME"
  tarball=$SMOKE_HOME/apache-tomcat-$TOMCAT_VERSION.tar.gz
  curl -fsSL "$TOMCAT_URL" -o "$tarball" \
    || die "could not download $TOMCAT_URL; unpack a Tomcat 11 at
       \$TOMCAT_HOME yourself and re-run"
  if [ -n "${TOMCAT_SHA512:-}" ]; then
    echo "$TOMCAT_SHA512  $tarball" | sha512sum -c - \
      || die "the Tomcat tarball does not match TOMCAT_SHA512"
  else
    say "WARNING: no TOMCAT_SHA512 given, so the download is unverified"
  fi
  mkdir -p "$TOMCAT_HOME"
  tar -xzf "$tarball" -C "$TOMCAT_HOME" --strip-components=1
fi

# -- build -----------------------------------------------------------------
cd "$REPO_ROOT"
if [ "${SKIP_BUILD:-0}" != 1 ]; then
  say "building (mvn -DskipTests package war:exploded)"
  mvn -q -DskipTests -T 1C package war:exploded
fi
exploded=$(ls -d "$REPO_ROOT"/target/carlos-*/ 2>/dev/null | head -1)
[ -n "$exploded" ] || die "no exploded build under target/; run without SKIP_BUILD"

# -- deploy ----------------------------------------------------------------
say "deploying $exploded to $TOMCAT_HOME/webapps/carlos"
rm -rf "$TOMCAT_HOME/webapps/carlos"
cp -a "${exploded%/}" "$TOMCAT_HOME/webapps/carlos"
props=$TOMCAT_HOME/webapps/carlos/WEB-INF/classes/carlos.properties
[ -f "$props" ] || die "the build has no WEB-INF/classes/carlos.properties"
python3 - "$props" "$MYSQL_DATABASE" "$DB_USER" "$DB_PASSWORD" "$DB_URI" <<'PY'
import re, sys
path, database, user, password, uri = sys.argv[1:6]
text = open(path, encoding="utf-8", errors="replace").read()
# keep every query parameter the shipped value carries (zeroDateTimeBehavior
# and friends); only the schema name in front of the '?' changes
def set_name(m):
    rest = m.group(2)
    return "db_name = {0}{1}".format(database, rest)
text = re.sub(r"(?m)^db_name\s*=\s*[^?\s]*(\S*)\s*$", set_name, text, count=1)
for key, value in (("db_username", user), ("db_password", password),
                   ("db_uri", uri)):
    text = re.sub(r"(?m)^{0}\s*=.*$".format(key),
                  "{0} = {1}".format(key, value), text, count=1)
open(path, "w", encoding="utf-8").write(text)
print("pointed the deployment at schema", database)
PY

# -- run -------------------------------------------------------------------
stop_tomcat() { "$TOMCAT_HOME/bin/catalina.sh" stop >/dev/null 2>&1 || true; }
trap stop_tomcat EXIT
stop_tomcat
sleep 2
say "starting Tomcat"
"$TOMCAT_HOME/bin/catalina.sh" start >/dev/null 2>&1
for _ in $(seq 1 90); do
  code=$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/" || true)
  [ "$code" = "200" ] && break
  sleep 2
done
[ "${code:-}" = "200" ] || die "the application did not answer 200 at $BASE_URL/
     (see $TOMCAT_HOME/logs/catalina.out)"
say "application is up; running the smoke"

BASE_URL="$BASE_URL" MYSQL_DATABASE="$MYSQL_DATABASE" \
  O19_STATE_DIR="$O19_STATE_DIR" \
  node "$REPO_ROOT/scripts/o19-migrated-smoke-playwright-checks.js"
rc=$?
say "smoke exited $rc"
exit $rc
