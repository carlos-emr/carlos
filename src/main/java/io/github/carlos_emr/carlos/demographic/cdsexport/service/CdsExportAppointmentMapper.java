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
 * in Notes or ResidualInfo; this schema has no such residual field, so a populated value
 * without a reviewed rule (for example {@code remarks}, which only the SOAP schedule service
 * and legacy data fill) still requires review. The caller must reconcile retained imported
 * facts, superseded archive versions and complete source coverage before publication.
 */
public final class CdsExportAppointmentMapper {
    /**
     * Rule of a start time written as the schedule's wall-clock time with no offset, because it
     * falls in, or the appointment spans, a daylight-saving transition of the clinical zone.
     */
    static final String LOCAL_TIME_RULE = "LOCAL_WALL_CLOCK_TIME_AT_DAYLIGHT_SAVING_TRANSITION";
    /**
     * Scheduler, site, billing-workflow and booking-provenance columns CARLOS writes with every
     * booking. CDS 5.2 Appointments has no field for them and no ResidualInfo; the appointment
     * template's reason and notes already reach AppointmentPurpose/AppointmentNotes.
     */
    private static final Map<String, String> SCHEDULER_CONTEXT =
            Map.of(
                    "style", "SCHEDULER_DISPLAY_STYLE_NOT_A_CDS_APPOINTMENT_FIELD",
                    "billing", "BILLING_WORKFLOW_STATE_NOT_A_CDS_APPOINTMENT_FIELD",
                    "location", "BOOKING_SITE_NOT_A_CDS_APPOINTMENT_FIELD",
                    "resources", "SCHEDULER_RESOURCE_TEXT_NOT_A_CDS_APPOINTMENT_FIELD",
                    "urgency", "SCHEDULER_URGENCY_FLAG_NOT_A_CDS_APPOINTMENT_FIELD",
                    "type", "APPOINTMENT_TEMPLATE_NAME_NOT_A_CDS_APPOINTMENT_FIELD",
                    "bookingSource", "BOOKING_PROVENANCE_NOT_A_CDS_APPOINTMENT_FIELD");

    public CdsExportAppointmentMappingDto map(
            int patient,
            CdsExportCapturedTextRowDto row,
            CdsExportAppointmentLookupsDto lookups,
            ZoneId clinicalZone)
            throws IOException {
        var result = candidate(patient, row, lookups, clinicalZone);
        return complete(row, lookups, result);
    }

    public CdsExportAppointmentMappingDto map(
            int patient,
            CdsExportCapturedTextRowDto row,
            CdsExportAppointmentLookupsDto lookups,
            ZoneId clinicalZone,
            CdsExportCapturedTextRowDto demographic)
            throws IOException {
        var result = candidate(patient, row, lookups, clinicalZone, demographic);
        return complete(row, lookups, result);
    }

    /**
     * A native appointment with the candidate's labelled lines appended to its notes. A booking
     * whose notes cannot take them (AppointmentNotes holds 32,000 characters) still requires
     * review: the category has no other carrier.
     */
    private static CdsExportAppointmentMappingDto complete(
            CdsExportCapturedTextRowDto row,
            CdsExportAppointmentLookupsDto lookups,
            Candidate result) {
        var fields = new LinkedHashMap<>(result.fields());
        var value = result.value();
        var codeKey = new CdsExportNativeFieldKeyDto(row.table(), row.recordKey(), "reasonCode");
        var reasonKey = new CdsExportNativeFieldKeyDto(row.table(), row.recordKey(), "reason");
        var code = fields.get(codeKey);
        if (value != null
                && code.outcome() == Outcome.REQUIRES_REVIEW
                && "LABELLED_PURPOSE_REQUIRES_REVIEW".equals(code.rule())) {
            // The reason-list label and the reason together exceed AppointmentPurpose's 250
            // characters. Neither is cut, as for an imported purpose: the reason stays the
            // purpose and the label is the first line of AppointmentNotes ("Reason: <label>").
            var items = lookups.reasonCodes().getOrDefault(row.fields().get("reasonCode"), List.of());
            String label = items.size() == 1 ? items.getFirst().fields().get("label") : null;
            String reason = row.fields().get("reason");
            if (label != null
                    && !label.isBlank()
                    && CdsExportNativeFieldAssessments.legal(label, Integer.MAX_VALUE)
                    && (reason == null || CdsExportNativeFieldAssessments.legal(reason, 250))) {
                String line = "Reason: " + label.trim();
                String notes = value.notes();
                value =
                        new CdsExportAppointmentDto(
                                value.timing(),
                                value.status(),
                                value.provider(),
                                reason,
                                notes == null || notes.isEmpty() ? line : line + "\n" + notes);
                fields.put(
                        codeKey,
                        new CdsExportFieldAssessmentDto(
                                code.presence(),
                                Outcome.MAPPED,
                                "AppointmentNotes",
                                CdsExportAppointmentFieldReconciler.LABEL_IN_NOTES));
                var stored = fields.get(reasonKey);
                if (stored.outcome() == Outcome.REQUIRES_REVIEW)
                    fields.put(
                            reasonKey,
                            new CdsExportFieldAssessmentDto(
                                    stored.presence(),
                                    Outcome.MAPPED,
                                    "AppointmentPurpose",
                                    "EXACT_NATIVE_TEXT"));
            }
        }
        if (value != null && !result.noteLines().isEmpty())
            value =
                    new CdsExportAppointmentDto(
                            value.timing(),
                            value.status(),
                            value.provider(),
                            value.purpose(),
                            withLines(value.notes(), result.noteLines()));
        if (value != null
                && value.notes() != null
                && !CdsExportNativeFieldAssessments.legal(value.notes(), 32000)) {
            // Text no XML document can hold, or notes beyond the schema's 32,000 characters:
            // decided by the caller (see notesOverflow).
            var notesKey = new CdsExportNativeFieldKeyDto(row.table(), row.recordKey(), "notes");
            var prior = fields.get(notesKey);
            fields.put(
                    notesKey,
                    new CdsExportFieldAssessmentDto(
                            prior.presence(),
                            Outcome.REQUIRES_REVIEW,
                            "AppointmentNotes",
                            "APPOINTMENT_NOTES_CAPACITY_REQUIRES_REVIEW"));
        }
        boolean blocked =
                fields.values().stream()
                        .anyMatch(field -> field.outcome() == Outcome.REQUIRES_REVIEW);
        return new CdsExportAppointmentMappingDto(blocked ? null : value, fields);
    }

    /** The notes followed by each labelled line on its own line. */
    static String withLines(String notes, List<String> lines) {
        var text = new StringBuilder(notes == null ? "" : notes);
        for (String line : lines) {
            if (!text.isEmpty()) text.append('\n');
            text.append(line);
        }
        return text.toString();
    }

    /**
     * @param noteLines labelled lines the caller appends to AppointmentNotes
     *     ({@link CdsExportAppointmentNativeMetadata#qualify}); {@code value}'s notes are the
     *     stored notes alone
     */
    record Candidate(
            CdsExportAppointmentDto value,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields,
            List<String> noteLines) {
        Candidate {
            fields = Map.copyOf(fields);
            noteLines = List.copyOf(noteLines);
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
                    timing.offset() == null
                            ? LOCAL_TIME_RULE
                            : "EXACT_NATIVE_APPOINTMENT_CLOCK");
            source.set(
                    "end_time", Outcome.MAPPED, "Duration", "EXACT_SAME_DAY_WHOLE_MINUTE_DURATION");
        } catch (IOException review) {
            for (String column : List.of("appointment_date", "start_time", "end_time"))
                source.review(
                        column,
                        "AppointmentDate,AppointmentTime,Duration",
                        "APPOINTMENT_TIMING_REQUIRES_REVIEW");
        }
        String purpose = purpose(source, lookups, decisions);
        String notes = source.text("notes", "AppointmentNotes", 32000);
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
        for (var column : SCHEDULER_CONTEXT.entrySet()) {
            String value = source.raw(column.getKey());
            if (value != null && !value.isEmpty())
                source.operational(column.getKey(), column.getValue());
        }
        var noteLines =
                CdsExportAppointmentNativeMetadata.qualify(
                        row, demographic, lookups.earlierPatientDisplays(), decisions);
        return new Candidate(
                timing == null
                        ? null
                        : new CdsExportAppointmentDto(timing, status, provider, purpose, notes),
                decisions,
                noteLines);
    }

    private static String status(
            CdsExportNativeFieldAssessments source,
            CdsExportAppointmentLookupsDto lookups,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> decisions)
            throws IOException {
        String code = source.raw("status");
        if (code == null || code.isEmpty()) return null;
        // Signing or verifying an encounter appends S or V to the appointment's status (H becomes
        // HS); the chart shows the first letter's status with "/Signed" or "/Verified"
        // (ApptStatusData). Every other code needs its own exact definition.
        String mark = "";
        if (code.length() == 2 && (code.endsWith("S") || code.endsWith("V"))) {
            mark = code.substring(1);
            code = code.substring(0, 1);
        }
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
        if (mark.isEmpty()) {
            source.set(
                    "status",
                    Outcome.MAPPED,
                    "AppointmentStatus",
                    "EXACT_CAPTURED_STATUS_DEFINITION");
            return label;
        }
        String marked = label + (mark.equals("S") ? "/Signed" : "/Verified");
        if (marked.codePointCount(0, marked.length()) > 250) {
            source.review("status", "AppointmentStatus", "EXACT_STATUS_DEFINITION_REQUIRED");
            return null;
        }
        source.set(
                "status",
                Outcome.MAPPED,
                "AppointmentStatus",
                "CAPTURED_STATUS_DEFINITION_WITH_SIGNED_OR_VERIFIED_MARK");
        return marked;
    }

    /**
     * AppointmentPurpose is what the day sheet shows: the trimmed label of the referenced
     * {@code reasonCode} list item, a space and the trimmed reason
     * ({@code appointmentprovideradminday.jsp}). When the code names no captured list item (the
     * add form's "-1" placeholder when no list exists, or an id outside the reasonCode list) or
     * the item's label is blank, the schedule shows the reason alone, so the exact reason is sent.
     */
    private static String purpose(
            CdsExportNativeFieldAssessments source,
            CdsExportAppointmentLookupsDto lookups,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> decisions)
            throws IOException {
        String reason = source.raw("reason");
        String code = source.raw("reasonCode");
        String label = "";
        if (code != null && !code.isEmpty()) {
            var candidates = lookups.reasonCodes().getOrDefault(code, List.of());
            if (candidates.size() > 1) {
                source.review(
                        "reasonCode", "AppointmentPurpose", "EXACT_REASON_CODE_ITEM_REQUIRED");
                return null;
            }
            if (candidates.isEmpty())
                source.operational(
                        "reasonCode",
                        "-1".equals(code)
                                ? "NO_REASON_LIST_PLACEHOLDER_SCHEDULE_SHOWS_REASON_ONLY"
                                : "REASON_CODE_OUTSIDE_REASON_LIST_SCHEDULE_SHOWS_REASON_ONLY");
            else {
                label = reasonLabel(source, code, candidates.getFirst(), decisions);
                if (label == null) return null;
            }
        }
        if (label.isEmpty()) {
            String exact = source.text("reason", "AppointmentPurpose", 250);
            if ("".equals(reason)) {
                source.set(
                        "reason", Outcome.MAPPED, "AppointmentPurpose", "EXACT_NATIVE_EMPTY_TEXT");
                return "";
            }
            return exact;
        }
        String purpose = (label + " " + (reason == null ? "" : reason.trim())).trim();
        if (!CdsExportNativeFieldAssessments.legal(purpose, 250)) {
            source.review("reasonCode", "AppointmentPurpose", "LABELLED_PURPOSE_REQUIRES_REVIEW");
            if (reason != null && !reason.isEmpty())
                source.review("reason", "AppointmentPurpose", "LABELLED_PURPOSE_REQUIRES_REVIEW");
            return null;
        }
        source.set(
                "reasonCode",
                Outcome.MAPPED,
                "AppointmentPurpose",
                "EXACT_CAPTURED_REASON_CODE_LABEL");
        if (reason != null)
            source.set(
                    "reason",
                    Outcome.MAPPED,
                    "AppointmentPurpose",
                    "REASON_TEXT_AFTER_SCHEDULE_REASON_LABEL");
        return purpose;
    }

    /** @return the trimmed label, "" when the item's label is blank, or null after a review */
    private static String reasonLabel(
            CdsExportNativeFieldAssessments source,
            String code,
            CdsExportCapturedTextRowDto row,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> decisions)
            throws IOException {
        if (!"LookupListItem".equals(row.table())
                || !row.fields()
                        .keySet()
                        .equals(
                                Set.copyOf(
                                        CdsExportPatientTableReadSupport.Plan
                                                .APPOINTMENT_REASON_CODES
                                                .columns()))
                || !code.equals(row.fields().get("id"))) scope();
        try {
            if (!CdsExportPatientTableSourceReader.key(Integer.parseInt(code))
                    .equals(row.recordKey())) scope();
        } catch (NumberFormatException failure) {
            scope();
        }
        var item = new CdsExportNativeFieldAssessments(row, decisions);
        item.operational("id", "CAPTURED_REASON_CODE_ITEM_IDENTITY");
        item.operational("lookupListId", "CAPTURED_REASON_LIST_MEMBERSHIP");
        item.operational("value", "REASON_CODE_ITEM_VALUE_NOT_SHOWN_ON_SCHEDULE");
        for (String column : List.of("active", "displayOrder", "createdBy", "dateCreated"))
            item.operational(column, "CAPTURED_REASON_LIST_CONFIGURATION");
        String label = row.fields().get("label");
        if (label == null) {
            // The day sheet dereferences the label; a labelless item is not a reviewed display.
            item.review("label", "AppointmentPurpose", "REASON_CODE_ITEM_HAS_NO_LABEL");
            source.review("reasonCode", "AppointmentPurpose", "REASON_CODE_ITEM_HAS_NO_LABEL");
            return null;
        }
        if (label.isBlank()) {
            if (!label.isEmpty()) item.operational("label", "BLANK_REASON_LABEL_NOT_DISPLAYED");
            source.operational(
                    "reasonCode", "REASON_CODE_LABEL_IS_BLANK_SCHEDULE_SHOWS_REASON_ONLY");
            return "";
        }
        item.set("label", Outcome.MAPPED, "AppointmentPurpose", "SCHEDULE_REASON_LABEL");
        return label.trim();
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
