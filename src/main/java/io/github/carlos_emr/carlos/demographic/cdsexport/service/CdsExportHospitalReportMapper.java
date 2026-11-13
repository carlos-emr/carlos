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
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedTextRowDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDateDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Outcome;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportNativeFieldKeyDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportPublicProviderDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportMappingReviewException;
import java.io.IOException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Maps one Hospital Report Manager report of the chart to one CDS 5.2 Reports entry, with an
 * explicit disposition for every captured field.
 *
 * <p>The report's own message ({@link CdsExportHospitalReportMessage}) gives the standard
 * elements, exactly as CARLOS shows them: Media, Format, FileExtensionAndVersion, the content
 * (TextContent, or the decoded binary report attached at FilePath), Class, SubClass,
 * EventDateTime, ReceivedDateTime, SourceAuthorPhysician, SendingFacilityId,
 * SendingFacilityReport, OBRContent, HRMResultStatus, MessageUniqueID and RecipientName (the
 * provider the message was delivered to). SourceFacility is the facility CARLOS recorded for the
 * report ({@code HRMDocument.sourceFacility}), as the legacy exporter wrote it. Each sign-off
 * ({@code HRMDocumentToProvider.signedOff} 1) by a known provider with a sign-off time is one
 * ReportReviewed; DateTimeReportReviewed holds a date only, so every sign-off row also travels
 * whole in Notes with its exact time.
 *
 * <p>Notes carry the rest as the report's source context: the report row CARLOS keeps (its
 * description, category, duplicate count, parent report, the recipient and the patient identity
 * the report named, and so on), its patient links, every comment that was not deleted, every
 * accompanying subclass with the one marked active, every sign-off, and the message values with
 * no standard element (the sender's review time and reviewer, every routing entry). A deleted
 * comment is not shown in the chart and is accounted, not exported; unsigned routes are inbox
 * workflow. The local storage location and the duplicate-detection digests are operational.
 *
 * <p>When the message file could not be read (none recorded, missing, an unsupported name) the
 * report is still exported, from the details CARLOS recorded: Format Text without Content, the
 * class, subclass, dates, facility report number and accompanying subclasses from the report
 * rows, and a Notes statement that its content could not be read. A stored file that is not an
 * HRM message the viewer could show is attached as stored ({@code xml}). A message holding more
 * than one report holds the patient: CARLOS shows only its first report.
 *
 * @since 2026-11-13
 */
final class CdsExportHospitalReportMapper {
    /** The CDS 5.2 {@code cdsd:reportClass} values. */
    private static final Set<String> CLASSES =
            Set.of(
                    "Diagnostic Imaging Report",
                    "Diagnostic Test Report",
                    "Cardio Respiratory Report",
                    "Medical Records Report",
                    "Consultant Report",
                    "Lab Report",
                    "Other Letter");

    /** Report columns that are local bookkeeping, not part of the report's source context. */
    private static final Map<String, String> OPERATIONAL_REPORT_COLUMNS =
            Map.of(
                    "reportHash", "HRM_DUPLICATE_DETECTION_DIGEST",
                    "reportLessTransactionInfoHash", "HRM_DUPLICATE_DETECTION_DIGEST",
                    "reportLessDemographicInfoHash", "HRM_DUPLICATE_DETECTION_DIGEST",
                    "reportFile", "LOCAL_HRM_MESSAGE_STORAGE_LOCATION_THE_MESSAGE_IS_EXPORTED");

    static final String NOTES_HEADER = "[CARLOS HRM report source context]";
    static final String CARRIED_RULE = "CARRIED_WHOLE_IN_HRM_REPORT_SOURCE_CONTEXT";

    /** The captured rows of one report. */
    record Source(
            int patient,
            CdsExportCapturedTextRowDto report,
            List<CdsExportCapturedTextRowDto> links,
            List<CdsExportCapturedTextRowDto> comments,
            List<CdsExportCapturedTextRowDto> subClasses,
            List<CdsExportCapturedTextRowDto> routes,
            CdsExportCapturedHospitalReportFiles.File file) {
        Source {
            Objects.requireNonNull(report);
            Objects.requireNonNull(file);
            links = sorted(links);
            comments = sorted(comments);
            subClasses = sorted(subClasses);
            routes = sorted(routes);
        }

        @Override
        public String toString() {
            return "Source[protected]";
        }
    }

    /** How the report's content is exported. */
    enum ContentKind {
        /** The message's TextContent. */
        TEXT,
        /** The message's decoded Media, attached at the content's FilePath. */
        BINARY,
        /** The stored file is not a readable HRM message; it is attached as stored. */
        MESSAGE_FILE,
        /** No content: the file was not read, is empty, or the message has none. */
        NONE
    }

    /** The content decision: its kind and, for an attachment, its path and declared extension. */
    record Content(ContentKind kind, String filePath, String extension) {
        static Content none() {
            return new Content(ContentKind.NONE, null, null);
        }

        static Content text() {
            return new Content(ContentKind.TEXT, null, null);
        }
    }

    /** One more ReportReviewed: who, and on which date. */
    record Reviewed(CdsExportPublicProviderDto provider, CdsExportDateDto date) {
        @Override
        public String toString() {
            return "Reviewed[protected]";
        }
    }

    /** One OBRContent element. */
    record Observation(String subClass, String mnemonic, String description, CdsExportDateDto observed) {
        @Override
        public String toString() {
            return "Observation[protected]";
        }
    }

    /** The mapped entry, its field decisions and every row they cover. */
    record Result(
            Map<String, String> text,
            Map<String, CdsExportDateDto> dates,
            String[] author,
            String[] recipient,
            List<Reviewed> reviewers,
            List<Observation> observations,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields,
            List<CdsExportCapturedTextRowDto> rows) {
        Result {
            text = Map.copyOf(text);
            dates = Map.copyOf(dates);
            reviewers = List.copyOf(reviewers);
            observations = List.copyOf(observations);
            fields = Map.copyOf(fields);
            rows = List.copyOf(rows);
        }

        // Records compare arrays by identity; a result is equal by the names it carries.
        @Override
        public boolean equals(Object other) {
            return other instanceof Result that
                    && text.equals(that.text)
                    && dates.equals(that.dates)
                    && java.util.Arrays.equals(author, that.author)
                    && java.util.Arrays.equals(recipient, that.recipient)
                    && reviewers.equals(that.reviewers)
                    && observations.equals(that.observations)
                    && fields.equals(that.fields)
                    && rows.equals(that.rows);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(
                    text,
                    dates,
                    java.util.Arrays.hashCode(author),
                    java.util.Arrays.hashCode(recipient),
                    reviewers,
                    observations,
                    fields,
                    rows);
        }

        @Override
        public String toString() {
            return "CdsExportHospitalReportMapper.Result[protected]";
        }
    }

    /**
     * @param source the report's captured rows
     * @param parsed the parsed message, or null when it was not read or could not be read
     * @param content how the content is exported (decided with the message)
     * @param providers captured public provider rows by provider number
     * @param zone the clinic zone
     * @throws CdsExportMappingReviewException when the report cannot be represented; only this
     *     patient is held
     */
    Result map(
            Source source,
            CdsExportHospitalReportMessage.Parsed parsed,
            Content content,
            Map<String, CdsExportCapturedTextRowDto> providers,
            ZoneId zone)
            throws IOException {
        Objects.requireNonNull(content);
        var report = source.report().fields();
        String id = report.get("id");
        if (source.patient() < 1
                || !source.report().table().equals(CdsExportHospitalReportSources.REPORTS)
                || !source.file().row().fields().get("hrm_document_id").equals(id)
                || parsed == null
                        && (content.kind() == ContentKind.TEXT
                                || content.kind() == ContentKind.BINARY)
                || content.kind() == ContentKind.MESSAGE_FILE && parsed != null
                || (content.kind() == ContentKind.BINARY || content.kind() == ContentKind.MESSAGE_FILE)
                        != (content.filePath() != null)) invalid();
        CdsExportHospitalReportMessage.Report message = null;
        if (parsed != null) {
            if (parsed.reports().size() > 1)
                review("CDS_EXPORT_HRM_REPORT_MULTIPLE_REPORTS_REQUIRES_REVIEW");
            if (!parsed.reports().isEmpty()) message = parsed.reports().getFirst();
        }
        var text = new LinkedHashMap<String, String>();
        var dates = new LinkedHashMap<String, CdsExportDateDto>();
        var fields = new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        var rows = new ArrayList<CdsExportCapturedTextRowDto>();
        var details = new LinkedHashMap<String, Object>();
        var unwritten = new LinkedHashMap<String, Object>();
        var preface = new ArrayList<String>();
        details.put("version", 1);

        // Content.
        switch (content.kind()) {
            case TEXT -> {
                if (message == null || message.text() == null) invalid();
                text.put("Format", "Text");
                if (!CdsExportNativeFieldAssessments.legal(message.text(), Integer.MAX_VALUE))
                    review("CDS_EXPORT_HRM_REPORT_TEXT_REQUIRES_REVIEW");
                text.put("TextContent", message.text());
            }
            case BINARY -> {
                if (message == null || message.binary() == null) invalid();
                text.put("Format", "Binary");
                text.put("FilePath", content.filePath());
            }
            case MESSAGE_FILE -> {
                text.put("Format", "Binary");
                text.put("FileExtensionAndVersion", "xml");
                text.put("FilePath", content.filePath());
                preface.add(
                        "The stored HRM message of this report could not be read as an HRM"
                                + " report, so it is attached exactly as stored.");
            }
            case NONE -> {
                text.put("Format", "Text");
                if (parsed == null
                        && source.file().state()
                                == CdsExportHospitalReportFileSourceReader.State.READ)
                    preface.add(
                            "The stored HRM message file of this report is empty; its details"
                                    + " are as CARLOS recorded them.");
                else if (parsed == null)
                    preface.add(
                            "The stored HRM message file of this report could not be read ("
                                    + source.file().state().name()
                                    + "); its details are as CARLOS recorded them.");
                else if (message == null)
                    preface.add("The stored HRM message holds no report.");
                else preface.add("The HRM report has no content.");
            }
            default -> invalid();
        }
        if (message != null) {
            put(text, unwritten, "Media", message.media(), 20);
            if (message.format() != null && !message.format().equals(text.get("Format")))
                unwritten.put("Format", message.format());
            if (content.kind() == ContentKind.TEXT || content.kind() == ContentKind.BINARY)
                put(text, unwritten, "FileExtensionAndVersion", message.extension(), 50);
            else if (message.extension() != null)
                unwritten.put("FileExtensionAndVersion", message.extension());
            if (content.kind() == ContentKind.BINARY
                    && !text.containsKey("FileExtensionAndVersion"))
                text.put("FileExtensionAndVersion", content.extension());
        }

        // Class and SubClass.
        String reportClass = message == null ? null : message.reportClass();
        if (reportClass == null || !CLASSES.contains(reportClass)) {
            if (reportClass != null) unwritten.put("Class", reportClass);
            reportClass = recordedClass(report);
        }
        text.put("Class", reportClass);
        String activeDescription = activeSubClass(source.subClasses());
        String subClass =
                first(
                        60,
                        message == null ? null : message.subClass(),
                        message == null ? report.get("subClassName") : null,
                        activeDescription,
                        message == null || message.observations().isEmpty()
                                ? null
                                : message.observations().getFirst().description());
        if (subClass != null) text.put("SubClass", subClass);
        if (message != null
                && message.subClass() != null
                && !message.subClass().equals(subClass)) unwritten.put("SubClass", message.subClass());

        // Dates: the message's own, else the times CARLOS recorded.
        dates(message == null ? null : message.event(), report.get("reportDate"), "EventDateTime", zone, dates, unwritten);
        dates(message == null ? null : message.received(), report.get("timeReceived"), "ReceivedDateTime", zone, dates, unwritten);
        if (message != null && message.reviewed() != null)
            unwritten.put("ReviewedDateTime", message.reviewed().lexical());
        if (message != null && message.reviewingOhip() != null)
            unwritten.put("ReviewingOHIPPhysicianId", message.reviewingOhip());

        // Authors, facilities, status and routing.
        String[] author = null;
        if (message != null && message.author() != null) {
            author = author(message.author());
            if (author == null) unwritten.put("AuthorPhysician", name(message.author()));
        }
        put(text, unwritten, "SourceFacility", report.get("sourceFacility"), 120);
        unwritten.remove("SourceFacility");
        if (message != null) put(text, unwritten, "SendingFacilityId", message.sendingFacility(), 4);
        String facilityReport = message == null ? null : message.sendingFacilityReport();
        if (facilityReport != null) put(text, unwritten, "SendingFacilityReport", facilityReport, 75);
        else {
            String recorded = report.get("sourceFacilityReportNo");
            if (recorded != null
                    && !recorded.isEmpty()
                    && CdsExportNativeFieldAssessments.legal(recorded, 75))
                text.put("SendingFacilityReport", recorded);
        }
        var observations = new ArrayList<Observation>();
        if (message != null) {
            var extra = new ArrayList<Map<String, Object>>();
            for (var obr : message.observations()) {
                var observed = CdsExportHospitalReportMessage.date(obr.observed(), zone);
                boolean fits =
                        fits(obr.subClass(), 60)
                                && fits(obr.mnemonic(), 200)
                                && fits(obr.description(), 200)
                                && (obr.observed() == null || observed != null);
                if (fits)
                    observations.add(
                            new Observation(
                                    obr.subClass(), obr.mnemonic(), obr.description(), observed));
                else {
                    var entry = new LinkedHashMap<String, Object>();
                    entry.put("AccompanyingSubClass", obr.subClass());
                    entry.put("AccompanyingMnemonic", obr.mnemonic());
                    entry.put("AccompanyingDescription", obr.description());
                    entry.put(
                            "ObservationDateTime",
                            obr.observed() == null ? null : obr.observed().lexical());
                    extra.add(entry);
                }
            }
            if (!extra.isEmpty()) unwritten.put("OBRContent", extra);
            put(text, unwritten, "HRMResultStatus", message.resultStatus(), 1);
        } else {
            // The accompanying subclasses CARLOS copied from the OBR content at receipt.
            for (var row : source.subClasses()) {
                var values = row.fields();
                CdsExportDateDto observed = null;
                String day = values.get("subClassDateTime");
                if (day != null && day.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))
                    observed = new CdsExportDateDto(CdsExportDateDto.Precision.DATE, day);
                if (fits(values.get("subClass"), 60)
                        && fits(values.get("subClassMnemonic"), 200)
                        && fits(values.get("subClassDescription"), 200))
                    observations.add(
                            new Observation(
                                    empty(values.get("subClass")),
                                    empty(values.get("subClassMnemonic")),
                                    empty(values.get("subClassDescription")),
                                    observed));
            }
            String status = report.get("reportStatus");
            if (status != null && status.length() == 1 && CdsExportNativeFieldAssessments.legal(status, 1))
                text.put("HRMResultStatus", status);
        }
        String[] recipient = null;
        if (parsed != null) {
            if (!parsed.transactions().isEmpty()) {
                var routing = parsed.transactions().getFirst();
                put(text, unwritten, "MessageUniqueID", routing.messageUniqueId(), 250);
                unwritten.remove("MessageUniqueID");
                if (routing.provider() != null) recipient = author(routing.provider());
            }
            var transactions = new ArrayList<Map<String, Object>>();
            for (var routing : parsed.transactions()) {
                var entry = new LinkedHashMap<String, Object>();
                entry.put("MessageUniqueID", routing.messageUniqueId());
                entry.put("DeliverToUserID", routing.deliverToUserId());
                entry.put("Provider", routing.provider() == null ? null : name(routing.provider()));
                transactions.add(entry);
            }
            if (!transactions.isEmpty()) unwritten.put("TransactionInformation", transactions);
        }

        // The report row: its source context, except the local bookkeeping.
        var reportState = new CdsExportNativeFieldAssessments(source.report(), fields);
        var reportContext = new TreeMap<String, String>();
        for (var entry : report.entrySet()) {
            String rule = OPERATIONAL_REPORT_COLUMNS.get(entry.getKey());
            if (rule != null) reportState.operational(entry.getKey(), rule);
            else {
                reportContext.put(entry.getKey(), entry.getValue());
                reportState.set(
                        entry.getKey(),
                        Outcome.MAPPED,
                        "Reports/Notes",
                        "EXACT_NATIVE_HRM_REPORT_SOURCE_CONTEXT");
            }
        }
        rows.add(source.report());
        details.put("hrmReport", reportContext);

        // Patient links: whole, except the derived numeric key.
        var links = new ArrayList<Map<String, String>>();
        for (var link : source.links()) {
            var state = new CdsExportNativeFieldAssessments(link, fields);
            var carried = new TreeMap<String, String>();
            for (var entry : link.fields().entrySet()) {
                if (entry.getKey().equals("cds_patient_key")) {
                    state.operational(entry.getKey(), "DERIVED_NUMERIC_PATIENT_KEY");
                    continue;
                }
                carried.put(entry.getKey(), entry.getValue());
                state.set(entry.getKey(), Outcome.MAPPED, "Reports/Notes", CARRIED_RULE);
            }
            links.add(carried);
            rows.add(link);
        }
        details.put("links", links);

        // Message file state.
        var file = source.file();
        var fileRow = file.row();
        var fileState = new CdsExportNativeFieldAssessments(fileRow, fields);
        fileState.operational("hrm_document_id", "LOCAL_HRM_REPORT_IDENTITY");
        fileState.operational("report_file", "LOCAL_HRM_MESSAGE_STORAGE_LOCATION_THE_MESSAGE_IS_EXPORTED");
        fileState.set("state", Outcome.MAPPED, "Reports/Notes", "HRM_MESSAGE_FILE_STATE_IN_SOURCE_CONTEXT");
        details.put("messageFile", file.state().name());
        String contentValue = fileRow.fields().get("content");
        if (contentValue == null)
            fileState.set(
                    "content",
                    Outcome.ABSENT,
                    "",
                    "HRM_MESSAGE_FILE_NOT_READ_REPORT_EXPORTED_FROM_RECORDED_DETAILS");
        else if (contentValue.isEmpty())
            fileState.set("content", Outcome.ABSENT, "", "EMPTY_HRM_MESSAGE_FILE");
        else
            fileState.set(
                    "content",
                    Outcome.MAPPED,
                    switch (content.kind()) {
                        case TEXT -> "Reports/Content";
                        case BINARY, MESSAGE_FILE -> "Reports/FilePath";
                        case NONE -> "Reports";
                    },
                    content.kind() == ContentKind.MESSAGE_FILE
                            ? "UNREADABLE_HRM_MESSAGE_ATTACHED_AS_STORED"
                            : "HRM_MESSAGE_REPORT_AND_ROUTING_ELEMENTS_SENDER_PATIENT_BLOCK_IS_THE_CHART_IDENTITY");
        rows.add(fileRow);

        // Accompanying subclasses, whole.
        details.put("subClasses", carry(source.subClasses(), fields, rows));

        // Comments: a deleted one is not in the chart; any other travels whole.
        var used = new TreeMap<String, CdsExportCapturedTextRowDto>();
        var named = new TreeMap<String, CdsExportPublicProviderDto>();
        var live = new ArrayList<CdsExportCapturedTextRowDto>();
        for (var comment : source.comments()) {
            if ("1".equals(comment.fields().get("deleted"))) {
                var state = new CdsExportNativeFieldAssessments(comment, fields);
                for (String column : comment.fields().keySet())
                    state.operational(column, "DELETED_HRM_COMMENT_NOT_SHOWN_IN_CHART");
                rows.add(comment);
            } else {
                live.add(comment);
                name(comment.fields().get("providerNo"), providers, used, named);
            }
        }
        details.put("comments", carry(live, fields, rows));

        // Provider routes: a dated sign-off by a known provider is a ReportReviewed.
        var reviewers = new ArrayList<Reviewed>();
        var signOffs = new ArrayList<CdsExportCapturedTextRowDto>();
        var reviewing = new HashSet<String>();
        for (var route : source.routes()) {
            var values = route.fields();
            String signed = values.get("signedOff"), provider = values.get("providerNo");
            if (signed == null || signed.equals("0")) {
                var state = new CdsExportNativeFieldAssessments(route, fields);
                for (String column : values.keySet())
                    state.operational(column, "UNSIGNED_HRM_INBOX_ROUTING_WORKFLOW");
                rows.add(route);
                continue;
            }
            if (!signed.equals("1")) review("CDS_EXPORT_HRM_SIGN_OFF_STATUS_REQUIRES_REVIEW");
            signOffs.add(route);
            var value = name(provider, providers, used, named);
            String time = values.get("signedOffTimestamp");
            if (value == null || time == null || time.isEmpty()) continue;
            var clock = CdsExportNativeHistoryMapper.clock(time, zone);
            reviewers.add(
                    new Reviewed(
                            value,
                            new CdsExportDateDto(
                                    CdsExportDateDto.Precision.DATE,
                                    clock.lexical().substring(0, 10))));
            reviewing.add(provider);
        }
        details.put("signOffs", carry(signOffs, fields, rows));
        for (var route : signOffs) {
            String provider = route.fields().get("providerNo");
            String time = route.fields().get("signedOffTimestamp");
            if (!reviewing.contains(provider) || time == null || time.isEmpty()) continue;
            var state = new CdsExportNativeFieldAssessments(route, fields);
            for (String column : route.fields().keySet())
                state.set(column, Outcome.MAPPED, "Reports/Notes", CARRIED_RULE);
            state.set("providerNo", Outcome.MAPPED, "Reports/ReportReviewed/Name", "HRM_SIGNING_PROVIDER");
            state.set("signedOff", Outcome.MAPPED, "Reports/ReportReviewed", "HRM_SIGNED_OFF");
            state.set(
                    "signedOffTimestamp",
                    Outcome.MAPPED,
                    "Reports/ReportReviewed/DateTimeReportReviewed",
                    "SIGN_OFF_DATE_ON_THE_WIRE_EXACT_TIME_IN_SOURCE_CONTEXT");
        }
        // One element per provider and date.
        var distinct = new LinkedHashMap<String, Reviewed>();
        reviewers.sort(
                Comparator.comparing((Reviewed reviewed) -> reviewed.date().lexical())
                        .thenComparing(reviewed -> reviewed.provider().lastName())
                        .thenComparing(reviewed -> reviewed.provider().firstName()));
        for (var reviewed : reviewers)
            distinct.putIfAbsent(
                    reviewed.provider().firstName()
                            + "\u0000"
                            + reviewed.provider().lastName()
                            + "\u0000"
                            + reviewed.provider().ohipNumber()
                            + "\u0000"
                            + reviewed.date().lexical(),
                    reviewed);

        // Public provider rows: the names of reviewers, signers and comment authors.
        for (var entry : used.entrySet()) {
            var provider = entry.getValue();
            var mapped =
                    new CdsExportPublicProviderMapper()
                            .map(provider, CdsExportPublicProviderMapper.Target.REPORT_REVIEWER);
            boolean reviewer = reviewing.contains(entry.getKey());
            mapped.fields()
                    .forEach(
                            (key, assessment) ->
                                    fields.put(
                                            key,
                                            reviewer || assessment.outcome() != Outcome.MAPPED
                                                    ? assessment
                                                    : new CdsExportFieldAssessmentDto(
                                                            assessment.presence(),
                                                            assessment.outcome(),
                                                            "Reports/Notes",
                                                            "PUBLIC_PROVIDER_NAME_IN_SOURCE_CONTEXT")));
            rows.add(provider);
        }
        if (!named.isEmpty()) {
            var names = new LinkedHashMap<String, Object>();
            named.forEach(
                    (number, value) -> {
                        var entry = new LinkedHashMap<String, String>();
                        entry.put("firstName", value.firstName());
                        entry.put("lastName", value.lastName());
                        if (value.ohipNumber() != null) entry.put("ohip", value.ohipNumber());
                        names.put(number, entry);
                    });
            details.put("providers", names);
        }
        if (!unwritten.isEmpty()) details.put("message", unwritten);

        var notes = new StringBuilder();
        for (String line : preface) notes.append(line).append('\n');
        if (!preface.isEmpty()) notes.append('\n');
        notes.append(NOTES_HEADER).append('\n');
        notes.append(JsonMapper.builder().build().writeValueAsString(details));
        if (!CdsExportNativeFieldAssessments.legal(notes.toString(), 32000))
            review("CDS_EXPORT_HRM_REPORT_METADATA_CAPACITY");
        text.put("Notes", notes.toString());
        return new Result(
                text,
                dates,
                author,
                recipient,
                List.copyOf(distinct.values()),
                observations,
                fields,
                rows);
    }

    /** Reports content in CDS 5.2 schema order. */
    void write(Result result, CdsExportXmlComposer.ContentWriter xml) throws IOException {
        for (String name : List.of("Media", "Format", "FileExtensionAndVersion", "FilePath"))
            text(result, xml, name);
        if (result.text().containsKey("TextContent")) {
            xml.start("cds", "Content");
            xml.element("cds_dt", "TextContent", result.text().get("TextContent"));
            xml.end();
        }
        for (String name : List.of("Class", "SubClass")) text(result, xml, name);
        date(xml, "EventDateTime", result.dates().get("EventDateTime"));
        date(xml, "ReceivedDateTime", result.dates().get("ReceivedDateTime"));
        if (result.author() != null) {
            xml.start("cds", "SourceAuthorPhysician");
            if (result.author().length == 2) {
                xml.start("cds", "AuthorName");
                xml.element("cds_dt", "FirstName", result.author()[0]);
                xml.element("cds_dt", "LastName", result.author()[1]);
                xml.end();
            } else xml.element("cds", "AuthorFreeText", result.author()[0]);
            xml.end();
        }
        text(result, xml, "SourceFacility");
        for (var review : result.reviewers()) {
            xml.start("cds", "ReportReviewed");
            xml.start("cds", "Name");
            xml.element("cds_dt", "FirstName", review.provider().firstName());
            xml.element("cds_dt", "LastName", review.provider().lastName());
            xml.end();
            if (review.provider().ohipNumber() != null)
                xml.element("cds", "ReviewingOHIPPhysicianId", review.provider().ohipNumber());
            date(xml, "DateTimeReportReviewed", review.date());
            xml.end();
        }
        for (String name : List.of("SendingFacilityId", "SendingFacilityReport"))
            text(result, xml, name);
        for (var observation : result.observations()) {
            xml.start("cds", "OBRContent");
            if (observation.subClass() != null)
                xml.element("cds", "AccompanyingSubClass", observation.subClass());
            if (observation.mnemonic() != null)
                xml.element("cds", "AccompanyingMnemonic", observation.mnemonic());
            if (observation.description() != null)
                xml.element("cds", "AccompanyingDescription", observation.description());
            date(xml, "ObservationDateTime", observation.observed());
            xml.end();
        }
        for (String name : List.of("HRMResultStatus", "MessageUniqueID", "Notes"))
            text(result, xml, name);
        if (result.recipient() != null && result.recipient().length == 2) {
            xml.start("cds", "RecipientName");
            xml.element("cds_dt", "FirstName", result.recipient()[0]);
            xml.element("cds_dt", "LastName", result.recipient()[1]);
            xml.end();
        }
    }

    /** Each row whole in the source context, every field accounted there. */
    private static List<Map<String, String>> carry(
            List<CdsExportCapturedTextRowDto> carried,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields,
            List<CdsExportCapturedTextRowDto> rows) {
        var result = new ArrayList<Map<String, String>>();
        for (var row : carried) {
            var state = new CdsExportNativeFieldAssessments(row, fields);
            for (String column : row.fields().keySet())
                state.set(column, Outcome.MAPPED, "Reports/Notes", CARRIED_RULE);
            result.add(new TreeMap<>(row.fields()));
            rows.add(row);
        }
        return result;
    }

    /**
     * The public name of a provider, when the lookup holds a provider row it can represent;
     * otherwise null and the provider number alone stays in the carried row.
     */
    private static CdsExportPublicProviderDto name(
            String number,
            Map<String, CdsExportCapturedTextRowDto> providers,
            Map<String, CdsExportCapturedTextRowDto> used,
            Map<String, CdsExportPublicProviderDto> named)
            throws IOException {
        if (number == null || number.isBlank() || number.equals("-1") || number.equals("0"))
            return null;
        if (named.containsKey(number)) return named.get(number);
        var provider = providers.get(number);
        if (provider == null) return null;
        var mapped =
                new CdsExportPublicProviderMapper()
                        .map(provider, CdsExportPublicProviderMapper.Target.REPORT_REVIEWER);
        if (!mapped.representable()) return null;
        used.put(number, provider);
        named.put(number, mapped.value());
        return mapped.value();
    }

    /** The class CARLOS recorded when the message gave none: className, then reportType. */
    private static String recordedClass(Map<String, String> report) {
        for (String column : List.of("className", "reportType")) {
            String value = report.get(column);
            if (value != null && CLASSES.contains(value)) return value;
        }
        // HRM reports that state no class are hospital medical records.
        return "Medical Records Report";
    }

    private static String activeSubClass(List<CdsExportCapturedTextRowDto> subClasses) {
        for (var row : subClasses)
            if ("1".equals(row.fields().get("isActive")))
                return empty(row.fields().get("subClassDescription"));
        return null;
    }

    private static void dates(
            CdsExportHospitalReportMessage.When stated,
            String recorded,
            String element,
            ZoneId zone,
            Map<String, CdsExportDateDto> dates,
            Map<String, Object> unwritten)
            throws IOException {
        if (stated != null) {
            var date = CdsExportHospitalReportMessage.date(stated, zone);
            if (date != null) {
                dates.put(element, date);
                return;
            }
            unwritten.put(element, stated.lexical());
        }
        if (recorded != null && !recorded.isEmpty())
            dates.put(element, CdsExportNativeHistoryMapper.clock(recorded, zone));
    }

    /** First and last name, or one free-text name, as SourceAuthorPhysician carries them. */
    private static String[] author(CdsExportHospitalReportMessage.Name name) {
        String first = empty(name.first()), last = empty(name.last());
        if (first != null
                && last != null
                && CdsExportNativeFieldAssessments.legal(first, 50)
                && CdsExportNativeFieldAssessments.legal(last, 50)) return new String[] {first, last};
        String whole = ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim();
        if (whole.isEmpty() || !CdsExportNativeFieldAssessments.legal(whole, 120)) return null;
        return new String[] {whole};
    }

    private static Map<String, String> name(CdsExportHospitalReportMessage.Name name) {
        var result = new LinkedHashMap<String, String>();
        result.put("FirstName", name.first());
        result.put("LastName", name.last());
        return result;
    }

    private static void put(
            Map<String, String> text,
            Map<String, Object> unwritten,
            String element,
            String value,
            int maximum) {
        if (value == null || value.isEmpty()) return;
        if (CdsExportNativeFieldAssessments.legal(value, maximum)) text.put(element, value);
        else unwritten.put(element, value);
    }

    private static String first(int maximum, String... values) {
        for (String value : values)
            if (value != null
                    && !value.isEmpty()
                    && CdsExportNativeFieldAssessments.legal(value, maximum)) return value;
        return null;
    }

    private static boolean fits(String value, int maximum) {
        return value == null || value.isEmpty() || CdsExportNativeFieldAssessments.legal(value, maximum);
    }

    private static String empty(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private static void text(Result result, CdsExportXmlComposer.ContentWriter xml, String name)
            throws IOException {
        if (result.text().containsKey(name)) xml.element("cds", name, result.text().get(name));
    }

    private static void date(CdsExportXmlComposer.ContentWriter xml, String name, CdsExportDateDto value)
            throws IOException {
        if (value == null) return;
        xml.start("cds", name);
        CdsExportNativeHistoryXmlWriter.dateContents(xml, value);
        xml.end();
    }

    private static List<CdsExportCapturedTextRowDto> sorted(List<CdsExportCapturedTextRowDto> rows) {
        var result = new ArrayList<>(rows);
        result.sort(Comparator.comparing(CdsExportCapturedTextRowDto::recordKey));
        return List.copyOf(result);
    }

    private static void review(String code) throws CdsExportMappingReviewException {
        throw new CdsExportMappingReviewException(code);
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_HRM_REPORT_SCOPE_INVALID");
    }
}
