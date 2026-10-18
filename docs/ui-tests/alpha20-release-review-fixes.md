# Alpha20 release review fixes (#4905)

This change targets `claude/promote-2026.08.0-alpha20`, the head branch of release PR #4902. The original scope is review items **1, 2, 4, 8, 9, 6 and 5** (#4905, merged as #4909). Follow-up #4910 addresses remaining-list items **5 and 6**, the E-Chart sinks (finding 286, recorded as 278 before #4913 renumbered it) and day-sheet roster filter (285), directly on the release branch at the user’s request. It does not approve the release or resolve the other review findings, including the Rourke font investigation (#4903).

## Changes

1. PHCP provider-role changes require role-administration write permission and POST, behind the existing blocking CSRF filter. A dedicated action/service replaces JSP writes, validates providers and allowed clinical roles, and preserves unrelated administrator/program roles. Report readers retain the read view with disabled write controls.
2. `DemographicNameAgeTag` encodes the patient label at the HTML text sink. Tests cover stored markup, accented names, ampersands and literal entity text.
3. Issue add/change/delete and diagnosis mutation entries enforce issue-write permission and patient access before restoring or modifying session state. GET/HEAD mutations are rejected. Session issues and notes must belong to the requested patient.
4. Both demo artifacts inherit a repaired snapshot: 53 orphan note-link rows, 10 patient document-routing rows, two provider document-routing rows and 4,676 attachments without an eForm parent are removed. Valid links/routes remain. Required tests cover both provinces and the development snapshot; four former TODO assertions are now ordinary passing tests. This repairs the shipped datasets; it does not delete historical rows from an existing operator database. Finding 143 remains open for already-loaded demo databases: rebuild a disposable demo database from the repaired artifacts, or explicitly reconcile its historical orphans before treating that install as repaired.
5. A future encounter date is refused before note mutation or persistence. Save, note-switch save, Sign & Save and Verify & Sign preserve the draft. AJAX saves show a localized validation message; full-form refusals render an encoded recovery draft. A valid-date retry remains possible.
6. Bulk MRP/resident/nurse/midwife reassignment requires administrative write permission, POST, CSRF and update/access rights on every affected patient. The whole batch is transactional. Managed originals remain unchanged until the existing demographic manager archives them. The existing “No Provider” clearing option remains supported.
7. Ontario billing commits the claim, appointment/archive update and corrected-bill cleanup in the same transaction. A false/refused cleanup or appointment failure rolls everything back; the review token becomes retryable. Only a committed complete operation is marked saved. Integration tests verify rollback and a single durable result after retry/repeat.

8. Stored tickler/eForm navbar titles, allergy patient names and consultation letterhead/default JavaScript values are encoded in their respective output contexts. Priority formatting and original literal text remain; navbar links fit the visible clipped title area.
9. Non Rostered Only excludes RO patients across provider/group/all views while retaining NR, TE, blank and NULL statuses. Sorting preserves provider, date range, time window and roster filtering. Only known SQL sort columns are accepted.
10. Late review of #4909 found issue deletion preceding date validation. Invalid/future encounter dates now refuse before deletion, CPP updates or session-list changes, with draft recovery. Valid and omitted dates retain issue deletion.
11. Release review/CI fixes use a private MCEDT staging lock with a real two-thread serialization test, validate billing BDD test naming, and document test-fixture/validated-ASCII static-analysis false positives with focused regression coverage.
12. Further CI findings remove dynamic report-range regex compilation, decode rich-text transport layers once each, preserve XML token boundaries during route audits, and model fixture-ID collisions correctly. Reported log values use `LogSafe`; consultation print confines rendered attachment paths to CARLOS-owned temporary directories before opening them, including symlink checks. Session form reads have narrow false-positive annotations backed by authorization tests. The measurement page passes its patient number to the server-side name/age tag with standard JSP EL; the tag parses the number and encodes the rendered label, with output-encoding regression coverage.
13. Three serializable request actions keep their security service reference transient and reacquire it when absent, so the service is not serialized and restored actions still enforce permissions.
14. Chart session cleanup separates its void operation from the Struts result and explicitly returns `NONE`, preserving the no-view response. Missing, invalid or conflicting patient scope releases nothing; a valid request clears only its patient's session state.

## Role and upgrade compatibility

The change uses existing security objects and leaves all Flyway migrations and stored role assignments unchanged. No permission migration is required. Regression checks replay the Ontario and BC migration streams, packaged demo loading and the development privilege restore.

| Operation | Required permission | Seeded compatibility |
| --- | --- | --- |
| Read PHCP provider settings | `_report r` | Doctor report access remains available |
| Change PHCP provider security roles | `_admin w` or `_admin.userAdmin w`, plus report read | Seeded `admin` grants this; clinical/report access alone does not |
| Bulk care-team reassignment | `_admin.misc w`, `_demographic u` and patient-record access | Retains the demographic manager's existing update requirement; `w`/`x` also satisfy `u` |
| Mutate clinical issues | `_casemgmt.issues w`, demographic read and patient-record access | Existing doctor, nurse, locum, RN, RPN, receptionist and secretary grants remain intact |

The user explicitly requested preservation of receptionist and secretary clinical-issue grants. Read-only custom roles remain read-only. Existing patient locks remain effective. Installations with custom administrative roles should retain the appropriate administrative write grant; report read or miscellaneous-administration read alone intentionally stops authorizing mutations.

## Validation

- **3,994 Java tests, zero failures/errors, two skips**, across 279 selected classes. This includes the four original defect regressions, real CSRF-filter validation, database-backed billing rollback/retry, and managed-entity demographic archival integration coverage.
- **3,912 script tests: 3,907 passed, zero failures, four skips and one existing TODO** (`npm run test:scripts`). All four demo orphan-link TODOs removed by this change pass as required tests.
- WAR build and compilation of **990 JSPs** passed. Dependency-lock and source checks passed.
- Live Ubuntu 26.04 testing used the previously built alpha20 packages, the candidate WAR/classes, packaged MariaDB/Tomcat/nginx/WAF and Chromium, with services started manually in an unprivileged disposable container. This is application validation on the packaged stack, not a rebuilt-package/systemd lifecycle or installed alpha19 upgrade certification.
- **Live reassignment workflow passed:** authorized MRP/resident changes, original-value archives, unchanged non-owned rows, GET/HEAD refusal, tokenless POST refusal, a read-only administrator's valid-token refusal, and “No Provider” clearing.
- **Live clinical issue authorization passed:** full-access control writes; a chart reader's valid-token replay receives application HTTP 403 and leaves every issue flag unchanged.
- **Live future-date workflow passed:** Save, Sign & Save and Verify & Sign refuse without persistence and retain drafts; a corrected Save retries successfully.
- **Follow-up live report workflow passed completely**, including role-security checks, Non Rostered Only and its positive cases, all sort links retaining filters, and unknown sort-key fallback.
- **Complete live E-Chart stored-markup walk passed:** 36 destinations opened, zero inert links and zero encoding/script findings. Four pre-existing optional prevention-due entries disappeared after the prevention module refreshed. Tickler/eForm titles and dates remain clickable; allergy and consultation pages preserve literal poisoned text. The walk exits inspected in-place CPP editors before continuing, using the visible Exit control.
- Follow-up Java validation: **4,008 tests, zero failures/errors, two skips**, followed by **75 focused tests, zero failures/errors/skips** after the review corrections (including real concurrent staging, Unicode rejection and issue-delete/date ordering).
- Further CodeQL correction: **136 focused Java tests passed**, including report range matching, Unicode names, reassignment authorization and transactional archival.
- Further Semgrep corrections: **147 focused Java tests passed**, including consultation attachment path/symlink refusal, valid attachment printing, clinical issue patient scope, billing setup and demographic save behavior.
- The local custom Semgrep scan passed all four configured rules over the seven reported Java/JSP files with zero findings or scanner errors. A separate measurement-JSP scan also reports zero findings; no JSP suppression is required.
- Sonar reliability corrections: **38 focused Java tests passed**, followed by **25 demographic update/stale-form tests**, including security-service restoration with read/write refusal and the existing patient-isolation checks.
- Cleanup response correction: **64 focused Java tests passed**, including valid/malformed/conflicting patient cleanup, issue authorization and encounter-date refusal/draft recovery. The preceding release CI run passed the full Java suite and the Toronto/Tokyo date checks; its only remaining failure was the Sonar reliability gate targeted by this correction.

The complete follow-up E-Chart walk passes and finding 286 is fixed. The document/inbox stored-markup walk already passes. The existing Rourke PDF tests use the DejaVu test configuration; the default-font failure remains open in #4903. The complete Java tree, every clinical browser workflow and full systemd/installed-upgrade testing were not run locally for these fixes. Release CI and review disposition are tracked on #4902.
