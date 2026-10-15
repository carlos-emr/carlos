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

## Verification

- The original browser check from release commit `3d5f85fbb6` reproduced the
  crossover: both initial patient controls passed, then A's post-B read failed.
  Owned fixture cleanup was verified.
- 444 targeted Java checks pass: 141 patient-context/measurement/note/graph cases,
  12 incoming-encounter cases, 56 form cases, 193 existing chart/appointment
  regressions, 17 authorization/routing cases, 11 existing measurement/JSP
  encoding regressions, seven existing reload-URL cases, and seven latest-form
  integration cases. Unit tests exercise production actions with mocked service boundaries.
- The new H2 integration coverage includes 31 cases for actual measurement
  queries/deletion tombstones, form ownership, Vascular Tracker historical-field
  retention, and latest-form selection. It rejects mixed-patient deletion before
  mutation, ignores forged uneditable fields, preserves SQL NULL and raw history,
  and resolves equal form-edit timestamps deterministically. The old latest-form
  query fails four of the seven new selection cases as a negative control.
- Existing chart revision, CPP transaction, appointment, authorization, and routing
  regression tests are rerun after changes.
- `npm run test:scripts` includes executable client tests for independent navbar
  request identities, complete patient snapshots, and safe form completion. The
  focused client suites pass 67 panel/completion/isolation cases, 12 Vascular
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

## Additional faults found

These findings remain outside the patient-isolation and form runtime-error repairs.
They were found by source review or form-script tests; each has not received a
separate live reproduction.

1. **Vascular Tracker partial saves:** Save and Exit writes measurements before
   inserting the form, without a common rollback. A later form SQL failure can
   leave measurements committed without the form.
2. **Vascular Tracker validation:** the validation flag is overwritten for each
   measurement and again by visit-date validation. An earlier invalid field can
   be masked by a later valid field.
3. **Old encounter save endpoint:** `EctSaveEncounter2Action` still takes patient
   and revision identity from the login-wide `EctSessionBean` and `eChartID`.
   The separate legacy encounter editor therefore needs its own patient-bound
   save/revision work. Modern case-management saves have separate regression
   coverage and do not use that endpoint.
4. **Legacy appointment dropdown:** the `appointmentList` entry branch loads the
   selected appointment's patient before checking agreement with the requested
   patient. The rendered page rejects a mismatch, but the lookup and metadata
   preparation have already occurred. That legacy switching flow needs a separate
   ownership and navigation fix.
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
8. **Historical form-row links:** `EctDisplayForm2Action` labels saved rows with
   their service date but constructs their shortcut URL with `formId=latest`
   rather than that row's form ID. An older row can therefore reopen the newest
   record. This source finding needs separate historical-selection coverage; the
   isolation workflow deliberately verifies the currently advertised latest link.
