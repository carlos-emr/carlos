# Preventions coverage: clinical options, amendments, eligibility and recall (#5279)

Issue #5279 listed the Preventions behaviour that no browser check reached. Ten checks now cover it, one per section
of the issue, so a defect in one section cannot stop the others from running. Each owns a synthetic `FAKE-PW` patient
through `runWorkflow`, works through the patient's Preventions page (E-Chart ▸ Preventions) and its add/edit popup,
and reads back what reached MariaDB. SQL only arranges owned fixtures (ages, a history record, catalogue rows, a
saved patient query), verifies what was stored and cleans up; every clinical action is taken in the page. Every
amendment is also checked against the existing contract of `AddPrevention2Action`: the edited record is archived with
its own values (`deleted=1`), and exactly one live replacement names it in `previousId`.

Shared helpers live in `scripts/lib/prevention-coverage.js` (Preventions page, popups, save and amendment checks,
printing, the Prevention Recommendations box, the application's own "today") and
`scripts/lib/prevention-h1n1-options.js` (the H1N1 form's categories and boxes). Two node tests run in CI with
`npm run test:scripts`: `scripts/prevention-h1n1-options.test.js` holds the option lists to `AddPreventionData.jsp`
and `AddPrevention2Action`, and `scripts/prevention-eligibility-rules.test.js` holds the reminder table to the
documentation in `prevention.drl`.

| Section | Check | What it asserts |
|---|---|---|
| 1. Influenza chronic-condition options | `prevention-h1n1-chronic-condition` | H1N1 (the only type on the `h1n1` layout) opens at No and offers No, Yes and nine categories; each category is stored as its exact option value, reopens selected and stays on the archived record when changed; returning to No stores `false`. All mismatches are collected before the step fails. |
| 2. Pregnancy and remote-location flags | `prevention-h1n1-pregnancy-remote` | Pregnant alone, Remote alone, both, and cleared: ticked boxes store `true`, clear boxes store no row at all (no stale value survives clearing), the archived record keeps its own boxes, and every box on the form reopens as saved. |
| 3. Healthcare-worker categories | `prevention-h1n1-healthcare-worker` | As section 1 for No, Yes and the eight categories, including acute care, laboratory and pharmacy, with every change between categories archived. |
| 4. Household-contact and first-responder flags | `prevention-h1n1-first-responder` | Household contact, police and firefighter alone, all three and cleared, while the pregnancy, chronic-condition and healthcare-worker values, the lot and the comment never change. A record saved before the police/fire split (one `firstresponder` value) reopens with both boxes ticked and saving stores both. |
| 5. First/second-dose flags | `prevention-h1n1-dose-flags` | The form's semantics, asserted explicitly: First Dose and Second Dose are independent boxes on one record. Each alone, both, and cleared are stored exactly; ticking one never clears the other; nothing is scheduled (one live record, no next date). CARLOS has no automated vaccine series and the check does not invent one. |
| 6. Screening result Other | `prevention-screening-result-other` | FOBT Result Other with an explanation is stored, listed on the Other colour, reopened with its summary and printed as `Result: other (…)`. The form has no required-explanation rule: Other without one is stored and prints `(no reason submitted)`, as `PreventionPrintPdf` intends. The one rule that refuses a save, the required date, posts nothing, changes nothing and keeps the clinician's entries, which then save. Amending to Abnormal keeps Other on the archived record. |
| 7. Manual dose, unit and route amendments | `prevention-dose-route-amendment` | A Tdap recorded through the brand picker (ADACEL, 0.5 mL IM) is amended to 1 vial SC: the replacement stores both, keeps the vaccine name, DIN, manufacturer, lot, site and comment, and the original is archived unchanged; the reopened form, its summary and the printout show the new dose (the printout has no route line). LAST, a dose saved without a unit must reopen with that dose and survive an unchanged save: it does not (finding 322). |
| 8. Dated vaccine-lot expiry | `prevention-cvc-lot-expiry` | A catalogue vaccination's brand offers two dated synthetic lots with nothing chosen; choosing one fills its expiry date, which is stored and reopens; changing lot replaces the date in the form and in the replacement, while the archived record keeps the first. When a refreshed catalogue no longer lists the recorded lot, the lot and its recorded expiry are kept through a save. `prevention-brand-picker` keeps its undated and historical-lot cases unchanged. |
| 9. Recommendation eligibility matrix | `prevention-reminder-eligibility-matrix` | Expectations come from `prevention.drl`'s documentation, not its conditions. Seven rules (MAM 3, AAA 1, FOBT 2, HPV-CERVIX 3, Bone Mineral Density, Smoking, Inf 3) for women and men on the last day before and the first day of every documented boundary (13, 25, 40, 45, 65, 70, 75, 81), in the documented box. Then history: a FIT 25 months old and its date amended to 6 months; a FIT completed externally; a colonoscopy superseding FIT; an ineligible mammogram and HPV-CERVIX; a mammogram coming due at 24 months and overdue once amended to 25; LDCT for a current smoker, withdrawn by amending the assessment to non-smoker; AAA and BMD cleared by new records. |
| 10. Recall through completed intervention | `prevention-recall-completed-intervention` | The Mammogram recall as of a fixed 2026-06-30 reports the owned patient and an untreated control Overdue; a mammogram entered on the patient's Preventions page makes the rerun report Pending, its result amended to Normal Up to date, its date corrected to 2024-01-15 due. The control stays Overdue on every run, and each rerun keeps its query, type and as-of date. |

Found while covering section 7, `prevention-brand-picker-units-routes` opens the brand picker once for each distinct
dose unit and route in `prevention/vaccine-brands.json` and fails while the form drops or replaces them (finding 323).

## Dates and clocks

The reminder rules count ages and intervals on the application server's clock. The package runs Tomcat in
`CARLOS_TZ` (America/Toronto by default), which need not be the database's time zone, so the matrix takes "today"
from the date the application offers on a new record rather than from `CURDATE()`, and fails if that date changes
during the run. The recall report's as-of date and every recorded date in sections 1–8 and 10 are fixed.

## Prerequisites and skips

All ten checks run on a default install (Ontario, BC or other) with the demo dataset and need the common `MYSQL_*`
contract. `prevention-dose-route-amendment` and `prevention-brand-picker-units-routes` SKIP when `cvc.url` replaces the
brand picker; sections 6 and 7 SKIP without Poppler `pdftotext`; the matrix SKIPs when the clinic loads its own rules
from `ResourceStorage`. A `PREVENTION_FILE` override cannot be seen from the browser and would show as mismatches.

## Defects recorded separately

Recorded in [app-findings-log.md](app-findings-log.md) section 27; the two failing checks carry `expectedFailure`
entries in `scripts/playwright-suite.json`, so the runner reports them as known failures and still fails on anything
else:

- **322:** a dose stored without a recognised unit reopens with an empty Dose box, and an unchanged save stores an
  empty dose (`prevention-dose-route-amendment`, last step).
- **323:** the brand picker drops or replaces the catalogue's dose unit or route when the form does not offer it
  (`prevention-brand-picker-units-routes`).
- **321:** the `Smoking` rule's comment calls it a warning while it raises a reminder; the matrix asserts only its
  documented age range until the intent is settled.

## Excluded

DHIR submission, the SSO/ISPA/non-ISPA warning controls and their integration prerequisites (item 11 of the source
inventory) are out of scope, as the issue states.

## Validation

Packaged install: an unstamped development build (`carlos-emr` and `carlos-emr-drugref` 2026.09.0~snapshot27, WAR
2026.08.0-beta3-SNAPSHOT) of `release/2026.08` at `e2517398`, made with `dpkg-buildpackage` in an `ubuntu:26.04`
container, with the pinned `carlos-ctl` 1.2.1, installed by `scripts/deb-docker-validation.sh up` (Ontario, demo
data, secure first-login reset; `carlos-ctl check` passed) and reached through the nginx and ModSecurity front door.

- Every new check passed except the two known failures above; with their `expectedFailure` entries the runner reports
  them as `known-fail` at their labelled steps.
- All 21 existing prevention checks were run alongside them: 20 passed, and `prevention-partial-date` failed at the
  step its manifest note already records (a year-only date, #4814).
- `scripts/deb-docker-validation.sh audit` passed: no unexplained server-log signature.
- After the runs no `FAKE-PW` patient, saved query, synthetic catalogue row or orphaned prevention remained.
- A mutation run (MAM 3 starting at 41, Bone Mineral Density expected as a warning) failed the matrix with fifteen
  mismatches, so its first step is not vacuous.
- `npm run test:scripts` passed, including the three new node tests.
