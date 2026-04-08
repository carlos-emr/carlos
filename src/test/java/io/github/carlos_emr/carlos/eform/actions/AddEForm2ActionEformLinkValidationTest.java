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
 * Unit tests for the eform_link validation pattern in {@link AddEForm2Action}.
 *
 * <p>Validates that the EFORM_LINK_PATTERN regex correctly accepts valid
 * eform-to-eform linking keys and rejects session poisoning attempts (CWE-501).</p>
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
        @DisplayName("should accept valid eform_link format")
        @ValueSource(strings = {
            "999998_12345_67_referralForm",
            "doc1_99999_100_linkField",
            "A1B2C3_1_1_f",
            "p1_123456789_9999999999_field.name",
            "abc_1_2_field-name",
            "abc_1_2_field_name",
            "abc_1_2_a.b-c_d"
        })
        void shouldAcceptValidEformLink(String eformLink) {
            assertThat(eformLink.matches(AddEForm2Action.EFORM_LINK_PATTERN))
                .as("eform_link '%s' should be accepted", eformLink)
                .isTrue();
        }
    }

    @Nested
    @DisplayName("Session poisoning attempts - must reject")
    class SessionPoisoningAttempts {

        @ParameterizedTest
        @DisplayName("should reject dangerous session attribute names")
        @ValueSource(strings = {
            "user",
            "CURRENT_FACILITY",
            "EctSessionBean",
            "RxSessionBean",
            "casemgmtNoteLock123"
        })
        void shouldRejectDangerousSessionKeys(String eformLink) {
            assertThat(eformLink.matches(AddEForm2Action.EFORM_LINK_PATTERN))
                .as("eform_link '%s' should be REJECTED (session poisoning)", eformLink)
                .isFalse();
        }
    }

    @Nested
    @DisplayName("Invalid format values - must reject")
    class InvalidFormatValues {

        @ParameterizedTest
        @DisplayName("should reject malformed eform_link values")
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
        void shouldRejectMalformedEformLink(String eformLink) {
            assertThat(eformLink.matches(AddEForm2Action.EFORM_LINK_PATTERN))
                .as("eform_link '%s' should be REJECTED (invalid format)", eformLink)
                .isFalse();
        }

        @Test
        @DisplayName("should reject provider number exceeding 6 characters")
        void shouldRejectProviderNo_whenExceedingMaxLength() {
            String eformLink = "ABCDEFG_123_456_field";
            assertThat(eformLink.matches(AddEForm2Action.EFORM_LINK_PATTERN)).isFalse();
        }

        @Test
        @DisplayName("should reject demographic number exceeding 10 digits")
        void shouldRejectDemographicNo_whenExceedingMaxLength() {
            String eformLink = "prov_12345678901_456_field";
            assertThat(eformLink.matches(AddEForm2Action.EFORM_LINK_PATTERN)).isFalse();
        }

        @Test
        @DisplayName("should reject fid exceeding 10 digits")
        void shouldRejectFid_whenExceedingMaxLength() {
            String eformLink = "prov_123_12345678901_field";
            assertThat(eformLink.matches(AddEForm2Action.EFORM_LINK_PATTERN)).isFalse();
        }

        @Test
        @DisplayName("should reject field name exceeding 50 characters")
        void shouldRejectFieldName_whenExceedingMaxLength() {
            String longField = "a".repeat(51);
            String eformLink = "prov_123_456_" + longField;
            assertThat(eformLink.matches(AddEForm2Action.EFORM_LINK_PATTERN)).isFalse();
        }
    }
}
