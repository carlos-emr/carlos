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
package io.github.carlos_emr.carlos.casemgmt.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the trust-boundary sanitization helpers added to
 * {@link CaseManagementEntry2Action} to address CWE-501 (Trust Boundary Violation).
 *
 * <p>These helpers are package-private static methods and do not require a Spring context.
 *
 * @since 2026-04-06
 */
@Tag("unit")
@Tag("security")
@DisplayName("CaseManagementEntry2Action sanitization helpers")
class CaseManagementEntry2ActionSanitizationUnitTest {

    // ------------------------------------------------------------------
    // sanitizeFromParam
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("sanitizeFromParam")
    class SanitizeFromParam {

        @Test
        @DisplayName("should return casemgmt when value is casemgmt")
        void shouldReturnCasemgmt_whenValueIsCasemgmt() {
            assertThat(CaseManagementEntry2Action.sanitizeFromParam("casemgmt")).isEqualTo("casemgmt");
        }

        @Test
        @DisplayName("should return null when value is not in whitelist")
        void shouldReturnNull_whenValueNotInWhitelist() {
            assertThat(CaseManagementEntry2Action.sanitizeFromParam("provider")).isNull();
            assertThat(CaseManagementEntry2Action.sanitizeFromParam("billing")).isNull();
            assertThat(CaseManagementEntry2Action.sanitizeFromParam("../admin")).isNull();
            assertThat(CaseManagementEntry2Action.sanitizeFromParam("<script>")).isNull();
        }

        @Test
        @DisplayName("should return null when value is null")
        void shouldReturnNull_whenValueIsNull() {
            assertThat(CaseManagementEntry2Action.sanitizeFromParam(null)).isNull();
        }

        @Test
        @DisplayName("should return null when value is empty string")
        void shouldReturnNull_whenValueIsEmpty() {
            assertThat(CaseManagementEntry2Action.sanitizeFromParam("")).isNull();
        }

        @Test
        @DisplayName("should return null when value differs only in case")
        void shouldReturnNull_whenDifferentCase() {
            // whitelist is case-sensitive to avoid ambiguity; "CASEMGMT" is not accepted
            assertThat(CaseManagementEntry2Action.sanitizeFromParam("CASEMGMT")).isNull();
        }
    }

    // ------------------------------------------------------------------
    // sanitizeNoteSortParam
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("sanitizeNoteSortParam")
    class SanitizeNoteSortParam {

        @Test
        @DisplayName("should accept all whitelisted sort values")
        void shouldAccept_whitelistedSortValues() {
            for (String allowed : new String[]{
                    "observation_date_asc", "observation_date_desc",
                    "providerName", "programName", "roleName", "update_date"}) {
                assertThat(CaseManagementEntry2Action.sanitizeNoteSortParam(allowed))
                        .as("Expected allowed value '%s' to pass", allowed)
                        .isEqualTo(allowed);
            }
        }

        @Test
        @DisplayName("should return null for unknown sort column")
        void shouldReturnNull_forUnknownSortColumn() {
            assertThat(CaseManagementEntry2Action.sanitizeNoteSortParam("evil_column")).isNull();
            assertThat(CaseManagementEntry2Action.sanitizeNoteSortParam("1 OR 1=1")).isNull();
        }

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNull_forNullInput() {
            assertThat(CaseManagementEntry2Action.sanitizeNoteSortParam(null)).isNull();
        }

        @Test
        @DisplayName("should return null for empty string")
        void shouldReturnNull_forEmptyString() {
            assertThat(CaseManagementEntry2Action.sanitizeNoteSortParam("")).isNull();
        }
    }

    // ------------------------------------------------------------------
    // sanitizeIdFilterArray
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("sanitizeIdFilterArray")
    class SanitizeIdFilterArray {

        @Test
        @DisplayName("should return null when input is null")
        void shouldReturnNull_whenInputIsNull() {
            assertThat(CaseManagementEntry2Action.sanitizeIdFilterArray(null)).isNull();
        }

        @Test
        @DisplayName("should return empty array when input is empty array")
        void shouldReturnEmpty_whenInputIsEmpty() {
            assertThat(CaseManagementEntry2Action.sanitizeIdFilterArray(new String[0])).isEmpty();
        }

        @Test
        @DisplayName("should accept the all sentinel a")
        void shouldAccept_allSentinel() {
            assertThat(CaseManagementEntry2Action.sanitizeIdFilterArray(new String[]{"a"}))
                    .containsExactly("a");
        }

        @Test
        @DisplayName("should accept the none sentinel n")
        void shouldAccept_noneSentinel() {
            assertThat(CaseManagementEntry2Action.sanitizeIdFilterArray(new String[]{"n"}))
                    .containsExactly("n");
        }

        @Test
        @DisplayName("should accept digit-only numeric IDs")
        void shouldAccept_numericIds() {
            assertThat(CaseManagementEntry2Action.sanitizeIdFilterArray(new String[]{"1", "42", "1000"}))
                    .containsExactly("1", "42", "1000");
        }

        @Test
        @DisplayName("should drop non-numeric non-sentinel values")
        void shouldDrop_nonNumericValues() {
            assertThat(CaseManagementEntry2Action.sanitizeIdFilterArray(
                    new String[]{"1", "doctor", "'; DROP TABLE--", "42"}))
                    .containsExactly("1", "42");
        }

        @Test
        @DisplayName("should return empty array when all values are invalid")
        void shouldReturnEmpty_whenAllValuesAreInvalid() {
            assertThat(CaseManagementEntry2Action.sanitizeIdFilterArray(
                    new String[]{"invalid", "<xss>", "1 OR 1=1"}))
                    .isEmpty();
        }

        @Test
        @DisplayName("should drop null elements within array")
        void shouldDrop_nullElements() {
            assertThat(CaseManagementEntry2Action.sanitizeIdFilterArray(new String[]{null, "5", null}))
                    .containsExactly("5");
        }

        @Test
        @DisplayName("should drop negative numbers")
        void shouldDrop_negativeNumbers() {
            assertThat(CaseManagementEntry2Action.sanitizeIdFilterArray(new String[]{"-1", "5"}))
                    .containsExactly("5");
        }

        @Test
        @DisplayName("should drop alphanumeric mixed values")
        void shouldDrop_alphanumericMixed() {
            assertThat(CaseManagementEntry2Action.sanitizeIdFilterArray(new String[]{"1abc", "abc1"}))
                    .isEmpty();
        }
    }
}
