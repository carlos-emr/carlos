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
        for (String authority : Arrays.asList(null, "", "MSP", "cpso")) {
            var mapped =
                    new CdsExportPublicProviderMapper()
                            .map(
                                    provider(authority, "000123"),
                                    CdsExportPublicProviderMapper.Target.PRIMARY_PHYSICIAN);
            assertThat(mapped.value().cpsoNumber()).isNull();
            assertThat(mapped.value().ohipNumber()).isEqualTo("001234");
        }
    }

    @Test
    void shouldBlockTheExactInvalidIdentifierField_withoutTrimmingTruncatingOrChangingNames()
            throws Exception {
        for (String identifier : List.of(" 01234", "0123456", "01\t234")) {
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
            assertThat(mapped.representable()).isFalse();
            assertThat(mapped.fields().get(key("practitionerNo")).outcome())
                    .isEqualTo(Outcome.REQUIRES_REVIEW);
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
