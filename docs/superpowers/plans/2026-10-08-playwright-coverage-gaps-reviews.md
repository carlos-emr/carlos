# Playwright Coverage Gaps (2026.08): Mapping Reviews

Appendix to `2026-10-08-playwright-coverage-gaps.md`. These are the five read-only reviews (2026-10-08) the plan's gap inventory is built from, kept verbatim except that absolute paths are made repo-relative. Each gap gives the user path, the routes, a proposed check and its fixtures. Defects marked "suspected" or "code reading" were not probed live; Task 1 of the plan confirms them before they are logged.

## Cross-cutting

```text
CROSS-CUTTING COVERAGE GAPS: CARLOS EMR Playwright suite (release/2026.08 harness branch, read-only review)

The manifest has 496 checks. By tier: core 462, smoke 12, front-door 9, extended 18, standalone 4 (2 of them manual). By province: all 410, ON 83, BC 3.

GAPS (most important first)

[P1] Nothing runs the suite automatically — NOT COVERED
  Path: `.github/workflows/script-regressions.yml` runs only `node --test scripts/*.test.js` and three isolated Chromium fixtures (rtl-measurement, eform-toolbar, calendar-eform). No workflow runs `run-playwright-suite.js` and there is no nightly job. The manifest calls `smoke` "the CI tier", but nothing wires it up, and the plan §0 lists the hosted smoke job as backlog.
  Routes: n/a. All tiers run only by hand on a packaged VM (deb-install-validation.md §6, through :443 with `EXPECT_FRONT_DOOR=true`). Only about 10 checks call `watchFrontDoor`, so the rest would also pass against bare Tomcat.
  Proposed check: a `playwright-smoke.yml` on pull requests, built from the tomcat-dev and mariadb-dev images that container-images.yml already builds and tests, plus a nightly `--tier core` run on develop and release/**. Cheapest first step: add `node scripts/run-playwright-suite.js --tier standalone` to script-regressions.yml. Those 4 checks (eform-admin, eform-page-exclusion, eform-runtime-compat, hrm-window) need no server, and that job already installs Chromium.
  Fixtures/blockers: Claude is denied writes to `.github/**`, so a maintainer must commit it. Smoke has a 3,600 s budget. On BC, smoke passes only 9 of 12 because of finding 146. The front-door tier needs the .deb, nginx and ModSecurity in CI.

[P1] Upgrade path (previous release to this one) and migrations on a populated database — SHALLOW (deb-upgrade-baseline.sh / deb-upgrade-verify.sh, manual)
  Path: the last recorded upgrade runs are a11→a12 and a13→a14. The alpha19 promotion did fresh installs only. `deb-upgrade-verify.sh` still defaults to a11→a12 (`EXPECT_FLYWAY=23`, `EXPECT_NEW=1.0.20..23`).
  Routes: db-schema-verify.yml migrates a fresh database and an "adopted" V1.0.2 baseline that holds seed data plus a few sentinels. It never migrates the demo or clinical dataset, and never starts from the previous tag's applied schema. o19-migrated-smoke was skipped in alpha19 for lack of a fixture. No backup→restore round trip is followed by a re-run of the checks.
  Proposed check: `deb-docker-validation.sh upgrade` to install the previous published tag with demo data, run a set of mutating checks that keep their rows, snapshot, upgrade, run deb-upgrade-verify (expected values taken from the flyway_schema_history diff, not defaults), then smoke plus read-only core. Also a db-schema-verify leg that loads the additive demo artifact at the previous tag's migration set, migrates to HEAD, and asserts row counts.
  Fixtures/blockers: published N-1 packages; an OSCAR 19 import fixture.

[P1] PHI in logs and error bodies (phi-in-error-pages) — SHALLOW (error-sanitization: two provoked 500s)
  Path: `deb-server-log-audit.sh` reduces only ERROR, FATAL and SEVERE events to signatures and never reads message text. PHI logged at INFO, WARN or DEBUG is therefore invisible. Findings 141 (note text in the journal) and 144 (Rx instructions) were caught only because they happened to log at ERROR.
  Routes: every route family. No check provokes 400/403/404/405 per family and inspects the response body.
  Proposed check: `phi-in-error-pages-playwright-checks.js` provokes each status per family and asserts the body carries no fixture HIN, FAKE- name or demographic_no. Add `deb-server-log-phi-scan.sh` to search every log level for this run's markers, FAKE-PW names and fixture HINs, printing counts and logger only. Extend audit-log-chart-read with the `CaseManagementEntry?method=history` path (Note Search / CaseManagementView), so finding 141's `log` row must name the patient.
  Fixtures/blockers: none beyond the existing fixtures; the log scan runs on the deb host.

[P1] WAF false positives on clinical prose (waf-clinical-text-corpus) — SHALLOW (clinical-freetext)
  Path: `REQUEST-900-EXCLUSION-RULES-BEFORE-CRS.conf` has 62 exclusion rule ids over 53 routes. clinical-freetext replays its phrase corpus through only 1100 (consultation request) and 1131 (master record alert/notes). Other saves pass through :443 only with marker text that never trips CRS. The alpha19 run also hit a WAF 403 on a numeric id (msgId).
  Routes: tickler/DbTicklerAdd, tickler/EditTickler, rx/writeScript, rx/UpdateScript, rx/addAllergy2, encounter/MeasurementData, prevention/AddPrevention, documentManager/ManageDocument, oscarMDS/UpdateStatus, messenger/CreateMessage, appointment/AddRecord, appointment/UpdateRecord, CaseManagementEntry, eform/addEForm, and the billing comment routes (1134–1137).
  Proposed check: `waf-clinical-text-corpus-playwright-checks.js` (front-door tier), table-driven over the exclusion list, asserting each save lands in the database through nginx. Also `waf-exclusion-routes.test.js`: every `@rx ^/carlos/<route>` must still resolve to a Struts action or servlet mapping. All resolve today; I checked. A renamed route silently brings back a bare nginx 403 with no application log line.
  Fixtures/blockers: the corpus check needs the deb VM; the static test runs anywhere.

[P1] Write-side authorization and tokenless replay (role-privilege-matrix write half, csrf-negative-matrix) — SHALLOW (authz-read-* x6, read-only; 38 checks with ad-hoc tokenless POSTs)
  Path: authz-read-role-matrix covers GET/HEAD for er_clerk, receptionist, nurse and doctor. Nothing posts mutations as a role that lacks write rights. Nothing asserts that menus are hidden (no Rx or Bill links, no Billing or Administration in the top bar).
  Routes: no tokenless-replay check covers appointment AddRecord/UpdateRecord/delete, demographic add/update, Ontario bill save, eform/addEForm, messenger send, RequestConsultation, AddPrevention or measurement save. `/ws/rs/*` is excluded from CSRFGuard (unprotected list). Session-cookie REST mutations rely only on `SameSite=Lax` plus a JSON content type, and no check posts form-encoded or text/plain to them.
  Proposed check: `authz-write-role-matrix` and `csrf-negative-matrix` families on authz-read-fixture and mutator-routes.js. For each family, the UI action writes a row; a replay without the token, or as an unprivileged role, gets 403 and writes nothing. Add a `/ws/rs` text/plain row and a menu-visibility step.
  Fixtures/blockers: throwaway logins (authz-read-fixture already provides them).

[P1] Unpinned 2026.08 findings on safety, PHI or money that are cheap to pin — NOT COVERED or SHALLOW
  Path and proposed pin for each, by finding:
  - 150: extend oauth-rest-surfaces with a scoped token calling an out-of-scope endpoint, expecting 403.
  - 149: an xss-poison lab row from lab-manual-entry-cumulative, then hover the Row Display tooltip; no injected element may appear.
  - 156: patient-photo-upload with two patients' photo popups open; Clear Photo in the first must keep the second patient's `client_image`.
  - 163: `get-reject-keygen-create`: GET `admin/ViewKeygenCreateKey?name=` must add no `publicKeys` row. admin-api-keygen probes only the specialist update.
  - 165: pathnet-status seeds F and P only separately; seed one message with both and assert it is not listed as Final.
  - 173: billing-on-premium-payment-date covers premiums only; add a radetail row paid on the month's last day.
  - 176: flowsheet-patient-customization should assert a flowsheet-level decision-support message still shows after customizing.
  - 178: allergy-add-penicillin asserts `regionalId || atc` on the first row only; assert both, on the amendment row too, and with no RxAddAllergy2Action ERROR.
  - 168: a throwaway login without `_edoc` x submits an eForm with Add to documents; it must not get the replay 409 text.
  Fixtures/blockers: all use existing fixtures except 168 (one throwaway role).

[P2] Province matrix — SHALLOW
  Path: 410 checks claim `all`, but the alpha19 BC container ran only smoke (9/12 pass) plus the 3 BC checks. About 400 `all` claims have never run on BC, and finding 146 broke 3 of 12 smoke checks there. `other` installs the ON schema; it is validated only as an installer debconf assertion (P3).
  Routes: everything except the 83 ON-only and 3 BC-only checks.
  Proposed check: a `--tier core --province BC` pass on the `deb-docker-validation.sh` BC container at every promotion. A manifest test should require a recorded BC result before a check may claim `all`.
  Fixtures/blockers: BC demo dataset exists via `carlos-ctl demo-data`; finding 146 first (or a hit-test-safe click helper).

[P2] Configuration modes never exercised — NOT COVERED
  Path:
  - `login_lock`: validation forces `login_lock=true`, so the shipped address-keyed lockout (where finding 162 lives) never runs.
  - `multisites=on`: never run; 22 JSPs branch on it.
  - CAISI: `caisi=off`, so PMmodule ProgramManager, ProgramManagerView, ClientManager, FacilityManager and ProviderInfo are untouched, and encounter-legacy-note-soft-wrap is skipped.
  - `mfa.legacy.pin.enable=false`: never run.
  - facility-selection: extended tier only.
  - Locales: fr, es, pl and pt_BR exist; only 4 checks set fr.
  Routes: PMmodule/*, admin/UnLock, the multisite schedule and consultation PDF branches.
  Proposed check: runner profiles (`--profile multisite|caisi|address-lock|no-pin`) that set the property through carlos-ctl, restart, then run smoke plus `multisite-schedule-visibility`, `caisi-program-smoke` (read-only, per plan §4.3) and `account-lockout-address-mode`.
  Fixtures/blockers: restart between profiles; for CAISI, confirm the project keeps it.

[P2] Known-failure bookkeeping — SHALLOW (prose in manifest `notes`)
  Path: 133 manifest notes mention failing, and none is a structured field citing a finding. In alpha19, triage of 94 failures was manual. Stale notes: eform-groups now fails on #4130's 302, not the 405/403 its note describes; gap-clinical-calculators-coronary was stale until fixed.
  Routes: n/a.
  Proposed check: a manifest field `expectedFailure: {finding, step}`. The runner reports known-fail-at-step, unexpected-pass and failed-elsewhere. A test, like app-findings-log.test.js, requires each field to cite a finding row.
  Fixtures/blockers: none.

[P2] Seed and demo-data contract (demo-dataset-contract.test.js, planned in §2.1, absent) — NOT COVERED
  Path: `_newCasemgmt.episode` is `o` for doctor in the Flyway seeds (V1.0.2 for ON and BC) but `x` in development.sql. That is why episode-lifecycle and record-access pass in the devcontainer but fail on packaged installs. Maintainers should decide whether doctors really lack Episodes on a fresh install.
  Other cases: form-print-pdf (Lab Req 2007 is not in the Forms menu); popup-opener-master-record (the fixture patient has no postal code). Finding 143's orphan `casemgmt_note_link` rows break tickler-note-dialog. Likely the same root (inferred, not verified): stored-document-mutations' cleanup refused its fixture because a note reference attached to it.
  Routes: n/a.
  Proposed check: a static test that diffs secObjPrivilege between the Flyway seed, development.sql and the additive artifact for the privileges checks rely on, and asserts no `casemgmt_note_link` points at filtered tickler or document ids. Checks that need a privilege should grant their own throwaway role.
  Fixtures/blockers: none.

[P2] Fixture hygiene and interruption safety — SHALLOW (graceful-signal-cancellation exists in the harness but is not adopted everywhere)
  Path: fax-configure saves a fake SRFax account with the gateway enabled and polling on, and never restores it. That caused finding 180: 189 ERRORs in 3 hours. It is labelled `assertsDatabase:false`, and the manifest has no `mutates` field. No post-suite residue audit exists.
  57 manifest checks have no signal-safe cleanup, 11 of them database-asserting, including smoke appointment-lifecycle. Mutating checks among the rest include fax-configure, eform-admin-crud, eform-image-delete, prevention-add-data and schedule-setting.
  Routes: admin/ViewConfigureFax.
  Proposed check: fax-configure snapshots and restores the `fax_config` row (or at least turns polling off). Add a `mutates` manifest field, a runner `--residue-audit` post-step (marker rows, fax_config, global properties), and migrate the 57 to `runCheck`.
  Fixtures/blockers: none.

[P2] Browser and front-door harness fragility (from alpha19) — SHALLOW
  Path:
  - double-submit-eform: Chromium 154 blocks `beforeunload` without a user gesture. Click in the page before navigating, and record the browser version in results.
  - export-content-eform-export-zip: Chromium named the download "download" although `Content-Disposition` was valid. Assert the response header, not `suggestedFilename()`.
  - gap-provider-messenger-write-to-encounter: the WAF answers 403 for `msgId=2147483648`. Classify a WAF refusal (front-door-checks.js) as a refusal and verify no row.
  Routes: eform/addEForm, eform export, messenger write-to-encounter.
  Proposed check: fixes in those three scripts plus a harness `browserVersion` field in results.
  Fixtures/blockers: none.

[P2] Direct-response contract and servlets outside the Struts measurement — NOT COVERED
  Path: "direct-response-contract" appears only in comments in run-playwright-suite.js and playwright-surfaces.test.js. It has no script and no manifest entries. About 37 download checks exist individually. Servlets are not Struts actions, so untouched-by-module.json misses them entirely:
  - `/servlet/BackupDownload`: Administration ▸ Backup Download, which serves full database backups.
  - `/servlet/OscarDownload`: OHIP claim disk and OBEC downloads from billingONMRI.jsp and obec.jsp.
  - `DocumentTeleplanReportUploadServlet` (BC) and `contentRenderingServlet`.
  These are unit-tested (BackupDownloadUnitTest, OscarDownloadUnitTest) but never through the filter chain or the front door.
  Routes: the servlets above.
  Proposed check: `direct-response-contract-playwright-checks.js` with one row per control. A role without `_admin.backup` (or `_billing`) is refused; traversal `filename` and `homepath` values are refused; the response has magic bytes and `Content-Disposition`; there is never an HTML error page inside a download.
  Fixtures/blockers: a synthetic backup file and an OHIP disk file in the configured directories.

[P2] Cancelling a confirm() writes nothing (dialog-confirmations) — SHALLOW (accept paths only)
  Path: appointment-lifecycle, tickler-crud, billing-on-correction-delete (Unbill) and document-upload assert the accept path. They never assert that Cancel leaves the row.
  Routes: appointment delete/cancel, tickler delete, Unbill, document delete, eForm delete.
  Proposed check: `dialog-confirmations-playwright-checks.js` dismisses each confirm and asserts the row is unchanged, then accepts and asserts it changed.
  Fixtures/blockers: the existing owned fixtures.

[P2] Other unpinned findings, cheap — NOT COVERED or SHALLOW
  Path and proposed pin for each, by finding:
  - 154: dashboard-display types the server's `MM-dd-yyyy` straight into the field; drive the picker instead (SHALLOW).
  - 152: demographic-measurement-modal.test.js under `TZ=America/Vancouver` with a 23:00 clock.
  - 161: report-cdm with a measurement created today and end date = today.
  - 157: visit the chart Disease Registry first, then the report adds one code; assert only that code is added.
  - 159: replay with a foreign `extraReviewerId`; the stored reviewer must be the logged-in user.
  - 151: prescribe the same drug twice by search; the E-Chart Medications panel shows it once.
  - 166: add an HDL ≥ total cholesterol case to framingham-ukpds-calculator.test.js.
  - 172: static nginx test for a `limit_req` on `/ws/services`.
  - 174/175: debian/assets/tests packaging-contract tests.
  Fixtures/blockers: none.

[P3] Remaining small findings — NOT COVERED
  Path and proposed pin for each, by finding:
  - 136: schedule-views clicks a Month week-number link and expects no 404.
  - 142: authz-read-role-matrix asserts the schedule's ViewTabAlertsRefresh answers 200 for roles without `_msg`.
  - 143: check-demo-additive asserts no orphan tickler links.
  - 167: Rx Info with an unknown DIN still shows a name search.
  - 169: double-submit-eform asserts "patient's eForms". Today it only checks "Check the patient", which also matches the broken text.
  - 170: measurement-stylesheet-delete adds an unused-stylesheet delete step.
  - 177: an eForm letterhead render asserts the doctor_contact_* values are non-empty.
  Fixtures/blockers: none.

[P3] Live-external tier — NOT COVERED
  Path: no manifest check has the `live-external` tier. scripts/e2e/fax/* (SRFax loopback) sits outside the manifest; `SRFAX_LIVE` is honoured only by fax-configure. MCEDT (15 routes), dhir/submit and common/OntarioMDRedirect have neither stubbed nor live checks.
  Routes: mcedt/*, dhir/submit, common/OntarioMDRedirect.
  Proposed check: register the e2e/fax scripts as `manual` live-external entries; add `mcedt-upload-stub` and `dhir-submit-stub` with stubbed transport.
  Fixtures/blockers: transport stubs, credentials, cost.

[P3] Responsive layout, accessibility, i18n walk — NOT COVERED or SHALLOW (echart-note-sign-bill checks Save at 1366×768; encounter-header-i18n checks fr only)
  Path: no axe-core in package.json. Finding 146 (on BC the content pane covers the master record's navigation links) would have been caught by a hit-test check. The page-health scan for `???key???` runs in English only.
  Routes: login, schedule, master record, chart, Rx, inbox, bill form, admin.
  Proposed check: `responsive-hit-test` (`elementFromPoint` on each primary control at 1280×720 and 1366×768), `accessibility-smoke` (report-only), and `i18n-locale-walk` (page-health engine under fr, es, pl and pt_BR).
  Fixtures/blockers: axe-core dev dependency.

FINDINGS 136–181 WITHOUT A CHECK
- Already pinned: 140, 145, 146, 147, 148, 158 (authz-read-chart-note-history), 171 (get-reject-inbox-queue).
- Cheap to pin, listed above: 136, 141, 142, 143, 149, 150, 151, 152, 154 (shallow), 156, 157, 159, 161, 163, 165, 166, 167, 168, 169, 170, 172, 173, 174, 175, 176, 177, 178 (shallow).
- Not cheap or blocked: 153 and 164 (BC adjust/receive payment; need BC billing fixtures), 155 (group-note fixture), 160 (pool-pressure race), 162 (needs the address-lock profile).
- Log-level only (138, 139, 144, 179, 180, 181): these are tolerated through server-log-baseline.tsv. Pin them by deleting the baseline row when fixed; no check is needed. 141 and 144 are also PHI and belong to the log-scan gap.

DEAD ROUTE CANDIDATES: providercontrol displaymode=encounter, saveencounter, savebill, savedeletetemplate, ar1 and ar2 (finding 137: their JSPs are missing and they answer 500); servlet `/servlet/io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.ScatterPlotChartServlet` (no caller); servlet `/servlet/io.github.carlos_emr.DocumentMgtUploadServlet` (no caller).

ALREADY COVERED (planned items the plan or the measurement shows as open)
- schedule-shortcuts-popups → gap-provider-schedule-keyboard-shortcuts and page-health-schedule-topbar
- page-script-integrity → page-health-* (12 checks) and csrf-bootstrap-audit (runs in CI via test:scripts)
- opener-refresh-contract → popup-opener-* (9 checks) and echart-prevention-row-links
- role matrix, read half → authz-read-role-matrix and 5 other authz-read-* checks
- login-mfa, session-heartbeat-timeout, account-lockout-unlock and password-change-preferences have landed
- the runner already guards build identity across restarts
- the `report/reportDownload` servlet → reached via report/ViewReportResult, covered by report-by-template (CSV and XLS bytes)

Key files:
scripts/playwright-suite.json
scripts/run-playwright-suite.js
.github/workflows/script-regressions.yml
.github/workflows/db-schema-verify.yml
scripts/deb-upgrade-verify.sh
scripts/deb-upgrade-baseline.sh
scripts/deb-server-log-audit.sh
debian/assets/modsecurity/REQUEST-900-EXCLUSION-RULES-BEFORE-CRS.conf
src/main/webapp/WEB-INF/Owasp.CsrfGuard.properties
scripts/fax-configure-playwright-checks.js
scripts/dashboard-display-playwright-checks.js
scripts/allergy-add-penicillin-playwright-checks.js
scripts/double-submit-eform-playwright-checks.js
database/mysql/migration/on/V1.0.2__on_data.sql
.devcontainer/db/scripts/development.sql
docs/ui-tests/deb-install-validation.md
docs/ui-tests/app-findings-log.md
```

## Chart and clinical

```text
GAPS (most important first)

[P1] Two charts open in one session write to or show the wrong patient — NOT COVERED
  Path: Master Record (patient A) ▸ E-Chart, then Search ▸ patient B ▸ E-Chart in a second window. Back in A's chart: save a popup so the navbar reloads, then use navbar "+" links, Measurements ▸ history, Forms ▸ Vascular Tracker (SetupForm), Plot.
  Routes: encounter/display* (navBarLoader posts no demographicNo; EctDisplayAction falls back to the session-wide EctSessionBean), encounter/oscarMeasurements/SetupHistoryIndex, SetupDisplayHistory, DeleteData2, ScatterPlotChartServlet, form/SetupForm. FrmSetupForm2Action replaces the request's demographic_no with bean.getDemographicNo() whenever a bean exists.
  Proposed check: echart-two-patient-isolation-playwright-checks.js — the chart-side twin of rx-stash-patient-isolation. Asserts that after B is opened, A's reloaded navbar, history, graph and form show only A's rows, and that every write made from A's window (measurement, Dx, CPP item, prevention "+", form save, history Delete) lands on demographic_no=A and leaves B untouched. Likely to FAIL: this is a new finding, not in the findings log.
  Fixtures/blockers: two owned FAKE- patients, each with one measurement and one form row.

[P1] Leaving the chart with an unsaved note, and getting the draft back — NOT COVERED
  Path: E-Chart ▸ type a note (it autosaves after about 5 s) ▸ Exit (closeEnc asks "exit without saving?"). Also close the window or kill the context, then reopen the E-Chart.
  Routes: CaseManagementEntry method=autosave (casemgmt_tmpsave), casemgmt/ViewForward draft restore, method=save.
  Proposed check: echart-note-draft-recovery-playwright-checks.js — dismissing the confirm keeps the window and the text; accepting closes it and leaves exactly one tmpsave row with the typed text; reopening (also after a kill with no beforeunload) puts the exact text back in the editor; Save writes casemgmt_note and deletes the tmpsave row; another provider never gets this draft. (echart-note-editor only proves the autosave POST.)
  Fixtures/blockers: owned patient; clear tmpsave and lock rows.

[P2] Verify & Sign, the appointment status it sets, and note dates — NOT COVERED (echart-note-sign-bill does Save, Sign & Save and Bill only, ON only)
  Path: Schedule ▸ appointment "E" ▸ E-Chart ▸ note ▸ "Verify & Sign" (#signVerifyImg); Sign & Save from an appointment; edit observationDate (back-dated and future) and encounter type.
  Routes: CaseManagementEntry method=saveAndExit (sign=on, verify=on); provider/providercontrol day sheet.
  Proposed check: echart-note-verify-appointment-status-playwright-checks.js — the note is signed and carries the verify signature line; appointment.status gains "S" after Sign and "V" after Verify (ApptStatusData.signStatus/verifyStatus) and the day-sheet icon matches; a note with no appointment changes no appointment; a back-dated observation_date persists and orders the note; a future date is refused with encounter.futureDate.Msg; encounter_type persists.
  Fixtures/blockers: one owned appointment on today's sheet.

[P2] Issues on chart notes: assign an issue, list it in Issues / Resolved Issues, filter notes by it — NOT COVERED on the default profile (echart-navbar-modules only renders the modules; admin-issue-editor drives #issueAutocompleteCPP but SKIPs at caisi=off and fails with caisi=on)
  Path: E-Chart ▸ CPP box "+" ▸ "Assign Issues" ▸ pick an ICD-9 issue (e.g. 250) ▸ Sign & Save; right nav Issues ▸ click the issue (setIssueCheckbox + filter(false)); click the heading to reset; resolve the issue.
  Routes: CaseManagementEntry method=issueList / issueNoteSave / issueChange / edit (chain=list), encounter/displayIssues, displayResolvedIssues.
  Proposed check: echart-issues-filter-playwright-checks.js — a casemgmt_issue row linked through casemgmt_issue_notes; the Issues module lists it; clicking it shows exactly the linked notes (an unlinked owned note is hidden) and the heading brings every note back; resolving moves it to Resolved Issues (resolved=1).
  Fixtures/blockers: demo-issue-codes.sql issues and two owned notes. Expect a FAIL. Two findings: #filter (showFilter()) has no opener, and the progress note's #noteIssues / asgnIssues controls are not rendered, so it is unclear how a user resolves an issue in the current UI.

[P2] Encounter form catalogue (form-catalog-smoke) — NOT COVERED for about 33 of the roughly 45 ON encounterForm entries; SHALLOW for 4 (form-asset-paths only renders Caregiver, MMSE, SF36 and annualfemaleprint)
  Path: Administration ▸ Forms/eForms ▸ Select Forms ▸ Add ▸ E-Chart ▸ Forms menu ▸ form ▸ Save ▸ reopen ▸ Print.
  Routes: form/select, form/formname, form/SubmitForm, form/forwardshortcutname, form/createpdf.
  Proposed check: form-catalog-smoke-playwright-checks.js — table-driven from encounterForm (Annual V2, Mental Health Forms 14 and 42, CHF, Falls, CESD, Intake, FDI, Self-Management, Treatment Pref, Letterhead and others). For each: no error page, pageerror or asset 404; one row in form_table for the patient with the typed value; reopen shows it; print gives a page or %PDF; rows and registration are removed afterwards.
  Fixtures/blockers: owned patient; marker-named registrations (the shipped rows are hidden on ON), so EXCLUSIVE=1.

[P2] Paediatric Rourke 2020 and growth charts — NOT COVERED (only Rourke 2017 and 2009 have checks)
  Path: Forms menu ▸ Rourke2020 (4 pages), Growth Charts / Growth 0-36m ▸ Print.
  Routes: form/formrourke2020complete (formRourke2020), formGrowthChart / formGrowth0_36 and their Print pages.
  Proposed check: form-rourke2020-growth-playwright-checks.js — saved values in formRourke2020 and reopened; the growth chart plots the entered weight, length and head circumference; print bytes carry them.
  Fixtures/blockers: owned infant patient; marker registration (hidden on ON), so EXCLUSIVE.

[P2] Lab results reaching the clinical flowsheet — SHALLOW (lab-manual-entry-cumulative asserts the measurements rows only)
  Path: Inbox ▸ Create Lab with CML 3767 (HbA1c; the seed maps it to LOINC 4548-4 and FLOWSHEET A1C) ▸ E-Chart ▸ Dx 250 ▸ Diabetes flowsheet and Health Tracker.
  Routes: oscarMDS/SubmitLab, oscarMeasurements/ViewTemplateFlowSheet, ViewHealthTracker.
  Proposed check: lab-to-flowsheet-playwright-checks.js — measurement type=A1C with measurementsExt lab_no; the flowsheet A1C row shows the value and date, flags it out of range and links to the lab; an unmapped code does not appear.
  Fixtures/blockers: owned patient, owned lab, dxresearch 250; the seed mapping already exists.

[P2] Consultation config writers accept GET and need only read rights — NOT COVERED live (findings 107/108 are needs-live-check; get-reject-consult-directory covers only Edit* deletes)
  Path: Schedule ▸ Consultations ▸ configuration ▸ Add Service, Delete Services, Enable Request/Response, Show All Institutions ▸ department link, service specialists.
  Routes: encounter/AddService, DelService, EnableConRequestResponse, UpdateInstitutionDepartment, UpdateServiceSpecialists.
  Proposed check: get-reject-consult-config-playwright-checks.js — drive each write through the page; replaying it as GET/HEAD on owned rows answers 405 with no change; a throwaway login holding only _con r is refused.
  Fixtures/blockers: owned service, institution and department; snapshot and restore the global flags, so EXCLUSIVE.

[P3] Pregnancy module — NOT COVERED, and broken
  Path: E-Chart right nav Pregnancy ▸ Normal / High Risk / Multiple / Ectopic; current pregnancy ▸ complete.
  Routes: /Pregnancy?method=list|create|complete. Pregnancy2Action has no Struts mapping, so every link 404s.
  Proposed check: echart-pregnancy-episode-playwright-checks.js, once the route is mapped — grant _newCasemgmt.pregnancy to a throwaway role; create writes an Episode (SnomedCore 72892002); the module lists it; complete moves it to the past list.
  Fixtures/blockers: the doctor role holds 'o' on _newCasemgmt.pregnancy in both seeds; route mapping missing (new finding).

[P3] Annual Review Planner risk/checklist editor Save — SHALLOW (antenatal-annual-review-planner opens the editors read-only)
  Path: Annual form ▸ Annual Review Planner ▸ Edit Risk / Edit CheckList ▸ Save.
  Routes: decision/annualreview/riskedit, checklistedit (rewrite the desannualreviewplanner*.xml files in the webapp).
  Proposed check: annual-review-config-edit-playwright-checks.js — Save needs _form w and POST; the file round-trips; markup in it renders as text in the planner; the file is restored afterwards.
  Fixtures/blockers: file-system snapshot and restore; EXCLUSIVE.

[P3] Edit or delete a reading from the flowsheet itself — SHALLOW (omd-flowsheet-conformance only reads the form's FormData; the history-page delete is covered)
  Path: Flowsheet ▸ click a saved reading ▸ ViewAddMeasurementData?id ▸ Delete.
  Routes: encounter/oscarMeasurements/DeleteData2.
  Proposed check: extend diagnosis-flowsheet — exactly the selected reading moves to measurementsDeleted and the flowsheet stops showing it.
  Fixtures/blockers: owned reading.

[P3] CPP extras — NOT COVERED
  Path: CPP item editor ▸ "Copy to current note" (copyCppToCurrentNote), Position select.
  Proposed check: echart-cpp-copy-position-playwright-checks.js — the copied text lands in the open note and saves; position reorders the box.
  Fixtures/blockers: owned patient.

[P3] Chart header and module secondary links — NOT COVERED or render-only
  Path: header OMD disease list (/commons/omdDiseaseList.jsp); Forms heading list paging (encounter/ViewFormlist limit1/limit2).
  Proposed check: echart-header-links-playwright-checks.js — each renders for the patient and paging changes the rows shown.
  Fixtures/blockers: a patient with more than one page of forms.

[P3] Flag-gated or external chart features — NOT COVERED
  Group note and Attach (facility enableGroupNotes / enablePhoneEncounter), Ocean eRefer (js/custom/ocean/conreq.js → encounter/eRefer), eConsult module, CAISI infirmary ArchiveView.
  Blockers: facility flags, caisi=on, external Ocean/OTN. Group note writes notes to several patients, so cover it first if a clinic enables it.

DEAD ROUTE CANDIDATES: decision/antenatal/{antenatalplanner, antenatalplannerprint, obarchecklistedit_99_12, obarriskedit_99_12, SaveAntenatalRiskConfig} + provider/ViewObar* (only commented-out BCAR links); adminFlowsheet/{ViewFlowsheetManager, ViewFlowsheetAdd, ViewFlowsheetEditor, ViewFlowsheetItemEditor, ViewFlowsheetAddTarget, ViewFlowsheetAddWarning} (they link only to each other); encounter/ViewIndex2 legacy form + SaveEncounter2, ViewEncounterPrint, report/ViewReportecharthistory (Index2.jsp always redirects to casemgmt/ViewForward except caisi casetoEncounter); oscarMeasurement/AddShortMeasurement (completedProcedure() in GenerateLetters.jsp is never called); oscarConsultationRequest/ViewCalendarPopup (links only to itself); oscarMeasurements/ViewMeasurementGroupDS{add,remove,complete}; casemgmt/ViewIssueSearch + ViewCaseManagementEntry; encounter/{ViewInsertTemplate(2), ViewLeftNavBarDisplay, ViewViewAttachment, ViewBilling, ViewClose}; immunization/{ViewSchedule, ViewScheduleConfig}; decisionSupport/{ViewGuidelineList, ViewGuidelineDetail} (replaced by guidelineAction); oscarMeasurements/{ViewMeasurements, ViewDisplayHistory, ViewHistoryIndex, ViewNewHistoryIndex, ViewAddMeasurementGroup, ViewDefineNewMeasurementGroup, ViewEditMeasurementGroupStyle, ViewProcessAddMeasurementGroupAction, ViewClose, DeleteData}; config/{ViewDisplayInstitution, ViewDisplayService}; ViewNothingtoPrint; oscarFacesheet/ViewTokenError; about 22 casemgmt/View* fragment gates (ViewChartNotesAjax, ViewNewCaseManagementView, ViewEncounterLayout, ViewFooter, ...).

ALREADY COVERED (routes the measurement missed):
encounter/display* (all, loaded by navBarLoader) -> echart-navbar-modules, page-health-echart-navbar (render only; the "+" links are skipped on purpose and each has its own workflow check except Pregnancy)
encounter/ViewFormlist (Forms heading) -> echart-navbar-modules (render)
encounter/decisionSupport/guidelineAction -> decision-support-guidelines (FAIL at last step)
decision/annualreview/{annualreviewplannerprint, riskedit, checklistedit} -> antenatal-annual-review-planner (editors read-only)
oscarDxResearch/{dxResearchCodeSearch, LoadQuickListItems, UpdateQuickList, LoadAssociations} -> dx-registry-quicklist, dx-registry-status-update, diagnosis-flowsheet, registry-ichppc-lifecycle
casemgmt/NoteBrowserDocumentUndelete, NoteBrowserDocumentRefile -> note-browser-documents
encounter/ViewCalculators, calculators/* -> clinical-calculators, gap-clinical-calculators-coronary, gap-clinical-calculators-conversions
encounter/immunization/{saveConfig, ViewScheduleEdit, config/*} -> immunization-schedule-config (needs IMMUNIZATION_IN_PREVENTION)
encounter/{AddService, AddInstitution, AddDepartment, UpdateInstitutionDepartment, DelService, EnableConRequestResponse}, config/View* -> consultation-services-admin, consultation-directory-crud
oscarMeasurements/{SetupAddMeasurementType, AddMeasurementType, SetupAddMeasuringInstruction, AddMeasuringInstruction, *MeasurementGroup, *MeasurementStyleSheet} -> measurement-type-group-admin (FAIL: Add links 405), measurement-stylesheet-delete, gap-clinical-measurement-group-style, get-reject-measurement-groups
oscarMeasurements/{AddMeasurementMap, NewMeasurementMap, RemoveMeasurementMap, RemapMeasurementMap, ViewAddMeasurementMap*, ViewRemoveMeasurementMap} -> measurement-map-admin (fails at its first step, so the later map steps have not run)
encounter/Measurements2 -> diagnosis-flowsheet, omd-flowsheet-conformance
oscarMeasurements/DeleteData2, SetupDisplayHistory -> measurement-history, measurement-group-entry
ViewTemplateFlowSheetPrint, adminFlowsheet/ViewUpdateFlowsheet -> flowsheet-patient-customization, flowsheet-editor-height
encounter/displayEpisodes "+" -> episode-lifecycle (ON only; needs the _newCasemgmt.episode grant, which is 'o' in the Flyway seed)
Planned items already landed under other names: echart-cpp-sections -> gap-clinical-cpp-boxes + cpp-note-extension-archive; echart-note-lifecycle -> note-browser-documents, concurrency-chart-note-*, echart-lock-lifecycle, authz-read-chart-note-history (except the draft and Verify gaps above); echart-measurements-history-graph -> measurement-history; clinical-flowsheet -> diagnosis-flowsheet.

New findings from code reading, not yet probed live: Pregnancy2Action is unmapped; FrmSetupForm2Action and the measurement history actions take the patient from the session-wide EctSessionBean; the chart notes #filter panel has no opener.
```

## Documents, labs, Rx, scheduling and forms

```text
GAPS (most important first)

[P1] Inbox "File" and manual lab Forward (HL7 labs, and filing documents) — NOT COVERED. detached-delete-forward-favourites forwards documents only. lab-forwarding-rules covers only rule-driven forwarding at upload.
  Path: Inbox ▸ list mode ▸ tick rows ▸ File (#topFileBtn → submitFile) or Forward (#topFBtn); lab popup ▸ Forward (ForwardSelectedRows ▸ ViewSelectProvider dialog); Inbox preview card ▸ Forward (labDisplayAjax ▸ ViewSelectProviderAltView ▸ AJAX); document viewer ▸ File (fileDoc ▸ method=fileLabAjax).
  Routes: oscarMDS/FileLabs, oscarMDS/ReportReassign (labType HL7), oscarMDS/Forward, oscarMDS/ViewSelectProvider, ViewSelectProviderAltView
  Proposed check: inbox-file-forward-playwright-checks.js — forward an uploaded HL7 lab from the popup and from a preview card: the recipient gets a providerLabRouting row (status N), the sender's row is unchanged, and the lab is in the recipient's Inbox. Filing sets status F for this provider only, does not acknowledge, and also closes queue_document_link for a document. A GET is refused.
  Fixtures/blockers: a throwaway second-provider login (lib/throwaway-login-fixture.js), a synthetic HL7 lab built like lab-upload's, an owned PDF.

[P1] Legacy lab types carried by the OSCAR 19 import (MDS, old CML, PathNet "BCP") — SHALLOW (pathnet-status only checks that the patient lab list links to ViewLabDisplay; o19-migrated-smoke opens HL7 labs only).
  Path: Chart ▸ Labs (lab/ViewDemographicLab), Inbox, or tickler ▸ a legacy lab ▸ display ▸ Acknowledge / Forward / Msg / Tickler. o19_preflight.json copies the mds*, labPatientPhysicianInfo/labTestResults and hl7_* tables. Optional: Administration ▸ Lab Upload (PathNet, only when OLD_LAB_UPLOAD=yes).
  Routes: oscarMDS/ViewSegmentDisplay, lab/CA/ON/ViewCMLDisplay, lab/CA/BC/ViewLabDisplay, oscarMDS/UpdateStatus, oscarMDS/Forward, lab/CA/ON/Forward, lab/CA/BC/Forward, lab/labUpload
  Proposed check: legacy-lab-display-playwright-checks.js — seed one lab of each type by SQL, as pathnet-status does; each renders its values and the patient; Acknowledge writes status A plus the comment; Forward creates the recipient's routing row.
  NEW FINDING to log: Forward cannot work on these three pages. The Forward button opens oscarMDS/SelectProvider.jsp as a bare popup, where the Submit button is commented out (line 169). Opened without a docId, prepSubmit looks up the form "reassignForm_", but these pages name it "reassignForm". Fixtures: SQL-seeded legacy rows, a second provider.

[P2] Rourke 2020 and the Growth Charts (well-baby record) — NOT COVERED.
  Path: Administration ▸ Select Forms (the shipped rows have hidden=0, so they are off by default) ▸ Chart ▸ Forms ▸ Rourke2020 / Growth Charts / Growth 0-36m ▸ Save ▸ reopen from the saved-forms entry ▸ Print.
  Routes: form/formrourke2020complete (p1–p4), form/formname (Frm2Action save and the Rourke2020_<id>.pdf print), form/formGrowthChart(+Print), formGrowth0_36(+Print), graphLengthWeight, graphHeadCirc
  Proposed check: form-rourke2020-growth-playwright-checks.js — one formRourke2020 / formGrowthChart / formGrowth0_36 row with the exact values and provider; values shown on every page after reopen; weight, length and head circumference imported into measurements; print starts with %PDF and pdftotext finds the marker; graph is a non-empty PNG.
  Fixtures/blockers: an owned infant patient; marker registrations as clinical-forms-save-reopen does (EXCLUSIVE=1).

[P2] BC antenatal and newborn forms (BC-AR 2020 pages 1–5 plus attachments, BC-NewBorn 2008; BC-AR/BC-AR 2007 are on by default) — NOT COVERED by any browser check (unit tests only, coverage-clinical-3774.md).
  Path: BC install ▸ Select Forms ▸ Chart ▸ Forms ▸ BC-AR 2020 ▸ pages 1–5 ▸ Save / Save and Exit ▸ reopen ▸ Print.
  Routes: form/BCAR2020 (save, saveAndExit, print → Jasper PDF), form/formBCAR2020pg1–5, formBCAR2020Attachments, form/formBCNewBorn2008pg1–3, form/formname
  Proposed check: form-bcar2020-newborn-playwright-checks.js — the formBCAR2020, formBCAR2020Data and formBCAR2020Text rows match under one form id; moving between pages keeps unsaved data; reopen shows every page; BCAR2020_<id>.pdf starts with %PDF and carries the marker for each selected page; BC-NewBorn 2008 save, reopen and print.
  Fixtures/blockers: a BC-preseeded install and `--province BC`. This now exists (the billing-bc-* checks run there), so the "no BC profile" blocker in plan §4.2 no longer holds.

[P2] BPMH (Best Possible Medication History) — NOT COVERED, and probably unreachable.
  Path: the BC seed registers `../formBPMH.do?demographic_no=` (hidden=0). FormViewRoutes.resolveActionPath recognises only `/form/pharmaForms/formBPMH.jsp`, and the `.do` suffix has no Struts mapping (extension ""), so the Forms-menu link should 404. The ON seed has no BPMH row at all. NEW FINDING candidate; BC "ECARES" (`../formeCARES.do`) has no action at all.
  Routes: formBPMH (fetch / save / print)
  Proposed check: form-bpmh-playwright-checks.js — enable BPMH through Select Forms and open it from Chart ▸ Forms (fails until the registration is fixed: report, don't encode); it lists the patient's active drugs and care team; Save writes a formBPMH row; method=fetch restores it; Print starts with %PDF and pdftotext shows the drug names.
  Fixtures/blockers: a BC install or an owned registration row; an owned patient with drugs and DemographicContact rows.

[P2] Ontario Mental Health Forms 14 and 42, Annual V2, and the Mental Health form chain — NOT COVERED (clinical-forms-save-reopen covers only Form 1 and Annual v1).
  Path: Chart ▸ Forms ▸ the form ▸ Save ▸ reopen ▸ Print Pdf / Print.
  Routes: form/formMentalHealthForm14, formMentalHealthForm42, formannualV2 (male/female V2), formmentalhealth → formmhassessment / formmhoutcome / formmhreferral (+print), form/formname
  Proposed check: no new script — add rows to the FORMS table in clinical-forms-save-reopen-playwright-checks.js: one owned row with the exact values, shown again on reopen, and the PDF or print page carries the marker.
  Fixtures/blockers: as clinical-forms-save-reopen.

[P2] Legacy "Add Relation" and delete relation (NEW_CONTACTS_UI=false) — NOT COVERED. contact-lifecycle and popup-opener-master-record cover only the new contacts UI.
  Path: Master Record ▸ "Add Relation" (edit-view.jsp:354, shown only when the flag is off) ▸ AddAlternateContact popup ▸ add a relation with substitute-decision-maker / emergency flags ▸ Delete. The OSCAR 19 import carries the site's flag (OSCAR 19 default "false" in o19map_props.json), so migrated clinics get this UI.
  Routes: demographic/AddRelation, demographic/DeleteRelation
  Proposed check: demographic-relations-legacy-playwright-checks.js — a `relationships` row with the relation, the two flags and the creator; the Master Record shows it on both patients; Delete removes it or flags it deleted; a GET is refused.
  Fixtures/blockers: the property switched off on a disposable install (needs a restart or override), two owned patients.

[P3] The remaining encounter forms (planned `form-catalog-smoke`) — NOT COVERED; the list is under CLINICAL FORMS below.
  Path: Administration ▸ Select Forms ▸ add the form ▸ Chart ▸ Forms ▸ open ▸ Save ▸ reopen ▸ remove it again.
  Routes: form/<view> wildcard, form/formname, form/select
  Proposed check: form-catalog-smoke-playwright-checks.js — table-driven over the encounterForm rows: opens with no error page and no new console error; one row in form_table; reopen shows a typed value.
  Fixtures/blockers: owned registrations, EXCLUSIVE=1.

[P3] eForm "Approve and download" of an incomplete render — SHALLOW. eform-apcache-renderer reaches the missing-content page and checks the renderApproval token but never approves; gap-encounter-eform-edoc-approval covers only the eDocument branch.
  Routes: eform/downloadEFormPdf
  Proposed check: extend eform-apcache-renderer-playwright-checks.js — approving downloads a file starting with %PDF; the eform_data count is unchanged (no duplicate); replaying the token is refused.
  Fixtures/blockers: the existing apcache fixture.

[P3] Rx clinic/satellite address on the prescription — NOT COVERED.
  Path: Rx ▸ preview (ViewScript2) ▸ Address select ▸ setDefaultAddr.
  Routes: rx/ViewSetDefaultAddr (sets the RX_ADDR session value)
  Proposed check: rx-satellite-address-playwright-checks.js — the preview's clinic address changes; the next prescription preselects it; pdftotext of the printed prescription shows the satellite address.
  Fixtures/blockers: multisites=on or the clinicSatelliteName property set.

[P3] Manage Sites — NOT COVERED (admin-sites-clinic-numbers records that there is no menu entry while multisites is off).
  Path: Administration ▸ Satellite-sites Admin.
  Routes: admin/ManageSites
  Proposed check: admin-manage-sites-playwright-checks.js — create and edit a site: `site` and `providersite` rows; the Add Appointment location menu offers the new site.
  Fixtures/blockers: multisites=on.

[P3] Population Report (CAISI shelter report) — SHALLOW (admin-index-links only renders it).
  Routes: PopulationReport
  Proposed check: report-population-playwright-checks.js — the shelter population and usage counts equal SQL over seeded program admissions.
  Fixtures/blockers: CAISI program data.

[P3] Chart informed-consent banner — NOT COVERED.
  Path: Chart ▸ consent banner ▸ tick the box (ChartNotes.jsp).
  Routes: DemographicExtService (method=saveNewValue, key informedConsent)
  Proposed check: chart-informed-consent-playwright-checks.js — demographicExt informedConsent=yes; the banner is gone after reload.
  Fixtures/blockers: privateConsentEnabled=true and the program listed in privateConsentPrograms.

[P3] Shared outcomes dashboard — NOT COVERED.
  Path: Schedule ▸ Dashboard ▸ Common Dashboard.
  Routes: web/dashboard/display/sharedOutcomesDashboard
  Proposed check: a stub-only check — the launch URL carries no PHI and postMessage targets the configured host.
  Fixtures/blockers: external system (shared_outcomes_dashboard_host).

CLINICAL FORMS (of the ~139 form JSPs) WITH NO SAVE/REOPEN CHECK
- Already saved and reopened by a check: Rourke2017, Annual v1, MH Form 1, Discharge Summary, Palliative Care, Peri-Menopausal, MMSE, Vascular Tracker (404s; already logged), Rh Immune Globulin, Lab Req 2007/2010.
- Rendered only: SF36, Caregiver, annual-female print (form-asset-paths).
- Seeded by SQL or import only, never saved through the UI: Rourke 2009 (export), 2 Minute Walk (XML import).
- No check, Ontario rows: Rourke2020, Rourke2006, Rourke (original), Growth Charts, Growth 0-36m, Annual V2 (male/female), MH Form 14, MH Form 42, Mental Health and its assessment/outcome/referral pages, Letterhead (formConsultant), Lab Req (pre-2007), ADFv2, ALPHA, CESD, CHF, Caregiver, Caregiver-SF36, SF36, Cost Questionnaire, Falls History, HOME FAST, Grip Strength, ImmunAllergies, Intake Information, FDI Disability, FDI Function, Position Hazard, Risk Assessment, Self Efficacy, Self Management, Treatment Preference, Health Passport, Patient Encounter Worksheet.
- No check, BC rows: BC-AR, BC-AR 2007, BC-AR 2012, BC-AR 2020, BC Labour/Birth Summary (and 2008), BC-NewBorn (and 2008), BC-INR, Chart Checklist, BPMH, ECARES.

DEAD ROUTE CANDIDATES: oscarMDS/SendMRP (the showDocument checkbox is permanently display:none; documentsInQueues has the function but no control); documentManager/ViewMultiPageDocDisplay (reached only from itself and the REST LabsDocsSummary); lab/CA/ALL/Forward (its form only holds hidden fields and is never submitted; the Forward button uses ReportReassign); lab/CA/BC/ViewIndex, ViewReport, ViewSearchReports, ViewDemoSelect (link only to each other); messenger/Transfer/SelectItems and PostItems; rx/addFavoriteStaticScript (StaticScript2 now posts rx/addFavorite2); rx/ViewCompleteMedRec (completeMedRec() is never called); rx/ViewUpdateHiddenResources (showHiddenRes() is never called); rx/ViewSideLinksEditFavorites2; rx/ViewTopLinks; eform/efmPrintPDF; eform/efmOpenEformByName; eform/eFormAttachmentForm; eform/efmpatientformlistsingle (reached only from itself); demographic/ViewAddNewDemographicSwipe; demographic/ViewDisplayFirstNationsModule; tickler/AddTickler; notification/create; form/AddRHWorkFlow; appointment/appointmentaddrecordcard and appointmentaddrecordprint; form JSPs with no Forms-menu row and no link: formInternetAccess, formSatisfactionScale, formSelfAssessment, formCounseling, formcounsellorassessment, formreceptionassessment.

ALREADY COVERED (routes the measurement missed)
- documentManager/documentUpload -> document-upload
- form/importLogDownload -> demographic-cds-import
- demographic/DemographicAddRecord -> demographic-add, gap-records-demographic-add-fields, double-submit-demographic-add
- demographic/ViewContact (AJAX row add), ViewContactSearch, ViewProfessionalSpecialistSearch -> popup-opener-master-record, contact-lifecycle
- demographic/ViewDemographicAudit -> demographic-edit-update (clicks "Audit Information")
- demographic/ViewZdemographicSwipe -> patient-swipe-card-search
- demographicSupport -> gap-records-demographic-add-fields (duplicate check) and every Master Record edit load
- tickler/DbTicklerAdd -> tickler-crud, double-submit-tickler
- appointment/printappointment -> appointment-lifecycle (Update & Receipt, PDF bytes checked)
- schedule/CreateDate and TemplateApplying -> schedule-setting, schedule-admin-settings
- web/dashboard/display/DashboardDisplay, DrilldownDisplay, DisplayIndicator -> dashboard-display, export-content-dashboard-csv
- eform/attachDoc -> eform-rtl-attachment-pdf ("Attach Selected")
- eform/partials/upload_image and eform/imageUpload -> eform-image-delete
- rx/GetRxPageSizeInfo -> every Rx check (runs on SearchDrug3 page load)
- rx/ViewManagePharmacy2 -> pharmacy-editor-workflow
- the label routes (print*Label*, ViewPrint*) -> demographic-labels, demographic-label-content
- Static-script "Add to Favorites" -> rx-legacy-null-fields (entered by URL)

Planned items that already landed under other names:
- appointment-form-fields -> gap-provider-appointment-type-booking, boundary-appointment-text
- appointment-repeat-group-copy -> appointment-group-copy-cut, appointment-recurrence
- demographic-export-import -> demographic-cds-import, cds-export-lab-documents
- demographic-audit -> demographic-edit-update
- lab-upload-hl7 -> lab-upload, gap-records-lab-patient-link
- lab-cumulative-requisition -> lab-manual-entry-cumulative, lab-acknowledge, lab-requisition-links
- document-manage -> stored-document-mutations, document-refile-combine, edoc-schedule-navigation, document-add-link
- hrm-report-lifecycle -> gap-records-hrm-report-actions, hrm-report-print-download, detached-delete-hrm
- rx-favorites -> rx-favorites-choose-drug, gap-encounter-rx-add-favourite
- rx-pharmacy-manage -> pharmacy-editor-workflow, gap-encounter-rx-pharmacy-order
- allergy-edit-delete -> allergy-custom-lifecycle, allergy-add-penicillin
- rx-write-to-encounter -> rx-print-profile
- prevention-edit-delete-refuse -> prevention-lifecycle
- form-discharge-summary, MH Form 1 -> clinical-forms-save-reopen
- form-xml-upload -> gap-encounter-form-data-import
- eform-groups-independent -> eform-groups, eform-deleted-restore
- consultation-response: has no JSP page; REST covered by consultation-response-attachments, admin toggle by consultation-services-admin

Outside my domain: encounter/oscarMeasurements/FlowSheetDrugAction is not dead. It is a relative form action in src/main/webapp/WEB-INF/jsp/encounter/oscarMeasurements/TemplateFlowSheetPage.jspf (flowsheet Dx list ▸ method=dxSave), and no check drives it; it belongs to the flowsheet owner.
```

## Billing and reports

```text
GAPS (most important first)

All BC items need a BC install: the packaged deb preseeded with billregion=BC and BC demo data, run with `run-playwright-suite --province BC`. There is no devcontainer BC profile. Defects marked "suspected" come from reading the source and have not been probed live.

[P1] BC bill entry: bill an appointment, view it, unbill it — NOT COVERED
  Path: Day sheet "B" (or Master Record ▸ Billing, or the BC unbilled report's Bill link) ▸ BC bill form with its code/dx/referral search popups ▸ Continue ▸ billingCreated ▸ Save Bill / Another Bill; Bill Status ▸ view; day sheet "-B" ▸ confirm.
  Routes: billing→billing/CA/BC/billingSetup, CreateBilling, SaveBilling, billingView, ViewBillingCodeNewSearch, BillingCodeNewUpdate, ViewBillingDigNewSearch, BillingDigNewUpdate, ViewBillingReferCodeSearch/Update, ViewDxReference, BC BillingDeleteWithoutNo, BillingDeleteNoAppt
  Proposed check: billing-bc-create-view-playwright-checks.js. Saving writes one billing + billingmaster row per item (status O, MSP, PHN, fee = BC billingservice value × units, dx1-3, service date) and sets the appointment to B. Another Bill reopens a clean form. "-B" sets status D and unbills the appointment, and is refused once the claim is submitted. GET on the BC delete actions must be refused; they have no method guard (suspected GET delete). Suspected: the form tag lost name="BillingCreateBillingForm" (billingBC.jsp:1347), so the 24 `document.BillingCreateBillingForm` handlers throw, including the onsubmit toggleWCB. Go Back points at '/billing?loadFromSession=yes' with no context path (billingCreated.jsp:579).
  Fixtures/blockers: BC install; owned FAKE patient with a BC PHN, owned appointment, billing provider with MSP numbers.

[P1] BC Teleplan claim file: simulate and generate — NOT COVERED (billing-bc-simulation-encoding drives only the legacy ViewBillingSim by typed URL; that menu item is hidden under the default NEW_BC_TELEPLAN=yes)
  Path: Administration ▸ Billing ▸ "Generate Teleplan File2", which is the SimulateTeleplanFile route; "Simulate Submission File2", which is GenerateTeleplanFile ▸ TeleplanSubmission ▸ provider ▸ Submit. The two labels are swapped against their routes.
  Routes: billing/CA/BC/SimulateTeleplanFile, GenerateTeleplanFile (legacy ViewBillingTeleplanGroupReport/ViewGenTeleplanGroupReport when NEW_BC_TELEPLAN=no)
  Proposed check: billing-bc-teleplan-file-playwright-checks.js. Simulation lists exactly the owned O claims and writes nothing. Generate writes one billactivity row and an MSP file in HOME_DIR whose records equal the seeded claims, moves the claims to submitted and writes log_teleplantx. A second simulation lists none of them. Never press Send, which is live Teleplan. Suspected: GenerateTeleplanFile2Action has no method guard. Opening its menu item (a GET with no parameters) commits an empty batch (files, a billactivity row and a monthly sequence number), and a GET with providers= commits real claims. The Activity List download links point at billing/CA/BC/DownloadBilling, which has no mapping (404). admin-index-links on a BC install would trigger the GET commit, so it needs a SKIP_ITEMS entry.
  Fixtures/blockers: BC install; reuse the provider/claim fixture from billing-bc-simulation-encoding; writable HOME_DIR.

[P1] BC billing mutators: GET refusal and role authorization — NOT COVERED
  Path: negative probes against the mutators the BC workflows reach.
  Routes: GenerateTeleplanFile, saveQuickBillingBC, BC BillingDeleteWithoutNo/NoAppt, formwcb, billingTeleplanCorrectionWCB, billingAddCode, billingEditCode, saveAssocAction, AddReferralDoc, saveBillingPreferencesAction, receivePaymentAction
  Proposed check: register these in MutatorActionGetRejectionContractUnitTest so that mutator-get-rejection-live probes them. billings.ca.bc is outside IN_SCOPE_PACKAGE_PREFIXES, and none of these classes has a method guard. Add authz-bc-billing-playwright-checks.js: a role without _billing is refused every BC page. authz-read-routes.js lists only ON billing routes, and authz-read-role-matrix runs on ON only.
  Fixtures/blockers: BC install; a role user from the authz harness.

[P1] BC MSP Quick Billing — NOT COVERED (admin-index-links only renders it, and only on a BC run)
  Path: Administration ▸ Billing ▸ BC MSP Quick Billing ▸ provider/date ▸ add patients + codes ▸ Save
  Routes: quickBillingBC, saveQuickBillingBC
  Proposed check: billing-bc-quick-billing-playwright-checks.js. N entered rows give N billing + billingmaster rows (O, MSP, C02, the quick-billing internal comment), the saved count is shown and GET is refused. Suspected: QuickBillingBCSave2Action:83 builds `new QuickBillingBCHandler()` without the session form bean, so saveBills() dereferences null. Save would answer 500 and save nothing.
  Fixtures/blockers: BC install; two owned patients with PHNs.

[P1] BC WCB claim and WCB form — NOT COVERED
  Path: BC bill form ▸ Billing Type WCB ▸ WCB forms list ▸ new/edit form (body part, nature of injury, ICD9, fee item lookups) ▸ Save ▸ Save Bill; Chart ▸ Forms ▸ BC-WCB; simulation row ▸ WCB correction.
  Routes: ViewWcbForms, viewformwcb, formwcb, support/BodyPart, NatureInjury, Icd9, BillingFeeItem, billingTeleplanCorrectionWCB
  Proposed check: billing-bc-wcb-playwright-checks.js. Saving writes a wcb row with the typed fields and a billingmaster linked by wcb id. Reopening shows the same values, the claim appears in the simulation, and a correction save updates it. Suspected broken entries: toggleWCB loads a relative "wcbForms.jsp" (under WEB-INF, so 404). The BC-WCB encounterForm value '../billing/CA/BC/viewformwcb.do' is not translated by FormViewRoutes and never matches an extensionless action. HtmlTeleplanHelper's WCB correction link targets billingTeleplanCorrectionWCB.jsp (404).
  Fixtures/blockers: BC install; owned patient.

[P1] BC remittance import, reconciliation and settlement — NOT COVERED
  Path: Administration ▸ Billing ▸ Upload Remittance Files ▸ S00/S01/S22 reports; MSP Reconciliation Reports; Bill Status ▸ remittance by office number; Settle Over/Under Paid Claims.
  Routes: ViewBillingTA, /servlet/oscar.DocumentTeleplanReportUploadServlet→ViewGenTA, ProcessRemittance, ViewGenTAS00/S01/S22, ViewGenTAS00ByOfficeNo, ViewSettleBG, createBillingReportAction (REP_MSPREM links)
  Proposed check: billing-bc-remittance-playwright-checks.js. A synthetic remittance (S21, S01 paid line, S00 refusal with an explanatory code, S22) writes teleplanS21/S00/S01/S22 rows. The paid claim becomes settled with its paid amount and the refused one is flagged. Settle BG flips an over/under-paid claim, and GET with settle parameters answers 405. Suspected: MSP Reconciliation Reports opened from the menu has no filename attribute, so GenTa2Action's validatePath(null) throws and an error page is shown.
  Fixtures/blockers: BC install. Upload Remittance is visible only with NEW_BC_TELEPLAN=no; under the default the remittance arrives only through ManageTeleplan ▸ Get Remittance, which is live Teleplan. Needs that property profile or a Teleplan stub, plus DOCUMENT_DIR access.

[P1] BC private (patient-pay) bill: receipt, bill-to, payments — NOT COVERED
  Path: BC bill form ▸ Private ▸ Save & Print Receipt ▸ billReceipt ▸ bill-to search/add ▸ Save; Receive Payment popup.
  Routes: SaveBilling (Pri), billingView (private result), UpdateBilling, BC ViewOnSearch3rdBillAddr, OnAddEdit3rdAddr, viewReceivePaymentAction, receivePaymentAction
  Proposed check: billing-bc-private-bill-playwright-checks.js. Expect Pri billing/billingmaster rows, a bill_recipients row for the bill-to, a payment written to billing_history with the balance reduced, and a receipt showing the amounts. Finding 164 (Receive Payment saves nothing) stands: report it, don't encode it.
  Fixtures/blockers: BC install. The planned statement half is dead (listed below).

[P2] BC Accounting Reports — NOT COVERED
  Path: Administration ▸ Billing ▸ Accounting Reports ▸ payee/provider/type (Invoice, Rejection, A/R, Write-Off, Payments/Refunds) ▸ PDF or Spread Sheet
  Routes: ViewBillingAccountReports, createBillingReportAction
  Proposed check: billing-bc-account-reports-playwright-checks.js. The PDF starts with %PDF and pdftotext shows the owned invoices and amounts. CSV rows equal SQL over billingmaster for the range and payee. The MSP/WCB/Private/ICBC boxes filter, and no HTML error appears inside the download.
  Fixtures/blockers: BC install; claims in several statuses; pdftotext.

[P2] BC billing report center (Billed / OB / Flu modes, row drill-downs) — SHALLOW (billing-unbilled-report covers Unbilled only, by typed URL)
  Path: Schedule ▸ Report ▸ Generate a billing report ▸ mode ▸ Create Report ▸ row ▸ view bill / Bill / delete
  Routes: billing/CA/BC/ViewBillingReportCenter, ViewBillingReportControl, billingView, BillingDeleteNoAppt
  Proposed check: billing-bc-report-center-playwright-checks.js. Entered by clicking through the Report index; for each mode, rows equal SQL for the provider and range, and each row link opens the right bill.
  Fixtures/blockers: BC install; claims in each status.

[P2] BC billing code administration — SHALLOW (admin-index-links only renders these, and only on a BC run)
  Path: Administration ▸ Billing ▸ Manage Private Bill / Manage Billing Codes (BC_BILLING_CODE_MANAGEMENT=yes) / Manage Service/Diagnostic Code Associations / Manage Referral Doc
  Routes: billingAddCode, billingEditCode, DeletePrivateCode, showServiceCodeAssocs, editServiceCodeAssocAction, saveAssocAction, deleteServiceCodeAssoc, AddReferralDoc
  Proposed check: billing-bc-codes-admin-playwright-checks.js. Code add/edit/delete and a fee edit write and restore billingservice. Association add/edit/remove writes ctl_servicecodes_dxcodes and the bill form then suggests the dx. Referral add writes billingreferral. Suspected: SaveAssoc2Action collects duplicate/invalid-code errors but saves anyway and returns success. "Create New Association" links back to the list (manageSVCDXAssoc.jsp:98), so a new association cannot be started from the UI.
  Fixtures/blockers: BC install; marker codes.

[P2] ON billing report for non-doctor roles: Unsettled / OB / FLU and the OB2 bill popup — SHALLOW (billing-unbilled-report covers Unbilled only; doctors and admins are chained to ViewBillingONNewReport, so billing-on-reports-inr-eoy never reaches these modes)
  Path: as a receptionist or billing clerk: Schedule ▸ Report ▸ Generate a billing report ▸ Unsettled/OB/FLU ▸ invoice # popup; FLU row ▸ delete
  Routes: billing/CA/ON/ViewBillingReportCenter (non-doctor branch), ViewBillingReportControl, ViewBillingOB2
  Proposed check: billing-on-report-center-clerk-playwright-checks.js. Rows for each mode equal SQL, and OB2 shows the owned claim's header and items.
  Fixtures/blockers: a receptionist-role login (the authz harness can create one); seeded bills.

[P2] ON Overnight Batch Eligibility (OBEC) file — SHALLOW (admin-report-validation asserts only the required-field validation)
  Path: Administration ▸ Reports ▸ Overnight Batch ▸ date + days ▸ submit ▸ File link
  Routes: oscarReport/obec, /servlet/OscarDownload?homepath=obecdownload
  Proposed check: report-obec-file-playwright-checks.js. The file has one OBEC01+HIN(10)+version(2) line per in-window appointment, CR/LF-framed, with out-of-window appointments excluded and no file for an empty window. This closes the loop with gap-billing-moh-return-files, which covers the MOH response (R file).
  Fixtures/blockers: owned appointments with synthetic HINs.

[P2] BC provider billing preferences — NOT COVERED
  Path: Preferences ▸ BC Billing Preferences (shown only when billregion=BC) ▸ Save
  Routes: viewBillingPreferencesAction, saveBillingPreferencesAction
  Proposed check: billing-bc-preferences-playwright-checks.js. The billing_preferences row is written and restored, and the next bill form uses its defaults.
  Fixtures/blockers: BC install.

[P3] Letter template delete — NOT COVERED (patient-letters-envelopes uploads and downloads, then deletes by SQL)
  Path: Report ▸ Generate Letters ▸ Manage ▸ Delete
  Routes: report/ViewManageLetters, report/DeleteLetter
  Proposed check: extend patient-letters-envelopes. Delete removes only the owned template from the database and the list, and GET is refused.
  Fixtures/blockers: none.

[P3] Encounter history (legacy eChart) popup — NOT COVERED
  Path: Chart ▸ header link "Encounter: Last, First" (encounter-row-three.jspf)
  Routes: report/ViewReportecharthistory
  Proposed check: echart-history-report-playwright-checks.js. Lists only this patient's eChart rows by date, with working paging.
  Fixtures/blockers: seeded eChart rows.

[P3] ON invoice letterhead logo — NOT COVERED (billing-on-invoice-third-party prints with no logo)
  Path: upload a document of type "invoice letterhead" ▸ Master Record ▸ Invoice ▸ Print
  Routes: billing/ca/on/DisplayInvoiceLogo, ViewBillingON3rdInv
  Proposed check: extend billing-on-invoice-third-party. The image answers with image/* bytes equal to the uploaded file and appears on the invoice; with no such document there is no broken image.
  Fixtures/blockers: owned document of type invoice_head_logo_doctype; not multisite.

[P3] CAISI-gated reports: CDS-4, MIS, Population — NOT COVERED
  Path: Administration ▸ Reports; the items render only with caisi=on.
  Routes: oscarReport/ViewCds4ReportForm, ViewCds4ReportResults, ViewMisReportForm, PopulationReport
  Proposed check: report-caisi-reports-playwright-checks.js, which skips when caisi is off. Each answers a report or its documented empty state for a seeded program.
  Fixtures/blockers: a caisi=on profile.

[P3] Property-gated or external billing pages — NOT COVERED, blocked
  Path: Administration ▸ Billing ▸ View MOH Files (needs moh_file_management_enabled=true); Manage Teleplan (sequence number, user, password; BILLING_SUPERUSER); Master Record ▸ Check Eligibility (live Teleplan); legacy INR generator (only when isNewONbilling≠true).
  Routes: billing/CA/ON/moveMOHFiles; billing/CA/BC/ManageTeleplan (setSequenceNumber/setUserName/changePass/checkElig/remit/sendFile); billing/CA/ON/ViewInrGenINRbilling
  Proposed check: billing-on-moh-files-playwright-checks.js under the property (file moves inbox→archive, GET refused); ManageTeleplan's local-only methods against a stubbed TeleplanService.
  Fixtures/blockers: property flags; a Teleplan stub.

DEAD ROUTE CANDIDATES: billing/CA/ON/billingShortcutPg1View + BillingShortcutPg2Save (no opener, so the planned billing-shortcut check cannot be written); billing/CA/ON/BillingEditWithApptNo (its day-sheet branch tests caisiBillingPreferenceNotDelete, which appointmentprovideradminday.jsp:295 declares null and never assigns); billing/CA/ON/ViewBillingResearchCodeSearch + BillingResearchCodeUpdate (the BC pages open a relative billingResearchCodeSearch.jsp, which 404s); billing/CA/ON/ViewGenRASummary, ViewGenRASummaryDetail; billing/CA/ON/BillingCorrectionReview, BillingCorrectionValid, BillingCorrectionSubmit; billing/CA/BC/ViewBillingCorrection, ViewBillingCorrectionValid, ViewBillingCorrectionReview, BillingCorrectionSubmit; billing/CA/BC/privateBilling/ViewStatement + PrintPreview (PrivateBillingController has no servlet mapping, so the planned billing-bc-private-statement check cannot be written); billing/CA/BC/associateCodesAction, support/BillingCodes; billing/CA/ON/ViewBillingOBECEA (its menu link is commented out), ViewGenReport, ViewGenGroupReport, ON ViewGenSimulation, ViewBillingON3rdPayments, Add3rdPartyPayment, inr/DbINRbilling, specialtyBilling/fluBilling/DbAddFluBilling, BillingDeleteWithBillNo; billing/DbManageBillingform* (the non-ON duplicates); RunClinicalReport, report/RemoveClinicalReport, ViewClinicalExport, ViewReportExport (ClinicalReports.jsp has no opener); report/ViewReportFilter, ViewReportFormCaption, ViewReportFormConfig, ViewReportFormDemoConfig, ViewReportFormOrder, ViewReportFormRecord, ViewReportResult (the report designer chain links only to itself; it is not part of the Demographic Report Tool); report/ViewReportedblist (its opener, go() in scheduleedittemplate.jsp, is never called); report/printLabDaySheetAction, reportByTemplate/exportTemplateAction, ViewResultReport, ViewTemplateGroups.

LIVE ROUTES MISFILED AS no_caller (the measurement missed relative, dynamic or property-built callers; covered by the gaps above): billing/CA/BC/billingSetup (chained from /billing), createBillingReportAction, viewReceivePaymentAction, editServiceCodeAssocAction, deleteServiceCodeAssoc (relative URLs), BC BillingDeleteWithoutNo and BC ViewBillingReportCenter (built as billing/CA/<%=prov%>/...), ViewGenTA (reached through the TA_FORWARD property).

ALREADY COVERED (routes the measurement missed):
- billing/CA/ON/createPaymentType, updatePaymentType, removePaymentType -> billing-payment-types
- billing/CA/ON/benefitScheduleUpload -> billing-on-gst-css-benefit
- billing/CA/ON/ViewOnGenRASummary, ViewOnGenRAError -> billing-on-ra-import
- billing/CA/ON/DbManageBillingformDx/Service/Premium/PremiumDelete, DbManageBillingLocation -> billing-on-admin-config
- oscarReport/reportByTemplate/rbtGroup, actions/tempInGroup, rbtAddToGroup, remFromGroup, delGroup, uploadTemplates -> report-by-template-groups (and boundary-rbt-group-label)
- oscarReport/reportByTemplate/generateOutFilesAction, ViewReportConfiguration -> report-by-template, export-content-rbt-columns
- oscarReport/RptByExamplesAllFavorites -> report-query-by-example; oscarReport/DbManageProvider -> report-age-sex-visit; appointment/printAppointmentReceiptAction -> appointment-lifecycle
- billing/CA/ON/ViewInrGenINRbilling (java_only) is the legacy twin, chosen only when isNewONbilling≠true; the live ViewInrOnGenINRbilling -> billing-on-reports-inr-eoy
- Planned items that landed under other names:
  - billing-on-ohip-file-cycle -> billing-on-ohip-simulation-report + billing-on-ra-import + gap-billing-moh-return-files + gap-billing-ra-premium-settle35. Only moveMOHFiles is left, and it is property-gated.
  - billing-on-mri-batch-clipboard -> billing-on-ohip-simulation-report (MRI) + billing-on-batch-clipboard. Its clipboard step fails on the CSRF defect it records.
  - billing-on-reports -> billing-on-reports-inr-eoy + billing-unbilled-report + flu-billing-report + report-age-sex-visit.
  - report-clinical-reports -> report-age-sex-visit + export-content-provider-service-csv + patient-list-by-appointment-export + the dx-registry checks. Only the CAISI items are left.

Key files: src/main/webapp/WEB-INF/jsp/administration/leftNav.jspf (billing/report menu and its property gates), src/main/webapp/WEB-INF/classes/struts-billing.xml, src/main/webapp/WEB-INF/jsp/billing/CA/BC/billingBC.jsp, src/main/java/io/github/carlos_emr/carlos/billings/ca/bc/quickbilling/QuickBillingBCSave2Action.java, src/main/java/io/github/carlos_emr/carlos/billings/ca/bc/pageUtil/GenerateTeleplanFile2Action.java, src/main/webapp/WEB-INF/jsp/billing/CA/BC/TeleplanSubmission.jsp, src/main/java/io/github/carlos_emr/carlos/billings/ca/bc/MSP/GenTa2Action.java, src/main/java/io/github/carlos_emr/carlos/billings/ca/bc/pageUtil/SaveAssoc2Action.java.
```

## Administration, integrations, provider and login

```text
GAPS (most important first)

[P1] A restricted role is refused writes, and the UI hides what it cannot do (the write half of the planned role-privilege-matrix) — NOT COVERED. The read half is covered by authz-read-role-matrix and authz-read-admin-objects; admin-role-management proves only one admin object.
  Path: Administration ▸ System Management ▸ Add A Role ▸ Assign Role/Rights to Object (grant only _demographic r, _appointment w, _eChart r) ▸ User Management ▸ Assign Role to Provider; then log in as that throwaway through the login form ▸ Schedule, Master Record, Chart.
  Routes: admin/ProviderAddRole, admin/ProviderPrivilege, admin/ProviderRole; write probes against tickler/AddTickler, CaseManagementEntry, rx/WriteScript, rx/addAllergy2, billing/CA/ON/BillingONSave, demographic/DemographicUpdate, admin/ProviderUpdate.
  Proposed check: role-privilege-write-matrix-playwright-checks.js. Asserts the top bar has no Administration and the chart nav has no Rx/Bill/Allergy "+". Each mutator POST is captured from the full-privilege session's own UI action and replayed with a valid token in the restricted session: it gets the app's 403 or securityError and writes no row (marker counts). The same POST from the full session writes the row.
  Fixtures/blockers: throwaway role and login (throwaway-login-fixture / authz-read-fixture) and an owned FAKE- patient. Nothing external.

[P1] PHI-bearing admin pages hidden from the shell but still live (Database/Document Download, Server Logging) — NOT COVERED. No check and no authz-read route list names them. admin-index-links skips "Database/Document Download". The only test is BackupDownloadUnitTest.
  Path: only the legacy panel admin/ViewAdmin (admin.jsp); the /administration shell comments both items out (leftNav.jspf). A typed-URL negative probe is therefore the point.
  Routes: admin/ViewAdminBackupDownload, /servlet/BackupDownload, admin/ViewOscarLogging. SERVERLOGGING is only a menu gate: the action serves LOGGING_PATH contents to anyone holding _admin or _admin.reporting.
  Proposed check: extend authz-read-admin-objects, or add admin-hidden-downloads-playwright-checks.js. Doctor, nurse and receptionist get the app 403 on all three. The _admin control gets the listing and the exact bytes of a seeded file. filename=../x gets 400 with no bytes. No download answers an HTML error page.
  Fixtures/blockers: a marker file in backup_path and a marker log under LOGGING_PATH. The alternative is to retire the routes.

[P1] MCEDT claim-file staging and outbox management (the local half) — SHALLOW. admin-index-links and page-health-admin-panel only open "MCEDT Mailbox" / "MCEDT Interface".
  Path: Administration ▸ Billing ▸ Generate OHIP diskette (as billing-on-group-disk-zero-total does) ▸ MCEDT Mailbox ▸ Upload tab ▸ Add / Delete, and ▸ Change Password. The packaged default shows MCEDT Mailbox (mcedt.mailbox.enabled=true); the devcontainer shows MCEDT Interface.
  Routes: mcedt/kaimcedt; mcedt/upload (its default execute runs moveOhipToOutBox; also method=deleteUpload); mcedt/openAddUploadMailbox; mcedt/addUpload; mcedt/kaichpass (changePassword).
  Proposed check: mcedt-mailbox-outbox-playwright-checks.js.
    - The generated H-file is copied into ONEDT_OUTBOX byte-identical and listed once; reopening does not duplicate it.
    - Add writes a fixture H-file; a non-OHIP/OBEC name or a traversal name is refused and nothing is written.
    - Delete removes only the owned file.
    - Change Password writes UserProperty MCEDT_ACCOUNT_PASSWORD (in plaintext) and never echoes it.
    - GET or a tokenless POST to each mutator gets 405/403; a login without _admin.billing is refused.
    - Upload and Submit are never clicked.
  Fixtures/blockers: Ontario only. Needs HOME_DIR and ONEDT_OUTBOX paths. The MCEDT password is clinic-wide, so snapshot and restore it and run with EXCLUSIVE=1.

[P2] Login-time chooser for a provider in two facilities (planned login-facility-select) — SHALLOW. facility-selection types /select_facility after a completed one-facility login, and the harness login() silently clicks the first facility.
  Path: log in through the login form as a provider assigned to two facilities ▸ chooser ▸ pick ▸ Schedule.
  Routes: login (Login2Action sets PENDING_FACILITY_SELECTION when the provider has more than one facility), select_facility, the LoginFilter pending gate.
  Proposed check: login-facility-chooser-playwright-checks.js.
    - The chooser appears unprompted with exactly the provider's facilities.
    - Before choosing, typed provider/providercontrol and chart URLs go back to the chooser, and logout works.
    - The pick writes one log row facilityId=<id>, and the facility banner shows only that facility's message.
    - A one-facility login skips the chooser and audits its facility.
  Fixtures/blockers: a second Facility row plus provider_facility for a throwaway login (facility-selection already does this), and facility_message rows.

[P2] Lab Recall preferences and the lab "Recall" action — SHALLOW. surface-audit:preferences-surface and page-health-preferences-referrals only render the settings page; no check clicks Recall.
  Path: Schedule ▸ Preferences ▸ Lab Recall Settings ▸ delegate / subject / assignee / priority ▸ Save; then Inbox ▸ lab ▸ Recall.
  Routes: setProviderStaleDate (viewLabRecall, saveLabRecallPrefs), messenger/SendDemoMessage&recall, tickler/ForwardDemographicTickler&recall.
  Proposed check: lab-recall-delegate-playwright-checks.js. The labRecall* property rows are written and shown on reopen. The Recall button appears only once a delegate is set. The message is prefilled with the subject, and the saved tickler is assigned to the delegate at the chosen priority and linked to the lab. A GET save is refused.
  Fixtures/blockers: throwaway login with _lab w, a second provider as delegate, a synthetic HL7 lab (lab-upload fixture).

[P2] Clinic/Agency Address edit and the letterheads that print it — SHALLOW. clinic-demo-name only reads the clinic name.
  Path: Administration ▸ System Management ▸ Clinic/Agency Address ▸ Update; then Chart ▸ Rx print preview, and the consultation letter print.
  Routes: admin/ManageClinic (method=update); RxProviderData and the consultation letterhead read the clinic row.
  Proposed check: clinic-address-admin-playwright-checks.js. The clinic row is updated exactly (punctuation kept). The Rx preview and the consultation PDF (via pdftotext) show the new address, phone and fax. A GET method=update is refused. The row is restored byte-exact.
  Fixtures/blockers: clinic-wide, so snapshot and restore with EXCLUSIVE=1.

[P3] MCEDT transport flows: Upload & Submit, Download, Sent ▸ Re-Submit / Info, auto upload and download, and the legacy MCEDT Interface — NOT COVERED.
  Routes: mcedt/upload (uploadSubmitToMcedt), mcedt/download, mcedt/kaiautodl, mcedt/autoUpload, mcedt/resourceInfo, mcedt/reSubmit, mcedt/mcedt, mcedt/uploads, mcedt/update, mcedt/openUpdateUpload, mcedt/info.
  Proposed check: scripts/e2e/mcedt/mcedt-transport-playwright-checks.js against a stub at MCEDT_STUB_URL, never MOH. Uploaded files move from outbox to sent; downloaded RA and error files land in ONEDT_INBOX and billing-on-ra-import can import them; Re-Submit and Info show the stub's status.
  Fixtures/blockers: needs a stub EDT SOAP service that handles WS-Security with a test keystore; none exists.

[P3] DHIR immunization submission — NOT COVERED.
  Path: Chart ▸ Preventions ▸ add immunization ▸ Save & Submit ▸ DHIR Submission Review ▸ Submit.
  Routes: prevention/AddPrevention (its "review" result), dhir/submit.
  Proposed check: dhir-submit-review-playwright-checks.js with a stubbed transport. The button appears only with dhir.enabled, an SSO session and the matching ISPA / non-ISPA consent. The review lists the immunization, and the submit outcome is recorded.
  Fixtures/blockers: dhir.enabled=true and consent rows. The SSO session (session attribute oneIdEmail) is only set by the external OneID login, so a test hook is needed. Also a DHIR stub.

[P3] OntarioMD lookups — NOT COVERED.
  Path: Rx search ▸ "OMD lookup", and the chart header ▸ OntarioMD. Both appear only when ONTARIO_MD_INCOMINGREQUESTOR is set (commented out by default).
  Routes: common/OntarioMDRedirect, common/omdDiseaseList, setProviderStaleDate (viewOntarioMDId, saveOntarioMDId).
  Proposed check: omd-redirect-playwright-checks.js. With no stored OMD ID the user is redirected to the OMD ID preference; saving writes the two UserProperty rows. The redirect then POSTs to OMD (intercepted, never sent) without leaking the password elsewhere in the page.
  Fixtures/blockers: the property flag and an external site.
  Code-reading finding: the chart-header link targets /commons/omdDiseaseList.jsp (casemgmt/newEncounterHeader.jsp:167), which does not exist, so it 404s whenever the flag is on.

[P3] CAISI administration authoring: Default Encounter Issue and the Facility Messages editor with its calendar popup — NOT COVERED on the packaged default. admin-messages and admin-issue-editor exist but SKIP; admin-facility-messages seeds rows by SQL and checks display only.
  Path: Administration ▸ CAISI ▸ Default Encounter Issue / Facility Messages ▸ Create ▸ date picker ▸ Save.
  Routes: DefaultEncounterIssue, FacilityMessage, calendar/oscarCalendarPopup.
  Proposed check: caisi-admin-authoring-playwright-checks.js. Add and remove a default_issue row; with wl_default_issue=true, a new program-client encounter gets that issue. A facility_message created in the editor, with the expiry chosen in the popup, shows on the day-sheet banner. Run admin-messages and admin-issue-editor in the same profile.
  Fixtures/blockers: a caisi=on profile (see note below). The same group's Survey Manager link points at the unmapped route SurveyManager.

[P3] PMmodule client and provider pages, and the landings that route to them — NOT COVERED, and code reading says they cannot render.
  Path: with caisi=on, Schedule ▸ Search (caisi.search.workflow=true is the default), the day sheet "|P" link, and the Master Record footer. A login whose ProviderPreference defaultCaisiPmm is "enabled" (no UI control sets it) lands on PMmodule/ProviderInfo. A login holding the seeded "Vaccine Provider" role, on any install, lands on ViewVaccineProvider, which meta-refreshes to ClientSearch2.
  Routes: PMmodule/ClientSearch2, PMmodule/ClientManager, PMmodule/ProviderInfo, PMmodule/StaffManager, provider/ViewVaccineProvider.
  Why it is broken: struts-pmmodule.xml (src/main/webapp/WEB-INF/classes/struts-pmmodule.xml, lines 85-101) maps these results to Tiles names (page.pmm.client.searchform, page.pmm.client, page.pmm.provider.view, page.pmm.staff.*). struts-tiles is excluded in pom.xml and those JSPs no longer exist. PmmoduleJspMigrationActionRoutingTest already removed the page.pmm.admin.* ones. Separately, Login2Action can return "patientIntake", which the login action does not map.
  Proposed check: caisi-mode-smoke-playwright-checks.js. Each control lands on a real page and Search finds the owned patient; the Vaccine Provider login lands on a usable page. Expect FAIL today.
  Fixtures/blockers: a caisi=on profile, and a decision on whether CAISI client mode stays.

[P3] Upload Login Exam / Acceptable Use Agreement — NOT COVERED.
  Path: Administration ▸ System Management ▸ Upload Login Exam (shown only when LOGINTEST=yes) ▸ Submit; the login page's AUA link (show_aua).
  Routes: admin/uploadEntryText.
  Code-reading findings:
    - The upload writes DOCUMENT_DIR/OSCARloginText.txt, but the login page reads BASE_DOCUMENT_DIR/login/AcceptableUseAgreement.txt, so uploaded text never reaches users.
    - getAgreementCutoffDate has no caller.
    - The login page renders the AUA text unencoded.
    - LOGINTEST only hides the menu; the action checks only _admin w.
  Proposed check: login-aua-playwright-checks.js, after the feature is fixed or retired. Asserts the upload writes aua_valid_from / aua_valid_duration and the file the login page reads, and the page shows it as text.
  Fixtures/blockers: LOGINTEST=yes and show_aua=true.

[P3] Property-gated billing and site configuration: Manage Clinic NBR Codes (needs rma_enabled=true) and Satellite-sites Admin (needs multisites=on) — NOT COVERED. admin-sites-clinic-numbers records that neither has a menu entry on the packaged Ontario install.
  Routes: admin/ViewClinicNbrManage, admin/clinicNbrManage, admin/ManageSites.
  Proposed check: extend billing-on-admin-config / admin-sites-clinic-numbers under an rma/multisite profile. Add, edit and delete clinic_nbr rows; the Ontario bill form offers the code and billing_on_cheader1 stores it; a site row drives the site selector.
  Fixtures/blockers: the property profile. Note that admin.jsp defaults rma_enabled to true while the shell defaults it to false.

CAISI ON A PACKAGED INSTALL: possible, but only half of it works today.
  - How to turn it on: set caisi=on in /etc/carlos-emr/carlos.properties (an override on top of the in-WAR file) and restart.
  - Data: the Flyway seed already has Facility 1, programs 10001+, program_provider rows for carlosdoc (999998), caisi_role and access_type. Finding 50 (#4012) records an installed CAISI login passing.
  - Testable now: the admin authoring half (System Messages, Facility Messages, Issue Editor, Default Encounter Issue, Lookup Field Editor).
  - Not testable yet: the PMmodule client side (gap above).
  - Profile requirement: also set caisi.search.workflow=false, or every check that searches from the top bar hits the broken ClientSearch2.
  - Other notes: Program Manager and Facility Manager are only linked from each other's pages. The CAISI list pages' Back buttons go to the legacy admin/ViewAdmin. The profile is clinic-wide, so it needs its own exclusive run.

DEAD ROUTE CANDIDATES: admin/ViewLookupListsIndex, admin/ViewLookupListsLookupList, FacilityManager (shell entry commented out; legacy admin.jsp only), admin/ViewAdminBackupDownload and admin/ViewOscarLogging (legacy admin.jsp only; probe per the P1 gap or retire), provider/UserPreference, PrinterList, provider/SaveDemographicAccessory, provider/ViewProviderEncounterSingle, provider/ViewProviderEncounterPrint, provider/ViewChangePassword, provider/ViewMainMenu, provider/ViewAppointmentAdminMonth, provider/ViewObarChecklistEdit, provider/ViewObarRiskEdit (the antenatal planner uses decision/antenatal/*), provider/ViewPreferenceAction, provider/ViewProviderDefaultDxCode, provider/ViewProviderColourErr, provider/ViewProviderFaxErr, provider/ViewFormALPHAprint, provider/ViewFormALPHAprint1, Provider/showPersonal, EditPhoneNum, EditAddress, EditFaxNum, setProviderColour (self-posting only; now handled by Preferences Save All), PMmodule/ProgramManager, PMmodule/ProgramManagerView, PMmodule/FacilityManager, PMmodule/StaffManager, web/dashboard/OutcomesDashboard, mcedt/openAutoUpload, library/eforms/signatureControl(.jsp). Menu links that point to unmapped routes: SurveyManager (CAISI group), admin/RecommitHSFO and RecommitHSFO2 (shown when hsfo.loginSiteCode is set), /commons/omdDiseaseList.jsp (chart header).

ALREADY COVERED (routes the measurement missed):
provider/AddStatus -> appointment-lifecycle (the day-sheet status letter goes through providercontrol displaymode=addstatus; asserts appointment.status advances)
admin/LotNrDeleteRecord -> prevention-admin (Delete sets PreventionsLotNrs.deleted=1; re-add restores the same row)
admin/GroupNoAcl -> admin-role-management (Access Control writes and clears MyGroupAccessRestriction; the group hides and returns on the day sheet)
provider/ViewProviderPreferenceQuickLinks -> provider-quick-links
provider/ViewSchedulePageJs -> loaded by every day-sheet check; appointment-lifecycle calls its updateApptStatus
provider/ViewStoreApptInSession -> fired by the day-sheet E/M links; echart-note-sign-bill asserts the Bill hand-off carries the appointment
mfa/loginMfa, securityRecord/mfa -> login-mfa (fails on the known PIN-wipe defect)
admin-jobs-api-keygen -> admin-jobs + admin-api-keygen (+ oauth-rest-surfaces)
role-privilege-matrix (read half) -> authz-read-role-matrix, authz-read-admin-objects, admin-role-management
admin-misc items -> Help Link (admin-sites-clinic-numbers), Customize Measurements (measurement-type-group-admin), Dx quick list (dx-registry-quicklist), Consult Appointment Instructions (admin-lookup-lists), Access Control and Fix notes (admin-role-management), Group Preferences (my-groups), Document Description Template (provider-preferences), Update Patient Provider (admin-update-demographic-provider), Purge Audit Log (admin-audit-log)
default Dx code, provider colour, address/phone/fax -> provider-preferences-cpp-dx, gap-provider-preferences-save-all, provider-signature-contact (all through Preferences; the old standalone pages are dead routes)
```
