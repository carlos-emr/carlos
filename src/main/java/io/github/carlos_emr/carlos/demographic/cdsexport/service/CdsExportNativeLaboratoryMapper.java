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

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Segment;
import ca.uhn.hl7v2.util.Terser;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportMappingReviewException;
import java.io.IOException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Maps individual native HL7 observations; complete source context is a separate NewCategory. */
final class CdsExportNativeLaboratoryMapper {
    private static final Set<String> FLAGS =
            Set.of("L", "LL", "H", "HH", "N", "A", "AA", "S", "R", "I", "MS", "VS", "U");

    record Value(
            String ordinal,
            Map<String, String> text,
            Map<String, CdsExportDateDto> dates,
            String flag,
            boolean enumFlag,
            boolean blocked) {
        Value {
            text = Map.copyOf(text);
            dates = Map.copyOf(dates);
        }

        @Override
        public String toString() {
            return "Value[protected]";
        }
    }

    List<Value> map(CdsExportLaboratoryOccurrenceService.NativeSource source, ZoneId zone)
            throws IOException {
        try {
            var result = new ArrayList<Value>();
            String laboratory = source.information().fields().get("sending_facility");
            if (laboratory == null || laboratory.isEmpty())
                laboratory = new Terser(source.parsed().message()).get("/MSH-4-1");
            laboratory = literal(laboratory);
            if (laboratory.isEmpty()) review("CDS_EXPORT_NATIVE_LAB_NAME_REQUIRED");
            for (var observation : source.parsed().observations()) {
                var text = new LinkedHashMap<String, String>();
                var dates = new LinkedHashMap<String, CdsExportDateDto>();
                Segment obx = observation.result(), obr = observation.order();
                put(text, "LaboratoryName", laboratory, 120);
                put(text, "TestNameReportedByLab", scalar(obx, 3, 0, 2), 120);
                put(text, "LabTestCode", scalar(obx, 3, 0, 1), 50);
                String accession = scalar(obr, 3, 0, 1);
                if (accession.isEmpty())
                    accession = source.information().fields().get("accessionNum");
                put(text, "AccessionNumber", accession, 120);
                String type = scalar(obx, 2, 0, 1);
                if (!Set.of("NM", "ST", "TX", "FT", "CE", "CWE", "CNE", "DT", "TS", "DTM")
                        .contains(type)) review("CDS_EXPORT_NATIVE_LAB_VALUE_TYPE_REQUIRES_REVIEW");
                String value;
                if (Set.of("CE", "CWE", "CNE").contains(type)) {
                    String code = scalar(obx, 5, observation.repetition(), 1);
                    String description = scalar(obx, 5, observation.repetition(), 2);
                    value =
                            description.isEmpty()
                                    ? code
                                    : code.isEmpty() ? description : code + " — " + description;
                } else value = scalar(obx, 5, observation.repetition(), 1);
                if (type.equals("NM") && !value.isEmpty()) {
                    try {
                        new java.math.BigDecimal(value);
                    } catch (NumberFormatException invalid) {
                        review("CDS_EXPORT_NATIVE_LAB_NUMERIC_VALUE_INVALID");
                    }
                }
                StringBuilder information = new StringBuilder();
                information
                        .append("Native HL7 observation ")
                        .append(observation.ordinal())
                        .append("; value type ")
                        .append(type)
                        .append(
                                ". Complete source message and routing context:"
                                        + " CARLOS.NativeLaboratory.v1.");
                if (!value.isEmpty()) {
                    if (CdsExportNativeFieldAssessments.legal(value, 120)) {
                        put(text, "ResultValue", value, 120);
                        put(text, "ResultUnit", scalar(obx, 6, 0, 1), 120);
                    } else {
                        if (!Set.of("ST", "TX", "FT").contains(type))
                            review("CDS_EXPORT_NATIVE_LAB_RESULT_VALUE_CAPACITY");
                        information.append("\n\nResult text (complete):\n").append(value);
                    }
                }
                put(text, "ReferenceRangeText", scalar(obx, 7, 0, 1), 1024);
                String requested = scalar(obr, 6, 0, 1);
                if (!requested.isEmpty())
                    dates.put("LabRequisitionDateTime", temporal(requested, zone));
                String collected = scalar(obx, 14, 0, 1);
                if (collected.isEmpty()) collected = scalar(obr, 7, 0, 1);
                if (collected.isEmpty()) review("CDS_EXPORT_NATIVE_LAB_COLLECTION_REQUIRED");
                dates.put("CollectionDateTime", temporal(collected, zone));
                String flag = scalar(obx, 8, 0, 1);
                if (obx.getField(8).length > 1) {
                    var flags = new ArrayList<String>();
                    for (int i = 0; i < obx.getField(8).length; i++)
                        flags.add(scalar(obx, 8, i, 1));
                    flag = String.join("; ", flags);
                }
                if (!CdsExportNativeFieldAssessments.legal(flag, 50))
                    review("CDS_EXPORT_NATIVE_LAB_FLAG_CAPACITY");
                if (flag.isEmpty())
                    information.append("\nThe laboratory supplied no normal/abnormal flag.");
                StringBuilder notes = new StringBuilder();
                for (var comment : observation.comments())
                    for (int i = 0; i < comment.getField(3).length; i++) {
                        if (!notes.isEmpty()) notes.append('\n');
                        notes.append(scalar(comment, 3, i, 1));
                    }
                put(text, "TestResultsInformationReportedByTheLab", information.toString(), 32000);
                put(text, "NotesFromLab", notes.toString(), 32000);
                put(text, "TestResultStatus", scalar(obx, 11, 0, 1), 20);
                // The laboratory's "do not disclose" marking travels as BlockedTestResult, using
                // the rules CARLOS's own handlers apply: OBX-13 "BLOCKED" (CMLHandler,
                // GDMLHandler) or the message-level MDS ZPD-3 "Y" (MDSHandler). A blocked result
                // is never presented to a receiver as an ordinary unrestricted one.
                boolean blocked =
                        source.parsed().blockedMessage()
                                || "BLOCKED".equals(scalar(obx, 13, 0, 1));
                result.add(
                        new Value(
                                observation.ordinal(),
                                text,
                                dates,
                                flag,
                                FLAGS.contains(flag),
                                blocked));
            }
            return List.copyOf(result);
        } catch (HL7Exception failure) {
            review("CDS_EXPORT_NATIVE_LAB_FIELDS_REQUIRES_REVIEW");
            return List.of();
        }
    }

    static Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> assessments(
            CdsExportLaboratoryOccurrenceService.NativeSource source) throws IOException {
        var result = new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        for (var row : source.rows()) {
            var assessed = new CdsExportNativeFieldAssessments(row, result);
            for (var entry : row.fields().entrySet()) {
                if (entry.getValue() != null
                        && !CdsExportNativeFieldAssessments.legal(entry.getValue(), 1 << 24))
                    review("CDS_EXPORT_NATIVE_LAB_CONTEXT_CAPACITY");
                assessed.set(
                        entry.getKey(),
                        CdsExportFieldAssessmentDto.Outcome.MAPPED,
                        "NewCategory/ResidualInfo",
                        "COMPLETE_NATIVE_LABORATORY_SOURCE_CONTEXT");
            }
        }
        return result;
    }

    void write(Value value, CdsExportXmlComposer.ContentWriter xml) throws IOException {
        for (String name :
                List.of(
                        "LaboratoryName",
                        "TestNameReportedByLab",
                        "LabTestCode",
                        "TestName",
                        "AccessionNumber")) text(value, name, xml);
        if (value.text().containsKey("ResultValue")) {
            xml.start("cds", "Result");
            xml.element("cds", "Value", value.text().get("ResultValue"));
            if (value.text().containsKey("ResultUnit"))
                xml.element("cds", "UnitOfMeasure", value.text().get("ResultUnit"));
            xml.end();
        }
        if (value.text().containsKey("ReferenceRangeText")) {
            xml.start("cds", "ReferenceRange");
            text(value, "ReferenceRangeText", xml);
            xml.end();
        }
        for (String name : List.of("LabRequisitionDateTime", "CollectionDateTime"))
            if (value.dates().containsKey(name)) {
                xml.start("cds", name);
                CdsExportNativeHistoryXmlWriter.dateContents(xml, value.dates().get(name));
                xml.end();
            }
        xml.start("cds", "ResultNormalAbnormalFlag");
        xml.element(
                "cds_dt",
                value.enumFlag()
                        ? "resultNormalAbnormalFlagAsEnum"
                        : "resultNormalAbnormalFlagAsPlainText",
                value.flag());
        xml.end();
        for (String name :
                List.of(
                        "TestResultsInformationReportedByTheLab",
                        "NotesFromLab",
                        "TestResultStatus")) text(value, name, xml);
        if (value.blocked()) xml.element("cds", "BlockedTestResult", "Y");
    }

    void writeSupporting(
            CdsExportLaboratoryOccurrenceService.NativeSource source,
            CdsExportXmlComposer.ContentWriter xml)
            throws IOException {
        xml.element("cds", "CategoryName", "CARLOS.NativeLaboratory.v1");
        xml.element(
                "cds",
                "CategoryDescription",
                "Complete native laboratory source message and routing context");
        xml.start("cds", "ResidualInfo");
        var rows = new ArrayList<Map<String, Object>>();
        for (var row : source.rows())
            rows.add(Map.of("table", row.table(), "fields", row.fields()));
        CdsExportNativeHistoryXmlWriter.residualElement(
                xml,
                "CARLOS.Laboratory.v1",
                "string",
                JsonMapper.builder()
                        .build()
                        .writeValueAsString(Map.of("version", 1, "rows", rows)));
        xml.end();
    }

    static CdsExportDateDto temporal(String value, ZoneId zone) throws IOException {
        try {
            if (value == null
                    || !value.matches(
                            "[0-9]{4}(?:[0-9]{2}){0,5}(?:\\.[0-9]{1,9})?(?:[+-][0-9]{4})?"))
                review("CDS_EXPORT_NATIVE_LAB_DATE_REQUIRES_REVIEW");
            String offset = "";
            int end = value.length();
            if (end >= 5 && (value.charAt(end - 5) == '+' || value.charAt(end - 5) == '-')) {
                offset = value.substring(end - 5);
                value = value.substring(0, end - 5);
            }
            if (value.startsWith("0000")) review("CDS_EXPORT_NATIVE_LAB_DATE_REQUIRES_REVIEW");
            if (offset.isEmpty() && value.length() == 4)
                return new CdsExportDateDto(
                        CdsExportDateDto.Precision.YEAR, Year.parse(value).toString());
            if (offset.isEmpty() && value.length() == 6)
                return new CdsExportDateDto(
                        CdsExportDateDto.Precision.YEAR_MONTH,
                        YearMonth.of(
                                        Integer.parseInt(value.substring(0, 4)),
                                        Integer.parseInt(value.substring(4)))
                                .toString());
            if (offset.isEmpty() && value.length() == 8)
                return new CdsExportDateDto(
                        CdsExportDateDto.Precision.DATE,
                        LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE).toString());
            if (value.length() < 14 || value.length() > 24)
                review("CDS_EXPORT_NATIVE_LAB_CLOCK_PRECISION_REQUIRES_REVIEW");
            String iso =
                    value.substring(0, 4)
                            + "-"
                            + value.substring(4, 6)
                            + "-"
                            + value.substring(6, 8)
                            + "T"
                            + value.substring(8, 10)
                            + ":"
                            + value.substring(10, 12)
                            + ":"
                            + value.substring(12);
            var local = LocalDateTime.parse(iso);
            if (offset.isEmpty()) return CdsExportNativeHistoryMapper.clock(iso, zone);
            var actualOffset = ZoneOffset.of(offset.substring(0, 3) + ":" + offset.substring(3));
            if (Math.abs(actualOffset.getTotalSeconds()) > 14 * 3600)
                review("CDS_EXPORT_NATIVE_LAB_OFFSET_REQUIRES_REVIEW");
            return new CdsExportDateDto(
                    CdsExportDateDto.Precision.DATE_TIME,
                    DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(local.atOffset(actualOffset)));
        } catch (DateTimeException | NumberFormatException failure) {
            review("CDS_EXPORT_NATIVE_LAB_DATE_REQUIRES_REVIEW");
            return null;
        }
    }

    private static String scalar(Segment segment, int field, int repetition, int component)
            throws HL7Exception {
        return literal(Terser.get(segment, field, repetition, component, 1));
    }

    private static String literal(String value) {
        return value == null || value.equals("\"\"") ? "" : value;
    }

    private static void put(Map<String, String> values, String name, String value, int maximum)
            throws IOException {
        if (value == null || value.isEmpty()) return;
        if (!CdsExportNativeFieldAssessments.legal(value, maximum))
            review("CDS_EXPORT_NATIVE_LAB_WIRE_CAPACITY");
        values.put(name, value);
    }

    private static void text(Value value, String name, CdsExportXmlComposer.ContentWriter xml)
            throws IOException {
        if (value.text().containsKey(name)) xml.element("cds", name, value.text().get(name));
    }

    private static void review(String code) throws CdsExportMappingReviewException {
        throw new CdsExportMappingReviewException(code);
    }
}
