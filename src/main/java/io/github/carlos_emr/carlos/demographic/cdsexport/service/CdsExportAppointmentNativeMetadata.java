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

import io.github.carlos_emr.carlos.commn.dto.CdsExportDemographicRowDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Outcome;
import java.io.IOException;
import java.util.*;

/** Storage audit and exact same-patient display text; this never grants patient or program access. */
final class CdsExportAppointmentNativeMetadata {
    private CdsExportAppointmentNativeMetadata() {}

    static void qualify(
            CdsExportCapturedTextRowDto row,
            CdsExportCapturedTextRowDto demographic,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields)
            throws IOException {
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
        if (!Objects.equals(
                name,
                demographic.fields().get("last_name")
                        + ","
                        + demographic.fields().get("first_name")))
            throw new io.github.carlos_emr.carlos.demographic.cdsexport.support
                    .CdsExportMappingReviewException(
                    "CDS_EXPORT_APPOINTMENT_PATIENT_DISPLAY_REQUIRES_REVIEW");
        mark(
                row,
                fields,
                "name",
                Outcome.OPERATIONAL,
                "EXACT_CURRENT_PATIENT_DISPLAY_NOT_APPOINTMENT_NOTE");
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
