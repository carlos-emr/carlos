# CDS export implementation contract

Native demographics, all seven case-management history/note categories, measurements,
HL7 laboratory results, document reports and prescriptions have native mappings alongside
retained-import reconciliation. Revised notes preserve the original and edits in one
occurrence. Patient and child-table scopes keep other patients' clinical data outside a
selection. Publication still requires independent source/field closure; unsupported or
inconsistent selected data requires review instead of a partial package.

The current review fixes preserve sealed import preparations, move native ATC allergy
classes into an explicit residual marker (the standard allergy Code is DIN-only),
respect native inclusive appointment end times and date-only immunization storage,
and retain native enrolment history beside the current demographic snapshot.
Patient membership is written synchronously to the normal chart audit in bounded
batches before a private package is returned; PREPARED and actual publication/download
outcomes are explicitly distinct and remain linked by run and attempt generation.

Native prevention screenings (the bundled Screenings types and `OtherB`, which the frozen
prevention classification puts in the non-immunization partition) are exported as PastHealth
procedures when PastHealth is selected, as the legacy exporter wrote them. The description is
the prevention type, its Refused, Ineligible or Completed externally status and the
`Result:`/`Reason:` extension lines; a description longer than 250 characters keeps the type
and status and moves those lines whole to Notes. ProcedureDate is the recorded calendar date at
its recorded year, year-month or full-date precision (a native clock time stays in the
residual), and Notes are the comments. The complete parent, extension and precision rows travel
in the `CARLOS.Prevention.Source.v1` residual as JSON with SQL NULL and empty text distinct and
spaces escaped, so `xs:token` normalization cannot change them. Screenings are captured through
their own logical tables (`preventions_screening`, `preventionsExt_screening`,
`prevention_precision_screening`) beside the classification inputs shared with Immunizations,
so every screening has its own expected PastHealth identity and row witnesses; a missing,
duplicated or unwitnessed row refuses the patient. Program-restricted rows, rows whose type is
not a proven screening, unknown status codes, duplicate result/reason/comments extensions and
unsupported date precision hold the patient for review, and any unclassifiable prevention row
also holds a PastHealth export because it may be a screening.
also holds a PastHealth export because it may be a screening.

A patient status CARLOS defines without a CDS value is exported by meaning ("Moved",
"Fired") as PersonStatusAsPlainText, and the CARLOS importer maps those labels back to MO and
FI; a clinic-defined status is exported as stored. The primary physician's practitioner number
becomes PrimaryPhysicianCPSO only when its recorded authority is CPSO; a number with no
recorded authority holds the patient (see the operator guide). Once-daily "OD" is written as
CT-024 "QD" on both the native and reconciled paths, and when a prescription's start date is
marked unknown its date-precision rows travel in the medication residual instead of being
counted as a StartDate.

Thirty-six further patient-linked tables are classified rather than refused. Edit history whose
current record is exported (consultation request and extension archives, tickler links left
after their migration to ticklerdocs, the consent migration audit), working state that is not
part of the record (autosaved note drafts, eRefer and document-queue staging, the recycle bin,
vacancy matching, saved demographic sets, report and letter scratch tables) and per-patient
reference or configuration rows (saved filters and queries, eForm report-tool definitions,
flowsheet and prevention-report settings, letter templates, WCB code lists) are not exported and
no longer hold a patient. Each table's reason is its ruling note in
`src/main/resources/cds-export/source-catalogue.json`.

Native vaccines are exported as Immunizations exactly as the prevention form records them. The
name is the recorded brand name, or the configured prevention type when the name is blank;
ImmunizationType is the configured Health Canada type when it is one of the CT-020 values, and
is left out (never guessed) for a configured vaccine outside that list, such as COVID-19,
Pneu-C-20 or RZV. Site, route, dose, lot, manufacturer, instructions and comments map to their
standard elements, and an eight-digit DIN becomes ImmunizationCode; text typed in the DIN box
that is not an eight-digit DIN is never exported as one. "Completed externally" exports as given
(RefusedFlag N) and "Ineligible" as not given (RefusedFlag Y), with the status as the first line
of Notes. The complete parent, extension and precision rows travel in the same
`CARLOS.Prevention.Source.v1` residual as screenings, so the recording provider, reminder date
and "never remind" choice, the CVC concept, flu risk factors, refusal reasons and the free-text
administering provider are carried exactly. The form saves an empty row for every unfilled
input; an empty row is not a fact and never hides the type name. An edited vaccine's link to the
version it replaced is not exported. Program-restricted rows, unknown status codes, duplicate
standard fields, unsupported date precision and the importer's date hints on a row no applied
import claims still hold the patient. An imported vaccine is written from its retained original
elements without this residual, so a provider, reminder or status added after the import holds
the patient for review.

Superseded or removed prevention versions are not exported, for vaccines and screenings alike.
CARLOS never edits a prevention in place: an edit soft-deletes the old row (`deleted = '1'`)
and inserts the new version, and a delete only sets the flag. The legacy exporter read only
`deleted = '0'` rows (`PreventionDao.findNotDeletedByDemographicId`). These versions stay in the
capture and are witnessed; every field of the version, its extensions and its precision rows
gets the `SUPERSEDED_OR_REMOVED_PREVENTION_VERSION` disposition, the field-closure count covers
them, and they are neither an expected occurrence nor a reason to hold the patient. A legacy
NULL flag records no deletion and is current. Any other flag value holds the patient. An
imported vaccine whose row was later edited or removed also still holds the patient: its import
unit's retained source fields can only be accounted by an emitted occurrence. Without PastHealth, screenings are an explicit
scope exclusion, and the ReadMe field `Prevention screenings (exported only as PastHealth)`
says `NOT_SELECTED`. The CARLOS importer files each entry as a Past Health (MedHistory) note
whose text is the description and whose observation date is the ProcedureDate; Notes and the
residual stay in the retained CDS source fields.

Native measurements are exported as CareElements where a standard element represents them
exactly, and each current observation also travels in full as a
`CARLOS.NativeObservation.v1` NewCategory record. Deleted measurements are not exported. A
`measurementsDeleted` row is either a clinician's deletion of an erroneous entry
(`EctDeleteData2Action`) or an HL7 result version superseded by a newer report
(`Hl7textResultsData`); neither is a current fact, and the legacy exporter, which read
`MeasurementDao.findByDemographicId`, never exported them. Deleting a measurement moves its row
but leaves its `measurementsExt` rows keyed by the original id, so capture follows
`measurementsDeleted.originalId` as well as current measurements. Every field of the deleted row
and of those extensions gets the `DELETED_OR_SUPERSEDED_OBSERVATION_VERSION` disposition: they
are accounted, never an expected occurrence, and no reason to hold the patient, and they need no
NewCategory selection. An extension whose id names both a current and a deleted observation, or
two deleted ones, holds the patient (`CDS_EXPORT_NATIVE_CARE_EXTENSION_OWNER_AMBIGUOUS`); an
original id deleted from two charts holds both (`CDS_EXPORT_AMBIGUOUS_SOURCE`).

Finite operator settings include `cds.export.captureTimeoutSeconds` (default 120,
range 1–7200), `cds.export.downloadTimeoutSeconds` (default 900, range 1–86400),
and `cds.export.maxMetadataBytes` (default 256 MiB, maximum 4 GiB). Patient capture
holds the legacy Rourke table's READ lock for at most the capture timeout plus
30 seconds; its independent deadline defaults to 150 seconds. Raising the capture
timeout can therefore block writes to that legacy form for longer, up to 7,230
seconds at the maximum setting. Raising metadata
limits requires enough JVM heap for retained patient/file/note indexes and normal
application work. These limits act together with member, row, byte and staging limits;
raising the patient count alone does not qualify an arbitrarily large package.
Native clinical clocks use the same explicit `cds.export.clinicalZone` as the accepted
plan; a configured import zone must agree. No JVM default zone is inferred.

This change targets `release/2026.08`, integrating through
`38cfc10c0ae72c66ebe501703de462f59249da1f`, including the release lab and package/test qualification fixes.
Its released migration bytes are identical to the pinned upgrade SQL baseline
`ef69eea17a4cc3fb288b26327471fce1e20dcd8f`. The integration retains the release's
importer, MariaDB Connector/J 3.5.11 and DAO/API fixes. It excludes develop-only
portal, SMS and email features. Historical WAR hashes and workflow results below
describe their recorded checkpoints; they do not qualify this release integration.

The new, unpublished migrations are `V1.0.59.38` through `V1.0.59.43`, following
the release's common `V1.0.59.37`. All 87 released SQL files remain unchanged.
The billing filename audit already shipped at common `V1.0.59.33` is reused. Fresh
ON/BC installations and upgrades from the pinned release are checked separately.
The upgrade gate requires unchanged applied Flyway history, retained synthetic
clinical/audit/property rows, explicit packaged CLI migration and validation,
boot validation, zero pending migrations, and the same physical schema and seed
expectations as a fresh install. It does not repair or rewrite migration history.

The release prescription receipt stores only delivery hashes, identities and outcomes, so it is classified as operational. Favourite-group tables inherit the existing `favorites` requirement to be empty; unqualified templates are never silently omitted. The drugs concurrency version is retained in read evidence as storage metadata, not presented as a clinical CDS value.

Both directions use the same MariaDB binary-read adapter. It verifies the actual
result decoder before exposing values, because Connector/J can fall back to text
protocol while retaining its server-prepared statement class. Unsupported driver
inspection or text results fail explicitly. Required tests cover actual float bits,
text-result refusal, statement ownership and bypass attempts.

The package lifecycle gate keeps its published beta6/CLI 1.3.1 and beta5/CLI 1.3.0
predecessors intact. Its unpublished 1.3.4 candidate is separately bound to the exact
companion fallback commit and actual CI-built Debian bytes (358,440 bytes,
SHA-256 `918a8b12ddc299c8dddab44013c5bcced9a7ab3002c6d4622b31e57feb0a3170`,
workflow 42759435594, artifact 13740162733). All 30 installed Python modules,
the executable shim, manual and changelog were compared with that source.
Source-built mode asserts no published tag or release-download URL. Lifecycle
configuration must supply this exact candidate package; predecessor packages
cannot substitute for it.

The companion CLI PR also targets `release/2026.08`, starting at its compatible
`84adfc5c237d4a536300a9d7e74e19599df23dcc` baseline. Its pinned runtime remains
`be395c7cfa008d45f6f1bb0a38bdd40d49f51cfa` (unpublished 1.3.4), with schema manifest format 2 and
ETL contract 4. CLI main has advanced to format 3 / ETL 6 for a newer application
line; those changes are not part of this release pair.

| Release migration | Purpose | Earlier unpublished develop numbering |
| --- | --- | --- |
| `V1.0.59.38` | Nullable native field witnesses; no clinical backfill | `V1.0.82` |
| `V1.0.59.39` | Independent export run/event ledger and authorization | `V1.0.83` |
| `V1.0.59.40` | Artifact publication attempts | `V1.0.84` |
| `V1.0.59.41` | Approved independent administrator import grant, preserving explicit decisions | `V1.0.85` |
| `V1.0.59.42` | Indexed virtual HRM numeric patient key; original owner strings remain unchanged | — |
| `V1.0.59.43` | Protected record of patients held back from an export package (chart number and safe code only) | — |

The earlier `V1.0.86` consent forward-merge repair does not apply to the retained
release history and is excluded. The release catalogue contains 510 rulings for
457 ON and 490 BC physical tables. Its appointment tables do not contain
develop's `locationCode`; unknown future columns still require explicit review.
All importer receipts, recorded fingerprints and released migration bytes remain
unchanged. Both directions retain separate authorization and admission.

For deployment, browser/CLI operation, recipient decryption and support arrangements,
start with the [operator guide](cds-export-operator-guide.md).

The OntarioMD CDS 5.2 exporter lives beside the modern importer under
`demographic/cdsexport`, sharing the pinned wire contract and clinical scalar
semantics while keeping export admission, authorization and delegation independent.
The registered `CdsExportClinicalEngine` connects the same clinical job service to
foreground CLI work and durable asynchronous browser work. Export admission remains
disabled by default. PCON is recognized as a logical identity but is not advertised
as an implemented export profile.

An accepted job fixes its authorized selection, recipient/audit arrangement,
implementation identity, clinic zone and storage binding. Patient-scoped read-only
capture, independent source/native field closure, typed clinical assembly and
finished XML/ZIP/supporting-file checks precede recipient encryption. Only verified
publication can atomically set `READY`. Download rechecks current authorization,
authenticates complete ciphertext and holds a separate lease against retention.
Unknown, changed, ambiguous, deleted, unrepresented or unauthorized clinical facts
require explicit review; they cannot produce a truncated or partial migration that
is labelled complete. The source catalogue classifies every physical ON/BC table;
unreviewed sources must be empty. Native-only history, unsupported historical
carriers and other unqualified clinical representations remain review cases.

The execution lease is renewed independently during bounded source I/O. A worker
that returns after its own lease expires records `CDS_EXPORT_EXECUTION_LEASE_EXPIRED`
and requires review; it cannot change a newer generation or a completed publication.
Delegated browser retrieval rechecks the configured delegation and the current
administration/read rights of both the account's provider and the authorizing
provider. Metadata ownership never substitutes for those clinical checks.

Saved jobs are listed newest first, with a stable creation-time/identifier cursor
restricted to the same operator. Browser controls fence pending polls before
starting a fresh status chain. Known missing jobs return HTTP 404 and invalid state
transitions return 409; unexpected failures are logged without exception messages,
patient values or physical paths. A committed download never enters Struts error
rendering. Fully written ciphertext with a failed final confirmation is audited as
`DOWNLOAD_COMPLETION_UNCONFIRMED`, rather than as confirmed delivery or no delivery.

Unpublished DDL is safe to replay after an interrupted statement sequence. The
native gate exercises this on its own MariaDB fixture, preserving existing native
witnesses, attempt rows and explicit privilege denials. Shared prevention readers
also preserve a legacy row with a null demographic association. An unavailable
source still fails explicitly instead of presenting an empty or incomplete chart.

## Historical implementation and qualification log

Everything below records earlier checkpoints, including work originally based
on develop. References to pending implementation, approval decisions, migration
numbers, source counts or nullable appointment locations describe those recorded
checkpoints. They do not override the release contract above, the current
[implementation progress](cds-export-progress.json), or the requirement-to-code
references in the [standards review](cds-export-spec-review.json). Only required
evidence bound to the exact current source and WAR qualifies a release artifact.

The exact source-catalogue WAR passed independent native-chart and genuine
sixteen-category-import checks without application-class overlays or restamping
import metadata. The latter detected 18 schema-valid clinical mutants. The concrete
adapter checkpoint passed a clean WAR/Javadoc build and 1,000 focused checks across
66 suites, following the preserved 4,914-check coordinated baseline. Its exact WAR
(`c9e1d8a737f15fdb9b10dba85ee33274dc826a8bf7b66905845520a6a273bad2`)
published a real native-chart package through `CdsExportPublicationService` and
retrieved it through `CdsExportArtifactService`. All thirteen independently expected
clinical facts matched; four schema-valid mutations were rejected. Independent
recipient decryption reproduced the exact validated ZIP, without copying a private
key. Temporary chart/provider/grant changes were restored and the test's own
published attempt was expired and physically retired.

Normal engine registration and CLI capability reporting passed 36 affected checks,
including the real MariaDB/Spring CLI graph with disabled admission, a missing active
zone and invalid active budgets. Metadata status/control stayed available.

The exact normally registered WAR
(`17f93a04c56771befd30271d4cfd75c2daad2b492be50df7c22742afe847c407`)
then passed the actual Python CLI, root-issued delegation, service-user Java 25,
real Spring/MariaDB, clinical publication and authorized download path. Independent
processes observed the same durable job and submission identity. A second download
to the same destination was refused without changing its bytes. Status and report
remained available with both an invalid clinical zone and invalid active budget.
Independent recipient decryption recovered the exact validated ZIP, all thirteen
expected native facts matched and four schema-valid clinical mutants were rejected.
The test retired its own tickets/artifact/metadata and restored temporary
provider/grant changes; it retained the original native chart and protected keys.

The owned installed workflow now runs the actual CLI and the authenticated asynchronous
browser against one normally deployed WAR. At `0acf632d`, both reached `READY`,
downloaded and independently recipient-decrypted packages matching all fourteen
expected native facts and rejecting four schema-valid clinical mutants. Browser
login, CSRF/method refusal, HTTP 202 acceptance and reload/submission recovery also
passed. That run failed the final ledger assertion because CLI and browser runtime
compatibility digests differed; it is not a completed release qualification. The
fixture now matches the CLI bootstrap's explicit clinical default time zone and
minimal locale. Exact runtime equality, unchanged native chart/WAR and retired
process tickets remain required final assertions.

At `8a6248f1`, that complete installed workflow passed, including both real `READY`
jobs, equal CLI/browser runtime compatibility, recipient decryption, all fourteen
native facts and four mutation checks for each transport. The native chart was
unchanged, no import metadata was created, root delegation tickets retired, and
all 10,327 installed WAR files verified before and after. The actual WAR SHA-256
was `3a9f54e906a9c778d354252056d572a17359f996b42b7ac1a74ae024d6300c1b`.
The retained [workflow evidence](https://github.com/carlos-emr/carlos/actions/runs/42540856434)
is a passing checkpoint; later changes still require their own final-head checks.

At `a42feca0`, the complete installed CLI/browser proof passed again with WAR
`7cce2bc8e3f37d73e32ee05528be21f8c810dd4380abbece2c53d4347250195c`.
All 3,906 coordinated checks across 305 required suites passed with zero skips,
and all five installed importer resource profiles passed, including scaling and 20 GiB.
Those profiles exercise import; they do not qualify export resource behavior.
The full Java run had zero errors and one static migration-guard failure: its
matcher did not recognize `WHERE EXISTS (...) AND NOT EXISTS (...)`. The actual
migration's four native authorization/reapply cases passed. The candidate fixes
the generic matcher and keeps the approved SQL unchanged. Its progress-executor
retirement and prescription-preview load binding have additional focused checks;
final-head workflow results remain the release authority.


At `ce2aa735`, all 3,909 coordinated checks across 305 required suites passed
without skips. The [complete Java workflow](https://github.com/carlos-emr/carlos/actions/runs/42549472262)
passed 39,371 checks across 3,008 report files, with zero failures or errors and
315 visible optional-environment skips; its separate time-zone and font matrices
also passed. The installed CLI/browser workflow passed with all fourteen native
facts and four schema-valid mutants per transport, equal runtime compatibility,
an unchanged chart and all 10,328 installed files verified. Its actual WAR SHA-256
is `f9ff6661fdc7146eecd362d077597ce441ce8b1d4468c4fb2bdde19294610fba`.
SonarCloud passed; the complete post-analysis report contained no security or
reliability findings.

Resource evidence has separate scopes. The five profiles in
`cds-import-performance.yml` qualify the importer regression boundary. The new
`cds-export-resources.yml` qualifies only the packaged export byte layer: a 256 MiB
heap, a 20 GiB aggregate archive containing entries above 4 GiB, independent ZIP
readers, real 65,537-entry ZIP64 count overflow, recipient decryption and physical
32 MiB filesystem exhaustion/reserve refusal with private-file cleanup and
recovery. This is separate from clinical readiness and query-scaling evidence.
The exact current source compiled and the physical disk checks passed locally.
A separate streaming CMS recipient verified the bounded rehearsal and rejected
a tag-only mutation. OpenSSL interoperability uses its own bounded fixture.
External proof checks reject small rehearsals, absent counters, ordinary ZIP
counts, unconfirmed disk cleanup and failed recipient authentication. The initial
CI attempt stopped before resource execution because Maven created a root-owned
output directory; fixtures now use the runner-owned temporary root. The actual
large run passed at `a40178c7`: 21,475,164,160 aggregate bytes, 4,295,032,832-byte
entries, 65,537-entry ZIP64 and verified streaming recipient decryption, with a
256 MiB heap and a measured largest-child RSS of 346,894,336 bytes. Both export
resource jobs verified all 10,328 installed files before and after. The same
head passed all 3,909 native checks, the complete 39,371-test Java workflow, all
five importer resource profiles, installed clinical CLI/browser proof, SonarCloud
and the full Semgrep workflow. The final CLI pin includes the matching ten-minute
import/export orphan-ticket cleanup and the clarified package artifact identifier;
its paired-pin checks remain authoritative. The final test fixtures capture the real prescription-preview
listener, and Semgrep's 120-minute budget preserves its full scan and custom-rule
steps. Scanner execution errors or absent SARIF now fail explicitly while the
existing non-blocking findings policy stays intact. Current-head checks remain authoritative. CodeFactor still reports an
obsolete critical function span; its available official refresh calls returned
HTTP 403, and no failed check has been waived.

All 305 reviewed coordinated suites passed at `b59628c7`, with no failures, errors
or required skips, including the four real MariaDB permission-seed cases and the
actual Struts action factory. The independent administrator `_cds_import` grant
was explicitly approved and applied in the new V1.0.85 migration. Existing explicit
grants and denials are preserved. This independent permission survives later
removal of `_admin` authority and must be revoked separately when intended.
The maintainer DCO attestation was also explicitly approved; its PR comment and
check must match the final head. There is no outstanding permission decision.

The paired CLI checkpoints passed their unit, installed MariaDB and Debian checks.
The final unpublished CLI pin is `cdc1302c`; its cleanup follow-up at `e141d0cb`
passed Python3.12/3.14, Debian/lintian, installed root identity/expiry and man-page
checks. A disposable current-schema test copy passed all 2,017 CLI tests with
107 optional-environment skips, while verifying unchanged pinned runtime code
and tests outside the supported standalone-data export.
It incorporates the published manifest-format-2/ETL-4 billing contract and retains
its filename/audit safeguards. Version 1.3.3 remains unpublished. Final candidate
Java, security, installed resource and complete clinical workflow results remain
release gates; the current PR checks and their bounded evidence are authoritative.
The adapter is [carlos-emr/carlos-ctl#20](https://github.com/carlos-emr/carlos-ctl/pull/20).

The complete reviewed inventory runs in the import/export/shared jobs of
`.github/workflows/cds-transfer.yml`, each with newly migrated owned databases.
Missing, duplicated, ambiguous or skipped suite evidence fails the gate. Only
sanitized reports are uploaded. Older checkpoint narratives below are retained as
historical component evidence, not statements of the current readiness state.

The current pair forward-integrates the released billing compatibility change from
application commit `d40d2939afde9bea460b232fe78976b2820698ca` and CLI main
`84adfc5c237d4a536300a9d7e74e19599df23dcc`. It preserves native billing
creator authorization, original filenames, cross-table reservations, audit
provenance and guarded download/regeneration. Schema manifests use format 2;
properties and preflight retain format 1. The candidate requires CLI 1.3.3.
The new `billing_filename_import` table is operational financial audit metadata,
matching the existing billing-table disposition. All previous 516 source rulings
remain unchanged; the current catalogue has 517 rulings (464 ON and 497 BC tables).
No original CDS import metadata is rebound or rewritten.

## Historical verification checkpoint

At `0ca658cf`, all 301 coordinated suites passed: 2,234 export, 1,160 import
and 379 shared checks, with no errors, failures or skips. Later import/export
shards and the canonical DAO wiring check also passed. These checkpoints do not
replace current-head browser, broad Java or resource verification.

The installed runner now uses the actual packaged Tomcat context descriptor,
verified byte for byte, alongside the immutable WAR. Replacing that descriptor
with a minimal test context had omitted `suspendWrappedResponseAfterForward=false`
and produced empty HTTP 200 login responses despite successful application/TLS
startup. Sanitization stays enabled. A proper disposable CA/server chain, strict
certificate validation and the real desktop login form are used. Successful CLI
clinical checkpoints are printed separately from the complete browser result.

The action-instantiation contract distinguishes named Spring prototype beans
from legacy class-mapped actions. It follows the included Struts mappings and
exercises the real Struts 7.4 factory, checks independent instances and retains
the legacy no-argument constructor requirement. Global autowiring is unchanged.
Both contracts run early and in the strict coordinated inventory; the current
304-suite revision still requires its passing reports.

A proposed independent importer admin grant requires explicit approval after
automatic approval review rejected that persistent permission change. The
unpublished draft seed was removed and original permission defaults, manifests
and migration bindings were restored. The unchanged security-seed contract stays
in the coordinated inventory. No exemption, weakened security gate or successful
readiness claim substitutes for that unresolved decision. DCO attestation and
external quality checks also remain outstanding.

## Original baseline and integration

The implementation worktree starts at `release/2026.08` commit
`b96e367945befe66eb333fe19aa1630aa9d10647`, which includes the modern importer.
The feature branch includes the forward integration of `develop` commit
`848ca460b2d5779a45950ae1451311f1c9409f33`, followed by appointment-location commit
`497896230576b492284b1c29efa77f4c4a2409ea`. The original 117 merge conflicts and
17 later appointment-location conflicts were resolved
and the integrated importer/exporter baseline was packaged and tested at
`2026.09.0-alpha1-SNAPSHOT`. The preserved package and installed component evidence
below distinguish this completed integration from the remaining clinical export
qualification under the [release process](release-process.md).

The original checkout and its work remain intact. The separate `carlos-ctl`
worktree starts at `c330ae97683757da4aeff18f70db665c561de1cf`. Its export adapter
is implemented alongside the Java entry point. The real protected Java 25 capability probe and native-chart clinical export/download have passed in an isolated installed container. The remaining qualification gates are stated above.

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

The V1.0.83 migration introduces independent export run and event tables. Both are
restricted from Query by Example. Only their exact metadata names and the exact
`SELECT UNIX_TIMESTAMP(CURRENT_TIMESTAMP(6))` lease-clock expression are exempted
from clinical maintenance admission; joins/subqueries involving clinical tables remain
guarded. Export metadata receives the same strict UTF-8 session treatment as import
metadata, without an importer clinical-write capability. The unpublished ledger was renumbered above develop's appointment Location List (`V1.0.81`) and the native field baselines (`V1.0.82`); existing published migration bytes remain unchanged.

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
can remain available. The migration registers `_cds_export` and grants that independent permission to
the administrator role. An existing customized administrator grant or denial is
preserved on replay. Current `_admin` authority remains an alternative. Neither
permission enables admission or supplies a recipient/audit policy: those controls
must be configured separately.

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
Because CDS 5.2 allergies have no status element and other EMRs ignore CARLOS residuals, an
archived allergy (removed, replaced by an edit, or marked inactive) also states in its standard
`Notes` that it is inactive in the sending EMR and not a current allergy. A native "Severe"
reaction (severity 3) has no CDS 5.2 value and is not evidence of a life-threatening (`LT`)
reaction: the standard `Severity` is absent and `CARLOS.Allergy.Severity.v1` (`string`,
`Severe`) carries the native label. The CARLOS importer does not yet restore that label into the
native severity; it keeps the residual in the retained source.
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
values never enter capture. A third lookup (`LookupListItem`, Appointments scope) captures
the items of the list named `reasonCode` that the patient's current appointments reference,
active or not; `LookupList` is read only as that list-name filter. All lookup scopes follow
selected patient/category references.

The native appointment mapper accounts for every row and used lookup field. It requires
exact captured identities and an unambiguous full-code status definition. It preserves
literal times and derives only a positive same-day whole-minute duration; overnight,
subminute and daylight-saving ambiguity require review. Imported status never substitutes
for a changed or cleared current status. This category has no `ResidualInfo`. The writer's
actual bytes pass the pinned XSD and receiving parser checks.

What an appointment exports, and what stays in CARLOS:

| Native value | Export |
| --- | --- |
| date, start and end time | `AppointmentDate`, `AppointmentTime`, `Duration` (end is inclusive) |
| `status` | `AppointmentStatus`: the captured status definition's description |
| `provider_no` | `Provider`: public name and OHIP number |
| `reasonCode` and `reason` | `AppointmentPurpose`: what the day sheet shows, the trimmed reason-list label, a space and the trimmed reason (`appointmentprovideradminday.jsp`). When the code names no captured item (the add form's `-1` placeholder when no list exists, or an id outside the `reasonCode` list) or the label is blank, the schedule shows the reason alone and the exact reason is sent; `reasonCode` is then operational. A labelled purpose over 250 characters, or an item without a label, requires review. |
| `notes` | `AppointmentNotes` |
| `style`, `billing`, `location`, `resources`, `urgency`, `type`, `bookingSource` | Operational: scheduler display, booking site, billing workflow, the appointment template name and booking provenance have no CDS Appointments field. The template's reason and notes already reach the purpose and notes. |
| `name` | Operational when it is the current patient's name in a form CARLOS stores (`Last,First` from the add form; `Last, First` or `Last, First (alias)` from the edit form's patient search and the booking web service), or the same form of a name from the patient's own captured `demographicArchive` snapshot (a booking made before a rename). Any other text requires review. |
| `remarks` | Review when populated. The add and edit forms only write an empty value or keep the stored one; the SOAP schedule service and older data can fill it, and the patient's appointment history shows it. |
| `program_id`, `imported_status` | Unchanged review rules (program affiliation; retained status reconciliation). |
| storage audit and identity columns | Operational. |

The separate owning archive DAO captures every `appointmentArchive` version, including repeated
booking IDs, SQL NULL/empty values, large text and historical-only status/provider definitions.
CARLOS copies an appointment into the archive before every edit, status change, billing and
deletion, so ordinary charts have archive rows. Under maintainer decision 17 the edit history is
superseded: each archive row is read and every field receives an explicit operational
disposition, `SUPERSEDED_APPOINTMENT_VERSION` when the current appointment with the same number
is captured, otherwise `DELETED_OR_MOVED_APPOINTMENT_VERSION`. Archive rows are never exported,
never counted as bookings and never hold the patient. A deleted appointment is therefore not
exported, as the day sheet and the default appointment history do not show it. A deleted or
aliased imported appointment still requires retained-import review.

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

The additive V1.0.59.38 migration adds nullable field-witness documents to import units and
checkpoints. New native receipt capture records SQL nullness, byte length, SHA-256 and
actual ordered JDBC metadata in the same original unit transaction. The protocol-2 receipt
JSON and its digest remain unchanged. Original metadata hashes were verified across the
upgrade on all four owned ON/BC test databases, with every historical witness still NULL.
Pristine independent ON/BC reference databases validate the complete migration chain and
canonical schema contracts. The witness document is bounded to 8MiB and carries no native
plaintext values. Its per-column hashes are consistency evidence, not anonymization:
low-entropy values can be guessed. Treat witnesses, ledger database access and backups
as protected patient data. Both ledger tables remain restricted for reporting, and no
baseline is returned in an ordinary status/diagnostic response. Unit metadata pages use
an explicit scalar projection so their 200-row limit does not hydrate witness documents;
receipt validation loads a single bounded unit at a time. Its exact referenced-row set and reconstructed row digests must match
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
precision, and require explicit DIN authority. Ineligible and externally completed statuses,
provider, reminder and other native fields are carried as described above; duplicate keys,
conflicting aliases, unsupported archive states and stale imported date hints remain review cases.
They cannot be collapsed into a refusal boolean or silently dropped. The writer independently checks the pinned CT-020
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
calendar records, aliases, deletions of imported appointments and merged patients remain review
gates; native archive versions are superseded history (decision 17, above). A reason-list label
chosen in CARLOS for an imported appointment whose purpose was not edited requires review,
because the preserved imported purpose cannot carry the label the schedule shows. All original field IDs are accounted for and the last callback cannot
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

## Historical implementation and acceptance checklist

This checklist records the original acceptance boundaries. Current executed
evidence and remaining checks are recorded above and in `cds-export-progress.json`;
the historical component narratives below do not reset completed work.

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

The complete reviewed import/export test inventory is committed in `scripts/ci/cds-coordinated-test-classes.json`. To reproduce it, provide the three owned disposable test-database property files through `CDS_TEST_PROPERTIES`, `CDS_LEDGER_TEST_PROPERTIES` and `CDS_EXPORT_TEST_PROPERTIES` and an owned empty directory through `CDS_EXPORT_TEST_DOCUMENT_ROOT`, then run `python3 scripts/ci/check-cds-transfer.py`. It clears inherited tag filters and, after Maven, checks every suite's report the way the native tier does: a missing, skipped, rerun or empty suite makes it fail even when Maven succeeded. Optional `--offline`, `--settings` and `--maven-local-repository` select the build environment. The script provisions no databases and reads no configuration secrets into console output. All required database fixtures and dependencies must be available; a skipped suite is not full integration evidence.


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

The appointment capture includes develop's nullable `locationCode` on current and archive
rows. A populated location reference remains explicit review until its clinical location
and supporting carrier are qualified. For an older sealed appointment baseline, only the
known added SQL-NULL column can be excluded from the common-field comparison; all original
receipt bytes remain unchanged. Other schema changes or a populated added value still
require review.

Current-develop integration at `e635af1a715e074bc1917b43ce3d97f3412d8314`
passed the complete 4,592-check, 348-suite coordinated package run with zero failures,
errors or skips. Fresh ON/BC physical schemas and packaged migration bytes match the
contracts. The exact WAR (`9bb826afaf3eed06763b00ad0f3bb318b9ccc4770283b3bf53c21c50a1f3367a`)
passed 107 installed assertions after a genuine current-runtime import. The test environment
uses its own freshly generated account/key; existing protected configuration was not copied.

The later nullable-column fix also handles a legacy appointment without a stored field
baseline only when removing that one added SQL-NULL field reproduces the exact recorded
whole-row digest. An edited common field, populated location reference or unknown schema
cannot use that derivation. No importer metadata is backfilled or restamped. Seventy focused
appointment checks pass; the final package qualification is recorded separately.

The final nullable-location compatibility diff was packaged at `4b27881d5fe` and passed
144 checks across its 11 affected suites (zero failures, errors or skips). Its actual
WAR SHA-256 is `879ccf07960510fd9b51a125e8794b2b30aacfdbfbb27a29551d2992f8b9ed22`. The same packaged
application passed all 107 installed history assertions using the preserved genuine
current-develop import fixture; no component class overlays or historical metadata
rewrites were used. This is a focused verification after the preserved 4,592-check
full current-develop baseline, not an additional full-suite count. Clinical publication
and complete engine qualification remain outstanding.

The laboratory component now captures all 26 structured result columns together with
the complete patient HL7 message, information and routing projections. The owning
DAOs reject duplicate, dangling, missing or shared chart associations before payload
reads. SHA-256 result IDs remain strings through keyset paging and large-value reads.
The shared occurrence service binds unchanged native receipts to the sealed preparation
and complete retained fields, preserves blocked results under sensitive-lab authority,
and applies original shifts to collection, requisition and every reviewer date.
Native-only or changed HL7 requires explicit review. A DOC route, which the inbox writes for
every document assigned to a patient, is a document link owned by the Reports path and does not
affect laboratory export; an older MDS, CML or BCP route, or an unknown one, still holds the
patient. Native result text, notes, reference ranges and test names are read from the raw HL7
segment (verified against HAPI's parse field by field), so a separator a laboratory left
unescaped, as in `Hemolyzed & lipemic ^ repeat`, stays in the text instead of cutting it. HL7
escapes are decoded as CARLOS displays them: `\.br\` and `\.sp\` are line breaks, `\Xhh\` is
UTF-8 and highlighting or layout commands carry no text, while an escaped escape stays literal.
The continuation lines of a TX or FT narrative form one result. TestName is the laboratory's
name for the test, or the ordered test's name when the result has none (#3959); AccessionNumber
is the accession CARLOS stores and shows, with a different order number noted in the
laboratory information. That field now carries only laboratory facts (sub-ID, ordered test,
order number, the complete text of a long result); CARLOS provenance stays in the NewCategory
residual. A result with no flag is written as the dictionary's Unknown (`U`), and the HL7 flags
with no CDS equivalent, including HL7 `U` ("significant change up"), are written as plain text.
A numeric, date or coded value with unexpected components needs review. Focused verification passed
156 checks plus 13 real MariaDB/CLI-startup checks; final packaging and genuine
installed laboratory import/export qualification are pending. This component does
not qualify the remaining native laboratory catalogue or clinical publication.

The coordinated laboratory checkpoint at `b69bbdb5f2de` passed the full
4,658-check package run (353 requested suites; zero failures, errors or skips). Its
actual WAR SHA-256 is `f94c8df0780a25a431d7c05ee94de65d6808c99a7ee5b8cfbf6ae5680bf674a4`. A genuine ordinary
clinical import produced three structured results, one blocked result and two native
HL7 records. The exact packaged application passed 39 installed export assertions
plus seven genuine-import assertions, including independent native/source field closure,
XSD readback, all source temporal spellings, changed-HL7 review and final-callback
sensitive-permission revocation. No component class overlays were used, and native rows,
grants and original import ruleset metadata were restored or retained exactly.
The fixture initially contained an invalid reference-range choice; preflight correctly
rejected it before any native commitments or patient targets. The rejected run and its
input remain intact, and the separate corrected fixture passed independent XSD validation.
This verifies an occurrence component; complete native-source coverage, a clinical engine
and actual delivery/download qualification remain required.

Category-specific occurrence assemblers now share one immutable registry in the CLI
and web application context. Duplicate handlers and attempts to shadow the coordinated
demographic, history, allergy or medication paths fail at startup. The seven history
positions retain their shared authenticated source session. Original standard
`NewCategory` occurrences have a dedicated retained-source assembler: every original
field is accounted, the current patient association is checked independently, and
unrelated demographic edits do not replace supplemental content. Other native fields
are never repurposed into `NewCategory`. The focused registry/association and clinical
regression run passed 206 checks; packaged startup and genuine installed qualification
are pending for this diff.

Genuine NewCategory qualification exposed an incorrect alias refusal in demographic
projection evidence. The importer uses sole demographic receipts for the nonmutating
CareElements and NewCategory parent associations. The exporter now distinguishes only
these exact original roles, requiring the same run, patient and lineage, canonical native
unit key and ordinal, original dependency, sole demographic reference and matching
current witness. A second demographic projection or unknown/extra reference still
requires review. No demographic source values come from an association receipt.
The fix passed 94 coordinated checks, including 20 adverse/association checks; final
packaging and the same genuine NewCategory fixture rerun remain pending.

The final association/registry checkpoint at `58300bcff913` passed 480 affected
checks across 42 requested suites (zero failures, errors or skips), after the preserved
4,658-check full baseline. Its actual WAR SHA-256 is
`f1cd5a8972cb35709d9b47ff617884962327a98fcf8cdd9ee4460285c7e5f969`. The same original completed
NewCategory import passed 23 exact installed export assertions and six genuine-import
assertions, including complete original field/type closure, current demographic edits
without replacing supplemental values, independent XSD validation and final-callback
category permission revocation. Native data/grants were restored; historical metadata
was not rewritten, and no component class overlays were used.

The care observation component now captures complete patient-scoped measurements, extension metadata, deleted-observation evidence and exact applicable vocabulary definitions through their owning DAOs. All thirteen standard care child types bind their complete retained fields to the original sealed preparation and unchanged whole native receipts. Shared decimal interpretation preserves importer semantics; dates use original import options and precision, and oversized goal text remains streamed. Missing, changed, duplicate, foreign, unknown or deleted evidence requires review. The source component passed 80 focused checks and 11 actual MariaDB/CLI checks; semantic/importer regressions passed 147 checks and native-binding/preparation/registry checks passed 80 (overlapping runs). Coordinated packaging and a genuine installed care import/export proof remain pending at this source checkpoint.

The care checkpoint passed all 4,764 coordinated checks across 360 requested suites, full Javadoc and WAR packaging, with zero failures, errors or skips. The exact packaged WAR (`402b06511a23bb3f8c0997be5674365e57440eff90fd96a9b5d1ba99dd4b4734`) then passed 50 installed care assertions and nine genuine clinical import assertions, without application-class overlays or metadata restamping. Both original care parents, all fifteen native observations across thirteen care kinds, every retained/native field, 40,000-character and empty goals, original numeric/date spelling, changed-native review and final callback access revocation were checked. Native rows and fixture grants were restored. The first stopped fixture lacked the legacy POSK vocabulary definition; its zero-write evidence remains preserved. A separate genuine ordinary import after restoring that definition in the disposable database supplied the successful proof. Native-only, edited and deleted care still require review; this evidence does not qualify a whole clinical export profile.

Report occurrence assembly now binds original metadata, every temporal choice (including reviewer and OBR dates), complete embedded text/binary bytes and each referenced native file to its original sealed preparation. Owning document/link DAOs capture every physical column and reproduce the importer’s original composite receipts. A separate native file source streams bytes into encrypted capture within the same read-only patient snapshot; mapping reopens only authenticated export-owned bytes. No mutable document file is reopened during mapping. Shared or dangling ownership, unqualified program restrictions, separate reviewer history and database file-storage sidecars require explicit review before payload capture. Native-only or changed reports remain review cases.

Original embedded and referenced representations remain separate. FilePath is replaced with a generated relative package member only after exact native/source digest and byte-length checks; a caller without an explicit attachment sink receives an error. Partial attachments are discarded if assembly fails. Tests caught and fixed a trailing Base64 error being swallowed by the default InputStream bulk adapter. Source/binding regressions passed 120 focused checks, actual MariaDB/CLI projection checks passed 12, and real directory-handle file capture passed 21 checks under the production UID, including symlink/traversal, truncation/growth, replacement, permission and close-time races. These runs overlap other component evidence and do not yet establish installed report or whole-profile qualification.

The report checkpoint at `ca91968787b8ac1a8a855b447d48b55500a1a279` passed 4,848 coordinated checks across 365 requested suites, full Javadoc and WAR packaging, with zero failures, errors or skips; the separate 21 filesystem checks also passed under the production UID. The first full run exposed a test race: MariaDB can acknowledge KILL before releasing its advisory lock. The maintenance test now observes bounded actual release before asserting the independent admission-row drain, preserving every production fence and the original blocking assertions.

The subsequent genuine import completed with both original reports, 35 retained source fields and three native representations. Installed export correctly refused the previously unaccounted durable file-publication effects. Report assembly now validates every `cds_import_file` and `cds_import_file_reference` alongside document and ownership effects: exact variant filename, immutable digest/length/generation/location key, same native unit and reference identity, and complete counts with no duplicate or extra effects. Inherited original file ownership is retained. Capture byte-limit settings are now parsed only when a present native document needs them, preserving metadata recovery and empty-domain capture when active settings are invalid. These changes passed 149 focused checks; packaging and rerunning that same genuine import’s export remain pending.

Installed report verification also required original publication/control rows in the retained index. Report-selected indexing now includes both file metadata tables from the same authenticated source generation and requires those inputs explicitly; other category scopes retain their existing index recipe. Sealed, positive publication generations are accepted after immutable retirement/republication, instead of assuming every committed report came from generation one. The updated index/publication regressions passed 169 focused checks; coordinated packaging and the existing genuine fixture rerun remain pending for this diff.

The corrected report/index checkpoint passed 317 affected package checks across 20 requested suites with zero failures, errors or skips, following the full 4,848-check, 365-suite coordinated baseline and full Javadoc. Its exact WAR (`eba42c36451d91bccacc8d189ba446989af8efc024d2dd72c2586e372f12c77f`) passed 44 installed report assertions against the same genuine completed import, plus the original nine import assertions. Both reports, all three native representations, all 35 retained fields and all 105 document/link/file fields closed. Complete large UTF8 text, embedded binary, referenced file bytes, unchanged temporal spelling, changed native metadata/file review, an absent attachment sink and final callback access revocation were verified. A failed private attachment was physically removed. Original native rows/files/grants were restored and the original import ruleset remained unchanged. Application/helper deployment roots were immutable and no application classes were overlaid. The report component is qualified at this checkpoint; whole-source coverage, a concrete qualified clinical engine, publication/retrieval/retention and real clinical CLI/browser execution remain open.


Combined all-category qualification found an unresolved legacy alert carrier in the
otherwise complete demographic field ledger. Selected `demographiccust.cust3` now
receives an explicit absence decision only for SQL NULL or empty text. Populated
values, including whitespace, require review and withhold proposed XML. The exact
previously authenticated handoff must exist; an unselected alert projection is
unchanged. Forty affected demographic/closure checks passed. The same genuine
completed sixteen-category import remains preserved for exact-WAR rerunning.

Laboratory source capture now refuses selected-patient routes outside the supported
exact `HL7` type before counting or paging structured results. A chart containing
only legacy or unknown laboratory routes cannot appear empty and export silently.
Other patients' routes remain outside the scope. Sixty-nine affected checks passed,
including twenty actual MariaDB projection cases. These source-closure changes
still require coordinated packaging and do not qualify clinical publication.


Clinical job planning now refuses conflicting explicit import/export clinic zones,
including invalid appointment-zone settings, before capture or package creation.
This keeps appointment interpretation and supporting-file calendar calculations
on the same captured zone/rules identity. Twenty-five affected checks passed;
configuration remains lazy so metadata recovery is available independently.


Report-selected capture now includes the complete physical hospital-report patient-link
contract through its owning HRM DAO. Any patient-associated hospital report requires
explicit review until its report adapter is available. An inner join cannot hide a
dangling link, and numeric legacy spellings in the VARCHAR patient column are
conservatively refused. Both absence checks share the actual patient snapshot;
other patients' links stay outside that scope. Twenty-five affected real MariaDB,
CLI-startup and source-recipe checks passed. The initial incremental CLI run found
a stale derived V84 SQL resource; refreshing that build output from the committed
SQL restored validation without changing source migrations or database history.


A native chart without an applied demographic import parent now uses the existing
typed demographic, phone, patient-note, current-enrolment, personal-contact and
pharmacy mappers through the same coordinated occurrence service. Every selected
patient-owned row is matched to its independent captured witness before assembly.
The six public physician fields retain their existing authenticated lookup scope
and exact typed field closure; no private whole-provider row is read or invented. Native field
closure covers the entire captured row universe, and the registered patient ID
is used in actual XML. A retained demographic field without a proven parent,
a provider row that no demographic column names exactly, populated unsupported carriers
or multiple parents still require explicit review. The retained
source path and its strict source-field closure are unchanged.

Superseded demographic snapshots are audited history, not refused data. Every CARLOS save
of a patient writes a `demographicArchive` row and, keyed by the same archive id, one
`demographicExtArchive` row per edited extension key; a changed alert, care-team name or
patient note archives the previous `demographiccust` values in `demographiccustArchive`.
Migration guide 18.2 transfers the current snapshot. Archived roster columns are mapped into
`Enrolment` history; every other archived column, including previous names, health cards,
addresses and phone numbers, is decided `OPERATIONAL` under
`SUPERSEDED_DEMOGRAPHIC_SNAPSHOT_IN_APPROVED_AUDIT` and is not exported. A former
most-responsible physician named only by an archive row is accounted for the same way
(`SUPERSEDED_PRIMARY_PHYSICIAN_IN_APPROVED_AUDIT`). The extension and custom archive
tables carry the same ruling in the source catalogue (`OPERATIONAL`), so an edited patient
is no longer held because these rows exist. Earlier values stay available only through the
approved audit arrangement in operator-guide step 6; the package does not carry them.

Values that CARLOS's own forms write when nobody chooses anything are not patient facts and do
not hold a chart. Each keeps an explicit `OPERATIONAL` disposition, and its source value stays in
the protected capture: newsletter `Unknown` and country of origin `-1` ("Not set")
(`UI_PLACEHOLDER_NOT_A_PATIENT_FACT`); the empty `demographicExt` row that a Master Record save
writes for every blank listed key (`PROPERTY_KEY_OF_AN_EMPTY_EXTENSION`), on both the native and
the imported-chart path; `rxInteractionWarningLevel` `0` ("Not specified"); and consent to contact
`1` on contact and pharmacy links, which is the model and form default
(`DEFAULT_CONTACT_CONSENT_HAS_NO_CDS_CARRIER`). A value that someone chose and that has no CDS 5.2
home still requires review: another newsletter choice or country, a populated unsupported
extension, a warning level, or consent `0`, which is an instruction not to contact that person.

Two user-entered demographic dates are practice-administrative and have no CDS 5.2
Demographics element: "Date Joined" (`date_joined`) and the health card "EFF Date"
(`eff_date`; `HealthCard` carries only number, version, expiry and issuer). They are decided
`OPERATIONAL` under `ADMINISTRATIVE_NOT_EXPORTED` and never hold a chart. Previously the first
was silently dropped as operational identity and the second required review. Every package
ReadMe carries the fixed line `Practice-administrative demographic fields "date joined" and
"effective date" are not part of CDS 5.2 Demographics and are not exported.`, which the
supporting-file validator requires.

Where CDS 5.2 has an exact home for a value the CARLOS forms store, the mapper uses it. Title
`RT_HON` is `NamePrefix` `RtHon`. A spoken language is the ISO 639-2 code that
`spoken_languages_codes.properties` gives for the stored display name; the importer reverses that
code with the same table. Middle names longer than one 50-character name part, up to the column's
100, are written as their space-separated names in ordered `OtherName` GIV parts, which the
importer joins back with single spaces. An imported chart whose edited middle names would need
several parts is held for review. Two values still require review because no exact home exists:
administrative gender `X` (Intersex), since the schema's `O` (Other) is imported back as `O` and
would lose the distinction, and a postal code without a known Canadian or pinned US subdivision.

The demographic occurrence decides exactly the public provider rows that its own columns
name byte-for-byte (the current and every archived `provider_no` and `roster_enrolled_to`),
whichever categories are selected. A provider captured only for another selected category
is decided by that category's receipt, on both the native and the retained path. Sixty-nine affected
checks passed, including twelve native assembly/boundary cases, schema readback,
selected-note witness scope, substituted fields, registered-ID mismatch and shared
primary/enrolment physician uses. Clean packaging and genuine installed native
chart verification remain required for this addition.


The actual shared job service now passes combined installed reconciliation for all
sixteen categories from one genuine completed import in the exact packaged WAR
(`811521002bb6968e3a5a58702ebabc861fadb92f753a09c048344bfe9edce3bc`).
All source facts plus the independently checked native allergy archive state and
configured appointment offset matched. Eighteen schema-valid clinical mutations
were rejected, including archive-state and time-offset changes. The clean build
passed 836 checks across 55 suites and full Javadoc. The original input, native
fixture and import metadata remain preserved. The unqualified production profile
still refused publication, as required; this is a combined clinical package proof.

Installed native-chart verification caught a scope error in the new witness join:
public provider candidates intentionally have only six public fields and no whole
private-account receipt. Native assembly now retains their existing authenticated
lookup contract and exact typed field closure, while every patient-owned clinical
row still requires its exact independent witness. Fabricated whole-provider
receipts are refused. Twenty-one affected scope checks passed; the same native
fixture remains available for exact-package rerunning without import metadata.


The native demographic checkpoint (`132b6bd4aabf`) passed a clean 986-check,
65-suite package build and full Javadoc. Its exact WAR
(`10d31751d32f762367898f1621f33d773bcc59ba7e1a1cd0a340189da5020191`)
passed installed shared-job export of a fresh native chart with no import metadata.
All thirteen independently expected facts matched, including complete Unicode note
text and the registered physician/patient identities; four schema-valid clinical
mutations were rejected. The original native fixture is retained, temporary grants
and provider fields were restored, and no application classes were overlaid.
Publication remained visibly unavailable through the unqualified engine boundary.

Clinical capture has a separate whole-source coverage gate over all 517 table
names in the exact shipped ON/BC physical contracts (464 ON, 497 BC). Explicit
rulings identify mapped sources, reference dictionaries, operational metadata and
sources that must be empty until their adapter or exclusion is reviewed.
Unclassified presence conservatively refuses the request across the database;
column names never establish patient ownership or justify discarding a source.
Missing required reader beans cannot reduce the expected inventory. Unknown
tables, columns, types, primary keys, engines or filtered metadata visibility
require review. Schema-wide SELECT visibility is required to establish that no
unreadable clinical table exists. The schema and empty-source probes return no
clinical, credential or key values. `scripts/verify-cds-export-source-catalogue.py`
checks canonical schema hashes, explicit table membership and mapped-scope coverage.

The stock Rourke 2009 form uses Aria in ON and MyISAM in BC. A dedicated controlled
unpooled connection holds a fixed READ lock on that one legacy table for the
bounded patient capture transaction. The regular transactional sources retain
their repeatable-read snapshot. No native data or schema is changed. Acquisition,
connection identity, release, loss and timeout are checked; automatic reconnect
is refused. An independent deadline aborts the physical lock connection after the
configured capture timeout plus 30 seconds (150 seconds by default), so a stalled
capture cannot keep the legacy table locked indefinitely. Raising that limit also
extends the possible writer wait for this form. Failure
to acquire, maintain or release the lock withholds capture. Database permissions
must allow this legacy table READ lock. Metadata status/control recovery stays lazy.
Eighty-two affected checks passed, including 24 real MariaDB cases, actual writer
blocking/release, lost-connection refusal, independent deadline release and the
whole catalogue through the actual guarded CLI datasource. Clean packaging and
installed reruns of this source gate remain required.


### Clinical adapter and metadata recovery checkpoint

The complete source-catalogue checkpoint (`c2f54feb7394`) passed a clean package with
998 focused checks across 66 suites, zero failures, errors or skips, and full Javadoc.
The exact WAR SHA-256 is
`293d551b542454e3ee00f1b7414df19b847661dd2c249dcbf9e7db485d7642c5`.
Installed chart reruns are separate evidence and remain pending at this checkpoint.

`CdsExportClinicalEngine` supplies the concrete OntarioMD adapter to the coordinated
clinical job service. It is deliberately not registered in the default application
or CLI context until its installed publication and transport tests pass. PCON is
not advertised by this adapter. Registry compatibility now depends on installed
implementation identity and the explicit clinic zone, without parsing active work
budgets or resolving the application build version. Invalid zones receive a distinct
non-executable identity so metadata and retention recovery remain available; clinical
admission still requires the strict active plan. Twenty-four affected identity,
registry and job orchestration checks passed. This checkpoint does not qualify a
clinical profile or establish successful clinical publication.


### Coordinated workflow execution

`.github/workflows/cds-transfer.yml` partitions the entire reviewed coordinated
inventory into import, export and shared jobs on separate fresh runners. Each job
compiles once and runs its exact selectors against its own newly migrated import,
ledger and export schemas using the existing native-tier harness. The matrix does
not archive complete WARs or share mutable database fixtures. Missing, ambiguous,
duplicate, empty or skipped suite evidence refuses the gate. Only sanitized test
reports are uploaded. The original native importer workflow and its required cases
remain unchanged. Installed application publication and transport evidence is still
required separately; a workflow declaration does not establish a passing run.

### Exact installed CLI and authenticated browser workflow

`.github/workflows/cds-export-installed.yml` runs the paired installed export
on a separate fresh runner, avoiding the workspace storage limit. It builds
the exact pinned companion source as its actual Debian package and packages
the application once. A complete WAR entry manifest binds every installed
file before and after execution. Fixture helpers are compiled separately;
they never replace a production class or enter the browser/CLI classpath.

The owned environment contains a newly migrated MariaDB database, an ordinary
native chart with no import metadata, a separate test login and new test-only
recipient and TLS identities. Missing application/email keys are initialized
through their actual complete empty-census guards, without loss acknowledgement
or rotation. No existing runtime, accepted job, clinical fixture or key is reused.

The check invokes the installed `carlos-ctl cds-export` with root delegation,
observes the same durable job from independent processes, downloads its package
and refuses an existing destination unchanged. Full service-user Tomcat then
runs the untouched WAR. Chromium signs in through the normal login page, checks
GET and tokenless-POST refusal, submits the real form, requires HTTP 202 with
an incomplete accepted job, reloads that job, waits for the asynchronous worker
and downloads through the authorized form.

The test recipient decrypts both real downloads independently. A separate
oracle validates each patient XML against the pinned OntarioMD XSD and compares
fourteen explicit native facts. Four altered clinical outputs must remain
schema valid and must fail that comparison. Direct read-only checks require
two genuinely complete durable jobs, matching CLI/browser implementation
bindings, unchanged native chart values and no import metadata.

Run on a fresh owned CI host with the repository's Java25 build container,
the exact pinned CLI checkout and pinned Playwright installation:

```sh
python3 scripts/ci/run-cds-export-installed-ci.py \
  --cli-source target/cds-ctl-source \
  --playwright-module target/cds-browser-tools/node_modules/playwright
```

Only bounded counts, hashes, fixed failure codes and application stack locations
are published. Keys, passwords, browser state, clinical payloads and raw traces
are excluded. The workflow's existence is not a passing qualification result;
the exact candidate's successful CI evidence is required.

The first dedicated installed run at `7d7a662f` completed the actual CLI flow,
including real READY, independent status/submission recovery, exclusive delivery,
recipient decryption, fourteen expected native facts and four schema-valid mutants.
The combined workflow then stopped at its test TLS/network startup before browser
execution. That is a CLI checkpoint, not a passing browser qualification.

Native prescriptions use a separate mapper beside the strict retained-source reconciler.
Established dates, exact dose witnesses, supply, route, frequency, status and public prescriber
fields have standard destinations. The complete captured `drugs` row, including SQL NULL versus
empty text and IEEE dose witnesses, is also preserved in `CARLOS.Medication.Source.v1` under the
pinned medication `ResidualInfo`. Unstructured strength remains exact `DrugDescription` and
source context; it is never split into an invented amount/unit. An unlabelled native regional
identifier stays explicitly unqualified source context and is not asserted as a Health Canada
DIN. Native `OD` maps to CT-024 `QD`; native duration weeks/months use the prescription calculator's exact
7/30-day rules without multiplying refills. Unknown status and a false past-medication flag do
not establish current treatment. Unbound import-provenance markers require review.

`CARLOS.Medication.Archived.v1` carries the exact archive state. Because other vendors ignore
CARLOS extensions and an archived row keeps the status it was copied with (re-prescribing copies
"Active"), a natively archived prescription is exported as no longer current: re-prescribed,
dose-changed or long-term-toggled rows as `Obsolete`, deleted rows and prescribing errors as
`Nullified`, every discontinuation reason as `Aborted`, each with `PastMedications` Y; the stored
status and reason stay in the source residual. The Rx page's as-needed choices `PRNL`/`PRNS` are
the CDS treatment types `PRN Long-term`/`PRN Short-term`, and its as-needed box with an ordinary
frequency is written as that frequency followed by `PRN` (for example `BID PRN`). The
receiving importer reads only that exact boolean marker, refuses malformed/duplicate values,
and supplements existing historical archiving. The optional prepared flag is defaulted only
after the original payload seal has been verified. Unknown residual context remains protected
source, with the receiving importer's existing clinical-reconciliation diagnostics; this is
not a claim that another vendor will interpret CARLOS extensions.

The owning `PrescriptionDao` also captures printed script text, comments and reprint history,
including scripts without remaining drug rows. Independent inventory accounts for every
parent as `CARLOS.NativePrescription.v1` supporting clinical data. Select `NewCategory` with
medications when such parents exist; excluding it refuses publication. Every nonzero drug
script reference must resolve to a captured parent belonging to the same patient. Source
signature identifiers remain non-executable context and actual signature payloads retain their
separate source-catalogue gate. The complete-table source descriptor is generated with
`python scripts/generate-cds-export-source-schema.py --table prescription`; both pinned physical
profiles must agree, and the table ruling/ownership/mapping remain explicit review inputs.

Database-selected native prescriptions also capture an immutable public DrugRef reference. A
short scalar discovery transaction closes before any network call. Only the DIN and the public
lookup option enter the existing native XML-RPC protocol; patient identity, names, dose, SIG and
chart data are never sent. Capture compares the prepared scalar identities/code/catalogue keys
with the actual patient snapshot. Changed inputs, unknown codes and malformed reference products
refuse qualification; network/protocol outages remain distinct I/O failures. Custom prescriptions
with no native catalogue selection keep unqualified identifiers as source context.

Representative DINs may legitimately differ from the authored medication name and native
strength text (guide10.1). The exporter preserves those source values and, as guide10.5 requires,
exports the first database ingredient's typed strength amount/unit for a database-selected DIN,
including combination products. It retains the complete ordered ingredient reference separately;
it never splits free text or replaces an authored name with a catalogue product name. The actual
installed fixture includes a representative-DIN combination product and verifies the real
XML-RPC client against an owned loopback public dictionary outside the immutable WAR.
