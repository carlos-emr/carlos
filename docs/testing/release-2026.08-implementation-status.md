# Issue 5700 implementation status

The [corrected plan](../superpowers/plans/2026-11-06-release-2026.08-full-verification-coverage.md)
is the execution specification. This branch consolidates implementation into one
PR targeting `release/2026.08`. The PR remains draft until the remaining implementation
and qualification below are complete. No release-ready verdict has been produced; preserved qualification attempts remain incomplete.

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
  native invocations each. The development native generated lane executed 3,069 cases without failures or skips; the focused JDBC suite executed 32. Final source-bound receipts remain required.
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

## Additional implementation

- Added source/class-bound coverage merging using pinned JaCoCo 0.8.16 CLI bytes,
  all required Java lane receipts and baseline-derived line/instruction/branch
  floors for 291 files. Each input report is retained and rehashed. Wrong source,
  plan, runtime, failed/missing/ambiguous lanes, changed compiled classes and
  omitted reports fail. Six package-info exclusions have precise JAXB proof
  references. The separate production-JS/view requirement remains blocking;
  baseline floors alone do not prove all security refusals are covered.
- Added the CDS native JUnit/JaCoCo lane, fresh output-directory mapping and
  pre/post source binding. Its development run passed all 91 cases across twelve
  suites with zero skips. Routed every previously unowned H2 skip to an explicit
  native destination, including case-management lock and CDS review parameter
  invocations observed in actual native reports.
- Added independent MOXy DTO fixtures, attachment cleanup/default transport and
  stream-failure tests, typed billing validation, clinical report result/privilege
  tests, three lab helpers, demographic ISO dates and six encounter heading keys.
- The scope audit now emits hashed structured assertions and refuses
  inventory-only qualification. Unowned production paths remain failures.
- Java coverage aggregation includes each required date-zone, legacy-library and
  real-browser PDF execution in addition to H2 and native lanes, by profile and
  iteration. Source-only Java checks remain separate discovery requirements.
- The pinned CLI suite passed all 1,969 cases on Python 3.14 with MariaDB and an
  actually migrated reference schema. These prerequisites are explicit matrix
  requirements; missing access cannot silently skip database cases.
- Fixed strict standalone execution to avoid contacting an application for the
  fixture-only tier. Mixed tiers, database and mutation declarations still require
  deployment identity. All five standalone browser checks passed using locked
  Chromium 149.0.7827.55; 157 affected harness cases passed.
- Kept EBS and all four SRFax staging selections explicitly BLOCKED per the
  user's access disposition, retaining their commands and province selections.
- A fresh source-bound run at `53709c7` executed 33,500 Java cases with no
  failures/errors and 130 explicitly routed H2 skips; native closure and the
  remaining qualification slots are separate requirements. The strict Node lane
  also passed after the standalone fix.
- Added executable prerequisite checks after actual qualification exposed missing
  restic/JDK tooling and the generator suite's separate migrated BC reference.
  Missing tools now produce BLOCKED receipts before spawning a suite.
- Added a synthetic local SOAP privacy oracle. It reproduces payload logging in
  `DownloadInInterceptor`'s opt-in active path and remains a FAILED required T19
  acceptance. The default excludes this interceptor. No vendor was contacted,
  and only boolean observations are retained. This is a product finding, not an
  expected-success assertion.
- Attachment fault cleanup now also proves a secondary close failure preserves
  the primary fault and still clears thread-local state.
- Added 24 individually reviewed path bindings for these direct proofs. This is
  not a blanket ownership assignment for the remaining production scope.
- Added concrete workflow amendments to the CI proposal. No workflow or migration
  files changed.

## Remaining work before this PR can complete issue 5700

1. Finish reviewed path ownership, named security-refusal branch requirements,
   production JS and JSP range maps, and qualify the implemented Java coverage union.
2. Complete the remaining Java and harness scenarios from Tasks 7–14, notably DTO
   full fixture teardown and actual signal/recovery proofs. Qualify the newly
   registered CDS native lane through its own harness.
3. Implement and live-validate the installed changed-view walk, compatibility and
   report-budget checks, and candidate-bound package lifecycle adapter. Cover every
   applicable province, locale, profile and supported baseline install.
4. Implement the remaining external adapters, including startup failure/concurrency,
   backup/restore/retention/purge, SIGKILL recovery, native large CDS, installed
   load/privacy and controlled mutation sensitivity. Generic command result support
   does not itself implement these acceptance oracles.
5. Validate the Task 28 proposal against the final registry and actual timing
   evidence. Workflow and migration files remain unchanged.
6. Commit/freeze final source, build one candidate, then collect all required fresh
   Java, Python, Node and installed browser/package receipts. Reconcile all known
   outcomes. Existing product failures, vendor prerequisites and policy decisions
   stay blocking until resolved; never delete their checks to obtain green.

Validation commands and observed results belong in the PR description and its
private execution logs. The final qualification report must live outside the frozen
checkout; editing this document during qualification would change source identity.
