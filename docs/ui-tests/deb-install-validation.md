# Deb Install Validation Runbook

This runbook builds the CARLOS Debian packages from source, installs them into a
disposable Ubuntu 26.04 VM with the demo dataset, and drives the full
`scripts/*-playwright-checks.js` suite against the packaged deployment — through
the real front door (nginx + ModSecurity/CRS on `:443`), not the devcontainer's
bare Tomcat.

It is the procedure that first surfaced the WAF false positives on note-saving
and eForm saves, the add-patient validation regression, the nullable-column
500s on the consultation surfaces, and the empty Consultations inbox on the demo
dataset (the seeded `_site_access_privacy` grant was applied without multisite mode;
`consultation-nullable-fields-playwright-checks.js` now asserts the list has rows). Last validated end-to-end 2026-08-31 with
**41/41 scripts passing** on 2026.09.0~snapshot18.

That run is also the cautionary tale for this document. A tester found six
defects on the build that produced it — an eForm editor save 403, an eForm
download failure, a false "0 error" banner on a successful delete, a DataTables
warning, a drug search 502, and a document upload 500 — and the suite was green
through all six, because no script drove those surfaces the way an operator
does. Two scripts were added for the surfaces nothing covered
(`drug-search-playwright-checks.js`, `document-upload-playwright-checks.js`) and
`eform-admin-crud-playwright-checks.js` was extended to save from inside the
Administration panel rather than only from the standalone editor page. When
adding a check here, reach the page by clicking the links an operator clicks:
the eForm editor 403 existed **only** on the panel path, and navigating straight
to the JSP exercised the one shape that already worked.

## Scope

This validation answers one question:

Does a clinician-facing workflow that passes in the devcontainer still work on a
clean packaged install — schema migrated by Flyway, WAF in blocking mode,
self-signed TLS, services running as their unprivileged accounts?

Testing `http://127.0.0.1:18080/carlos` (Tomcat directly) **bypasses the WAF
and answers a different question**. Every check below goes through `:443`.

## Host prerequisites

- LXD with a VM-capable storage pool and a NAT bridge (the VM needs outbound
  network for `apt`).
- ~20 GB free disk (16 GiB VM root + the three `.deb` files) and enough RAM to
  give the VM 8 GiB. Do not run heavy Maven builds while VMs are up on a
  memory-constrained host — the build below is done **before** the VM exists.
- Build dependencies satisfied on the host: `dpkg-checkbuilddeps` must be clean
  (OpenJDK 21, Maven, debhelper, tomcat11 packages).

## 1. Build the packages

For a promotion, start from the **promotion candidate** (whose Maven version
and SCM tag are already the release tag), not the correction branch's
`-SNAPSHOT` version. Use an isolated worktree and apply the same release-version
stamping as `.github/workflows/deb-packages.yml` before building:

```bash
git fetch origin
# Update for each promotion. After handoff/deletion, use the retained release
# tag or exact validated commit instead of this temporary candidate branch.
promotion_ref=origin/codex/promote-2026-08-alpha12-to-main
git worktree add --detach ../carlos-package-validation "$promotion_ref"
cd ../carlos-package-validation
release_tag=2026.08.0-alpha12
deb_version="${release_tag//-/~}"
printf 'carlos-emr (%s) resolute; urgency=medium\n\n  * Validation package of release %s.\n\n -- CARLOS Release CI <releases@carlos-emr.invalid>  %s\n' \
  "$deb_version" "$release_tag" "$(date -R)" > debian/changelog
test "$(dpkg-parsechangelog -SVersion)" = "$deb_version"
```

This replaces the snapshot changelog in the **isolated packaging worktree
only**; do not commit that generated stamp. The tracked changelog can start at
`2026.09.0~snapshot21`, which is newer than alpha12, so retaining it below the
release stanza would violate Debian version ordering. Git retains the history.
An unstamped development build is valid for development, but is not an alpha12
release artifact and cannot satisfy the exact About-page assertion below.

Then, from that packaging worktree:

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export MAVEN_OPTS="-Xmx3g"
env -u DRUGREF_WAR -u DRUGREF_SRC -u DRUGREF_REF dpkg-buildpackage -us -uc -b
```

This compiles the CARLOS WAR, fetches and builds DrugRef at the ref pinned in
`debian/drugref.pin`, and downloads the Chromium revision pinned in
`debian/chromium.pin`. The three `.deb` files land in the parent directory.

For iterative rebuilds, optionally reuse Chromium downloaded by this build
while `debian/chromium.pin` is unchanged. Refresh that cache whenever the pin
changes. Keep DrugRef on the default pinned-source build for promotion
validation:

```bash
mkdir -p ../build-cache
cp -a debian/build/chromium     ../build-cache/chromium
# later rebuilds:
CHROMIUM_DIST=$PWD/../build-cache/chromium \
env -u DRUGREF_WAR -u DRUGREF_SRC -u DRUGREF_REF dpkg-buildpackage -us -uc -b
```

The local `DRUGREF_WAR` and `DRUGREF_SRC` overrides in
[`debian/rules`](../../debian/rules) bypass the pinned-source fetch. A prebuilt
WAR's filename or package version does not establish that it contains the
revision in `debian/drugref.pin`; an old unversioned cache can silently omit
DrugRef fixes even when the CARLOS build is current. Do not use an unverified
DrugRef WAR as evidence for a promotion. The commands above clear those
overrides, including `DRUGREF_REF`, so DrugRef is built from the repository pin.

## 2. Create the test VM

```bash
lxc launch ubuntu:26.04 carlos-test --vm \
    -c limits.cpu=2 -c limits.memory=8GiB -d root,size=16GiB
lxc exec carlos-test -- cloud-init status --wait

# Mount the repo read-only: the checks read fixtures and src/** paths,
# and script edits on the host are live in the VM with no re-push.
lxc config device add carlos-test carlosrepo disk \
    source=$PWD path=/root/carlos readonly=true
```

## 3. Install the packages (non-interactive)

Preseed debconf so the install runs unattended. Release validation deliberately
keeps `reset-seed-admin=true`, the package's secure default: it proves that a
fresh install replaces the source-published seed password/PIN, creates the
root-only handoff file, and enforces the first-login password reset. The suite
consumes that credential once in section 6 and then uses the reset password.

The preseed below answers the province question with `on`. To validate the
`other` alias instead, substitute `carlos-emr/province select other` and assert
after the install that the env file records the answer while the rendered
properties still name the Ontario billing region. The two files are written by
different code and do not share a format, so match each as it is actually
written:

```bash
# set_env_key writes env values quoted and unspaced
lxc exec carlos-test -- grep '^CARLOS_PROVINCE=' /etc/carlos-emr/carlos-emr.env
# expect CARLOS_PROVINCE="other"
# prop_set rewrites properties with spaces around the '='
lxc exec carlos-test -- grep -E '^billregion[[:space:]]*=' /etc/carlos-emr/carlos.properties
# expect billregion = ON
```

`other` applies the Ontario migrations, so every other step in this runbook is
unchanged.

```bash
cat > /tmp/carlos-preseed.txt <<'EOF'
carlos-emr carlos-emr/server-name string localhost
carlos-emr carlos-emr/bind-ip string 0.0.0.0
carlos-emr carlos-emr/province select on
carlos-emr carlos-emr/tls-mode select selfsigned
carlos-emr carlos-emr/acme-email string
carlos-emr carlos-emr/java-heap string 2g
carlos-emr carlos-emr/reset-seed-admin boolean true
carlos-emr carlos-emr/install-demo-data boolean true
EOF
lxc file push /tmp/carlos-preseed.txt carlos-test/root/
lxc file push ../carlos-emr_*_all.deb ../carlos-emr-drugref_*_all.deb \
              ../carlos-emr-eform-renderer_*_amd64.deb carlos-test/root/

lxc exec carlos-test -- bash -c '
  export DEBIAN_FRONTEND=noninteractive
  apt-get update -y
  debconf-set-selections /root/carlos-preseed.txt
  apt-get install -y /root/carlos-emr_*_all.deb \
                     /root/carlos-emr-drugref_*_all.deb \
                     /root/carlos-emr-eform-renderer_*_amd64.deb'
```

Then verify the deployment before anything else:

```bash
lxc exec carlos-test -- carlos-ctl check
```

Every line must be `OK` (services, loopback-only Tomcat/MariaDB, WAF blocking a
probe SQLi with 403, live DrugRef lookup, renderer service, Flyway history).
Do not continue on a failing check — every later step assumes this baseline.

## 4. Demo dataset and test fixtures

`install-demo-data=true` in the preseed above makes the installer load the
package's own demonstration dataset (`carlos-ctl demo-data`): the additive
per-province patient snapshot, the referral-specialist and provider-link
seeds, the name sanitization, and the Rich Text Letter chain including the
attachment-route fix. Being additive (`INSERT IGNORE` only), it never touches
the Flyway-seeded rows, so the V1.0.17 digital-signatures default survives.
(The devcontainer counterpart is `.devcontainer/db/scripts/populate_db.sh`;
if the two ever disagree about the RTL chain, that script and
`debian/assets/carlos_ctl/dbops.py` are the authorities.)

`carlos-ctl demo-data` also copies the demo document FILES (the PDFs the
dump's document rows reference, plus the fictitious HRM report that
`demo-hrm-report.sql` points one demographic-1 `HRMDocument` row at) into
`/var/lib/carlos-emr/CarlosDocument/carlos/document/` as `carlos:carlos 0640`.
Confirm they arrived before running the attachment checks; without them every
attachment render fails "could not be converted into a PDF" and the attach
popups list no HRM documents:

```bash
lxc exec carlos-test -- ls -la /var/lib/carlos-emr/CarlosDocument/carlos/document/
# expect six *_LabReport.pdf and demo-hrm-diagnostic-imaging.xml
```

Three fixtures the dataset alone does not provide remain. Do not clear
`forcePasswordReset` in SQL: section 6 exercises the package's real first-login
handoff before the suite runs.

```bash
# a) (Demo document files: seeded by carlos-ctl demo-data, see above. On a
#    store provisioned by an older package, push them by hand:)
#    for f in .devcontainer/db/db_data/documents/*.pdf .devcontainer/db/db_data/hrm/*.xml; do
#      lxc file push "$f" carlos-test/var/lib/carlos-emr/CarlosDocument/carlos/document/
#    done
#    then chown carlos:carlos and chmod 0640 the pushed files.

# b) Provider stamp for the consultation-signature checks: any small PNG,
#    named consult_sig_<providerNo>.png in the eForm image directory.
#    (Any PNG will do, e.g.: convert -size 240x80 xc:white consult_sig_999998.png,
#    or reuse a repo image such as release/4422-84v9-1.png renamed.)
lxc file push consult_sig_999998.png \
  carlos-test/var/lib/carlos-emr/CarlosDocument/carlos/eform/images/
lxc exec carlos-test -- bash -c \
  'chown carlos:carlos /var/lib/carlos-emr/CarlosDocument/carlos/eform/images/consult_sig_999998.png
   chmod 0640          /var/lib/carlos-emr/CarlosDocument/carlos/eform/images/consult_sig_999998.png'

# c) A clinic Rich Text Letter template, so eform-rtl-print-pdf-playwright-checks.js
#    (RTL_TEMPLATE_NAME=MissedAppointment.rtl) can prove clinic .rtl templates load
#    into the editor unsandboxed. The repo ships one.
lxc file push release/Document/carlos/eform/images/MissedAppointment.rtl \
  carlos-test/var/lib/carlos-emr/CarlosDocument/carlos/eform/images/
lxc exec carlos-test -- bash -c \
  'chown carlos:carlos /var/lib/carlos-emr/CarlosDocument/carlos/eform/images/MissedAppointment.rtl
   chmod 0640          /var/lib/carlos-emr/CarlosDocument/carlos/eform/images/MissedAppointment.rtl'

# d) The three LOCAL_SEED_OBEC_REPORT appointments that
#    patient-list-by-appointment-export-playwright-checks.js documents as its
#    operator-provisioned fixture contract (see that script's header):
lxc exec carlos-test -- mariadb -u root carlos -e "
INSERT INTO appointment (provider_no, appointment_date, start_time, end_time,
    name, demographic_no, notes, reason, location, resources, type, style,
    billing, status, createdatetime, creator)
VALUES
 ('9','2026-08-07','09:00:00','09:15:00','LOCAL_SEED_OBEC_REPORT_1',714,'','','','',NULL,'','','t',NOW(),'carlosdoc'),
 ('999998','2026-08-08','10:00:00','10:15:00','LOCAL_SEED_OBEC_REPORT_2',71,'','','','',NULL,'','','t',NOW(),'carlosdoc'),
 ('999998','2026-08-10','11:00:00','11:15:00','LOCAL_SEED_OBEC_REPORT_3',81,'','','','',NULL,'','','t',NOW(),'carlosdoc');"
```

Restart once after loading so nothing serves from a pre-load cache:

```bash
lxc exec carlos-test -- carlos-ctl restart
```

## 5. Install the Playwright harness in the VM

```bash
lxc exec carlos-test -- bash -c '
  export DEBIAN_FRONTEND=noninteractive
  apt-get install -y nodejs npm
  cd /root && npm init -y && npm install --save-exact playwright@1.60.0
  /usr/lib/carlos-emr/chromium/chrome --version'
```

Do not run `playwright install` on Ubuntu 26.04 with Playwright 1.60.0: that
Playwright release does not recognise the `ubuntu26.04-x64` host platform. The
`carlos-emr-eform-renderer` package already supplies the release-pinned Chromium
and its runtime dependencies. Using it also makes the suite exercise the exact
browser shipped to operators instead of a second downloaded browser.

The scripts run from `/root/carlos` (the repo mount) so their relative fixture
paths resolve; Node still finds Playwright via `/root/node_modules`.

## 6. Run the suite

Environment contract (one block, exported before every script):

```bash
cd /root/carlos
export BASE_URL=https://127.0.0.1/carlos
export CHROME_PATH=/usr/lib/carlos-emr/chromium/chrome
# A secure fresh install randomises both secrets. Read the root-only handoff file,
# then perform the mandatory first-login reset once before any suite loop.
if [ ! -r /etc/carlos-emr/initial-admin.txt ]; then
  echo "FAIL initial administrator handoff file is not readable"
  exit 1
fi
export TEST_USER="$(sed -n 's/^ *user: *//p' /etc/carlos-emr/initial-admin.txt)"
export TEST_PASSWORD="$(sed -n 's/^ *password: *//p' /etc/carlos-emr/initial-admin.txt)"
export TEST_PIN="$(sed -n 's/^ *PIN: *//p' /etc/carlos-emr/initial-admin.txt)"
if [ -z "$TEST_USER" ] || [ -z "$TEST_PASSWORD" ] \
    || ! printf '%s' "$TEST_PIN" | grep -Eq '^[0-9]{4}$'; then
  echo "FAIL initial administrator handoff credentials are incomplete or invalid"
  exit 1
fi
if ! RESET_PASSWORD='Carlos2026!Verify' DRUGREF_UPDATE_REQUIRE_STATUS=true \
    node scripts/drugref-update-playwright-checks.js; then
  echo "FAIL mandatory first-login password reset"
  exit 1
fi
export TEST_PASSWORD='Carlos2026!Verify'
# DB-backed checks: root over the MariaDB unix socket (the password value is
# ignored by unix_socket auth but the scripts require it to be set).
export MYSQL_HOST=localhost MYSQL_USER=root MYSQL_PASSWORD=dummy MYSQL_DATABASE=carlos
# login-playwright-checks mutates and restores this account; give it the hash of
# the password that the forced-reset step above actually installed.
export TEST_PASSWORD_HASH="$(mariadb -u root carlos -Nse \
  "SELECT password FROM security WHERE user_name='${TEST_USER}' LIMIT 1")"
if [ -z "$TEST_PASSWORD_HASH" ]; then
  echo "FAIL could not read the reset administrator password hash"
  exit 1
fi
# Record pointers into the demo dataset:
export PRESCRIPTION_SCRIPT_ID=45 PRESCRIPTION_DEMOGRAPHIC_NO=1
export CONSULT_DEMO_NO=1 CONSULT_SERVICE_ID=1 CONSULT_REQUEST_ID=1
export CONSULT_STAMP_PROVIDER_NO=999998 CONSULT_UNSIGNED_REQUEST_ID=3
export PATIENT_LIST_FIXTURE_PROFILE=local-seed-obec-report-v1
# Ontario 3rd-Party / Bonus-Codes bill entry (billing-on-third-party-playwright-checks.js).
# Read-only: it opens the Ontario bill form for this appointment and switches the bill
# type, but never submits a bill, so it seeds nothing and cleans nothing up. It asserts
# billRegion travels on every self-navigation, which is what #3588 pinned -- an install
# that has the `billregion` property set still routes correctly with the parameter
# missing, so a status-only assertion would pass on a re-broken page.
export BILLING_APPOINTMENT_NO=11 BILLING_DEMOGRAPHIC_NO=1 BILLING_PROVIDER_NO=999998
export BILLING_APPOINTMENT_DATE=2024-04-16 BILLING_START_TIME=12:00:00
# Rich Text Letter print/PDF check (fixture c above); omit to skip only its template step.
export RTL_TEMPLATE_NAME=MissedAppointment.rtl
# Rx signature-stamp fax check (rx-fax-signature-stamp-playwright-checks.js). It writes and then
# deletes its own prescription (and every other row it creates: drugs, DigitalSignature, faxes,
# FaxClientLog, fax_config), so it needs no fixture script id. It cannot remove FILES: each run
# leaves one prescription_<providerNo><millis>.pdf under DOCUMENT_DIR plus the .pdf/.txt pair in
# the fax spool (fax_file_location) that the fax scheduler consumes. Harmless on a throwaway VM. Two prerequisites, both
# operator-staged like the consultation stamp checks:
#   1. rx_fax_enabled=true in /etc/carlos-emr/carlos.properties (rx_signature_enabled is already
#      true by default), then `carlos-ctl restart`. Without rx_fax the Fax buttons never render.
#   2. the same provider stamp PNG the consultation checks stage, consult_sig_999998.png, in the
#      eForm image dir (CarlosDocument/eform/images and .../carlos/eform/images).
# It also stages a destination fax number on the patient's active pharmacies and restores their
# original value on cleanup: the demo dataset ships pharmacies with a blank fax, and the servlet
# refuses such a prescription with "Valid fax number not found", so without it the check would be
# measuring the missing pharmacy number rather than the signature gate.
export RX_FAX_PROVIDER_NO=999998 RX_FAX_DEMOGRAPHIC_NO=1
# Rx reprint / re-prescribe check (rx-fax-reprint-represcribe-playwright-checks.js). Same two
# prerequisites as the fax check above, and it reuses RX_FAX_PROVIDER_NO / RX_FAX_DEMOGRAPHIC_NO.
# It creates one prescription through the UI and removes it (with its drugs row and stored
# signature) in a finally; it reprints and re-prescribes only that row, so no pre-existing patient
# record is touched, and it writes no files. Like the fax check it stages, and then restores, a fax
# number on the patient's active pharmacies — ViewScript2 folds `hasFaxNumber` into the Fax button,
# so without one the pad assertions would not isolate the stamp.
# It reaches the reprint list the way an operator does: the "Reprint" link in the drug-profile
# section head reveals a cell that starts hidden, and that link only renders with `_rx` write
# access. It tolerates one known pre-existing page error (issue #3578, expandPreview writing into
# the preview iframe before it has parsed) and fails on any other.
# Required for a release gate: RX_EXPECTED_BUILD_TAG makes the About-page assertion exact.
# Set it to the tag the packaged WAR should carry, which is
# "<pom version> (carlos-emr-deb <debian/changelog version>)", e.g.
#   export RX_EXPECTED_BUILD_TAG='2026.08.0-alpha12 (carlos-emr-deb 2026.08.0~alpha12)'
# Never leave it unset for promotion validation: that can accept a stale WAR/package pair.
export RX_EXPECTED_BUILD_TAG='2026.08.0-alpha12 (carlos-emr-deb 2026.08.0~alpha12)'
# Rx fax record-binding check (rx-fax-record-binding-playwright-checks.js). Pins the
# guarantees of PR #3606: a forged patient identity, prescription date, clinic block, reprint
# annotation or satellite-clinic block on the fax POST never reaches the faxed PDF (the
# prescription record's own values do), a standalone one-character direction line survives,
# a note typed immediately before Fax is on the fax (the save is deliberately delayed so the race
# is deterministic), and the clinic header is submitted as separate lines with the clinic name
# rendered as its own line (the glued-letter-n defect collapsed it to one line). Same
# prerequisites and the same seed-then-delete fixtures as the two Rx checks above; it asserts on
# the PDF the servlet writes, so it must be able to READ DOCUMENT_DIR
# -- run it as root on the VM, and point RX_FAX_DOCUMENT_DIR at the install's DOCUMENT_DIR as this
# process sees it. It leaves two prescription_<pdfId>.pdf files there per run (one from the Fax
# button, one from the forged POST) plus their fax-spool pairs; nothing from a PDF is printed.
export RX_FAX_DOCUMENT_DIR=/var/lib/carlos-emr/CarlosDocument/carlos/document
# Straight after `carlos-ctl restart`, Tomcat compiles the Rx JSPs on first hit and one Fax
# click can take longer than the check's default 45 s round-trip allowance; raise it for a
# cold server rather than reading the timeout as a fax failure.
export RX_FAX_ROUND_TRIP_TIMEOUT_MS=180000
# Administration > Update Drugref (drugref-update-playwright-checks.js). Read-only by default:
# it opens the page from the Administration panel and asserts the status panel and the status
# relay answer. DRUGREF_UPDATE_TRIGGER=true also clicks the button and follows the rebuild to
# its end (SUCCEEDED, date moved forward, drug search still answers). That rebuilds the DrugRef
# database from DPD_BASE_URL in /etc/carlos-emr/drugref2.properties -- Health Canada's site
# unless you point it at a mirror -- and takes 15-60 minutes, so run it last and only on a
# throwaway VM. DRUGREF_UPDATE_REQUIRE_STATUS=true rejects a DrugRef build without
# getUpdateStatus (the packaged one must have it).
# TRIGGER stays false in this exported block: the suite loop below runs every script under
# `timeout 300`, and a triggered rebuild takes 15-60 minutes, so the loop would SIGTERM it
# mid-rebuild -- skipping the script's cleanup and leaving the remaining drug-dependent
# checks running against a half-rebuilt database. Run the triggered mode on its own, after
# the loop finishes, with a timeout longer than DRUGREF_UPDATE_TIMEOUT_SEC:
#
#   DRUGREF_UPDATE_TRIGGER=true DRUGREF_UPDATE_REQUIRE_STATUS=true \
#     DRUGREF_UPDATE_TIMEOUT_SEC=3600 \
#     timeout 3900 node scripts/drugref-update-playwright-checks.js
export DRUGREF_UPDATE_TRIGGER=false DRUGREF_UPDATE_REQUIRE_STATUS=true
# A browser failure may be the first symptom of the JVM being killed and
# restarted. Record the service counter so the suite cannot finish green after
# silently testing two different application processes.
service_restarts_before="$(systemctl show carlos-emr -p NRestarts --value)"
suite_failed=0
for s in scripts/*-playwright-checks.js scripts/demographic-master-crud-smoke.js; do
  case "$s" in
    *eform-corpus-soak*) continue ;;   # needs a corpus dir; see below
    *login-playwright-checks*) continue ;; # run its deliberate failed-login probes last
  esac
  # The record-binding check waits up to RX_FAX_ROUND_TRIP_TIMEOUT_MS twice on a cold server and
  # must still reach its fixture cleanup; a SIGTERM from the wrapper would skip that.
  t=300; case "$s" in *rx-fax-record-binding*) t=$((2 * ${RX_FAX_ROUND_TRIP_TIMEOUT_MS:-45000} / 1000 + 300)) ;; esac
  # Signal the Node runner so its cleanup can keep using the browser.
  if timeout --foreground "$t" node "$s"; then
    echo "PASS $s"
  else
    rc=$?
    echo "FAIL ($rc) $s"
    suite_failed=1
  fi
done
service_restarts_after="$(systemctl show carlos-emr -p NRestarts --value)"
if [ "$service_restarts_after" != "$service_restarts_before" ]; then
  echo "FAIL carlos-emr restarted during suite ($service_restarts_before -> $service_restarts_after)"
  suite_failed=1
fi
if [ "$suite_failed" -ne 0 ]; then
  echo "FAIL positive Playwright suite; refusing to mask it with the isolated login phase"
  exit 1
fi

# This security check deliberately submits two bad passwords. Run it after the
# positive suite against a freshly started process so a prior harness mistake
# cannot supply the third failure that locks the shared test account, and leave
# it last so its own negative probes cannot affect another check.
if ! systemctl restart carlos-emr; then
  echo "FAIL could not restart carlos-emr before the isolated login phase"
  exit 1
fi
login_phase_ready=0
for attempt in $(seq 1 90); do
  if curl -skf --max-time 5 -o /dev/null https://127.0.0.1/carlos/; then
    login_phase_ready=1
    break
  fi
  sleep 2
done
if [ "$login_phase_ready" -ne 1 ]; then
  echo "FAIL carlos-emr did not become ready for the isolated login phase"
  exit 1
fi
if timeout 300 node scripts/login-playwright-checks.js; then
  echo "PASS isolated login Playwright phase"
else
  rc=$?
  echo "FAIL ($rc) isolated login Playwright phase"
  exit 1
fi
```

Notes on the contract:

- **`BASE_URL` uses `127.0.0.1`, deliberately.** The scripts set
  `ignoreHTTPSErrors`, and Chromium is lenient about loopback certificates, so
  every script works against the self-signed cert. A direct loopback/private
  client with no forwarding headers is deliberately exempted from CRS rule
  **920350**; seeing 920350 for this suite is a regression. The exemption must
  disappear when `Forwarded`, `X-Forwarded-*`, `X-Real-IP`, or `Via` is present,
  because a proxied/public numeric-host request still requires CRS inspection.
- **`PRESCRIPTION_SCRIPT_ID` must point at a prescription that has `drugs`
  rows.** The demo dump contains drugless `prescription` rows (46+); a
  drugless script renders no preview and the check times out. Script 45 has
  drugs; verify with
  `SELECT p.script_no FROM prescription p JOIN drugs d ON d.script_no=p.script_no`.
- **`CONSULT_UNSIGNED_REQUEST_ID` is consumed.** The stamp-update scenario
  signs that consultation, so a second back-to-back run needs the fixture
  reset: `UPDATE consultationRequests SET signature_img=NULL WHERE requestId=3;`
- `eform-consultation-acceptance` skips its stored image-layer template probe
  (with a `[skip]` note) unless `LIBRARY_EFORM_NAME` names a form that exists
  in the library; the main acceptance workflow runs regardless.
- `eform-corpus-soak-playwright-checks.js` additionally needs a corpus
  directory (see `docs/eform-corpus-soak-method.md`) and is not part of the
  standard pass.
- **`allergy-rx-alert-playwright-checks.js` cleans up both allergies it records.**
  Each run uses cryptographically unique reaction markers and, in `finally`,
  inactivates every active row carrying one of those exact markers through the
  product's supported UI path. Rows remain archived because that is the allergy
  list's normal audit-preserving semantics, but repeat runs do not accumulate
  active clinical data. SIGINT/SIGTERM sent to the Node runner PID stop new
  fixture writes, allow the current submit to settle, and run that same cleanup
  before closing Chromium, exiting with code 130/143. The loop uses
  `timeout --foreground` to target the runner; signalling the browser or its
  process group directly can prevent browser-based cleanup. Shutdown has a
  three-minute grace deadline; a hung request,
  cleanup failure, SIGKILL, or host panic can still leave a row. A timeout does
  not prove that the server abandoned a pending write. In these cases,
  clear only those generated markers on a disposable demo box with
  `UPDATE allergies SET archived=1 WHERE archived=0 AND (reaction LIKE 'Rash typed check %' OR reaction LIKE 'Rash free-text check %');`
  Run it on a **loopback** `BASE_URL`: like `billing-on-third-party`, it relaxes
  certificate verification only for loopback, so a host opted in with
  `ALLOW_NON_LOCAL_BASE_URL` must present a certificate the browser trusts.
  It is also the one script whose failure can mean the OTHER package is stale:
  the alert half runs through DrugRef, so a build whose `debian/drugref.pin`
  predates the free-text (typeCode 0) allergy branch fails part 3 with an empty
  warnings array while CARLOS itself is correct.
- **`eform-rtl-print-pdf-playwright-checks.js` must be run through `:443`** too. It
  drives the Rich Text Letter the way a clinician does (Preventions, Download,
  the form's PDF and "Submit & PDF" buttons — the latter must auto-close the window
  after the download — toolbar Print, "Submit & Print", a clinic template) and
  verifies real PDF bytes come back from the render browser. One of the defects
  it pins exists only behind the WAF: CRS 932100 scored the letter's own prose
  in `ARGS:Letter` and answered the save with a 403 (package exclusion 1045).
- **`eform-admin-crud-playwright-checks.js` must be run through `:443`.** It
  covers the eForm administration create/edit/delete round trip, and one of the
  three defects it pins (the CRS block on the editor's `ARGS:formHtml`, rule
  1050) exists *only* behind the WAF — against bare Tomcat the check still
  passes on the CSRF and persistence assertions while silently no longer
  covering the failure it was written for. It warns on stdout when `BASE_URL`
  is not HTTPS. It creates its own timestamped probe eForm and deletes only
  that one; a failing run leaves the probe behind on purpose, so clear strays
  with `UPDATE eform SET status=0 WHERE form_name LIKE 'Playwright Admin CRUD %';`.
- **`echart-new-patient-notes-playwright-checks.js` builds its own fixture** —
  it creates a `PLAYWRIGHT-EC-<timestamp>` patient, books an appointment for
  them, and opens the eChart from that appointment, which is the path the
  notes-pagination loop was reported on. It deletes the patient, the
  appointment and the note lock in its `finally`, including after a failure, so
  repeat runs stay clean. A run killed mid-flight (SIGINT/SIGKILL) skips that
  `finally`; identify and remove any stray with

  ```sql
  SELECT demographic_no, last_name FROM demographic WHERE last_name LIKE 'PLAYWRIGHT-EC-%';
  DELETE a, l, adm, arch, d
    FROM demographic d
    LEFT JOIN appointment a        ON a.demographic_no   = d.demographic_no
    LEFT JOIN casemgmt_note_lock l ON l.demographic_no   = d.demographic_no
    LEFT JOIN admission adm        ON adm.client_id      = d.demographic_no
    LEFT JOIN demographicArchive arch ON arch.demographic_no = d.demographic_no
   WHERE d.last_name LIKE 'PLAYWRIGHT-EC-%';
  ```

  It shrinks the notes wrapper in the browser before watching the poll: the
  pagination only fires when that pane overflows and sits at the top, which a
  tall headless window never reproduces on its own.
- **`error-sanitization-playwright-checks.js` provokes two real 500s on
  purpose**, and must also run through `:443`. It is the only check that
  exercises `ResponseSanitizationFilter`'s error-replacement path — every other
  script drives success paths, so a filter that stopped sanitizing entirely
  would leave the suite green. While it runs, `journalctl -u carlos-emr` will
  show `Uncaught exception escaped filter chain` and `Sanitizing ... error
  response body` at ERROR: that is the check working, not a failure. It creates
  and deletes nothing (one append-only `OscarLog` audit row from the `/ws`
  probe). To confirm it can still fail — worth doing after any change to the
  filter — set `response.sanitization.enabled=false` in
  `/etc/carlos-emr/carlos.properties`, `carlos-ctl restart`, and re-run: it
  must FAIL. Restore the property and restart afterwards.

## 7. Exercise the upgrade path

Re-installing the same (or a newer) package pair over the live install is the
upgrade path — schema migrates before the service restarts:

```bash
lxc exec carlos-test -- bash -c '
  export DEBIAN_FRONTEND=noninteractive
  apt-get install -y --reinstall /root/carlos-emr_*_all.deb \
      /root/carlos-emr-drugref_*_all.deb \
      /root/carlos-emr-eform-renderer_*_amd64.deb'
lxc exec carlos-test -- carlos-ctl check   # expect the same all-OK, with any
                                           # new migrations counted in flyway_schema_history
```

## Diagnosing failures

**Fax recovery safety.** A lost response or a database commit-acknowledgement
error is an unknown fax outcome, not proof that transmission failed. The page
disables resend and encounter paste and retains captured text for verification.
Once persistence has been attempted, prepared fax files are retained because a
committed job may already be using them. Check the fax outbox and have an
administrator reconcile the job/audit record before sending again or removing
any retained files. Only failures proven to occur before persistence clean up
their owned files automatically. A historical reprint must remain ineligible
for ordinary Print and Paste after any signature event or recovery.

**A "timed-out" save with a clean application log usually means the WAF ate the
request.** A ModSecurity block returns nginx's 403 page and the request **never
reaches Tomcat**, so it appears in `/var/log/nginx/access.log` and in
`/var/log/carlos-emr/modsec/modsec_audit.log` (JSON, one transaction per line —
the matched rule ids, the argument, and the matched bytes are all in
`messages[].details`) but not in Tomcat's access log. `carlos-ctl waf tail`
prints the same signal live. Package-shipped exclusions live in
`debian/assets/modsecurity/REQUEST-900-EXCLUSION-RULES-BEFORE-CRS.conf`
(ids 1000–1999); site exclusions belong in the operator-owned
`local-exclusions-*.conf` files (ids 5000–5999).

**Schema is validated at boot — do not hand-apply migrations the deployed WAR
does not ship.** The package configures `carlos.flyway.onBoot=validate`: a
database *ahead* of the WAR fails validation and the application refuses to
start on its next restart. Land the migration in the tree, rebuild the
packages, and let the reinstall apply it.

**Hot-validating a one-file fix without a full package rebuild.** JSPs can be
pushed straight into the exploded webapp
(`/usr/share/carlos-emr/webapp/carlos/…`) — Tomcat recompiles them on the next
request. A single Java class can be compiled against the exploded WAR and
dropped in, followed by `carlos-ctl restart`:

```bash
W=target/carlos-*-SNAPSHOT/WEB-INF
javac -nowarn -cp "$W/classes:$W/lib/*:/usr/share/java/tomcat11-servlet-api.jar:/usr/share/java/tomcat11-el-api.jar:/usr/share/java/tomcat11-jsp-api.jar:$HOME/.m2/repository/com/github/spotbugs/spotbugs-annotations/4.9.3/spotbugs-annotations-4.9.3.jar" \
      -d /tmp/classout path/to/The2Action.java
lxc file push /tmp/classout/.../The2Action.class \
  carlos-test/usr/share/carlos-emr/webapp/carlos/WEB-INF/classes/.../The2Action.class
lxc exec carlos-test -- carlos-ctl restart
```

This is a validation shortcut only — the fix is not real until it survives a
full `dpkg-buildpackage` + reinstall cycle.

**Caches can make direct-SQL fixtures invisible.** `getActiveProviders()` is
`@Cacheable` (`ACTIVE_PROVIDERS`, 5-minute TTL) and only
`saveProvider()`/`updateProvider()` evict it — a provider INSERTed behind the
app's back stays missing from admin dropdowns until the TTL expires. Prefer
driving the app's own UI (as `add-login-account-playwright-checks.js` does) or
restart after seeding.

**Known demo-data sharp edges** (handled by the steps above, listed for when a
check fails anyway): the filtered demo snapshot carries `casemgmt_note_link`
rows whose TICKLER target no longer exists (new ticklers reuse those ids and
"inherit" orphaned notes — `tickler-note-dialog` purges them in setup); the HRM
parser logs `FileNotFoundException` for lab files the dump references but does
not ship (cosmetic); the Rich Text Letter page logs a 404 + MIME-refusal
console error for `displayImage.do?imagefile=stamps.js` on every stock install
(by design — `EFormAssetDeployer` never auto-deploys `stamps.js` because it
holds clinic-specific signature stamps; the editor works without it); and
`consultationRequests.providerNo`/`urgency`/`status` are nullable in the
SCHEMA but always populated in the dump — regressions on the null path have
been 500s in the past, so exercise it by nulling a row explicitly
(`UPDATE consultationRequests SET providerNo=NULL, urgency=NULL WHERE
requestId=<id>;`) rather than assuming the dump provides one.
