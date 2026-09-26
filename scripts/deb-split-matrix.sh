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
# deb-split-matrix.sh — the upgrade matrix of the carlos-ctl package split
# (carlos-emr/carlos#4001), run as root on a DISPOSABLE Ubuntu 26.04 host or
# systemd container. It installs the pre-split carlos-emr, then drives every
# transaction the split makes possible, asserting after each one what the
# host must look like; the post-upgrade state is checked with
# deb-upgrade-verify.sh (the same assertions every packaging upgrade must
# keep) plus its split contract.
#
#   1. pre-split carlos-emr upgraded to the split pair in one transaction
#   2. pre-split carlos-emr offered the new carlos-emr WITHOUT the carlos-ctl
#      file: apt refuses, nothing changed
#   3. split pair, carlos-emr only upgraded
#   4. split pair, carlos-ctl only upgraded
#   5. carlos-ctl upgrade attempted with a staged o19 import: the preinst
#      refuses, the import ledger is untouched
#   6. apt remove carlos-emr: carlos-ctl remains and reports "carlos-emr is
#      not installed" cleanly
#   7. purge both (clinical data and the material to read it stay)
#
# Run order is 2 before 1 (both need the pre-split state) and 5 before 4
# (both consume the newer carlos-ctl). Inputs, all .deb paths:
#
#   PRESPLIT_EMR   carlos-emr <= 2026.09.0~snapshot24 (ships the CLI itself)
#   SPLIT_EMR      carlos-emr >= 2026.09.0~snapshot25 (Depends: carlos-ctl)
#   CTL_A          the carlos-ctl release SPLIT_EMR's pin names
#   CTL_B          a newer carlos-ctl (case 4/5); built from the same tree
#                  with a higher version is enough
#   SPLIT_EMR_2    optional: a higher-versioned split carlos-emr for case 3;
#                  when unset one is derived from SPLIT_EMR by repacking it
#                  under the next ~snapshot version (dpkg-deb -R/-b), which
#                  exercises the upgrade path without a second build
#   TRANSITIONAL   optional: carlos-emr-eform-renderer_*.deb matching each
#                  carlos-emr (TRANSITIONAL_PRESPLIT / TRANSITIONAL_SPLIT);
#                  needed when the pre-split package pulls it in
#   PROVINCE       on (default) | bc, for the debconf preseed
#   PAUSE_BEFORE_REMOVE=1
#                  after case 3, with the upgraded split pair serving, wait
#                  until $WORK/continue exists before cases 6 and 7 -- the
#                  window for an external browser check against the upgraded
#                  EMR (scripts/deb-login-playwright-checks.js from another
#                  machine or the container host)
#
# The debconf answers are preseeded (server name localhost, self-signed
# TLS, no demonstration data, replace the seeded administrator credential),
# so the run is unattended. The EMR redeploys once per carlos-emr upgrade
# (~2 min each); budget 15-20 minutes.
#
# Exit status: the number of failed assertions.
set -u
: "${PRESPLIT_EMR:?} ${SPLIT_EMR:?} ${CTL_A:?} ${CTL_B:?}"
PROVINCE="${PROVINCE:-on}"
HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="${WORK:-/root/split-matrix}"; mkdir -p "$WORK"
LEDGER=/var/lib/carlos-emr/o19-import/state.json
export DEBIAN_FRONTEND=noninteractive
pass=0; fail=0
ok()  { echo "PASS $1"; pass=$((pass+1)); }
bad() { echo "FAIL $1"; fail=$((fail+1)); }
hdr() { echo; echo "===== $1 ====="; }
ver() { dpkg-query -W -f='${Version}' "$1" 2>/dev/null; }
status() { dpkg-query -W -f='${db:Status-Status}' "$1" 2>/dev/null; }
apt_install() {
    # --no-remove as the documented operator command; the log is kept per case
    local log="$1"; shift
    apt-get install -y --no-remove "$@" > "$log" 2>&1
    local rc=$?
    echo "UPGRADE_RC=$rc" >> "$log"
    return $rc
}
front() { curl -sk -o /dev/null -w '%{http_code}' https://127.0.0.1/carlos/ 2>/dev/null; }
wait_front() { for i in $(seq 1 60); do [ "$(front)" = 200 ] && return 0; sleep 5; done; return 1; }
flyway_count() { mariadb -u root carlos -Nse 'SELECT COUNT(*) FROM flyway_schema_history WHERE success=1' 2>/dev/null; }

preseed() {
    debconf-set-selections <<SEL
carlos-emr carlos-emr/server-name string localhost
carlos-emr carlos-emr/bind-ip string 0.0.0.0
carlos-emr carlos-emr/province select $PROVINCE
carlos-emr carlos-emr/java-heap string 1g
carlos-emr carlos-emr/tls-mode select selfsigned
carlos-emr carlos-emr/reset-seed-admin boolean true
carlos-emr carlos-emr/install-demo-data boolean false
SEL
}

hdr "0. fresh install of the pre-split carlos-emr ($(dpkg-deb -f "$PRESPLIT_EMR" Version))"
apt-get update -qq
preseed
pre_pkgs=("$PRESPLIT_EMR"); [ -n "${TRANSITIONAL_PRESPLIT:-}" ] && pre_pkgs+=("$TRANSITIONAL_PRESPLIT")
if apt_install "$WORK/0-install.log" "${pre_pkgs[@]}"; then ok "pre-split carlos-emr installed"; else bad "pre-split install failed (see $WORK/0-install.log)"; tail -30 "$WORK/0-install.log"; fi
wait_front && ok "front door 200 on the pre-split install" || bad "front door $(front) on the pre-split install"
[ "$(dpkg -S /usr/sbin/carlos-ctl 2>/dev/null | cut -d: -f1)" = carlos-emr ] && ok "/usr/sbin/carlos-ctl belongs to the pre-split carlos-emr" || bad "/usr/sbin/carlos-ctl owner: $(dpkg -S /usr/sbin/carlos-ctl 2>/dev/null)"
carlos-ctl check > "$WORK/0-check.log" 2>&1 && ok "carlos-ctl check passes on the pre-split install" || { bad "carlos-ctl check failed on the pre-split install"; grep -E "FAIL" "$WORK/0-check.log" | head; }
presplit_ver="$(ver carlos-emr)"; flyway_before="$(flyway_count)"
"$HERE/deb-upgrade-baseline.sh" > "$WORK/baseline-presplit.txt"

hdr "2. the new carlos-emr WITHOUT the carlos-ctl file is refused up front"
split_pkgs=("$SPLIT_EMR"); [ -n "${TRANSITIONAL_SPLIT:-}" ] && split_pkgs+=("$TRANSITIONAL_SPLIT")
if apt_install "$WORK/2-refused.log" "${split_pkgs[@]}"; then bad "apt installed the split carlos-emr without carlos-ctl"; else ok "apt refused the split carlos-emr without carlos-ctl (rc $(sed -n 's/^UPGRADE_RC=//p' "$WORK/2-refused.log"))"; fi
grep -qiE "carlos-ctl" "$WORK/2-refused.log" && ok "the refusal names carlos-ctl" || bad "the refusal does not name carlos-ctl"
[ "$(ver carlos-emr)" = "$presplit_ver" ] && ok "carlos-emr still $presplit_ver" || bad "carlos-emr changed to $(ver carlos-emr)"
[ "$(status carlos-emr)" = installed ] && ok "carlos-emr still fully configured" || bad "carlos-emr status: $(status carlos-emr)"
[ -z "$(ver carlos-ctl)" ] && ok "no carlos-ctl package appeared" || bad "carlos-ctl $(ver carlos-ctl) appeared"
[ "$(front)" = 200 ] && ok "the EMR kept serving through the refused transaction" || bad "front door $(front) after the refusal"
[ "$(dpkg -S /usr/sbin/carlos-ctl 2>/dev/null | cut -d: -f1)" = carlos-emr ] && ok "the old CLI is still in place" || bad "/usr/sbin/carlos-ctl owner changed"

hdr "1. pre-split carlos-emr upgraded to the split pair in one transaction"
# the cache the old copy left behind: root ran the tool, so it exists
[ -d /usr/lib/carlos-emr/carlos_ctl/__pycache__ ] || python3 -c 'import compileall; compileall.compile_dir("/usr/lib/carlos-emr/carlos_ctl", quiet=1)'
if apt_install "$WORK/1-upgrade.log" "${split_pkgs[@]}" "$CTL_A"; then ok "split pair installed in one transaction"; else bad "split upgrade failed (see $WORK/1-upgrade.log)"; tail -40 "$WORK/1-upgrade.log"; fi
wait_front || true
PRE="$WORK/baseline-presplit.txt" POST="$WORK/baseline-split.txt" UPGRADE_LOG="$WORK/1-upgrade.log" \
  EXPECT_FLYWAY="${EXPECT_FLYWAY:-$flyway_before}" EXPECT_NEW="${EXPECT_NEW-}" \
  EXPECT_TAG="${EXPECT_TAG:-build.number=$(dpkg-deb -f "$SPLIT_EMR" Version)}" \
  EXPECT_SPLIT=1 EXPECT_CTL="$(dpkg-deb -f "$CTL_A" Version)" \
  "$HERE/deb-upgrade-verify.sh" > "$WORK/1-verify.log" 2>&1
grep -E "^(PASS|FAIL) " "$WORK/1-verify.log"
v_pass=$(grep -c "^PASS " "$WORK/1-verify.log"); v_fail=$(grep -c "^FAIL " "$WORK/1-verify.log")
pass=$((pass+v_pass)); fail=$((fail+v_fail))
grep -q "carlos-ctl finish-install\|carlos-ctl check" "$WORK/1-upgrade.log" || true
# the postinst's verbs ran with the NEW CLI: the configure of carlos-emr happens after carlos-ctl is configured
awk '/Setting up carlos-ctl/{c=NR} /Setting up carlos-emr /{e=NR} END{exit !(c && e && c < e)}' "$WORK/1-upgrade.log" && ok "carlos-ctl was configured before carlos-emr" || bad "configure order: $(grep -n 'Setting up carlos' "$WORK/1-upgrade.log" | tr '\n' ' ')"
split_ver="$(ver carlos-emr)"

hdr "5. a carlos-ctl upgrade during a staged OSCAR 19 import is refused, the ledger untouched"
mkdir -p "$(dirname "$LEDGER")"; chmod 0700 "$(dirname "$LEDGER")"
printf '{"phases":{"stage":{"status":"done"},"etl":{"status":"done"}}}' > "$LEDGER"
ledger_sum="$(sha256sum "$LEDGER" | cut -c1-16)"
ctl_a_ver="$(ver carlos-ctl)"
if apt_install "$WORK/5-refused.log" "$CTL_B"; then bad "apt installed carlos-ctl over an import in progress"; else ok "the carlos-ctl preinst refused the upgrade (rc $(sed -n 's/^UPGRADE_RC=//p' "$WORK/5-refused.log"))"; fi
grep -q "OSCAR 19 import is in progress" "$WORK/5-refused.log" && ok "the refusal explains the import and the remedy" || bad "no explanation in the refusal"
[ "$(sha256sum "$LEDGER" | cut -c1-16)" = "$ledger_sum" ] && ok "the import ledger is untouched" || bad "the ledger changed"
[ "$(ver carlos-ctl)" = "$ctl_a_ver" ] && ok "carlos-ctl still $ctl_a_ver" || bad "carlos-ctl is $(ver carlos-ctl)"
[ "$(status carlos-ctl)" = installed ] && ok "carlos-ctl still fully configured after the refusal" || bad "carlos-ctl status: $(status carlos-ctl)"
carlos-ctl --help >/dev/null 2>&1 && ok "the installed carlos-ctl still runs" || bad "carlos-ctl broken after the refused unpack"
rm -f "$LEDGER"

hdr "4. split pair, carlos-ctl only upgraded"
nrestarts_before="$(systemctl show carlos-emr -p NRestarts --value)"
emr_pid_before="$(systemctl show carlos-emr -p MainPID --value)"
if apt_install "$WORK/4-ctl-only.log" "$CTL_B"; then ok "carlos-ctl upgraded alone"; else bad "carlos-ctl-only upgrade failed"; tail -20 "$WORK/4-ctl-only.log"; fi
[ "$(ver carlos-ctl)" = "$(dpkg-deb -f "$CTL_B" Version)" ] && ok "carlos-ctl is now $(ver carlos-ctl)" || bad "carlos-ctl is $(ver carlos-ctl)"
[ "$(ver carlos-emr)" = "$split_ver" ] && ok "carlos-emr untouched ($split_ver)" || bad "carlos-emr changed"
[ "$(systemctl show carlos-emr -p MainPID --value)" = "$emr_pid_before" ] && [ "$(systemctl show carlos-emr -p NRestarts --value)" = "$nrestarts_before" ] && ok "the EMR was not restarted by a CLI-only upgrade" || bad "the EMR restarted during a CLI-only upgrade"
[ "$(front)" = 200 ] && ok "front door 200 after the CLI-only upgrade" || bad "front door $(front)"
carlos-ctl check > "$WORK/4-check.log" 2>&1 && ok "carlos-ctl check passes with the newer CLI" || { bad "carlos-ctl check failed with the newer CLI"; grep FAIL "$WORK/4-check.log" | head; }
grep -q "^  carlos-ctl $(ver carlos-ctl)\$" "$WORK/4-check.log" && ok "check names the new carlos-ctl version" || bad "check does not name carlos-ctl $(ver carlos-ctl)"
[ ! -d /usr/lib/carlos-ctl/carlos_ctl/__pycache__ ] && ok "no bytecode written under /usr/lib/carlos-ctl" || bad "bytecode cache appeared under /usr/lib/carlos-ctl"
carlos-ctl o19-preflight --write-standalone "$WORK/o19_preflight.py" > "$WORK/4-standalone.log" 2>&1 && python3 "$WORK/o19_preflight.py" --help >/dev/null 2>&1 && ok "--write-standalone produces a runnable assessment script from the shipped manifest" || { bad "--write-standalone failed"; cat "$WORK/4-standalone.log"; }

hdr "3. split pair, carlos-emr only upgraded"
if [ -z "${SPLIT_EMR_2:-}" ]; then
    # derive a higher version by repacking: the postinst upgrade path, the
    # Depends floor and the takeover are what this case is about
    SPLIT_EMR_2="$WORK/carlos-emr-next.deb"
    rm -rf "$WORK/repack"; dpkg-deb -R "$SPLIT_EMR" "$WORK/repack"
    next="$(dpkg-deb -f "$SPLIT_EMR" Version | sed -E 's/~snapshot([0-9]+)$/~snapshot\1/')"
    n="$(printf '%s' "$next" | sed -n 's/.*~snapshot\([0-9]*\)$/\1/p')"
    if [ -n "$n" ]; then next="${next%~snapshot*}~snapshot$((n+1))"; else next="$next+matrix1"; fi
    sed -i "s/^Version: .*/Version: $next/" "$WORK/repack/DEBIAN/control"
    dpkg-deb -b "$WORK/repack" "$SPLIT_EMR_2" >/dev/null
    rm -rf "$WORK/repack"
fi
"$HERE/deb-upgrade-baseline.sh" > "$WORK/baseline-before-emr-only.txt"
emr2_pkgs=("$SPLIT_EMR_2"); [ -n "${TRANSITIONAL_SPLIT_2:-}" ] && emr2_pkgs+=("$TRANSITIONAL_SPLIT_2")
if apt_install "$WORK/3-emr-only.log" "${emr2_pkgs[@]}"; then ok "carlos-emr upgraded alone to $(ver carlos-emr)"; else bad "carlos-emr-only upgrade failed"; tail -30 "$WORK/3-emr-only.log"; fi
wait_front || true
PRE="$WORK/baseline-before-emr-only.txt" POST="$WORK/baseline-after-emr-only.txt" UPGRADE_LOG="$WORK/3-emr-only.log" \
  EXPECT_FLYWAY="${EXPECT_FLYWAY:-$flyway_before}" EXPECT_NEW="" \
  EXPECT_TAG="build.job=carlos-emr-deb" EXPECT_SPLIT=1 EXPECT_CTL="$(ver carlos-ctl)" \
  "$HERE/deb-upgrade-verify.sh" > "$WORK/3-verify.log" 2>&1
grep -E "^(PASS|FAIL) " "$WORK/3-verify.log" | grep -E "carlos-ctl|service active|front door|preserved|migration" | head -12
v_pass=$(grep -c "^PASS " "$WORK/3-verify.log"); v_fail=$(grep -c "^FAIL " "$WORK/3-verify.log")
pass=$((pass+v_pass)); fail=$((fail+v_fail))

if [ "${PAUSE_BEFORE_REMOVE:-0}" = 1 ]; then
    echo; echo "PAUSED: the upgraded split pair is serving; create $WORK/continue to go on to cases 6 and 7"
    while [ ! -e "$WORK/continue" ]; do sleep 5; done
fi

hdr "6. apt remove carlos-emr: carlos-ctl remains and says so"
docs_before="$(find /var/lib/carlos-emr/CarlosDocument -type f 2>/dev/null | wc -l)"
apt-get remove -y carlos-emr carlos-emr-drugref carlos-emr-eform-renderer > "$WORK/6-remove.log" 2>&1 && ok "carlos-emr removed" || { bad "remove failed"; tail -20 "$WORK/6-remove.log"; }
[ "$(status carlos-ctl)" = installed ] && ok "carlos-ctl remains installed" || bad "carlos-ctl status: $(status carlos-ctl)"
out="$(carlos-ctl check 2>&1)"; rc=$?
[ "$rc" -ne 0 ] && echo "$out" | grep -q "carlos-emr is not installed" && ok "carlos-ctl check reports 'carlos-emr is not installed' (rc $rc)" || bad "check without carlos-emr: rc $rc: $(echo "$out" | head -3)"
echo "$out" | grep -qi traceback && bad "a traceback leaked" || ok "no traceback"
out="$(carlos-ctl status 2>&1)"; echo "$out" | grep -q "carlos-emr is not installed" && ok "every verb refuses the same way (status)" || bad "status without carlos-emr: $(echo "$out" | head -2)"
carlos-ctl --help >/dev/null 2>&1 && ok "--help still answers" || bad "--help failed without carlos-emr"
[ "$(find /var/lib/carlos-emr/CarlosDocument -type f 2>/dev/null | wc -l)" = "$docs_before" ] && ok "the document store survived remove" || bad "document store changed on remove"
mariadb -u root carlos -Nse 'SELECT 1' >/dev/null 2>&1 && ok "the database survived remove" || bad "database gone after remove"

hdr "7. purge both"
apt-get purge -y carlos-emr carlos-emr-drugref carlos-emr-eform-renderer carlos-ctl > "$WORK/7-purge.log" 2>&1 && ok "purged" || { bad "purge failed"; tail -20 "$WORK/7-purge.log"; }
[ -z "$(ver carlos-ctl)" ] && [ -z "$(ver carlos-emr)" ] && ok "dpkg knows neither package" || bad "left: carlos-emr=$(ver carlos-emr) carlos-ctl=$(ver carlos-ctl)"
[ ! -e /usr/lib/carlos-ctl ] && ok "/usr/lib/carlos-ctl is gone" || bad "/usr/lib/carlos-ctl remains: $(ls -R /usr/lib/carlos-ctl | head -5 | tr '\n' ' ')"
[ ! -e /usr/sbin/carlos-ctl ] && [ ! -e /usr/sbin/carlosctl ] && ok "the command and its alias are gone" || bad "/usr/sbin/carlos-ctl or carlosctl remains"
[ "$(find /var/lib/carlos-emr/CarlosDocument -type f 2>/dev/null | wc -l)" = "$docs_before" ] && ok "the document store survived purge" || bad "document store changed on purge"
mariadb -u root carlos -Nse 'SELECT 1' >/dev/null 2>&1 && ok "the database survived purge" || bad "database gone after purge"

echo; echo "== $pass passed, $fail failed =="
exit "$fail"
