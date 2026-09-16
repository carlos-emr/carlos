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
- Revised full Java package build: **12,109 tests**, zero failures/errors, 51 skips.
- Revised script regressions: **614 passed** (baseline 593).
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
| `episode-lifecycle` | Empty-description refusal; create; automatic chart refresh; reopen/edit; complete; reactivate; soft delete retaining history | Pass; final package rerun pending |
| `diagnosis-flowsheet` | ICD9 250 search/selection; diagnosis; enabled `diab2` flowsheet; A1C save/refresh; resolve; cancel/accept deletion | Pass; final package rerun pending |
| `prevention-lifecycle` | Fluzone/Inf refusal with date/comments; completed amendment replaces and archives original; ineligible; reopen; soft delete | Pass; final package rerun pending |
| `allergy-custom-lifecycle` | Custom confirmation cancel/accept; non-drug details; onset/date/life-stage; amendment archives original; cancel/accept archive | Pass before additional field assertions; final rerun pending |
| `contact-lifecycle` | Punctuated name search; association; SDM/emergency/consent/note round-trip; cancel; update; remove association while preserving directory contact | Selection passes; corrected new-association ID awaits final package |
| `consultation-directory-crud` | Institution and department create/read/update/delete; cancel deletion; unselected control row survives | Pass; final package rerun pending |
| `measurement-history` | Dated values; real plot PNG bytes; selected-row deletion; unselected value survives | Pass before stronger date assertions; final rerun pending |

## Application blockers fixed here

| Problem | Fix and verification |
|---|---|
| SOAP interceptor by-type injection instantiates request actions during startup | Use annotated injection; executable Spring regression fails on the release definition and passes after the change; packaged app starts. |
| Legacy NULL clinic location breaks Messenger hydration | Nullable `GroupMembers` field with existing zero-valued getter contract; unit regressions and live compose/inbox actions pass. Regenerated import metadata matches the model. |
| Contact search handler/JSON breaks with punctuation | Encode the complete handler for its HTML attribute and serialize with `JSON.stringify`; executable original serializer fails, corrected serializer and live quoted-name selection pass. |
| New contact association sends a blank integer ID | Initialize the two association templates to ID zero; final package retest pending. |
| Numeric measurement history has no Plot control | Test the first row's `canPlot` inside a nonempty collection; real graph response has PNG bytes. |
| Missing optional clinic vaccine catalogue emits 404 | Fall back only for exact `vaccine-brands.json`, after existing access/path checks. Clinic override wins; unrelated missing files remain 404. Java and live prevention tests pass. |
| Native-document/calendar requests to host `/favicon.ico` return 404 | Exact nginx redirect to the existing application icon; final package routing retest pending. |

## Tests tested

- Executable event-order regressions fail when popup wiring is moved back after
  the click, proving that first-document JavaScript errors cannot disappear.
- A legacy-dialog mutation fails its regression; deliberately handled legacy
  confirmations retain their existing contract while strict wiring records all.
- Three live episode mutations each fail as intended: a success-looking response
  without a database write, a successful save without opener refresh, and an
  unexpected startup JavaScript exception. The unmodified positive control passes.
- Cleanup tests prove fixture ownership, child-before-parent ordering, recovery
  on failure, and detection of a silently ineffective patient delete. A live
  cleanup audit found chart note locks; cleanup now removes only locks belonging
  to its owned patient. Final post-run cleanup audit pending.
- Shared audit tests reject blank/error HTML and invalid PDFs (status, MIME,
  signature and EOF). Real Chromium probes cover nested menus, hover entries and
  iframe destinations. Native PDFs are validated as PDFs, not accepted as blank
  HTML. Failed popup validation closes the popup without closing its host tab.
- Five before/after event-race probes reproduce stale listeners in the original
  helpers and prove zero listeners remain after navigation/download success or
  click failure. Popup-only, popup/navigation and popup/download waits now all
  dispose their temporary listeners.
- The runner rejects unknown `--only` and `--skip` names, including mixed
  valid/misspelled selections, before launching a browser.
- Demographic audit waits for its asynchronous read before closing the window;
  both success and failure paths have regressions. The live five-field edit and
  audit-row assertions pass using an exact patient fixture.

## Remaining findings and coverage limits

The aggregate issue contains the full reproduction/status list. Confirmed open
findings include demographic PDF label/address/chart failures and envelope 404;
three anonymous routes returning HTTP 200 with an empty body; no calculator entry
in the current chart; Row Display's absent CSRF input; provider-preference errors;
and an Inbox HRM row present under All but absent from New/Acknowledged/Filed.
The anonymous empty responses do not establish patient-data disclosure.

Source review also found candidate episode validation/authorization and
scratchpad version-ownership gaps; low-privilege live exploitation was not tested.
The existing upstream [DrugRef issue #13](https://github.com/carlos-emr/drugref2026/issues/13)
remains open. Unit-test success does not make these application findings green.

Retests pass for Messenger/inbox actions, allergy/Rx alerts, encounter timer and
save/sign/billing handoff, demographic edits/audits, patient-list exports,
consultation links, Messenger links, scratchpad form presence, and protected
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
compression succeeded, and no partial package was installed. The final package
and live retest evidence will be recorded before this PR leaves draft. Final
validation is currently paused: LXD stalled during VM shutdown (including forced
stop/cancellation), with the guest agent offline. No compilation was started
while the VM process remained running. Host administrator intervention is needed
to stop that process before rebuilding and resuming validation.
