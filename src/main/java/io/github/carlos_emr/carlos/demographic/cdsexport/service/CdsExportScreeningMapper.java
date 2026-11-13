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
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Outcome;
import java.io.IOException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.*;

/**
 * Native screening (non-immunization prevention) as a CDS PastHealth procedure, as the legacy
 * CDS exporter wrote it: the description is the prevention type, its refused/ineligible/external
 * status and the "Result:"/"Reason:" extension lines; ProcedureDate is the recorded calendar date
 * at its recorded precision; Notes are the comments. The complete native parent, extension and
 * precision rows also travel in a CARLOS residual, so every captured field is MAPPED there unless
 * a rule below requires review. Superseded or removed versions are never mapped (see
 * {@link CdsExportPreventionVersions}), and a row whose frozen classification is not a
 * screening never becomes one here.
 *
 * <p>A row restricted to a program (legacy OSCAR 19/CAISI columns) is exported when a fresh check
 * says every exporting identity works in that program; its flag and program then travel exactly
 * in the residual with the rest of the row. Without that check it is held, and a malformed flag
 * always needs review.
 */
public final class CdsExportScreeningMapper {
    static final String RESIDUAL = "PastHealth/ResidualInfo",
            DESCRIPTION = "PastHealth/PastHealthProblemDescriptionOrProcedures;ResidualInfo",
            NOTES = "PastHealth/Notes;ResidualInfo",
            PROCEDURE_DATE = "PastHealth/ProcedureDate;ResidualInfo";
    private static final String CARRIED = "EXACT_NATIVE_PREVENTION_SOURCE_CONTEXT";
    private static final int DESCRIPTION_LIMIT = 250, NOTES_LIMIT = 65_536;
    private static final JsonMapper JSON = JsonMapper.builder().build();
    /** Native refused codes; the UI saves 3 for "Completed externally". Others need review. */
    private static final Map<String, String> STATUS =
            Map.of("0", "", "1", "Refused", "2", "Ineligible", "3", "Completed externally");
    private static final Set<String> PARENT =
                    Set.copyOf(CdsExportPatientTableReadSupport.Plan.PREVENTIONS_FULL.columns()),
            EXTENSION = Set.copyOf(CdsExportPatientTableReadSupport.Plan.PREVENTIONS_EXT.columns()),
            PRECISION =
                    Set.copyOf(CdsExportPatientTableReadSupport.Plan.PREVENTION_PRECISION.columns());

    public record Result(
            CdsExportScreeningDto value,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields,
            List<CdsExportCapturedTextRowDto> rows) {
        public Result {
            fields = Map.copyOf(fields);
            rows = List.copyOf(rows);
        }

        public boolean representable() {
            return value != null;
        }

        @Override
        public String toString() {
            return "CdsExportScreeningMapper.Result[protected]";
        }
    }

    public Result map(
            int patient,
            CdsExportCapturedTextRowDto row,
            List<CdsExportCapturedTextRowDto> extensions,
            List<CdsExportCapturedTextRowDto> precision,
            CdsExportPreventionClassifier classifier)
            throws IOException {
        // Without a live program check, a screening restricted to a program is held.
        return map(patient, row, extensions, precision, classifier, program -> false);
    }

    /**
     * As {@link #map(int, CdsExportCapturedTextRowDto, List, List, CdsExportPreventionClassifier)},
     * for a screening the chart may show only inside a program. {@code readableProgram} is a fresh
     * answer to whether every exporting identity works in that program; the row needs review
     * when it is false.
     */
    public Result map(
            int patient,
            CdsExportCapturedTextRowDto row,
            List<CdsExportCapturedTextRowDto> extensions,
            List<CdsExportCapturedTextRowDto> precision,
            CdsExportPreventionClassifier classifier,
            java.util.function.Predicate<String> readableProgram)
            throws IOException {
        Objects.requireNonNull(readableProgram);
        shape(row, CdsExportPreventionSources.SCREENINGS, PARENT);
        int id = integer(row.fields().get("id"));
        if (patient < 1
                || !Integer.toString(patient).equals(row.fields().get("demographic_no"))
                || !row.recordKey().equals(CdsExportPatientTableSourceReader.key(id))
                || extensions == null
                || extensions.size() > 1024
                || precision == null
                || precision.size() > 256
                || classifier == null) scope();
        classifier.sourceManifestSha256();
        var decisions =
                new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        var rows = new ArrayList<CdsExportCapturedTextRowDto>();
        var source = carried(row, decisions, rows);
        String deleted = source.raw("deleted");
        // "0" and a legacy NULL are the current version (carried as recorded). The occurrence
        // service never maps a superseded version ("1"); any other value needs review.
        if ("1".equals(deleted))
            source.review("deleted", "", "SUPERSEDED_OR_REMOVED_VERSION_IS_NOT_CURRENT");
        else if (!CdsExportPreventionVersions.current(deleted))
            source.review("deleted", RESIDUAL, "ARCHIVE_STATE_NEEDS_A_LOSSLESS_CARRIER");
        // An unrestricted row's flag and program are carried as recorded.
        var restriction =
                CdsExportPreventionScopePlanner.ProgramRestriction.of(
                        source.raw("restrictToProgram"), source.raw("programNo"));
        if (restriction.restricted() && readableProgram.test(restriction.program()))
            // The flag and program travel exactly in the residual with the whole native row.
            for (String column : List.of("restrictToProgram", "programNo"))
                source.set(
                        column,
                        Outcome.MAPPED,
                        RESIDUAL,
                        CdsExportImmunizationMapper.PROGRAM_SCOPE_AUTHORIZED);
        else if (restriction.restricted() || restriction.malformed())
            for (String column : List.of("restrictToProgram", "programNo"))
                source.review(column, "", "PROGRAM_SCOPE_REQUIRES_REVIEW");
        String type = source.raw("prevention_type");
        if (classifier.classify(type, source.raw("snomedId")).kind()
                != CdsExportPreventionClassifier.Kind.NON_IMMUNIZATION)
            source.review("prevention_type", DESCRIPTION, "CAPTURED_TYPE_IS_NOT_PROVEN_SCREENING");
        else if (!CdsExportNativeFieldAssessments.legal(type, DESCRIPTION_LIMIT) || type.isBlank())
            source.review("prevention_type", DESCRIPTION, "WIRE_TEXT_REQUIRES_REVIEW");
        else source.set("prevention_type", Outcome.MAPPED, DESCRIPTION, "EXACT_NATIVE_SCREENING_TYPE");
        String refused = source.raw("refused"),
                status = refused == null ? null : STATUS.get(refused);
        if (status == null)
            source.review("refused", DESCRIPTION, "UNKNOWN_PREVENTION_STATUS_REQUIRES_REVIEW");
        else if (!status.isEmpty())
            source.set("refused", Outcome.MAPPED, DESCRIPTION, "EXPLICIT_NATIVE_SCREENING_STATUS");
        var byKey = new HashMap<String, List<CdsExportNativeFieldAssessments>>();
        var seen = new HashSet<String>();
        for (var extension : sorted(extensions)) {
            shape(extension, CdsExportPreventionSources.SCREENING_EXTENSIONS, EXTENSION);
            if (!seen.add(extension.recordKey())
                    || !extension
                            .recordKey()
                            .equals(
                                    CdsExportPatientTableSourceReader.key(
                                            integer(extension.fields().get("id"))))
                    || integer(extension.fields().get("prevention_id")) != id) scope();
            var fields = carried(extension, decisions, rows);
            String key = fields.raw("keyval");
            if (Set.of("result", "reason", "comments").contains(key))
                byKey.computeIfAbsent(key, ignored -> new ArrayList<>()).add(fields);
        }
        for (var duplicate : byKey.values())
            if (duplicate.size() > 1)
                for (var fields : duplicate)
                    for (String column : List.of("keyval", "val"))
                        fields.review(column, "", "DUPLICATE_NATIVE_EXTENSION_REQUIRES_REVIEW");
        var formats = new ArrayList<String>();
        var dates = new ArrayList<CdsExportNativeFieldAssessments>();
        seen.clear();
        for (var item : sorted(precision)) {
            shape(item, CdsExportPreventionSources.SCREENING_PRECISION, PRECISION);
            if (!seen.add(item.recordKey())
                    || !item.recordKey()
                            .equals(
                                    CdsExportPatientTableSourceReader.key(
                                            integer(item.fields().get("id"))))
                    || !"4".equals(item.fields().get("table_name"))
                    || integer(item.fields().get("table_id")) != id) scope();
            var fields = carried(item, decisions, rows);
            if ("7".equals(fields.raw("field_name"))) {
                formats.add(fields.raw("format"));
                dates.add(fields);
            } else
                for (String column : List.of("field_name", "format"))
                    fields.review(column, "", "UNSUPPORTED_OR_NONSTANDARD_PREVENTION_DATE");
        }
        var date = new CdsExportDateDto(CdsExportDateDto.Precision.ABSENT, null);
        try {
            date = procedureDate(source.raw("prevention_date"), formats);
            if (date.precision() != CdsExportDateDto.Precision.ABSENT) {
                source.set(
                        "prevention_date",
                        Outcome.MAPPED,
                        PROCEDURE_DATE,
                        "EXACT_NATIVE_CALENDAR_DATE_AND_PRECISION");
                for (var fields : dates)
                    fields.set(
                            "format", Outcome.MAPPED, PROCEDURE_DATE, "EXPLICIT_NATIVE_DATE_PRECISION");
            }
        } catch (IOException invalidDate) {
            source.review("prevention_date", PROCEDURE_DATE, "DATE_PRECISION_REQUIRES_REVIEW");
            for (var fields : dates)
                fields.review("format", PROCEDURE_DATE, "DATE_PRECISION_REQUIRES_REVIEW");
        }
        var lines = new ArrayList<String>();
        if (type != null) lines.add(type);
        if (status != null && !status.isEmpty()) lines.add(status);
        var details = new ArrayList<String>();
        var detailFields = new ArrayList<CdsExportNativeFieldAssessments>();
        for (var entry : List.of(Map.entry("result", "Result:"), Map.entry("reason", "Reason:"))) {
            var fields = single(byKey, entry.getKey());
            // A value carried() already held for review never reaches a standard element.
            if (fields == null || !standard(fields.raw("val"))) continue;
            details.add(entry.getValue() + fields.raw("val"));
            detailFields.add(fields);
        }
        var comments = single(byKey, "comments");
        String comment = comments == null ? null : comments.raw("val");
        if (!standard(comment)) comment = null;
        var full = new ArrayList<>(lines);
        full.addAll(details);
        String description = String.join("\n", full), notes = comment;
        String detailDestination = DESCRIPTION;
        if (description.codePointCount(0, description.length()) > DESCRIPTION_LIMIT) {
            // Never clip: an over-long result/reason moves whole to Notes, ahead of the comments.
            description = String.join("\n", lines);
            var moved = new ArrayList<>(details);
            if (comment != null) moved.add(comment);
            notes = String.join("\n", moved);
            detailDestination = NOTES;
            if (description.codePointCount(0, description.length()) > DESCRIPTION_LIMIT)
                source.review(
                        "prevention_type",
                        DESCRIPTION,
                        "SCREENING_DESCRIPTION_CAPACITY_REQUIRES_REVIEW");
        }
        for (var fields : detailFields)
            fields.set("val", Outcome.MAPPED, detailDestination, "EXACT_NATIVE_SCREENING_DETAIL");
        if (comment != null)
            comments.set("val", Outcome.MAPPED, NOTES, "EXACT_NATIVE_SCREENING_COMMENTS");
        if (notes != null && notes.codePointCount(0, notes.length()) > NOTES_LIMIT) {
            for (var fields : detailFields)
                fields.review("val", NOTES, "SCREENING_NOTES_CAPACITY_REQUIRES_REVIEW");
            if (comment != null)
                comments.review("val", NOTES, "SCREENING_NOTES_CAPACITY_REQUIRES_REVIEW");
        }
        boolean blocked =
                decisions.values().stream()
                        .anyMatch(field -> field.outcome() == Outcome.REQUIRES_REVIEW);
        if (blocked) return new Result(null, decisions, rows);
        var context = new LinkedHashMap<String, Object>();
        context.put("version", 1);
        context.put("prevention", row.fields());
        context.put("extensions", sorted(extensions).stream().map(r -> r.fields()).toList());
        context.put("precision", sorted(precision).stream().map(r -> r.fields()).toList());
        return new Result(
                new CdsExportScreeningDto(
                        description, date, notes, tokenSafe(JSON.writeValueAsString(context))),
                decisions,
                rows);
    }

    /**
     * The recorded calendar date. A native clock time stays in the residual: ProcedureDate is a
     * date-only CDS type. A year or year-month marker keeps that precision; no day is invented.
     */
    static CdsExportDateDto procedureDate(String anchor, List<String> markers) throws IOException {
        if (markers.size() > 1) dateReview();
        String marker = markers.isEmpty() ? null : markers.getFirst();
        if (marker != null && !marker.isEmpty())
            return new CdsExportNativePreventionDateMapper().map(anchor, markers);
        if (anchor == null) return new CdsExportDateDto(CdsExportDateDto.Precision.ABSENT, null);
        if (!anchor.matches("[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}")) dateReview();
        try {
            var parsed =
                    LocalDateTime.parse(
                            anchor,
                            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")
                                    .withResolverStyle(ResolverStyle.STRICT));
            if (parsed.getYear() < 1) dateReview();
            return new CdsExportDateDto(
                    CdsExportDateDto.Precision.DATE, parsed.toLocalDate().toString());
        } catch (DateTimeException malformed) {
            dateReview();
            return null;
        }
    }

    /**
     * Residual Content is an xs:token, whose normalization collapses XML whitespace. Compact
     * JSON already escapes tab, CR and LF inside strings, and its only literal spaces are inside
     * strings, so escaping them keeps the exact values under any schema-aware reader.
     */
    static String tokenSafe(String json) {
        return json.replace(" ", "\\u0020");
    }

    /** Every captured field is carried exactly in the residual unless a rule says otherwise. */
    private static CdsExportNativeFieldAssessments carried(
            CdsExportCapturedTextRowDto row,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> decisions,
            List<CdsExportCapturedTextRowDto> rows) {
        rows.add(row);
        var fields = new CdsExportNativeFieldAssessments(row, decisions);
        for (var entry : row.fields().entrySet())
            if (entry.getValue() == null
                    || CdsExportNativeFieldAssessments.legal(entry.getValue(), Integer.MAX_VALUE))
                fields.set(entry.getKey(), Outcome.MAPPED, RESIDUAL, CARRIED);
            else fields.review(entry.getKey(), RESIDUAL, "WIRE_TEXT_REQUIRES_REVIEW");
        return fields;
    }

    private static boolean standard(String value) {
        return value != null
                && !value.isBlank()
                && CdsExportNativeFieldAssessments.legal(value, Integer.MAX_VALUE);
    }

    private static CdsExportNativeFieldAssessments single(
            Map<String, List<CdsExportNativeFieldAssessments>> byKey, String key) {
        var present = byKey.getOrDefault(key, List.of());
        return present.size() == 1 ? present.getFirst() : null;
    }

    private static List<CdsExportCapturedTextRowDto> sorted(
            List<CdsExportCapturedTextRowDto> rows) {
        return rows.stream()
                .sorted(Comparator.comparing(CdsExportCapturedTextRowDto::recordKey))
                .toList();
    }

    private static void shape(CdsExportCapturedTextRowDto row, String table, Set<String> columns)
            throws IOException {
        if (row == null
                || !table.equals(row.table())
                || !row.fields().keySet().equals(columns)
                || !row.exactNumbers().isEmpty()) scope();
    }

    private static int integer(String text) throws IOException {
        try {
            int value = Integer.parseInt(text);
            if (!Integer.toString(value).equals(text)) scope();
            return value;
        } catch (NumberFormatException failed) {
            throw new IOException("CDS_EXPORT_SCREENING_MAPPING_SCOPE_INVALID");
        }
    }

    private static void dateReview() throws IOException {
        throw new io.github.carlos_emr.carlos.demographic.cdsexport.support
                .CdsExportMappingReviewException("CDS_EXPORT_PREVENTION_DATE_REQUIRES_REVIEW");
    }

    private static void scope() throws IOException {
        throw new IOException("CDS_EXPORT_SCREENING_MAPPING_SCOPE_INVALID");
    }
}
