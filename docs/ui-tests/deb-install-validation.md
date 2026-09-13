# Deb Install Validation Runbook

This runbook builds the CARLOS Debian packages from source, installs them into a
disposable Ubuntu 26.04 VM with the demo dataset, and drives the full
`scripts/*-playwright-checks.js` suite against the packaged deployment — through
the real front door (nginx + ModSecurity/CRS on `:443`), not the devcontainer's
bare Tomcat.

It is the procedure that first surfaced the WAF false positives on note-saving
and eForm saves, the add-patient validation regression, and the nullable-column
500s on the consultation surfaces. Last validated end-to-end 2026-08-31 with
**41/41 scripts passing** on 2026.09.0~snapshot18.

The 2026-09 pass added six clinical-workflow checks — appointment booking,
allergies, immunizations, internal messaging, lab-result review and vitals entry
— for surfaces that had no browser coverage at all. They were written against
2026.08.0-alpha11 packaged as 2026.09.0~snapshot19 and found four unvalidated-input
HTTP 500s on the measurement surfaces plus two workflows that are unusable on a
clean install until an operator provisions something the installer does not
(messenger contacts, lab routing). See the per-check notes in the environment
contract below; the reports are tracked as findings, not encoded as expectations.

That 37/37 is also the cautionary tale for this document. A tester found six
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

From the repo root:

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export MAVEN_OPTS="-Xmx3g"
dpkg-buildpackage -us -uc -b
```

This compiles the CARLOS WAR, fetches and builds DrugRef at the ref pinned in
`debian/drugref.pin`, and downloads the Chromium revision pinned in
`debian/chromium.pin`. The three `.deb` files land in the parent directory.

For iterative rebuilds, cache the parts that do not change and skip their
network fetches (see the header of [`debian/rules`](../../debian/rules) for the
full input list):

```bash
mkdir -p ../build-cache
cp    debian/build/drugref2.war ../build-cache/
cp -a debian/build/chromium     ../build-cache/chromium
# later rebuilds:
DRUGREF_WAR=$PWD/../build-cache/drugref2.war \
CHROMIUM_DIST=$PWD/../build-cache/chromium \
dpkg-buildpackage -us -uc -b
```

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

Preseed debconf so the install runs unattended. `reset-seed-admin=false` is the
critical answer: it keeps the published dev credential
(`carlosdoc` / `carlos2026` / PIN `2026`) that every check logs in with. Decline
it **only** on a disposable machine that will never hold patient data — which
this VM is.

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
carlos-emr carlos-emr/reset-seed-admin boolean false
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

One database tweak and three fixtures remain:

```bash
# The seed row ships forcePasswordReset=1; the checks need a direct login.
# (login-playwright-checks.js exercises the forced-reset flow itself and
# restores whatever state it changes.)
lxc exec carlos-test -- mariadb -u root carlos \
  -e "UPDATE security SET forcePasswordReset=0 WHERE user_name='carlosdoc';"
```

Fixtures the dataset alone does not provide:

```bash
# a) (Demo document files: seeded by carlos-ctl demo-data, see above. On a
#    store provisioned by an older package, push them by hand:)
#    for f in .devcontainer/db/db_data/documents/*.pdf .devcontainer/db/db_data/hrm/*.xml; do
#      lxc file push "$f" carlos-test/var/lib/carlos-emr/CarlosDocument/carlos/document/
#    done
#    then chown carlos:carlos and chmod 0640 the pushed files.

# b) Provider stamp for the consultation-signature checks: a small PNG named
#    consult_sig_<providerNo>.png in the eForm image directory.
#    IT MUST BE A DECODABLE PNG, not merely a file with a PNG header. The stamp is
#    embedded into the consultation PDF by ImageIO, so a truncated or hand-built
#    PNG makes the whole print preview fail with the generic "A print preview of
#    this consultation could not be generated" message, and the real cause
#    (javax.imageio.IIOException: Error reading PNG image data) appears only in the
#    application log. Verify before staging it, e.g.
#      python3 -c "from PIL import Image; Image.open('consult_sig_999998.png').load()"
#    (Any real PNG will do: convert -size 240x80 xc:white consult_sig_999998.png,
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
  cd /root && npm init -y && npm install playwright
  npx --yes playwright install --with-deps chromium'
```

The scripts run from `/root/carlos` (the repo mount) so their relative fixture
paths resolve; Node still finds Playwright via `/root/node_modules`.

## 6. Run the suite

Environment contract (one block, exported before every script):

```bash
cd /root/carlos
export BASE_URL=https://127.0.0.1/carlos
export TEST_USER=carlosdoc TEST_PASSWORD=carlos2026 TEST_PIN=2026
# DB-backed checks: root over the MariaDB unix socket (the password value is
# ignored by unix_socket auth but the scripts require it to be set).
export MYSQL_HOST=localhost MYSQL_USER=root MYSQL_PASSWORD=dummy MYSQL_DATABASE=carlos
# Published seed hash for carlos2026 (from database/mysql/migration/on/V1.0.2__on_data.sql)
export TEST_PASSWORD_HASH='{bcrypt}$2a$10$RcoNeqhcLzkfBzAoTQ5C5.nnsOs15iOasQCp0/smjDAuTtkMQ.Uju'
# Record pointers into the demo dataset:
export PRESCRIPTION_SCRIPT_ID=45 PRESCRIPTION_DEMOGRAPHIC_NO=1
export CONSULT_DEMO_NO=1 CONSULT_SERVICE_ID=1 CONSULT_REQUEST_ID=1
# CONSULT_REQUEST_ID must name a consultation that ALREADY CARRIES A SIGNATURE:
# consultation-signature-playwright-checks.js waits for the stored signature image to
# load. The demo dataset ships none (signature_img is NULL on every consultationRequests
# row), so on a fresh install run consultation-signature-submit first — it signs
# CONSULT_UNSIGNED_REQUEST_ID — and then point CONSULT_REQUEST_ID at that same id.
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
# ---------------------------------------------------------------------------
# Clinical-workflow checks added 2026-09 (appointment, allergy, prevention,
# messenger, lab review, measurement). All six share scripts/carlos-playwright-harness.js
# and all six clean up after themselves in a finally, including after a failure:
# each stamps the rows it writes with a unique PW_<AREA>_<millis> marker and deletes
# exactly those. None of them touches a pre-existing clinical record.
#
# appointment-crud-playwright-checks.js — books into an empty slot on the day view,
# edits, advances the status from the schedule's status letter, cancels, deletes, and
# asserts the appointmentArchive row the delete must leave behind. It books
# APPOINTMENT_DAYS_AHEAD out (default 400) so the demo dataset's own appointments
# cannot occupy the slot it clicks.
export APPOINTMENT_DEMOGRAPHIC_NO=1 APPOINTMENT_PROVIDER_NO=999998 APPOINTMENT_DAYS_AHEAD=400
# allergy-crud-playwright-checks.js — drug-class search, quick-add, save, modify
# (adds a replacement and archives the original), delete (archives), and the
# prescriber's allergy-warning JSON endpoint. ALLERGY_WARNING_ATC only has to be a
# real ATC; the endpoint is asserted as a JSON contract, not on a specific warning.
export ALLERGY_DEMOGRAPHIC_NO=1 ALLERGY_WARNING_ATC=J01CA04
# prevention-immunization-playwright-checks.js — records an immunization from the
# prevention grid and asserts BOTH the preventions row and its preventionsExt detail
# rows, then the reporting page and the printable record.
export PREVENTION_DEMOGRAPHIC_NO=1 PREVENTION_PROVIDER_NO=999998
# messenger-inbox-playwright-checks.js — compose, deliver, read, mark unread/read,
# search, archive, unarchive, each asserted on the per-recipient messagelisttbl row.
# A CLEAN INSTALL HAS NO MESSENGER CONTACTS (groupMembers_tbl is empty and the shipped
# "doc" group has no members), so the compose page offers no recipients and nothing can
# be sent. The check enrols the test provider through Administration > Messenger — the
# operator's own remedy — and removes that enrolment again only if it created it.
export MESSENGER_PROVIDER_NO=999998
# lab-results-review-playwright-checks.js — Inboxhub, lab display (asserting the
# CSRF bootstrap labDisplay.jsp depends on), the lab PDF, the acknowledge, and
# cumulative values. The demo dataset routes its labs to provider 0, so no provider has
# a reviewable inbox item; the check routes ONE existing demo lab to the test provider
# and restores the routing exactly as it found it. LAB_SEGMENT_ID must be an HL7 lab
# LINKED to a patient — labDisplay refuses to acknowledge an unmatched lab — and
# defaults to the lowest demo lab that is.
export LAB_PROVIDER_NO=999998
# measurement-entry-playwright-checks.js — vitals entry, server-side validation of a
# bad value, the save, the edit-form read-back and the trend graph.
# MEASUREMENT_TEMPLATE IS NOT OPTIONAL: the entry form resolves it through
# MeasurementTemplateFlowSheetConfig.getFlowSheet(), which has no null or unknown-name
# guard, so a missing/empty/unknown template answers HTTP 500. "phv" (Periodic Health
# Visit) is the default because it ships in the WAR and carries the general vitals
# (BP/HT/WT/BMI); MEASUREMENT_TYPE must be a member of whichever flowsheet is named.
# MEASUREMENT_ASSERT_INPUT_VALIDATION=true turns the malformed-input probes into
# assertions — leave it OFF until that null handling is fixed, then turn it on.
export MEASUREMENT_DEMOGRAPHIC_NO=1 MEASUREMENT_TYPE=WT MEASUREMENT_TEMPLATE=phv MEASUREMENT_VALUE=72
# ---------------------------------------------------------------------------
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
# Optional: RX_EXPECTED_BUILD_TAG makes the About-page assertion exact instead of merely
# "looks like a version" — set it to the tag the packaged WAR should carry, which is
# "<pom version> (carlos-emr-deb <debian/changelog version>)", e.g.
#   export RX_EXPECTED_BUILD_TAG='2026.08.0-alpha11-SNAPSHOT (carlos-emr-deb 2026.09.0~snapshot18)'
# Leave it unset when validating a WAR you did not build through the packaging.

for s in scripts/*-playwright-checks.js scripts/demographic-master-crud-smoke.js; do
  case "$s" in *eform-corpus-soak*) continue ;; esac   # needs a corpus dir; see below
  timeout 300 node "$s" && echo "PASS $s" || echo "FAIL $s"
done
```

Notes on the contract:

- **`BASE_URL` uses `127.0.0.1`, deliberately.** The scripts set
  `ignoreHTTPSErrors`, and Chromium is lenient about loopback certificates, so
  every script works against the self-signed cert. The cost: a numeric-IP
  `Host:` header trips CRS rule **920350** (+3 anomaly on every request), which
  a production hostname never sees. When judging any WAF block found this way,
  discount 920350 and look at the *other* matched rules. Using the real
  `server_name` FQDN instead avoids 920350 but fails the handful of scripts
  that create a browser context without `ignoreHTTPSErrors`.
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
