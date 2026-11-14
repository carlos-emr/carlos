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

/**
 * Bounded external source relations; these identities never resolve or create a local provider.
 *
 * <p>{@code pharmacyCarried} holds, by {@code pharmacyInfo} column, the exact stored values of the
 * preferred pharmacy a CARLOS sender carries in its native demographic record because
 * PreferredPharmacy could not hold them exactly (a postal code's display space, Phone 2, Notes,
 * the service location, a free-text province...), each proven against the element the sender
 * wrote. The writer stores them over the element projection. A preparation sealed before they
 * were read back decodes them empty ({@code CdsPreparedDemographicDefaults}).
 */
public record CdsDemographicRelationsDto(
        Person familyPhysician,
        Person referringPhysician,
        Pharmacy preferredPharmacy,
        int facilityId,
        List<CdsClinicalDiagnosticDto> diagnostics,
        // Omitted when nothing is restored, so such a payload keeps the bytes it had before.
        @com.fasterxml.jackson.annotation.JsonInclude(
                        com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY)
                java.util.Map<String, String> pharmacyCarried) {
    /** The {@code pharmacyInfo} columns a carried value can be restored to, with their capacity. */
    public static final java.util.Map<String, Integer> PHARMACY_COLUMNS =
            java.util.Map.ofEntries(
                    java.util.Map.entry("name", 255),
                    java.util.Map.entry("address", 16_777_215),
                    java.util.Map.entry("city", 255),
                    java.util.Map.entry("province", 255),
                    java.util.Map.entry("postalCode", 20),
                    java.util.Map.entry("phone1", 20),
                    java.util.Map.entry("phone2", 20),
                    java.util.Map.entry("fax", 20),
                    java.util.Map.entry("email", 100),
                    java.util.Map.entry("notes", 16_777_215),
                    java.util.Map.entry("status", 1),
                    java.util.Map.entry("serviceLocationIdentifier", 255));

    public CdsDemographicRelationsDto {
        diagnostics = List.copyOf(diagnostics);
        // A restored value can be SQL NULL, which Map.copyOf refuses.
        pharmacyCarried =
                java.util.Collections.unmodifiableMap(
                        new java.util.TreeMap<>(
                                pharmacyCarried == null ? java.util.Map.of() : pharmacyCarried));
        if (facilityId < 0
                || !pharmacyCarried.isEmpty() && preferredPharmacy == null
                || !PHARMACY_COLUMNS.keySet().containsAll(pharmacyCarried.keySet()))
            throw new IllegalArgumentException("DEMOGRAPHIC_RELATIONS_INVALID");
        for (var value : pharmacyCarried.entrySet())
            bounded(value.getValue(), PHARMACY_COLUMNS.get(value.getKey()));
    }

    /** Relations with nothing restored from a CARLOS sender's native demographic record. */
    public CdsDemographicRelationsDto(
            Person familyPhysician,
            Person referringPhysician,
            Pharmacy preferredPharmacy,
            int facilityId,
            List<CdsClinicalDiagnosticDto> diagnostics) {
        this(
                familyPhysician,
                referringPhysician,
                preferredPharmacy,
                facilityId,
                diagnostics,
                java.util.Map.of());
    }

    public static CdsDemographicRelationsDto empty() {
        return new CdsDemographicRelationsDto(null, null, null, 0, List.of());
    }

    public record Person(String firstName, String lastName) {
        public Person {
            if (firstName == null || lastName == null || firstName.isBlank() && lastName.isBlank())
                throw new IllegalArgumentException("DEMOGRAPHIC_CONTEXT_PHYSICIAN_NAME_REQUIRED");
            bounded(firstName, 50);
            bounded(lastName, 50);
        }

        @Override
        public String toString() {
            return "CdsContextPhysician[protected]";
        }
    }

    public record Pharmacy(
            String name,
            String address,
            String city,
            String province,
            String postal,
            String phone,
            String fax,
            String email) {
        public Pharmacy {
            if (name == null || name.isBlank())
                throw new IllegalArgumentException("DEMOGRAPHIC_PHARMACY_NAME_REQUIRED");
            bounded(name, 255);
            bounded(address, 255);
            bounded(city, 60);
            bounded(province, 255);
            bounded(postal, 20);
            bounded(phone, 20);
            bounded(fax, 20);
            bounded(email, 100);
        }

        @Override
        public String toString() {
            return "CdsPreferredPharmacy[protected]";
        }
    }

    private static void bounded(String value, int maximum) {
        if (value != null && value.codePointCount(0, value.length()) > maximum)
            throw new IllegalArgumentException("DEMOGRAPHIC_RELATIONS_TEXT_INVALID");
    }

    @Override
    public String toString() {
        return "CdsDemographicRelationsDto[protected]";
    }
}
