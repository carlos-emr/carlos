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
| Immunization schedule (`Schedule.jsp`, `ScheduleConfig.jsp`, `ScheduleEdit.jsp`, save/delete/template actions) | fell back to the bean's patient; delete/restore posted `demoNo`, which the redisplay never read; provider from the bean | request patient only (400 otherwise), access to the patient's record on the pages (`isAllowedAccessToPatientRecord`, as the prints check it) and patient-level write checks in the actions, logged-in provider |
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
  (`EctSaveEncounterPatientIsolationUnitTest`, 33 cases, including the `_eChart` privileges and the
  editor's own visit after a save, dated today when it has no appointment), the revision persister against H2 with real commits and
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
  provider's day sheet. The notes list compares each unsigned note's author with this chart's own
  entry provider (the patient's remembered entry, or the logged-in provider when this login has not
  entered the chart), which it used to take from the bean. The notes service has no browser step
  (see 1 below).
- **`legacy-encounter-editor-isolation`** drives the editor itself, opened from the day sheet in two
  windows, with `caisi=on`: both Saves, Print, the stale editor's 409 concurrency page, Sign & Save
  (which closes the window and refreshes the day sheet), an editor that keeps its own visit through
  a Save after its patient was entered again from another visit, so that its Sign signs that visit,
  and a double-clicked Save, which posts the editor once (its buttons submit once per rendering)
  and shows it again: all 8 steps pass. With the previous button row the double click ends on the
  concurrency page. With `caisi=off` it reports SKIP. `encounter-legacy-note-soft-wrap`
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
   answers 500 whenever it has to start a new note: `CaseManagementNote.isLinkTo()` looks up the
   links of a note whose id is still null, and `JpqlQueryHelper` refuses the null parameter
   (#4784). That happens before the visit type is read, so the visit-type fix is covered by unit
   tests only. Not changed here.
2. The browser check's patient snapshot read `measurementsExt` with `IN (... UNION ...)`, which
   MariaDB 11.8 runs as a dependent subquery for every extension row: minutes against the
   demonstration data. A derived table returns the same rows in under a second.
3. The server-log audit caught the legacy navigation failing with a `PropertyNotFoundException` at
   its messenger list on every rendering, after the response had already started (200, page cut
   short). Fixed here, with the list bound to the request's patient.
4. `NotesService.saveNote` dereferenced a missing body (`POST /ws/rs/notes/{demographicNo}/save`
   without one), as SonarCloud reported; it now saves nothing, as for an empty note.
5. `EctImmCreateImmunizationSetConfig2Action` and `EctImmInitConfigDeleteImmuSet2Action` (templates,
   not patient data) do not check the request method themselves; `HttpMethodGuardFilter` refuses
   GET on both routes. Not changed here (#4811, item 20).


## Session state beyond the encounter bean

The same defect had more carriers than the encounter bean: state that one window left in the
login's session under a login-wide name and that a request from another window read. Each now
belongs to its own patient or window. `ChartSessionCarrierSourceUnitTest` scans every Java source
and JSP (comments removed) for the replaced names, so a reintroduction fails the build.

| Feature | Was | Now |
|---|---|---|
| Chart note list, search, Disease Registry add (`casemgmt_DemoNo`) | fell back to the chart opened last when the request named no patient | the request must name its patient (400 otherwise) |
| Chart editor state: note-filter handoff, the text the last save wrote into the legacy eChart, the draft-restore signal, `readonly` | one session attribute per login | keyed by patient (`ChartSessionKeys`); `readonly` is a request attribute. As before, the editor drops the saved-note text when it opens again for that patient, and the chart view consumes the restore signal; until then each patient keeps its own (#4811) |
| Disease Registry page and its refused-code re-render | patient and diagnoses from the session | request attributes for the request's own patient |
| Waiting-list patient page, allergies page, Rx drug search, PMM client view | patient number or data copied into the session under generic names (`demographicNo`, …), which every page resolving that name through EL fell back to | request attributes or the page's own patient |
| Day sheet's current visit (Health Tracker link, multisite tickler and prescription defaults) | the appointment last opened for any patient | filed per patient by `provider/ViewStoreApptInSession` (204); read back only if still that patient's |
| Messenger compose attachments | on the login's messenger bean: another compose window's PDFs went out with this message, linked to the other patient | staged per compose window (`MsgAttachmentStaging`), bound to the patient they were rendered for; a send whose attachments were released to make room for other windows' is refused once with the reason, never sent without them unannounced |
| Messenger forward ("Save Attachments" ▸ Forward, Reply or Compose) | the selection waited on the bean for the next message from any window | staged under the message's own forwarding key, the same on every view of that message in the login; that view's Forward, Reply, Reply All and Compose send it, and showing the view again (linking a patient does) keeps it |
| Messenger viewer and PDF download | message, linked patients and PDF list in the session; the download served another message's PDF (#4473) | request attributes; the download names its message, streams and returns `NONE` |
| BC bill create ▸ review ▸ save | one session bill: a second billing window replaced it, so the first billed the other patient and appointment | staged per bill (`BcBillStaging`, `billKey`); the form's in-place reloads (physician, bill type or billing form changed) are rendered without being staged, and Continue records the billing form shown. Every step that finds a bill keeps it longest; a saved bill is dropped first; a window whose bill was dropped is told its billing session expired |
| BC receipt after "Update Invoice" | the invoice viewed last in any window | redirect to the view of the receipt it came from, every bill of it |
| Ontario year-end "Print PDF" | the statement the last search in any window left in the session | the statement on screen, recomputed: Create Statement renders its patient and dates into the form, so dates changed afterwards without creating it again do not change the PDF |
| PMM "attach note to client" popup | the note to attach from the session | carried by the popup's own form and links |
| Appointment booked page | fell back through EL to the list the waiting-list page last showed, offering to remove a patient who is on no list | only the booked patient's own waiting-list entry |

### Verification of this part

- **Java tests.** Unit tests per fix (`MsgAttachmentStagingUnitTest`, `BcBillStagingUnitTest`,
  `ViewStoreApptInSession2ActionUnitTest`, the Messenger, BC billing, year-end, Health Tracker,
  Disease Registry and PMM action tests) and the source pin above. The full suite was run before
  pushing.
- **`session-state-two-window-isolation`** (new) drives six of these in two windows of one login,
  each right after the other patient's window wrote its own state: Health Tracker saves after the
  day sheet opened both patients' visits, two compose windows, two forwarding message views (the
  first linked to a patient, which shows it again, then Forward; the second Reply), two year-end
  statement windows (A's printed after its start date was changed without creating the statement
  again), a booking after the waiting-list page showed a list, and a refused registry code. All six
  pass on this branch.
- **Negative control.** The same check, made to continue past a failing step, against
  `release/2026.08`: every step fails on the crossover it pins. A's tracker save is linked to B's
  visit, A's message carries B's PDF, the first view forwards the second view's items, A's
  statement names B, the booking offers to take A off B's list, and A's re-rendered registry lists
  B's diagnoses. Against this branch before the final review's fixes, the forwarding step fails
  ("forward from the first message view does not show the items saved in it") and the year-end step
  fails ("A's year-end PDF does not list exactly A's own invoice"); the other four pass. The runs
  left no owned row behind.
- **`billing-bc-two-window-isolation`** (new, BC installs) drives the BC bill in two windows: two
  bill forms opened from the day sheet each review and save their own patient, appointment and
  codes; one bill window keeps its own bill through ten in-place reloads, a Continue the validation
  refuses, Go Back and Another Bill, and both its claims are saved for its own patient and
  appointment; Update Invoice on a two-line private receipt shows that receipt again after another
  window saved and viewed its own; a bill window whose billing form was changed comes back from a
  refused Continue with that form's code table, and, once eight more bills were opened in the same
  login, its Continue is told that its billing session expired. All four steps pass on this branch,
  run against a BC database built from the Flyway set (common + bc) with `billregion=BC`. Negative
  controls: against `release/2026.08`, A's review names B and A's receipt after Update Invoice is
  B's invoice; against this branch before the reload fix, the reloaded window's Continue answers
  400; before the final review's fixes, the fourth step fails ("The refused Continue came back to
  the GP billing form the bill was opened with, not ER"). No run left an owned row behind. The existing BC checks pass on this branch too (`billing-bc-associations`,
  `billing-bc-simulation-encoding`, `billing-bc-receive-payment`, which views the invoice;
  `billing-bc-reprocess-bill` stops at its known finding 145).
- **Existing checks for every touched flow**, re-run on this branch: `messenger`,
  `messenger-attachments`, `messenger-pdf-attachments-server-render`, `messenger-inbox-actions`,
  `messenger-demographic-link`, `gap-provider-messenger-write-to-encounter`, `health-tracker`,
  `waiting-list`, `dx-registry-quicklist`, `dx-registry-status-update`, `billing-on-year-end-statement`,
  `popup-opener-day-sheet`, `appointment-lifecycle`, `tickler-crud`, `tickler-attachments`,
  `echart-print`, `echart-note-soft-wrap` and `echart-two-patient-isolation` pass, and after the final
  review's fixes also `echart`, `echart-note-editor`, `echart-note-signed-edit`,
  `echart-group-note-history`, `echart-notes-pagination-retry`, `gap-clinical-chart-notes-pagination`,
  `search-sort-messenger-inbox` and the year-end steps of `billing-on-reports-inr-eoy` (one
  `echart-two-patient-isolation` run stopped at its Vascular Tracker popup step with "Target page,
  context or browser has been closed"; the rerun passed all 40 steps). Three need the
  packaged defaults rather than the devcontainer's overrides (`DEMOGRAPHIC_WAITING_LIST=true`,
  `dxResearch_coding_sys=icd9,ichppccode`, `AbandonOldChart` unset; see #4793), and the two-patient
  check the packaged form registry (#4792). Not run green here, for reasons outside this change:
  the Rx checks need DrugRef, `patient-messenger-context` trips over note locks the devcontainer's
  demo data ships for patients 1 and 2, `double-submit-messenger` and `get-reject-chart-notes` fail
  as their manifest entries document for 2026.08, and `xss-poison-messenger-consult` stops before
  its documented step because the Msg inbox answers 500 for its seeded patient, on
  `release/2026.08` too (#4835).
- **Regressions this verification caught and fixed:** "Save Attachments" no longer reached the
  compose page (the forward key above); "Update Invoice" showed only the first line of a
  multi-line BC receipt; the legacy editor's Sign, Save and Bill handed the BC bill no appointment
  number when the visit had none (the form's physician switch then answered 400); the day sheet's
  fire-and-forget store request, answered with 204 and never read, was reported as aborted by the
  browser; the BC bill form's in-place reloads each staged a bill, so a window whose physician was
  changed a few times lost its own bill and its Continue answered 400.
- **Found by the final review and fixed:** Forward and Reply did not carry the items saved from the
  message's Attachment window, and showing the view again (linking a patient) lost them, as its key
  changed on every render; a compose window whose attachments the session had released sent the
  message without them; a BC bill window left open while eight other bills were opened answered
  Continue with a bare 400, and Go Back after changing the billing form showed the codes of the
  form the bill was opened with; Print PDF printed the dates in the fields rather than the
  statement on screen, and answered a database failure with 400; a double click on the legacy
  editor's Sign & Save ended on the concurrency page; the legacy save of an editor without an
  appointment kept the visit date of another window's entry, which Sign, Save and Bill put on the
  bill; Update Invoice accepted a bill number its receipt view refuses.
- **Not driven in a browser:** the legacy encounter's Sign, Save and Bill into a BC bill (CAISI
  editor on a BC install; the forward is unit-tested), the PMM attach popup (its search form cannot
  find a client, #4811) and the prescription pages (DrugRef).

Left out deliberately, with locations: #4811. The dashboard's per-provider state shared across
windows and users is noted on #3947.
