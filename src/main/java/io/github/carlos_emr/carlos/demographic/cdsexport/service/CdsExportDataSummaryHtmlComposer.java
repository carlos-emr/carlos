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

import io.github.carlos_emr.carlos.utility.SafeEncode;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

/**
 * Composes the deterministic HTML of one CARLOS data summary: a title naming the record kind, the
 * patient identity a receiving clinician needs, a statement that this is a data summary of CARLOS
 * records and not an original document, then every source row with its table and every column
 * with its exact value. Every value is HTML-encoded; SQL NULL, an empty value and a value are
 * labelled outside the value itself, so stored text can never be mistaken for a marker; long text
 * is printed whole and wraps instead of being clipped. A binary value shows its stored size and
 * SHA-256, and is also embedded when it is a PNG, JPEG or GIF image (directly or as stored
 * Base64), or decoded when its column stores Base64 UTF-8 text. No timestamp or other volatile
 * value enters the document.
 *
 * @since 2026-11-13
 */
final class CdsExportDataSummaryHtmlComposer {
    /** The patient header, from the captured demographic row (absent values stay blank). */
    record Patient(
            String patientNumber,
            String lastName,
            String firstName,
            String dateOfBirth,
            String sex,
            String healthCard,
            String chartNumber) {
        static Patient of(Map<String, String> demographic) {
            String year = demographic.get("year_of_birth"),
                    month = demographic.get("month_of_birth"),
                    day = demographic.get("date_of_birth");
            String birth =
                    blank(year) ? "" : year + (blank(month) ? "" : "-" + month + (blank(day) ? "" : "-" + day));
            String card = text(demographic.get("hin"));
            if (!blank(demographic.get("ver"))) card += " version " + demographic.get("ver");
            if (!blank(demographic.get("hc_type"))) card += " (" + demographic.get("hc_type") + ")";
            return new Patient(
                    text(demographic.get("demographic_no")),
                    text(demographic.get("last_name")),
                    text(demographic.get("first_name")),
                    birth,
                    text(demographic.get("sex")),
                    card.strip(),
                    text(demographic.get("chart_no")));
        }

        @Override
        public String toString() {
            return "CdsExportDataSummaryHtmlComposer.Patient[protected]";
        }
    }

    static final String STATEMENT =
            "This is a data summary of CARLOS EMR records, not an original document. CARLOS has no"
                    + " CDS 5.2 element for these records, so every stored field of each record is"
                    + " listed below with its exact value. A field marked “no value (SQL NULL)”"
                    + " had nothing stored; a field marked “empty” held an empty value.";

    private static final String STYLE =
            "body{font-family:'DejaVu Sans',sans-serif;font-size:9pt;color:#000}"
                    + "h1{font-size:14pt;margin:0 0 6pt 0}"
                    + "h2{font-size:11pt;margin:12pt 0 4pt 0;border-bottom:1px solid #444}"
                    + ".statement{border:1px solid #444;padding:4pt;margin:0 0 8pt 0}"
                    + ".patient td{padding:1pt 6pt 1pt 0;vertical-align:top}"
                    + ".field{margin:0 0 4pt 0}"
                    + ".name{font-weight:bold}"
                    + ".state{font-style:italic;color:#333}"
                    + ".value{white-space:pre-wrap;word-wrap:break-word;border-left:2px solid #999;"
                    + "padding:0 0 0 4pt;margin:1pt 0 0 0}"
                    + "img{max-width:100%}";

    String compose(CdsExportDataSummaryPlanner.Group group, Patient patient) {
        Objects.requireNonNull(group);
        Objects.requireNonNull(patient);
        var html = new StringBuilder(4096);
        String title = group.title();
        html.append("<!DOCTYPE html><html><head><meta charset=\"utf-8\"/><title>")
                .append(SafeEncode.forHtmlContent(title))
                .append("</title><style>")
                .append(STYLE)
                .append("</style></head><body><h1>")
                .append(SafeEncode.forHtmlContent(title))
                .append("</h1><p class=\"statement\">")
                .append(SafeEncode.forHtmlContent(STATEMENT))
                .append("</p>");
        if (group.notice() != null)
            html.append("<p class=\"statement\">")
                    .append(SafeEncode.forHtmlContent(group.notice()))
                    .append("</p>");
        html.append("<table class=\"patient\">");
        row(html, "Patient", join(patient.lastName(), patient.firstName()));
        row(html, "Date of birth", patient.dateOfBirth());
        row(html, "Sex", patient.sex());
        row(html, "Health card", patient.healthCard());
        row(html, "Chart number", patient.chartNumber());
        row(html, "CARLOS patient number", patient.patientNumber());
        row(html, "Record", group.subClass());
        html.append("</table>");
        int index = 0;
        long fields = 0;
        for (var source : group.rows()) {
            index++;
            html.append("<h2>")
                    .append(SafeEncode.forHtmlContent(
                            "Source row " + index + " of " + group.rows().size() + ": table " + source.table()))
                    .append("</h2>");
            var rule = CdsExportDataSummaryRegistry.rule(source.table());
            for (var entry : source.values().entrySet()) {
                fields++;
                if (CdsExportDataSummaryRegistry.withheld(source.table(), entry.getKey())
                        && !absent(entry.getValue())) {
                    withheld(html, entry.getKey());
                    continue;
                }
                field(html, entry.getKey(), entry.getValue(), rule.binaries().get(entry.getKey()));
            }
        }
        html.append("<p class=\"state\">")
                .append(SafeEncode.forHtmlContent(
                        "End of data summary: " + group.rows().size() + " source rows, " + fields + " fields."))
                .append("</p></body></html>");
        return html.toString();
    }

    private static boolean absent(CdsExportDataSummaryPlanner.Value value) {
        return switch (value.storage()) {
            case SQL_NULL -> true;
            case TEXT -> value.text().isEmpty();
            case BINARY -> value.bytes() == 0;
        };
    }

    /** A credential value: its presence is stated, its content (and length) never printed. */
    private static void withheld(StringBuilder html, String column) {
        html.append("<div class=\"field\"><span class=\"name\">")
                .append(SafeEncode.forHtmlContent(column))
                .append("</span> <span class=\"state\">")
                .append("value withheld: an e-mail access credential is not exported")
                .append("</span></div>");
    }

    private static void field(
            StringBuilder html,
            String column,
            CdsExportDataSummaryPlanner.Value value,
            CdsExportDataSummaryRegistry.BinaryPresentation presentation) {
        html.append("<div class=\"field\"><span class=\"name\">")
                .append(SafeEncode.forHtmlContent(column))
                .append("</span> <span class=\"state\">");
        switch (value.storage()) {
            case SQL_NULL -> html.append("no value (SQL NULL)</span>");
            case TEXT -> {
                if (value.text().isEmpty()) html.append("empty</span>");
                else {
                    html.append(value.text().codePointCount(0, value.text().length()))
                            .append(" characters</span><div class=\"value\">")
                            .append(SafeEncode.forHtmlContent(value.text()))
                            .append("</div>");
                }
            }
            case BINARY -> {
                if (value.bytes() == 0) {
                    html.append("empty (binary, 0 bytes)</span>");
                    break;
                }
                html.append("binary, ")
                        .append(value.bytes())
                        .append(" bytes, SHA-256 ")
                        .append(SafeEncode.forHtmlContent(value.sha256()))
                        .append("</span>");
                binary(html, value.content(), presentation);
            }
            default -> throw new IllegalStateException("CDS_EXPORT_DATA_SUMMARY_VALUE_INVALID");
        }
        html.append("</div>");
    }

    private static void binary(
            StringBuilder html,
            byte[] content,
            CdsExportDataSummaryRegistry.BinaryPresentation presentation) {
        if (content == null || presentation == null) return;
        if (presentation == CdsExportDataSummaryRegistry.BinaryPresentation.IMAGE) {
            String type = imageType(content);
            byte[] image = content;
            boolean decoded = false;
            if (type == null) {
                image = base64(content);
                type = image == null ? null : imageType(image);
                decoded = true;
            }
            if (type != null) {
                html.append("<div class=\"state\">")
                        .append(decoded ? "Image (decoded from the stored Base64):" : "Image:")
                        .append("</div><div><img alt=\"stored image\" src=\"data:")
                        .append(SafeEncode.forHtmlAttribute(type))
                        .append(";base64,")
                        .append(Base64.getEncoder().encodeToString(image))
                        .append("\"/></div>");
            }
            return;
        }
        byte[] decoded = base64(content);
        String text = decoded == null ? null : utf8(decoded);
        if (text != null)
            html.append("<div class=\"state\">Text decoded from the stored Base64 (")
                    .append(text.codePointCount(0, text.length()))
                    .append(" characters):</div><div class=\"value\">")
                    .append(SafeEncode.forHtmlContent(text))
                    .append("</div>");
    }

    /** The media type of a PNG, JPEG or GIF by its file signature, never by a stored type name. */
    static String imageType(byte[] bytes) {
        if (bytes == null) return null;
        if (bytes.length >= 8
                && (bytes[0] & 0xff) == 0x89
                && bytes[1] == 'P'
                && bytes[2] == 'N'
                && bytes[3] == 'G'
                && bytes[4] == 0x0d
                && bytes[5] == 0x0a
                && bytes[6] == 0x1a
                && bytes[7] == 0x0a) return "image/png";
        if (bytes.length >= 3
                && (bytes[0] & 0xff) == 0xff
                && (bytes[1] & 0xff) == 0xd8
                && (bytes[2] & 0xff) == 0xff) return "image/jpeg";
        if (bytes.length >= 6
                && bytes[0] == 'G'
                && bytes[1] == 'I'
                && bytes[2] == 'F'
                && bytes[3] == '8'
                && (bytes[4] == '7' || bytes[4] == '9')
                && bytes[5] == 'a') return "image/gif";
        return null;
    }

    /** Strict padded Base64 (no line breaks), or null. */
    static byte[] base64(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length % 4 != 0) return null;
        try {
            return Base64.getDecoder().decode(bytes);
        } catch (IllegalArgumentException notBase64) {
            return null;
        }
    }

    private static String utf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException notText) {
            return null;
        }
    }

    private static void row(StringBuilder html, String label, String value) {
        html.append("<tr><td class=\"name\">")
                .append(SafeEncode.forHtmlContent(label))
                .append("</td><td>")
                .append(SafeEncode.forHtmlContent(value))
                .append("</td></tr>");
    }

    private static String join(String last, String first) {
        if (blank(last)) return text(first);
        if (blank(first)) return last;
        return last + ", " + first;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
