#!/bin/bash
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
# Invoked only by the disposable upgrade canary after its browser has closed.
set -euo pipefail
[[ ${CARLOS_DISPOSABLE_HOST:-} == true ]] || exit 2
umask 077
cd /root/upgrade-target
mapfile -t emrs < <(find . -maxdepth 1 -name 'carlos-emr_*_amd64.deb')
[[ ${#emrs[@]} == 1 ]] || exit 2
old=$(dpkg-query -W -f='${Version}' carlos-emr)
new=$(dpkg-deb -f "${emrs[0]}" Version)
dpkg --compare-versions "$new" gt "$old" || { echo 'Target must be newer than the installed EMR package.' >&2; exit 2; }
# Extract before installation: deriving the expected identity from the installed
# application after the upgrade could accept the old application by accident.
mkdir identity
dpkg-deb --fsys-tarfile "${emrs[0]}" | tar -x -C identity \
  ./usr/share/carlos-emr/webapp/carlos/WEB-INF/classes/carlos-build.properties
export TARGET_BUILD_PROPERTIES=/root/upgrade-target/identity/usr/share/carlos-emr/webapp/carlos/WEB-INF/classes/carlos-build.properties
export PRE=/root/upgrade-before.txt POST=/root/upgrade-after.txt UPGRADE_LOG=/root/upgrade.log
/root/carlos/scripts/deb-upgrade-baseline.sh > "$PRE"
set +e
DEBIAN_FRONTEND=noninteractive apt-get install -y --no-remove ./*.deb > "$UPGRADE_LOG" 2>&1
rc=$?
set -e
printf '\nUPGRADE_RC=%s\n' "$rc" >> "$UPGRADE_LOG"
[[ $rc == 0 ]] || { echo 'Upgrade apt transaction failed; see /root/upgrade.log.' >&2; exit "$rc"; }
/root/carlos/scripts/deb-upgrade-verify.sh
