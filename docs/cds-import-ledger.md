# CDS import ledger and recovery contract

The shared import engine targets `release/2026.08`. Its HTTP actions and verified CLI use
`CdsImportService`; neither adapter performs clinical writes. Class responsibilities follow
[the Ontario billing module](billing-ontario-module.md) and the repository layer names:

| Responsibility | Class |
| --- | --- |
| Durable request document and accepted source identity | `CdsImportJobService` |
| Complete source discovery, semantic validation and bounded work descriptors | `CdsImportPlanningService` |
| Run/patient state, exact expected work and canonical provenance | `CdsImportLedgerService` |
| One owning native transaction and atomic checkpoint | `CdsImportUnitService` |
| Individually addressed original/normalized source fields | `CdsImportFieldPersister` |
| Immutable file generations, adoption and retirement claims | `CdsImportFileService` |
| Bounded execution, recovery and finalization | `CdsImportExecutionService` |
| Protected streamed operator reports and fenced report selection | `CdsImportResultService`, `CdsImportReportService` |
| Versioned correction/decline requests and full revalidation | `CdsImportReviewService` |

DAOs each access their own table. Service boundaries exchange immutable DTOs, identifiers and
protected spool locators. They do not retain servlet requests, logged-in entity graphs, XML DOMs
or whole-patient clinical collections across stages.

## Ownership and transactions

A dedicated unpooled coordinator advisory connection excludes competing source planning and
execution across application nodes. Clinical admission additionally requires the drained
[maintenance protocol](cds-maintenance-admission.md), target engine/trigger verification and a
new database owner epoch. Ownership is not inferred from a heartbeat or wall-clock lease.

Every clinical unit rejects an ambient transaction and starts an explicit `READ_COMMITTED`
transaction through the configured JPA transaction manager. It locks admission, the run/epoch,
the unit and canonical lineage; verifies source/options/rules and dependencies; then executes
the native callback. Native rows, target binding, the import hold, individually addressed fields,
file references, exact target receipt, checkpoint and strict import event commit together.
Checked failures roll back. Required audits are not best-effort writes.

The active marker remains installed through the explicit native/JPA flush. The physical datasource
guard requires verified Unicode parameter/session capabilities and `STRICT_ALL_TABLES` for active
import writes, restores prior session settings before pooled reuse, and fences every clinical
write against the current maintenance owner. See the maintenance contract for ordinary JDBC,
background-writer exclusion, lost-owner behavior and deployment prerequisites.

The checkpoint is an acknowledgement of a known atomic unit. An exception after an owning
commit can mean `UNIT_COMMIT_OUTCOME_UNKNOWN`; it does not mean that the unit rolled back.
A successor verifies the durable checkpoint and exact native receipt before continuing. It
never blindly replays a unit with a committed receipt. Fatal worker errors unwind transactions
and retain maintenance/holds; a healthy supervisor records a stopped outcome from durable state.

## Sealed expected work and patient completeness

Planning freezes the entire discovered source workset before the first native mutation. The seal
includes patient/copy lineage, every native and helper key, source digest, category, ordinal,
work locator and dependency. A separate execution digest reconciles the administrative keyset.
After sealing begins, additions and replacements are refused; interrupted sealing can finish
only against the same manifest and owner epoch.

Native anchors, source-field pages and bounded contact/care child units are all expected work.
Dependencies are patient-local, limited to eight per unit and ordered from demographics to native
anchors to helpers, which prevents cycles. Source-field pages contain at most 32 fields.
Helper units count towards operational completeness; they do not inflate imported clinical
occurrence counts. Equal repeated source occurrences remain separate occurrences.

A new chart and its hold are bound in the same transaction. The hold is separate from the native
patient status. An incomplete chart stays held even if the worker, report writer or connection
fails. Patient finalization verifies the exact sealed keys, checkpoints, dependencies, native
receipts and physical file dependencies, then atomically publishes the patient outcome and
releases its hold. A completed patient is not republished during another patient's recovery.

A zero-patient or unreadable source cannot produce a successful empty seal. A fully discovered
batch may have zero units only when each identified patient has an explicit pre-write refusal
or other recognized pre-write disposition. Refused and never-started patients remain visible.

## Source lineage, no-op imports and review

Stable unit keys derive from the registered source namespace, source patient identifier, copy,
source section/occurrence and helper ordinal. Run IDs and mapping options do not define clinical
identity. Checkpoints separately pin source, mapping options and ruleset digests.

A repeated import may reuse prior work only when every expected key has a matching checkpoint,
the originating patient was fully published, and the current target matches the exact canonical
identity. Missing checkpoints or a partial original attempt require resuming the original import.
Target receipt or file drift requires reconciliation rather than overwriting subsequent edits.

Dry runs have no native checkpoints, holds or imported counts. Their terminal state is
`DRY_RUN_COMPLETE`, distinct from clinical completeness. Mapping changes following only a preview
may revise an unbound, checkpoint-free canonical lineage when source/rules are unchanged. A
committed or bound lineage cannot be silently revised.

For an actual attempt that wrote nothing, changing raw source, mapping policy or rules requires
an explicit `APPROVE_UNWRITTEN_REVISION` review on a newly accepted replacement submission that
reports `SOURCE_REVISION_REVIEW_REQUIRED`. The old accepted source and plan remain immutable.
The review archives the conflicting old/new fingerprints and paged prior-run provenance. Its
publication transaction locks the canonical lineage, compares the old fingerprints and review
version, and refuses any canonical target, checkpoint, hold, advanced unit or unresolved commit
uncertainty. Only that zero-write proof permits rebinding the canonical fingerprints; old plans
remain fenced from clinical writes. Operators never need to invent a namespace or edit database
rows to approve a legitimate unwritten replacement.

An explicit review uses a compare-and-set review version. It archives the prior patient/unit/
dependency plan with counts and a durable digest, validates the effective corrected source against
the pinned schema, and publishes an immutable correction manifest. Only a checkpoint-free,
unbound, unheld, unsealed plan can reset. `REPLANNING` freezes ordinary planning while bounded
pages remove old expected units, dependencies and the preflight identity register. Patient IDs,
lineages, original source and archived evidence remain. Interrupted resets are restartable.
A scalar set that still fails the whole pinned XSD remains inactive as
`DRAFT_REQUIRES_CORRECTION`; subsequent edits can build on it while the original XML remains
immutable. An unresolved draft blocks resume. The same verified reviewer may carry their inactive
fields forward; another reviewer must explicitly restate or replace those inherited fields before
publication. Scalar provenance retains each origin. The archived patient/unit/dependency counts
are checked independently against the database before reset.

The fresh plan repeats schema, normalization, batch identity and target eligibility checks before
retiring the corresponding old blockers. Acknowledgement alone cannot resolve a blocking defect.

Review requests and imports share the same bounded worker. A pending review prevents execution
of the old plan. Explicit resume intent is durable as `RESUME_REQUESTED`; a full in-memory queue
does not lose it. A later cancellation remains authoritative at owner acquisition. Automatic
continuation after a review never clears a newer cancellation.

## Files and memory bounds

Source snapshots, parsed values and native documents are private immutable generations. Expensive
source work, hashing, decompression, validation, publication and report I/O occur outside clinical
transactions. Values remain streamed; large fields use protected content references with offsets,
lengths and SHA-256 digests. Source-field namespace, XPath and occurrence remain individually
addressable, including same-local-name fields from different namespaces.

Published file adoption and retirement lock the same database row. Retirement first claims an
unreferenced generation; future references are then refused before unlinking. A deletion tombstone
prevents replacement bytes from adopting an old path. Referenced files include dependencies
inherited through verified prior-run no-op checkpoints.

A per-owner-epoch physical proof prevents rehashing a whole source values blob for every field
page. The proof cache is operational metadata, excluded from the immutable native receipt hash.
Final reconciliation still verifies referenced bytes outside database transactions.

Pages are bounded at 200 rows; source field pages at 32. Seal calculation batches dependency
metadata for 200 units instead of making an unbounded graph or a query per dependency. Final
native receipt verification uses one semantic unit per owning transaction. Operator report files
are streamed row by row, rather than accumulated in memory. These are explicit bounds, not a
promise that arbitrarily large input requires no disk or execution time; configured source,
expansion, private storage and retention limits remain admission requirements.

## Operator outcomes and protected evidence

Run state, per-patient outcome, clinical completeness, report delivery and cleanup are separate.
`COMPLETE` alone does not mean that the operator's files were delivered. `cleanComplete` requires
clinical completion (or an explicitly valid dry run), selected durable reports and completed cleanup.
A report-only retry preserves clinical commits and patient publications. Report/cleanup failure
never overwrites the primary import failure, and a failed delivery terminates polling with an
explicit failed operational state rather than hanging indefinitely.

Successful clinical completion and successful preview completion atomically persist
`RELEASE_PENDING` before releasing clinical admission. This preserves the maintenance boundary
if the process dies between `COMPLETE` and closing its dedicated owner. The engine resolves the
pending release only after that owner reads back its inactive generation with no global holds
while it still owns the advisory lock. A retained chart from another run produces
`RELEASE_DEFERRED`, without reclassifying the completed clinical outcome. A failed owner close
retains a specific operational reconciliation diagnostic. Neither filesystem cleanup nor report
delivery can clear these states or make `cleanComplete` true. Explicit resume of a completed run
reacquires the real maintenance owner, verifies the target catalogue and global hold/drain proof,
then retries release and regenerates the reports; it does not replay clinical writes. The explicit
release-retry request is durable even if its queued worker is lost or the process restarts.

Cleanup records an actual bounded catalogue of retained/removed artifact counts and logical bytes.
Accepted snapshots, parsed source, review evidence, durable report sets and resumable native
publications have explicit retention reasons. Unknown ownership, missing registered publications
and mismatched bytes remain retained and produce a failed cleanup outcome. A proved private
publication alias/journal can retire after its final immutable publication is reconciled; a private
unpublished partial can retire without deleting its accepted source. Unknown byte counts are
reported as null, never as a fabricated zero. The current report generation is explicitly excluded
from this catalogue while its durable publication completes. Global expiry cleanup separately
handles owned unaccepted source generations and upload spools with bounded work and visible
administrative warnings; missing acceptance acknowledgement is not evidence of rollback.

The selected report set contains:

* `ImportEventLog.jsonl`: a receipt for every identified source patient/copy, its outcome, hold,
  source inventory completeness and per-category new/prior/not-committed occurrence counts.
* `ImportErrorLog.jsonl`: patient failures/refusals, pending source units, precise current-generation
  schema errors and normalization/review diagnostics with severity and source address.
* `units.jsonl`: every expected helper/native unit and its exact committed target receipt.
* `completion.json`: source/rules/options/manifest fingerprints and clinical/report/cleanup outcomes.

Each report generation is forced to durable private storage and hash-pinned before a fenced CAS
selects it. Downloads authorize the current account and verify the selected files. These reports
are protected clinical/admin evidence; ordinary logs contain safe codes and opaque run IDs only.
Web controls atomically audit the current security account, operator and provider alongside the
metadata transition; an admin controlling a CLI-owned run does not replace its original clinical
actor. Web source-field reads separately enforce live patient/category rights, holds and sensitive-lab
restrictions. CLI observations retain the verified ticket nonce, OS UID, provider and operation.

## Required physical verification

`CdsImportLedgerMariaDbIntegrationTest` runs only with
`-Dcds.ledger.test.properties=/path/to/protected-jdbc.properties`. It requires the explicitly named
disposable `cds_ledger_test` database with the actual complete Flyway schema. It tests real JPA/JDBC
rollback, strict-audit rollback, loss of the physical owning commit acknowledgement, fenced replay,
row-lock lifetime, exact prior-completion proof, source namespace preservation, file retirement,
report generation CAS, preview/import distinction, reviewed replan, authorization recovery and
resume/cancel races, zero-write revision CAS/audit rollback, stale canonical-plan refusal, and
atomic maintenance release-pending/deferred outcomes. The test guards its database name and uses synthetic data. A skipped test is
not a passing recovery tier.

The complementary maintenance integration tier verifies actual guarded datasources, multiple
nodes, permissive legacy SQL mode, Unicode overflow/rejection, session restoration, advisory
connection loss and target engine/trigger admission. Migration validation must start from the
actual baseline and include the complete common/province chain; changed unpublished migrations
require recreating only a disposable database, never repairing checksums.
