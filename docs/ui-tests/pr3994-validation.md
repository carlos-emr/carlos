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
