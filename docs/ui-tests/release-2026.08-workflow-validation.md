# Release 2026.08 workflow validation

This pass starts from origin/release/2026.08 at
`fcb14db3c0bf568d10481dce144af526f80e4f4b`. It uses the existing
[coverage plan](playwright-coverage-plan-2026.08.md), manifest and strict browser
harness. A page opening is not evidence that its save, cancellation, archive or
opener refresh works.

## Baseline

- Java: 12,103 tests, zero failures/errors, 51 skips (`mvn package`, one test fork).
- Script regressions: 593 tests passed. The first sandboxed attempt could not
  spawn subprocesses; the unrestricted rerun passed without source changes.
- Package management Python: 1,519 tests passed.
- Live browser validation and new workflow results: in progress.

## Confirmed findings

| ID | Defect | Reproduction / evidence | Disposition |
|---|---|---|---|
| RUI-01 | SOAP interceptor by-type wiring initializes unrelated request actions during startup | `AuthenticationInterceptorWiringUnitTest` fails on the release definition with `UnsatisfiedDependencyException` on inherited `properties`; the request-only bean is instantiated outside an HTTP request | Apply the small annotated-injection fix already on develop, with its executable Spring regression test. Required to start release validation. |
| RUI-02 | Existing administration/anonymous audits assume every schedule link opens a popup | Live baseline times out awaiting `page`; `openScheduleSection()` navigates the current page in focused mode | Use the shared popup-or-navigation helper and return through browser history before the next surface. Live retest pending. |
| RUI-03 | Shared popup-or-navigation helper silently misses first-document JavaScript errors | A deterministic event-order regression test emits `pageerror` before the opener click resolves; the old helper reports no error | Wire the popup in the page-event continuation. Regression test fails before and passes after the change. |

| RUI-04 | Legacy dialog adapter records expected custom confirmations as failures | `allergy-rx-alert` finishes its clinical assertions but fails on two deliberately handled custom-allergy confirmations | Restore the legacy custom handler's ownership of recording. Strict wiring still retains every dialog. Isolated mutation fails the executable regression; 55 harness/helper tests pass after the fix. |
| RUI-05 | Calculator check expects a title attribute absent from the chart's text link | `clinical-calculators` fails before opening a calculator although `ViewCalculators` is offered | Select the actual UI control. Live retest pending. |
| RUI-06 | Demographic label printing fails | PDF Label and PDF Address Label return HTTP 500; server reports Jasper `queryString` deserialization failure. PDF Envelope returns 404. | Application defect; does not block unrelated workflows. |
| RUI-07 | Chart save/sign/bill check receives a blank save result | Existing `echart-note-sign-bill` fails at `echart-save rendered a blank page` | Further triage required; retain failure. |
| RUI-08 | eForm fax preview lacks an active sender fixture | Existing saved-render check times out clicking a disabled `remoteFaxButton` titled `No active fax senders` | Fixture limitation; do not count fax preview as validated. |

The baseline also prints JaCoCo `MethodTooLargeException` for third-party Drools
lexers. Tests continue and pass; this is instrumentation noise, not evidence of
an application workflow failure.

## New coverage

Each patient workflow creates an owned `FAKE-PW` patient, navigates from Schedule →
Search → Master Record → Chart, uses visible controls for mutations, checks both
persisted rows and visible results, and removes its owned clinical rows and
patient in cleanup. Unexpected browser errors fail the check. No clinical action
is invoked by calling a JavaScript handler or posting around the UI. The consultation
directory check owns marked institution/department rows instead of a patient.
If child cleanup fails, the patient is retained for recovery and the check fails.

| Check | Planned assertions | Live result |
|---|---|---|
| `episode-lifecycle` | Empty-description refusal; create; opener refresh; reopen/edit; completion; reactivation; soft delete retaining history | Pending |
| `diagnosis-flowsheet` | Code search/selection; add; diagnosis-triggered flowsheet; measurement save and refresh; resolve; cancelled and accepted delete | Pending |
| `prevention-lifecycle` | Refusal; date/comments; completed correction; ineligible status; reopen; soft delete | Pending |
| `allergy-custom-lifecycle` | Custom confirmation cancellation/acceptance; non-drug details; amendment archives original; cancelled/accepted archive | Pending |
| `contact-lifecycle` | Search and associate external contact; SDM/emergency/consent/note round-trip; cancelled edits; update; remove association without deleting directory contact | Pending |
| `consultation-directory-crud` | Institution and department create/read/update/delete; cancelled deletion; unselected control row survives | Pending |
| `measurement-history` | Dated values; plot's PNG bytes; selected-row deletion; unselected value survives | Pending |

## Environment discipline

Use an isolated release database, never the VM's develop database with newer
Flyway migrations. Compile only while the VM is stopped. Host builds use a 5 GiB
memory cap and one Surefire fork. VM validation uses 6 GiB guest RAM and a 2 GiB
Java heap, with host/guest memory monitors and sequential browser checks.

External fax/email sends and paid integrations require dedicated test credentials;
missing fixtures must be reported as skips, not counted as passing coverage.

## Running the new workflows

Use a disposable, seeded Ontario deployment and the credentials/environment in
[the package validation runbook](deb-install-validation.md). Complete the first
login/password reset before testing. The configured provider needs access to the
chart and consultation directory administration. `BASE_URL` must identify that
same deployment, and `MYSQL_DATABASE` must identify its database.

```sh
npm run test:scripts
node scripts/run-playwright-suite.js --province ON \
  --only episode-lifecycle --only diagnosis-flowsheet \
  --only prevention-lifecycle --only allergy-custom-lifecycle \
  --only contact-lifecycle --only consultation-directory-crud \
  --only measurement-history --junit workflow-results.xml
```

Run one browser check at a time on a small VM. The runner reports each check and
writes JUnit; exit 1 means a failure, and missing required fixtures must not be
reported as success. These seven checks do not send email or fax, rebuild
DrugRef, or require a paid integration.

The patient checks use synthetic names beginning `FAKE-PW`, assert that the
opened chart belongs to their own fixture, and remove their owned records even
when an assertion fails. Directory rows have the same per-run prefix. A cleanup
failure is itself a failed check. Preserve the failing log and inspect the
specific marker before retrying; never delete every `FAKE-` demo record to clean
up a single run.
