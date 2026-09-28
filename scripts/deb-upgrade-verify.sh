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
set -uo pipefail
PRE="${PRE:-/root/baseline-a11.txt}"; POST="${POST:-/root/baseline-a12.txt}"
UPGRADE_LOG="${UPGRADE_LOG:-/root/a12-upgrade.log}"
EXPECT_FLYWAY="${EXPECT_FLYWAY:-23}"; EXPECT_NEW="${EXPECT_NEW-1.0.20 1.0.21 1.0.22 1.0.23}"
EXPECT_TAG="${EXPECT_TAG:-build.version=2026.08.0-alpha12-SNAPSHOT build.job=carlos-emr-deb build.number=2026.09.0~snapshot22}"
EXPECT_SPLIT="${EXPECT_SPLIT:-$(dpkg-query -W -f='${db:Status-Status}' carlos-ctl 2>/dev/null | grep -qx installed && echo 1 || echo 0)}"
EXPECT_CTL="${EXPECT_CTL:-}"
HERE="$(cd "$(dirname "$0")" && pwd)"
source "$HERE/lib/deb-upgrade-common.sh"
# Own the log path instead of writing a predictable name into a world-writable /tmp.
CHECK_LOG="$(mktemp -t deb-upgrade-check.XXXXXX)" || exit 2
DOC_LIST="$(mktemp -t deb-upgrade-documents.XXXXXX)" || { rm -f "$CHECK_LOG"; exit 2; }
trap 'rm -f "$CHECK_LOG" "$DOC_LIST"' EXIT
g() {
  awk -v key="$2" 'index($0,key "=")==1 { print substr($0,length(key)+2); found++ } END { if (found!=1) exit 1 }' "$1"
}
pass=0; fail=0
ok()  { echo "PASS $1"; pass=$((pass+1)); }
bad() { echo "FAIL $1"; fail=$((fail+1)); }
[ -r "$PRE" ] || { echo "PRE snapshot $PRE not readable"; exit 2; }
[[ ! "$PRE" -ef "$POST" ]] || { echo 'ERROR: PRE and POST must refer to different files; refusing to overwrite the baseline.' >&2; exit 2; }
[ "$(g "$PRE" baseline.format)" = 2 ] || { echo 'ERROR: capture a fresh format-2 PRE snapshot; legacy snapshots contain raw credential fields.' >&2; exit 2; }
[ -r "$UPGRADE_LOG" ] && { echo "== upgrade log =="; grep -E "^UPGRADE_RC=" "$UPGRADE_LOG"; grep -iE "^E: |dpkg: error|No space|FAILED" "$UPGRADE_LOG" | head -5 | cut -c1-140; }
if [ -r "$UPGRADE_LOG" ] && grep -q '^UPGRADE_RC=' "$UPGRADE_LOG"; then
  upgrade_rc=$(g "$UPGRADE_LOG" UPGRADE_RC)
  [ "$upgrade_rc" = 0 ] || bad 'upgrade command failed or its recorded exit status is invalid'
fi
started=$SECONDS; deadline=$((SECONDS+300)); c=000
while [ "$SECONDS" -lt "$deadline" ]; do
  c=$(curl -sk --max-time 10 -o /dev/null -w "%{http_code}" "$FRONT_URL") || c=000
  [ "$c" = 200 ] && break
  sleep 5
done
echo "front=$c after $((SECONDS-started))s"
echo "== carlos-ctl check =="
carlos-ctl check > "$CHECK_LOG" 2>&1 || bad 'carlos-ctl health check failed'
grep -E "All checks passed|check\(s\) failed" "$CHECK_LOG" || true
if ! "$HERE/deb-upgrade-baseline.sh" > "$POST"; then
  echo 'ERROR: post-upgrade baseline failed; refusing to compare partial snapshots.' >&2
  exit 2
fi
# Required values must exist exactly once and be non-empty: missing queries or
# malformed snapshots must never turn into equality-of-empty-values successes.
required=(baseline.format service.active flyway.count flyway.versions flyway.failed admin.hash admin.forceReset admin.pin
  cfg.carlos-emr.env.sha cfg.carlos.properties.sha cfg.backup.env.sha cfg.tls.cert.sha cfg.province cfg.tz cfg.dbname cfg.tls.mode
  rows.demographic rows.appointment rows.prescription rows.drugs rows.allergies rows.consultationRequests rows.casemgmt_note
  rows.preventions rows.hl7TextMessage rows.document rows.tickler docs.files war.buildtag http.front)
for snapshot in "$PRE" "$POST"; do
  for key in "${required[@]}"; do
    if ! value=$(g "$snapshot" "$key") || [ -z "$value" ]; then
      echo "ERROR: missing, duplicate or empty required snapshot field: $key" >&2
      exit 2
    fi
    case "$key" in
      flyway.count|flyway.failed|rows.*|docs.files)
        [[ $value =~ ^[0-9]+$ ]] || { echo "ERROR: non-numeric snapshot field: $key" >&2; exit 2; } ;;
      admin.hash|admin.pin)
        [[ $value =~ ^[[:xdigit:]]{64}$ ]] || { echo "ERROR: invalid credential digest field: $key" >&2; exit 2; } ;;
    esac
  done
done
echo "== pre/post diff (changed non-credential keys) =="
diff <(grep -v '^admin\.' "$PRE" | sort) <(grep -v '^admin\.' "$POST" | sort) | grep -E "^[<>]" | sed 's/^</  before: /;s/^>/  after:  /' || true
echo "== assertions =="
[ "$(g "$POST" service.active)" = active ] && ok "carlos-emr service active" || bad "service is $(g "$POST" service.active)"
[ "$(g "$POST" flyway.count)" = "$EXPECT_FLYWAY" ] && [ "$(g "$POST" flyway.failed)" = 0 ] && ok "Flyway at $EXPECT_FLYWAY applied, 0 failed" || bad "flyway count=$(g "$POST" flyway.count) failed=$(g "$POST" flyway.failed)"
newv=$(comm -13 <(g "$PRE" flyway.versions | tr , '\n' | sort) <(g "$POST" flyway.versions | tr , '\n' | sort) | sort -V | tr '\n' ' ' | sed 's/ $//')
[ "$newv" = "$EXPECT_NEW" ] && ok "exactly the expected new migrations applied: $newv" || bad "new migrations were: '$newv' (expected '$EXPECT_NEW')"
lost=$(comm -23 <(g "$PRE" flyway.versions | tr , '\n' | sort) <(g "$POST" flyway.versions | tr , '\n' | sort) | tr '\n' ' ')
[ -z "$lost" ] && ok "no previously applied migration disappeared from history" || bad "migrations missing after upgrade: $lost"
[ "$(g "$PRE" admin.hash)" = "$(g "$POST" admin.hash)" ] && ok "operator password digest unchanged (bootstrap-admin is idempotent)" || bad "selected administrator password CHANGED across upgrade"
[ "$(g "$PRE" admin.pin)" = "$(g "$POST" admin.pin)" ] && ok "operator PIN digest unchanged" || bad "selected administrator PIN CHANGED across upgrade"
[ "$(g "$POST" admin.forceReset)" = "$(g "$PRE" admin.forceReset)" ] && ok "forcePasswordReset unchanged" || bad "forcePasswordReset $(g "$PRE" admin.forceReset) -> $(g "$POST" admin.forceReset)"
for k in cfg.carlos-emr.env.sha cfg.carlos.properties.sha cfg.backup.env.sha cfg.tls.cert.sha cfg.province cfg.tz cfg.dbname cfg.tls.mode; do [ "$(g "$PRE" $k)" = "$(g "$POST" $k)" ] && ok "$k preserved" || bad "$k changed: $(g "$PRE" $k) -> $(g "$POST" $k)"; done
for k in rows.demographic rows.appointment rows.prescription rows.drugs rows.allergies rows.consultationRequests rows.casemgmt_note rows.preventions rows.hl7TextMessage rows.document rows.tickler; do [ "$(g "$PRE" $k)" = "$(g "$POST" $k)" ] && ok "$k preserved ($(g "$POST" $k))" || bad "$k changed: $(g "$PRE" $k) -> $(g "$POST" $k)"; done
# The document store may legitimately GROW on upgrade (a12 ships synthetic HRM
# fixture files); what must not happen is a stored document losing its file.
# Legacy HTML/link documents store their body in docxml rather than a file.
# Java String.trim() removes only U+0000..U+0020. Match those UTF-8 bytes in HEX
# so tabs/newlines and SQL modes cannot change the fallback eligibility check;
# return only the boolean, never the stored clinical HTML body.
if ! db_query "SELECT HEX(docfilename), CASE WHEN docxml IS NOT NULL AND HEX(docxml) NOT REGEXP '^(0[0-9A-F]|1[0-9A-F]|20)*$' THEN 1 ELSE 0 END FROM document" > "$DOC_LIST"; then
  echo 'ERROR: document inventory query failed; missing-file count is unknown.' >&2
  exit 2
fi
missing=$(python3 - "$DOC_DIR" "$DOC_LIST" "$(g "$POST" rows.document)" <<'PY'
from pathlib import Path
import sys
root = Path(sys.argv[1]).resolve()
missing = 0
entries = Path(sys.argv[2]).read_text().splitlines()
if len(entries) != int(sys.argv[3]):
    sys.exit('Document inventory row count changed since the snapshot')
for line in entries:
    try:
        encoded_name, inline = line.split('\t')
        if inline not in ('0', '1'):
            raise ValueError('Invalid inline-body flag')
        name = bytes.fromhex(encoded_name).decode('utf-8')
        candidate = (root / name).resolve()
        valid = bool(name) and candidate.is_relative_to(root) and (inline == '1' or candidate.is_file())
    except (ValueError, UnicodeError, OSError):
        valid = False
    missing += not valid
print(missing)
PY
) || { echo 'ERROR: document inventory validation failed.' >&2; exit 2; }
[ "$missing" = 0 ] && ok "every stored document has a file or inline HTML (store $(g "$PRE" docs.files) -> $(g "$POST" docs.files) files)" || bad "$missing document file(s) missing without an inline HTML fallback after upgrade"
echo "$(g "$POST" war.buildtag)" | grep -qF "$EXPECT_TAG" && ok "build tag carries the new version and deb stamp" || bad "build tag: $(g "$POST" war.buildtag)"
[ "$(g "$POST" http.front)" = 200 ] && ok "front door 200" || bad "front door $(g "$POST" http.front)"
if [ "$EXPECT_SPLIT" = 1 ]; then
  echo "== carlos-ctl split contract =="
  ctlv=$(g "$POST" pkg.carlos-ctl)
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
  grep -qFx "  carlos-emr $(g "$POST" pkg.carlos-emr)" "$CHECK_LOG" && ok "check names the carlos-emr version" || bad "check does not name carlos-emr $(g "$POST" pkg.carlos-emr)"
  grep -qFx "  carlos-ctl $ctlv" "$CHECK_LOG" && ok "check names the carlos-ctl version" || bad "check does not name carlos-ctl $ctlv"
  grep -q '^ExecStart=/usr/sbin/carlos-ctl finish-install --boot$' /usr/lib/systemd/system/carlos-emr-provision.service 2>/dev/null && ok "carlos-emr-provision.service starts /usr/sbin/carlos-ctl" || bad "carlos-emr-provision.service ExecStart: $(grep ^ExecStart= /usr/lib/systemd/system/carlos-emr-provision.service 2>/dev/null)"
  [ -x /usr/lib/carlos-ctl/carlos-ctl ] && grep -q '^export PYTHONDONTWRITEBYTECODE=1' /usr/lib/carlos-ctl/carlos-ctl && ok "the shim disables bytecode" || bad "the shim does not set PYTHONDONTWRITEBYTECODE"
  [ ! -d /usr/lib/carlos-ctl/carlos_ctl/__pycache__ ] && ok "check left no bytecode under /usr/lib/carlos-ctl" || bad "check wrote /usr/lib/carlos-ctl/carlos_ctl/__pycache__"
fi
echo "NOTE consultServices.active $(g "$PRE" consultServices.active) -> $(g "$POST" consultServices.active); prescription sig ids $(g "$PRE" prescription.sigIds) -> $(g "$POST" prescription.sigIds) (demo-data is skipped on an already-loaded install, so data-seed fixes reach fresh installs only)"
echo "== $pass passed, $fail failed =="
[ "$fail" = 0 ]
