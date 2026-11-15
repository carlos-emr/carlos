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
import io.github.carlos_emr.carlos.commn.dao.CdsExportDiagnosisCodeReadSupport;
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Outcome;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportMappingReviewException;
import java.io.IOException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

/**
 * A Disease Registry entry ({@code dxresearch}) or a coded episode ({@code Episode}) as one CDS
 * ProblemList item, written by {@link CdsExportNativeHistoryXmlWriter} like the CPP problems.
 *
 * <p>Registry entry: status {@code A} is ProblemStatus {@code Active} and {@code C} is
 * {@code Resolved}, with the date of {@code update_date} (the day the entry was resolved or last
 * changed) as ResolutionDate, as the legacy exporter wrote it. {@code start_date} is OnsetDate at
 * the precision the registry shows: its {@code partial_date} marker ({@code YYYY} or
 * {@code YYYY-MM}) keeps a year or year-month, otherwise the stored full date is exported. Status
 * {@code D} entries are deleted from the registry list and are accounted, not exported.
 *
 * <p>Episode: {@code Current} is {@code Active}, {@code Complete} is {@code Resolved}, startDate
 * and endDate are OnsetDate and ResolutionDate, the episode description is ProblemDescription (or
 * ProblemDiagnosisDescription when no code description exists) and the notes are Notes. An episode
 * saved as {@code Deleted} is accounted, not exported. Any other status of either source holds
 * the patient for review.
 *
 * <p>Both: the stored coding system and code become DiagnosisCode exactly as stored (as the CPP
 * problems send theirs), and the public code-table description becomes
 * StandardCodeDescription and ProblemDiagnosisDescription (several differing rows of one code, as
 * the BC MSP table's continued descriptions, are joined by " / " in table row order). A code whose
 * table has no row for it, or whose system has no reviewed table, is still exported, with an empty
 * standard description. A start date left at the column default {@code 0001-01-01} gives no
 * OnsetDate, and a stored date that is not a calendar day gives no element ({@link
 * #NOT_A_CALENDAR_DAY}). A
 * code, system or text a standard element cannot carry exactly is left out of that element only.
 * Every captured value, apart from the row identity and patient owner, also travels in the
 * {@code CARLOS.NativeHistory.v1} residual as JSON with SQL NULL and empty text distinct and every
 * space escaped, so {@code xs:token} normalization cannot change it: nothing captured is lost.
 */
public final class CdsExportProblemRegistryMapper {
    static final String RESIDUAL = "ProblemList/ResidualInfo";
    private static final String DIAGNOSIS = "ProblemList/DiagnosisCode;ResidualInfo",
            DESCRIPTION = "ProblemList/ProblemDiagnosisDescription;DiagnosisCode;ResidualInfo",
            PROBLEM_DESCRIPTION = "ProblemList/ProblemDescription;ResidualInfo",
            DIAGNOSIS_DESCRIPTION = "ProblemList/ProblemDiagnosisDescription;ResidualInfo",
            STATUS = "ProblemList/ProblemStatus;ResidualInfo",
            ONSET = "ProblemList/OnsetDate;ResidualInfo",
            RESOLUTION = "ProblemList/ResolutionDate;ResidualInfo",
            NOTES = "ProblemList/Notes;ResidualInfo";
    static final String DELETED_REGISTRY_ENTRY = "DELETED_DISEASE_REGISTRY_ENTRY_NOT_SHOWN_IN_CHART",
            DELETED_EPISODE = "DELETED_EPISODE_NOT_CURRENT";
    private static final int TEXT_LIMIT = 250, CODE_LIMIT = 20, SYSTEM_LIMIT = 250;
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Set<String> REGISTRY =
                    Set.copyOf(CdsExportPatientTableReadSupport.Plan.DISEASE_REGISTRY.columns()),
            PRECISION =
                    Set.copyOf(
                            CdsExportPatientTableReadSupport.Plan.DISEASE_REGISTRY_PRECISION
                                    .columns()),
            EPISODE = Set.copyOf(CdsExportPatientTableReadSupport.Plan.EPISODES.columns());

    /** {@code value} is {@code null} for a deleted source, whose fields are all accounted. */
    public record Result(
            CdsExportNativeHistoryDto value,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields,
            List<CdsExportCapturedTextRowDto> rows) {
        public Result {
            fields = Map.copyOf(fields);
            rows = List.copyOf(rows);
        }

        public boolean exported() {
            return value != null;
        }

        @Override
        public String toString() {
            return "CdsExportProblemRegistryMapper.Result[protected]";
        }
    }

    /** The code and its public description, after the code-table rows have been assessed. */
    private record Coding(CdsExportNativeHistoryDto.Code code, String description) {}

    /**
     * @param precision the captured {@code partial_date} rows that name this entry
     * @param references the captured code-table rows whose lookup key is this entry's code
     */
    public Result mapRegistry(
            int patient,
            CdsExportCapturedTextRowDto entry,
            List<CdsExportCapturedTextRowDto> precision,
            List<CdsExportCapturedTextRowDto> references)
            throws IOException {
        shape(entry, CdsExportProblemRegistrySources.REGISTRY, REGISTRY);
        long id = owned(patient, entry, "dxresearch_no", "demographic_no");
        if (precision == null || references == null) scope();
        var decisions =
                new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        var rows = new ArrayList<CdsExportCapturedTextRowDto>();
        rows.add(entry);
        var source = new CdsExportNativeFieldAssessments(entry, decisions);
        var dates = new ArrayList<CdsExportNativeFieldAssessments>();
        var dateRows = new ArrayList<CdsExportCapturedTextRowDto>();
        for (var item : sorted(precision)) {
            shape(item, CdsExportProblemRegistrySources.PRECISION, PRECISION);
            if (!"3".equals(item.fields().get("table_name"))
                    || !Long.toString(id).equals(item.fields().get("table_id"))
                    || !item.recordKey()
                            .equals(
                                    CdsExportPatientTableSourceReader.key(
                                            number(item.fields().get("id"))))) scope();
            rows.add(item);
            dateRows.add(item);
            dates.add(new CdsExportNativeFieldAssessments(item, decisions));
        }
        String status = source.raw("status");
        if (CdsExportProblemRegistrySources.REGISTRY_DELETED.equals(status)) {
            source.operational("status", DELETED_REGISTRY_ENTRY);
            retire(source, entry, DELETED_REGISTRY_ENTRY);
            for (int i = 0; i < dates.size(); i++)
                retire(dates.get(i), dateRows.get(i), DELETED_REGISTRY_ENTRY);
            return new Result(null, decisions, rows);
        }
        if (!"A".equals(status) && !"C".equals(status))
            review("CDS_EXPORT_NATIVE_PROBLEM_REGISTRY_STATUS_REQUIRES_REVIEW");
        source.operational("dxresearch_no", "VERIFIED_NATIVE_REGISTRY_ENTRY_IDENTITY");
        source.operational("demographic_no", "VERIFIED_NATIVE_PATIENT_OWNER");
        var text = new LinkedHashMap<String, String>();
        var when = new LinkedHashMap<String, CdsExportDateDto>();
        text.put("ProblemStatus", "A".equals(status) ? "Active" : "Resolved");
        source.set("status", Outcome.MAPPED, STATUS, "EXACT_NATIVE_REGISTRY_STATUS");
        String start = source.raw("start_date");
        var onset = registryOnset(start, dates);
        if (onset != null) {
            when.put("OnsetDate", onset);
            source.set("start_date", Outcome.MAPPED, ONSET, "EXACT_NATIVE_REGISTRY_START_DATE");
        } else
            carried(
                    source,
                    "start_date",
                    NO_ONSET_RECORDED.equals(start)
                            ? "REGISTRY_START_DATE_COLUMN_DEFAULT_NO_ONSET_RECORDED"
                            : start != null && !start.isEmpty()
                                    ? NOT_A_CALENDAR_DAY
                                    : "EXACT_NATIVE_REGISTRY_START_DATE");
        String updated = source.raw("update_date");
        LocalDate updatedDay = clockDay(updated);
        if ("C".equals(status) && updatedDay != null) {
            when.put("ResolutionDate", day(updatedDay));
            source.set(
                    "update_date",
                    Outcome.MAPPED,
                    RESOLUTION,
                    "EXACT_NATIVE_REGISTRY_RESOLUTION_DAY_CLOCK_IN_RESIDUAL");
        } else
            // A resolved entry whose stored time is not a calendar time gives no ResolutionDate
            // (it held the patient as CDS_EXPORT_NATIVE_PROBLEM_REGISTRY_DATE_REQUIRES_REVIEW);
            // every registry writer stamps the save time, so only a row changed directly in the
            // database has one. The stored value stays in the residual either way.
            carried(
                    source,
                    "update_date",
                    "C".equals(status) ? NOT_A_CALENDAR_DAY : "EXACT_NATIVE_REGISTRY_LAST_CHANGE");
        carried(source, "association", "EXACT_NATIVE_REGISTRY_ISSUE_ASSOCIATION");
        carried(source, "providerNo", "EXACT_NATIVE_REGISTRY_ENTRY_PROVIDER");
        var coding =
                coding(source, "coding_system", "dxresearch_code", references, decisions, rows);
        if (coding.description() != null) text.put("ProblemDiagnosisDescription", coding.description());
        var context = new TreeMap<String, Object>();
        context.put("entry", carriedValues(entry, Set.of("dxresearch_no", "demographic_no")));
        var precisionValues = new ArrayList<Map<String, String>>();
        for (var item : dateRows)
            precisionValues.add(carriedValues(item, Set.of("id", "table_id", "table_name")));
        context.put("precision", precisionValues);
        context.put("references", referenceValues(references));
        return result(
                "diseaseRegistry", context, text, when, coding.code(), decisions, rows);
    }

    /** @param references the captured code-table rows whose lookup key is this episode's code */
    public Result mapEpisode(
            int patient,
            CdsExportCapturedTextRowDto episode,
            List<CdsExportCapturedTextRowDto> references)
            throws IOException {
        shape(episode, CdsExportProblemRegistrySources.EPISODES, EPISODE);
        owned(patient, episode, "id", "demographicNo");
        if (references == null) scope();
        var decisions =
                new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        var rows = new ArrayList<CdsExportCapturedTextRowDto>();
        rows.add(episode);
        var source = new CdsExportNativeFieldAssessments(episode, decisions);
        String status = source.raw("status");
        if (CdsExportProblemRegistrySources.EPISODE_DELETED.equals(status)) {
            source.operational("status", DELETED_EPISODE);
            retire(source, episode, DELETED_EPISODE);
            return new Result(null, decisions, rows);
        }
        if (!"Current".equals(status) && !"Complete".equals(status))
            review("CDS_EXPORT_NATIVE_EPISODE_STATUS_REQUIRES_REVIEW");
        source.operational("id", "VERIFIED_NATIVE_EPISODE_IDENTITY");
        source.operational("demographicNo", "VERIFIED_NATIVE_PATIENT_OWNER");
        var text = new LinkedHashMap<String, String>();
        var when = new LinkedHashMap<String, CdsExportDateDto>();
        text.put("ProblemStatus", "Current".equals(status) ? "Active" : "Resolved");
        source.set("status", Outcome.MAPPED, STATUS, "EXACT_NATIVE_EPISODE_STATUS");
        for (var date : List.of(Map.entry("startDate", "OnsetDate"), Map.entry("endDate", "ResolutionDate"))) {
            String stored = source.raw(date.getKey());
            if (stored == null || stored.isEmpty()) {
                carried(source, date.getKey(), "EXACT_NATIVE_EPISODE_DATE");
                continue;
            }
            var day = calendarDay(stored);
            if (day == null) {
                // A stored value that is not a calendar day (MariaDB's zero date, which the
                // packaged non-strict sql_mode stores for a date the column cannot hold, as an
                // OSCAR 19 episode form saved for a mistyped year) gives no element; the stored
                // value stays in the residual. It held the patient
                // (CDS_EXPORT_NATIVE_EPISODE_DATE_REQUIRES_REVIEW).
                carried(source, date.getKey(), NOT_A_CALENDAR_DAY);
                continue;
            }
            when.put(date.getValue(), day(day));
            source.set(
                    date.getKey(),
                    Outcome.MAPPED,
                    date.getValue().equals("OnsetDate") ? ONSET : RESOLUTION,
                    "EXACT_NATIVE_EPISODE_DATE");
        }
        carried(source, "lastUpdateUser", "EXACT_NATIVE_EPISODE_AUDIT");
        carried(source, "lastUpdateTime", "EXACT_NATIVE_EPISODE_AUDIT");
        var coding = coding(source, "codingSystem", "code", references, decisions, rows);
        if (coding.description() != null) text.put("ProblemDiagnosisDescription", coding.description());
        String description = source.raw("description");
        if (wire(description, TEXT_LIMIT) && !description.isBlank()) {
            if (coding.description() == null) {
                text.put("ProblemDiagnosisDescription", description);
                source.set(
                        "description",
                        Outcome.MAPPED,
                        DIAGNOSIS_DESCRIPTION,
                        "EXACT_NATIVE_EPISODE_DESCRIPTION");
            } else {
                text.put("ProblemDescription", description);
                source.set(
                        "description",
                        Outcome.MAPPED,
                        PROBLEM_DESCRIPTION,
                        "EXACT_NATIVE_EPISODE_DESCRIPTION");
            }
        } else
            carried(
                    source,
                    "description",
                    "EPISODE_DESCRIPTION_OUTSIDE_WIRE_LIMIT_CARRIED_IN_RESIDUAL");
        String notes = source.raw("notes");
        if (wire(notes, CdsExportNativeHistoryMapper.notesLimit(CdsCategory.PROBLEM_LIST))
                && !notes.isEmpty()) {
            text.put("Notes", notes);
            source.set("notes", Outcome.MAPPED, NOTES, "EXACT_NATIVE_EPISODE_NOTES");
        } else
            carried(source, "notes", "EPISODE_NOTES_OUTSIDE_WIRE_LIMIT_CARRIED_IN_RESIDUAL");
        var context = new TreeMap<String, Object>();
        context.put("entry", carriedValues(episode, Set.of("id", "demographicNo")));
        context.put("references", referenceValues(references));
        return result("episode", context, text, when, coding.code(), decisions, rows);
    }

    /**
     * Rule of a stored date that is not a calendar day (MariaDB's zero date, year 0): no element
     * is written, and the stored value, with any precision row, stays in the residual.
     */
    static final String NOT_A_CALENDAR_DAY = "STORED_DATE_NOT_A_CALENDAR_DAY_CARRIED_IN_RESIDUAL";

    /**
     * The registry start date at the precision the registry list shows ({@code PartialDateDao}
     * substring display): {@code YYYY} keeps the year and {@code YYYY-MM} the year-month of the
     * stored date; no marker, an empty one or any other one is the stored full date, as the list
     * shows it ({@code PartialDateDao.getDatePartial}). The list reads the newest precision row of
     * the entry's start date ({@code getPartialDate} orders by id descending); an older duplicate
     * row, which only a concurrent save can leave, and a row of another field (the registry
     * writes only {@code PartialDate.DXRESEARCH_STARTDATE}, so no screen leaves one) stay in the
     * residual. No start date, the column default {@code 0001-01-01} (none entered) and a stored
     * value that is not a calendar day (MariaDB's zero date, which the packaged non-strict
     * {@code sql_mode} stores for a date the column cannot hold, as the registry's update screen
     * saves for a mistyped five-digit year) give no OnsetDate, with the stored value and any
     * precision row in the residual. Each of these held the patient
     * ({@code CDS_EXPORT_NATIVE_PROBLEM_REGISTRY_DATE_PRECISION_REQUIRES_REVIEW},
     * {@code CDS_EXPORT_NATIVE_PROBLEM_REGISTRY_DATE_REQUIRES_REVIEW}).
     */
    private static CdsExportDateDto registryOnset(
            String stored, List<CdsExportNativeFieldAssessments> dates) {
        CdsExportNativeFieldAssessments newest = null;
        for (var fields : dates) {
            fields.operational("id", "VERIFIED_NATIVE_PRECISION_ROW_IDENTITY");
            fields.operational("table_id", "VERIFIED_NATIVE_PRECISION_OWNER");
            fields.operational("table_name", "VERIFIED_NATIVE_PRECISION_OWNER_TABLE");
            // PartialDate.DXRESEARCH_STARTDATE (4) is the one date the registry list reads.
            if (!"4".equals(fields.raw("field_name"))) {
                for (String column : List.of("field_name", "format"))
                    carried(fields, column, "PRECISION_ROW_OF_NO_REGISTRY_DATE_CARRIED");
                continue;
            }
            // Rows arrive in ascending id order, so the last one is the row the chart reads.
            if (newest != null)
                for (String column : List.of("field_name", "format"))
                    carried(newest, column, "OLDER_DUPLICATE_PRECISION_ROW_THE_CHART_DOES_NOT_READ");
            newest = fields;
            fields.set("field_name", Outcome.MAPPED, ONSET, "EXPLICIT_NATIVE_DATE_PRECISION_FIELD");
            fields.set("format", Outcome.MAPPED, ONSET, "EXPLICIT_NATIVE_DATE_PRECISION");
        }
        String marker = newest == null ? null : newest.raw("format");
        var day = calendarDay(stored);
        if (stored == null || stored.isEmpty() || NO_ONSET_RECORDED.equals(stored) || day == null) {
            if (newest != null)
                for (String column : List.of("field_name", "format"))
                    carried(
                            newest,
                            column,
                            NO_ONSET_RECORDED.equals(stored)
                                    ? "REGISTRY_START_DATE_COLUMN_DEFAULT_NO_ONSET_RECORDED"
                                    : "PRECISION_OF_NO_CALENDAR_START_DATE_CARRIED");
            return null;
        }
        if ("YYYY".equals(marker))
            return new CdsExportDateDto(
                    CdsExportDateDto.Precision.YEAR,
                    String.format(Locale.ROOT, "%04d", day.getYear()));
        if ("YYYY-MM".equals(marker))
            return new CdsExportDateDto(
                    CdsExportDateDto.Precision.YEAR_MONTH,
                    String.format(Locale.ROOT, "%04d-%02d", day.getYear(), day.getMonthValue()));
        // A marker the registry does not write (PartialDateDao.getFormat writes YYYY, YYYY-MM or
        // none) leaves the list showing the full date; the marker stays in the residual.
        if (marker != null && !marker.isEmpty())
            carried(newest, "format", "UNKNOWN_PRECISION_MARKER_FULL_DATE_AS_THE_LIST_SHOWS_IT");
        return day(day);
    }

    /**
     * DiagnosisCode and the public description. A stored system and code that a standard
     * element cannot carry exactly stay only in the residual; the item is still exported.
     */
    private static Coding coding(
            CdsExportNativeFieldAssessments source,
            String systemColumn,
            String codeColumn,
            List<CdsExportCapturedTextRowDto> references,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> decisions,
            List<CdsExportCapturedTextRowDto> rows)
            throws IOException {
        String system = source.raw(systemColumn), code = source.raw(codeColumn);
        var descriptions = new TreeSet<String>();
        var rowKeys = new HashSet<String>();
        // Each description with its code-table row key, so continued text is read in row order
        // (numeric row ids numerically; ICHPPC rows are keyed by their own code).
        var exact = new TreeMap<String, String>(ROW_ORDER);
        for (var reference : sorted(references)) {
            shape(
                    reference,
                    CdsExportProblemRegistrySources.REFERENCES,
                    CdsExportDiagnosisCodeReferenceSourceReader.COLUMNS);
            var allowed = CdsExportDiagnosisCodeReadSupport.Reference.of(system);
            if (!Objects.equals(reference.fields().get("coding_system"), system)
                    || !Objects.equals(reference.fields().get("code"), code)
                    || allowed == null
                    || !allowed.table().equals(reference.fields().get("reference_table")))
                scope();
            rows.add(reference);
            var fields = new CdsExportNativeFieldAssessments(reference, decisions);
            fields.operational("coding_system", "VERIFIED_REFERENCE_LOOKUP_KEY");
            fields.operational("code", "VERIFIED_REFERENCE_LOOKUP_KEY");
            for (String column : List.of("reference_table", "reference_id", "reference_code"))
                fields.set(column, Outcome.MAPPED, RESIDUAL, "EXACT_PUBLIC_CODE_TABLE_ROW");
            String description = reference.fields().get("description");
            String stripped = description == null ? "" : description.strip();
            String id = reference.fields().get("reference_id");
            if (id == null || id.isEmpty() || !rowKeys.add(id)) scope();
            if (!stripped.isEmpty() && CdsExportNativeFieldAssessments.legal(stripped, 65_536)
                    && descriptions.add(token(stripped)))
                exact.put(id, stripped);
            fields.set(
                    "description",
                    Outcome.MAPPED,
                    DESCRIPTION,
                    "EXACT_PUBLIC_CODE_TABLE_DESCRIPTION");
        }
        // A code table can hold one code on several rows with different text: the bundled BC MSP
        // table continues long descriptions on the next row, and the chart lists the entry once
        // per row. No row is chosen over another: every distinct description is sent, in
        // code-table row order, joined by " / "; each row stays whole in the residual.
        String joined = exact.isEmpty() ? null : String.join(" / ", exact.values());
        String standard = joined == null ? "" : token(joined);
        if (!CdsExportNativeFieldAssessments.legal(standard, TEXT_LIMIT)) standard = "";
        String description = joined;
        if (description != null && !CdsExportNativeFieldAssessments.legal(description, TEXT_LIMIT))
            description = null;
        CdsExportNativeHistoryDto.Code result = null;
        if (code != null
                && !code.isEmpty()
                && system != null
                && !system.isBlank()
                && CdsExportNativeFieldAssessments.legal(system, SYSTEM_LIMIT)) {
            // The code exactly as stored, as the CPP problems send it: one ProblemList never
            // mixes two spellings of a code (the legacy exporter's dotting also split long codes
            // at every third character).
            String value = code;
            if (token(value).equals(value)
                    && CdsExportNativeFieldAssessments.legal(value, CODE_LIMIT))
                result = new CdsExportNativeHistoryDto.Code(system, value, standard);
        }
        if (result != null) {
            source.set(systemColumn, Outcome.MAPPED, DIAGNOSIS, "EXACT_NATIVE_CODING_SYSTEM");
            source.set(codeColumn, Outcome.MAPPED, DIAGNOSIS, "EXACT_NATIVE_CODE");
        } else {
            carried(source, systemColumn, "CODE_NOT_A_STANDARD_CODE_CARRIED_IN_RESIDUAL");
            carried(source, codeColumn, "CODE_NOT_A_STANDARD_CODE_CARRIED_IN_RESIDUAL");
        }
        return new Coding(result, description);
    }

    private static Result result(
            String kind,
            Map<String, Object> context,
            Map<String, String> text,
            Map<String, CdsExportDateDto> dates,
            CdsExportNativeHistoryDto.Code code,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> decisions,
            List<CdsExportCapturedTextRowDto> rows)
            throws IOException {
        if (decisions.values().stream().anyMatch(v -> v.outcome() == Outcome.REQUIRES_REVIEW))
            review("CDS_EXPORT_NATIVE_PROBLEM_REGISTRY_UNMAPPED_FIELD");
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("version", 1);
        metadata.put(kind, context);
        return new Result(
                new CdsExportNativeHistoryDto(
                        CdsCategory.PROBLEM_LIST,
                        text,
                        dates,
                        code,
                        List.of(),
                        CdsExportScreeningMapper.tokenSafe(JSON.writeValueAsString(metadata))),
                decisions,
                rows);
    }

    /** Every value except the listed identity columns, in column order, NULL kept distinct. */
    private static Map<String, String> carriedValues(
            CdsExportCapturedTextRowDto row, Set<String> identity) {
        var result = new TreeMap<String, String>();
        for (var entry : row.fields().entrySet())
            if (!identity.contains(entry.getKey())) result.put(entry.getKey(), entry.getValue());
        return result;
    }

    private static List<Map<String, String>> referenceValues(
            List<CdsExportCapturedTextRowDto> references) {
        var result = new ArrayList<Map<String, String>>();
        for (var reference : sorted(references))
            result.add(carriedValues(reference, Set.of("coding_system", "code")));
        return result;
    }

    /** The dxresearch.start_date column default: no start date was entered. */
    static final String NO_ONSET_RECORDED = "0001-01-01";

    /** Code-table row keys: all-digit ids in numeric order before any other key, then text. */
    private static final Comparator<String> ROW_ORDER =
            Comparator.<String, Boolean>comparing(key -> !key.matches("[0-9]{1,64}"))
                    .thenComparing(
                            key ->
                                    key.matches("[0-9]{1,64}")
                                            ? new java.math.BigInteger(key)
                                            : java.math.BigInteger.ZERO)
                    .thenComparing(Comparator.naturalOrder());

    private static void carried(
            CdsExportNativeFieldAssessments fields, String column, String rule) {
        fields.set(column, Outcome.MAPPED, RESIDUAL, rule);
    }

    /** A deleted source: every captured field is explicitly not exported. */
    private static void retire(
            CdsExportNativeFieldAssessments fields, CdsExportCapturedTextRowDto row, String rule) {
        for (String column : row.fields().keySet()) fields.operational(column, rule);
    }

    private static boolean wire(String value, int limit) {
        return value != null && CdsExportNativeFieldAssessments.legal(value, limit);
    }

    /** xs:token normalization: XML whitespace runs collapse to one space, ends trimmed. */
    static String token(String value) {
        return value.replaceAll("[ \\t\\r\\n]+", " ").strip();
    }

    private static CdsExportDateDto day(LocalDate value) {
        return new CdsExportDateDto(CdsExportDateDto.Precision.DATE, value.toString());
    }

    /** A stored DATE column value; MariaDB's zero date and year 0 are not calendar days. */
    private static LocalDate calendarDay(String stored) {
        if (stored == null || !stored.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) return null;
        try {
            var value = LocalDate.parse(stored);
            return value.getYear() < 1 ? null : value;
        } catch (DateTimeException invalid) {
            return null;
        }
    }

    /** The calendar day of a stored DATETIME value; its clock stays in the residual. */
    private static LocalDate clockDay(String stored) {
        if (stored == null
                || !stored.matches(
                        "[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}(\\.[0-9]{1,6})?"))
            return null;
        try {
            LocalTime.parse(stored.substring(11));
        } catch (DateTimeException invalid) {
            return null;
        }
        return calendarDay(stored.substring(0, 10));
    }

    private static long owned(
            int patient, CdsExportCapturedTextRowDto row, String idColumn, String ownerColumn)
            throws IOException {
        long id = number(row.fields().get(idColumn));
        if (patient < 1
                || !Integer.toString(patient).equals(row.fields().get(ownerColumn))
                || !row.recordKey().equals(CdsExportPatientTableSourceReader.key(id))) scope();
        return id;
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

    private static long number(String text) throws IOException {
        try {
            long value = Long.parseLong(text);
            if (!Long.toString(value).equals(text)) scope();
            return value;
        } catch (NumberFormatException malformed) {
            throw new IOException("CDS_EXPORT_PROBLEM_REGISTRY_MAPPING_SCOPE_INVALID");
        }
    }

    private static void review(String code) throws CdsExportMappingReviewException {
        throw new CdsExportMappingReviewException(code);
    }

    private static void scope() throws IOException {
        throw new IOException("CDS_EXPORT_PROBLEM_REGISTRY_MAPPING_SCOPE_INVALID");
    }
}
