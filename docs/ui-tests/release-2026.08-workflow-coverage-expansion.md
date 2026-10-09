# Release 2026.08 workflow coverage expansion

This pass added **78 browser checks** for clinician and administrator workflows that
no existing check drove, and ran every one of them against a packaged CARLOS install
through the real front door. It implements backlog rows of
[playwright-coverage-plan-2026.08.md](playwright-coverage-plan-2026.08.md) §2–§4 and
records what the checks found in
[app-findings-log.md §10](app-findings-log.md#10-found-while-expanding-workflow-coverage-on-the-packaged-install-october-2026).

**Why the result column is mostly red.** The suite's rule is *report, don't encode*:
a check asserts the correct behaviour, so a defect makes it fail rather than being
pinned as expected. Where the workflow allows it, a check proves the reachable steps
first and puts the step that hits a confirmed defect last, so a failure names the defect
and the steps before it are evidence that that part of the workflow works. That is not
always possible: a defect on the way in (`waiting-list` and `measurement-map-admin` fail
at their first step) or in the middle (`admin-jobs`, `report-daysheet-labs`) stops the run
before later steps execute. **Only the steps a run reached are validated;** where a row
below names later steps, they are written and assert the correct behaviour but have not
yet run against this install. When a defect is fixed, the check that names it should move
past it with no edit; one that still fails has found the next problem. The manifest's
`notes` field for each failing check says which defect stops it.

## Where it ran

| Item | Value |
|---|---|
| Source | `release/2026.08` at `93054a6242` (2026.08.0-alpha18-SNAPSHOT) |
| Packages | `carlos-emr_2026.09.0~snapshot26_amd64.deb`, `carlos-emr-drugref_2026.09.0~snapshot26_all.deb` built with `dpkg-buildpackage -us -uc -b` in an `ubuntu:26.04` container (DrugRef from the `debian/drugref.pin` revision, pinned Chromium), plus `carlos-ctl_1.1.1_all.deb` from `debian/carlos-ctl.pin` |
| Install | Ubuntu 26.04 container running systemd, `carlos-ctl check` clean; preseed and fixtures as in [deb-install-validation.md §3–§4](deb-install-validation.md), demo dataset loaded, first-login reset completed |
| Front door | Every run used `BASE_URL=https://127.0.0.1/carlos` (nginx + ModSecurity/CRS blocking) and `EXPECT_FRONT_DOOR=true` |
| Browser | The packaged Chromium (`/usr/lib/carlos-emr/chromium/chrome`) with Playwright 1.60.0 |

Notes for reproducing it on a host without LXD:

- **systemd in Docker on a cgroup-v1 host.** Start the container `--privileged --cgroupns host`
  with an entrypoint that remounts `/sys/fs/cgroup` as `cgroup2` and execs `/sbin/init`, as
  [deb-install-validation.md §2](deb-install-validation.md#2-create-the-test-vm) describes.
- **Maven Central rate limiting.** Behind a shared egress the build stalled for minutes per
  artifact on HTTP 429 from `repo.maven.apache.org` and from `repo1.maven.org`, which the
  `wss4j` POM names as `A_maven.central`. A `settings.xml` mirror of `central,A_maven.central`
  to `maven-central.storage-download.googleapis.com` removed the stall. This is an environment
  note, not a packaging change.
- **Extra properties the run staged.** `login_lock=true` (so `account-lockout-unlock` locks
  its throwaway user name, not the runner's address) and `rx_fax_enabled=true`, both in
  `/etc/carlos-emr/carlos.properties` followed by `carlos-ctl restart`. The environment also
  exported `DOCUMENT_DIR`, `INCOMINGDOCUMENT_DIR` and `MEASUREMENT_CSS_UPLOAD_DIR` (the
  packaged `oscarMeasurement_css_upload_path` default, which `carlos.properties` leaves commented).

## How the checks were run

Each check was run on its own with `node scripts/<name>-playwright-checks.js` inside the
container, with the section-6 environment contract of
[deb-install-validation.md](deb-install-validation.md#6-run-the-suite). The full suite runner
was not used for this pass; it runs checks one at a time.

Up to three checks ran at once during development. Every check scopes its assertions to
rows it owns (ids it captured or the run's `FAKE-PW…` marker), so parallel runs do not
interfere. A check whose `fixtures` text says it **must not run concurrently with other
checks** changes or reads clinic-wide state (a system property, a clinic-wide list, the
email sender, global row counts). Those were run alone. The serial suite runner already
satisfies this, so the manifest needs no extra field.

Many surfaces open as popups, so a check's first click is often the one that matters.
Each workflow enters through the control a user clicks, with one deliberate exception noted
in its row (`report-cdm`, whose page no menu links). The GET-refusal and missing-token
probes are requests made directly on purpose, not workflow entry points: they send the
forged request a cross-site attacker would. Separately, SQL-seeded fixtures are test setup,
not requests: they create the rows a workflow starts from.

New shared helpers:

- `scripts/lib/throwaway-login-fixture.js`: a disposable login (security, provider and role
  rows copied from the test login) for checks that lock, change the password of, enrol MFA
  for, or restrict a user, so the shared account is never touched. Its cleanup also removes
  the `ProviderPreference` rows a login creates.
- `scripts/lib/mfa-otp.js`: decodes the enrolment QR in Node and computes RFC 6238 codes
  (tested against the RFC vectors).
- `scripts/lib/playwright-harness.js` `login()`: now answers the real MFA challenge (served at
  `/login` with `#otpInput`, auto-submitted at six digits) and refuses a re-rendered login
  form instead of treating that landing as success.

## Results

| Outcome | Checks |
|---|---:|
| Pass | 21 |
| Fail on a confirmed application defect | 55 |
| Skip (surface gated off on the packaged default) | 2 |
| **Total new checks** | **78** |

Every failing check passes the steps it reaches before the one that hits the defect its manifest note names; steps after that point have not run (see the opening section). Across the pass the checks recorded 64 application defects as findings 53–116 of the findings log: 59 confirmed by a live run and five (107–109, 115, 116) found by code reading and not yet probed. Eight of the live findings are cited by a check that passes; their rows say *reported, not asserted*, meaning the check observed the defect but does not fail on it. The most serious are state changes reachable by a cross-site GET (bulk MRP reassignment, security-role rewrite, bulk note-role update, saved queries, patient sets, preferences, lookup lists, disease registry), missing patient-level authorization on HRM reports, the Ontario billing report rendering the session cookie into the page, drug-drug interaction warnings never being shown in the Rx module, and chart note edits overwriting the previous text instead of adding a revision.

| Check | Plan § | Provinces | Result on the packaged 2026.08 install |
|---|---|---|---|
| `account-lockout-unlock` | §2.2 | all | FAIL on confirmed defects. Fails on 2026.08: Unlock Account answers 500 whenever a login is tracked (SecurityDaoImpl.findByProviderSite duplicate provider_no alias). |
| `admin-role-management` | §2.2 | all | FAIL on confirmed defects. Fails at its last step on 2026.08: TypeError on Assign Role/Rights to Object, the Administration link 403s for plain doctors, and GET FixRolesOnNotes reaches the bulk update (500 on the probe's invalid role). |
| `login-mfa` | §2.2 | all | FAIL on confirmed defects. Fails at its last step on 2026.08: enabling MFA wipes the stored PIN (the disabled PIN field posts no pin), and the security record edit page loads a missing admin/bcArStyle.css. |
| `password-change-preferences` | §2.2 | all | FAIL on confirmed defects. Fails at its last step on 2026.08: the change-password page has no server-side password policy. |
| `provider-record-admin` | §2.2 | all | PASS |
| `security-record-admin` | §2.2 | all | FAIL on confirmed defects. Fails on 2026.08: the edit page loads a missing admin/bcArStyle.css (console MIME error) and Delete Record throws DetachedObjectException, leaving the login in place. |
| `session-heartbeat-timeout` | §2.2 | all | PASS |
| `appointment-group-copy-cut` | §2.3 | all | PASS |
| `appointment-search-history` | §2.3 | all | PASS |
| `my-groups` | §2.3 | all | PASS |
| `schedule-admin-settings` | §2.3 | all | FAIL on confirmed defects. Fails at its last step on 2026.08: the schedule date popup preselects the wrong template and Template Code Setting edit truncates apostrophes. |
| `schedule-views` | §2.3 | all | PASS |
| `waiting-list` | §2.3 | all | FAIL on confirmed defects. Fails at its first step on 2026.08 until the Master Record stops rendering the waiting list read-only when DEMOGRAPHIC_WAITING_LIST=true (edit.jsp wLReadonly). |
| `admin-update-demographic-provider` | §2.4 | all | FAIL on confirmed defects. Fails at its last step on 2026.08: a GET performs the bulk reassignment (HTTP 200). |
| `demographic-cds-import` | §2.4 | all | FAIL on confirmed defects. Fails at its last step on 2026.08: a duplicate import is reported as 'Imported Successfully'. Needs xmllint (libxml2-utils). |
| `demographic-merge` | §2.4 | all | PASS |
| `demographic-relations-pdf-labels` | §2.4 | all | FAIL on confirmed defects. Fails on 2026.08: the WAF blocks the Messenger attachment preview POST to messenger/Doc2PDF (CRS 949110). |
| `patient-set-cohort` | §2.4 | all | PASS |
| `patient-swipe-card-search` | §2.4 | ON | FAIL on confirmed defects. Fails at its last step on 2026.08: ValidateSwipeCard answers 500 on a malformed track instead of a bad request. |
| `antenatal-annual-review-planner` | §2.5 | all | FAIL on confirmed defects. Fails at its last step on 2026.08: the risk/checklist editors load a missing antenatalrecord.css. |
| `decision-support-guidelines` | §2.5 | all | FAIL on confirmed defects. Fails at its last step on 2026.08: the guideline list answers 500 (EL char coercion) and the detail page judges every condition by the first condition's cached rule. |
| `dx-registry-quicklist` | §2.5 | ON | FAIL on confirmed defects. Fails on 2026.08: choosing a named quick list in the registry sidebar answers 500; the later association steps hit the JSON+JSP response and the silently ignored CSV upload. |
| `dx-registry-status-update` | §2.5 | all | FAIL on confirmed defects. Fails at its last step on 2026.08: choosing a quick list in the Disease Registry report adds none of its codes (quicklistname/quickListName mismatch). |
| `encounter-templates` | §2.5 | all | FAIL on confirmed defects. Fails at its last step on 2026.08: Insert a Template ▸ Delete throws DetachedObjectException and deletes nothing. |
| `flowsheet-patient-customization` | §2.5 | all | FAIL on confirmed defects. Fails at its last step on 2026.08: the Health Tracker custom print omits patient customisations (and its Preview POST answers 405). |
| `immunization-schedule-config` | §2.5 | all | FAIL on confirmed defects. Fails at its last step on 2026.08: schedule del/restore forms are nested and dropped by the parser, UI-created templates lose their name, and the pages link missing stylesheets. |
| `measurement-group-entry` | §2.5 | all | FAIL on confirmed defects. Fails at its last step on 2026.08: the group page hides the last value of readings without a measuring instruction (Heart Rate). |
| `note-browser-documents` | §2.5 | all | FAIL on confirmed defects. Fails on 2026.08: the Note Browser page has a JavaScript syntax error (identifiers split across lines) and its controls POST to a GET-only gate; note edits also overwrite rather than add revisions. |
| `patient-photo-upload` | §2.5 | all | FAIL on confirmed defects. Fails on 2026.08: a refused upload shows no error message; Clear Photo answers 500 (DetachedObjectException) and deleteImage accepts GET. |
| `document-refile-combine` | §2.6 | all | FAIL on confirmed defects. Fails on 2026.08: the document Edit popup throws ReferenceError (validDate), Combine PDF posts to a /WEB-INF/ URL (404) and the Document Browser has a JS syntax error. |
| `fax-queue-admin` | §2.6 | all | PASS |
| `hrm-report-print-download` | §2.6 | ON | FAIL on confirmed defects. Fails at its last step on 2026.08: HRM list, print and download serve a locked patient's reports (no patient-level authorization). |
| `lab-forwarding-rules` | §2.6 | ON | FAIL on confirmed defects. Fails at its last step on 2026.08: the Administration Lab Forwarding Rules page binds its provider selector to the wrong id, so no provider's rules can be loaded or saved. |
| `lab-manual-entry-cumulative` | §2.6 | ON | FAIL on confirmed defects. Fails at its last step on 2026.08: Row Display throws ReferenceError (scanDOM) and loads a missing spinner image. |
| `lab-to-flowsheet` | §2.6 | ON | A lab created through Inbox ▸ Create Lab (CML 3767 HEMOGLOBIN A1C, one result within and one above the Diabetes Flowsheet's own target, read from the flowsheet, plus an unmapped CML 2010 hemoglobin) is followed into `measurements` (type A1C, `lab_no`, identifier, unit, date), the chart's Measurements module, the Diabetes Flowsheet after Dx 250 (each result under its date, the one above the target flagged and the one within it not, nothing for the unmapped test) and the Health Tracker; all of that passes. Four known failures each have a pinned entry: the A1C row has no link to the lab it came from (258), a result longer than four characters is filed cut to four, so 0.095 becomes 0.09, by the shipped `HL7_LAB_MEASUREMENT_FILTER` (256), a result observed between 12:00 and 12:59 is filed twelve hours early (259), and an A1C reported as a fraction is never flagged (257). The unmapped test is filed with an empty type (a lab-only record the Lab Result views read), not left out of `measurements`; "does not appear" means no flowsheet type, Measurements line, flowsheet row or tracker card. |
| `billing-on-admin-config` | §2.7 | ON | FAIL on confirmed defects. Fails at its last step on 2026.08: Manage Billing Form Add/bill-type/Delete and dx Search Update are refused for missing CSRF tokens, and the code Search update/confirm pages throw script errors. |
| `billing-on-batch-clipboard` | §2.7 | ON | FAIL on confirmed defects. Fails on 2026.08: Billing Reconciliation ▸ Report posts a runtime-built form without a CSRF token (403), so the billing clipboard is unreachable. |
| `billing-on-correction-delete` | §2.7 | ON | FAIL on confirmed defects. Fails at its Unbill step on 2026.08: the history page submits a runtime-built form without a CSRF token (403). |
| `billing-on-gst-css-benefit` | §2.7 | ON | FAIL on confirmed defects. Fails at its last step on 2026.08: Manage Code Styles drops a hand-typed colour declaration on save. |
| `billing-on-invoice-third-party` | §2.7 | ON | FAIL on confirmed defects. Fails at its last step on 2026.08: picking a payer address throws a JavaScript syntax error (double-encoded handler). |
| `billing-on-ohip-simulation-report` | §2.7 | ON | PASS |
| `billing-on-payment-status` | §2.7 | ON | FAIL on confirmed defects. Fails at its last step on 2026.08: Payment Received omits payments dated on the End Date (< instead of <=), so today's payments never appear with the default range. |
| `billing-on-ra-import` | §2.7 | ON | FAIL on confirmed defects. Fails on 2026.08: the claims error report page answers 500 (missing bean property), and Billing Reconciliation Report/Summary/Settle post a runtime-built form without a CSRF token (403). |
| `billing-on-reports-inr-eoy` | §2.7 | ON | PASS on #4138: all 12 steps, including correct report headers/cells, complete statement PDF, ES/OU MOH rendering and malformed-file errors, INR edit GET, POST save, opener refresh and GET mutation refusal. Owned fixtures removed. |
| `rx-edit-discontinue` | §3.2 | all | PASS |
| `rx-interactions-renal-luc` | §3.2 | ON | FAIL on confirmed defects. Fails at its last step on 2026.08: no drug-drug interaction marker is shown for a DrugRef major interaction (the interaction calls are commented out of the Rx page). |
| `rx-print-profile` | §3.2 | all | PASS |
| `consultation-edit-status` | §3.3 | all | PASS |
| `consultation-services-admin` | §3.3 | all | PASS |
| `messenger-attachments` | §3.4 | all | FAIL on confirmed defects. Fails on 2026.08: the patient-search page closes itself on load (null-safe encoder vs "null" comparison) and throws a TypeError; through the front door the WAF blocks the Doc2PDF preview/attach POSTs. |
| `messenger-demographic-link` | §3.4 | all | FAIL on confirmed defects. Fails at its last step on 2026.08: the Search Patient popup closes itself before listing anyone, and Link to Patient posts a runtime-built form without a CSRF token. |
| `prevention-admin` | §3.4 | all | FAIL on confirmed defects. Fails at its last step on 2026.08: Preventions Print posts to a /WEB-INF/ URL (404). |
| `tickler-forward-filters` | §3.4 | all | PASS |
| `tickler-preferences` | §3.4 | all | FAIL on confirmed defects. Fails on 2026.08: the Preferences link opens the stale-note-date page instead of the tickler preference form (and further defects behind it). |
| `workflow-tickler-suggested-text` | §3.4 | all | FAIL on confirmed defects. Fails at its last step on 2026.08: the patient tickler list (ticklerDemoMain) answers 500 for a patient with a tickler (LazyInitializationException). The WorkFlow step skips while WORKFLOW is off. |
| `clinical-forms-save-reopen` | §3.5 | ON | Records each form and asserts it per concern. The second-Save CSRF refusal, Annual print, MMSE image and Discharge Summary / Mental Health Form 1 defects of the first run were fixed (#4295 to #4298; finding 70 stays open for Print and Save and Exit from a redisplayed page, which no check drives); the entries now also cover Mental Health Forms 14 and 42, Annual V2 for a female and a male patient, the Vascular Tracker and the Mental Health referral / assessment / outcome chain. Known failures (Vascular Tracker 404 and its 400 Save, Forms 14 and 42 script errors, the Mental Health script error, chart reload, reopen 500 and Print) each have a pinned entry; see `app-findings-log.md` findings 103, 236 to 239, 242 and 254. The remaining Ontario forms are `form-catalog-smoke`. |
| `form-rourke2020-growth` | §3.5 | ON | Rourke 2020 and the Growth 0-36m and Growth Chart forms, each asserted per concern for an owned infant: open, keys, save, redisplay, reopen, restore (Rourke: on every page), Rourke's measurement dialog (weights and lengths into `measurements`), the graph PDFs (their pages, points counted from the content streams, every page drawn as a PNG), Print (PDF carrying the typed text). Known failures each have a pinned entry: no head circumference dialog (243), the local date (152), the two Immunization boxes that are not stored (244), `null` visit dates (245), the wrong sex marks (246), gestational age one week short (247), seven page II notes the template declares and never places (248), the empty DOB line (249), Growth Chart Print BMI 500 (250), the wrong title of the lower Length and Weight graph (252) and the CDC title of the Growth 0-36 window (253); unpinned on purpose: slow save and print (251, no agreed budget). A pair fails under its pinned step only for its own defect: a control that cannot be reached, a concern that was not reached and a stray browser problem read as a failure elsewhere. The graph is a PDF, not the PNG the plan assumed. |
| `dashboard-display` | §3.6 | all | FAIL on confirmed defects. Fails at its last step on 2026.08: Assign Tickler 404s on a doubled context path, Add To Disease Registry writes nothing, BulkPatientAction writes on GET, and Drill Down 403s without _dashboardChgUser. |
| `demographic-report-favourites` | §3.6 | all | FAIL on confirmed defects. Fails at its last step on 2026.08: a GET of report/DemographicReport with query=Save Query writes a saved query (no GET rejection, no CSRF on GET). |
| `report-age-sex-visit` | §3.6 | ON | FAIL on confirmed defects. Fails at its last step on 2026.08: the Provider Service Report form replaces the shell's jQuery and throws on its oscarMonth rule. |
| `report-by-template` | §3.6 | all | FAIL on confirmed defects. Fails on 2026.08: Delete Template submits a runtime-built form without a CSRF token (403), and the WAF blocks saving a template with <param> (CRS 941160). |
| `report-by-template-groups` | §3.6 | all | PASS |
| `report-cdm` | §3.6 | all | FAIL on confirmed defects. Enters the CDM report by address like cdm-measurement-report, because no menu links SetupSelectCDMReport (a finding). Fails at its last step on 2026.08: the frequency report prints nothing, 'patients seen' shows a demographic number, and invalid-date errors are lost. |
| `report-daysheet-labs` | §3.6 | ON | FAIL on confirmed defects. Fails after its positive steps on 2026.08: report/ViewReportonbilledvisitprovider changes security roles on a tokenless GET; later steps assert the ignored Non Rostered filter and the sort links that drop the time window. |
| `report-query-by-example` | §3.6 | all | PASS |
| `admin-api-keygen` | §3.7 | all | FAIL on confirmed defects. Fails at its last step on 2026.08: the Key Manager buttons post to /admin/... without the context path (404). |
| `admin-audit-log` | §3.7 | all | PASS |
| `admin-email-config` | §3.7 | all | FAIL on confirmed defects. Fails at its last step on 2026.08: Manage Emails Resolve shows RESOLVED but never updates the log row (setResolved is not dispatched). |
| `admin-facility-messages` | §3.7 | all | PASS |
| `admin-issue-editor` | §3.7 | all | SKIP: on the packaged default (caisi=off hides the Issue Editor); with caisi=on its CPP issue-assignment step fails on 2026.08. |
| `admin-jobs` | §3.7 | all | FAIL on confirmed defects. Fails on 2026.08: clicking a job name raises a page error (javascript:void() link); later steps would fail on the schedule dialog, stored XSS in job names and a DataTables reinitialise alert. |
| `admin-lookup-lists` | §3.7 | all | FAIL on confirmed defects. Fails at its last step on 2026.08: lookupListManagerAction accepts GET for its mutating methods. |
| `admin-messages` | §3.7 | all | SKIP: on the packaged default (caisi=off); its later steps have not run on a caisi=on deployment yet. |
| `admin-sites-clinic-numbers` | §3.7 | ON | FAIL on confirmed defects. Fails at its last step on 2026.08: the administration shell Help ignores the saved Help Link, and the Ontario Billing Settings Save (no options shown) writes NULL rows for the BC-only property keys and invoice SystemPreferences. Manage Sites and clinic numbers have no UI entry on this install (multisites off, rma_enabled=false). |
| `measurement-map-admin` | §3.7 | all | FAIL on confirmed defects. Fails at its first step on 2026.08: View Mapping throws ReferenceError (stripe is not defined); the add/remove/remap pages also answer 405. |
| `measurement-type-group-admin` | §3.7 | all | FAIL on confirmed defects. Fails on 2026.08: the Customize Measurements Add links answer 405 (HttpMethodGuardFilter treats their GET form-openers as mutators). |
| `provider-preferences-cpp-dx` | §3.7 | ON | PASS |
| `provider-signature-contact` | §3.7 | all | PASS |
| `eform-deleted-restore` | §4.1 | all | FAIL on confirmed defects. Fails on 2026.08: Restore posts a JavaScript-built form without a CSRF token (403); independent Delete is also rejected for a missing token. |
| `eform-groups` | §4.1 | all | FAIL on confirmed defects. Fails on 2026.08: the Add eForm list throws a TypeError on unload (unguarded window.opener), and the group mutators answer 405 / 403 (forward to a GET-only gate; missing CSRF token in the Administration panel). |

## Stored-markup (xss-poison) sweep

Ten further checks walk the screens a clinician and an administrator use with **stored values that
contain markup**, to find output-encoding defects that a check typing ordinary text cannot see. Each
check INSERTs its own FAKE rows (a patient, providers, list entries, documents, messages ...) whose
text columns carry an inert payload, `FAKE-XP<i data-xp="N">q</i> "dq" 'sq' \back &amp; </script data-xp>`
(shorter variants for narrow columns; `N` names the field). The rows go in by SQL so the WAF does not
refuse them, the way imported or legacy data arrives. The check then enters every surface through the
controls a user clicks and asserts, per page and frame: the value is shown literally, no `[data-xp]`
element exists, no inline handler or script block carrying the payload fails to compile, and no
script error was raised. Findings are collected across the walk and the check fails once at the end,
so one run reports every defect it reached. Shared code: `scripts/lib/xss-poison-helpers.js`,
`scripts/lib/xss-poison-patient.js`, `scripts/lib/xss-poison-admin-walk.js`.

**Coverage is asserted, not assumed.** Each surface declares the seeded fields it is known to show, and
a surface that does not show them (or cannot be reached) is a `MISSING` finding; a catalogued link that
cannot be opened is `NOT-OPENED` unless the fixture itself cannot back it (a document row with no file, a
dated chart entry the page re-renders), which is noted with that reason. A click that changes nothing is
noted and never inspected as its own destination, and a frame the inspector could not read is
`INSPECT-FAILED`, so none of these can pass as an encoded page. A click that opens a window is inspected
in that window even when the host redraws itself before the window appears (or navigates only after
touching the page), and a required module that lives in a window of its own (the E-Chart and Master
Record modules) counts only when it opened that page: the host's copy of the same values never stands
in for it. Administration items, which load into the page's own content frame, may open in place. An aborted subresource request is excused only when its document provably went away (the
frame moved on, was removed, or its page closed); any other `ERR_ABORTED` is a `REQUEST-FAILED`
finding. The summary also lists the seeded fields
no surface reached. The schedule check and both Administration halves wait out the five-minute
active-provider cache once each (about fifteen minutes of a full sweep), so the provider selects are
inspected with the fixture present.

**Report, don't encode** applies as everywhere else: a check passes when the screen encodes and fails
at the defect. The one allowance is a field a page renders as sanitised rich text by design (the
Messenger body is Markdown shown through DOMPurify): markup there is noted, not counted.

**Fixture lifecycle.** The fixtures are global while a check runs (an active provider shows up on
every provider select), and other checks' forms that echo them back are refused by the WAF, so run an
`xss-poison-*` check alone; the suite runner is sequential. Every row is recorded by its key in a
per-run ledger under `$TMPDIR/carlos-xss-poison` (`XSS_POISON_LEDGER_DIR`) and cleanup deletes exactly
those keys and asserts them gone, also after a failed run. Natural-key inserts refuse a key that
already exists. A run killed outright is recovered by key, from its ledger, by the next `xss-poison`
run; when no other `xss-poison` run is alive that run also sweeps every row carrying `<i data-xp=`
(with the rows hanging off such patients and providers) and asserts none is left. Each check owns a
range of payload numbers, so a concurrent run's rows are never attributed to it. Rows a check borrows
rather than inserts itself (the billing check's session patient and the billing provider, provider
sites, claim and items of `createBillingFixture`) are recorded in the same ledger before the first
write, so a killed billing run is recovered by key like any other; a check opens its ledger (and with
it the first-run payload sweep) before it poisons anything, so the sweep can never neutralise the run's
own fixture. `scripts/xss-poison-helpers.test.js` pins these rules.

| Check | Provinces | Result on the packaged 2026.08 install |
|---|---|---|
| `xss-poison-master-record` | all | FAIL on confirmed defects (findings 117, 118): Master Record view and Edit form, Documents and Manage Contacts print stored values raw. Create Invoice is skipped because the WAF refuses its URL, which carries the patient name. |
| `xss-poison-echart` | all | FAIL on confirmed defects (findings 117, 119, 120, 128): left navbar titles (Rx, Tickler, eForms), the Rx drug list, the Allergies page, the Disease Registry, Documents, CDM Indicators flowsheet, cumulative lab (Grid/Row Display) and measurement history headers, and the consultation form the chart opens. |
| `xss-poison-schedule` | all | FAIL on confirmed defects (findings 123, 126, 127): the month view's provider select and holiday name, and Schedule Setting ▸ Template Setting's template select. Day sheet, week view, appointment popups, tickler list and edit, Add Appointment, Search and the Schedule Setting provider selects encode. |
| `xss-poison-documents-inbox` | all | FAIL on a confirmed defect (finding 117): Master Record ▸ Documents. Inbox, eDoc provider list and document Edit encode. |
| `xss-poison-eform` | all | FAIL on confirmed defects (findings 121, 125), including the Deleted patient-independent list; the Deleted eForms list also hits finding 77 (TypeError on unload). |
| `xss-poison-messenger-consult` | all | FAIL on confirmed defects (findings 117, 120, 78, 124): Msg inbox patient name; the consultation form's letterhead script breaks; Compose's Search Demographic popup closes itself on load; a specialist whose specialty type is text cannot be opened (500). Message view, consultation list, specialist list and services encode. |
| `xss-poison-billing` | ON | PASS: Billing History, Create Invoice, Invoice Reports, Billing Correction with its diagnostic-code and payer searches, Manage Billing Service Code and the billing administration lists encode. Only the billing form's group name is not shown by any surface it reaches. |
| `xss-poison-admin-detail` | all | FAIL on confirmed defects (finding 122): group members and document types. The report template run page, description templates, Insert a Template and the dx quick list encode. |
| `xss-poison-admin-users-billing` | all | FAIL on a confirmed defect (finding 121): the eForm upload form's role select. |
| `xss-poison-admin-reports-system` | all | FAIL on confirmed defects (findings 63, 122): ten Administration report and system pages. |

## Routes with no UI entry

`report/GenerateSpreadsheet` was retired under #3965. The
`patient-retired-spreadsheet` regression checks that authenticated GET and CSRF-valid
POST requests return 404 without patient data or a download.

Per the suite's rule a route with no link gets no check; each is a finding about the route.
The checks' authors confirmed these by source search and by the absence of an opener on the
live install:

- **Reports:** `RunClinicalReport` / `RemoveClinicalReport` / `ViewClinicalExport`, the
  `report/ViewReportForm*` / `ViewReportFilter` / `ViewReportResult` designer chain,
  `report/ViewGenerateLetters` and the letters chain, `report/printLabDaySheetAction`,
  `ViewReportecharthistory`, `ViewReportedblist`,
  `ViewOscarReportDxReg`, `ViewSelectCDMReport`, `ViewCDMReport`, `ViewEditCodeDesc`,
  `oscarReport/reportByTemplate/ViewListTemplates`, `exportTemplateAction`.
- **Patient sets and records:** `demographic/ViewAddDemoToPatientSet`,
  `ViewDemographicCohort`, `report/CreateDemographicSet`, `DemographicSetEdit`,
  `SetEligibility`, `demographic/AddRelation` / `DeleteRelation` (with the new contacts UI on),
  `messenger/ImportDemographic`, `messenger/Transfer/SelectItems` / `PostItems`.
- **Chart and clinical:** `casemgmt/ViewIssueSearch`, `ViewShowHistory`, `ViewHistoryview`,
  `OscarChartPrint`, `encounter/ViewTimeOut`, `encounter/decisionSupport/ViewGuidelineList` /
  `ViewGuidelineDetail`, `encounter/immunization/ViewSchedule` / `ViewScheduleConfig`,
  the `adminFlowsheet/ViewFlowsheet*` chain, `SelectMeasurementGroup`, `DeleteData`,
  `rx/ViewCompleteMedRec`, `rx/ViewInteractionDisplay`, the BC antenatal planner routes.
- **Documents, labs, eForms:** `documentManager/ViewMultiPageDocDisplay`, `ViewAddDocument`,
  `oscarMDS/SendMRP`, `eform/efmOpenEformByName`, `efmpatientformlistsingle`.
- **Administration and preferences:** `admin/ViewLookupLists*`, the waiting-list management
  page, `provider/ViewProviderDefaultDxCode`, `ViewPreferenceAction`, `UserPreference`,
  `PrinterList`, `Provider/showPersonal`, `EditAddress` / `EditFaxNum` / `EditPhoneNum`,
  `appointment/appointmentaddrecordcard` / `appointmentaddrecordprint`,
  `billingShortcutPg1View` (so no check was written for the billing shortcut).
- **Billing:** `ViewGenReport`, `ViewGenGroupReport`, `ImportOnRA`, `ViewGenRASummary` /
  `ViewGenRASummaryDetail`, `BillingInvoice`, `ViewBillingON3rdPayments`,
  `Add3rdPartyPayment`, `BillingEditWithApptNo`, `inr/DbINRbilling`,
  `specialtyBilling/fluBilling/DbAddFluBilling`, `ViewBillingOBECEA`.
- **Gated off on the packaged default:** everything behind `caisi=on` (System Messages,
  Issue Editor, Facility Messages, Default Encounter Issue, Lookup Field Editor and all of
  PMmodule, so no PMmodule checks were written), `moveMOHFiles`
  (`moh_file_management_enabled`), Manage Sites (`multisites`), clinic numbers
  (`rma_enabled`), and the legacy `/admin/ViewAdmin` page, whose only opener is the month
  view's Alt+A shortcut, which does not fire in Chromium.

`admin-messages` and `admin-issue-editor` are written and skip on the packaged default for
the CAISI gate; a `caisi=on` profile is needed to run them, as §4.3 of the plan already says.
