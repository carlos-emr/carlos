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
 * The schedule's date and start clock, in the documented clinical zone, with the slot's length in
 * whole minutes. {@code durationMinutes} is 0 when the stored end states no length that CDS
 * {@code Duration} (a positive integer) can hold; the element is then left out and the exact
 * stored end travels in {@code CARLOS.Appointment.Source.v1}. A slot that ends on the next day
 * can reach 1,440 minutes.
 */
public record CdsExportAppointmentTimingDto(CdsExportDateDto date, String time, int durationMinutes, java.time.ZoneOffset offset) {
    public CdsExportAppointmentTimingDto(CdsExportDateDto date, String time, int durationMinutes){this(date, time, durationMinutes, null); }
    public String wireTime(){return offset==null?time:time+offset.getId(); }
    public CdsExportAppointmentTimingDto {
        if (date == null || date.precision() != CdsExportDateDto.Precision.DATE || time == null
                || !time.matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]") || durationMinutes < 0 || durationMinutes > 1440) {
            throw new IllegalArgumentException("CDS_EXPORT_APPOINTMENT_TIMING_INVALID");
        }
    }
    @Override public String toString() { return "CdsExportAppointmentTimingDto[protected]"; }
}
