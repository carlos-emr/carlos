# CDS import documentation

This is the entry point for the CDS import system targeting `release/2026.08`. Browser imports and the paired `carlos-ctl` command use the same CARLOS engine, clinical models and durable recovery contracts. Import admission is disabled by default. The CDS 5.2 exporter ships in the same release with its own admission setting, also off by default; see the [export operator guide](cds-export-operator-guide.md).

These documents explain the implementation and operating contracts. Release acceptance additionally requires the executed evidence for the final candidate, recorded on the release pull request.

The [export implementation contract](cds-export.md) describes the sibling exporter.
Enabling export never enables import, and import completion does not depend on it.
Packages the exporter writes are ordinary CDS 5.2 files to this importer; the CARLOS
residual markers it adds are listed under
[Recognised CARLOS residual markers](#recognised-carlos-residual-markers).

## Start by role

| Reader | Read first | Then use |
| --- | --- | --- |
| Import operator | [Operator guide](cds-import-operator-guide.md) | [Web workflow](cds-import-web.md), [recovery runbook](cds-import-recovery-runbook.md) |
| Clinical reconciler | [Clinical meaning and native writes](cds-clinical-import-contract.md) | [Prevention dates](cds-prevention-date-contract.md), [legacy reader safeguards](cds-legacy-projection-contract.md), protected per-run reports |
| Administrator | [Maintenance admission](cds-maintenance-admission.md) | Operator guide, recovery runbook, [installed compatibility](cds-import-installed-compatibility.md) |
| Developer or reviewer | [Architecture and class responsibilities](cds-import-architecture.md) | [Ledger and transactions](cds-import-ledger.md), clinical contract |
| Release verifier | [Resource verification](cds-import-performance.md) | [CLI gate](cds-import-cli-verification.md), [browser lifecycle gate](cds-import-lifecycle-verification.md), [WAF policy and gate](cds-import-waf-verification.md), [installed compatibility](cds-import-installed-compatibility.md) |

## Documentation map

| Subject | Authoritative document |
| --- | --- |
| Architecture, MVC, dependency direction, class map, lifecycle and persistence choices | [Architecture](cds-import-architecture.md) |
| Uploading, progress, bookmarkable jobs, source views, review and protected downloads | [Web workflow](cds-import-web.md) |
| Deployment preparation, rehearsal, reconciliation and operator exit meanings | [Operator guide](cds-import-operator-guide.md) |
| Server defaults, source registration, delegation and capacities | [Configuration reference](cds-import-configuration.md) |
| Failure diagnosis, safe continuation, unknown outcomes and retained evidence | [Recovery runbook](cds-import-recovery-runbook.md) |
| Ownership, epochs, seals, checkpoints, review publication and finalization | [Ledger contract](cds-import-ledger.md) |
| Writer exclusion, physical connection guard, startup and maintenance release | [Maintenance admission](cds-maintenance-admission.md) |
| Source normalization, existing DAO composition and historical clinical semantics | [Clinical contract](cds-clinical-import-contract.md) |
| Unknown and partial prevention dates, storage anchors and downstream meaning | [Prevention date contract](cds-prevention-date-contract.md) |
| REST/SOAP, legacy readers and refusal of unsafe projections | [Legacy projection contract](cds-legacy-projection-contract.md) |
| Field, choice and compositor coverage | [Specification crosswalk](cds-spec-crosswalk.md), [machine-readable crosswalk](cds-spec-crosswalk.json), [review pins](cds-spec-review.json) |
| Physical JDBC timeout and cancellation behavior | [JDBC timeout verification](cds-import-jdbc-timeouts.md) |
| Actual host package, delegation, interruption and command compatibility | [Installed CLI verification](cds-import-cli-verification.md) |
| Container replacement and resource isolation | [Container recovery driver](cds-container-recovery-driver.md) |
| Genuine old/new browser, API, runtime and schema compatibility | [Installed compatibility](cds-import-installed-compatibility.md) |
| Large inputs, heap/RSS, SQL counts, disk reserve and actual ENOSPC | [Resource verification](cds-import-performance.md) |
| Session expiry, proxy failure, tab closure and same-run recovery | [Browser lifecycle verification](cds-import-lifecycle-verification.md) |
| Actual Nginx/ModSecurity body and route policy | [WAF verification](cds-import-waf-verification.md) |

## What completion means

Transfer, durable acceptance, clinical completion, report delivery, cleanup and maintenance release are separate facts. A successful HTTP request or a displayed upload percentage does not establish import completion. A complete clinical count does not prove reports were delivered. A status command can succeed while the import it describes remains incomplete.

Use the durable run ID and all four protected report kinds. Reconcile source patients, destination copies, clinical occurrences, helper units, committed units, verified prior work and unfinished work. Preserve unknown totals until discovery and sealing prove them. The operator guide and ledger contract define these terms and the supported controls.

Source values that require review remain attributable and visible. Original XML, precision, restrictions and provenance are not replaced by a convenient native default. Acknowledging a message does not resolve a blocking clinical interpretation.

## Recognised CARLOS residual markers

The coordinated [export implementation](cds-export.md) writes versioned `ResidualInfo` fields
where the pinned schema has no equivalent standard field. The importer recognises only these
exact names and types listed below as interpreted, honours effective corrections, and blocks an
invalid or duplicate known marker instead of falling back to a default. Every other residual name, including an unknown
`CARLOS.*` name, stays protected source-only data. Unmarked records keep their established
behaviour, and old prepared payload bytes and digests are preserved (optional decoder defaults
are added only after seal verification).

| Marker | Type | Category | Import behaviour |
| --- | --- | --- | --- |
| `CARLOS.Allergy.Archived.v1` | boolean | Allergies | Restored as a native archived allergy only when `cds.import.mapping.restoreArchivedAllergies=true` (decision 9, default off). By default the allergy is imported active with warning `CDS_ALLERGY_ARCHIVED_SOURCE_MARKER`; when restored, `CDS_ALLERGY_ARCHIVED_SOURCE_RESTORED`. The decision is part of the run's ruleset and of each prepared allergy. |
| `CARLOS.Allergy.AgeOfOnset.v1` | string | Allergies | Exact native age text (at most 4 characters). |
| `CARLOS.Allergy.OnsetOfReaction.v1` | string | Allergies | Unknown, Immediate, Gradual or Slow, as the native onset code. |
| `CARLOS.Allergy.Atc.v1` | string | Allergies | Native ATC class (`TYPECODE` 8); refused together with a standard `Code`. |
| `CARLOS.Allergy.Severity.v1` | string | Allergies | `Severe` restores native severity 3; refused together with a standard `Severity`. |
| `CARLOS.Allergy.Reaction.v1` | string (encoded) | Allergies | Exact native reaction longer than the standard `Reaction` (120 characters), JSON-encoded so token normalization cannot alter it; restored as the native reaction. Refused together with a standard `Reaction`, or when malformed or empty. The record's `Notes` repeat the text for other receivers and stay in the retained source. |
| `CARLOS.Medication.Archived.v1` | boolean | Medications | Imported archived with warning `CDS_MEDICATION_VENDOR_ARCHIVE_CONTEXT`; clinical status/currentness stay independent. |
| `CARLOS.History.Archived.v1` | boolean | CPP histories | Imported archived with warning `CDS_HISTORY_ARCHIVE_STATE_VENDOR_EXTENSION`. |
| `CARLOS.History.Narrative.v1` | string (encoded) | CPP histories | Exact native narrative when no standard description exists (PersonalHistory always; long items of the other sections). |
| `CARLOS.History.PersonalNarrative.v1` | string | PersonalHistory | Earlier raw narrative carrier, still read. |
| `CARLOS.Medication.Source.v1` | string (JSON) | Medications | Not interpreted: retained as protected source data like any other residual. |
| `CARLOS.Prevention.Source.v1` | string (JSON) | Immunizations, PastHealth | Not interpreted: retained as protected source data like any other residual. |

The two `Source.v1` carriers hold a complete native row (SQL NULL kept distinct from empty)
for audit and for a future, separately reviewed restoration. They are not projected because
their JSON is not schema-validated clinical content and would bypass the standard-field
normalization and review rules: a CARLOS-to-CARLOS move restores what the standard elements
carry. What they alone carry is visible in the protected source view of the import: for a
medication, the native quantity unit, as-needed flags and the exact IEEE dose witnesses
(a week duration arrives as its exact day count, months and years with their unit); for a
vaccine, the administering provider's free-text name, the reminder date and the never-remind
flag. Restoring any of them natively is a maintainer decision recorded in the release pull
request.

Export clinical qualification remains separate from these shared transfer rules.

## Deployment companions

The CARLOS application owns the shared engine, web workflow, migrations and clinical behavior. [carlos-ctl](https://github.com/carlos-emr/carlos-ctl/pull/15) supplies the verified host/container command and delegation boundary. [carlos-podman](https://github.com/carlos-emr/carlos-podman/pull/22) supplies the coordinated container integration. Compatible packages, runtime classes, configuration and implementation fingerprints must be verified together. Publishing a companion release asset follows the normal maintainer release process.

## Keeping these documents accurate

Update the relevant contract when changing a public route, command, configuration property, DTO field, state transition, DAO operation, native representation, resource bound or recovery decision. Update this index when adding a document. Check relative links and examples against the implementation.

Keep executed candidate-specific results on the release pull request with their exact source/artifact identities. Preserve failures, skips, interrupted attempts and pending gates explicitly. A previous candidate's result or a source review does not certify a successor or establish OntarioMD certification.
