# eChart patient isolation — issue 4556

The regression is two patients' charts open in separate windows of one login.
Opening or refreshing B must not change which patient A's navbar, history,
measurement entry, forms, or note metadata addresses.

Requests now carry an explicit patient. Missing, invalid, and conflicting patient
parameters fail before clinical access; the resolved patient is checked against
the caller's privileges. Navbar requests use their own encounter bean. Entry
metadata is remembered separately for each patient, copied before use, and removed
only for the patient being closed. Form records and measurement deletion batches
are checked against the requested patient before writes.

The Vascular Tracker's seeded legacy route, field binding, forwarding, and saved
record reopening were repaired so its actual save paths can be verified. Save and
Exit appends literal completion text only to an opener that still identifies the
same patient; navigating that opener to another patient prevents the append.

The touched Vascular Tracker markup also has contextual labels for 42 controls,
real column headers for its three entry matrices, and presentation semantics only
for the nested historical date/value layouts. Deprecated font wrappers are replaced
with spans. Existing control names, DOM order, values, IDs and clinical scripts are
preserved. The Medication Adherence comments field's misspelled keyboard-order
attribute is corrected, with native Tab navigation covered by the browser check.

The chart's identity banner (`newEncounterHeader.jsp`) also names the patient its own
chart request carries. It previously read the login-wide `EctSessionBean`, which
`IncomingEncounter` still rewrites for whichever chart was entered last, so refreshing
A's chart after entering B showed B's name, date of birth and health number above A's
notes. The notes panel (`ChartNotes.jsp`, rendered into every chart) likewise opens
Browse Notes for its own request's patient and takes the Bill/provider entry metadata from
the session bean only when that bean is this chart's. Save and Exit's panel refresh asks
for the usual six rows (`numToDisplay=6`), and `loadDiv()` treats an undeclared
`appointmentNo` (the standalone notes view) as no appointment instead of throwing.

A navbar request whose appointment no longer belongs to the chart's patient (deleted, or
moved by cut and paste while the chart was open; or a forged number) now renders as a chart
without an appointment instead of answering 400 for every module. The foreign or missing
appointment's provider is still never used.

The Vascular Tracker routing, binding and script repairs also fix findings 103
(#3358), 254 and 255 (#4612). Their pinned `clinical-forms-save-reopen-vascular-tracker`,
`-save` and `-script` checks pass live, so the findings are marked fixed and the pins
are now regressions. The eye-exam comment box is read back from a row its save writes
(`iDiaComments`, as the date reads `iDiaDate`) and is encoded; it previously rendered the
retained `iEx` column, so the comment vanished on reopen and the next Update cleared it.
That box now also has its own screen-reader label, as the foot-exam box does.

## Verification

- The original browser check from release commit `3d5f85fbb6` reproduced the
  crossover: both initial patient controls passed, then A's post-B read failed.
  Owned fixture cleanup was verified.
- 447 targeted Java checks pass: 143 patient-context/measurement/note/graph cases,
  12 incoming-encounter cases, 57 form cases, 193 existing chart/appointment
  regressions, 17 authorization/routing cases, 11 existing measurement/JSP
  encoding regressions, seven existing reload-URL cases, and seven latest-form
  integration cases. Unit tests exercise production actions with mocked service boundaries.
- The new H2 integration coverage includes 31 cases for actual measurement
  queries/deletion tombstones, form ownership, Vascular Tracker historical-field
  retention, and latest-form selection. It rejects mixed-patient deletion before
  mutation, ignores forged uneditable fields, preserves SQL NULL and raw history,
  and resolves equal form-edit timestamps deterministically. The old latest-form
  query fails four of the seven new selection cases as a negative control.
- The accessibility repair preserves all 186 controls, 95 existing IDs, 41 inline
  handlers and the exact clinical JavaScript. Fresh and saved forms produce the
  same complete Save and Save & Exit payload/state in four comparison cases.
- Existing chart revision, CPP transaction, appointment, authorization, and routing
  regression tests are rerun after changes.
- CodeRabbit's two error-path findings are covered: absent billing demographics
  use empty names without borrowing another chart's metadata; form-record lookup
  SQL failures return a controlled 500 before clinical prefill or forwarding.
- `npm run test:scripts` includes executable client tests for independent navbar
  request identities, complete patient snapshots, and safe form completion. The
  focused client suites pass 74 panel/completion/isolation cases, 21 Vascular
  Tracker script cases, and 93 link-audit/navbar-readiness cases. The manifest
  suite passes 63 checks. An earlier complete local run passed 3,719 tests with
  five existing TODOs. A later full run exhausted the runtime's disk quota in
  two unchanged demo-data suites; their isolated rerun passes 64 tests with four
  existing TODOs. Current-head CI runs the complete suite.
- The expanded `echart-two-patient-isolation` Playwright workflow drives real
  reads and writes for both patients, rejects invalid/conflicting identities and
  foreign form IDs, and checks the other patient's complete clinical rows after
  every write. It includes Vascular Tracker Update, reopen, Save and Exit, and
  navigation of the opener to another patient before completion. The seeded VT
  registration is temporarily enabled for this exclusive test and its exact prior
  registry row is restored and asserted during cleanup.
  Each patient's native VT setup additionally verifies all 42 repaired control
  names/label associations, unique DOM IDs, the three matrices' column-header
  names in both detail states, native Weight-label focus and MedR-to-MedA Tab navigation, and unchanged complete
  control state/FormData after the accessibility inspection.
- The actual-app Ontario and BC workflows each pass all 23 steps, including both native
  measurement saves and history links, VT Update/reopen/Save and Exit, retained
  historical data despite forged hidden inputs, and same-login opener navigation.
  Its navigation check captures the real unload beacon through an unmodified
  pass-through route and verifies the exact patient/note/provider and database
  lock removal; it does not mock a response or relax the request-error checks.
  Cleanup leaves zero owned rows across 20 tables in each database and restores
  all form registrations exactly (102 in Ontario, 55 in BC). On the same
  production overlay, the navbar catalogue
  passes 24 opened links (14 configured policy skips), and the document/note suite
  passes 16 steps using its already-merged recorder fix from release commit
  `2336e371b68c`. Their fixture and file cleanup also passes.

Local browser validation uses an isolated Tomcat/MariaDB deployment based on the
previously audited WAR, with compiled production classes and changed web resources
overlaid and hashed. It is not a claim that a fresh WAR was packaged locally.
GitHub CI provides the clean build and JSP compilation checks.

A follow-up validation built this branch from source (exploded WAR on Tomcat 11, Ontario
demo data, JDK 25):

- The extended `echart-two-patient-isolation` check has 24 steps. Its new refresh step
  (banner and Browse Notes patient) fails on the previous head ("A's refreshed chart names
  another patient in its identity banner") and passes with the header fix. `activateOther()` now enters the other chart through
  `IncomingEncounter`. Navbar requests no longer rewrite the session bean, so reloading a
  chart URL left the session on the last-entered patient, and the B-direction steps could
  not detect a regression. The first 23 steps pass in every run, and one complete run passed
  all 24. In the other runs the final opener-navigation step completed every functional
  assertion (A's lock released, B's note and rows unchanged, A's measurement and form revision
  written to A), but its strict recorder kept one aborted note-lock beacon. Under the
  pass-through route, this sandbox's Chromium sometimes re-issued the beacon as a second request
  without its Blob body, which the step's exact-request binding does not consume. The
  unmodified check from `5f736b9a` does the same on this deployment, so it is a property of that
  browser build rather than of these changes; the reference devcontainer run should confirm it.
- `EncounterHeaderPatientIsolationUnitTest` (5 cases) fails 3 cases against the previous
  header. `ChartNotesPatientIsolationUnitTest` pins the notes panel's patient source, and the
  navbar unit test now expects a dropped stale appointment rather than a 400. The client tests
  for the `numToDisplay` refresh, the undeclared appointment global and the eye-exam comment
  round trip each fail without their fix.
- A multi-agent adversarial review of the follow-up work (five reviewers, two refuting
  verifiers per finding) confirmed the notes-panel crossover, the stale-appointment 400, the
  eye-exam comment round trip and three documentation errors, all fixed here or in the
  follow-up issues; nine other candidate findings were refuted.
- Sandbox-only accommodations, not committed: Chromium 141 with Playwright 1.60 text-decodes
  the `image/png;charset=UTF-8` plot body, so the local copy re-fetched it through the same
  session; menu hovers were forced; and the `measurementsExt` snapshot query used an
  equivalent `OR` form for MariaDB 10.11 (the package requires 11.4 or later).

## Additional faults found

These findings remain outside the patient-isolation and form runtime-error repairs.
They were found by source review or form-script tests; each has not received a
separate live reproduction. Each is tracked in its own issue: items 1, 2 and 6 in
#4674, item 3 (with the other remaining `EctSessionBean` readers, including the chart
print's MRP line) in #4673, item 4 in #4676, item 5 in #4675, item 7 in #4677 and
item 8 in #4678. The Vascular Tracker's remaining unencoded output of stored text is #4681.

1. **Vascular Tracker partial saves:** Save and Exit writes measurements before
   inserting the form, without a common rollback. A later form SQL failure can
   leave measurements committed without the form.
2. **Vascular Tracker validation:** the validation flag is overwritten for each
   measurement and again by visit-date validation. An earlier invalid field can
   be masked by a later valid field.
3. **Old encounter save endpoint:** `EctSaveEncounter2Action` took patient
   and revision identity from the login-wide `EctSessionBean` and `eChartID`.
   **Fixed in #4673** with the other remaining readers: the save binds patient,
   revision and appointment to its own request and checks them under the patient's
   row lock before writing. See [issue-4673-patient-isolation.md](issue-4673-patient-isolation.md).
   Modern case-management saves have separate regression coverage and do not use
   that endpoint.
4. **Dead legacy appointment switch:** the `appointmentList` entry branch is reached
   only by a hand-built request (its one URL builder, `getAnotherEncounter()` in
   `encounter.js`, has no callers and sends no patient). It prepares the named
   appointment's patient, with that patient's read privilege enforced, before the
   rendered page rejects the mismatch. **Fixed in #4676:** the branch, its builder,
   `EctSessionBean`'s appointment-number setup overload and the never-rendered
   appointment list are removed, and every entry's `appointmentNo` must now be an
   existing appointment of the request's own patient (or `0`/absent for none) before
   the chart is loaded or remembered. See
   [issue-4676-appointment-entry.md](issue-4676-appointment-entry.md).
5. **Patient-record restrictions in measurements:** history, setup, write, and
   graph routes check measurement privileges but do not call
   `isAllowedAccessToPatientRecord`, which additionally enforces patient-specific
   chart restrictions. Measurement deletion already performs both checks. This
   existing authorization gap needs a separate restriction-focused fix and tests.
6. **Vascular Tracker waist/hip ratio UI:** its XML definition includes WHR, but
   the form has no visible WHR control. Obsolete handlers targeting that missing
   control are removed; WC and HC remain independent entered values. Existing
   WHR values, dates, and comments are retained without inventing a new derived
   clinical value. Restoring a visible, validated ratio workflow is separate work.
7. **Classic editor billing path:** legacy `Index2.jsp` stores the application
   context as `casemgmt_oscar_baseurl`, while the classic editor's Add Billing link
   appends the context again. That entry path can therefore produce
   `/carlos/carlos/billing`. This existing base-URL composition needs separate
   validation for local and external legacy deployments; it was not live-tested here.
8. **Started-form item link:** `EctDisplayForm2Action` shows one started item per
   form (not a list of historical rows), dated from the most recently *created* row,
   but links it to `formId=latest`, which resolves by edit time. For a form updated in
   place, the item could show one row's date and open another. **Fixed in #4678:** the
   item keeps its `formId=latest` link and is now dated from the row that link opens
   (`EctFormData.getShortcutLatestPatientForm`, ordered as the shortcut orders,
   `formEdited DESC, ID DESC`). Pinned by `EctFormDataShortcutLatestIntegrationTest`,
   `EctDisplayForm2ActionUnitTest` and the `echart-forms-latest-item` browser check,
   which clicks patient A's item with patient B's chart open.
