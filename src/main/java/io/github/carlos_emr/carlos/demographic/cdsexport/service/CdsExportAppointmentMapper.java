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
import io.github.carlos_emr.carlos.commn.dto.CdsExportProviderRowDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Outcome;
import java.io.IOException;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Native-only interpretation of exact captured appointment values and references. Imported
 * status is never a fallback for a changed/cleared local status. Extra values cannot be put
 * in Notes or ResidualInfo; this schema has no such residual field. The caller must reconcile
 * retained imported facts, archive/history and complete source coverage before publication.
 */
public final class CdsExportAppointmentMapper {
    public CdsExportAppointmentMappingDto map(
            int patient,
            CdsExportCapturedTextRowDto row,
            CdsExportAppointmentLookupsDto lookups,
            ZoneId clinicalZone)
            throws IOException {
        var result = candidate(patient, row, lookups, clinicalZone);
        boolean blocked =
                result.fields().values().stream()
                        .anyMatch(field -> field.outcome() == Outcome.REQUIRES_REVIEW);
        return new CdsExportAppointmentMappingDto(blocked ? null : result.value(), result.fields());
    }

    public CdsExportAppointmentMappingDto map(
            int patient,
            CdsExportCapturedTextRowDto row,
            CdsExportAppointmentLookupsDto lookups,
            ZoneId clinicalZone,
            CdsExportCapturedTextRowDto demographic)
            throws IOException {
        var result = candidate(patient, row, lookups, clinicalZone, demographic);
        boolean blocked =
                result.fields().values().stream()
                        .anyMatch(field -> field.outcome() == Outcome.REQUIRES_REVIEW);
        return new CdsExportAppointmentMappingDto(blocked ? null : result.value(), result.fields());
    }

    record Candidate(
            CdsExportAppointmentDto value,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields) {
        Candidate {
            fields = Map.copyOf(fields);
        }

        @Override
        public String toString() {
            return "CdsExportAppointmentMapper.Candidate[protected]";
        }
    }

    Candidate candidate(
            int patient,
            CdsExportCapturedTextRowDto row,
            CdsExportAppointmentLookupsDto lookups,
            ZoneId clinicalZone)
            throws IOException {
        return candidate(patient, row, lookups, clinicalZone, null);
    }

    Candidate candidate(
            int patient,
            CdsExportCapturedTextRowDto row,
            CdsExportAppointmentLookupsDto lookups,
            ZoneId clinicalZone,
            CdsExportCapturedTextRowDto demographic)
            throws IOException {
        return candidate(
                patient,
                row,
                lookups,
                clinicalZone,
                demographic,
                CdsExportAppointmentTimingMapper.EndConvention.NATIVE_INCLUSIVE);
    }

    Candidate candidate(
            int patient,
            CdsExportCapturedTextRowDto row,
            CdsExportAppointmentLookupsDto lookups,
            ZoneId clinicalZone,
            CdsExportCapturedTextRowDto demographic,
            CdsExportAppointmentTimingMapper.EndConvention endConvention)
            throws IOException {
        if (patient < 1
                || row == null
                || lookups == null
                || !"appointment".equals(row.table())
                || !row.fields()
                        .keySet()
                        .equals(
                                Set.copyOf(
                                        CdsExportPatientTableReadSupport.Plan.APPOINTMENTS
                                                .columns()))
                || !Integer.toString(patient).equals(row.fields().get("demographic_no"))) scope();
        try {
            int id = Integer.parseInt(row.fields().get("appointment_no"));
            if (!Integer.toString(id).equals(row.fields().get("appointment_no"))
                    || !CdsExportPatientTableSourceReader.key(id).equals(row.recordKey())) scope();
        } catch (NumberFormatException failure) {
            scope();
        }
        var decisions =
                new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        var source = new CdsExportNativeFieldAssessments(row, decisions);
        source.operational("appointment_no", "NATIVE_OCCURRENCE_IDENTITY");
        source.operational("demographic_no", "SEALED_PATIENT_IDENTITY");
        source.operational("updatedatetime", "CAPTURED_MODIFICATION_CLOCK_NOT_APPOINTMENT_DATE");
        CdsExportAppointmentTimingDto timing = null;
        try {
            timing =
                    new CdsExportAppointmentTimingMapper()
                            .map(
                                    source.raw("appointment_date"),
                                    source.raw("start_time"),
                                    source.raw("end_time"),
                                    clinicalZone,
                                    endConvention);
            source.set(
                    "appointment_date",
                    Outcome.MAPPED,
                    "AppointmentDate/FullDate",
                    "EXACT_NATIVE_APPOINTMENT_DATE");
            source.set(
                    "start_time",
                    Outcome.MAPPED,
                    "AppointmentTime",
                    "EXACT_NATIVE_APPOINTMENT_CLOCK");
            source.set(
                    "end_time", Outcome.MAPPED, "Duration", "EXACT_SAME_DAY_WHOLE_MINUTE_DURATION");
        } catch (IOException review) {
            for (String column : List.of("appointment_date", "start_time", "end_time"))
                source.review(
                        column,
                        "AppointmentDate,AppointmentTime,Duration",
                        "APPOINTMENT_TIMING_REQUIRES_REVIEW");
        }
        String purpose = source.text("reason", "AppointmentPurpose", 250);
        String notes = source.text("notes", "AppointmentNotes", 32000);
        if ("".equals(source.raw("reason"))) {
            purpose = "";
            source.set("reason", Outcome.MAPPED, "AppointmentPurpose", "EXACT_NATIVE_EMPTY_TEXT");
        }
        if ("".equals(source.raw("notes"))) {
            notes = "";
            source.set("notes", Outcome.MAPPED, "AppointmentNotes", "EXACT_NATIVE_EMPTY_TEXT");
        }
        String status = status(source, lookups, decisions);
        var provider = provider(source, lookups, decisions);
        if (source.raw("imported_status") != null && !source.raw("imported_status").isEmpty()) {
            source.review(
                    "imported_status",
                    "AppointmentStatus",
                    "RETAINED_APPOINTMENT_STATUS_REQUIRES_RECONCILIATION");
        }
        CdsExportAppointmentNativeMetadata.qualify(row, demographic, decisions);
        return new Candidate(
                timing == null
                        ? null
                        : new CdsExportAppointmentDto(timing, status, provider, purpose, notes),
                decisions);
    }

    private static String status(
            CdsExportNativeFieldAssessments source,
            CdsExportAppointmentLookupsDto lookups,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> decisions)
            throws IOException {
        String code = source.raw("status");
        if (code == null || code.isEmpty()) return null;
        var candidates = lookups.statuses().getOrDefault(code, List.of());
        if (candidates.size() != 1) {
            source.review("status", "AppointmentStatus", "EXACT_STATUS_DEFINITION_REQUIRED");
            return null;
        }
        var row = candidates.getFirst();
        if (!"appointment_status".equals(row.table())
                || !row.fields()
                        .keySet()
                        .equals(
                                Set.copyOf(
                                        CdsExportPatientTableReadSupport.Plan.APPOINTMENT_STATUSES
                                                .columns()))
                || !code.equals(row.fields().get("status"))) scope();
        try {
            if (!CdsExportPatientTableSourceReader.key(Integer.parseInt(row.fields().get("id")))
                    .equals(row.recordKey())) scope();
        } catch (NumberFormatException failure) {
            scope();
        }
        var lookup = new CdsExportNativeFieldAssessments(row, decisions);
        lookup.operational("id", "CAPTURED_STATUS_DEFINITION_IDENTITY");
        for (String column :
                List.of(
                        "active",
                        "color",
                        "editable",
                        "icon",
                        "short_letter_colour",
                        "short_letters"))
            lookup.operational(column, "CAPTURED_STATUS_DISPLAY_CONFIGURATION");
        lookup.set("status", Outcome.MAPPED, "AppointmentStatus", "EXACT_NATIVE_STATUS_REFERENCE");
        String label = lookup.text("description", "AppointmentStatus", 250);
        if (label == null || label.isBlank()) {
            source.review(
                    "status", "AppointmentStatus", "STATUS_DEFINITION_HAS_NO_MEANINGFUL_LABEL");
            return null;
        }
        source.set(
                "status", Outcome.MAPPED, "AppointmentStatus", "EXACT_CAPTURED_STATUS_DEFINITION");
        return label;
    }

    private static CdsExportPublicProviderDto provider(
            CdsExportNativeFieldAssessments source,
            CdsExportAppointmentLookupsDto lookups,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> decisions)
            throws IOException {
        String id = source.raw("provider_no");
        if (id == null || id.isEmpty()) return null;
        var row = lookups.providers().get(id);
        if (row == null) {
            source.review("provider_no", "Provider", "EXACT_PUBLIC_PROVIDER_REFERENCE_REQUIRED");
            return null;
        }
        if (!"provider".equals(row.table())
                || !row.fields().keySet().equals(CdsExportProviderRowDto.COLUMNS)
                || !id.equals(row.fields().get("provider_no"))
                || !CdsExportPublicProviderSourceReader.key(id).equals(row.recordKey())) scope();
        var mapped =
                new CdsExportPublicProviderMapper()
                        .map(row, CdsExportPublicProviderMapper.Target.APPOINTMENT);
        decisions.putAll(mapped.fields());
        if (!mapped.representable()) {
            source.review("provider_no", "Provider", "PUBLIC_PROVIDER_VALUES_REQUIRES_REVIEW");
            return null;
        }
        source.set("provider_no", Outcome.MAPPED, "Provider", "EXACT_CAPTURED_PUBLIC_PROVIDER");
        return mapped.value();
    }

    private static void scope() throws IOException {
        throw new IOException("CDS_EXPORT_APPOINTMENT_CAPTURE_SCOPE_INVALID");
    }
}
