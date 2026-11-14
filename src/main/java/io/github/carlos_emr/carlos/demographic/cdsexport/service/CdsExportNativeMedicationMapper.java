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
                    // A duration with no Duration element: an amount saved with no unit (typed
                    // into the Rx card's duration box when the directions named none), a unit that
                    // is not a day, week, month or year (the card's unit box is free text and the
                    // column keeps one character), or a duration typed as text. No Duration is
                    // written, and the exact duration and durunit travel in the residual.
                    CdsExportMedicationMapper.DURATION_WITHOUT_UNIT,
                    CdsExportMedicationMapper.DURATION_UNIT_UNKNOWN,
                    CdsExportMedicationMapper.DURATION_NOT_A_NUMBER,
                    // A quantity that is not a plain number ("30 tabs", "1 bottle": the legacy
                    // Rx form, favourites, the REST service and OSCAR 19 data store free text), a
                    // negative refill count, quantity or duration, a flag other than 0 or 1 and a
                    // treatment type outside the CDS list: the element is left out, never
                    // rewritten, and the stored value travels in the residual.
                    "NUMERIC_QUANTITY_REQUIRES_REVIEW",
                    "NONNEGATIVE_INTEGER_CAPACITY_REQUIRES_REVIEW",
                    "NATIVE_BOOLEAN_REQUIRES_REVIEW",
                    "UNRECOGNIZED_TREATMENT_TYPE",
                    // OSCAR's "no date" defaults (0001-01-01, the zero date) and a date whose
                    // precision rows disagree are not a recorded date: no date element is
                    // written, and the column and its precision rows travel in the residual.
                    "CDS_EXPORT_LEGACY_DATE_SENTINEL_REQUIRES_REVIEW",
                    "CDS_EXPORT_NATIVE_DATE_PRECISION_REQUIRES_REVIEW",
                    // A precision row for a field other than the written or start date.
                    "UNKNOWN_MEDICATION_PRECISION_FIELD",
                    // The Rx page's Note button, or a row that names no drug at all: DrugName is
                    // the exporter's label (below) and the row travels in the residual.
                    "MEDICATION_IDENTITY_REQUIRED",
                    "CONFLICTING_NATIVE_DOSE_UNITS",
                    "PRESCRIBER_NAME_MUST_NOT_BE_SPLIT",
                    // A provider number with no provider record (migrated data): nobody is
                    // named as PrescribedBy, and the stored number travels in the residual.
                    "EXACT_CAPTURED_PRESCRIBER_REQUIRED",
                    // The Rx page saves "Past medication" and the status select independently
                    // (prescribe.jsp), so "Yes" beside Active, or "No" beside Completed, is an
                    // ordinary saved prescription. Both standard elements are written as stored.
                    "CONFLICTING_MEDICATION_CURRENTNESS",
                    // The treatment type select and the hidden short-term box (driven by the
                    // long-term radio) are also saved independently; TreatmentType is written as
                    // chosen and the short-term flag travels in the residual.
                    "CONFLICTING_NATIVE_TREATMENT_TYPE_INDICATOR",
                    // The as-needed box unticked beside a "PRN" frequency or a PRN treatment type:
                    // Frequency and TreatmentType are written as chosen, the flag in the residual.
                    "CONFLICTING_NATIVE_AS_NEEDED_INDICATOR",
                    // The Rx page's "outside provider" box: the CARLOS provider recorded a
                    // prescription another physician wrote. The recorder is then not written as
                    // PrescribedBy, and the outside name (one string, never split into a
                    // personNameSimple) and number travel in the residual.
                    "CONFLICTING_PRESCRIBER_NAME",
                    "CONFLICTING_PRESCRIBER_IDENTIFIER");

    /**
     * DrugName of a prescription written with the Rx page's Note button ({@code custom_note} 1):
     * free text with no drug. CDS 5.2 and the CARLOS importer need a name or a DIN for a
     * medication, so the name says what the entry is; the note itself is the
     * PrescriptionInstructions, and the row travels in the residual.
     */
    static final String NOTE_NAME = "Prescription note (no drug named)";

    /** DrugName of any other row that names no drug (a migrated row with an empty name). */
    static final String UNNAMED = "Unnamed prescription";

    /**
     * DrugName of a row whose stored name is longer than the element (BN holds 255 characters,
     * DrugName 120). The name is never clipped: it is the first line of Notes, after this label.
     */
    static final String LONG_NAME = "Drug name longer than 120 characters (see Notes)";

    static final String LONG_NAME_LABEL = "Drug name: ";

    /**
     * The rules of a date with no date element, which its precision rows share: the rows travel
     * in the residual's {@code precision} list with the drug row.
     */
    private static final Set<String> CARRIED_PRECISION_RULES =
            Set.of(
                    "CDS_EXPORT_LEGACY_DATE_SENTINEL_REQUIRES_REVIEW",
                    "CDS_EXPORT_NATIVE_DATE_PRECISION_REQUIRES_REVIEW",
                    "UNKNOWN_MEDICATION_PRECISION_FIELD");

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
        // A blank name names nothing (DrugName is a token: it would be written empty).
        String name = populated(custom) && !custom.isBlank() ? custom : brand;
        values.remove(Field.DrugIdentificationNumber);
        if (reference != null) values.put(Field.DrugIdentificationNumber, reference.din());
        String longName = null;
        boolean named = populated(name) && !name.isBlank();
        if (named && CdsExportNativeFieldAssessments.legal(name, Field.DrugName.maximum()))
            values.put(Field.DrugName, name);
        else if (reference != null && !(populated(custom) && !custom.isBlank()))
            values.remove(Field.DrugName);
        else if (!named)
            // No drug is named: the Rx page's Note button (free text only), or a migrated row
            // with empty names. The entry says so instead of holding the chart.
            values.put(Field.DrugName, "1".equals(source.get("custom_note")) ? NOTE_NAME : UNNAMED);
        else if (CdsExportNativeFieldAssessments.legal(name, Integer.MAX_VALUE)) {
            // Longer than DrugName: never clipped. It leads Notes when Notes can hold it, and
            // is exact in the residual either way.
            longName = name;
            values.put(Field.DrugName, LONG_NAME);
        } else review();
        String strength = source.get("dosage");
        if (populated(strength)) {
            // A strength longer than DrugDescription is left out, never clipped (residual).
            if (CdsExportNativeFieldAssessments.legal(strength, Field.DrugDescription.maximum()))
                values.put(Field.DrugDescription, strength);
            else if (!CdsExportNativeFieldAssessments.legal(strength, Integer.MAX_VALUE)) review();
        }
        // The shared candidate wrote days, weeks as exact days, and months/years with their
        // unit (never the calculator's 30-day month). It wrote no Duration for one the
        // prescription does not state (the Rx directions parser's "0", stored for "use as
        // directed" or "2 puffs prn"), for an amount saved with no unit, for a unit that is not a
        // day, week, month or year, nor for a duration typed as text: all are ordinary saved
        // prescriptions, and their exact columns travel in the residual.
        String sig = source.get("special");
        if (populated(sig)) {
            String pharmacy = source.get("special_instruction");
            String instructions =
                    populated(pharmacy) && !pharmacy.equals(sig)
                            ? sig + "\n[Native pharmacy instructions]\n" + pharmacy
                            : sig;
            if (CdsExportNativeFieldAssessments.legal(
                    instructions, Field.PrescriptionInstructions.maximum()))
                values.put(Field.PrescriptionInstructions, instructions);
            else if (!CdsExportNativeFieldAssessments.legal(instructions, Integer.MAX_VALUE))
                review();
            else if (CdsExportNativeFieldAssessments.legal(
                    sig, Field.PrescriptionInstructions.maximum()))
                // Together they exceed the element: the SIG as printed is written, and the
                // pharmacy instructions travel in the residual with the rest of the row.
                values.put(Field.PrescriptionInstructions, sig);
            // A SIG longer than the element itself is left out, never clipped (residual).
            else values.remove(Field.PrescriptionInstructions);
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
            if (CdsExportNativeFieldAssessments.legal(authored, Field.Quantity.maximum()))
                values.put(Field.Quantity, authored);
            else if (!CdsExportNativeFieldAssessments.legal(authored, Integer.MAX_VALUE)) review();
            // An amount that no longer fits with its unit is left out, never written as a bare
            // number that would misstate the amount dispensed (residual).
            else values.remove(Field.Quantity);
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
            // A frequency that already fills the element is written as stored; the as-needed
            // flag then travels in the residual (CONFLICTING_NATIVE_AS_NEEDED_INDICATOR).
            if (CdsExportNativeFieldAssessments.legal(qualified, Field.Frequency.maximum())) {
                values.put(Field.Frequency, qualified);
                prnResolved = true;
            }
        } else if (asNeeded
                && frequencyValue == null
                && !populated(source.get("freqcode"))
                && (treatmentValue == null || !treatmentValue.startsWith("PRN "))) {
            // The as-needed box with no frequency at all ("take 1 tab as needed") is the CT-024
            // frequency PRN, which the CARLOS importer reads back as the as-needed flag.
            values.put(Field.Frequency, "PRN");
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
                boolean carriedPrecision =
                        assessment.outcome() == Outcome.REQUIRES_REVIEW
                                && key.table().equals("partial_date")
                                && CARRIED_PRECISION_RULES.contains(assessment.rule());
                if (assessment.outcome() == Outcome.REQUIRES_REVIEW && !carriedPrecision) review();
                if (carriedPrecision)
                    fields.put(
                            key,
                            new CdsExportFieldAssessmentDto(
                                    assessment.presence(),
                                    Outcome.MAPPED,
                                    "MedicationsAndTreatments/ResidualInfo",
                                    "DATE_PRECISION_CARRIED_WITHOUT_A_DATE_ELEMENT"));
                else if (startUnknown
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
                                    .equals("UNKNOWN_START_DATE_REQUIRES_RECONCILIATION"))
                    // A value longer than its element (the Rx page sets no maximum length: a
                    // dispense interval of 100 characters against DispenseInterval's 10, a
                    // protocol of 255 against 20, a MEDIUMTEXT comment against Notes' 32,000)
                    // is an ordinary saved prescription: the element is omitted, never
                    // truncated, and the exact value travels in the residual. Only text no XML
                    // document can hold still requires review.
                    && !(assessment.rule().equals("WIRE_TEXT_REQUIRES_REVIEW")
                            && CdsExportNativeFieldAssessments.legal(
                                    source.get(key.column()), Integer.MAX_VALUE)))
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
        if (longName != null) {
            String comment = values.get(Field.Notes);
            String notes = LONG_NAME_LABEL + longName + (comment == null ? "" : "\n" + comment);
            if (CdsExportNativeFieldAssessments.legal(notes, Field.Notes.maximum()))
                values.put(Field.Notes, notes);
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
        if (reference == null && catalogueSelected(row))
            details.put("catalogueReference", "NOT_IN_DRUG_REFERENCE");
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
        boolean outsidePrescriber =
                candidate.assessments().values().stream()
                        .anyMatch(
                                field ->
                                        field.outcome() == Outcome.REQUIRES_REVIEW
                                                && field.rule().startsWith("CONFLICTING_PRESCRIBER_"));
        var value =
                new CdsExportMedicationDto(
                        candidate.written(),
                        start,
                        reference == null ? null : reference.components().getFirst().strength(),
                        reference == null ? null : reference.components().getFirst().unit(),
                        outsidePrescriber ? null : candidate.prescriber(),
                        values);
        try {
            CdsExportMedicationXmlWriter.validate(value);
        } catch (io.github.carlos_emr.carlos.demographic.cdsexport.support
                        .CdsExportMappingReviewException held) {
            throw held;
        } catch (IOException unwritable) {
            // A value of this one prescription the writer cannot hold concerns this patient's
            // chart: the patient is held, the job is not stopped.
            review();
        }
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
        // A catalogue selection without a captured public reference: its stored identifier is
        // not an eight-digit DIN, or DrugRef no longer holds the product
        // (CdsExportDrugReferenceSourceReader). It is exported unqualified, with no DIN element;
        // GCN_SEQNO and regional_identifier travel exactly in the residual.
        if (reference == null) return null;
        if (!reference.table().equals("medication_reference")
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

    private static boolean catalogueSelected(CdsExportCapturedTextRowDto row) {
        String catalogue = row.fields().get("GCN_SEQNO");
        try {
            return catalogue != null
                    && !catalogue.isEmpty()
                    && new BigInteger(catalogue).signum() != 0;
        } catch (NumberFormatException invalid) {
            return false;
        }
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
