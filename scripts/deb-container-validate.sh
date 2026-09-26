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
# deb-container-validate.sh — install freshly built CARLOS .debs into a disposable
# Ubuntu 26.04 Docker container running systemd, then drive Playwright checks
# against the packaged front door. The Docker counterpart of the LXD runbook in
# docs/ui-tests/deb-install-validation.md, for hosts with Docker but no LXD.
#
#   DEB_DIR=/path/with/debs scripts/deb-container-validate.sh up
#   scripts/deb-container-validate.sh install
#   scripts/deb-container-validate.sh check lab-line-break-rendering lab-acknowledge
#   scripts/deb-container-validate.sh tier smoke
#   scripts/deb-container-validate.sh down
#
# Environment:
#   DEB_DIR     directory holding carlos-emr_*_amd64.deb, carlos-emr-drugref_*_all.deb
#               and carlos-emr-eform-renderer_*_all.deb (default: the repo's parent,
#               where dpkg-buildpackage writes them). Mounted at /debs; screenshots
#               and JUnit reports land there.
#   CONTAINER   container name (default carlos-deb-validate)
#   APT_PROXY   optional http(s) proxy for apt inside the image build; when set, apt
#               sources are switched to https and APT_CA (a PEM bundle) is trusted.
#
# WHY THESE CONTAINER SETTINGS (each one cost a failed run to find):
#   - systemd is PID 1. carlos-emr.postinst starts MariaDB and the services only when
#     /run/systemd/system exists; without it provisioning is silently deferred.
#   - /usr/sbin/policy-rc.d is removed. Docker's Ubuntu image ships one that exits
#     101, so every service start in postinst is refused and MariaDB "does not
#     answer within 60 seconds".
#   - cgroup v2. Current systemd will not boot on a cgroup v1 host, so on a v1 host a
#     private cgroup2 hierarchy is mounted and bound over the container's
#     /sys/fs/cgroup.
#   - --network host, so the front door is https://127.0.0.1/carlos and apt can use a
#     host-local proxy. The container's nginx/Tomcat/MariaDB bind host ports: run it
#     on a disposable host.
#   - Precompiled JSPs. The package ships compiled JSP classes in Tomcat's work dir,
#     so editing a JSP under /usr/share/carlos-emr/webapp changes nothing until the
#     matching *_jsp.* files are removed AND carlos-emr is restarted.
set -euo pipefail

REPO="$(cd "$(dirname "$0")/.." && pwd)"
DEB_DIR="$(cd "${DEB_DIR:-$REPO/..}" && pwd)"
CONTAINER="${CONTAINER:-carlos-deb-validate}"
IMAGE="${IMAGE:-carlos-deb-validate:26.04}"
CGROUP2_ROOT="${CGROUP2_ROOT:-/run/carlos-deb-validate-cgroup2}"

build_image() {
  local ctx
  ctx="$(mktemp -d)"
  if [ -n "${APT_PROXY:-}" ]; then
    cp "${APT_CA:?APT_CA must name a PEM bundle when APT_PROXY is set}" "$ctx/apt-ca.crt"
  else
    : > "$ctx/apt-ca.crt"
  fi
  cat > "$ctx/Dockerfile" <<'DOCKERFILE'
FROM ubuntu:26.04
ARG APT_PROXY=
COPY apt-ca.crt /usr/local/share/ca-certificates/apt-ca.crt
RUN if [ -n "$APT_PROXY" ]; then \
      printf 'Acquire::https::Proxy "%s";\nAcquire::http::Proxy "%s";\nAcquire::https::CaInfo "/usr/local/share/ca-certificates/apt-ca.crt";\n' \
        "$APT_PROXY" "$APT_PROXY" > /etc/apt/apt.conf.d/99proxy; \
      sed -i 's|http://|https://|g' /etc/apt/sources.list.d/*.sources; \
    fi \
 && apt-get update \
 && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
      systemd systemd-sysv dbus ca-certificates curl poppler-utils nodejs npm iproute2 procps \
 && update-ca-certificates \
 && rm -f /usr/sbin/policy-rc.d \
 && systemctl mask systemd-modules-load.service systemd-logind.service getty.target \
      console-getty.service systemd-udevd.service systemd-networkd.service systemd-resolved.service
ENV container=docker
STOPSIGNAL SIGRTMIN+3
CMD ["/sbin/init"]
DOCKERFILE
  docker build --network host --build-arg "APT_PROXY=${APT_PROXY:-}" -t "$IMAGE" "$ctx"
  rm -rf "$ctx"
}

cgroup_args() {
  if [ "$(stat -fc %T /sys/fs/cgroup)" = "cgroup2fs" ]; then
    echo "--cgroupns=private"
    return
  fi
  mkdir -p "$CGROUP2_ROOT"
  mountpoint -q "$CGROUP2_ROOT" || mount -t cgroup2 none "$CGROUP2_ROOT"
  mkdir -p "$CGROUP2_ROOT/$CONTAINER"
  echo "-v $CGROUP2_ROOT/$CONTAINER:/sys/fs/cgroup:rw"
}

up() {
  docker image inspect "$IMAGE" >/dev/null 2>&1 || build_image
  # shellcheck disable=SC2046 # cgroup_args deliberately expands to separate words
  docker run -d --name "$CONTAINER" --privileged --network host \
    --tmpfs /run --tmpfs /run/lock $(cgroup_args) \
    -v "$DEB_DIR:/debs" -v "$REPO:/root/carlos:ro" "$IMAGE" >/dev/null
  for _ in $(seq 1 30); do
    state="$(docker exec "$CONTAINER" systemctl is-system-running 2>/dev/null || true)"
    case "$state" in running|degraded) break ;; esac
    sleep 2
  done
  echo "systemd: $state"
}

# Everything below runs inside the container (bash -s), as root.
in_container() {
  docker exec -i "$CONTAINER" bash -s -- "$@"
}

install() {
  in_container <<'INSTALL'
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive
debconf-set-selections <<'PRESEED'
carlos-emr carlos-emr/server-name string localhost
carlos-emr carlos-emr/bind-ip string 0.0.0.0
carlos-emr carlos-emr/province select on
carlos-emr carlos-emr/tls-mode select selfsigned
carlos-emr carlos-emr/acme-email string
carlos-emr carlos-emr/java-heap string 2g
carlos-emr carlos-emr/reset-seed-admin boolean true
carlos-emr carlos-emr/install-demo-data boolean true
PRESEED
apt-get update -q
apt-get install -y --no-remove /debs/carlos-emr_*_amd64.deb /debs/carlos-emr-drugref_*_all.deb \
  /debs/carlos-emr-eform-renderer_*_all.deb
for _ in $(seq 1 90); do
  code="$(curl -sk -o /dev/null -w '%{http_code}' https://127.0.0.1/carlos/ || true)"
  [ "$code" = 200 ] && break
  sleep 10
done
carlos-ctl check
[ -d /root/node_modules/playwright ] || (cd /root && npm init -y >/dev/null && npm install --save-exact playwright@1.60.0)
INSTALL
}

# Exports the runbook's environment contract, performs the mandatory first-login
# reset once, then runs whatever follows.
run_checks() {
  in_container "$@" <<'RUN'
set -uo pipefail
cd /root/carlos
export BASE_URL=https://127.0.0.1/carlos CHROME_PATH=/usr/lib/carlos-emr/chromium/chrome
export MYSQL_HOST=localhost MYSQL_USER=root MYSQL_PASSWORD=dummy MYSQL_DATABASE=carlos
export SCREENSHOT_DIR=/debs/screens LAB_BREAK_SCREENSHOT_DIR=/debs/screens
export EDOC_NAV_DOCUMENT_STORE=/var/lib/carlos-emr/CarlosDocument/carlos/document
export TEST_USER="$(sed -n 's/^ *user: *//p' /etc/carlos-emr/initial-admin.txt)"
export TEST_PIN="$(sed -n 's/^ *PIN: *//p' /etc/carlos-emr/initial-admin.txt)"
if [ ! -f /root/.carlos-first-login-reset ]; then
  TEST_PASSWORD="$(sed -n 's/^ *password: *//p' /etc/carlos-emr/initial-admin.txt)" \
    RESET_PASSWORD='Carlos2026!Verify' DRUGREF_UPDATE_REQUIRE_STATUS=true \
    node scripts/drugref-update-playwright-checks.js || { echo "FAIL mandatory first-login reset"; exit 1; }
  touch /root/.carlos-first-login-reset
fi
export TEST_PASSWORD='Carlos2026!Verify'
export TEST_PASSWORD_HASH="$(mariadb -u root carlos -Nse "SELECT password FROM security WHERE user_name='${TEST_USER}' LIMIT 1")"
if [ "${1:-}" = "--tier" ]; then
  exec node scripts/run-playwright-suite.js --tier "$2" --junit "/debs/playwright-$2.xml"
fi
rc=0
for check in "$@"; do
  echo "=== $check"
  node "scripts/${check}-playwright-checks.js" || rc=1
done
exit $rc
RUN
}

case "${1:-}" in
  image)   build_image ;;
  up)      up ;;
  install) install ;;
  check)   shift; run_checks "$@" ;;
  tier)    run_checks --tier "${2:?tier name}" ;;
  down)    docker rm -f "$CONTAINER" >/dev/null 2>&1 || true
           rmdir "$CGROUP2_ROOT/$CONTAINER" 2>/dev/null || true ;;
  *)       sed -n '13,24p' "$0"; exit 2 ;;
esac
