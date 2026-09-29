# PR #3994 validation

PR #3994 addresses #3957, #4020, #4021 and #4022, and shares the CAISI scheduler fixes for #4012 with #3990. The original validation-message port is attributed to Liam Stanziani, Open-O PR #2410. The current branch incorporates release/2026.08 at 25a867f07b9bda663aaa70aab8e5de225605f999 (alpha16).

Both forms use one inert text renderer and clear the previous attempt before validating. Add reports all current demographic/date/assignee failures. The obsolete CAISI program check and unused program-provider query were removed: neither form renders that control, and neither save action accepts it. Existing program data and encounter-note program resolution remain intact.

Server actions reject invalid ISO dates and required fields before mutating ticklers. Add respects manager rejection and guards the saved ID. Edit respects manager rejection, compares parsed dates/enums, avoids artificial no-op history, and snapshots the original timestamp before adding a comment. Browser fixture cleanup now scopes deletion to patient, marker, validated IDs and exact test note text; it no longer purges unrelated orphan links.

Validation:

- Clean Java compilation, full unit/integration suite: 13,384 tests, no failures/errors, 51 existing skips. Includes 41 focused tickler action cases, 50 shared mutator contract cases and two pooled CAISI tag cases.
- Full Node suite: 1,043 passed, zero skips. Includes seven actual form JavaScript behavior cases and six scoped-cleanup cases.
- Encoder-null-safety, BDD naming and i18n property checks passed.
- 984 JSPs compiled; WAR and Javadoc build passed.
- Changed executable Java line coverage: 68/68 (100%).

The edit and suggested-text actions require POST before reading or mutating records (405 and Allow: POST otherwise). The shared mutator contract covers the default and edit dispatches. The installed browser target sends an authenticated GET mutation probe as well as invalid POSTs.

The CAISI schedule now includes two compilable JSPs rather than dynamically executing JSP fragments, supplies the required view parameters, and keeps pooled tag view state local. These files match the corresponding #3990 fixes. The obsolete JSPF files are absent from the WAR.

All three DEBs for `2026.08.0~alpha16~pr3994.2` built serially with the VM stopped for compilation. 6,673 tested class/web files match the package. The Ubuntu 26.04 LXD VM installed all three packages, matched all 6,673 deployed payload files, and passed `carlos-ctl check` before and after browser testing.

Installed browser checks passed with zero skips:

- `tickler-validation-messages` in normal mode, entered through Search → Master Record → Tickler → New Tickler. It covers multiple simultaneous errors, repeated attempts, corrected errors, invalid date/assignee POSTs, an authenticated GET mutation probe, successful add/edit dates and unchanged database state after rejection.
- The same target with `caisi=true`, a visible CAISI schedule selector, and `TICKLER_DIRECT_LIST=true`. This opens the owned patient's tickler list directly because the test account lacks `_pmm_management` search permission. Strict HTTP/console/JavaScript checks remain enabled.
- Existing `tickler-crud`, `tickler-note-dialog` and `tickler-demo-main` checks on an isolated patient.

The browser target waits for the application's broadcast-driven list refresh; forcing a page reload during that refresh caused a test-induced aborted request and was removed. Both normal and CAISI targets passed again after that harness correction. The installed application payload did not change.

The first installed CAISI attempt reproduced the shared #4012 scheduler JSP defect. The final build contains the matching scheduler/tag fixes from #3990 and compiles all 984 JSPs, including both renamed views.

All owned patient, tickler, note, issue and history fixtures were removed. The original properties file was restored byte-for-byte after the temporary CAISI setting. A count and SHA-256 comparison verified all 327 pre-existing note-link rows were unchanged. Final health passed and the VM was stopped. Host root space remained about 3.3 GiB free and VM space about 4.5 GiB free; superseded DEBs and build staging were removed while logs and package hashes were retained.

Application payload commit: `855910bf9a`. Evidence logs and hashes are retained locally in `/home/michael/work/pr3985-4000-evidence/` with the `pr3994-` prefix.

## Review follow-up

All six new findings are addressed. Tickler priorities use stable enum values for both selection and submission while keeping localized labels (#4043). New active/inactive suggested text is persisted without logging the text or parsing exception (#4044). Add/edit/suggestion mutation guards now compare the exact POST method; 405 responses advertise `Allow: POST`. The tests reject lowercase, mixed-case and Unicode case-fold variants before mutation.

Owned tickler cleanup removes all links belonging to the deleted ticklers even when their notes are excluded by the text filter; those notes remain intact. Regression cases cover no matching notes and an empty note-text list. The validation-function extractor uses fixed regex literals. Both pooled-tag tests follow the BDD naming convention. Shared CAISI views match #3990's independent placeholder/program rendering, presentation-table semantics, HTML5 spans and associated status label, addressing all six Sonar reliability findings.

Follow-up validation passed: **106 Java integration/tag/mutation tests**, **1,044 complete Node tests**, encoder and BDD audits, **984 JSPs**, WAR and Javadocs. The initial new add-method test exposed a missing `Allow` header; that was corrected before the final passing run.

All three DEBs `2026.08.0~alpha16~pr3994.3` were installed on Ubuntu 26.04; **6,673 package and installed files** matched the tested source. The expanded target passed in normal and CAISI modes. Its French edit pages display Élevée/Normale/Faible while submitting High/Normal/Low, preserve the stored selection, persist every priority and comment, and preserve High during a comment-only edit. Existing `tickler-crud`, `tickler-note-dialog` and `tickler-demo-main` checks also passed. A temporary authenticated JSP probe exercised four selector cases with the actual included JSP, including available programs with empty/zero selections and encoded labels.

The probe and fixtures were removed; CAISI configuration was restored byte-for-byte. All **327 original note-link rows** retained their exact count and SHA-256 hash. Final health and installed hashes passed, `NRestarts=0`, VM free space was 4.5 GiB, and the VM was stopped. Builds and tests ran serially with the VM stopped throughout compilation. Application payload commit: `2bbf3845a2`.

## Second review follow-up: shared notes and unchanged service dates

The summary-only service-date finding reproduced a spurious update when an existing service date contained a non-midnight time. Edits now compare calendar days in the same local time zone used by the form parser. An unchanged day preserves the original timestamp and creates no history; a changed day still records the original and new values. Java regressions cover both paths, and the installed browser saves an owned tickler with a `14:35:00` service time and verifies that the exact value and history count remain unchanged.

Fixture cleanup selects a marked note for deletion only if it has no links outside the owned ticklers. Its SQL regression executes against SQLite, checking a note shared with another tickler, a link to another record type, a foreign patient's note, their issue-note rows, and complete deletion of the solely owned note. Installed neighboring workflows and original MariaDB note-link/table checksums provide the complementary deployed checks. The rejected-GET browser probe now captures priority and history before the request and verifies both are unchanged after the 405/Allow: POST response.

Own review found #4053 in the existing edit failure paths: malformed identifiers and attached persistence exceptions could expose entered clinical values in operational logs. Diagnostics retain a fixed refusal reason or exception type, with no request values, tickler/provider identifiers or attached throwable. Log-capture regressions check messages, parameters and throwables while preserving the explicit failure views. This extends #4044's successful suggestion-creation privacy coverage.

Final validation:

- Full Java unit/integration suite: **13,862 tests, zero failures/errors, 51 existing skips**. The new unchanged-time regression failed before the fix and passed afterward.
- Full Node suite: **1,214 passed, zero skips**. BDD naming (282 files), encoder, security-message, JSP taglib and locale checks passed. Python packaging and manifest suites passed **29** and **244** tests.
- All **984 JSPs**, WAR and Javadocs built. Changed executable Java coverage is **70/70 lines (100%)**, with no unmapped files.
- All three DEBs `2026.08.0~alpha16~pr3994.4` built and installed on Ubuntu 26.04. **6,694** tested/packaged/installed files matched; the separate CLI's **23** installed files also matched its previously validated pinned-source package.
- Normal and CAISI installed targets passed, including unchanged-time saves, GET refusal without state changes, invalid POSTs, repeated validation, French labels, all priorities and saved comments. Three neighboring workflows passed: tickler CRUD, note-dialog round trips/stale-state prevention, and the demographic tickler view.

The concurrent release merge was preserved in `6943a5eb4a`; its final tree exactly matches tested source `8caa39912f`. Original note links and seven clinical-table checksums matched after the browser runs. Owned fixtures were removed, the original configuration was restored byte-for-byte, final health and payload checks passed, and automatic restarts remained zero. VM free space was 4.4 GiB and the VM was stopped. Builds, installation and browser tests ran serially; the VM remained stopped throughout compilation.
