# CDS clinical normalization and native writes

This is the implemented clinical component contract for the import engine. It does not establish whole-import conformance or release readiness. The coordinator must preserve every source leaf in the corresponding category's protected, separately addressable additional data and reconcile those fields before reporting completion. A native-row receipt alone is insufficient.

`CdsClinicalNormalizationService` reads a bounded occurrence using `CdsFieldParser`. It returns immutable medication, allergy or immunization data without clinical writes or catalogue enrichment. `CdsMedicationPersister`, `CdsAllergyPersister` and `CdsImmunizationPersister` require the caller's real transaction, write native rows and precision/extension rows in that transaction, and return exact table/ID receipts. The owning unit must add its strict audit, field dispositions and source checkpoint before committing.

## Temporal meaning

`CdsTemporal` interprets the actual YearOnly, YearMonth, FullDate and FullDateTime branches strictly. Year/month values retain their interval and original precision; optional absence remains unknown. Storage anchors are synthetic and require the existing `partial_date` YYYY/YYYY-MM metadata in the same unit. No UNKNOWN tag is introduced. Exact January 1 remains exact unless explicit partial metadata says otherwise.

Offsets retain their lexical value and instant. Clinical date-only values retain their calendar date. The XML Schema midnight spelling 24:00:00 is interpreted as the following midnight with the original lexical value retained. Local timestamps require an explicitly configured `cds.import.clinicalZone`; a gap or overlap in that zone requires review instead of an arbitrary DST choice. The supported native calendar is years 1–9999; malformed, conflicting or unrepresentable temporal values produce a stable review/error code and do not enter malformed native rows.

Medication/allergy DATE destinations do not carry a source timestamp's full time/offset. The original leaf and its precision must remain available in the corresponding category data. Unknown-date immunizations remain valid native historical records with a null date; RefusedFlag remains an independent required assertion. `docs/cds-prevention-date-contract.md` describes the precision-aware native readers and legacy protocol safeguards.

## Medication destinations

| Source concept | Native destination and rule |
| --- | --- |
| DrugName/DIN | Source name in custom/brand name as appropriate and source DIN in regional identifier; no DrugRef substitution/enrichment overwrites these values |
| Strength amount/unit | Native strength display in `Drug.dosage`; original components remain separate category fields |
| Dose/unit | Exact raw dose in `cds_dose_text`, unit fields and source-literal SIG; legacy float endpoints are populated only when the complete decimal/range is exactly representable |
| Frequency/form/route | Independent native fields; no substring extraction or invented route |
| Initial/refill duration | Independent numeric-day values; never derive treatment end/currentness from dispense duration |
| Quantity/refill quantity/repeats | Independent values with strict numeric/target-range checking; overflow or non-core textual values require review, never leading-number truncation |
| Written/start dates | Separate native dates and existing partial metadata; absent dates stay null rather than import time |
| LongTerm/Past/Compliance | Nullable independent source indicators |
| Substitution/authority | Nullable independent source indicators; NonAuthoritativeIndicator accepts its own Y/y/N/n vocabulary, not the Boolean alternatives of another field |
| TreatmentType/PrescriptionStatus | Raw source values preserved separately; PRN long-term and short-term remain distinct |
| Currentness | Nullable `cds_currentness`: CURRENT, HISTORICAL, SUSPENDED, NEW or UNKNOWN for imported rows; null for untouched native rows |
| Provider Notes | Exact provider-only text in `Drug.comment`; never concatenated into special/SIG or pharmacy instructions |
| Pharmacy instructions | `special_instruction`, separate from provider Notes |
| Original prescriber | Truthful external name/OHIP; an unresolved local provider reference is empty and never replaced with the importing clinician |
| Import actor/time | Owning unit's audit and native creation/creator metadata, separate from source clinical authorship/time |
| Description, source prescription/reference IDs, residuals and other leaves | Corresponding protected category fields with their exact source meaning and visible disposition; not a successful generic Notes fallback |

CT035 Active maps to CURRENT; New and Suspended remain distinct; Aborted/Completed/Obsolete/Nullified are historical. Missing lifecycle status is UNKNOWN unless an explicit PastMedications assertion establishes history. Missing start date never establishes past status. Definite conflicts such as Active plus PastMedications=Y require review. Only known historical rows are archived; UNKNOWN/NEW/SUSPENDED are not silently turned into completed/discontinued prescriptions.

The native current-only queries and unique current-medication content exclude non-current imported states while retaining their rows in full history/export queries and the category view. Later clinical archival overrides an imported CURRENT assertion. Existing native rows with null CDS metadata keep their established selection behavior.

The legacy prescription bridge carries currentness, exact dose and nullable indicators rather than erasing them. Automatic renewal refuses unresolved imported currentness, substitution, authority or unstructured dose with `CDS_MEDICATION_RECONCILIATION_REQUIRED_BEFORE_RENEWAL`. An authorized clinician must review the source and make an explicit new prescribing decision; import itself does not prescribe or establish dispensing authority. The established renewal policy for ordinary native prescriptions is unchanged. Remaining clinical/API consumers must either read these representations safely or explicitly refuse unsupported imported precision; the release gate cannot rely solely on additional source storage.

## Allergy and immunization destinations

Allergy AL and AR are retained literally; AR does not establish intolerance or side-effect subtype. Agent property DR/ND/UK is independent from severity. No representative product/class is invented from a missing DrugRef mapping. Native severity NO maps to the native No Reaction code, independently of unknown severity. Source reaction, agent/code, onset/recorded precision and history remain separate. Source Notes/code-system/residual values require the corresponding category fields and review evidence.

Immunization RefusedFlag N/false/0 asserts administration; Y/true/1 asserts refusal. Missing required flag is not administration. Optional absent/partial date is preserved with its timing uncertainty. Source manufacturer uses the native extension key `manufacture`; lot, route, site, dose, comments, instructions, code/system and original date/precision use their separate fields. No importing provider is attributed as the administering clinician. Creator/audit identifies the importing actor.

## Evidence and remaining release obligations

The focused clinical tests exercise all seven lifecycle states, absence versus explicit negative indicators, contradictory flags, representative DIN/source preservation, fractions/ranges and overflow, private Notes versus prescription output, source-author separation, optional unknown immunization date, true calendar/offset/DST semantics, January 1, Unicode capacities and failure propagation. Native DAO regressions cover current-only selection, full history retention and nullable imported indicators/dates.

The isolated Java25 run is useful component evidence, not proof of physical transaction participation, database/filesystem crash recovery, clinical UI/category completeness, production schema compatibility or installed integration authorization. Required native capacities are part of the forward migration; test both real upgraded MariaDB storage and the exact final candidate. New/remaining legacy readers and external clients must pass the full mapping/consumer and compatibility gates before import admission is enabled.

## Legacy medication consumer safeguards

The existing Date/float-based REST DrugTo1 and SOAP/integrator DrugTransfer shapes cannot carry imported currentness, text dose, partial dates and unknown prescribing flags. They explicitly refuse imported medication projections rather than returning anchors, zero dose or asserted negative flags. REST returns HTTP 409 with `CDS_MEDICATION_PRECISION_AWARE_REVIEW_REQUIRED`; SOAP callers receive an explicit fault. Native records with null CDS metadata retain their protocol behavior. The protected imported-medication category/source representation must be available and authorized before activating imports for a deployment using these clients.

The old Rx manager replacement and prescribe routes also inspect the persisted source before any write; omitting CDS metadata from an incoming legacy DTO cannot erase imported uncertainty or create executable prescribing authority. All five existing web renewal forms preflight clinical uncertainty before altering the draft. Mixed bulk selections refuse the complete attempt before staging any candidate, preserve prior drafts and source-ID selections, and return actionable HTTP 409. An unrelated unexpected legacy staging exception now returns failure rather than the legacy success result.

The static medication history view keeps unknown dates sortable without null failures, shows the external source prescriber truthfully, and labels imported lifecycle/nullable authority/substitution separately. The detailed medication view encodes imported text as text, shows exact raw dose, displays unknown source flags explicitly and keeps provider-only Notes separate from pharmacy instructions. Browser and installed integration execution on the final candidate, remaining direct SQL/Date-only readers, and the complete category view remain release obligations.
