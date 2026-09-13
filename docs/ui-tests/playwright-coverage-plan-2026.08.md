# Playwright coverage plan — release/2026.08

Status: **Phase 0 has landed; everything else is still a plan.** What exists in the
repository today is listed in §0; the rest of this document has not been implemented. It records what
browser checks exist on `release/2026.08` (at `7e322ee3`, 2026.08.0-alpha13-SNAPSHOT), what
they leave untouched, and — grouped by priority — which scripts to add and which to change to
get comprehensive, *meaningful* Playwright coverage of CARLOS. "Meaningful" has the definition
the suite already uses ([clinical-workflow-browser-checks.md](clinical-workflow-browser-checks.md)):
a check reaches the surface the way an operator does, asserts the rows that reached MariaDB
(or the bytes that reached the browser), proves a refusal against a matching acceptance,
cleans up on a per-run marker, and reports a defect rather than pinning it.

Related open issues this plan absorbs or depends on: #3313 (12 suites fail against a real
deployment), #3317 (`validateBaseUrl` duplicated in 19+ scripts), #3598 (`ignoreHTTPSErrors`
unconditional), #3600 (no signal handler in 11 scripts), #2859 (measurement-graph image bytes),
#3578 (Rx preview iframe race), #3377 / #3346 / #3275 / #3237 (already pinned by checks).

## How to read the paths in this document

Every check is described by the **clicks a user makes**, written as
`Schedule ▸ Administration ▸ User Management ▸ Add a Provider Record`, using the labels the
pages render in English. "Schedule" is the post-login day sheet and its top bar
(Search · Inbox · Tickler · Msg · Consultations · eConsult · eDoc · Report · Ref ·
WorkFlow · Administration · Dashboard · Help, and two icon-only controls on the right,
Scratch Pad and personal settings — there is **no** Billing control on the top bar; Ontario
billing is reached through Administration, and the per-appointment `B` badge is a workflow,
not a surface. `Ref` and `WorkFlow` are property-gated (`referral_menu`, `WORKFLOW`) and a
deployment may legitimately render neither); "Master Record" is the patient page opened
from a search result or the day sheet's M link; "Chart" is the E-Chart opened from the
day sheet's E link or the Master Record, and "Chart ▸ ‹module›" is an item in its left
navigation (Rx, Allergies, Consultations, Immunization, Preventions, Dx Registry, Forms,
eForms, Documents, Messenger, Flowsheet, Measurements, Ticklers, Calculators, Bill).
"Administration" is the panel the top bar opens, whose groups are User Management, Billing,
Labs/Inbox, Forms/eForms, Reports, eChart, CAISI (which, oddly, is where System Messages,
Facility Messages, Issue Editor, Default Encounter Issue and Lookup Field Editor live),
Schedule Management, Doctor Content Management, System Management, System Reports,
Integration, Status and Data Management. The `/administration` shell renders the same groups
plus Faxes (Configure Fax, Manage Faxes) and Emails (Configure Email, Manage Emails), and
puts Jobs Management / Job Type Management under System Management and Purge Audit Log under
Data Management. Paths below use the panel's grouping and say "(shell)" where an item exists
only there.

A URL appears in a path **only where the check must use one**: negative authorisation and
CSRF probes (the point is that a typed address is refused), and byte assertions on a download
that a click produced. A route with no UI entry gets no check (the suite's existing
corollary). The "Routes" column of each table is traceability for the manifest, not the path
the check takes.

## Why full user-facing paths: the JavaScript layer

CARLOS is not a server-rendered app with a thin front end. Measured on this branch:

| Mechanism a user's click depends on | Count |
|---|---:|
| `popupPage` / `popup` / `newWindow` openers in `WEB-INF/jsp` | 827 |
| Files with a `window.opener` refresh callback (the opener re-renders after a popup saves) | 155 |
| `confirm()` / `alert()` call sites | 1,383 |
| Inline `<script>` blocks in `WEB-INF/jsp` | 1,158 |
| `fetch` / `XMLHttpRequest` / `$.ajax` / `Ajax.Request` call sites | 171 |
| Administration pages injected into `#dynamic-content` by AJAX / hosted in the `#myFrame` iframe | 20 / 3 files |
| DataTables-rendered lists | 35 files |
| jQuery UI autocompletes (patient, provider, specialist, drug, dx) | 19 files |
| flatpickr date pickers | 35 files |
| Day-sheet keyboard shortcuts | 17 keys |
| CSRFGuard client script injected into pages; AJAX POSTs read its hidden token | every page |

So the thing that breaks for a user is almost never the Struts action alone: it is the
`onclick` that opens the popup, the opener callback that repaints the day sheet, the DataTables
init that renders the list, the autocomplete that fills the hidden id, the `confirm()` whose
result gates the delete, the AJAX fragment the admin shell injects, or the CSRF token an AJAX
POST reads from a page that never received it. The suite's own history says the same: the
`aSubmit is not defined` add-patient form, the `contextPath is not defined` Inbox, the
`parent.parent.resizeIframe` schedule wizard, the eForm restore GET, the notes-pagination
loop, the `#payee` `TypeError`, and the Select Forms panel that rendered white were all
JavaScript-layer failures on pages whose server actions were fine.

**The rule this plan applies to every check, existing and new:**

1. **Drive the browser's own event path.** Real `locator.click()` / `fill()` / `press()` /
   `selectOption()` / `setInputFiles()` on the element the user uses — scrolled into view,
   in the frame it lives in. No `page.evaluate(() => someHandler())`, no
   `form.evaluate(f => f.submit())`, no `context.request.post(...)` **as the path**. Those are
   permitted only for a *negative probe* (replay a captured POST without its token, with a
   forged patient, as GET) after the positive path has been driven through the UI.
2. **Enter through the opener, not the address.** If the UI opens a page as a popup, the
   check clicks the opener and takes the `popup` event; if the admin shell injects a panel,
   the check clicks the left-nav item and asserts inside `#dynamic-content` / `#myFrame`.
3. **Assert the JavaScript signals on every page, not only the DB row.** A `pageerror`, a
   `jQuery.Deferred exception`, a `Refused to execute script … MIME type` message, a failed
   subresource (`requestfailed`, or a script/CSS answering ≥ 400), or a `confirm()` nobody
   answered is a failure unless it is on the shared, issue-keyed console baseline.
4. **Assert the round trip a user sees.** After a popup saves, the opener has re-rendered the
   new row (the callback ran); after an AJAX save, the fragment shows the result; after a
   DataTables list loads, the row is in the table, not only in the response.

Measured against the existing 75 scripts: **26** assert `pageerror`, **0** assert
`requestfailed`, **3** wait for a popup window, **10** touch the opener contract, **3** press
a keyboard shortcut, and **31** navigate directly to a page the UI opens as a popup (§1.3).
`rx-med-history` calls `window.displayMedHistory(id)` and `drugref-update` calls
`pollStatus()` from `page.evaluate`; `prevention-brand-picker` probes the lot lookup with
`context.request.post('/cvc')` rather than the widget; `echart-note-sign-bill` triggers the
Save / Sign / Bill buttons through their handlers because the row sits below the viewport
(obs. 16). Each is listed for a change in §2.1.

---

## 0. What has landed so far

Phase 0 of §5 (the shared harness, the suite manifest and the runner) is in the
repository, and **22 checks implementing this plan have landed on it** — listed
in the second table below, which is the authoritative account of what exists.
Everything else in this document is still a plan.

| Landed | What it is | Verified by |
|---|---|---|
| `scripts/lib/playwright-harness.js` | The shared harness: `readConfig`, `createSqlRunner`, `login` (forced reset, facility select, and the MFA challenge **when the caller supplies an `mfaCode` callback** — the harness generates no OTP itself, and no check passes one today, so an MFA-enrolled account is refused with an assertion rather than logged in; `login-mfa` in §2.2 is what closes that), `wireStrictPage` / `assertStrictPage`, `runCheck`, the TLS gate, `SkipCheck` | `scripts/playwright-harness.test.js` (18 tests), run by `script-regressions.yml` |
| `scripts/lib/playwright-ui.js` | The JavaScript-path helpers (`clickOpensPopup`, `clickInjectsPanel`, `expectOpenerRefresh`, `typeAutocomplete`, `pickDate`, `dataTableRows`, `pressShortcut`, `csrfTokenPresent`, `expectDialog`) and the `NAVIGATION` click map | `scripts/playwright-suite-manifest.test.js` |
| `scripts/lib/console-baseline.json` | The suite-wide, issue-keyed allow-list that replaces per-check `allow` arrays | a test asserts every entry names where its removal is tracked |
| `scripts/playwright-suite.json` | The manifest: 99 named check entries over 90 scripts (the table-driven families — `surface-audit`, `direct-response-contract` — are one script backing several named checks, selected by `envSet`), each with tier, province, timeout, database use and env knobs | a test fails the build if a check has no entry, or an entry no script |
| `scripts/run-playwright-suite.js` | The runner: `--tier`, `--only`, `--skip`, `--province`, `--junit`, `--list`, `--dry-run` | `scripts/playwright-suite-manifest.test.js` |
| `package.json` | `test:playwright`, `test:playwright-smoke`, `test:playwright-list`, plus the 8 checks that had no alias at all | a test asserts every manifest entry is reachable by an alias |

**Checks implementing this plan, landed so far.** Each script's header names the
section it implements, and every *browser* check is UI-driven: it is entered by
clicking from the schedule, never by a URL. `csrf-bootstrap-audit` is the one
exception in the table below and is marked as such — it reads the webapp's JSPs
from disk and drives no browser at all, which is why it can run on every pull
request without a deployment.

| Check | Implements | Covers |
|---|---|---|
| `admin-index-links` | §3.7 | The Administration panel, ~120 items across its fourteen groups |
| `master-record-tabs` | §2.4 | The patient Master Record hub |
| `echart-navbar-modules` | §2.5 | The chart's 20 navigation modules, and that the navbars loaded at all |
| `surface-audit:report-index` | §3.6 | The Report index and everything on it |
| `surface-audit:inbox-surface` | §2.6 | The Inbox (the surface half of `inboxhub-filters`) |
| `surface-audit:consultations-surface` | §3.3 | The Consultations list |
| `surface-audit:messenger-surface` | §3.4 | Messenger |
| `surface-audit:tickler-surface` | §3.4 | Tickler |
| `surface-audit:edoc-surface` | §2.6 | eDoc document report |
| `surface-audit:referrals-surface` | §3.6 | Manage billing referrals (property-gated: skips where `referral_menu` is off) |
| `surface-audit:preferences-surface` | §3.7 | Provider preferences |
| `surface-audit:workflow-surface` | §4.4 | WorkFlow list |
| `surface-audit:scratch-surface` | §4.4 | Scratch pad |
| `demographic-edit-update` | §2.4 (also §2.4 `demographic-audit`) | Editing a patient from the Master Record, asserted against the database, restored — and asserted to have been **recorded** in the audit trail with an actor |
| `patient-search-modes` | §2.4 | Every patient-search mode, the active/inactive/all scope, and the browser-side date-of-birth refusal |
| `clinical-calculators` | §2.5 | The chart's osteoporotic-fracture and simple calculators — the numbers themselves, not just that the page rendered |
| `demographic-labels` | §2.4 | The Master Record's Print / Labels menu — the PDF *bytes* of every envelope and label, not just that the popup opened |
| `inboxhub-filters` | §2.6 | The Inbox's type and review-status filters, asserted as a *partition* of the unfiltered list — which is what catches a filter that is silently ignored |
| `mutator-get-rejection-live` | §2.2 | Every action the GET/HEAD rejection contract covers, driven through the **real** stack. Its route list is derived from `MutatorActionGetRejectionContractUnitTest`, so it cannot cover less than the unit contract does |
| `csrf-bootstrap-audit` (static) | §2.2 | CLAUDE.md's CSRF token-bootstrapping rule, enforced across all 1,031 JSPs. Not a browser check — it needs no deployment, so it runs on every pull request |
| `schedule-date-navigation` | §2.3 | The day sheet's month-boundary arithmetic (`day-1` on the 1st, `day+1` on the last), reached through the calendar popup — the two days a month where a clinician hits it and cannot reproduce it the next day |
| `anonymous-access-refused` | §2.2 | Everything a clinician reaches from the Administration panel and the Master Record, re-requested from a **session-less** context. Routes catalogued from the UI, not listed |

The first thirteen share one tested engine (`scripts/lib/playwright-link-audit.js`):
catalogue what the live page offers, click every item, and attribute each finding
to the page that broke. The last three are not audits: `demographic-edit-update`
and `patient-search-modes` assert what reached MariaDB, `clinical-calculators`
asserts the clinical numbers a page computes in the browser, and
`demographic-labels` asserts the bytes of a generated file — the first check in
the suite to look inside a download, which is where CLAUDE.md's direct-response
failures (an HTML error page inside a PDF, a truncated stream) actually live. The ten `surface-audit:*`
rows are a table in
`scripts/lib/playwright-surfaces.js` — a new surface is four lines, not a new
150-line script — and each is registered and reported individually.

**The smoke tier is at its budget.** Its twelve checks come to 3,600s of
worst-case timeout, which is the ceiling `playwright-suite-manifest.test.js`
enforces — a pull-request gate that can take longer than an hour is not a gate.
`anonymous-access-refused` is registered in `core` for that reason alone, not
because it earns less than the checks already there: it answers "can a stranger
read patient data", which is exactly what a gate is for. Adding the next security
check to `smoke` means taking one out, and `browser-surface` is the candidate —
§2.1 already records that it should be folded into `master-record-tabs` and
`admin-index-links`, both of which now exist.

**Application defects these turn up go in
[app-findings-log.md](app-findings-log.md)**, not into the checks. The suite's
rule is report, don't encode: a check that pins current broken behaviour as
expected makes the bug permanent.

`scripts/eform-local-playwright-utils.js` is now a re-export of the harness plus
the eForm-specific helpers, so **no existing check changed behaviour**: `wirePage`
deliberately records exactly what it recorded before (no `requestfailed`, no
script-MIME finding, no unexpected-dialog finding, no console baseline). Only
`wireStrictPage` applies the strict contract, so migrating a check is a reviewed
change to that check rather than 75 checks gaining new failure modes at once.

**The CI smoke tier needs a maintainer.** `.claude/settings.json` denies Claude
`Write(.github/**)` and `Write(.github/workflows/**)`, so the `playwright-smoke.yml`
workflow in §2.1 cannot be added by an agent — the YAML has to be committed by a
human. Everything else in Phase 1 is unaffected.

**What is not yet verified.** The checks above, the harness and the runner are
unit-tested but have not
been run against a deployment: no Tomcat or MariaDB was available in the session
that wrote them. Before anything migrates onto them, one pass of the existing
suite through `node scripts/run-playwright-suite.js` against the devcontainer is
needed, and the `NAVIGATION` selectors (read out of
`appointmentprovideradminday.jsp`, each carrying `validated: false`) have to be
confirmed by a live run. Until that happens
[deb-install-validation.md §6](deb-install-validation.md#6-run-the-suite) remains
the authoritative way to run the suite.

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

For every `<action name>` in `struts-*.xml` (1,064 after dropping short un-namespaced names),
does any check script reference it? (`grep -F` over all scripts; "touched" over-counts slightly
where a name is a substring of another.)

| Module | Routes | Touched | Coverage | Largest untouched areas |
|---|---:|---:|---:|---|
| admin | 68 | 11 | 16% | lookup lists, system/facility messages, issue editor, security log, jobs, REST clients, key pairs, email, security-record edit/delete, provider-record edit |
| billing | 185 | 7 | 4% | **entire BC module (0)**; ON correction/delete/status, invoices, OHIP file generation, RA import/settle, report centre, billing admin, MCEDT |
| clinical | 168 | 14 | 8% | Dx registry, patient flowsheet, measurement history/graphs/admin, immunization schedule, decision support, calculators, antenatal/annual planners, note browser, CPP sections other than Social Hx |
| demographic | 46 | 4 | 9% | edit/update, merge, contacts/relations, labels/envelope PDFs, export/import, patient sets/cohorts, audit, health-care team |
| document | 47 | 9 | 19% | forward/MRP/reassign/file, document edit/refile/delete/split/combine, HRM display/modify, incoming docs, patient match |
| eform | 53 | 25 | 47% | groups, patient-independent eForms, image manager, visual editor, generator, save-as-eDoc, field-note report |
| encounter | 90 | 14 | 16% | every chart navbar module except Rx/Allergies/Measurements, consultation config CRUD, measurement admin, immunization config |
| form | 22 | 5 | 23% | dashboard display/drilldown/export/bulk action, form XML upload, RH workflow |
| integration | 30 | 4 | 13% | MCEDT, DHIR, workflow list, swipe validation |
| lab | 29 | 6 | 21% | HL7 upload paths, forwarding rules, cumulative values, BC lab pages, lab label PDF |
| login | 14 | 6 | 43% | **MFA**, facility select, heartbeat, `securityError` |
| messenger | 23 | 6 | 26% | attachments, PDF preview, transfer to chart, patient-linked message list |
| pmmodule | 13 | 0 | 0% | provider signature/phone/address/printer/fax edit, CAISI program/client/staff managers |
| prescription | 69 | 17 | 25% | favourites, stash, discontinue/delete, pharmacy manage, interactions/renal, print profile |
| provider | 51 | 8 | 16% | Preferences (all sections), change password, fax/email queues, encounter history, find-provider |
| report | 84 | 6 | 7% | report by template, query by example, day sheet, CDS/MIS/provider-service, letters/envelopes/spreadsheets, CDM, clinical export, patient list |
| scheduling | 72 | 14 | 19% | appointment repeat/group/cut/copy/print, waiting list, holidays, template codes, appointment status/type settings, groups |
| **Total** | **1,064** | **156** | **~15%** | |

Route count is a proxy: a route can be "touched" by a hand-built URL the user never types, and
a real workflow spans several routes. Sections 2–4 are organised by workflow, not route.

### 1.2 What the existing checks already do well

The alpha-11 and clinical-workflow passes set the bar and this plan keeps it. Already pinned
(see [alpha-11-tester-coverage.md](alpha-11-tester-coverage.md) for the full map): login /
forced reset / CSRF rejection / lockout avoidance; logout broadcast, session invalidation,
multiple sessions; provider + login-record creation; role assignment; demographic add and
CRUD smoke; DOB search; quick-search booking; day-sheet booking; appointment edit/status/
cancel/delete; schedule templates and settings; chart first render + Social Hx; note save/
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

### 1.3 Existing checks that enter by URL where a click exists

The clinical-workflow doc's rule is "reach it the way a user reaches it". These scripts type
an address for a page the user reaches by clicking; each is scheduled for a path change in the
priority group where its module lives:

| Script | Types | The user clicks |
|---|---|---|
| `lab-macro-tickler` | `lab/CA/ALL/ViewLabDisplay?…`, `setProviderStaleDate?method=viewLabMacroPrefs` | Schedule ▸ Inbox ▸ row; Schedule ▸ Preferences ▸ Lab Recall Macros |
| `echart-vitals-bmi` | `oscarMeasurements/SetupMeasurements?groupName=…` | Chart ▸ Measurements ▸ group |
| `prevention-brand-picker` | `prevention/ViewPreventionIndex`, `ViewAddPreventionData` | Chart ▸ Preventions ▸ Add |
| `form-rourke2017` | `form/forwardname?form_link=…` | Chart ▸ Forms ▸ Rourke 2017 |
| `billing-service-code-admin` | `billing/CA/ON/AddEditServiceCode` | Schedule ▸ Administration ▸ Billing ▸ Manage Billing Service Code |
| `schedule-template-crud` | `schedule/TemplateSetting` | Schedule ▸ Administration ▸ Schedule Management ▸ Schedule Setting ▸ Template |
| `specialist-add-cpso` | `…/config/ViewAddSpecialist`, `ShowAllServices` | Schedule ▸ Consultations ▸ configuration icon ▸ Add Specialist |
| `fax-configure` | `admin/ViewConfigureFax` | Schedule ▸ Administration ▸ Faxes ▸ Configure Fax (shell; the panel lists Status ▸ Fax Status) |
| `rx-*` (five scripts) | `rx/choosePatient?demographicNo=…` | Chart ▸ Rx (or Master Record ▸ Prescriptions) |
| `messenger`, `messenger-inbox-actions` | `messenger/DisplayMessages` | Schedule ▸ Msg |
| `tickler-crud`, `tickler-note-dialog` | `tickler/ViewAddTickler`, `ViewTicklerMain` | Schedule ▸ Tickler ▸ Add Tickler |
| `add-login-account`, `assign-role` | `admin/View…AddARecord`, `admin/ProviderRole` | Schedule ▸ Administration ▸ User Management ▸ … |
| `allergy-add-penicillin`, `allergy-rx-alert` | `rx/showAllergy`, `encounter/IncomingEncounter` | Chart ▸ Allergies |
| `eform-*` (six scripts) | `eform/efmformmanager`, `efmformadd_data`, `efmshowform_data` | Schedule ▸ Administration ▸ Forms/eForms ▸ Manage eForms; Chart ▸ eForms |
| `document-upload` | `web/inboxhub/Inboxhub` | Schedule ▸ Inbox ▸ Doc Upload |
| `consultation-*` (four) | `encounter/ViewRequest?requestId=…` | Schedule ▸ Consultations ▸ row |

Once the shared `navigate.*` map exists (§2.1) each of these becomes a one-line change.

---

## 2. Priority 1 — daily clinical, revenue and security workflows

### 2.1 Changes to the existing suite (prerequisites for everything below)

**One shared harness.** `scripts/eform-local-playwright-utils.js` is the de-facto harness
(50 of 75 scripts require it) but 33 scripts still carry their own `login()`, 31 their own
`validateBaseUrl()` (#3317), 35 their own `sql()`/defaults-file helper, and only 23 handle
SIGINT/SIGTERM (#3600). 51 pass `ignoreHTTPSErrors: true` unconditionally (#3598). Plan:

- Move the harness to `scripts/lib/playwright-harness.js` (keep the old file as a re-export
  shim for one release) and add:
  - `readConfig()` — one env contract (`BASE_URL`, `CHROME_PATH`, `TEST_USER/PASSWORD/PIN`,
    `MYSQL_*`, `EXPECT_FRONT_DOOR`, `ALLOW_NON_LOCAL_BASE_URL`) with **documented defaults per
    check** (#3313: "required env is only discoverable by reading the throw").
  - `sql()` / `sqlRows()` over a 0600 defaults file, with the `mysql -B` unescape the clinical
    doc warns about, and `withMysqlDefaults(fn)` so the file is always removed.
  - `login()` that understands forced reset, the MFA challenge, facility select and the
    `select_facility` result — today each copy handles a different subset.
  - `ignoreHTTPSErrors` gated on a loopback/private host (#3598).
  - `runCheck({ name, steps, cleanup })` — installs the `graceful-signal-cancellation`
    handler, standardises `PASS/FAIL/SKIP/WARN <check> <step>` lines and exit codes (0 pass,
    1 fail, 2 skipped-for-missing-fixture), optional `RESULT_JSON`/JUnit output.
  - **`navigate.*` — the click map.** One helper per UI section, each implemented as the
    clicks a user makes from the schedule: `navigate.schedule()`, `.search()`, `.tickler()`,
    `.consultations()`, `.msg()`, `.inbox()`, `.report()`, `.billing()`, `.administration(group,
    item)`, `.preferences(section)`, `.edoc()`, `.masterRecord(demographicNo)` (via Search →
    result), `.chart(demographicNo)` (via Master Record → E-Chart, or day sheet → E),
    `.chartModule(name)` (left-nav item and its "+"), `.rx(demographicNo)` (Chart → Rx). Every
    new check enters through it; a moved link fails one helper, not twenty asserts. It is also
    what lets §1.3 be fixed mechanically.
  - `consoleBaseline` — one shared allow-list (`scripts/lib/console-baseline.json`) of known
    legacy console errors keyed to issue numbers (the `providerSignatureImage` 404, the Rourke
    favicon 404, the `getActiveText()` TypeError). New checks fail on *new* console errors.
  - **`wireStrictPage(page)` — the JavaScript-signal contract.** Extends today's `wirePage`
    so that, by default, a `pageerror`, a `jQuery.Deferred exception`, a `Refused to execute
    script` MIME error, a `requestfailed`, or a script/stylesheet/XHR answering ≥ 400 fails the
    check unless matched by the console baseline; one `dialog` listener per page with an
    explicit expectation (`expectDialog('confirm', accept)`) so an unanswered `confirm()`
    is a failure, not a silent dismiss.
  - **`ui.*` — the JavaScript-path helpers**, so every check drives the same mechanisms the
    way a user does: `ui.clickOpensPopup(locator)` (click + `popup` event + `load` +
    `wireStrictPage` on the popup), `ui.clickInjectsPanel(navItem, '#dynamic-content')`
    (click + wait for the fragment's marker element), `ui.inFrame('#myFrame')`,
    `ui.expectOpenerRefresh(page, popup, rowLocator)` (close the popup, assert the opener
    re-rendered the row without a manual reload), `ui.typeAutocomplete(input, text, option)`
    (type, wait for the suggestion list, pick, assert the hidden id filled),
    `ui.pickDate(input, isoDate)` (through flatpickr, not `fill`), `ui.dataTableRows(table)`
    (wait for DataTables init + draw), `ui.pressShortcut(page, key)`, `ui.csrfTokenPresent(page)`
    (the hidden `CSRF-TOKEN` input is populated before an AJAX POST is attempted).
- Unit-test the harness in `scripts/playwright-harness.test.js` (CI already runs `*.test.js`).
- Migrate the 25 scripts that do not require the harness; delete local copies in slices of
  ~8 scripts per PR.

**A suite manifest and runner.** Replace the runbook's bash `for` loop with:

- `scripts/playwright-suite.json`: one entry per check — `tier` (`smoke` | `core` |
  `extended` | `front-door` | `live-external`), `mutates` (tables), `fixtures`, `env`
  (required vs optional with defaults), `timeoutSec`, `runLast`, `provinces`.
- `scripts/run-playwright-suite.js`: `--tier`, `--only`, `--skip`, `--province`, `--junit`,
  `--screenshots`; refuses a `mutates` check against a non-local host without
  `ALLOW_NON_LOCAL_BASE_URL`; records the build tag before and after so a green suite cannot
  span a JVM restart; prints a summary table.
- `package.json`: `test:playwright` → runner; add the 8 missing aliases
  (`application-health`, `consultation-nullable-fields`, `demographic-add`, `drugref-update`,
  `patient-list-by-appointment-export`, `patient-search-dob`, `rx-preview-pharmacy`,
  `demographic-master-crud-smoke`).
- The runbook §6 loop becomes `node scripts/run-playwright-suite.js --tier core --tier
  front-door --junit …`; per-check knobs move into the manifest and a `.test.js` renders
  `docs/ui-tests/playwright-checks-reference.md` from it, failing when a check has no entry.

**Fixtures as files.** `scripts/fixtures/sql/<check>.seed.sql` / `.teardown.sql` with a
`PW_<CHECK>_<RUN>` marker convention; `local-fixture-cleanup.js --sweep-stale`;
`scripts/fixtures/files/`: synthetic HL7 ORU (one accession, two versions — the `showLatest`
chain), synthetic OHIP RA + error report, synthetic HRM XML+PDF, a 2-page PDF and a JPG, an
eForm `.zip`, a schedule-of-benefits CSV, a demographic import CSV — all `FAKE-`, reviewed
under the same no-PHI rule as `demo-specialists.sql`. A `demo-dataset-contract.test.js`
asserts the dataset facts checks rely on (which demographics have clean charts, seeded
appointment dates, active consultation services after V1.0.23) so a dataset change fails in CI
before it fails a VM run.

**A CI smoke tier.** *(A maintainer has to commit this file: `.claude/settings.json`
denies Claude write access to `.github/**`.)* `playwright-smoke.yml` on PRs touching `src/main/webapp/**`, the
filters, `**/web/**`, `scripts/**`, `struts-*.xml`: `carlos-tomcat-dev` + MariaDB service,
`populate_db.sh`, `make install`, `run-playwright-suite.js --tier smoke` (≤ 12 min: login,
schedule links, application health, browser surfaces, chart first render, tickler CRUD,
appointment lifecycle, document upload, drug search, eForm render, error sanitization).
Screenshots + JUnit as artifacts. Non-required for two weeks of green, then required. Nightly
`--tier core` on `develop` and `release/**`. The `front-door` tier stays in the deb runbook
until the package can be installed with nginx + ModSecurity in CI.

**Targeted changes to existing P1-module scripts**

| Script | Change | Why |
|---|---|---|
| `logout-redirect`, `application-health`, `logout-session-invalidation` | Decide one post-logout target (`/logoutPage` vs login) with maintainers and assert it in all three | #3313 items 2 and 8 — three checks disagree with the app and each other |
| `login` | Add the MFA-challenge and facility-select branches (see 2.2); keep the lockout probes isolated | Two uncovered login branches |
| `lab-macro-tickler` | Enter the lab through Schedule ▸ Inbox ▸ row (as `lab-acknowledge` does) and the macro prefs through Schedule ▸ Preferences; assert the future-dated tickler shows in Master Record ▸ Tickler once obs. 12 is fixed | §1.3 |
| `echart-vitals-bmi`, `measurement-validation` | One shared measurement-popup helper entered via Chart ▸ Measurements | Two entry paths to one popup |
| `echart-note-sign-bill` | `WARN` that Save / Sign & Save / Bill are inside the viewport at 1366×768 and 1920×1080; promote when obs. 16 is fixed | Handlers are clicked today, hiding a real layout defect |
| `billing-on-submit` | Assert no `TypeError` from `onSave()` (obs. 2) once `#payee` is guarded; assert the billing physician defaults to the appointment provider (obs. 4) when fixed | Report, don't encode |
| `demographic-master-crud-smoke` | Rename to `-playwright-checks.js`, adopt the harness, add DB asserts (page-only today) | A save that wrote nothing passes a page-only check |
| `echart-note-sign-bill` | Click the real Save / Sign & Save / Bill buttons (`scrollIntoViewIfNeeded` + `click`); keep the handler invocation only as the documented fallback that reports `WARN` until obs. 16 is fixed, then remove it | The button row is the JS path a clinician uses |
| `rx-med-history` | Replace `page.evaluate(() => window.displayMedHistory(id))` with the click on the medication-history control | Handler invocation skips the opener/onclick wiring |
| `drugref-update` | Replace `page.evaluate(() => pollStatus())` with waiting on the page's own status poll (`waitForResponse` on the relay + the rendered status) | The check must prove the page polls by itself |
| `prevention-brand-picker` | Drive the lot-number lookup through the widget in the Add Prevention popup; keep the `context.request.post('/cvc')` probe as a secondary assertion | The widget is what a nurse uses; obs. 20 says its endpoint may be unmapped |
| the 31 scripts in §1.3 that `goto` a popup page | Click the opener via `navigate.*` / `ui.clickOpensPopup`; assert the opener refresh where the popup saves (appointments, ticklers, preventions, Rx, eForms, consultations) | 827 popup openers and 155 opener callbacks are otherwise untested |
| the 49 scripts without `pageerror` wiring | `wireStrictPage` via `runCheck()` | A page whose script block fails to parse currently passes if its form still posts |
| `browser-surface` | Fold into `master-record-tabs` (2.4) and `admin-index-links` (3.7) | Overlaps |
| `schedule-links` | Thin wrapper over `navigate.*` asserting every top-bar item, not six | The click map is the contract |
| `document-upload`, `tickler-*`, `messenger*`, `rx-*`, `allergy-*` | Enter via `navigate.*` | §1.3 |
| all 51 with `ignoreHTTPSErrors: true`; all 52 without a signal handler | Use the harness gate / `runCheck()` | #3598, #3600 |

### 2.2 Authentication, session, authorisation

| Check | Path | Asserts | Fixtures / cleanup | Routes |
|---|---|---|---|---|
| `login-mfa` | Schedule ▸ Administration ▸ User Management ▸ Search/Edit/Delete Security Records ▸ open the throwaway record ▸ MFA section ▸ enable → log out → log in → the MFA challenge page | Wrong code refused with no session; right code lands on the schedule; enrolment row written; disabling MFA on the record restores plain login | Throwaway login record created through Add a Login Record; deleted after | `securityRecord/mfa`, `mfa/loginMfa` |
| `login-facility-select` | Log in as a provider assigned to two facilities | Facility chooser renders; the choice persists; charts respect it | Second facility row; restore | `select_facility` |
| `session-heartbeat-timeout` | Schedule open, idle past the session timeout (shortened on a dev profile) → click Tickler | Lands on login, no chart HTML leaks, the chart's "session timed out" popup renders when the chart was open | none | `status/SessionHeartbeat`, `encounter/ViewTimeOut` |
| `password-change-preferences` | Schedule ▸ Preferences ▸ Change Password | Weak password refused server-side; good one accepted; old password fails to log in; hash changed | Restore hash as `login` does | `provider/ViewProviderChangePassword`, `ViewProviderUpdatePassword` |
| `account-lockout-unlock` (runLast) | Three bad logins on a throwaway record → Schedule ▸ Administration ▸ User Management ▸ Unlock Account | Locked row; login refused with the lockout message; unlock clears it; login succeeds | Throwaway record | `admin/UnLock` |
| `security-record-admin` | Administration ▸ User Management ▸ Search/Edit/Delete Security Records ▸ edit ▸ delete | `security` row updated then removed; a deleted login cannot log in | Throwaway record | `admin/SecurityUpdate`, `SecurityDelete` |
| `provider-record-admin` | Administration ▸ User Management ▸ Search/Edit Provider Records ▸ Update; System Management ▸ Assign Role/Rights to Object; provider template | `provider` fields (OHIP no, billing no, status) updated; an inactive provider disappears from the day sheet's provider list | Throwaway provider | `admin/ProviderUpdate`, `ProviderPrivilege`, `ProviderTemplate` |
| `role-privilege-matrix` | Administration ▸ System Management ▸ Add A Role (no `_rx`, `_billing`, `_admin`) ▸ User Management ▸ Assign Role to Provider; log in as that provider | Chart shows no Rx/Bill links and the top bar no Billing/Administration; **typed URLs** to `rx/choosePatient`, `billing/CA/ON/*`, `admin/ViewAdmin` answer the sanitised security page, not content; audit rows recorded | Throwaway role + provider — the one place a typed address is the point | `SecurityInfoManager` gates |
| `csrf-negative-matrix` | For each mutator family (tickler, appointment, note, Rx, allergy, billing, provider admin, eForm): perform the action through the UI, then replay the captured POST **without** its token | Replay → 403 and **no row**; the UI action → row | Reuses each family's seed; parameterised from the manifest | `CarlosCsrfGuardFilter` |
| `mutator-get-rejection-live` | Same families, replay as GET | 405 / security page and no side effect — the live twin of `MutatorActionGetRejectionContractUnitTest` | none | contract manifest |

### 2.3 Schedule and appointments

| Check | Path | Asserts | Fixtures | Routes |
|---|---|---|---|---|
| `appointment-form-fields` | Schedule ▸ far-future day ▸ click a slot ▸ Add Appointment | Every field round-trips (reason, reason code, notes, type, duration, status, location, resources, urgent); the edit popup shows them; `appointment` row | `APPOINTMENT_DAYS_AHEAD` | `appointment/addappointment`, `AddRecord`, `editappointment` |
| `appointment-repeat-group-copy` | Add Appointment ▸ Repeat; ▸ Group; day sheet ▸ appointment ▸ Cut / Paste, Copy; ▸ Print | N rows for a repeat; group rows share the group id; cut moves, copy duplicates; print answers a printable page | Same | `appointmentrepeatbooking`, `appointmentgrouprecords`, `CutRecord`, `appointmentcopyrecord`, `printappointment` |
| `schedule-views` | Top bar: Day / Week / Month / Flip / Zoom / Search views; All (providers); multiplier; jump-to-date calendar; Find a Provider | Each view renders the seeded appointment; the week-view deep link hides E/B links only on week view (obs. 13 fixed behaviour) | Seeded appointment | `provider/providercontrol` modes, `ViewReceptionistFindProvider`, `schedule/FlipView` |
| `schedule-admin-settings` | Administration ▸ Schedule Management ▸ Schedule Setting (Holiday, Template Code), Appointment Status Setting, Appointment Type List; Preferences ▸ General ▸ Provider Colour | Rows in `scheduleholiday`, `scheduletemplatecode`, `appointment_status`, `appointmentType`; the new status appears in the edit popup | Restore | `schedule/HolidaySetting`, `TemplateCodeSetting`, `appointment/apptStatusSetting`, `appointmentTypeAction`, `setProviderColour` |
| `waiting-list` | Master Record ▸ Waiting List (add / remove; list names are edited from the same page) | Add / remove / rename; `waitingList`, `waitingListName` rows | Throwaway list | `waitinglist/*` |
| `appointment-search-history` | Schedule ▸ provider column header ▸ Search view icon; Master Record ▸ Appt. History | The seeded appointment is found by date/provider/patient; history lists it | Seeded appointment | `appointment/appointmentsearch`, `demographic/DemographicApptHistory` |
| `my-groups` | Administration ▸ Schedule Management ▸ Add a Group ▸ add providers; day sheet ▸ Group view | Group created; the day sheet's group view shows its providers | Throwaway group | `admin/AdminNewGroup`, `AdminSaveMyGroup`, `provider/SaveMyGroup` |

### 2.4 Demographics and the master record

| Check | Path | Asserts | Fixtures | Routes |
|---|---|---|---|---|
| `demographic-edit-update` | Schedule ▸ Search ▸ result ▸ Master Record ▸ Edit | Address/phone/email/HIN+version/status/roster/MRP/language round-trip; a bad HIN check digit is refused, a good one accepted; `demographic` + `demographicExt` rows; roster change writes enrolment history | Throwaway demographic | `demographic/DemographicEdit`, `DemographicUpdate`, `validateHC` |
| `master-record-tabs` | Every link on the Master Record: Appt. History, Waiting List, Billing History, Invoice List, Add Bill, Consultations, Prescriptions, E-Chart, Prevention, Tickler, AR1/AR2, Documents, eForms, Manage Contacts, Add Relation, Enrollment History, print/label menu | Each opens without an error page or a new console error; replaces the master-record half of `browser-surface` | Demographic 2 | see §1 |
| `demographic-contacts` | Master Record ▸ Add Relation; ▸ Manage Contacts (Other Contacts, professional contacts); the health-care team the Master Record displays is managed from Chart ▸ Forms ▸ BPMH | Add/edit/delete a relation, an alternate contact, a professional contact; SDM flags; `relationships`, `demographiccontact` rows | Throwaway rows | `demographic/AddRelation`, `DeleteRelation`, `Contact`, `ViewProContact`, `ViewManageHealthCareTeam` |
| `demographic-merge` | Administration ▸ Data Management ▸ Merge Patient Records | Two throwaway patients; after merge the child is flagged, the parent's chart lists the child's notes, search finds one active record; `demographic_merged` rows | Throwaway pair | `admin/DemographicMergeRecord`, `MergeRecords` |
| `demographic-labels` | Master Record ▸ print/label menu ▸ Label, Address Label, Chart Label, Envelope; Inbox ▸ lab ▸ print lab label | bytes: `%PDF`, `application/pdf`, non-empty | none | `demographic/print*Label*`, `ViewPrintEnvelope`, `lab/CA/ALL/createLabLabel` |
| `demographic-export-import` | Administration ▸ Data Management ▸ Demographic Export; ▸ Import New Demographic | Export produces the CDS XML for the patient (bytes); importing the fixture creates `FAKE-` patients; the import log downloads | Fixture files; delete created rows | `demographic/DemographicExport`, `form/importUpload`, `importLogDownload` |
| `patient-set-cohort` | Master Record ▸ add to patient set; Schedule ▸ Report ▸ Demographic Report Tool ▸ sets | Set created, patient added, set drives a report row (the `prevention-recall-report` pattern) | Throwaway set | `demographic/ViewAddDemoToPatientSet`, `report/CreateDemographicSet`, `DemographicSetEdit` |
| `demographic-audit` | Master Record ▸ Edit ▸ clinical section ▸ audit link | The edit `demographic-edit-update` made appears with user and time | Reuse | `demographic/ViewDemographicAudit` |
| `patient-search-modes` | Schedule ▸ Search: name, HIN, phone, chart no, DOB (existing), address, all; Active/inactive; swipe | Result set per mode; inactive excluded by default and included on request; nothing beyond the search term in the URL | Demo data | `demographic/ViewSearch`, `DemographicSearch`, `ViewZdemographicSwipe` |

### 2.5 Chart / encounter

| Check | Path | Asserts | Fixtures | Routes |
|---|---|---|---|---|
| `echart-cpp-sections` | Chart ▸ each CPP box "+" (Family Hx, Medical Hx, Ongoing Concerns, Reminders, Risk Factors, Other Meds; Social Hx exists) | Add, edit, archive; `casemgmt_note` rows with the right issue code; archived notes leave the box | Demographic 2 | `CaseManagementEntry`, `casemgmt/*` |
| `echart-note-lifecycle` | Chart ▸ existing note | Edit creates a revision (uuid chain); a signed note is read-only; annotate; assign issues via the issue search; a draft survives reopen (`casemgmt_tmpsave`); a second browser opening the same note gets the lock warning; stale-lock recovery (obs. 18) | Demographic 2; clear locks | `casemgmt/ViewIssueSearch`, `ViewConcurrencyError`, `CaseManagementEntry` |
| `echart-dx-registry` | Chart ▸ Dx Registry | Add an ICD-9 code by search and by quick list, resolve, reactivate; `dxresearch` rows; the diagnosis makes the matching flowsheet item appear in the left nav | Demographic 3 | `oscarResearch/dxresearch/*` |
| `clinical-flowsheet` | Chart ▸ Flowsheet (visible after `echart-dx-registry`) ▸ Add links | Renders; Add saves; print answers a page; the measurement-entry 500 without a patient is asserted fixed | Needs the Dx fixture — the clinical doc's "recommended next addition" | `ViewTemplateFlowSheet`, `ViewAddMeasurementData`, `ViewTemplateFlowSheetPrint` |
| `echart-measurements-history-graph` | Chart ▸ Measurements ▸ History / Graph / Export | History lists rows; the graph is a real PNG (#2859); export bytes | Seeded measurements | `SetupHistoryIndex`, `GraphMeasurements`, `ScatterPlotChartServlet`, `ViewExportMeasurement` |
| `echart-navbar-modules` | Every Chart left-nav module and its "+": Consultations, Documents, eForms, Forms, HRM, Labs, Messenger, Pregnancy, Episode, Contacts, Decision Support, Ticklers, Preventions, Issues/Resolved | Renders with rows for demographic 2, no error page, "+" opens the right popup; read-only | Demographic 2 | `encounter/display*` |
| `encounter-templates` | Administration ▸ eChart ▸ Insert a Template (create); Chart ▸ note ▸ Templates | Template text lands in the note; `encounterTemplate` CRUD | Throwaway template | `encounter/InsertTemplate` |
| `decision-support-alerts` | Chart of a patient matching a seeded rule ▸ Decision Support | Alert renders; the module's guideline list opens; dismissal persists | Seeded rule | `encounter/decisionSupport/*` |
| `episode-pregnancy` | Chart ▸ Episode; Chart ▸ Pregnancy (antenatal planner, OBAR risk / checklist) | Episode CRUD; a pregnancy episode creates planner rows; planner print bytes | Throwaway rows | `Episode`, `decision/antenatal/*`, `provider/ViewObar*` |
| `immunization-schedule` | Chart ▸ Immunization | Schedule renders for the patient's age; saving a schedule round-trips (the immunization *set* configuration pages have no UI entry — see §6) | Restore | `encounter/immunization/*Schedule*` |
| `calculators` | Chart ▸ Calculators | CAD risk / osteoporotic fracture / simple calculator compute deterministic results from typed inputs | none | `encounter/ViewCalculators`, `calculators/*` |

### 2.6 Labs, Inbox, documents, HRM

| Check | Path | Asserts | Fixtures | Routes |
|---|---|---|---|---|
| `inboxhub-filters` | Schedule ▸ Inbox: Unmatched/Matched × Documents/HL7/HRM counts, provider, status, patient, date, Search/Reset, pagination, list vs preview mode | Rows match SQL for the same filter; the `contextPath is not defined` pageerror (#3313 #1) stays fixed | Demo data | `web/inboxhub/Inboxhub`, `LabDataController` |
| `lab-upload-hl7` | Inbox ▸ HL7 Lab Upload (and Administration ▸ Labs/Inbox ▸ Lab Upload) | The synthetic HL7 imports; appears under Unmatched ▸ HL7; Patient Match links it; Unlink removes it; `hl7TextInfo`, `patientLabRouting`, `providerLabRouting` rows | `fixtures/files/*.hl7` | `lab/labUpload`, `newLabUpload`, `CMLlabUpload`, `oscarMDS/PatientMatch`, `SearchPatient`, `UnlinkDemographic` |
| `lab-forward-mrp-file` | Inbox ▸ row ▸ Forward; Send to MRP; Reassign; File; Inbox ▸ Forwarding Rules | `providerLabRouting` rows per action; the forwarded lab appears in the other provider's inbox; a rule created here is honoured by the next upload | Second provider | `oscarMDS/Forward`, `SendMRP`, `ReportReassign`, `FileLabs`, `ForwardingRules` |
| `lab-cumulative-requisition` | Inbox ▸ lab ▸ cumulative values; Ontario lab values graph; link requisition | Cumulative columns match versions; graph bytes | Chain fixture | `lab/ViewCumulativeLabValues*`, `lab/CA/ON/ViewLabValuesGraph`, `ViewLinkReq` |
| `document-manage` | Inbox ▸ document ▸ edit description/type/observation date; refile; delete; undelete; split; combine; add link; Schedule ▸ eDoc ▸ document list ▸ combine / undelete; Inbox ▸ Incoming Docs / Pending Docs; Administration ▸ Doctor Content Management ▸ Document Description Template | `document` rows and `ctl_document` links; split → two rows and two files; combine → one; deleted hidden then restored | Fixture PDFs; files removed via `EDOC_NAV_DOCUMENT_STORE` | `documentManager/*` |
| `hrm-report-lifecycle` | Inbox ▸ HRM ▸ open ▸ sign off / comment / category ▸ Print; Administration ▸ Integration ▸ Hospital Report Manager (HRM) Status | `HRMDocument*` rows; print bytes; **a missing HRM file does not 500 the chart notes** (obs. 17) once fixed | Synthetic HRM XML/PDF | `hospitalReportManager/*` |
| `fax-queue-admin` | Administration ▸ Faxes ▸ Manage Faxes (shell; the panel: Status ▸ Fax Status); Preferences ▸ Contact Information ▸ fax number | Seeded outbound rows render with status; resend/cancel updates `faxes`; **no live send** | Seeded `faxes` rows | `admin/ViewManageFaxes`, `fax/faxAction` |

### 2.7 Ontario billing (4% covered; the revenue loop is unprotected)

| Check | Path | Asserts | Fixtures | Routes |
|---|---|---|---|---|
| `billing-on-correction-delete` | Master Record ▸ Billing History ▸ bill; Administration ▸ Billing ▸ Billing Correction | Edit service code/dx/units → `billing_on_item` updated; status change; delete variants mark `billing_on_cheader1.status='D'` and unbill the appointment (day sheet B badge) | Bill created through the `billing-on-submit` path | `BillingONCorrection`, `UpdateBillingONCorrection`, `ViewBillingONStatus`, `BillingDelete*` |
| `billing-on-invoice-3rdparty` | Master Record ▸ Invoice List ▸ print; Administration ▸ Billing ▸ Billing Correction ▸ 3rd-party bill ▸ payments; Administration ▸ Billing ▸ Manage Payment Type | bytes `%PDF` with the invoice logo; `billing_on_payment` rows; statement balances | Third-party bill | `BillingInvoice*`, `ViewBillingON3rdInv`, `billingON3rdPayments`, `Add3rdPartyPayment`, `managePaymentType` |
| `billing-on-ohip-file-cycle` | Administration ▸ Billing ▸ Simulation OHIP File; Generate OHIP File; Billing Reconciliation ▸ pick the RA file ▸ summary / detail ▸ settle; Upload MOH files (fixture error report); View MOH files | Claim file bytes match the MOH fixed-width layout for the seeded bills; bills flip to `B`; the RA creates `raheader`/`radetail`; settle flips to `S`; the error report marks rejects | Synthetic RA placed in the MOH files directory by the fixture (the `ImportOnRA` route has no UI caller — the Billing Reconciliation page reads the directory), synthetic error-report file, seeded bills | `ViewBillingOHIPsimulation`, `ViewBillingOHIPreport`, `ViewGenReport`, `ViewGenRA`, `ViewOnGenRA*`, `ViewOnGenRAsettle`, `BillingONUpload`, `DocumentErrorReportUpload`, `moveMOHFiles` |
| `billing-on-mri-batch-clipboard` | Schedule ▸ Billing ▸ MRI; Administration ▸ Billing ▸ Batch Billing; bill form ▸ clipboard ▸ print | MRI lists unbilled/errored; batch creates N bills from N appointments; clipboard rows and print bytes | Seeded appointments | `ViewBillingONMRI`, `BatchBill`, `ViewBillingClipboard`, `ViewPrintBillingClipboard` |
| `billing-on-reports` | Schedule ▸ Report ▸ Generate a billing report ▸ unbilled / billed / unsettled / OB / flu ▸ Create Report; Administration ▸ Billing ▸ Invoice Reports, End Year Statement, Payment Received Report, INR Batch Billing; Administration ▸ Reports ▸ Age-Sex Report | Row counts equal SQL for the same range; PDF/CSV bytes | Seeded bills | `ViewBillingReportCenter`, `billingLreport`, `ViewBillingOBECEA`, `endYearStatement/*`, `DbReportAgeSex`, `ViewInrReportINR`, `InrUpdateINRbilling` |
| `billing-on-admin-config` | Administration ▸ Billing ▸ Manage Billing Form, Add Billing Location, Manage Private Billing Code, Manage Billing Codes (dx), Upload Schedule Of Benefits (fixture), Manage Clinic NBR Codes, Manage Referral Doctors, Manage Service Code Display Styles, GST Control/Report; bill form ▸ favourites | Each CRUD writes and restores `ctl_billingservice`, `billingservice`, `billing_payment_type`, `clinic_nbr`, `professionalSpecialists`… | Restore | `ManageBillingform*`, `ManageBillingLocation`, `ViewBillingONEditPrivateCode`, `BillingDigUpdate`, `benefitScheduleUpload`, `clinicNbrManage`, `ViewSearchRefDoc`, `manageCSSStyles`, `admin/Gst*`, `ViewBillingONFavourite` |
| `billing-shortcut` | Day sheet ▸ B ▸ billing form ▸ shortcut page 1 / 2 | Shortcut save creates the same rows as the long form | Seeded appointment | `billingShortcutPg1View`, `BillingShortcutPg2Save` |

### 2.8 Cross-cutting

| Check | Path | Asserts |
|---|---|---|
| `direct-response-contract` (parameterised) | Click every Print / PDF / Export / Download control a user can reach — eForm PDF, consultation letter, Rx print, labels, invoices, lab PDF, HRM print, chart print, eChart history print, flowsheet print, prevention print, report exports, Database/Document Download, envelope, measurement graph | Right `Content-Type`, magic bytes (`%PDF`, `PK`, `\x89PNG`), `Content-Disposition`, and never an HTML error page inside a download (the `CARLOS Error: 0` class from PR #2043) |
| `phi-in-error-pages` | Provoke 400/403/404/405/500 across each route family (bad ids on real pages, replayed POSTs) | No HIN pattern, no `FAKE-` name, no `demographic_no` in the body; local-only extension greps `catalina.out` after the suite for the same patterns |
| `waf-clinical-text-corpus` (front-door tier) | Extends `clinical-freetext`: post a fixture corpus of clinician sentences the CRS mis-scores through every free-text field the survey identified, via the UI | Each saves through nginx on `:443` |
| `page-script-integrity` (parameterised over the click map) | Reach every page/popup/panel `navigate.*` knows through its click | No script or stylesheet answers ≥ 400 or the wrong MIME type (the `displayImage.do?imagefile=stamps.js` class from #3313); no `pageerror`; no `jQuery.Deferred exception`; every page whose scripts do AJAX POSTs has a populated hidden `CSRF-TOKEN` input (the bootstrap rule in `CLAUDE.md`); DataTables lists finish their first draw; autocompletes answer |
| `schedule-shortcuts-popups` | Schedule: press each of the 17 day-sheet shortcut keys; click every top-bar opener (Tickler, Consultations, Msg, Inbox, Report, Billing, Administration, Preferences, eDoc, Scratch, WorkFlow, Dashboard, Program Management) | The right popup/window opens with focus, is wired strictly, renders its first content, and closes; the day-sheet view/date shortcuts change the rendered day |
| `opener-refresh-contract` | Day sheet ▸ slot ▸ Add Appointment ▸ save; Master Record ▸ Tickler ▸ add; Chart ▸ Preventions ▸ add; Chart ▸ Rx ▸ save; Chart ▸ eForms ▸ save; Consultations ▸ new ▸ save | After the popup closes the opener shows the new row/badge **without a manual reload** (the `window.opener` callback ran) — and the row is in the DB |
| `dialog-confirmations` | For each delete/cancel/discard control in the P1 families (appointment delete, tickler delete, note discard, Rx delete, allergy delete, document delete, bill delete, eForm delete) | Dismissing the `confirm()` writes nothing; accepting writes the row; a missing dialog (handler changed) is a failure |

---

## 3. Priority 2 — daily-use surfaces with partial coverage

### 3.1 Changes to existing scripts in P2 modules

| Script | Change | Why |
|---|---|---|
| `eform-consultation-acceptance` | Drive the `#specialistInput` autocomplete; fail if the programmatic fallback fires | Obs. 5: silently takes the fallback since the form changed |
| `form-rourke2017` | Enter via Chart ▸ Forms ▸ Rourke 2017; promote the plain-Save `WARN` to `FAIL` once the forward sends a `Content-Type` (obs. 11); add the NULL-page-1 500 and missing-`.jsp` 400 as negative asserts when fixed | §1.3; a WARN nobody reads is not coverage |
| `consultation-request-create` | Enter via Schedule ▸ Consultations; assert the Created/Updated confirmation once `transType` survives the redirect (obs. 1) | Report, don't encode |
| `specialist-add-cpso` | Enter via Consultations ▸ configuration icon | §1.3 |
| `prevention-brand-picker`, `allergy-add-penicillin` | Enter via Chart ▸ Preventions / Chart ▸ Allergies; assert the lot lookup answers (obs. 20) or is absent — a 404 is tolerated today | §1.3; report, don't encode |
| `rx-fax-reprint-represcribe` | Drop the tolerated #3578 page error when it closes | Burn-down |
| `billing-service-code-admin`, `schedule-template-crud`, `fax-configure`, `add-login-account`, `assign-role`, `eform-*` | Enter via Administration ▸ group ▸ item through `navigate.administration()` | §1.3 |
| `eform-corpus-soak` | Manifest `tier: extended`; `SKIP` (exit 2) when `EFORM_CORPUS_DIR` is unset | Fails for a missing fixture today |
| `consultation-signature-submit` | Manifest default `CONSULT_DEMO_NO=1` | #3313 "no documented default" |

### 3.2 Prescriptions and allergies

| Check | Path | Asserts | Routes |
|---|---|---|---|
| `rx-favorites` | Chart ▸ Rx ▸ Favorites: Add to Favorites from a written script; edit; copy; use; delete | `favorites` rows; a used favourite prefills the script | `rx/*Favorite*`, `useFavorite` |
| `rx-edit-discontinue` | Chart ▸ Rx ▸ drug profile: edit, discontinue with reason, delete, reorder, stash / clear pending | `drugs.archived`, `drugs.archived_reason`, order; Medical History shows it | `rx/UpdateScript`, `RxReason`, `deleteRx`, `reorderDrug`, `stash`, `clearPending` |
| `rx-interactions-renal` | Chart ▸ Rx ▸ a known interacting pair; Preferences ▸ Prescriptions ▸ Rx Interaction Warning Level (auto-saves via AJAX) | Interaction panel shows severity; changing the level hides/shows; renal dosing; limited-use code popup | `ViewInteractionDisplay`, `rxInteractionWarningLevel`, `ViewRenalDosing`, `ViewLimitedUseCode` |
| `rx-pharmacy-manage` | Chart ▸ Rx ▸ Pharmacy: click to edit ▸ add, edit, set default, deactivate | `pharmacyInfo`, `demographicPharmacy` rows; preview picks the default | `rx/managePharmacy2`, `ViewSetDefaultAddr` |
| `rx-print-profile` | Chart ▸ Rx ▸ print drug profile; previous prints | bytes `%PDF`; the print is listed | `ViewPrintDrugProfile2`, `ViewShowPreviousPrints` |
| `allergy-edit-delete` | Chart ▸ Allergies: non-drug allergy, severity, reaction edit, delete/archive | `allergies` rows; chart box updates; the unnamed search form (obs. 20) asserted fixed | `rx/addAllergy2`, `addReaction2`, `deleteAllergy2` |
| `rx-write-to-encounter` | Chart ▸ Rx ▸ Print & Add to encounter note | Note contains the Rx text | `rx/WriteToEncounter` |

### 3.3 Consultations

| Check | Path | Asserts | Routes |
|---|---|---|---|
| `consultation-edit-status` | Schedule ▸ Consultations ▸ row: status Pending → Completed, appointment date/time, urgency, attach document / lab / eForm, referral date, letterhead ▸ Print | `consultationRequests` + `consultdocs` rows; the list filters by status; attachments appear in the print PDF (bytes) | `encounter/RequestConsultation`, `oscarConsultationRequest/printPdf2` |
| `consultation-config-admin` | Consultations ▸ configuration icon ▸ services / institutions / departments (add, edit, delete); Administration ▸ System Management ▸ Professional Specialist/External Providers Admin | CRUD rows; the "All services" page without a selection no longer 500s (obs. 10) | `…/config/*`, `encounter/AddService`, `AddInstitution`, `AddDepartment`, `DelService` |
| `consultation-response` | Consultations ▸ row ▸ response section | Response saved and displayed | `EnableConRequestResponse` |

### 3.4 Messenger, ticklers, preventions

| Check | Path | Asserts | Routes |
|---|---|---|---|
| `messenger-attachments` | Schedule ▸ Msg ▸ Compose ▸ attach document / lab / eForm; recipient opens it ▸ PDF preview ▸ Transfer to chart; Administration ▸ System Management ▸ Messenger Group Admin ▸ group message; Chart ▸ Messenger ▸ patient-linked list | Attachments listed; preview bytes; note written; one `messagelisttbl` row per group member; markdown body renders (obs. 8) | `messenger/attachmentFrameset`, `AdjustAttachments`, `PreviewPDF`, `WriteToEncounter`, `AddGroup`, `DisplayDemographicMessages` |
| `tickler-forward-filters` | Schedule ▸ Tickler: forward to another provider; priority/status/date filters; Tickler ▸ Add Tickler ▸ suggested text; Preferences ▸ Lab, Prevention & Messaging ▸ tickler settings; Dashboard ▸ Assign Tickler | Forwarded row appears for the other provider; filters match SQL; the patient-view date window (obs. 12) asserted fixed | `tickler/ForwardDemographicTickler`, `EditTicklerTextSuggest`, `setTicklerPreferences`, `web/dashboard/display/AssignTickler` |
| `prevention-edit-delete-refuse` | Chart ▸ Preventions: edit and delete an existing immunization; refused / ineligible; comments; next-date recall | `preventions` + `preventionsExt` rows; recall shows in the tickler | `prevention/AddPrevention` |
| `prevention-admin` | Administration ▸ Schedule Management ▸ Prevention Notification Settings; System Management ▸ Prevention List Manager (shell), Add Prevention Lot number / Search lot number by prevention; Chart ▸ Preventions ▸ print | Config rows; lot rows offered in the picker; print bytes | `ViewPreventionManager`, `ViewPreventionListManager`, `admin/LotNr*`, `rtlPreventions` |

### 3.5 Clinical forms (139 JSPs, 22 routes)

- `form-catalog-smoke` (parameterised): Administration ▸ Forms/eForms ▸ Select Forms ▸ add
  each form; Chart ▸ Forms ▸ open it for demographic 2; assert no error page and no new console
  error; Save; assert a row in the form's table (the manifest lists the table per form);
  delete the row; Select Forms ▸ delete. One run covers ~40 forms; a broken JSP is a one-line
  finding.
- Deep checks for the forms clinics use daily, all entered via Chart ▸ Forms: `form-rourke2020`
  (four pages, growth percentiles), `form-growth-chart` (+ print bytes), `form-annual-v2`
  (male/female), `form-mental-health-on` (Forms 1/14/42), `form-discharge-summary`,
  `form-xml-upload` (Administration ▸ Forms/eForms ▸ Import Form Data), and the print
  buttons that hit the PDF servlets (bytes; `error-sanitization` already uses one for the 500
  case).

### 3.6 Reports and dashboards

| Check | Path | Asserts |
|---|---|---|
| `report-index-links` | Schedule ▸ Report: Day Sheet, Demographic Report Tool, Ontario Prevention Report, Generate a billing report; Administration ▸ Reports ▸ every item | Each renders without an error page or new console error (extends `schedule-links`, which only opens the index) |
| `report-by-template` | Administration ▸ Reports ▸ Report by Template: upload the fixture template, list, group, run with parameters, export | Result table equals SQL; CSV/XLS bytes; delete |
| `report-query-by-example` | Administration ▸ Reports ▸ Query By Example: save, favourite, run, load favourites | Rows; favourites persisted |
| `report-daysheet-labs` | Schedule ▸ Report ▸ Day Sheet for a provider/date; lab day sheet print | Seeded appointments listed; print bytes |
| `report-clinical-reports` | Administration ▸ Reports ▸ CDS Report, MIS Report, Provider Service Report (+export), PCN Catchment Report, Disease Registry Report, Visit Report, Age-Sex Report, Patient List by Appointment Time (existing), Population Report; Report ▸ Demographic Report Tool ▸ clinical export | Each answers a real report or its documented empty state; download bytes |
| `report-cdm` | Administration ▸ Reports ▸ Query By Example ▸ CDM report: patients met guideline / abnormal range / frequency of tests over seeded measurements | Rows equal SQL |
| `dashboard-display` | Schedule ▸ Dashboard (main menu): indicators, drilldown, export, bulk action (assign tickler), shared outcomes dashboard | Indicator counts equal SQL; drilldown lists patients; export bytes; `tickler` rows |

### 3.7 Administration and preferences

| Check | Path | Asserts |
|---|---|---|
| `admin-index-links` | Schedule ▸ Administration ▸ every link/popup in every group (and the same items in the `/administration` shell's left nav) | Opens without an error page or a new console error; replaces the admin half of `browser-surface`; catches the #3313 class of JS breakage across ~90 pages |
| `admin-lookup-lists` | Administration ▸ System Management ▸ Manage Lookup Lists; CAISI ▸ Lookup Field Editor | CRUD; the new item is offered where the list is used |
| `admin-messages` | Administration ▸ CAISI ▸ System Messages / Facility Messages / Default Encounter Issue | CRUD; the message shows on the login page / schedule banner |
| `admin-issue-editor` | Administration ▸ CAISI ▸ Issue Editor | Add/edit; the issue is offered in the note's issue search |
| `admin-audit-log` | Administration ▸ System Reports ▸ Security Log Report; Data Management ▸ Purge Audit Log (shell) | Lists the READ/UPDATE audits earlier checks generated (PIPEDA evidence); purge refused without privilege and, with it, only outside the retention window |
| `admin-jobs-api-keygen` | Administration ▸ System Management ▸ Jobs Management / Job Type Management (shell), Key Pair Generator; Integration ▸ REST Clients | CRUD; API client create/revoke; key create/manage, public key download |
| `admin-email-config` | Administration ▸ Emails ▸ Configure Email / Manage Emails (shell) | Save/restore; seeded queue rows; compose/send against a stubbed transport |
| `admin-misc` | Administration ▸ System Management ▸ Help Link Setting, Clinic/Agency Address, Satellite-sites Admin, Customize Measurements, Customize Disease Registry Quick List, Customize Consult Appointment Instructions, Manage Facilities; Schedule Management ▸ Access Control, Add/Edit Group Preferences; Doctor Content Management ▸ Document Description Template; Reports ▸ Server Logging; Data Management ▸ Database/Document Download (bytes, admin only, refused for others), Update Patient Provider, Fix notes with invalid role (shell) | Each writes and restores its rows |
| `provider-preferences` | Schedule ▸ Preferences (`providerpreference.jsp`, the page the top bar opens) ▸ every section: Schedule & Appointments, Contact Information (address / phone / fax used on prescriptions and consult letters), Prescriptions (Rx Interaction Warning Level), Clinical Settings (stale date for case notes and its format), Consultation (team warning), Display & UI (schedule navigation mode, quick chart size), Lab, Prevention & Messaging (Lab Recall Macros, tickler settings), Quick Links (Add Link / Remove), signature and colour where the page offers them, and the Change Password link | Each writes `property`/`provider` rows and restores; the signature shows on the next consultation letter |

---

## 4. Priority 3 — admin-rare, province-gated, or blocked on infrastructure

### 4.1 eForms (already 47% by route)

`eform-groups-independent`: Administration ▸ Forms/eForms ▸ eForm Groups (create, add, remove),
Patient-independent eForm list, deleted lists + restore (`pr-hardening` covers the GET refusal
only), Upload an Image (upload / delete / display), Chart ▸ eForms ▸ save as eDoc (document
row + file), download PDF (bytes), open by name, Field Note Report & Management, Visual eForm
Editor and generator open and save a trivial form.

### 4.2 BC billing, BC labs, BC forms — blocked on a BC dev profile

There is no BC devcontainer profile (`billregion=ON`; `populate_db.sh` loads the Ontario
migration set). Prerequisite: a BC profile for `populate_db.sh` / `carlos-ctl demo-data` and a
`--province BC` manifest filter. Then, in the same shape as §2.7 and entered from
Administration ▸ Billing (BC MSP Quick Billing, Generate Teleplan File, Simulate Submission
File, Manage Teleplan, Upload Remittance Files, MSP Reconcilliation Reports, Accounting
Reports, Edit Invoices, Settle Over/Under Paid Claims, Manage Service/Diagnostic Code
Associations, Manage Procedure/Fee Code Associations, Manage Private Bill) and the day sheet's
B link: `billing-bc-create-view`, `billing-bc-teleplan-file` (synthetic remittance),
`billing-bc-wcb` (WCB form + correction), `billing-bc-private-statement`,
`billing-bc-codes-admin`, `lab-bc-pages` (Inbox in a BC install), `form-bcar2020` /
`form-bcnewborn2008` (Chart ▸ Forms). Until the profile exists these routes have **zero**
coverage and should be listed as such in the runbook rather than silently absent.

### 4.3 PMmodule / CAISI

Read-only smoke of Schedule ▸ Program Management (client search, program / client / staff /
facility managers), gated on CAISI being enabled in the dev profile; write checks only if the
project keeps CAISI (confirm under the cleanup policy first).

### 4.4 Integration surfaces

Schedule ▸ WorkFlow (list CRUD); Schedule ▸ Scratch (save; extend `schedule-links`);
Search ▸ swipe with a synthetic track; Administration ▸ Billing ▸ MCEDT Interface with the
transport stubbed (`MCEDT_LIVE=false`; a live twin under `scripts/e2e/mcedt/` like the fax
e2e if ever wanted); DHIR submit and the OntarioMD redirect as stubbed-transport UI checks.

### 4.5 Cross-cutting, report-only first

| Check | Path | Asserts |
|---|---|---|
| `responsive-viewport` | Login, Schedule, Master Record, Chart, Rx, Inbox, bill form, Administration at 1366×768 and 1920×1080 | Primary actions inside the viewport, no horizontal scroll (obs. 16) |
| `accessibility-smoke` | Same pages, axe-core injected | Report-only for one release, then fail on new serious violations |
| `i18n-locale` | Log in with the French locale; Schedule, Chart, Master Record, Rx | No `???key???`; the bundle-linted keys resolve |

---

## 5. Sequencing

Effort is in engineer-weeks for someone who has written one check on this suite already.

| Phase | Scope | Exit criteria | Effort |
|---|---|---|---|
| 0 | §2.1 harness + `runCheck` + `wireStrictPage` + `navigate.*` + `ui.*`, manifest + runner, missing npm aliases, migrate 8 scripts as proof | Runner drives the existing suite in the devcontainer; `script-regressions.yml` tests the harness and the manifest; runbook §6 loop replaced | 2 |
| 1 | §2.1 fixtures, smoke CI tier (non-blocking), the §1.3 path changes, strict wiring on all 75, migrate remaining scripts | Smoke tier green on three consecutive PRs; every script enters through its opener and fails on a `pageerror`; #3313 / #3317 / #3598 / #3600 closed | 3 |
| 2 | Priority 1 checks (§2.2–2.8), starting with `page-script-integrity`, `schedule-shortcuts-popups`, `opener-refresh-contract`, `dialog-confirmations` | Route coverage ≥ 45%; every daily clinical + revenue workflow has a DB-asserting check; nightly core tier on `develop` and `release/**` | 8–10 |
| 3 | Priority 2 (§3) | Route coverage ≥ 70%; `admin-index-links`, `report-index-links`, `master-record-tabs`, `echart-navbar-modules`, `form-catalog-smoke` give every reachable page at least a render check | 6–8 |
| 4 | Priority 3 (§4; BC after the profile); front-door CI | BC routes covered or explicitly listed as uncovered; a11y / i18n report-only in nightly | 4–6 |

Tracking: one umbrella epic "Playwright coverage — release/2026.08" with one sub-issue per
check and per row of the change tables, labelled `type: test`; each carries the manifest
entry it must add. New checks land with their manifest entry, fixture files, a row in a
`coverage-map.md` in the style of `alpha-11-tester-coverage.md`, and a `.test.js` for any new
shared helper.

## 6. Deliberately not covered, and why

- **Live external systems** (SRFax send/receive, MCEDT, DHIR, CPSO registry, DrugRef rebuild
  against Health Canada): stay in `scripts/e2e/` or behind `*_LIVE=true`; UI checks stub the
  transport. They cost money, need credentials, or take an hour.
- **Routes with no UI entry**: no check — the finding is that the route is dead (or
  service-only), tracked for removal or documentation under the cleanup policy. Found while
  verifying this plan: `prevention/printPrevention`; `report/ViewGenerateLetters` and the
  letters / envelopes / spreadsheet generation behind it (nothing links the page);
  `provider/ViewProviderEncounterHistory` (a `providercontrol` dispatch nothing calls);
  the immunization *set* configuration pages (`encounter/immunization/config/*`);
  `admin/ViewDbConnection`; `billing/CA/ON/ImportOnRA` (service-only, the Billing
  Reconciliation page reads the MOH directory instead); and several `View*` fragments only
  reachable as includes.
- **PHR / integrator / eConsult**: the top bar's eConsult opens an external URL; nothing to
  assert locally.
- **Visual regression by screenshot diff**: the MCP manual tests keep gold screenshots; the
  scripted suite asserts DOM/DB/bytes instead. A pixel-diff tier is a possible later add-on but
  is not what has caught defects on this project.
- **Load / soak** beyond `eform-corpus-soak`: out of scope for browser checks.

## 7. Appendix — measured hygiene numbers on this branch

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
| enter by typed URL where a click exists (§1.3) | ~30 |
| assert `pageerror` | 26 |
| assert `requestfailed` | 0 |
| wait for a popup window (`popup` event) | 3 |
| assert the opener refresh contract | 10 |
| press a keyboard shortcut | 3 |
| call a page function from `page.evaluate` as the path | 2 |
