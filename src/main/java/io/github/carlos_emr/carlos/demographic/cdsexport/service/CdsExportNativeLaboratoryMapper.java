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
 * {@link #decode}). A value of any type is written: one this mapping does not read structurally
 * is the laboratory's whole text of it. A time is written at the precision the laboratory sent
 * ({@link #temporal}); one that is no calendar time, or whose offset no {@code xs:dateTime}
 * holds, is stated with the laboratory's facts as it was sent ({@link #REQUISITION_SENT},
 * {@link #COLLECTION_SENT}).
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

    /**
     * Starts the TestResultsInformationReportedByTheLab line that states a requisition time
     * (OBR-6) exactly as the laboratory sent it, when it is not written as sent: it is no
     * calendar time, or its offset is one no {@code xs:dateTime} holds.
     */
    static final String REQUISITION_SENT = "Requisition time sent by the laboratory: ";

    /**
     * Starts the TestResultsInformationReportedByTheLab line that states a collection time
     * (OBX-14 or OBR-7) exactly as the laboratory sent it, when it is not written as sent.
     */
    static final String COLLECTION_SENT = "Collection time sent by the laboratory: ";

    /**
     * Starts the TestResultsInformationReportedByTheLab line that holds a laboratory name longer
     * than LaboratoryName's 120 characters, whole.
     */
    static final String LABORATORY_NAME_COMPLETE = "Laboratory name (complete): ";

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
            String laboratory = laboratoryName(source);
            var raw = source.parsed().raw();
            for (var observation : source.parsed().observations()) {
                var text = new LinkedHashMap<String, String>();
                var dates = new LinkedHashMap<String, CdsExportDateDto>();
                Segment obx = observation.result(), obr = observation.order();
                // LaboratoryName is mandatory and its type allows empty text: a report that
                // names no laboratory has an empty name, as CARLOS holds it, and a name longer
                // than the element is written whole with the laboratory's facts.
                String named = CdsExportXmlText.element(laboratory);
                boolean whole = named.codePointCount(0, named.length()) <= 120;
                text.put("LaboratoryName", whole ? named : "");
                String reportedName = componentText(raw, obx, 3, 0, 2);
                put(text, "TestNameReportedByLab", reportedName, 120);
                put(text, "LabTestCode", scalar(obx, 3, 0, 1), 50);
                String orderedName = componentText(raw, obr, 4, 0, 2);
                // Issue #3959: receivers key their result list on TestName, so it carries the
                // laboratory's name for the test, or the ordered test's when the result has none.
                put(text, "TestName", reportedName.isEmpty() ? orderedName : reportedName, 120);
                // The accession CARLOS stores, shows and groups versions by is the handler's
                // (hl7TextInfo.accessionNum); OBR-3 is only the generic order number.
                String stored = source.stored("accessionNum");
                String order = scalar(obr, 3, 0, 1);
                String accession = stored == null || stored.isEmpty() ? order : stored;
                put(text, "AccessionNumber", accession, 120);
                String type = scalar(obx, 2, 0, 1);
                // ED (an embedded document) is not a result value: the parser exports it as a
                // Reports entry (CdsExportLaboratoryDocumentMapper). A value type this mapping
                // does not read structurally (a laboratory's own NUM, TXT or DOC, an RP, TM or
                // any other) is the laboratory's text of the value, as for ST.
                String value;
                if (type.equals("SN")) {
                    value = structuredNumeric(raw, obx, observation.repetition());
                    // Not a structured numeric ("^POSITIVE", five components): its whole text.
                    if (value == null) value = rawText(raw, obx, 5, observation.repetition());
                } else if (Set.of("CE", "CWE", "CNE").contains(type)) {
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
                } else
                    // ST, a numeric or date value and any other type: the laboratory's whole
                    // text of the value. A number that is no decimal ("<5", "POSITIVE") or a value
                    // with more components than its first is written as sent, never refused.
                    value = rawText(raw, obx, 5, observation.repetition());
                // Only laboratory-reported facts: CARLOS provenance stays in the NewCategory
                // residual, never in a field a receiver shows as the laboratory's commentary.
                var information = new ArrayList<String>();
                if (!whole) information.add(LABORATORY_NAME_COMPLETE + named);
                String subId = scalar(obx, 4, 0, 1);
                if (!subId.isEmpty()) information.add("Observation sub-ID: " + subId);
                if (!orderedName.isEmpty()) information.add("Ordered test: " + orderedName);
                if (!order.isEmpty() && !order.equals(accession))
                    information.add("Order number: " + order);
                if (!value.isEmpty()) {
                    // The parser already shows U+FFFD for a character XML cannot carry.
                    value = CdsExportXmlText.element(value);
                    if (value.codePointCount(0, value.length()) <= 120) {
                        put(text, "ResultValue", value, 120);
                        String unit = scalar(obx, 6, 0, 1);
                        // Units sent only as the CE text component are still the result's unit.
                        if (unit.isEmpty()) unit = componentText(raw, obx, 6, 0, 2);
                        put(text, "ResultUnit", unit, 120);
                    } else
                        // Longer than ResultValue (120 characters), whatever the observation's
                        // type: never clipped, and written whole with the laboratory's facts.
                        information.add("Result text (complete):\n" + value);
                }
                put(text, "ReferenceRangeText", rawText(raw, obx, 7, 0), 1024);
                String requested = scalar(obr, 6, 0, 1);
                if (!requested.isEmpty()) {
                    // A time that is no calendar time, or whose offset no xs:dateTime holds, is
                    // not written as sent: the laboratory's text is stated with its facts.
                    var requisition = read(requested, zone);
                    if (requisition.date() != null)
                        dates.put("LabRequisitionDateTime", requisition.date());
                    if (!requisition.asSent()) information.add(REQUISITION_SENT + requested);
                }
                // The collection time is the first of OBX-14 and OBR-7 that the laboratory sent
                // and that is a calendar time; a sent time passed over, or written without its
                // offset, is stated with the laboratory's facts. None: the report date the chart
                // lists (CHART_REPORT_DATE_WITHOUT_SENT_COLLECTION_TIME, recorded in the residual).
                CdsExportDateDto collection = null;
                for (String sent : List.of(scalar(obx, 14, 0, 1), scalar(obr, 7, 0, 1))) {
                    if (sent.isEmpty()) continue;
                    var reading = read(sent, zone);
                    if (!reading.asSent()) information.add(COLLECTION_SENT + sent);
                    if (reading.date() != null) {
                        collection = reading.date();
                        break;
                    }
                }
                dates.put("CollectionDateTime", collection == null ? chartDate(source, zone) : collection);
                String flag = scalar(obx, 8, 0, 1);
                if (obx.getField(8).length > 1) {
                    var flags = new ArrayList<String>();
                    for (int i = 0; i < obx.getField(8).length; i++)
                        flags.add(scalar(obx, 8, i, 1));
                    flag = String.join("; ", flags);
                }
                if (!CdsExportNativeFieldAssessments.legal(flag, 50)) {
                    // Flags longer than the element (several repetitions joined), or holding a
                    // character XML cannot carry: written whole with the laboratory's facts
                    // (U+FFFD for such a character); the flag element says Unknown.
                    information.add("Abnormal flags (complete): " + CdsExportXmlText.element(flag));
                    flag = "";
                }
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

    /**
     * Rule of every field of a report whose message names several patients, none of them this
     * chart's: nothing of it is exported and the ReadMe names it (owner decision 2026-11-14).
     */
    static final String OTHER_PATIENTS_ONLY = "MESSAGE_NAMES_ONLY_OTHER_PATIENTS_NOT_EXPORTED";

    /**
     * Rule of the stored message of a report that names several patients: the carried message is
     * this chart's part of it (the MSH segment and this chart's PID groups), not the stored one.
     */
    static final String THIS_PATIENTS_PART = "THIS_PATIENTS_PART_OF_A_MESSAGE_NAMING_SEVERAL_PATIENTS";

    /**
     * Rule of a value the upload stored for such a report from the message's first patient when
     * that patient's part is not this chart's: it is neither written nor carried.
     */
    static final String ANOTHER_PATIENTS_PART = "ANOTHER_PATIENTS_PART_OF_THE_MESSAGE_NOT_EXPORTED";

    /** The report's own rows, and its inbox routes and their providers as {@code reviews} decided them. */
    static Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> assessments(
            CdsExportLaboratoryOccurrenceService.NativeSource source,
            CdsExportInboxReviewMapper.Reviews reviews)
            throws IOException {
        var result = new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        var selection = source.parsed().selection();
        if (source.foreign()) {
            for (var row : source.rows()) {
                var assessed = new CdsExportNativeFieldAssessments(row, result);
                for (String column : row.fields().keySet())
                    assessed.operational(column, OTHER_PATIENTS_ONLY);
            }
            return result;
        }
        result.putAll(reviews.fields());
        // The stored field the chart's report date came from, when an observation carries it:
        // obr_date (directly or through the HL7 field it names) or, when the upload stored none,
        // the time CARLOS stored the report. MSH-7 travels in the message itself.
        String dated = undated(source).isEmpty() ? null : chartDateSource(source);
        var chartRow =
                CREATED.equals(dated)
                        ? source.message()
                        : dated == null || dated.equals("MSH-7") ? null : source.information();
        String chartField = CREATED.equals(dated) ? "created" : "obr_date";
        for (var row : source.rows()) {
            var assessed = new CdsExportNativeFieldAssessments(row, result);
            for (var entry : row.fields().entrySet()) {
                // No value is refused for its characters or length: CARLOS.Laboratory.v1 is
                // JSON, where a character XML cannot carry is its escape, and residual Content
                // has no maxLength.
                if (row == chartRow && entry.getKey().equals(chartField)) {
                    assessed.set(
                            entry.getKey(),
                            CdsExportFieldAssessmentDto.Outcome.MAPPED,
                            "LaboratoryResults/CollectionDateTime",
                            CHART_REPORT_DATE);
                    continue;
                }
                if (row == source.message()
                        && entry.getKey().equals("message")
                        && selection.part() == CdsExportNativeLaboratoryParser.Part.THIS_PATIENT) {
                    assessed.set(
                            entry.getKey(),
                            CdsExportFieldAssessmentDto.Outcome.MAPPED,
                            "NewCategory/ResidualInfo",
                            THIS_PATIENTS_PART);
                    continue;
                }
                if (row == source.information()
                        && !selection.storedInformation()
                        && !CdsExportLaboratoryOccurrenceService.NativeSource.CARLOS_INFORMATION
                                .contains(entry.getKey())) {
                    assessed.operational(entry.getKey(), ANOTHER_PATIENTS_PART);
                    continue;
                }
                assessed.set(
                        entry.getKey(),
                        CdsExportFieldAssessmentDto.Outcome.MAPPED,
                        "NewCategory/ResidualInfo",
                        "COMPLETE_NATIVE_LABORATORY_SOURCE_CONTEXT");
            }
        }
        return result;
    }

    /**
     * Every field of a report no part of which is this chart's ({@link #OTHER_PATIENTS_ONLY}) and
     * of its inbox routes: nothing of it is exported, every value is accounted.
     */
    static Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> foreign(
            CdsExportLaboratoryOccurrenceService.NativeSource source,
            List<CdsExportCapturedTextRowDto> routes)
            throws IOException {
        if (!source.foreign()) throw new IOException("CDS_EXPORT_NATIVE_LAB_SELECTION_INVALID");
        var result =
                new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>(
                        assessments(source, CdsExportInboxReviewMapper.Reviews.NONE));
        for (var route : routes) {
            var assessed = new CdsExportNativeFieldAssessments(route, result);
            for (String column : route.fields().keySet())
                assessed.operational(column, OTHER_PATIENTS_ONLY);
        }
        return result;
    }

    /** Rule of a collection date taken from the report date the chart lists. */
    static final String CHART_REPORT_DATE = "CHART_REPORT_DATE_WITHOUT_SENT_COLLECTION_TIME";

    /** The source named when the chart's date is the time CARLOS stored the report. */
    static final String CREATED = "hl7TextMessage.created";

    /** The report date as MessageUploader stores it: always a full clock, padded when needed. */
    private static final java.util.regex.Pattern STORED_REPORT_DATE =
            java.util.regex.Pattern.compile("([0-9]{4}-[0-9]{2}-[0-9]{2}) [0-9]{2}:[0-9]{2}:[0-9]{2}(?:\\.0)?");

    /** An HL7 TS: its wall-clock digits, then any fraction and offset the stored date drops. */
    private static final java.util.regex.Pattern HL7_TIMESTAMP =
            java.util.regex.Pattern.compile(
                    "([0-9]{4})([0-9]{2})?([0-9]{2})?([0-9]{2})?([0-9]{2})?([0-9]{2})?(?:\\.[0-9]{1,9})?(?:[+-][0-9]{4})?");

    /**
     * The ordinals of the observations the laboratory sent no collection time for that is a
     * calendar time (OBX-14 and OBR-7 empty, or neither readable): they carry the report date the
     * chart lists.
     */
    static List<String> undated(CdsExportLaboratoryOccurrenceService.NativeSource source)
            throws IOException {
        var result = new ArrayList<String>();
        try {
            for (var observation : source.parsed().observations())
                if (read(scalar(observation.result(), 14, 0, 1), ZoneOffset.UTC).date() == null
                        && read(scalar(observation.order(), 7, 0, 1), ZoneOffset.UTC).date()
                                == null) result.add(observation.ordinal());
        } catch (HL7Exception failure) {
            review("CDS_EXPORT_NATIVE_LAB_FIELDS_REQUIRES_REVIEW");
        }
        return List.copyOf(result);
    }

    /**
     * Where the report date the chart lists came from. MessageUploader stores the handler's
     * service date (EpsilonHandler.getServiceDate is OBR-6), or the message date (MSH-7) when
     * that is empty, as {@code hl7TextInfo.obr_date}, reformatted to a full clock. The first
     * OBR-7, OBR-6, OBR-14 or OBR-22 (in message order), then MSH-7, whose wall clock equals the
     * stored date names its field, so the date keeps the precision the laboratory sent; when none
     * does, the stored date itself is the source. The upload stores an empty date when it cannot
     * read the handler's ({@code UtilDateUtilities.StringToDate} answers null and {@code
     * DateToString} writes ""): the message time (MSH-7) is then the source when it is a calendar
     * time, and otherwise the time CARLOS stored the report ({@code hl7TextMessage.created},
     * never null).
     *
     * @return the field name, such as {@code OBR[1]-6}, {@code MSH-7}, {@code
     *     hl7TextInfo.obr_date} or {@code hl7TextMessage.created}; null when the report has no
     *     date at all
     */
    static String chartDateSource(CdsExportLaboratoryOccurrenceService.NativeSource source)
            throws IOException {
        var candidate = chartDateCandidate(source);
        return candidate == null ? null : candidate.getKey();
    }

    private static Map.Entry<String, String> chartDateCandidate(
            CdsExportLaboratoryOccurrenceService.NativeSource source) throws IOException {
        String stored = source.stored("obr_date");
        var match = stored == null ? null : STORED_REPORT_DATE.matcher(stored);
        try {
            String sent = literal(new Terser(source.parsed().message()).get("/MSH-7-1"));
            if (match == null || !match.matches()) {
                if (read(sent, ZoneOffset.UTC).date() != null) return Map.entry("MSH-7", sent);
                String created = source.message().fields().get("created");
                var received = created == null ? null : STORED_REPORT_DATE.matcher(created);
                return received == null || !received.matches() ? null : Map.entry(CREATED, created);
            }
            String wall = stored.substring(0, 19);
            int order = 0;
            for (var segment : source.parsed().raw().orders()) {
                order++;
                for (int field : new int[] {7, 6, 14, 22}) {
                    String value = scalar(segment, field, 0, 1);
                    if (wall.equals(wallClock(value)))
                        return Map.entry("OBR[" + order + "]-" + field, value);
                }
            }
            if (wall.equals(wallClock(sent))) return Map.entry("MSH-7", sent);
        } catch (HL7Exception failure) {
            review("CDS_EXPORT_NATIVE_LAB_FIELDS_REQUIRES_REVIEW");
        }
        return Map.entry("hl7TextInfo.obr_date", stored);
    }

    /** An HL7 TS as the upload stores it, {@code yyyy-MM-dd HH:mm:ss}, or null. */
    private static String wallClock(String value) {
        if (value == null || value.isEmpty()) return null;
        var parts = HL7_TIMESTAMP.matcher(value);
        if (!parts.matches()) return null;
        return parts.group(1)
                + "-"
                + (parts.group(2) == null ? "01" : parts.group(2))
                + "-"
                + (parts.group(3) == null ? "01" : parts.group(3))
                + " "
                + (parts.group(4) == null ? "00" : parts.group(4))
                + ":"
                + (parts.group(5) == null ? "00" : parts.group(5))
                + ":"
                + (parts.group(6) == null ? "00" : parts.group(6));
    }

    /**
     * The collection date of an observation the laboratory sent no collection time for: the
     * report date the chart lists, at the precision of the HL7 field it came from, or the stored
     * date's day when no field gives it (the upload pads the time); the day CARLOS stored the
     * report when the upload stored no date and MSH-7 is no calendar time. Every stored report has
     * that time ({@code hl7TextMessage.created} is NOT NULL and set on every store), so only a row
     * written outside CARLOS without it is still held.
     */
    static CdsExportDateDto chartDate(
            CdsExportLaboratoryOccurrenceService.NativeSource source, ZoneId zone)
            throws IOException {
        var candidate = chartDateCandidate(source);
        if (candidate == null) review("CDS_EXPORT_NATIVE_LAB_COLLECTION_REQUIRED");
        if (candidate.getKey().equals("hl7TextInfo.obr_date") || candidate.getKey().equals(CREATED))
            try {
                return new CdsExportDateDto(
                        CdsExportDateDto.Precision.DATE,
                        LocalDate.parse(candidate.getValue().substring(0, 10)).toString());
            } catch (DateTimeException invalid) {
                review("CDS_EXPORT_NATIVE_LAB_COLLECTION_REQUIRED");
            }
        var date = temporal(candidate.getValue(), zone);
        if (date == null) review("CDS_EXPORT_NATIVE_LAB_COLLECTION_REQUIRED");
        return date;
    }

    /**
     * The laboratory's name: the sending facility the upload stored, else the first component of
     * MSH-4; empty when the report names none.
     */
    static String laboratoryName(CdsExportLaboratoryOccurrenceService.NativeSource source)
            throws HL7Exception {
        String laboratory = source.stored("sending_facility");
        if (laboratory == null || laboratory.isEmpty())
            laboratory = new Terser(source.parsed().message()).get("/MSH-4-1");
        return literal(laboratory);
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
            reviewerContent(reviewer, xml);
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

    /** The content of one ResultReviewer: the provider's public name, OHIP number and time. */
    static void reviewerContent(
            CdsExportInboxReviewMapper.Review reviewer, CdsExportXmlComposer.ContentWriter xml)
            throws IOException {
        xml.start("cds", "Name");
        xml.element("cds_dt", "FirstName", reviewer.provider().firstName());
        xml.element("cds_dt", "LastName", reviewer.provider().lastName());
        xml.end();
        if (reviewer.provider().ohipNumber() != null)
            xml.element("cds", "OHIPPhysicianId", reviewer.provider().ohipNumber());
        xml.start("cds", "DateTimeResultReviewed");
        CdsExportNativeHistoryXmlWriter.dateContents(xml, reviewer.reviewed());
        xml.end();
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
        if (source.foreign()) throw new IOException("CDS_EXPORT_NATIVE_LAB_SELECTION_INVALID");
        var selection = source.parsed().selection();
        var rows = new ArrayList<Map<String, Object>>();
        for (var row : source.rows()) rows.add(supportingRow(carriedRow(source, row)));
        for (var row : CdsExportInboxReviewMapper.carried(carried)) {
            if (!row.table().equals("providerLabRouting")) throw new IOException("CDS_EXPORT_NATIVE_LAB_ROUTE_INVALID");
            rows.add(supportingRow(row));
        }
        var context = new LinkedHashMap<String, Object>();
        context.put("version", 1);
        context.put("rows", rows);
        // Only a report whose laboratory sent no collection time gains this entry, so every
        // other report keeps its residual bytes.
        var undated = undated(source);
        if (!undated.isEmpty()) {
            var collection = new LinkedHashMap<String, Object>();
            collection.put("rule", CHART_REPORT_DATE);
            String field = chartDateSource(source);
            collection.put("source", field == null ? "" : field);
            collection.put("observations", undated);
            context.put("collection", collection);
        }
        // Only a message that names several patients gains this entry: the carried message is this
        // chart's part of it (owner decision 2026-11-14).
        if (selection.part() == CdsExportNativeLaboratoryParser.Part.THIS_PATIENT) {
            var part = new LinkedHashMap<String, Object>();
            part.put("rule", THIS_PATIENTS_PART);
            part.put("groups", selection.groups());
            part.put("exported", selection.exported());
            context.put("part", part);
        }
        CdsExportNativeHistoryXmlWriter.residualElement(
                xml,
                "CARLOS.Laboratory.v1",
                "string",
                CdsExportScreeningMapper.tokenSafe(
                        JsonMapper.builder().build().writeValueAsString(context)));
        xml.end();
    }

    /**
     * The NewCategory record of the inbox comments recorded in CARLOS on a laboratory result from
     * an earlier CDS import: each commented route whole, with its provider, status and time, in
     * one fixed key order. The result itself is exported from its imported elements.
     */
    static void writeImportedComments(
            CdsExportLaboratoryOccurrenceService.ImportedComments comments,
            CdsExportXmlComposer.ContentWriter xml)
            throws IOException {
        xml.element("cds", "CategoryName", "CARLOS.ImportedLaboratoryReview.v1");
        xml.element(
                "cds",
                "CategoryDescription",
                "Inbox comments recorded in CARLOS on a laboratory result from an earlier CDS import");
        xml.start("cds", "ResidualInfo");
        var rows = new ArrayList<Map<String, Object>>();
        for (var row : comments.routes()) {
            if (!row.table().equals(CdsExportLaboratoryOccurrenceService.ROUTES))
                throw new IOException("CDS_EXPORT_NATIVE_LAB_ROUTE_INVALID");
            rows.add(supportingRow(row));
        }
        var context = new LinkedHashMap<String, Object>();
        context.put("version", 1);
        context.put("labNo", comments.lab());
        context.put("rows", rows);
        CdsExportNativeHistoryXmlWriter.residualElement(
                xml,
                "CARLOS.ImportedLaboratoryReview.v1",
                "string",
                CdsExportScreeningMapper.tokenSafe(
                        JsonMapper.builder().build().writeValueAsString(context)));
        xml.end();
    }

    /**
     * A report row as its supporting record carries it: the stored message is this chart's part
     * of a message that names several patients, and the values the upload read from another
     * patient's part are left out (only the columns CARLOS assigns stay).
     */
    private static CdsExportCapturedTextRowDto carriedRow(
            CdsExportLaboratoryOccurrenceService.NativeSource source,
            CdsExportCapturedTextRowDto row) {
        var selection = source.parsed().selection();
        if (row == source.message()
                && selection.part() == CdsExportNativeLaboratoryParser.Part.THIS_PATIENT) {
            var fields = new java.util.HashMap<>(row.fields());
            fields.put("message", selection.carried());
            return new CdsExportCapturedTextRowDto(row.table(), row.recordKey(), fields);
        }
        if (row == source.information() && !selection.storedInformation()) {
            var fields = new java.util.HashMap<String, String>();
            for (String column : CdsExportLaboratoryOccurrenceService.NativeSource.CARLOS_INFORMATION)
                if (row.fields().containsKey(column)) fields.put(column, row.fields().get(column));
            return new CdsExportCapturedTextRowDto(row.table(), row.recordKey(), fields);
        }
        return row;
    }

    private static Map<String, Object> supportingRow(CdsExportCapturedTextRowDto row) {
        var entry = new LinkedHashMap<String, Object>();
        entry.put("table", row.table());
        entry.put("fields", new TreeMap<>(row.fields()));
        return entry;
    }

    /**
     * An HL7 structured numeric (SN) value as the result text: comparator, first number,
     * separator or suffix and second number, written together as the laboratory reports them
     * ({@code ">60"}, {@code "1:20"}, {@code "10-20"}, {@code "2+"}). Some laboratories escape the
     * SN's own component separators ({@code \S\1\S\:\S\20}, the Excelleris guide's titre);
     * the escaped text then holds the same four components.
     *
     * @return the rendered value, or null when the value is not a structured numeric in either
     *     form; the caller then writes the laboratory's whole text of it
     */
    static String structuredNumeric(
            CdsExportNativeLaboratoryParser.Raw raw, Segment obx, int repetition)
            throws IOException {
        var repetitions = raw.repetitions(obx, 5);
        if (repetition >= repetitions.size()) return "";
        var components =
                CdsExportNativeLaboratoryParser.Raw.split(repetitions.get(repetition), raw.component);
        var parts = new ArrayList<String>();
        for (String component : components)
            if (component.indexOf(raw.subcomponent) >= 0) return null;
            else parts.add(literal(decode(component, raw)));
        if (parts.size() == 1 && parts.getFirst().indexOf(raw.component) >= 0)
            parts =
                    new ArrayList<>(
                            CdsExportNativeLaboratoryParser.Raw.split(
                                    parts.getFirst(), raw.component));
        if (parts.size() > 4) return null;
        String rendered = String.join("", parts);
        if (!rendered.isEmpty() && !STRUCTURED_NUMERIC.matcher(rendered).matches()) return null;
        return rendered;
    }

    /** Comparator, number, then a separator or suffix with an optional second number. */
    private static final java.util.regex.Pattern STRUCTURED_NUMERIC = structuredNumeric();

    // FindSecBugs REDOS: Matched whole against one rendered value; every group is optional once and none is repeated, so a digit run is walked a bounded number of times (24,000-character adversarial values match in about a millisecond).
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "REDOS", justification = "matched whole against one rendered value; every group is optional once and none is repeated, so a digit run is walked a bounded number of times (24,000-character adversarial values match in about a millisecond)")
    private static java.util.regex.Pattern structuredNumeric() {
        return java.util.regex.Pattern.compile(
                "(?:(?:>=|<=|<>|>|<|=) ?)?[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)"
                        + "(?:[-+/.:](?:[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+))?)?");
    }

    /**
     * How an HL7 time is written: its CDS date, or null for text that is no calendar time, and
     * whether that date is the time as the laboratory sent it.
     */
    record Reading(CdsExportDateDto date, boolean asSent) {}

    private static final Reading UNREADABLE = new Reading(null, false);

    /** An HL7 TS (DTM): digits, an optional fraction of a second and an optional offset. */
    private static final java.util.regex.Pattern SENT_TIME =
            java.util.regex.Pattern.compile(
                    "([0-9]{4}(?:[0-9]{2}){0,5})(\\.[0-9]{1,9})?(?:([+-][0-9]{2})([0-9]{2}))?");

    /**
     * An HL7 time as a CDS date, at the precision the laboratory sent: {@code YYYY} a year,
     * {@code YYYYMM} a month, {@code YYYYMMDD} a date; hour or minute precision ({@code
     * YYYYMMDDHH[MM]}) its date, since a CDS date-time carries seconds that were never sent;
     * seconds a date-time at the laboratory's offset, or in the clinic's zone when it sent none
     * ({@link CdsExportNativeHistoryMapper#clock}). An offset sent with less than seconds says
     * nothing a date keeps. A seconds time whose offset no {@code xs:dateTime} holds (beyond
     * &plusmn;14:00, or minutes of 60 or more) is the laboratory's wall clock without an offset.
     *
     * @return the date, or null when the text is no calendar time (not an HL7 TS, year 0000, a
     *     thirteenth month, an hour 24, ...)
     */
    static CdsExportDateDto temporal(String value, ZoneId zone) throws IOException {
        return read(value, zone).date();
    }

    /** {@link #temporal}, and whether the date written is the time as the laboratory sent it. */
    static Reading read(String value, ZoneId zone) throws IOException {
        var parts = value == null ? null : SENT_TIME.matcher(value);
        if (parts == null || !parts.matches()) return UNREADABLE;
        String digits = parts.group(1), fraction = parts.group(2);
        String hours = parts.group(3), minutes = parts.group(4);
        if (digits.startsWith("0000") || fraction != null && digits.length() != 14)
            return UNREADABLE;
        try {
            int year = Integer.parseInt(digits.substring(0, 4));
            switch (digits.length()) {
                case 4 -> {
                    return new Reading(
                            new CdsExportDateDto(
                                    CdsExportDateDto.Precision.YEAR, Year.of(year).toString()),
                            true);
                }
                case 6 -> {
                    return new Reading(
                            new CdsExportDateDto(
                                    CdsExportDateDto.Precision.YEAR_MONTH,
                                    YearMonth.of(year, Integer.parseInt(digits.substring(4)))
                                            .toString()),
                            true);
                }
                case 8 -> {
                    return new Reading(date(digits), true);
                }
                case 10, 12 -> {
                    // Hour or minute precision: the date the laboratory wrote, never an invented
                    // ":00"; the exact time stays in the complete source message.
                    LocalTime.of(
                            Integer.parseInt(digits.substring(8, 10)),
                            digits.length() == 12 ? Integer.parseInt(digits.substring(10, 12)) : 0);
                    return new Reading(date(digits), true);
                }
                default -> {
                    String iso =
                            digits.substring(0, 4)
                                    + "-"
                                    + digits.substring(4, 6)
                                    + "-"
                                    + digits.substring(6, 8)
                                    + "T"
                                    + digits.substring(8, 10)
                                    + ":"
                                    + digits.substring(10, 12)
                                    + ":"
                                    + digits.substring(12, 14)
                                    + (fraction == null ? "" : fraction);
                    var local = LocalDateTime.parse(iso);
                    if (hours == null)
                        return new Reading(CdsExportNativeHistoryMapper.clock(iso, zone), true);
                    int offsetMinutes = Integer.parseInt(minutes);
                    int offsetHours = Integer.parseInt(hours.substring(1));
                    if (offsetMinutes >= 60 || offsetHours * 60 + offsetMinutes > 14 * 60)
                        // No xs:dateTime holds this offset: the laboratory's wall clock,
                        // without one; the sent text is stated with the laboratory's facts.
                        return new Reading(
                                new CdsExportDateDto(
                                        CdsExportDateDto.Precision.DATE_TIME,
                                        DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(local)),
                                false);
                    var offset = ZoneOffset.of(hours + ":" + minutes);
                    return new Reading(
                            new CdsExportDateDto(
                                    CdsExportDateDto.Precision.DATE_TIME,
                                    DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(local.atOffset(offset))),
                            true);
                }
            }
        } catch (DateTimeException | NumberFormatException notACalendarTime) {
            return UNREADABLE;
        }
    }

    private static CdsExportDateDto date(String digits) {
        return new CdsExportDateDto(
                CdsExportDateDto.Precision.DATE,
                LocalDate.parse(digits.substring(0, 8), DateTimeFormatter.BASIC_ISO_DATE).toString());
    }

    /** One component's text; empty for the order fields of a result sent before any order. */
    private static String scalar(Segment segment, int field, int repetition, int component)
            throws HL7Exception {
        return segment == null ? "" : literal(Terser.get(segment, field, repetition, component, 1));
    }

    /** The laboratory's whole text of one repetition; separators it left unescaped stay literal. */
    private static String rawText(
            CdsExportNativeLaboratoryParser.Raw raw, Segment segment, int field, int repetition)
            throws IOException {
        if (segment == null) return "";
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
        if (segment == null) return "";
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
                            // Hexadecimal data that is not UTF-8 text (another character set):
                            // the escape is written as the laboratory sent it, like any escape
                            // this reader does not interpret, and the message travels exactly
                            // in the report's CARLOS.NativeLaboratory.v1 record.
                            out.append(text, index, end + 1);
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
        // A character XML cannot carry is shown as U+FFFD. A value longer than its element is
        // left out whole, never clipped. Either way the complete laboratory message and rows
        // travel exactly in the report's CARLOS.NativeLaboratory.v1 record.
        String element = CdsExportXmlText.element(value);
        if (element.codePointCount(0, element.length()) > maximum) return;
        values.put(name, element);
    }

    private static void text(Value value, String name, CdsExportXmlComposer.ContentWriter xml)
            throws IOException {
        if (value.text().containsKey(name)) xml.element("cds", name, value.text().get(name));
    }

    private static void review(String code) throws CdsExportMappingReviewException {
        throw new CdsExportMappingReviewException(code);
    }
}
