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
import java.time.*;
import java.util.*;

/** A native chart document retains its own dates, source author, reviewer and complete metadata. */
public final class CdsExportNativeReportMapper {
    private static final Set<String> CLASSES =
            Set.of(
                    "Diagnostic Imaging Report",
                    "Diagnostic Test Report",
                    "Cardio Respiratory Report",
                    "Medical Records Report",
                    "Consultant Report",
                    "Lab Report",
                    "Other Letter");

    public record Result(
            Map<String, String> text,
            Map<String, CdsExportDateDto> dates,
            CdsExportPublicProviderDto reviewer,
            CdsExportDateDto reviewDate,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields,
            List<CdsExportCapturedTextRowDto> rows) {
        public Result {
            text = Map.copyOf(text);
            dates = Map.copyOf(dates);
            fields = Map.copyOf(fields);
            rows = List.copyOf(rows);
        }

        @Override
        public String toString() {
            return "CdsExportNativeReportMapper.Result[protected]";
        }
    }

    public Result map(
            int patient,
            CdsExportCapturedTextRowDto document,
            CdsExportCapturedTextRowDto link,
            String relative,
            String extension,
            Map<String, CdsExportCapturedTextRowDto> providers,
            ZoneId zone)
            throws IOException {
        if (patient < 1
                || !document.table().equals("document")
                || !link.table().equals("ctl_document")
                || !Integer.toString(patient).equals(link.fields().get("module_id"))
                || !"demographic".equals(link.fields().get("module"))
                || !Objects.equals(
                        document.fields().get("document_no"), link.fields().get("document_no"))
                || relative == null
                || !relative.matches("Reports/[0-9a-f]{64}\\.[a-z0-9]{1,12}")
                || extension == null
                || !extension.matches("[a-z0-9]{1,12}")) invalid();
        var source = document.fields();
        if (!"A".equals(source.get("status")) || !"A".equals(link.fields().get("status")))
            review("CDS_EXPORT_NATIVE_REPORT_STATUS_REQUIRES_REVIEW");
        String restricted = source.get("restrictToProgram");
        if (restricted != null && !restricted.isEmpty() && !restricted.equals("0"))
            review("CDS_EXPORT_NATIVE_REPORT_PROGRAM_REQUIRES_REVIEW");
        if (source.get("cds_content_precision") != null
                && !source.get("cds_content_precision").isEmpty())
            review("CDS_EXPORT_NATIVE_REPORT_IMPORTED_PRECISION_REQUIRES_REVIEW");
        var text = new LinkedHashMap<String, String>();
        var dates = new LinkedHashMap<String, CdsExportDateDto>();
        var fields = new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        var rows = new ArrayList<CdsExportCapturedTextRowDto>(List.of(document, link));
        for (var row : rows) {
            var state = new CdsExportNativeFieldAssessments(row, fields);
            for (var entry : row.fields().entrySet()) {
                if (entry.getValue() != null
                        && !CdsExportNativeFieldAssessments.legal(entry.getValue(), 32768))
                    review("CDS_EXPORT_NATIVE_REPORT_METADATA_CAPACITY");
                state.set(
                        entry.getKey(),
                        Outcome.MAPPED,
                        "Reports/Notes",
                        "EXACT_NATIVE_DOCUMENT_SOURCE_CONTEXT");
            }
        }
        text.put("Media", "Download");
        text.put("Format", "Binary");
        text.put("FileExtensionAndVersion", extension);
        text.put("FilePath", relative);
        String classification = source.get("docClass");
        if (classification == null || classification.isEmpty() || !CLASSES.contains(classification))
            classification = classify(source.get("doctype"));
        text.put("Class", classification);
        String subclass = source.get("docSubClass");
        if (subclass == null || subclass.isEmpty()) subclass = source.get("doctype");
        if (subclass != null
                && !subclass.isEmpty()
                && CdsExportNativeFieldAssessments.legal(subclass, 60))
            text.put("SubClass", subclass);
        var details = new LinkedHashMap<String, Object>();
        details.put("version", 1);
        details.put("document", source);
        details.put("link", link.fields());
        for (var entry :
                Map.of("receivedDate", "ReceivedDateTime", "sent_date_time", "SentDateTime")
                        .entrySet()) {
            String value = source.get(entry.getKey());
            if (value != null && !value.isEmpty())
                dates.put(entry.getValue(), CdsExportNativeHistoryMapper.clock(value, zone));
        }
        // The report's event is the date the clinician recorded (observationdate), as the legacy
        // exporter wrote it. contentdatetime is the instant the file entered CARLOS
        // (EDoc.preliminaryProcessing), which for a scanned or faxed letter is days or years
        // later: it is the receipt time, never the clinical event, and a difference between the
        // two is normal rather than a conflict. Both stay in the document source context.
        String observed = source.get("observationdate");
        if (observed != null && !observed.isEmpty())
            dates.put("EventDateTime", CdsExportNativeHistoryMapper.clock(observed, zone));
        String content = source.get("contentdatetime");
        if (content != null && !content.isEmpty() && !dates.containsKey("ReceivedDateTime"))
            dates.put("ReceivedDateTime", CdsExportNativeHistoryMapper.clock(content, zone));
        for (var entry :
                Map.of("source", "SourceAuthorPhysician", "sourceFacility", "SourceFacility")
                        .entrySet()) {
            String value = source.get(entry.getKey());
            if (value != null && !value.isEmpty()) {
                if (!CdsExportNativeFieldAssessments.legal(value, 120))
                    review("CDS_EXPORT_NATIVE_REPORT_SOURCE_CAPACITY");
                text.put(entry.getValue(), value);
            }
        }
        CdsExportPublicProviderDto reviewer = null;
        CdsExportDateDto reviewDate = null;
        String reviewerId = source.get("reviewer"), reviewed = source.get("reviewdatetime");
        if (reviewerId != null && !reviewerId.isEmpty() && !reviewerId.equals("0")) {
            var provider = providers.get(reviewerId);
            if (provider == null) review("CDS_EXPORT_NATIVE_REPORT_REVIEWER_REQUIRED");
            var mapped =
                    new CdsExportPublicProviderMapper()
                            .map(provider, CdsExportPublicProviderMapper.Target.REPORT_REVIEWER);
            if (!mapped.representable())
                review("CDS_EXPORT_NATIVE_REPORT_REVIEWER_REQUIRES_REVIEW");
            fields.putAll(mapped.fields());
            rows.add(provider);
            reviewer = mapped.value();
            var publicReviewer = new LinkedHashMap<String, String>();
            publicReviewer.put("firstName", reviewer.firstName());
            publicReviewer.put("lastName", reviewer.lastName());
            if (reviewer.ohipNumber() != null) publicReviewer.put("ohip", reviewer.ohipNumber());
            details.put("publicReviewer", publicReviewer);
            if (reviewed != null && !reviewed.isEmpty()) {
                var clock = CdsExportNativeHistoryMapper.clock(reviewed, zone);
                reviewDate =
                        new CdsExportDateDto(
                                CdsExportDateDto.Precision.DATE, clock.lexical().substring(0, 10));
            } else {
                // The standard reviewer requires a date; retain undated public reviewer details
                // in Notes instead of inventing a review date or claiming a missing wire field.
                mapped.fields()
                        .forEach(
                                (key, assessment) ->
                                        fields.put(
                                                key,
                                                new CdsExportFieldAssessmentDto(
                                                        assessment.presence(),
                                                        assessment.outcome(),
                                                        "Reports/Notes",
                                                        "UNDATED_PUBLIC_REVIEWER_IN_SOURCE_CONTEXT")));
            }
        }
        String notes =
                "[CARLOS native report source context]\n"
                        + JsonMapper.builder().build().writeValueAsString(details);
        if (!CdsExportNativeFieldAssessments.legal(notes, 32000))
            review("CDS_EXPORT_NATIVE_REPORT_METADATA_CAPACITY");
        text.put("Notes", notes);
        return new Result(text, dates, reviewer, reviewDate, fields, rows);
    }

    public void write(Result result, CdsExportXmlComposer.ContentWriter xml) throws IOException {
        for (String name :
                List.of(
                        "Media",
                        "Format",
                        "FileExtensionAndVersion",
                        "FilePath",
                        "Class",
                        "SubClass")) text(result, xml, name);
        date(result, xml, "EventDateTime");
        date(result, xml, "ReceivedDateTime");
        if (result.text().containsKey("SourceAuthorPhysician")) {
            xml.start("cds", "SourceAuthorPhysician");
            xml.element("cds", "AuthorFreeText", result.text().get("SourceAuthorPhysician"));
            xml.end();
        }
        text(result, xml, "SourceFacility");
        if (result.reviewer() != null && result.reviewDate() != null) {
            xml.start("cds", "ReportReviewed");
            xml.start("cds", "Name");
            xml.element("cds_dt", "FirstName", result.reviewer().firstName());
            xml.element("cds_dt", "LastName", result.reviewer().lastName());
            xml.end();
            if (result.reviewer().ohipNumber() != null)
                xml.element("cds", "ReviewingOHIPPhysicianId", result.reviewer().ohipNumber());
            xml.start("cds", "DateTimeReportReviewed");
            CdsExportNativeHistoryXmlWriter.dateContents(xml, result.reviewDate());
            xml.end();
            xml.end();
        }
        text(result, xml, "Notes");
        date(result, xml, "SentDateTime");
    }

    private static void text(Result result, CdsExportXmlComposer.ContentWriter xml, String name)
            throws IOException {
        if (result.text().containsKey(name)) xml.element("cds", name, result.text().get(name));
    }

    private static void date(Result result, CdsExportXmlComposer.ContentWriter xml, String name)
            throws IOException {
        if (result.dates().containsKey(name)) {
            xml.start("cds", name);
            CdsExportNativeHistoryXmlWriter.dateContents(xml, result.dates().get(name));
            xml.end();
        }
    }

    private static String classify(String type) {
        if (type == null) return "Medical Records Report";
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "consult", "consultation", "consultant" -> "Consultant Report";
            case "lab", "laboratory" -> "Lab Report";
            case "imaging", "radiology" -> "Diagnostic Imaging Report";
            case "diagnostic", "diagnostic test" -> "Diagnostic Test Report";
            case "cardiology", "cardio respiratory" -> "Cardio Respiratory Report";
            case "letter" -> "Other Letter";
            default -> "Medical Records Report";
        };
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_NATIVE_REPORT_SCOPE_INVALID");
    }

    private static void review(String code) throws CdsExportMappingReviewException {
        throw new CdsExportMappingReviewException(code);
    }
}
