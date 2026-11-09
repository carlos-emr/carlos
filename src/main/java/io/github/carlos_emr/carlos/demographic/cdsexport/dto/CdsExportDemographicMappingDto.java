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

import io.github.carlos_emr.carlos.commn.dto.CdsExportDemographicRowDto;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Explicit classification of the base demographic row. This does not assert completeness
 * of associated contacts, extensions, enrolment history, pharmacy or retained import data.
 * A populated unsupported field prevents this row from being serialized.
 */
public record CdsExportDemographicMappingDto(
        int patient,
        Names names,
        LocalDate birthDate,
        HealthCard healthCard,
        String chartNumber,
        String administrativeGender,
        List<Address> addresses,
        List<Phone> phones,
        String officialLanguage,
        String spokenLanguage,
        String email,
        String personStatus,
        boolean enumeratedStatus,
        LocalDate personStatusDate,
        String sin,
        Map<String, Assessment> fields,
        CdsExportPublicProviderDto primaryPhysician,
        Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> relatedFields) {
    /** Preserve the base-row-only API; absent related evidence never silently qualifies a populated reference. */
    public CdsExportDemographicMappingDto(
            int patient,
            Names names,
            LocalDate birthDate,
            HealthCard healthCard,
            String chartNumber,
            String administrativeGender,
            List<Address> addresses,
            List<Phone> phones,
            String officialLanguage,
            String spokenLanguage,
            String email,
            String personStatus,
            boolean enumeratedStatus,
            LocalDate personStatusDate,
            String sin,
            Map<String, Assessment> fields) {
        this(
                patient,
                names,
                birthDate,
                healthCard,
                chartNumber,
                administrativeGender,
                addresses,
                phones,
                officialLanguage,
                spokenLanguage,
                email,
                personStatus,
                enumeratedStatus,
                personStatusDate,
                sin,
                fields,
                null,
                Map.of());
    }

    public CdsExportDemographicMappingDto withPrimaryPhysician(
            CdsExportPublicProviderDto provider,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> related,
            Assessment reference) {
        var updated = new java.util.HashMap<>(fields);
        updated.put("provider_no", reference);
        var joined = new java.util.HashMap<>(relatedFields);
        joined.keySet().removeIf(field -> field.table().equals("provider"));
        for (var entry : related.entrySet()) {
            if (!entry.getKey().table().equals("provider")
                    || joined.putIfAbsent(entry.getKey(), entry.getValue()) != null)
                throw new IllegalArgumentException(
                        "CDS_EXPORT_DEMOGRAPHIC_PROVIDER_MAPPING_INVALID");
        }
        return new CdsExportDemographicMappingDto(
                patient,
                names,
                birthDate,
                healthCard,
                chartNumber,
                administrativeGender,
                addresses,
                phones,
                officialLanguage,
                spokenLanguage,
                email,
                personStatus,
                enumeratedStatus,
                personStatusDate,
                sin,
                updated,
                provider,
                joined);
    }

    /** Related-property decisions remain separate from the exactly captured base-row fields. */
    public CdsExportDemographicMappingDto withPhones(
            List<Phone> updatedPhones,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> properties) {
        var related = new java.util.HashMap<>(relatedFields);
        for (var entry : properties.entrySet()) {
            if (!entry.getKey().table().equals("demographicExt")
                    || related.putIfAbsent(entry.getKey(), entry.getValue()) != null)
                throw new IllegalArgumentException("CDS_EXPORT_DEMOGRAPHIC_PHONE_EVIDENCE_INVALID");
        }
        return new CdsExportDemographicMappingDto(
                patient,
                names,
                birthDate,
                healthCard,
                chartNumber,
                administrativeGender,
                addresses,
                updatedPhones,
                officialLanguage,
                spokenLanguage,
                email,
                personStatus,
                enumeratedStatus,
                personStatusDate,
                sin,
                fields,
                primaryPhysician,
                related);
    }

    public enum Presence {
        SQL_NULL,
        EMPTY,
        VALUE
    }

    public enum Outcome {
        ABSENT,
        MAPPED,
        OPERATIONAL,
        REQUIRES_REVIEW
    }

    public record Assessment(Presence presence, Outcome outcome, String destination, String rule) {
        public Assessment {
            java.util.Objects.requireNonNull(presence);
            java.util.Objects.requireNonNull(outcome);
            java.util.Objects.requireNonNull(destination);
            java.util.Objects.requireNonNull(rule);
        }
    }

    public record Names(String prefix, String first, String last, String middle) {
        @Override
        public String toString() {
            return "Names[protected]";
        }
    }

    public record HealthCard(String number, String version, LocalDate expiry, String province) {
        @Override
        public String toString() {
            return "HealthCard[protected]";
        }
    }

    public record Address(
            String type, String line1, String city, String subdivision, String postalCode) {
        @Override
        public String toString() {
            return "Address[protected]";
        }
    }

    public record Phone(String type, String number, String extension) {
        public Phone(String type, String number) {
            this(type, number, null);
        }

        public Phone {
            if (type == null
                    || !java.util.Set.of("R", "W", "C").contains(type)
                    || number == null
                    || !token(number, 25)
                    || extension != null && !token(extension, 5))
                throw new IllegalArgumentException("CDS_EXPORT_DEMOGRAPHIC_PHONE_INVALID");
        }

        private static boolean token(String value, int maximum) {
            return value.codePointCount(0, value.length()) <= maximum
                    && !value.startsWith(" ")
                    && !value.endsWith(" ")
                    && !value.contains("  ")
                    && value.codePoints()
                            .noneMatch(
                                    code ->
                                            code < 0x20
                                                    || code >= 0xd800 && code <= 0xdfff
                                                    || code == 0xfffe
                                                    || code == 0xffff);
        }

        @Override
        public String toString() {
            return "Phone[protected]";
        }
    }

    public CdsExportDemographicMappingDto {
        if (patient < 1
                || names == null
                || fields == null
                || !fields.keySet()
                        .equals(java.util.Set.copyOf(CdsExportDemographicRowDto.COLUMNS))) {
            throw new IllegalArgumentException("CDS_EXPORT_DEMOGRAPHIC_MAPPING_INVALID");
        }
        addresses = List.copyOf(addresses);
        phones = List.copyOf(phones);
        fields = Map.copyOf(fields);
        relatedFields = Map.copyOf(relatedFields);
        if (primaryPhysician != null
                && (fields.get("provider_no").outcome() != Outcome.MAPPED
                        || relatedFields.keySet().stream()
                                .noneMatch(field -> field.table().equals("provider"))
                        || relatedFields.entrySet().stream()
                                .filter(entry -> entry.getKey().table().equals("provider"))
                                .anyMatch(
                                        entry ->
                                                entry.getValue().outcome()
                                                        == CdsExportFieldAssessmentDto.Outcome
                                                                .REQUIRES_REVIEW))) {
            throw new IllegalArgumentException("CDS_EXPORT_DEMOGRAPHIC_PROVIDER_MAPPING_INVALID");
        }
    }

    /** The base row only; the patient still needs the independent category inventory/reconciliation. */
    public boolean baseRowRepresentable() {
        return birthDate != null
                && administrativeGender != null
                && personStatus != null
                && names.first() != null
                && names.last() != null
                && fields.values().stream()
                        .noneMatch(field -> field.outcome() == Outcome.REQUIRES_REVIEW)
                && relatedFields.values().stream()
                        .noneMatch(
                                field ->
                                        field.outcome()
                                                == CdsExportFieldAssessmentDto.Outcome
                                                        .REQUIRES_REVIEW);
    }

    @Override
    public String toString() {
        return "CdsExportDemographicMappingDto[protected]";
    }
}
