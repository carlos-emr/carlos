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
# Everything here is either a count, a hash or a flag: no PHI leaves the host.
q() { mariadb -u root carlos -Nse "$1" 2>/dev/null | tr '\n' ',' | sed 's/,$//'; }
echo "pkg.carlos-emr=$(dpkg-query -W -f='${Version}' carlos-emr 2>/dev/null)"
echo "pkg.drugref=$(dpkg-query -W -f='${Version}' carlos-emr-drugref 2>/dev/null)"
echo "pkg.renderer=$(dpkg-query -W -f='${Version}' carlos-emr-eform-renderer 2>/dev/null)"
echo "pkg.carlos-ctl=$(dpkg-query -W -f='${Version}' carlos-ctl 2>/dev/null)"
echo "ctl.owner=$(dpkg -S /usr/sbin/carlos-ctl 2>/dev/null | cut -d: -f1)"
echo "service.active=$(systemctl is-active carlos-emr 2>/dev/null)"
echo "service.nrestarts=$(systemctl show carlos-emr -p NRestarts --value 2>/dev/null)"
echo "flyway.count=$(q 'SELECT COUNT(*) FROM flyway_schema_history WHERE success=1')"
echo "flyway.versions=$(q 'SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank')"
echo "flyway.failed=$(q 'SELECT COUNT(*) FROM flyway_schema_history WHERE success=0')"
for t in demographic appointment prescription drugs allergies consultationRequests casemgmt_note preventions hl7TextMessage document tickler; do echo "rows.$t=$(q "SELECT COUNT(*) FROM $t")"; done
echo "admin.hash=$(q "SELECT password FROM security WHERE user_name='carlosdoc'")"
echo "admin.forceReset=$(q "SELECT forcePasswordReset FROM security WHERE user_name='carlosdoc'")"
echo "admin.pin=$(q "SELECT pin FROM security WHERE user_name='carlosdoc'" | cut -c1-2)xx"
echo "consultServices.active=$(q "SELECT COUNT(*) FROM consultationServices WHERE active='1'")"
echo "prescription.sigIds=$(q 'SELECT CONCAT(IFNULL(digital_signature_id,"NULL"),":",COUNT(*)) FROM prescription GROUP BY digital_signature_id')"
echo "consultReq.nullStatus=$(q 'SELECT COUNT(*) FROM consultationRequests WHERE status IS NULL')"
for f in carlos-emr.env carlos.properties backup.env; do echo "cfg.$f.sha=$(sha256sum /etc/carlos-emr/$f 2>/dev/null | cut -c1-16)"; done
echo "cfg.tls.mode=$(cat /etc/carlos-emr/tls/mode 2>/dev/null)"
echo "cfg.tls.cert.sha=$(sha256sum /etc/carlos-emr/tls/fullchain.pem 2>/dev/null | cut -c1-16)"
echo "cfg.province=$(sed -n 's/^CARLOS_PROVINCE=//p' /etc/carlos-emr/carlos-emr.env | tr -d '"')"
echo "cfg.tz=$(sed -n 's/^CARLOS_TZ=//p' /etc/carlos-emr/carlos-emr.env | tr -d '"')"
echo "cfg.dbname=$(sed -n 's/^CARLOS_DB_NAME=//p' /etc/carlos-emr/carlos-emr.env | tr -d '"')"
echo "cfg.consultSig=$(grep -E '^consultation_signature_enabled=' /etc/carlos-emr/carlos.properties)"
echo "cfg.rxFax=$(grep -E '^rx_fax_enabled=' /etc/carlos-emr/carlos.properties)"
echo "cfg.initialAdminTxt=$([ -e /etc/carlos-emr/initial-admin.txt ] && echo present || echo absent)"
for s in .consult-signature-default-migrated .db-name-default-migrated .first-configure-pending .seed-credential-live; do echo "sentinel.$s=$([ -e /var/lib/carlos-emr/$s ] && echo yes || echo no)"; done
echo "docs.store=$(ls -d /var/lib/carlos-emr/CarlosDocument 2>/dev/null && echo ok || echo missing)"
echo "docs.files=$(find /var/lib/carlos-emr/CarlosDocument -type f 2>/dev/null | wc -l)"
echo "war.buildtag=$(grep -E '^build\.(version|job|number)=' /usr/share/carlos-emr/webapp/carlos/WEB-INF/classes/carlos-build.properties 2>/dev/null | tr '\n' ' ')"
echo "http.front=$(curl -sk -o /dev/null -w '%{http_code}' https://127.0.0.1/carlos/ 2>/dev/null)"
