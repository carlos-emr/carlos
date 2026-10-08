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
# deb-db-ownership-lock-checks.sh — the database-ownership lock on a PACKAGED
# install (#3678). The o19 guard cannot see an OSCAR 19 import until its
# ledger is published, so what keeps the package's own provisioning and an
# import apart is ONE lock, /var/lib/carlos-emr/.finish-install.lock: both
# postinsts, carlos-emr-provision.service and `carlos-ctl finish-install`
# take it before they check the guard, and `carlos-ctl import-o19` (carlos-ctl
# 1.1.2 and later) holds it for its whole run. This drives the real installed
# commands against each other:
#
#   1. import-o19 is refused, with nothing written, while a configure (flock(1)
#      exactly as the postinst takes it) holds the lock -- every mode.
#   2. finish-install is refused while an import holds it.
#   3. dpkg-reconfigure carlos-emr defers AT ONCE to an import holding it (no
#      five-minute wait), records no unfinished install and starts nothing;
#      dpkg-reconfigure carlos-emr-drugref refuses with the import's
#      instructions.
#   4. Once the import has gone, a reconfigure provisions and the EMR starts.
#
# The "import" in 2 and 3 holds the lock through the installed carlos_ctl
# module's own take_db_ownership_lock, with a fabricated in-progress ledger so
# the guard reports it; nothing is imported and no clinical data is touched,
# but the service is stopped and reconfigured: run it as root on a disposable
# install only.
set -euo pipefail

LOCK=/var/lib/carlos-emr/.finish-install.lock
WORKSPACE=/var/lib/carlos-emr/o19-import
LEDGER="${WORKSPACE}/state.json"
MARKER=/var/lib/carlos-emr/.install-incomplete
export DEBIAN_FRONTEND=noninteractive

fails=0
pass() { echo "  PASS  $*"; }
fail() { echo "  FAIL  $*"; fails=$((fails + 1)); }
check() { # check DESCRIPTION COMMAND...
    local what="$1"; shift
    if "$@"; then pass "$what"; else fail "$what"; fi
}

[ "$(id -u)" = 0 ] || { echo "run as root" >&2; exit 2; }
[ -x /usr/sbin/carlos-ctl ] || { echo "carlos-ctl is not installed" >&2; exit 2; }
# The package's tmpfiles.d lays down an EMPTY workspace on every install; any
# content in it is a real (or past) import, which this must never disturb.
if [ -n "$(ls -A "${WORKSPACE}" 2>/dev/null)" ]; then
    echo "${WORKSPACE} is not empty: this host has (or had) an import; refusing to run" >&2
    exit 2
fi

HOLDER=""
# The holder child touches READY only once IT holds the lock: a busy lock alone
# could be some other run's, and this script must never act on that.
READY="$(mktemp -u)"
LEDGER_CREATED=0
remove_ledger() {
    # only a ledger this script wrote, under a lock it held: the empty
    # workspace itself is the package's, and anything else is a real import's
    if [ "${LEDGER_CREATED}" = 1 ]; then
        rm -f "${LEDGER}"
        LEDGER_CREATED=0
    fi
}
release() {
    if [ -n "${HOLDER}" ]; then
        kill "${HOLDER}" 2>/dev/null || true
        wait "${HOLDER}" 2>/dev/null || true
        HOLDER=""
    fi
    rm -f "${READY}"
}
cleanup() {
    remove_ledger
    release
}
trap cleanup EXIT

wait_held() {
    local _
    for _ in $(seq 1 100); do
        [ -e "${READY}" ] && return 0
        kill -0 "${HOLDER}" 2>/dev/null || break
        sleep 0.1
    done
    echo "could not take ${LOCK} for the test (is another run holding it?)" >&2
    exit 2
}

hold_as_configure() {
    ( exec 9>"${LOCK}"; flock -n 9 || exit 1; : > "${READY}"; exec sleep 600 ) &
    HOLDER=$!
    wait_held
}

hold_as_import() {
    python3 - "${LOCK}" "${WORKSPACE}" "${READY}" <<'PY' &
import sys, time
sys.path.insert(0, "/usr/lib/carlos-ctl")
from carlos_ctl import o19import
o19import.take_db_ownership_lock(sys.argv[1], sys.argv[2])
open(sys.argv[3], "x").close()
time.sleep(600)
PY
    HOLDER=$!
    wait_held
}

echo "=== 1. import-o19 against a configure holding the lock ==="
hold_as_configure
for mode in "--admin-user MigrationAdmin --dump /nonexistent.sql --skip-documents" \
            "--admin-user MigrationAdmin --resume" \
            "--cleanup" \
            "--dry-run --dump /nonexistent.sql --skip-documents"; do
    # shellcheck disable=SC2086  # the mode is a word list on purpose
    if out="$(carlos-ctl import-o19 ${mode} 2>&1)"; then
        fail "import-o19 ${mode} ran while the lock was held"
    else
        check "import-o19 ${mode%% *}... refused while a configure holds the lock" \
            grep -q "owns the database" <<<"${out}"
    fi
    check "import-o19 ${mode%% *}... published no ledger" test ! -e "${LEDGER}"
done
release
check "a refused import left the lock free" flock -n "${LOCK}" true

echo "=== 2. finish-install against an import holding the lock ==="
hold_as_import
if out="$(carlos-ctl finish-install 2>&1)"; then
    fail "finish-install ran while an import held the lock"
else
    check "finish-install refused, naming import-o19 as a possible holder" \
        grep -q "import-o19" <<<"${out}"
fi
release

echo "=== 3. configure against an import holding the lock ==="
systemctl stop carlos-emr
hold_as_import
install -d -m 0700 "${WORKSPACE}"
if [ -n "$(ls -A "${WORKSPACE}")" ]; then
    echo "${WORKSPACE} filled while the lock was being taken; refusing to continue" >&2
    exit 2
fi
LEDGER_CREATED=1
printf '%s\n' '{"phases": {"check-pristine": {"status": "done"}, "backup": {"status": "done"}}}' > "${LEDGER}"
check "the guard reports the fabricated import" bash -c '! /usr/lib/carlos-emr/carlos-emr-o19-guard 2>/dev/null'
rm -f "${MARKER}"
started=$(date +%s)
out="$(dpkg-reconfigure -f noninteractive carlos-emr 2>&1)" || true
elapsed=$(( $(date +%s) - started ))
check "carlos-emr configure deferred without waiting out the lock (${elapsed}s)" test "${elapsed}" -lt 240
check "carlos-emr configure named the import and the way out" \
    grep -q "OSCAR 19 import is in progress and owns the database" <<<"${out}"
check "carlos-emr configure said to reconfigure after the import" \
    grep -q "dpkg-reconfigure carlos-emr" <<<"${out}"
check "carlos-emr configure recorded no unfinished install" test ! -e "${MARKER}"
check "carlos-emr configure did not start the EMR" bash -c '! systemctl is-active --quiet carlos-emr'
if out="$(dpkg-reconfigure -f noninteractive carlos-emr-drugref 2>&1)"; then
    fail "carlos-emr-drugref configure provisioned while an import held the lock"
else
    check "carlos-emr-drugref configure refused with the import's instructions" \
        grep -q "OSCAR 19 import is in progress" <<<"${out}"
fi

echo "=== 4. the import ends; provisioning resumes ==="
remove_ledger
release
check "carlos-emr-drugref configure succeeds" dpkg-reconfigure -f noninteractive carlos-emr-drugref
check "carlos-emr configure succeeds" dpkg-reconfigure -f noninteractive carlos-emr
check "no unfinished install is recorded" test ! -e "${MARKER}"
for _ in $(seq 1 60); do
    systemctl is-active --quiet carlos-emr && break
    sleep 2
done
check "the EMR is running again" systemctl is-active --quiet carlos-emr

echo
if [ "${fails}" -ne 0 ]; then
    echo "${fails} check(s) FAILED"
    exit 1
fi
echo "all database-ownership lock checks passed"
