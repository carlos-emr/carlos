# Release 2026.08 verification

The [40-workstream matrix](release-2026.08-verification.json) is the executable
version of the reviewed plan. It records the cases, existing test sources,
required checks, profiles, prerequisites and repetition counts. The
[outcome ledger](release-2026.08-known-outcomes.json) retains every current browser
expected-failure pin and the five former Node TODO dispositions.

**Release qualification remains incomplete.** Passing the tooling workflow or
the ordinary Maven/Node commands does not certify this release. Historical path
ownership, coverage ratchets, unchanged-client contracts, main-only migrations,
installed package operations, vendor staging and crash/load qualifications remain
required. Checks marked `external` produce `BLOCKED`; there is no manual `PASS`
switch. Their dedicated acceptance adapters or reviewed contract dispositions
must be committed before they can become passing evidence.

## Comparison boundaries

| Scope | Pinned commit |
| --- | --- |
| Older workspace main | `27b660c00fa7c6f53317ed0ffeeccb0aebaf50cf` |
| Main used in the review | `e53e6cb555125a942bbb28e52097972a24c88ad1` |
| Release/main merge base | `b4c0dfc94e677514e6f64dd271358f0295270739` |
| Implementation base, including the facility chooser fix | `37a48abf47fa3f4561885e58baa3358e2aa746af` |

`release-scope-audit.py` reads complete NUL-delimited Git diffs. It retains
deleted, binary and renamed-as-delete/add paths and has a regression above the
GitHub compare API's 300-file limit. Candidate test names are labelled as
discovery aids. An explicit path binding must name real sources/tests, checks,
workstreams and a rationale. Unowned production paths block scope acceptance;
`--inventory-only` generates review material without claiming coverage. Test-only
packaging paths remain in the inventory with `production: false`.

The six main-only migrations are individually listed with content hashes. They
require a disposition object containing the exact `sha256` and a nonempty
`reason`. They are not automatically backported or treated as verified exclusions. Published
migrations and the branch version/SCM metadata are preserved.

## Implemented regressions and gates

* Native selection now explicitly includes Rx rollback, BCAR version saves,
  consultation projections and patient merge transactions. Generated, ON Flyway
  and BC Flyway lanes require their report families and execute with the
  packaged 32 KiB InnoDB page setting.
* Rx tests use a scoped server trigger to force the intended second INSERT
  failure under non-strict MariaDB SQL modes. They verify the preceding write
  happened, rollback restored database/workspace identities, no audit survived,
  and retry succeeds.
* BCAR tests exercise 0/1/49/50/51/101 ordinary and long Unicode text fields,
  source preservation and missing/foreign source refusal. The text mapping now
  matches the shipped `TEXT` column instead of generating `VARCHAR(255)`.
* Patient merges/unmerges are transactional, lock participant rows in a stable
  order, retain a single link for duplicate inputs and persist strict audits in the same transaction.
  Native faults prove late insert/update rollback; contending connections prove
  one winner without cycles or duplicate links. The tests also exposed and fix
  reversed parent/child bindings in the unmerge DAO. A scalar locking query
  avoids locking eager relationship gaps. The DAO's default method preserves
  compatibility for extension implementations. Separate-connection assertions
  require committed merge/unmerge audit rows after return; second-audit faults
  prove that every patient link and preceding audit rolls back together.
* Document first/additional reviews derive the actor from the session. Extra
  review preserves the stored first reviewer and time. Forged hidden reviewer
  fields have active regressions.
* Both clinical risk calculators reject HDL at/above total cholesterol, clear
  stale figures and recover after correction. Four former TODOs are active;
  the fifth former TODO is an active nginx configuration check with a nonempty
  server-selection assertion. Installed HTTP throttling remains a separate check.
* Strict browser mode rejects missing structured results, required skips,
  known failures, unexpected passes, incomplete selections, cleanup failures
  and missing/changed deployment identity. Province matching handles `ON` and
  `on` consistently. Ordinary diagnostic mode retains its classifications.
* Node and Python adapters use engine events/results rather than parsing stdout
  as evidence. They retain skips, TODOs, expected failures, unexpected successes,
  cancellation, subtest failures and both primary/cleanup errors. Node identities
  include engine numbers and parent containers so table-driven equal labels do
  not collapse distinct executions. Empty discovery never passes. Python checks
  reuse the verifier's interpreter and its pinned virtual-environment dependencies
  even when that environment is not in `PATH`.
* Prescription QR fixtures own a HAPI context with an in-memory ID generator.
  They no longer create or share the library's default counter file in the
  source checkout, and the context closes after each test.
* JUnit evidence validates filenames, owning suites, counters, actual cases,
  duplicates and first-attempt/flaky/rerun failures. It handles Surefire's real
  parenthesized lane suffixes and JUnit display-name containers. Surefire 3.5.6
  can undercount a header when nested containers share method names: only that
  exact observable collision is accepted, every case is checked, and the
  discrepancy is recorded explicitly. Unexplained counter drift still fails.
* Java discovery reconciles concrete annotated source families with reports.
  The audit found two CDS classes whose `@BeforeAll` assumptions produce zero
  tests and zero reported skips. They remain missing until their native lane
  executes. A family with only skipped cases cannot satisfy global readiness.
* JaCoCo agent data and reports are separated per lane; direct `surefire:test`
  CI invocations attach the agent. CI writes and checks each report inside the
  build container to preserve its output ownership. The diff audit reports instructions,
  branches, partially covered lines and unmapped sources, with independent
  instruction/branch gates. Proposed 90/85 floors were replaced by the required
  baseline/ratchet review.
* Real restic tests verify the shipped exclusion globs, exact restore bytes,
  retention and failure exits. Removing credential exclusions is a negative
  control that exposes canaries. A real `find -P` traversal checks the shipped
  purge predicate against nested credentials, retained ledgers and outside
  symlinks. Full installed backup/recovery qualification remains required.
  T36 follows `debian/carlos-emr.README.Debian` section 7: named staging,
  TLS and bootstrap paths are excluded; operational configuration needed for
  recovery is intentionally retained under restic encryption. Verify that the
  application account cannot read backup credentials or repository storage,
  and that recovery preserves the encryption key. A blanket assertion that
  every credential must be absent from encrypted snapshots is an invalid oracle.
  Keep its failed diagnostic attempt visible when correcting that scope.
* Chromium runs the unchanged calculator HTML/JS and exports source hashes and
  V8 execution ranges. This is explicitly an isolated fixture, separate from
  installed navigation, authorization and persistence acceptance.

## Producing evidence

Use the supported qualification tools: Java 25, Node 22, npm 10.9.9, the locked
Playwright browser, physical MariaDB 11.8.8 and the target packaging tools.
Install the hash-pinned Python parser and the system prerequisites used by the
selected checks (`xmllint`, restic, nginx/ModSecurity/CRS, and so on).

For `node-all`, set `CARLOS_CTL_SRC` to a separate checkout of the CLI release
named by `debian/carlos-ctl.pin` (currently tag `1.3.1`). The Node suite exercises
that CLI's installation and proxy contracts; omitting its source skips four
cases and cannot produce passing release evidence. Use the published tag, with
the pin's fallback commit only when that tag is unavailable, as CI does.

```sh
python3 -m venv /tmp/carlos-release-verification-venv
/tmp/carlos-release-verification-venv/bin/pip install --require-hashes -r scripts/ci/requirements.txt
python=/tmp/carlos-release-verification-venv/bin/python
"$python" scripts/ci/verify-release-evidence.py validate-plan
"$python" scripts/ci/verify-release-evidence.py list
```

Commit changes before collecting receipts. Each run gets a private UUID
directory, fresh reports, a source commit/tree identity, plan hash, runtime
versions, profile and iteration. Source/plan changes during a check invalidate
it. Raw logs and Surefire properties are private; publish only metadata that
has been reviewed for synthetic-data safety.

Build from a fresh local clone to avoid retaining removed classes/dependencies.
Set the package version that will stamp the candidate, and reuse the resulting
WAR as `CARLOS_WAR` when building the disposable validation package:

```sh
export CARLOS_RELEASE_DEB_VERSION="$(dpkg-parsechangelog -SVersion)"
"$python" scripts/ci/verify-release-evidence.py run \
  --check build --evidence /tmp/carlos-release-evidence
```

The build receipt identifies `/tmp/carlos-release-evidence/<runId>/carlos.war`.
The build does not delete earlier evidence with `mvn clean`; it uses an isolated
checkout and publishes the candidate without replacement.

```sh
"$python" scripts/ci/verify-release-evidence.py run \
  --check node-all --evidence /tmp/carlos-release-evidence
"$python" scripts/ci/verify-release-evidence.py run \
  --check java-native-flyway --evidence /tmp/carlos-release-evidence
```

The native checks require an explicitly selected disposable server URL ending
in `/`, without a database, query or embedded credentials. Credentials use the
separate native-test environment variables. Each test harness creates and drops
only its randomly named schema. Shipped Flyway schemas are not repaired by
test-only DDL.

Run packaged checks **inside the disposable installed package**, beside the
loopback `BASE_URL`. The verifier compares every installed application file
under `/usr/share/carlos-emr/webapp/carlos` with the candidate WAR before and
after the command, rejecting missing/changed/extra files and symlink escapes.
Use the installation harness's synthetic fixture environment and exclusive
installation ownership. Execute global/property-profile checks serially.

```sh
"$python" scripts/ci/verify-release-evidence.py run \
  --check packaged-full --profile ON --iteration 1 \
  --artifact /path/to/candidate/carlos.war --evidence /tmp/carlos-release-evidence
```

Complete iterations 1–3 for each full-suite province. The #5566 matrix requires
iterations 1–50 of `packaged-repeat-5566`, specifically `messenger-group-admin`.
Every property profile is a separate required execution. A failed attempt is
not erased by a later successful retry: duplicate execution identities are
refused, and a qualification series must retain its failure disposition.

Java cases that are inapplicable in H2 have exact container/name routes to a
required native, browser-PDF, legacy-compatibility or CLI lane. The H2 receipt
retains them as routed skips. Global verification requires the exact passing
case in that destination; a routing declaration alone supplies no coverage.
New or unmatched skips remain failures. A disabled parameterized template names
every required native invocation explicitly; each destination must pass, and
empty or duplicate destination lists are refused.

```sh
"$python" scripts/ci/verify-release-evidence.py verify \
  --artifact /path/to/candidate/carlos.war --evidence /tmp/carlos-release-evidence \
  --output /tmp/carlos-release-readiness.json
```

Missing, blocked, cancelled, stale, duplicated, skipped or failed required
evidence yields a nonzero exit. The source, plan, candidate, installed-byte
binding, underlying results, case discovery, profiles and repetition counts
must agree. A metadata-only dry inventory is never release acceptance.

## Review and remaining qualification

The scope inventory and coverage baselines deliberately expose open work.
The older-main Java audit measured 27,832 of 33,760 changed executable lines
covered (82.4%), 158,875/201,110 instructions and 17,196/24,947 branches in the
H2 lane. Native coverage is retained separately. Eight unmapped paths are
package metadata or JRXML resources; their appropriate compilation/render
coverage and exclusions require review. Percentages do not replace named
security, transaction and unchanged-client assertions.

Required `external` contracts are listed in the matrix with their precise
reason, including installed restarts/ciphertext, client compatibility, EBS
vendor/CXF-cache acceptance, CDS large/crash qualification, .deb install/upgrade,
backup recovery, image overwrite policy, interruption recovery and load/privacy
budgets. Scope ownership and the six main-only migration dispositions also
remain blocking. The current 33 installed expected-failure pins are not passing
workflows; each needs a fresh retake and disposition.

The workflow named **Release Verification Tooling** validates the adapters and
matrix. The existing Java/native, Node, WAF and Debian workflows exercise their
respective layers. Its green status certifies the tooling checks only. The
final release verdict is the `verify` command above, with every required result.

## Workstream index

The full case/acceptance text and required checks are in the JSON matrix.

| ID | Area |
| --- | --- |
| T01 | Comparison and traceability |
| T02 | Strict outcome accounting |
| T03 | Executable coverage and discovery |
| T04 | Native MariaDB lane completeness |
| T05 | Prescription batch atomicity and ambiguous saves |
| T06 | BCAR 2020 version saves |
| T07 | Encryption key durability and startup |
| T08 | REST XML, JSON and content negotiation |
| T09 | Schedule editing compatibility and concurrency |
| T10 | Bulk schedule names and consultation projections |
| T11 | Consultation hidden attachment preservation |
| T12 | Queue IDs and linked writes |
| T13 | E-Chart save barriers, session and logout races |
| T14 | Lab provenance, timestamps and clinical visibility |
| T15 | Inbound fax backoff and resource lifecycle |
| T16 | BC typed DAO and transaction parity |
| T17 | CSV and XLSX export contracts |
| T18 | PDF engines, fonts and print/fax layouts |
| T19 | EBS transport and WS-Security qualification |
| T20 | CDS XML and provider replacement |
| T21 | Dependency removals and shared utility compatibility |
| T22 | Response reset, filter ordering and failure privacy |
| T23 | Authorization, CSRF, patient and identity boundaries |
| T24 | Document/image lifecycle and concurrency |
| T25 | Imports, migrations and main-only omissions |
| T26 | Debian installation, reinstall and dependency locks |
| T27 | Forms, eForms and secondary-operation truthfulness |
| T28 | Clinical rules, preventions and measurement UI |
| T29 | Messaging, email and report/account privacy |
| T30 | Profile, locale, dialogs and browser matrix |
| T31 | Harness interruption and fixture cleanup |
| T32 | Known findings and truthful completion |
| T33 | Observability, load and lifecycle budgets |
| T34 | Older-main feature regression sweep |
| T35 | Clinical WAF policy |
| T36 | Backup, restore and purge |
| T37 | Authentication and token contracts |
| T38 | Cache publication and invalidation |
| T39 | Patient merge and refusal contracts |
| T40 | Production JSP and executed JavaScript |
