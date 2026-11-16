# HRM mutation deployment and validation

The HRM viewer and `/hospitalReportManager/Modify` are one versioned application contract.
Deploy the WAR's Java classes, JSPs and JavaScript together. The endpoint accepts POST and
returns `application/json` with `success`, `message`, and, for sign-off, `clearedCount`.
The latter counts committed routing-row transitions. Inboxhub counts distinct HRM documents,
so a positive transition removes one HRM from its badge even when duplicate routing rows exist.
Revoking reloads authoritative totals and carries the category selection, active type badge,
acknowledgement toggle and Rapid Review state through that request. The page validates and
restores this display state before fetching the new results.

## Patient access

The module right (`_hrm`) lets a provider work with hospital reports; it does not decide for one
patient. A report filed to a patient is refused whenever that patient's chart would be: a
demographic or chart lock (`_demographic$N` or `_eChart$N` with no rights), no `_demographic` read
for the patient, or no `_hrm` read for the patient. `HRMReportAccess` is the one place that
decision is written, and every HRM route asks it before a report row, file or patient is read:

| Route or reader | When the patient is refused |
|---|---|
| `hospitalReportManager/Display` (and `displayHRMReport.jsp` itself, which the inbox includes) | 403 security page; inside the inbox, one line of text in place of the report |
| `hospitalReportManager/ViewDocList`, `PrintHRMReport`, `HRMDownloadFile` | 403 security page; a print request naming several reports is refused whole |
| `hospitalReportManager/Modify` | 403 with the endpoint's JSON failure body; nothing is written. This includes `removeDemographic`, which would otherwise unlink the patient and leave the report readable |
| `hospitalReportManager/hrm` (the report list) and `saveCategory` | The row is left out of the page (the totals still count it); the category is not changed |
| `HRMUtil.listHRMDocuments` (chart HRM panel, attachment pickers), `getHRMDocumentById`, `renderHRM`, `HRMPDFCreator` | An empty list, or `SecurityException` |
| `HRMResultsData` (the provider inbox feed) | The row is left out. The inbox never required `_hrm` to list a routed report, so here only the lock and `_demographic` read are applied |

A report filed to no patient is governed by the module right alone, as before, so unmatched
reports can still be opened and matched. Lists evaluate read-only (`listFilter`); opening one
report uses the ordinary privilege check.

## Upgrade and rollback

The Debian package stops the application before replacing its WAR contents and starts it after
installation. Reload already-open Inboxhub, legacy inbox and HRM viewer pages after an upgrade
or rollback; their loaded handlers and acknowledgement bookkeeping belong to the previous version.

For multiple application nodes, drain existing sessions and complete the upgrade on all nodes
before admitting HRM traffic again. Do not route the new viewer to an old `Modify` endpoint or
serve assets from a different application release. Mixed-version rolling deployment of this
endpoint is unsupported: the old endpoint replies with `Success` or `Error encountered` as HTML,
possibly followed by response-decorator scripts, and provides no committed routing count.

The client deliberately does not infer success from arbitrary HTML or execute a response script.
If a successful HTTP reply cannot be parsed as JSON, the mutation may already have committed.
The status directs the clinician to refresh the report and inbox before retrying. Operators should
restore a consistent application version, then reload those pages. Refreshing reads authoritative
state; blindly retrying could add a duplicate comment.

## Validation

- `HRMModifyTransactionIntegrationTest` checks committed duplicate routing transitions, rollback
  after flushed database writes, subclass selection and patient-link replacement.
- `HRMReportAccessUnitTest` and the per-route tests beside it (`HRMDisplayReport2ActionUnitTest`,
  `ViewHRMDocList2ActionUnitTest`, `HRMDownloadFile2ActionPatientAccessUnitTest`,
  `PrintHRMReport2ActionUnitTest`, `HRM2ActionUnitTest`, `HRMModifyDocument2ActionUnitTest`,
  `HRMUtilPatientAccessUnitTest`, `HRMPDFCreatorPatientAccessUnitTest`, `HRMResultsDataUnitTest`)
  check the permitted, locked and no-patient cases. `scripts/hrm-report-print-download-playwright-checks.js`
  checks the list, print and download refusals on an installed package.
- `node scripts/hrm-report-actions.test.js` and `node scripts/inbox-acknowledge-in-place.test.js`
  check response handling, failure behavior, count reconciliation and revocation.
- `CHROME_BIN=/path/to/chrome node scripts/hrm-window-playwright-checks.js` uses a loopback fixture
  with the real shipped handlers, jQuery, AJAX, BroadcastChannel and browser window lifecycle.
  It covers popup, COOP, direct fallback, iframe, legacy inline, revoke/re-sign and failure flows,
  one browser context at a time. It needs Playwright installed.
- Installed-package validation must additionally exercise the actual viewer through HTTPS,
  CSRFGuard, response filters and the database. Use a disposable synthetic report, including
  duplicate routing rows and a second unsigned report whose badge entry must remain intact.
