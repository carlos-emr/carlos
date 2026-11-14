/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsMedicationDose;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportMedicationDto.Field;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Outcome;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportRetainedReceiptVerificationDto.Reason;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Fieldwise prescription reconciliation; current values never erase unrelated, verified source meanings. */
public final class CdsExportMedicationFieldReconciler {
    /**
     * @param currentSource the {@code CARLOS.Medication.CurrentSource.v1} content, or null when
     *     every changed value has a CDS element: the complete current row of an imported
     *     prescription whose in-place change (deleted or discontinued, hidden from the CPP, ...)
     *     CDS 5.2 has no element for
     * @param archived the archive state the occurrence's {@code CARLOS.Medication.Archived.v1}
     *     must state once the archive columns were changed in CARLOS, or null when the retained
     *     marker (or its absence) still stands
     */
    public record Result(
            String sourceManifestSha256,
            String unitAddress,
            CdsExportMedicationMappingDto mapping,
            Set<String> preparedPaths,
            Set<String> currentPaths,
            boolean retainedStrengthRequired,
            String currentSource,
            Boolean archived) {
        public Result {
            preparedPaths = Set.copyOf(preparedPaths);
            currentPaths = Set.copyOf(currentPaths);
        }

        /** A reconciliation with nothing to carry beside the standard elements. */
        public Result(
                String sourceManifestSha256,
                String unitAddress,
                CdsExportMedicationMappingDto mapping,
                Set<String> preparedPaths,
                Set<String> currentPaths,
                boolean retainedStrengthRequired) {
            this(
                    sourceManifestSha256,
                    unitAddress,
                    mapping,
                    preparedPaths,
                    currentPaths,
                    retainedStrengthRequired,
                    null,
                    null);
        }

        @Override
        public String toString() {
            return "CdsExportMedicationFieldReconciler.Result[protected]";
        }
    }

    private record Group(List<Field> fields, String path, Set<String> columns) {
        Group(Field field, String... columns) {
            this(List.of(field), field.name(), Set.of(columns));
        }

        Group(String path, String... columns) {
            this(List.of(), path, Set.of(columns));
        }
    }

    static final String RESIDUAL = "MedicationsAndTreatments/ResidualInfo";

    /** Rule of a current value carried in {@code CARLOS.Medication.CurrentSource.v1}. */
    static final String CARRIED = "CURRENT_NATIVE_VALUE_IN_EDITED_SOURCE_CONTEXT";

    private static final String STATUS = "PrescriptionStatus/PastMedications";

    /**
     * The candidate rules of a current value CDS 5.2 has no element for. On a never-imported
     * prescription the native mapper carries them in {@code CARLOS.Medication.Source.v1}; on an
     * imported one changed in CARLOS they travel in {@code CARLOS.Medication.CurrentSource.v1},
     * so an ordinary in-place change (Delete or Discontinue, Hide from CPP, the script a REST
     * prescribe files it under) never holds the chart.
     */
    private static final Set<String> CARRIED_RULES =
            Set.of(
                    "UNMAPPED_NATIVE_VALUE",
                    CdsExportMedicationMapper.DURATION_WITHOUT_UNIT,
                    CdsExportMedicationMapper.DURATION_UNIT_UNKNOWN,
                    CdsExportMedicationMapper.DURATION_NOT_A_NUMBER,
                    "NUMERIC_QUANTITY_REQUIRES_REVIEW",
                    "NONNEGATIVE_INTEGER_CAPACITY_REQUIRES_REVIEW",
                    "NATIVE_BOOLEAN_REQUIRES_REVIEW",
                    "UNRECOGNIZED_TREATMENT_TYPE");

    private static final List<Group> GROUPS =
            List.of(
                    new Group(Field.DrugName, "BN", "customName", "GN"),
                    new Group(
                            Field.DrugIdentificationNumber,
                            "regional_identifier",
                            "GCN_SEQNO",
                            "ATC"),
                    new Group(Field.Dosage, "cds_dose_text", "takemin", "takemax"),
                    new Group(Field.DosageUnitOfMeasure, "unit", "unitName"),
                    new Group("Strength", "dosage"),
                    new Group(Field.Form, "drug_form"),
                    new Group(Field.Route, "route"),
                    new Group(
                            List.of(Field.Frequency, Field.TreatmentType),
                            "Frequency/TreatmentType",
                            Set.of("freqcode", "eTreatmentType", "prn", "short_term")),
                    new Group(Field.Duration, "duration", "durunit"),
                    new Group(Field.RefillDuration, "refill_duration"),
                    new Group(Field.Quantity, "quantity"),
                    new Group(Field.RefillQuantity, "refill_quantity"),
                    new Group(Field.NumberOfRefills, "repeat"),
                    new Group(Field.LongTermMedication, "long_term"),
                    new Group(Field.PatientCompliance, "patient_compliance"),
                    new Group(Field.SubstitutionNotAllowed, "nosubs"),
                    new Group(Field.NonAuthoritativeIndicator, "non_authoritative"),
                    new Group(
                            List.of(Field.PrescriptionStatus, Field.PastMedications),
                            "PrescriptionStatus/PastMedications",
                            Set.of(
                                    "rxStatus",
                                    "past_med",
                                    "cds_currentness",
                                    "archived",
                                    "archived_reason",
                                    "archived_date",
                                    "end_date")),
                    new Group(Field.Notes, "comment"),
                    new Group(Field.PrescriptionInstructions, "special_instruction"),
                    new Group(Field.DispenseInterval, "dispense_interval"),
                    new Group(Field.ProtocolIdentifier, "protocol"),
                    new Group(Field.PriorPrescriptionReferenceIdentifier, "priorRxProtocol"),
                    new Group(
                            "PrescribedBy",
                            "provider_no",
                            "outside_provider_name",
                            "outside_provider_ohip"),
                    new Group("PrescriptionWrittenDate", "written_date"),
                    new Group("StartDate", "rx_date", "start_date_unknown"),
                    new Group(
                            "generatedInstructions",
                            "special",
                            "custom_instructions",
                            "custom_note",
                            "cds_projection_incomplete"));

    public Result reconcile(
            int patient,
            CdsExportRetainedIndexDto index,
            String address,
            CdsExportRetainedReceiptVerificationDto checked,
            CdsExportCapturedTextRowDto current,
            CdsExportNativeRowReceiptDto witness,
            List<CdsExportCapturedTextRowDto> precision,
            Map<String, CdsExportCapturedTextRowDto> providers)
            throws IOException {
        var preparation =
                new CdsExportRetainedPreparationReader().medication(index, address).value();
        if (!index.sourceManifestSha256().equals(checked.sourceManifestSha256())
                || !index.appliedUnits().equals(checked.units().keySet())) scope();
        var references = checked.units().get(address);
        if (references == null
                || !references.stream()
                        .map(CdsExportRetainedReceiptVerificationDto.CheckedReference::expected)
                        .toList()
                        .equals(index.references().get(address))) scope();
        var original = verifyNativeWitness(index, address, references, current, witness);
        String run = address.substring(0, 36), unitKey = address.substring(37);
        var unit = index.tables().get("cds_import_unit").get(CdsImportKeys.hash(run, unitKey));
        CdsNativeRowState baseline = null;
        if (unit.fields().get("native_baseline") != null) {
            try {
                var document =
                        CdsNativeBaselineCodec.decode(
                                unit.fields().get("native_baseline"),
                                unit.fields().get("target_references"),
                                unit.fields().get("target_digest"));
                baseline =
                        document.rows().stream()
                                .filter(
                                        row ->
                                                row.table().equals("drugs")
                                                        && row.id().equals(witness.id()))
                                .findFirst()
                                .orElse(null);
            } catch (IllegalArgumentException invalid) {
                scope();
            }
        } else if (original.reason() == Reason.UNCHANGED_ROW
                && CdsNativeRowComparison.compare(witness.state(), witness.state())
                        .unchangedProjection()
                && java.util.stream.IntStream.range(0, witness.state().columns().size())
                        .noneMatch(
                                i ->
                                        !witness.state().cells().get(i).sqlNull()
                                                && CdsExactNumeric.codec(
                                                                witness.state()
                                                                        .columns()
                                                                        .get(i)
                                                                        .type())
                                                        != null)) baseline = witness.state();
        if (baseline == null || !baseline.digest().equals(original.expected().digest()))
            review("CDS_EXPORT_MEDICATION_FIELD_BASELINE_REQUIRED");
        if (!matches(baseline, "demographic_no", Integer.toString(patient))
                || !matches(baseline, "drugid", witness.id())) scope();
        var difference = CdsNativeRowComparison.compare(baseline, witness.state());
        if (difference.schemaChanged() || !difference.unqualifiedColumns().isEmpty())
            review("CDS_EXPORT_MEDICATION_FIELD_CODEC_REQUIRES_REVIEW");
        var source = preparation.source();
        var old = new CdsExportPreparedMedicationMapper().forRetainedReconciliation(source);
        var candidate =
                new CdsExportMedicationMapper().candidate(patient, current, precision, providers);
        var decisions = new LinkedHashMap<>(candidate.assessments());
        var selected = new EnumMap<Field, String>(Field.class);
        selected.putAll(candidate.values());
        var expected = projection(patient, witness.id(), preparation);
        var preparedPaths = new HashSet<String>();
        var currentPaths = new HashSet<String>();
        var chosen = new HashSet<String>();
        var currentPrecisionIds = new HashSet<String>();
        for (var row : precision) if (!currentPrecisionIds.add(row.fields().get("id"))) scope();
        var priorPrecisionIds = new HashSet<String>();
        boolean samePrecision = true;
        for (var ref : references)
            if (ref.expected().table().equals("partial_date")) {
                if (!priorPrecisionIds.add(ref.expected().id())) scope();
                samePrecision &= ref.reason() == Reason.UNCHANGED_ROW;
            }
        samePrecision &= priorPrecisionIds.equals(currentPrecisionIds);
        var restored = restoredColumns(source);
        for (var group : groups(source)) {
            boolean proved = true;
            for (String column : group.columns())
                proved &= matches(baseline, column, expected.get(column));
            if (group.path().equals("Dosage")) proved &= floatProjection(baseline, source);
            if (!proved) {
                mark(current, decisions, group, "PREPARED_NATIVE_PROJECTION_NOT_PROVEN");
                continue;
            }
            boolean unchanged = Collections.disjoint(group.columns(), difference.changedColumns());
            var restoredChanged = new HashSet<>(group.columns());
            restoredChanged.retainAll(restored);
            restoredChanged.retainAll(difference.changedColumns());
            if (!unchanged
                    && (!restoredChanged.isEmpty()
                            || group.path().equals(Field.Quantity.name())
                                    && restored.contains("unitName"))) {
                // The retained CARLOS.Medication.Source.v1 carrier would restate a value the
                // import restored from it (or the quantity it was checked against) and contradict
                // the current chart (#6198).
                mark(current, decisions, group, "RESTORED_SOURCE_CONTEXT_CHANGED_REQUIRES_REVIEW");
                continue;
            }
            if (source.strengthSourceOnly() && group.path().equals("Strength") && !unchanged) {
                mark(current, decisions, group, "SOURCE_ONLY_STRENGTH_PROJECTION_CHANGED");
                continue;
            }
            boolean date =
                    group.path().equals("StartDate")
                            || group.path().equals("PrescriptionWrittenDate");
            if (date && !samePrecision) {
                mark(current, decisions, group, "CHANGED_PRECISION_DEPENDENCY_REQUIRES_REVIEW");
                continue;
            }
            var oldDate = group.path().equals("StartDate") ? old.start() : old.written();
            if (date
                    && !unchanged
                    && (oldDate.precision() == CdsExportDateDto.Precision.DATE_TIME
                            || hasOffset(oldDate))) {
                mark(
                        current,
                        decisions,
                        group,
                        "UNPROJECTED_CLOCK_OR_OFFSET_REQUIRES_RECONCILIATION");
                continue;
            }
            if (group.path().equals("generatedInstructions")
                    && unchanged
                    && !Collections.disjoint(
                            difference.changedColumns(),
                            Set.of(
                                    "BN",
                                    "customName",
                                    "cds_dose_text",
                                    "takemin",
                                    "takemax",
                                    "unit",
                                    "unitName",
                                    "dosage",
                                    "route",
                                    "freqcode"))) {
                mark(
                        current,
                        decisions,
                        group,
                        "GENERATED_SIG_DEPENDENCIES_CHANGED_REQUIRES_REVIEW");
                continue;
            }
            if (unchanged) {
                chosen.add(group.path());
                preparedPaths.add(group.path());
                for (var field : group.fields()) {
                    String value = old.fields().get(field);
                    if (value == null) selected.remove(field);
                    else selected.put(field, value);
                }
                for (String column : group.columns()) {
                    var key =
                            new CdsExportNativeFieldKeyDto(
                                    current.table(), current.recordKey(), column);
                    var prior = decisions.get(key);
                    decisions.put(
                            key,
                            new CdsExportFieldAssessmentDto(
                                    prior.presence(),
                                    Outcome.MAPPED,
                                    group.path(),
                                    "VERIFIED_UNCHANGED_PREPARED_FIELD_PROJECTION"));
                }
                if (group.path().equals("PrescribedBy"))
                    for (var entry : new ArrayList<>(decisions.entrySet()))
                        if (entry.getKey().table().equals("provider"))
                            decisions.put(
                                    entry.getKey(),
                                    new CdsExportFieldAssessmentDto(
                                            entry.getValue().presence(),
                                            Outcome.OPERATIONAL,
                                            "PrescribedBy",
                                            "VERIFIED_ORIGINAL_PRESCRIBER_IDENTITY_PRESERVED"));
            } else currentPaths.add(group.path());
        }
        // These standard source fields have no native prescription column. The retained assembler
        // separately requires their unchanged, applied field pages and preserves unknown siblings.
        for (var field : List.of(Field.DrugDescription, Field.PrescriptionIdentifier)) {
            if (old.fields().containsKey(field)) selected.put(field, old.fields().get(field));
            preparedPaths.add(field.name());
        }
        // An imported prescription archived in CARLOS (Delete, Discontinue, a REST update) keeps
        // the status it was imported with, so the archive decides its currentness, exactly as on
        // a never-imported row: other receivers ignore CARLOS residuals and must not read it as
        // current. An archive the import itself wrote (reason "cds:...") keeps its status.
        Boolean archived = null;
        if (currentPaths.contains(STATUS)) {
            String flag = current.fields().get("archived");
            String reason = current.fields().get("archived_reason");
            if ("1".equals(flag)) {
                archived = true;
                if (reason == null || !reason.startsWith("cds:"))
                    selected.put(
                            Field.PrescriptionStatus,
                            CdsExportNativeMedicationMapper.archivedStatus(reason));
                selected.put(Field.PastMedications, "Y");
                for (String column : List.of("rxStatus", "past_med")) {
                    var key =
                            new CdsExportNativeFieldKeyDto(
                                    current.table(), current.recordKey(), column);
                    var prior = decisions.get(key);
                    if (prior.outcome() == Outcome.REQUIRES_REVIEW
                            && Set.of(
                                            "CONFLICTING_MEDICATION_CURRENTNESS",
                                            "MEDICATION_CURRENTNESS_NOT_ESTABLISHED",
                                            "UNRECOGNIZED_PRESCRIPTION_STATUS")
                                    .contains(prior.rule()))
                        decisions.put(
                                key,
                                new CdsExportFieldAssessmentDto(
                                        prior.presence(),
                                        Outcome.MAPPED,
                                        STATUS + ";" + RESIDUAL,
                                        "ARCHIVE_DECIDES_CURRENTNESS_STORED_VALUE_CARRIED"));
                }
            } else if ("0".equals(flag)) archived = false;
        }
        boolean carried = false;
        for (String column : current.fields().keySet()) {
            var key = new CdsExportNativeFieldKeyDto(current.table(), current.recordKey(), column);
            var prior = decisions.get(key);
            if (prior.outcome() != Outcome.REQUIRES_REVIEW || !CARRIED_RULES.contains(prior.rule()))
                continue;
            carried = true;
            decisions.put(
                    key,
                    new CdsExportFieldAssessmentDto(
                            prior.presence(), Outcome.MAPPED, RESIDUAL, CARRIED));
        }
        boolean blocked =
                decisions.values().stream()
                        .anyMatch(field -> field.outcome() == Outcome.REQUIRES_REVIEW);
        String currentSource = carried && !blocked ? currentSource(current, precision) : null;
        CdsExportMedicationDto result = null;
        if (!blocked)
            result =
                    new CdsExportMedicationDto(
                            chosen.contains("PrescriptionWrittenDate")
                                    ? old.written()
                                    : candidate.written(),
                            chosen.contains("StartDate") ? old.start() : candidate.start(),
                            chosen.contains("Strength") ? old.strengthAmount() : null,
                            chosen.contains("Strength") ? old.strengthUnit() : null,
                            chosen.contains("PrescribedBy")
                                    ? old.prescriber()
                                    : candidate.prescriber(),
                            selected);
        return new Result(
                index.sourceManifestSha256(),
                address,
                new CdsExportMedicationMappingDto(result, decisions),
                preparedPaths,
                currentPaths,
                source.strengthSourceOnly(),
                currentSource,
                blocked ? null : archived);
    }

    /**
     * The complete current row in the shape of {@code CARLOS.Medication.Source.v1}: every column
     * with SQL NULL and empty text distinct, the exact FLOAT witnesses and the date-precision
     * rows, in a fixed key order with every space escaped.
     */
    private static String currentSource(
            CdsExportCapturedTextRowDto current, List<CdsExportCapturedTextRowDto> precision)
            throws IOException {
        var details = new LinkedHashMap<String, Object>();
        details.put("version", 1);
        details.put("fields", new TreeMap<>(current.fields()));
        details.put("exactNumbers", new TreeMap<>(current.exactNumbers()));
        details.put(
                "precision",
                precision.stream()
                        .sorted(Comparator.comparing(CdsExportCapturedTextRowDto::recordKey))
                        .map(row -> new TreeMap<>(row.fields()))
                        .toList());
        String content =
                CdsExportScreeningMapper.tokenSafe(
                        com.fasterxml.jackson.databind.json.JsonMapper.builder()
                                .build()
                                .writeValueAsString(details));
        if (!CdsExportNativeFieldAssessments.legal(content, 1 << 24))
            review("CDS_EXPORT_MEDICATION_CURRENT_SOURCE_REQUIRES_REVIEW");
        return content;
    }

    private CdsExportRetainedReceiptVerificationDto.CheckedReference verifyNativeWitness(
            CdsExportRetainedIndexDto index,
            String address,
            List<CdsExportRetainedReceiptVerificationDto.CheckedReference> references,
            CdsExportCapturedTextRowDto current,
            CdsExportNativeRowReceiptDto witness)
            throws IOException {
        var drugRefs =
                references.stream().filter(ref -> ref.expected().table().equals("drugs")).toList();
        if (drugRefs.size() != 1
                || references.stream()
                        .anyMatch(
                                ref ->
                                        !Set.of("drugs", "partial_date")
                                                .contains(ref.expected().table()))) scope();
        var original = drugRefs.getFirst();
        if (original.reason() == Reason.ROW_NOT_IN_CAPTURE)
            review("CDS_EXPORT_MEDICATION_DELETION_REQUIRES_REVIEW");
        if (current == null
                || witness == null
                || witness.state() == null
                || original.currentDigest() == null)
            review("CDS_EXPORT_MEDICATION_NATIVE_WITNESS_REQUIRED");
        if (!"drugs".equals(witness.table())
                || !original.expected().id().equals(witness.id())
                || !witness.id().equals(current.fields().get("drugid"))
                || !current.recordKey().equals(witness.recordKey())
                || !original.currentDigest().equals(witness.digest())
                || !witness.digest().equals(witness.state().digest())) scope();
        for (var entry : index.references().entrySet())
            if (!entry.getKey().equals(address)
                    && entry.getValue().stream()
                            .anyMatch(
                                    ref ->
                                            ref.table().equals("drugs")
                                                    && ref.id().equals(witness.id())))
                review("CDS_EXPORT_MEDICATION_ALIAS_REQUIRES_REVIEW");
        if (!witness.id().equals(witness.state().id())
                || !witness.table().equals(witness.state().table())) scope();
        verifyCurrent(current, witness.state());
        return original;
    }

    /**
     * The field groups of this preparation. A quantity unit restored from a CARLOS sender's
     * carrier (#6198) makes {@code unitName} part of Quantity (the exporter wrote "100 mL"), not
     * of the dose unit, so a changed quantity unit cannot be exported as a dose unit.
     */
    private static List<Group> groups(CdsMedicationDto source) {
        if (source.quantityUnit() == null) return GROUPS;
        var result = new ArrayList<Group>(GROUPS.size());
        for (var group : GROUPS)
            result.add(
                    group.path().equals(Field.DosageUnitOfMeasure.name())
                            ? new Group(Field.DosageUnitOfMeasure, "unit")
                            : group.path().equals(Field.Quantity.name())
                                    ? new Group(Field.Quantity, "quantity", "unitName")
                                    : group);
        return result;
    }

    /** The native columns the import restored from a CARLOS sender's source carrier (#6198). */
    private static Set<String> restoredColumns(CdsMedicationDto source) {
        var result = new HashSet<String>();
        if (source.quantityUnit() != null) result.add("unitName");
        if (source.asNeeded() != null) result.add("prn");
        return result;
    }

    private static Map<String, String> projection(
            int patient, String id, CdsMedicationNativeDto prepared) {
        var source = prepared.source();
        var values = new HashMap<String, String>();
        io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.Plan.DRUGS
                .columns()
                .forEach(column -> values.put(column, null));
        values.put("drugid", id);
        values.put("demographic_no", Integer.toString(patient));
        values.put(
                "provider_no",
                prepared.provider() != null && prepared.provider().matched()
                        ? prepared.provider().localProviderNo()
                        : "");
        values.put("outside_provider_name", trim(source.prescriberName()));
        values.put("outside_provider_ohip", trim(source.prescriberOhip()));
        values.put(source.din() == null ? "customName" : "BN", trim(source.name()));
        values.put("regional_identifier", trim(source.din()));
        values.put("GCN_SEQNO", "0");
        values.put(
                "written_date",
                source.written().earliest() == null
                        ? null
                        : source.written().earliest().toString());
        values.put(
                "rx_date",
                source.start().earliest() == null ? null : source.start().earliest().toString());
        values.put("start_date_unknown", source.start().exactCalendarDate() ? "0" : "1");
        values.put("duration", trim(source.duration()));
        values.put("durunit", source.duration() == null ? null : source.durationUnit());
        values.put("quantity", trim(source.quantity()));
        values.put("repeat", number(source.repeats()));
        values.put("refill_duration", number(source.refillDurationDays()));
        values.put("refill_quantity", number(source.refillQuantity()));
        values.put("freqcode", trim(source.frequency()));
        values.put("route", trim(source.route()));
        values.put("drug_form", trim(source.form()));
        values.put("unit", trim(source.doseUnit()));
        values.put(
                "unitName",
                trim(source.quantityUnit() != null ? source.quantityUnit() : source.doseUnit()));
        values.put("cds_dose_text", source.dose());
        String strength =
                source.strengthSourceOnly()
                        ? "[Source strength: see imported medication details]"
                        : source.strengthAmount() == null
                                ? null
                                : source.strengthAmount()
                                        + (source.strengthUnit() == null
                                                ? ""
                                                : " " + source.strengthUnit());
        values.put("dosage", trim(strength));
        values.put("long_term", flag(source.longTerm()));
        values.put("past_med", flag(source.past()));
        values.put("patient_compliance", flag(source.compliance()));
        values.put(
                "short_term",
                source.treatmentType() == null
                        ? null
                        : flag(
                                Set.of("ACU", "ONET", "PRN Short-term")
                                        .contains(source.treatmentType())));
        values.put("nosubs", flag(source.substitutionNotAllowed()));
        values.put("non_authoritative", flag(source.nonAuthoritative()));
        values.put("eTreatmentType", source.treatmentType());
        values.put("rxStatus", source.prescriptionStatus());
        values.put("cds_currentness", source.currentness().name());
        values.put("cds_projection_incomplete", flag(source.sourceRequiresReconciliation()));
        values.put(
                "prn",
                flag(
                        source.asNeeded() != null
                                ? source.asNeeded()
                                : source.treatmentType() != null
                                                && source.treatmentType().startsWith("PRN ")
                                        || "PRN".equals(source.frequency())));
        boolean historical = source.currentness() == CdsMedicationDto.Currentness.HISTORICAL;
        boolean archived = prepared.archived() || historical;
        values.put("archived", flag(archived));
        values.put(
                "archived_reason",
                historical
                        ? "cds:"
                                + (source.prescriptionStatus() == null
                                        ? "PastMedications"
                                        : source.prescriptionStatus())
                        : prepared.archived()
                                ? "cds:"
                                        + io.github.carlos_emr.carlos.demographic.cds.contract
                                                .CdsNativeExtensionContract.MEDICATION_ARCHIVED
                                : null);
        values.put("dispense_interval", source.dispenseInterval());
        values.put("protocol", source.protocol());
        values.put("priorRxProtocol", source.priorPrescription());
        values.put("comment", source.providerNotes());
        values.put("special_instruction", trim(source.pharmacyInstructions()));
        var instructions = new StringBuilder();
        append(
                instructions,
                source.name() == null || source.name().isBlank()
                        ? "Source DIN " + source.din()
                        : source.name());
        append(instructions, strength);
        instructions.append('\n');
        append(instructions, source.dose());
        append(instructions, source.doseUnit());
        append(instructions, source.route());
        append(instructions, source.frequency());
        values.put("special", trim(instructions.toString()));
        values.put("custom_instructions", "1");
        values.put("custom_note", "0");
        return values;
    }

    private static boolean floatProjection(CdsNativeRowState baseline, CdsMedicationDto source)
            throws IOException {
        var range = CdsMedicationDose.parse(source.dose());
        return numeric(baseline, "takemin", range.isPresent() ? range.get().minimum() : 0f)
                && numeric(baseline, "takemax", range.isPresent() ? range.get().maximum() : 0f);
    }

    private static boolean numeric(CdsNativeRowState row, String column, float expected)
            throws IOException {
        int index = column(row, column);
        var witness = row.cells().get(index).exactNumeric();
        return witness != null
                && witness.equals(
                        new CdsExactNumeric(
                                        CdsExactNumeric.Codec.IEEE754_BINARY32_V1,
                                        HexFormat.of()
                                                .toHexDigits(Float.floatToRawIntBits(expected)))
                                .witness());
    }

    private static boolean matches(CdsNativeRowState row, String column, String expected)
            throws IOException {
        // IEEE columns are checked against exact typed projection witnesses, never display text.
        int index = column(row, column);
        if (CdsExactNumeric.codec(row.columns().get(index).type()) != null) return true;
        var cell = row.cells().get(index);
        if (expected == null) return cell.sqlNull();
        byte[] bytes = expected.getBytes(StandardCharsets.UTF_8);
        return !cell.sqlNull()
                && cell.bytes() == bytes.length
                && cell.sha256()
                        .equals(HexFormat.of().formatHex(CdsImportKeys.digest().digest(bytes)));
    }

    private static int column(CdsNativeRowState row, String column) throws IOException {
        for (int i = 0; i < row.columns().size(); i++)
            if (row.columns().get(i).name().equals(column)) return i;
        scope();
        return -1;
    }

    private static void verifyCurrent(CdsExportCapturedTextRowDto row, CdsNativeRowState state)
            throws IOException {
        if (!row.table().equals(state.table())
                || !row.fields()
                        .keySet()
                        .equals(
                                Set.copyOf(
                                        state.columns().stream()
                                                .map(CdsNativeRowDigest.Column::name)
                                                .toList()))) scope();
        for (var entry : row.exactNumbers().entrySet()) {
            var cell = state.cells().get(column(state, entry.getKey()));
            if (!entry.getValue().witness().equals(cell.exactNumeric())) scope();
        }
        for (int i = 0; i < state.columns().size(); i++) {
            String text = row.fields().get(state.columns().get(i).name());
            var cell = state.cells().get(i);
            if (text == null
                    ? !cell.sqlNull()
                    : cell.sqlNull()
                            || text.getBytes(StandardCharsets.UTF_8).length != cell.bytes()
                            || !HexFormat.of()
                                    .formatHex(
                                            CdsImportKeys.digest()
                                                    .digest(text.getBytes(StandardCharsets.UTF_8)))
                                    .equals(cell.sha256())) scope();
        }
    }

    private static void mark(
            CdsExportCapturedTextRowDto row,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> decisions,
            Group group,
            String rule) {
        for (String column : group.columns()) {
            var key = new CdsExportNativeFieldKeyDto(row.table(), row.recordKey(), column);
            decisions.put(
                    key,
                    new CdsExportFieldAssessmentDto(
                            decisions.get(key).presence(),
                            Outcome.REQUIRES_REVIEW,
                            group.path(),
                            rule));
        }
    }

    private static boolean hasOffset(CdsExportDateDto date) {
        if (date.lexical() == null) return false;
        int size =
                switch (date.precision()) {
                    case YEAR -> 4;
                    case YEAR_MONTH -> 7;
                    case DATE -> 10;
                    default -> date.lexical().length();
                };
        return date.lexical().length() > size;
    }

    private static void append(StringBuilder text, String part) {
        if (part != null && !part.isBlank()) {
            if (!text.isEmpty()) text.append(' ');
            text.append(part);
        }
    }

    private static String trim(String value) {
        return org.apache.commons.lang3.StringUtils.trimToNull(value);
    }

    private static String flag(Boolean value) {
        return value == null ? null : value ? "1" : "0";
    }

    private static String number(Integer value) {
        return value == null ? null : value.toString();
    }

    private static void scope() throws IOException {
        throw new IOException("CDS_EXPORT_MEDICATION_RECONCILIATION_SCOPE_INVALID");
    }

    /** A patient-specific data condition: the patient is held for review, not an I/O failure. */
    private static void review(String code) throws IOException {
        throw new io.github.carlos_emr.carlos.demographic.cdsexport.support
                .CdsExportMappingReviewException(code);
    }
}
