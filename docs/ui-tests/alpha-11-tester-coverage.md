> Promotion follow-up: the observations below describe the original alpha11
> investigation. PR #3644 imports these checks with stricter assertions for
> alpha12: editor/billing exceptions, missing signatures, lost appointment IDs,
> broken form redirects, inaccessible save controls, and missing specialist
> assignments now fail the checks. Patient ticklers are checked in the patient
> view, and the default chart patient is now demographic 1. The vaccine check
> also exercises local CVC lot lookup. See the promotion PR for current results;
> historical warnings below are not accepted promotion outcomes.

# Alpha 11 tester report → Playwright coverage map

An alpha-11 tester (main branch) reported the workflows below as working. This
page records which `scripts/*-playwright-checks.js` pins each one so a
regression fails a script instead of waiting for the next manual pass. Every
script logs in as `carlosdoc`, drives the real UI the way the tester did,
verifies the resulting database rows, and deletes what it created. Run them
with the environment contract in
[deb-install-validation.md §6](deb-install-validation.md#6-run-the-suite)
(`BASE_URL`, `TEST_*`, `MYSQL_*`, `CHROME_PATH`) against a disposable dev
database.

| # | Tester item | Coverage | Script(s) |
|---|-------------|----------|-----------|
| 1 | Adding new providers | pre-existing | `add-login-account-playwright-checks.js` (seeds its provider through Administration > Add a Provider) |
| 2 | Adding administration privileges | pre-existing | `assign-role-playwright-checks.js` (Assign Role to Provider offers and adds the `admin` role), `add-login-account-playwright-checks.js` (login account) |
| 3 | Making schedule templates | **new** | `schedule-template-crud-playwright-checks.js` (create / edit / delete a day template through Template Setting); `schedule-setting-playwright-checks.js` already applied an existing template |
| 4 | Making demographics | pre-existing | `demographic-add-playwright-checks.js`, `demographic-master-crud-smoke.js` |
| 5 | Billing code updates | **new** | `billing-service-code-admin-playwright-checks.js` (search, update fee/description, restore, add a new code) |
| 6 | Messaging from messenger and from the chart, linked to the patient | **new** | `messenger-playwright-checks.js` (contact enrolment, compose from the inbox, compose from the chart's Messenger "+", inbox and message view show the linked patient) |
| 7 | Appointments from the schedule and from the search widget | pre-existing + **new** | `echart-new-patient-notes-playwright-checks.js` (day-sheet slot), `schedule-quick-search-appointment-playwright-checks.js` (quick-search Appt badge → next available slot → booked) |
| 8 | eChart: sign, save and bill a note | **new** | `echart-note-sign-bill-playwright-checks.js` (Save, Sign & Save, Sign Save & Bill hand-off to the Ontario bill form); `echart-playwright-checks.js` covers CPP items and first render |
| 9 | Vitals: add, double-click to metric, automatic BMI | **new** | `echart-vitals-bmi-playwright-checks.js` (Anthropometrics group: 5'10"/150 lb → 177.8 cm/68 kg, BMI 21.5, rows saved) |
| 10 | New Rx and repeat Rx through to fax or print | pre-existing | `rx-fax-signature-stamp-playwright-checks.js`, `rx-fax-reprint-represcribe-playwright-checks.js`, `drug-search-playwright-checks.js`, `rx-preview-pharmacy-playwright-checks.js` (the browser print path itself is not asserted) |
| 11 | Preventions including the brand picker | **new** | `prevention-brand-picker-playwright-checks.js` (pick a brand, pre-filled popup, lot, saved rows, listed) |
| 12 | Rourke 2017 form | **new** | `form-rourke2017-playwright-checks.js` (auto-populated identity, feet/inches conversion, Save and Exit, reopen latest) |
| 13 | Add Penicillins allergy | **new** | `allergy-add-penicillin-playwright-checks.js` (Penicillin shortcut, reaction form, saved row, shown in the chart) |
| 14 | ON billing including 3rd party WSIB and bonus | **new** | `billing-on-submit-playwright-checks.js` (OHIP, WSIB and Bonus bills saved through Next → Review → Save); `billing-on-third-party-playwright-checks.js` stays read-only on bill-type navigation |
| 15 | The timer | **new** | `echart-note-sign-bill-playwright-checks.js` (counts, pauses, pastes Start/End time into the note) |
| 16 | PDFing eForms | pre-existing | the `eform-*` scripts, notably `eform-rtl-print-pdf-playwright-checks.js` and `eform-test-pattern-playwright-checks.js` |
| 17 | Ticklers manually and via macro | pre-existing + **new** | `tickler-crud-playwright-checks.js` (manual), `lab-macro-tickler-playwright-checks.js` (lab recall macro → tickler + acknowledgement) |
| 18 | eDocs upload | pre-existing | `document-upload-playwright-checks.js` |
| 19 | Consultation requests | pre-existing + **new** | `consultation-request-create-playwright-checks.js` (service/consultant pickers → Submit → row, reopen, listed); the `consultation-signature-*` scripts cover stamps and print preview |
| 20 | Adding consultants including CPSO lookup | **new** | `specialist-add-cpso-playwright-checks.js` (CPSO widget with a stubbed registry answer plus a live-proxy degradation probe, add, assign to a service, offered in the consult picker, delete) |
| 21 | Search widget including setting appointments | pre-existing + **new** | `patient-search-dob-playwright-checks.js` (Search popup), `schedule-quick-search-appointment-playwright-checks.js` (`#quickSearch` widget and its Appt badge) |

## Fixture notes for the new scripts

- All of them default to demographic 1 and provider `999998` (carlosdoc) from
  the demo dataset unless noted; every one cleans up in a `finally`.
- `echart-note-sign-bill`, `messenger` and `allergy-add-penicillin` default to
  demographic **2**: demographic 1's chart notes panel answers 500 on the demo
  dataset because its HRM rows point at report files that never shipped (see
  the review findings from the alpha-11 coverage pass).
- `messenger` enrols `999998` as a messenger contact through Messenger Group
  Admin when the install has none (the demo seed ships none) and removes that
  enrolment again.
- `lab-macro-tickler` stages an unacknowledged `providerLabRouting` row for the
  provider on the first HL7 lab with a patient, defines the macro through
  Preferences > Lab Recall Macros, and restores the routing row and the
  provider's `labMacroJSON` property afterwards.
- `billing-on-submit` and `echart-note-sign-bill` insert their own
  appointments (SQL) so no demo appointment is billed or charted.
- `billing-service-code-admin` edits `A007A` and restores it, and adds/deletes
  `X987Z`.
- `specialist-add-cpso` stubs `/encounter/CpsoSearch` with `page.route()` for
  the deterministic pick; the live proxy is still probed once and must answer
  200 with results or `CPSO_SERVICE_UNAVAILABLE`.
- `schedule-quick-search-appointment` needs the patient's MRP to have a schedule
  template applied within the next 90 days (`FindNextAvailableSlot`); the demo
  dataset's `999998` does.
- `form-rourke2017` uses Save and Exit; plain Save's redirect is probed and
  reported as `WARN` when it answers a broken page (it does on a stock install).
- Chart buttons (Save / Sign & Save / Bill) are triggered through their click
  handlers because the encounter layout places that button row below the
  viewport in a headless window; the request/response and rows are still the
  real ones.

## Deb-path validation (alpha-11 packages, 2026-09-12)

The 13 new scripts were also run against the published `2026.08.0~alpha11`
`.deb` packages (carlos-emr, carlos-emr-drugref, carlos-emr-eform-renderer)
installed non-interactively into an Ubuntu 26.04 container, through the nginx
front door on `:443`, following
[deb-install-validation.md](deb-install-validation.md). The container host
could not boot systemd (cgroup v1), so MariaDB, `carlos-ctl db-apply-settings /
db-users / db-migrate / bootstrap-admin / demo-data`, the drugref load,
chromedriver, Tomcat (`carlos-emr-tomcat run` as `carlos`) and nginx were
started by hand; the forced first-login reset went through the
`drugref-update-playwright-checks.js` step from §6.

Result: 10 of 13 `PASS` on the stock alpha-11 install. The three failures are
deployment findings, not script defects:

- `consultation-request-create` and `specialist-add-cpso`: the Ontario
  reference seed ships every `consultationServices` row with `active = '02'`
  (finding 21), so the service picker and the Add Specialist specialty list are
  empty on a fresh ON install. Both scripts pass once the rows are `'1'`.
- `echart-note-sign-bill`: the alpha-11 WAF policy 403s the note save as soon
  as the timer's `Start Time:` line is pasted (finding 22). With the
  `release/2026.08` policy files (`debian/assets/modsecurity/`, PRs #3623 and
  #3625) copied into `/etc/carlos-emr/modsecurity/` and nginx reloaded, the
  script passes through `:443`.

## Observations to review (found while writing the checks, 2026-09-12)

Recorded here so the checks' `WARN` lines and allow-lists have a home; none of
these are fixed by this change.

1. Consultation request confirmation never says "Created"/"Updated": the save
   302-redirects to `ViewConfirmConsultationRequest` and `transType` is a
   request attribute that does not survive the redirect.
2. `billingONReview.jsp` `onSave()` reads `#payee`, which only renders for
   3rd-party bills, so OHIP/WSIB/bonus saves throw a `TypeError` in `onsubmit`
   (the bill still saves; `checkTotal()` is skipped).
3. Ontario bill form with `default_view=GP`: no favourite-code grid is visible
   until a Billing form is chosen (no `ctl_billingservice` type is `GP`).
4. Ontario bill form: the billing physician defaults to the first provider with
   an OHIP number, not the appointment provider; `999998` has no `ohip_no` in
   the demo seed and never appears in the list.
5. `eform-consultation-acceptance-playwright-checks.js` still expects
   `#specialist` to be a `<select>`; the form now uses hidden `#specialist` +
   `#specialistInput` autocomplete, so that script silently takes its
   programmatic fallback.
6. Consultation form requests `providerSignatureImage?providerNo=...` which
   404s for providers without a stored signature (console error on every open).
7. Demo seed ships 6 `messagelisttbl` rows without `messagetbl` parents and no
   messenger contacts (`groupMembers_tbl` empty): the first messages on a fresh
   dev DB inherit orphan deliveries, and compose has no recipients until an
   admin enrols one.
8. Message bodies are stored as Toast UI markdown (underscores escaped) — check
   the viewer renders markdown rather than raw escapes.
9. Add Specialist's Specialty (`specType`) does not make a consultant
   selectable in the consultation request; only the `serviceSpecialists` join
   (Show All Services > service > Update) does. 63 demo specialists, 7 mapped.
10. `/encounter/ShowAllServices` without `serviceId` renders `CARLOS Error: 500`.
11. Rourke 2017 plain Save redirects to
    `/form/forwardname?form_link=formrourke2017complete.jsp&...`, which answers
    200 with no `Content-Type`, so the browser shows the form's HTML source as
    text. The row is saved; Save and Exit is unaffected. The same route answers
    500 for a row with NULL page-1 columns and 400 without the `.jsp` suffix.
12. Patient tickler view (`ViewTicklerMain?demoview=N`) has no date controls
    and `ListTicklers` applies a default window when dates are blank, so
    future-dated ticklers (e.g. a 2-week lab-macro recall) never show there.
13. `providercontrol` treats any `provider_no` parameter as the week view and
    hides the per-appointment E/B links even on `displaymode=day`; deep links
    (including the quick-search Appt navigation) carry `provider_no`.
14. Rourke 2017 page declares no favicon; browsers 404 on `/favicon.ico` at
    the server root on every open.
15. eChart note editor: every keystroke throws `TypeError` from
    `getActiveText()` in `js/newCaseManagementView.js.jsp` (writes to a
    `keyword` element the layout no longer renders).
16. Encounter layout: the Save / Sign & Save / Bill button row sits ~250 px
    below the bottom of the viewport at 1600x1100 and 1920x1400 with no
    ancestor scrolling it into view (worth checking at common laptop sizes).
17. Demo demographic 1's chart notes panel answers 500 (`method=edit`) because
    HRM rows 25-35 point at report files that never shipped; a missing HRM file
    should not take down the notes panel. Demographics 2 and 3 load cleanly.
18. A leftover `casemgmt_tmpsave` draft / stale `casemgmt_note_lock` rows from
    an interrupted session also made the notes panel answer 500 until cleared.
19. Notes saved from an appointment-opened chart carry `appointmentNo = 0`
    (0 of 591 demo notes have one); billing still finds the appointment from
    the form's hidden field.
20. (static review, unverified live) `ChooseAllergy2.jsp`'s search form has no
    `name`/`id` while its scripts reference `document.RxSearchAllergyForm`;
    and `prevention/index.jsp` / `AddPreventionData.jsp` call a `/cvc` endpoint
    that has no Struts or servlet mapping (lot-number lookups will 404).
21. (deb) `database/mysql/migration/on/V1.0.2__on_data.sql` seeds all 257
    `consultationServices` rows with `active = '02'`; the BC seed uses `'1'`
    and the DAO filters on `'1'`. A fresh Ontario package install therefore has
    no selectable consultation service and no specialty on Add Specialist
    until an admin activates them. The dev/demo database hides this because
    `development.sql` truncates the table and reseeds six active services, and
    the additive demo build excludes the table.
22. (deb, fixed in `release/2026.08`) The alpha-11 WAF policy inspects
    `ARGS:caseNote_note` with the full CRS set, so any encounter note with a
    line starting `Start `, `Type `, `Find ` (Windows-RCE rule 932115, e.g.
    the timer's `Start Time:` stamp or "Type 2 diabetes" on a new line) is
    answered with nginx's 403 and the note is not saved. Probed unauthenticated
    per the runbook: nginx 403 (146 bytes) on alpha-11, the application's CSRF
    403 with the release policy. PRs #3623/#3625 carry the fix; the release
    policy should stay in the next alpha's packages.
