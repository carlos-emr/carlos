# Issue 5700 implementation status

The [corrected plan](../superpowers/plans/2026-11-06-release-2026.08-full-verification-coverage.md)
is the execution specification. This branch consolidates implementation into one
PR targeting `release/2026.08`. The PR remains draft until the remaining implementation
and qualification below are complete. No release-readiness verdict has been produced.

## Implemented

- Command checks can declare a `resultContract` with an exact set of boolean
  assertions and hashed underlying inputs. Both collection and final verification
  validate those files; exit zero without the required result fails. Tests cover
  missing/changed inputs, duplicate assertions/inputs, symlink traversal and final
  receipt revalidation.
- Python discovery supports an explicit package root. The o19 generator suite
  and strict pinned CLI suite are required matrix checks. The CLI adapter verifies
  the selected revision, uses the existing immutable test-copy preparation,
  preserves test and cleanup failures and rejects skipped cases.
- The generated native lane explicitly selects the JDBC compatibility suite and
  requires 32 cases. Sixteen H2 disabled parameterized templates route to both
  native invocations each. Actual native execution remains required.
- All 16 manual manifest checks have explicit required owners and province
  selections. Plan validation rejects removing required manual coverage.
  First-login qualification stays external until its installation-stage adapter
  exists; it cannot be run against an already-reset shared account as a substitute.
- Browser acceptance checks require reported Chromium identity to match the
  installed Playwright metadata and committed lock. Missing/mixed/wrong versions
  fail instead of silently qualifying an arbitrary installed browser.
- Scope candidate generation covers the union of all three pinned comparisons,
  including deleted paths. It never promotes name matches to coverage. Writing
  requires an explicit reviewed bindings file; the 2,448 previously unowned paths
  have not been silently assigned or excluded.
- Server-log auditing supports explicit `--include-warn`, with payload-preserving
  privacy behavior and tests. Qualification must still integrate that audit into
  required iteration evidence and review actual warning baselines.
- Direct Java coverage was added for string/character maps, Date XML conversion,
  REST JSON introspection, fax backoff and BC deployment guards. Eleven harness
  libraries now have direct Node tests, including uncertain-write draining,
  scoped refusal handling and cleanup/error preservation. These unit tests do not
  replace installed ownership, signal or fault-injection qualification.
- Stale matrix contracts for tokenless appointment edits, retired outcomes and
  already-shared migrations were corrected without changing production behavior.

## Remaining work before this PR can complete issue 5700

1. Finish reviewed path ownership, changed-line/instruction/branch floors and
   compatible H2/native coverage aggregation; add production JS and JSP range maps.
2. Complete the remaining Java and harness scenarios from Tasks 7–14, notably DTO
   wire fixtures, report/lab/encounter paths, EBS interceptors, full fixture teardown
   and actual signal/recovery proofs. Ingest CDS native JUnit through its own harness.
3. Implement and live-validate the installed changed-view walk, compatibility and
   report-budget checks, and candidate-bound package lifecycle adapter. Cover every
   applicable province, locale, profile and supported baseline install.
4. Implement the remaining external adapters, including startup failure/concurrency,
   backup/restore/retention/purge, SIGKILL recovery, native large CDS, installed
   load/privacy and controlled mutation sensitivity. Generic command result support
   does not itself implement these acceptance oracles.
5. Prepare the exact workflow proposal permitted by Task 28. Workflow and migration
   files remain unchanged.
6. Commit/freeze final source, build one candidate, then collect all required fresh
   Java, Python, Node and installed browser/package receipts. Reconcile all known
   outcomes. Existing product failures, vendor prerequisites and policy decisions
   stay blocking until resolved; never delete their checks to obtain green.

Validation commands and observed results belong in the PR description and its
private execution logs. The final qualification report must live outside the frozen
checkout; editing this document during qualification would change source identity.
