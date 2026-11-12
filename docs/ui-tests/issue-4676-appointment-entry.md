# Chart entry appointment ownership — issue 4676

Follow-up to [issue-4556-patient-isolation.md](issue-4556-patient-isolation.md) (item 4) and
[issue-4673-patient-isolation.md](issue-4673-patient-isolation.md).

`/encounter/IncomingEncounter` is every chart entry: the day sheet's E link, the patient search,
the master record, messages, labs and documents. It remembers, per patient in the login
(`EncounterPatientContext.rememberEncounter`), the entry's provider, appointment, reason and
encounter type. That remembered entry is what the chart's notes panel names as its entry provider,
what a new note takes its encounter type from, and what the legacy encounter save, the eForm links
and the chart's billing link start from.

## What was wrong

- **The dead `appointmentList` branch.** It checked the requested `demographicNo`, then called
  `EctSessionBean.setUpEncounterPage(loggedInInfo, appointmentNo)`, which loaded the *named
  appointment's* patient, and remembered that bean under that patient. `Index2.jsp` then refused to
  render a chart other than the one requested, but by then the other patient's remembered entry
  (provider, appointment, reason, encounter type) had been overwritten. With that patient's chart
  open in another window, its next note named the wrong entry provider and a new note could take
  the wrong encounter type. No page called the branch: its only URL builder, `getAnotherEncounter()`
  in `encounter/js/encounter.js`, had no callers and sent no patient (so since #4667 it is refused
  before the branch), and the appointment list it served (`appointmentsIdArray` /
  `appointmentsNamesArray`) was filled on every chart entry but never rendered.
- **Any appointment number was accepted.** The ordinary entry remembered the request's
  `appointmentNo` without looking it up, so a hand-built request could make another patient's
  visit (or one that does not exist) a chart's remembered appointment.

Neither was a patient-authorization bypass: the requested patient's `_demographic` read was always
checked, and loading the appointment's patient checked that patient's read privilege too.

## What changed

- The `appointmentList` branch, `getAnotherEncounter()`, `EctSessionBean`'s
  `setUpEncounterPage(LoggedInInfo, String appointmentNo)` overload and the never-rendered
  appointment list (and the query of the provider's day that filled it) are removed. A request
  carrying `appointmentList` is now an ordinary entry for its own `demographicNo`.
- Every entry's `appointmentNo` is checked against its row before the chart is loaded or
  remembered (`EctIncomingEncounter2Action.ownAppointment`): absent, empty or `0` opens the chart
  without an appointment, as before; a malformed number is refused as before; an appointment that
  does not exist, is another patient's or is booked without a patient is refused with 400. An
  accepted appointment is remembered by the row's own number.

The day sheet always pairs an appointment with its own patient
(`appointmentprovideradminday.jsp` takes `demographic_no` from the appointment), and the other
entries send no appointment or `0`, so no page's entry changes.

## Verification

- **Java tests.** `IncomingEncounterRequestIsolationUnitTest` (`AppointmentOwnership`): a request
  naming A with B's visit, with a visit that does not exist or with one booked without a patient,
  with and without `appointmentList`, is refused before any chart is loaded, and both patients'
  remembered entries keep their appointment, reason and encounter type; A's own visit with the
  flag opens as an ordinary entry; `0` opens without a lookup; a padded number is remembered as the
  row's; malformed numbers and an unreadable patient are refused before any lookup.
  `IncomingEncounterAppointmentOwnershipIntegrationTest` drives the action against real H2 rows
  (patients, eChart revisions, visits, notes) with real commits, and compares both patients' rows
  before and after each refused entry.
- **Browser check.** `chart-entry-appointment-ownership` (core tier): two chart windows of one
  login, B entered from another provider's day sheet. The refused entries answer 400 without a
  redirect and change no row; each chart's next note save still names its own entry provider and
  links its own visit.
- **Packaged install (2026-10-22).** 2026.09.0~snapshot26 packages built in an Ubuntu 26.04
  container from this branch (DrugRef from its pinned ref; carlos-ctl from the
  `debian/carlos-ctl.pin` fallback commit `325482a`, stamped 1.2.0 because that tag is not
  published), lintian clean, installed fresh with `scripts/deb-docker-validation.sh up` and the
  Ontario demo dataset (`carlos-ctl check` clean, `:443`). `chart-entry-appointment-ownership`
  **PASS**es 4/4 steps and leaves no fixture rows. With the pre-fix `EctIncomingEncounter2Action`
  and `EctSessionBean` classes swapped into the installed webapp it **FAIL**s at the refusal step:
  every hostile entry answered 302 and opened a chart. A throwaway variant that only logged those
  answers then showed the overwrite itself: B's notes panel named the logged-in provider instead of
  the provider B was entered from, and A's named the other provider.

```bash
npm run test:chart-entry-appointment-ownership-playwright
```
