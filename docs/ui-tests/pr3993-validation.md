# PR #3993 validation

Issue #3974 adds pharmacy telephone numbers to Fax & Paste chart notes and prescription PDFs. Review and installed testing also corrected PDF layout/address defects (#4016), a pharmacy-preview race (#4017), schema-legal NULL prescription values (#4018), and prescription-history request/navigation failures (#4019).

## Reviewed behavior

- Phone composition preserves free text, skips missing values and normalizes line breaks. Encoding occurs at the output boundary. A paste retry retains the captured note without duplicating its telephone segment.
- PDF contact fields wrap inside Letter, A4, half-letter and A6 pages. Narrow pages reserve header space above the prescription. Missing address components do not print `null` or dangling separators. All telephone labels use the negotiated supported locale, including `de-DE,fr` falling back to French.
- The preview renders the selected pharmacy snapshot already loaded by the page. It does not depend on an auxiliary lookup completing before fax submission. Explicit removal and iframe reload remain covered.
- Nullable dose bounds and primitive flags load with their established defaults through explicit JPA converters. Nullable repeat counts default to zero at the four primitive DTO boundaries; existing counts and instructions are retained. Reusing instructions tolerates a missing prior quantity. Both prescription-loading paths preserve true internal-dispensing flags and default an absent flag to false.
- History favorites and re-prescribing use the existing CSRF-compatible request helper. Dependent writes finish in order; a failed write retains the page and shows a localized error. Staging checks write access, the active patient, and patient-specific permission, and returns explicit errors for invalid/missing state or application failures. A protected return route renders the prescribing workspace without resetting its staged session.
- The pharmacy fax check owns its patient, pharmacy, sender, prescriptions, notes, signatures and fax artifacts. It checks the configured provider against the login and selects its sender explicitly. Cleanup failures are fatal. Seven bounded waits plus login/UI/cleanup allowance are reflected in the wrapper and suite timeouts. Browser failures remain strict except the single deliberately injected 409 and its matching console message.

## Automated checks

Java 25 full unit/integration suite: **13,394 tests, zero failures/errors, 51 skips**. This includes 33 re-prescribe action tests and 19 parameterized legacy-value cases. DAO regressions verify all five nullable primitive mappings and retain fractional dose bounds and true flags on database round trips.

Node suite: **1,050 passing, zero skips**. Tests execute the actual page JavaScript for phone composition/retry, synchronous preview handling, favorite cancellation/success/failure, and both ordered re-prescribe writes. Encoding, BDD naming and translation audits passed.

All **982 JSPs** compiled; WAR and Javadocs built. Changed executable Java coverage is **86/87 lines (98.9%)**, with no unmapped files. The remaining uncovered line is the clinic-phone PDF branch.

Negative controls reproduced the missing pharmacy header when its old auxiliary request was delayed, nullable drug hydration/unboxing failures, and history-action failures. Browser testing reproduced the favorite POST returning 403 and the incorrect return to the legacy drug-search view. The corrected installed workflow must display the staged editor; successful HTTP responses alone do not pass it.

## Ubuntu 26.04 installation

Built and installed all three packages at version `2026.08.0~alpha16~pr3993.7`: EMR, DrugRef and renderer. Package and installed-payload verification matched **6,669 files** to the tested checkout. The VM uses the packaged HTTPS/nginx/ModSecurity front door, MariaDB and DrugRef. Deployment health checks passed before and after browser validation.

All nine checks passed on that installation, with zero skips:

- `rx-legacy-null-fields`: NULL prescription loading, favorite persistence, unchanged source values, and visible staged re-prescription without another saved prescription.
- `rx-fax-pharmacy-phone`: both numbers, phone2 only, no numbers, exact stored note, retry, generated PDF readback and delayed auxiliary lookup.
- `rx-fax-reprint-represcribe`.
- `rx-fax-record-binding`.
- `rx-fax-signature-stamp`.
- `rx-preview-pharmacy`.
- `pharmacy-editor-workflow`.
- `pharmacy-search`.
- `pharmacy-list-filters`.

The neighboring Rx checks used isolated synthetic patients and prescriptions, including nullable legacy fields. Owned database fixtures and the temporary synthetic signature stamp were removed. Original patient data and configuration were preserved. The older neighboring fax checks document retaining small generated PDF/spool files after deleting their database rows; the pharmacy-phone target removes its own artifacts.

Builds and tests ran serially. The VM was stopped for compilation, and installation/browser runs used CPU/memory limits. Disk space was monitored and superseded DEBs removed while preserving hashes and logs. The VM was stopped after final health verification.

## Review follow-up: favorites and diagnostic privacy

Sonar's nullable-favorite finding led to issue #4041. Both favorite-selection routes and both editing routes now reject malformed IDs (400) and deleted favorites (404) before modifying the staged prescription. Selection also reports missing sessions, denied access and application failures explicitly. The browser inserts markup and renders the stage only after a successful response; a failed favorite request shows a localized message and preserves the existing controls. Re-prescribing uses `SecureRandom` within the established generated-ID bounds.

Issue #4042 removes prescription instructions, medication details and history from `RxUtil` debug diagnostics. Legacy diagnostic entry points remain as documented compatibility no-ops. Three regression tests explicitly enable and verify debug capture while checking unchanged instruction trimming, dose parsing and staged content.

Validation on the follow-up: **73 clean favorite/re-prescribe/legacy-value Java tests** passed before the privacy change; **139 focused parser/privacy/favorite/re-prescribe tests** passed on the final source. The complete Node suite passed **1,059 tests**, including nine favorite success/failure controls. All **982 JSPs**, WAR and Javadocs built successfully.

All three DEBs `2026.08.0~alpha16~pr3993.9` were installed on Ubuntu 26.04. Package and installed verification matched **6,670 files**. Health checks passed before and after these installed HTTPS checks:

- `rx-legacy-null-fields`: the previous three steps plus successful favorite staging, deletion of the owned favorite, a visible 404 alert with identical staged controls, and 404 responses from legacy selection and both editing routes. No extra prescription was persisted.
- `rx-fax-pharmacy-phone`: both numbers, phone2 only, absent numbers, retry, stored encounter note and generated PDF.
- `rx-fax-reprint-represcribe`: save, reprint and re-prescribe without duplicate persistence.

Owned fixtures and the synthetic signature stamp were removed. Final health and installed hashes passed, automatic restarts remained zero, and the VM was stopped. Compilation and installed testing ran serially; the VM was stopped for every build. VM disk space finished at 4.5 GiB free.

## Second review follow-up: stale history, satellite locale and preview completeness

History staging now uses a single database lookup that can report an absent row. A missing or foreign-patient item returns 404 before the stash is changed; the existing prescription-loading API retains its contract. Both satellite address bundles in the page and the servlet's telephone/fax labels resolve the first supported browser language consistently. The binding regression exercises `Accept-Language: de-DE,fr` and confirms the French satellite block remains valid.

Pharmacy preview lines now contain only trimmed, populated fields, with no empty contact labels or location separators. Values remain encoded and the pharmacy identifier is preserved. Node tests cover null, undefined, empty/whitespace, full contact data and hostile text. The strict browser negative-control test consumes the deliberate 404 first, then verifies each unrelated HTTP, console and runtime signal still fails the harness.

Own review found #4051: prescription/patient/signature identifiers in servlet diagnostics, drug identifiers in history diagnostics, and clinical values in attached exceptions. Operational diagnostics now retain the reason and exception type without those identifiers, values or attached causes. Existing clinical audit calls remain unchanged. Tests capture formatted messages, event parameters and throwables across signature refusals, satellite fallback, missing instructions and invalid duration rendering.

Validation:

- Full Java suite: **13,423 tests, zero failures/errors, 51 skips**. Final focused suite: **162 passed**, including the real loader's one-lookup stale-history refusal and no stash interactions.
- Full Node suite: **1,064 passed**. BDD naming (275 files), encoder, security-message, JSP taglib and locale checks passed.
- All **982 JSPs**, WAR and Javadocs built. Changed executable Java lines: **151/162 (93.2%)**, with no unmapped files.
- Built and installed all three DEBs `2026.08.0~alpha16~pr3993.10` on Ubuntu 26.04; **6,670** tested/packaged/installed files matched.
- The installed legacy workflow passed five steps, including deletion of a history row after rendering its control, a visible 404 with no navigation, and unchanged staged prescription values after reopening the editor. Deleted-favorite recovery also passed.
- Installed pharmacy-phone checks passed all three telephone cases, exact encounter-note/PDF readback and retry. The browser also verified omission of empty Email/Note labels and correct present/absent Tel labels. Reprint/re-prescribe passed.

Six original clinical-table fingerprints matched before and after the browser runs. Owned fixtures and the synthetic stamp were removed. Final health and payload verification passed, automatic restarts remained zero, VM disk space was 4.4 GiB free, and the VM was stopped. Builds, installation and browser checks ran serially, with the VM stopped throughout compilation.
