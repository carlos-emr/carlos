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
package io.github.carlos_emr.carlos.demographic.cdsimport.dto;

import java.util.List;

/** One bounded calendar projection; unrepresentable source timing remains a clinical category source record. */
public record CdsAppointmentNativeDto(
        String date,
        String start,
        String end,
        String status,
        String purpose,
        String notes,
        String providerNo,
        boolean representable,
        List<CdsClinicalDiagnosticDto> diagnostics) {
    public CdsAppointmentNativeDto {
        diagnostics = List.copyOf(diagnostics);
        bounded(status, 250);
        bounded(purpose, 80);
        bounded(notes, 32768);
        bounded(providerNo, 6);
        if (representable) {
            if (date == null || start == null || end == null)
                throw new IllegalArgumentException("APPOINTMENT_PREPARED_TIMING_MISSING");
            var day = java.time.LocalDate.parse(date);
            var begins = java.time.LocalTime.parse(start);
            var ends = java.time.LocalTime.parse(end);
            if (day.getYear() < 1
                    || day.getYear() > 9999
                    || begins.getNano() != 0
                    || ends.getNano() != 0
                    || !ends.isAfter(begins))
                throw new IllegalArgumentException("APPOINTMENT_PREPARED_TIMING_INVALID");
        }
    }

    private static void bounded(String value, int maximum) {
        if (value != null && value.codePointCount(0, value.length()) > maximum)
            throw new IllegalArgumentException("APPOINTMENT_PREPARED_TEXT_INVALID");
    }

    @Override
    public String toString() {
        return "CdsAppointmentNativeDto[protected]";
    }
}
