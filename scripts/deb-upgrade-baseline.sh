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
# deb-upgrade-baseline.sh — a stable key=value snapshot of a packaged CARLOS
# install, diffable before and after a package upgrade. Run as root on the host
# (MariaDB root over the unix socket). Pair with deb-upgrade-verify.sh:
#
#   ./scripts/deb-upgrade-baseline.sh > /root/baseline-before.txt
#   apt-get install ./carlos-emr_<new>.deb ...          # the upgrade
#   PRE=/root/baseline-before.txt ./scripts/deb-upgrade-verify.sh
#
# DB_NAME and ADMIN_USER default to carlos/carlosdoc; MYSQL_SOCKET is optional.
# Output format 2 uses digests of credential fields, never an authentication hash
# or a PIN prefix. Capture pre/post with the same script version (umask 077).
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
source "$HERE/lib/deb-upgrade-common.sh"
emit() { printf '%s=%s\n' "$1" "$2"; }
query() {
    local value
    value=$(db_query "$2") || return 1
    emit "$1" "${value//$'\n'/,}"
}
file_hash() {
    local value
    value=$(sha256sum < "$2") || return 1
    emit "$1" "${value:0:16}"
}
admin_count=$(db_query "SELECT COUNT(*) FROM security WHERE user_name=$ADMIN_SQL")
[[ $admin_count == 1 ]] || { echo 'ERROR: expected exactly one selected administrator row.' >&2; exit 1; }
emit baseline.format 2
for entry in 'carlos-emr:carlos-emr' 'drugref:carlos-emr-drugref' 'renderer:carlos-emr-eform-renderer' 'carlos-ctl:carlos-ctl'; do
    package=${entry#*:}
    if ! value=$(dpkg-query -W -f='${Version}' "$package" 2>/dev/null); then
        [[ $package == carlos-ctl ]] || exit 1
        value=''
    fi
    emit "pkg.${entry%%:*}" "$value"
done
value=$(dpkg -S /usr/sbin/carlos-ctl | cut -d: -f1); emit ctl.owner "$value"
value=$(systemctl is-active carlos-emr) || [[ -n $value ]]; emit service.active "$value"
value=$(systemctl show carlos-emr -p NRestarts --value); emit service.nrestarts "$value"
query flyway.count 'SELECT COUNT(*) FROM flyway_schema_history WHERE success=1'
query flyway.versions 'SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank'
query flyway.failed 'SELECT COUNT(*) FROM flyway_schema_history WHERE success=0'
for t in demographic appointment prescription drugs allergies consultationRequests casemgmt_note preventions hl7TextMessage document tickler; do
    query "rows.$t" "SELECT COUNT(*) FROM $t"
done
query admin.hash "SELECT SHA2(CONCAT('password:',IFNULL(password,'<NULL>')),256) FROM security WHERE user_name=$ADMIN_SQL"
query admin.forceReset "SELECT forcePasswordReset FROM security WHERE user_name=$ADMIN_SQL"
query admin.pin "SELECT SHA2(CONCAT('pin:',IFNULL(pin,'<NULL>')),256) FROM security WHERE user_name=$ADMIN_SQL"
query consultServices.active "SELECT COUNT(*) FROM consultationServices WHERE active='1'"
query prescription.sigIds 'SELECT CONCAT(IFNULL(digital_signature_id,"NULL"),":",COUNT(*)) FROM prescription GROUP BY digital_signature_id ORDER BY digital_signature_id'
query consultReq.nullStatus 'SELECT COUNT(*) FROM consultationRequests WHERE status IS NULL'
for f in carlos-emr.env carlos.properties backup.env; do file_hash "cfg.$f.sha" "$CARLOS_ETC_DIR/$f"; done
value=$(cat "$CARLOS_ETC_DIR/tls/mode"); emit cfg.tls.mode "$value"
file_hash cfg.tls.cert.sha "$CARLOS_ETC_DIR/tls/fullchain.pem"
for mapping in 'province:CARLOS_PROVINCE' 'tz:CARLOS_TZ' 'dbname:CARLOS_DB_NAME'; do
    value=$(sed -n "s/^${mapping#*:}=//p" "$CARLOS_ETC_DIR/carlos-emr.env" | tr -d '"')
    [[ -n $value ]] || { echo "ERROR: missing ${mapping#*:} in packaged environment." >&2; exit 1; }
    emit "cfg.${mapping%%:*}" "$value"
done
value=$(grep -E '^consultation_signature_enabled=' "$CARLOS_ETC_DIR/carlos.properties" || true); emit cfg.consultSig "$value"
value=$(grep -E '^rx_fax_enabled=' "$CARLOS_ETC_DIR/carlos.properties" || true); emit cfg.rxFax "$value"
emit cfg.initialAdminTxt "$([ -e "$CARLOS_ETC_DIR/initial-admin.txt" ] && echo present || echo absent)"
for s in .consult-signature-default-migrated .health-tracker-default-migrated .db-name-default-migrated .first-configure-pending .seed-credential-live; do
    emit "sentinel.$s" "$([ -e "$CARLOS_STATE_DIR/$s" ] && echo yes || echo no)"
done
emit docs.store ok
value=$(find "$DOC_DIR" -type f | wc -l); emit docs.files "$value"
value=$(grep -E '^build\.(version|job|number)=' "$CARLOS_SHARE_DIR/webapp/carlos/WEB-INF/classes/carlos-build.properties" | tr '\n' ' '); emit war.buildtag "$value"
value=$(curl -sk --max-time 10 -o /dev/null -w '%{http_code}' "$FRONT_URL"); emit http.front "$value"
