# Alpha16 promotion review — PR #4055

Review started on 2026-09-28 against `origin/main` (`d84c62490f`) and the
promotion branch `claude/promote-2026-08-to-main-0yklth`, initially
`c37ceac51c`. Review fixes remain on that branch. This document records
completed validation and explicit remaining release requirements; a passing
source test is not evidence of a passing installed workflow.

## Review scope

Four reviewers divided the promotion and cross-checked fixes:

- Clinical: lab/HRM routing and provenance, patient matching, demographic
  searches and export, prevention transactions, Messenger membership and
  migrations.
- Billing and workflows: Ontario export rollback/locking, BC simulation,
  unbilled reports, prescriptions, patient letters/PDFs and ticklers.
- Packaging: separate CLI ownership/dependencies, maintainer scripts,
  OSCAR 19 manifests and generator, release workflows and documentation.
- Cross-cutting: exception rendering, encoding, document links, legacy model
  conversions, encounter editor sequencing, inbox pagination, test discovery,
  review threads and CI.

The original promotion contains 470 changed paths, including deletion of the
CLI implementation now maintained in `carlos-emr/carlos-ctl`. The review
compared the former Python data definitions with the shipped JSON manifests:
all 41 runtime fields matched after tuple/list normalization. CodeRabbit
skipped its initial review because the PR exceeds its 300-file limit; its
initial successful status must not be interpreted as a completed review. The
requested incremental review at `6a3d4b5203` was also refused: 527 files exceed
the same 300-file limit. No CodeRabbit approval is claimed.

## Findings corrected

- Lab reconciliation now considers every current forwarding recipient even
  when the direct MRP already has an acknowledged or independent assignment.
  Tests retain acknowledgement/provenance and verify revocation after patient
  correction. Forwarding now also respects the report-type selection: an
  HRM-only rule cannot grant HL7 access, and removing HL7 from a rule revokes
  its generated lab assignment. College/HSO provider matching now uses the
  actual schema columns rather than silently falling back to OHIP identifiers.
- Migration `1.0.40` widens lab labels to TEXT. A real archived report's
  356-character panel label previously failed the 255-character insert limit;
  the migration preserves existing values and retains complete new labels.
- Prescription favorite edits retain the selected row identity when another
  favorite has identical content. The editor preserves weeks/months and
  internal-dispensing flags; edits require POST and the trusted prescribing
  provider's ownership. Creation deduplication compares all persisted prescribing
  fields, so different routes, doses, drug identifiers and dispensing settings
  cannot silently alias another favorite. The parameter-free editor GET remains available.
  Editor controls receive associated labels after Sonar identified missing
  accessible names on the favorite-name and duration fields.
- Rx patient lookup and vacancy criteria resolve DAOs/managers from the
  active Spring context, avoiding stale statically cached dependencies.
- Queue-name lookup binds the supplied name instead of concatenating HQL.
  Teleplan status lookups bind the mapped Character type and reject invalid
  multi-character inputs.
- Inbox date filters bind timestamps and use mapped document dates instead
  of parsing native JDBC date strings. This preserves results when Hibernate
  returns `LocalDateTime` values for the received/created date preference.
- eForm conversion accepts missing subjects, preserves metadata and resolves
  the file manager from the active context. Regression tests inspect a real
  generated PDF's text and page count.
- JDBC routine calls include parentheses for zero arguments, keeping the
  callable syntax portable for null and empty parameter arrays.
- Inbox page failures release the loading state and expose a retry for the
  same page. Request identity and result-generation checks reject stale
  callbacks. Browser coverage deliberately fails a page load and retries it.
- Duplicate provider identifiers no longer count the same appointment slot
  repeatedly toward the configured next-slot ordinal.
- Messenger diagnostics sanitize request-controlled values. BC simulation
  forwards dispatcher parameters without treating them as raw HTML output,
  accepts an absent optional start date and rejects a missing end date.
- Packaged first-login checks use cryptographic password generation, the
  configured packaged Chromium and one application context for either a host
  URL or a URL ending in `/carlos`. HTTPS is required before login.
- CLI download diagnostics no longer corrupt the package-path stdout
  contract. CI fails if neither the pinned release nor its explicit source
  fallback is available. Explicit CLI source selection takes precedence over
  an installed CLI in generator and migration verification tools.
- Packaging Python discovery avoids collision with the system
  `python-debian` package. Full regeneration against the pinned OSCAR19 source
  also found four stale manifest entries: both province profiles omitted the
  two MRP provenance columns added by migration `1.0.39`. The corrected manifest
  has a general regression comparing every destination column inventory with
  the current migration sources; import conversion mappings are unchanged.
- The Struts source validator follows modular includes and rejects an empty
  action inventory instead of reporting success after checking zero actions.
  The live source inventory now validates 1,083 actions across 18 configs.
- Browser inventory checks recurse through `scripts/`, including the previously
  omitted document annotation check under `scripts/e2e/fax/`. CI also runs the
  coverage-helper regressions and the live Struts inventory.
- Installed inspection exposed sidebar loading that stopped its indicator on
  the first response. The loader now tracks each module until completion and
  rejects obsolete responses. The browser harness waits for that state rather
  than briefly stable link counts; its regression deliberately delays a module.
- Favorite saves now return an explicit 204 response after persistence. The
  editor finishes the response before reporting success, rejects redirected
  login/error responses and clears stale success on failure. The browser check
  exercises a failed save followed by a real successful retry.
- Upgrade snapshots now support the configured database/administrator and fail
  on SQL errors, missing administrator rows and incomplete snapshots. Verification
  checks command outcomes, validates document-query results and compares
  credential digests instead of printing password hashes or PIN prefixes.

## Test discovery

The previous Surefire include list omitted 113 concrete annotated test
classes. The default build now discovers `*Test` and `*Tests` in every
package. The first expanded run executed 15,113 tests and exposed stale
fixtures that the prior green default run had not exercised. These included
obsolete appointment-search mocks/reflection, missing Struts text-provider
and schedule-manager fixtures, and an incorrect email-length boundary.
The tests were repaired against the current behavior and kept enabled.

Skipped-test reasons were audited against current production code. Obsolete
skips were removed for transaction-bound population queries, JDBC callable
statements, fax destination failures, forwarding and demographic fixtures,
relationship/message/style queries and PDF advisory handling. The JDBC tests
use actual H2 aliases to verify call syntax and bound arguments; they do not
claim to validate MySQL stored-routine definitions.

Builds and test suites run serially with one Maven fork. The VM remains stopped
during compilation; installed browser checks run one at a time. Host and VM
memory, disk space and pressure metrics are checked between phases.

JaCoCo excludes the generated third-party Drools `DRL6Lexer`, whose method
exceeds the JVM size limit after instrumentation. CARLOS application code and
rule execution remain in scope.

## Completed validation

- Initial configured Java suite: 14,161 reported, zero failures/errors,
  51 existing skips.
- Final full Java/JSP/WAR run at application revision `6a3d4b5203`: 15,166
  reported, zero failures/errors; the three Chromium Selenium checks skipped
  in that host run subsequently passed on the installed VM. The last favorite regressions separately passed
  54 tests before the complete rerun.
- JSP compilation: 985 JSPs, zero errors. Javadoc: completed, 34 warnings, no
  errors. WAR commit identity verified against the pushed application revision.
- JaCoCo promotion changed-line audit: 2,265/2,454 executable Java lines covered
  (92.3%); the only unmapped file is documentation-only `package-info.java`.
  Review fixes: 159/176 (90.3%). Additional vacancy lifecycle/context, inbox
  fallback-date and favorite privilege regressions passed 66 tests with no skips.
- Latest full Node suite: 1,363 passed, zero failures/skips, one test file at a time,
  with the pinned CLI checkout configured. This includes browser cleanup failures
  and physical/inline document verification. The prior 1,349-case run and its
  CLI-dependent rerun also passed.
- Packaging contracts/subprocesses: 38 passed.
- Manifest generator and loaders: 249 passed. Full manifest regeneration
  matched the acquired upstream archive at `a7900d569d3faf741993e5e1da8c14021bbefede`.
  The evidence runner supplies only the archive commit provenance absent from
  a non-Git checkout; generation and comparison execute unchanged.
- Entity audit: 1,760 field pairs across 255 shared entities, no name mismatches.
  Implicit fields, `@JoinColumn` and Hibernate XML mappings are outside that audit.
- Pinned CLI source suite: 1,464 reported, including 19 opt-in MariaDB integration
  skips in the host run. All 19 subsequently passed against the installed VM.
- Installation recovery: 33 passed. Coverage helper: 14 passed.
- Encoder, security-message, BDD naming, Struts DTD, JSP-taglib and locale
  checks passed before the final installed validation.

The browser inventory contains 181 registered checks, including the manual
packaged first-login check and ON/BC checks. The complete browser run remains
in progress; the focused results below do not replace it.

## Installed validation

The three `2026.08.0~alpha16~pr4055.2` Debian packages were built successfully
from `b95625c5f6` and installed over the existing alpha16 review packages in
the Ubuntu 26.04 `carlos-val` VM. A private database/configuration backup was
verified before the upgrade. Both host and guest storage gates passed; the
VM was stopped throughout compilation and package construction.

- Verified 9,860 installed WAR/schema/manifest/Chromium/DrugRef payload hashes
  and 23 separate CLI files, including package ownership. The CLI test package
  comes from the pinned source revision; it is not a published release asset.
- HTTPS readiness and `carlos-ctl check` passed. Migration `1.0.40` applied
  successfully and the live lab-label column is TEXT.
- All three environment-gated Java Selenium checks passed using installed
  Chromium and precompiled test classes: no failures, skips or aborts.
- Direct Playwright inspection captured the encounter and favorite editor.
  Screenshots were inspected; all 11 inspected favorite inputs/selects had native
  associated labels. The first inspection navigated away before Rx requests
  completed and correctly failed on aborted requests; the corrected inspection
  waited for them and passed.
- The prevention row-link workflow passed, including actual keyboard Enter
  on the heading anchor, popup editing, panel refresh, preservation of unsaved
  notes, invalid-save rejection and merged-history selection. Its first added
  keyboard assertion inspected the popup's initial blank document; explicitly
  waiting for the destination corrected that test timing issue.
- Live fee-migration checks passed for 34 fees, overrides and rerun idempotence.
  The lab-label migration preserved original, NULL, empty, Unicode and long
  labels and passed its repeatability check.
- The historical DDL oracle compared 1,975 statements with MariaDB without a
  mismatch or an unbuildable probe. The server rejected 1,593 other historical
  statements; those are explicitly uncomparable, not successful comparisons.
  Transport-integrity and import SQL-semantics checks also passed, including
  damaged-transfer rejection and oversized-binary export failures.
- The live email-configuration schema/rollback check and standalone rich-text
  measurement browser check passed with the installed Chromium.
- Legacy renderer refusal/dependency-veto/reconfiguration recovery passed,
  followed by a successful installed health check.

The subsequent `2026.08.0~alpha16~pr4055.3` packages contain the application
fixes at `a68b116161`. All 9,860 payload files and the installed CLI were
verified, followed by successful application health checks. The WAR build
compiled 985 JSPs without errors and passed 38 focused Java regressions.
Build logs, WAR SHA-256, package checksums and installed payload hashes record
provenance; the WAR manifest itself does not contain a full Git SHA.

The installed delayed-sidebar and favorite-save checks both passed (112.8s and
25.3s respectively), including held-response loading, all expected modules,
13 favorite label associations, failed-save visibility and successful retry.
The exact upgrade verifier passed 45 assertions against the private pre-install
snapshot. Its first run exposed a verifier false positive for an inline HTML
link with no physical file; corrected eligibility matches Java trimming, with
ten live MariaDB edge cases passing. No placeholder file was fabricated.

Three inbox acknowledgement checks now restore their exact original routing
state. Consultation signature checks own their requests and remove only owned
requests, signatures and verified preview files. Cleanup failures remain test
failures. The full script suite covers lost replies, ignored database writes and
changed files.

Full browser, corpus and isolated ON-import/BC-profile results
will be recorded after their respective runs. Missing validation fixtures are
prepared with private baselines and ownership journals, without treating a
fixture-dependent skip as a pass.

## Migration and publication requirements

Migration `1.0.39` does not depend on the unmerged attachment feature in
PR #3996. Its reserved migrations `1.0.37` and `1.0.38` are absent from this
promotion. Before that feature later merges, its unpublished migrations must
be renumbered above the then-current release high-water mark. The migration
inventories now state this explicitly; published SQL checksums are preserved.

The pinned `carlos-ctl` 1.1.0 GitHub release was not published at review start.
Its source fallback and a local test package can validate compatibility, but
cannot satisfy the public-release prerequisite. Before promotion/tagging,
publish that dependency in its own repository and run
`debian/fetch-carlos-ctl.sh` with authenticated `gh`, without a local package
override or attestation bypass. No release tag or merge is performed by this
review.

## Additional static-analysis triage

Sonar's initial report contained one minor bug classification for an onclick
heading. The installed browser regression proves that its nested native link
dispatches the heading action from keyboard Enter and preserves the encounter.
The two subsequent missing-label findings were fixed and cleared by the next
scan. That scan flagged a repeated custom-name ID across mutually exclusive
JSP branches; the unnecessary ID on the hidden input was removed, preserving
the visible control's label association. A fresh scan remains required.
Transactional self-calls in the billing services intentionally share an
existing transaction with the same REQUIRED propagation; they do not require
an independent proxy boundary. Other critical classifications were primarily
complexity, repeated literals and serialization/style advice.

`check_setbundle.py` reports six repeated declarations across five JSPs and
exits 1. Manual inspection found the same default page-scoped bundle with no
intervening locale/bundle change. This remains an explicit diagnostic result,
not a claimed passing gate or a confirmed runtime defect.
