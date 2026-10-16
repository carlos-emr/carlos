# Remaining encounter-bean readers — issue 4673

Follow-up to [issue-4556-patient-isolation.md](issue-4556-patient-isolation.md) (#4556, #4667).
The login-wide `EctSessionBean` is still written by `IncomingEncounter`, for whichever chart was
entered last in any window of a login. The readers below took patient data from it, so with two
charts open they printed, or wrote to, the other patient. Each now resolves its patient from its
own request (`EncounterPatientContext.requireDemographicNo`) and reads patient attributes from that
patient's record or from that patient's own remembered entry (`findEncounter`/`entryOrLoggedIn`).

| Reader | Was | Now |
|---|---|---|
| Chart print MRP line (`CaseManagementPrint.getMRP`) | the last-entered chart's family doctor | the printed patient's `demographic.provider_no` |
| Legacy encounter save (`EctSaveEncounter2Action`, `/encounter/SaveEncounter2`) | patient and revision from the bean and the session `eChartID`; appointment signed from the bean | patient, revision (`eChartId` field) and appointment from the request, checked under the patient's row lock by `EChartRevisionPersister` |
| Notes service new note (`NotesService.getCurrentNote`) | visit type from the bean | from this patient's own entry |
| `encounterPrint.jsp`, `echarthistoryprint.jsp` | printed the bean; the history print overwrote the bean | request-owned bean for the request's patient, patient-level access, encoded output |
| `TimeOut.jsp`, `concurrencyError.jsp` | refresh decision and recovered text from the bean | from the save's own request |
| Immunization schedule (`Schedule.jsp`, `ScheduleConfig.jsp`, `ScheduleEdit.jsp`, save/delete/template actions) | fell back to the bean's patient; delete/restore posted `demoNo`, which the redisplay never read; provider from the bean | request patient only (400 otherwise), patient-level write checks, logged-in provider |
| `ChartNotesAjax.jsp` | provider, pending message and its clearing from the bean | only when the bean is this chart's |
| Legacy navigation (`casemgmt/navigation.jsp`) | links from a session-wide copy of the bean; its messenger list named the session-wide `casemgmt_msgBeans` as a literal string, so every rendering stopped there with a 500 | links from the request's patient and entry; the messenger list shows that patient's own messages (with `_msg` read), encoded |
| `messenger/generatePreviewPDF.jsp` (writer) | replaced the bean with a bare one | no longer touches it |

## The legacy encounter editor

`encounter/Index2.jsp` renders the editor only with the CAISI module (`caisi=on`) and only for an
`IncomingEncounter` request carrying `casetoEncounter`; otherwise it forwards to the case-management
chart. Nothing in the application builds such a URL (`navigation.jsp` computes one and never emits
it), so the editor is reached only by a hand-built entry and then re-renders itself after each save
through its own `casetoEncounter` field. The save action is reachable in every configuration, so its
rules are enforced there, not in the page:

- POST only; patient from `demographicNo` (missing, malformed or conflicting: 400) with
  `_demographic` write for that patient and access to the record; every button but Exit also needs
  `_eChart` write and Verify and Sign `_eChart.verifyButton` write, as the editor offers them;
- the revision the editor shows (`eChartId`, rendered by `Index2.jsp`): another patient's revision
  or a malformed one is 400; a revision that is no longer the patient's newest, or an editor without
  the field, is 409 with the refused text on the concurrency page (412 when the request says
  `submitMethod=ajax`, which no current page sends);
- the appointment (`appointment_no`): another patient's appointment is 409; a deleted appointment is
  treated as none; Sign/Verify derive the new status from the appointment's current status and
  archive the old values only after the revision is written; a visit without a status is saved
  against but not marked;
- a refusal writes nothing: no revision, no appointment change, no layout row;
- after a save the editor keeps the visit it submitted (appointment, date, reason). The patient's
  remembered entry is the latest of any of that patient's windows, so re-rendering from it could
  name another of the patient's visits, which the next Sign would then sign.

A repeated submit of one rendering (a double click, or a reload of its result) is refused as stale
with its text on the concurrency page; the first submit's revision stands.

The editor's own form also posted nowhere: `encounter-head.jspf` sets `<base href>` to the context
root, so the relative action `SaveEncounter2` resolved to `/carlos/SaveEncounter2` and every Save
and Sign & Save answered 404. It now posts to `/encounter/SaveEncounter2`.

## Verification

- **Java tests.** Each new pin was run red before its fix: the legacy save action
  (`EctSaveEncounterPatientIsolationUnitTest`, 32 cases, including the `_eChart` privileges and the
  editor's own visit after a save), the revision persister against H2 with real commits and
  rollbacks (`EChartRevisionPersisterIntegrationTest`, 17, including a visit without a status, which
  used to throw `StringIndexOutOfBoundsException` or be blanked), the immunization schedule actions
  and pages (`ImmunizationSchedulePatientIsolationUnitTest`, 12;
  `ImmunizationScheduleViewPatientIsolationUnitTest`, 8), the print MRP
  (`CaseManagementPrintMrpUnitTest`, 4), the notes-service visit type
  (`NotesServiceEncounterTypeIsolationUnitTest`, 4), the prints, the editor's revision field, its
  encoded appointment and its form action (`EncounterPrintPatientIsolationUnitTest`, 15),
  `ChartNotesAjax.jsp` (3) and the legacy navigation (`LegacyNavigationPatientIsolationUnitTest`, 6).
  The GET-rejection contract registers the legacy save action, and `EncounterPatientContextUnitTest`
  covers the remembered entry's appointment status. 361 focused encounter, notes, immunization and
  print tests pass (JDK 25, Ubuntu 26.04 builder), and the jspc profile compiles all 992 JSPs.
- **Packages.** `carlos-emr` and `carlos-emr-drugref` were built from this branch with
  `dpkg-buildpackage` in an Ubuntu 26.04 / JDK 25 builder and installed into a fresh Ubuntu 26.04
  container (MariaDB 11.8, Ontario demonstration data) with `scripts/deb-docker-validation.sh`; the
  browser checks ran through the packaged front door. See the record in
  [deb-install-validation.md](deb-install-validation.md).
- **`echart-two-patient-isolation`** passes all 40 steps (the 24 existing and 16 new). With the other
  chart entered last, in each direction: the chart print's MRP line; the legacy save, its Sign, Save
  and Exit of the chart's own appointment (archived, then signed `tS`), and its refusals (stale,
  missing or foreign revision, another patient's appointment, malformed values, missing or
  conflicting patients, GET) with both patients' rows unchanged; the editor and history prints; the
  immunization schedule's template add and set delete; the legacy navigation, which must render
  past its messenger list and link only to its own patient; and the notes list, which must keep the
  logged-in provider's own unsigned notes editable after the other chart was entered from another
  provider's day sheet. The notes list compares each unsigned note's author with the chart entry's
  provider, which it used to take from the bean. The notes service has no browser step (see 1
  below).
- **`legacy-encounter-editor-isolation`** drives the editor itself, opened from the day sheet in two
  windows, with `caisi=on`: both Saves, Print, the stale editor's 409 concurrency page, Sign & Save
  (which closes the window and refreshes the day sheet), and an editor that keeps its own visit
  through a Save after its patient was entered again from another visit, so that its Sign signs that
  visit: all 7 steps pass. With `caisi=off` it reports SKIP. `encounter-legacy-note-soft-wrap`
  reached the recovery page by a bare GET and expected it to show the login-wide bean's text; it now
  reaches it through a refused stale save, as a user does, and passes with `caisi=on`.
- **Negative controls.** With the previous classes and pages swapped into the same installation,
  each new group of steps fails on the crossover it pins: A's print names B's MRP; the legacy save
  reopens the other patient's chart; the signed save's closing page does not refresh the schedule;
  a stale revision is accepted (302); A's editor print is not A's chart alone; the immunization
  template is not added to A's schedule; the navigation renders no links; A's notes list treats the
  logged-in provider's own unsigned notes as another provider's. The editor check, with
  only the previous form action corrected, fails at the first Save: "A's legacy Save: the other
  patient's echart rows changed". Unmodified, every previous editor Save is a 404. Before the visit
  fix, its last step fails with "A's redisplayed editor names another visit than the one it was
  opened from". Each run's cleanup asserted every owned row removed.

Found while testing, outside this issue's readers:

1. The notes service's new-note REST call (`POST /ws/rs/notes/{demographicNo}/getCurrentNote`)
   answers 500 when the patient has no note in progress: `CaseManagementNote.isLinkTo()` looks up
   the links of a note whose id is still null, which Hibernate 7 rejects. That happens before the
   visit type is read, so the visit-type fix is covered by unit tests only. Not changed here.
2. The browser check's patient snapshot read `measurementsExt` with `IN (... UNION ...)`, which
   MariaDB 11.8 runs as a dependent subquery for every extension row: minutes against the
   demonstration data. A derived table returns the same rows in under a second.
3. The server-log audit caught the legacy navigation failing with a `PropertyNotFoundException` at
   its messenger list on every rendering, after the response had already started (200, page cut
   short). Fixed here, with the list bound to the request's patient.
4. `NotesService.saveNote` dereferenced a missing body (`POST /ws/rs/notes/{demographicNo}/save`
   without one), as SonarCloud reported; it now saves nothing, as for an empty note.
5. `EctImmCreateImmunizationSetConfig2Action` (template creation, not patient data) still writes on
   GET; its page posts. Not changed here.
