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
# deb-docker-validation.sh -- the Docker variant of
# docs/ui-tests/deb-install-validation.md, as one command. It installs the
# CARLOS .debs into a disposable Ubuntu 26.04 systemd container, stages the
# runbook's fixtures, performs the mandatory first-login reset, writes the
# suite environment, and runs the Playwright suite and the server-log audit
# inside the container (database-backed checks need the MariaDB unix socket).
#
# WHY A CONTAINER NEEDS CARE. The packages need systemd as PID 1, and the
# systemd in Ubuntu 26.04 no longer boots on a cgroup-v1 hierarchy. On a
# hybrid host (cgroup v1 controllers plus a cgroup2 mount at
# /sys/fs/cgroup/unified) the unified hierarchy is bound over /sys/fs/cgroup;
# a pure cgroup-v2 host needs nothing; a pure v1 host is refused. The image's
# /usr/sbin/policy-rc.d is removed, or the maintainer scripts cannot start
# MariaDB and nginx and the install stops half-provisioned.
#
# WHY HOST NETWORKING. nginx redirects to https://$host$request_uri without a
# port, so the front door must really be on :443; and an apt/npm proxy that
# listens on the host loopback is only reachable from the host's namespace.
# The container therefore binds 80, 443, 3306, 18080 and 9515 on THIS host,
# so one validation container runs at a time (validate provinces in turn).
#
# DISPOSABLE HOSTS ONLY. The container is --privileged, uses host networking,
# loads publicly-known demonstration data and known credentials. The script
# refuses to run unless CARLOS_DISPOSABLE_HOST=true.
#
# Usage:
#   CARLOS_DISPOSABLE_HOST=true DEBS_DIR=/path/to/debs scripts/deb-docker-validation.sh up
#   CARLOS_DISPOSABLE_HOST=true scripts/deb-docker-validation.sh suite --tier smoke
#   CARLOS_DISPOSABLE_HOST=true scripts/deb-docker-validation.sh audit
#   CARLOS_DISPOSABLE_HOST=true scripts/deb-docker-validation.sh down
#
# Commands:
#   up            build the image, start the container, install the debs,
#                 require `carlos-ctl check` to pass, stage the fixtures,
#                 perform the first-login reset and write /root/suite-env.sh
#   suite [ARGS]  run scripts/run-playwright-suite.js ARGS in the container,
#                 limited to the installed province unless ARGS name one
#                 (default: --tier smoke); fails if carlos-emr restarted
#                 during the run; the JUnit report is copied to LOG_DIR
#   audit [ARGS]  run scripts/deb-server-log-audit.sh ARGS in the container
#   shell         interactive shell in the container
#   down          remove the container (the image is kept)
#   print-preseed print the debconf preseed this run would use
#   cgroup-mode   print how the host's cgroup hierarchy would be mounted
#
# Environment:
#   DEBS_DIR            directory holding carlos-emr_*_amd64.deb,
#                       carlos-emr-drugref_*_all.deb and carlos-ctl_*_all.deb
#                       (required by `up`; exactly one of each)
#   CARLOS_PROVINCE     on | bc | other (default on)
#   INSTALL_DEMO_DATA   true | false (default true; the suite needs it)
#   RESET_PASSWORD      password installed by the first-login reset
#                       (default Carlos2026!Verify)
#   CONTAINER           container name (default carlos-deb-validation)
#   IMAGE               image tag (default carlos-deb-validation:26.04)
#   BASE_IMAGE          default ubuntu:26.04
#   PLAYWRIGHT_VERSION  default 1.60.0 (runbook section 5; do not run
#                       `playwright install`: the package ships Chromium)
#   BUILD_PROXY         optional http(s) proxy URL for apt and npm while
#                       building the image and installing packages
#   BUILD_CA_BUNDLE     optional PEM bundle to trust for that proxy
#   APT_FORCE_HTTPS     true rewrites the image's apt sources to https://
#                       (for an egress proxy that only tunnels CONNECT)
#   CONTAINER_CPUS      optional --cpus limit for the container (on a host that
#                       also builds, so the browser checks are not starved)
#   LOG_DIR             host directory for logs and reports
#                       (default target/deb-docker-validation, git-ignored)
#   CGROUP_ROOT         override /sys/fs/cgroup (tests only)
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CARLOS_PROVINCE="${CARLOS_PROVINCE:-on}"
INSTALL_DEMO_DATA="${INSTALL_DEMO_DATA:-true}"
RESET_PASSWORD="${RESET_PASSWORD:-Carlos2026!Verify}"
CONTAINER="${CONTAINER:-carlos-deb-validation}"
IMAGE="${IMAGE:-carlos-deb-validation:26.04}"
BASE_IMAGE="${BASE_IMAGE:-ubuntu:26.04}"
PLAYWRIGHT_VERSION="${PLAYWRIGHT_VERSION:-1.60.0}"
BUILD_PROXY="${BUILD_PROXY:-}"
BUILD_CA_BUNDLE="${BUILD_CA_BUNDLE:-}"
APT_FORCE_HTTPS="${APT_FORCE_HTTPS:-false}"
CONTAINER_CPUS="${CONTAINER_CPUS:-}"
LOG_DIR="${LOG_DIR:-$REPO_ROOT/target/deb-docker-validation}"
CGROUP_ROOT="${CGROUP_ROOT:-/sys/fs/cgroup}"

die() { echo "deb-docker-validation: $*" >&2; exit 1; }
say() { echo "deb-docker-validation: $*"; }

usage() { sed -n '/^# Usage:/,/^set -euo pipefail/p' "${BASH_SOURCE[0]}" | sed '$d; s/^# \{0,1\}//'; }

case "$CARLOS_PROVINCE" in on|bc|other) ;; *) die "CARLOS_PROVINCE must be on, bc or other (got '$CARLOS_PROVINCE')" ;; esac
case "$INSTALL_DEMO_DATA" in true|false) ;; *) die "INSTALL_DEMO_DATA must be true or false" ;; esac
# Both values are written into shell text that runs as root in the container
# (and RESET_PASSWORD into /root/suite-env.sh), so refuse anything that quoting
# could not carry through unchanged.
case "$RESET_PASSWORD" in
  *[\'\"\$\`\\]*|'') die "RESET_PASSWORD must not be empty or contain quotes, \$, backticks or backslashes" ;;
esac
[ -z "$BUILD_PROXY" ] || [[ "$BUILD_PROXY" =~ ^https?://[A-Za-z0-9._:-]+/?$ ]] \
  || die "BUILD_PROXY must be a plain http(s)://host:port URL"

# The debconf answers of runbook section 3. reset-seed-admin stays true: the
# validation must prove a fresh install replaces the published seed credential
# and forces the first-login reset, which `up` then performs once.
preseed() {
  cat <<EOF
carlos-emr carlos-emr/server-name string localhost
carlos-emr carlos-emr/bind-ip string 0.0.0.0
carlos-emr carlos-emr/province select ${CARLOS_PROVINCE}
carlos-emr carlos-emr/tls-mode select selfsigned
carlos-emr carlos-emr/acme-email string
carlos-emr carlos-emr/java-heap string 2g
carlos-emr carlos-emr/reset-seed-admin boolean true
carlos-emr carlos-emr/install-demo-data boolean ${INSTALL_DEMO_DATA}
EOF
}

# Prints "v2", "hybrid" or "v1". Only the first two can boot systemd >= 256.
cgroup_mode() {
  if [ -e "$CGROUP_ROOT/cgroup.controllers" ]; then
    echo v2
  elif [ -e "$CGROUP_ROOT/unified/cgroup.controllers" ]; then
    echo hybrid
  else
    echo v1
  fi
}

cgroup_run_args() {
  case "$(cgroup_mode)" in
    v2) echo "--cgroupns=private" ;;
    hybrid) echo "--cgroupns=host -v $CGROUP_ROOT/unified:/sys/fs/cgroup:rw" ;;
    *) die "this host has a cgroup-v1-only hierarchy; Ubuntu 26.04's systemd cannot boot in a container here (use an LXD VM, runbook section 2)" ;;
  esac
}

require_disposable() {
  [ "${CARLOS_DISPOSABLE_HOST:-}" = true ] \
    || die "refusing to run: set CARLOS_DISPOSABLE_HOST=true on a disposable host (privileged container, host networking, demonstration data)"
  command -v docker >/dev/null || die "docker is not installed"
}

in_container() { docker exec "$CONTAINER" bash -c "$1"; }

build_image() {
  local ctx
  ctx="$(mktemp -d)"
  # A trap would be shared with the caller; remove the context explicitly.
  if [ -n "$BUILD_CA_BUNDLE" ]; then
    [ -r "$BUILD_CA_BUNDLE" ] || die "BUILD_CA_BUNDLE is not readable: $BUILD_CA_BUNDLE"
    cp "$BUILD_CA_BUNDLE" "$ctx/extra-ca.crt"
  else
    : > "$ctx/extra-ca.crt"
  fi
  cat > "$ctx/Dockerfile" <<'EOF'
ARG BASE_IMAGE=ubuntu:26.04
FROM ${BASE_IMAGE}
ARG BUILD_PROXY=
ARG APT_FORCE_HTTPS=false
ARG PLAYWRIGHT_VERSION
COPY extra-ca.crt /usr/local/share/ca-certificates/carlos-validation-extra-ca.crt
RUN set -e; \
    if [ "$APT_FORCE_HTTPS" = true ]; then sed -i 's#http://#https://#g' /etc/apt/sources.list.d/*.sources; fi; \
    if [ -n "$BUILD_PROXY" ]; then printf 'Acquire::http::Proxy "%s";\nAcquire::https::Proxy "%s";\n' "$BUILD_PROXY" "$BUILD_PROXY" > /etc/apt/apt.conf.d/99carlos-validation-proxy; fi; \
    if [ -s /usr/local/share/ca-certificates/carlos-validation-extra-ca.crt ]; then \
      printf 'Acquire::https::CaInfo "/usr/local/share/ca-certificates/carlos-validation-extra-ca.crt";\n' > /etc/apt/apt.conf.d/98carlos-validation-ca; \
    else rm -f /usr/local/share/ca-certificates/carlos-validation-extra-ca.crt; fi; \
    apt-get update; \
    DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
      ca-certificates systemd systemd-sysv dbus iproute2 procps curl \
      nodejs npm poppler-utils gnupg libxml2-utils; \
    update-ca-certificates; \
    rm -f /etc/apt/apt.conf.d/98carlos-validation-ca; \
    rm -rf /var/lib/apt/lists/*; \
    rm -f /usr/sbin/policy-rc.d; \
    systemctl mask systemd-firstboot.service systemd-udevd.service systemd-modules-load.service \
      getty@tty1.service console-getty.service systemd-networkd-wait-online.service; \
    cd /root && npm init -y >/dev/null; \
    if [ -n "$BUILD_PROXY" ]; then export HTTPS_PROXY="$BUILD_PROXY"; fi; \
    export NODE_EXTRA_CA_CERTS=/etc/ssl/certs/ca-certificates.crt; \
    npm install --save-exact --no-audit --no-fund "playwright@${PLAYWRIGHT_VERSION}"
ENV container=docker
STOPSIGNAL SIGRTMIN+3
CMD ["/sbin/init"]
EOF
  say "building image $IMAGE from $BASE_IMAGE"
  docker build --network host -t "$IMAGE" \
    --build-arg BASE_IMAGE="$BASE_IMAGE" --build-arg BUILD_PROXY="$BUILD_PROXY" \
    --build-arg APT_FORCE_HTTPS="$APT_FORCE_HTTPS" --build-arg PLAYWRIGHT_VERSION="$PLAYWRIGHT_VERSION" \
    "$ctx" >"$LOG_DIR/image-build.log" 2>&1 || { rm -rf "$ctx"; die "image build failed; see $LOG_DIR/image-build.log"; }
  rm -rf "$ctx"
}

find_one_deb() {
  local pattern="$1" matches
  matches=$(find "$DEBS_DIR" -maxdepth 1 -name "$pattern" | sort)
  [ "$(printf '%s\n' "$matches" | grep -c .)" = 1 ] \
    || die "DEBS_DIR must hold exactly one $pattern (found: ${matches:-none})"
  basename "$matches"
}

cmd_up() {
  require_disposable
  [ -n "${DEBS_DIR:-}" ] && [ -d "$DEBS_DIR" ] || die "DEBS_DIR must name the directory holding the .debs"
  DEBS_DIR="$(cd "$DEBS_DIR" && pwd)"
  local emr drugref ctl port
  emr=$(find_one_deb 'carlos-emr_*_amd64.deb')
  drugref=$(find_one_deb 'carlos-emr-drugref_*_all.deb')
  ctl=$(find_one_deb 'carlos-ctl_*_all.deb')
  mkdir -p "$LOG_DIR"
  docker inspect "$CONTAINER" >/dev/null 2>&1 && die "container $CONTAINER already exists; run 'down' first"
  for port in 80 443 3306 18080 9515; do
    if ss -Hltn "sport = :$port" 2>/dev/null | grep -q .; then die "port $port is already in use on this host"; fi
  done
  local extra=() cgroup_args
  [ -n "$CONTAINER_CPUS" ] && extra+=(--cpus "$CONTAINER_CPUS")
  # An assignment, not a substitution inside docker run's arguments: a v1-only
  # host must stop here, before an image is built or a container started.
  cgroup_args=$(cgroup_run_args) || exit 1
  build_image

  say "starting $CONTAINER (cgroup mode: $(cgroup_mode))"
  # shellcheck disable=SC2086 # cgroup_args is a deliberate word list
  docker run -d --name "$CONTAINER" --hostname "$CONTAINER" --privileged --network host \
    $cgroup_args "${extra[@]}" --tmpfs /run --tmpfs /run/lock \
    -v "$REPO_ROOT:/root/carlos:ro" -v "$DEBS_DIR:/debs:ro" "$IMAGE" >/dev/null
  local state="" i
  for i in $(seq 1 60); do
    state=$(docker exec "$CONTAINER" systemctl is-system-running 2>/dev/null || true)
    case "$state" in running|degraded) break ;; esac
    sleep 2
  done
  case "$state" in running|degraded) ;; *) die "systemd did not come up in $CONTAINER (state '${state:-none}')" ;; esac

  preseed | docker exec -i "$CONTAINER" debconf-set-selections
  say "installing $emr, $drugref and $ctl (log: $LOG_DIR/install.log)"
  # The proxy is exported only for apt here; the running EMR never sees it.
  if ! docker exec -e DEBIAN_FRONTEND=noninteractive "$CONTAINER" bash -c "
      set -e
      if [ -n '$BUILD_PROXY' ]; then
        printf 'Acquire::http::Proxy \"%s\";\nAcquire::https::Proxy \"%s\";\n' '$BUILD_PROXY' '$BUILD_PROXY' > /etc/apt/apt.conf.d/99carlos-validation-proxy
      fi
      apt-get update
      apt-get install -y --no-remove /debs/$emr /debs/$drugref /debs/$ctl" >"$LOG_DIR/install.log" 2>&1; then
    die "apt-get install failed; see $LOG_DIR/install.log"
  fi
  # A transaction can finish with exit 0 and still leave provisioning pending;
  # the marker is the package's own record of that.
  if in_container 'test -e /var/lib/carlos-emr/.install-incomplete'; then
    in_container 'cat /var/lib/carlos-emr/.install-incomplete' >&2 || true
    die "the install left /var/lib/carlos-emr/.install-incomplete; see $LOG_DIR/install.log"
  fi
  if grep -q 'is installed but is NOT ready to use' "$LOG_DIR/install.log"; then
    say "NOTE: the installer printed 'NOT ready to use' during the transaction although no marker remains"
  fi
  in_container 'carlos-ctl check' >"$LOG_DIR/carlos-ctl-check.log" 2>&1 \
    || die "carlos-ctl check failed; see $LOG_DIR/carlos-ctl-check.log"
  grep -q '^All checks passed' "$LOG_DIR/carlos-ctl-check.log" \
    || die "carlos-ctl check did not report 'All checks passed'; see $LOG_DIR/carlos-ctl-check.log"
  say "carlos-ctl check: all checks passed"

  stage_fixtures
  first_login_reset
  write_suite_env
  say "ready: https://127.0.0.1/carlos (suite environment in $CONTAINER:/root/suite-env.sh)"
}

# Runbook section 4 fixtures b, c, d and the CDS export PGP recipient, plus
# rx_fax_enabled for the Rx fax checks and login_lock=true. Each is test-only
# state on a disposable install; none relaxes a security default. login_lock
# keys the failed-login lockout to the username instead of the address: every
# check logs in from 127.0.0.1, so with the default address lockout one
# lockout check (account-lockout-unlock) locks every later check out.
stage_fixtures() {
  say "staging the runbook fixtures"
  in_container "
    set -e
    P=/etc/carlos-emr/carlos.properties
    for d in /var/lib/carlos-emr/CarlosDocument/carlos/eform/images /var/lib/carlos-emr/CarlosDocument/eform/images; do
      install -d -o carlos -g carlos -m 0750 \"\$d\"
      install -o carlos -g carlos -m 0640 /root/carlos/release/4422-84v9-1.png \"\$d/consult_sig_999998.png\"
    done
    install -o carlos -g carlos -m 0640 /root/carlos/release/Document/carlos/eform/images/MissedAppointment.rtl \
      /var/lib/carlos-emr/CarlosDocument/carlos/eform/images/MissedAppointment.rtl
    if [ '$CARLOS_PROVINCE' != bc ] && [ '$INSTALL_DEMO_DATA' = true ]; then
      mariadb -u root carlos -e \"
        INSERT INTO appointment (provider_no, appointment_date, start_time, end_time, name, demographic_no,
            notes, reason, location, resources, type, style, billing, status, createdatetime, creator)
        SELECT * FROM (SELECT '9' p,'2026-08-07' d,'09:00:00' s,'09:15:00' e,'LOCAL_SEED_OBEC_REPORT_1' n,714 dn,
              '' a,'' b,'' c,'' f,NULL g,'' h,'' i,'t' j,NOW() k,'carlosdoc' l
          UNION ALL SELECT '999998','2026-08-08','10:00:00','10:15:00','LOCAL_SEED_OBEC_REPORT_2',71,'','','','',NULL,'','','t',NOW(),'carlosdoc'
          UNION ALL SELECT '999998','2026-08-10','11:00:00','11:15:00','LOCAL_SEED_OBEC_REPORT_3',81,'','','','',NULL,'','','t',NOW(),'carlosdoc') seed
        WHERE NOT EXISTS (SELECT 1 FROM appointment WHERE name LIKE 'LOCAL_SEED_OBEC_REPORT_%');\"
    fi
    G=/var/lib/carlos-emr/export-gnupg
    if [ ! -d \"\$G\" ]; then
      install -d -o carlos -g carlos -m 0700 \"\$G\"
      runuser -u carlos -- gpg --homedir \"\$G\" --batch --passphrase '' \
        --quick-gen-key 'CARLOS Export Validation <export-validation@carlos.invalid>' default default never 2>/dev/null
    fi
    printf '%s\n' '#!/bin/sh' \
      '# PGP 2-style front end for GnuPG: carlos-export-pgp -e FILE RECIPIENT -> FILE.pgp' \
      '[ \"\$1\" = -e ] && [ -n \"\$2\" ] && [ -n \"\$3\" ] || exit 2' \
      'exec /usr/bin/gpg --batch --yes --trust-model always --output \"\$2.pgp\" --recipient \"\$3\" --encrypt \"\$2\"' \
      > /usr/local/bin/carlos-export-pgp
    chmod 0755 /usr/local/bin/carlos-export-pgp
    sed -i \"s#^PGP_BIN: .*#PGP_BIN: /usr/local/bin/carlos-export-pgp#;
            s#^PGP_KEY: .*#PGP_KEY: export-validation@carlos.invalid#;
            s#^PGP_ENV: .*#PGP_ENV: GNUPGHOME=\$G#\" \"\$P\"
    if grep -qE '^rx_fax_enabled' \"\$P\"; then sed -i -E 's#^rx_fax_enabled.*#rx_fax_enabled=true#' \"\$P\";
    else echo 'rx_fax_enabled=true' >> \"\$P\"; fi
    if grep -qE '^login_lock' \"\$P\"; then sed -i -E 's#^login_lock.*#login_lock=true#' \"\$P\";
    else echo 'login_lock=true' >> \"\$P\"; fi
    carlos-ctl restart" >"$LOG_DIR/fixtures.log" 2>&1 || die "fixture staging failed; see $LOG_DIR/fixtures.log"
  wait_front_door
}

# Asked from inside the container, which is where the suite runs too.
wait_front_door() {
  local i
  for i in $(seq 1 120); do
    if in_container 'curl -skf --max-time 5 -o /dev/null https://127.0.0.1/carlos/'; then return 0; fi
    sleep 3
  done
  die "the front door did not answer https://127.0.0.1/carlos/ after the restart"
}

# Runbook section 6: the installer credential works once and must be replaced
# through the real forced-reset page before any suite run.
first_login_reset() {
  say "performing the first-login password reset"
  in_container "
    set -e
    cd /root/carlos
    export BASE_URL=https://127.0.0.1/carlos CHROME_PATH=/usr/lib/carlos-emr/chromium/chrome
    export TEST_USER=\"\$(sed -n 's/^ *user: *//p' /etc/carlos-emr/initial-admin.txt)\"
    export TEST_PASSWORD=\"\$(sed -n 's/^ *password: *//p' /etc/carlos-emr/initial-admin.txt)\"
    export TEST_PIN=\"\$(sed -n 's/^ *PIN: *//p' /etc/carlos-emr/initial-admin.txt)\"
    printf '%s' \"\$TEST_PIN\" | grep -Eq '^[0-9]{4}\$'
    RESET_PASSWORD='$RESET_PASSWORD' DRUGREF_UPDATE_REQUIRE_STATUS=true node scripts/drugref-update-playwright-checks.js" \
    >"$LOG_DIR/first-login-reset.log" 2>&1 || die "the first-login reset failed; see $LOG_DIR/first-login-reset.log"
}

# The environment block of runbook section 6, with the build tag read from the
# installed WAR so the About-page assertion is exact for THIS package.
write_suite_env() {
  local suite_province=''
  case "$CARLOS_PROVINCE" in on) suite_province=ON ;; bc) suite_province=BC ;; esac
  in_container "
    set -e
    props=/usr/share/carlos-emr/webapp/carlos/WEB-INF/classes/carlos-build.properties
    version=\"\$(sed -n 's/^build.version=//p' \$props)\"
    tag=\"\$version (\$(sed -n 's/^build.job=//p' \$props) \$(sed -n 's/^build.number=//p' \$props))\"
    mkdir -p /tmp/carlos-shots /tmp/carlos-artifacts
    cat > /root/suite-env.sh <<EOF
# Written by scripts/deb-docker-validation.sh (runbook deb-install-validation.md section 6).
export BASE_URL=https://127.0.0.1/carlos CHROME_PATH=/usr/lib/carlos-emr/chromium/chrome EXPECT_FRONT_DOOR=true
export TEST_USER=\"\\\$(sed -n 's/^ *user: *//p' /etc/carlos-emr/initial-admin.txt)\"
export TEST_PIN=\"\\\$(sed -n 's/^ *PIN: *//p' /etc/carlos-emr/initial-admin.txt)\"
export TEST_PASSWORD='$RESET_PASSWORD'
export MYSQL_HOST=localhost MYSQL_USER=root MYSQL_PASSWORD=dummy MYSQL_DATABASE=carlos
export TEST_PASSWORD_HASH=\"\\\$(mariadb -u root carlos -Nse \"SELECT password FROM security WHERE user_name='\\\${TEST_USER}' LIMIT 1\")\"
export PRESCRIPTION_SIGNATURE_CLEANUP=true
export EDOC_NAV_DOCUMENT_STORE=/var/lib/carlos-emr/CarlosDocument/carlos/document
export LAB_UPLOAD_DOCUMENT_STORE=/var/lib/carlos-emr/CarlosDocument/carlos/document
export PRESCRIPTION_SCRIPT_ID=45 PRESCRIPTION_DEMOGRAPHIC_NO=1
export CONSULT_DEMO_NO=1 CONSULT_SERVICE_ID=1 CONSULT_REQUEST_ID=1 CONSULT_STAMP_PROVIDER_NO=999998
export CONSULT_APPLICATION_TEMP_DIR=/var/lib/carlos-emr/catalina/temp/carlos-temp
export PATIENT_LIST_FIXTURE_PROFILE=local-seed-obec-report-v1
export BILLING_APPOINTMENT_NO=11 BILLING_DEMOGRAPHIC_NO=1 BILLING_PROVIDER_NO=999998
export BILLING_APPOINTMENT_DATE=2024-04-16 BILLING_START_TIME=12:00:00
export RTL_TEMPLATE_NAME=MissedAppointment.rtl
export RX_FAX_PROVIDER_NO=999998 RX_FAX_DEMOGRAPHIC_NO=1
export RX_EXPECTED_BUILD_TAG='\$tag' EXPECTED_BUILD_VERSION='\$version'
# The packaged store layout. Without these, about 30 checks fail on a
# precondition before they drive anything.
export DOCUMENT_DIR=/var/lib/carlos-emr/CarlosDocument/carlos/document
export LETTER_DOCUMENT_DIR=/var/lib/carlos-emr/CarlosDocument/carlos/document
export BOUNDARY_DOCUMENT_DIR=/var/lib/carlos-emr/CarlosDocument/carlos/document
export FORWARDING_SCOPE_DOCUMENT_STORE=/var/lib/carlos-emr/CarlosDocument/carlos/document
export RA_DOCUMENT_DIR=/var/lib/carlos-emr/CarlosDocument/carlos/document
export INCOMINGDOCUMENT_DIR=/var/lib/carlos-emr/CarlosDocument/carlos/incomingdocs
export OHIP_DISK_DIR=/var/lib/carlos-emr/CarlosDocument/carlos/billing/download
export INVOICE_DIR=/var/lib/carlos-emr/CarlosDocument/carlos/billing/invoices
export ONEDT_INBOX=/var/lib/carlos-emr/CarlosDocument/carlos/onEDTDocs/inbox
export RA_EDT_INBOX=/var/lib/carlos-emr/CarlosDocument/carlos/onEDTDocs/inbox
export EFORM_IMAGE_DIR=/var/lib/carlos-emr/CarlosDocument/carlos/eform/images
export SCREENSHOT_DIR=/tmp/carlos-shots ARTIFACT_DIR=/tmp/carlos-artifacts
# The package ships ALLOW_UPDATE_DOCUMENT_CONTENT=true.
export STORED_DOCUMENT_EXPECT_CONTENT_UPDATES=true
# This container is disposable: checks that change clinic-wide settings may run.
export CARLOS_DISPOSABLE_VM=true CARLOS_LOG_JOURNAL_UNIT=carlos-emr.service
# The installed province: 'suite' passes it as --province unless one is given.
export CARLOS_SUITE_PROVINCE=$suite_province
export RX_FAX_DOCUMENT_DIR=/var/lib/carlos-emr/CarlosDocument/carlos/document
export RX_FAX_ROUND_TRIP_TIMEOUT_MS=180000 RX_FAX_SPOOL_DIR=/var/lib/carlos-emr/catalina/temp
export DRUGREF_UPDATE_TRIGGER=false DRUGREF_UPDATE_REQUIRE_STATUS=true
export FIRST_NATIONS_DEMOGRAPHIC_NO=1 CDS_EXPORT_GNUPGHOME=/var/lib/carlos-emr/export-gnupg
export NOTE_DEMOGRAPHIC_NO=2 PREVENTION_BRAND_QUERY=Tdap
export BILLING_SUBMIT_DATE=2024-05-06 BILLING_OHIP_CODE=A007A BILLING_BONUS_CODE=Q040A
export GROUP_DISK_SERVICE_DATE=2003-02-03 GROUP_DISK_PAID_CODE=A007A
export BILLING_CODE_EXISTING=A007A BILLING_CODE_NEW=X987Z
export APPOINTMENT_PROVIDER_NO=999998 APPOINTMENT_DEMOGRAPHIC_NO=1 APPOINTMENT_DAYS_AHEAD=400
export MESSENGER_PROVIDER_NO=999998 LAB_PROVIDER_NO=999998
export MEASUREMENT_DEMOGRAPHIC_NO=1 MEASUREMENT_GROUP=Anthropometrics MEASUREMENT_TYPE=WT
export NEXT_APPT_DEMOGRAPHIC_NO=1 NEXT_APPT_PROVIDER_NO=999998
export CARLOS_LOG_AUDIT_SINCE='\$(date -u '+%Y-%m-%d %H:%M:%S UTC')'
cd /root/carlos
EOF
    chmod 0600 /root/suite-env.sh"
}

cmd_suite() {
  require_disposable
  mkdir -p "$LOG_DIR"
  [ "$#" -gt 0 ] || set -- --tier smoke
  # Select only checks for the installed province (and province-neutral ones),
  # unless the caller chose a province.
  local province
  province=$(in_container '. /root/suite-env.sh; printf %s "${CARLOS_SUITE_PROVINCE:-}"' 2>/dev/null || true)
  case " $* " in
    *" --province "*) ;;
    *) [ -z "$province" ] || set -- "$@" --province "$province" ;;
  esac
  local quoted before after rc=0 stamp
  quoted=$(printf '%q ' "$@")
  stamp=$(date -u +%Y%m%dT%H%M%SZ)
  before=$(in_container 'systemctl show carlos-emr -p NRestarts --value')
  in_container ". /root/suite-env.sh; node scripts/run-playwright-suite.js $quoted --junit /root/suite-$stamp.xml" \
    2>&1 | tee "$LOG_DIR/suite-$stamp.log" || rc=$?
  docker cp "$CONTAINER:/root/suite-$stamp.xml" "$LOG_DIR/" >/dev/null 2>&1 || true
  after=$(in_container 'systemctl show carlos-emr -p NRestarts --value')
  # A browser failure can be the first symptom of the JVM being killed and
  # restarted; a run that tested two processes must not finish green.
  if [ "$before" != "$after" ]; then
    echo "FAIL carlos-emr restarted during the suite ($before -> $after)"
    rc=1
  fi
  return "$rc"
}

cmd_audit() {
  require_disposable
  # printf '%q ' with no arguments still prints '', which the audit rejects
  # as an unknown empty argument.
  local quoted=''
  [ "$#" -eq 0 ] || quoted=$(printf '%q ' "$@")
  in_container ". /root/suite-env.sh; scripts/deb-server-log-audit.sh $quoted"
}

main() {
  local cmd="${1:-}"
  [ "$#" -gt 0 ] && shift
  case "$cmd" in
    up) cmd_up ;;
    suite) cmd_suite "$@" ;;
    audit) cmd_audit "$@" ;;
    shell) require_disposable; docker exec -it "$CONTAINER" bash ;;
    down) require_disposable; docker rm -f "$CONTAINER" >/dev/null && say "removed $CONTAINER" ;;
    print-preseed) preseed ;;
    cgroup-mode) cgroup_mode ;;
    -h|--help|help|'') usage ;;
    *) usage >&2; exit 2 ;;
  esac
}

main "$@"
