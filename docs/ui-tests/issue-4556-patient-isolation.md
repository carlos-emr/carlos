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
- 382 targeted Java checks pass: 120 patient-context/measurement/note/graph cases,
  12 incoming-encounter cases, 40 form cases, 193 existing chart/appointment
  regressions, and 17 authorization/routing cases. Unit tests exercise the production actions with mocked service boundaries.
- H2 integration tests exercise actual measurement queries/deletion tombstones and
  form record ownership, including rejecting a mixed-patient deletion batch before
  any mutation.
- Existing chart revision, CPP transaction, appointment, authorization, and routing
  regression tests are rerun after changes.
- `npm run test:scripts` includes executable client tests for independent navbar
  request identities, complete patient snapshots, and safe form completion. The
  focused client suites include 54 panel/completion/isolation cases and seven
  navbar-readiness cases.
- The expanded `echart-two-patient-isolation` Playwright workflow drives real
  reads and writes for both patients, rejects invalid/conflicting identities and
  foreign form IDs, and checks the other patient's complete clinical rows after
  every write. It includes Vascular Tracker Update, reopen, Save and Exit, and
  navigation of the opener to another patient before completion.

Local browser validation uses an isolated Tomcat/MariaDB deployment based on the
previously audited WAR, with compiled production classes and changed web resources
overlaid and hashed. It is not a claim that a fresh WAR was packaged locally.
GitHub CI provides the clean build and JSP compilation checks.

## Additional faults found

These are source-review findings outside the modern eChart navbar/history/form
isolation fix. They have not been reproduced in a separate live legacy-mode run.

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
