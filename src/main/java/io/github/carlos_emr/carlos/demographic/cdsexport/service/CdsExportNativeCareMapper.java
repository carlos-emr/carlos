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
    private static final Map<String, String> ELEMENTS =
            Map.of(
                    "WT",
                    "Weight",
                    "HT",
                    "Height",
                    "WAIS",
                    "WaistCircumference",
                    "BP",
                    "BloodPressure",
                    "SKST",
                    "SmokingStatus",
                    "POSK",
                    "SmokingPacks");

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

    /** Native generic observations have no equivalent standard CareElements child and remain explicit supporting clinical data. */
    static String element(String type) {
        return type == null ? null : ELEMENTS.get(type);
    }

    public Result map(
            int patient,
            CdsExportCapturedTextRowDto observation,
            List<CdsExportCapturedTextRowDto> extensions,
            List<CdsExportCapturedTextRowDto> definitions,
            boolean deleted)
            throws IOException {
        if (patient < 1
                || !observation.table().equals(deleted ? "measurementsDeleted" : "measurements")
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
        String type = observation.fields().get("type"), element = deleted ? null : element(type);
        if (element != null
                && extra.containsKey("date_precision")
                && !"FULL_DATE".equals(extra.get("date_precision")))
            review("CDS_EXPORT_NATIVE_CARE_DATE_PRECISION_REQUIRES_REVIEW");
        var output = new LinkedHashMap<String, String>();
        if (element != null) {
            String observed = observation.fields().get("dateObserved");
            try {
                if (observed == null) review("CDS_EXPORT_NATIVE_CARE_DATE_REQUIRED");
                LocalDate date =
                        observed.length() == 10
                                ? LocalDate.parse(observed)
                                : LocalDateTime.parse(observed.replace(' ', 'T')).toLocalDate();
                if (date.getYear() < 1 || date.getYear() > 9999)
                    review("CDS_EXPORT_NATIVE_CARE_DATE_REQUIRES_REVIEW");
                output.put("Date", date.toString());
            } catch (DateTimeException malformed) {
                review("CDS_EXPORT_NATIVE_CARE_DATE_REQUIRES_REVIEW");
            }
            String value = observation.fields().get("dataField");
            if (value == null || !CdsExportNativeFieldAssessments.legal(value, 1024))
                review("CDS_EXPORT_NATIVE_CARE_VALUE_REQUIRES_REVIEW");
            String instruction = observation.fields().get("measuringInstruction");
            String unit = extra.get("unit");
            if (unit == null) unit = extra.get("units");
            if (unit != null
                    && instruction != null
                    && !instruction.isEmpty()
                    && !sameUnit(unit, instruction))
                review("CDS_EXPORT_NATIVE_CARE_UNITS_CONFLICT");
            if (unit == null) unit = instruction;
            if (element.equals("SmokingStatus")) {
                String status =
                        switch (value) {
                            case "Y", "Yes", "yes" -> "Y";
                            case "N", "No", "no" -> "N";
                            default -> null;
                        };
                if (status == null) element = null;
                else output.put("Status", status);
            } else if (element.equals("SmokingPacks")) {
                if (!Set.of("pack/day", "packs/day", "packs per day").contains(normalUnit(unit)))
                    element = null;
                else output.put("PerDay", decimal(value));
            } else if (element.equals("BloodPressure")) {
                if (!normalUnit(unit).equals("mmhg")) element = null;
                else {
                    var parts = value.split("/", -1);
                    if (parts.length != 2)
                        review("CDS_EXPORT_NATIVE_CARE_BLOOD_PRESSURE_REQUIRES_REVIEW");
                    output.put("SystolicBP", decimal(parts[0]));
                    output.put("DiastolicBP", decimal(parts[1]));
                    output.put("BPUnit", "mmHg");
                }
            } else {
                BigDecimal factor = factor(type, unit);
                if (factor == null) element = null;
                else {
                    String target = element;
                    output.put(
                            target,
                            new BigDecimal(decimal(value))
                                    .multiply(factor)
                                    .stripTrailingZeros()
                                    .toPlainString());
                    output.put(target + "Unit", type.equals("WT") ? "kg" : "cm");
                }
            }
            if (element != null) {
                var state = new CdsExportNativeFieldAssessments(observation, new LinkedHashMap<>());
                for (String column :
                        List.of("dataField", "dateObserved", "type", "measuringInstruction")) {
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
        }
        for (String value : output.values())
            if (!CdsExportNativeFieldAssessments.legal(value, 1024))
                review("CDS_EXPORT_NATIVE_CARE_WIRE_CAPACITY");
        String json =
                JsonMapper.builder()
                        .build()
                        .writeValueAsString(
                                Map.of("version", 1, "deleted", deleted, "rows", metadata));
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

    private static String decimal(String value) throws IOException {
        if (value == null || !value.strip().matches("[+]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)"))
            review("CDS_EXPORT_NATIVE_CARE_NUMERIC_VALUE_REQUIRES_REVIEW");
        return new BigDecimal(value.strip()).stripTrailingZeros().toPlainString();
    }

    private static String normalUnit(String unit) {
        return unit == null ? "" : unit.strip().toLowerCase(Locale.ROOT).replaceFirst("^in +", "");
    }

    private static boolean sameUnit(String left, String right) {
        return normalUnit(left).equals(normalUnit(right));
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
