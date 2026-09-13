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
**41/41 scripts passing** on 2026.09.0~snapshot18. That is the historical
full-suite baseline: the count and the date are that run's, not this release's.
The two checks added since, `echart-print-playwright-checks.js` and
`clinical-freetext-playwright-checks.js`, were run on 2026-09-12 against a
2026.09.0~snapshot22 package built from the #3623 branch (DrugRef and the eForm
renderer skipped) and installed into an Ubuntu 26.04 container: both **PASS**
through the packaged front door, with `EXPECT_FRONT_DOOR=true`. The full suite
has not been re-run on a later snapshot.

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

The same lesson has a second half: **what a check types matters as much as where
it clicks.** A later tester hit a 403 saving eChart CPP items that the green
`echart-playwright-checks.js` could not have caught, because the text the script
typed was clean and the text in the tester's chart was not — a pasted link whose
query string contained `&cmd` scored CRS 932110 on the encounter note body, and
that body rides on the CPP save's issue-refresh POST and the draft autosave as
well as on the note save itself. A WAF check that submits only inoffensive prose
measures nothing. Position matters too: CRS 931100 (RFI via an IP-address URL)
is anchored on the start of the argument, so it fired only once the seeded text
*began* with the pasted PACS link — a probe that buried the link mid-sentence
reported the argument clean.

The note route was not special. A per-argument survey of every clinician
free-text field the application posts (consultation requests, ticklers,
prescriptions and allergies, preventions, document and lab comments, HRM,
messenger and patient email, appointment reasons and notes, master-record
notes and alerts, billing comments, fax cover comments, program notes) found
all 67 of them answering 403 on the same three shapes, and a consultation
request save and a tickler add reproduced it in the browser. Exclusions
1100-1142 close them per argument (73 arguments across 43 rules once the
per-route table and the provider template body are counted; the regression
test pins the table). `tickler-crud-playwright-checks.js` now
types that scoring text too. Fields whose parameter names are generated per
row (measurement `comments-<n>`, manual lab `test_<n>.labnotes`, contact
`contact_<n>.note`, waiting-list `waitingListBean[<n>].note`) cannot be literal
`ctl` targets and libmodsecurity 3.0.14 rejects a regex target in a `ctl`
action, but it accepts one in a config-time `SecRuleUpdateTargetByTag`, so
those four are anchored patterns in `RESPONSE-999-EXCLUSION-RULES-AFTER-CRS.conf`.
The trade-off, spelled out in that file, is that a config-time update is not
route-scoped: an argument of exactly that shape is exempt on any route, while a
near-miss name (`xcomments-1`, `comments-1x`) still scores. Measured after
deploying them: every per-row field reaches the application on all six shapes,
the structured neighbours on the same rows (`test_<n>.lab_test_name`,
`contact_<n>.contactId`, `waitingListBean[<n>].demographicNo`) and the
un-indexed names (`comments`, `labnotes`, `note`) still block on the same
shapes, and a measurement saved from the browser with such a comment lands in
the `measurements` table intact.

Everything the survey, the per-row patterns and the form list exempt keeps the
CRS **XSS** family inspected: those rules did not fire on any measured prose
shape at paranoia level 1, and legacy views still render some stored values
raw, so only the six families that misread prose (SQLi, RCE, PHP injection,
protocol, LFI, RFI) are removed. Exclusion 1010 on the note route keeps its
original seven, and the raw sinks a review found behind exempted arguments
(note history, the legacy tickler list, prescription record and reason
comments, the demographic alert and notes boxes) now go through the null-safe
encoder, pinned by `ProseSinkEncodingRegressionTest`.

The encounter forms under `/form/*` are covered by a **generated** file,
`REQUEST-901-FORM-PROSE-EXCLUSIONS-BEFORE-CRS.conf` (ids 1200-1399), written by
`scripts/waf/generate-form-prose-exclusions.py` from the form JSPs: one rule per
save route and, on the shared `/form/formname` route, per `form_class`, listing
that form's `<textarea>` cells and the single-line inputs whose names mark them
as narrative boxes (46 rules, 1,236 cells at the time of writing). The `other`
classifier is token-bounded, so mother identity/measurement fields retain inspection. The
`form_class`-keyed rules run in phase 2, where the POST body is available.
`FormProseWafExclusionRegressionTest` re-derives the same table from the JSPs
and fails when the committed file is stale, so after editing a form run the
generator and commit its output. Measured on the packaged install: config test
85 ms and a normal reload with the file loaded (1,027 rules); Discharge
Summary, Mental Health Form 1, Rourke 2020, BCAR 2020 and Vascular Tracker
cells reach the application on all shapes; a Rourke cell posted under another
form's `form_class`, the same cell with no `form_class`, the same cell on GET,
and the structured fields on every form (`formId`, `demographic_no`,
`form_class`) still block; Mental Health Form 1 and Rourke 2020 saved from the
browser through `:443` with such prose are stored intact.

Two groups of form cells are **deliberate residuals**: they still answer 403
on scored prose, the generator reports each by name as skipped, and both are
security decisions rather than gaps. The policy this deployment holds to is
one route, one argument: an exemption is a `ctl` action chained on the route
(and, on the shared form route, the `form_class`), naming one literal
argument. A config-time `SecRuleUpdateTargetByTag` would accept an anchored
regex for names a `ctl` target cannot carry, but it is not route-scoped and so
exempts that name on **every** route; measured here, and rejected.

- The Vascular Tracker's `value(...)` cells. libmodsecurity 3.0.14 rejects
  `ARGS:value(subjective)` in a `ctl` action quoted, unquoted and
  backslash-escaped (`Expecting an action` at `nginx -t` each time), so there
  is no per-route spelling. A global pattern was tried and withdrawn on the
  policy above. Nothing is lost today: the form cannot submit on this line at
  all (next paragraph), and when it is restored its cells should be given
  names the per-route file can target.
- The growth-chart and chart-checklist per-row cells (`comment_<n>`,
  `descOther<n>`). A global `^comment_[0-9]+$` was also tried and withdrawn:
  `rx/prescribe.jsp` posts the prescription comment as `comment_<rand>`, so
  the pattern would unscore that field and hand a forged POST to any endpoint
  a rule-free generic name. The clean fix is a per-form rename to fixed
  repeated names the 901 file can target by literal `ARGS`, which the save
  action (keyed by row index) must follow — a form migration.

The Rh-injection page under the form directory posts elsewhere
(`/prevention/AddPrevention`, `reason` and `reasonOtherText`) and is covered by
rule 1117; the lab requisition print view posts nothing; the generator reports
both by name rather than as skipped.

The Vascular Tracker form itself cannot be exercised from the browser on
this line, for reasons that have nothing to do with the firewall and are NOT
fixed here. Its Struts 2
migration was never finished: the chart shortcut is stored as the Struts 1
route (`../form/SetupForm.do?formName=VTForm&demographic_no=`), which the
route resolver returns null for (`CARLOS Error: 400`); the setup and submit
actions' configured-form check rejects that same stored row as not their
route (400); the submit action reads its `value(...)` cells from a map that
Struts 2 never fills (`value(x)` is not an accepted parameter name) and
returns raw paths as result names that nothing maps; and the setup action
sets the page's request attributes and then redirects, so the page renders
`null` in every label it reads from them. Each was reproduced on the
packaged install by fixing the one before it. Restoring the form is a form
migration with its own browser verification, not a WAF exclusion, and it
was deliberately not bundled into this change. The one piece kept is the
JAXB binding of `<validationRule>` in `EctMeasurementTypesBean`: a raw
`Vector` gave JAXB no element type, so every definition with a validation
rule unmarshalled to DOM elements and the first cast to `EctValidationsBean`
threw, and the measurement-type import shares that path
(`EctFormPropUnmarshalUnitTest`). Two form-page 500s
found while proving this are fixed alongside it:
the chart's form shortcut now always carries `formId` (0 when the patient has no
record of that form yet, which every form page parses unconditionally), and
Discharge Summary no longer requires a program id in the session. Verified in
the browser: Mental Health Form 14 opens from the shortcut for a patient with
no record, and Discharge Summary opens and saves such prose as a new record.
The form save (`FrmForm2Action`) and the setup check
(`EctFindMeasurementTypeUtil.checkMeasurmentTypes`) also now validate against
the definition file they read rather than `EctFormProp.getMeasurementTypes()`,
a static list refilled by every unmarshal in the JVM, so one provider opening
a form can no longer change the rules another provider's save is checked
against. One nested form page is reported by the generator but exempted
nowhere: `form/pharmaForms/formBPMH.jsp` posts to `/formBPMH`, not a `/form/`
route, so it is outside the generator's scope; and it posts no prose today,
because no page links to it, its fetch path dereferences a handler only the
save path constructs (HTTP 500 measured on the packaged install), and its
prose widgets are `<form:textarea>` tags with no such taglib declared, so
they render as inert text. Repairing it is a form migration of its own, and
its cells then need an exclusion on `/formBPMH`.

Two more defects surfaced while driving the note route and are fixed here,
both verified in the browser through `:443`. Leaving a note with unsaved text
for another note (save-on-switch) rendered an empty view: `ajaxsave()` never
set the `noteTxt` attribute `noteIssueList.jsp` reads, and for a brand-new
note the fragment then threw on `$("nc" + origId)`, because the page renders
the initial note's container as `nc<offset><idx>` (`nc00` for `n0`) while
`newNote()` builds `nc0N`. The action now hands the saved text to the view and
the fragment promotes the container by walking up from the note div it just
renamed. Reopening a saved note for editing (`editNote()`) now HTML-escapes
the decoded text before splicing it into the textarea markup, so a note
holding `</textarea><img onerror=...>` decodes back into the editor as text
rather than closing the element (`CaseManagementCppSaveRegressionTest`). And
the waiting-list page (`waitinglist/DisplayWaitingList.jsp`) now names its row
fields `waitingListBean[<i>].demographicNo/note/onListSince` with their
current values: the plain names left from the Struts 1 `indexed="true"` tags
made `setParameters()` build `waitingListBean[undefined].*` selectors, so
every row update fell through to a reposition and the edit was lost. Verified
on the packaged install: a note holding `& < > 2+2` and a new date persist as
a new `waitingList` row with the old one marked history
(`WLMutation2ActionsTest` pins the page's field names against the action's
selector contract). The action also refuses `update=Y` without a usable
`waitingListId` (missing, non-numeric or non-positive) with a 400: the
legacy null check around the mutation never fired, because the parsed id
defaulted to an empty string, so the reposition and update ran against `""`.
Likewise a row edit whose selectors are present but whose patient number or
date is blank answers 400 instead of falling through to a reposition the
clinician did not ask for. A
quick way to re-survey after a policy change is to POST each field
through `:443` unauthenticated with a value that begins with
`http://10.0.0.5/pacs/study?id=1&cmd=view`: the WAF decides before the
application does, so nginx's own 403 page means blocked and any application
answer (302 to login, its CSRF 403) means the request got through. Scripts driving a free-text clinical field through the front
door should carry text the rule set actually scores (see
`CLINICAL_TEXT_THE_WAF_SCORES` in `scripts/echart-playwright-checks.js`) and
should be confirmed to fail against the previous exclusion file, not merely to
pass against the new one.

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
# Set PROMOTION_REF for each promotion. After the candidate branch is deleted,
# use the retained release tag or exact validated commit.
promotion_ref=${PROMOTION_REF:?Set PROMOTION_REF to the reviewed promotion ref or retained release tag/commit}
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
  apt-get install -y nodejs npm poppler-utils
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

`eform-render-playwright-checks.js` requires `pdftotext` from `poppler-utils`.
It checks both authored PDF pages, content beyond a narrower page container,
and the coordinates of a percentage-positioned field. A PDF header alone
cannot establish that the clinical content was retained. The corpus soak also
records the application's explicit completeness refusal and its issue report;
it does not approve omitted content to obtain a PDF.

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
# Remove this run's unreferenced signature/image and uniquely named uploaded PDF,
# using explicit local database access and the mounted server document directory.
export PRESCRIPTION_SIGNATURE_CLEANUP=true
export EDOC_NAV_DOCUMENT_STORE=/var/lib/carlos-emr/CarlosDocument/carlos/document
# Browser diagnostics omit raw clinical content. eDoc screenshots are disabled by
# default; set EDOC_NAV_SCREENSHOT_DIR only for an explicitly approved test-data capture.
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
# Alpha-11 tester coverage scripts (docs/ui-tests/alpha-11-tester-coverage.md). They default to
# demographic 1 / provider 999998 and clean up after themselves; the few knobs they take:
#   NOTE_DEMOGRAPHIC_NO=2        (echart-note-sign-bill; demographic 1's chart 500s on the demo HRM rows)
#   BILLING_SUBMIT_DATE=2024-05-06 BILLING_OHIP_CODE=A007A BILLING_BONUS_CODE=Q040A (billing-on-submit)
#   BILLING_CODE_EXISTING=A007A BILLING_CODE_NEW=X987Z   (billing-service-code-admin)
#   PREVENTION_BRAND_QUERY=Tdap  (prevention-brand-picker)
#   MACRO_LAB_NO=<lab_no>        (lab-macro-tickler; defaults to the first HL7 lab with a patient)
# On a fresh Ontario install, consultation-request-create and specialist-add-cpso need at least one
# active consultationServices row (the ON seed ships them all inactive; see the coverage page, finding 21).
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
- **`rx-med-history-playwright-checks.js` needs a patient with an existing
  prescription, not a script id.** It reads `RX_MED_HISTORY_DEMOGRAPHIC_NO`,
  falling back to `PRESCRIPTION_DEMOGRAPHIC_NO` and then `1`, and stages
  whichever drug the profile lists first by ticking its ReRx box — so the
  "Rx Examples" window it opens is asked for a drug that HAS history. A
  patient with no drug profile fails the check at staging rather than
  silently measuring the empty state. It stages in memory only: nothing is
  saved, so it seeds and cleans up nothing.
- **`CONSULT_UNSIGNED_REQUEST_ID` is consumed.** The stamp-update scenario
  signs that consultation, so a second back-to-back run needs the fixture
  reset: `UPDATE consultationRequests SET signature_img=NULL WHERE requestId=3;`
- `eform-consultation-acceptance` skips its stored image-layer template probe
  (with a `[skip]` note) unless `LIBRARY_EFORM_NAME` names a form that exists
  in the library; the main acceptance workflow runs regardless.
- `eform-corpus-soak-playwright-checks.js` additionally needs a corpus
  directory (see `docs/eform-corpus-soak-method.md`) and is not part of the
  standard pass.
- **DrugRef refreshes can remove legacy AHFS names.** The allergy browser check uses
  `AMOXICILLIN` (typed) and `CLARITHROMYCIN` (free text), which are present in both
  the demo reference and the current DPD extract. It requires both recording paths
  to produce confirmed warnings; it no longer substitutes an arbitrary search
  result or skips the typed warning. Also run with
  `ALLERGY_CUSTOM_ALLERGEN=PWUNKNOWNALRG ALLERGY_EXPECT_UNCHECKED=true` to require
  an explicit `Not checked` notice for an unresolved allergy. Service failures and
  malformed responses must remain visibly incomplete, never look like a negative check.
  The check also opens another patient in the same browser session and rechecks the
  original tab. `ALLERGY_OTHER_DEMOGRAPHIC_NO` defaults to patient 1 (patient 2 when
  the primary fixture is patient 1); it must name a different existing demo patient.

- **The HRM PDF marker belongs to a specific report.**
  `eform-rtl-attachment-pdf-playwright-checks.js` defaults to `RTL_HRM_DOCUMENT_NO=1`
  and `RTL_HRM_TEXT_MARKER=SEED-HRM-ATTACHMENT-MARKER`. Override both together for
  another fixture; the first listed report can change when missing demo files are restored.

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
- **`echart-print-playwright-checks.js` must be run through `:443`.** It is the
  guard for package exclusion 1010, and like 1045/1050 the defect it pins exists
  *only* behind the WAF: the chart print POSTs the whole encounter form, so CRS
  scored the clinician's own note prose in `ARGS:caseNote_note` and answered
  every print with a 403 — "the chart print button gives a 403 no matter what
  you choose to print" on 2026.08.0-alpha11. Each of its eight note bodies is a
  phrase measured to trip a different CRS family, the pasted-PACS-link body
  included now that 1010 also unhooks attack-rfi; against bare Tomcat they are
  just ordinary notes and the check degrades to covering the print path itself.
  It types into the open encounter note but never saves it as a note; the
  chart's own 5-second draft autosave still posts what it typed as the
  patient's draft, so before it opens the chart it applies the same
  synthetic-patient gate as the free-text check (`FAKE-`/`PLAYWRIGHT-` name
  prefix on `ECHART_DEMOGRAPHIC_NO`, override with
  `ECHART_ALLOW_NON_SYNTHETIC_PATIENT=true`), and it reads the note before its first print and, when
  the prints are done, puts that text back and either writes it back over the
  draft (a clinician's restored draft) or deletes the draft through the page's
  cancel path (a fresh note). Because of that write, its `BASE_URL` guard is
  the same as the free-text check's: loopback only unless
  `ALLOW_NON_LOCAL_BASE_URL=true`, a non-loopback target must be HTTPS, and
  like `billing-on-third-party` it relaxes certificate verification only for
  loopback, so an opted-in host must present a certificate the browser trusts.
  The two dimensions run in sequence rather than as a matrix -- each body once,
  then each selection with the worst-case body, 15 prints in all -- because the
  403 rides on the note in the serialized form, not on any print checkbox.
  Every draft autosave must succeed, including after earlier prints. A 409
  indicates a lost note lock and fails the check, as does a 403. Restoring an
  existing draft during cleanup must also succeed. Each print
  must come back as a download whose bytes start with `%PDF-` and run to at
  least 1 KB: the action sets `application/pdf` before it generates, so the
  Content-Type alone would pass a truncated body. It also waits, per note
  body, for the chart's draft autosave carrying that body, so its `ARGS:note`
  coverage is a POST it observed rather than a timer it assumed; and like
  `echart-playwright-checks.js` it flags whether any response carried the
  nginx `Server` header, warning when none did and failing when
  `EXPECT_FRONT_DOOR=true` is set, so a run against bare Tomcat cannot be
  mistaken for a WAF result. Two things it found on the package are worth
  knowing when reading its output. Printing used to stop the chart's draft
  autosave (the print's form submit fires page-hide, which released the note
  lock; every later autosave answered 409): the chart now holds the lock across
  a print, and the check's per-body autosave status shows 200 throughout on a
  fixed build. And a run that aborts mid-way (a 502, a crash) leaves this
  provider's note lock behind, so the next open of the chart raises the
  "edit this note in another window ... continue?" prompt; the check accepts it
  as a clinician would and lists it under `Notices` rather than failing, so a
  rerun needs no manual `casemgmt_note_lock` cleanup.
- **`clinical-freetext-playwright-checks.js` must be run through `:443`.** It is
  the browser guard for the survey block (exclusions 1100-1199, the clinician
  free text OUTSIDE the eChart), driven through the two rules with the most
  prose: 1100, the consultation request, and 1131, the demographic master
  record. It opens each page, serialises the real form (hidden fields and the
  injected CSRF token included), and replays that body once per prose phrase
  with only the free-text fields swapped — clicking through each form's own
  required-field JS seven times would measure that validation rather than the
  WAF. Its corpus deliberately carries no HTML markup: the survey block keeps
  the CRS XSS family on (pinned by `ClinicalProseWafExclusionRegressionTest`),
  so a `<span>` pasted into a referral is expected to 403 there, and a phrase
  that carried one would fail the check against a correct rule set.
  Both replays are real saves, and the check treats that as its own problem to
  contain rather than the operator's. Its `BASE_URL` guard admits only loopback
  and refuses anything else — a private LAN address, `host.docker.internal` and
  the compose name `carlos` included — unless `ALLOW_NON_LOCAL_BASE_URL=true` is
  set deliberately, and even then a plain-http target is refused because the
  login would send credentials in cleartext; but loopback bounds the host, not
  the data, and a local
  install can hold real patient records. So before its first write it opens the
  master record of `CLINICAL_DEMOGRAPHIC_NO` (default 1) and refuses to run
  unless the first or last name carries the synthetic-data prefix the demo
  dataset writes on every person name (`FAKE-`, see
  `.devcontainer/db/scripts/demo-name-sanitization.sql`) or the `PLAYWRIGHT-`
  prefix the fixture-owning checks use; `CLINICAL_ALLOW_NON_SYNTHETIC_PATIENT=true`
  overrides that only for a record you know to be test data. It then writes the
  corpus, each phrase stamped `(Playwright clinical-freetext run <epoch>)`,
  into the patient's Alert/Notes and puts the original text back at the end,
  on the failure path as well as the success path (the restore is a save
  through the same route and a failed restore fails the run). **The two
  workflows write different things, measured on a packaged install:** the
  demographic save runs in place (a seven-phrase run leaves eight
  `demographicArchive` rows for the patient, the replays plus the restore),
  while the consultation replay **files a new request per phrase** -- seven
  `consultationRequests` rows for the patient per run, each with the phrase
  and the run stamp in its `reason` -- and the check requires the application's
  confirmation redirect back for every one of them. A 200 on that route is the
  form re-rendered with its error alert, which is how the blank-consultant save
  defect fixed in #3623 presented (the front door accepted the prose; the
  application then threw and stored nothing), so the check reports it as a
  failure and points at the application log. There is no delete route for a
  consultation request, so the rows stay until you remove them; the stamp is
  the key:

  ```sql
  -- <n> is CLINICAL_DEMOGRAPHIC_NO (default 1). Ext rows first: no FK cascades.
  DELETE e FROM consultationRequestExt e
    JOIN consultationRequests r ON r.requestId = e.requestId
   WHERE r.demographicNo = <n> AND r.reason LIKE '%(Playwright clinical-freetext run %';
  DELETE FROM consultationRequests
   WHERE demographicNo = <n> AND reason LIKE '%(Playwright clinical-freetext run %';
  ```
  After each workflow's replays it re-opens that page and requires
  its free-text control to render again, because a session that lapsed mid-run
  would answer every replay with an opaque redirect indistinguishable from a
  save. Like
  `echart-print`, it relaxes certificate verification only for loopback, so such
  a target must present a certificate the browser trusts.
  `CLINICAL_CONSULT_SERVICE_ID` (default `1`) names the other record it
  assumes; override it if the install's `consultationServices` table does not
  start at 1. Against bare
  Tomcat the phrases are ordinary notes and the check degrades to guarding the
  two save paths. It flags the packaged front door the same way as the
  eChart checks (nginx `Server` header; warning when absent, failure with
  `EXPECT_FRONT_DOOR=true`), so a loopback run against bare Tomcat is never
  mistaken for coverage of 1100/1131.
- **`echart-playwright-checks.js` allows 90 seconds for note pagination to settle.**
  The chart loads 20 entries per one-second poll, including eForms and other
  chart entries as well as encounter notes. A populated fixture can legitimately
  need more than 30 seconds. `ECHART_NOTES_POLL_TIMEOUT_MS` accepts 5000–240000
  milliseconds for an intentionally larger fixture; the check still requires
  four quiet seconds and a cleared loading indicator, and fails continuous
  pagination at the configured deadline.
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

Two things a same-day rebuild does NOT do for you, both met while validating
#3623. First, `apt-get install --reinstall` of the same version replaces the
files but, with `policy-rc.d` denying starts in the container, leaves the
already-running JVM alone: run `carlos-ctl restart` (or check
`systemctl show carlos-emr -p ExecMainStartTimestamp`) before believing that
what you are exercising is the build you just installed. Second, the package is
built reproducibly, so every shipped file carries the changelog entry's date
as its mtime; Tomcat decides whether to recompile a JSP by comparing that mtime
with the one it recorded at the last compile, and two builds from the same
changelog entry carry the same date, so an edited JSP keeps serving its
previous compiled form. `touch` the JSPs you changed under
`/usr/share/carlos-emr/webapp/carlos/WEB-INF/jsp/` (or clear
`/var/lib/carlos-emr/catalina/work/Catalina/localhost/carlos/`) and Tomcat
recompiles them on the next request. Neither applies to a real upgrade, whose
changelog entry carries a new date.

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

### Demo HRM report files

The demo package includes synthetic HRM XML for all 41 report filenames in the
snapshot, plus `demo-hrm-diagnostic-imaging.xml` for the attachment PDF check.
The legacy `.xml.<timestamp>` filenames are intentional: existing demo installs
can rerun `carlos-ctl demo-data` to bootstrap missing files even when the SQL
completion marker already exists. Both Debian and devcontainer bootstraps leave
existing documents untouched. These files contain invented, explicitly labelled
reports for the FAKE patients; they do not reproduce the old reports' content.

### Ontario consultation catalogue repair

Migration `V1.0.23` activates the accidentally disabled reference catalogue only
when all 257 entries still match the shipped Ontario seed and there are no
non-FAKE patients. It preserves custom catalogues and clinical databases because
`02` is also the administrator's deliberate-disable value. On a configured
clinical installation, an administrator must review the existing service IDs and
explicitly reactivate the intended rows (`active='1'`) through database
maintenance to retain their IDs and specialist assignments. The current
consultation settings offer Add/Delete, without a re-enable control. Automatic
repair cannot infer which existing inactive services were deliberately disabled.
Published migration checksums remain unchanged.
