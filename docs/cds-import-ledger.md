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
Checked failures roll back. Required audits are not best-effort writes. Failure to acquire the
transaction before its callback is `UNIT_TRANSACTION_START_FAILED`, a known no-write outcome; it
is distinct from an unknown commit acknowledgement.

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
reports `SOURCE_REVISION_REVIEW_REQUIRED`. An identified conflict copy remains visible as
`NOT_STARTED`, with no target binding or clinical eligibility, so known-source counters and error
receipts do not hide it. The old accepted source and plan remain immutable.
The review archives the conflicting old/new fingerprints and paged prior-run provenance. Its
publication transaction locks the canonical lineage, compares the old fingerprints and review
version, and refuses any canonical target, checkpoint, hold, advanced unit or unresolved commit
uncertainty. Only that zero-write proof permits rebinding the canonical fingerprints; old plans
remain fenced from clinical writes. Operators never need to invent a namespace or edit database
rows to approve a legitimate unwritten replacement.

The protected operator summary supplies `unwrittenRevisionApprovalAllowed` to the browser.
This is a bounded metadata hint that a stopped, unsealed job can request that review: it requires
the exact unresolved preflight conflict and private evidence reference, no pending review or
inactive draft, and no target, hold, checkpoint, advanced unit or unresolved commit uncertainty.
Polling never reads source files or validates the entire historical lineage. Review acceptance
and publication still revalidate the current version and immutable evidence under ownership.

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
of the old plan and has a separate report-only route. An authorized clinical resume records the
indexed `resume_requested` projection and audited `RESUME_REQUESTED` event. An active recorded
state such as `RUNNING`, its cancellation and epoch stay unchanged while ownership is busy; the
request remains durable even if the in-memory queue is full. Neither that state nor a heartbeat
proves that a worker died. Only a successor holding both physical coordinator and maintenance
ownership may clear an older cancellation and advance its preparation epoch. Preparation retains
the indexed request and its original audited event. `RESUME_INTENT_APPLIED` consumes that request
atomically with entering `PLANNING`/`SEALING` or `RUNNING` and resetting report/cleanup to `PENDING`.
Both physical owners and the latest Cancel/review restrictions are rechecked under the run lock.

A process loss after preparation remains discoverable through the queued bit; after phase commit,
the active phase drives recovery. A configured worker restart requires neither a new Resume nor
a surviving local queue to rediscover this work. Old delivered report/cleanup acknowledgements
cannot certify the new attempt.

A later Cancel clears the pending projection and remains authoritative. Definitive authorization
or fingerprint refusal retires only the observed request through a versioned comparison; newer
controls survive. Automatic continuation after review never clears a newer cancellation. Retrying
a suspended review does not itself set the clinical resume bit, and execution rechecks pending
reviews and inactive drafts while holding the run lock.

A fatal review worker is suspended with durable `REVIEW_WORKER_FATAL` evidence. It remains a
pending review that blocks execution of the old plan, but automatic dispatch excludes it. An
explicit, audited Resume retries that review with its immutable request and fresh authorization;
published approval/reset evidence is preserved. Metadata unavailability keeps the worker suspended
until the healthy supervisor can record the failure. A fatal supervisor exposes
`IMPORT_SUPERVISOR_FATAL` and stops automatic work until the operator repairs resources and
restarts the service. Ordinary logs contain the safe code, without the fatal error's raw message.

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

Each active execution attempt has one monotonic elapsed budget using `cds.import.maxDurationMillis`
(the existing default is 24 hours). It spans snapshot verification, planning, preparation and all
clinical units rather than restarting at each unit. The budget is checked by existing cooperative
I/O controls and between atomic units. A committed unit remains committed if the next check expires;
the stopped outcome retains its source, receipts and holds for an authorized resume of the same run.
A resume starts a fresh bounded active attempt; time spent stopped or awaiting review is excluded.
No elapsed heartbeat authorizes takeover or changes an uncertain commit into a rollback.

Protected report generation has a separate fresh budget under the same policy. Its streaming,
validation, hashing and cleanup loops check that budget before a new immutable report set is
selected. Timeout records delivery failure without rewriting clinical outcomes, selecting an
incomplete report set or replaying clinical writes. A stopped `FAILED` delivery requires the
existing explicit recovery route, rather than an automatic retry loop. An already selected immutable
`DELIVERED` generation keeps its acknowledgement, pointer and hashes
when a later report attempt times out; failed cleanup makes the current operation non-clean until
explicit recovery selects a fresh complete generation. The historical timeout remains a nonblocking
operational warning in subsequent reports. The controls are cooperative: they cannot impose an
absolute deadline on a kernel-blocked filesystem open/read/write/fsync or
replace the independent bounded JDBC/network policies and process supervision.

Run state, per-patient outcome, clinical completeness, report delivery and cleanup are separate.
`COMPLETE` alone does not mean that the operator's files were delivered. `cleanComplete` requires
clinical completion (or an explicitly valid dry run), selected durable reports and completed cleanup.
A report-only retry preserves clinical commits and patient publications. Report/cleanup failure
never overwrites the primary import failure, and a failed delivery terminates polling with an
explicit failed operational state rather than hanging indefinitely.

Each immutable completion receipt projects delivery of its own selected report generation using
the fresh completion-time ledger state. A pending Resume observed in that snapshot keeps
`cleanComplete=false` and `operationTerminal=false`. Requests accepted after that snapshot belong
to later live status; an immutable prior receipt is historical evidence and is not rewritten.
Receipt predicates use the base ledger summary. The operator facade additionally suppresses
terminality for ordinary unresolved review work; consult its live protected status for current
operator action rather than inferring it from a stopped attempt's receipt.

Restart discovery includes `REVIEW_REQUIRED` and `RECONCILIATION_REQUIRED` when their report is
still `PENDING`. A pending review permits report delivery only for those stopped states; the
worker forces the report-only entry and leaves clinical state, seals and native units untouched.
An explicit clinical resume remains blocked while review is pending, including
`RESUME_REQUESTED`. A bounded run-row lookup inspects report state without aggregating a large
import during dispatch.

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


The `CDS import native contracts` workflow compiles the actual application and test classes, then
runs `scripts/ci/run-cds-native-tier.py`. The harness creates a uniquely owned internal-network
MariaDB server with checked tmpfs storage and bootstraps two empty schemas through the complete
common/Ontario Flyway chain. A separate workflow step runs eight fresh 64MiB parser probes.
The [required native-suite manifest](../scripts/ci/run-cds-native-tier.py) includes demographic and document ORM
callbacks alongside maintenance, coordinator binding prefetch, identity, clinical capacity,
appointments, history visibility, HRM admission, review acceptance, ledger recovery and target
receipts. Missing reports, zero tests, skips, inconsistent counters,
failures, errors and flaky reruns fail the gate. The host parser uses hash-pinned `defusedxml`;
only reports with captured configuration/output removed become CI artifacts. Cleanup removes
only the harness's own container, network and generated credentials. This physical gate does not
replace schema/parser, browser, installed CLI or application-wide regression checks.

### Physical schema contract review

The fresh ON and BC contracts add the same 17 tables: 16 for import provenance, source fields and
control, and one for typed laboratory results. Together they add 232 new columns, 138 index-definition rows, 59 key-column rows, 14 foreign-key rows and
one check constraint. No tables, columns, index definitions, keys or foreign keys are removed.
Database defaults remain unchanged. Both provinces have the following 45 existing-column changes,
each matched to an explicit `MODIFY COLUMN` in the listed forward migration:

| Existing table | Changed columns | Migration suffix after `V1.0.59.` |
| --- | --- | --- |
| `drugs` | 17 capacity/nullability changes; outside provider name (1) | `8`; `16` |
| `demographic` | first/last name, city/residential city, status, chart number (6) | `8` |
| `demographicArchive` | matching six archive capacities | `15` |
| `Contact` | combined name and two telephone capacities (3) | `11` |
| `allergies`, `preventionsExt` | description and source comments (2) | `8` |
| `measurements` | source measurement value capacity (1) | `11` |
| `hl7TextInfo` | first/last name (2); health identifier (1) | `10`; `14` |
| `appointment`, `appointmentArchive` | imported status and notes (4) | `10`; `15` |
| `casemgmt_note` | unknown observation date permits `NULL` and removes its zero-date default (1) | `21` |
| `preventions` | creator becomes an ASCII provider identifier, preserving alphanumeric attribution (1) | `22` |

Migration `V1.0.59.24` adds the nonclinical `cds_import_run.resume_requested` boolean with a
non-null false default. Three run indexes cover resume intent, active-state polling and pending
report polling; `(run_id,event_type,id)` on events covers the latest resume and newer cancel/applied
checks. Existing indexes and audit rows remain intact. Historical `RESUME_REQUESTED` events are
not backfilled into clinical authority: older review-only retries used that same generic event.
Older actual resume requests remain discoverable through their existing state/report projection.

Existing column character sets/collations remain unchanged apart from that explicit numeric-to-ASCII
creator conversion; no ordinal positions or generated expressions drift. The large default Git
diff reflects alignment of repeated JSON arrays. `git diff --diff-algorithm=histogram --
database/mysql/schema-contract/` gives a more compact view of the canonical generator output.

For an authorized schema update, `--refresh-schema-contracts` also builds a fresh BC reference and
runs the existing schema generator, its seed/collation validation, physical `--check`, and
`--verify-files` for both provinces. The optional `--schema-only` mode requires that flag and
explicitly reports that native tests were not run; the required CI gate uses the full test mode.

Before the durable-resume migration, the reviewed reference run passed all 47 native cases across nine required suites with zero
failures, errors or skips. Both ON fixtures applied and validated all 66 migrations (450 tables);
the BC reference applied and validated all 63 migrations (483 tables). Both generated contracts
passed physical re-export comparison and migration/policy binding checks. The six document ORM
cases compare exact timestamp milliseconds, so JDBC's `Timestamp` subclass does not make equal
stored clocks fail a Java object-type equality check. These results cover the tested compiled
candidate; whole-build, browser and installed CLI verification remain separate gates.

The separate `59.24` schema proof bootstrapped fresh Ontario (450 tables, 67 migrations) and BC
(483 tables, 64 migrations) references from the complete baseline. A second Flyway migrate/validate
applied zero migrations in each province. Both actual generated contracts passed physical
re-export comparison and migration/policy binding; the incremental shape is exactly one column
and ten index-definition rows per province, with unchanged database defaults. This schema proof
does not substitute for the combined candidate's required native recovery and installed-runtime gates.

Migration `V1.0.59.25` widens `cds_import_review.kind` from 24 to 32 characters so the
26-character `APPROVE_UNWRITTEN_REVISION` control can be durably accepted. It retains an
already larger capacity and preserves nullability, collation, default, comment and existing
values. Published migration bytes remain unchanged. Fresh references applied and validated
all 68 Ontario migrations (450 tables) and 65 BC migrations (483 tables), followed by a
zero-migration repeat. Both canonical contracts were generated from those actual databases
and independently re-exported and checked. The only physical column change is the intended
review-kind capacity; the migration/policy bindings were regenerated from the frozen source.
The eight actual service/ORM acceptance cases passed without failures or skips. Three separate
exact-SQL metadata replays preserved a quoted comment/custom collation/default, nullable
Unicode/NULL data, and an adopted `VARCHAR(64)` capacity; second replays changed nothing.
A separate owned Ontario catalog applied the complete 67-migration predecessor, inserted a
synthetic administrative run/diagnostic/review preservation fixture, then applied exactly
`59.25`, validated, and repeated with zero migrations. All 450 table row counts apart from
history, every CDS table's ordered row/control digest, all 67 prior migration checksums,
review payload/Unicode data and holds were unchanged. The column's other metadata was
unchanged. This is an actual forward-upgrade preservation proof, not an imported clinical
patient. Only the owned temporary catalog was dropped, with absence verified afterward.

A review-persistence error remains HTTP 503 with acceptance unconfirmed. A committed review
can exist after a lost acknowledgement: refresh the same durable run before another action.
Do not infer `NOT_ACCEPTED` or automatically resubmit. Bounded protected technical evidence
and a live nonblocking operational diagnostic can explain that request failure without
rewriting an already selected immutable report generation or changing its clinical flags.

An additional actual MariaDB query-plan probe used 5,006 owned synthetic run rows and 100,001
events. The three candidate queries visited respectively two, one and one matching index rows,
without traversing the 5,000 delivered historical runs. Latest-control checks covered no control,
an old resume followed by 50,000 unit events, a newer cancellation and a newer applied receipt.
They required two to four key reads and zero sequential event-row reads, using the covering
control index or the database's optimized MIN/MAX lookup. These are measured fixture results,
not a universal timing guarantee. All mock rows were removed and both owned reference schemas
were dropped with absence verified; retained acceptance databases were not modified.

Admission seeds for `global`, `clinical-maintenance` and `UPLOAD_BUDGET` use explicit
`INSERT ... SELECT ... WHERE NOT EXISTS` guards. The fresh native harness replays the exact
statements against a uniquely named scratch clone, verifies existing owner/reservation and
maintenance-generation values remain unchanged, and verifies unrelated storage errors remain
errors. It drops and checks absence of the scratch table before physical contract export.


Parser operational controls also run before and after each bounded transport read, sharing the existing 250ms durable-cancellation throttle. A suffix of individually short comments or processing instructions cannot defer cancellation, interruption or the elapsed deadline until EOF; incomplete parsing never publishes `parse.seal`. Processing-instruction target names share the bounded name-length/vocabulary accounting because the pinned Xerces scanner interns them in its document symbol table. PI content is never accumulated. The focused parser/control suite passes 24 tests; the additional fresh64MiB probe refuses a 111,491,543-byte distinct-target source with `CDS_XML_NAME_RESOURCE_LIMIT` and no seal. The prior four fresh64MiB payload/diagnostic/index/markup probes also pass; the integrated candidate must run all five together.

The final native ledger regression rediscovered both stopped report states from actual database
rows, routed them through default report-only execution and verified no native-unit or maintenance
work occurred. The receipt suite also checked every allowlisted physical key and actual admission
row mutation; the Unicode provider lookup executed against a real Latin1 directory with the
freshly compiled production DAO. These were included in the 47-case, zero-skip result.

### Bounded schema diagnostics and dynamic type state

A later constrained CI run reproduced heap exhaustion in the pinned Xerces validator's document-wide PSVI error contexts. The application already streamed its diagnostic JSONL, but Xerces also retained every formatted error until the ancestor elements closed. The importer now disables that duplicate augmentation and obtains type metadata from the same compiled, hash-pinned schema through `CdsSchemaTypeCursor`. Implicit `xsi:*` metadata uses the pinned validator's built-in schema-instance declarations. Full XSD validation and default emission remain with `ValidatorHandler`.

The cursor follows local/anonymous declarations in the parent's content model and namespace scopes. A recognized `xsi:type` guides streaming even when the validator rejects its derivation or surrounding element, preventing malformed numeric or binary input from selecting an unbounded validation path. Intrinsic string types retain their actual lexical checks. Dynamic XML identity types have independent aggregate token/character guards before validator retention; crossing a guard preserves source, reports an explicit resource failure and leaves the parse unsealed.

The separate performance class retains all five original 64 MiB modes and adds mixed diagnostics/large payloads, malformed dynamic types, and excessive ID/IDREF state. The diagnostic fixture keeps category order valid and checks every expected occurrence and both error rows for each of 120,000 invalid Severity values, including the final occurrence. Golden metadata comparisons use Xerces PSVI independently across six complete fixtures and dynamic/attribute contexts. Duplicate IDs, unresolved references, intrinsic lexical checks, fixed defaults and separate identity token/character limits remain covered. These focused checks supplement the complete native CI and deployed web/CLI release gates.
