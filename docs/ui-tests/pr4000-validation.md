# PR #4000 installed validation

Validated on 2026-09-27 against `release/2026.08` at `25a867f07b`, using JDK 25 and the local Ubuntu 26.04 VM. Builds and tests ran serially; the VM was stopped during compilation. Final executable/test changes: `1b258559c8`. The subsequent validation-document commit changes documentation only.

## Automated checks

- Full Java suite: 13,457 tests reported, zero failures/errors, 51 existing skips.
- After the installed measurement-correction failure was fixed: 61 focused measurement DAO, patient-match and transaction tests passed. A further explicit rollback test confirmed the injected failure occurred after successful reassignment and restored the original patient.
- Full Node suite after the final JavaScript and fixture changes: 1,046 passed, zero failures/skips.
- Coverage helper: seven Python tests passed. Changed executable Java lines: 409/450 (90.9%); no changed files unmapped or with zero covered changed lines.
- 983 JSPs compiled; WAR packaging, Javadoc, BDD/encoder/security-exception/Struts checks passed. Java/JSP sources were unchanged after those checks; the final WAR includes the tested frontend correction.
- All three DEBs built as `2026.08.0~alpha16~pr4000.3`. Lintian reported no errors; one possible-bashism warning points at a Python expression in the maintainer script.
- 6,680 tested classes/web files matched both the package and final installed payload. `carlos-ctl check` passed before and after browser validation.

## Installed browser results

The installed checks used the nginx/ModSecurity HTTPS front door and packaged Chromium. The HRM window check uses real Chromium/AJAX against its loopback window/iframe fixture; the provider-linking check separately exercises installed HRM persistence and rendering.

| Check | Result |
|---|---|
| `provider-linking-rules` | Passed: admin switch off/on, audited uploads, unmatched patient assignment, correction to another patient, obsolete MRP revocation, independent ordering access retained, imported measurements moved without duplication, HRM assign/unlink and in-place provider-list update, GET 405 and tokenless POST 403 |
| `inboxhub-filters` | Passed: type and New/Acknowledged/Filed partitions; finding 45 is fixed by the current release's source-row identity change |
| `lab-acknowledge` | Passed all 10 checks, including exact selected-report identity, older/latest received dates, cumulative values and document-queue isolation |
| `hrm-window` | Passed popup, COOP, fallback, iframe, legacy, chart, cached-page and failure scenarios |
| `anonymous-access-refused` | Passed |
| `application-health` | Passed |

Final suite: six passed, zero failures, zero skips. The application had zero automatic restarts.

## Defects exposed and corrected

The numeric lab fixture exposed that `Measurement` rejects ordinary JPA updates. Patient correction now uses a source- and previous-owner-constrained DAO operation that changes only patient ownership. Real database tests preserve clinical values, annotations and source links, reject inappropriate updates, and verify rollback.

The installed HRM path exposed that the JSON response normalizer dropped the committed provider list. The full AJAX callback path now retains that list, updates assignment/unlink displays without a reload, and preserves the fallback for older responses.

Pre-existing lab-acknowledgement test cleanup cleared review comments, changed timestamps and left older versions filed. The repair snapshots the frozen source chain, restores exact review fields, tracks owned inserted IDs, recovers lost insert acknowledgements and reports cleanup failures. Five helper regressions and the installed acknowledgement run passed.

## Migration and restoration

Migration 39 passed against the baseline, Ontario and BC table structures in an isolated MariaDB database. Existing values and independent NULL provenance were preserved, and rerunning the migration retained explicit provenance and clinical state.

Original fingerprints for `HRMDocumentToProvider`, `HRMDocumentToDemographic`, `providerLabRouting`, `patientLabRouting`, `measurements`, `measurementsExt`, `demographic` and `property` matched after the final browser suite. No automatic fixture assignments remained. The test-only migration was reversed after stopping the application, and the VM was stopped. Audit entries from the test actions were retained.

Deploy #3986's migration 36 and #3996's migrations 37/38 before this PR's migration 39. Existing rows have unknown provenance and deliberately remain independent.

The VM retains its pre-existing external-service configuration; an SRFAX account reports HTTP 403. These runs do not validate external fax credentials or external clinical integrations. No browser errors were waived for the checks above.

Related issues: #3971, #4036, #4037, #4038, #4039. Operator details: [Provider Linking Rules](../provider-linking-rules.md).
