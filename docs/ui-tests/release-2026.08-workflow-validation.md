# Release 2026.08 workflow validation

PR [#3683](https://github.com/carlos-emr/carlos/pull/3683) starts from
`origin/release/2026.08` at `fcb14db3c0bf568d10481dce144af526f80e4f4b` and targets
that release branch. All findings are tracked in
[issue #3682](https://github.com/carlos-emr/carlos/issues/3682). No develop merge,
Flyway migration, or clinical schema change is included.

This pass follows the existing [coverage plan](playwright-coverage-plan-2026.08.md),
manifest and strict browser harness. A page opening does not prove its save,
cancellation, archive or opener refresh works. The manifest now registers 108
checks across 99 scripts; registration does not mean every fixture is available.

## Environment and baseline

Ubuntu 26.04 VM; 6 GiB RAM, two CPUs, 2 GiB Java heap; packaged Chromium
154.0.8025.0. An isolated fresh `release_ui_202608` database uses the release's 23
successful Flyway migrations. Demo import completed without duplicate keys.
Reinstall applied zero migrations and preserved the database. Existing databases
with newer develop migrations were not reused or modified.

- Baseline Java package build: 12,103 tests, zero failures/errors, 51 skips.
- Full local Java package build before the final type-preservation follow-up: **12,134 tests**, zero failures/errors, 51 skips.
  A discovery audit found the legacy `Contact2ActionTest` filename excluded by
  Surefire. Renamed it `Contact2ActionUnitTest`; its **25 cases** pass in both the
  focused run and the default full suite, verified in the Surefire XML. Earlier
  successful CI did not execute that legacy filename. The subsequent type-preservation
  follow-up expands the focused suite to **30 passing cases**; final-head CI runs
  the normal suite again.
- Revised script regressions: **635 passed** (baseline 593).
- Revised package-management Python tests: **1,519 passed**. Generated O19
  primitive-column metadata was regenerated from pinned upstream commit
  `a7900d569d3faf741993e5e1da8c14021bbefede` after the nullable model fix; the
  generator's drift check passes for both profiles.
- Initial broad live sweep: **100 checks: 75 passed, 21 failed, 4 skipped**.
  Harness fixes were made during this sweep; this is not an immutable baseline
  comparison. Focused retests below supersede individual initial failures.

JaCoCo reports `MethodTooLargeException` for third-party Drools lexers; Java tests
still pass. This is an instrumentation limitation, not a passed clinical test.
No browser or console failure was added to an allowlist to make this PR pass.

## New workflow coverage

Each patient workflow creates an owned `FAKE-PW` patient, navigates Schedule →
Search → Master Record → Chart, uses visible controls for mutations, checks
persisted rows and visible results, and removes its owned clinical rows and
patient. Unexpected browser errors fail the check. SQL seeds fixtures, verifies
persistence and performs cleanup; it does not substitute for the UI action under
test. The directory workflow owns marked institution/department rows instead.
Cleanup failures fail the check and retain the patient for recovery. Clinical
audit logs are intentionally retained.

| Check | Assertions | Latest live result |
|---|---|---|
| `episode-lifecycle` | Empty-description refusal; create; automatic chart refresh; reopen/edit; complete; reactivate; soft delete retaining history | Pass on final installed package |
| `diagnosis-flowsheet` | ICD9 250 search/selection; diagnosis; enabled `diab2` flowsheet; A1C save/refresh; resolve; cancel/accept deletion | Pass on final installed package |
| `prevention-lifecycle` | Fluzone/Inf refusal with date/comments; completed amendment replaces and archives original; ineligible; reopen; soft delete | Pass on final installed package |
| `allergy-custom-lifecycle` | Custom confirmation cancel/accept; non-drug details; onset/date/life-stage; amendment archives original; cancel/accept archive | Pass, including all field assertions |
| `contact-lifecycle` | Punctuated name search; personal flags/note round-trip; cancel/update/remove; professional consent/status round-trip; directory preserved; existing internal relationship creates a separate correctly typed reverse row with no unrequested SDM/EC flags | Pass, including all six steps and duplicate prevention on resave |
| `consultation-directory-crud` | Institution and department create/read/update/delete; cancel deletion; unselected control row survives | Pass on final installed package |
| `measurement-history` | Dated values; real plot PNG bytes; selected-row deletion; unselected value survives | Pass, including dates, archive preservation and PNG bytes |

## Application blockers fixed here

| Problem | Fix and verification |
|---|---|
| SOAP interceptor by-type injection instantiates request actions during startup | Use annotated injection; executable Spring regression fails on the release definition and passes after the change; packaged app starts. |
| Legacy NULL clinic location breaks Messenger hydration | Nullable `GroupMembers` field with existing zero-valued getter contract; unit regressions and live compose/inbox actions pass. Regenerated import metadata matches the model. |
| Contact search handler/JSON breaks with punctuation | Encode the complete handler for its HTML attribute and serialize with `JSON.stringify`; executable original serializer fails, corrected serializer and live quoted-name selection pass. |
| New contact association sends a blank integer ID; removal also fails | Initialize both templates to zero, ignore unsaved zero IDs during deletion, and collect typed association rows before deletion. The 30-case contact suite passes; installed-package workflow passes. |
| Contact saves can reassign another patient's association; reciprocal edits can move the original row | Prevalidate both categories and removals before writes, check both patients for reciprocal writes, and create a distinct reverse row. Negative and successful reciprocal Java regressions pass. |
| Professional consent/status silently defaults to true | Read the professional field prefix. Regression submits opposing personal values; professional false values persist. Installed-package round-trip passes. |
| Personal/professional row controls have duplicate IDs | Give personal fields their own prefix and label SDM/emergency/note controls; installed-package uniqueness assertion passes. |
| Measurement Plot query embeds unencoded type in JavaScript | Encode the type as a URI component and then for its JavaScript attribute. |
| Numeric measurement history has no Plot control | Test the first row's `canPlot` inside a nonempty collection; real graph response has PNG bytes. |
| Missing optional clinic vaccine catalogue emits 404 | Fall back only for exact `vaccine-brands.json`, after existing access/path checks. Clinic override wins; unrelated missing files remain 404. Java and live prevention tests pass. |
| Native-document/calendar requests to host `/favicon.ico` return 404 | Exact nginx redirect to the existing application icon; installed route returns 302 to the application icon, then HTTP 200 with 6,822 bytes. |

## Second review follow-up

Two later review comments exposed reciprocal-contact authorization errors:
checking related-patient write access when no reverse row would be written, and
missing that check when the form omitted its disabled type selector. Validation
now resolves the effective persisted type, plans reverse writes before any
mutation, and checks target access only for those writes. The execution path
uses that authorized plan, rechecks existence to avoid duplicates in one save,
and explicitly assigns internal-patient type. Reciprocal creation does not grant
SDM or emergency-contact status; those flags require an explicit selection.

The expanded action suite reproduced five failures against the prior revision
(three assertion failures and two inappropriate access denials). All 25 cases
then passed in the normal 12,134-test suite. Additional cases cover numeric type
`01`, an omitted type, existing reverse links, unmapped roles, no partial writes,
and correct reciprocal type/flags. Shared field-name constants, distinct local
association names and a smaller planning helper address the static-analysis
findings without changing request parameter names.

The contact browser workflow also edits a seeded existing internal relationship,
checks the original row stays owned by its patient, verifies the reverse type,
role and flags, then reopens/saves and checks for duplicates. The related patient
and associations have separate ownership and cleanup assertions. The initial
related fixture name exceeded the 30-character surname column and the ownership
check correctly refused deletion. After recovering only the two verified
synthetic records, the suffix was shortened. Repeating the negative browser run
on the old package fails only on the intended reciprocal type/flag assertion;
cleanup succeeds. An audit of those four owned patients finds only 16 audit rows. Internal patient
search is disabled in the release's `ManageContacts.jsp`; this fixture does not
claim that search works. A live UI click confirms the unavailable alert; it is tracked independently in #3682.
The corrected package passes all seven workflows again, including this sixth
contact step. All 14 login checks pass with independent credential/reset-field
restoration verification. The second-review cleanup audit covers 12 newly owned
patients and finds only 49 intentionally retained audit-log rows; directory
cleanup also passes. Temporary episode grants were restored and the VM was
stopped normally. Host/guest minimum available memory was 13.73/1.52 GiB, with
zero swap use, zero full memory PSI and no guard intervention.

## Existing contact type preservation

A final review identified a classification-consistency defect: an existing
association's disabled type selector could be overridden with a crafted POST.
Planning and persistence agreed on that submitted type, so this was not an
unauthorized write to the related patient; nevertheless it could convert the
existing relationship and leave inconsistent reciprocal state. Existing rows
now retain their stored type for both planning and persistence. New rows still
use the submitted type. Reverse-role calculation reads the already-authorized
source patient; the helper parameter is renamed to make that explicit.

Five additional Java cases fail on the preceding implementation (three failures,
two errors), then all 30 pass after the fix. They cover internal-to-external and
external-to-internal submissions, denial before any mutation, correctly typed
reciprocal creation, and professional-row types including malformed submissions.

A controlled VM probe changes only the submitted type for the workflow's owned
internal association. The old installed package fails the original-row type
assertion after one tampered request, with successful fixture cleanup. The new
package passes all six ordinary contact steps and the probe, including two
tampered saves and the reciprocal duplicate check. Installed class/JSP hashes
match the tested build. Reinstall applies zero migrations and preserves the
release database's 23 successful migrations and demo data. The final audit covers
six owned patients and finds only 26 intentionally retained audit rows. This is
separate from the earlier 12-patient/49-row audit. Health checks pass; the VM is
stopped and its original mounts restored. Memory guards recorded no swap use,
full memory pressure, or interventions.

## Tests tested

- The new reciprocal-contact browser step fails on the previously installed
  package: the save returns normally with type `0`, SDM `true` and emergency
  contact `true`. It checks type `1` and empty flags on the corrected package.
- Executable event-order regressions fail when popup wiring is moved back after
  the click, proving that first-document JavaScript errors cannot disappear.
- A legacy-dialog mutation fails its regression; deliberately handled legacy
  confirmations retain their existing contract while strict wiring records all.
- Three live episode mutations each fail as intended: a success-looking response
  without a database write, a successful save without opener refresh, and an
  unexpected startup JavaScript exception. The unmodified positive control passes.
- Cleanup tests prove fixture ownership, child-before-parent ordering, recovery
  on failure, and detection of a silently ineffective patient delete. A live
  cleanup audits found note locks, one chart autosave and one archived
  measurement. Cleanup now removes and verifies these owned support rows before
  deleting the patient; a silent child-delete failure retains its parent and
  fails. After the first correction, all seven workflows pass again. That earlier audit
  of **12 owned patients** found only **60 retained audit-log rows**, with
  no clinical/support rows remaining. Directory cleanup also verifies zero rows.
- Shared audit tests reject blank/error HTML and invalid PDFs (status, MIME,
  signature and EOF). Real Chromium probes cover nested menus, hover entries and
  iframe destinations. Native PDFs are validated as PDFs, not accepted as blank
  HTML. Failed popup validation closes the popup without closing its host tab.
- Six additional regressions reproduce popups opened before a click rejects;
  the helpers now close those abandoned popups and preserve the original error,
  even if closing also fails. A same-tab control confirms its host stays open.
- Five before/after event-race probes reproduce stale listeners in the original
  helpers and prove zero listeners remain after navigation/download success or
  click failure. Popup-only, popup/navigation and popup/download waits now all
  dispose their temporary listeners.
- Three actual Chromium probes confirm popup cleanup when a click opens a
  window and subsequently rejects. Plain links to the current document are
  explicitly skipped, never counted as destinations; action handlers, popups
  and links with different query parameters remain covered.
- The runner rejects unknown `--only` and `--skip` names, including mixed
  valid/misspelled selections, before launching a browser.
- Demographic audit waits for its asynchronous read before closing the window;
  both success and failure paths have regressions. The live five-field edit and
  audit-row assertions pass using an exact patient fixture.

The final suppressed-comment review also reproduced a harness cleanup defect:
if `browser.close()` threw, successful child cleanup still left the owned patient
behind. Cleanup now separates browser errors from child-delete success; it removes
the verified parent and still reports the browser error. The new regression fails
before the fix and passes afterward; a second regression verifies that child-delete
failure still retains the parent. All 635 Node tests pass after the final audit-assertion follow-up.

The next review identified two additional false-pass gaps. A missing required
administration panel now fails instead of reading shell text, while a full
navigation to another document still validates that destination. Hash-only changes
cannot bypass the panel requirement. Prevention creation and replacement now read
all matching active rows and require exactly one; the scalar SQL helper otherwise
returns only the first row. New cases cover missing/duplicate/invalid prevention
rows and missing-panel, hash-navigation, full-navigation and error-page behavior.

Two other suggested test changes were not needed. The SOAP regression already
fails with the old by-type XML: Spring's `resolveMultipleBeanMap` instantiates the
Object-valued prototype while populating `Map<String,Object>`. Replacing it with a
Map bean would change the scenario. Popup saves synchronously call `isClosed()`
and install the close listener without an intervening await; an already processed
close is detected, and an event cannot interleave those synchronous operations.

## Remaining findings and coverage limits

The aggregate issue contains the full reproduction/status list, including the
separate administration audit and final account-restoration checks. Confirmed open
findings include demographic PDF label/address/chart failures and envelope 404;
Client Lab Label returning HTTP 200 with PDF MIME but **zero bytes** (the server
catches a Jasper `queryString` deserialization exception);
three anonymous routes returning HTTP 200 with an empty body; no calculator entry
in the current chart; Row Display's absent CSRF input; provider-preference errors;
and an Inbox HRM row present under All but absent from New/Acknowledged/Filed.
The anonymous empty responses do not establish patient-data disclosure.

Contact removal now requires POST, patient write permission and matching
association ownership; a mixed-owner selection is rejected before any deletion.
The 30 Java cases and installed-package contact workflows pass. Cross-patient
rejection is verified at the Java action boundary; no low-privilege VM exploit
is claimed.
Source review also found candidate episode validation/authorization and
scratchpad version-ownership gaps; low-privilege live exploitation was not tested.
A reciprocal-contact lookup also lacks category/type predicates: a coincident
directory/provider numeric ID could suppress a reverse relationship. This is a
source-review candidate in #3682, not a reproduced VM failure. The existing
`saveManage()` numeric parsing also precedes authorization and may turn malformed
input into an uncontrolled error; no write occurs before permission checks.
That inherited error-handling candidate is recorded separately, not VM-reproduced.
The existing upstream [DrugRef issue #13](https://github.com/carlos-emr/drugref2026/issues/13)
remains open. Unit-test success does not make these application findings green.

Retests pass for Messenger/inbox actions, allergy/Rx alerts, encounter timer and
save/sign/billing handoff, demographic edits/audits, patient-list exports,
consultation links, Messenger links, scratchpad form presence, eDoc upload/delete/
restore and all 17 document destinations, calendar/month navigation, schedule
links, front-door Host-header handling, and protected
eForm fax preview/cancel. The first encounter blank-page failure did not recur;
its original cause remains unproven. Scratchpad form presence is not CRUD coverage.

Unavailable coverage is explicit: O19 migrated smoke requires a separate imported
fixture and break-glass login; the fresh demo is not such a fixture. eForm corpus,
referral/workflow menu properties and next-appointment fixtures were unavailable.
Some optional paths also lack chart-number/phone, unbilled appointment or eForm
signature fixtures. No success is claimed for those paths or external sends.

## Running and reproducing

Use a disposable seeded Ontario deployment and
[the package validation runbook](deb-install-validation.md). Complete first-login
password reset. `BASE_URL` and `MYSQL_DATABASE` must identify the same deployment.
The provider needs chart/admin access plus `_episode` and `_newCasemgmt.episode`;
the demo doctor's episode grants are disabled by default. Record fixture-only
permission changes. Use a narrow demographic search matching its configured ID.
Patient-list export uses `local-seed-obec-report-v1`; fax preview uses a local fake
sender with polling disabled, and sends nothing.

```sh
npm run test:scripts
PYTHONPATH=debian/assets python3 -m unittest discover -s debian/assets/carlos_ctl/tests
node scripts/run-playwright-suite.js --province ON \
  --only episode-lifecycle --only diagnosis-flowsheet \
  --only prevention-lifecycle --only allergy-custom-lifecycle \
  --only contact-lifecycle --only consultation-directory-crud \
  --only measurement-history --junit workflow-results.xml
```

Run checks sequentially. The runner reports failures and writes JUnit; exit 1
means failure. Run the account-mutating login check last and verify restoration.
Preserve a failing marker and log for diagnosis; never delete all `FAKE-` demo
records to clean up one run. Private captures and credentials must not be posted
to the PR or issue.

Host compilation occurs only with the VM stopped, with a 5 GiB memory cap and one
Surefire fork. Host/guest memory guards protect the sequential browser runs.
Default package compression exceeded the temporary-file quota; sequential zstd
compression succeeded, and no partial package was installed. The earlier
seven-workflow and 14-login validation used runtime `73ce9d8d95`; the intervening
suffix-constant refactor was verified by normalized `javap` to preserve executable
instructions. After the type-preservation fix, the WAR and DEB were rebuilt and
the affected contact workflows were rerun as described above. The unaffected
workflows were not repeated on this last artifact.

The final main package `carlos-emr_2026.09.0~snapshot23_all.deb` reports application
version `2026.08.0-alpha13-SNAPSHOT`, with SHA-256
`537a1eb86d06495b57bf226ed36c0e0296743da25db49a5a4278a9a815828d27`.
Installed contact class/JSP and measurement JSP hashes match the tested build.
`carlos-ctl check` passes; reinstall applies zero migrations and preserves demo
records. The host recovery/shutdown interruption is resolved. Final detailed
outcomes and retained open defects are recorded in issue #3682.
