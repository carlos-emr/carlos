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
| `ChartNotes.jsp`, `ChartNotesAjax.jsp` | the entry provider (unsigned notes' edit warnings, the page's `providerNo`), the pending message and its clearing from the bean | the entry provider from this patient's own remembered entry, or the logged-in provider when this login has not entered the chart; the pending message only when the bean is this chart's |
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
  and pages (`ImmunizationSchedulePatientIsolationUnitTest`, 20, the schedule's Save included;
  `ImmunizationScheduleViewPatientIsolationUnitTest`, 8), the print MRP
  (`CaseManagementPrintMrpUnitTest`, 4), the notes-service visit type
  (`NotesServiceEncounterTypeIsolationUnitTest`, 4), the prints, the editor's revision field, its
  encoded appointment and its form action (`EncounterPrintPatientIsolationUnitTest`, 15),
  `ChartNotesAjax.jsp` (3) and the legacy navigation (`LegacyNavigationPatientIsolationUnitTest`, 6).
  The GET-rejection contract registers the legacy save action, and `EncounterPatientContextUnitTest`
  covers the remembered entry's appointment status. 361 focused encounter, notes, immunization and
  print tests pass (JDK 25, Ubuntu 26.04 builder), and the jspc profile compiles every JSP, as the CI `jspc` job does on each push.
- **Packages.** `carlos-emr` and `carlos-emr-drugref` were built from this branch with
  `dpkg-buildpackage` in an Ubuntu 26.04 / JDK 25 builder and installed into a fresh Ubuntu 26.04
  container (MariaDB 11.8, Ontario demonstration data) with `scripts/deb-docker-validation.sh`; the
  browser checks ran through the packaged front door. See the record in
  [deb-install-validation.md](deb-install-validation.md).
- **`echart-two-patient-isolation`** passes all 43 steps (the 24 existing and 19 new, three of them from the second coverage round). With the other
  chart entered last, in each direction: the chart print's MRP line; the legacy save, its Sign, Save
  and Exit of the chart's own appointment (archived, then signed `tS`), and its refusals (stale,
  missing or foreign revision, another patient's appointment, malformed values, missing or
  conflicting patients, GET) with both patients' rows unchanged; the editor and history prints; the
  immunization schedule's template add and set delete; the legacy navigation, which must render
  past its messenger list and link only to its own patient; and the notes list, which must keep the
  logged-in provider's own unsigned notes editable after the other chart was entered from another
  provider's day sheet. The notes list compares each unsigned note's author with this chart's own
  entry provider (the patient's remembered entry, or the logged-in provider when this login has not
  entered the chart), which it used to take from the bean. `chart-session-state-two-window-isolation`
  drives the notes service's visit type (see 1 below).
- **`legacy-encounter-editor-isolation`** drives the editor itself, with `caisi=on`, in two windows
  that the day sheet's window opens at the editor's hand-built entry address (nothing links there;
  opened from the day sheet's window, Sign & Save refreshes it): both Saves, Print, the stale editor's 409 concurrency page, Sign & Save
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
   answered 500 whenever it had to start a new note: `CaseManagementNote.isLinkTo()` looked up the
   links of a note whose id was still null, and `JpqlQueryHelper` refuses the null parameter
   (#4784). Fixed here: a note not saved yet has no links and is not looked up
   (`NotesServiceNewNoteResponseUnitTest` builds the answer with the real `NoteDisplayLocal`), and
   `chart-session-state-two-window-isolation` now drives the visit-type fix in two charts.
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
| Chart note list, search, Disease Registry add (`casemgmt_DemoNo`) | fell back to the chart opened last when the request named no patient | the request must name its patient (400 otherwise). The Disease Registry add, which no page calls, also answered a GET with no privilege check; it takes a POST (405 otherwise) from a user with `_dxresearch` write for that patient and access to the record |
| Chart editor state: note-filter handoff, the text the last save wrote into the legacy eChart, the draft-restore signal, `readonly` | one session attribute per login | keyed by patient (`ChartSessionKeys`); `readonly` is a request attribute. As before, the editor drops the saved-note text when it opens again for that patient, and the chart view consumes the restore signal; until then each patient keeps its own (#4811) |
| Disease Registry page and its refused-code re-render | patient and diagnoses from the session | request attributes for the request's own patient |
| Waiting-list patient page, allergies page, Rx drug search, PMM client view | patient number or data copied into the session under generic names (`demographicNo`, …), which every page resolving that name through EL fell back to | request attributes or the page's own patient |
| Day sheet's current visit (Health Tracker link, multisite prescription address) | the appointment last opened for any patient | filed per patient by `provider/ViewStoreApptInSession` (204); read back only if still that patient's. The tickler page looked it up as well but never applied it; it no longer reads it |
| Messenger compose attachments | on the login's messenger bean: another compose window's PDFs went out with this message, linked to the other patient | staged per compose window (`MsgAttachmentStaging`), bound to the patient they were rendered for; a send whose attachments were released to make room for other windows' is refused once with the reason, never sent without them unannounced |
| Messenger forward ("Save Attachments" ▸ Forward, Reply or Compose) | the selection waited on the bean for the next message from any window | saved under the message's own forwarding key, the same on every view of that message in the login, so showing the view again (linking a patient does) keeps it; every compose window that view's Forward, Reply, Reply All or Compose opens starts with a copy of its own, so what one of them attaches stays out of the others |
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
  pass on this branch; the coverage round below added three more.
- **Negative control.** A scratch copy of the check that continues past a failing step, with any
  assertion on what only this branch has (such as the visit store's 204 answer or a compose
  window's attachment key) relaxed, run against `release/2026.08`: every step fails on the crossover
  it pins. A's tracker save is linked to B's
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
  login, its Continue is told that its billing session expired. All four steps pass on this branch
  (the coverage round below added a fifth, a WCB bill),
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
  context or browser has been closed"; the rerun passed all its steps, 40 then). Three need the
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
  statement on screen, and answered a database failure with 400; a double click on a legacy
  editor button that saves (Save, Sign & Save) posted the editor twice, and the second post ended
  on the concurrency page; the legacy save of an editor without an
  appointment kept the visit date of another window's entry, which Sign, Save and Bill put on the
  bill; Update Invoice accepted a bill number its receipt view refuses.
- **Not driven in a browser:** see the coverage round below.

### Coverage round: every changed area driven in a browser

After the final review, every behaviour this PR changes was mapped to a browser check. The gaps a
page or its own request reaches were closed with a new check or step, run on this branch and, as a
negative control, against `release/2026.08` or against this branch before the fix; the rest are
listed at the end of this section with the reason. Against `release/2026.08` the controls ran
scratch copies of the checks that continue past a failing step, with any assertion on what only this
branch has relaxed. Every run's cleanup removed the owned rows by key and asserted the owned records
gone (each child row too, since the final review).

| Check (new or extended) | What it drives | Negative control |
|---|---|---|
| `chart-session-state-two-window-isolation` (new) | Chart note list and search requests with no patient, a malformed one or two different ones are refused (400); a Disease Registry add by GET is refused (405), naming a patient or not, and writes nothing. An issue filter applied in A's chart is not applied to B's note list, which loads first. A new note from the notes service (`POST ws/rs/notes/{patient}/getCurrentNote`) for A's chart, entered as a face-to-face visit, and for B's, entered after it as a telephone visit, carries its own chart's visit type. | On `release/2026.08`, `viewNotesOpt` with no patient answers 200; B's list consumes A's filter and lists none of B's notes; both new notes answer 500 (#4784), and with only that fixed, A's new note takes B's telephone visit type. |
| `session-state-two-window-isolation` (three steps added) | Clear New Message in A's compose window leaves B's window's attachments in place. A transfer post (`PostItems`) opens a compose window of its own, under a new key, in the window that posted it, with the posted items; a compose window opened before it sends none of them. Reply, Forward and Compose opened from one message in three windows each start with the items saved for it, and the patient PDF attached in the reply goes out with the reply only. | On `release/2026.08`, clearing A's window leaves B's message with none of its PDFs, and B's message carries the transfer's items. On this branch before the last fix, the forward carries the PDF attached in the reply's window. |
| `echart-two-patient-isolation` (step extended) | The immunization dose window, opened after the other chart was entered, names its own patient and the logged-in provider. | With the previous `ScheduleEdit.jsp`, A's dose window names the other patient. |
| `pmm-attach-note-two-window-isolation` (new) | A's and B's charts each save a note and open the PMM Attach popup, B's last; each popup's client search for C attaches its own chart's note, so C gets one copy of each. | With only the popup's search form repaired, A's popup attaches B's note. |
| `rx-drug-pick-patient-isolation` (new, DrugRef) | A drug picked from the Rx search box is staged and saved for the page's own patient, on `rx/choosePatient` and on `rx/prescribing`, after another window showed B's allergies. | The prescribing page stages the pick for B. |
| `rx-preview-visit-site-isolation` (new, `multisites=on`; SKIP otherwise) | The Rx print window preselects the address of the patient's own day-sheet visit after another patient's visit was opened. An address picked by hand stays the remembered one for a patient with no visit. A new multisite tickler starts on "select clinic", and picking a clinic lists its providers. | A's print window shows B's clinic; C's shows B's clinic too, because loading B's print window stored its preselection. |
| `billing-bc-two-window-isolation` (WCB step added, BC) | A WCB bill (Bill WCB lists A's WCB forms only) opened before B's bill in the other window: a Continue with a code that needs a complete WCB report is refused back to A's own bill form, and a Continue with a code that needs none is saved for A on A's appointment with A's WCB form. | With the WCB fixes below patched in, A's window comes back from the refused Continue showing B's bill. |

The Rx and allergy checks were also run with DrugRef, in its own Tomcat using the packaged
context descriptor. All pass on this branch: `rx-stash-patient-isolation`, `rx-stash-deletion`,
`popup-opener-rx-drug-search`, `rx-edit-discontinue`, `double-submit-rx`, `boundary-rx-drug-name`,
`allergy-rx-alert`, `allergy-class-shortcut-alert`, `allergy-custom-lifecycle`,
`allergy-injected-form-csrf`, `allergy-stale-amend-refused`, `concurrency-allergy-amend`,
`drug-search`, `prescription-signature`, `rx-preview-pharmacy`, `rx-print-profile`,
`rx-legacy-null-fields`, `rx-profile-legend`, `rx-interaction-warnings`,
`gap-encounter-rx-pharmacy-order`, `rx-med-history`. Two fail the same way on
`release/2026.08`:
- `rx-favorites-choose-drug` passes every step but sometimes fails on one aborted
  `rx/interactionWarnings` request, as often on the base build;
- `rx-drug-name-quotes` stores the custom name "Crème" garbled.

The tickler, Rx preview and BC checks were re-run on this branch after the fixes they cover:
`tickler-crud`, `tickler-attachments`, `boundary-date-tickler`, `search-sort-tickler-list`,
`gap-encounter-rx-script-notes`, `rx-preview-pharmacy` (single site), `billing-bc-associations`,
`billing-bc-simulation-encoding` and `billing-bc-receive-payment` pass. On the final build, every
check in the table passes, and so do `immunization-schedule-config`, `messenger`,
`messenger-attachments`, `messenger-inbox-actions` and `billing-bc-associations`;
`double-submit-messenger` fails as its manifest entry documents for 2026.08 (Send Message has no
duplicate guard).

**Fixed here because these tests found them:**
- **The PMM attach-note popup could not search** (`casemgmt/attachClient.jsp`). It had no
  `<head>`, so CSRFGuard gave its form no token and every search answered 403. Its criteria were
  checkboxes, its two flags posted "on", which Struts cannot convert, and its stylesheets and reset
  were broken.
- **No PMM client could be viewed or admitted with dependents.**
  `ClientManagerImpl.jointAdmissionDao` was never injected: the bean is wired by name, and the DAO's
  bean name differs. `@Autowired` on the setter; `ClientManagerImplWiringUnitTest`.
- **The Rx print window printed clinic names unencoded** in its address list.
- **The multisite tickler page's visit lookup was removed.** It looked up the visit's clinic as if to
  preselect it, but never applied it, here or in the code inherited from OpenO.
- **BC "Bill WCB" could not be used.** The WCB form list loaded a relative `wcbForms.jsp` (404). A
  submit reloaded the list and cleared the chosen form while the diagnosis was being checked. The
  create action's WCB check looked up `BillingmasterDAO` by type, of which there are two beans, so
  every WCB Continue answered 500.
- **The notes service answered 500 for every new note** (#4784).

**Found by the review of this round, and fixed:**
- **Compose windows opened from one message shared that message's key,** so a chart PDF attached in
  one went out with the message sent from another, to its recipients. Each compose window now starts
  with its own copy of the saved items (`MsgAttachmentStaging.composeKey`); the step above pins it.
- **The immunization schedule's Save wrote whatever patient number was posted to it,** with no
  patient-level check. It now checks its patient as the template add and the set delete do.
- **The chart's Disease Registry add answered a GET and wrote with no privilege check.** No page
  calls it; it now takes a POST with `_dxresearch` write for the patient and access to the record.
- **The PMM attach popup** rendered its messages above the doctype, which put the page in quirks
  mode, and its result table's paging links repeated the search form's CSRF token in their URLs.
- **Two compose windows' attachment choosers shared one window name,** so opening the second took
  over the first one's chooser window, and the first compose window's chooser was gone.
- **A BC bill step checked that its bill was still open outside the session lock,** so a bill
  dropped in the meantime could come back.
- **The PMM attach copied a note on a GET, as the source note's newest revision.** Reachable once
  the popup's search worked, `IncomingEncounter?PEAttach=yes` wrote the copy on a GET, which
  CSRFGuard does not check, with only `_demographic` read, into any chart named by `selectId`, and
  kept the source note's uuid, appointment and issue links. The copy thus became the source note's
  newest revision, and the source chart's next save of that note was refused (403). The attach now
  takes a POST that names the chart it opens as its client, needs `_casemgmt.notes` read for the
  note's patient and write for the client, and writes a new note of the client's (new uuid, no
  appointment or issues). A GET copies nothing.
- **Sending did not release the chart items saved for a message,** so every later reply or forward
  of it in the login attached them again; a send now releases them. A reply or forward opened after
  the session had released them is refused once with the reason, like a window whose own
  attachments were released.
- **The PMM attach popup's Status filter was never applied:** its select was named `active`, and the
  search reads `criteria.active`, so discharged clients were listed whatever was chosen.
- **A BC WCB Continue billed whatever WCB form was posted,** so a crafted post could bill another
  patient's WCB report for this patient. It bills only the bill's own patient's WCB form now, and
  answers anything else with 400.
- **SonarCloud's reliability gate** failed on the attach popup, which this change made a whole page
  (no `lang`, search fields without labels, `<font>` hints); it now has labels, the request's
  language as its `lang`, and `<small>` hints.

**Found, not fixed here (filed):**
- The WCB form page cannot be opened at all (#4864). The WCB step therefore bills against a WCB
  form seeded in the database.
- The multisite tickler list's script breaks when a clinic has more than one provider (#4865).
- The PMM client view's results name Tiles pages that do not exist (#4561).
- The PMM client search writes the patients it lists (#4835).
- The rest are added to #4811, among them the legacy chart navigation's message list, whose
  `messenger/ViewMessageByPosition` forward hands the message view no message id, so it shows the
  inbox (on `release/2026.08` too).

### Second coverage round

An audit of the first round listed what was still driven only by unit tests, or not at all. Each
item now has a browser step, run on this branch and, as a negative control, against
`release/2026.08` or against this branch with only the fix in question undone. As in the first
round, the controls against `release/2026.08` ran scratch copies of the checks that continue past a
failing step; where a step still stopped there before its crossover, a probe drove the requests in
question directly, as the table says. Every run's cleanup removed the owned rows by key and asserted
them gone.

| Check | Steps added | Negative control |
|---|---|---|
| `session-state-two-window-isolation` (14 steps in all) | Each message's PDF list downloads its own message's PDF, byte for byte, after another message's list was opened (#4473); a malformed index answers 400, and an index the message does not have, or a request naming no message, 404. A chart PDF goes out only on a message linked to its own patient: linked to B, then to no one, the send is refused and the page keeps the subject, body and attachment; linked to A again, it goes out. The inbox and both patients' message lists, opened between attaching and sending, leave the compose window's PDF; each list shows only its own patient's messages, and a list naming no patient shows none. Two compose windows' attachment choosers, open at once, are two windows, each attaching to its own compose window. A compose window whose PDF was released for eight newer windows is refused once with the notice, then sends without it. The day sheet's visit store refuses a request without one valid appointment (400) or by GET (405). | On `release/2026.08`: A's list downloads B's PDF; the message linked to B is sent with A's PDF; opening the lists leaves B's message without its PDF; B's chooser takes over A's window. A probe finds the day sheet's visit store there answering 200 to each request naming no valid appointment (it refuses a GET with 405 there too). On this branch without the release notice, the compose window sends silently. |
| `chart-session-state-two-window-isolation` (6 steps in all) | A Disease Registry add by POST, with B's chart entered last: refused (400) without one valid patient, filed for the patient it names. A notes service save without a note body saves nothing. Write to Encounter from B's message, with B's new chart holding its note list back: A's note list, loaded meanwhile, does not carry the message, and B's new note does. | On `release/2026.08` a registry add without a patient answers 500, not 400, and the save without a note answers 500. B's message list there offers no Write to Encounter; opened from the inbox instead, A's note list carries B's message in its page's script. |
| `echart-two-patient-isolation` (43 steps in all) | The immunization schedule's Save, after the other chart was entered, writes the patient's next version by the logged-in provider; a Save, template add or set restore without one valid patient is refused (400) and writes nothing. A template made after a chart was entered from another provider's day sheet is the logged-in provider's. The legacy navigation lists only its own patient's messages. The notes panel, rendered again by a note save, names its own chart's entry provider after a chart was entered from another provider's day sheet. | On `release/2026.08` the immunization step stops at its first template add, which is not A's by the logged-in provider; a probe of its refused requests there adds the template, named for no patient or for A and B, to B's schedule, the chart entered last. The administration's template is stored as made by the provider of the chart entered last, the legacy navigation stops at its messenger list, and each notes panel names the logged-in provider rather than its chart's entry provider. The Save itself is a regression guard: `release/2026.08` also saved the patient posted, by the logged-in provider. |
| `legacy-encounter-editor-isolation` (11 steps in all; 12 on BC) | Verify & Sign verifies only A's own appointment; Split Chart stores A's whole encounter as the split revision and continues A's chart from its end; Exit closes the editor and saves neither chart; on a BC install with CAISI, Sign, Save & Bill opens a bill of A's own visit. All pass on an Ontario and a BC build. | `release/2026.08`'s editor saves nothing (it posts to an address that does not exist). With this branch's editor and the old save action, Verify & Sign and Split Chart fail, and on BC Sign, Save & Bill writes B's chart and opens no bill ("Billing session expired."); Exit writes nothing with either action. |
| `rx-preview-visit-site-isolation` | The print window's address list shows a clinic name with markup as written. | On `release/2026.08` the list writes the name into the page unencoded, so the name shows without its markup. |

**Found by this round and fixed:**
- **A message opened from a patient's message list never offered Write to Encounter,** on
  `release/2026.08` too: the view compared the message's linked patients with a `demographic_no`
  attribute that nothing set. Its button also posted the message id from a `messageID` attribute
  that nothing set, so the chart it would have opened was an error page. The view action now names
  the listing patient (digits only, for a view opened from a patient's list), the button posts the
  message shown, and the view reads its mode (`from`) from its own request rather than the session,
  where another window's view could change it (`MsgViewMessage2ActionUnitTest`).
- The CI `tests` job had failed since the message view's Compose link changed, on an assertion that
  still expected the old link (`ScheduleNavigationAssetRegressionTest`), and the i18n check refused
  the attach popup's hard-coded `lang="en"`, which now follows the request's locale. SpotBugs flagged
  the attach's redirect to the client's chart; it is the application's own route with a validated
  patient number, suppressed at that one method with the reason.
- In the checks: the legacy editor check read the editor's script before a redisplayed editor had
  run it, and the new notes-panel step left the chart page naming the other provider for the steps
  after it, until it saved a note again under the logged-in provider's entry. The Write to Encounter
  step searched A's note list for the message as written, but the list writes it into a script,
  JavaScript-encoded, which escapes the run marker's hyphen, so the search could not find it even on
  `release/2026.08`. It now searches for a part the encoding leaves as written, and checks that B's
  own page carries that part.

**Checked, not changed:** the compose page's patient search popup also has one window name for every
compose window. In Chromium a second search takes over the first popup, and a pick fills the
compose window that opened the search last, so nothing crosses windows.

### Final review

Two independent reviews of the finished branch, one of the product code and one of the checks and
this record, found the following; each was fixed here.

- **A second compose window opened from a message could still send without its saved items,
  unannounced.** Once the session's cap had released the items saved for a message, the first
  Reply, Forward or Compose opened from it was refused once with the reason, but it also cleared
  the message's mark, so a second window opened from the same message sent without them, with no
  notice. Every window opened from that message is now told once
  (`MsgAttachmentStagingUnitTest`).
- **A refused send came back without its recipients.** The new refusals (chart documents prepared
  for another patient, attachments released) showed the compose page again with its subject, body,
  patient and attachments, but no recipient ticked, so sending again, as the notices say, answered
  "No valid recipients selected." The recipients ticked on the refused message are ticked again
  (`MsgCreateMessage2ActionTest`). Both tests fail without their fix.
- **Assertions that could not fail, in the checks:**
  - `pmm-attach-note-two-window-isolation` asserted that C's copy has no appointment, but the
    source charts were opened without one. Each source chart is now entered for a visit of its
    own, the source note is asserted to carry it, and with an attach that keeps the source's
    appointment the check fails there ("C's copy of A's note keeps A's appointment").
  - The chooser step's assertion that B's chooser had not taken over A's compared two distinct
    page objects. It now reads A's chooser, which must still be A's own.
  - The Write to Encounter step's check of A's note editor read an editor that the step never
    reloads, and the BC step's check that a bill window which only continued saved no bill
    checked a request that never saves one. Both are removed; the step's other assertions pin
    what they claimed.
- **Cleanup assertions.** Some child rows were deleted by key without being asserted gone: note
  links and extensions, note locks and drafts, message deliveries and patient links, admissions,
  prescription signatures and the BC physician's memberships. Each check now asserts them gone.
- **This record:** the controls against `release/2026.08` ran scratch copies of the checks, as now
  stated; the visit store's answers there came from a probe; a PDF download that names no message,
  not one without a PDF, is the browser's 404 case; and the step counts and the JSP count were
  updated.
- **A flaky step.** `echart-two-patient-isolation`'s last step sometimes failed on the note-lock
  release beacon that its own navigation aborts, because the browser reported the abort after the
  step had excused its releases. The step now waits for each release to settle first.

Checked, not changed: the legacy editor's provider fields come from the patient's latest entry,
but every entry of one login names the logged-in provider (the day sheet's link sends it), so
two windows cannot differ there. The current chart's Write to Encounter message comes from the
chart's own page, so another chart entered meanwhile cannot lose it.

**Still not driven in a browser,** with the reason:
- the eChart text replacement and the draft-restore signal, in the chart check's header;
- the PMM client view (#4561);
- the legacy chart navigation in a page: the current chart never shows that layout (#4811); its
  route is requested, with its message list;
- the Messenger transfer page itself: it cannot render on any install (#4811); its post is driven;
- the Rx stash-delete, delete and clear-pending results, which only AJAX calls or unlinked forms
  reach;
- the `rx-fax-*` checks, which need a fax account fixture, a provider stamp and a fax document
  directory. A fax takes its clinic address from the same address list as a print, which is driven.

Left out deliberately, with locations: #4811. The dashboard's per-provider state shared across
windows and users is noted on #3947.
