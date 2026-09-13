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
set -u
PRE="${PRE:-/root/baseline-a11.txt}"; POST="${POST:-/root/baseline-a12.txt}"
UPGRADE_LOG="${UPGRADE_LOG:-/root/a12-upgrade.log}"
EXPECT_FLYWAY="${EXPECT_FLYWAY:-23}"; EXPECT_NEW="${EXPECT_NEW:-1.0.20 1.0.21 1.0.22 1.0.23}"
EXPECT_TAG="${EXPECT_TAG:-build.version=2026.08.0-alpha12-SNAPSHOT build.job=carlos-emr-deb build.number=2026.09.0~snapshot22}"
HERE="$(cd "$(dirname "$0")" && pwd)"
g() { sed -n "s/^$2=//p" "$1"; }
pass=0; fail=0
ok()  { echo "PASS $1"; pass=$((pass+1)); }
bad() { echo "FAIL $1"; fail=$((fail+1)); }
[ -r "$PRE" ] || { echo "PRE snapshot $PRE not readable"; exit 2; }
[ -r "$UPGRADE_LOG" ] && { echo "== upgrade log =="; grep -E "^UPGRADE_RC=" "$UPGRADE_LOG"; grep -iE "^E: |dpkg: error|No space|FAILED" "$UPGRADE_LOG" | head -5 | cut -c1-140; }
for i in $(seq 1 60); do c=$(curl -sk -o /dev/null -w "%{http_code}" https://127.0.0.1/carlos/); [ "$c" = 200 ] && break; sleep 5; done; echo "front=$c after $((i*5))s"
echo "== carlos-ctl check =="; carlos-ctl check > /tmp/deb-upgrade-check.log 2>&1; grep -E "All checks passed|check\(s\) failed" /tmp/deb-upgrade-check.log
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
echo "NOTE consultServices.active $(g $PRE consultServices.active) -> $(g $POST consultServices.active); prescription sig ids $(g $PRE prescription.sigIds) -> $(g $POST prescription.sigIds) (demo-data is skipped on an already-loaded install, so data-seed fixes reach fresh installs only)"
echo "== $pass passed, $fail failed =="
[ "$fail" = 0 ]
