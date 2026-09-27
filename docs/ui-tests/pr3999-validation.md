# PR #3999 validation

Validated on 2026-09-27 against `release/2026.08` (`25a867f07b9b`), using
Ubuntu 26.04 VM `carlos-val`. Related issues: #3942, #4032, #4033, #4034,
and #4035. The original zero-value group-member correction is credited to
Sebastian Ibanez in openo-beta/Open-O#2510.

## Behavior reviewed

- Group membership uses claim-item count for creation and regeneration, including
  zero-value and net-zero batches. Empty providers are omitted from output and
  finalization; an entirely empty regeneration preserves the existing file.
- Selecting one group provider processes only that provider. Complete group
  rendering precedes any file publication.
- Optional legacy referral, facility, laboratory, manual-review and location
  NULLs become spaces in the fixed-width export. Required-field validation is
  retained.
- Regeneration prepares batch metadata without changing the database. Its audit
  and editable metadata are finalized with claim status and summaries in the
  same transaction. Stored batch identity and the prior audit value are retained.
- Existing OHIP files and HTML previews are restored after a confirmed failure.
  Failed restoration retains rollback copies and reports reconciliation guidance.
  A lost commit acknowledgement retains output rather than deleting files for
  claims that may already be billed. Preview backup names identify the original
  preview for reconciliation.
- The existing diskette R button now adds the session CSRF token to its POST.
  Missing tokens produce a localized visible error; cancellation sends nothing.
  Existing method signatures and POST-only action enforcement remain available.

## Automated checks

| Check | Result |
| --- | --- |
| Full Java suite after service changes | 13,367 tests; zero failures/errors; 51 existing skips |
| MRI JSP encoding and mutation-method contracts after CSRF fix | 48 passed |
| Node suite after final browser/JSP changes | 1,040 passed, no skips |
| JSP compilation | 982 JSPs, zero errors |
| Javadoc, BDD naming, JSP taglibs, locale consistency, security-exception convention and encoder lint | Passed |
| Changed executable Java lines | 143/166 covered (86.1%) |

Five new group-integrity cases reproduced the original failures before their
fixes. The optional-NULL export test also reproduced the original exception.
Regression coverage includes exact fixed-width zero-fee records, original-preview
restoration, partial group publication and failed restoration, and real Spring
transaction proxies with H2 for commit, rollback and lost commit acknowledgement.
Orchestration tests verify that uncertain outcomes retain files.

Seven cleanup tests exercise partial setup, lost insert acknowledgement, credential
disposal even when cleanup fails, path/symlink rejection and unrelated-file
preservation. Three JavaScript tests execute the R-button handler for token-bearing
submission, missing tokens and cancellation.

## Packages and installed checks

All three packages were built serially and installed:

- `carlos-emr_2026.08.0~alpha16~pr3999.2_amd64.deb`
- `carlos-emr-drugref_2026.08.0~alpha16~pr3999.2_all.deb`
- `carlos-emr-eform-renderer_2026.08.0~alpha16~pr3999.2_all.deb`

6,669 packaged and installed classes/web resources matched the tested build.
`carlos-ctl check` passed, including front-door/WAF checks, live DrugRef lookup
and the existing 432-table database with 29 successful migrations.

The initial installed run successfully generated ZERO's disk but exposed the
pre-existing R-button HTTP 403: native form submission omitted the CSRF token.
That failure is tracked in #4035. After the fix and a complete package rebuild,
`billing-on-group-disk-zero-total` passed without skips:

1. ZERO exports two zero-fee items with NULL optional fields; PAID stays unbilled.
2. The actual R button regenerates ZERO with legacy EMPTY-member metadata, emitting
   only ZERO's batch and leaving EMPTY unfinalized.
3. PAID exports separately with the correct count and total.
4. Selecting EMPTY allocates no OHIP output file.

The check downloads the page's generated files and verifies records, billed status,
batch association and persisted summaries. Both the failed and successful runs
restored all six billing-table checksums, the original empty output-file inventory,
and the configuration fingerprint. Owned group providers and claims were removed.
`HOME_DIR` is derived by application startup from `BASE_DOCUMENT_DIR` and the
`carlos` context on this VM; the browser check received that verified local path.

Neighboring installed `application-health`, `billing-on-submit` and
`billing-payment-types` checks all passed without skips. The three neighboring
submission claims were also verified removed. The service reported zero automatic
restarts and approximately 1.6 GB memory usage at the end.

The VM was stopped for compilation; local builds and browser checks ran serially.
Disk space was monitored and superseded packages removed. The VM was stopped after
validation. No GitHub merge was performed. Final batch review and bot/CI convergence
are tracked separately from these local validation results.

## Follow-up review: concurrency, publication and visible errors

- Corrected the coverage-plan row to describe separate provider disks and the R-button
  regeneration flow. The browser check now also tests overlap refusal under a real external
  POSIX lock. The application creates its persistent lock first through EMPTY's normal flow,
  preserving application ownership; the test never creates a privileged replacement lock.
- Fixed #4048 after both deterministic overlapping-operation regressions failed against the
  prior code. A JVM guard and a filesystem lock now cover the entire export/regeneration
  operation, including reads, rendering, publication, finalization and rollback. Requests
  sharing HOME_DIR fail visibly before entering billing work while another operation owns it.
  The empty lock inode is retained so processes cannot accidentally lock different inodes.
- Completed output is written to an exclusive validated sibling, closed successfully and
  atomically published. Existing POSIX permissions are preserved. Regeneration copies the
  prior OHIP output without making its download disappear; rollback restores it atomically.
  The public legacy `renameFile()` API retains its original move behavior for other callers.
  Missing claim headers, batch headers or disk summaries now abort finalization explicitly.
- Both rollback-path findings use `PathValidationUtils.getRequiredHomeDirectory()` and a
  validated exact basename. Exclusive temporary siblings have a method-local FindSecBugs
  explanation. Missing configuration, invalid directories, traversal, symlink locks,
  partial/close-time write failures and rollback preservation are covered.
- The first installed overlap check found a separate pre-existing error-message defect,
  tracked in #4049: the interceptor published the handled exception only on the Struts
  value stack while the billing page read a request attribute. The new regression failed
  before the fix. Only package-handled exceptions now receive the explicit request attribute;
  generic failures and authorization refusals remain private, with existing status/log behavior.

Final validation:

- **13,381 Java tests**, zero failures/errors, **51 existing skips**. The final focused billing
  run passed **90 tests**, including real cross-JVM exclusion, release after failure, missing
  finalization rows, retained downloads and file permissions. The full suite also verifies
  the handled-message handoff and generic/refusal privacy. Changed Java coverage: **208/241 (86.3%)**.
- **1,042 Node tests** passed, including real POSIX-lock lifetime/cleanup checks. BDD naming
  passed across **277 files**; encoder, security-message and JSP taglib checks passed.
  **982 JSPs**, WAR and Javadocs built successfully.
- All three DEBs **2026.08.0~alpha16~pr3999.4** built with the VM stopped and installed on
  Ubuntu 26.04. **6,671** tested/packaged/installed classes and web resources matched.
- Installed OHIP checks pass without skips: blocked export allocates no disk and leaves the
  claim unbilled; blocked regeneration preserves exact output and batch metadata; both display
  the busy message. Normal ZERO/PAID exports, ZERO regeneration with EMPTY metadata, downloads,
  summary/linkage assertions, NULL fields and EMPTY omission all pass.
- Four neighboring workflows pass without skips: application health, Ontario submission,
  flu billing and payment types. A local suite-wrapper reference to another PR's unavailable
  check was corrected before running these existing checks; it required no application change.
- Both installed runs preserved the original six billing-table checksums and configuration
  fingerprint. The only new operational file is the empty persistent lock; original output
  files were preserved and owned exports/backups/fixtures removed. Final installed verification
  and health checks passed, NRestarts=0, VM disk had **4.5 GiB free**, and the VM was stopped.
