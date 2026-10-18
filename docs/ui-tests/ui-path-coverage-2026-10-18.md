# UI workflow coverage on release/2026.08 — 18 October 2026

Target: `release/2026.08`, source commit `a7611d07b4ae9b237b4a5d252fa938e09208eb85`.
Tests use fictitious patients and a disposable Ubuntu 26.04 systemd container.

## Current coverage branch and latest validation

The completed 31-round expansion adds **62 checks**, including **44 Rx checks**.
Together with the earlier 21 additions, the manifest grows from **692 to 775**.
Passing checks and defect reproductions are saved on local branch
`codex/ui-workflow-coverage-2026-08` (test commits `1365345a7ea`, `d3ab4c4a6cb` and `010fd58f99e`,
following `b94f409ad40`). No application source changes are included.

| Final selection | Executed | Result |
|---|---|---|
| Rounds 1–9: clinical and task actions | 18 | 15 passes, 3 known failures, 0 unexpected failures, 0 skips |
| Rounds 10–31: added Rx actions | 44 | 35 passes, 9 known failures, 0 unexpected failures, 0 skips |
| Latest result for each existing Rx-related check, including WAF and multisite | 50 | 44 passes, 6 known failures, 0 unexpected failures, 0 skips |

The initial 50-check Rx regression selection records 44 passes, 2 known failures, 4 unexpected failures, 0 skips.
Its focused follow-up records 1 pass, 3 known failures, 0 unexpected failures, 0 skips. The table uses
those later results for repeated checks; raw XML/logs are retained. The
additional Info server-log verification records 0 passes, 1 known failure, 0 unexpected failures, 0 skips
and uses `LAB_UPLOAD_JOURNAL_UNIT=carlos-emr`.
The medication-list follow-up enables its documented
`CONSULTATION_AUTO_INCLUDE_MEDICATIONS=true` prerequisite; the initial default
false result is a configuration prerequisite, not an application defect.

Across **94 distinct Rx-related checks**, the latest results are **79 passes
and 15 known failures**, reproducing 13 Rx issues (292, 295 and 298–308).
No selected Rx workflow remains skipped or unexpectedly failing after focused
verification with its documented prerequisites.

The strengthened inline-dose check also passes separately with actual method
and route changes. Each grouped selection runs with the database residue audit;
its result is retained in the evidence archive. The 152 harness, manifest,
script-inventory and issue-log tests pass. Counts above distinguish executed
known failures from unavailable/skipped checks; JUnit represents known failures
with a `skipped` element whose message explicitly begins `known-fail`.

The [Rx action matrix](rx-workflow-coverage-2026-10-18.md) maps existing and added
assertions to user controls. The [31-round ledger](workflow-expansion-rounds-2026-10-18.md)
lists every new entry. This is workflow/action coverage, not a claim that all
775 application checks ran, every possible input was explored, or every Rx task
works correctly.

## Coverage review

The starting manifest contained 692 checks. This review focused on the clinical
WorkFlow queue and task completion paths, comparing the registered scripts with
the actual JSP controls and their server actions. It is not a claim that every
application feature has been exercised.

| Existing coverage | Gap addressed |
|---|---|
| `rh-form-workflow` saves and edits an RH form, checks rejected writes and transaction rollback | It does not open the saved form from WorkFlow, close the pregnancy, or start a later one |
| `surface-audit:workflow-surface` and `workflow-tickler-suggested-text` open WorkFlow when enabled | They do not assert queue membership or follow a patient's RH link |
| RH form save checks | They do not press Add Injection or read/delete an existing injection from the RH panel |
| `tickler-crud` completes one task through a directly opened edit address | Completion now opens the task's row pencil; a missing search result now fails instead of printing a warning |
| `schedule-links` falls back to the last browser page when no popup opens | Uses the actual popup or current-tab navigation, verifies each destination route, and closes owned popups; cannot inspect an earlier Scratch window for Report/Inbox/eDoc |
| `search-sort-tickler-list` checks status-filter results; `tickler-demo-main-status` checks a separate patient-list page | The main task list's Check All/Clear All, subset Complete/Delete, reactivation and opener refresh, hidden control task, and action buttons after switching to Deleted were not covered |

## Added checks

The first batch added seven entries (699 total), with npm aliases:

- `rh-workflow-queue`: save a pregnancy, assert its queue row, and use its RH link.
- `rh-workflow-queue-lifecycle`: close a pregnancy from the chart form, verify it
  leaves the active queue, then save a later pregnancy without reopening the old one.
- `rh-injection-workflow`: Add Injection, save its clinical fields, verify the
  prevention and pregnancy/form links, and verify the opener panel and Delete.
- `rh-injection-existing`: display and delete an existing injection independently
  of the add path, so an add failure cannot mask the existing-record workflow.
- `tickler-bulk-actions`: select visible tasks, complete a subset, reactivate one
  through the row pencil and assert the automatic opener refresh.
- `tickler-bulk-state-actions`: use an explicit Create Report refresh after the
  edit, then delete the other task and check the Deleted view's action buttons.
- `rh-injection-log-privacy`: open an existing injection through the RH form and
  verify that its clinical data does not appear in the application journal.

All positive paths use actual UI controls. Database assertions complement the
rendered results. Cleanup owns the synthetic patient and its children; RH checks
restore optional form registration. The hidden tickler control must remain active.

## Package and runtime setup

CARLOS was compiled from the target source with JDK 25 and the Debian Maven flags;
DrugRef was compiled from the pinned revision
`1941e1426a52c2a129c96ec4d32a0f3499974739`.
`dpkg-buildpackage -us -uc -b` ran inside `ubuntu:26.04` and built
`carlos-emr_2026.09.0~snapshot26_amd64.deb` and
`carlos-emr-drugref_2026.09.0~snapshot26_all.deb`. This is the branch's existing
developer package version, not a new published release. The pinned
`carlos-ctl_1.1.2_all.deb` was downloaded and its published SHA256 verified.

The full package build could not fetch its pinned renderer Chromium from
`commondatastorage.googleapis.com` (HTTP 403 under the environment network policy).
The workflow-test packages therefore use the documented development option
`SKIP_EFORM_RENDERER=1`. They are not release artifacts. Saved-eForm rendering is
outside this validation. Playwright's separate Chromium 148.0.7778.96 test browser
was downloaded from the allowed Playwright service and used for UI automation.

The validation harness's session copy uses a prebuilt Ubuntu image and a read-only
CA mount; no session CA is retained in an image layer. It otherwise follows
`scripts/deb-docker-validation.sh`: fresh install, demonstration data, package
health checks, first-login reset and HTTPS through nginx/ModSecurity. `WORKFLOW=yes`
must be enabled before running the queue checks.

## Results and issues

The focused run against the unmodified packaged application completed with
**1 pass, 6 failures, 0 skips**. The fixture residue audit passed. Chromium was
148.0.7778.96. These are application failures, not a passing workflow claim:

| Check | Result | Finding |
|---|---|---|
| `rh-workflow-queue` | RH link opens `/form/forwardshortcutname` outside the application context | 284 |
| `rh-injection-workflow` | Add Injection renders the CARLOS error page | 285 |
| `tickler-bulk-actions` | Reactivation stores A, but the Completed opener never reloads | 289 |
| `rh-workflow-queue-lifecycle` | PASS: active queue row, closure, later pregnancy, old workflow preserved | — |
| `rh-injection-existing` | Existing injection fragment returns 200 with no injection displayed | 288 |
| `tickler-bulk-state-actions` | Selection, Complete, manual refresh/reactivation and Delete pass; Deleted has no Erase Completely button | 286 |
| `rh-injection-log-privacy` | Journal scan finds one clinical-data event from `prevention.PreventionData` | 290 |

All six defects have exact `expectedFailure` pins in the suite manifest. The
underlying scripts still assert correct behaviour and exit 1; the runner reports
`known-fail` only at that one labelled step. Failure elsewhere, cleanup failure
and timeout continue to fail the suite. Finding 287 (`return False` in the
injection submit handler) is source-confirmed and remains `needs-live-check`:
finding 285 blocks the only real UI entry before its save step. It has not been
reported as live coverage.

The issue register is [app-findings-log.md](app-findings-log.md), findings 284–290.
No application fixes or production data changes are included.

During test development a hidden-input visibility wait and an immediate read
following a `javascript:` selection link were corrected. Check All/Clear All then
passed; neither is recorded as an application defect. The first residue audit
also saw 63 measurement types and 44 validation catalogue rows appear on first
chart use; the subsequent complete focused run left no residue.

The existing smoke tier passed **11/11 selected checks**: application health,
appointment lifecycle, browser surfaces, document upload, drug search, eChart,
error sanitization, GET/HEAD mutation refusal, schedule links, tickler CRUD, and
login/reset. `eform-render` was explicitly excluded because the packaged renderer
was omitted; this is not a full 12-check smoke claim.

Four related checks also passed: `rh-form-workflow`, `tickler-forward-filters`,
`tickler-preferences`, and `tickler-demo-main-status`. The two independent new
checks repeated in that run reported their exact known failures (286 and 290).
Its residue audit was clean. The existing test harness, complete script syntax
inventory, suite registration and findings-log checks passed **152/152**.

The schedule-link test was then strengthened after its initial smoke result
showed several destinations incorrectly recorded as Scratch. Its final dedicated
run passed in 10.9 seconds and recorded the correct Tickler, Consultations,
Scratch, Report, Inbox and eDoc routes (the earlier version took 54.1 seconds
and recorded Scratch for several unrelated destinations).

The final `carlos-ctl check` again reported **All checks passed**. The server-error
audit deliberately remains a failure: two `PreventionData` signatures (numeric-id
conversion and the subsequent null map) match findings 288/290. It also recognized
existing finding 138 (appointment receipt debug output logged at ERROR) and the
negative probes the tests intentionally send. No new audit suppression was added.

Installed stack: Ubuntu 26.04.1 LTS, OpenJDK 25.0.4.1, Tomcat libraries 11.0.22,
MariaDB 11.8.6 and nginx 1.28.3. The container remains available as
`carlos-coverage-validation`; the prepared environment is `/root/suite-env.sh`
inside it. Source changes are on local branch
`codex/ui-workflow-coverage-2026-08`.

## Repeating the checks

From the repository in this prepared workspace:

```bash
docker exec carlos-coverage-validation bash -c 'source /root/suite-env.sh; cd /root/carlos; node scripts/run-playwright-suite.js --only rh-workflow-queue --only rh-workflow-queue-lifecycle --only rh-injection-workflow --only rh-injection-existing --only rh-injection-log-privacy --only tickler-bulk-actions --only tickler-bulk-state-actions --residue-audit'
docker exec carlos-coverage-validation bash -c 'source /root/suite-env.sh; cd /root/carlos; node scripts/run-playwright-suite.js --tier smoke --skip eform-render'
node --test scripts/playwright-suite-manifest.test.js scripts/playwright-harness.test.js scripts/app-findings-log.test.js
```

The full 713-entry suite, BC workflows, and saved-eForm PDF rendering were not
validated in this run. The added RH tests need `WORKFLOW=yes` for queue coverage
and exclusive use of their disposable deployment for optional form registration.

## Follow-up: waiting-list actions

A second review compared the 699 checks with the waiting-list manager and name
editor controls. The existing `waiting-list` test creates a list, adds a patient,
edits through Master Record, reads the report and removes through the patient
page. It does not operate the manager's row Update/Remove controls, Rename/Delete
in the name editor, or Make Appointment. Screen and two-window checks do not
substitute for those writes.

Six additional entries bring the manifest to **705 checks**:

| Check | Actions and outcomes |
|---|---|
| `waiting-list-management` | Rename an occupied list with quotes/ampersand; duplicate-name refusal; occupied-list deletion refusal; empty-list archive; both pickers and existing members |
| `waiting-list-row-edit` | Manager row note/date update; date-based reprioritization; multiline text; original history; clearing a note; Update without editing; another patient's note/date preserved |
| `waiting-list-manager-remove` | Cancelled confirmation writes nothing; confirmed removal archives only the selected row, preserves the other patient and refreshes the manager |
| `waiting-list-booking` | Make Appointment from a row, free Flip View slot, patient/provider/date/time prefill, Add once, Cancel the removal prompt, both list members retained |
| `waiting-list-booking-navigation` | Next Month in that same booking path must preserve the selected patient when a slot opens |
| `waiting-list-booking-provider` | Changing provider in that calendar must preserve the selected patient when a slot opens |

Each starts at the schedule's Report control and uses the real Waiting List entry.
Existing lists are SQL fixtures so these actions do not depend on the older Create
List test. The fixture owns two synthetic patients, its list names and any booked
appointment. The provider-change variant adds two marker-owned memberships for
existing active providers to the login group, runs exclusively and verifies their
removal; provider records and preferences are unchanged. The booking tests use
a provider actually offered by the group's selector and a future free rendered
slot; they report a skip if that fixture prerequisite is unavailable.

The first three checks passed live with no fixture residue. During development,
a control patient's overlong surname and a browser form's CRLF line endings were
corrected in the tests; these are not application findings. The affected synthetic
fixtures were explicitly removed and the next complete run's residue audit passed.
The stale source-only finding 278 was reconciled with the already-fixed finding
79: the current packaged application has a working Report > Waiting List entry.

Immediate booking passes. **Finding 291** is reproduced by two independent tests:
Next Month and the provider picker both drop the patient parameters, so choosing
a slot opens Add Appointment with an empty patient field. Each has an exact
expected-failure step; the underlying scripts continue to assert the correct
patient. The provider-change check first reported a skip because the demo group's
picker had fewer than two members; owned temporary memberships now exercise it
live. No skipped provider-change result is counted as coverage.

The first related run also identified a cleanup omission in the existing
`waiting-list` test: Master Record Update left 60 extension-archive rows and one
custom-field row for its removed synthetic patient. The test now removes and
verifies those rows before releasing its patient. Those orphan fixtures from the
first run were explicitly removed. `appointment-waiting-list-prompt` passed on
this release branch, so its stale finding-100 expected-failure pin (documented
as develop-only even in its notes) is removed. Finding 100 remains open for its
other pages; no CSS failure is suppressed.

The final combined follow-up run completed with **7 passes, 2 known failures,
0 unexpected failures and 0 skips**. Four new positive workflows and the three
related checks (`waiting-list`, `appointment-waiting-list-prompt`,
`schedule-quick-search-appointment`) passed. The two failures reproduce finding
291 at their exact steps. The residue audit passed, and final database counts
confirm no FAKE-PW patients, lists or group memberships, and no support-row residue
from these tests. Harness, script syntax inventory, manifest and issue-register
checks passed **152/152**. `carlos-ctl check` again reported All checks passed.
The follow-up's current-boot server audit passed with only four deliberate CSRF
rejection events; it does not supersede the earlier RH error findings.

This follow-up reused the already-built target-branch debs in the Ubuntu 26.04
container. Only tests, registration and documentation changed; no application
code or package rebuild was needed. The change through that follow-up added **13 checks**
(692 → 705); finding 291 is the seventh live-confirmed application defect from
this coverage work, alongside the one source-only finding 287.

Repeat the follow-up in the prepared container:

```bash
docker exec carlos-coverage-validation bash -c 'source /root/suite-env.sh; cd /root/carlos; node scripts/run-playwright-suite.js --only waiting-list-management --only waiting-list-row-edit --only waiting-list-manager-remove --only waiting-list-booking --only waiting-list-booking-navigation --only waiting-list-booking-provider --only waiting-list --only appointment-waiting-list-prompt --only schedule-quick-search-appointment --residue-audit'
```

## Further expansion: medication, letter and task actions

This review started with 705 checks and compared actual controls with assertions,
including test bodies reached through reused scripts. It adds eight entries
(**713 total**) across three workflows:

| Existing coverage | Added action coverage |
|---|---|
| Rx favourites: add, share/copy, ownership, name edits, duration-unit rendering and retry after a server failure | Change dose range, frequency, duration, quantity, repeats, No Subs, PRN and custom instructions; save/reopen; choose the edited result; refuse six invalid fields before posting; dismiss and confirm Delete |
| Patient letters: navigation, template upload/download and successful generation by direct request | Template Delete/archive and generation picker, actual Generate button/PDF/document/log, stale generation after template deletion, and readable manager labels |
| Patient task queue: Complete/Delete/status controls; Erase Completely only listed | Empty Erase changes nothing; selected Erase must remove only its task from the ordinary Deleted queue, with audit retention permitted |

The new entries are `rx-favourite-dose-roundtrip`,
`rx-favourite-editor-validation`, `rx-favourite-multiline-directions`,
`patient-letter-template-delete`, `patient-letter-generation-ui`,
`patient-letter-stale-template`, `patient-letter-manager-labels`, and
`tickler-patient-erase`. Dosing and multiline directions run independently; the
PRN and line-break assertions have separate exact failure steps. Letter labels
are checked separately from the archive and generation operations.

The consultation-response endpoint has API coverage, but this source review
found no corresponding UI entry/renderer in the target branch. No constructed
route is counted as a response UI workflow.

All new writes start from UI controls. Lists/templates are either marker-owned
SQL fixtures (Rx/tasks) or uploaded through the actual library form (letters).
The patient queue is opened from E-Chart > document > open ticklers. Every
patient, favourite, task, template, generated document/file and generation-log
row is owned and removed; document support rows use the existing residue helper.
The letter-library tests run exclusively. No application code changes are included.

Findings 292–295 record four reproduced application defects: multiline favourite
directions concatenate, Erase Completely leaves a selected task in Deleted,
Manage Letters renders unresolved keys, and staging clears an edited favourite's
PRN flag. The issue register distinguishes the observed symptoms from any root
cause not fully traced (PRN). These are assertions of correct behaviour, with
precise manifest pins rather than suppressed browser errors.

Test-development corrections include waiting for a javascript-link confirmation,
selecting one of two equivalent Rx Back controls, following Upload's actual
return page, entering the HTML document viewer from the chart, and distinguishing
inline PDFs from downloads. For generation, a transparent route observer forwards
the actual UI POST exactly once (no retry or redirect), captures its original raw
bytes and passes the response through unchanged: Chromium otherwise exposes its
PDF-viewer HTML as the response body. This does not replay a generation write.
The stale-template check requires its particular HTTP 400 and visible error
before consuming that one expected network-console entry.

The final combined run completed with **7 passes, 4 known failures, 0 unexpected
failures and 0 skips**. The four new positive checks (Rx validation/delete, letter
archive, generation and stale-template recovery) passed, along with the existing
`rx-legacy-null-fields`, `patient-letters-envelopes` and
`tickler-demo-main-status`. The residue audit passed. Final database checks found
zero FAKE-PW patients, favourites, letter templates or tasks.

| New failing check | Exact finding |
|---|---|
| `rx-favourite-dose-roundtrip` | 295: save/reopen, medication identity, quantity, repeats, directions and No Subs pass; PRN on the staged card is false |
| `rx-favourite-multiline-directions` | 292: the editor preserves the newline, but the prescribing card concatenates the lines |
| `tickler-patient-erase` | 293: empty selection and untouched controls pass; the erased task is still listed in Deleted |
| `patient-letter-manager-labels` | 294: eleven manager message keys remain unresolved, including Upload and Delete |

All **152** harness, manifest, script-syntax and issue-register checks pass.
The final package health check reports **All checks passed**. The current-boot
server-log audit passes with five recognized deliberate CSRF-rejection events
and no unexplained signature. No new audit suppression was added. The same
Ubuntu 26.04 deb installation is reused: application/package source is unchanged.

The cumulative change now adds **21 checks (692 → 713)**. This work has recorded
**11 live-confirmed application defects** (284–286 and 288–295) plus source-only
finding 287. It does not claim a complete 713-check run, BC validation or saved
eForm-renderer validation. Chromium remains 148.0.7778.96.

Repeat this expansion in the prepared container:

```bash
docker exec carlos-coverage-validation bash -c 'source /root/suite-env.sh; export LETTER_DOCUMENT_DIR="${LETTER_DOCUMENT_DIR:-${DOCUMENT_DIR:-}}"; cd /root/carlos; node scripts/run-playwright-suite.js --only rx-favourite-dose-roundtrip --only rx-favourite-editor-validation --only rx-favourite-multiline-directions --only tickler-patient-erase --only patient-letter-template-delete --only patient-letter-generation-ui --only patient-letter-stale-template --only patient-letter-manager-labels --only rx-legacy-null-fields --only patient-letters-envelopes --only tickler-demo-main-status --residue-audit'
```

## Thirty-one further rounds: clinical tasks and Rx workflows

The user-requested current round plus 10 and then 20 additional rounds total 31.
The first nine cover episode coding/editing, clinical care teams, consultation
follow-up, paged bulk tasks, repeated/empty patient-set saves, message selection,
appointment-search filters/limits, prevention reminder suppression/resumption,
and saved Rourke/lab-requisition corrections. External administration was
removed and replaced by another reminder task. The clinical external-contact
picker remains a care-team workflow.

The remaining 22 rounds add two Rx checks each. They cover indication archive
and patient-registry selection; CPP visibility and refused writes; inline dosing
and PRN; empty saves and cancelled entry; 18 renewal-field changes; medication
renaming; invalid numeric doses and written dates; double saves; all 16
discontinuation reasons and Cancel with another draft; pharmacy removal followed
through to save/preview; graph selection; and correction after closing preview.
Database assertions preserve source history and untouched control medications.

Findings **296–308** add thirteen confirmed defects, eleven in Rx. The new Rx failures
are patient-diagnosis indication selection (298), a misleading Hide CPP checkbox
after refusal (299), optional pickup clearing alerts (300), an impossible written
date silently saved on another day (301), HTTP 500 for non-numeric doses (302),
clear-all timeline selection reverting to all (303), and two active doses after
correcting a saved preview (304). The existing card-options regression also
confirms noon pickup saved as midnight (305), and saved date-only pickup
displays an invented midnight time (306), and an empty search offers a
selectable placeholder posted as product 0 (307). Within the 44 added Rx
checks, minimum/maximum and X/Escape each have separate reproductions: nine
failing checks correspond to seven causes. The existing regressions expose
the three additional Rx issues (305–307). The server-log audit then exposes
a DrugRef exception behind a successful missing-DIN Info fallback (308); the
existing Info check now brackets the lookup with a journal cursor and asserts
that no such error is emitted. Finding 82 is
independently reproduced by the new patient-set uniqueness assertion. All remain
assertions of correct behavior; no application fix or new error suppression is
included.

The cumulative branch adds **83 checks (692 → 775)**. The register now holds
**24 live-confirmed findings from this coverage campaign** (284–308 except
source-only 287), and independently reconfirms existing finding 82. Thirteen of
the live findings concern Rx. The historical Qty/price portion of finding 99
passes now; its other page components are not reclassified.

The packages built from the target application source were reused. Test-only
changes do not require rebuilding the WAR. Their snapshot version, renderer
omission, stack versions and SHA256 values remain as recorded above. The
multisite regression uses the documented temporary `multisites=on` setting and
an explicit service restart before its selection; the original absent multisite setting
and false consultation automatic-inclusion setting are restored afterward. No restart occurs within a completed grouped selection.
An early regression attempt was interrupted because startup was still in
progress; it is not counted as an application failure or final coverage. The
final attempt starts after `carlos-ctl check` passes and synthetic-patient count
is zero.

The existing Rx selection has 50 distinct entries. Its latest non-pass outcomes after focused verification:

| Check | Outcome | Detail |
|---|---|---|
| `rx-card-options` | known-fail | known-fail: known failure at step "a 12:30 pick-up time is stored as 12:30" (finding 305) |
| `gap-encounter-rx-drug-info-on-host` | known-fail | known-fail: known failure at step "missing DIN fallback does not log a DrugRef server exception" (finding 308) |
| `rx-static-script-pickup` | known-fail | known-fail: known failure at step "a pick-up date with no time is stored at midnight and shows as the date alone on both pages" (finding 306) |
| `rx-search-keyboard` | known-fail | known-fail: known failure at step "a search with no match offers no row that can be picked: ArrowDown, Enter and a click post nothing" (finding 307) |
| `rx-favourite-dose-roundtrip` | known-fail | known-fail: known failure at step "the edited favourite PRN flag survives staging on the prescribing card" (finding 295) |
| `rx-favourite-multiline-directions` | known-fail | known-fail: known failure at step "multiline favourite directions retain their line break on the prescribing card" (finding 292) |

Final health, server-error audit, fixture counts and machine-readable selection results are included in the validation evidence archive. Server-error signatures are reported separately from browser passes and are not silently suppressed.

The final package health check reports **All checks passed**, and all eight
synthetic-fixture marker counts are zero. The independent current-boot
server-error audit **fails** with four previously unexplained signatures:
the two HTTP-500 signatures are finding 302, the invalid product-load signature
is finding 307, and the DrugRef missing-DIN signature is finding 308. These are
now documented defects; no server-error suppression was added. Previously
recorded findings 139 and 144 are also reconfirmed by the audit. Browser results
and clean fixtures do not supersede the server-error audit failure.

After the final Info assertion was added, all 84 manifest, issue-register and
server-log reader checks pass. The live Info reproduction reaches its exact
finding-308 assertion with no skip or unexpected failure. The earlier 152-check
static selection and its raw results are retained in the evidence archive.
