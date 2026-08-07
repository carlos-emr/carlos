/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.appointment.dto;

import java.util.Date;

/**
 * Narrow projection of the fields required by the patient appointment export.
 * Keeping the cursor scalar avoids loading complete entities and triggering
 * relationship queries while a MySQL streaming result set is active.
 */
public record PatientAppointmentExportRow(
        String patientLastName,
        String patientFirstName,
        String phone,
        String alternatePhone,
        Date startTime,
        Date appointmentDate,
        String appointmentType,
        String providerFirstName,
        String providerLastName,
        String location) {

    public PatientAppointmentExportRow {
        startTime = copy(startTime);
        appointmentDate = copy(appointmentDate);
    }

    @Override
    public Date startTime() {
        return copy(startTime);
    }

    @Override
    public Date appointmentDate() {
        return copy(appointmentDate);
    }

    private static Date copy(Date value) {
        return value == null ? null : new Date(value.getTime());
    }
}
