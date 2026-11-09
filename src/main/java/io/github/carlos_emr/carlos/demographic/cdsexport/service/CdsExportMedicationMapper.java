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

import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportMedicationDto.Field;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Outcome;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Native prescription interpretation from frozen values. Every populated field has a disposition.
 * No dose is recovered from rounded SQL FLOAT text, no strength/name/prescriber is split, and
 * archive/visibility/end dates never turn a prescription into an asserted current treatment.
 * Retained preparations, related scripts/pharmacies and authors need separate reconciliation.
 */
public final class CdsExportMedicationMapper {
    private static final Set<String> STATUSES =
            Set.of("New", "Active", "Suspended", "Aborted", "Completed", "Obsolete", "Nullified");
    private static final Set<String> HISTORICAL =
            Set.of("Aborted", "Completed", "Obsolete", "Nullified");
    private static final Set<String> TREATMENTS =
            Set.of("CHRON", "ACU", "ONET", "PRN Long-term", "PRN Short-term");

    public CdsExportMedicationMappingDto map(
            int patient,
            CdsExportCapturedTextRowDto row,
            List<CdsExportCapturedTextRowDto> precision)
            throws IOException {
        return map(patient, row, precision, Map.of());
    }

    public CdsExportMedicationMappingDto map(
            int patient,
            CdsExportCapturedTextRowDto row,
            List<CdsExportCapturedTextRowDto> precision,
            Map<String, CdsExportCapturedTextRowDto> providers)
            throws IOException {
        var candidate = candidate(patient, row, precision, providers);
        boolean blocked =
                candidate.assessments().values().stream()
                        .anyMatch(field -> field.outcome() == Outcome.REQUIRES_REVIEW);
        return new CdsExportMedicationMappingDto(
                blocked
                        ? null
                        : new CdsExportMedicationDto(
                                candidate.written(),
                                candidate.start(),
                                null,
                                null,
                                candidate.prescriber(),
                                candidate.values()),
                candidate.assessments());
    }

    record Candidate(
            CdsExportDateDto written,
            CdsExportDateDto start,
            CdsExportPublicProviderDto prescriber,
            Map<Field, String> values,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> assessments) {
        Candidate {
            values = Map.copyOf(values);
            assessments = Map.copyOf(assessments);
        }

        @Override
        public String toString() {
            return "CdsExportMedicationMapper.Candidate[protected]";
        }
    }

    Candidate candidate(
            int patient,
            CdsExportCapturedTextRowDto row,
            List<CdsExportCapturedTextRowDto> precision,
            Map<String, CdsExportCapturedTextRowDto> providers)
            throws IOException {
        long id = verifiedPatientRow(patient, row, precision, providers);
        var assessments =
                new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        var source = new CdsExportNativeFieldAssessments(row, assessments);
        var values = new EnumMap<Field, String>(Field.class);
        initializeIdentity(source, values);
        text(source, values, "cds_dose_text", Field.Dosage);
        text(source, values, "drug_form", Field.Form);
        text(source, values, "route", Field.Route);
        text(source, values, "freqcode", Field.Frequency);
        text(source, values, "quantity", Field.Quantity);
        text(source, values, "refill_quantity", Field.RefillQuantity);
        text(source, values, "refill_duration", Field.RefillDuration);
        text(source, values, "repeat", Field.NumberOfRefills);
        text(source, values, "comment", Field.Notes);
        text(source, values, "special_instruction", Field.PrescriptionInstructions);
        text(source, values, "dispense_interval", Field.DispenseInterval);
        text(source, values, "protocol", Field.ProtocolIdentifier);
        text(source, values, "priorRxProtocol", Field.PriorPrescriptionReferenceIdentifier);
        for (var entry :
                Map.of(
                                "repeat",
                                Field.NumberOfRefills,
                                "refill_quantity",
                                Field.RefillQuantity,
                                "refill_duration",
                                Field.RefillDuration)
                        .entrySet()) {
            String number = values.get(entry.getValue());
            if (number != null
                    && (!number.matches("[0-9]+")
                            || new java.math.BigInteger(number).bitLength() > 31))
                source.review(
                        entry.getKey(),
                        entry.getValue().name(),
                        "NONNEGATIVE_INTEGER_CAPACITY_REQUIRES_REVIEW");
        }
        String quantity = values.get(Field.Quantity);
        if (quantity != null && !quantity.matches("[0-9]+(?:\\.[0-9]+)?"))
            source.review("quantity", Field.Quantity.name(), "NUMERIC_QUANTITY_REQUIRES_REVIEW");
        String unit =
                source.text(
                        "unit",
                        Field.DosageUnitOfMeasure.name(),
                        Field.DosageUnitOfMeasure.maximum());
        String unitName =
                source.text(
                        "unitName",
                        Field.DosageUnitOfMeasure.name(),
                        Field.DosageUnitOfMeasure.maximum());
        if (unit != null && unitName != null && !unit.equals(unitName)) {
            source.review(
                    "unit", Field.DosageUnitOfMeasure.name(), "CONFLICTING_NATIVE_DOSE_UNITS");
            source.review(
                    "unitName", Field.DosageUnitOfMeasure.name(), "CONFLICTING_NATIVE_DOSE_UNITS");
        }
        put(values, Field.DosageUnitOfMeasure, unit == null ? unitName : unit);
        text(source, values, "duration", Field.Duration);
        if (values.containsKey(Field.Duration)) {
            if (!values.get(Field.Duration).matches("[0-9]+"))
                source.review(
                        "duration", Field.Duration.name(), "NUMERIC_DAY_DURATION_REQUIRES_REVIEW");
            if ("D".equals(source.raw("durunit")))
                source.set(
                        "durunit", Outcome.MAPPED, Field.Duration.name(), "EXPLICIT_DURATION_DAYS");
            else {
                source.review("durunit", Field.Duration.name(), "DURATION_IS_NOT_KNOWN_DAYS");
                source.review("duration", Field.Duration.name(), "DURATION_IS_NOT_KNOWN_DAYS");
            }
        }
        for (var pair :
                Map.of(
                                "long_term",
                                Field.LongTermMedication,
                                "past_med",
                                Field.PastMedications,
                                "patient_compliance",
                                Field.PatientCompliance,
                                "nosubs",
                                Field.SubstitutionNotAllowed,
                                "non_authoritative",
                                Field.NonAuthoritativeIndicator)
                        .entrySet()) {
            String raw = source.raw(pair.getKey());
            if (raw == null) continue;
            if (raw.equals("0") || raw.equals("1")) {
                values.put(pair.getValue(), raw.equals("1") ? "Y" : "N");
                source.set(
                        pair.getKey(),
                        Outcome.MAPPED,
                        pair.getValue().name(),
                        "EXACT_NULLABLE_NATIVE_BOOLEAN");
            } else
                source.review(
                        pair.getKey(), pair.getValue().name(), "NATIVE_BOOLEAN_REQUIRES_REVIEW");
        }
        String status =
                source.text(
                        "rxStatus",
                        Field.PrescriptionStatus.name(),
                        Field.PrescriptionStatus.maximum());
        if (status != null && !STATUSES.contains(status))
            source.review(
                    "rxStatus",
                    Field.PrescriptionStatus.name(),
                    "UNRECOGNIZED_PRESCRIPTION_STATUS");
        else put(values, Field.PrescriptionStatus, status);
        String treatment =
                source.text(
                        "eTreatmentType",
                        Field.TreatmentType.name(),
                        Field.TreatmentType.maximum());
        if (treatment != null && !TREATMENTS.contains(treatment))
            source.review(
                    "eTreatmentType", Field.TreatmentType.name(), "UNRECOGNIZED_TREATMENT_TYPE");
        else put(values, Field.TreatmentType, treatment);
        currentness(source, values, status);
        boolean prn =
                "PRN".equals(values.get(Field.Frequency))
                        || treatment != null && treatment.startsWith("PRN ");
        if (("1".equals(source.raw("prn")) && prn) || ("0".equals(source.raw("prn")) && !prn))
            source.set(
                    "prn",
                    Outcome.MAPPED,
                    "Frequency/TreatmentType",
                    "CONSISTENT_NATIVE_AS_NEEDED_INDICATOR");
        else if (source.raw("prn") != null)
            source.review(
                    "prn", "Frequency/TreatmentType", "CONFLICTING_NATIVE_AS_NEEDED_INDICATOR");
        if (treatment != null && TREATMENTS.contains(treatment)) {
            boolean shortTerm = Set.of("ACU", "ONET", "PRN Short-term").contains(treatment);
            if (("1".equals(source.raw("short_term")) && shortTerm)
                    || ("0".equals(source.raw("short_term")) && !shortTerm))
                source.set(
                        "short_term",
                        Outcome.MAPPED,
                        "TreatmentType",
                        "CONSISTENT_NATIVE_TREATMENT_TYPE_INDICATOR");
            else if (source.raw("short_term") != null)
                source.review(
                        "short_term",
                        "TreatmentType",
                        "CONFLICTING_NATIVE_TREATMENT_TYPE_INDICATOR");
        }
        // These columns have no independently proved, lossless field interpretation yet.
        dose(row, source, values);
        if (source.raw("dosage") != null && !source.raw("dosage").isEmpty())
            source.review("dosage", "Strength", "UNSTRUCTURED_STRENGTH_REQUIRES_RECONCILIATION");
        var prescriber = prescriber(source, providers, assessments);
        if ("0".equals(source.raw("archived")))
            source.operational("archived", "NATIVE_ARCHIVE_FLAG_NOT_SET");
        if ("0".equals(source.raw("start_date_unknown")))
            source.operational("start_date_unknown", "NATIVE_START_DATE_FLAG_NOT_SET");
        var dates = new LinkedHashMap<Integer, List<CdsExportNativeFieldAssessments>>();
        dates.put(3, new ArrayList<>());
        dates.put(5, new ArrayList<>());
        var seen = new HashSet<String>();
        for (var entry : precision) {
            shape(
                    entry,
                    "partial_date",
                    CdsExportPatientTableReadSupport.Plan.PARTIAL_DATE_MEDICATIONS);
            if (!seen.add(entry.recordKey())
                    || !CdsExportPatientTableSourceReader.key(integer(entry.fields().get("id")))
                            .equals(entry.recordKey())
                    || !"2".equals(entry.fields().get("table_name"))
                    || integer(entry.fields().get("table_id")) != id) scope();
            var state = new CdsExportNativeFieldAssessments(entry, assessments);
            state.operational("id", "CAPTURED_PRECISION_ROW_IDENTITY");
            state.operational("table_name", "MEDICATION_PRECISION_OWNER_TYPE");
            state.operational("table_id", "VERIFIED_MEDICATION_PARENT_IDENTITY");
            String field = state.raw("field_name");
            if ("3".equals(field) || "5".equals(field)) {
                state.operational("field_name", "EXPLICIT_NATIVE_DATE_FIELD");
                dates.get(Integer.parseInt(field)).add(state);
            } else {
                state.review("field_name", "", "UNKNOWN_MEDICATION_PRECISION_FIELD");
                state.review("format", "", "UNKNOWN_MEDICATION_PRECISION_FIELD");
            }
        }
        var written = date(source, "written_date", "PrescriptionWrittenDate", dates.get(3));
        var start = date(source, "rx_date", "StartDate", dates.get(5));
        // A date anchor cannot override the explicit unknown-start marker.
        if ("1".equals(source.raw("start_date_unknown")))
            source.review(
                    "start_date_unknown",
                    "StartDate",
                    "UNKNOWN_START_DATE_REQUIRES_RECONCILIATION");
        return new Candidate(written, start, prescriber, values, assessments);
    }

    private static void initializeIdentity(
            CdsExportNativeFieldAssessments source, EnumMap<Field, String> values)
            throws IOException {
        source.operational("drugid", "NATIVE_OCCURRENCE_IDENTITY");
        source.operational("demographic_no", "SEALED_PATIENT_IDENTITY");
        for (String field : List.of("lastUpdateDate", "create_date", "position"))
            source.operational(field, "NATIVE_STORAGE_OR_DISPLAY_METADATA");
        for (String field :
                List.of(
                        "custom_instructions",
                        "custom_note",
                        "hide_cpp",
                        "hide_from_drug_profile",
                        "dispenseInternal")) {
            if ("0".equals(source.raw(field)))
                source.operational(field, "NATIVE_OPTION_NOT_ENABLED");
        }
        if ("0".equals(source.raw("GCN_SEQNO")))
            source.operational("GCN_SEQNO", "NATIVE_CATALOGUE_IDENTIFIER_UNSPECIFIED");
        if ("0".equals(source.raw("cds_projection_incomplete")))
            source.operational(
                    "cds_projection_incomplete", "IMPORT_PROJECTION_FLAG_NOT_A_COMPLETENESS_PROOF");
        String name = source.text("customName", Field.DrugName.name(), Field.DrugName.maximum());
        String brand = source.text("BN", Field.DrugName.name(), Field.DrugName.maximum());
        if (name != null && brand != null && !name.equals(brand)) {
            source.review(
                    "customName",
                    Field.DrugName.name(),
                    "MULTIPLE_NATIVE_DRUG_NAMES_REQUIRE_RECONCILIATION");
            source.review(
                    "BN",
                    Field.DrugName.name(),
                    "MULTIPLE_NATIVE_DRUG_NAMES_REQUIRE_RECONCILIATION");
        }
        put(values, Field.DrugName, name == null ? brand : name);
        String din = source.raw("regional_identifier");
        if (din != null && !din.isEmpty()) {
            // The physical column carries no authority discriminator. Eight digits do not
            // prove a Health Canada DIN; a qualified catalogue/preparation must bind it.
            source.review(
                    "regional_identifier",
                    Field.DrugIdentificationNumber.name(),
                    "REGIONAL_DRUG_IDENTIFIER_AUTHORITY_REQUIRES_REVIEW");
        }
        if ((values.get(Field.DrugName) == null || values.get(Field.DrugName).isBlank())
                && !values.containsKey(Field.DrugIdentificationNumber))
            source.review("customName", Field.DrugName.name(), "MEDICATION_IDENTITY_REQUIRED");
    }

    private static long verifiedPatientRow(
            int patient,
            CdsExportCapturedTextRowDto row,
            List<CdsExportCapturedTextRowDto> precision,
            Map<String, CdsExportCapturedTextRowDto> providers)
            throws IOException {
        java.util.Objects.requireNonNull(providers);
        shape(row, "drugs", CdsExportPatientTableReadSupport.Plan.DRUGS);
        long id = integer(row.fields().get("drugid"));
        if (patient < 1
                || !Integer.toString(patient).equals(row.fields().get("demographic_no"))
                || !CdsExportPatientTableSourceReader.key(id).equals(row.recordKey())
                || precision.size() > 256) scope();
        return id;
    }

    private static void dose(
            CdsExportCapturedTextRowDto row,
            CdsExportNativeFieldAssessments source,
            Map<Field, String> values) {
        if (source.raw("takemin") == null && source.raw("takemax") == null) return;
        for (String column : List.of("takemin", "takemax"))
            if (source.raw(column) != null)
                source.review(
                        column,
                        Field.Dosage.name(),
                        "SQL_FLOAT_DISPLAY_IS_NOT_AN_EXACT_DOSE_WITNESS");
        var low = row.exactNumbers().get("takemin");
        var high = row.exactNumbers().get("takemax");
        if (low == null
                || high == null
                || low.codec()
                        != io.github.carlos_emr.carlos.demographic.cds.contract.CdsExactNumeric
                                .Codec.IEEE754_BINARY32_V1
                || high.codec() != low.codec()) return;
        float minimum = Float.intBitsToFloat((int) Long.parseUnsignedLong(low.bits(), 16));
        float maximum = Float.intBitsToFloat((int) Long.parseUnsignedLong(high.bits(), 16));
        if (minimum < 0
                || maximum < minimum
                || low.bits().equals("80000000")
                || high.bits().equals("80000000")) return;
        String literal = values.get(Field.Dosage);
        if (literal != null) {
            if (!literal.matches("[0-9]+(?:\\.[0-9]+)?(?:-[0-9]+(?:\\.[0-9]+)?)?")) return;
            var parts = literal.split("-", -1);
            try {
                if (Float.floatToRawIntBits(Float.parseFloat(parts[0]))
                                != Float.floatToRawIntBits(minimum)
                        || Float.floatToRawIntBits(Float.parseFloat(parts[parts.length - 1]))
                                != Float.floatToRawIntBits(maximum)) return;
            } catch (NumberFormatException invalid) {
                return;
            }
        } else {
            // An all-zero legacy default without an explicit dose is not clinical evidence.
            if (minimum == 0f && maximum == 0f) return;
            String first = new java.math.BigDecimal(low.decimal()).toPlainString();
            String last = new java.math.BigDecimal(high.decimal()).toPlainString();
            literal = minimum == maximum ? first : first + "-" + last;
            if (!CdsExportNativeFieldAssessments.legal(literal, Field.Dosage.maximum())) return;
            values.put(Field.Dosage, literal);
        }
        source.set(
                "takemin",
                Outcome.MAPPED,
                Field.Dosage.name(),
                "EXACT_IEEE_DOSE_BOUND_WITH_ROUNDTRIP_DECIMAL");
        source.set(
                "takemax",
                Outcome.MAPPED,
                Field.Dosage.name(),
                "EXACT_IEEE_DOSE_BOUND_WITH_ROUNDTRIP_DECIMAL");
    }

    private static CdsExportPublicProviderDto prescriber(
            CdsExportNativeFieldAssessments source,
            Map<String, CdsExportCapturedTextRowDto> providers,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> decisions)
            throws IOException {
        String id = source.raw("provider_no");
        if (id == null || id.isEmpty()) {
            if (source.raw("outside_provider_name") != null
                    && !source.raw("outside_provider_name").isEmpty())
                source.review(
                        "outside_provider_name",
                        "PrescribedBy/Name",
                        "PRESCRIBER_NAME_MUST_NOT_BE_SPLIT");
            return null;
        }
        var row = providers.get(id);
        if (row == null) {
            source.review("provider_no", "PrescribedBy", "EXACT_CAPTURED_PRESCRIBER_REQUIRED");
            return null;
        }
        if (!id.equals(row.fields().get("provider_no"))) scope();
        var mapping =
                new CdsExportPublicProviderMapper()
                        .map(row, CdsExportPublicProviderMapper.Target.PRESCRIBER);
        decisions.putAll(mapping.fields());
        if (!mapping.representable()) {
            source.review(
                    "provider_no", "PrescribedBy", "PUBLIC_PRESCRIBER_VALUES_REQUIRES_REVIEW");
            return null;
        }
        var value = mapping.value();
        source.set(
                "provider_no", Outcome.MAPPED, "PrescribedBy", "EXACT_CAPTURED_PUBLIC_PRESCRIBER");
        String outsideName = source.raw("outside_provider_name");
        String outsideOhip = source.raw("outside_provider_ohip");
        if (outsideName != null && !outsideName.isEmpty()) {
            String joined = (value.firstName() + " " + value.lastName()).strip();
            if (outsideName.equals(joined))
                source.set(
                        "outside_provider_name",
                        Outcome.MAPPED,
                        "PrescribedBy/Name",
                        "CONSISTENT_CAPTURED_PRESCRIBER_NAME");
            else
                source.review(
                        "outside_provider_name",
                        "PrescribedBy/Name",
                        "CONFLICTING_PRESCRIBER_NAME");
        }
        if (outsideOhip != null && !outsideOhip.isEmpty()) {
            if (outsideOhip.equals(value.ohipNumber()))
                source.set(
                        "outside_provider_ohip",
                        Outcome.MAPPED,
                        "PrescribedBy/OHIPPhysicianId",
                        "CONSISTENT_CAPTURED_PRESCRIBER_IDENTIFIER");
            else
                source.review(
                        "outside_provider_ohip",
                        "PrescribedBy/OHIPPhysicianId",
                        "CONFLICTING_PRESCRIBER_IDENTIFIER");
        }
        return value;
    }

    private static void currentness(
            CdsExportNativeFieldAssessments source, Map<Field, String> values, String status) {
        String past = values.get(Field.PastMedications);
        String expected;
        if ("Active".equals(status)) {
            expected = "CURRENT";
            if ("Y".equals(past)) {
                source.review(
                        "rxStatus", "PrescriptionStatus", "CONFLICTING_MEDICATION_CURRENTNESS");
                source.review("past_med", "PastMedications", "CONFLICTING_MEDICATION_CURRENTNESS");
            }
        } else if ("New".equals(status)) expected = "NEW";
        else if ("Suspended".equals(status)) expected = "SUSPENDED";
        else if (HISTORICAL.contains(status == null ? "" : status)) {
            expected = "HISTORICAL";
            if ("N".equals(past)) {
                source.review(
                        "rxStatus", "PrescriptionStatus", "CONFLICTING_MEDICATION_CURRENTNESS");
                source.review("past_med", "PastMedications", "CONFLICTING_MEDICATION_CURRENTNESS");
            }
        } else expected = "Y".equals(past) ? "HISTORICAL" : "UNKNOWN";
        String recorded = source.raw("cds_currentness");
        if (recorded != null && recorded.equals(expected))
            source.operational("cds_currentness", "CONSISTENT_IMPORTED_CURRENTNESS_WITNESS");
        if (expected.equals("UNKNOWN"))
            source.review(
                    "rxStatus", "PrescriptionStatus", "MEDICATION_CURRENTNESS_NOT_ESTABLISHED");
    }

    private static CdsExportDateDto date(
            CdsExportNativeFieldAssessments source,
            String column,
            String path,
            List<CdsExportNativeFieldAssessments> metadata) {
        var formats = new ArrayList<String>();
        metadata.forEach(row -> formats.add(row.raw("format")));
        try {
            var value = new CdsExportNativeDateMapper().map(source.raw(column), formats);
            // The legacy 0001-01-01 default is not an independently established clinical date.
            if ("0001-01-01".equals(source.raw(column)))
                throw new IOException("CDS_EXPORT_LEGACY_DATE_SENTINEL_REQUIRES_REVIEW");
            source.set(
                    column,
                    value.precision() == CdsExportDateDto.Precision.ABSENT
                            ? Outcome.ABSENT
                            : Outcome.MAPPED,
                    path,
                    "NATIVE_DATE_WITH_EXPLICIT_PRECISION");
            metadata.forEach(
                    row ->
                            row.set(
                                    "format",
                                    Outcome.MAPPED,
                                    path,
                                    "NATIVE_EXPLICIT_DATE_PRECISION"));
            return value;
        } catch (IOException invalid) {
            source.review(column, path, invalid.getMessage());
            metadata.forEach(row -> row.review("format", path, invalid.getMessage()));
            return new CdsExportDateDto(CdsExportDateDto.Precision.ABSENT, null);
        }
    }

    private static void text(
            CdsExportNativeFieldAssessments source,
            Map<Field, String> values,
            String column,
            Field field) {
        put(values, field, source.text(column, field.name(), field.maximum()));
    }

    private static void put(Map<Field, String> values, Field field, String value) {
        if (value != null) values.put(field, value);
    }

    private static void shape(
            CdsExportCapturedTextRowDto row,
            String table,
            CdsExportPatientTableReadSupport.Plan plan)
            throws IOException {
        if (row == null
                || !table.equals(row.table())
                || !row.fields().keySet().equals(Set.copyOf(plan.columns()))) scope();
    }

    private static long integer(String value) throws IOException {
        try {
            int number = Integer.parseInt(value);
            if (!Integer.toString(number).equals(value)) scope();
            return number;
        } catch (NumberFormatException invalid) {
            scope();
            return 0;
        }
    }

    private static void scope() throws IOException {
        throw new IOException("CDS_EXPORT_MEDICATION_CAPTURE_SCOPE_INVALID");
    }
}
