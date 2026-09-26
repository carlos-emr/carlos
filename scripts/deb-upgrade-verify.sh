#!/bin/bash
# Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
#
# This software is published under the GPL GNU General Public License.
# This program is free software; you can redistribute it and/or
# modify it under the terms of the GNU General Public License
# as published by the Free Software Foundation; either version 2
# of the License, or (at your option) any later version.
#
# CARLOS EMR Project
# https://github.com/carlos-emr/carlos
#
# deb-upgrade-verify.sh — assert a package upgrade left an install intact.
# Takes the pre-upgrade snapshot in $PRE (from deb-upgrade-baseline.sh), takes
# a fresh one, diffs them, and asserts the contract an upgrade must keep:
# schema migrated forward (never re-run, never lost), the operator's password
# untouched, /etc config and TLS preserved, every clinical row count and every
# stored document file preserved, the service up behind the front door, and the
# new build identity in place. Run as root after `apt-get install` returns.
# UPGRADE_LOG, EXPECT_FLYWAY (count), EXPECT_NEW (space-separated versions) and
# EXPECT_TAG tune it for a given release pair; defaults match a11 -> a12.
#
# EXPECT_FLYWAY counts the rows in flyway_schema_history on the install under test,
# NOT the migration files the package ships. They differ: a12 ships 27 migration
# files, but the BC-only ones never apply to an Ontario install, so an ON install
# upgraded from a11 goes 19 applied -> 23 applied. Set it to the applied count for
# the province being verified. EXPECT_NEW may be empty when the two packages ship
# the same migration set (a packaging-only upgrade).
#
# From 2026.08.0~alpha16 (developer builds: 2026.09.0~snapshot25) the carlos-ctl command is its own package that
# carlos-emr depends on (carlos-emr/carlos#4001). EXPECT_SPLIT=1 (the default
# once the post-upgrade dpkg knows carlos-ctl) adds the split contract: the
# command, its alias and its man page belong to carlos-ctl and nothing of the
# old in-carlos-emr copy remains; the manifests carlos-ctl reads are shipped by
# carlos-emr; `carlos-ctl check` names both package versions; the boot-time
# provisioning unit starts /usr/sbin/carlos-ctl. EXPECT_CTL pins the carlos-ctl
# version the upgrade was expected to leave installed (empty: any).
set -u
PRE="${PRE:-/root/baseline-a11.txt}"; POST="${POST:-/root/baseline-a12.txt}"
UPGRADE_LOG="${UPGRADE_LOG:-/root/a12-upgrade.log}"
EXPECT_FLYWAY="${EXPECT_FLYWAY:-23}"; EXPECT_NEW="${EXPECT_NEW-1.0.20 1.0.21 1.0.22 1.0.23}"
EXPECT_TAG="${EXPECT_TAG:-build.version=2026.08.0-alpha12-SNAPSHOT build.job=carlos-emr-deb build.number=2026.09.0~snapshot22}"
EXPECT_SPLIT="${EXPECT_SPLIT:-$(dpkg-query -W -f='${db:Status-Status}' carlos-ctl 2>/dev/null | grep -qx installed && echo 1 || echo 0)}"
EXPECT_CTL="${EXPECT_CTL:-}"
HERE="$(cd "$(dirname "$0")" && pwd)"
# Own the log path instead of writing a predictable name into a world-writable /tmp.
CHECK_LOG="$(mktemp -t deb-upgrade-check.XXXXXX)"
trap 'rm -f "$CHECK_LOG"' EXIT
g() { sed -n "s/^$2=//p" "$1"; }
pass=0; fail=0
ok()  { echo "PASS $1"; pass=$((pass+1)); }
bad() { echo "FAIL $1"; fail=$((fail+1)); }
[ -r "$PRE" ] || { echo "PRE snapshot $PRE not readable"; exit 2; }
[ -r "$UPGRADE_LOG" ] && { echo "== upgrade log =="; grep -E "^UPGRADE_RC=" "$UPGRADE_LOG"; grep -iE "^E: |dpkg: error|No space|FAILED" "$UPGRADE_LOG" | head -5 | cut -c1-140; }
for i in $(seq 1 60); do c=$(curl -sk -o /dev/null -w "%{http_code}" https://127.0.0.1/carlos/); [ "$c" = 200 ] && break; sleep 5; done; echo "front=$c after $((i*5))s"
echo "== carlos-ctl check =="; carlos-ctl check > "$CHECK_LOG" 2>&1; grep -E "All checks passed|check\(s\) failed" "$CHECK_LOG"
"$HERE/deb-upgrade-baseline.sh" > "$POST"
echo "== pre/post diff (changed keys) =="; diff <(sort "$PRE") <(sort "$POST") | grep -E "^[<>]" | sed 's/^</  before: /;s/^>/  after:  /'
echo "== assertions =="
[ "$(g $POST service.active)" = active ] && ok "carlos-emr service active" || bad "service is $(g $POST service.active)"
[ "$(g $POST flyway.count)" = "$EXPECT_FLYWAY" ] && [ "$(g $POST flyway.failed)" = 0 ] && ok "Flyway at $EXPECT_FLYWAY applied, 0 failed" || bad "flyway count=$(g $POST flyway.count) failed=$(g $POST flyway.failed)"
newv=$(comm -13 <(g $PRE flyway.versions | tr , '\n' | sort) <(g $POST flyway.versions | tr , '\n' | sort) | sort -V | tr '\n' ' ' | sed 's/ $//')
[ "$newv" = "$EXPECT_NEW" ] && ok "exactly the expected new migrations applied: $newv" || bad "new migrations were: '$newv' (expected '$EXPECT_NEW')"
lost=$(comm -23 <(g $PRE flyway.versions | tr , '\n' | sort) <(g $POST flyway.versions | tr , '\n' | sort) | tr '\n' ' ')
[ -z "$lost" ] && ok "no previously applied migration disappeared from history" || bad "migrations missing after upgrade: $lost"
[ "$(g $PRE admin.hash)" = "$(g $POST admin.hash)" ] && ok "operator password hash unchanged (bootstrap-admin is idempotent)" || bad "carlosdoc hash CHANGED across upgrade"
[ "$(g $POST admin.forceReset)" = "$(g $PRE admin.forceReset)" ] && ok "forcePasswordReset unchanged" || bad "forcePasswordReset $(g $PRE admin.forceReset) -> $(g $POST admin.forceReset)"
for k in cfg.carlos-emr.env.sha cfg.carlos.properties.sha cfg.backup.env.sha cfg.tls.cert.sha cfg.province cfg.tz cfg.dbname cfg.tls.mode; do [ "$(g $PRE $k)" = "$(g $POST $k)" ] && ok "$k preserved" || bad "$k changed: $(g $PRE $k) -> $(g $POST $k)"; done
for k in rows.demographic rows.appointment rows.prescription rows.drugs rows.allergies rows.consultationRequests rows.casemgmt_note rows.preventions rows.hl7TextMessage rows.document rows.tickler; do [ "$(g $PRE $k)" = "$(g $POST $k)" ] && ok "$k preserved ($(g $POST $k))" || bad "$k changed: $(g $PRE $k) -> $(g $POST $k)"; done
# The document store may legitimately GROW on upgrade (a12 ships synthetic HRM
# fixture files); what must not happen is a stored document losing its file.
missing=$(mariadb -u root carlos -Nse "SELECT docfilename FROM document" | while read -r f; do [ -e "/var/lib/carlos-emr/CarlosDocument/carlos/document/$f" ] || echo "$f"; done | wc -l)
[ "$missing" = 0 ] && ok "no stored document lost its file (store $(g $PRE docs.files) -> $(g $POST docs.files) files)" || bad "$missing document file(s) missing after upgrade"
echo "$(g $POST war.buildtag)" | grep -qF "$EXPECT_TAG" && ok "build tag carries the new version and deb stamp" || bad "build tag: $(g $POST war.buildtag)"
[ "$(g $POST http.front)" = 200 ] && ok "front door 200" || bad "front door $(g $POST http.front)"
if [ "$EXPECT_SPLIT" = 1 ]; then
  echo "== carlos-ctl split contract =="
  ctlv=$(g $POST pkg.carlos-ctl)
  [ -n "$ctlv" ] && ok "carlos-ctl installed ($ctlv)" || bad "carlos-ctl is not installed"
  [ -z "$EXPECT_CTL" ] || { [ "$ctlv" = "$EXPECT_CTL" ] && ok "carlos-ctl is the expected $EXPECT_CTL" || bad "carlos-ctl is $ctlv, expected $EXPECT_CTL"; }
  for f in /usr/sbin/carlos-ctl /usr/sbin/carlosctl /usr/share/man/man8/carlos-ctl.8.gz /usr/share/man/man8/carlosctl.8.gz; do
    owner=$(dpkg -S "$f" 2>/dev/null | cut -d: -f1 | sort -u | tr '\n' ' ' | sed 's/ $//')
    [ "$owner" = carlos-ctl ] && ok "$f belongs to carlos-ctl" || bad "$f belongs to '${owner:-nobody}'"
  done
  [ "$(readlink -f /usr/sbin/carlos-ctl)" = /usr/lib/carlos-ctl/carlos-ctl ] && ok "/usr/sbin/carlos-ctl resolves into /usr/lib/carlos-ctl" || bad "/usr/sbin/carlos-ctl resolves to $(readlink -f /usr/sbin/carlos-ctl)"
  [ -f /usr/lib/carlos-ctl/carlos_ctl/cli.py ] && ok "the CLI package is under /usr/lib/carlos-ctl" || bad "/usr/lib/carlos-ctl/carlos_ctl/cli.py missing"
  [ ! -e /usr/lib/carlos-emr/carlos_ctl ] && [ ! -e /usr/lib/carlos-emr/carlos-ctl ] && ok "nothing of the pre-split CLI copy remains under /usr/lib/carlos-emr" || bad "pre-split CLI copy still present: $(ls -d /usr/lib/carlos-emr/carlos_ctl /usr/lib/carlos-emr/carlos-ctl 2>/dev/null | tr '\n' ' ')"
  [ ! -e /usr/lib/carlos-emr/carlos_ctl/__pycache__ ] && ok "no orphaned bytecode cache under /usr/lib/carlos-emr" || bad "orphaned /usr/lib/carlos-emr/carlos_ctl/__pycache__"
  mf_ok=1
  for m in o19map_schema o19map_props o19_preflight; do
    f=/usr/share/carlos-emr/o19-manifest/$m.json
    { [ -f "$f" ] && python3 -c 'import json,sys; d=json.load(open(sys.argv[1])); sys.exit(0 if d.get("format")==1 else 1)' "$f"; } || { mf_ok=0; bad "manifest $f missing or not format 1"; }
  done
  [ "$mf_ok" = 1 ] && ok "the three OSCAR 19 manifests ship with carlos-emr at format 1"
  grep -q "^  carlos-emr $(g $POST pkg.carlos-emr)\$" "$CHECK_LOG" && ok "check names the carlos-emr version" || bad "check does not name carlos-emr $(g $POST pkg.carlos-emr)"
  grep -q "^  carlos-ctl $ctlv\$" "$CHECK_LOG" && ok "check names the carlos-ctl version" || bad "check does not name carlos-ctl $ctlv"
  grep -q '^ExecStart=/usr/sbin/carlos-ctl finish-install --boot$' /usr/lib/systemd/system/carlos-emr-provision.service 2>/dev/null && ok "carlos-emr-provision.service starts /usr/sbin/carlos-ctl" || bad "carlos-emr-provision.service ExecStart: $(grep ^ExecStart= /usr/lib/systemd/system/carlos-emr-provision.service 2>/dev/null)"
  [ -x /usr/lib/carlos-ctl/carlos-ctl ] && grep -q '^export PYTHONDONTWRITEBYTECODE=1' /usr/lib/carlos-ctl/carlos-ctl && ok "the shim disables bytecode" || bad "the shim does not set PYTHONDONTWRITEBYTECODE"
  [ ! -d /usr/lib/carlos-ctl/carlos_ctl/__pycache__ ] && ok "check left no bytecode under /usr/lib/carlos-ctl" || bad "check wrote /usr/lib/carlos-ctl/carlos_ctl/__pycache__"
fi
echo "NOTE consultServices.active $(g $PRE consultServices.active) -> $(g $POST consultServices.active); prescription sig ids $(g $PRE prescription.sigIds) -> $(g $POST prescription.sigIds) (demo-data is skipped on an already-loaded install, so data-seed fixes reach fresh installs only)"
echo "== $pass passed, $fail failed =="
[ "$fail" = 0 ]
