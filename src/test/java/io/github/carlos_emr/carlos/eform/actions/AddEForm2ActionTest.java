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

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for trust-boundary-violation fixes in {@link AddEForm2Action}.
 *
 * <p>Covers CWE-501 (Trust Boundary Violation) mitigations:</p>
 * <ul>
 *   <li>eform_link session-key injection fix (line ~220 in the action)</li>
 *   <li>{@code validateIntId} — safe integer-string canonicalisation</li>
 *   <li>{@code validateIntIdArray} — array-wide integer validation</li>
 * </ul>
 *
 * @since 2026-04-07
 */
@DisplayName("AddEForm2Action — Trust Boundary Validation")
@Tag("unit")
@Tag("eform")
@Tag("security")
class AddEForm2ActionTest extends CarlosUnitTestBase {

    // -------------------------------------------------------------------------
    // eform_link pattern (session key injection guard)
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("eform_link session-key pattern")
    class EformLinkPattern {

        @Test
        @DisplayName("should accept valid eform_link pattern with numeric segments and word suffix")
        void shouldAccept_validEformLinkPattern() {
            assertThat("1_100_5_fieldName".matches(AddEForm2Action.EFORM_LINK_PATTERN)).isTrue();
        }

        @Test
        @DisplayName("should accept eform_link with underscore in suffix segment")
        void shouldAccept_eformLinkWithUnderscoreInSuffix() {
            assertThat("12_34_56_my_field".matches(AddEForm2Action.EFORM_LINK_PATTERN)).isTrue();
        }

        @Test
        @DisplayName("should reject eform_link that is a plain session attribute name")
        void shouldReject_plainSessionAttributeName() {
            // Attempts to overwrite 'user' session attribute
            assertThat("user".matches(AddEForm2Action.EFORM_LINK_PATTERN)).isFalse();
        }

        @Test
        @DisplayName("should reject eform_link that targets userrole session attribute")
        void shouldReject_userRoleSessionAttributeName() {
            assertThat("userrole".matches(AddEForm2Action.EFORM_LINK_PATTERN)).isFalse();
        }

        @Test
        @DisplayName("should reject eform_link with injected SQL-like content")
        void shouldReject_sqlInjectionAttempt() {
            assertThat("1'; DROP TABLE eform;--".matches(AddEForm2Action.EFORM_LINK_PATTERN)).isFalse();
        }

        @Test
        @DisplayName("should reject eform_link missing numeric prefix segments")
        void shouldReject_missingNumericSegments() {
            assertThat("abc_def_ghi_field".matches(AddEForm2Action.EFORM_LINK_PATTERN)).isFalse();
        }

        @Test
        @DisplayName("should reject eform_link with only two numeric segments")
        void shouldReject_onlyTwoNumericSegments() {
            assertThat("1_2_field".matches(AddEForm2Action.EFORM_LINK_PATTERN)).isFalse();
        }

        @Test
        @DisplayName("should reject empty string eform_link")
        void shouldReject_emptyString() {
            assertThat("".matches(AddEForm2Action.EFORM_LINK_PATTERN)).isFalse();
        }
    }

    // -------------------------------------------------------------------------
    // validateIntId
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("validateIntId")
    class ValidateIntId {

        @Test
        @DisplayName("should return canonical integer string for valid integer input")
        void shouldReturnCanonicalIntegerString_forValidInput() {
            assertThat(AddEForm2Action.validateIntId("42")).isEqualTo("42");
        }

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNull_forNullInput() {
            assertThat(AddEForm2Action.validateIntId(null)).isNull();
        }

        @Test
        @DisplayName("should return null for non-integer string")
        void shouldReturnNull_forNonIntegerString() {
            assertThat(AddEForm2Action.validateIntId("abc")).isNull();
        }

        @Test
        @DisplayName("should return null for string with leading/trailing spaces")
        void shouldReturnNull_forStringWithSpaces() {
            // Leading/trailing whitespace is NOT a valid integer — reject it
            assertThat(AddEForm2Action.validateIntId(" 42 ")).isNull();
        }

        @Test
        @DisplayName("should return null for SQL injection attempt")
        void shouldReturnNull_forSqlInjectionAttempt() {
            assertThat(AddEForm2Action.validateIntId("1; DROP TABLE eform;--")).isNull();
        }

        @Test
        @DisplayName("should return null for overflow integer")
        void shouldReturnNull_forOverflowInteger() {
            assertThat(AddEForm2Action.validateIntId("99999999999")).isNull();
        }

        @Test
        @DisplayName("should return null for empty string")
        void shouldReturnNull_forEmptyString() {
            assertThat(AddEForm2Action.validateIntId("")).isNull();
        }

        @Test
        @DisplayName("should return canonical string for zero")
        void shouldReturnCanonicalString_forZero() {
            assertThat(AddEForm2Action.validateIntId("0")).isEqualTo("0");
        }

        @Test
        @DisplayName("should return canonical string for negative integer")
        void shouldReturnCanonicalString_forNegativeInteger() {
            assertThat(AddEForm2Action.validateIntId("-1")).isEqualTo("-1");
        }

        @Test
        @DisplayName("should strip leading zeros by returning canonical integer form")
        void shouldStripLeadingZeros_byReturningCanonicalForm() {
            // Integer.parseInt("007") = 7, String.valueOf(7) = "7"
            assertThat(AddEForm2Action.validateIntId("007")).isEqualTo("7");
        }
    }

    // -------------------------------------------------------------------------
    // validateIntIdArray
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("validateIntIdArray")
    class ValidateIntIdArray {

        @Test
        @DisplayName("should return empty array for null input")
        void shouldReturnEmptyArray_forNullInput() {
            assertThat(AddEForm2Action.validateIntIdArray(null)).isEmpty();
        }

        @Test
        @DisplayName("should return empty array for empty array input")
        void shouldReturnEmptyArray_forEmptyArrayInput() {
            assertThat(AddEForm2Action.validateIntIdArray(new String[0])).isEmpty();
        }

        @Test
        @DisplayName("should return validated integer strings for all-valid array")
        void shouldReturnValidatedStrings_forAllValidArray() {
            String[] result = AddEForm2Action.validateIntIdArray(new String[]{"1", "2", "3"});
            assertThat(result).containsExactly("1", "2", "3");
        }

        @Test
        @DisplayName("should filter out non-integer elements, keeping valid ones")
        void shouldFilterNonIntegers_keepingValidOnes() {
            String[] result = AddEForm2Action.validateIntIdArray(
                new String[]{"1", "abc", "3", null, "5"}
            );
            assertThat(result).containsExactly("1", "3", "5");
        }

        @Test
        @DisplayName("should return empty array when all elements are invalid")
        void shouldReturnEmptyArray_whenAllElementsInvalid() {
            String[] result = AddEForm2Action.validateIntIdArray(
                new String[]{"abc", null, "1 DROP TABLE", ""}
            );
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should normalise IDs by returning canonical integer form")
        void shouldNormaliseIds_byReturningCanonicalForm() {
            // "007" -> "7" (canonical integer string)
            String[] result = AddEForm2Action.validateIntIdArray(new String[]{"007", "100"});
            assertThat(result).containsExactly("7", "100");
        }
    }
}
