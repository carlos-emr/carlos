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
# check that RENDERS the application, and it is where four defects that
# every SQL gate passed were found: a NULL in a column CARLOS maps as a
# Java primitive (HTTP 500 on the schedule, and on every chart's notes
# pane), a program row the import writes that Hibernate cannot hydrate,
# encounter-form menu entries naming forms CARLOS removed, and
# appointments carrying a program id the day view cannot show.
#
# WHAT IT NEEDS, and it checks each before doing anything:
#   * a database an import-o19 has already finished against, with its
#     state directory intact (the smoke reads the break-glass
#     credentials from admin-credentials.txt);
#   * the application's own database credential -- read from
#     $CARLOS_PROPERTIES (the file `carlos-ctl db-users` wrote), or
#     given as $DB_USER and $DB_PASSWORD. import-o19 provisions the
#     account with a GENERATED password, so there is no useful default;
#   * a JDK and Maven able to build this checkout, with exactly one
#     exploded build under target/;
#   * node with `playwright` installed (npm install playwright) and a
#     Chromium to point CHROME_PATH at;
#   * a Tomcat 11 tarball, either already unpacked at $TOMCAT_HOME or
#     downloadable from $TOMCAT_URL. A download is verified against
#     $TOMCAT_SHA512 (defaulted for the pinned version) or refused --
#     this archive is executed in front of a copy of clinic records.
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
DEFAULT_TOMCAT_URL=https://archive.apache.org/dist/tomcat/tomcat-11/v11.0.24/bin/apache-tomcat-11.0.24.tar.gz
#: Apache's published digest for DEFAULT_TOMCAT_URL, from
#: <that URL>.sha512. It pins the default download and nothing else:
#: override TOMCAT_VERSION or TOMCAT_URL and TOMCAT_SHA512 becomes
#: required, because this value no longer describes what is fetched.
DEFAULT_TOMCAT_SHA512=a2fb1bd511735bd3d135b87f628d2b1f71a43aed7c4d7511e770092e571bad6d5ad9e97a580852119770477fd86d7ed156d83d3cee2854bce260725ce48934d0
TOMCAT_URL=${TOMCAT_URL:-https://archive.apache.org/dist/tomcat/tomcat-11/v${TOMCAT_VERSION}/bin/apache-tomcat-${TOMCAT_VERSION}.tar.gz}
BASE_URL=${BASE_URL:-http://127.0.0.1:8080/carlos}
MYSQL_DATABASE=${MYSQL_DATABASE:-oscar}
O19_STATE_DIR=${O19_STATE_DIR:-/var/lib/carlos-emr/o19-import}
CARLOS_PROPERTIES=${CARLOS_PROPERTIES:-/etc/carlos-emr/carlos.properties}
DB_URI=${DB_URI:-jdbc:mysql://127.0.0.1:3306/}

say() { printf 'ui-smoke: %s\n' "$*"; }
die() { printf 'ui-smoke: ERROR: %s\n' "$*" >&2; exit 1; }

# The deployment's own credential, read from the file `carlos-ctl
# db-users` wrote. Defaulting the password to empty deployed a build
# that could not connect at all -- the importer provisions `carlos` with
# a GENERATED password -- and the failure surfaced as a blank page forty
# lines later. Java-properties escaping is undone the same way
# `util.prop_unescape` does it.
prop() {
  python3 - "$CARLOS_PROPERTIES" "$1" <<'PROP'
import re, sys
path, key = sys.argv[1], sys.argv[2]
want = re.compile(r"^\s*" + re.escape(key) + r"\s*[=:]\s*(.*?)\s*$")
with open(path, encoding="latin-1") as fh:
    for line in fh:
        if line.lstrip().startswith(("#", "!")):
            continue
        m = want.match(line.rstrip("\n"))
        if m:
            # `util.prop_unescape`, and only that: the deb writes these
            # values with `prop_escape`, which doubles backslashes and
            # nothing else. A general properties unescape would decode
            # sequences the writer never produced.
            sys.stdout.write(m.group(1).replace("\\\\", "\\"))
            break
PROP
}

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

# Resolved HERE, before the build, so a missing credential is a refusal
# with a name on it rather than a deployment that cannot log in.
if [ -z "${DB_USER:-}" ] || [ -z "${DB_PASSWORD:-}" ]; then
  [ -f "$CARLOS_PROPERTIES" ] \
    || die "no $CARLOS_PROPERTIES to read the database credential from;
     set CARLOS_PROPERTIES, or pass DB_USER and DB_PASSWORD"
  DB_USER=${DB_USER:-$(prop db_username)}
  DB_PASSWORD=${DB_PASSWORD:-$(prop db_password)}
fi
[ -n "$DB_USER" ] || die "no db_username in $CARLOS_PROPERTIES; set DB_USER"
[ -n "$DB_PASSWORD" ] \
  || die "no db_password in $CARLOS_PROPERTIES; set DB_PASSWORD.
     import-o19 provisions the application account with a GENERATED
     password, so an empty one deploys a build that cannot connect"

# -- Tomcat ----------------------------------------------------------------
if [ ! -x "$TOMCAT_HOME/bin/catalina.sh" ]; then
  say "fetching Tomcat $TOMCAT_VERSION into $TOMCAT_HOME"
  mkdir -p "$SMOKE_HOME"
  tarball=$SMOKE_HOME/apache-tomcat-$TOMCAT_VERSION.tar.gz
  curl -fsSL "$TOMCAT_URL" -o "$tarball" \
    || die "could not download $TOMCAT_URL; unpack a Tomcat 11 at
       \$TOMCAT_HOME yourself and re-run"
  # Verified or not unpacked. This archive is about to be EXECUTED in
  # front of a database holding a copy of a clinic's records, so a
  # compromised mirror or an intercepted download is not something to
  # warn about and continue past. Apache publishes the digest beside the
  # tarball: `curl -fsSL "$TOMCAT_URL.sha512"`.
  sha512=${TOMCAT_SHA512:-}
  if [ -z "$sha512" ] && [ "$TOMCAT_URL" = "$DEFAULT_TOMCAT_URL" ]; then
    sha512=$DEFAULT_TOMCAT_SHA512
  fi
  [ -n "$sha512" ] || die "set TOMCAT_SHA512 (Apache publishes it at
     \$TOMCAT_URL.sha512), or unpack a Tomcat you already trust at
     \$TOMCAT_HOME; an unverified archive is not run against clinic data"
  echo "$sha512  $tarball" | sha512sum -c - \
    || die "the Tomcat tarball does not match its expected sha512"
  mkdir -p "$TOMCAT_HOME"
  tar -xzf "$tarball" -C "$TOMCAT_HOME" --strip-components=1
fi

# -- build -----------------------------------------------------------------
cd "$REPO_ROOT"
if [ "${SKIP_BUILD:-0}" != 1 ]; then
  say "building (mvn -DskipTests package war:exploded)"
  mvn -q -DskipTests -T 1C package war:exploded
fi
# Never `head -1`: with two exploded builds under target/ (a SKIP_BUILD
# run after a version bump is the ordinary way to get there) that
# deploys whichever sorts first and reports on the wrong application.
mapfile -t exploded_builds < <(
  find "$REPO_ROOT/target" -maxdepth 1 -type d -name 'carlos-*' | sort)
case ${#exploded_builds[@]} in
  0) die "no exploded build under target/; run without SKIP_BUILD" ;;
  1) exploded=${exploded_builds[0]} ;;
  *) die "target/ holds ${#exploded_builds[@]} exploded builds
     ($(printf '%s ' "${exploded_builds[@]##*/}")); remove the stale ones
     or point the smoke at one explicitly" ;;
esac

# -- deploy ----------------------------------------------------------------
say "deploying $exploded to $TOMCAT_HOME/webapps/carlos"
rm -rf "$TOMCAT_HOME/webapps/carlos"
cp -a "${exploded%/}" "$TOMCAT_HOME/webapps/carlos"
props=$TOMCAT_HOME/webapps/carlos/WEB-INF/classes/carlos.properties
[ -f "$props" ] || die "the build has no WEB-INF/classes/carlos.properties"
# The password goes through the ENVIRONMENT, never argv: /proc/<pid>/cmdline
# is world-readable, and this is the credential to a database holding PHI.
# `dbops.flyway` passes it to Flyway the same way and for the same reason.
SMOKE_DB_PASSWORD="$DB_PASSWORD" \
python3 - "$props" "$MYSQL_DATABASE" "$DB_USER" "$DB_URI" <<'PY'
import os, re, sys
path, database, user, uri = sys.argv[1:5]
password = os.environ["SMOKE_DB_PASSWORD"]
text = open(path, encoding="latin-1").read()
# keep every query parameter the shipped value carries (zeroDateTimeBehavior
# and friends); only the schema name in front of the '?' changes


def set_name(m):
    # group(1) -- the ONE group this pattern has. Reading a second one
    # raised IndexError on every run, before Tomcat ever started.
    rest = m.group(1)
    return "db_name = {0}{1}".format(database, rest)

text = re.sub(r"(?m)^db_name\s*=\s*[^?\s]*(\S*)\s*$", set_name, text, count=1)
for key, value in (("db_username", user), ("db_password", password),
                   ("db_uri", uri)):
    # A FUNCTION replacement, never a template string: `re.sub` reads
    # backslash sequences in a literal replacement, so a password
    # holding `\w` raised `re.error: bad escape` and one holding `\1`
    # would have silently written a capture group into the file.
    # `prop_escape` on the way in, because that is how the deb writes
    # this file and how java.util.Properties reads it back.
    escaped = "{0} = {1}".format(key, value.replace("\\", "\\\\"))
    text = re.sub(r"(?m)^{0}\s*=.*$".format(key),
                  lambda _m, line=escaped: line, text, count=1)
# latin-1, matching PROPERTIES_ENCODING: the application loads this file
# with java.util.Properties.load(InputStream), which decodes ISO-8859-1.
open(path, "w", encoding="latin-1").write(text)
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
