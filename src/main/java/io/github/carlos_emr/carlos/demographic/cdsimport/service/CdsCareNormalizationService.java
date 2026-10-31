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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsCareElementDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsClinicalDiagnosticDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsFieldDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsTemporalDto;
import io.github.carlos_emr.carlos.commn.model.CdsClinicalException;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsInputException;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Pure interpretation of one emitted CareElements child; no patient tree or catalogue mutation. */
@Service
public class CdsCareNormalizationService {
    public static final int GOAL_NATIVE_BYTE_BUDGET = 32768;
    public static final String OVERFLOW_GOALS = "[Imported collaborative goals: complete value available in Care Elements source details]";
    public static final Set<String> SECTIONS = Set.of("SmokingStatus", "SmokingPacks", "Weight", "Height",
            "WaistCircumference", "BloodPressure", "DiabetesComplicationsScreening",
            "DiabetesMotivationalCounselling", "DiabetesSelfManagementCollaborative",
            "DiabetesSelfManagementChallenges", "DiabetesEducationalSelfManagement",
            "HypoglycemicEpisodes", "SelfMonitoringBloodGlucose");
    private final CdsFieldParser fields;
    private final CdsClinicalNormalizationService clinical;
    public CdsCareNormalizationService(CdsFieldParser fields, CdsClinicalNormalizationService clinical) {
        this.fields = fields; this.clinical = clinical;
    }

    public CdsCareElementDto normalize(CdsRecordDto record, CdsImportCommand command) throws IOException {
        if (!SECTIONS.contains(record.section())) throw new CdsClinicalException("CDS_CARE_KIND_UNSUPPORTED", "CareElements");
        CdsTemporalDto date = clinical.date(record, "Date", command);
        if (date.precision() != CdsTemporalDto.Precision.FULL_DATE) {
            throw new CdsClinicalException("CDS_CARE_FULL_DATE_REQUIRED", "Date");
        }
        List<CdsClinicalDiagnosticDto> diagnostics = new ArrayList<>();
        String type; String value; String unit = null; String code = null; String digest = null;
        boolean overflow = false; boolean uninterpreted = false;
        switch (record.section()) {
            case "SmokingStatus" -> { type = "SKST"; value = yesNo(record, "Status", diagnostics); }
            case "SmokingPacks" -> { type = "POSK"; value = required(record, "PerDay", 2048); unit = "pack/day"; }
            case "Weight" -> { type = "WT"; value = required(record, "Weight", 1024); unit = unit(record, "WeightUnit", "kg"); }
            case "Height" -> { type = "HT"; value = required(record, "Height", 1024); unit = unit(record, "HeightUnit", "cm"); }
            case "WaistCircumference" -> {
                type = "WAIS"; value = required(record, "WaistCircumference", 1024);
                unit = unit(record, "WaistCircumferenceUnit", "cm");
            }
            case "BloodPressure" -> {
                type = "BP"; unit = unit(record, "BPUnit", "mmHg");
                String systolic = required(record, "SystolicBP", 1024);
                String diastolic = required(record, "DiastolicBP", 1024);
                String normalizedSystolic = nonnegativeDecimal(systolic);
                String normalizedDiastolic = nonnegativeDecimal(diastolic);
                if (normalizedSystolic == null || normalizedDiastolic == null) {
                    type = "CDS_CARE_VALUE"; uninterpreted = true;
                    value = "Systolic: " + systolic + "; Diastolic: " + diastolic;
                } else {
                    value = normalizedSystolic + "/" + normalizedDiastolic;
                    if (!value.equals(systolic + "/" + diastolic)) diagnostics.add(diagnostic("CDS_CARE_NUMERIC_NORMALIZED", "BloodPressure"));
                }
            }
            case "DiabetesComplicationsScreening" -> {
                type = "CDS_EXAM"; value = "Performed";
                code = required(record, "ExamCode", 1024);
                if (!Set.of("32468-1", "11397-7", "67536-3").contains(code)) {
                    throw new CdsClinicalException("CDS_CARE_CODE_UNRECOGNIZED", "ExamCode");
                }
            }
            case "DiabetesMotivationalCounselling" -> {
                type = Map.of("Nutrition", "MCCN", "Exercise", "MCCE", "Smoking Cessation", "MCCS", "Other", "MCCO")
                        .get(required(record, "CounsellingPerformed", 64));
                if (type == null) throw new CdsClinicalException("CDS_CARE_CODE_UNRECOGNIZED", "CounsellingPerformed");
                value = "Yes";
                diagnostics.add(diagnostic("CDS_CARE_PERFORMED_ASSERTION_MAPPED", "CounsellingPerformed"));
            }
            case "DiabetesSelfManagementCollaborative" -> {
                type = "CDS_GOALS"; code = unit(record, "CodeValue", "44943-9");
                CdsFieldDto[] source = new CdsFieldDto[1];
                fields.forEachField(record, field -> {
                    if (field.kind().equals("TEXT") && Set.of("cds", "cds_dt").contains(field.namespace())
                            && CdsFieldParser.unindexed(field.path()).equals("DocumentedGoals")) {
                        if (source[0] != null) throw new CdsClinicalException("CDS_CARE_VALUE_AMBIGUOUS", "DocumentedGoals");
                        source[0] = field;
                    }
                });
                if (source[0] == null) throw new CdsClinicalException("CDS_CARE_REQUIRED_FIELD_MISSING", "DocumentedGoals");
                digest = source[0].sha256();
                overflow = record.corrections() == null && source[0].length() > GOAL_NATIVE_BYTE_BUDGET;
                value = OVERFLOW_GOALS;
                if (!overflow) {
                    try {
                        value = fields.readLogicalText(record, source[0], GOAL_NATIVE_BYTE_BUDGET);
                        overflow = value.getBytes(StandardCharsets.UTF_8).length > GOAL_NATIVE_BYTE_BUDGET;
                    } catch (CdsInputException exceedsNativeBudget) {
                        if (!"CDS_TARGET_TEXT_CAPACITY_EXCEEDED".equals(exceedsNativeBudget.code())) throw exceedsNativeBudget;
                        overflow = true;
                    }
                    if (overflow) value = OVERFLOW_GOALS;
                }
                if (overflow) diagnostics.add(diagnostic("CDS_CARE_COMPLETE_VALUE_IN_SOURCE", "DocumentedGoals"));
            }
            case "DiabetesSelfManagementChallenges" -> {
                type = "SMCD"; code = unit(record, "CodeValue", "44941-3"); value = yesNo(record, "ChallengesIdentified", diagnostics);
            }
            case "DiabetesEducationalSelfManagement" -> { type = "DMME"; value = yesNo(record, "EducationalTrainingPerformed", diagnostics); }
            case "SelfMonitoringBloodGlucose" -> { type = "SMBG"; value = yesNo(record, "SelfMonitoring", diagnostics); }
            case "HypoglycemicEpisodes" -> { type = "HYPE"; value = required(record, "NumOfReportedEpisodes", 2048); }
            default -> throw new CdsClinicalException("CDS_CARE_KIND_UNSUPPORTED", "CareElements");
        }
        if (Set.of("WT", "HT", "WAIS", "POSK", "HYPE").contains(type)) {
            String normalized = nonnegativeDecimal(value);
            if (normalized == null || type.equals("HYPE") && normalized.contains(".")) {
                type = "CDS_CARE_VALUE"; uninterpreted = true;
            } else {
                if (!normalized.equals(value)) diagnostics.add(diagnostic("CDS_CARE_NUMERIC_NORMALIZED", record.section()));
                value = normalized;
            }
        }
        if (uninterpreted) diagnostics.add(diagnostic("CDS_CARE_VALUE_NOT_QUANTIFIED", record.section()));
        if (date.appliedShiftDays() != 0) diagnostics.add(diagnostic("CDS_SYNTHETIC_DATE_SHIFT_APPLIED", "Date"));
        return new CdsCareElementDto(record.section(), type, value, unit, code, date,
                overflow, uninterpreted, digest, diagnostics);
    }

    private String required(CdsRecordDto record, String path, int capacity) throws IOException {
        String value = fields.firstText(record, path, capacity);
        if (value == null) throw new CdsClinicalException("CDS_CARE_REQUIRED_FIELD_MISSING", path);
        return value;
    }
    private String unit(CdsRecordDto record, String field, String expected) throws IOException {
        String actual = required(record, field, Math.max(16, expected.length()));
        if (!expected.equals(actual)) throw new CdsClinicalException("CDS_CARE_CODE_UNRECOGNIZED", field);
        return actual;
    }
    private String yesNo(CdsRecordDto record, String field, List<CdsClinicalDiagnosticDto> diagnostics) throws IOException {
        boolean asserted = CdsClinicalNormalizationService.directIndicator(required(record, field, 1), field);
        diagnostics.add(diagnostic("CDS_CARE_BOOLEAN_NORMALIZED", field));
        // Existing clinical reports and MeasurementInfo.isDataEqualToYes recognize the full native spelling.
        return asserted ? "Yes" : "No";
    }
    private static String nonnegativeDecimal(String original) {
        String value = original.strip();
        if (!value.matches("[+]?(?:[0-9]++(?:\\.[0-9]*+)?|\\.[0-9]++)")) return null;
        BigDecimal number = new BigDecimal(value);
        if (!Double.isFinite(number.doubleValue())) return null;
        return number.stripTrailingZeros().toPlainString();
    }
    private static CdsClinicalDiagnosticDto diagnostic(String code, String field) {
        return new CdsClinicalDiagnosticDto(code, field);
    }
}
