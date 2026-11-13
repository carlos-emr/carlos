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
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDateDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportMappingReviewException;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.EdObservationValue;
import java.io.IOException;
import java.time.ZoneId;
import java.util.*;

/**
 * Maps one embedded document of a native HL7 laboratory report (an OBX whose value type is
 * {@code ED}, such as the PDF pathology reports LifeLabs sends through Excelleris) to a CDS 5.2
 * Reports entry of class Lab Report.
 *
 * <p>A document is not a 120-character result value, so it never becomes LaboratoryResults: the
 * schema carries documents as Reports. The report is linked to its laboratory results by the
 * accession number CARLOS stores for the report, written in Notes with the observation's own
 * identification. The payload is read the way CARLOS shows it ({@link EdObservationValue}): ED.5
 * when the laboratory filled it, otherwise the legacy OBX-5.1 payload; ED.4 decides the encoding
 * (A text, Base64, Hex), and an undeclared payload is a document when it decodes as strict base64
 * to bytes with a known file signature or of document length, and text otherwise. Raw RTF is a
 * binary .rtf report. A declared encoding the payload does not satisfy is exported as text, so
 * nothing is dropped. Bytes are declared by their own signature ({@link
 * CdsExportReportFileFormats}). The complete message, including the exact payload, also travels in
 * the report's NewCategory supporting record.
 *
 * @since 2026-11-13
 */
final class CdsExportLaboratoryDocumentMapper {
    /** Shortest undeclared base64 payload without a known signature still taken as a document. */
    static final int MINIMUM_UNRECOGNISED_BINARY = 64;

    /** The decoded payload: bytes for a Binary report, or the text of a Text report. */
    record Payload(byte[] bytes, String text, String encoding, String dataType, String subtype) {
        @Override
        public String toString() {
            return "Payload[protected]";
        }
    }

    record Result(
            Map<String, String> text,
            Map<String, CdsExportDateDto> dates,
            List<CdsExportNativeReportMapper.Reviewed> reviewers) {
        Result {
            text = Map.copyOf(text);
            dates = Map.copyOf(dates);
            reviewers = List.copyOf(reviewers);
        }

        @Override
        public String toString() {
            return "Result[protected]";
        }
    }

    /** Reads and decodes the payload of one embedded document. */
    static Payload payload(CdsExportLaboratoryOccurrenceService.NativeSource source, int index)
            throws IOException {
        var document = source.parsed().documents().get(index);
        var raw = source.parsed().raw();
        var repetitions = raw.repetitions(document.result(), 5);
        String value =
                document.repetition() < repetitions.size()
                        ? repetitions.get(document.repetition())
                        : "";
        var components = CdsExportNativeLaboratoryParser.Raw.split(value, raw.component);
        String type = component(components, 2, raw),
                subtype = component(components, 3, raw),
                declared = component(components, 4, raw),
                data = component(components, 5, raw);
        String encoding = null;
        int payload = 5;
        if (data.isBlank()) {
            payload = 1;
            data = component(components, 1, raw);
        } else encoding = declared.isBlank() ? null : declared.trim();
        byte[] bytes = null;
        if (data.startsWith("{\\rtf"))
            bytes = data.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        else if ("Base64".equalsIgnoreCase(encoding)) bytes = base64(data);
        else if ("Hex".equalsIgnoreCase(encoding)) {
            try {
                String compact = data.replaceAll("\\s+", "");
                bytes = compact.isEmpty() ? null : HexFormat.of().parseHex(compact);
            } catch (IllegalArgumentException invalid) {
                bytes = null;
            }
        } else if (encoding == null && EdObservationValue.isBase64Shaped(data)) {
            byte[] decoded = base64(data);
            if (decoded != null
                    && (!CdsExportReportFileFormats.extension(null, null, prefix(decoded))
                                    .equals(CdsExportReportFileFormats.OPAQUE)
                            || data.replaceAll("\\s+", "").length()
                                    >= MINIMUM_UNRECOGNISED_BINARY)) bytes = decoded;
        }
        if (bytes != null && bytes.length == 0) bytes = null;
        String text = null;
        if (bytes == null) {
            // Text as CARLOS shows it: the laboratory's escapes decoded, never trimmed.
            text =
                    payload > components.size()
                            ? ""
                            : literal(
                                    CdsExportNativeLaboratoryMapper.decode(
                                            components.get(payload - 1), raw));
            if (!CdsExportNativeFieldAssessments.legal(text, Integer.MAX_VALUE))
                review("CDS_EXPORT_NATIVE_LAB_DOCUMENT_TEXT_REQUIRES_REVIEW");
        }
        return new Payload(bytes, text, encoding, type, subtype);
    }

    /**
     * @param filePath the attachment's package path for a Binary payload, otherwise null
     * @param extension the declared extension of a Binary payload, otherwise null
     */
    Result map(
            CdsExportLaboratoryOccurrenceService.NativeSource source,
            int index,
            Payload payload,
            String filePath,
            String extension,
            ZoneId zone,
            CdsExportInboxReviewMapper.Reviews reviews)
            throws IOException {
        Objects.requireNonNull(reviews);
        if ((payload.bytes() == null) != (filePath == null)
                || (filePath == null) != (extension == null)
                || filePath != null && !filePath.matches("Reports/[0-9a-f]{64}\\.[a-z0-9]{1,12}"))
            throw new IOException("CDS_EXPORT_NATIVE_LAB_DOCUMENT_SCOPE_INVALID");
        try {
            var document = source.parsed().documents().get(index);
            var raw = source.parsed().raw();
            Segment obx = document.result(), obr = document.order();
            var text = new LinkedHashMap<String, String>();
            var dates = new LinkedHashMap<String, CdsExportDateDto>();
            if (payload.bytes() != null) {
                text.put("Format", "Binary");
                text.put("FileExtensionAndVersion", extension);
                text.put("FilePath", filePath);
            } else {
                text.put("Format", "Text");
                text.put("FileExtensionAndVersion", "txt");
                if (!payload.text().isEmpty()) text.put("TextContent", payload.text());
            }
            text.put("Class", "Lab Report");
            String name = componentText(raw, obx, 3, 2);
            String ordered = componentText(raw, obr, 4, 2);
            String subclass = name.isEmpty() ? ordered : name;
            if (!subclass.isEmpty() && CdsExportNativeFieldAssessments.legal(subclass, 60))
                text.put("SubClass", subclass);
            String collected = scalar(obx, 14);
            if (collected.isEmpty()) collected = scalar(obr, 7);
            if (!collected.isEmpty())
                dates.put("EventDateTime", CdsExportNativeLaboratoryMapper.temporal(collected, zone));
            String laboratory = source.information().fields().get("sending_facility");
            if (laboratory == null || laboratory.isEmpty())
                laboratory = new Terser(source.parsed().message()).get("/MSH-4-1");
            laboratory = literal(laboratory);
            if (!laboratory.isEmpty() && CdsExportNativeFieldAssessments.legal(laboratory, 120))
                text.put("SourceFacility", laboratory);
            String stored = source.information().fields().get("accessionNum");
            String order = scalar(obr, 3);
            String accession = stored == null || stored.isEmpty() ? order : stored;
            // Fixed key order, so an unchanged report produces the same Notes bytes.
            var identification = new LinkedHashMap<String, Object>();
            identification.put("accessionNumber", accession);
            identification.put("labNo", source.id());
            identification.put("observation", document.ordinal());
            identification.put("laboratory", laboratory);
            identification.put("testCode", scalar(obx, 3));
            identification.put("testName", name);
            identification.put("orderedTest", ordered);
            identification.put("orderNumber", order);
            identification.put("subId", scalar(obx, 4));
            identification.put("resultStatus", scalar(obx, 11));
            identification.put("dataType", payload.dataType());
            identification.put("dataSubtype", payload.subtype());
            identification.put("encoding", payload.encoding() == null ? "" : payload.encoding());
            var notes = new StringBuilder();
            for (var comment : document.comments())
                for (String line : raw.repetitions(comment, 3)) {
                    if (!notes.isEmpty()) notes.append('\n');
                    notes.append(literal(CdsExportNativeLaboratoryMapper.decode(line, raw)));
                }
            identification.put("notesFromLab", notes.toString());
            var details = new LinkedHashMap<String, Object>();
            details.put("version", 1);
            details.put("embeddedDocument", identification);
            if (!reviews.carried().isEmpty()) {
                var carried = new ArrayList<Map<String, Object>>();
                for (var row : reviews.carried()) {
                    var entry = new LinkedHashMap<String, Object>();
                    entry.put("table", row.table());
                    entry.put("fields", new TreeMap<>(row.fields()));
                    carried.add(entry);
                }
                details.put("reviews", carried);
                var named = new LinkedHashMap<String, Object>();
                for (var entry : reviews.providers().entrySet()) {
                    var value = new LinkedHashMap<String, String>();
                    value.put("firstName", entry.getValue().firstName());
                    value.put("lastName", entry.getValue().lastName());
                    if (entry.getValue().ohipNumber() != null)
                        value.put("ohip", entry.getValue().ohipNumber());
                    named.put(entry.getKey(), value);
                }
                if (!named.isEmpty()) details.put("reviewers", named);
            }
            String written =
                    "[CARLOS embedded laboratory document]\nAccession number: "
                            + accession
                            + "\n"
                            + JsonMapper.builder().build().writeValueAsString(details);
            if (!CdsExportNativeFieldAssessments.legal(written, 32000))
                review("CDS_EXPORT_NATIVE_LAB_DOCUMENT_NOTES_CAPACITY");
            text.put("Notes", written);
            // An acknowledgement of the report is a review of each of its documents; the date-only
            // element keeps the day and the route travels whole, with its time, in Notes.
            var reviewers = new ArrayList<CdsExportNativeReportMapper.Reviewed>();
            var seen = new HashSet<String>();
            for (var reviewed : reviews.reviewers()) {
                var date =
                        new CdsExportDateDto(
                                CdsExportDateDto.Precision.DATE,
                                reviewed.reviewed().lexical().substring(0, 10));
                if (seen.add(reviewed.providerNo() + "\u0000" + date.lexical()))
                    reviewers.add(new CdsExportNativeReportMapper.Reviewed(reviewed.provider(), date));
            }
            return new Result(text, dates, reviewers);
        } catch (HL7Exception failure) {
            review("CDS_EXPORT_NATIVE_LAB_FIELDS_REQUIRES_REVIEW");
            return null;
        }
    }

    void write(Result result, CdsExportXmlComposer.ContentWriter xml) throws IOException {
        for (String name : List.of("Format", "FileExtensionAndVersion", "FilePath"))
            text(result, xml, name);
        if (result.text().containsKey("TextContent")) {
            xml.start("cds", "Content");
            xml.element("cds_dt", "TextContent", result.text().get("TextContent"));
            xml.end();
        }
        for (String name : List.of("Class", "SubClass")) text(result, xml, name);
        if (result.dates().containsKey("EventDateTime")) {
            xml.start("cds", "EventDateTime");
            CdsExportNativeHistoryXmlWriter.dateContents(xml, result.dates().get("EventDateTime"));
            xml.end();
        }
        text(result, xml, "SourceFacility");
        for (var reviewed : result.reviewers()) {
            xml.start("cds", "ReportReviewed");
            xml.start("cds", "Name");
            xml.element("cds_dt", "FirstName", reviewed.provider().firstName());
            xml.element("cds_dt", "LastName", reviewed.provider().lastName());
            xml.end();
            if (reviewed.provider().ohipNumber() != null)
                xml.element("cds", "ReviewingOHIPPhysicianId", reviewed.provider().ohipNumber());
            xml.start("cds", "DateTimeReportReviewed");
            CdsExportNativeHistoryXmlWriter.dateContents(xml, reviewed.date());
            xml.end();
            xml.end();
        }
        text(result, xml, "Notes");
    }

    static byte[] prefix(byte[] bytes) {
        return Arrays.copyOf(bytes, Math.min(bytes.length, CdsExportReportFileFormats.PREFIX));
    }

    /** Strict base64 after the line breaks HL7 senders insert; null when it does not decode. */
    private static byte[] base64(String data) {
        String compact = data.replaceAll("[\\r\\n\\t ]+", "");
        if (compact.isEmpty()) return null;
        try {
            return Base64.getDecoder().decode(compact);
        } catch (IllegalArgumentException notBase64) {
            try {
                return Base64.getUrlDecoder().decode(compact);
            } catch (IllegalArgumentException notUrlSafe) {
                return null;
            }
        }
    }

    /** One component of the raw ED value with only HL7's delimiter escapes decoded. */
    private static String component(
            List<String> components, int component, CdsExportNativeLaboratoryParser.Raw raw) {
        return component > components.size()
                ? ""
                : literal(
                        CdsExportNativeLaboratoryParser.unescapeDelimiters(
                                components.get(component - 1), raw));
    }

    private static String componentText(
            CdsExportNativeLaboratoryParser.Raw raw, Segment segment, int field, int component)
            throws IOException {
        var repetitions = raw.repetitions(segment, field);
        if (repetitions.isEmpty()) return "";
        var components =
                CdsExportNativeLaboratoryParser.Raw.split(repetitions.getFirst(), raw.component);
        return component > components.size()
                ? ""
                : literal(CdsExportNativeLaboratoryMapper.decode(components.get(component - 1), raw));
    }

    private static String scalar(Segment segment, int field) throws HL7Exception {
        return literal(Terser.get(segment, field, 0, 1, 1));
    }

    private static String literal(String value) {
        return value == null || value.equals("\"\"") ? "" : value;
    }

    private static void text(Result result, CdsExportXmlComposer.ContentWriter xml, String name)
            throws IOException {
        if (result.text().containsKey(name)) xml.element("cds", name, result.text().get(name));
    }

    private static void review(String code) throws CdsExportMappingReviewException {
        throw new CdsExportMappingReviewException(code);
    }
}
