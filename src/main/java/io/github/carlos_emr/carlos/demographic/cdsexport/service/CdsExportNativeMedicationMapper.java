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

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeExtensionContract;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportMedicationDto.Field;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Outcome;
import java.io.IOException;
import java.math.BigInteger;
import java.util.*;

/**
 * Native prescriptions use standard fields only when their interpretation is established.
 * The complete captured source, including exact FLOAT witnesses, is also carried in the
 * category's versioned ResidualInfo. An unqualified regional identifier is never a DIN.
 * Imported projections continue through their separate, strict retained-source reconciler.
 */
public final class CdsExportNativeMedicationMapper {
    private static final Set<String> CONTEXT_RULES =
            Set.of(
                    "UNMAPPED_NATIVE_VALUE",
                    "REGIONAL_DRUG_IDENTIFIER_AUTHORITY_REQUIRES_REVIEW",
                    "UNSTRUCTURED_STRENGTH_REQUIRES_RECONCILIATION",
                    "MULTIPLE_NATIVE_DRUG_NAMES_REQUIRE_RECONCILIATION",
                    "MEDICATION_CURRENTNESS_NOT_ESTABLISHED",
                    "UNRECOGNIZED_PRESCRIPTION_STATUS",
                    "SQL_FLOAT_DISPLAY_IS_NOT_AN_EXACT_DOSE_WITNESS",
                    "DURATION_IS_NOT_KNOWN_DAYS",
                    "CONFLICTING_NATIVE_DOSE_UNITS",
                    "PRESCRIBER_NAME_MUST_NOT_BE_SPLIT");

    /**
     * One mapped prescription. {@code reasons} is the {@code CARLOS.Medication.Reasons.v1}
     * content, or null when the chart shows no indication for it.
     */
    public record Result(
            CdsExportMedicationDto value,
            String metadata,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields,
            boolean archived,
            String reasons) {
        public Result {
            fields = Map.copyOf(fields);
        }

        @Override
        public String toString() {
            return "CdsExportNativeMedicationMapper.Result[protected]";
        }
    }

    public Result map(
            int patient,
            CdsExportCapturedTextRowDto row,
            List<CdsExportCapturedTextRowDto> precision,
            Map<String, CdsExportCapturedTextRowDto> providers)
            throws IOException {
        return map(patient, row, precision, providers, null);
    }

    public Result map(
            int patient,
            CdsExportCapturedTextRowDto row,
            List<CdsExportCapturedTextRowDto> precision,
            Map<String, CdsExportCapturedTextRowDto> providers,
            CdsExportCapturedTextRowDto referenceRow)
            throws IOException {
        return map(patient, row, precision, providers, referenceRow, List.of());
    }

    /**
     * As {@link #map(int, CdsExportCapturedTextRowDto, List, Map, CdsExportCapturedTextRowDto)},
     * with the prescription's captured {@code drugReason} rows: the indication's ICD-9 code
     * becomes ProblemCode when it fits, and every reason the chart shows travels in
     * {@code CARLOS.Medication.Reasons.v1} ({@link CdsExportMedicationReasonMapper}).
     */
    public Result map(
            int patient,
            CdsExportCapturedTextRowDto row,
            List<CdsExportCapturedTextRowDto> precision,
            Map<String, CdsExportCapturedTextRowDto> providers,
            CdsExportCapturedTextRowDto referenceRow,
            List<CdsExportCapturedTextRowDto> reasonRows)
            throws IOException {
        var candidate =
                new CdsExportMedicationMapper().candidate(patient, row, precision, providers);
        var values = new EnumMap<Field, String>(Field.class);
        values.putAll(candidate.values());
        var fields = new LinkedHashMap<>(candidate.assessments());
        var source = row.fields();
        // These are import provenance markers, not ordinary native authoring fields. Without
        // a matching applied retained unit they cannot be converted into native authority.
        if (populated(source.get("cds_dose_text"))
                || populated(source.get("cds_currentness"))
                || "1".equals(source.get("cds_projection_incomplete"))) review();
        boolean archived = flag(source.get("archived"));
        String custom = source.get("customName"), brand = source.get("BN");
        // RxPrescriptionData.isCustom/getDrugName gives an authored custom name precedence.
        var reference = reference(row, referenceRow);
        String name = populated(custom) ? custom : brand;
        values.remove(Field.DrugIdentificationNumber);
        if (reference != null) values.put(Field.DrugIdentificationNumber, reference.din());
        if (populated(name)
                && CdsExportNativeFieldAssessments.legal(name, Field.DrugName.maximum()))
            values.put(Field.DrugName, name);
        else if (reference != null && !populated(custom)) values.remove(Field.DrugName);
        else review();
        String strength = source.get("dosage");
        if (populated(strength)) {
            if (!CdsExportNativeFieldAssessments.legal(strength, Field.DrugDescription.maximum()))
                review();
            values.put(Field.DrugDescription, strength);
        }
        String duration = source.get("duration"), unit = source.get("durunit");
        // The shared candidate wrote days, weeks as exact days, and months/years with their
        // unit (never the calculator's 30-day month); any other unit is not established.
        if (populated(duration) && CdsExportMedicationMapper.duration(duration, unit) == null)
            review();
        String sig = source.get("special");
        if (populated(sig)) {
            String pharmacy = source.get("special_instruction");
            String instructions =
                    populated(pharmacy) && !pharmacy.equals(sig)
                            ? sig + "\n[Native pharmacy instructions]\n" + pharmacy
                            : sig;
            if (!CdsExportNativeFieldAssessments.legal(
                    instructions, Field.PrescriptionInstructions.maximum())) review();
            values.put(Field.PrescriptionInstructions, instructions);
        }
        String doseUnit =
                doseUnit(source.get("unit"), source.get("dosage"), source.get("drug_form"));
        values.remove(Field.DosageUnitOfMeasure);
        if (doseUnit != null) values.put(Field.DosageUnitOfMeasure, doseUnit);
        String quantityUnit = source.get("unitName");
        if (populated(quantityUnit)
                && !quantityUnit.equals(source.get("unit"))
                && values.containsKey(Field.Quantity)) {
            // On the Rx page unitName is the quantity's unit (or the Mitte duration unit), never
            // a dose unit: "100" with unitName "mL" was authored as "100 mL", and Mitte "1" with
            // "month" as "1 month"; a bare number would misstate the amount dispensed. The CDS
            // importer copies the dose unit into both unit and unitName, so an equal pair is
            // that copy (kept by re-prescribing an imported drug), not a quantity unit.
            String authored = values.get(Field.Quantity) + " " + quantityUnit.strip();
            if (!CdsExportNativeFieldAssessments.legal(authored, Field.Quantity.maximum())) review();
            values.put(Field.Quantity, authored);
        }
        String status = source.get("rxStatus");
        boolean knownStatus =
                status != null
                        && Set.of(
                                        "New",
                                        "Active",
                                        "Suspended",
                                        "Aborted",
                                        "Completed",
                                        "Obsolete",
                                        "Nullified")
                                .contains(status);
        String archivedStatus = archived ? archivedStatus(source.get("archived_reason")) : null;
        if (archivedStatus != null) {
            // An archived row keeps the rxStatus it had (re-prescribing copies "Active"), so the
            // archive decides currentness; the stored status and reason stay in the residual.
            status = archivedStatus;
            knownStatus = true;
            values.put(Field.PrescriptionStatus, status);
            values.put(Field.PastMedications, "Y");
        } else if (!knownStatus) {
            values.remove(Field.PrescriptionStatus);
            if (!"1".equals(source.get("past_med"))) values.remove(Field.PastMedications);
        }
        boolean asNeeded = "1".equals(source.get("prn"));
        String frequencyValue = values.get(Field.Frequency);
        String treatmentValue = values.get(Field.TreatmentType);
        boolean prnResolved = false;
        if (asNeeded
                && frequencyValue != null
                && !frequencyValue.toUpperCase(Locale.ROOT).contains("PRN")
                && (treatmentValue == null || !treatmentValue.startsWith("PRN "))) {
            // The Rx page's as-needed box with an ordinary frequency means "BID as needed".
            String qualified = frequencyValue + " PRN";
            if (!CdsExportNativeFieldAssessments.legal(qualified, Field.Frequency.maximum()))
                review();
            values.put(Field.Frequency, qualified);
            prnResolved = true;
        }
        // A flag saying the start date is unknown takes precedence over a stored anchor. The
        // start date's precision rows are then not a StartDate: they travel, exactly, in the
        // residual with the drug row (below), so a year-only start is not read as a full date.
        var start = candidate.start();
        boolean startUnknown = "1".equals(source.get("start_date_unknown"));
        if (startUnknown) start = new CdsExportDateDto(CdsExportDateDto.Precision.ABSENT, null);
        for (var entry : new ArrayList<>(fields.entrySet())) {
            var key = entry.getKey();
            var assessment = entry.getValue();
            if (!key.table().equals("drugs")) {
                if (assessment.outcome() == Outcome.REQUIRES_REVIEW) review();
                if (startUnknown
                        && key.table().equals("partial_date")
                        && "StartDate".equals(assessment.destination()))
                    fields.put(
                            key,
                            new CdsExportFieldAssessmentDto(
                                    assessment.presence(),
                                    Outcome.MAPPED,
                                    "MedicationsAndTreatments/ResidualInfo",
                                    "UNKNOWN_START_DATE_PRECISION_CARRIED"));
                continue;
            }
            if (assessment.outcome() == Outcome.REQUIRES_REVIEW
                    && !CONTEXT_RULES.contains(assessment.rule())
                    && !(prnResolved
                            && key.column().equals("prn")
                            && assessment
                                    .rule()
                                    .equals("CONFLICTING_NATIVE_AS_NEEDED_INDICATOR"))
                    && !(archivedStatus != null
                            && Set.of("rxStatus", "past_med").contains(key.column())
                            && assessment
                                    .rule()
                                    .equals("CONFLICTING_MEDICATION_CURRENTNESS"))
                    && !(reference != null
                            && Set.of("BN", "customName").contains(key.column())
                            && Set.of("WIRE_TEXT_REQUIRES_REVIEW", "MEDICATION_IDENTITY_REQUIRED")
                                    .contains(assessment.rule()))
                    && !(key.column().equals("start_date_unknown")
                            && assessment
                                    .rule()
                                    .equals("UNKNOWN_START_DATE_REQUIRES_RECONCILIATION")))
                review();
            // The context contains SQL NULL and empty values distinctly, and contains every
            // physical field. This disposition does not claim a standard clinical projection.
            fields.put(
                    key,
                    new CdsExportFieldAssessmentDto(
                            assessment.presence(),
                            Outcome.MAPPED,
                            "MedicationsAndTreatments/ResidualInfo",
                            "EXACT_NATIVE_MEDICATION_SOURCE_CONTEXT"));
        }
        var details = new LinkedHashMap<String, Object>();
        details.put("version", 1);
        details.put("fields", source);
        details.put("exactNumbers", row.exactNumbers());
        // The date-precision rows (year-only or year-month dates) behind this drug's dates.
        details.put(
                "precision",
                precision.stream()
                        .sorted(java.util.Comparator.comparing(CdsExportCapturedTextRowDto::recordKey))
                        .map(CdsExportCapturedTextRowDto::fields)
                        .toList());
        details.put(
                "regionalIdentifierAuthority",
                reference == null ? "UNQUALIFIED" : reference.authority());
        if (referenceRow != null) {
            details.put("reference", referenceRow.fields());
            var assessments = new CdsExportNativeFieldAssessments(referenceRow, fields);
            for (String column : referenceRow.fields().keySet())
                assessments.set(
                        column,
                        Outcome.MAPPED,
                        "MedicationsAndTreatments/ResidualInfo",
                        "EXACT_FROZEN_PUBLIC_DRUG_REFERENCE");
        }
        details.put("currentness", knownStatus ? status : "UNESTABLISHED");
        String metadata = JsonMapper.builder().build().writeValueAsString(details);
        if (!CdsExportNativeFieldAssessments.legal(metadata, 1 << 24)) review();
        var reasons = new CdsExportMedicationReasonMapper().map(patient, row, reasonRows);
        if (reasons.problemCode() != null)
            values.put(Field.ProblemCode, reasons.problemCode());
        fields.putAll(reasons.fields(reasons.problemCode() != null));
        var value =
                new CdsExportMedicationDto(
                        candidate.written(),
                        start,
                        reference == null ? null : reference.components().getFirst().strength(),
                        reference == null ? null : reference.components().getFirst().unit(),
                        candidate.prescriber(),
                        values);
        CdsExportMedicationXmlWriter.validate(value);
        return new Result(value, metadata, fields, archived, reasons.residual());
    }

    /**
     * The CDS 5.2 prescription status of an archived native row, following the HL7 act statuses
     * the CDS values come from: a row replaced by a new prescription (re-prescribed, dose or
     * long-term status changed) is Obsolete; a deleted row or a prescribing error is Nullified
     * (entered in error); any discontinuation reason is Aborted (stopped before its end).
     */
    static String archivedStatus(String reason) {
        if (reason == null) return "Aborted";
        return switch (reason) {
            case "represcribed", "doseChange", "Updated Long-term status: ON",
                    "Updated Long-term status: OFF" -> "Obsolete";
            case "deleted", "prescribingError" -> "Nullified";
            default -> "Aborted";
        };
    }

    private static final java.util.regex.Pattern STRENGTH_UNIT =
            java.util.regex.Pattern.compile("[0-9]+(?:[.,][0-9]+)?\\s*([A-Za-z%\u00b5\u03bc][A-Za-z0-9%\u00b5\u03bc]*)");
    private static final List<String> COUNT_UNITS =
            List.of("capsule", "tablet", "patch", "pellet", "pill", "drop", "granule");

    /**
     * The administered-dose unit of a native prescription, or null when it is not recorded.
     * The Rx page stores the DrugRef strength unit of the product in {@code unit} ("mg" for
     * "500.0mg") while takemin/takemax count dispensable units ("take 1 tab"), so a {@code unit}
     * that names the product's strength unit is never the dose unit; the count unit then comes
     * from the drug form when the form is a counted form (tablet, capsule, ...), as the legacy
     * exporter derived it. A {@code unit} the strength does not mention (a custom drug's "puff",
     * an imported "tablet") is the dose unit as recorded. {@code unitName} is the quantity unit
     * and is handled with Quantity.
     */
    static String doseUnit(String unit, String strength, String form) {
        if (!populated(unit)) return null;
        String recorded = unit.strip();
        var strengthUnits = new java.util.HashSet<String>();
        if (populated(strength)) {
            var matcher = STRENGTH_UNIT.matcher(strength);
            while (matcher.find()) strengthUnits.add(matcher.group(1).toLowerCase(Locale.ROOT));
        }
        if (!strengthUnits.contains(recorded.toLowerCase(Locale.ROOT)))
            return CdsExportNativeFieldAssessments.legal(recorded, Field.DosageUnitOfMeasure.maximum())
                    ? recorded
                    : null;
        String shape = form == null ? "" : form.toLowerCase(Locale.ROOT);
        for (String count : COUNT_UNITS) if (shape.contains(count)) return count;
        return null;
    }

    private static CdsExportDrugReferenceDto reference(
            CdsExportCapturedTextRowDto row, CdsExportCapturedTextRowDto reference)
            throws IOException {
        String catalogue = row.fields().get("GCN_SEQNO");
        boolean selected;
        try {
            selected =
                    catalogue != null
                            && !catalogue.isEmpty()
                            && new BigInteger(catalogue).signum() != 0;
        } catch (NumberFormatException invalid) {
            review();
            return null;
        }
        if (!selected) {
            if (reference != null) review();
            return null;
        }
        if (reference == null
                || !reference.table().equals("medication_reference")
                || !reference.recordKey().equals(row.recordKey())
                || !reference.fields().keySet().equals(CdsExportDrugReferenceSourceReader.COLUMNS)
                || !Objects.equals(row.fields().get("drugid"), reference.fields().get("drugid"))
                || !Objects.equals(catalogue, reference.fields().get("GCN_SEQNO"))
                || !Objects.equals(
                        row.fields().get("regional_identifier"),
                        reference.fields().get("regional_identifier"))) review();
        CdsExportDrugReferenceDto value;
        try {
            var json =
                    JsonMapper.builder()
                            .enable(
                                    com.fasterxml.jackson.core.JsonParser.Feature
                                            .STRICT_DUPLICATE_DETECTION)
                            .enable(
                                    com.fasterxml.jackson.databind.DeserializationFeature
                                            .FAIL_ON_TRAILING_TOKENS)
                            .build();
            value =
                    json.readValue(
                            reference.fields().get("public_reference"),
                            CdsExportDrugReferenceDto.class);
        } catch (IOException | RuntimeException malformed) {
            review();
            return null;
        }
        if (value == null
                || !value.din().equals(row.fields().get("regional_identifier"))
                || value.components().isEmpty()) review();
        var component = value.components().getFirst();
        try {
            if (new java.math.BigDecimal(component.strength()).signum() <= 0) review();
        } catch (NumberFormatException invalid) {
            review();
        }
        // Guide 10.1 permits representative DINs whose product/name differs. Guide 10.5
        // requires the database's first ingredient amount, including combination products.
        // Authored names and complete native strength text remain independent source values.
        return value;
    }

    public void write(Result result, CdsExportXmlComposer.ContentWriter xml) throws IOException {
        xml.content(
                writer -> {
                    writer.start("cds", "ResidualInfo");
                    CdsExportNativeHistoryXmlWriter.residualElement(
                            writer,
                            CdsNativeExtensionContract.MEDICATION_SOURCE_CONTEXT,
                            "string",
                            CdsExportScreeningMapper.tokenSafe(result.metadata()));
                    CdsExportNativeHistoryXmlWriter.residualElement(
                            writer,
                            CdsNativeExtensionContract.MEDICATION_ARCHIVED,
                            "boolean",
                            Boolean.toString(result.archived()));
                    if (result.reasons() != null)
                        CdsExportNativeHistoryXmlWriter.residualElement(
                                writer,
                                CdsNativeExtensionContract.MEDICATION_REASONS,
                                CdsNativeExtensionContract.STRING_TYPE,
                                result.reasons());
                    writer.end();
                    new CdsExportMedicationXmlWriter().write(result.value(), writer);
                });
    }

    private static boolean populated(String value) {
        return value != null && !value.isEmpty();
    }

    private static boolean flag(String value) throws IOException {
        if ("0".equals(value)) return false;
        if ("1".equals(value)) return true;
        review();
        return false;
    }

    private static void review() throws IOException {
        throw new io.github.carlos_emr.carlos.demographic.cdsexport.support
                .CdsExportMappingReviewException("CDS_EXPORT_NATIVE_MEDICATION_REQUIRES_REVIEW");
    }
}
