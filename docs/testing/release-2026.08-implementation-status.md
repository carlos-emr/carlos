# Issue 5700 implementation status

The [corrected plan](../superpowers/plans/2026-11-06-release-2026.08-full-verification-coverage.md)
is the execution specification. This branch consolidates implementation into one
PR targeting `release/2026.08`. The PR is open for review, but the remaining implementation
and qualification below are still required to complete issue 5700. No release-ready verdict has been produced; preserved qualification attempts remain incomplete.

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
  First-login qualification now has an installation-stage command adapter. It
  requires the private installer file and an independently observed forced-reset
  flag before driving the browser, then verifies password change, reset clearance,
  preserved identity/PIN and locked browser. An already-reset account fails before
  another browser login. Province and final source-bound acceptance remain required.
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
- Fresh Ubuntu 26.04 installation of the `53709c7` candidate completed. Full
  installed-tree attestation matched all 9,094 candidate files plus reviewed
  package transforms. This is development lifecycle evidence, not a complete
  upgrade/reinstall verdict.
- Live execution exposed and corrected two harness defects: native readiness now
  waits for MariaDB's final TCP listener (its temporary initialization socket is
  insufficient), and the first-login check refuses a reset field that truncates
  its supplied password before submission. The failed records remain preserved;
  the owned test account was recovered only after an independent hash comparison
  confirmed the form-limited credential. Twelve native harness tests and 22 login
  harness cases pass.
- Added 30 individually reviewed path bindings for these direct proofs. This is
  not a blanket ownership assignment for the remaining production scope.
- Added concrete workflow amendments to the CI proposal. No workflow or migration
  files changed.

- Added required main/release binary-client comparison: 612 identical legacy XML
  requests across 308 types, 14 direct XML/JSON outputs and a local privacy canary.
  It verifies the published main package pin and candidate WAR, uses Tomcat's
  class loader and each binary's own production mapper/module definitions, retains
  hashed observations, and rejects changed text/order/types or missing cases.
  Development comparison found 589 matching wire cases and 34 unreviewed XML
  differences; three main-side defects improve and the SOAP exposure is pre-existing.
  These findings remain failing, with no allowlist. See
  [the comparison record](release-2026.08-main-release-comparison.md).
- Corrected production mapper unit setup to execute its SmartDateModule
  registration. Converted document-upload, tickler-crud and login smoke scripts
  to structured cooperative completion, actual browser identity and cleanup that
  attempts every restoration even when an earlier action fails.

- Live interruption checks exposed an orphan page-rendering cache file and a
  swallowed document cleanup error. Upload cleanup now inventories the configured
  stores before writes, removes only owned new files/page renderings, preserves
  pre-existing entries/links and propagates every cleanup failure.
- Real OS signal checkpoints wait for confirmed SIGINT/SIGTERM delivery; historical
  synchronous handler injection is kept visibly separate. The signal audit now
  recognizes actual checkpoint calls with an AST and independently verifies the
  shared credential's password/reset fields, not only row counts. Login uses its
  own disposable account to keep repeated failure/reset checks from locking the
  shared operator. Final source-bound/province acceptance remains required.

- The installed ON signal adapter passed all six actual OS interruptions using
  locked Chromium 149.0.7827.55, with no added/removed/changed fixture-store entries
  or SQL residue and exact original credential restoration. The disposable-login
  success path also passed. These development receipts preserve the earlier cache
  residue and lockout failures; BC and final source-bound qualification remain.
- The Chromium gate now accepts the runner's actual `chromium <version>` metadata
  while still rejecting wrong browser families, mixed versions and missing identity.

- Strict Node validation passed all 7,308 cases with zero skips, TODOs,
  cancellations or failures after providing the pinned CLI source prerequisite.
  Focused production mapper tests and binary/signal adapter negative controls pass.

- Fresh `bdce201` Java qualification passed 33,501 cases; all additional generated,
  ON/BC Flyway, date-zone, legacy-library, PDF and seed lanes passed. The CDS native
  first attempt failed before tests because its builder was attached to Docker's
  `none` network. After disconnecting that network, the separately linked diagnostic
  run passed all 91 cases. The original failure remains in its immutable root.
- Tightened API comparison for protected subclass contracts and potentially
  breaking additions (abstract methods, enum constants and competing overloads).
  Conservative flags require client review; they are not automatic regression verdicts.
- Corrected T36's backup oracle to match the public documented recovery contract:
  named exclusions and encrypted operational recovery configuration have distinct
  requirements. The initial overly broad exclusion failure remains preserved.
- The clean `bdce201` candidate installed successfully on the minimal Ubuntu 26.04
  runtime image and passed `carlos-ctl check`. The new installation-stage adapter
  passed its seven browser steps and all five independent assertions using locked
  Chromium. All 239 tooling cases passed on Python 3.14. The host Python 3.12
  diagnostic retains two errors in 3.14-specific ZIP/scaling fixtures; it is not
  substituted for the required interpreter.
- Added an inner installed package transition adapter and a required ON/BC
  reinstall check. It pins all package inputs, rejects an invalid version order
  before mutation, verifies the predecessor against every application file in
  its actual published package, and compares every cell in eleven clinical
  tables plus document bytes/directories/ownership/modes and the encryption key.
  It also runs the existing upgrade preservation contract and binds the final
  application to the candidate WAR. Its ON diagnostic passed all nine assertions
  and removed its exact file canary. Clean install, unchanged-client reopen,
  supported main upgrade, unsupported-upgrade cases and BC remain separate
  requirements; the full lifecycle check has not been replaced by reinstall.
- Repeated installed ON smoke and all six OS interruptions after reinstall:
  PASS with no SQL/file residue. Both WAR and actual Debian application manifests
  match all 9,094 installed files. These remain development lifecycle observations.
- A separate Java 25 API oracle passes all ten assertions with real client
  compilation failures and a still-inlined old constant. The expanded comparison
  retains 28 changed/removed and 35 additive API type candidates; none is waived.
- Python tooling pins required case identities as well as its count;
  replacing an omitted regression with an unrelated passing test cannot qualify.
  The initial development run executed 252 cases successfully on Python 3.14. The live transition adapter also
  refused an equal-version "upgrade" before any package transaction or canary.
- Package preservation now recognizes the explicitly managed eForm editor script
  using exact old/new package entries and unchanged metadata. Clinical files and
  seeded/customizable templates remain exact; this does not waive JS behavior
  acceptance. The earlier blanket file-equality attempt remains failed.
- Version comparison uses the actual Debian control field. The earlier note
  inferred a dotted control version from an asset filename; published beta6 is
  actually `2026.08.0~beta6`. The adapter already read the field correctly; the
  test name and documentation are corrected, with both version forms covered.

- Added a distinct beta5 historical package baseline in both provinces; beta6 alone
  cannot exercise the six later migrations or CLI 1.3.0 dependency transition.
- Fixed Rx fixture cleanup to remove note extensions before parent notes. Real
  SQLite checks reproduce both restrictive-FK failure and orphan behavior before
  the fix; both pass afterward. All 7,310 Node cases pass with no skips or TODOs.
  These are development checks; fresh frozen-source qualification remains required.

## Review corrections

- The coverage ratchet invokes its standalone checker so sibling imports work
  without a custom `PYTHONPATH`. Its regression executes the real checker against
  a committed fixture and preserves both passing and failing floor results.
- The CI Maven cache is ignored as generated output. Source checks continue to
  reject untracked application files and edits to any tracked file. This corrects
  the native CDS workflow's refusal before physical database tests could start.
- API inventory includes protected nested types. The real-client oracle now
  verifies twelve assertions, including a subclass in another package that loses
  access to a protected member type. Incomplete API diagnostics retain exception
  classes without printing arbitrary exception messages or stacks.
- Upload cleanup binds the document and rendering directories to their observed
  identities. Replaced directories and symlinks fail cleanup without deleting
  unrelated files; independent safe cleanup actions still run.
- The combined Python tooling registry pins 278 required case identities, including the
  coverage-command, stale-class, process-group and source-cleanliness regressions. `xmllint` is an
  explicit prerequisite for the existing streaming XML fixture tests.
- Every class-bound Java lane discards old compiler outputs before compiling;
  direct Surefire lanes now use the Maven lifecycle. The native release lane
  recompiles and compares the actual container/host class bytes. Evidence outside
  compiler output directories is retained. Run these lanes sequentially in a
  dedicated checkout. The JaCoCo merger validates the exact Java executable it uses.
- Clinical fingerprints preserve fractions and widen FLOAT exactly before
  encoding, so even adjacent float values remain distinguishable. Eight real
  MariaDB numeric/NULL/binary oracles run with the native database gate.
- Package operations and installed signal audits bound and stop their process
  groups, including descendants that survive their parent. Cleanup OS errors also retain the unverified state. Uncertain interruption
  records require fixture reconciliation; the signal adapter starts no further
  audit after a failure. Its deadline includes the manifest timeout plus cleanup.
- No-op path-binding updates retain plan bytes. Evidence path checks stop at the
  owned root, login fixtures use the same MySQL connection as their smoke test,
  and the proposed qualification archive lives outside the source checkout.

- Added a host/container package lifecycle core controller with candidate clean
  install/reinstall, main upgrade and distinct beta5 upgrade in ON and BC. It
  preserves read-only clients, first-login ordering, whole-data cleanup checks and
  private failed-cell recovery evidence. Its explicit orchestrated deployment
  scope keeps packaged-byte binding during collection and final verification.
  Eight regression checks cover missing baselines, wrong bytes/source/province,
  failed cleanup, modified evidence and the actual host receipt path. Live/final
  qualification and the broader unsupported/interrupted lifecycle cases remain.
  See [execution prerequisites](release-2026.08-package-lifecycle.md).

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

The first lifecycle core development attempt stopped before package installation
because forwarding the outer proxy hostname replaced Docker's reachable proxy
default. That failed record remains retained. The controller now preserves Docker's
configured proxy/registry settings and removes only endpoint/TLS selectors. APT
index failures are fatal. The historical cell also requires actual older-CLI
dependency refusal, compatible resolution and unchanged package/data state.

Review follow-up validation also exercises detached Chromium termination using
`python3 scripts/ci/run-browser-process-oracle.py` after `npm ci` and installation
of the locked Playwright browser. The real timeout, SIGTERM and SIGINT cases force
SIGKILL and verify that owned browser sessions stop; fixture restoration still
requires independent reconciliation. Failed lifecycle containers are stopped and
retained, including after a Docker client timeout, with explicit shutdown and
reconciliation status. The MariaDB preservation oracle also compares latin1 and
utf8mb4 text while retaining exact binary and numeric comparisons.

Browser supervision refuses a worker with pre-existing children before enabling
Linux adoption or launching an audit, so unrelated descendants cannot become
owned after their parent exits. The outer receipt runner defers startup signals
and retains reconciliation status, allowing the lifecycle controller a bounded
120-second shutdown window to stop its container before escalation.
