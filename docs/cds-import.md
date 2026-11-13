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
| `CARLOS.Medication.Source.v1` | string (JSON) | Medications | Restores the native quantity unit and as-needed flag (#6198) when the standard Quantity and Frequency/TreatmentType agree with it; refused when malformed, duplicated or contradicted. The rest stays protected source. |
| `CARLOS.Prevention.Source.v1` | string (JSON) | Immunizations | Restores the free-text "Other" administering provider and the reminder date (#6198) when the carrier's status matches RefusedFlag; refused when malformed or duplicated. The rest stays protected source. |
| `CARLOS.Prevention.Source.v1` | string (JSON) | PastHealth | Not interpreted: retained as protected source data like any other residual. |

The two `Source.v1` carriers hold a complete native row (SQL NULL kept distinct from empty)
as one JSON object, every space escaped so `xs:token` normalization cannot alter it. The
importer reads them strictly (`CdsSourceContextParser`: `"version":1`, no duplicate member,
nothing after the document, at most 2^24 characters) and restores only the native values CDS
has no element for, and only when the standard elements the exporter wrote from the same row
agree with the carrier. The carrier itself, and every value it alone carries, stays unchanged in
the import's retained source fields.

- **Medication quantity unit.** On the Rx page `unitName` is the quantity's unit. A carried
  `unitName` that is not empty and differs from `unit` (the exporter's own reading) is restored
  to `drugs.unitName` with the carried amount in `drugs.quantity`, only when the standard
  Quantity is exactly that amount, a space and the trimmed unit (`100 mL`). A bare, different or
  absent Quantity is contradictory: `CDS_MEDICATION_NATIVE_FIELD_REQUIRES_REVIEW` (path
  `Quantity`). Without a quantity unit the dose unit is copied to `unitName`, as before.
- **Medication as-needed flag.** The carried `prn` (0 or 1) is restored to `drugs.prn` exactly,
  including the `BID PRN` case the frequency alone cannot show. The exporter always shows a set
  flag in Frequency (`PRN`, `BID PRN`) or a PRN TreatmentType, so a set flag beside elements that
  name no PRN is `CDS_MEDICATION_NATIVE_FIELD_REQUIRES_REVIEW` (path `Frequency`); an unset flag
  beside a PRN frequency (the box left unticked) is restored as recorded. Without a carrier the
  flag is derived from Frequency and TreatmentType, as before.
- **Vaccine provider.** Only the prevention form's "Other" provider (`provider_no` `-1`) is a
  free-text name: its one non-empty `providerName` extension is restored as `provider_no` `-1`
  and a `providerName` extension, which is how the form saves it and what the chart shows. A
  CARLOS provider number belongs to the sender and is never restored; the import still
  attributes no local clinician. Two typed names require review.
- **Vaccine reminder date.** `next_date` is restored to `preventions.next_date`, shifted by an
  authorized synthetic date shift like every other clinical date. MySQL's zero date records no
  reminder. The carrier describes its record: its native status must be the RefusedFlag the
  exporter wrote (given and completed externally `N`, refused and ineligible `Y`), else
  `CDS_IMMUNIZATION_NATIVE_FIELD_REQUIRES_REVIEW` (path `RefusedFlag`).

A malformed, oversized or duplicated carrier, a `prn` other than 0 or 1, an unknown status or an
invalid date is `CDS_MEDICATION_NATIVE_STATE_REQUIRES_REVIEW` or
`CDS_IMMUNIZATION_NATIVE_STATE_REQUIRES_REVIEW` (path `ResidualInfo`); nothing is guessed. The
restored values are part of the sealed preparation (`CdsMedicationDto.quantityUnit`/`asNeeded`,
`CdsImmunizationDto.providerName`/`reminderDate`), omitted when absent so earlier payloads keep
their bytes and seal, and defaulted after seal verification. The import receipt's native-baseline
witness records the rows as written. Re-exporting the unedited imported chart writes the
original elements beside the retained original carrier (the reconcilers account the restored
columns to it), so a further CARLOS import restores the same values; a restored value changed in
CARLOS since the import holds the patient (`RESTORED_SOURCE_CONTEXT_CHANGED_REQUIRES_REVIEW`),
because the retained carrier would restate the old value.

What the carriers alone still carry, and the importer does not restore, stays visible in the
protected source view of the import: for a medication, the exact IEEE dose witnesses and the
other native columns (a week duration arrives as its exact day count, months and years with
their unit); for a vaccine, the never-remind flag, the CVC concept, flu risk factors and the
other native extensions.

Export clinical qualification remains separate from these shared transfer rules.

## Deployment companions

The CARLOS application owns the shared engine, web workflow, migrations and clinical behavior. [carlos-ctl](https://github.com/carlos-emr/carlos-ctl/pull/15) supplies the verified host/container command and delegation boundary. [carlos-podman](https://github.com/carlos-emr/carlos-podman/pull/22) supplies the coordinated container integration. Compatible packages, runtime classes, configuration and implementation fingerprints must be verified together. Publishing a companion release asset follows the normal maintainer release process.

## Keeping these documents accurate

Update the relevant contract when changing a public route, command, configuration property, DTO field, state transition, DAO operation, native representation, resource bound or recovery decision. Update this index when adding a document. Check relative links and examples against the implementation.

Keep executed candidate-specific results on the release pull request with their exact source/artifact identities. Preserve failures, skips, interrupted attempts and pending gates explicitly. A previous candidate's result or a source review does not certify a successor or establish OntarioMD certification.
