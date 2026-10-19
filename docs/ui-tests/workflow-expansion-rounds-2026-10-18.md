# Thirty-one workflow expansion rounds — 18 October 2026

> Publication context: these results and finding numbers describe the original
> pinned package validation. See the [release integration and finding-ID crosswalk](coverage-publication-2026-10-18.md)
> for the current 786-entry manifest, retained check variants and merged fixes.

Target: `release/2026.08` at `a7611d07b4ae9b237b4a5d252fa938e09208eb85`.
The requested current round plus 10 and then 20 further rounds total **31**.
This expansion registers **62** additional checks: 18 general clinical/task checks
and 44 Rx checks. Earlier batches added 21, so the cumulative manifest changes
from **692 to 775**. External administration is excluded. Passing checks remain
on `codex/ui-workflow-coverage-2026-08` alongside the defect reproductions.

| Round | Workflow / task | Registered checks |
|---|---|---|
| 1 | Episode diagnosis selection and literal descriptions | `episode-coded-diagnosis`; `episode-literal-description` (finding 296) |
| 2 | Embedded internal/external health-care team | `health-care-team-embedded-actions`; `health-care-team-external-picker` |
| 3 | Consultation date/completion filters and overdue follow-up | `consultation-date-completion-filters`; `consultation-overdue-tickler` (finding 297) |
| 4 | Paged bulk task actions | `tickler-last-page-complete`; `tickler-first-page-delete` |
| 5 | Patient set repeat and empty saves | `patient-set-repeat-save` (finding 82); `patient-set-empty-query` |
| 6 | Message Select All actions | `messenger-select-all-read-controls`; `messenger-select-all-archive-restore` |
| 7 | Appointment search filters and limits | `appointment-search-weekday-time-code`; `appointment-search-result-limit` |
| 8 | Prevention reminder suppression and resumption | `prevention-next-date-never-remind`; `prevention-resume-reminders` |
| 9 | Saved Rourke record and lab requisition corrections | `clinical-followup-rourke-revise`; `clinical-followup-labreq-revise` |
| 10 | Indications | `rx-indication-archive`; `rx-indication-patient-registry` (finding 298) |
| 11 | CPP visibility | `rx-cpp-visibility-roundtrip`; `rx-cpp-visibility-save-refusal` (finding 299) |
| 12 | Structured dose and PRN | `rx-inline-structured-dose-save`; `rx-inline-prn-toggle` |
| 13 | Empty final saves | `rx-empty-save-only`; `rx-empty-save-print` |
| 14 | Cancel custom entry | `rx-custom-drug-decline`; `rx-custom-note-decline` |
| 15 | Renewal long-term / past-medication | `rx-renewal-long-term`; `rx-renewal-past-medication` |
| 16 | Renewal compliance / authoritative | `rx-renewal-compliance`; `rx-renewal-authoritative` |
| 17 | Renewal outside-provider / no-substitution | `rx-renewal-outside-provider`; `rx-renewal-no-substitution` |
| 18 | Renewal refill-duration / refill-quantity | `rx-renewal-refill-duration`; `rx-renewal-refill-quantity` |
| 19 | Renewal dispense-interval / protocol | `rx-renewal-dispense-interval`; `rx-renewal-protocol` |
| 20 | Renewal last-refill / comment | `rx-renewal-last-refill`; `rx-renewal-comment` |
| 21 | Renewal pickup / status | `rx-renewal-pickup` (finding 300); `rx-renewal-status` |
| 22 | Renewal treatment / treatment-clear | `rx-renewal-treatment`; `rx-renewal-treatment-clear` |
| 23 | Renewal start-known / start-unknown | `rx-renewal-start-known`; `rx-renewal-start-unknown` |
| 24 | Medication naming | `rx-custom-name-edit`; `rx-product-name-change-confirmation` |
| 25 | Dose validation | `rx-inline-minimum-validation` (finding 302); `rx-inline-maximum-validation` (finding 302) |
| 26 | Discontinue tasks | `rx-discontinue-cancel-preserves-draft`; `rx-discontinue-reason-options` |
| 27 | Written-date correction | `rx-written-date-future-correction`; `rx-written-date-impossible-correction` (finding 301) |
| 28 | Duplicate final clicks | `rx-double-save-only`; `rx-double-save-print` |
| 29 | Preferred pharmacy removal | `rx-pharmacy-remove-primary-return`; `rx-pharmacy-remove-all-return` |
| 30 | Timeline selections | `rx-timeline-multiple-selection`; `rx-timeline-clear-selection` (finding 303) |
| 31 | Preview return and correction | `rx-preview-close-return-edit` (finding 304); `rx-preview-escape-return-edit` (finding 304) |

The Rx control inventory, existing coverage and limitations are in
[rx-workflow-coverage-2026-10-18.md](rx-workflow-coverage-2026-10-18.md).
Grouped execution results and package details are in the
[validation report](ui-path-coverage-2026-10-18.md).

Each check is registered in `scripts/playwright-suite.json` and has a
`test:<check-name>-playwright` npm alias. Run selected names with:

```bash
node scripts/run-playwright-suite.js --only rx-inline-structured-dose-save --only rx-discontinue-reason-options --junit /tmp/rx-workflows.xml --residue-audit
```

Use the documented local-install environment from `deb-install-validation.md`.
Selections run sequentially against an otherwise idle disposable installation.
The residue audit verifies table counts, marker fixtures and declared shared
state. A known-fail result means the script still asserts correct behavior and
fails at exactly the documented step; it is not counted as a passing workflow.

## Final results by round

| Round | Passes | Known failures | Unexpected failures / skips |
|---|---|---|---|
| 1 | 1 | 1 | 0 / 0 |
| 2 | 2 | 0 | 0 / 0 |
| 3 | 1 | 1 | 0 / 0 |
| 4 | 2 | 0 | 0 / 0 |
| 5 | 1 | 1 | 0 / 0 |
| 6 | 2 | 0 | 0 / 0 |
| 7 | 2 | 0 | 0 / 0 |
| 8 | 2 | 0 | 0 / 0 |
| 9 | 2 | 0 | 0 / 0 |
| 10 | 1 | 1 | 0 / 0 |
| 11 | 1 | 1 | 0 / 0 |
| 12 | 2 | 0 | 0 / 0 |
| 13 | 2 | 0 | 0 / 0 |
| 14 | 2 | 0 | 0 / 0 |
| 15 | 2 | 0 | 0 / 0 |
| 16 | 2 | 0 | 0 / 0 |
| 17 | 2 | 0 | 0 / 0 |
| 18 | 2 | 0 | 0 / 0 |
| 19 | 2 | 0 | 0 / 0 |
| 20 | 2 | 0 | 0 / 0 |
| 21 | 1 | 1 | 0 / 0 |
| 22 | 2 | 0 | 0 / 0 |
| 23 | 2 | 0 | 0 / 0 |
| 24 | 2 | 0 | 0 / 0 |
| 25 | 0 | 2 | 0 / 0 |
| 26 | 2 | 0 | 0 / 0 |
| 27 | 1 | 1 | 0 / 0 |
| 28 | 2 | 0 | 0 / 0 |
| 29 | 2 | 0 | 0 / 0 |
| 30 | 1 | 1 | 0 / 0 |
| 31 | 0 | 2 | 0 / 0 |
