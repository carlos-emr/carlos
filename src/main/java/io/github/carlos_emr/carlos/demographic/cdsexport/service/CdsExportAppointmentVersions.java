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
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedTextRowDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportNativeFieldKeyDto;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Version state of an {@code appointmentArchive} row. CARLOS copies the whole appointment into
 * {@code appointmentArchive} before every edit, status change, billing and deletion
 * ({@code AppointmentUpdateRecord2Action}, {@code EChartRevisionPersister},
 * {@code BillingOnLookupService}, {@code AppointmentDeleteRecord2Action} and others), so an
 * ordinary chart gathers archive rows as soon as a patient is marked here or billed. An archive
 * row is a superseded version of the current appointment with the same number, or the last
 * version of an appointment that was deleted or moved to another chart. Neither is a booking:
 * the current row is exported and a deleted appointment is not, as the day sheet and the
 * default appointment history show it. Every captured field of the version still receives an
 * explicit not-exported disposition. Retained import reconciliation (a deleted or aliased
 * imported appointment) is decided separately and still requires review.
 */
final class CdsExportAppointmentVersions {
    static final String SUPERSEDED = "SUPERSEDED_APPOINTMENT_VERSION";
    static final String REMOVED = "DELETED_OR_MOVED_APPOINTMENT_VERSION";

    private CdsExportAppointmentVersions() {}

    /**
     * @param current appointment numbers of this patient's captured current appointment rows
     * @return one OPERATIONAL disposition for every captured field of the archive row
     */
    static Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> dispositions(
            int patient, CdsExportCapturedTextRowDto row, Set<String> current) throws IOException {
        if (patient < 1
                || row == null
                || current == null
                || !"appointmentArchive".equals(row.table())
                || !row.exactNumbers().isEmpty()
                || !row.fields()
                        .keySet()
                        .equals(
                                Set.copyOf(
                                        CdsExportPatientTableReadSupport.Plan.APPOINTMENT_ARCHIVE
                                                .columns()))
                || !Integer.toString(patient).equals(row.fields().get("demographic_no"))) scope();
        try {
            String id = row.fields().get("id");
            if (id == null
                    || !CdsExportPatientTableSourceReader.key(Long.parseLong(id))
                            .equals(row.recordKey())) scope();
        } catch (NumberFormatException failure) {
            scope();
        }
        String number = row.fields().get("appointment_no");
        String rule = number != null && current.contains(number) ? SUPERSEDED : REMOVED;
        var result = new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        var fields = new CdsExportNativeFieldAssessments(row, result);
        for (String column : row.fields().keySet()) fields.operational(column, rule);
        return result;
    }

    private static void scope() throws IOException {
        throw new IOException("CDS_EXPORT_APPOINTMENT_VERSION_SCOPE_INVALID");
    }
}
