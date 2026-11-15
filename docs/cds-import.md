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
| Server defaults, the free-disk reserve and stall window (no size or duration limits), XML guards, retired settings, source registration and delegation | [Configuration reference](cds-import-configuration.md) |
| Every import bound and its class (pacing, external or integrity limit, or removed) | [Configuration reference](cds-import-configuration.md#import-bounds-and-their-classes) |
| Failure diagnosis, safe continuation, unknown outcomes and retained evidence | [Recovery runbook](cds-import-recovery-runbook.md) |
| Closing a stopped written import that cannot complete (audited abandon) | [Operator guide](cds-import-operator-guide.md#abandoning-an-import-that-cannot-complete) |
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
| `CARLOS.Allergy.Source.v1` | string (JSON) | Allergies | Not interpreted: the complete native row of an allergy with a value CDS has no element for (an OSCAR 19 recording provider or First Databank identifier, an unlisted code, a date without established precision, text holding a character XML cannot carry). Retained as protected source data; read only to restore the exact text of an element shown with U+FFFD (see "Exact text a CARLOS sender carries"). |
| `CARLOS.Medication.CurrentSource.v1` | string (JSON) | Medications | Not interpreted: the complete current row of an imported prescription the sending CARLOS changed after its own import (deleted, discontinued, hidden from the CPP). Retained as protected source data; the standard PrescriptionStatus, PastMedications and `CARLOS.Medication.Archived.v1` already say that it is no longer current. Read only to restore the exact text of an element shown with U+FFFD (see "Exact text a CARLOS sender carries"). |
| `CARLOS.PatientAlert.v1` | string (JSON) | AlertsAndSpecialNeeds | The exact patient Alert (`{"version":1,"alert":"..."}`), written by a CARLOS sender beside an AlertDescription that shows U+FFFD for each character XML cannot carry (a control character pasted into the Alert box). The carrier is proven against its element first: AlertDescription must be the carried text with those characters as U+FFFD, by the one rule both sides use (`CdsNativeExtensionContract.xmlElementText`). Proven, the Reminders note is filed with the exact text. Malformed, larger than the native note bound, or not matching its element, it is not used: the element is imported as any sender's and the patient's report names `CDS_PATIENT_ALERT_CARRIER_NOT_APPLIED`. In another history category the record is refused (`CDS_HISTORY_NATIVE_STATE_REQUIRES_REVIEW`). |
| `CARLOS.Medication.Archived.v1` | boolean | Medications | Imported archived with warning `CDS_MEDICATION_VENDOR_ARCHIVE_CONTEXT`; clinical status/currentness stay independent. |
| `CARLOS.History.Archived.v1` | boolean | CPP histories | Imported archived with warning `CDS_HISTORY_ARCHIVE_STATE_VENDOR_EXTENSION`. |
| `CARLOS.History.Narrative.v1` | string (encoded) | CPP histories | Exact native narrative when no standard description exists (PersonalHistory always; long items of the other sections). |
| `CARLOS.History.PersonalNarrative.v1` | string | PersonalHistory | Earlier raw narrative carrier, still read. |
| `CARLOS.History.NarrativeText.v1` | string | PersonalHistory | Not interpreted: the exporter's whitespace-collapsed copy of the narrative (and Social History dates) for other EMRs. The narrative is restored from `CARLOS.History.Narrative.v1` only; the copy is retained as protected source data and never imported as a second value. |
| `CARLOS.Medication.Source.v1` | string (JSON) | Medications | Restores the native quantity unit and as-needed flag (#6198) when the standard Quantity and Frequency/TreatmentType agree with it, and the stored strength text of a record with no Strength when DrugDescription proves it; refused when malformed, duplicated or contradicted. The rest stays protected source. |
| `CARLOS.Prevention.Source.v1` | string (JSON) | Immunizations | Restores the free-text "Other" administering provider, the reminder date (#6198) and the sender's own prevention type when the carrier's status matches RefusedFlag; refused when malformed or duplicated. The rest stays protected source. |
| `CARLOS.Prevention.Source.v1` | string (JSON) | PastHealth | Not interpreted: retained as protected source data like any other residual. |
| `CARLOS.Appointment.Source.v1` | string (JSON) | NewCategory `CARLOS.NativeAppointment.v1` | Read for the Appointments element it names (see "CARLOS appointment source record"): restores the exact stored end, the complete notes and the scheduler context once the record proves itself; otherwise reported and not used. Always also retained as protected source data. |
| `CARLOS.Issues.v1` | string (JSON) | NewCategory `CARLOS.NativeIssues.v1` | One note-free system marker per record. Restores the patient issue's flags, type and change time; binds its code to a unique compatible receiving catalogue entry and maps a restricted program to the selected receiving program. No note is created. Malformed carriers or unresolved mappings refuse the patient explicitly. `CARLOS.NativeIssues.Source.v1` preserves earlier payloads as source only and never restores a marker. |

The two `Source.v1` carriers hold a complete native row (SQL NULL kept distinct from empty)
as one JSON object, every space escaped so `xs:token` normalization cannot alter it. The
importer reads them strictly and as a stream (`CdsSourceContextParser`, over
`CdsFieldParser.openToken`): one object with `"version":1`, nesting of at most 16, no repeated
member among those it reads, nothing after the document. A carrier has no length limit, on either
side: the reader keeps only the members it restores and skips every other value where it stands,
whatever its size, so a row with megabytes of text costs no more memory than a small one. What it
keeps is bounded by what can be restored (a kept text can be no longer than an appointment's
complete notes, the longest text restored from a carrier; extension rows are visited one at a
time). The 16,777,216-character bound both sides once applied is gone. The importer restores only the native values CDS
has no element for, and only when the standard elements the exporter wrote from the same row
agree with the carrier. The carrier itself, and every value it alone carries, stays unchanged in
the import's retained source fields.

- **Medication quantity unit.** On the Rx page `unitName` is the quantity's unit. A carried
  `unitName` that is not empty and differs from `unit` (the exporter's own reading) is restored
  to `drugs.unitName` with the carried amount in `drugs.quantity`, only when the standard
  Quantity is exactly that amount, a space and the trimmed unit (`100 mL`), as the sender writes
  it: a unit holding a character XML cannot carry shows U+FFFD in its place (`100 m\uFFFDL`, by
  `isElementTextOf`, see "Exact text a CARLOS sender carries"), and the carried unit is restored
  exactly. A bare, different or absent Quantity is contradictory:
  `CDS_MEDICATION_NATIVE_FIELD_REQUIRES_REVIEW` (path `Quantity`). Without a quantity unit the
  dose unit is copied to `unitName`, as before.
- **Medication as-needed flag.** The carried `prn` (0 or 1) is restored to `drugs.prn` exactly,
  including the `BID PRN` case the frequency alone cannot show. The exporter always shows a set
  flag in Frequency (`PRN`, `BID PRN`) or a PRN TreatmentType, so a set flag beside elements that
  name no PRN is `CDS_MEDICATION_NATIVE_FIELD_REQUIRES_REVIEW` (path `Frequency`); an unset flag
  beside a PRN frequency (the box left unticked) is restored as recorded. Without a carrier the
  flag is derived from Frequency and TreatmentType, as before.
- **Medication strength text.** A CARLOS sender writes Strength only from the drug
  database's first ingredient amount, when it is a positive decimal number that Strength can
  hold; a custom drug, or a product whose first amount is zero, blank, a range or text, has no
  Strength, and its stored strength text (`drugs.dosage`) is its DrugDescription. For a record
  with no Strength, the carried row's `dosage` (the last `CARLOS.Medication.CurrentSource.v1`,
  else `CARLOS.Medication.Source.v1`) is restored to `drugs.dosage` exactly, and named in the
  generated outline, when DrugDescription is that text by the exporter's element rule
  (`isElementTextOf`), or when DrugDescription is absent and the text is longer than its 2,000
  characters (up to the 32,768 characters the importer reads a strength value with). The
  prescription then counts as having its strength (`cds_projection_incomplete` is not set for a
  missing Strength). A carrier holding no strength text restores nothing; one holding another
  text, or an unreadable one, restores nothing and the report names
  `CDS_MEDICATION_EXACT_TEXT_NOT_APPLIED` (path `DrugDescription`). A record with a Strength
  element keeps the Strength as its stored strength, as from any sender. The restored text is
  part of the sealed preparation (`CdsMedicationDto.strengthText`, omitted when absent and
  defaulted after seal verification), and the retained reconciler projects it: unchanged it is
  the retained carrier's value, changed in place the sender's carrier is superseded and
  DrugDescription states the current text.
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
- **Vaccine type.** The sender's `prevention_type` is stored as the vaccine's type in place of the
  configuration's mapping of ImmunizationType. A CARLOS sender exports some vaccines without
  ImmunizationType (a type outside CT-020 such as COVID-19, Pneu-C-20 or RZV, an Rh injection, a
  catalogue vaccine, a type its prevention file does not define), and the configuration would
  file each under the catch-all `OtherA`. An empty type, or one longer than the receiving column
  (255 characters, the sender's own column), restores nothing and the configuration maps the
  element as for any sender; a carrier without the column (an earlier CARLOS) restores no type.
  On re-export the restored type is proved from the sealed preparation itself, whatever the
  receiving configuration declares, so an unchanged vaccine is exported from its retained
  elements. An entry the sender named with a fixed label because nothing else named it
  (`Unnamed immunization`, `Immunization (type in Notes)`, `Other immunization or injection`,
  `Rh immune globulin`, or its type) keeps that label as its name, and a type the sender moved to
  Notes (`Type: ...`) stays in the comments.

A malformed or duplicated carrier, a restorable value longer than any column it could be restored
to, a `prn` other than 0 or 1, an unknown status or an
invalid date is `CDS_MEDICATION_NATIVE_STATE_REQUIRES_REVIEW` or
`CDS_IMMUNIZATION_NATIVE_STATE_REQUIRES_REVIEW` (path `ResidualInfo`); nothing is guessed. The
restored values are part of the sealed preparation (`CdsMedicationDto.quantityUnit`/`asNeeded`,
`CdsImmunizationDto.providerName`/`reminderDate`/`nativeType`), omitted when absent so earlier payloads keep
their bytes and seal, and defaulted after seal verification. The import receipt's native-baseline
witness records the rows as written. Re-exporting the unedited imported chart writes the
original elements beside the retained original carrier (the reconcilers account the restored
columns to it), so a further CARLOS import restores the same values. A restored medication value
changed in CARLOS since the import is exported as current: the sender's
`CARLOS.Medication.Source.v1` is removed and the current row travels in
`CARLOS.Medication.CurrentSource.v1`. A restored vaccine value changed since the import still
holds the patient (`RESTORED_SOURCE_CONTEXT_CHANGED_REQUIRES_REVIEW`), because the retained
carrier would restate the old value.

What the carriers alone still carry, and the importer does not restore, stays visible in the
protected source view of the import: for a medication, the exact IEEE dose witnesses and the
other native columns (a week duration arrives as its exact day count, months and years with
their unit); for a vaccine, the never-remind flag, the CVC concept, flu risk factors and the
other native extensions.

### Carriers the importer keeps as source only

These CARLOS carriers hold exact values the standard element beside them could not, and the
importer does not read them back into the chart. They stay in the protected source view. Where
that means a transfer between two CARLOS installations changes what the receiving chart shows,
it is said here.

| Carrier | What it holds beside the element | On import |
| --- | --- | --- |
| `CARLOS.History.Narrative.v1` | A CPP narrative too long for its description element (which is then left out), or a PersonalHistory narrative | Restored (the note body), as before. |
| `CARLOS.NativeHistory.v1` `exactNarrative` / `narrative`, `patientIssue.issue.description` | The exact body of a CPP item or note that holds a character XML cannot carry, or the exact description of a diagnosis sent on its own; the element and the narrative carrier show U+FFFD | Restored, see "Exact text a CARLOS sender carries" below. |
| `CARLOS.NativeHistory.v1` `sections` | Every CPP section tag of an item the sender's chart files under two or more CPP sections; the item itself is written once, under the first | Restored (the item's links), see "CPP items filed under several sections" below. |
| `CARLOS.NativeHistory.v1` `importedNotes`, `revisions`, extension facts | Imported Notes left out of Notes on an edited imported item; every revision | Source only. No native value is changed: Notes was never a native column. |
| `CARLOS.NativeDemographic.v1` (`CARLOS.Demographic.v1`) `note` | A patient note longer than NoteAboutPatient (64,000), with a character XML cannot carry, or in a legacy frame | Restored, see "Exact text a CARLOS sender carries" below. A legacy frame needs nothing: the element already holds the note as the sender's chart shows it. |
| `CARLOS.NativeDemographic.v1` `demographic` (names, title, birth columns, sex, status, status date, health card, phones, chart number, e-mail, SIN, languages, address parts) and `properties` | The stored value of a Demographics element the sender could not write exactly or that this importer would store in another form, and the `demographicExt` rows | Restored, see "CARLOS demographic values" below. |
| `CARLOS.NativeDemographic.v1` other `demographic` columns, `roster`, `careTeam`, `related` | Demographic, enrolment, contact, health care team and pharmacy values with no element (preferred name, pronoun, Date Joined, the nurse, a second pharmacy...) | Source only; the standard elements are imported as from any sender. |
| `CARLOS.Medication.Source.v1`, `CARLOS.Medication.CurrentSource.v1`, `CARLOS.Allergy.Source.v1`, `CARLOS.Prevention.Source.v1` | The complete native row, including text an element shows with U+FFFD | The fields listed above are restored (quantity unit, as-needed, strength text, "Other" provider, reminder date, prevention type), and the exact text of an element shown with U+FFFD (see "Exact text a CARLOS sender carries" below). The other columns stay source only. |
| `CARLOS.Laboratory.v1`, `CARLOS.NativeLaboratory.v1`, `CARLOS.Observation.v1`, `CARLOS.NativeObservation.v1`, `CARLOS.Prescription.v1`, `CARLOS.ImportedLaboratoryReview.v1` | The stored message, measurement, script or comment. For a message that names several patients, the sender's part only (its MSH segment and the PID groups of the exported patient, with a `part` entry; owner decision 2026-11-14), so the importer stores that part's results, built from the elements as for any report | Source only. A laboratory time the sender could not write as sent (no calendar time, or an offset no date-time holds) is not lost: the sender states it in `TestResultsInformationReportedByTheLab` (`Collection time sent by the laboratory: ...`, `Requisition time sent by the laboratory: ...`), which the importer writes, as for any sender, into the laboratory information (NTE) of the result it builds. An empty `LaboratoryName` (the report named no laboratory) leaves the result's laboratory unnamed. |
| `carlos-data-summary-record.json` and image files inside a data-summary PDF | The exact record of a data summary whose PDF could not show everything | Not read. The PDF is stored unchanged (its bytes are checked against the planned digest), so the attachments stay inside the stored document. |
| A data summary exported as HTML (Format Text, `html`) and its exact record (Format Text, `json`, SubClass "CARLOS data summary record") | A data summary no PDF could be made of, and its exact record | Not read back into tables. Each is kept as a text document of the chart requiring review, the HTML shown as text and never run (`CDS_REPORT_HTML_RETAINED_AS_TEXT`, `CDS_REPORT_TEXT_MEDIA_DECLARATION_RETAINED`). |

### Exact text a CARLOS sender carries

XML 1.0 cannot carry a control character (other than tab, line feed and carriage return), U+FFFE,
U+FFFF or an unpaired surrogate, and CDS elements have lengths. A CARLOS sender writes such a
value by one documented rule (`CdsNativeExtensionContract.xmlElementText`: the same text with
U+FFFD in place of each such character, one for one) and carries the stored value beside it. The
importer restores the stored value only after proving the carrier against its element: the
element must be exactly what the sender's rule writes from the carried value, and the carried
value must be one a CARLOS column can hold (`isElementTextOf`). Only an element that shows U+FFFD
(or the patient note's shortened mark) can stand for another stored value, so no other element
reads a carrier, and an element that is absent is never restored. A carrier that holds nothing
for the element, or holds the element's own text, leaves it unchanged with no report: the U+FFFD
is then the sender's own text. A carrier that is unreadable, or that does not prove itself, is
never guessed at: the element is imported as from any sender and the patient's report names the
code below. Text from a sender that is not CARLOS is unaffected, whatever it contains.

- **CPP items** (`CdsHistoryPersister.exactItemText`). The item's text (its description element,
  or `CARLOS.History.Narrative.v1`) shows U+FFFD; the current revision of the item's last
  `CARLOS.NativeHistory.v1` (an edited imported item's sender writes its own after the one it
  received) carries `exactNarrative`, or `narrative` for a PersonalHistory item. The details are
  read twice as a stream and only the current revision's text is kept, so an earlier revision of
  any length is never held. A diagnosis the sender exported on its own (a patient issue no CPP
  item files, sent as a ProblemList item) has no revisions: its details are one `patientIssue`
  object, and the catalogue row's `issue.description` is the item's text, which its
  ProblemDiagnosisDescription (or, past 250 characters, `CARLOS.History.Narrative.v1`) shows with
  U+FFFD (`CdsHistoryExactNarrativeReader`). Not proven: `CDS_HISTORY_EXACT_NARRATIVE_NOT_APPLIED`
  (path `ResidualInfo`).
- **Encounter notes** (`CdsHistoryPersister.exactNoteText`). ClinicalNotes has no ResidualInfo:
  the text ends with `[CARLOS source note details]` and the revision details. A revision whose
  body was written with U+FFFD carries `exactNarrative`, and every revision then states
  `bodyLength`, its body's UTF-16 length as written. Counted back from the details, each body has
  one place, under the heading the sender writes (`[Original note]`, `[Revision n]`; none for one
  revision). Every heading must stand where the lengths put it and every carried body must prove
  itself there; then all are restored, otherwise none and `CDS_HISTORY_EXACT_NARRATIVE_NOT_APPLIED`
  (path `MyClinicalNotesContent`). Text that merely ends like revision details is another
  sender's quotation and is left alone.
- **Patient note** (`CdsDemographicDetailsService.carriedPatientNote`). NoteAboutPatient shows
  U+FFFD, or, for a note longer than its 64,000 characters, the first characters followed by the
  mark `[CARLOS: shortened to the CDS limit of 64,000 characters]` on its own line
  (`patientNoteElement`). The whole stored column is `note.content` of the last
  `CARLOS.NativeDemographic.v1` record in the patient file (read from the file's sealed parse, its
  records manifest checked against the parse seal); the note the sender's chart shows from it must
  give exactly the element. A note too long for the receiving column then becomes the source-view
  placeholder (`CDS_PATIENT_NOTE_COMPLETE_TEXT_SOURCE_VIEW`), as any long note does. Not proven:
  `CDS_PATIENT_NOTE_CARRIER_NOT_APPLIED` (path `NoteAboutPatient`). Because the element is never
  absent for a note, a record kept from an earlier sender cannot bring back a note the sending
  chart no longer has.
- **Prescriptions, allergies and vaccines** (`CdsCarriedTextResolver`). Each element the
  importer stores is written by the sender from stored columns, alone or composed with fixed
  text. For an element that shows U+FFFD the importer builds the compositions the sender can
  write it as from the carried row and uses the one whose U+FFFD form is exactly the element;
  none, or two different ones, is reported. The carrier is read as a stream, and a stored value
  longer than the element it is wanted for is skipped unheld.
  - Prescription (row of the last `CARLOS.Medication.CurrentSource.v1`, else
    `CARLOS.Medication.Source.v1`): DrugName (custom name or brand name), Form, Route, Frequency
    (with ` PRN` when the as-needed box qualifies it), Dosage, DosageUnitOfMeasure, Notes (the
    comment, or a long name led by `Drug name: `), PrescriptionInstructions (the SIG, the
    pharmacy instructions, or both joined by `[Native pharmacy instructions]` on its own line),
    DispenseInterval, ProtocolIdentifier and PriorPrescriptionReferenceIdentifier. The SIG the
    importer composes for the native row follows the restored values. Not proven:
    `CDS_MEDICATION_EXACT_TEXT_NOT_APPLIED`.
  - Allergy (`CARLOS.Allergy.Source.v1`): OffendingAgentDescription and Reaction. Not proven:
    `CDS_ALLERGY_EXACT_TEXT_NOT_APPLIED`.
  - Vaccine (`CARLOS.Prevention.Source.v1`): ImmunizationName (the name extension, or the
    prevention type), Manufacturer, LotNumber, Route, Site, Dose, Instructions and Notes (the
    status label, values too long for their elements as `Label: value` lines, the comments,
    whole parts within 32,000 characters, as the sender composes them). Not proven:
    `CDS_IMMUNIZATION_EXACT_TEXT_NOT_APPLIED`.
  Allergy and vaccine warnings are part of the sealed preparation (`diagnostics`, omitted when
  empty, so earlier payloads keep their bytes; defaulted after seal verification). Re-exported
  unchanged, a restored value is written as its U+FFFD form again (the exporter's prepared
  projections, `CdsExportPrepared*Mapper`, follow the element rule), beside the sender's carrier,
  so it is never refused for its characters and a further CARLOS import restores it again.
- **Patient Alert**: `CARLOS.PatientAlert.v1`, in the marker table above.

The text is restored before the native note size is checked: the stored value is never longer in
UTF-8 than its U+FFFD form. A note, restored or not, is stored whole when its prepared record fits
the column that holds it between preparation and write: `cds_import_preparation.payload`, whose
check admits 1,048,576 bytes (about 1 MiB of text; the export reads the retained record under the
same bound). `casemgmt_note.note` itself is LONGTEXT. Only a larger note becomes the documented
placeholder, its complete text in the source fields (`CDS_NOTE_COMPLETE_TEXT_SOURCE_VIEW`). No
setting chooses the size; earlier builds held a note to 256 KiB (`cds.import.native.maxNoteBytes`,
retired).

Editing such a note in the encounter or CPP editor is safe. The save posts the whole text: the
packaged WAF admits a 16 MiB form and nginx and Tomcat 50 MB, and a larger form is refused before
anything is written, so the note stays as it was. The note and its revision history are LONGTEXT,
and the packaged server accepts a 1 GiB statement. The CPP editor also copies the note into the
legacy CPP and eChart summaries, which are MEDIUMTEXT (16 MiB); one imported note cannot fill them.

### CPP items filed under several sections

A CARLOS chart can file one CPP item under two or more CPP sections (the encounter and CPP
editors' issue search offers the six section tags), and shows it in each of those boxes. A CDS
item has one section, so a CARLOS sender writes it once, under the first of its sections in CDS
PatientRecord order (PersonalHistory, FamilyHistory, PastHealth, ProblemList, RiskFactors,
AlertsAndSpecialNeeds), names the others in its Notes (`Also filed under:` and one section name
per line), and lists every tag at the root of the item's `CARLOS.NativeHistory.v1` details, in
that order: `"sections":["FamHistory","MedHistory"]`. An imported item the sender edited has it
at the root of `nativeHistory` instead (`docs/cds-export.md`).

The importer reads the list from the item's last `CARLOS.NativeHistory.v1` as a stream, keeping
no other value (`CdsHistorySectionsReader`), and files the note under every tag in it: its own
section's, as for any CPP item, and each other one, by the same rule (the patient issue of that
tag, created when the chart has none) (`CdsHistoryPersister.otherSections`, `write`). So the
receiving chart shows the item in every box the sender's chart did, and the unit's receipt names
each tag's patient issue and link (`casemgmt_issue#<tag>`, `casemgmt_issue_notes#<tag>`), which
an export from the receiving chart proves before writing the item again as the record it was
imported from. The prepared item names the other tags (`additionalIssueCodes`, omitted when there
are none, so a payload sealed earlier keeps its bytes and reads as none). A tag the receiving
issue catalogue does not hold exactly once is not linked and is reported as
`CDS_HISTORY_SECTION_NOT_RESTORED` (path `ResidualInfo`); the item keeps its other sections. A
list that is not two to six distinct section tags including the record's own, or details that
cannot be read, is never guessed at: the item is filed under its own section only and the report
names `CDS_HISTORY_SECTIONS_NOT_APPLIED` (path `ResidualInfo`). Either way the record's retained
source keeps the sender's list, and an item from a sender that is not CARLOS has none. A
receiver that is not CARLOS files the item under its one section and shows the Notes line.

### CARLOS demographic values

A CARLOS sender writes every Demographics element by the rules of
`CdsDemographicCarrierContract` (`docs/cds-export.md`, "Demographics values no element holds
exactly") and carries the stored column in the `demographic` object of its native demographic
record whenever the element is not that value: an empty or blank name is an empty `Part`, a
character XML cannot carry is U+FFFD, a name longer than a part is its first 50 characters, a
missing or unlisted sex is `U`, a missing status an empty `PersonStatusAsPlainText`, birth
columns that are not one calendar date `0001-01-01`, an unlisted title, a health card without a
usable number, a three-character version and a phone no element holds are left out. Optional text
(chart number, e-mail, address parts, phones, version, health number) is left out when no element
holds it and written as its `xs:token` form otherwise, a SIN typed with spaces or hyphens as its
nine digits, and a value this importer would store in another form (a language, issuer or
subdivision code, a postal display space, a health number with spaces, a status spelled as Moved
or Fired) is written as before and carried too. Its
`properties` list holds the `demographicExt` rows no element states (a hidden row with its
`hidden` flag, a value without a key, a second row of one phone key, every property with no
Demographics element).

`CdsDemographicCarriedValuesResolver` reads the last native demographic record of the patient
file (from its sealed parse, as the patient note is read). A CARLOS export writes the sending
chart's record after every retained copy of an earlier sender's, and always writes one when it
re-exports such a copy, so the last record is the sending chart's own. When the new chart is
written (`CdsDemographicPersister.write`):

- **Base-row columns.** A carried column is stored exactly, over the element's value, when the
  element is exactly what the rules write from it (`namePart`, `middleNameParts` joined with
  single spaces, `title`, `gender`, `personStatus`, `0001-01-01` for birth columns that state no
  date; the `xs:token` form of a chart number, e-mail, phone, version, health number or address
  part; the nine digits of a SIN; a postal code without its display space; the issuer's
  `CdsHealthCardAuthority.wire` code; a language or subdivision code as stored) or, for an
  element the rules leave out (health card, version, expiry, status date, phones, chart number,
  e-mail, SIN, languages, address parts), when the element is absent; and when the receiving
  column can hold it (its length, a calendar date for a DATE column, NOT NULL for names and sex,
  and the record's own trimming of the birth columns and of the mailing province). A document
  whose element was changed after the export (another value, a second phone or address of the
  type, a street in other lines) proves nothing. Otherwise the element's value stands and the
  report names `CDS_DEMOGRAPHIC_CARRIED_VALUE_NOT_RESTORED` with the element. A replay of an
  imported patient compares the chart with the restored identity values (names, sex, status,
  birth columns, health card number and issuer; `CdsPatientIdentityResolver.matchesCanonical`),
  and an empty name `Part` a CARLOS sender carries no longer refuses the patient
  (`DEMOGRAPHIC_REQUIRED_VALUE_MISSING`).
- **Extension rows.** Each carried row is written as stored: key (none for a row without one),
  value and hidden flag. CARLOS keeps one row per key (`uk_demo_ext_single_value`, except the
  dashboard's `excludeIndicator` history), so a row whose key the chart already has from its
  elements or an earlier carried row is not written and is reported, as is a flag other than 0 or
  1. The rows are part of the import's written references, so abandoning the import removes them.
- **Not restored, by decision.** A primary-physician number is local to the sending CARLOS; the
  physician is matched from `PrimaryPhysician` as for any sender. A zero date cannot be written
  through the patient record. The `demographic` columns no Demographics element states (alias,
  preferred name, pronoun, gender identity, the practice-administrative dates and the other
  ancillary columns), `roster`, `careTeam` and `related` stay source only: they travel in the
  retained record, and re-exporting the chart writes them again.

What was restored is part of the sealed preparation (`CdsDemographicNativeDto.carried` and
`properties`; a preparation sealed before them decodes them empty,
`CdsPreparedDemographicDefaults`), and re-exporting the chart reads it back: the projection
evidence compares the written row with the restored values, and the restored extension rows are
decided as on a chart added in CARLOS. A restored value is reported once as
`CDS_DEMOGRAPHIC_CARRIED_VALUES_RESTORED` (path `Demographics`); a record that cannot be read as
a version 1 document is reported as `CDS_DEMOGRAPHIC_CARRIER_NOT_APPLIED` (path `NewCategory`) and
nothing is restored from it. The extension rows are read in a pass of their own: a list holding a
value longer than any restorable text (`CdsSourceContextParser.RESTORABLE_UNITS`) is not read, and
rows that do not fit the 1 MiB sealed preparation with the chart are not kept; both are reported as
`CDS_DEMOGRAPHIC_CARRIED_VALUE_NOT_RESTORED` (path `NewCategory`) and stay in the retained record,
while the base-row columns are still restored.

### CARLOS appointment source record

CDS 5.2 Appointments has no ResidualInfo, so a CARLOS sender writes one NewCategory record per
appointment (`CategoryName` `CARLOS.NativeAppointment.v1`, residual
`CARLOS.Appointment.Source.v1`; its content is defined in `docs/cds-export.md`). Its `appointment`
member is the 1-based position of the Appointments element it belongs to in the same patient
file. `CdsAppointmentResidualReader` finds it from the patient file's own sealed parse (the
records manifest is checked against the parse seal) and uses the **last** record in document
order that names the position: a CARLOS export writes its current record for every element after
any retained copy of an earlier sender's record, so an older copy is never used.

Nothing is taken from a record until it proves itself against its element
(`CdsAppointmentPersister`):

- the receiving clinic's `cds.import.clinicalZone` is set and the element has an exact calendar
  date;
- the element's date and start, read in that zone, are the record's `appointment_date` and
  `start_time`;
- the record's `end_time` gives the element's Duration by the shared rule
  (`CdsAppointmentSourceContract.minutes`): as the scheduler's inclusive end (no Duration when the
  end states no positive length), or as the exclusive end an earlier CDS import left.

Then:

- **Exact end.** With an inclusive end the appointment is written with the sender's stored date,
  start and end. A slot past midnight (23:30 with end 00:29), with no stated length, with an end
  between minutes, or at a daylight-saving change, which the element alone leaves a source-only
  record (`CDS_APPOINTMENT_CROSS_MIDNIGHT_SOURCE_ONLY`, `..._DURATION_SOURCE_ONLY`,
  `..._DST_DURATION_SOURCE_ONLY`, `..._LOCAL_TIME_SOURCE_ONLY`), becomes a native appointment
  exactly as the sender's schedule showed it.
- **Complete notes.** When the record carries `complete.AppointmentNotes` and the element's notes
  are exactly the shared cut of that text (`CdsAppointmentSourceContract.notesElement`: without
  XML-illegal characters, cut before the mark at 32,000 characters) and the text fits the notes
  column (32,768 characters), the complete text is stored. Otherwise the element's notes are
  kept and `CDS_APPOINTMENT_SOURCE_NOTES_NOT_RESTORED` is reported.
- **Scheduler context.** `style`, `billing`, `location`, `resources`, `urgency`, `type` and
  `bookingSource` are stored as the sender stored them. A value longer than the receiving column
  (`CDS_APPOINTMENT_SOURCE_COLUMN_CAPACITY_NOT_RESTORED`) or a booking source this build does not
  know (`CDS_APPOINTMENT_SOURCE_BOOKING_SOURCE_NOT_RESTORED`) is reported and left empty.

A record that is not a readable version 1 document, or that does not prove itself, is reported
as `CDS_APPOINTMENT_SOURCE_RECORD_NOT_APPLIED` (path `NewCategory`) and the appointment is
imported from its standard elements only; an applied record is reported as
`CDS_APPOINTMENT_SOURCE_RECORD_APPLIED`. Not restored, by decision: the status code (the status
arrives as the sender's status text; a code depends on the receiving clinic's own status table),
the provider number (the provider is matched from the Provider element, as always), the booking
name, the remarks and an imported status written beside a status code (they already travel as
labelled lines of the notes), the reason-list item and
the program. They stay in the retained record. What was restored is part of the sealed
preparation (`CdsAppointmentNativeDto.source`, defaulted to "nothing restored" for earlier
payloads after seal verification, `CdsPreparedAppointmentDefaults`). Re-exporting such an
appointment reads a restored end as the scheduler's inclusive end and restored notes as current
notes, and writes a new source record for it after the retained one.

Export clinical qualification remains separate from these shared transfer rules.

## Report file types

A report file is published as a native document of the type its `FileExtensionAndVersion`
declares when its first sixteen bytes bear the declaration out (`CdsReportMediaService`; the
[clinical contract](cds-clinical-import-contract.md#report-representations-and-clinical-timing)
has the details). The recognised types are every type CARLOS stores and its own export declares
except `html` and `bin`:

| Declaration (and spellings) | Published as | Confirmed by |
| --- | --- | --- |
| `pdf`, `application/pdf` | `application/pdf` | `%PDF-n.n` header |
| `txt`, `text/plain` | `text/plain;charset=UTF-8` | strict UTF-8 decoding of the whole file (unstated encoding is reviewed) |
| `jpg`, `jpeg`, `image/jpeg` | `image/jpeg` | JPEG header |
| `png`, `image/png` | `image/png` | PNG signature |
| `tif`, `tiff`, `image/tiff` | `image/tiff` | TIFF byte-order header |
| `gif`, `image/gif` | `image/gif` | `GIF87a`/`GIF89a` |
| `bmp`, `image/bmp`, `image/x-bmp`, `image/x-ms-bmp` | `image/bmp` | `BM` with zero reserved bytes |
| `doc`, `application/msword` | `application/msword` | OLE2 compound-file header |
| `docx`, the Word OOXML type | `application/vnd.openxmlformats-officedocument.wordprocessingml.document` | ZIP local-file header |
| `rtf`, `application/rtf`, `text/rtf` | `application/rtf` | `{\rtf` |

A declaration the bytes contradict becomes an opaque document requiring review
(`CDS_REPORT_MEDIA_DECLARATION_MISMATCH`). An unknown or versioned declaration takes a PDF, PNG,
JPEG, TIFF, GIF or RTF header when the bytes have one (`CDS_REPORT_MEDIA_FROM_BOUNDED_HEADER`);
a bitmap, OLE2 or ZIP header alone names no type, since other formats and text share it. Any
other file, `bin` included, stays opaque and reviewed (`CDS_REPORT_MEDIA_UNRECOGNIZED_SOURCE_RETAINED`).

HTML (`html`, `htm`, `text/html`) is recognised but never published as HTML. The document viewer
(`ManageDocument2Action.display`) renders a `text/html` document as a live page in the CARLOS
origin, the behaviour a clinician's own upload relies on; script in a file from another system
must not run there. An HTML file is therefore an opaque `application/octet-stream` document
requiring review (`CDS_REPORT_HTML_RETAINED_OPAQUE`), which the browser downloads rather than
renders (CARLOS sends `X-Content-Type-Options: nosniff`), and HTML report text, which is how
CARLOS exports an Add HTML or Add Link document, stays plain text requiring review
(`CDS_REPORT_HTML_RETAINED_AS_TEXT`), its markup shown rather than run. The original bytes and
declaration remain in the report's protected source fields. A report imported for review is still
exported again by CARLOS from its retained source without holding the patient (see the
[export contract](cds-export.md)).

## Deployment companions

The CARLOS application owns the shared engine, web workflow, migrations and clinical behavior. [carlos-ctl](https://github.com/carlos-emr/carlos-ctl/pull/15) supplies the verified host/container command and delegation boundary. [carlos-podman](https://github.com/carlos-emr/carlos-podman/pull/22) supplies the coordinated container integration. Compatible packages, runtime classes, configuration and implementation fingerprints must be verified together. Publishing a companion release asset follows the normal maintainer release process.

## Keeping these documents accurate

Update the relevant contract when changing a public route, command, configuration property, DTO field, state transition, DAO operation, native representation, resource bound or recovery decision. Update this index when adding a document. Check relative links and examples against the implementation.

Keep executed candidate-specific results on the release pull request with their exact source/artifact identities. Preserve failures, skips, interrupted attempts and pending gates explicitly. A previous candidate's result or a source review does not certify a successor or establish OntarioMD certification.
