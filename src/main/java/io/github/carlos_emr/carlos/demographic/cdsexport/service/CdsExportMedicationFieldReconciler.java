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
    public record Result(
            String sourceManifestSha256,
            String unitAddress,
            CdsExportMedicationMappingDto mapping,
            Set<String> preparedPaths,
            Set<String> currentPaths) {
        public Result {
            preparedPaths = Set.copyOf(preparedPaths);
            currentPaths = Set.copyOf(currentPaths);
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
        var old = new CdsExportPreparedMedicationMapper().map(source);
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
        for (var group : GROUPS) {
            boolean proved = true;
            for (String column : group.columns())
                proved &= matches(baseline, column, expected.get(column));
            if (group.path().equals("Dosage")) proved &= floatProjection(baseline, source);
            if (!proved) {
                mark(current, decisions, group, "PREPARED_NATIVE_PROJECTION_NOT_PROVEN");
                continue;
            }
            boolean unchanged = Collections.disjoint(group.columns(), difference.changedColumns());
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
        boolean blocked =
                decisions.values().stream()
                        .anyMatch(field -> field.outcome() == Outcome.REQUIRES_REVIEW);
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
                currentPaths);
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
        values.put("duration", trim(source.durationDays()));
        values.put("durunit", source.durationDays() == null ? null : "D");
        values.put("quantity", trim(source.quantity()));
        values.put("repeat", number(source.repeats()));
        values.put("refill_duration", number(source.refillDurationDays()));
        values.put("refill_quantity", number(source.refillQuantity()));
        values.put("freqcode", trim(source.frequency()));
        values.put("route", trim(source.route()));
        values.put("drug_form", trim(source.form()));
        values.put("unit", trim(source.doseUnit()));
        values.put("unitName", trim(source.doseUnit()));
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
                        source.treatmentType() != null && source.treatmentType().startsWith("PRN ")
                                || "PRN".equals(source.frequency())));
        boolean archived = source.currentness() == CdsMedicationDto.Currentness.HISTORICAL;
        values.put("archived", flag(archived));
        values.put(
                "archived_reason",
                archived
                        ? "cds:"
                                + (source.prescriptionStatus() == null
                                        ? "PastMedications"
                                        : source.prescriptionStatus())
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

    private static void review(String code) throws IOException {
        throw new IOException(code);
    }
}
