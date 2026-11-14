/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportNativeFieldKeyDto;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Where the CARLOS source context of a Reports record travels.
 *
 * <p>Reports/Notes is the only free text of a Reports record, the category has no ResidualInfo,
 * and Notes is a {@code text32K}: 32,000 characters as the migration dictionary counts it, which
 * the export's own XML validator enforces. That number is not CARLOS's. It decides where the
 * context is written, never whether the report is exported:
 *
 * <ul>
 *   <li>when the whole Notes text fits, it is written in Notes, as it always was;
 *   <li>when it does not, Notes keeps its leading lines (the statements a person reads) followed
 *       by one fixed sentence naming a reference, and the complete text travels in a companion
 *       Reports record written directly after the report: Format Text, TextContent the same
 *       reference followed by the complete Notes text, Class Medical Records Report, SubClass
 *       {@value #SUBCLASS}. TextContent has no length facet, so nothing is cut.
 * </ul>
 *
 * <p>The companion has its own Reports identity, which the patient inventory expects by running
 * the same mapping ({@link CdsExportReportContextPlanner}); a companion the inventory did not
 * predict stops the document as any unexpected occurrence does. A CARLOS import stores the
 * companion as an ordinary text report of the chart: it does not join it back to the report it
 * belongs to, so the two are matched by the reference a person reads in both.
 */
final class CdsExportReportContext {
    /** Reports/Notes: text32K in the schema, 32,000 characters in the migration dictionary. */
    static final int NOTES_LENGTH = 32000;

    /** SubClass of every companion record. */
    static final String SUBCLASS = "CARLOS context of a report";

    private CdsExportReportContext() {}

    /**
     * The Notes text of a report and, when its context did not fit there, the companion text.
     *
     * @param notes the text of Reports/Notes, at most {@link #NOTES_LENGTH} characters
     * @param companion the TextContent of the companion record, or null when Notes holds it all
     */
    record Notes(String notes, String companion) {
        @Override
        public String toString() {
            return "Notes[protected]";
        }
    }

    /**
     * Decides where a report's context is written.
     *
     * @param lead the lines kept in Notes either way, each ended by a line feed; legal XML text of
     *     a few hundred characters at most (fixed statements), or empty
     * @param context the context header and its JSON, legal XML text of any length
     * @param reference the companion's identity ({@link #id}), written in both records
     */
    static Notes notes(String lead, String context, String reference) {
        String whole = lead + context;
        if (whole.codePointCount(0, whole.length()) <= NOTES_LENGTH) return new Notes(whole, null);
        String pointer =
                "The CARLOS source context of this report is longer than a CDS Notes element"
                        + " holds. It is exported complete as the text of the report of subclass \""
                        + SUBCLASS
                        + "\" beside this one, under the reference "
                        + reference
                        + ".";
        return new Notes(
                lead + pointer,
                "CARLOS data summary: the complete CARLOS source context of a report exported"
                        + " beside this one, whose Notes name the reference "
                        + reference
                        + ".\n"
                        + whole);
    }

    /**
     * The identity of the companion record of the report stored as {@code id} of {@code table}.
     * It is a digest of local identifiers only and doubles as the reference written in both
     * records.
     */
    static String id(String table, String id) {
        return CdsImportKeys.hash("carlos-native-occurrence-v1", "report_context", table, id);
    }

    /** The companion record: a text report whose content is the complete context. */
    static void write(String text, CdsExportXmlComposer.ContentWriter xml) throws IOException {
        xml.element("cds", "Format", "Text");
        xml.element("cds", "FileExtensionAndVersion", "txt");
        xml.start("cds", "Content");
        xml.element("cds_dt", "TextContent", text);
        xml.end();
        xml.element("cds", "Class", "Medical Records Report");
        xml.element("cds", "SubClass", SUBCLASS);
        xml.element(
                "cds",
                "Notes",
                "CARLOS data summary: the report this context belongs to is exported beside this"
                        + " one; its Notes name the same reference.");
    }

    /**
     * The decisions of the fields that travel in the context, as the companion record accounts
     * them: every field decided for Reports/Notes is exported in the companion's Content.
     */
    static Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> carried(
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields) {
        var result = new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        fields.forEach(
                (key, value) -> {
                    if ("Reports/Notes".equals(value.destination()))
                        result.put(
                                key,
                                new CdsExportFieldAssessmentDto(
                                        value.presence(),
                                        value.outcome(),
                                        "Reports/Content",
                                        value.rule()));
                });
        return result;
    }

    /** {@code fields} with every Reports/Notes decision moved to the companion's Content. */
    static Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> relocated(
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields) {
        return CdsExportInboxReviewMapper.relocated(fields, "Reports/Notes", "Reports/Content");
    }
}
