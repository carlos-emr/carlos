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

/**
 * Fixed-size prepared native projection; all repeated originals remain separate category-local
 * source fields.
 *
 * <p>{@code carried} and {@code properties} are what a CARLOS sender's native demographic record
 * restores: the exact stored value of each base-row column whose element the sender could not
 * write exactly or that no element states (by column; a null value is SQL NULL), the sender's
 * current roster state when it is not a CDS enrolment event, and the {@code demographicExt} rows
 * it carries. {@code careTeam} names, by {@code demographiccust} column (cust1 nurse, cust2
 * resident, cust4 midwife), the local provider matched from the public columns the record
 * carries for the sender's care-team provider (the sender's own provider number is never
 * stored). The writer stores them over the element projection; the exporter's projection
 * evidence reads them back. A preparation sealed before they existed decodes them empty
 * ({@code CdsPreparedDemographicDefaults}).
 */
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
        List<CdsClinicalDiagnosticDto> diagnostics,
        Map<String, String> carried,
        List<Property> properties,
        Map<String, String> careTeam) {
    public static final String MIDDLE_NAMES_SCALAR = "Names/LegalName/OtherName/GIV";

    /** The care-team columns of {@code demographiccust} a restored provider can fill. */
    public static final java.util.Set<String> CARE_TEAM = java.util.Set.of("cust1", "cust2", "cust4");

    public CdsDemographicNativeDto {
        scalars = Map.copyOf(scalars);
        diagnostics = List.copyOf(diagnostics);
        details = details == null ? CdsDemographicDetailsDto.empty() : details;
        relations = relations == null ? CdsDemographicRelationsDto.empty() : relations;
        // A restored column can be SQL NULL, which Map.copyOf refuses.
        carried =
                java.util.Collections.unmodifiableMap(
                        new java.util.TreeMap<>(carried == null ? Map.of() : carried));
        properties = properties == null ? List.of() : List.copyOf(properties);
        careTeam =
                java.util.Collections.unmodifiableMap(
                        new java.util.TreeMap<>(careTeam == null ? Map.of() : careTeam));
        for (var member : careTeam.entrySet())
            if (!CARE_TEAM.contains(member.getKey()) || member.getValue() == null)
                throw new IllegalArgumentException("DEMOGRAPHIC_PREPARED_CARE_TEAM_INVALID");
            else bounded(member.getValue(), 6);
        if (firstName == null || lastName == null || birthDate == null || gender == null)
            throw new IllegalArgumentException("DEMOGRAPHIC_PREPARED_REQUIRED_VALUE_MISSING");
        bounded(firstName, 50);
        bounded(lastName, 50);
        bounded(healthNumber, 20);
        bounded(issuer, 20);
        bounded(gender, 1);
        bounded(status, 50);
        bounded(primaryProvider, 6);
        bounded(scalars.get(MIDDLE_NAMES_SCALAR), 100);
        var birth = java.time.LocalDate.parse(birthDate);
        if (birth.getYear() < 1 || birth.getYear() > 9999)
            throw new IllegalArgumentException("DEMOGRAPHIC_PREPARED_DATE_INVALID");
    }

    /** A projection with no care-team provider restored. */
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
            CdsDemographicDetailsDto details,
            CdsDemographicRelationsDto relations,
            List<CdsClinicalDiagnosticDto> diagnostics,
            Map<String, String> carried,
            List<Property> properties) {
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
                details,
                relations,
                diagnostics,
                carried,
                properties,
                Map.of());
    }

    /** A projection with nothing restored from a CARLOS sender's native demographic record. */
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
            CdsDemographicDetailsDto details,
            CdsDemographicRelationsDto relations,
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
                details,
                relations,
                diagnostics,
                Map.of(),
                List.of());
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

    /**
     * A {@code demographicExt} row of the sender, restored as it was stored: its key (null when
     * it had none), value and hidden flag.
     */
    public record Property(String key, String value, boolean hidden) {
        @Override
        public String toString() {
            return "CdsNativeProperty[protected]";
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
