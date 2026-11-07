# CDS export implementation contract

This document tracks the exporter being built beside the modern CDS importer.
The current change implements shared bounded wire validation and category read-domain
selection, export category/profile identities, live export authorization, conservative
occurrence reconciliation, source-coverage validation, standards-based logical filenames,
ReadMe/event-log composers, authenticated encrypted staging, bounded patient selection,
a durable metadata ledger, finished ZIP/ZIP64 byte validation, and recipient encryption.
Independent OpenSSL decryption and actual importer snapshot/parser tests exercise the
package and encryption layers. Browser MVC routes, a durable dispatcher, and a Java25
CLI entry point now share the application facade and ledger. Installed metadata/control
checks pass through real root delegation, Spring/JPA, Struts, login and CSRFGuard.
The CLI candidate passed 66 combined import/export/documentation cases without skips;
actual root/service-user checks verify separate import/export ticket directories,
service read access, forgery/modification denial and cleanup.
A complete clinical engine and verified artifact publication remain unfinished. The separate `carlos-ctl`
worktree includes its export command adapter and shared import/export process transport;
the adapter requires an installed, qualified Java engine before executing a clinical job.
Neither logical profile is advertised as supported for export until the complete
acceptance gates pass.

The complete delivery requires both the installed command-line workflow and an
asynchronous browser workflow. Both must call the same authorized application
service and use the same durable jobs, captured generations, mapping revision,
dispositions and publication checks. Passing component or metadata/control tests do not establish a complete clinical migration.

The reviewable draft checkpoint is recorded in [`cds-export-progress.json`](cds-export-progress.json). The preserved full integrated baseline passed 3,931 checks across 317 suites. The final revision inventory and preceding bounded source diff passed 166 package checks across 13 targeted suites, with no failures, errors or skips. Its exact WAR passed 78 installed assertions against the genuine seven-category import, including complete witnessed revision inventory, memory-limit refusal, unchanged patient-issue receipts and final-callback authorization. Earlier exact-WAR checkpoints remain recorded separately. A concrete clinical export engine, full source coverage, current clinical field/issue/revision reconciliation and end-to-end clinical CLI/browser qualification remain unfinished.

Retained enrolment reconciliation now coordinates original source history with witnessed current and archive rows. Exact packaged installed checks passed for both an ordinary import (45 checks) and a shifted training copy (46 checks), including schema-valid XML, a later native termination, and exact restoration of the owned patient and archive changes. The coordinated package check passed; other demographic and archive fields still require coverage.

Primary-physician reconciliation and its coordinated provider uses passed package verification and 127 exact installed checks across ordinary and shifted imports. Public provider lookups carry the authenticated capture identity; primary and enrolment reconciliation reject another generation and handle a SQL NULL reference without guessing a physician. Every use of a provider field is retained when the same physician fills multiple roles. Focused checks cover reassignment, removal, unresolved source details, explicit empty values and all-or-nothing review blockers.

External family/referring-physician reconciliation passed package verification. The importer stores these relationships in the demographic parent receipt; the exporter binds each original relationship by patient, role and contact reference, including when names are identical. Forty-seven focused checks and 44 exact packaged installed checks cover name edits, complete source/field accounting, XML validation and exact restoration. Missing, moved, inactive/deleted or contradictory relationships require review. Additional populated professional-contact attributes still need a supporting native projection before publication.
Preferred-pharmacy reconciliation and the coordinated demographic care assembler are packaged and installed component verified. The assembler combines personal contacts, family/referring physicians and preferred pharmacy with the demographic core, checks exact native/source field identities, and retains each use of shared physician names. Unchanged source address lines, address type, phone extensions and fax components survive import's flattened native projection. Explicit native edits replace only the corresponding source groups; city edits preserve unchanged street lines, and jurisdiction/postal changes are checked together. Conflicting source-only details, ambiguous preferences, shared personal/professional contact aliases and unsupported populated native attributes require review. All 104 focused checks pass. The exact packaged care run passed 51 checks, including real pharmacy name/city edits, receiving-schema XML validation, and full physical-row/timestamp restoration. Other demographic and archive fields remain deferred, so this is not whole-demographic or clinical-export qualification.


Current history occurrence assembly uses one authenticated source session for all seven
history sections. It binds the current note, extensions, physical note-to-issue links,
patient issues and referenced catalogue to the same frozen generation. The link receipt
uses the importer's existing `noteId_issueId` identity and actual ordered JDBC metadata.
Every retained field and every current native field receives an explicit disposition;
unchanged source dates keep their original precision, while an edited narrative replaces
only its corresponding standard field. Missing date hints, changed authors or clinical
issue flags, native-only notes and revision chains needing an audit carrier require
review. They cannot silently replay an old source occurrence. Session expiry, duplicate
or omitted categories, caught failures and final authorization revocation invalidate
completion. These additions are source-level work until the next packaged checkpoint
is recorded; they do not qualify a clinical export profile.

## Baseline and integration

The implementation worktree starts at `release/2026.08` commit
`b96e367945befe66eb333fe19aa1630aa9d10647`, which includes the modern importer.
The feature branch includes the forward integration of `develop` commit
`848ca460b2d5779a45950ae1451311f1c9409f33`. The 117 merge conflicts were resolved
and the integrated importer/exporter baseline was packaged and tested at
`2026.09.0-alpha1-SNAPSHOT`. The preserved package and installed component evidence
below distinguish this completed integration from the remaining clinical export
qualification under the [release process](release-process.md).

The original checkout and its work remain intact. The separate `carlos-ctl`
worktree starts at `c330ae97683757da4aeff18f70db665c561de1cf`. Its export adapter
is implemented alongside the Java entry point. The real protected Java25 capability probe has been exercised in an isolated installed container; clinical execution remains unqualified.

## Implemented foundation

| Component | Contract |
| --- | --- |
| `demographic.cds.contract.CdsCategory` | The 16 schema sections in wire order. Helper units and ReportsText/ReportsBinary are not extra schema categories. Unknown names are refused. |
| `demographic.cds.contract.CdsLogicalProfile` | Explicit `omd-5.2` and `pcon-2024-02` identities. Recognition does not establish direction-specific support or conformance. |
| `cdsexport.dto.CdsExportIdentityDto` | Stable patient/category/source-occurrence identity. Identical values in different occurrences remain distinct facts. |
| `cdsexport.validator.CdsExportReconciliationValidator` | Selects effective retained source only for verified source-only or unchanged projected occurrences; selects current data for native-only occurrences. Changed, deleted, contradictory or insufficient evidence requires review. |
| `cdsexport.validator.CdsExportCoverageValidator` | Compares independent, sorted source inventory with actual serialization dispositions. Missing, unexpected, blocked, duplicate and unordered occurrences cannot produce complete source coverage. |

The coverage validator retains two input rows and at most 16 issue previews.
Complete mismatch counts remain available when the previews are limited. Input
failure, cancellation, lost ownership and resource exhaustion propagate rather
than returning partial success. Callers own and close the input streams.

These classes are internal contracts. They provide no disclosure authorization,
storage verification, schema validation, package publication, or proof that an
upstream inventory/mapping is correct. In particular, a mapping digest must be
checked against the reviewed mapping registry by the eventual application
service; merely supplying a digest-shaped string is not approval evidence.

Provenance decisions, serialization dispositions and coverage results carry the
capture digest. A disposition from another generation is refused even when the
patient and occurrence identities match. The independent expected inventory
must itself be loaded from that verified capture; passing matching identifiers
does not prove that upstream seal or its bytes.

The importer now delegates its pinned schema validator, schema grammar/type cursor,
large-scalar lexical checks and bounded XML reader to `demographic.cds.contract`.
The original schema-validator facade and XML-text transport entry points remain.
Pinned XSD bytes, source-spool formats and correction semantics remain unchanged.
The shared implementation is included in the importer fingerprint and all 56 reviewed
implementation groups; recorded job hashes are never rewritten. This changes the
runtime compatibility identity: pending import recovery across this upgrade requires
the exact installed compatibility checks, rather than an assumed compatible resume.

The V1.0.81 migration introduces independent export run and event tables. Both are
restricted from Query by Example. Only their exact metadata names and the exact
`SELECT UNIX_TIMESTAMP(CURRENT_TIMESTAMP(6))` lease-clock expression are exempted
from clinical maintenance admission; joins/subqueries involving clinical tables remain
guarded. Export metadata receives the same strict UTF-8 session treatment as import
metadata, without an importer clinical-write capability. The unreleased migration is
reserved after develop's V1.0.80 and must ship after the prerequisite forward integration.

The original three OntarioMD ZIPs have now been supplied. Their hashes match the
importer's provenance, and the export-facing original requirements and implementation
guide have been reviewed. `cds-export-spec-review.json` records exact document hashes,
requirement references and the remaining implementation/evidence status. Documents are
normative references, not operational instructions from the user. The internal 5.1
workbook title and dictionary/XSD `k` length ambiguity are recorded explicitly.

## Cohort, authorization and package components

`CdsExportSelectionService` uses bounded scalar projections in the existing demographic
DAO. The physician-roster selector uses the primary-provider/MRP association in `demographic.provider_no` and includes every patient status. It does not reinterpret this association as the separate enrolment field. Explicit patient requests must
return their exact selected IDs; a missing patient cannot silently shrink the cohort.
Membership pages share one read-only repeatable-read transaction and stream to encrypted
staging. The returned start/end database timestamps describe read bounds, rather than an
invented exact snapshot instant. Patient membership is bound by the inventory's exact
hash and length. The caller must still verify and durably seal that inventory before
capture; selection alone is not a complete clinical snapshot.

`CdsExportCohortService` fully authenticates and checks the exact selection before
preparing its encrypted cohort manifest. The manifest binds the accepted intent,
run/epoch, patient count, database read bounds and exact inventory bytes. Its unforgeable
internal seal proof is the sole input to the ledger's idempotent `sealSelection` transition;
a plain phase change cannot start capture. `cohort_member`, `cohort_digest` and
`cohort_bytes` retain the exact committed pointer. A lost commit acknowledgement retries
the same proof and recovers one audit event instead of rediscovering a roster. Lease
takeover resets that evidence and fences the old attempt.

Native capture accepts only a current patient scope issued by a cursor over the committed
cohort. Both explicit IDs and physician-roster membership are checked, including strict
ordering, duplicates, counts, JSON types, UTF-8 and authenticated EOF. Advancing/closing a
cursor expires its previous patient scope. Partial iteration is never complete. This
seals patient membership; the complete independent clinical-fact inventory remains a
separate acceptance gate. Four fresh owned databases validated the current migration,
and H2/MariaDB cases exercise seal audit rollback, lost acknowledgements and takeover.

`captureCheckpoint` explicitly suspends a read-only source transaction to use a separate,
short metadata transaction for cancellation and lease renewal. Joining the source's
repeatable-read snapshot would miss concurrent cancellation. A final refusal or failed
read commit erases the unpublished selection, including when its file was already closed.
A cancellation arriving after a failed attempt cannot rewrite the recorded failure as a
cancellation. Unknown totals remain unknown until discovery is sealed.

`CdsExportAccessService` reconstructs current accounts, active providers, facility membership,
export administration, selected category reads, and patient restrictions in a fresh
read-committed transaction at each application boundary. Actual sensitive lab occurrences
also require the sensitive-lab right. This service is independent of import admission and
never substitutes chart write permission for clinical read/disclosure permission. The
export policy uses `cds.export.enabled` and `cds.export.operator.<operator>.providers` /
`.facilities`; new admission defaults off, while authorized metadata observation/control
can remain available. The migration registers `_cds_export`; current `_admin` authority
is an alternative. No role is granted export rights by this migration.

Browser and Unix identities have distinct durable operator keys (`web:<securityNo>` and
`unix:<verifiedLogin>`), even when a Unix login happens to be named `web-7`. Root-issued
exact-argument delegation and live policy remain necessary for CLI authority. An operator
key is only an identifier. Metadata ownership alone never authorizes a clinical download.
The shared application facade and execution control invoke these boundaries. Browser acceptance commits before queueing; database rediscovery owns recovery. A local interruption releases an attempt back to waiting without cancelling disclosure intent. Cancel/resume from a browser check its observed revision under the durable row lock.

The shared `CdsCategoryReadPermission` selects the same read domain for all 16 categories
in both engines. Import account/provider, patient and invalid-category behavior is
retained. The SQL admission guard also owns its JSQLParser executor explicitly: the
pinned library's convenience overload omits executor cleanup when parsing fails. Both
successful and refused SQL now retire that worker without changing classification or
executing a normalized statement.

`CdsExportPackageComposer` creates STORED ZIP/ZIP64 entries from exact captured encrypted
members, avoiding a receiving importer's compression-ratio refusal for highly repetitive
text. Central records are encrypted streams, rather than an in-memory member list.
`CdsExportPackageValidator` independently reads local records and payload hashes/CRCs,
streams encrypted local receipts, checks every central record and offset, and requires
exact ZIP/ZIP64 end records and authenticated staging EOF. Member inventories must reopen
identically. Logical paths use component ordering and reject case/Unicode collisions,
file-versus-directory conflicts, unsafe names, missing members, mutations and volume limits.
A 65,537-entry test crosses the real ZIP64 count boundary and verifies the finished
archive with both Java ZIP readers. Oversized single volumes refuse; patient-complete
volume planning remains unfinished.

Actual parsed XML evidence includes legal patient name, ID and calendar birth date.
`verifyFileBinding` compares that evidence to the exact DP01.11 filename without stripping
names or shifting dates through UTC. Physician folders still need independent captured
roster/provider evidence. Package byte agreement does not establish clinical completeness,
attachment-reference closure, supporting-file content, disclosure authority or READY.
There is deliberately no ledger API that can publish this intermediate byte proof.

## Installed integration evidence

The current capture implementation passed 2,214 Java checks without failures, errors or skips:
2,016 checks in the packaged lifecycle and 198 additional baseline checks against the same
source. The latter include upload and mutation-route suites whose display names had been
omitted from the reconstructed selector; all eight classes are now explicit in that inventory.
The current package passed 37 installed capture/permission checks across 21 tables and 370
fields, plus 12 installed selected-immunization capture, importer-receipt, closure and privacy
checks. Six real installed command-line process checks passed. These cover private source
capture and job metadata/control; a complete clinical export engine remains unfinished.

The packaged application was run on an owned private Docker/MariaDB network. Tomcat11
Jasper compiled the new JSP. Five Chromium checks passed through ordinary login, actual
Struts and CSRFGuard: waiting preserves unknown totals; GET mutation is rejected;
a tokenless POST changes no durable state; authorized AJAX cancellation is recorded; and
reload recovers the cancelled job without implying a completed clinical package. The
owned Tomcat process and loopback forwarder were stopped after proof so background
queries cannot contaminate the native coordinator's server-global query-count checks.
No production deployment, public port, clinical export qualification or certification is
implied by these checks.

`CdsExportNativeCaptureService` now captures an explicit installed table scope into three
protected members: exact concatenated source values, a separate JSON-lines field index,
and the final manifest binding the run/epoch/patient/sealed cohort/read bounds/table schemas/counts and
actual member hashes/lengths. Schema, count and row reads share one bounded read-only
repeatable-read patient transaction. Current authorization runs before and after capture
outside that source transaction; source-time lease/cancellation probes use independent
metadata transactions. A failed commit, cancellation or post-capture access revocation
erases every unpublished member, including closed files.

The first installed reader captures all 60 physical demographic columns as raw binary
scalars and strictly decodes UTF-8. It preserves SQL NULL, empty text, incomplete date
components, nullable flags and full names without model/UI defaults. Its metadata pin and
exact reviewed source-schema contract refuse added/missing/changed columns or a
nontransactional engine before payload capture. Independent Ontario/BC contracts and a
live MariaDB schema/row read verify that contract. Related contacts/pharmacy, the remaining
clinical mappings and the complete clinical source catalogue remain unfinished.
Twelve actual non-root CLI-context/protected-storage checks pass across eleven captured tables
and 102 fields, including missing/revoked allergy permission, captured allergy projection,
the durable cohort and values preserved after a subsequent database update. The
installed run exposed unsupported `ORDER BY BINARY` syntax in the maintenance SQL parser;
bounded Java sorting now preserves the exact metadata order, and the full guarded CLI
graph regression exercises this query. The separate finished-stream reader checks
table/column/ordered unique row identities,
counts, strict UTF-8, every byte offset/hash and both EOFs. Swallowed adapter/stream errors
leave capture and staging permanently failed. This verifies the declared transport scope;
it does not independently prove discovery or mapping of every clinical fact. The latest
focused current-source run passed 348 cases without failures or skips before the projection
service and synchronous push API additions. The later 307-unit run also covers those two
additions. XML composer cases verify sticky failure, stalled-source refusal, producer/mapper
lifetimes, thread and recursion boundaries; a caught write error cannot produce a complete
document. A final authenticated-read failure still invalidates a document after all rows
have been pushed. Occurrence receipts must remain private until whole-document verification.

Patient-owned readers capture `demographicExt`, `demographiccust`, complete `allergies`
rows and allergy-linked `partial_date` metadata.
The custom-row reader includes `cust3` only when Alerts and Special Needs is selected:
that column is an alert, and its bytes never enter a demographics-only query. The full
physical schema is checked in either case. Each owning entity DAO exposes bounded raw projections; no
entity graph or UI converter supplies source values. Header pages contain at most 32
rows with bounded inline values. Larger values stream in 64KiB SQL byte ranges under
the same patient snapshot, with an independently computed SQL length/SHA-256 checked
at EOF. Exact signed native IDs, including historical negative/zero IDs, have unique
ordered internal keys. Null, empty text, zero and full Unicode remain distinct. Runtime
schema contracts include complete primary keys as well as columns/engines, and both
regional schema inventories independently check the source plans.

The zero-row physical-table probes check all-column visibility before and after reading
the schema catalogue. An ordinary constant projection could succeed while column-only
database grants hid a future field from `information_schema`. Real empty-clone regressions
reproduce that omission with both ordinary and invisible fields, and prove the new probe
refuses the incomplete catalogue. The probes return no clinical values.

Allergy capture includes archived rows and retains nullable booleans, exact reaction text,
coding references and native dates before classification. Precision rows are selected through
the allergy's patient identity and native owner type; another patient's rows and other source
types cannot enter this capture. Duplicate field metadata and unknown/null formats remain
available for explicit review. Dates must be mapped together with that precision information;
the physical date's storage anchor is not evidence of a complete calendar date. Other native
owners of `partial_date`, drug lookup definitions and retained imported allergy fields
remain required work.

The native allergy mapper classifies all 24 physical columns plus linked precision rows.
It distinguishes nullable drug-property flags, maps only explicit code authorities, preserves
partial dates, and never substitutes a modification clock for recorded date. Native severe
does not prove life-threatening severity; that case requires source reconciliation. Unresolved
catalogue IDs and recording clinicians remain explicit review blockers. The bounded projection
service joins only the authenticated allergy/date generation, refuses orphan/extra precision
rows, and retains every field assessment. Its results establish native-row representability,
not complete clinical coverage or source-only reconciliation.

Typed allergy XML carries the versioned `CARLOS.Allergy.Archived.v1` boolean residual.
Age text and onset speed use `CARLOS.Allergy.AgeOfOnset.v1` and
`CARLOS.Allergy.OnsetOfReaction.v1` string residuals: exact native age text retains leading
zeroes without an inferred unit; onset labels are Unknown, Immediate, Gradual and Slow.
These individually named fields have no standard allergy counterpart. The importer recognizes
only these explicit names and types, applies effective corrections, and blocks duplicate or
invalid known fields. Other residual names retain their protected source-only behavior.
Omitted fields preserve existing importer defaults. The strict prepared-value decoder supplies
only omitted extension defaults after verifying the stored payload; legacy bytes/digests are
never rewritten. Native ORM, pinned XML/parser and actual CMS/OpenSSL receiver cases cover
these semantics in the combined verification.

Six further owning-DAO adapters capture selected retained import fields, units, dependencies,
preparations, patient records and originating run metadata in the same native snapshot.
Digest keys and UUID run keys stay exact. Category filters include the known field/contact/care
work units; another patient's rows and unselected category values are excluded. Unknown
category/work-unit names stop discovery instead of disappearing from the expected inventory.
Retained laboratory evidence requires sensitive-lab authority while its per-row classification
is unfinished. Fresh authorization checks deduplicate read domains and preserve the strongest
required privilege.

`CdsExportRetainedPayloadService` copies only the referenced original field ranges, not an
entire spool that may contain other categories or patients. It verifies the source range hash,
streams into encrypted owned values/index members, independently verifies their finished bytes,
and binds every field identity, null/inline marker and payload digest to the exact source/cohort
generation. Effective inline corrections stay separate from copied originals. Restore uses
the owned copies after an original file is deleted. Missing files, corrupt data, changed metadata,
resource exhaustion, cancellation or final access revocation cannot produce a successful receipt;
all newly created members are erased. Native-only/inline-only evidence requires no original
import-storage configuration, and import admission need not be enabled.

The bounded retained index reads related metadata tables in one authenticated pass. Equal
keys in different tables remain separate rows. It checks field/native-unit dependencies,
cycles, captured holds, publication states, receipt envelopes and prepared payload seals.
Refused or unstarted proposals are retained as metadata but do not become applied clinical
facts. Replays can refer to original preserved fields across runs; those fields are not
duplicated. Historical row digests remain assertions until actual row verification, and the
index does not establish clinical projection equivalence, resolve native aliases, or qualify
an export profile. Complete fieldwise reconciliation and reviewed legacy evidence remain work
to finish.

The appointment capture reads all 26 current-table columns across all booking statuses.
A separate lookup captures full status codes and every referenced definition, including
inactive and duplicate definitions; the first-character UI lookup is never used. The
provider DAO projects only public ID/name/OHIP fields, checks all physical provider
columns, and uses binary UTF-8 key paging for nonnumeric identifiers. Private provider
values never enter capture. Both lookup scopes follow selected patient/category references.

The native appointment mapper accounts for every row and used lookup field. It requires
exact captured identities and an unambiguous full-code status definition. It preserves
literal times and derives only a positive same-day whole-minute duration; overnight,
subminute and daylight-saving ambiguity require review. Imported status never substitutes
for a changed or cleared current status. Populated extra fields remain explicit blockers;
this category has no `ResidualInfo`. The writer's actual bytes pass the pinned XSD and
receiving parser checks. The separate owning archive DAO now captures every version, including repeated booking
IDs, SQL NULL/empty values, large text and historical-only status/provider definitions.
Current appointment projection requires that independent archive capture and refuses any
nonempty history until its representation is qualified; historical versions are not
counted as new bookings. Retained-field reconciliation also remains necessary before
complete appointment coverage can be claimed.

`CdsNativeRowDigest` shares the importer's existing protocol-2 calculation without changing
its receipt envelope or digest bytes. A frozen independent Python vector and actual-driver
compatibility checks protect this extraction. The driver's legacy `SELECT *` omits only
the reviewed invisible derived `demographicExt.dashboard_single_value_key`; raw capture
still includes all eight extension columns. Capture and restore verify that exact recipe
exception and refuse ordinary missing columns. All 60 base demographic columns remain
in their legacy receipt. Owning DAOs record ordered JDBC type,
precision and scale metadata inside a new capture; a partial or old capture retains an
unavailable recipe. Restore never substitutes current metadata. The bounded row scanner
can recompute these hashes from authenticated captured field lengths and hashes while
streaming large values without loading their contents into a row DTO.

`CdsExportRetainedReceiptVerificationService` compares every applied historical reference
with those captured-row digests. Duplicate references and aliases with different old hashes
remain separate. Changed rows, rows outside capture, missing adapters and missing metadata
require review. Generation mismatch, cancellation, resource limits or final authorization
failure cannot return a verification result. An unchanged reference set is still not a
qualified mapping, fieldwise reconciliation, alias resolution or clinical publication.

Actual MariaDB cases cross a 32-row page, retain full large text across a concurrent
update, and verify SQL hashes. The installed non-root proof now traverses a durable
cohort and 15 native/reference/retained tables and 141 fields (before the subsequent six-field public provider refinement) for a demographics/allergies request under the real SQL guard. The finished-capture
reader can deliver bounded field bytes to mapper-owned sinks and acknowledges a field
only after strict UTF-8 and hash verification. Sink errors propagate; a field callback
is not whole-patient or clinical coverage proof.

The base demographic mapper now consumes only a fully authenticated captured generation,
including complete transport EOF and current authorization checks. It classifies all 59
non-key base columns and keeps SQL null distinct from empty text. Typed output covers
legal names, exact birth date, health card, mailing/residential addresses, home/work phones,
languages, email, patient status/date, chart number and SIN. Its writer follows the pinned
wire order and its tests parse the finished XML and run the real schema validator.
Known code translations and the Canadian postal display separator are recorded as named
rules; source values remain in the protected capture. An explicit US subdivision selects the
`ZipCode` choice. Import and export now share the exact 71-value pinned health-card authority
vocabulary; explicit unknown-authority wire codes never become known identity authorities.
Missing/partial required birth dates,
unknown administrative gender, token normalization, excess lengths and populated unsupported
fields prevent serialization. There is no truncation, guessed date or fallback note dump.
This establishes representability of that base row only. Captured primary physician mapping now shares the public provider mapper with appointments: exact provider references, distinct OHIP/CPSO identifiers and schema order are checked without an operator fallback. Contacts,
enrolment history, notes, extensions, pharmacy and imported-source reconciliation still
need their own complete mappings and independent clinical-fact coverage. In particular,
the pinned Demographics schema has no `ResidualInfo`; extra demographic fields cannot
be moved into another category in violation of the implementation guide's semantic rule.

The shared schema metadata now retains nested compositor order and occurrence bounds.
`CdsExportRetainedSectionWriter` reconstructs a bounded section from retained field paths,
orders each selected choice branch, retains repeated occurrences and empty complex nodes,
and streams effective text or binary values. It refuses duplicate/gapped/conflicting paths,
unknown namespaces/types and unresolved structural attributes. The real receiving parser
compares every scalar/attribute in all four pinned choice fixtures; semantically conflicting
report fixtures must still fail the stricter finished-export validator. Structural round-trip
coverage does not establish that an occurrence reflects the current clinical chart.

`CdsExportRetainedValuesService` reads a completely checked retained index and frozen payload
manifest, binds requested DTOs to their captured identities, and provides a scoped effective-value
capability. An explicit empty logical correction remains empty. Original import files are never
reopened. Large values use one pinned encrypted file descriptor. Each requested range decrypts
only overlapping 64KiB chunks and verifies its own exact hash. The complete enclosing file,
including its authenticated terminator, whole-file hash and EOF, is checked before and after
the callback. A final live authority check follows that last full verification. This removes
one complete file scan per field while retaining bounded memory and detecting corruption in
unused original values. Caught read failures, malformed UTF-8, abandoned fields, cancellation
and scope/thread violations still invalidate the complete result.

`CdsExportDemographicArchiveSourceReader` now reads all 58 physical historical demographic
columns through the owning archive DAO, with bounded keyset pages and chunk access in the
same read-only patient snapshot. The ON and BC physical contracts agree. Every archived row
is retained, including repeated enrollment states, nullable values and signed BIGINT keys.
Current and historical primary/enrollment provider references contribute only public clinician
identity fields to the frozen lookup. Archive timestamps are not invented enrollment event
dates, and snapshots are not automatically deduplicated or promoted into standard enrollment
history. That semantic reconciliation and whole-demographic coverage remain unfinished.

`projectWithPatientNote` combines the base demographic and native patient note from the same
protected source manifest. Its writer inserts `NoteAboutPatient` in the standard schema order.
Only the complete outer legacy `unotes` frame is removed; internal closing tags, entity-looking
text, whitespace and unmatched literal tags are preserved. A complete embedded frame with
additional prefix/suffix content requires review. Nurse, resident and midwife assignments
remain separate unresolved fields; selected alerts are explicitly delegated to their category.
The note reader buffers at most one wire-sized value. Larger values stream to discard and
produce a review decision only after the complete encrypted capture and live authority check
succeed. The guide's 64,000-character limit is enforced before serialization; no note is
shortened. This projection still requires retained-source reconciliation, historical/related
source coverage and the remaining demographic writers before it can establish completeness.

The demographics-only `demographiccust` capture now retains actual driver metadata for its
five selected columns. Its receipt is explicitly partial, with a separate partial-row count.
An original full-row baseline can qualify comparisons of those five fields; `cust3` remains
omitted and unverified. Missing legacy baselines and changed column definitions remain
unqualified. A partial comparison always leaves the import unit in the review set and cannot
be promoted to a whole-row match, even when every selected field is unchanged. Original
import receipt hashes and baseline documents are unchanged. The installed non-root proof
compares actual MariaDB metadata and confirms that an alert edit invalidates the original
whole import receipt while preserving the frozen selected-field evidence.

All typed writers run their validation and output inside the same document failure guard.
A caller that catches a rejected demographic, allergy, medication, appointment, provider or
immunization value cannot subsequently obtain a successful composition or emission receipt.
The standard date-choice writer is shared across allergy, medication and immunization output;
each destination explicitly permits or refuses clock precision.

The sealed preparation reader and typed serializer now cover historical immunization events.
They preserve the prepared post-option date (including partial dates, offsets and fractions),
required refusal state and distinct standard text fields. The writer refuses invalid DIN/code
pairs and overlength values; the pinned schema refuses unrecognized CT-020 types. Native
per-program grants and current-chart reconciliation are still required before
this category can be enabled; exact classification and frozen capture are described below.
These serializers do not establish clinical completeness or qualify an export profile.

The additive V1.0.82 migration adds nullable field-witness documents to import units and
checkpoints. New native receipt capture records SQL nullness, byte length, SHA-256 and
actual ordered JDBC metadata in the same original unit transaction. The protocol-2 receipt
JSON and its digest remain unchanged. Original metadata hashes were verified across the
upgrade on all four owned ON/BC test databases, with every historical witness still NULL.
Pristine independent ON/BC reference databases validate the complete migration chain and
canonical schema contracts. The witness document is bounded to 8MiB and carries no native
plaintext values. Its exact referenced-row set and reconstructed row digests must match
the original receipt. Commit, checkpoint reuse and field comparisons passed the
combined database/rollback/replay verification; neither export profile is qualified.

`CdsExportNativeRowReceiptReader` now supplies independently authenticated column witnesses
from the frozen capture. The retained verifier can report the mechanically changed columns
when an original witness exists. Legacy absence stays explicit. New version-two baselines
add codec-bound hashes of server-binary IEEE values without changing the original receipt.
The prescription DAO captures exact stored dose bits in its encrypted native index, alongside
the original SQL display and its unchanged byte receipt. The driver capability retains the
physical admission barrier, bound parameters, query deadline and caller transaction; an
unsupported binary protocol refuses instead of falling back to text. Version-one baselines
remain readable and receive no invented exact witness. Equal SQL display text for a legacy
non-null FLOAT/DOUBLE/REAL, or session-dependent TIMESTAMP, still requires review. Replay
comparisons use each complete baseline, because equal old SQL digests can conceal different
float values. A field difference does not choose the clinical value;
category reconciliation, external lookup changes, import-option transformations and reviewed
resolutions remain separate required work.

## Protected storage configuration

`cds.export.store.active` selects a stable root ID, resolved through
`cds.export.store.<id>.root`. Each root and run/epoch directory must be private, service
owned and free of symlink or writable-ancestor substitution. `cds.export.masterKeyRoot`
contains `<keyId>.key`; `cds.export.masterKey.active` chooses the active immutable key ID.
Keys contain exactly 32 raw random bytes and must be root owned and read-only to the
service (for example root:carlos 0640 below a root-owned 0750 directory). Writable
service-owned secrets are refused. Key bytes and filesystem paths never enter a job
response or accepted document. Bounds are `cds.export.maxStagedFileBytes` (default 128GiB,
maximum 1TiB) and `cds.export.minimumFreeBytes` (default 512MiB, maximum 1TiB).

Version-two accepted documents bind the root/key IDs and the key SHA-256. Exact legacy
version-one JSON bytes remain compatible for metadata recovery; a version-one job cannot
create a new storage attempt. A claim creates an exclusive run/epoch directory only after
ownership validation. Accepted jobs retain their old root/key binding when active policy
rotates, and changed key bytes refuse access. Keep retained roots and immutable key IDs
configured until their artifacts are retired through the eventual retention protocol.
The context can start for metadata-only observation without loading any master key.
Eight installed non-root storage/binding checks and six actual POSIX permission checks
passed. Storage evidence alone cannot publish an artifact.

## Medication capture and historical prepared meanings

The prescription source adapter reads every one of the 64 reviewed `drugs` columns through
`DrugDao`, including archived, historical and hidden rows. ON/BC physical contracts agree.
The shared partial-date reader binds its predicate to selected allergy and/or medication
parents; colliding owner IDs, other patients and unselected domains do not enter capture.
The native regional-identifier column has no authority discriminator. Even eight digits
require a verified Health Canada DIN catalogue or retained preparation binding; the mapper
cannot relabel an unverified identifier as DrugIdentificationNumber.
Public prescriber candidates use the same snapshot and six public provider fields, including
inactive referenced providers. Exact binary identifiers are required at mapping time; a local
provider number, billing number or exporting actor cannot substitute for source authorship.

Native medication mapping assesses every captured column, keeps duration, refill duration,
quantity, refill quantity, dose, strength, instructions and provider notes separate, and checks
nullable flags and currentness independently. The writer follows the pinned schema sequence
and indicator choices; generated values passed the real receiving parser and clinical
normalizer. Exact server-binary dose bounds can supply a round-trip decimal representation;
contradictory dose text, signed zero and the undocumented all-zero default require review.
SQL floating-dose displays without those witnesses, unstructured strengths, unresolved script or
pharmacy links, archive history, unknown flags and contradictory prescriber hints remain
explicit review cases. These are unfinished mapping/reconciliation cases, not a qualification
of a complete medication export.

`CdsExportRetainedPreparationReader` verifies historical unit/category/type/source/options/
ruleset ownership and the exact original preparation seal before decoding. It reads the
captured index, not current import staging or current import options. Shared transient allergy
defaults retain old omitted optional properties without rewriting any stored payload/seal.
The temporal converter proves original lexical meaning plus its recorded synthetic shift;
it preserves calendar precision, offsets, fractional seconds and legal end-of-day spelling.
It rejects inconsistent anchors and cannot truncate a clock into a date-only destination.
Historical preparation conversion is not a current-chart selection decision.

Retained XML assembly now supports bounded, explicitly accounted subtree changes. Every
prior field ID under a changed/deleted subtree must be supplied exactly; untouched source
fields, attributes and vendor residuals remain in the original occurrence. Additions use
original source coordinates, independent of deletion/list order. Nested replacement callbacks
cannot close their parent, and all replacements retain the live authenticated-value scope
even when no original scalar stream is opened.

The allergy retained assembler combines those changes with qualified fieldwise decisions. It
requires unchanged published field pages, checks descriptors against captured metadata, and
compares known marker meanings with the sealed historical preparation before updating them.
Unrecognized vendor data remains streamed intact. Clinical occurrences, aliases, audit/history,
other categories and final publication still need the separate complete delivery gates.

Medication reconciliation now checks the sealed historical preparation against each native
field group and its own exact baseline. A changed provider note or quantity can use its current
value while preserving a proven DIN, structured strength, original prescriber, prescription
identifiers and clock precision. Native clears remain cleared. Unprojected standard ProblemCode
and unknown vendor data retain their source fields through the separate retained assembler.
Source field pages must be applied and unchanged before those values are reused. The native
parent link is keyed by each field ID; the shared occurrence-field resolver derives the
separate preservation-unit address and checks that field's own receipt. The native
dose row carries its authenticated exact numeric evidence into mapping; a baseline from an older
SQL display cannot acquire historical float bits from today's row. Changed dose/SIG dependencies,
ambiguous strengths, missing/aliased occurrences and changed precision still require review.
An actual importer-generated Drug persisted through JPA and MariaDB exercises the projection
check, in addition to finished-XSD and receiving-parser tests. This domain utility is not yet
an orchestrated clinical engine or a complete review/delivery workflow.

`CdsExportClinicalOccurrenceService` connects the authenticated retained-value scope, real
field-ID ownership, receipt verification, frozen rows/lookups, reconciliation and retained
assembly for allergy and medication occurrences. It emits each native row once, accounts
for every retained field in those occurrences, and refuses missing rows, aliases, orphan
precision, unknown mappings and mismatched generations. The CLI registers the same service.
Its output is private intermediate XML. The source guard on the XML section emitter keeps
final authorization, authenticated EOF and receipt failures sticky even when a caller catches
them. Other domains, independent whole-chart field coverage, review and delivery publication
remain required before this becomes a qualified clinical engine.

`demographic_merged` is captured through the existing owning DAO, including incoming,
outgoing and deleted links. CARLOS merges keep clinical rows under their original patient
owners; an exact-root query does not include all linked chart facts. The occurrence service
therefore verifies the frozen merge scope before reading clinical values. Active, null or
unknown deletion flags require explicit review; deleted history remains captured. Missing
scope, foreign links, malformed ownership, failed authentication and late authority failures
refuse output. A caught review cannot produce a complete document. Full linked-patient
closure, authorization and original-owner reconciliation remain unfinished; this safeguard
does not qualify merged-chart exports.

The retained-preparation reader also verifies demographic preparations against their original
source, options, ruleset, payload type and seal. It decodes the historical post-option values
without applying current options or restoring an original unshifted date. This is evidence
for demographic reconciliation, not a completed demographic section or enrollment history.

The public CVC immunization and legacy-name catalogues now have bounded owning-DAO readers,
full physical schema checks and frozen driver metadata. Existing native finder methods are
unchanged and covered beside the new readers. These global vocabulary rows contain no patient
occurrences. Their capture requires a current patient scope and the immunization read domain.
The exact configured prevention XML is copied into a source-bound encrypted member and read
back with the fully authenticated captured CVC rows. Recovery does not reopen current settings
or vocabulary. Unknown, conflicting or unsupported types require review. Injection layouts
alone do not prove vaccination: Mantoux tuberculosis testing stays screening, and ambiguous
Other injections require review. Explicit vaccine configuration or exact CVC evidence must
prove the category; conflicting declarations remain review cases. This
classification does not establish code equivalence, authorize a program-restricted fact or
complete native immunization capture/reconciliation.

The explicit `preventions_scope` alias captures only six classification/restriction metadata
fields through the prevention DAO; its full 16-column physical schema is still checked. Dates,
provider descriptions and other clinical values stay outside this projection. Every native
authorization boundary now runs a reader's additional scope checks after ordinary patient and
category rights, outside the captured transaction. Prevention restriction checks use fresh
read-committed transactions, so an older snapshot cannot keep a newly restricted record readable.
An active, unknown or ambiguous program restriction requires review before capture or frozen
readback; no program grant is inferred. A legacy null flag with no program records no additional
restriction, and explicit false keeps its meaning. Program-specific authorization remains required
before restricted facts can be supported. Classification partitions every frozen metadata row
into vaccinations, other preventions or explicit review, with generation/count/hash checks and
final live authority. This is not a native immunization occurrence writer or whole-chart proof.

The source schema guard now binds a declared source name to its exact physical schema. The
reviewed metadata alias is explicit; an ordinary reader cannot claim another table's schema.

Selected immunization capture now reads every physical prevention, extension and precision
column through its owning DAO. Selection uses the exact trusted configuration bytes, captured
CVC definitions and patient metadata in the same read-only repeatable-read transaction. The
configuration is an explicit binary source with no claimed SQL table or native receipt; UTF-16
configuration stays byte-for-byte intact. Unknown categories fail selection, and dependencies
cannot be omitted to bypass it. The completed encrypted capture is independently classified
again: every selected parent must match its frozen metadata, children must retain their original
selected owner, and date aliases must be disjoint. Large extension values stream while NULL,
empty values, duplicate keys and archived rows remain distinct. Recovery uses the embedded
configuration and never falls back to current settings. Actual installed checks compare frozen
row evidence with the importer, exercise live edits/revocations, and verify failed-attempt cleanup.
This establishes capture and dependency closure; retained-field reconciliation and complete
immunization occurrence integration remain separate unfinished gates.

The native immunization mapper and projection service now retain a decision for every parent,
extension and precision field. They use captured definitions, exact current text and date
precision, and require explicit DIN authority. Ineligible/external statuses, duplicate keys,
conflicting aliases, unsupported archive/provider/clinical fields and stale imported date hints
remain review cases until their reconciliation or carriers are complete. They cannot be collapsed
into a refusal boolean or silently dropped. The writer independently checks the pinned CT-020
enumeration and the dictionary's 32,000-character note boundary. Source generations, bounded
child indexes, counts, authenticated EOF and late live authority guard the projection. The
86-case isolated suite and 15 installed checks passed before integration. The integrated
package passed all 2,260 combined Java checks and 15 installed checks against the packaged
classes. These services remain private intermediates.

Retained immunization reconciliation now checks the original sealed preparation against the
actual parent, extension and precision witnesses field by field. Changed or cleared notes,
names, refusal and supported optional fields use the current recorded value; unchanged fields
keep their proved prepared meanings. A newly recorded DIN cannot disappear behind an absent
old code. Renamed/duplicate keys, aliases, deletions, changed precision, mismatched generations
and missing field baselines remain explicit review cases. Historical offset events require
original zone/projection evidence, and gap-adjusted anchors cannot be guessed from today's
settings. Imported date hints remain provenance rather than restoring an unshifted date.
The retained assembler accounts for all source field IDs and preserves untouched vendor data.
The existing allergy, medication and common retained writers now share source occurrence path
handling, including the second patient record in an originally imported file; verified patient
ownership and field-page receipts remain mandatory. Shared captured-row/receipt readers also
check current authority after their final private callback. The 152-case prototype suite and
18 installed checks passed before integration. The installed reconciliation fixture uses real
JDBC baselines and encrypted captures with an explicitly simulated historical metadata index;
it does not establish a completed import/export round trip. The integrated package passed all
2,320 selected combined Java checks, 18 installed component checks, six installed CLI process
checks and five browser checks. Immunization occurrence-service integration remains unfinished.

The common clinical occurrence service now routes vaccines through the same private XML,
retained-values and field-receipt pipeline as allergies and medications. Its explicit embedded
configuration path never opens today's settings or creates a duplicate configuration member.
It verifies the vaccine partition, exact parent/child witnesses, generation, bounded indexes,
counts, all retained source field IDs and current authority after the final output callback.
Active merges, missing/deleted/duplicate parents, orphaned children, unmapped current values
and unaccounted source fields cannot become a completed document, including when a caller
catches the failure. The isolated 129-case suite and 23 actual installed checks passed before
integration. The native-only installed proof uses the full metadata/retained-values pipeline
and actual receiving parser without mocked services; the separate historical reconciliation
index remains explicitly simulated. The expanded coordinated package passed all 2,347
selected combined Java tests with zero failures, errors or skips.
No clinical profile or publication is enabled by this component integration.

Occurrence assembly now independently compares the complete native row field identities and
NULL/empty/value states with its mapping assessments before emitting XML. Equal field counts
cannot hide a missing field behind an invented column. The helper checks only supplied captured
rows; whole-chart inventory and semantic/output validation remain separate gates. Its 49-case
isolated suite includes intentional same-count field substitution inside allergy and vaccine
assembly, both refused before emission.

Strict export capacities follow the migration dictionary and Guide section 1.10 decimal units:
1k is 1,000 and 32k is 32,000. Appointment notes, medication duration/quantity text, medication
notes/instructions and strength now use those bounds. Allergy and medication retained assembly
call the same validation gate as direct serialization, preventing original imported values from
bypassing dictionary limits. Oversize values require review; no truncation or importer limit
change is made. The 54-case isolated suite passed, including direct/retained validation and a
caught-failure document check. The expanded combined package passed all 2,368 selected Java
tests with zero failures, errors or skips. Installed clinical-path verification remains pending.

Appointment occurrence assembly now uses the common private XML, native witness, retained-value
and field-receipt pipeline. The explicit clinic setting `cds.import.clinicalZone` is shared with
import and captured as trusted configuration, together with the installed timezone rules
version. Recovery reads that encrypted capture and rejects missing old metadata, changed
rules, mismatched generations or an explicit projection zone that differs from the capture.
It never substitutes current settings or the JVM default. The XML clock carries its proved
offset, including winter/summer differences; historical second offsets and out-of-range wire
offsets require review. An actual importer preparation test preserves the instant when a
Toronto appointment is received by a Vancouver clinic.

Current edits and clears take effect; unchanged recorded provider and full source-view purpose
values require separately verified source-field pages. Post-option dates are not shifted again.
Native patient-display names require an exact captured same-patient join. Storage audit fields
receive explicit assessments. Unknown clinical extras, program affiliations, source-only
calendar records, aliases, deletions, merged patients and unrepresented appointment history
remain review gates. All original field IDs are accounted for and the last callback cannot
bypass live authority. The 126-case isolated suite and 14 installed checks passed before
integration. The native-only installed path uses real services and captured metadata; historical
reconciliation metadata is explicitly simulated. Expanded coordinated verification is pending.
These are private component gates; the full clinical engine and publication remain unfinished.

The importer review now includes the existing CVC mapping dependencies explicitly. Additional
read methods do not change its native finder bodies or rewrite persisted preparation/receipt
bytes. The participating DAO/helper byte changes do change the runtime fingerprint; existing
pending jobs retain their recorded compatibility identity and must satisfy normal recovery
checks.

## Value provenance and current chart state

For each in-scope imported occurrence, capture original source evidence, accepted
effective logical value, its native projection baseline and the current native
projection. Retain mapping and capture identities with the work. Corrected large
values can have an original spool reference and a different inline effective
value; `writeOriginal()` is not an effective-value export API.

`NATIVE` and paged `FIELDS` units have different keys and are linked through
dependencies. A field's unit key must not be treated as its native unit key.
Whole-row receipts can prove that recorded projections changed, but do not prove
which field should take precedence. A digest mismatch cannot automatically
choose native, original, normalized, or concatenated content.
A matching physical-row digest also does not establish complete clinical equivalence:
external physician/catalogue definitions and one-to-many projections need their own witnesses.
Retained XML logical text precedes import-option transformations. Synthetic date shifts and
identity substitutions must follow the verified preparation/option evidence, rather than
restoring an unshifted date or original identity from retained XML. These are reconciliation
gates, not permissions supplied by the generic retained serializer.

The allergy field reconciler now qualifies individual standard fields against the sealed
historical preparation and exact native baseline. It preserves a coding authority or
life-threatening source severity only when the corresponding original projection is proved;
a changed reaction can use the current native value without erasing those unchanged meanings.
An explicitly cleared reaction stays cleared. Matching complete legacy receipts can qualify
an unchanged projection transiently; no old field witness is backfilled or persisted. Changed
legacy rows, deletion, aliases, changed precision dependencies and unresolved original clocks
require review. Independent source-extension/occurrence coverage remains mandatory.

The general occurrence validator deliberately blocks changed or missing projections.
Resolving those cases is unfinished delivery work, not a permanent blanket
refusal that can be called a complete exporter. Implement category-specific
reconciliation and evidence-backed migration/backfill for existing charts, with
an authorized review workflow where older evidence is ambiguous. Support
replacements, deletions, merges, renewals, new native additions and one-to-many
or many-to-one projections without duplication or resurrection.

Unknown or partial clinical values allowed by the profile remain valid values.
They must not be confused with missing evidence or turned into zero, false,
an exact date, import time, or a local clinical author.

## Snapshot and asynchronous execution contracts

Durable acceptance records an immutable request and returns a run identifier.
Expensive planning/capture/rendering occurs outside the HTTP request. Explicit
patient IDs are fixed at acceptance; provider-based selection needs a documented
selection instant and a sealed inventory before capture. Totals remain unknown
until discovery completes.

The snapshot reader must use bounded projections participating in its chosen
database snapshot. The clinical source-view reader rejects ambient transactions
and performs separate read-committed reads; wrapping it in a repeatable-read
transaction is not a valid snapshot implementation.

Bind native data, retained source, accepted corrections, associations and exact
attachment/render inputs to a captured generation. A database snapshot alone
does not freeze files or another browser's eForm rendering. Copy and hash the
actual bytes into owned immutable staging and verify their captured versions.
Oversized or changed patients must restart from a new consistent generation or
refuse visibly; never resume half a patient from a different snapshot.

Use durable database rediscovery, bounded worker scheduling, ownership fencing,
idempotent acceptance and explicit startup/shutdown ordering. Tab closure and
logout do not erase accepted work. Current rights remain authoritative; saved
actor identity is not a saved permission grant. Status, cancel, resume, history,
reports and protected download must survive restart.

Foreground CLI execution uses the same engine with both background dispatchers
disabled in its dedicated Spring context. Detached submission must report
waiting if no compatible persistent worker is available. Stopping a foreground
wait is distinct from cancelling the job. The same submission identity recovers
the original run after a lost acknowledgment.

## Native enrolment history integration

The demographic enrolment projector now reads current and historical roster fields,
public physician identities and patient notes from the same authenticated source capture.
Identical snapshots share one event while every source roster field retains its own
assessment. Conflicting termination versions, reopened episodes, missing conditional
dates, unknown codes, a cleared current state and stale effective dates require review.
The writer includes the patient note and emits the guide's required dates in schema order.
Field counts cannot substitute for complete row identities or erase mapped events.

The isolated implementation passed 65 focused checks and 12 installed non-root checks
with actual MariaDB rows, encrypted storage, receiving-parser validation, frozen/current
edit comparisons and final SQL revocation. This installed native-only fixture uses real
empty import-metadata tables; historical metadata is not simulated. The combined package after integration passed all 2,458 tests. A further ambiguity
guard passed 66 focused checks: changing a date or physician cannot manufacture
another enrolment while the prior episode remains unclosed. The updated combined package passed all 2,459 tests and 13 installed packaged
checks, including the actual SQL ambiguity case. Applied imported demographics still require
retained field reconciliation, and other demographic related data, native archive fields,
whole-chart inventory, the clinical engine, review workflow and publication remain open.

The preceding appointment/frozen-zone package passed 2,422 combined tests and installed
37 native capture, 14 appointment and 23 vaccine checks. The appointment and vaccine
historical reconciliation fixtures explicitly simulate old metadata. Neither profile is
qualified. The exact artifact identity is in the task's `cds-appointment-zone-package-proof.json`.

## Native demographic phone integration

Home and work extensions (`hPhoneExt`, `wPhoneExt`) and the mobile number (`demo_cell`)
now join the base demographic values from the same frozen capture. Exact NULL and empty
extension values remain distinct. Hidden history, duplicate keys, orphaned nonempty
extensions and unknown populated properties require review; no latest-row heuristic,
phone concatenation or truncation supplies a replacement. The typed phone DTO enforces
the dictionary's 25-character number and five-character extension limits before writing.
All eight native property columns receive independently checked field dispositions.

Native enrolment assembly now requires the property table and includes these phones
alongside the patient note and enrolment history. A later primary-physician join preserves
the independent phone values and evidence. The implementation passed 85 focused checks
and 17 installed checks using twelve actual source tables, real empty import metadata,
protected storage, receiver parsing, frozen/current edits and final SQL revocation.
There is no historical metadata simulation in that installed fixture. The expanded combined package passed all 2,482 tests and 17 installed packaged
checks. The pinned crosswalk is unchanged and all 25 crosswalk checks pass. Imported demographic reconciliation,
other associated data, whole-source coverage and clinical publication remain unfinished.

## Remaining implementation and acceptance gates

| Gate | Required evidence before release |
| --- | --- |
| Normative export review | Original ZIPs and document hashes are verified and export requirements reviewed. Bind every completed mapper, log and package claim to those requirements and executed evidence; resolve recorded dictionary/XSD length ambiguities without truncation. |
| Source-domain inventory | Inventory every native and retained-source domain, including all 16 sections, contacts/pharmacies, typed labs, source-only fields, historical documents, eForms, encounter forms and consultations. The existing 407-path wire inventory is not an exhaustive CARLOS source inventory. |
| Export mapping | Source-to-wire mappings, cardinality/choice rules, precision, codes, restrictions, authorship, provenance and supported exclusions. No truncation, invented defaults or generic Notes fallback. |
| Identity and replay | Stable sender namespace and patient IDs within wire limits; defined merge/restore/copy behavior; separate identical-artifact replay, regenerated equivalent delivery and changed delivery tests. Automatic same-instance update synchronization is not assumed. |
| Rendering | Version-bound inputs, completeness-aware rendering, historical-form scope and explicit review for incomplete results; preserve approval scope and expiry. |
| Job persistence and MVC | Independent run/event entities, DAOs, live authorization, cohort selection and short audited ledger transactions are implemented. Finish patient/source/artifact inventories, the application/worker services, prototype Struts actions, per-JSP assembler/immutable view model, safe responses and browser stale-response fencing. |
| Joint lifecycle | Explicit import-admission/maintenance/holds/startup/disable/recovery matrix. No wildcard route exemption or importer-ownership bypass. Released charts remain exportable when import admission is disabled. |
| Packaging and publication | Finished ZIP/ZIP64 byte validation is implemented. Complete actual XML/supporting-file validation, attachment closure, source/output identities and multiplicities, metadata and logs before atomic publication. Diagnostic delivery is separate from clinical package availability. |
| Secure delivery | Recipient-bound CMS encryption, independent decryption and live authorization primitives are implemented. Complete application-enforced disclosure authorization at creation and retrieval, owned artifact retention/active-download protection, recipient-bound encryption without plaintext fallback, approved decryption and real importer acceptance. Respect receiver archive limits or create independently importable volumes. |
| Upgrade compatibility | Accurate dependency fingerprints, old persisted-source readability and verified pending-job recovery across application/runtime/schema/store versions. Never rewrite recorded fingerprints. |
| CLI and browser parity | Real installed browser and separate `carlos-ctl` package runs, restart and interruption recovery, cross-interface authorized observation/control, equivalent semantic output and dispositions. |
| Independent semantics | Reviewed expected clinical facts independent of production mappers; mutation tests must fail for missing/duplicated occurrences, stale values, wrong choice arms and lost precision. |
| Resource and side effects | Bounded memory/disk/queries/time, physical failure/race tests, and no clinical mutation other than separately reviewed provenance work. Export ledger, disclosure audit and owned artifacts are explicitly permitted effects. |

Wire validity, logical-profile compliance, current-chart completeness and receiver
interoperability remain distinct acceptance claims. Source coverage alone cannot
publish a package. Selected, partial, review-required, cancelled, failed and
expired outcomes never inherit complete-chart migration labeling.

Keep existing [legacy projection refusals](cds-legacy-projection-contract.md)
until each replaced path proves its coverage. Do not relax unrelated REST, SOAP,
FHIR or PDF safeguards when enabling the new exporter.

## Focused verification

```sh
mvn -B -ntp -Dtest=CdsContractUnitTest,CdsExportReconciliationValidatorUnitTest,CdsExportCoverageValidatorUnitTest test
mvn -B -ntp -Dtest=CdsSpecificationCrosswalkUnitTest,CdsRulesetFingerprintMappingUnitTest,CdsClinicalSourceServiceUnitTest test
```

These commands cover component and importer regression checks. Native database and
installed CLI/browser metadata/control checks have also run as described above. Complete
clinical CLI/browser export, eForm rendering, upgrade/recovery and an independent clinical
receiving-system oracle remain required before qualifying an export profile.


## Retained demographic core reconciliation

The demographic core coordinator binds the actual applied parent, sealed preparation,
patient lineage and authenticated source field pages to historical and current native
column witnesses. A matched existing chart is never assumed to have received prepared
source values. Missing legacy baselines, row substitution, conflicting source identities
and changed SQL column recipes require review. Generated SQL timestamps remain explicitly
unqualified by the general row comparator.

Separate name, birth-date, scalar, optional-date, health-card and address components own
27 base-row fields without overlapping decisions. Unchanged source representations retain
name-part boundaries, phone-independent details, address lines, formatted addresses and
date offsets. The common calendar mapper uses the effective prepared date for accepted
corrections and synthetic shifts; later native edits are not shifted again. Ambiguous
compound-name/address edits and changes to source-only projections require review. A full
health-card clear removes its complete subtree, and adding a card creates the required
parent and children together. Province edits also reconcile the postal/ZIP choice.

The coordinator keeps the other 32 base-row fields and all other source IDs as explicit
hand-offs. Phones and extensions, notes, enrolment and archives, professional/patient/provider
references, pharmacy, operational/native-only values and complete source/native closure still
need the enclosing demographic assembly. `coreRepresentable()` therefore does not qualify a
whole demographic, an export job or a profile. One unresolved component prevents all core
changes from being used. A completed genuine import with multiple name parts, two address
forms, phone components/extensions, enrolment history, a patient note and offset-bearing
dates supplies the installed reconciliation fixture; its historical metadata is not simulated.


The adjunct coordinator now combines the core, retained phones and the patient note in
one field/source-ID ownership check. It accounts for home/work numbers, every known phone
property column and the note content/owner; unknown properties, care-team carriers and
other demographic fields remain explicit hand-offs. The seven-column historical
`demographicExt` receipt omits its captured invisible generated key. Phone reconciliation
checks that key independently instead of weakening the general witness guard. Original
phone components and unprojected mobile extensions survive unchanged rows; ambiguous
later edits require review. Missing note rows supply explicit blockers even when there is
no remaining native content field on which to attach a review outcome.

For demographics-only jobs, note reconciliation compares the selected five cust columns
against the historical full baseline through the reviewed partial comparator. It does not
claim to verify the omitted alert column. Care-team fields remain separate native facts.
The outer unotes frame is plain text; embedded delimiters are never parsed as markup or
used to truncate a note. Null clears and empty notes remain distinct. Native edits to a
source-only oversized note require review. Simultaneous core/phone/note changes are all
withheld when any component requires review; remaining fields still prevent whole-chart
qualification. Genuine imported-data edit/restoration fixtures cover both components.


## Native contact and pharmacy source capture

Import-file evidence now has separate owning-DAO readers for `cds_import_file` and
`cds_import_file_reference`. Their shared-payload closure is limited to units belonging to
the selected patient and clinical categories; the owning run alone never supplies access.
UUID file identifiers and digest reference identifiers retain their original forms. Exact
ON, BC and owned-database schema contracts, bounded paging/chunks and repeatable-read
snapshots apply to both tables. Capture retains `verified_epoch`, while the receipt recipe
omits it exactly as the importer does, because verification advances it after unit commit.
Missing or changed file dependencies still refuse a preservation-unit proof.

The contact-child binding verifies original lineage, copy identity, source path, ordinal
and the demographic-parent dependency against the parent's separately committed field
pages. Contact fields remain owned by those pages; they are not reassigned to the native
child. An actual completed modern import has passed authenticated capture and this binding
across 18 source tables, including both file dependencies, without simulated historical
metadata. Generated Contact/association SQL timestamps remain explicitly unqualified by
the general row comparator; equal clinical-column witnesses do not qualify an entire row.
The fieldwise contact reconciler now qualifies each original prepared projection against
historical and current native witnesses before choosing a source group or a current edit.
Unchanged groups retain the original first/middle names, purpose choices and telephone
components. Independent family-name, email, simple-phone, work-extension and matching-note
edits can replace their own fields; clears stay cleared. An edited compound given name,
embedded extension, unprojected purpose set or conflicting note copies requires review.
Other populated native fields, consent and inactive/deleted history keep their independent
dispositions. A genuine import has passed the authenticated retained-value path, complete
field-ID coverage, finished XML validation and independent name/purpose/extension checks.
The personal-contact assembler now checks whole-patient contact occurrence, native-row and
field coverage for one verified demographic parent. It reconciles every imported contact,
preserves its source field identities, and appends subsequently created native contacts
without duplicating the imported occurrence. Missing source children, row aliases, missing
or substituted frozen witnesses and additional associations to an imported contact refuse
assembly. Patient/provider/specialist links, professional relationships, unresolved consent
and history still require their dedicated projections or review. A genuine import followed
by actual later-contact and extra-association database mutations has passed the authenticated
assembly, XML and coverage checks; the owned mutations were restored. Complete demographic
reconciliation remains required before clinical output or an export profile can be qualified.

Four explicit owning-DAO adapters capture `DemographicContact`, linked `Contact`,
`demographicPharmacy` and linked `pharmacyInfo` rows in the common bounded read-only
repeatable-read snapshot. Both link tables retain deleted/inactive/history rows and
unresolved references for later disposition. External contact IDs must match their
canonical decimal identifier byte for byte; SQL numeric coercion cannot resolve padded
or malformed references. Type 1 patient links do not cause another patient's chart or
contact record to be read. Those relationships still require their own authorized identity
projection before clinical export can be qualified.

All four tables use exact installed schema contracts and ordered driver receipt metadata.
The CLI and web Spring configurations share the same readers, capture protocol and fresh
patient/category checks. The existing clinical access service supplies facility/chart
checks; `consentToContact` remains captured source data and is not an invented disclosure
permission. Capture alone does not map a preferred pharmacy, a personal contact or a
professional relationship into clinical XML, and does not qualify a complete profile.


## Native preferred pharmacy integration

The native mapper follows CARLOS's explicit rank-one active pharmacy preference and the
5.2 dictionary limits, including the 50-character pharmacy name limit. It maps address,
work phone, work fax and email without truncating values or reverse-parsing an extension
from a stored telephone string. The existing reviewed Canadian province and postal display
translations preserve their clinical meaning. A complete field-identity and presence check
covers both physical tables; row counts alone cannot substitute another source field.

Additional preferences, inactive/deleted history, duplicate first preferences, missing linked
rows, contact-consent context, a second phone, notes, service identifiers and unsupported
populated values remain explicit review requirements. Two authenticated empty tables are
distinct from a missing reader/table. The native projection and enrolment/phone/note result
must share the patient and protected source manifest before the schema-ordered demographic
writer can combine them. Imported demographic reconciliation remains mandatory; this
component does not qualify a whole profile or publish a clinical export.


## Native personal contacts and source access

The external personal-contact mapper groups associations through the exact captured Contact
identifier. Multiple purposes remain in one contact occurrence, including EC and SDM flags,
reviewed native role labels and exact free-text roles. Names, residence/cell/work numbers,
separate work extensions, email and the contact note are mapped without truncation, name
splitting or telephone reverse-parsing. Matching duplicate note copies share one output;
distinct notes require reconciliation. Every field of every supplied Contact and association
row receives an independently checked presence/disposition. Diagnostic destinations use the
output occurrence ordinal; native identities remain in protected field keys.

Inactive/deleted history, missing or malformed references, unexpected contact subtypes,
source-purpose placeholders, conflicting purpose assertions, consent/responsible-provider
context and populated out-of-schema values require review. Patient, internal-provider and
specialist links need dedicated authorized identity projections; professional relationships
need their own schema mapping. They are not read through a personal-contact alias or silently
filtered out. Retained demographic/contact reconciliation remains a separate mandatory gate.
The combined native writer requires matching patient and capture-manifest bindings for
contacts, preferred pharmacy, phones, patient notes and enrolment history.

Both pharmacy source readers require a separate fresh, patient-specific `_rx` read in addition
to the demographic wire-category rights. This matches CARLOS's pharmacy REST/Rx read routes.
The actual worker control checks both the effective provider and any delegated web operator,
with current account, patient and facility restrictions. Unknown/legacy controls fail closed.
These checks apply before and after capture and replay; they cannot join the source snapshot.
A late SQL revocation of `_rx` therefore refuses the projection. Selecting demographics does
not select medication payloads or bypass their source permissions.

Patient and pharmacy postal values require a known supported subdivision before choosing
`PostalCode` or `ZipCode`. A missing jurisdiction requires review; country is not inferred
from the shape of a postal string. These native components still do not qualify a full
clinical profile or permit publication.


The pharmacy source check includes both global `_rx` read and the patient-specific `_rx`
read used by the owning Rx route, for each acting identity. A patient-level grant does not
substitute for the route's global requirement.

Retained contact preparations are decoded only from applied `Demographics_CONTACT` child
units with their exact source/options/ruleset/type and original preparation seal. Combined
native names, separate prepared extensions and importer purpose placeholders remain evidence;
they are not reinterpreted as current wire values. All preparation decoders consistently
reject a sealed JSON `null` root with the safe integrity code. This decoder does not establish
the child-to-parent source-field binding, unchanged native projection or a completed retained
contact/demographic reconciliation; those gates remain required before clinical output.

## Captured original import options

Export capture now includes a finite `retained_import_options` source alongside the original run, patient and unit metadata. The owning run DAO identifies only the selected patient’s applied copy keys for the selected categories. The reader verifies the private hash-named acceptance document and, for course copies, its frozen copy plan, then recomputes the effective program/facility options digests. Only minimum verified settings and that patient’s bindings enter encrypted source capture. Unapplied attempts require no original files. Private commands, source paths and other patients’ copy bindings are excluded from this source and from clinical output.

The ordinary provenance index and retained-values scope read these settings from the authenticated source generation. Preparation seals retain their original options digests; course-copy digests are verified against their effective options instead of being incorrectly compared with the original unexpanded request. Mapping and restart do not reopen import job documents. Original options/document/copy hashes remain bound to the captured run. Older captures without the necessary copy evidence fail closed.

The retained enrolment source mapper applies the verified original shift to every historical date, including accepted logical corrections and source offsets, and verifies the selected event against the sealed preparation. Counts, duplicate paths, conditional dates and conflicting latest events are checked. This preserves history rather than reducing it to the native latest roster. Sixty focused provenance/source checks and sixteen enrolment-source checks pass. Installed genuine ordinary and shifted imports passed 24 and 25 component checks through nineteen captured sources. The combined package passed 2,797 Java checks without failures, errors or skips; the same packaged WAR passed the 24 ordinary and 25 shifted installed component checks. Native archive/current reconciliation, remaining demographic closure and clinical publication are still unfinished.

The complete reviewed import/export test inventory is committed in `scripts/ci/cds-coordinated-test-classes.json`. To reproduce it, provide the three owned disposable test-database property files through `CDS_TEST_PROPERTIES`, `CDS_LEDGER_TEST_PROPERTIES` and `CDS_EXPORT_TEST_PROPERTIES`, then run `python3 scripts/ci/check-cds-transfer.py`. Optional `--offline`, `--settings` and `--maven-local-repository` select the build environment. The script provisions no databases and reads no configuration secrets into console output. All required database fixtures and dependencies must be available; a skipped suite is not full integration evidence.


### Registered disclosure policy

The disclosure-policy service is packaged and installed component verified. Twenty-five focused policy/encryption checks and 14 exact packaged container checks verify registered certificates, approved audit scope, current expiry/revocation, strict JSON types, manifest coherence and real root-file ownership/permission checks. It supplies approved arrangement evidence; it does not grant clinical read access, qualify an export profile or publish an artifact. The clinical engine must connect it to acceptance and recheck it before capture, encryption, publication and download.

`cds.export.policyRoot` identifies an administrator-controlled absolute directory. Every directory in its ancestry must pass the protected-file trust checks. Policy files are root owned, private regular files; a service-readable group is allowed only under the existing protected-file contract. Symlinks and group-writable policy files are refused. Construction performs no policy reads, so metadata/control startup remains available without this configuration.

The protocol-one layout is:

| Relative file | Required fields in addition to `protocol: 1` and `enabled: true` |
| --- | --- |
| `policy.json` | `senderSha256`; `recipients`, `certificates` and `auditPlans` maps from registered opaque identifiers to exact file SHA-256 hashes |
| `sender.json` | Stable `namespace`, `physicianGroup`, `groupContactInformation` |
| `recipients/<id>.json` | Matching `id`, registered `certificateId`, `certificateSha256`, explicit logical-profile identifiers in `profiles` |
| `certificates/<certificateId>.der` | Exactly one DER-encoded recipient leaf certificate, pinned by both the manifest and recipient registration |
| `audit/<id>.json` | Matching `id`, approved access `description`, explicit `recipientIds`, `providerNos` and `profiles` |

Certificates must meet the existing authenticated-delivery contract: current validity, a non-CA RSA key of at least 2048 bits and permitted key encipherment. No recipient private key is accepted or retained. Filenames come from validated registration identifiers; callers cannot supply certificate paths or bytes.

Publish complete policy files before atomically replacing the approved manifest. The service verifies every selected document against that manifest and rereads the manifest before returning. Unknown properties, duplicate keys, trailing JSON documents, numeric/boolean values coerced into identifiers, malformed Unicode, unknown profiles and duplicate approvals are refused. An audit plan must explicitly approve the requested provider, recipient and logical profile.

The accepted `auditPlanDigest` binds the entire approved arrangement: manifest, sender registration, recipient registration, exact certificate and audit plan. Any later byte change requires a new accepted request with a new submission key; resume never replaces the original arrangement. Revoked or expired policy is unavailable even when its old digest is known. No production policy or clinical profile is enabled by this integration.

### Independent supporting-file readback

`CdsExportSupportingFilesValidator` reads the completed ReadMe and event CSV against the sealed physician inventory, actual ZIP entry paths and independently validated patient XML receipts. It checks recipient audit text, selected scope, identities, hashes, physician totals and category/report counts, requires complete UTF-8 input and EOF, and propagates cancellation. Exact byte and line limits and a bounded duplicate patient-identifier index prevent silent truncation. Caller-owned streams remain open. Thirty focused validator/composer checks, the 2,983-check coordinated package and 67 checks against the exact packaged implementation using genuine retained importer data pass. This consistency gate does not establish source completeness or authorize publication.


### Coordinated demographic assembly and ancillary hand-offs

`CdsExportDemographicAssembler` composes the existing scalar, phone, patient-note, enrolment, primary-physician, contact and pharmacy reconcilers before closing the explicitly reviewed ancillary base-row fields. The final pass requires the captured row witness and exact earlier field ownership. Null and empty ancillary values stay distinct; populated values without a qualified mapping require review and withhold every proposed XML change. Operational identity, local chart-registration and update metadata are captured without being presented as clinical history. The importer creates `date_joined` from its execution time; it is not the patient’s enrolment date. The enclosing pass also accounts for empty care-team carriers and blocks populated carriers until their physician relationships are reconciled. Missing or duplicated field hand-offs, substituted row keys, changed captured bytes and false presence claims are rejected. Remaining retained-source and associated-row hand-offs remain visible and prevent a demographic-complete result. This component does not qualify a clinical profile or enable publication.


### Sender patient identity reconciliation

`CdsExportPatientIdentityReconciler` explicitly decides the retained previous vendor-ID field after the demographic assembly. It binds that field through the authenticated demographic parent and imported patient row, checks the effective previous ID, and emits `C` followed by the exact positive native patient number. The result is injective within the registered sender namespace and fits the 20-character wire limit without hash truncation. The namespace stays in accepted server-owned intent; changing it changes identity. A database restore, copy or merge still requires the separate lineage and ownership policy before admission. An earlier review blocker withholds the identity change along with the other proposed XML. This component does not qualify clinical export or enable publication.


### Independent demographic field closure

`CdsExportDemographicClosureValidator` compares the assembled result with the exact captured row universe and authenticated parent source-field IDs supplied independently by the caller. It rejects deferred or substituted identities, presence mismatches, review blockers and overlapping XML replacement claims. The combined component proof passed 33 assertions against a genuinely imported owned synthetic chart, including no deferred native/source fields, independent closure, finished sender-ID XML readback and supporting-file consistency. This used isolated new components over the preserved packaged baseline; exact integrated-WAR qualification remains separate. A receipt proves closure only for that supplied authenticated demographic scope; it does not prove that the overall source catalogue is complete, that every serialized clinical fact is correct, or that disclosure is authorized.


### Scoped demographic occurrence service

`CdsExportDemographicOccurrenceService` connects the coordinated demographic mapper, registered sender identity and independent field closure to the same synchronous scoped XML emitter used by the clinical occurrence services. It loads bounded authenticated rows, rejects active merge scope, verifies retained parent receipts, requires every captured demographic field and source ID to be accounted for, and checks final source authority before returning. XML and receipt callbacks stay private until the complete enclosing operation succeeds. Native-only demographics, multiple applied demographic parents, nonempty unrepresented ancillary values and unresolved archive/care facts still require dedicated reconciliation; they cannot produce a partial demographic fallback.


### Historical note preparation evidence

The retained preparation reader now decodes the importer’s sealed history/clinical-note preparation for the seven supported history categories. The original payload seal, category-specific issue code and source date dependency must match. Unknown and partial temporal values remain explicit; native timestamp or author reconciliation is not inferred. Twenty-five preparation-reader checks cover existing categories and the new history decoder. Native case-management note capture, issue/revision reconciliation and fresh program-specific access still require separate implementation and installed qualification.


### Per-note program authorization

History and clinical-note payloads require the shared worker's `authorizeNote` boundary in addition to the selected category, patient and `_eChart` read privileges. Both the initiating and effective providers must pass the checks. `CdsExportAccessService` compares the captured note ID, patient, program and reporter role with the live physical row in a fresh authorization transaction. The owning DAO projects access metadata only and reads current rights without the chart cache.

The policy follows the active `CaseManagementManager.filterNotes` and chart entry routes. A patient with admissions requires a provider membership in an admitted program (including historical admissions), or an active referral into a provider program. With no admissions, the domain check allows the ordinary chart read. Empty and zero program numbers use that domain and the chart/category rights. A positive note program supplies the exporter’s program context: an unambiguous membership role is required, and the reporter’s same-role default or a `read ROLE notes` default/program grant must allow reading. Role names and program map keys use the active reader’s lowercase spelling with spaces preserved. Access-type labels do not substitute for these names. Different membership roles or duplicate program map keys refuse disclosure instead of choosing an arbitrary row.

The trusted server setting `FILTER_ON_FACILITY` defaults to true, as in the chart. A positive note program then requires the command’s verified facility to match the program; disabling the setting does not remove program-domain, membership, chart or category checks. Invalid settings refuse the operation. Locked notes, unknown lock flags and malformed or changed dependencies also refuse disclosure. The unused `filterNotesByAccess` helper’s legacy `ROLEnotes` and admission-interval rules are not used. Earlier component evidence used that helper; the current active-policy checks below supersede those authorization claims. History issue/revision reconciliation and complete clinical output remain separate work.


### Retained history source reconciliation

`CdsExportHistoryRetainedSource` authenticates each history occurrence’s complete field set, original options and sealed preparation. Its public entry requires live authorization for the exact native note referenced by that import unit, before reading retained clinical text and again before returning. Structured descriptions, separate notes, residual data, participating authors and reviewers remain in their original wire fields. All known dates use the verified original shift, including non-projected resolution, procedure, exposure-end and reviewer dates. Precision, offsets and fractional clock spelling survive unchanged. A partial date that cannot be shifted, an inconsistent observation selection, a changed narrative, a stalled or failed authenticated stream, or an expired callback refuses the result. Oversized source narratives remain complete; the importer’s native source-view marker is never substituted as clinical output. Current native note, issue and revision reconciliation and the history occurrence service remain required before publication.


The packaged history-source component was checked through a genuine import of all seven history categories. Exact-WAR readback preserved every retained field, structured descriptions, separate notes, partial-date precision, clock fractions/offsets and author/reviewer dates. Actual program membership revocation, revocation at the end of a note-body stream, and locking of that note all refused the public source-reader entry point. These 32 component assertions do not qualify native note/issue/revision reconciliation or a clinical export profile.


### Captured note access dependencies

The encrypted native-source manifest can now carry each note’s patient, native identity, program and reporter-role dependencies. These are saved identities, not saved grants. Native capture prepares an immutable patient scope after category authorization, checks the prepared reader’s table/category/dependency identity, and verifies captured note scope fields independently during readback. Recovery and buffered final-row callbacks recheck the exact captured note dependencies through the common authorization boundary. A missing scope, another patient or note, changed program/role, locked or unknown lock flag, oversized metadata, missing field or expired validator refuses completion. Existing tables omit the empty note metadata property and retain their prior serialized manifest shape. The physical note reader is now integrated; issue/revision reconciliation still remains, and no clinical profile is enabled by these infrastructure checks.


### Bounded native note capture

`CdsExportNoteSourceReader` delegates physical schema, ordered receipt metadata, row headers and text chunks to the existing case-management note DAO. A separate scalar preflight obtains the complete bounded patient note scope; every note then requires current program/reporter-role permission. The reader compares that immutable scope with the actual repeatable-read snapshot before any payload and preserves all 25 physical fields in private encrypted capture. No activity, archive or lock filter silently removes a row. Locked, malformed or unauthorized notes refuse capture; ordinary imported notes with an empty program remain eligible under the chart and patient-domain rules. The current reader requires all seven history categories together until an independent issue classifier qualifies narrower projections.

The reader passed 167 focused checks including actual MariaDB ownership, large UTF-8 LOBs, snapshot isolation, strict physical schema, real CLI Spring bootstrap and import-fingerprint regressions. A component proof over the preserved package passed 44 assertions through a genuine seven-category import, including complete native note field/receipt capture and revocation during the final buffered text-row and receipt-row callbacks. This used newly compiled components in one assembled class directory; it is separate from exact packaged-WAR qualification. Temporary native access edits were restored. Issue, extension, revision and native-only reconciliation remain unfinished.

The new owning-DAO read projections participate in the existing deployed implementation fingerprint. Pending import jobs retain their recorded fingerprint and follow normal upgrade/recovery checks; they are never silently restamped. Completed retained imports remain evidence under their original sealed ruleset.


The native-note capture checkpoint is now package verified: 3,911 Java checks passed across 317 selected suites, followed by 44 assertions using the exact packaged WAR and byte-for-byte exploded classes. The previously completed genuine import kept its original ruleset; no import metadata was restamped. The earlier assembled-component proof remains separate evidence. This qualifies the captured native note component and its access fences, not a complete clinical history exporter.


### Native note extensions and active access policy

The owning case-management extension DAO now supplies all five physical `casemgmt_note_ext` fields through the same bounded, read-only snapshot interface. Patient ownership is established by the exact parent note. The source reader requires the complete captured note table, so extensions inherit its saved note identities and fresh live authorization at readback and after the final consumer callback. Unknown keys, historical rows, large values and date precision records are captured without filtering.

The updated note/extension and access components passed 183 focused checks, including real MariaDB program-domain, same-role, explicit/default grants, active-referral, facility, malformed-scope and locked-note cases, shared capture regressions, CLI bootstrap and importer fingerprint validation. A genuine completed seven-category import passed 55 installed CLI component assertions using one assembled component class root over the prior packaged WAR. Those checks captured the original unscoped notes, all 25 note fields, all five fields of 18 extension rows and complete row receipts; source dates and complete XML stayed consistent, and membership revocation at final text/receipt callbacks refused completion. The installed fixture explicitly disabled facility filtering for its scoped revocation scenario; separate MariaDB tests verified enabled facility enforcement. Synthetic access edits were restored. These component-overlay results do not replace exact-package qualification or enable clinical publication.


The active-policy and extension checkpoint is now verified in the actual packaged WAR (`ad1045c3a0425b538c0ab6dace866f2330e40952ced7dd3f01a2c5e4d31c5b00`): all 3,919 coordinated checks passed, followed by the same 55 installed CLI assertions with byte-for-byte exploded-class verification and no component overlays. Its genuine completed import retained the original ruleset and metadata. This supersedes the earlier component-overlay access qualification; issue/link/revision reconciliation and clinical publication remain unfinished.


### Complete physical note-to-issue relations

`CdsExportNoteIssueLinkSourceReader` now reads the physical `casemgmt_issue_notes` relation through the existing note DAO. Its two-column key is paged without `DISTINCT`, arbitrary row numbers or dropped duplicates. Independent counts cover the entire owned relation, including archived notes. A duplicate relationship, dangling reference or either direction of a cross-patient link requests review through the worker’s existing `AMBIGUOUS_SOURCE` outcome. The source requires the complete captured note table and inherits its exact saved note scope and final authorization checks. Issue catalogue payload and clinical interpretation still require their own readers and reconciliation.

The chart’s globally grouped UUID revision chains also receive an ownership existence check before scope discovery and on fresh per-note authorization. A chain shared with another patient or an unassigned row refuses disclosure; the query reads no foreign note text or identifiers.

These additions passed 189 focused checks and 58 installed CLI component assertions over the prior exact WAR. The assertions include all six genuine imported history relations and refusal when a foreign revision is introduced during final text/receipt callbacks. A further 34 execution and actual MariaDB checks passed after link conflicts were assigned to the explicit review state. These are component results pending coordinated packaging and exact-WAR qualification of the added relation/ownership code.


### Patient issue and catalogue source capture

The existing patient-issue and issue-catalogue DAOs now expose the same finite, bounded scalar snapshot interface as notes. `CdsExportCaseIssueSourceReader` captures all ten patient-issue fields, including resolved and unlinked issues. `CdsExportIssueCatalogueSourceReader` captures all eight definition fields for exactly the catalogue identities referenced by that patient’s issues. Codes, descriptions, roles, flags and original identifiers remain exact; no activity filter, code de-duplication or inferred crosswalk removes data. Whole-row receipt recipes cover both tables.

Both readers require the captured note, physical link, patient issue and catalogue context. Their shared worker capability checks current chart/category rights and the active chart’s patient program domain for both identities, even when the patient has no notes. This follows the chart’s local issue-list read boundary; the issue-search write/read role filter is not substituted for that route. Authorization runs outside the source snapshot and is repeated after final callbacks. The CLI and asynchronous browser worker use the same capability; its default implementation refuses access.

The combined changes passed 205 focused checks, including installed MariaDB schemas, exact receipt metadata, retained resolved/unlinked issues, fresh authorization regressions and real CLI bootstrap. Seventy-one installed CLI component assertions verified the genuine import’s six patient issues, their six exact catalogue definitions and whole-row receipts, seven note/category/issue-code associations, and refusal after chart access was revoked in final catalogue text/receipt callbacks. Temporary access edits were restored; import metadata kept its original ruleset. These component results remain separate from pending coordinated packaging and do not complete clinical field, issue or revision reconciliation.


The patient-issue context checkpoint is now verified in the exact packaged WAR (`75e973f31190fdbf4e4266f7fee08056664ab6848291f0229413a6dcd95af829`): all 3,931 coordinated checks passed, followed by 72 installed assertions with byte-for-byte exploded-class verification and no component overlays. All six patient-issue references matched their original historical row receipts. The genuine completed import kept its original metadata and ruleset. Physical relation/ownership and issue-source component qualification is complete at this checkpoint; clinical field/revision reconciliation and publication remain unfinished.


### Streaming revision metadata and current native authors

`CdsExportNoteRevisionReader` verifies the complete authenticated source while materializing only seven bounded revision metadata fields. Note bodies, history and passwords stream through verification without entering its DTO. Exact UUID spelling, nullable legacy fields and native dates remain unchanged; no current revision is inferred from timestamps. The complete source generation, row count, patient/record identities and final live authority must all match before return. Nine dedicated tests cover large text, missing or substituted metadata, incomplete streams, late writes and consumer failures.

The public provider source now includes exact public reference candidates for note authors and signing providers when the complete history group is selected. It requires the captured note table, excludes unselected note associations, preserves historical inactive provider references and still captures only its six approved public fields. Empty or zero reporter-role defaults remain exact on empty/zero-program notes, matching the active chart’s unscoped read behavior; positive programs still require a valid role and current program rights. Roles are never replaced with invented defaults.

The additions passed 83 unit/shared transport/fingerprint checks, 14 actual MariaDB note/issue/provider checks and 65 legacy-scope/access/capture checks. An initial transaction-fixture assertion was corrected and its entire affected DAO suite passed on rerun. Seventy-four installed component assertions over the prior exact WAR verified streaming revision metadata and final callback revocation alongside genuine note/issue/source readback. Final packaging also exercises the legacy reporter defaults. These are focused/component results over the separately preserved 3,931-check coordinated package; they do not establish completed revision or clinical field assembly.

The migration guide’s section 18.2 describes the wire document as a current clinical snapshot and distinguishes data-element audit changes from current clinical history. Revision mapping must therefore keep the current clinical projection and its separately preserved audit evidence distinct; old revisions must not be emitted as independently current notes. Section 18.1’s supplementary source format allowance does not remove the requirement to map corresponding standard clinical facts.


The final revision/provider/legacy-scope diff is now package verified: 150 checks across 11 targeted suites over the separately preserved 3,931-check baseline, followed by 76 installed assertions using the exact WAR and byte-for-byte exploded classes, with no component overlays. Empty and zero reporter defaults were captured without substitution, and all temporary fixture changes were restored. The final WAR SHA-256 is `b933829ab33ac337d4bb29c0e64a9137e734dd73a30f8e91b7a2c6d30a6e7f4f`. These counts remain separate; they are not added together or described as a new full-suite run. Clinical field/revision assembly and publication remain unfinished.


### Independently witnessed revision inventory

`CdsExportNoteRevisionInventoryService` owns both the complete frozen row-receipt scan and the streaming metadata scan. Every metadata field must match its exact whole-row witness, and the note identities must exactly equal the captured access universe. Source-generation mismatch, missing/duplicate rows, unavailable witnesses, substituted metadata and final authority changes refuse completion. The inventory has explicit row and byte limits; exceeding them enters the existing resource review state.

Canonical UUID case aliases form one chain. The current native note is the maximum note ID, matching the active chart rather than timestamp order. Every earlier revision ID remains explicit audit evidence. Missing or unsupported UUIDs require review, and unsupported flags quarantine the entire recognized chain. The result is source-bound and protected from status/log rendering; it does not itself approve clinical content or serialize old revisions as current notes.

The inventory additions passed 38 focused checks, including ten planner tests, six service tests, shared capture regressions, real CLI bootstrap and importer fingerprint validation. Seventy-eight installed component assertions over the prior exact package verified the genuine seven-note inventory and actual memory-limit refusal alongside retained/native/source checks. Packaging and exact-artifact qualification follow separately. Current-note field reconciliation, historical evidence serialization and clinical occurrence assembly remain unfinished.


The revision inventory is now package verified: 166 checks across 13 targeted suites, followed by 78 installed assertions using the exact WAR (`14c225f58639e78506e45510337ed5b926999afc3a4850ad82d0a44da56614e3`) and byte-for-byte exploded classes with no component overlays. The genuine note universe and whole-row metadata witnesses matched completely, the memory budget requested review when exceeded, and temporary fixture access edits were restored. These counts remain separate from the preserved 3,931-check full baseline. This qualifies the revision inventory component; current clinical field reconciliation, audit evidence serialization and history occurrence assembly remain unfinished.
