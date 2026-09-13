# Playwright coverage plan — release/2026.08

Status: **plan only**. Nothing in this document has been implemented. It records what
browser checks exist on `release/2026.08` (at `7e322ee3`, 2026.08.0-alpha13-SNAPSHOT), what
they leave untouched, and — in priority order — which scripts to add and which to change to
get comprehensive, *meaningful* Playwright coverage of CARLOS. "Meaningful" here has the
definition the suite already uses ([clinical-workflow-browser-checks.md](clinical-workflow-browser-checks.md)):
a check reaches the surface the way an operator does, asserts the rows that reached MariaDB
(or the bytes that reached the browser), proves a refusal against a matching acceptance,
cleans up on a per-run marker, and reports a defect rather than pinning it.

Related open issues this plan absorbs or depends on: #3313 (12 suites fail against a real
deployment), #3317 (`validateBaseUrl` duplicated in 19+ scripts), #3598 (`ignoreHTTPSErrors`
unconditional), #3600 (no signal handler in 11 scripts), #2859 (measurement-graph image bytes),
#3578 (Rx preview iframe race), #3377 / #3346 / #3275 / #3237 (already pinned by checks).

---

## 1. Baseline: what exists today

| Asset | Count | Runs where |
|---|---|---|
| `scripts/*-playwright-checks.js` | 75 | By hand (`npm run test:<name>`), and as the suite loop in [deb-install-validation.md §6](deb-install-validation.md#6-run-the-suite) against a packaged VM |
| `scripts/demographic-master-crud-smoke.js` | 1 | Same loop |
| `scripts/e2e/fax/*.js` | 4 | Never in CI; live SRFax account, costs fax pages |
| `scripts/*.test.js` (harness/source-contract unit tests, `node --test`) | 24 | CI: `script-regressions.yml` on every PR |
| MCP-driven manual UI tests (`docs/ui-tests/test-1..9`, `/ui-tests:testN` skills) | 9 | By hand, screenshot-compared |

**No Playwright check runs in CI today.** `script-regressions.yml` runs only the `.test.js`
files (no browser, no database). `maven-project.yml` already boots the `carlos-tomcat-dev`
container and can run `make install`, so the missing piece for a CI browser tier is a database
service plus the demo load (`.devcontainer/db/scripts/populate_db.sh`) — not a new runtime.

### 1.1 Route coverage, measured

For every `<action name>` in `struts-*.xml` (1,064 after dropping one-word generic names),
does any check script reference it? (`grep -F` over all scripts; "touched" over-counts slightly
where a name is a substring of another.)

| Module | Routes | Touched | Coverage | Largest untouched areas |
|---|---:|---:|---:|---|
| admin | 68 | 11 | 16% | lookup lists, system/facility messages, issue admin, audit log, jobs, API clients, keygen, email, security record edit/delete, provider record edit |
| billing | 185 | 7 | 4% | **entire BC module (0)**; ON correction/delete/status, invoices, OHIP file generation, RA import/settle, report centre, billing admin config, MCEDT |
| clinical | 168 | 14 | 8% | dx registry, flowsheets (patient side), measurement history/graphs/admin, immunization schedule, decision support, calculators, antenatal/annual planners, note browser, CPP sections other than Social Hx |
| demographic | 46 | 4 | 9% | edit/update, merge, contacts/relations, labels/envelope PDFs, export/import, patient sets/cohorts, audit, health-care team |
| document | 47 | 9 | 19% | forward/MRP/reassign/file, document edit/refile/delete/split/combine, HRM display/modify, incoming docs, patient match |
| eform | 53 | 25 | 47% | groups, independent eForms, image manager, visual editor, generator, save-as-eDoc, field-note report |
| encounter | 90 | 14 | 16% | every `display*` navbar module except Rx/Allergy/Measurements, consultation config CRUD, measurement admin, immunization config |
| form | 22 | 5 | 23% | dashboard display/drilldown/export/bulk action, form XML upload, RH workflow |
| integration | 30 | 4 | 13% | MCEDT, DHIR, workflow list, swipe validation |
| lab | 29 | 6 | 21% | HL7 upload paths, forwarding rules, cumulative values, BC lab pages, lab label PDF |
| login | 14 | 6 | 43% | **MFA (`mfa/loginMfa`, `securityRecord/mfa`)**, facility select, heartbeat, `securityError` |
| messenger | 23 | 6 | 26% | attachments, PDF preview, transfer to chart, demographic-linked message list |
| pmmodule | 13 | 0 | 0% | provider signature/phone/address/printer/fax edit, CAISI program/client/staff managers |
| prescription | 69 | 17 | 25% | favourites, stash, discontinue/delete, pharmacy manage, interactions/renal, print profile |
| provider | 51 | 8 | 16% | preferences (all tabs), change password, fax/email queues, encounter history, receptionist find-provider |
| report | 84 | 6 | 7% | report-by-template, query-by-example, day sheet, CDS4/MIS/provider-service, letters/envelopes/spreadsheets, CDM, clinical export, patient list |
| scheduling | 72 | 14 | 19% | appointment repeat/group/cut/copy/print, waiting list, holidays, template codes, appointment status/type admin, my-groups |
| **Total** | **1,064** | **156** | **~15%** | |

Route count is a proxy: a route can be "touched" by a hand-built URL the user never types, and
a real workflow spans several routes. Section 3 is organised by workflow, not route.

### 1.2 What the existing checks already do well

The alpha-11 and clinical-workflow passes set the bar and this plan keeps it. Already pinned
(see [alpha-11-tester-coverage.md](alpha-11-tester-coverage.md) for the full map): login /
forced reset / CSRF rejection / lockout avoidance; logout broadcast, session invalidation,
multiple sessions; provider + login-account creation; role assignment; demographic add and
CRUD smoke; DOB search; quick-search booking; day-sheet booking; appointment edit/status/
cancel/delete; schedule templates and settings; eChart first render + Social Hx; note save/
sign/bill + timer; new-patient notes loop; chart print through the WAF; clinical free text
through the WAF; vitals + BMI; refused vitals; Penicillin allergy; allergy → Rx alert; drug
search; Rx signature/fax/reprint/re-prescribe/record binding/pharmacy preview/med history;
prevention brand picker; prevention recall report; Rourke 2017; lab acknowledge + PDF +
cumulative; lab macro → tickler; document upload; eDoc header navigation; Inboxhub entry;
messenger compose (inbox + chart) and inbox actions; tickler CRUD + note dialog; consultation
create/sign/print/nullable columns; specialist + CPSO; ON OHIP/WSIB/bonus bill save; ON
third-party navigation; service-code admin; flu billing report; patient-list export;
demographic report navigation; flowsheet admin; Select Forms panel; eForm manager CRUD,
render, saved render, RTL attachments/print/PDF, page exclusion, runtime compat, corpus soak;
fax configure; DrugRef update; response-sanitization error replacement; PR-hardening GET
refusals; browser-surface smoke; schedule-link smoke; application health.

---

## 2. Part A — Changes to the existing suite (do these first)

The gaps below are structural; every new check in Part B is cheaper and more reliable once
they are closed. Numbers are measured on this branch.

### A1. One shared harness, and retire the local copies

`scripts/eform-local-playwright-utils.js` is the de-facto harness (50 of 75 scripts require
it) but 33 scripts still carry their own `login()`, 31 their own `validateBaseUrl()` (#3317),
35 their own `sql()`/defaults-file helper, and only 23 handle SIGINT/SIGTERM (#3600). 51
scripts pass `ignoreHTTPSErrors: true` unconditionally (#3598).

Plan:

- Move the harness to `scripts/lib/playwright-harness.js` (keep `eform-local-playwright-utils.js`
  as a re-export shim for one release so nothing breaks) and add to it:
  - `readConfig()` — one env contract (`BASE_URL`, `CHROME_PATH`, `TEST_USER/PASSWORD/PIN`,
    `MYSQL_*`, `EXPECT_FRONT_DOOR`, `ALLOW_NON_LOCAL_BASE_URL`) with **documented defaults per
    check** (the "required env is only discoverable by reading the throw" finding in #3313).
  - `sql()` / `sqlRows()` over a 0600 defaults file, with the `mysql -B` unescape the clinical
    doc warns about, and `withMysqlDefaults(fn)` so cleanup always removes the file.
  - `login()` that understands forced-reset, MFA challenge, facility select and the
    `select_facility` result — today each copy handles a different subset.
  - `ignoreHTTPSErrors` gated on a loopback/private host (#3598).
  - `runCheck({ name, steps, cleanup })` — installs the `graceful-signal-cancellation`
    handler, standardises `PASS/FAIL/SKIP/WARN <check> <step>` lines, exit codes
    (0 pass, 1 fail, 2 skipped-for-missing-fixture — distinct from a failure), and an optional
    `RESULT_JSON=<path>` / JUnit XML output for CI.
  - `navigate.*` — a small map of *how a user reaches* each section (schedule top-nav →
    Tickler/Inbox/Consultations/Report/Billing/Admin/Preferences/eDoc/Msg; master record →
    tabs; eChart navbar → module "+" popups; Administration shell → `#myFrame`/`#dynamic-content`
    panels). Every new check enters through it, so a moved link fails *one* helper and not
    twenty asserts, and the "reach it the way a user reaches it" rule stops depending on each
    author re-deriving the path.
  - `consoleBaseline` — one shared allow-list file (`scripts/lib/console-baseline.json`) of
    the known legacy console errors, each entry keyed to an issue number (obs. 6, 14, 15 in
    the alpha-11 page: `providerSignatureImage` 404, missing favicon on Rourke, the
    `getActiveText()` TypeError). New checks fail on *new* console errors; the list burns down
    as issues close.
- Unit-test the harness in `scripts/playwright-harness.test.js` (CI already runs `*.test.js`).
- Migrate the 25 scripts that do not require the harness, then delete the local copies. Do it
  in slices of ~8 scripts per PR, re-running each slice against the devcontainer.

### A2. A suite manifest and a runner

Today the "suite" is a bash `for` loop in the runbook with two `case` exclusions and one
per-script timeout override. Replace it with:

- `scripts/playwright-suite.json`: one entry per check — `tier` (`smoke` | `core` |
  `extended` | `front-door` | `live-external`), `mutates` (tables it writes), `fixtures`
  (SQL/files it needs), `env` (required vs optional, with defaults), `timeoutSec`,
  `runLast` (for the login-lockout style checks), `provinces` (`ON`, `BC`, `all`).
- `scripts/run-playwright-suite.js`: `--tier`, `--only`, `--skip`, `--province`, `--junit
  <file>`, `--screenshots <dir>`; refuses to run a `mutates` check against a non-local host
  without `ALLOW_NON_LOCAL_BASE_URL`; records `NRestarts`-style process identity where it can
  (`/status/SessionHeartbeat` build tag before and after) so a green suite cannot span a JVM
  restart; prints a summary table.
- `package.json`: `test:playwright` → runner; add the 8 missing aliases
  (`application-health`, `consultation-nullable-fields`, `demographic-add`, `drugref-update`,
  `patient-list-by-appointment-export`, `patient-search-dob`, `rx-preview-pharmacy`,
  `demographic-master-crud-smoke`).
- The deb runbook §6 loop becomes `node scripts/run-playwright-suite.js --tier core --tier
  front-door --junit ...`; the prose about per-check knobs moves into the manifest's `env`
  entries and is rendered into a generated `docs/ui-tests/playwright-checks-reference.md`
  by a `.test.js` that also fails when a check exists without a manifest entry.

### A3. Fixtures as files, not prose

Checks currently seed via inline SQL strings and depend on undocumented demo-dataset facts
(demographic 1's chart 500s on missing HRM files; `999998` has no OHIP number; consultation
services inactive on a pristine ON install; pharmacies have blank fax numbers). Plan:

- `scripts/fixtures/sql/<check>.seed.sql` / `.teardown.sql` with a `PW_<CHECK>_<RUN>` marker
  column convention; `scripts/local-fixture-cleanup.js` gains a `--sweep-stale` that removes
  any `PW_*` rows older than a day (a failed run must not poison the next).
- `scripts/fixtures/files/`: synthetic HL7 ORU (one accession, two versions — the `showLatest`
  chain), a synthetic OHIP RA file and error report, a synthetic HRM XML+PDF, a 2-page PDF and
  a JPG for uploads, an eForm `.zip`, a schedule-of-benefits CSV, a demographic import CSV.
  All names `FAKE-`, all numbers obviously synthetic; reviewed under the same no-PHI rule as
  `demo-specialists.sql`.
- A `demo-dataset-contract.test.js` that reads `development.sql` and asserts the facts the
  checks rely on (which demographics have clean charts, which have appointments on which
  dates, active consultation services after V1.0.23, etc.) so a dataset change fails in CI
  before it fails a browser run in a VM.

### A4. A CI browser tier

- New workflow `playwright-smoke.yml`: on PRs that touch `src/main/webapp/**`,
  `src/main/java/**/app/**` (filters), `src/main/java/**/web/**`, `scripts/**`, or `struts-*.xml`:
  start `carlos-tomcat-dev` + a MariaDB service, run `populate_db.sh`, `make install`, then
  `run-playwright-suite.js --tier smoke` (target ≤ 12 minutes: login, schedule links,
  application health, browser surfaces, echart first render, tickler CRUD, appointment
  lifecycle, document upload, drug search, eform render, error sanitization). Upload
  screenshots + JUnit as artifacts. Non-required for two weeks of green, then required.
- Nightly on `develop` and `release/**`: `--tier core` (everything that needs only bare
  Tomcat + demo DB), with a summary comment on failure into a tracking issue.
- The `front-door` tier (WAF-dependent asserts: `echart`, `echart-print`,
  `clinical-freetext`, `tickler-crud`'s `&cmd` text, and the new B19 items) stays in the deb
  runbook until `deb-packages.yml` can install into a container with nginx + ModSecurity;
  track as a follow-up rather than blocking this plan.

### A5. Targeted changes to individual existing scripts

| Script | Change | Why |
|---|---|---|
| `eform-consultation-acceptance` | Drive the `#specialistInput` autocomplete; fail if the programmatic fallback fires | Obs. 5: silently takes the fallback since the form changed |
| `logout-redirect`, `application-health`, `logout-session-invalidation` | Decide one post-logout target (`/logoutPage` vs login) with maintainers and assert it in all three | #3313 items 2 and 8 — three checks disagree with the app and each other |
| `form-rourke2017` | Promote the plain-Save `WARN` to a `FAIL` once `/form/forwardname` sends a `Content-Type` (obs. 11); add the NULL-page-1 500 and missing-`.jsp` 400 as negative asserts when fixed | A WARN nobody reads is not coverage |
| `consultation-request-create` | Assert the Created/Updated confirmation once `transType` survives the redirect (obs. 1) | Same |
| `billing-on-submit` | Assert no `TypeError` from `onSave()` (obs. 2) once `#payee` is guarded; assert the billing physician defaults to the appointment provider (obs. 4) when fixed | Same |
| `echart-note-sign-bill` | Add a `WARN` that the Save / Sign / Bill row is inside the viewport at 1366×768 and 1920×1080; promote when obs. 16 is fixed | The buttons are clicked via handlers today, which hides a real layout defect |
| `lab-macro-tickler` | Enter the lab through the Inboxhub row (as `lab-acknowledge` does) rather than `ViewLabDisplay` by URL; assert the future-dated tickler is visible in the patient tickler view once obs. 12 is fixed | Rule 2 of the clinical doc |
| `echart-vitals-bmi`, `measurement-validation` | Share one measurement-popup helper; enter via the eChart Measurements module | Two checks, two entry paths to one popup |
| `prevention-brand-picker`, `allergy-add-penicillin` | Assert the `/cvc` lot lookup answers (obs. 20) or is absent — today a 404 is tolerated | Report, don't encode |
| `rx-fax-reprint-represcribe` | Drop the tolerated #3578 page error when it closes | Burn-down |
| `schedule-links` | Becomes a thin wrapper over `navigate.*` (A1) and asserts every top-nav item, not six | The nav map is the contract |
| `browser-surface` | Fold into `admin-index-links` and `master-record-tabs` (B) | Overlaps |
| `demographic-master-crud-smoke` | Rename to `-playwright-checks.js`, adopt the harness, add DB asserts (it reads only the page today) | Consistency; page-only asserts pass on a save that wrote nothing |
| `login` | Add the MFA and facility-select paths (see B1); keep the lockout probes isolated | Login has two uncovered branches |
| `eform-corpus-soak` | Manifest `tier: extended`, `SKIP` (exit 2) when `EFORM_CORPUS_DIR` is unset | Today it fails for a missing fixture |
| `consultation-signature-submit` | Manifest default `CONSULT_DEMO_NO=1` | #3313 "no documented default" |
| all 51 with `ignoreHTTPSErrors: true` | Use the harness gate | #3598 |
| all 52 without a signal handler | Use `runCheck()` | #3600 |

---

## 3. Part B — New checks, by workflow and priority

Priority: **P1** = clinical/financial/security workflows an operator uses daily with no
coverage; **P2** = daily-use surfaces with partial coverage or lower blast radius; **P3** =
admin-rare, province-gated, or needs infrastructure first. Each entry names the entry path,
the assertion shape, fixtures, and the routes it covers (so A2's manifest can be filled from
this table). "DB" means the check reads the resulting rows; "bytes" means it asserts the
downloaded content (magic number + content type, per the direct-response contract in
`CLAUDE.md`).

### B1. Authentication, session, authorisation — P1

| Check | Entry | Asserts | Fixtures / cleanup |
|---|---|---|---|
| `login-mfa` | Login → Preferences → MFA enrolment (`securityRecord/mfa`) → logout → login → `mfa/loginMfa` | Wrong code refused with no session; right code lands on the schedule; enrolment row written; disabling MFA restores plain login | Throwaway login account from the `add-login-account` flow; delete after |
| `login-facility-select` | Login with a provider in two facilities | `select_facility` renders, choice persists in session, chart access respects it | Second facility row; restore |
| `session-heartbeat-timeout` | Schedule open; drive `status/SessionHeartbeat`; expire the session server-side (SQL `session` or a short `session-timeout` on a dev profile) | Next click lands on login, no chart HTML leaks, `encounter/ViewTimeOut` renders in a popup | none |
| `password-change-preferences` | Preferences → Change Password | Weak password refused (server-side, not only JS); good password accepted; old password fails; hash changed | Restore `TEST_PASSWORD_HASH` as `login` does |
| `account-lockout-unlock` (runLast) | Three bad logins on a throwaway account → `admin/UnLock` | Locked row, login refused with the lockout message, unlock clears it, login succeeds | Throwaway account |
| `security-record-admin` | Administration → Search Login Records → edit → delete | `security` row updated / removed; a deleted login cannot log in | Throwaway account |
| `provider-record-admin` | Administration → Search Provider Records → Update; Provider Template; Provider Privilege | `provider` row fields (OHIP no, billing no, status) updated; inactive provider disappears from the schedule provider list | Throwaway provider |
| `role-privilege-matrix` | Create a role without `_rx`, `_billing`, `_admin`; a provider with that role; log in as it | Chart shows no Rx/Billing links; **direct requests** to `rx/choosePatient`, `billing/CA/ON/*`, `admin/ViewAdmin` answer the sanitised security page, not content; audit rows recorded | Throwaway role + provider; the one place a hand-built URL is the point |
| `csrf-negative-matrix` | For each mutator family (tickler, appointment, note, Rx, allergy, billing, admin provider, eForm) | POST without token → 403 and **no row**; same POST with token → row | Reuses each family's seed; parameterised from the manifest |
| `mutator-get-rejection-live` | GET on the same mutators | 405 / SecurityException page and no side effect — the live twin of `MutatorActionGetRejectionContractUnitTest` | none |

### B2. Schedule and appointments — P1

| Check | Entry | Asserts | Fixtures |
|---|---|---|---|
| `appointment-form-fields` | Day sheet slot → Add Appointment | Every field round-trips (reason, reason code, notes, type, duration, status, location, resources, urgent, `creator`); edit popup shows them; DB `appointment` | Far-future day (`APPOINTMENT_DAYS_AHEAD`) |
| `appointment-repeat-group-copy` | Add Appointment → repeat booking; group booking; cut/paste; copy; print | N rows for a repeat; group rows share `multisites`/group id; cut moves, copy duplicates; print answers a printable page | Same |
| `schedule-views` | Top nav: Day/Week/Month/Flip/Zoom/Search views, View All, multiplier, jump-to-date, provider filter (`ViewReceptionistFindProvider`) | Each view renders the seeded appointment; the `provider_no` deep link hides E/B links only on week view (obs. 13, pin the fixed behaviour) | Seeded appointment |
| `schedule-admin-settings` | Administration → Schedule Management: Holiday Setting, Template Code Setting, Appointment Status Setting, Appointment Type, Provider Colour | Rows in `scheduleholiday`, `scheduletemplatecode`, `appointment_status`, `appointmentType`; the new status appears in the edit popup's status list | Restore |
| `waiting-list` | Master record → Waiting List; Administration → Waiting List names | Add / remove / rename; `waitingList`, `waitingListName` rows | Throwaway list |
| `appointment-search-history` | Top nav Search → Appointments; master record → Appt History | The seeded appointment is found by date/provider/patient; history lists it | Seeded appointment |
| `my-groups` | Administration → Groups / schedule View by group | Create group, add providers, schedule shows the group's providers | Throwaway group |

### B3. Demographics and the master record — P1

| Check | Entry | Asserts | Fixtures |
|---|---|---|---|
| `demographic-edit-update` | Search → patient → Edit | Address/phone/email/HIN+version/status/roster/MRP/language round-trip; HIN check-digit validation (`validateHC`) refuses a bad HIN and accepts a good one; `demographic` + `demographicExt` rows; enrolment history row on roster change | Throwaway demographic |
| `master-record-tabs` | Every link on the master record (Appt Hx, Billing Hx, eForms, Forms, Docs, Labs, Consults, Tickler, Rx, Prevention, Export, Labels, Waiting List, Patient Set, Swipe) | Each opens without error page or new console error; replaces the master-record half of `browser-surface` | Demographic 2 |
| `demographic-contacts` | Master record → Relations / Contacts / Professional Contacts / Health Care Team | Add/edit/delete a relation, an alternate contact, a professional contact; SDM flags; `relationships`, `demographiccontact` rows | Throwaway rows |
| `demographic-merge` | Administration → Merge Records | Two throwaway patients; after merge the child shows merged status, chart of the parent lists the child's notes, and search finds one active record; `demographic_merged` rows | Throwaway pair |
| `demographic-labels` | Master record → Label / Address Label / Chart Label / Envelope / Lab Label | bytes: `%PDF`, `application/pdf`, non-empty | none |
| `demographic-export-import` | Master record → Export; Administration → Import | Export produces the CDS XML for the patient (bytes); importing the fixture CSV/XML creates `FAKE-` patients; import log downloads | Fixture files; delete created rows |
| `patient-set-cohort` | Master record → Add to Patient Set; Report → Demographic Set Edit; Cohort | Set created, patient added, set drives a report row (the `prevention-recall-report` pattern) | Throwaway set |
| `demographic-audit` | Master record → Audit | The edit `demographic-edit-update` made appears with user and timestamp | Reuse |
| `patient-search-modes` | Search popup: name, HIN, phone, chart no, DOB (existing), address, "all"; inactive/active filters; swipe | Result set per mode; inactive excluded by default and included on request; no PHI in the URL beyond the search term | Demo data |

### B4. eChart / encounter — P1

| Check | Entry | Asserts | Fixtures |
|---|---|---|---|
| `echart-cpp-sections` | Chart → each CPP "+" (Family Hx, Medical Hx, Ongoing Concerns, Reminders, Risk Factors, Other Meds, Social Hx already) | Add, edit, archive; `casemgmt_note` rows with the right issue code; archived notes leave the box | Demographic 2 |
| `echart-note-lifecycle` | Chart → existing note | Edit creates a revision (`casemgmt_note` uuid chain); signed note is read-only; annotation; assign issues (`ViewIssueSearch`); `casemgmt_tmpsave` draft restored after reopen; a second context opening the same note gets the lock/`ViewConcurrencyError`; obs. 18 stale-lock recovery | Demographic 2; clear locks |
| `echart-dx-registry` | Chart → Dx registry (`setupDxResearch`) | Add ICD-9 code via search and via quick list, resolve, reactivate; `dxresearch` rows; the diagnosis triggers the matching flowsheet item in the navbar | Demographic 3 |
| `clinical-flowsheet` | Navbar flowsheet item (dx-triggered, per `echart-dx-registry`) → `ViewTemplateFlowSheet` → Add links → `AddMeasurementData` | Renders; Add saves; print answers a page; the `AddMeasurementData` 500 without `demographic_no` is asserted fixed | Requires B4 dx fixture — the "recommended next addition" of the clinical doc |
| `echart-measurements-history-graph` | Measurements module → History / Graph / Export | History lists rows; graph image is a real PNG (#2859); export bytes | Seeded measurements |
| `echart-navbar-modules` | Every `EctDisplay*` module and its "+" (Consults, Docs, eForms, Forms, HRM, Labs, Msg, Pregnancy, Episode, Contacts, Decision Support, Tickler, Prevention, Issues/Resolved) | Renders with rows for demographic 2, no error page, popups open the right route; read-only | Demographic 2 |
| `encounter-templates` | Chart → Insert Template; Administration → templates | Template text lands in the note; `encounterTemplate` CRUD | Throwaway template |
| `decision-support-alerts` | Chart of a patient matching a seeded DS rule / guideline list | Alert renders; guideline detail opens; dismissing persists | Seeded rule (Drools) |
| `episode-pregnancy` | Chart → Episodes; → Pregnancy (antenatal planner, OBAR risk/checklist) | Episode CRUD; pregnancy episode creates the planner rows; planner print bytes | Throwaway rows |
| `immunization-schedule` | Chart → Prevention → Schedule; Administration → immunization sets | Schedule renders for age; config save round-trips | Restore config |
| `calculators` | Chart → Calculators | CAD risk / osteoporotic fracture / simple calculator compute deterministic results from typed inputs | none |

### B5. Prescriptions and allergies — P2

| Check | Entry | Asserts |
|---|---|---|
| `rx-favorites` | Rx → Favorites: add from a written script and static, use, edit, delete | `favorites` rows; used favourite prefills the script |
| `rx-edit-discontinue` | Rx → drug profile: edit (`UpdateScript`), discontinue with reason (`RxReason`), delete, reorder, stash/clear pending | `drugs.archived`, `archivedReason`, order; history view shows it |
| `rx-interactions-renal` | Rx with a known DrugRef interacting pair; provider warning level pref | Interaction panel shows severity; changing `rxInteractionWarningLevel` hides/shows; renal dosing page; limited-use code popup |
| `rx-pharmacy-manage` | Rx → Manage Pharmacy: add, edit, set patient default, deactivate | `pharmacyInfo`, `demographicPharmacy` rows; preview picks the default |
| `rx-print-profile` | Rx → Print drug profile / previous prints | bytes `%PDF`; `ViewShowPreviousPrints` lists the print |
| `allergy-edit-delete` | Chart → Allergies: non-drug allergy, severity, reaction edit, delete/archive | `allergies` rows; chart box updates; the `ChooseAllergy2.jsp` unnamed form (obs. 20) asserted fixed |
| `rx-write-to-encounter` | Rx → Write to encounter | Note contains the Rx text; `casemgmt_note` row |

### B6. Labs, Inboxhub, documents, HRM — P1

| Check | Entry | Asserts | Fixtures |
|---|---|---|---|
| `inboxhub-filters` | Inboxhub top bar: provider, status (N/A/F), patient, type (lab/doc/HRM), date range, search, pagination, view mode | Rows match the SQL for the same filter; the `contextPath is not defined` pageerror (#3313 #1) stays fixed; `LabDataController` answers | Demo data |
| `lab-upload-hl7` | Administration → Lab Upload (`lab/labUpload`, `newLabUpload`, `CMLlabUpload`, `insideLabUpload`) | Synthetic HL7 imports; appears in Inboxhub unmatched; Patient Match / Search Patient links it; Unlink removes; `hl7TextInfo`, `patientLabRouting`, `providerLabRouting` rows | `fixtures/files/*.hl7`; delete rows |
| `lab-forward-mrp-file` | Inboxhub row → Forward to provider; Send to MRP; Reassign; File | `providerLabRouting` rows per action; forwarded lab appears in the other provider's inbox; forwarding rules admin (`ForwardingRules`) creates a rule and an upload honours it | Second provider |
| `lab-cumulative-requisition` | Lab → cumulative values, ON lab values graph, link requisition, lab label | Cumulative table columns match versions; graph bytes; label `%PDF` | Chain fixture |
| `document-manage` | Inboxhub doc → edit description/type/observation date, refile, delete, undelete, split, combine, add link; document browser; multi-page display; description templates admin | `document` rows and `ctl_document` links; split produces two rows and two files; combine one; deleted hidden then restored | Fixture PDFs; delete files via `EDOC_NAV_DOCUMENT_STORE` |
| `hrm-report-lifecycle` | Inboxhub HRM → Display → Modify (sign-off, comment, category) → Print; Statement | `HRMDocument*` rows; print bytes; **a missing HRM file does not 500 the chart notes panel** (obs. 17) once fixed | Synthetic HRM XML/PDF |
| `fax-queue-admin` | Administration → Manage Faxes; provider fax page | Seeded outbound rows render with status; resend/cancel updates `FaxJob`; **no live send** (`fax-resend.test.js` is the unit twin) | Seeded `FaxJob` rows |

### B7. Consultations — P2

| Check | Entry | Asserts |
|---|---|---|
| `consultation-edit-status` | Consultations list → open → edit: status Pending→Completed, appointment date/time, urgency, attach doc/lab/eForm, referral date, letterhead | `consultationRequests` + `consultdocs` rows; list filters by status; attachments appear in the print PDF (bytes) |
| `consultation-config-admin` | Administration → Consultation services / institutions / departments (`ViewAddService`, `ViewEditInstitutions`, …) | CRUD rows; `ShowAllServices` without `serviceId` no longer 500s (obs. 10) |
| `consultation-response` | `EnableConRequestResponse`; response fields | Response saved and displayed |

### B8. Messenger — P2

`messenger-attachments`: attach a document, lab and eForm to a message (`attachmentFrameset`,
`AdjustAttachments`), recipient opens it, PDF preview bytes, Transfer to chart
(`WriteToEncounter`) writes the note, Group admin creates a group and a group message reaches
all members (`messagelisttbl` per recipient), demographic-linked message list from the chart
(`DisplayDemographicMessages`), markdown body renders (obs. 8).

### B9. Ticklers — P2

`tickler-forward-filters`: forward to another provider, priority/status/date filters, text
suggestions admin (`EditTicklerTextSuggest`), tickler preferences (`setTicklerPreferences`),
dashboard Assign Tickler (`web/dashboard/display/AssignTickler`), the patient-view date
window (obs. 12) asserted fixed.

### B10. Prevention — P2

`prevention-edit-delete-refuse`: edit and delete an existing immunization, "refused" and
"ineligible" states, comments, next-date recall; `preventions` + `preventionsExt`. 
`prevention-admin`: Prevention Manager / List Manager config, RTL preventions, print
prevention record (bytes).

### B11. Clinical forms (139 JSPs, 22 routes) — P2

- `form-catalog-smoke` (parameterised): from Administration → Select Forms, for each form in
  `encounterForm`, enable it, open it for demographic 2 from the chart Forms module, assert no
  error page and no new console error, press Save, assert a row in the form's table (every
  legacy form saves to its own `form*` table; the manifest lists the table per form), then
  delete the row and disable the form. One run covers ~40 forms; a failing form is reported
  by name so a broken JSP is a one-line finding.
- Deep checks for the forms clinics use daily: `form-rourke2020` (all four pages, growth
  percentile computation), `form-growth-chart` (+ print servlet bytes), `form-annual-v2`
  (male/female), `form-mental-health-on` (Forms 1/14/42), `form-discharge-summary`,
  `form-bcar2020` (BC — see B14), `form-xml-upload` (custom form upload), the PDF servlets
  `/form/createpdf` and `/form/createcustomedpdf` (bytes; `error-sanitization` already uses
  the latter for the 500 case).

### B12. eForms — P3 (already 47% by route)

`eform-groups-independent`: groups CRUD (`efmmanageformgroups`, add/remove), independent
eForms list, deleted lists + restore (`pr-hardening` covers the GET refusal only), image
manager upload/delete/display, `saveEFormAsEDoc` (document row + file), `downloadEFormPdf`
bytes, `efmOpenEformByName`, field-note report, Administration → eForm Report Tool, visual
editor and generator open and save a trivial form.

### B13. Ontario billing — P1 (4% covered; the revenue loop is unprotected)

| Check | Entry | Asserts | Fixtures |
|---|---|---|---|
| `billing-on-correction-delete` | Master record → Billing Hx → bill → Correction | Edit service code/dx/units → `billing_on_item` updated; status change (`ViewBillingONStatus`, ER update); delete variants (with/without bill no, no appointment) mark `billing_on_cheader1.status='D'` and unbill the appointment | Bill created through `billing-on-submit`'s path |
| `billing-on-invoice-3rdparty` | Bill → Invoice / Invoice list print; 3rd-party invoice; Add 3rd-party payment; payment types admin | bytes `%PDF` with the invoice logo; `billing_on_payment` rows; statement balances | Third-party bill |
| `billing-on-ohip-file-cycle` | Billing → Report Centre → generate OHIP claim file (`ViewGenReport`/`BillingONUpload`/`moveMOHFiles`) → import fixture RA (`ImportOnRA`) → RA summary/detail/errors → settle (`ViewOnGenRAsettle`) | Claim file bytes match the MOH fixed-width layout for the seeded bills; bills flip to `B`; RA import creates `ra_header`/`ra_detail`; settle flips to `S`; error report upload (`DocumentErrorReportUpload`) marks rejects | Synthetic RA + error-report files; seeded bills |
| `billing-on-mri-batch-clipboard` | Billing → MRI; Batch billing; Clipboard / print | MRI lists unbilled/errored; batch creates N bills from N appointments; clipboard rows and print bytes | Seeded appointments |
| `billing-on-reports` | Report Centre: billed / unbilled / unsettled / flu (existing) / OB; EA report; L report; end-year statement (+PDF); age-sex; group report; INR billing | Row counts equal SQL for the same range; PDF/CSV bytes | Seeded bills |
| `billing-on-admin-config` | Administration → Billing: manage billing forms (add/dx/service/premium/billtype), locations, private codes, dx-code update, benefit-schedule upload (fixture), billing settings, GST control/report, clinic number, referral doc add/search, favourite codes, practitioner premium | Each CRUD writes and restores `ctl_billingservice`, `billingservice`, `billing_on_payment_type`, `clinic_nbr`, `professionalSpecialists`… | Restore |
| `billing-shortcut` | Bill form → Shortcut pg1/pg2 | Shortcut save creates the same rows as the long form | Seeded appointment |
| `mcedt-ui` (`MCEDT_LIVE=false`) | Billing → MCEDT | Page renders; upload/download forms post to a `page.route()` stub; no live MOH traffic. A live `scripts/e2e/mcedt/` twin, like the fax e2e, is out of scope for CI | Stub |

### B14. BC billing, BC labs, BC forms — P3 (blocked on infrastructure)

There is no BC devcontainer profile (`billregion=ON`, `populate_db.sh` loads the ON
migration set). Prerequisite: a `BC` profile for `populate_db.sh`/`carlos-ctl demo-data` and
a `--province BC` manifest filter. Then, in the same shape as B13: `billing-bc-create-view`
(`quickBillingBC`, `CreateBilling`/`SaveBilling`, `billingView`), `billing-bc-teleplan-file`
(`GenerateTeleplanFile`, `ProcessRemittance` with a synthetic remittance, `SimulateTeleplanFile`),
`billing-bc-wcb` (`formwcb`, `viewformwcb`, WCB correction), `billing-bc-private-statement`,
`billing-bc-codes-admin`, `lab-bc-pages` (`lab/CA/BC/*`), and `form-bcar2020` /
`form-bcnewborn2008`. Until the profile exists these routes have **zero** coverage and should
be listed as such in the runbook rather than silently absent.

### B15. Reports and dashboards — P2

| Check | Asserts |
|---|---|
| `report-index-links` | Every link on the Report index renders without error page/console error (extends `schedule-links`, which only opens the index) |
| `report-by-template` | Upload fixture template, list, group, run with parameters, result table equals SQL, export CSV/XLS bytes, delete |
| `report-query-by-example` | Save a query, favourite it, run it, load favourites |
| `report-daysheet-labs` | Day sheet for a provider/date lists seeded appointments; lab day sheet print bytes |
| `report-clinical-reports` | CDS4, MIS, provider service report (+export), catchment, dx registry, visit control, age-sex, patient list (`ViewPatientlist`), clinical export, `report/reportDownload` — each answers a real report or a documented empty state |
| `report-letters` | Generate letters / envelopes / spreadsheet for a demographic set; manage/download/delete letter; bytes |
| `report-cdm` | Patients met guideline / abnormal range / frequency of tests over seeded measurements |
| `dashboard-display` | Main-menu dashboard: indicators render, drilldown lists patients, export bytes, bulk action (assign tickler) writes `tickler` rows, shared outcomes dashboard |

### B16. Administration and preferences — P2

| Check | Asserts |
|---|---|
| `admin-index-links` | Every link/popup on the Administration index opens without error page or new console error; replaces the admin half of `browser-surface`. Cheap and catches the #3313 class of JS breakage across ~90 pages |
| `admin-lookup-lists` | Lookup list CRUD; the new item appears where the list is used (e.g. a demographic field) |
| `admin-messages` | System message and facility message CRUD; the message shows on the login page / schedule banner; default encounter issue |
| `admin-issue-admin` | Issue add/edit; the issue is offered in the note issue search |
| `admin-audit-log` | Log Report lists the READ/UPDATE audits earlier checks generated (PIPEDA evidence); Audit Log Purge refused without privilege and, with it, only purges outside the retention window |
| `admin-jobs-api-keygen` | Job types / jobs CRUD; API client create/revoke (OAuth keys); keygen create/manage, public key download |
| `admin-email-config` | Configure Email save/restore; Manage Emails queue with seeded rows; compose/send against a stubbed transport (no live mail) |
| `admin-misc` | Resource base URL, document description templates, clinic/sites admin, group ACL, fix-roles-on-notes (dry run), DB connection page, backup download (`servlet/BackupDownload` bytes, admin only, refused for others), logging levels, lot numbers, manage CSS |
| `provider-preferences` | Every Preferences tab: colour, signature (edit/upload/stamp), phone/fax/address/printer, default dx code, quick links, CPP preferences, tickler prefs, stale date, Rx warning level, appointment form links, workload view — each writes `property`/`provider` rows and restores |
| `provider-encounter-history` | `ViewProviderEncounterHistory` / single / print for the provider's seeded notes; bytes |

### B17. PMmodule / CAISI — P3

Read-only smoke of `PMmodule/ClientSearch2` (main-menu link), Program/Client/Staff/Facility
managers, gated on `caisi` being enabled in the dev profile; write checks only if the project
keeps CAISI (the cleanup policy in `CLAUDE.md` suggests confirming first).

### B18. Integration surfaces — P3

Workflow list (`oscarWorkflow/WorkFlowList`, main menu) CRUD; Scratch pad save (extend
`schedule-links`); swipe-card validation with a synthetic track; DHIR submit and OntarioMD
redirect as stubbed-transport UI checks only; live twins under `scripts/e2e/` if ever wanted.

### B19. Cross-cutting — P1/P3

| Check | Priority | Asserts |
|---|---|---|
| `direct-response-contract` (parameterised) | P1 | Every PDF/CSV/XLS/PNG/ZIP route a user can reach — eForm PDF, consult PDF, Rx print, labels, invoices, lab PDF, HRM print, chart print, eChart history print, flowsheet print, prevention print, report exports, backup download, envelope, measurement graph — answers the right `Content-Type`, magic bytes, `Content-Disposition`, and never an HTML error page inside a download (the `CARLOS Error: 0` class from PR #2043) |
| `phi-in-error-pages` | P1 | Provoke 400/403/404/405/500 across each route family; assert no HIN pattern, no `FAKE-` name, no `demographic_no` in the body; local-only extension greps `catalina.out` after the suite for the same patterns |
| `waf-clinical-text-corpus` (front-door tier) | P1 | Extends `clinical-freetext`: a fixture corpus of clinician sentences the CRS mis-scores, posted through every free-text argument the survey identified; each must save through nginx on `:443` |
| `responsive-viewport` | P3 | Key pages at 1366×768 and 1920×1080: primary actions inside the viewport, no horizontal scroll (obs. 16) |
| `accessibility-smoke` | P3 | axe-core on login, schedule, master record, chart, Rx, Inboxhub, billing form, admin index; report-only for one release, then fail on new serious violations |
| `i18n-locale` | P3 | Log in with the French locale (`LoginResourceAction`); schedule, chart, master record, Rx show no `???key???` and the bundle-linted keys resolve |

---

## 4. Part C — Sequencing

Effort is in engineer-weeks for someone who has written one check on this suite already.

| Phase | Scope | Exit criteria | Effort |
|---|---|---|---|
| 0 | A1 harness + `runCheck`, A2 manifest + runner, missing npm aliases, migrate 8 scripts as proof | Runner drives the existing suite in the devcontainer; `script-regressions.yml` tests the harness and the manifest; runbook §6 loop replaced | 2 |
| 1 | A3 fixtures, A4 smoke CI tier (non-blocking), A5 script changes, migrate remaining scripts | Smoke tier green on three consecutive PRs; #3313/#3317/#3598/#3600 closed | 3 |
| 2 | P1 checks: B1, B2, B3, B4, B6, B13, B19 (`direct-response-contract`, `phi-in-error-pages`) | Route coverage ≥ 45%; every daily clinical + revenue workflow has a DB-asserting check; nightly core tier on `develop` and `release/**` | 8–10 |
| 3 | P2 checks: B5, B7–B11, B15, B16 | Route coverage ≥ 70%; `admin-index-links`, `report-index-links`, `master-record-tabs`, `echart-navbar-modules`, `form-catalog-smoke` give every reachable page at least a render check | 6–8 |
| 4 | P3: B12, B14 (after the BC profile), B17, B18, B19 remainder; front-door CI | BC routes covered or explicitly listed as uncovered; a11y/i18n report-only in nightly | 4–6 |

Tracking: one umbrella epic "Playwright coverage — release/2026.08" with one sub-issue per
check in Part B and per row in A5, labelled `type: test`; each sub-issue carries the manifest
entry it must add. New checks land with their manifest entry, fixture files, an
`alpha-*-tester-coverage.md`-style row in a `coverage-map.md`, and a `.test.js` for any new
shared helper.

## 5. Deliberately not covered, and why

- **Live external systems** (SRFax send/receive, MCEDT, DHIR, CPSO registry, DrugRef rebuild
  against Health Canada): stay in `scripts/e2e/` or behind `*_LIVE=true`; UI checks stub the
  transport. They cost money, need credentials, or take an hour.
- **Routes with no UI entry** (`prevention/printPrevention`, several `View*` fragments only
  reachable as includes): per the suite's corollary, no check — the finding is that the route
  is dead, tracked for removal under the cleanup policy.
- **PHR / integrator / eConsult**: main-menu eConsult opens an external URL; nothing to assert
  locally.
- **Visual regression by screenshot diff**: the MCP manual tests keep gold screenshots; the
  scripted suite asserts DOM/DB/bytes instead. A pixel-diff tier is a possible Phase 4 add-on
  but is not what has caught defects on this project.
- **Load / soak** beyond `eform-corpus-soak`: out of scope for browser checks.

## 6. Appendix — measured hygiene numbers on this branch

| Property | Scripts |
|---|---:|
| require the shared harness | 50 / 75 |
| own `validateBaseUrl` copy | 31 |
| own `login()` copy | 33 |
| own `sql()` / defaults-file helper | 35 |
| assert the database | 32 |
| handle SIGINT/SIGTERM | 23 |
| `ignoreHTTPSErrors: true` unconditionally | 51 |
| honour `EXPECT_FRONT_DOOR` | 3 |
| write a machine-readable result | 4 |
| without an `npm run` alias | 8 |
| enter their target by hand-built URL where a UI entry exists (A5 list) | ~10 |
