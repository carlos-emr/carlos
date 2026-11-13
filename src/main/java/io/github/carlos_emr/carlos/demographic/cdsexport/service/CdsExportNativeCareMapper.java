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
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Outcome;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportMappingReviewException;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Standard care values and their complete native supporting context from one verified observation. */
public final class CdsExportNativeCareMapper {
    /**
     * The CareElements child each native type can map to; the value, unit, instruction and date
     * decide whether it does. The native types and the code choices are those of the CARLOS
     * importer (CdsCareNormalizationService) and the legacy exporter.
     */
    private static final Map<String, String> ELEMENTS =
            Map.ofEntries(
                    Map.entry("WT", "Weight"),
                    Map.entry("HT", "Height"),
                    Map.entry("WAIS", "WaistCircumference"),
                    // "Waist Circumference" in the seeded measurement map (LOINC 8280-0); no
                    // seeded vocabulary implies its unit, so it maps only with a stated one.
                    Map.entry("WC", "WaistCircumference"),
                    Map.entry("BP", "BloodPressure"),
                    Map.entry("SKST", "SmokingStatus"),
                    Map.entry("POSK", "SmokingPacks"),
                    Map.entry("SMBG", "SelfMonitoringBloodGlucose"),
                    Map.entry("DMME", "DiabetesEducationalSelfManagement"),
                    Map.entry("SMCD", "DiabetesSelfManagementChallenges"),
                    Map.entry("HYPE", "HypoglycemicEpisodes"),
                    Map.entry("MCCN", "DiabetesMotivationalCounselling"),
                    Map.entry("MCCE", "DiabetesMotivationalCounselling"),
                    Map.entry("MCCS", "DiabetesMotivationalCounselling"),
                    Map.entry("MCCO", "DiabetesMotivationalCounselling"),
                    Map.entry("EYEE", "DiabetesComplicationsScreening"),
                    Map.entry("FTE", "DiabetesComplicationsScreening"),
                    Map.entry("FTLS", "DiabetesComplicationsScreening"),
                    Map.entry("NRTF", "DiabetesComplicationsScreening"));

    /**
     * Instructions under which a diabetes assertion means what its CDS element says: none, or the
     * text CARLOS seeds for the type (V1.0.2 data, V1.0.32 for NRTF; the flowsheets reuse it).
     * A locally reworded instruction (for example "Referred") may change what "Yes" asserts, so
     * the observation stays supporting-only rather than being reinterpreted.
     */
    private static final Map<String, Set<String>> ASSERTION_INSTRUCTIONS =
            Map.ofEntries(
                    Map.entry("SMBG", Set.of("", "yes/no")),
                    Map.entry("SMCD", Set.of("", "yes/no")),
                    Map.entry("DMME", Set.of("", "discussed")),
                    Map.entry("HYPE", Set.of("", "since last visit")),
                    Map.entry("MCCN", Set.of("", "yes/no")),
                    Map.entry("MCCE", Set.of("", "yes/no")),
                    Map.entry("MCCS", Set.of("", "yes/no")),
                    Map.entry("MCCO", Set.of("", "yes/no")),
                    Map.entry("EYEE", Set.of("", "exam done")),
                    Map.entry("FTE", Set.of("", "normal")),
                    Map.entry("FTLS", Set.of("", "normal")),
                    Map.entry("NRTF", Set.of("", "normal")));

    /** DiabetesMotivationalCounselling/CounsellingPerformed, as the importer maps it back. */
    private static final Map<String, String> COUNSELLING =
            Map.of(
                    "MCCN", "Nutrition",
                    "MCCE", "Exercise",
                    "MCCS", "Smoking Cessation",
                    "MCCO", "Other");

    /**
     * DiabetesComplicationsScreening/ExamCode: retinal 32468-1, foot 11397-7, and the one
     * neurological code 67536-3 for both the monofilament (FTLS) and tuning-fork (NRTF) exams.
     */
    private static final Map<String, String> EXAM_CODES =
            Map.of(
                    "EYEE", "32468-1",
                    "FTE", "11397-7",
                    "FTLS", "67536-3",
                    "NRTF", "67536-3");

    public record Result(
            String table,
            String id,
            String element,
            Map<String, String> values,
            String metadata,
            List<CdsExportCapturedTextRowDto> rows,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields) {
        public Result {
            values = Map.copyOf(values);
            rows = List.copyOf(rows);
            fields = Map.copyOf(fields);
        }

        @Override
        public String toString() {
            return "CdsExportNativeCareMapper.Result[protected]";
        }
    }

    /** The CareElements child a native type can map to; the value, unit and date decide whether it does. */
    static String element(String type) {
        return type == null ? null : ELEMENTS.get(type);
    }

    /** One standard CareElements value derived exactly from a native observation. */
    record Standard(String element, Map<String, String> values) {
        Standard {
            values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        }

        @Override
        public String toString() {
            return "CdsExportNativeCareMapper.Standard[protected]";
        }
    }

    /**
     * The single decision whether a current native observation also has an exact standard
     * CareElements form; the planner, the inventory and the mapper all use it. Every native
     * observation is exported in full as NewCategory supporting data; this only adds the standard
     * element when the type, unit, value and calendar date are all exactly representable. Anything
     * else (an unknown unit, a value with words in it, a partial date) returns null and the
     * observation stays NewCategory-only instead of stopping the job.
     */
    static Standard standard(Map<String, String> observation, Map<String, String> extra) {
        String type = observation.get("type"), element = element(type);
        if (element == null) return null;
        if (extra.containsKey("date_precision") && !"FULL_DATE".equals(extra.get("date_precision")))
            return null;
        String date = calendarDate(observation.get("dateObserved"));
        String value = observation.get("dataField");
        if (date == null || value == null || !CdsExportNativeFieldAssessments.legal(value, 1024))
            return null;
        String explicit = extra.containsKey("unit") ? extra.get("unit") : extra.get("units");
        String unit = careUnit(type, explicit, observation.get("measuringInstruction"));
        var output = new LinkedHashMap<String, String>();
        if (ASSERTION_INSTRUCTIONS.containsKey(type)
                && !ASSERTION_INSTRUCTIONS
                        .get(type)
                        .contains(assertionInstruction(observation.get("measuringInstruction"))))
            return null;
        switch (element) {
            case "SmokingStatus" -> {
                String status = yesNo(value);
                if (status == null) return null;
                output.put("Status", status);
            }
            case "SelfMonitoringBloodGlucose", "DiabetesEducationalSelfManagement" -> {
                String asserted = yesNo(value);
                if (asserted == null) return null;
                output.put(
                        element.equals("SelfMonitoringBloodGlucose")
                                ? "SelfMonitoring"
                                : "EducationalTrainingPerformed",
                        asserted);
            }
            case "DiabetesSelfManagementChallenges" -> {
                String asserted = yesNo(value);
                if (asserted == null) return null;
                output.put("CodeValue", "44941-3");
                output.put("ChallengesIdentified", asserted);
            }
            case "HypoglycemicEpisodes" -> {
                // A count of episodes: xs:integer, never a fraction or a sign.
                if (!value.strip().matches("[0-9]{1,18}")) return null;
                output.put("NumOfReportedEpisodes", Long.toString(Long.parseLong(value.strip())));
            }
            case "DiabetesMotivationalCounselling", "DiabetesComplicationsScreening" -> {
                // These elements can only say that the counselling or exam was performed. "No"
                // or "NA" (not done; under the exams' "Normal", possibly an abnormal result)
                // has no standard form and stays in the supporting record.
                if (!"Y".equals(yesNo(value))) return null;
                if (element.equals("DiabetesMotivationalCounselling"))
                    output.put("CounsellingPerformed", COUNSELLING.get(type));
                else output.put("ExamCode", EXAM_CODES.get(type));
            }
            case "SmokingPacks" -> {
                String perDay = decimalOrNull(value);
                if (perDay == null
                        || !Set.of("pack/day", "packs/day", "packs per day").contains(unit))
                    return null;
                output.put("PerDay", perDay);
            }
            case "BloodPressure" -> {
                if (!"mmhg".equals(unit)) return null;
                var parts = value.split("/", -1);
                if (parts.length != 2) return null;
                String systolic = decimalOrNull(parts[0]), diastolic = decimalOrNull(parts[1]);
                if (systolic == null || diastolic == null) return null;
                output.put("SystolicBP", systolic);
                output.put("DiastolicBP", diastolic);
                output.put("BPUnit", "mmHg");
            }
            default -> {
                BigDecimal factor = factor(type, unit);
                String number = decimalOrNull(value);
                if (factor == null || number == null) return null;
                output.put(
                        element,
                        new BigDecimal(number).multiply(factor).stripTrailingZeros().toPlainString());
                output.put(element + "Unit", type.equals("WT") ? "kg" : "cm");
            }
        }
        output.put("Date", date);
        for (String text : output.values())
            if (!CdsExportNativeFieldAssessments.legal(text, 1024)) return null;
        return new Standard(element, output);
    }

    private static String calendarDate(String observed) {
        if (observed == null) return null;
        try {
            LocalDate date =
                    observed.length() == 10
                            ? LocalDate.parse(observed)
                            : LocalDateTime.parse(observed.replace(' ', 'T')).toLocalDate();
            return date.getYear() < 1 || date.getYear() > 9999 ? null : date.toString();
        } catch (DateTimeException malformed) {
            return null;
        }
    }

    /**
     * The unit of a standard care observation, normalized. An explicit unit extension wins; the
     * measuring instruction contributes a unit only when, after removing CARLOS's flowsheet hint
     * ("(nnn) Range:... Interval:...") and the waist prefix, it names a known unit. CARLOS records
     * blood pressure in mmHg and treats the instruction as posture text
     * (MeasurementUnitConstants), and with no unit recorded at all, weight is kg and height and
     * waist are cm, as CARLOS displays them. Two different known units, or an unrecognized
     * instruction, leave the unit undecided.
     */
    static String careUnit(String type, String explicit, String instruction) {
        String stated = explicit == null || explicit.isBlank() ? null : normalUnit(explicit);
        String hinted = instructionUnit(instruction);
        if (type.equals("BP")) {
            if (stated != null && !stated.equals("mmhg")) return null;
            return hinted != null && !hinted.isEmpty() && !hinted.equals("mmhg") && known(hinted)
                    ? null
                    : "mmhg";
        }
        if (type.equals("SKST")) return stated == null ? hinted : stated;
        if (stated != null && hinted != null && !hinted.isEmpty() && !stated.equals(hinted))
            return null;
        if (stated != null) return stated;
        if (hinted == null) return null;
        if (!hinted.isEmpty()) return hinted;
        return switch (type) {
            case "WT" -> "kg";
            case "HT", "WAIS" -> "cm";
            // WC: no seeded vocabulary says which unit a bare number is in.
            default -> null;
        };
    }

    /** "" when the instruction states no unit; null when it is text that is not a known unit. */
    private static String instructionUnit(String instruction) {
        if (instruction == null) return "";
        String text =
                instruction
                        .strip()
                        .toLowerCase(Locale.ROOT)
                        .replaceFirst("\\s*\\(.*$", "")
                        .replaceFirst("\\s*range:.*$", "")
                        .replaceFirst("^waist circum(?:ference)?\\s+", "")
                        .replaceFirst("^in\\s+", "")
                        .strip();
        if (text.isEmpty()) return "";
        return known(text) ? text : null;
    }

    private static boolean known(String unit) {
        return factor("WT", unit) != null
                || factor("HT", unit) != null
                || unit.equals("mmhg")
                || Set.of("pack/day", "packs/day", "packs per day").contains(unit);
    }

    /** Y or N from the spellings the seeded Yes/No validations accept; null otherwise. */
    private static String yesNo(String value) {
        return switch (value.strip()) {
            case "Y", "Yes", "yes", "YES" -> "Y";
            case "N", "No", "no", "NO" -> "N";
            default -> null;
        };
    }

    /** The instruction without CARLOS's flowsheet hint ("(...) Range:... Interval:..."). */
    private static String assertionInstruction(String instruction) {
        if (instruction == null) return "";
        return instruction
                .strip()
                .toLowerCase(Locale.ROOT)
                .replaceFirst("\\s*\\(.*$", "")
                .replaceFirst("\\s*range:.*$", "")
                .strip();
    }

    private static String decimalOrNull(String value) {
        if (value == null || !value.strip().matches("[+]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)"))
            return null;
        return new BigDecimal(value.strip()).stripTrailingZeros().toPlainString();
    }

    /** Disposition of every field of a deleted or superseded observation version. */
    static final String RETIRED = "DELETED_OR_SUPERSEDED_OBSERVATION_VERSION";

    /**
     * A measurementsDeleted row is either a clinician's deletion of an erroneous entry
     * (EctDeleteData2Action) or an HL7 result version superseded by a newer report
     * (Hl7textResultsData). Neither is a current clinical fact, and the legacy exporter, which
     * read MeasurementDao.findByDemographicId, never exported them; a receiving EMR must not see
     * an erroneous value beside the current ones. As with superseded prevention versions
     * (CdsExportPreventionVersions), every field of the row and of the extensions the deletion
     * left behind keeps an explicit not-exported disposition: accounted, never dropped silently,
     * and no reason to hold the patient.
     */
    static Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> retired(
            int patient, CdsExportNativeCarePlanner.Occurrence occurrence) throws IOException {
        var row = occurrence.observation();
        String owner = row.fields().get("originalId");
        if (patient < 1
                || !occurrence.deleted()
                || !row.table().equals("measurementsDeleted")
                || !Integer.toString(patient).equals(row.fields().get("demographicNo"))
                || owner == null
                || !occurrence.definitions().isEmpty()) invalid();
        var result = new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        var rows = new ArrayList<CdsExportCapturedTextRowDto>();
        rows.add(row);
        rows.addAll(occurrence.extensions());
        for (var version : rows) {
            if (version != row
                    && (!version.table().equals("measurementsExt")
                            || !owner.equals(version.fields().get("measurement_id")))) invalid();
            var fields = new CdsExportNativeFieldAssessments(version, result);
            for (String column : version.fields().keySet()) fields.operational(column, RETIRED);
        }
        return result;
    }

    public Result map(
            int patient,
            CdsExportCapturedTextRowDto observation,
            List<CdsExportCapturedTextRowDto> extensions,
            List<CdsExportCapturedTextRowDto> definitions)
            throws IOException {
        if (patient < 1
                || !observation.table().equals("measurements")
                || !Integer.toString(patient).equals(observation.fields().get("demographicNo")))
            invalid();
        String id = observation.fields().get("id");
        var rows = new ArrayList<CdsExportCapturedTextRowDto>();
        rows.add(observation);
        rows.addAll(extensions);
        rows.addAll(definitions);
        var fields = new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        var metadata = new ArrayList<Map<String, Object>>();
        for (var row : rows) {
            var state = new CdsExportNativeFieldAssessments(row, fields);
            for (var column : row.fields().entrySet()) {
                if (column.getValue() != null
                        && !CdsExportNativeFieldAssessments.legal(column.getValue(), 1 << 24))
                    review("CDS_EXPORT_NATIVE_CARE_TEXT_REQUIRES_REVIEW");
                state.set(
                        column.getKey(),
                        Outcome.MAPPED,
                        "NewCategory/ResidualInfo",
                        "EXACT_NATIVE_OBSERVATION_CONTEXT");
            }
            metadata.add(Map.of("table", row.table(), "fields", row.fields()));
        }
        var extra = new HashMap<String, String>();
        for (var ext : extensions) {
            if (!id.equals(ext.fields().get("measurement_id"))) invalid();
            String key = ext.fields().get("keyval"), value = ext.fields().get("val");
            if (key == null || extra.containsKey(key))
                review("CDS_EXPORT_NATIVE_CARE_EXTENSION_AMBIGUOUS");
            extra.put(key, value);
        }
        var standard = standard(observation.fields(), extra);
        String element = standard == null ? null : standard.element();
        var output = new LinkedHashMap<String, String>();
        if (standard != null) {
            output.putAll(standard.values());
            var state = new CdsExportNativeFieldAssessments(observation, new LinkedHashMap<>());
            for (String column : List.of("dataField", "dateObserved", "type", "measuringInstruction")) {
                String raw = state.raw(column);
                fields.put(
                        new CdsExportNativeFieldKeyDto(
                                observation.table(), observation.recordKey(), column),
                        new CdsExportFieldAssessmentDto(
                                raw == null
                                        ? CdsExportFieldAssessmentDto.Presence.SQL_NULL
                                        : raw.isEmpty()
                                                ? CdsExportFieldAssessmentDto.Presence.EMPTY
                                                : CdsExportFieldAssessmentDto.Presence.VALUE,
                                Outcome.MAPPED,
                                "CareElements/" + element,
                                "EXPLICIT_NATIVE_CARE_VALUE_UNIT_AND_CALENDAR_DATE"));
            }
        }
        for (String value : output.values())
            if (!CdsExportNativeFieldAssessments.legal(value, 1024))
                review("CDS_EXPORT_NATIVE_CARE_WIRE_CAPACITY");
        String json =
                JsonMapper.builder()
                        .build()
                        .writeValueAsString(
                                Map.of("version", 1, "deleted", false, "rows", metadata));
        return new Result(observation.table(), id, element, output, json, rows, fields);
    }

    public void writeCare(Result result, CdsExportXmlComposer.ContentWriter xml)
            throws IOException {
        if (result.element() == null) invalid();
        xml.start("cds", result.element());
        var order =
                switch (result.element()) {
                    case "Weight" -> List.of("Weight", "WeightUnit", "Date");
                    case "Height" -> List.of("Height", "HeightUnit", "Date");
                    case "WaistCircumference" ->
                            List.of("WaistCircumference", "WaistCircumferenceUnit", "Date");
                    case "BloodPressure" -> List.of("SystolicBP", "DiastolicBP", "BPUnit", "Date");
                    case "SmokingStatus" -> List.of("Status", "Date");
                    case "SmokingPacks" -> List.of("PerDay", "Date");
                    case "SelfMonitoringBloodGlucose" -> List.of("SelfMonitoring", "Date");
                    case "DiabetesEducationalSelfManagement" ->
                            List.of("EducationalTrainingPerformed", "Date");
                    case "DiabetesSelfManagementChallenges" ->
                            List.of("CodeValue", "ChallengesIdentified", "Date");
                    case "HypoglycemicEpisodes" -> List.of("NumOfReportedEpisodes", "Date");
                    case "DiabetesMotivationalCounselling" ->
                            List.of("CounsellingPerformed", "Date");
                    case "DiabetesComplicationsScreening" -> List.of("ExamCode", "Date");
                    default -> throw new IOException("CDS_EXPORT_NATIVE_CARE_ELEMENT_INVALID");
                };
        for (String name : order) xml.element("cds_dt", name, result.values().get(name));
        xml.end();
    }

    public void writeSupporting(Result result, CdsExportXmlComposer.ContentWriter xml)
            throws IOException {
        xml.element("cds", "CategoryName", "CARLOS.NativeObservation.v1");
        xml.element("cds", "CategoryDescription", "Native clinical observation and source context");
        xml.start("cds", "ResidualInfo");
        CdsExportNativeHistoryXmlWriter.residualElement(
                xml, "CARLOS.Observation.v1", "string", result.metadata());
        xml.end();
    }

    private static String normalUnit(String unit) {
        return unit == null ? "" : unit.strip().toLowerCase(Locale.ROOT).replaceFirst("^in +", "");
    }

    private static BigDecimal factor(String type, String unit) {
        String normalized = normalUnit(unit);
        if (type.equals("WT"))
            return switch (normalized) {
                case "kg", "kilograms" -> BigDecimal.ONE;
                case "g", "grams" -> new BigDecimal("0.001");
                case "lb", "lbs", "pounds" -> new BigDecimal("0.45359237");
                default -> null;
            };
        return switch (normalized) {
            case "cm", "centimeters", "centimetres" -> BigDecimal.ONE;
            case "m", "meters", "metres" -> new BigDecimal("100");
            case "in", "inch", "inches" -> new BigDecimal("2.54");
            default -> null;
        };
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_NATIVE_CARE_SCOPE_INVALID");
    }

    private static void review(String code) throws CdsExportMappingReviewException {
        throw new CdsExportMappingReviewException(code);
    }
}
