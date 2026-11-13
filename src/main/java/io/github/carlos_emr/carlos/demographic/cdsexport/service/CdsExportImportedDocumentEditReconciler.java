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

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeBaselineCodec;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowComparison;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowState;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Outcome;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportMappingReviewException;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsReportNativeDto;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.*;

/**
 * Reconciles an edit made in CARLOS to the document row of a report that came from an earlier
 * CDS import. The document edit page ({@code AddEditDocument2Action.editDocument},
 * {@code EDocUtil.editDocumentSQL}) rewrites exactly the {@link #EDIT_PAGE_COLUMNS}: type,
 * class, subclass, description, source author and facility, the HTML ({@code ""}), responsible
 * provider, public flag, abnormal flag and received date on every save; the reviewer and review
 * time on a review, or else the update time and observation date; and, when a replacement file
 * is uploaded, the stored file name, content type and page count. A change anywhere else (the
 * program restriction, the patient link, the status) is not an edit-page edit and still requires
 * review.
 *
 * <p>The user's edit supersedes the source, as for the other imported-chart edits. A first review
 * stored on the row becomes one more ReportReviewed appended after the imported ones. A class,
 * subclass, source author, source facility, observation date or received date the user changed
 * replaces that element of the retained report with the current value, or removes it when the
 * user cleared it. A replacement file is the report's content: the Reports assembler attaches
 * the current bytes, and the imported file's name, content type and SHA-256 travel in context
 * ({@value #REPLACED_FILE}). Every changed column travels in the report's CARLOS edit context with its
 * current value and the imported value it superseded: the exact imported element text of a
 * replaced element, the imported value the import receipt proves (by its SHA-256) for a column
 * the import set, or that digest when no value can be proved. A column the page turned from SQL
 * NULL into {@code ""} (it posts an empty field for a missing value) is not an edit: the imported
 * element stays and the column is operational. An unedited imported document is never passed
 * here and exports exactly as before.
 */
final class CdsExportImportedDocumentEditReconciler {
    /** Every column {@code EDocUtil.editDocumentSQL} writes without replacing the file. */
    static final Set<String> EDIT_PAGE_COLUMNS =
            Set.of(
                    "doctype",
                    "docClass",
                    "docSubClass",
                    "docdesc",
                    "source",
                    "sourceFacility",
                    "docxml",
                    "responsible",
                    "public1",
                    "reviewer",
                    "reviewdatetime",
                    "updatedatetime",
                    "observationdate",
                    "abnormal",
                    "receivedDate",
                    // A replacement file ("File", ALLOW_UPDATE_DOCUMENT_CONTENT): its stored name,
                    // content type and page count. The content time of an imported document is
                    // kept (cds_content_precision is set), so it never changes here.
                    "docfilename",
                    "contenttype",
                    "number_of_pages");

    /** The columns a replacement file writes. */
    static final Set<String> FILE_COLUMNS = Set.of("docfilename", "contenttype", "number_of_pages");

    static final String SUPERSEDES = "CARLOS_EDIT_SUPERSEDES_IMPORTED_VALUE";
    static final String CARRIED = "CARLOS_EDIT_OF_IMPORTED_DOCUMENT_IN_SOURCE_CONTEXT";
    static final String CLEARED = "CARLOS_EDIT_CLEARED_IMPORTED_VALUE";
    static final String EMPTY_FORM_VALUE = "EMPTY_EDIT_FORM_VALUE_FOR_AN_ABSENT_IMPORTED_VALUE";
    static final String REPLACED_FILE = "REPLACED_IMPORTED_DOCUMENT_FILE_EXPORTED_CURRENT";

    /** The retained element each edited column supersedes. */
    private static final Map<String, String> ELEMENTS =
            Map.of(
                    "docClass", "Class[1]",
                    "docSubClass", "SubClass[1]",
                    "source", "SourceAuthorPhysician[1]",
                    "sourceFacility", "SourceFacility[1]",
                    "observationdate", "EventDateTime[1]",
                    "receivedDate", "ReceivedDateTime[1]");

    /** CDS report classes a current document class can be written as. */
    private static final Set<String> CLASSES =
            Set.of(
                    "Diagnostic Imaging Report",
                    "Diagnostic Test Report",
                    "Cardio Respiratory Report",
                    "Medical Records Report",
                    "Consultant Report",
                    "Lab Report",
                    "Other Letter");

    /** What one edited document row contributes to its retained report. */
    record Result(
            List<CdsExportRetainedSectionWriter.Change> changes,
            List<CdsExportNativeReportMapper.Reviewed> reviewers,
            Map<String, Object> context,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields,
            List<CdsExportCapturedTextRowDto> rows,
            Map<String, String> elements) {
        Result {
            changes = List.copyOf(changes);
            reviewers = List.copyOf(reviewers);
            fields = Map.copyOf(fields);
            rows = List.copyOf(rows);
            elements = Map.copyOf(elements);
        }

        @Override
        public String toString() {
            return "CdsExportImportedDocumentEditReconciler.Result[protected]";
        }
    }

    /**
     * The import-time state of each document of an imported report unit whose row CARLOS
     * changed, from the unit's sealed native baseline. Every other reference of the unit must be
     * unchanged and every changed column one the edit page writes; otherwise the patient is held,
     * as before.
     */
    static Map<String, CdsNativeRowState> editedDocuments(
            String address,
            CdsExportRetainedIndexDto index,
            CdsExportRetainedReceiptVerificationDto checked)
            throws IOException {
        if (checked.unchangedUnits().contains(address)) return Map.of();
        var comparisons = checked.units().get(address);
        if (comparisons == null) unmapped();
        var changed = new TreeMap<String, CdsExportRetainedReceiptVerificationDto.CheckedReference>();
        for (var reference : comparisons) {
            if (reference.reason() == CdsExportRetainedReceiptVerificationDto.Reason.UNCHANGED_ROW)
                continue;
            var comparison = reference.fields();
            if (!reference.expected().table().equals("document")
                    || reference.reason() != CdsExportRetainedReceiptVerificationDto.Reason.CHANGED_ROW
                    || comparison == null
                    || comparison.schemaChanged()
                    || !comparison.unqualifiedColumns().isEmpty()
                    || comparison.changedColumns().isEmpty()
                    || !EDIT_PAGE_COLUMNS.containsAll(comparison.changedColumns())
                    || changed.putIfAbsent(reference.expected().id(), reference) != null)
                unmapped();
        }
        if (changed.isEmpty()) unmapped();
        var unit =
                index.tables()
                        .getOrDefault("cds_import_unit", Map.of())
                        .get(CdsImportKeys.hash(address.substring(0, 36), address.substring(37)));
        if (unit == null || unit.fields().get("native_baseline") == null) unmapped();
        CdsNativeBaselineCodec.Document baseline;
        try {
            baseline =
                    CdsNativeBaselineCodec.decode(
                            unit.fields().get("native_baseline"),
                            unit.fields().get("target_references"),
                            unit.fields().get("target_digest"));
        } catch (IllegalArgumentException unreadable) {
            unmapped();
            return Map.of();
        }
        var result = new TreeMap<String, CdsNativeRowState>();
        for (var row : baseline.rows())
            if (row.table().equals("document") && changed.containsKey(row.id()))
                if (result.putIfAbsent(row.id(), row) != null
                        || !row.digest().equals(changed.get(row.id()).expected().digest()))
                    unmapped();
        if (!result.keySet().equals(changed.keySet())) unmapped();
        return Collections.unmodifiableMap(result);
    }

    /**
     * The values the import wrote on a document row, as {@code bind} checks them: the type,
     * status, file, class, subclass, source author and facility, content type and precision, the
     * description, and the flags the import leaves at zero.
     */
    static Map<String, String> imported(
            CdsReportNativeDto prepared, CdsReportNativeDto.Variant variant, String filename) {
        var metadata = prepared.values();
        String source = metadata.get("NativeSourceAuthor");
        if (source != null && source.codePointCount(0, source.length()) > 60) source = null;
        String subclass = metadata.get("SubClass");
        var result = new LinkedHashMap<String, String>();
        result.put("doctype", "CDS");
        result.put("status", "A");
        result.put("docfilename", filename);
        result.put("docClass", metadata.get("Class"));
        result.put("docSubClass", subclass);
        result.put("sourceFacility", metadata.get("SourceFacility"));
        result.put("source", source);
        result.put("contenttype", variant.mime());
        result.put("cds_content_precision", prepared.event().precision().name());
        result.put(
                "docdesc",
                (subclass == null || subclass.isBlank() ? "Imported report" : subclass)
                        + (prepared.variants().size() == 1
                                ? ""
                                : " (" + variant.name() + " representation)")
                        + (variant.mediaReviewRequired() ? " (source media review required)" : ""));
        result.put("abnormal", "0");
        result.put("public1", "0");
        result.put("restrictToProgram", "0");
        return result;
    }

    /**
     * @param document the current document row
     * @param baseline the row as the import wrote it ({@link #editedDocuments})
     * @param current the current row's verified witness state
     * @param expected the values the import wrote ({@link #imported})
     * @param fields the retained fields of the report unit
     */
    Result reconcile(
            CdsExportCapturedTextRowDto document,
            CdsNativeRowState baseline,
            CdsNativeRowState current,
            Map<String, String> expected,
            List<CdsExportRetainedFieldDto> fields,
            CdsExportRetainedSectionWriter.Values values,
            Map<String, CdsExportCapturedTextRowDto> providers,
            ZoneId zone)
            throws IOException {
        if (!document.table().equals("document")
                || !baseline.table().equals("document")
                || !baseline.id().equals(document.fields().get("document_no"))
                || !current.id().equals(baseline.id())) scope();
        var comparison = CdsNativeRowComparison.compare(baseline, current);
        if (comparison.schemaChanged()
                || !comparison.unqualifiedColumns().isEmpty()
                || !EDIT_PAGE_COLUMNS.containsAll(comparison.changedColumns())) unmapped();
        var root = root(fields);
        var row = document.fields();
        var all = new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        var state = new CdsExportNativeFieldAssessments(document, all);
        var changes = new ArrayList<CdsExportRetainedSectionWriter.Change>();
        var reviewers = new ArrayList<CdsExportNativeReportMapper.Reviewed>();
        var rows = new ArrayList<CdsExportCapturedTextRowDto>();
        var extra = new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        var columns = new TreeMap<String, Object>();
        var elements = new TreeMap<String, String>();
        var supersededElements = new TreeMap<String, Object>();
        for (String column : new TreeSet<>(comparison.changedColumns())) {
            var before = cell(baseline, column);
            String value = row.get(column);
            var entry = new LinkedHashMap<String, Object>();
            entry.put("imported", importedValue(before, expected.get(column)));
            entry.put("current", value);
            boolean formEmpty = before.sqlNull() && value != null && value.isEmpty();
            String rule;
            if (formEmpty) {
                // The page posts "" for a value the row did not have: not an edit.
                state.operational(column, EMPTY_FORM_VALUE);
                rule = EMPTY_FORM_VALUE;
            } else if (FILE_COLUMNS.contains(column)) {
                // The current file is the report's content (the Reports assembler attaches it);
                // the imported file's identity travels in context ("replacedFile").
                state.set(
                        column,
                        empty(value) ? Outcome.ABSENT : Outcome.MAPPED,
                        empty(value)
                                ? ""
                                : switch (column) {
                                    case "docfilename" -> "Reports/FilePath";
                                    case "contenttype" -> "Reports/FileExtensionAndVersion";
                                    default -> "Reports/Notes";
                                },
                        REPLACED_FILE);
                rule = REPLACED_FILE;
            } else if (column.equals("reviewer") || column.equals("reviewdatetime")) {
                // Decided below, once both columns are known; an "Update" clears the review.
                String number = row.get("reviewer");
                rule =
                        number == null || number.isBlank() || number.equals("0")
                                ? CARRIED
                                : "FIRST_REVIEWER_RECORDED_AFTER_IMPORT";
            } else if (ELEMENTS.containsKey(column)) {
                String path = ELEMENTS.get(column);
                var replacement = element(column, value);
                var prior = prior(fields, root, path);
                if (replacement == null && !empty(value)) {
                    // A current value the element cannot hold (an unlisted class, a long
                    // subclass): the imported element stays and the value travels in context.
                    state.set(column, Outcome.MAPPED, "Reports/Notes", CARRIED);
                    rule = CARRIED;
                } else {
                    if (replacement != null || !prior.isEmpty()) {
                        changes.add(
                                new CdsExportRetainedSectionWriter.Change(
                                        path,
                                        prior.keySet(),
                                        replacement == null ? null : replacement.content()));
                        elements.put(path, replacement == null ? "" : replacement.text());
                        var superseded = new TreeMap<String, String>();
                        for (var field : prior.entrySet())
                            superseded.put(field.getValue(), text(values, field.getKey(), fields));
                        supersededElements.put(path, superseded);
                    }
                    if (replacement == null) {
                        state.set(column, Outcome.ABSENT, "", CLEARED);
                        rule = CLEARED;
                    } else {
                        state.set(column, Outcome.MAPPED, "Reports/" + path.substring(0, path.indexOf('[')), SUPERSEDES);
                        rule = SUPERSEDES;
                    }
                }
            } else {
                state.set(
                        column,
                        empty(value) ? Outcome.ABSENT : Outcome.MAPPED,
                        empty(value) ? "" : "Reports/Notes",
                        CARRIED);
                rule = CARRIED;
            }
            entry.put("rule", rule);
            columns.put(column, entry);
        }
        if (columns.containsKey("reviewer") || columns.containsKey("reviewdatetime"))
            reviewer(document, state, providers, zone, reviewers, rows, extra, columns);
        var context = new LinkedHashMap<String, Object>();
        context.put("document_no", row.get("document_no"));
        context.put("columns", columns);
        if (!supersededElements.isEmpty()) context.put("supersededElements", supersededElements);
        var decisions = new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        for (String column : comparison.changedColumns()) {
            var key = new CdsExportNativeFieldKeyDto(document.table(), document.recordKey(), column);
            decisions.put(key, all.get(key));
        }
        decisions.putAll(extra);
        return new Result(changes, reviewers, context, decisions, rows, elements);
    }

    /** The first review the edit page stored on the row: one more ReportReviewed when dated. */
    private static void reviewer(
            CdsExportCapturedTextRowDto document,
            CdsExportNativeFieldAssessments state,
            Map<String, CdsExportCapturedTextRowDto> providers,
            ZoneId zone,
            List<CdsExportNativeReportMapper.Reviewed> reviewers,
            List<CdsExportCapturedTextRowDto> rows,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields,
            Map<String, Object> columns)
            throws IOException {
        String number = document.fields().get("reviewer");
        String reviewed = document.fields().get("reviewdatetime");
        if (number == null || number.isBlank() || number.equals("0")) {
            // An "Update" cleared the review: nothing to append, the change is in context.
            for (String column : List.of("reviewer", "reviewdatetime"))
                if (columns.containsKey(column)) state.set(column, Outcome.ABSENT, "", CARRIED);
            return;
        }
        var provider = providers.get(number);
        if (provider == null) review("CDS_EXPORT_NATIVE_REPORT_REVIEWER_REQUIRED");
        var mapped =
                new CdsExportPublicProviderMapper()
                        .map(provider, CdsExportPublicProviderMapper.Target.REPORT_REVIEWER);
        if (!mapped.representable()) review("CDS_EXPORT_NATIVE_REPORT_REVIEWER_REQUIRES_REVIEW");
        rows.add(provider);
        if (reviewed == null || reviewed.isEmpty()) {
            // The element requires a date; the undated reviewer travels in context only.
            mapped.fields()
                    .forEach(
                            (key, value) ->
                                    fields.put(
                                            key,
                                            new CdsExportFieldAssessmentDto(
                                                    value.presence(),
                                                    value.outcome(),
                                                    "Reports/Notes",
                                                    "UNDATED_PUBLIC_REVIEWER_IN_SOURCE_CONTEXT")));
            state.set("reviewer", Outcome.MAPPED, "Reports/Notes", CARRIED);
            if (columns.containsKey("reviewdatetime"))
                state.set("reviewdatetime", Outcome.ABSENT, "", CARRIED);
            return;
        }
        fields.putAll(mapped.fields());
        var clock = CdsExportNativeHistoryMapper.clock(reviewed, zone);
        reviewers.add(
                new CdsExportNativeReportMapper.Reviewed(
                        mapped.value(),
                        new CdsExportDateDto(
                                CdsExportDateDto.Precision.DATE, clock.lexical().substring(0, 10))));
        state.set(
                "reviewer",
                Outcome.MAPPED,
                "Reports/ReportReviewed/Name",
                "FIRST_REVIEWER_RECORDED_AFTER_IMPORT");
        state.set(
                "reviewdatetime",
                Outcome.MAPPED,
                "Reports/ReportReviewed/DateTimeReportReviewed",
                "REVIEW_DATE_ON_THE_WIRE_EXACT_TIME_IN_SOURCE_CONTEXT");
    }

    private record Replacement(String text, CdsExportXmlComposer.Content content) {}

    /** The element holding a current value, or null when it is empty or the element cannot. */
    private static Replacement element(String column, String value) {
        if (empty(value)) return null;
        switch (column) {
            case "docClass" -> {
                return CLASSES.contains(value) ? new Replacement(value, xml -> xml.text(value)) : null;
            }
            case "docSubClass" -> {
                return CdsExportNativeFieldAssessments.legal(value, 60)
                        ? new Replacement(value, xml -> xml.text(value))
                        : null;
            }
            case "sourceFacility" -> {
                return CdsExportNativeFieldAssessments.legal(value, 120)
                        ? new Replacement(value, xml -> xml.text(value))
                        : null;
            }
            case "source" -> {
                return CdsExportNativeFieldAssessments.legal(value, 120)
                        ? new Replacement(
                                value, xml -> xml.element("cds", "AuthorFreeText", value))
                        : null;
            }
            case "observationdate", "receivedDate" -> {
                // The edit page edits a date (MyDateFormat.getSysDate): its day is the value.
                if (value.length() < 10) return null;
                String day = value.substring(0, 10);
                try {
                    java.time.LocalDate.parse(day);
                } catch (java.time.DateTimeException invalid) {
                    return null;
                }
                return new Replacement(day, xml -> xml.element("cds_dt", "FullDate", day));
            }
            default -> {
                return null;
            }
        }
    }

    /** Whether the import wrote {@code value} to {@code column}, as the sealed baseline proves. */
    static boolean wrote(CdsNativeRowState baseline, String column, String value) {
        if (value == null) return false;
        for (int i = 0; i < baseline.columns().size(); i++)
            if (baseline.columns().get(i).name().equals(column)) {
                var cell = baseline.cells().get(i);
                byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                return !cell.sqlNull()
                        && cell.bytes() == bytes.length
                        && HexFormat.of()
                                .formatHex(CdsImportKeys.digest().digest(bytes))
                                .equals(cell.sha256());
            }
        return false;
    }

    /** The IDs of the retained fields under {@code path} (relative to the report root). */
    static Set<String> priorIds(List<CdsExportRetainedFieldDto> fields, String path)
            throws IOException {
        return prior(fields, root(fields), path).keySet();
    }

    /** The retained fields under {@code path}, by ID, with their path relative to the report. */
    private static Map<String, String> prior(
            List<CdsExportRetainedFieldDto> fields, String root, String path) {
        var result = new TreeMap<String, String>();
        for (var field : fields) {
            if (field.sourcePath().length() == root.length()) continue;
            String relative = field.sourcePath().substring(root.length() + 1);
            if (relative.equals(path) || relative.startsWith(path + "/"))
                result.put(field.id(), relative);
        }
        return result;
    }

    private static String root(List<CdsExportRetainedFieldDto> fields) throws IOException {
        if (fields.isEmpty()) scope();
        return CdsExportRetainedOccurrencePath.root(fields.getFirst(), CdsCategory.REPORTS);
    }

    /** The exact text of one retained field, as the retained report writes it. */
    private static String text(
            CdsExportRetainedSectionWriter.Values values,
            String id,
            List<CdsExportRetainedFieldDto> fields)
            throws IOException {
        for (var field : fields)
            if (field.id().equals(id)) {
                var out = new StringBuilder();
                try (Reader input = Objects.requireNonNull(values.text(field))) {
                    char[] buffer = new char[1024];
                    for (int n; (n = input.read(buffer)) != -1; ) {
                        out.append(buffer, 0, n);
                        if (out.length() > 32_768) review("CDS_EXPORT_IMPORTED_REPORT_EDIT_CAPACITY");
                    }
                }
                return out.toString();
            }
        scope();
        return null;
    }

    /**
     * The value the import wrote, when the receipt proves it: SQL NULL, the empty string, or the
     * value the import is known to write whose SHA-256 and length equal the receipt's; otherwise
     * the receipt's digest and length.
     */
    private static Object importedValue(CdsNativeRowState.Cell before, String candidate) {
        if (before.sqlNull()) return null;
        if (before.bytes() == 0) return "";
        if (candidate != null) {
            byte[] bytes = candidate.getBytes(StandardCharsets.UTF_8);
            if (bytes.length == before.bytes()
                    && HexFormat.of()
                            .formatHex(CdsImportKeys.digest().digest(bytes))
                            .equals(before.sha256())) return candidate;
        }
        var digest = new LinkedHashMap<String, Object>();
        digest.put("sha256", before.sha256());
        digest.put("bytes", before.bytes());
        return digest;
    }

    private static CdsNativeRowState.Cell cell(CdsNativeRowState state, String column)
            throws IOException {
        for (int i = 0; i < state.columns().size(); i++)
            if (state.columns().get(i).name().equals(column)) return state.cells().get(i);
        scope();
        return null;
    }

    private static boolean empty(String value) {
        return value == null || value.isEmpty();
    }

    private static void unmapped() {
        throw new io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportReviewException(
                io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportReviewException
                        .Reason.UNMAPPED_DATA);
    }

    private static void review(String code) throws CdsExportMappingReviewException {
        throw new CdsExportMappingReviewException(code);
    }

    private static void scope() throws IOException {
        throw new IOException("CDS_EXPORT_IMPORTED_DOCUMENT_EDIT_SCOPE_INVALID");
    }
}
