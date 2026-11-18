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
package io.github.carlos_emr.carlos.demographic.cdsexport.dto;

/**
 * Schema values after source/current-field reconciliation; this value alone does not establish
 * appointment coverage. The bounds here are the schema's: AppointmentNotes (text32K) admits
 * 32,768 characters, which an imported note retained unchanged may use. Notes CARLOS writes keep
 * the data dictionary's 32,000 by the native rule (CdsExportAppointmentMapper.fitNotes), which
 * CdsExportAppointmentXmlWriter checks.
 */
public record CdsExportAppointmentDto(CdsExportAppointmentTimingDto timing, String status,
        CdsExportPublicProviderDto provider, String purpose, String notes) {
    public CdsExportAppointmentDto {
        if (timing == null || status != null && status.codePointCount(0, status.length()) > 250
                || purpose != null && purpose.codePointCount(0, purpose.length()) > 250
                || notes != null && notes.codePointCount(0, notes.length()) > 32768) {
            throw new IllegalArgumentException("CDS_EXPORT_APPOINTMENT_VALUES_REQUIRES_REVIEW");
        }
    }
    @Override public String toString() { return "CdsExportAppointmentDto[protected]"; }
}
