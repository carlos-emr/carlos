# Clinical-workflow browser checks

Six `scripts/*-playwright-checks.js` scripts added in September 2026 for clinical
workflows that had **no** browser coverage, plus the shared harness they are built
on. They are part of the standard pass described in
[deb-install-validation.md](deb-install-validation.md) and run the same way as
every other check in that suite.

## Why these six

The suite before this pass was deep on eForms, login/logout, consultations and
prescriptions, and empty on the workflows a clinic spends most of its day in. The
selection was made by enumerating the Struts action surface per module and
subtracting the routes any existing check actually navigates to — not by guessing
which modules felt untested. The six largest clinical gaps were:

| Check | Workflow | Routes that had no coverage before |
|---|---|---|
| `appointment-crud-playwright-checks.js` | Book, edit, advance status, cancel, delete an appointment | `appointment/AddRecord`, `appointment/UpdateRecord`, `appointment/DeleteRecord`, `providercontrol?dboperation=updateapptstatus` |
| `allergy-crud-playwright-checks.js` | Record, amend and withdraw a patient allergy; prescriber warning | `rx/searchAllergy2`, `rx/addReaction2`, `rx/addAllergy2`, `rx/deleteAllergy2`, `rx/showAllergy?method=allergyData` |
| `prevention-immunization-playwright-checks.js` | Record an immunization, report and print it | `prevention/ViewPreventionIndex`, `prevention/ViewAddPreventionData`, `prevention/AddPrevention`, `prevention/PreventionReport`, `prevention/printPrevention` |
| `messenger-inbox-playwright-checks.js` | Provider-to-provider messaging, inbox lifecycle | the whole `messenger/**` module, plus `messenger` (administration) |
| `lab-results-review-playwright-checks.js` | Review and acknowledge a lab result | `web/inboxhub/Inboxhub`, `lab/CA/ALL/ViewLabDisplay`, `oscarMDS/UpdateStatus`, `lab/CA/ALL/PrintPDF`, `lab/ViewCumulativeLabValues` |
| `measurement-entry-playwright-checks.js` | Enter, amend and graph vitals | `encounter/oscarMeasurements/ViewAddMeasurementData`, `encounter/Measurements2`, `encounter/GraphMeasurements` |

## What they assert, and why that shape

**The database is the assertion, the page is the path.** Every check drives the UI
the way an operator does and then asserts on the rows that reached MariaDB. A page
that renders a success banner and writes nothing is the failure mode these
workflows actually have, and it is invisible to a check that only reads the page.

**Both halves of an archive.** Three of these workflows never delete: an
appointment delete writes `appointmentArchive` then removes the row, an allergy
modify adds a replacement and archives the original, an allergy delete flips
`archived`. Each check asserts both halves, because "gone from the page" is
equally true of a working archive and of one that dropped the record.

**Reach the surface by clicking.** The add-appointment popup is opened from an
empty slot link, the compose page from the inbox's Compose link, the
add-prevention popup from the grid's prevention name. This is deliberate and it
earned its keep immediately: navigating directly to `messenger/ViewCreateMessage`
redirects to `/index`, which renders the **login page**, so a check that took the
direct route would have been quietly asserting against a login form.

**Dialogs are answered by name.** A delete, a cancel and an acknowledge are each
gated on `confirm()` (the lab acknowledge adds a `prompt()`). The harness dismisses
dialogs by default and a check opts in per action, so a read-only check can never
answer "delete?" with yes by accident.

## The shared harness

`scripts/carlos-playwright-harness.js` carries what every clinical check needs:
BASE_URL validation (loopback/RFC1918 only unless `ALLOW_NON_LOCAL_BASE_URL=true`),
a root-relative-only URL builder, the page recorder that turns silent 500s and
browser exceptions into failures, a login that accepts both schedule landing
routes, a MySQL client that passes its password through a 0600 defaults file, and
the dialog-accept queue.

`scripts/eform-local-playwright-utils.js` is the eForm counterpart and is
deliberately left alone: it carries eForm editor and attachment knowledge that
clinical checks must not inherit.

Two harness behaviours are worth knowing before writing a new check:

- **One dialog listener per page.** Playwright delivers a dialog to every
  registered listener, so a second listener that accepts races the harness's
  dismiss and whichever loses throws "already handled". Use
  `acceptNextDialog(page, recorder, label, promptText?)`, which enqueues an intent
  the single handler reads, and `clearPendingDialogAccepts(page)` for an action
  that only *might* prompt.
- **`mysql -B` escapes its output.** Backslashes, tabs and newlines come back
  escaped, so a column holding `a\_b` reads as `a\\_b`. Normalise before comparing
  text that can contain any of them.

## Fixtures a clean install does not provide

Two of these workflows cannot run on a freshly installed system, and that is a
property of the install rather than of the checks:

- **Messenger has no contacts.** `groupMembers_tbl` is empty and the shipped "doc"
  group has no members, so the compose page renders an empty recipient list and no
  message can be sent. The check enrols the test provider through
  **Administration > Messenger**, which is the operator's own remedy, and removes
  that enrolment again only if it created it.
- **No lab is routed to a provider.** The demo dataset routes its labs to provider
  `0`, so no provider has a reviewable inbox item. The check routes one existing
  demo lab to the test provider and restores the routing exactly as it found it.

Neither check fabricates clinical content: both reuse records the demo dataset
already ships.

## Adding a seventh

Follow the three rules the existing six follow, in order of how much grief they
save:

1. **Assert the database.** If the check would still pass when the save silently
   wrote nothing, it is not covering the workflow.
2. **Clean up in a `finally`, keyed on a per-run marker.** A failed run must leave
   the deployment as it found it, and a marker in a text column (reason, comment,
   lot, subject) is what makes cleanup precise enough to run against a shared
   test database.
3. **Report, don't encode, a defect you find.** A check that pins current broken
   behaviour as expected makes the bug permanent. Where a probe documents a defect
   worth keeping an eye on, print a `NOTE` and put the assertion behind an opt-in
   environment variable — `measurement-entry-playwright-checks.js` does this with
   `MEASUREMENT_ASSERT_INPUT_VALIDATION` — so it can be switched on the day the fix
   lands.
