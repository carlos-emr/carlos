# HRM mutation deployment and validation

The HRM viewer and `/hospitalReportManager/Modify` are one versioned application contract.
Deploy the WAR's Java classes, JSPs and JavaScript together. The endpoint accepts POST and
returns `application/json` with `success`, `message`, and, for sign-off, `clearedCount`.
The latter counts committed routing-row transitions. Inboxhub counts distinct HRM documents,
so a positive transition removes one HRM from its badge even when duplicate routing rows exist.
Revoking reloads authoritative totals and carries the category selection, active type badge,
acknowledgement toggle and Rapid Review state through that request. The page validates and
restores this display state before fetching the new results.

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
- `node scripts/hrm-report-actions.test.js` and `node scripts/inbox-acknowledge-in-place.test.js`
  check response handling, failure behavior, count reconciliation and revocation.
- `CHROME_BIN=/path/to/chrome node scripts/hrm-window-playwright-checks.js` uses a loopback fixture
  with the real shipped handlers, jQuery, AJAX, BroadcastChannel and browser window lifecycle.
  It covers popup, COOP, direct fallback, iframe, legacy inline, revoke/re-sign and failure flows,
  one browser context at a time. It needs Playwright installed.
- Installed-package validation must additionally exercise the actual viewer through HTTPS,
  CSRFGuard, response filters and the database. Use a disposable synthetic report, including
  duplicate routing rows and a second unsigned report whose badge entry must remain intact.
