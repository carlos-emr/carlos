# Clinical-workflow browser checks

Five `scripts/*-playwright-checks.js` scripts that cover clinical work the rest of
the suite leaves untouched. They are part of the standard pass described in
[deb-install-validation.md](deb-install-validation.md) and run the same way as
every other check there.

| Check | Covers | The check next to it |
|---|---|---|
| `appointment-lifecycle` | Edit, advance status from the day sheet, cancel, delete an appointment (+ the `appointmentArchive` row) | `schedule-quick-search-appointment` and `echart-new-patient-notes` cover **booking**; nothing covered what happens to a booking afterwards |
| `messenger-inbox-actions` | Mark read / unread, search and clear, archive, unarchive, and the archived box | `messenger` covers composing, sending from the messenger and the chart, and that **opening** a message marks it read |
| `lab-acknowledge` | Acknowledge a result (`oscarMDS/UpdateStatus`), the lab PDF, cumulative values | `lab-macro-tickler` covers raising a tickler from a lab macro |
| `prevention-recall-report` | Run the prevention recall report for a screening type | `prevention-brand-picker` covers recording an immunization on one chart |
| `measurement-validation` | A bad vital is **refused** and writes nothing; a good one through the same form still saves | `echart-vitals-bmi` covers the happy path on the same popup |

## The rule these follow: reach it the way a user reaches it

Every surface is entered by clicking from where the user already is — the schedule's
top nav, the day sheet, the Inboxhub list, the report index, the inbox's own Compose
link. None of them is entered with a hand-built URL to the target page. That is not
a style preference; each of the following was found by following the link instead of
typing the address:

- **The messenger compose page cannot be opened directly.** `CreateMessage.jsp`
  redirects to `/index` when the session carries no `msgSessionBean`, and `/index`
  renders the **login page**. A check that navigated straight to
  `/messenger/ViewCreateMessage` would have been asserting against a login form and
  passing.
- **The Inboxhub row's link carries context a URL does not.** It appends
  `providerNo`, `searchProviderNo`, `status`, `demoName` **and `showLatest=true`** to
  the lab URL, and the acknowledge gate reads the provider context. Navigating to
  `ViewLabDisplay` directly exercises a shape no operator ever produces — and it
  hides the one thing that matters most about this route: on `showLatest=true`
  `labDisplay.jsp:319` **replaces the requested segment** with the newest lab sharing
  its accession, so the page that opens is not the segment the row named. The demo
  dataset has one accession with 31 versions, so clicking the row for segment 1
  renders segment 162. The check therefore routes the *newest* segment of a chain and
  asserts the rendered acknowledge form belongs to the segment it routed; a check
  written against the requested id acknowledges a lab that was never in the inbox.
- **The day sheet needs its full day-search parameter set.**
  `providercontrol?year=&month=&day=` alone answers HTTP **200 with an empty
  document** — no error, no redirect, a blank page. Only the parameter set the
  post-login landing page uses actually renders a schedule.
- **The measurement entry form is opened by its group.** The eChart's Measurements
  module opens `SetupMeasurements` for a measurement group and saves through
  `encounter/Measurements?ajax=true`. Reaching the older `ViewAddMeasurementData`
  form directly answers HTTP 500 unless a valid `template` is supplied, because
  `MeasurementTemplateFlowSheetConfig.getFlowSheet()` has no null or unknown-name
  guard — a route no UI path reaches, and a defect worth fixing rather than
  working around in a check.
- **The prevention recall report has exactly one UI entry**, the Preventions link on
  the report index. Driving it from there is what notices the link disappearing.

A corollary: if a route has **no** UI entry, it does not get a check.
`prevention/printPrevention` is referenced by no JSP in the tree, so it is left
alone rather than covered through an address only a test would know.

## What they assert, and why that shape

**The database is the assertion; the page is the path.** Each check drives the UI
and then reads the rows that reached MariaDB. A page that shows a success banner and
writes nothing is the failure these workflows actually have, and it is invisible to
a check that only reads the page.

**Both halves of an archive.** An appointment delete writes `appointmentArchive`
then removes the `appointment` row; an archive in the messenger flips the
per-recipient status. Each is asserted on both sides, because "gone from the list" is
equally true of a working archive and of one that dropped the record.

**Bulk actions are pinned to the selected row.** A bulk action's failure mode is not
"nothing happened" but "it happened to the wrong rows", so
`messenger-inbox-actions` inspects the POST body as well as the resulting status,
and reads the per-recipient `messagelisttbl` row — the row the inbox renders. A
status written to the content row instead would look right in the database and
change nothing an operator sees.

**A known defect is tolerated by origin, never by message.** `lab-acknowledge`
allows exactly one console error — the `Failed to fetch` that `oscarMDSIndex.js`
`updateDocStatusInQueue` raises when the acknowledge closes its own window mid-fetch
— and matches it on file, function and message together, so any other failure in the
same file still fails the check. The underlying defect (that same call posts a **lab**
segment id as a **document** id) is recorded in the script header and in the findings,
not asserted as correct.

**A refusal is proven against a matching acceptance.** `measurement-validation`
asserts a bad value is refused AND that a good value through the same form still
saves. Without the second half, a save that is broken for every input would pass as
"correctly rejected".

## Conventions

These use `scripts/eform-local-playwright-utils.js`, which is the suite's shared
harness despite the eForm name: `validateBaseUrl`, `gotoApp`, `login`, `wirePage`,
the recorder, `assertNoPageErrors`, `buildFailureDetails`. Two things about it are
worth knowing before writing another check:

- **`wirePage(page, label, recorder, dialogHandler)` takes the dialog handler.**
  There is one dialog listener per page and the fourth argument decides what it
  does. Do not add a second `page.on('dialog')`: Playwright delivers a dialog to
  every listener, so a second one that accepts races the default dismiss and
  whichever loses throws "already handled" — which shows up as a delete that never
  posts.
- **`mysql -B` escapes its output.** Backslashes, tabs and newlines come back
  escaped, so a column holding `a\_b` reads as `a\\_b`. Normalise before comparing
  text that can contain any of them.

Each check keeps its own `sql()` helper over a 0600 defaults file, as the other
data-asserting checks in the suite do, so the MySQL password never reaches a command
line.

## Adding a sixth

1. **Check what the suite already covers**, per route and not per module — subtract
   the routes existing checks actually navigate to, not the ones their names suggest.
2. **Enter from where the user is.** If the only way to reach the surface is a URL,
   that is a finding about the surface, not a licence to type the URL.
3. **Assert the database.** If the check would still pass when the save wrote
   nothing, it is not covering the workflow.
4. **Clean up in a `finally`, keyed on a per-run marker**, so a failed run leaves
   the deployment as it found it.
5. **Report, don't encode, a defect.** A check that pins current broken behaviour as
   expected makes the bug permanent.
