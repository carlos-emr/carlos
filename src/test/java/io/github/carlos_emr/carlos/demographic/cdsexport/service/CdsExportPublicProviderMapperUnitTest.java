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

import static org.assertj.core.api.Assertions.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Outcome;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@Tag("cds-export")
class CdsExportPublicProviderMapperUnitTest {
    @Test
    void shouldKeepPublicNameOhipAndExplicitCpsoDistinct_withoutNumericCoercion() throws Exception {
        var row = provider("CPSO", "000123");
        var mapper = new CdsExportPublicProviderMapper();
        var primary = mapper.map(row, CdsExportPublicProviderMapper.Target.PRIMARY_PHYSICIAN);
        assertThat(primary.representable()).isTrue();
        assertThat(primary.fields()).hasSize(6);
        assertThat(primary.value().firstName()).isEqualTo("Zoë😀");
        assertThat(primary.value().ohipNumber()).isEqualTo("001234");
        assertThat(primary.value().cpsoNumber()).isEqualTo("000123");
        var appointment = mapper.map(row, CdsExportPublicProviderMapper.Target.APPOINTMENT);
        assertThat(appointment.representable()).isTrue();
        assertThat(appointment.value().cpsoNumber()).isNull();
        assertThat(appointment.fields().get(key("practitionerNo")).outcome())
                .isEqualTo(Outcome.OPERATIONAL);
    }

    @Test
    void shouldNeverRelabelAnotherOrMissingRegistrationAuthorityAsCpso_forPublicProviderMapperContract() throws Exception {
        for (String authority : Arrays.asList("MSP", "cpso")) {
            var mapped =
                    new CdsExportPublicProviderMapper()
                            .map(
                                    provider(authority, "000123"),
                                    CdsExportPublicProviderMapper.Target.PRIMARY_PHYSICIAN);
            assertThat(mapped.value().cpsoNumber()).isNull();
            assertThat(mapped.value().ohipNumber()).isEqualTo("001234");
        }
        // A missing authority is never relabelled either; the number is provider-record lookup
        // metadata under its own rule, and the physician is still named.
        for (String authority : Arrays.asList(null, "")) {
            var mapped =
                    new CdsExportPublicProviderMapper()
                            .map(
                                    provider(authority, "000123"),
                                    CdsExportPublicProviderMapper.Target.PRIMARY_PHYSICIAN);
            assertThat(mapped.representable()).isTrue();
            assertThat(mapped.value().cpsoNumber()).isNull();
            assertThat(mapped.value().ohipNumber()).isEqualTo("001234");
        }
    }

    @Test
    void shouldNotAssertAnUnrepresentableCpsoNumber_withoutTrimmingOrTruncatingIt()
            throws Exception {
        // A CPSO-typed number that is not a six-character token cannot be written as
        // PrimaryPhysicianCPSO without changing it; it is not asserted, and nothing is held.
        for (String identifier : List.of(" 01234", "0123456", "01\t234", "01  23")) {
            var fields = new LinkedHashMap<>(provider("CPSO", "000123").fields());
            fields.put("practitionerNo", identifier);
            var mapped =
                    new CdsExportPublicProviderMapper()
                            .map(
                                    new CdsExportCapturedTextRowDto(
                                            "provider",
                                            CdsExportPublicProviderSourceReader.key("0é27"),
                                            fields),
                                    CdsExportPublicProviderMapper.Target.PRIMARY_PHYSICIAN);
            assertThat(mapped.representable()).isTrue();
            assertThat(mapped.value().cpsoNumber()).isNull();
            assertThat(mapped.value().firstName()).isEqualTo("Zoë😀");
            assertThat(mapped.fields().get(key("practitionerNo")).outcome())
                    .isEqualTo(Outcome.OPERATIONAL);
            assertThat(mapped.fields().get(key("practitionerNo")).rule())
                    .isEqualTo("UNREPRESENTABLE_CPSO_NUMBER_NOT_ASSERTED");
        }
    }

    @Test
    void shouldNotAssertALongOrSpacedBillingNumber_forAnyProviderTarget() throws Exception {
        // A17: Add/Edit Provider accepts up to twenty characters with spaces in ohip_no. None of
        // these is a six-digit OHIP number; it is neither asserted, trimmed nor truncated.
        for (String number :
                List.of(
                        "1234567",
                        "12345678901234567890",
                        "123 456",
                        " 123456",
                        "123456 ",
                        "12\t3456")) {
            for (var target : CdsExportPublicProviderMapper.Target.values()) {
                var fields = new LinkedHashMap<>(provider("CPSO", "000123").fields());
                fields.put("ohip_no", number);
                var mapped =
                        new CdsExportPublicProviderMapper()
                                .map(
                                        new CdsExportCapturedTextRowDto(
                                                "provider",
                                                CdsExportPublicProviderSourceReader.key("0é27"),
                                                fields),
                                        target);
                assertThat(mapped.representable()).isTrue();
                assertThat(mapped.value().ohipNumber()).isNull();
                assertThat(mapped.value().lastName()).isEqualTo(fields.get("last_name"));
                assertThat(mapped.fields().get(key("ohip_no")).rule())
                        .isEqualTo("NON_OHIP_BILLING_NUMBER_NOT_ASSERTED");
            }
        }
    }

    @Test
    void shouldNotAssertAnotherJurisdictionsNumber_asTheOhipBillingNumber() throws Exception {
        // D9: the data dictionary defines OHIPPhysicianId as six digits; a BC MSP number ("A1234")
        // or a placeholder ("0") in ohip_no is not one. The physician is still named.
        for (String number : List.of("A1234", "0", "12345", "1234AB")) {
            var fields = new LinkedHashMap<>(provider("CPSO", "000123").fields());
            fields.put("ohip_no", number);
            var mapped =
                    new CdsExportPublicProviderMapper()
                            .map(
                                    new CdsExportCapturedTextRowDto(
                                            "provider",
                                            CdsExportPublicProviderSourceReader.key("0é27"),
                                            fields),
                                    CdsExportPublicProviderMapper.Target.PRIMARY_PHYSICIAN);
            assertThat(mapped.representable()).isTrue();
            assertThat(mapped.value().ohipNumber()).isNull();
            assertThat(mapped.value().firstName()).isEqualTo("Zoë😀");
            assertThat(mapped.fields().get(key("ohip_no")).rule())
                    .isEqualTo("NON_OHIP_BILLING_NUMBER_NOT_ASSERTED");
        }
    }

    @Test
    void shouldRefuseAChangedIdentityKey_insteadOfUsingTheDisplayName() {
        var row = provider("CPSO", "000123");
        var forged =
                new CdsExportCapturedTextRowDto(
                        "provider", CdsExportPublicProviderSourceReader.key("OTHER"), row.fields());
        assertThatThrownBy(
                        () ->
                                new CdsExportPublicProviderMapper()
                                        .map(
                                                forged,
                                                CdsExportPublicProviderMapper.Target
                                                        .PRIMARY_PHYSICIAN))
                .hasMessage("CDS_EXPORT_PUBLIC_PROVIDER_CAPTURE_SCOPE_INVALID");
    }

    @Test
    void shouldNotAssertAnUntypedRegistrationNumber_insteadOfHoldingThePhysiciansPatients()
            throws Exception {
        // A17 (supersedes review P4): it may be the MRP's CPSO number or another authority's.
        // It is not asserted as CPSO; it is provider-record lookup metadata with its own rule.
        for (String authority : Arrays.asList(null, "", " ")) {
            var mapped =
                    new CdsExportPublicProviderMapper()
                            .map(
                                    provider(authority, "12345"),
                                    CdsExportPublicProviderMapper.Target.PRIMARY_PHYSICIAN);
            assertThat(mapped.representable()).isTrue();
            assertThat(mapped.value().cpsoNumber()).isNull();
            assertThat(mapped.fields().get(key("practitionerNo")).outcome())
                    .isEqualTo(Outcome.OPERATIONAL);
            assertThat(mapped.fields().get(key("practitionerNo")).rule())
                    .isEqualTo("UNTYPED_REGISTRATION_NUMBER_NOT_ASSERTED");
        }
        assertThat(
                        new CdsExportPublicProviderMapper()
                                .map(
                                        provider("CPSO", "12345"),
                                        CdsExportPublicProviderMapper.Target.PRIMARY_PHYSICIAN)
                                .value()
                                .cpsoNumber())
                .isEqualTo("12345");
        assertThat(
                        new CdsExportPublicProviderMapper()
                                .map(
                                        provider(null, null),
                                        CdsExportPublicProviderMapper.Target.PRIMARY_PHYSICIAN)
                                .representable())
                .isTrue();
        assertThat(
                        new CdsExportPublicProviderMapper()
                                .map(
                                        provider(null, "12345"),
                                        CdsExportPublicProviderMapper.Target.APPOINTMENT)
                                .representable())
                .isTrue();
    }

    @Test
    void shouldHoldOnlyAnXmlIllegalProviderName_asGenuinelyMalformed() throws Exception {
        // first_name/last_name are varchar(30) on the pinned schema, so only a control character
        // CARLOS's provider form cannot enter can make a name unrepresentable.
        var fields = new LinkedHashMap<>(provider("CPSO", "000123").fields());
        fields.put("last_name", "Bad\u0001Name");
        var mapped =
                new CdsExportPublicProviderMapper()
                        .map(
                                new CdsExportCapturedTextRowDto(
                                        "provider",
                                        CdsExportPublicProviderSourceReader.key("0é27"),
                                        fields),
                                CdsExportPublicProviderMapper.Target.APPOINTMENT);
        assertThat(mapped.representable()).isFalse();
        assertThat(mapped.fields().get(key("last_name")).rule())
                .isEqualTo("WIRE_TEXT_REQUIRES_REVIEW");
    }

    static CdsExportCapturedTextRowDto provider(String authority, String number) {
        var fields = new LinkedHashMap<>(CdsExportAppointmentMapperUnitTest.provider().fields());
        fields.put("practitionerNoType", authority);
        fields.put("practitionerNo", number);
        return new CdsExportCapturedTextRowDto(
                "provider", CdsExportPublicProviderSourceReader.key("0é27"), fields);
    }

    private static CdsExportNativeFieldKeyDto key(String column) {
        return new CdsExportNativeFieldKeyDto(
                "provider", CdsExportPublicProviderSourceReader.key("0é27"), column);
    }
}
