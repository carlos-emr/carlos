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
import java.util.Map;

/** Fixed-size prepared native projection; all repeated originals remain separate category-local source fields. */
public record CdsDemographicNativeDto(
        String sourceId,
        String firstName,
        String lastName,
        String birthDate,
        String healthNumber,
        String issuer,
        String gender,
        String status,
        Map<String, String> scalars,
        Address mailing,
        Address residential,
        Phone home,
        Phone work,
        Phone cell,
        String primaryProvider,
        CdsDemographicDetailsDto details,
        CdsDemographicRelationsDto relations,
        List<CdsClinicalDiagnosticDto> diagnostics) {
    public CdsDemographicNativeDto {
        scalars = Map.copyOf(scalars);
        diagnostics = List.copyOf(diagnostics);
        details = details == null ? CdsDemographicDetailsDto.empty() : details;
        relations = relations == null ? CdsDemographicRelationsDto.empty() : relations;
        if (firstName == null || lastName == null || birthDate == null || gender == null)
            throw new IllegalArgumentException("DEMOGRAPHIC_PREPARED_REQUIRED_VALUE_MISSING");
        bounded(firstName, 50);
        bounded(lastName, 50);
        bounded(healthNumber, 20);
        bounded(issuer, 20);
        bounded(gender, 1);
        bounded(status, 50);
        bounded(primaryProvider, 6);
        var birth = java.time.LocalDate.parse(birthDate);
        if (birth.getYear() < 1 || birth.getYear() > 9999)
            throw new IllegalArgumentException("DEMOGRAPHIC_PREPARED_DATE_INVALID");
    }

    /** Explicit source-less compatibility for existing native writer fixtures; actual preparation supplies details. */
    public CdsDemographicNativeDto(
            String sourceId,
            String firstName,
            String lastName,
            String birthDate,
            String healthNumber,
            String issuer,
            String gender,
            String status,
            Map<String, String> scalars,
            Address mailing,
            Address residential,
            Phone home,
            Phone work,
            Phone cell,
            String primaryProvider,
            List<CdsClinicalDiagnosticDto> diagnostics) {
        this(
                sourceId,
                firstName,
                lastName,
                birthDate,
                healthNumber,
                issuer,
                gender,
                status,
                scalars,
                mailing,
                residential,
                home,
                work,
                cell,
                primaryProvider,
                CdsDemographicDetailsDto.empty(),
                CdsDemographicRelationsDto.empty(),
                diagnostics);
    }

    private static void bounded(String value, int maximum) {
        if (value != null && value.codePointCount(0, value.length()) > maximum)
            throw new IllegalArgumentException("DEMOGRAPHIC_PREPARED_TEXT_INVALID");
    }

    public record Address(String address, String city, String province, String postal) {
        public Address {
            bounded(address, 60);
            bounded(city, 80);
            bounded(province, 20);
            bounded(postal, 9);
        }

        @Override
        public String toString() {
            return "CdsNativeAddress[protected]";
        }
    }

    public record Phone(String number, String extension) {
        public Phone {
            bounded(number, 20);
            bounded(extension, 5);
        }

        @Override
        public String toString() {
            return "CdsNativePhone[protected]";
        }
    }

    @Override
    public String toString() {
        return "CdsDemographicNativeDto[protected]";
    }
}
