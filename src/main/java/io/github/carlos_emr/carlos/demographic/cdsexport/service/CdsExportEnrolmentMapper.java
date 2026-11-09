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

import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport;
import io.github.carlos_emr.carlos.commn.dto.CdsExportDemographicRowDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Outcome;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Presence;
import java.io.IOException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.*;

/** Exact roster fields only; unrelated demographic/archive values retain their separate coverage obligations. */
public final class CdsExportEnrolmentMapper {
    static final Map<String, String> COLUMNS =
            Map.of(
                    "roster_status",
                    "EnrollmentStatus",
                    "roster_date",
                    "EnrollmentDate",
                    "roster_termination_date",
                    "EnrollmentTerminationDate",
                    "roster_termination_reason",
                    "TerminationReason",
                    "roster_enrolled_to",
                    "EnrolledToPhysician");

    public record Result(
            CdsExportEnrolmentEventDto event,
            String nativeProvider,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields) {
        public Result {
            fields = Map.copyOf(fields);
        }

        public boolean representable() {
            return fields.values().stream()
                    .noneMatch(field -> field.outcome() == Outcome.REQUIRES_REVIEW);
        }

        @Override
        public String toString() {
            return "CdsExportEnrolmentMapper.Result[protected]";
        }
    }

    public Result map(
            int patient, CdsExportCapturedTextRowDto row, CdsExportCapturedTextRowDto provider)
            throws IOException {
        requireRow(patient, row);
        var decisions =
                new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        for (var column : COLUMNS.entrySet()) {
            String value = row.fields().get(column.getKey());
            decisions.put(
                    new CdsExportNativeFieldKeyDto(row.table(), row.recordKey(), column.getKey()),
                    new CdsExportFieldAssessmentDto(
                            presence(value),
                            present(value) ? Outcome.REQUIRES_REVIEW : Outcome.ABSENT,
                            path(column.getValue()),
                            "UNPROVEN_NATIVE_ENROLMENT_VALUE"));
        }
        String nativeState = row.fields().get("roster_status");
        boolean other =
                COLUMNS.keySet().stream()
                        .filter(column -> !column.equals("roster_status"))
                        .anyMatch(column -> present(row.fields().get(column)));
        // NR has no termination-event meaning. It never supplies a fabricated zero-status event.
        if ((!present(nativeState) || "NR".equals(nativeState)) && !other) {
            if (provider != null) scope();
            if ("NR".equals(nativeState))
                set(
                        row,
                        decisions,
                        "roster_status",
                        Outcome.OPERATIONAL,
                        "CURRENT_NON_ENROLLED_FLAG_WITHOUT_AN_ENROLMENT_EVENT");
            return new Result(null, null, decisions);
        }
        if (!"RO".equals(nativeState) && !"TE".equals(nativeState))
            return new Result(null, null, decisions);
        String status = "RO".equals(nativeState) ? "1" : "0";
        LocalDate start = date(row.fields().get("roster_date")),
                end = date(row.fields().get("roster_termination_date"));
        String reason = optional(row.fields().get("roster_termination_reason"));
        boolean invalid =
                start == null
                        || present(row.fields().get("roster_termination_date")) && end == null
                        || "0".equals(status) && end == null
                        || "1".equals(status) && (end != null || reason != null)
                        || end != null && start != null && end.isBefore(start)
                        || reason != null
                                && !CdsExportEnrolmentEventDto.TERMINATION_REASONS.contains(reason);
        if (invalid) {
            for (String column : COLUMNS.keySet())
                if (!column.equals("roster_enrolled_to"))
                    set(
                            row,
                            decisions,
                            column,
                            Outcome.REQUIRES_REVIEW,
                            "ENROLMENT_STATE_DATES_OR_CODE_REQUIRES_REVIEW");
            return new Result(null, optional(row.fields().get("roster_enrolled_to")), decisions);
        }
        for (String column : COLUMNS.keySet())
            if (!column.equals("roster_enrolled_to") && present(row.fields().get(column)))
                set(row, decisions, column, Outcome.MAPPED, "EXACT_NATIVE_ENROLMENT_EVENT");
        String id = optional(row.fields().get("roster_enrolled_to"));
        CdsExportPublicProviderDto physician = null;
        if (id == null) {
            if (provider != null) scope();
        } else if (provider == null || !id.equals(provider.fields().get("provider_no"))) {
            set(
                    row,
                    decisions,
                    "roster_enrolled_to",
                    Outcome.REQUIRES_REVIEW,
                    "EXACT_ENROLLED_PHYSICIAN_REFERENCE_REQUIRED");
        } else {
            var mapped =
                    new CdsExportPublicProviderMapper()
                            .map(provider, CdsExportPublicProviderMapper.Target.ENROLLED_PHYSICIAN);
            decisions.putAll(mapped.fields());
            physician = mapped.value();
            set(
                    row,
                    decisions,
                    "roster_enrolled_to",
                    mapped.representable() ? Outcome.MAPPED : Outcome.REQUIRES_REVIEW,
                    "EXACT_CAPTURED_ENROLLED_PHYSICIAN");
        }
        boolean blocked =
                decisions.values().stream()
                        .anyMatch(field -> field.outcome() == Outcome.REQUIRES_REVIEW);
        return new Result(
                blocked
                        ? null
                        : new CdsExportEnrolmentEventDto(status, start, end, reason, physician),
                id,
                decisions);
    }

    static void requireRow(int patient, CdsExportCapturedTextRowDto row) throws IOException {
        if (patient < 1
                || row == null
                || !Integer.toString(patient).equals(row.fields().get("demographic_no"))) scope();
        var columns = new HashSet<>(CdsExportDemographicRowDto.COLUMNS);
        columns.add("demographic_no");
        if (row.table().equals("demographic")) {
            if (!row.fields().keySet().equals(columns)
                    || !String.format(Locale.ROOT, "%010d", patient).equals(row.recordKey()))
                scope();
        } else if (row.table().equals("demographicArchive")) {
            if (!row.fields()
                    .keySet()
                    .equals(
                            Set.copyOf(
                                    CdsExportPatientTableReadSupport.Plan.DEMOGRAPHIC_ARCHIVE
                                            .columns()))) scope();
            String id = row.fields().get("id");
            try {
                long number = Long.parseLong(id);
                if (!Long.toString(number).equals(id)
                        || !CdsExportPatientTableSourceReader.key(number).equals(row.recordKey()))
                    scope();
            } catch (NumberFormatException | NullPointerException invalid) {
                scope();
            }
        } else scope();
    }

    static Presence presence(String value) {
        return value == null
                ? Presence.SQL_NULL
                : value.isEmpty() ? Presence.EMPTY : Presence.VALUE;
    }

    private static boolean present(String value) {
        return value != null && !value.isEmpty();
    }

    private static String optional(String value) {
        return present(value) ? value : null;
    }

    private static LocalDate date(String value) {
        if (value == null || !value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) return null;
        try {
            var parsed = LocalDate.parse(value);
            return parsed.getYear() > 0 && parsed.getYear() <= 9999 ? parsed : null;
        } catch (DateTimeException invalid) {
            return null;
        }
    }

    private static String path(String part) {
        return "Enrolment/EnrolmentHistory/" + part;
    }

    private static void set(
            CdsExportCapturedTextRowDto row,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields,
            String column,
            Outcome outcome,
            String rule) {
        fields.put(
                new CdsExportNativeFieldKeyDto(row.table(), row.recordKey(), column),
                new CdsExportFieldAssessmentDto(
                        presence(row.fields().get(column)),
                        outcome,
                        path(COLUMNS.get(column)),
                        rule));
    }

    private static void scope() throws IOException {
        throw new IOException("CDS_EXPORT_ENROLMENT_SOURCE_SCOPE_INVALID");
    }
}
