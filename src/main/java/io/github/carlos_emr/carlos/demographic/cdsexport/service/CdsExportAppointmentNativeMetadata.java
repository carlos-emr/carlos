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

    static void qualify(
            CdsExportCapturedTextRowDto row,
            CdsExportCapturedTextRowDto demographic,
            Set<String> earlierDisplays,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields)
            throws IOException {
        Objects.requireNonNull(earlierDisplays);
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
            mark(
                    row,
                    fields,
                    "program_id",
                    Outcome.REQUIRES_REVIEW,
                    "PROGRAM_AFFILIATION_REQUIRES_REVIEW");
        String name = row.fields().get("name");
        if (name == null || name.isEmpty()) return;
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
        String rule;
        if (displays(demographic.fields()).contains(name))
            rule = "EXACT_CURRENT_PATIENT_DISPLAY_NOT_APPOINTMENT_NOTE";
        else if (earlierDisplays.contains(name))
            rule = "EXACT_EARLIER_PATIENT_DISPLAY_FROM_CAPTURED_ARCHIVE_NOT_APPOINTMENT_NOTE";
        else
            throw new io.github.carlos_emr.carlos.demographic.cdsexport.support
                    .CdsExportMappingReviewException(
                    "CDS_EXPORT_APPOINTMENT_PATIENT_DISPLAY_REQUIRES_REVIEW");
        mark(row, fields, "name", Outcome.OPERATIONAL, rule);
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
        var key = new CdsExportNativeFieldKeyDto(row.table(), row.recordKey(), column);
        var old = fields.get(key);
        if (old == null) throw new IOException("CDS_EXPORT_APPOINTMENT_METADATA_SCOPE_INVALID");
        fields.put(key, new CdsExportFieldAssessmentDto(old.presence(), outcome, "", rule));
    }
}
