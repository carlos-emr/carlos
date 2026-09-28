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
initial successful status must not be interpreted as a completed review.

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
  `python-debian` package.
- The Struts source validator follows modular includes and rejects an empty
  action inventory instead of reporting success after checking zero actions.
  The live source inventory now validates 1,083 actions across 18 configs.
- Browser inventory checks recurse through `scripts/`, including the previously
  omitted document annotation check under `scripts/e2e/fax/`. CI also runs the
  coverage-helper regressions and the live Struts inventory.

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
- Expanded full Java checkpoint: 15,157 reported, zero failures/errors; the
  three Chromium Selenium checks remain pending on the installed VM.
- Final full Node suite: 1,308 passed, zero failures/skips, one test file at a time.
- Packaging contracts/subprocesses: 38 passed.
- Manifest generator and loaders: 248 passed.
- Pinned CLI source suite: 1,464 reported, with 19 opt-in MariaDB integration
  skips pending the installed VM run.
- Installation recovery: 33 passed. Coverage helper: 14 passed.
- Encoder, security-message, BDD naming, Struts DTD, JSP-taglib and locale
  checks passed before the final installed validation.

Final expanded Java, JSP/WAR/DEB, installed VM and browser results will be
recorded below once completed. The browser inventory contains 181 registered
checks, including the manual packaged first-login check and ON/BC checks.

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
heading; its nested focusable link already dispatches the click from keyboard
Enter. Transactional self-calls in the billing services intentionally share an
existing transaction with the same REQUIRED propagation; they do not require
an independent proxy boundary. Other critical classifications were primarily
complexity, repeated literals and serialization/style advice.

`check_setbundle.py` reports six repeated declarations across five JSPs and
exits 1. Manual inspection found the same default page-scoped bundle with no
intervening locale/bundle change. This remains an explicit diagnostic result,
not a claimed passing gate or a confirmed runtime defect.
