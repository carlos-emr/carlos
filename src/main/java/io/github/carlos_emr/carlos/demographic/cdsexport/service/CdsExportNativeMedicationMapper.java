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

    public record Result(
            CdsExportMedicationDto value,
            String metadata,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields,
            boolean archived) {
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
        String name = populated(custom) ? custom : brand;
        if (!populated(name)
                || !CdsExportNativeFieldAssessments.legal(name, Field.DrugName.maximum())) review();
        values.put(Field.DrugName, name);
        values.remove(Field.DrugIdentificationNumber);
        String strength = source.get("dosage");
        if (populated(strength)) {
            if (!CdsExportNativeFieldAssessments.legal(strength, Field.DrugDescription.maximum()))
                review();
            values.put(Field.DrugDescription, strength);
        }
        String duration = source.get("duration"), unit = source.get("durunit");
        if (populated(duration) && !"D".equals(unit)) {
            if (!duration.matches("[0-9]+") || !Set.of("W", "M").contains(unit == null ? "" : unit))
                review();
            // The native prescription calculator defines W=7 and M=30 days. Refills are
            // separate dispense information; they do not multiply the exported duration.
            values.put(
                    Field.Duration,
                    new BigInteger(duration)
                            .multiply(BigInteger.valueOf(unit.equals("W") ? 7 : 30))
                            .toString());
        }
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
        String frequency = source.get("freqcode");
        if ("OD".equals(frequency)) values.put(Field.Frequency, "daily");
        if (populated(source.get("unit"))
                && populated(source.get("unitName"))
                && !source.get("unit").equals(source.get("unitName")))
            values.remove(Field.DosageUnitOfMeasure);
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
        if (!knownStatus) {
            values.remove(Field.PrescriptionStatus);
            if (!"1".equals(source.get("past_med"))) values.remove(Field.PastMedications);
        }
        // A flag saying the start date is unknown takes precedence over a stored anchor.
        var start = candidate.start();
        if ("1".equals(source.get("start_date_unknown")))
            start = new CdsExportDateDto(CdsExportDateDto.Precision.ABSENT, null);
        for (var entry : new ArrayList<>(fields.entrySet())) {
            var key = entry.getKey();
            var assessment = entry.getValue();
            if (!key.table().equals("drugs")) {
                if (assessment.outcome() == Outcome.REQUIRES_REVIEW) review();
                continue;
            }
            if (assessment.outcome() == Outcome.REQUIRES_REVIEW
                    && !CONTEXT_RULES.contains(assessment.rule())
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
        details.put("regionalIdentifierAuthority", "UNQUALIFIED");
        details.put("currentness", knownStatus ? status : "UNESTABLISHED");
        String metadata = JsonMapper.builder().build().writeValueAsString(details);
        if (!CdsExportNativeFieldAssessments.legal(metadata, 1 << 24)) review();
        var value =
                new CdsExportMedicationDto(
                        candidate.written(), start, null, null, candidate.prescriber(), values);
        CdsExportMedicationXmlWriter.validate(value);
        return new Result(value, metadata, fields, archived);
    }

    public void write(Result result, CdsExportXmlComposer.ContentWriter xml) throws IOException {
        xml.content(
                writer -> {
                    writer.start("cds", "ResidualInfo");
                    CdsExportNativeHistoryXmlWriter.residualElement(
                            writer,
                            CdsNativeExtensionContract.MEDICATION_SOURCE_CONTEXT,
                            "string",
                            result.metadata());
                    CdsExportNativeHistoryXmlWriter.residualElement(
                            writer,
                            CdsNativeExtensionContract.MEDICATION_ARCHIVED,
                            "boolean",
                            Boolean.toString(result.archived()));
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
