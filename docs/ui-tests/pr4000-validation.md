# PR #4000 installed validation

Validated on 2026-09-27 against `release/2026.08` at `25a867f07b`, using JDK 25 and the local Ubuntu 26.04 VM. Builds and tests ran serially; the VM was stopped during compilation. Final application changes: `f8506f6332`; final demo seed and regression changes: `60380724b4`. The subsequent validation-document commit changes documentation only.

## Automated checks

- Full Java suite: 13,465 tests reported, zero failures/errors, 51 existing skips.
- Earlier, after the installed measurement-correction failure was fixed: 61 focused measurement DAO, patient-match and transaction tests passed. A further explicit rollback test confirmed the injected failure occurred after successful reassignment and restored the original patient.
- Full Node suite after the final JavaScript and fixture changes: 1,052 passed, zero failures/skips.
- Coverage helper: nine Python tests passed. Changed executable Java lines: 427/465 (91.8%); no changed files unmapped or with zero covered changed lines.
- 983 JSPs compiled; WAR packaging, Javadoc, BDD/encoder/security-exception/JSP-taglib/i18n checks passed. Java/JSP sources were unchanged after those checks; the final packages also include the separately tested demo seed corrections.
- All three DEBs built as `2026.08.0~alpha16~pr4000.5`. Lintian reported no errors; one possible-bashism warning points at a Python expression in the maintainer script.
- 6,680 tested classes/web files matched both the package and final installed payload. `carlos-ctl check` passed before and after browser validation.

## Installed browser results

The installed checks used the nginx/ModSecurity HTTPS front door and packaged Chromium. The HRM window check uses real Chromium/AJAX against its loopback window/iframe fixture; the provider-linking check separately exercises installed HRM persistence and rendering.

| Check | Result |
|---|---|
| `provider-linking-rules` | Passed: admin switch off/on, audited uploads, unmatched patient assignment, correction to another patient, obsolete MRP revocation, independent ordering access retained, imported measurements moved without duplication, HRM assign/unlink and in-place provider-list update, GET 405 and tokenless POST 403 |
| `inboxhub-filters` | Passed: type and New/Acknowledged/Filed partitions; finding 52 is fixed by the current release's source-row identity change |
| `lab-acknowledge` | Passed all 10 checks, including exact selected-report identity, older/latest received dates, cumulative values and document-queue isolation |
| `hrm-window` | Passed popup, COOP, fallback, iframe, legacy, chart, cached-page and failure scenarios |
| `anonymous-access-refused` | Passed |
| `application-health` | Passed |

Final suite: six passed, zero failures, zero skips. The application had zero automatic restarts.

## Defects exposed and corrected

The numeric lab fixture exposed that `Measurement` rejects ordinary JPA updates. Patient correction now uses a source- and previous-owner-constrained DAO operation that changes only patient ownership. Real database tests preserve clinical values, annotations and source links, reject inappropriate updates, and verify rollback.

The installed HRM path exposed that the JSON response normalizer dropped the committed provider list. The full AJAX callback path now retains that list, updates assignment/unlink displays without a reload, and preserves the fallback for older responses.

Pre-existing lab-acknowledgement test cleanup cleared review comments, changed timestamps and left older versions filed. The repair snapshots the frozen source chain, restores exact review fields, tracks owned inserted IDs, recovers lost insert acknowledgements and reports cleanup failures. Six helper regressions and the installed acknowledgement run passed.

## Migration and restoration

Migration 39 passed against the baseline, Ontario and BC table structures in an isolated MariaDB database. Existing values and independent NULL provenance were preserved, and rerunning the migration retained explicit provenance and clinical state.

Original fingerprints for `HRMDocumentToProvider`, `HRMDocumentToDemographic`, `providerLabRouting`, `patientLabRouting`, `measurements`, `measurementsExt`, `demographic` and `property` matched after the final browser suite. No automatic fixture assignments remained. The test-only migration was reversed after stopping the application, and the VM was stopped. Audit entries from the test actions were retained.

Alpha 16 promotion follow-up: PR #3996 remains unmerged, so its reserved migrations 37/38 are absent and are not prerequisites for migration 39. The release includes 36, 39 and 40. Renumber the attachment feature's unpublished migrations above the release high-water mark before its later merge; do not enable out-of-order execution. Existing rows have unknown provenance and deliberately remain independent.

The VM retains its pre-existing external-service configuration; an SRFAX account reports HTTP 403. These runs do not validate external fax credentials or external clinical integrations. No browser errors were waived for the checks above.

Related issues: #3971, #4036, #4037, #4038, #4039, #4050. Operator details: [Provider Linking Rules](../provider-linking-rules.md).

## Follow-up review and strict demo initialization

All ten follow-up review threads were addressed. Clinic-wide setting saves use a reserved nonclinical key in the existing database coordination table and a current locking property read. Real concurrent transactions prove that a second first-time save waits for either commit or rollback, then leaves exactly one global row with the last saved value. Production uses constructor injection; the existing constructor remains available. The existing persistence test also covers both saved states.

HRM unlink handles a null audit actor after authorization. Upload tests now verify the actual routing method, and malformed patient IDs are rejected before matching. Fixture ownership is recovered immediately after an INSERT with a lost acknowledgement, before its marker can be overwritten; combined insertion/recovery errors retain both causes. Coverage tests include quoted Git paths, source-directory boundaries, parent traversal and mapped 0/0 files. The French privacy notice explicitly includes lab results and HRM reports.

The failed MariaDB CI job was caused by positional demo INSERTs omitting schema39's new provenance columns. Explicit original-column lists preserve independent NULL provenance. A complete bootstrap in a disposable strict-mode MariaDB instance then exposed pre-existing seed defects tracked in #4050:

- Two patient layouts mixed in one INSERT shifted preferred names and gender/pronoun fields. Separate explicit column lists preserve all 3,000 rows.
- 981 synthetic country names required the application's `CA` code, and 106 email-consent flags required `1`/`0` instead of `Y`/`N` (37 yes, 69 no).
- Email-log additional parameters and provider attribution used the opposite column order.
- Pharmacy rows placed `uid` before name, shifting contact details and timestamps.
- The legacy Rich Text Letter seed omitted the required `showLatestFormOnly` flag; it now explicitly supplies zero.

A literal-by-literal comparison confirmed that the patient seed changed only the documented country and consent representations; email and pharmacy fixes change column headers. Five new script tests verify the seed mappings, patient gender/pronoun/consent integer fields, representative names/contact details, the eForm flag and both actual generated Ontario/BC Debian demo artifacts.

The final bootstrap ran with normal stop-on-error behavior and zero SQL errors through both clinical databases, DrugRef and every demo supplement. Its socket-only server and owned files were removed. These fixes apply to future demo loads and do not migrate existing patient records. The final VM upgrade preserved its existing dataset, and the installed eForm seed matched source exactly.

Final DEBs were installed as `2026.08.0~alpha16~pr4000.5`. All six browser checks, 6,680 installed payload hashes, health and eight original-table fingerprints passed. The application had zero automatic restarts and approximately 1.49 GiB service memory; the VM had 4.5 GiB free before it was stopped. No schema or fixture restoration remains pending.

## Second review follow-up: translated descriptions and current release

Spanish and French now explicitly describe uploading and linking a lab result, avoiding language that could mean uploading a laboratory. The Polish privacy notice explicitly says the provider-linking option is enabled, while retaining MRP as the subject who receives results. Authenticated installed-page checks verify all three corrected translations and confirm that viewing them leaves the stored routing setting unchanged.

Concurrent branch work was preserved and reviewed: HRM bulk-delete queries use literal entity names with the same bound parameters; the routing diagnostic keeps its existing sanitized arguments; authorization tests separate a missing audit actor after valid authorization from an unauthenticated request; and the concurrent-writer test checkpoints at the real lock boundary. Release merges retain the migration reservations, both findings-log sections and both coverage-audit contracts (including quoted paths, XML safeguards, bounds and the compatibility alias). The final release merge includes the validated prescription fixes from #3993.

Final combined-source validation:

- Full Java unit/integration suite: **14,016 tests, zero failures/errors, 51 existing skips**.
- Full Node suite: **1,259 passed, zero skips**. BDD naming (287 files), encoder, security-message, JSP taglib and locale checks passed. Python coverage-audit, packaging and manifest suites passed **14**, **29** and **244** tests.
- All **985 JSPs**, WAR and Javadocs built. Changed executable Java coverage is **427/465 (91.8%)**, with no unmapped or wholly uncovered changed files.
- Built and installed all three DEBs `2026.08.0~alpha16~pr4000.6` on Ubuntu 26.04; **6,707** application payload files and **23** separate CLI files matched their tested builds.
- Migration 1.0.39 and its rerun passed on baseline, Ontario and BC table layouts, preserving original routing fields and explicit provenance.
- The exact container database bootstrap passed again in an isolated, socket-only MariaDB instance: both migrated clinical databases, DrugRef and all demo supplements, with zero SQL errors. Existing seeded routing provenance remains NULL; owned isolated database files were removed.
- Installed Spanish, French and Polish descriptions passed. All six installed workflows passed with zero skips: anonymous-access refusal, application health, inbox filters, lab acknowledgement, HRM window and provider-linking rules.

All eight original clinical/configuration table fingerprints matched after browser cleanup. Final application/CLI hashes and health checks passed; automatic restarts remained zero. With the application stopped, only the temporary 1.0.39 columns/history were restored to their recorded pre-test state. The current release's successful 1.0.36 migration remains installed; 1.0.37–39 are absent after restoration. The VM was stopped with 4.4 GiB free. Builds, installation and tests ran serially, with the VM stopped during compilation.
