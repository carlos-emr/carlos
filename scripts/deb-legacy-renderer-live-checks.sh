#!/bin/bash
# Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later.
# Run only as root on a disposable installed DEB VM. Restarts the application.
# Exercises the postinst recovery path without a legacy package's prerm stopping
# the driver first. The fixture deliberately refuses manual stop requests.
set -euo pipefail
[ "${CARLOS_DISPOSABLE_VM:-}" = true ] || { echo 'Set CARLOS_DISPOSABLE_VM=true on the validation VM' >&2; exit 2; }
[ "$(id -u)" = 0 ] || { echo 'Run as root on the validation VM' >&2; exit 2; }
unit=/etc/systemd/system/carlos-emr-chromedriver.service
home=/var/lib/carlos-emr/render
marker=/var/lib/carlos-emr/.legacy-renderer-live
if systemctl cat carlos-emr-chromedriver.service >/dev/null 2>&1 || [ -e "$unit" ] || [ -L "$unit" ] || [ -e "$home" ] || [ -e "$marker" ]; then
    echo 'A legacy renderer or recovery state already exists; refusing to replace it' >&2
    exit 2
fi
cleanup() {
    local result=$?
    trap - EXIT
    if [ -f "$unit" ]; then
        sed -i 's/RefuseManualStop=yes/RefuseManualStop=no/' "$unit"
        systemctl daemon-reload
        systemctl stop carlos-emr-chromedriver.service || result=1
        rm -f "$unit"
        systemctl daemon-reload
    fi
    # Reconfigure through the supported recovery path, including marker removal.
    DEBIAN_FRONTEND=noninteractive dpkg-reconfigure -f noninteractive carlos-emr || result=1
    if [ -e "$marker" ] || [ -e "$home" ] || ! systemctl is-active --quiet carlos-emr-render-browser.service; then
        echo 'FAIL legacy renderer recovery did not restore the new renderer' >&2
        result=1
    fi
    [ "$result" = 0 ] && echo 'PASS legacy renderer refuses stop, dependency start is vetoed, and reconfiguration recovers'
    exit "$result"
}
trap cleanup EXIT
install -d -o carlos-render -g carlos-render -m 700 "$home"
printf '%s\n' 'owned promotion review fixture' > "$home/pr3929-sentinel"
cat > "$unit" <<'UNIT'
[Unit]
Description=Disposable legacy renderer stop-failure fixture
RefuseManualStop=yes
[Service]
Type=simple
ExecStart=/usr/bin/sleep infinity
UNIT
systemctl daemon-reload
systemctl stop carlos-emr-render-browser.service
systemctl start carlos-emr-chromedriver.service
DEBIAN_FRONTEND=noninteractive dpkg-reconfigure -f noninteractive carlos-emr
systemctl is-active --quiet carlos-emr-chromedriver.service
test -f "$home/pr3929-sentinel"
test -f "$marker"
# The package has restarted the EMR, whose Wants pulls in the new renderer.
# An explicit start must be vetoed as well, rather than competing for the port.
systemctl start carlos-emr-render-browser.service
if systemctl is-active --quiet carlos-emr-render-browser.service; then
    echo 'FAIL new renderer started while the old driver refused to stop' >&2
    exit 1
fi
test "$(systemctl show carlos-emr-render-browser.service -p ConditionResult --value)" = no
