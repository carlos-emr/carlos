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
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsAppointmentSourceContract;
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
 * status is never a fallback for a changed/cleared local status.
 *
 * <p>CDS 5.2 Appointments has no ResidualInfo, so every booking also has one NewCategory record,
 * {@code CARLOS.Appointment.Source.v1} ({@link CdsExportAppointmentSourceMapper}), that holds the
 * complete stored row. A value the element cannot hold exactly is written in its schema-valid
 * form by a named rule and is whole in that record: notes past 32,000 characters are cut and
 * marked ({@link #SHORTENED_MARK}); characters no XML document can hold are left out of the
 * element text; a status code with no usable definition is written as the stored code; a
 * provider number with no provider record, or whose record cannot form a CDS Provider, is
 * exported without Provider; scheduler context (style, billing, location, resources, urgency,
 * type, booking source, program) has no element at all. No such value holds the chart. The
 * caller must reconcile retained imported facts, superseded archive versions and complete source
 * coverage before publication.
 */
public final class CdsExportAppointmentMapper {
    /**
     * Rule of a start time written as the schedule's wall-clock time with no offset, because it
     * falls in, or the appointment spans, a daylight-saving transition of the clinical zone.
     */
    static final String LOCAL_TIME_RULE = "LOCAL_WALL_CLOCK_TIME_AT_DAYLIGHT_SAVING_TRANSITION";

    /** Destination of a value held by the appointment's NewCategory source record. */
    static final String CARRIER = CdsExportDemographicSupportingMapper.DESTINATION;

    /** Rule of the stored date or start that is not a calendar date or time of day (a hold). */
    static final String NOT_A_CALENDAR_TIME_RULE = "APPOINTMENT_START_NOT_A_CALENDAR_TIME";

    /** AppointmentNotes holds 32,000 characters (CDS 5.2 schema). */
    static final int NOTES_MAXIMUM = CdsAppointmentSourceContract.NOTES_MAXIMUM;

    /**
     * Ends AppointmentNotes when the complete text is longer than the element: the element holds
     * the first characters that fit before this mark, the source record the complete text. The
     * importer recomputes the same rule ({@link CdsAppointmentSourceContract#notesElement}).
     */
    public static final String SHORTENED_MARK = CdsAppointmentSourceContract.SHORTENED_MARK;

    static final String SHORTENED_RULE =
            "NOTES_CUT_AT_CDS_LIMIT_COMPLETE_TEXT_IN_APPOINTMENT_SOURCE_RECORD";
    static final String CUT_RULE =
            "TEXT_CUT_AT_CDS_LIMIT_COMPLETE_TEXT_IN_APPOINTMENT_SOURCE_RECORD";
    static final String CLEANED_RULE =
            "XML_ILLEGAL_CHARACTERS_LEFT_OUT_EXACT_VALUE_IN_APPOINTMENT_SOURCE_RECORD";
    static final String CONTEXT_RULE = "NO_CDS_APPOINTMENT_FIELD_IN_APPOINTMENT_SOURCE_RECORD";

    /**
     * Scheduler, site, billing-workflow and booking-provenance columns CARLOS writes with every
     * booking. CDS 5.2 Appointments has no field for them; they travel in the appointment's
     * source record. The appointment template's reason and notes already reach
     * AppointmentPurpose/AppointmentNotes.
     */
    private static final List<String> SCHEDULER_CONTEXT =
            List.of("style", "billing", "location", "resources", "urgency", "type", "bookingSource");

    /**
     * An element's schema-valid text and, when that is not the complete value, the complete
     * value and the rule that produced the element text.
     */
    record Fitted(String element, String complete, String rule) {
        @Override
        public String toString() {
            return "CdsExportAppointmentMapper.Fitted[protected]";
        }
    }

    /** The text without the code points no XML 1.0 document can hold. */
    static String xmlText(String value) {
        return CdsAppointmentSourceContract.xmlText(value);
    }

    /**
     * AppointmentNotes for the complete notes text: the text itself when the element can hold it,
     * otherwise without XML-illegal characters and, past 32,000 characters, its first characters
     * followed by {@link #SHORTENED_MARK}.
     */
    static Fitted fitNotes(String complete) {
        if (complete == null) return new Fitted(null, null, null);
        String text = CdsAppointmentSourceContract.notesElement(complete);
        if (text.equals(complete)) return new Fitted(text, null, null);
        String legal = xmlText(complete);
        return new Fitted(
                text,
                complete,
                legal.codePointCount(0, legal.length()) > NOTES_MAXIMUM
                        ? SHORTENED_RULE
                        : CLEANED_RULE);
    }

    /** A 250-character element: without XML-illegal characters, cut at 250 when longer. */
    static Fitted fitShort(String complete) {
        if (complete == null) return new Fitted(null, null, null);
        String text = xmlText(complete);
        boolean cut = text.codePointCount(0, text.length()) > 250;
        if (cut) text = text.substring(0, text.offsetByCodePoints(0, 250));
        return text.equals(complete)
                ? new Fitted(text, null, null)
                : new Fitted(text, complete, cut ? CUT_RULE : CLEANED_RULE);
    }

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
     * A native appointment with the candidate's labelled lines appended to its notes. Notes the
     * element cannot hold whole are written by {@link #fitNotes}; the complete text is returned
     * for the appointment's source record.
     */
    private static CdsExportAppointmentMappingDto complete(
            CdsExportCapturedTextRowDto row,
            CdsExportAppointmentLookupsDto lookups,
            Candidate result) {
        var fields = new LinkedHashMap<>(result.fields());
        var complete = new LinkedHashMap<>(result.complete());
        var value = result.value();
        String purpose = value == null ? null : value.purpose();
        String notes = result.notes();
        var codeKey = new CdsExportNativeFieldKeyDto(row.table(), row.recordKey(), "reasonCode");
        var reasonKey = new CdsExportNativeFieldKeyDto(row.table(), row.recordKey(), "reason");
        var code = fields.get(codeKey);
        if (code.outcome() == Outcome.REQUIRES_REVIEW
                && "LABELLED_PURPOSE_REQUIRES_REVIEW".equals(code.rule())) {
            // The reason-list label and the reason together exceed AppointmentPurpose's 250
            // characters. Neither is cut, as for an imported purpose: the reason stays the
            // purpose and the label is the first line of AppointmentNotes ("Reason: <label>").
            var items = lookups.reasonCodes().getOrDefault(row.fields().get("reasonCode"), List.of());
            String label = items.size() == 1 ? items.getFirst().fields().get("label") : null;
            if (label != null && !label.isBlank()) {
                String line = "Reason: " + label.trim();
                notes = notes == null || notes.isEmpty() ? line : line + "\n" + notes;
                var reason = fitShort(row.fields().get("reason"));
                purpose = reason.element();
                if (reason.complete() != null) complete.put("AppointmentPurpose", reason.complete());
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
                            reason.complete() == null
                                    ? new CdsExportFieldAssessmentDto(
                                            stored.presence(),
                                            Outcome.MAPPED,
                                            "AppointmentPurpose",
                                            "EXACT_NATIVE_TEXT")
                                    : new CdsExportFieldAssessmentDto(
                                            stored.presence(),
                                            Outcome.MAPPED,
                                            "AppointmentPurpose," + CARRIER,
                                            reason.rule()));
            }
        }
        var fitted =
                fitNotes(result.noteLines().isEmpty() ? notes : withLines(notes, result.noteLines()));
        if (fitted.complete() != null) {
            complete.put("AppointmentNotes", fitted.complete());
            var notesKey = new CdsExportNativeFieldKeyDto(row.table(), row.recordKey(), "notes");
            fields.put(
                    notesKey,
                    new CdsExportFieldAssessmentDto(
                            fields.get(notesKey).presence(),
                            Outcome.MAPPED,
                            "AppointmentNotes," + CARRIER,
                            fitted.rule()));
        }
        boolean blocked =
                value == null
                        || fields.values().stream()
                                .anyMatch(field -> field.outcome() == Outcome.REQUIRES_REVIEW);
        return new CdsExportAppointmentMappingDto(
                blocked
                        ? null
                        : new CdsExportAppointmentDto(
                                value.timing(),
                                value.status(),
                                value.provider(),
                                purpose,
                                fitted.element()),
                fields,
                complete);
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
     * @param value the timing, status, provider and purpose; its notes are always null
     * @param noteLines labelled lines the caller appends to AppointmentNotes
     *     ({@link CdsExportAppointmentNativeMetadata#qualify})
     * @param notes the stored notes alone, exactly as stored and of any length; the caller
     *     composes AppointmentNotes and fits it with {@link #fitNotes}
     * @param complete the complete value of each element whose text is only its schema-valid form
     */
    record Candidate(
            CdsExportAppointmentDto value,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields,
            List<String> noteLines,
            String notes,
            Map<String, String> complete) {
        Candidate {
            fields = Map.copyOf(fields);
            noteLines = List.copyOf(noteLines);
            complete = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(complete));
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
        var complete = new LinkedHashMap<String, String>();
        var source = new CdsExportNativeFieldAssessments(row, decisions);
        source.operational("appointment_no", "NATIVE_OCCURRENCE_IDENTITY");
        source.operational("demographic_no", "SEALED_PATIENT_IDENTITY");
        source.operational("updatedatetime", "CAPTURED_MODIFICATION_CLOCK_NOT_APPOINTMENT_DATE");
        CdsExportAppointmentTimingDto timing = null;
        try {
            var planned =
                    new CdsExportAppointmentTimingMapper()
                            .plan(
                                    source.raw("appointment_date"),
                                    source.raw("start_time"),
                                    source.raw("end_time"),
                                    clinicalZone,
                                    endConvention);
            timing = planned.value();
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
            switch (planned.length()) {
                case EXACT -> source.set("end_time", Outcome.MAPPED, "Duration", planned.length().rule());
                case PARTIAL_MINUTE, NEXT_DAY ->
                        source.set(
                                "end_time",
                                Outcome.MAPPED,
                                "Duration," + CARRIER,
                                planned.length().rule());
                default -> source.set("end_time", Outcome.MAPPED, CARRIER, planned.length().rule());
            }
        } catch (io.github.carlos_emr.carlos.demographic.cdsexport.support
                        .CdsExportMappingReviewException
                impossible) {
            // Not a calendar date or a time of day at all: AppointmentDate and AppointmentTime
            // are required, so nothing could be written. The one remaining timing hold.
            for (String column : List.of("appointment_date", "start_time", "end_time"))
                source.review(
                        column, "AppointmentDate,AppointmentTime,Duration", NOT_A_CALENDAR_TIME_RULE);
        }
        String purpose = purpose(source, lookups, decisions, complete);
        String notes = source.raw("notes");
        if (notes != null)
            source.set(
                    "notes",
                    Outcome.MAPPED,
                    "AppointmentNotes",
                    notes.isEmpty() ? "EXACT_NATIVE_EMPTY_TEXT" : "EXACT_NATIVE_TEXT");
        String status = status(source, lookups, decisions);
        var provider = provider(source, lookups, decisions);
        if (source.raw("imported_status") != null && !source.raw("imported_status").isEmpty()) {
            source.review(
                    "imported_status",
                    "AppointmentStatus",
                    "RETAINED_APPOINTMENT_STATUS_REQUIRES_RECONCILIATION");
        }
        for (String column : SCHEDULER_CONTEXT) {
            String value = source.raw(column);
            if (value != null && !value.isEmpty())
                source.set(column, Outcome.MAPPED, CARRIER, CONTEXT_RULE);
        }
        var noteLines =
                CdsExportAppointmentNativeMetadata.qualify(
                        row, demographic, lookups.earlierPatientDisplays(), decisions);
        return new Candidate(
                timing == null
                        ? null
                        : new CdsExportAppointmentDto(timing, status, provider, purpose, null),
                decisions,
                noteLines,
                notes,
                complete);
    }

    /**
     * AppointmentStatus is the label the schedule shows for the stored code. A code that has no
     * single definition, or whose definition has no label CDS text can hold, is written as the
     * stored code itself; the code and its captured definitions are in the source record.
     */
    private static String status(
            CdsExportNativeFieldAssessments source,
            CdsExportAppointmentLookupsDto lookups,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> decisions)
            throws IOException {
        String stored = source.raw("status");
        if (stored == null || stored.isEmpty()) return null;
        String code = stored;
        // Signing or verifying an encounter appends S or V to the appointment's status (H becomes
        // HS); the chart shows the first letter's status with "/Signed" or "/Verified"
        // (ApptStatusData). Every other code needs its own exact definition.
        String mark = "";
        if (code.length() == 2 && (code.endsWith("S") || code.endsWith("V"))) {
            mark = code.substring(1);
            code = code.substring(0, 1);
        }
        var candidates = lookups.statuses().getOrDefault(code, List.of());
        if (candidates.size() != 1)
            return storedCode(source, stored, "STATUS_CODE_WITHOUT_SINGLE_DEFINITION_WRITTEN_AS_STORED");
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
        String label = lookup.raw("description");
        String marked =
                label == null ? null : label + (mark.isEmpty() ? "" : mark.equals("S") ? "/Signed" : "/Verified");
        if (label == null
                || label.isBlank()
                || !CdsExportNativeFieldAssessments.legal(marked, 250)) {
            if (label != null && !label.isEmpty())
                lookup.set(
                        "description",
                        Outcome.MAPPED,
                        CARRIER,
                        "STATUS_LABEL_NOT_USABLE_AS_CDS_TEXT_IN_APPOINTMENT_SOURCE_RECORD");
            return storedCode(
                    source, stored, "STATUS_DEFINITION_WITHOUT_USABLE_LABEL_WRITTEN_AS_STORED_CODE");
        }
        lookup.set("description", Outcome.MAPPED, "AppointmentStatus", "EXACT_NATIVE_TEXT");
        source.set(
                "status",
                Outcome.MAPPED,
                "AppointmentStatus",
                mark.isEmpty()
                        ? "EXACT_CAPTURED_STATUS_DEFINITION"
                        : "CAPTURED_STATUS_DEFINITION_WITH_SIGNED_OR_VERIFIED_MARK");
        return marked;
    }

    /** The stored status code as AppointmentStatus; a code of only XML-illegal text is left out. */
    private static String storedCode(
            CdsExportNativeFieldAssessments source, String stored, String rule) {
        String text = xmlText(stored);
        source.set(
                "status",
                Outcome.MAPPED,
                text.isBlank() ? CARRIER : "AppointmentStatus," + CARRIER,
                rule);
        return text.isBlank() ? null : text;
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
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> decisions,
            Map<String, String> complete)
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
            if (reason == null) return null;
            if (reason.isEmpty()) {
                source.set(
                        "reason", Outcome.MAPPED, "AppointmentPurpose", "EXACT_NATIVE_EMPTY_TEXT");
                return "";
            }
            var fitted = fitShort(reason);
            if (fitted.complete() == null)
                source.set("reason", Outcome.MAPPED, "AppointmentPurpose", "EXACT_NATIVE_TEXT");
            else {
                complete.put("AppointmentPurpose", fitted.complete());
                source.set(
                        "reason", Outcome.MAPPED, "AppointmentPurpose," + CARRIER, fitted.rule());
            }
            return fitted.element();
        }
        String purpose = (label + " " + (reason == null ? "" : reason.trim())).trim();
        String text = xmlText(purpose);
        if (text.codePointCount(0, text.length()) > 250) {
            // Resolved by the caller: the label becomes the first line of AppointmentNotes.
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
        if (!text.equals(purpose)) complete.put("AppointmentPurpose", purpose);
        if (reason != null)
            source.set(
                    "reason",
                    Outcome.MAPPED,
                    text.equals(purpose) ? "AppointmentPurpose" : "AppointmentPurpose," + CARRIER,
                    "REASON_TEXT_AFTER_SCHEDULE_REASON_LABEL");
        return text;
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
            // A provider number with no provider record (migrated data): exported without
            // Provider, as a prescription is without its prescriber; the stored number is in
            // the appointment's source record.
            source.set(
                    "provider_no",
                    Outcome.MAPPED,
                    CARRIER,
                    "PROVIDER_NUMBER_WITHOUT_PROVIDER_RECORD_IN_APPOINTMENT_SOURCE_RECORD");
            return null;
        }
        if (!"provider".equals(row.table())
                || !row.fields().keySet().equals(CdsExportProviderRowDto.COLUMNS)
                || !id.equals(row.fields().get("provider_no"))
                || !CdsExportPublicProviderSourceReader.key(id).equals(row.recordKey())) scope();
        var mapped =
                new CdsExportPublicProviderMapper()
                        .map(row, CdsExportPublicProviderMapper.Target.APPOINTMENT);
        if (!mapped.representable()) {
            // A provider record that cannot form a CDS Provider (a name part that is missing,
            // longer than the element or not XML text): no Provider element; the record's public
            // columns travel whole in the appointment's source record.
            for (var field : mapped.fields().entrySet())
                decisions.put(
                        field.getKey(),
                        field.getValue().outcome() == Outcome.MAPPED
                                        || field.getValue().outcome() == Outcome.REQUIRES_REVIEW
                                ? new CdsExportFieldAssessmentDto(
                                        field.getValue().presence(),
                                        Outcome.MAPPED,
                                        CARRIER,
                                        "PROVIDER_RECORD_NOT_A_CDS_PROVIDER_IN_APPOINTMENT_SOURCE_RECORD")
                                : field.getValue());
            source.set(
                    "provider_no",
                    Outcome.MAPPED,
                    CARRIER,
                    "PROVIDER_RECORD_NOT_A_CDS_PROVIDER_IN_APPOINTMENT_SOURCE_RECORD");
            return null;
        }
        decisions.putAll(mapped.fields());
        source.set("provider_no", Outcome.MAPPED, "Provider", "EXACT_CAPTURED_PUBLIC_PROVIDER");
        return mapped.value();
    }

    private static void scope() throws IOException {
        throw new IOException("CDS_EXPORT_APPOINTMENT_CAPTURE_SCOPE_INVALID");
    }
}
