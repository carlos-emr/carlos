/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
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
 * Maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.eform.actions;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for eform_link validation in {@link AddEForm2Action}.
 *
 * <p>Validates that {@link AddEForm2Action#validateEformLink(String)} correctly
 * accepts valid eform-to-eform linking keys and rejects session poisoning
 * attempts (CWE-501).</p>
 *
 * <p>Expected format: {providerNo}_{demographicNo}_{fid}_{fieldName}</p>
 *
 * @since 2026-04-08
 */
@DisplayName("AddEForm2Action eform_link Validation")
@Tag("unit")
@Tag("eform")
@Tag("security")
class AddEForm2ActionEformLinkValidationTest {

    @Nested
    @DisplayName("Valid eform_link values")
    class ValidValues {

        @ParameterizedTest
        @DisplayName("should return value when format is valid")
        @ValueSource(strings = {
            "999998_12345_67_referralForm",
            "doc1_99999_100_linkField",
            "A1B2C3_1_1_f",
            "p1_123456789_9999999999_field.name",
            "abc_1_2_field-name",
            "abc_1_2_field_name",
            "abc_1_2_a.b-c_d"
        })
        void shouldReturnValue_whenFormatIsValid(String eformLink) {
            assertThat(AddEForm2Action.validateEformLink(eformLink))
                .as("eform_link '%s' should be accepted", eformLink)
                .isEqualTo(eformLink);
        }

        @ParameterizedTest
        @DisplayName("should accept eform_link when demographic is -1 (admin view)")
        @ValueSource(strings = {
            "999998_-1_67_referralForm",
            "doc1_-1_100_linkField",
            "p1_-1_1_f"
        })
        void shouldAcceptEformLink_whenDemographicIsNegativeOne(String eformLink) {
            assertThat(AddEForm2Action.validateEformLink(eformLink))
                .as("eform_link '%s' with -1 demographic should be accepted", eformLink)
                .isEqualTo(eformLink);
        }

        @Test
        @DisplayName("should return null when input is null")
        void shouldReturnNull_whenInputIsNull() {
            assertThat(AddEForm2Action.validateEformLink(null)).isNull();
        }
    }

    @Nested
    @DisplayName("Session poisoning attempts - must reject")
    class SessionPoisoningAttempts {

        @ParameterizedTest
        @DisplayName("should return null when value is a dangerous session key")
        @ValueSource(strings = {
            "user",
            "CURRENT_FACILITY",
            "EctSessionBean",
            "RxSessionBean",
            "casemgmtNoteLock123"
        })
        void shouldReturnNull_whenValueIsDangerousSessionKey(String eformLink) {
            assertThat(AddEForm2Action.validateEformLink(eformLink))
                .as("eform_link '%s' should be REJECTED (session poisoning)", eformLink)
                .isNull();
        }
    }

    @Nested
    @DisplayName("Invalid format values - must reject")
    class InvalidFormatValues {

        @ParameterizedTest
        @DisplayName("should return null when format is malformed")
        @ValueSource(strings = {
            "",
            "singletoken",
            "two_parts",
            "three_parts_only",
            "../../../etc/passwd",
            "<script>alert(1)</script>",
            "prov_demo_fid_field with spaces",
            "toolongprovider_123_456_field",
            "prov_notanumber_456_field",
            "prov_123_notanumber_field"
        })
        void shouldReturnNull_whenFormatIsMalformed(String eformLink) {
            assertThat(AddEForm2Action.validateEformLink(eformLink))
                .as("eform_link '%s' should be REJECTED (invalid format)", eformLink)
                .isNull();
        }

        @Test
        @DisplayName("should return null when provider exceeds 6 characters")
        void shouldReturnNull_whenProviderExceedsMaxLength() {
            String eformLink = "ABCDEFG_123_456_field";
            assertThat(AddEForm2Action.validateEformLink(eformLink)).isNull();
        }

        @Test
        @DisplayName("should return null when demographic exceeds 10 digits")
        void shouldReturnNull_whenDemographicExceedsMaxLength() {
            String eformLink = "prov_12345678901_456_field";
            assertThat(AddEForm2Action.validateEformLink(eformLink)).isNull();
        }

        @Test
        @DisplayName("should return null when fid exceeds 10 digits")
        void shouldReturnNull_whenFidExceedsMaxLength() {
            String eformLink = "prov_123_12345678901_field";
            assertThat(AddEForm2Action.validateEformLink(eformLink)).isNull();
        }

        @Test
        @DisplayName("should return null when field name exceeds 50 characters")
        void shouldReturnNull_whenFieldNameExceedsMaxLength() {
            String longField = "a".repeat(51);
            String eformLink = "prov_123_456_" + longField;
            assertThat(AddEForm2Action.validateEformLink(eformLink)).isNull();
        }
    }
}
