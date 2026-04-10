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

import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.email.core.EmailAttachmentSettings;
import io.github.carlos_emr.carlos.managers.EformDataManager;
import io.github.carlos_emr.carlos.managers.EmailManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;

/**
 * Unit tests for trust-boundary-violation fixes in {@link AddEForm2Action}.
 *
 * <p>Covers CWE-501 (Trust Boundary Violation) mitigations:</p>
 * <ul>
 *   <li>{@code validateEformLink} — eform_link session-key injection guard</li>
 *   <li>{@code validateIntId} — safe integer-string canonicalisation</li>
 *   <li>{@code validateIntIdArray} — array-wide integer validation</li>
 *   <li>{@code addEmailAttachmentsToSession} — session sink wiring</li>
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
    // validateEformLink (session key injection guard)
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("validateEformLink — eform_link session-key pattern")
    class ValidateEformLink {

        @Test
        @DisplayName("should return value for valid eform_link pattern with numeric segments and word suffix")
        void shouldReturn_validEformLinkPattern() {
            assertThat(AddEForm2Action.validateEformLink("1_100_5_fieldName")).isEqualTo("1_100_5_fieldName");
        }

        @Test
        @DisplayName("should return value for eform_link with underscore in suffix segment")
        void shouldReturn_eformLinkWithUnderscoreInSuffix() {
            assertThat(AddEForm2Action.validateEformLink("12_34_56_my_field")).isEqualTo("12_34_56_my_field");
        }

        @Test
        @DisplayName("should return value for eform_link with dash and dot in field name")
        void shouldReturn_eformLinkWithDashAndDot() {
            // Pattern allows [a-zA-Z0-9_.-] in the field name segment
            assertThat(AddEForm2Action.validateEformLink("1_100_5_field.name-v2")).isEqualTo("1_100_5_field.name-v2");
        }

        @Test
        @DisplayName("should return null for eform_link that is a plain session attribute name")
        void shouldReturnNull_plainSessionAttributeName() {
            // Attempts to overwrite 'user' session attribute
            assertThat(AddEForm2Action.validateEformLink("user")).isNull();
        }

        @Test
        @DisplayName("should return null for eform_link that targets userrole session attribute")
        void shouldReturnNull_userRoleSessionAttributeName() {
            assertThat(AddEForm2Action.validateEformLink("userrole")).isNull();
        }

        @Test
        @DisplayName("should return null for eform_link with injected SQL-like content")
        void shouldReturnNull_sqlInjectionAttempt() {
            assertThat(AddEForm2Action.validateEformLink("1'; DROP TABLE eform;--")).isNull();
        }

        @Test
        @DisplayName("should return null for eform_link missing numeric prefix segments")
        void shouldReturnNull_missingNumericSegments() {
            assertThat(AddEForm2Action.validateEformLink("abc_def_ghi_field")).isNull();
        }

        @Test
        @DisplayName("should return null for eform_link with only two numeric segments")
        void shouldReturnNull_onlyTwoNumericSegments() {
            assertThat(AddEForm2Action.validateEformLink("1_2_field")).isNull();
        }

        @Test
        @DisplayName("should return null for empty string eform_link")
        void shouldReturnNull_emptyString() {
            assertThat(AddEForm2Action.validateEformLink("")).isNull();
        }

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNull_nullInput() {
            assertThat(AddEForm2Action.validateEformLink(null)).isNull();
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
        @DisplayName("should return null for negative integer")
        void shouldReturnNull_forNegativeInteger() {
            // Entity IDs are auto-increment PKs — negative values are never valid
            assertThat(AddEForm2Action.validateIntId("-1")).isNull();
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

        @Test
        @DisplayName("should filter out negative integer IDs as invalid")
        void shouldFilterNegativeIds_asInvalid() {
            // Negative values are not valid auto-increment PKs
            String[] result = AddEForm2Action.validateIntIdArray(new String[]{"1", "-5", "3"});
            assertThat(result).containsExactly("1", "3");
        }
    }

    // -------------------------------------------------------------------------
    // Session sink — addEmailAttachmentsToSession
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("addEmailAttachmentsToSession — session sink validation")
    class AddEmailAttachmentsToSessionTests {

        private HttpSession mockSession;
        private HttpServletRequest mockRequest;
        private AddEForm2Action action;

        @BeforeEach
        void setUpActionWithMocks() {
            // Register all Spring beans used in AddEForm2Action field initializers
            registerMock(SecurityInfoManager.class, Mockito.mock(SecurityInfoManager.class));
            registerMock(EformDataManager.class, Mockito.mock(EformDataManager.class));
            registerMock(DocumentAttachmentManager.class, Mockito.mock(DocumentAttachmentManager.class));
            registerMock(EmailManager.class, Mockito.mock(EmailManager.class));

            // Create action (ServletActionContext returns null for request/response outside Struts2,
            // but addEmailAttachmentsToSession takes request as a parameter so this.request is unused)
            action = new AddEForm2Action();

            mockSession = Mockito.mock(HttpSession.class);
            mockRequest = Mockito.mock(HttpServletRequest.class);
            Mockito.when(mockRequest.getSession()).thenReturn(mockSession);
        }

        @Test
        @DisplayName("should store canonicalized fdid when fdid has leading zeros")
        void shouldStoreCanonicalized_whenFdidHasLeadingZeros() {
            EmailAttachmentSettings settings = minimalSettings("007", "100");
            action.addEmailAttachmentsToSession(mockRequest, settings);
            // "007" must be stored as canonical "7", not the raw input
            Mockito.verify(mockSession).setAttribute("fdid", "7");
        }

        @Test
        @DisplayName("should store null for fdid when fdid is a negative integer")
        void shouldStoreNull_whenFdidIsNegative() {
            EmailAttachmentSettings settings = minimalSettings("-1", "100");
            action.addEmailAttachmentsToSession(mockRequest, settings);
            Mockito.verify(mockSession).setAttribute("fdid", null);
        }

        @Test
        @DisplayName("should store empty array for attachedEForms when all values are non-integer")
        void shouldStoreEmptyArray_whenAllAttachedEFormsAreInvalid() {
            EmailAttachmentSettings settings = new EmailAttachmentSettings(
                "100", "200",
                new String[]{"abc", "1 DROP TABLE"}, // invalid
                new String[0], new String[0], new String[0], new String[0],
                false, false, false, false, false, false,
                null, null, null, null, null, null, null
            );
            action.addEmailAttachmentsToSession(mockRequest, settings);

            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            Mockito.verify(mockSession).setAttribute(eq("attachedEForms"), captor.capture());
            assertThat((String[]) captor.getValue()).isEmpty();
        }

        @Test
        @DisplayName("should store only valid IDs when attachedEForms array contains mixed values")
        void shouldStoreOnlyValidIds_whenAttachedEFormsAreMixed() {
            EmailAttachmentSettings settings = new EmailAttachmentSettings(
                "100", "200",
                new String[]{"5", "abc", "007", "-3"}, // mixed: 2 valid, 2 invalid
                new String[0], new String[0], new String[0], new String[0],
                false, false, false, false, false, false,
                null, null, null, null, null, null, null
            );
            action.addEmailAttachmentsToSession(mockRequest, settings);

            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            Mockito.verify(mockSession).setAttribute(eq("attachedEForms"), captor.capture());
            assertThat((String[]) captor.getValue()).containsExactly("5", "7");
        }

        /**
         * Creates a minimal EmailAttachmentSettings with only fdid and demographicNo set;
         * all arrays are empty and all flags/strings are false/null.
         */
        private EmailAttachmentSettings minimalSettings(String fdid, String demographicNo) {
            return new EmailAttachmentSettings(
                fdid, demographicNo,
                new String[0], new String[0], new String[0], new String[0], new String[0],
                false, false, false, false, false, false,
                null, null, null, null, null, null, null
            );
        }
    }
}
