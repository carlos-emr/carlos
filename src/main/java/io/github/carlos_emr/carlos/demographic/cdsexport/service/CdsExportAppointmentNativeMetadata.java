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
import java.io.IOException;
import java.util.*;

/**
 * Storage audit and exact same-patient display text; this never grants patient or program access.
 *
 * <p>{@code appointment.name} is a copy of the patient's name made when the booking was saved,
 * not an appointment fact. The add form stores {@code last + "," + first}
 * ({@code AppointmentAddRecord2Action}); the edit form's patient search and the booking web
 * service store {@code Demographic.getFormattedName()}, {@code last + ", " + first} followed by
 * {@code " (" + alias + ")"} when an alias is recorded. A name in one of these exact forms of the
 * current patient, or of one of the patient's own earlier captured {@code demographicArchive}
 * snapshots (a booking made before a rename), is the patient's display text. Any other value,
 * including a case or spacing variant, stays a review gate.
 */
final class CdsExportAppointmentNativeMetadata {
    private CdsExportAppointmentNativeMetadata() {}

    /** Leads the booking's own name text in AppointmentNotes when it is not the patient's name. */
    static final String NAME_LABEL = "Booking name: ";

    /** Leads the appointment's remarks in AppointmentNotes. */
    static final String REMARKS_LABEL = "Remarks: ";

    static final String NAME_IN_NOTES = "BOOKING_NAME_NOT_THE_PATIENT_DISPLAY_AS_LABELLED_NOTES_LINE";
    static final String REMARKS_IN_NOTES = "APPOINTMENT_REMARKS_AS_LABELLED_NOTES_LINE";

    /**
     * Decides the storage, program, booking-name and remarks columns.
     *
     * @return the labelled lines to append to AppointmentNotes, in order: a booking name that is
     *     not one of the patient's own name forms (the Edit Appointment form stores whatever its
     *     name box holds, so a typed annotation is chart text), then the remarks (kept by the add
     *     and edit forms, filled by the SOAP schedule service and by OSCAR 19 data such as its
     *     reminder mailer's "Emailed:" marks, and shown in the appointment history). The
     *     Appointments category has no ResidualInfo, so these lines are their carrier.
     */
    static List<String> qualify(
            CdsExportCapturedTextRowDto row,
            CdsExportCapturedTextRowDto demographic,
            Set<String> earlierDisplays,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields)
            throws IOException {
        Objects.requireNonNull(earlierDisplays);
        var lines = new java.util.ArrayList<String>();
        for (String column :
                List.of("creator", "creatorSecurityId", "lastupdateuser", "createdatetime"))
            mark(
                    row,
                    fields,
                    column,
                    Outcome.OPERATIONAL,
                    "CAPTURED_STORAGE_AUDIT_NOT_APPOINTMENT_TIME");
        String program = row.fields().get("program_id");
        if (program == null || program.equals("0"))
            mark(row, fields, "program_id", Outcome.OPERATIONAL, "NO_RECORDED_PROGRAM_AFFILIATION");
        else
            // The program a booking was made under (OSCAR 19 with its program view on; CARLOS
            // always stores 0) is scheduler context like the booking site and template, which
            // have no CDS Appointments field either. It never holds a chart.
            mark(
                    row,
                    fields,
                    "program_id",
                    Outcome.OPERATIONAL,
                    "BOOKING_PROGRAM_NOT_A_CDS_APPOINTMENT_FIELD");
        String name = row.fields().get("name");
        if (name != null && !name.isEmpty()) name(row, demographic, earlierDisplays, fields, lines);
        String remarks = row.fields().get("remarks");
        if (remarks != null && !remarks.isBlank()) {
            if (CdsExportNativeFieldAssessments.legal(remarks, Integer.MAX_VALUE)) {
                lines.add(REMARKS_LABEL + remarks);
                mark(row, fields, "remarks", Outcome.MAPPED, "AppointmentNotes", REMARKS_IN_NOTES);
            } else
                mark(
                        row,
                        fields,
                        "remarks",
                        Outcome.REQUIRES_REVIEW,
                        "AppointmentNotes",
                        "WIRE_TEXT_REQUIRES_REVIEW");
        } else if (remarks != null && !remarks.isEmpty())
            mark(row, fields, "remarks", Outcome.OPERATIONAL, "BLANK_REMARKS_NOT_DISPLAYED");
        return List.copyOf(lines);
    }

    private static void name(
            CdsExportCapturedTextRowDto row,
            CdsExportCapturedTextRowDto demographic,
            Set<String> earlierDisplays,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields,
            List<String> lines)
            throws IOException {
        String name = row.fields().get("name");
        if (demographic == null) {
            mark(
                    row,
                    fields,
                    "name",
                    Outcome.REQUIRES_REVIEW,
                    "EXACT_CURRENT_PATIENT_DISPLAY_REQUIRED");
            return;
        }
        var columns = new HashSet<>(CdsExportDemographicRowDto.COLUMNS);
        columns.add("demographic_no");
        String owner = row.fields().get("demographic_no");
        if (!"demographic".equals(demographic.table())
                || !demographic.fields().keySet().equals(columns)
                || !Objects.equals(owner, demographic.fields().get("demographic_no"))
                || owner == null
                || !owner.matches("[1-9][0-9]*")
                || !demographic
                        .recordKey()
                        .equals(String.format(Locale.ROOT, "%010d", Integer.parseInt(owner))))
            throw new IOException("CDS_EXPORT_APPOINTMENT_PATIENT_DISPLAY_SCOPE_INVALID");
        if (displays(demographic.fields()).contains(name))
            mark(
                    row,
                    fields,
                    "name",
                    Outcome.OPERATIONAL,
                    "EXACT_CURRENT_PATIENT_DISPLAY_NOT_APPOINTMENT_NOTE");
        else if (earlierDisplays.contains(name))
            mark(
                    row,
                    fields,
                    "name",
                    Outcome.OPERATIONAL,
                    "EXACT_EARLIER_PATIENT_DISPLAY_FROM_CAPTURED_ARCHIVE_NOT_APPOINTMENT_NOTE");
        else if (name.isBlank())
            mark(row, fields, "name", Outcome.OPERATIONAL, "BLANK_BOOKING_NAME_NOT_DISPLAYED");
        else if (CdsExportNativeFieldAssessments.legal(name, Integer.MAX_VALUE)) {
            // Any other text is what the user left in the booking's name box: exported as a
            // labelled line of the notes instead of holding the chart.
            lines.add(NAME_LABEL + name);
            mark(row, fields, "name", Outcome.MAPPED, "AppointmentNotes", NAME_IN_NOTES);
        } else
            mark(
                    row,
                    fields,
                    "name",
                    Outcome.REQUIRES_REVIEW,
                    "AppointmentNotes",
                    "WIRE_TEXT_REQUIRES_REVIEW");
    }

    /**
     * The display names one of this patient's own captured {@code demographicArchive} snapshots
     * would have produced. Only the names are kept, so a long archive cannot exhaust the lookup
     * index; the row itself is assessed by the Demographics assembler.
     */
    static Set<String> archivedDisplays(int patient, CdsExportCapturedTextRowDto archive)
            throws IOException {
        if (patient < 1
                || archive == null
                || !"demographicArchive".equals(archive.table())
                || !archive.fields()
                        .keySet()
                        .equals(
                                Set.copyOf(
                                        CdsExportPatientTableReadSupport.Plan.DEMOGRAPHIC_ARCHIVE
                                                .columns()))
                || !Integer.toString(patient).equals(archive.fields().get("demographic_no")))
            throw new IOException("CDS_EXPORT_APPOINTMENT_PATIENT_DISPLAY_SCOPE_INVALID");
        try {
            String id = archive.fields().get("id");
            if (id == null
                    || !CdsExportPatientTableSourceReader.key(Long.parseLong(id))
                            .equals(archive.recordKey()))
                throw new IOException("CDS_EXPORT_APPOINTMENT_PATIENT_DISPLAY_SCOPE_INVALID");
        } catch (NumberFormatException failure) {
            throw new IOException("CDS_EXPORT_APPOINTMENT_PATIENT_DISPLAY_SCOPE_INVALID");
        }
        return displays(archive.fields());
    }

    /** Java string concatenation, as the writers use it: a missing name part reads "null". */
    private static Set<String> displays(Map<String, String> patient) {
        String last = patient.get("last_name"), first = patient.get("first_name");
        String alias = patient.get("alias");
        var result = new HashSet<String>();
        result.add(last + "," + first);
        result.add(last + ", " + first);
        if (alias != null && !alias.isEmpty()) result.add(last + ", " + first + " (" + alias + ")");
        return Set.copyOf(result);
    }

    private static void mark(
            CdsExportCapturedTextRowDto row,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields,
            String column,
            Outcome outcome,
            String rule)
            throws IOException {
        mark(row, fields, column, outcome, "", rule);
    }

    private static void mark(
            CdsExportCapturedTextRowDto row,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields,
            String column,
            Outcome outcome,
            String destination,
            String rule)
            throws IOException {
        var key = new CdsExportNativeFieldKeyDto(row.table(), row.recordKey(), column);
        var old = fields.get(key);
        if (old == null) throw new IOException("CDS_EXPORT_APPOINTMENT_METADATA_SCOPE_INVALID");
        fields.put(key, new CdsExportFieldAssessmentDto(old.presence(), outcome, destination, rule));
    }
}
