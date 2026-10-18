# Rx workflow coverage — release/2026.08

Target source: `a7611d07b4ae9b237b4a5d252fa938e09208eb85`. Tests exercise
the Debian packages installed on Ubuntu 26.04.1 through nginx/HTTPS. The
application source is unchanged. All new clinical fixtures are synthetic and
owned by their check; they are removed after both passes and failures.

This inventory compares the rendered controls in `SearchDrug3.jsp`,
`prescribe.jsp`, `ListDrugs.jsp`, `StaticScript2.jsp`, `SelectReason.jsp`,
`SelectPharmacy2.jsp`, `chartDrugProfile.jsp` and `ViewScript2.jsp` with the
assertions in registered checks. A route being opened is not sufficient:
the checks below assert the resulting clinical record, rendered state, or both.
It describes workflow/action coverage, not every input combination or a
proof that every medication workflow works correctly.

## Action inventory

| End-user task | Existing checks | Additional coverage in this expansion |
|---|---|---|
| Find a catalogue product, choose with mouse/keyboard, cancel, retain patient context | `popup-opener-rx-drug-search`, `rx-search-keyboard`, `rx-drug-pick-patient-isolation`, `rx-edit-discontinue` | `rx-product-name-change-confirmation`: decline/accept conversion to a custom drug and save exact accepted name |
| Enter a custom medication or free-text note | `rx-custom-note`, `rx-drug-name-quotes`, boundary/WAF drug-name checks | `rx-custom-drug-decline`, `rx-custom-note-decline`, `rx-custom-name-edit`: cancel entry without writes and rename an existing card |
| Parse directions and calculate dose, frequency, quantity and duration | `rx-edit-discontinue`, `gap-encounter-rx-quantity-price`, WAF instructions check | `rx-inline-structured-dose-save`, `rx-inline-prn-toggle`, `rx-inline-minimum-validation`, `rx-inline-maximum-validation`: actual editable dose links, blur saves and invalid numeric input |
| Read/reuse prior directions and special instructions | `rx-med-history` covers both history links, distinct fields and empty history | Retained; no duplicate history-only scenario |
| Set medication flags, outside prescriber, refills, treatment, dates and comments | `rx-card-options`, `rx-partial-dates`, `rx-static-script-pickup`, `rx-legacy-null-fields` | 18 `rx-renewal-*` variants cover changing/clearing fields on a replacement while preserving its archived source and an untouched medication; saved prescribing-event metadata is inspected on its actual record |
| Reject an invalid written date and correct the same card | Partial/full valid dates were covered | `rx-written-date-future-correction`, `rx-written-date-impossible-correction` |
| Save Only / Save And Print, clear a committed stage, avoid duplicate saves | `rx-prescription-lifecycle`, `rx-stash-patient-isolation`, `rx-stash-deletion` | `rx-empty-save-only`, `rx-empty-save-print`, `rx-double-save-only`, `rx-double-save-print`; double print also completes Create New Rx |
| Return from preview and correct the acknowledged prescription | Existing stash tests cover revision conflicts, reset failures, Edit Rx/delete and archive safety | `rx-preview-close-return-edit`, `rx-preview-escape-return-edit`: dismiss, change directions and save the acknowledged drug without a duplicate |
| Renew selected medications / renew long-term medications / cancel staged selection | `rx-rerx-cancel`, `rx-represcribe-long-term`, `rx-prescription-lifecycle` | 18 field-specific renewal cases; each checks source archival, replacement value and untouched control |
| Filter/order/read the medication profile | `rx-profile-legend`, `rx-profile-reorder`, `rx-unique-medication-list`, `rx-print-profile` | `rx-cpp-visibility-roundtrip`, `rx-cpp-visibility-save-refusal`: Hide CPP/unhide persistence and honest failure handling |
| Delete a medication or discontinue it with clinical reason/comment | `rx-delete-drug`, `rx-edit-discontinue`, `concurrency-rx-discontinue` | `rx-discontinue-cancel-preserves-draft`, `rx-discontinue-reason-options`: cancel while another draft is open; exercise all 16 offered reason values independently |
| File an indication, use patient diagnoses, archive a reason | `rx-edit-discontinue` files and reopens a typed ICD9 indication | `rx-indication-patient-registry`, `rx-indication-archive`: use the real patient-registry link, cancel archive, then archive exactly one reason |
| Graph medication history | `rx-edit-discontinue` graphs one saved product | `rx-timeline-multiple-selection`, `rx-timeline-clear-selection`: two products, reduction to one, clear all, custom-drug exclusion, unchanged clinical records |
| Create, edit, validate, choose, copy, share and delete favourites | `rx-favorites-choose-drug`, `gap-encounter-rx-add-favourite`, `rx-favourite-editor-validation`, `rx-favourite-dose-roundtrip`, `rx-favourite-multiline-directions` | Earlier batch retains passing validation/delete tests and exact failing PRN/multiline assertions (findings 292/295) |
| Manage preferred pharmacies, reorder, view details and return to prescribing | `pharmacy-editor-workflow`, `pharmacy-search`, `pharmacy-list-filters`, `gap-encounter-rx-pharmacy-order` | `rx-pharmacy-remove-primary-return`, `rx-pharmacy-remove-all-return`: follow unlinking into the next save/preview and reject stale pharmacy bindings |
| Print/reprint, notes, pharmacy/visit-site binding and signature | `rx-print-profile`, `rx-preview-pharmacy`, `rx-preview-visit-site-isolation`, `gap-encounter-rx-script-notes`, `prescription-signature`, `rx-drug-name-quotes` | Double-save and preview-return cases complement the existing preview-content and note round trips |
| Fax prescription, destination, record binding and re-prescribe/reprint | `rx-fax-pharmacy-phone`, `rx-fax-record-binding`, `rx-fax-reprint-represcribe`, `rx-fax-signature-stamp` | Existing isolated fax fixtures retained; no messages to real recipients |
| Allergy, interaction, renal and limited-use advice | `allergy-rx-alert`, `rx-interaction-warnings`, `rx-interactions-renal-luc`, `gap-encounter-rx-drug-info-on-host` | Existing assertions retained; external catalogue/information systems remain outside local downstream validation |
| Concurrency, stale drafts, authorization and patient isolation | Rx stash, patient-isolation, discontinue concurrency, authz role matrix and mutation-family checks | New cases preserve control medications and patient identity; existing security checks remain the source for the broader role matrix |

## Known limits

`completeMedRec` is defined in `SearchDrug3.jsp` and has a server mapping, but
the audited ON deployment renders no control calling it. It is not counted as a
completed UI workflow; a direct URL would not establish end-user coverage. Other
province/site-specific controls require their own configured deployments.

Real pharmacy delivery, external DrugRef information providers, external identity
systems and physical printing are not validated by a local container. Fax tests
use isolated fixtures or loopback simulation. The packaged eForm renderer was
omitted with `SKIP_EFORM_RENDERER=1` after its pinned download was refused; this
does not affect the Rx HTML preview checks, but is not saved-eForm PDF coverage.

Known failures remain assertions of correct behavior. An exact manifest
`expectedFailure` points to the [application issue register](app-findings-log.md).
Failure at a different step, a timeout or cleanup failure is an unexpected failure.
Passing tests are retained alongside those reproductions.

## Execution evidence

Final selection totals, regression results and package limitations are recorded
in [the validation report](ui-path-coverage-2026-10-18.md). The complete 31-round
ledger is [workflow-expansion-rounds-2026-10-18.md](workflow-expansion-rounds-2026-10-18.md).
Do not interpret registration of 775 checks as execution of the complete suite.

## Final validation totals

Added Rx selection: **35 passes, 9 known failures, 0 unexpected failures, 0 skips** across 44 entries.
Existing Rx-related selection: **44 passes, 6 known failures, 0 unexpected failures, 0 skips** across 50 entries.
Together, the **94 distinct Rx-related checks** have **79 passes and 15 known
failures**, tied to 13 recorded Rx issues. The latest result per check is used
after prerequisite-specific follow-up; raw initial results are retained.
The inline method/route refinement also passes separately. The multisite
workflow is exercised with its documented temporary setting; deployment
settings are restored afterward. See the validation report for individual
non-pass outcomes and the server-error audit.

The Info fallback check requires a readable application/DrugRef log source:
`LAB_UPLOAD_JOURNAL_UNIT=carlos-emr` in the systemd container, or the documented
`LAB_UPLOAD_LOG_FILE` alternative. It reads from a cursor taken before the
missing-DIN request. A deployment without that source skips the log assertion;
this validation provided the journal source and reproduced finding 308.
