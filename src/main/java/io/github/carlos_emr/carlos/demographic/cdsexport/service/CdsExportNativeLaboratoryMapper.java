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

/**
 * Maps individual native HL7 observations; complete source context is a separate NewCategory.
 *
 * <p>Text is exported as the laboratory wrote it. Results, notes, reference ranges and names are
 * read from the raw segment, so a separator a laboratory left unescaped stays in the text instead
 * of cutting it at the first piece, and HL7 escapes are decoded the way CARLOS displays them (see
 * {@link #decode}). A structured value (numeric, date, coded) with unexpected components needs
 * review.
 */
final class CdsExportNativeLaboratoryMapper {
    /**
     * HL7 Table 0078 flags with the same meaning in the CDS enumeration. HL7 {@code U} means
     * "significant change up" while the CDS enumeration's {@code U} means Unknown, so it travels
     * as plain text, like {@code D}, {@code B} and {@code W}, which the enumeration lacks.
     */
    private static final Set<String> FLAGS =
            Set.of("L", "LL", "H", "HH", "N", "A", "AA", "S", "R", "I", "MS", "VS");

    /** The data dictionary's translation of an absent flag. */
    static final String UNKNOWN_FLAG = "U";

    private static final Set<String> TEXT_TYPES = Set.of("ST", "TX", "FT");

    record Value(
            String ordinal,
            Map<String, String> text,
            Map<String, CdsExportDateDto> dates,
            String flag,
            boolean enumFlag,
            boolean blocked,
            List<CdsExportInboxReviewMapper.Review> reviewers) {
        Value {
            text = Map.copyOf(text);
            dates = Map.copyOf(dates);
            reviewers = List.copyOf(reviewers);
        }

        Value(
                String ordinal,
                Map<String, String> text,
                Map<String, CdsExportDateDto> dates,
                String flag,
                boolean enumFlag,
                boolean blocked) {
            this(ordinal, text, dates, flag, enumFlag, blocked, List.of());
        }

        @Override
        public String toString() {
            return "Value[protected]";
        }
    }

    List<Value> map(CdsExportLaboratoryOccurrenceService.NativeSource source, ZoneId zone)
            throws IOException {
        return map(source, zone, CdsExportInboxReviewMapper.Reviews.NONE);
    }

    /**
     * Maps every observation of one report. A report's acknowledgements are reviews of the whole
     * report, so every result of it carries the same ResultReviewer elements; comments travel in
     * the report's supporting record ({@link #writeSupporting}).
     */
    List<Value> map(
            CdsExportLaboratoryOccurrenceService.NativeSource source,
            ZoneId zone,
            CdsExportInboxReviewMapper.Reviews reviews)
            throws IOException {
        try {
            var result = new ArrayList<Value>();
            String laboratory = source.information().fields().get("sending_facility");
            if (laboratory == null || laboratory.isEmpty())
                laboratory = new Terser(source.parsed().message()).get("/MSH-4-1");
            laboratory = literal(laboratory);
            if (laboratory.isEmpty()) review("CDS_EXPORT_NATIVE_LAB_NAME_REQUIRED");
            var raw = source.parsed().raw();
            for (var observation : source.parsed().observations()) {
                var text = new LinkedHashMap<String, String>();
                var dates = new LinkedHashMap<String, CdsExportDateDto>();
                Segment obx = observation.result(), obr = observation.order();
                put(text, "LaboratoryName", laboratory, 120);
                String reportedName = componentText(raw, obx, 3, 0, 2);
                put(text, "TestNameReportedByLab", reportedName, 120);
                put(text, "LabTestCode", scalar(obx, 3, 0, 1), 50);
                String orderedName = componentText(raw, obr, 4, 0, 2);
                // Issue #3959: receivers key their result list on TestName, so it carries the
                // laboratory's name for the test, or the ordered test's when the result has none.
                put(text, "TestName", reportedName.isEmpty() ? orderedName : reportedName, 120);
                // The accession CARLOS stores, shows and groups versions by is the handler's
                // (hl7TextInfo.accessionNum); OBR-3 is only the generic order number.
                String stored = source.information().fields().get("accessionNum");
                String order = scalar(obr, 3, 0, 1);
                String accession = stored == null || stored.isEmpty() ? order : stored;
                put(text, "AccessionNumber", accession, 120);
                String type = scalar(obx, 2, 0, 1);
                if (!Set.of("NM", "ST", "TX", "FT", "CE", "CWE", "CNE", "DT", "TS", "DTM")
                        .contains(type)) review("CDS_EXPORT_NATIVE_LAB_VALUE_TYPE_REQUIRES_REVIEW");
                String value;
                if (Set.of("CE", "CWE", "CNE").contains(type)) {
                    String code = scalar(obx, 5, observation.repetition(), 1);
                    String description =
                            componentText(raw, obx, 5, observation.repetition(), 2);
                    value =
                            description.isEmpty()
                                    ? code
                                    : code.isEmpty() ? description : code + " — " + description;
                } else if (Set.of("TX", "FT").contains(type)) {
                    // Each repetition of a TX/FT result is a continuation line of one narrative.
                    var lines = new ArrayList<String>();
                    for (String line : raw.repetitions(obx, 5)) lines.add(literal(decode(line, raw)));
                    value = String.join("\n", lines);
                } else if (type.equals("ST"))
                    value = rawText(raw, obx, 5, observation.repetition());
                else {
                    value = scalar(obx, 5, observation.repetition(), 1);
                    if (!value.equals(rawText(raw, obx, 5, observation.repetition())))
                        review("CDS_EXPORT_NATIVE_LAB_VALUE_COMPONENTS_REQUIRES_REVIEW");
                }
                if (type.equals("NM") && !value.isEmpty()) {
                    try {
                        new java.math.BigDecimal(value);
                    } catch (NumberFormatException invalid) {
                        review("CDS_EXPORT_NATIVE_LAB_NUMERIC_VALUE_INVALID");
                    }
                }
                // Only laboratory-reported facts: CARLOS provenance stays in the NewCategory
                // residual, never in a field a receiver shows as the laboratory's commentary.
                var information = new ArrayList<String>();
                String subId = scalar(obx, 4, 0, 1);
                if (!subId.isEmpty()) information.add("Observation sub-ID: " + subId);
                if (!orderedName.isEmpty()) information.add("Ordered test: " + orderedName);
                if (!order.isEmpty() && !order.equals(accession))
                    information.add("Order number: " + order);
                if (!value.isEmpty()) {
                    if (CdsExportNativeFieldAssessments.legal(value, 120)) {
                        put(text, "ResultValue", value, 120);
                        String unit = scalar(obx, 6, 0, 1);
                        // Units sent only as the CE text component are still the result's unit.
                        if (unit.isEmpty()) unit = componentText(raw, obx, 6, 0, 2);
                        put(text, "ResultUnit", unit, 120);
                    } else {
                        if (!TEXT_TYPES.contains(type))
                            review("CDS_EXPORT_NATIVE_LAB_RESULT_VALUE_CAPACITY");
                        information.add("Result text (complete):\n" + value);
                    }
                }
                put(text, "ReferenceRangeText", rawText(raw, obx, 7, 0), 1024);
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
                // The data dictionary translates a result with no flag to Unknown.
                boolean enumFlag = flag.isEmpty() || FLAGS.contains(flag);
                if (flag.isEmpty()) flag = UNKNOWN_FLAG;
                StringBuilder notes = new StringBuilder();
                for (var comment : observation.comments())
                    for (String line : raw.repetitions(comment, 3)) {
                        if (!notes.isEmpty()) notes.append('\n');
                        notes.append(literal(decode(line, raw)));
                    }
                put(
                        text,
                        "TestResultsInformationReportedByTheLab",
                        String.join("\n", information),
                        32000);
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
                                enumFlag,
                                blocked,
                                reviews.reviewers()));
            }
            return List.copyOf(result);
        } catch (HL7Exception failure) {
            review("CDS_EXPORT_NATIVE_LAB_FIELDS_REQUIRES_REVIEW");
            return List.of();
        }
    }

    static Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> assessments(
            CdsExportLaboratoryOccurrenceService.NativeSource source) throws IOException {
        return assessments(source, CdsExportInboxReviewMapper.Reviews.NONE);
    }

    /** The report's own rows, and its inbox routes and their providers as {@code reviews} decided them. */
    static Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> assessments(
            CdsExportLaboratoryOccurrenceService.NativeSource source,
            CdsExportInboxReviewMapper.Reviews reviews)
            throws IOException {
        var result = new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        result.putAll(reviews.fields());
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
        for (var reviewer : value.reviewers()) {
            xml.start("cds", "ResultReviewer");
            xml.start("cds", "Name");
            xml.element("cds_dt", "FirstName", reviewer.provider().firstName());
            xml.element("cds_dt", "LastName", reviewer.provider().lastName());
            xml.end();
            if (reviewer.provider().ohipNumber() != null)
                xml.element("cds", "OHIPPhysicianId", reviewer.provider().ohipNumber());
            xml.start("cds", "DateTimeResultReviewed");
            CdsExportNativeHistoryXmlWriter.dateContents(xml, reviewer.reviewed());
            xml.end();
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
        writeSupporting(source, List.of(), xml);
    }

    /**
     * The complete source message, metadata and patient routing, followed by every inbox route of
     * the report that carries a comment, whole, so no acknowledgement or inbox comment is lost.
     * A route without a comment adds no row: the record of a report nobody commented on is
     * unchanged.
     */
    void writeSupporting(
            CdsExportLaboratoryOccurrenceService.NativeSource source,
            List<CdsExportCapturedTextRowDto> carried,
            CdsExportXmlComposer.ContentWriter xml)
            throws IOException {
        xml.element("cds", "CategoryName", "CARLOS.NativeLaboratory.v1");
        xml.element(
                "cds",
                "CategoryDescription",
                "Complete native laboratory source message and routing context");
        xml.start("cds", "ResidualInfo");
        // Every map is built in one fixed key order (version, rows; each row table, then its
        // sorted fields), so an unchanged report produces the same residual bytes on every run
        // and worker. Map.of iterates in a per-JVM salted order.
        var rows = new ArrayList<Map<String, Object>>();
        for (var row : source.rows()) rows.add(supportingRow(row));
        for (var row : CdsExportInboxReviewMapper.carried(carried)) {
            if (!row.table().equals("providerLabRouting")) throw new IOException("CDS_EXPORT_NATIVE_LAB_ROUTE_INVALID");
            rows.add(supportingRow(row));
        }
        var context = new LinkedHashMap<String, Object>();
        context.put("version", 1);
        context.put("rows", rows);
        CdsExportNativeHistoryXmlWriter.residualElement(
                xml,
                "CARLOS.Laboratory.v1",
                "string",
                JsonMapper.builder().build().writeValueAsString(context));
        xml.end();
    }

    private static Map<String, Object> supportingRow(CdsExportCapturedTextRowDto row) {
        var entry = new LinkedHashMap<String, Object>();
        entry.put("table", row.table());
        entry.put("fields", new TreeMap<>(row.fields()));
        return entry;
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

    /** The laboratory's whole text of one repetition; separators it left unescaped stay literal. */
    private static String rawText(
            CdsExportNativeLaboratoryParser.Raw raw, Segment segment, int field, int repetition)
            throws IOException {
        var repetitions = raw.repetitions(segment, field);
        return repetition >= repetitions.size()
                ? ""
                : literal(decode(repetitions.get(repetition), raw));
    }

    /** One component's text; subcomponent separators the laboratory left unescaped stay literal. */
    private static String componentText(
            CdsExportNativeLaboratoryParser.Raw raw,
            Segment segment,
            int field,
            int repetition,
            int component)
            throws IOException {
        var repetitions = raw.repetitions(segment, field);
        if (repetition >= repetitions.size()) return "";
        var components =
                CdsExportNativeLaboratoryParser.Raw.split(
                        repetitions.get(repetition), raw.component);
        return component > components.size()
                ? ""
                : literal(decode(components.get(component - 1), raw));
    }

    /**
     * Decodes HL7 escapes in raw text the way CARLOS displays it: the delimiter escapes, line
     * breaks ({@code .br}, {@code .sp[n]}), spaces ({@code .sk n}) and UTF-8 hex
     * ({@code Xhh..}); highlighting and layout commands ({@code H}, {@code N}, {@code .in},
     * {@code .ti}, {@code .ce}, {@code .fi}, {@code .nf}) carry no text. Decoding from the raw
     * text keeps an escaped escape (a literal backslash sequence) distinct from a line break.
     * Any other sequence is kept as written.
     */
    static String decode(String text, CdsExportNativeLaboratoryParser.Raw raw) throws IOException {
        var out = new StringBuilder(text.length());
        int index = 0;
        while (index < text.length()) {
            int end = text.charAt(index) == raw.escape ? text.indexOf(raw.escape, index + 1) : -1;
            if (end < 0) {
                out.append(text.charAt(index++));
                continue;
            }
            String code = text.substring(index + 1, end);
            switch (code) {
                case "F" -> out.append(raw.field);
                case "S" -> out.append(raw.component);
                case "T" -> out.append(raw.subcomponent);
                case "R" -> out.append(raw.repetition);
                case "E" -> out.append(raw.escape);
                case ".br" -> out.append('\n');
                case "H", "N", ".ce", ".fi", ".nf" -> {
                    // Highlighting and layout only.
                }
                default -> {
                    if (code.matches("\\.sp[+]?[0-9]{0,3}"))
                        out.append(
                                "\n".repeat(
                                        code.length() > 3
                                                ? Math.max(1, Math.min(64, Integer.parseInt(code.substring(3).replace("+", ""))))
                                                : 1));
                    else if (code.matches("\\.sk[+]?[0-9]{1,3}"))
                        out.append(" ".repeat(Math.min(256, Integer.parseInt(code.substring(3).replace("+", "")))));
                    else if (code.matches("\\.(?:in|ti)[+-]?[0-9]{0,3}")) {
                        // Indentation only.
                    } else if (code.matches("X(?:[0-9A-Fa-f]{2})+")) {
                        try {
                            out.append(
                                    java.nio.charset.StandardCharsets.UTF_8
                                            .newDecoder()
                                            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                                            .onUnmappableCharacter(
                                                    java.nio.charset.CodingErrorAction.REPORT)
                                            .decode(
                                                    java.nio.ByteBuffer.wrap(
                                                            java.util.HexFormat.of()
                                                                    .parseHex(code.substring(1)))));
                        } catch (java.nio.charset.CharacterCodingException invalid) {
                            review("CDS_EXPORT_NATIVE_LAB_ESCAPE_REQUIRES_REVIEW");
                        }
                    } else out.append(text, index, end + 1);
                }
            }
            index = end + 1;
        }
        return out.toString();
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
