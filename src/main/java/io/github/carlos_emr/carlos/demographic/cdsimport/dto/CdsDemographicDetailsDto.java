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

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/** Bounded canonical demographic details. Complete enrollment event history stays separately preserved. */
public record CdsDemographicDetailsDto(
        String spokenLanguage,
        String statusDate,
        String healthCardExpiry,
        String patientNote,
        Enrollment roster,
        long enrollmentHistoryCount,
        List<CdsClinicalDiagnosticDto> diagnostics) {
    public CdsDemographicDetailsDto {
        bounded(spokenLanguage, 60);
        date(statusDate);
        date(healthCardExpiry);
        if (patientNote != null && patientNote.getBytes(StandardCharsets.UTF_8).length > 65518)
            throw new IllegalArgumentException("DEMOGRAPHIC_PATIENT_NOTE_NATIVE_CAPACITY");
        if (enrollmentHistoryCount < 0)
            throw new IllegalArgumentException("DEMOGRAPHIC_ENROLMENT_COUNT_INVALID");
        diagnostics = List.copyOf(diagnostics);
    }

    public static CdsDemographicDetailsDto empty() {
        return new CdsDemographicDetailsDto(null, null, null, null, null, 0, List.of());
    }

    public record Enrollment(
            String status,
            String date,
            String terminationDate,
            String terminationReason,
            String enrolledProvider) {
        public Enrollment {
            bounded(status, 20);
            CdsDemographicDetailsDto.date(date);
            CdsDemographicDetailsDto.date(terminationDate);
            bounded(terminationReason, 2);
            bounded(enrolledProvider, 20);
        }

        @Override
        public String toString() {
            return "CdsCurrentEnrollment[protected]";
        }
    }

    private static void date(String value) {
        if (value != null) {
            var date = LocalDate.parse(value);
            if (date.getYear() < 1 || date.getYear() > 9999)
                throw new IllegalArgumentException("DEMOGRAPHIC_NATIVE_DATE_RANGE");
        }
    }

    private static void bounded(String value, int maximum) {
        if (value != null && value.codePointCount(0, value.length()) > maximum)
            throw new IllegalArgumentException("DEMOGRAPHIC_DETAIL_TEXT_CAPACITY");
    }

    @Override
    public String toString() {
        return "CdsDemographicDetailsDto[protected]";
    }
}
