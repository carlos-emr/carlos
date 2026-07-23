/**
 * Copyright (c) 2026 CARLOS EMR Contributors. All Rights Reserved.
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
package io.github.carlos_emr.carlos.email.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link EmailAttachmentSettings} input validation methods.
 * Tests sanitization of raw user input before session storage.
 *
 * @since 2026-04-08
 */
@Tag("unit")
@Tag("fast")
@Tag("email")
@DisplayName("EmailAttachmentSettings validation")
class EmailAttachmentSettingsTest {

    @Test
    @DisplayName("should create settings from request with sanitized values")
    void shouldCreateSettingsFromRequest_withSanitizedValues() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("attachEFormToEmail", "false");
        request.setParameter("openEFormAfterSendingEmail", "true");
        request.setParameter("enableEmailEncryption", "true");
        request.setParameter("encryptEmailAttachments", "false");
        request.setParameter("autoSendEmail", "true");
        request.setParameter("deleteEFormAfterSendingEmail", "true");
        request.setParameter("senderEmail", "sender@example.com");
        request.setParameter("subjectEmail", "Subject\r\nBcc: attacker@example.com");
        request.setParameter("bodyEmail", "B".repeat(10005));
        request.setParameter("encryptedMessageEmail", "Encrypted".repeat(1500));
        request.setParameter("emailPatientChartOption", "doNotAddAsNote");

        EmailAttachmentSettings settings = EmailAttachmentSettings.of(
                request,
                "12",
                "123",
                new String[]{"1"},
                new String[]{"2"},
                new String[]{"3"},
                new String[]{"4"},
                new String[]{"5"});

        assertThat(settings.fdid()).isEqualTo("12");
        assertThat(settings.demographicNo()).isEqualTo("123");
        assertThat(settings.attachedEForms()).containsExactly("1");
        assertThat(settings.attachedDocuments()).containsExactly("2");
        assertThat(settings.attachedLabs()).containsExactly("3");
        assertThat(settings.attachedHRMDocuments()).containsExactly("4");
        assertThat(settings.attachedForms()).containsExactly("5");
        assertThat(settings.attachEFormItSelf()).isFalse();
        assertThat(settings.openAfterEmail()).isTrue();
        assertThat(settings.isEmailEncrypted()).isTrue();
        assertThat(settings.isEmailAttachmentEncrypted()).isFalse();
        assertThat(settings.isEmailAutoSend()).isTrue();
        assertThat(settings.deleteEFormAfterEmail()).isTrue();
        assertThat(settings.senderEmail()).isEqualTo("sender@example.com");
        assertThat(settings.subjectEmail()).isEqualTo("SubjectBcc: attacker@example.com");
        assertThat(settings.bodyEmail()).hasSize(10000);
        assertThat(settings.encryptedMessageEmail()).hasSize(10000);
        assertThat(settings.emailPatientChartOption()).isEqualTo("doNotAddAsNote");
    }

    @Nested
    @DisplayName("validateEmail")
    class ValidateEmail {

        @Test
        @DisplayName("should return email when valid format")
        void shouldReturnEmail_whenValidFormat() {
            assertThat(EmailAttachmentSettings.validateEmail("user@example.com")).isEqualTo("user@example.com");
        }

        @Test
        @DisplayName("should return email when complex valid format")
        void shouldReturnEmail_whenComplexValidFormat() {
            assertThat(EmailAttachmentSettings.validateEmail("user.name+tag@sub.domain.co")).isEqualTo("user.name+tag@sub.domain.co");
        }

        @Test
        @DisplayName("should return null when null input")
        void shouldReturnNull_whenNullInput() {
            assertThat(EmailAttachmentSettings.validateEmail(null)).isNull();
        }

        @Test
        @DisplayName("should return null when missing at sign")
        void shouldReturnNull_whenMissingAtSign() {
            assertThat(EmailAttachmentSettings.validateEmail("userexample.com")).isNull();
        }

        @Test
        @DisplayName("should return null when missing domain")
        void shouldReturnNull_whenMissingDomain() {
            assertThat(EmailAttachmentSettings.validateEmail("user@")).isNull();
        }

        @Test
        @DisplayName("should return null when empty string")
        void shouldReturnNull_whenEmptyString() {
            assertThat(EmailAttachmentSettings.validateEmail("")).isNull();
        }

        @Test
        @DisplayName("should return null when contains spaces")
        void shouldReturnNull_whenContainsSpaces() {
            assertThat(EmailAttachmentSettings.validateEmail("user @example.com")).isNull();
        }

        @Test
        @DisplayName("should return null when contains angle brackets")
        void shouldReturnNull_whenContainsAngleBrackets() {
            assertThat(EmailAttachmentSettings.validateEmail("<script>@example.com")).isNull();
        }

        @Test
        @DisplayName("should return null when exceeding RFC 5321 length limit")
        void shouldReturnNull_whenExceedingMaxLength() {
            String longLocal = "a".repeat(245);
            String longEmail = longLocal + "@example.com";
            assertThat(longEmail.length()).isGreaterThan(254);
            assertThat(EmailAttachmentSettings.validateEmail(longEmail)).isNull();
        }

        @Test
        @DisplayName("should return email when at RFC 5321 length limit")
        void shouldReturnEmail_whenAtMaxLength() {
            String local = "a".repeat(242);
            String email = local + "@example.com";
            assertThat(email.length()).isEqualTo(254);
            assertThat(EmailAttachmentSettings.validateEmail(email)).isEqualTo(email);
        }
    }

    @Nested
    @DisplayName("sanitizeSubject")
    class SanitizeSubject {

        @Test
        @DisplayName("should return subject when valid input")
        void shouldReturnSubject_whenValidInput() {
            assertThat(EmailAttachmentSettings.sanitizeSubject("Test Subject")).isEqualTo("Test Subject");
        }

        @Test
        @DisplayName("should return null when null input")
        void shouldReturnNull_whenNullInput() {
            assertThat(EmailAttachmentSettings.sanitizeSubject(null)).isNull();
        }

        @Test
        @DisplayName("should strip carriage return characters")
        void shouldStripCR_whenPresent() {
            assertThat(EmailAttachmentSettings.sanitizeSubject("Line1\rLine2")).isEqualTo("Line1Line2");
        }

        @Test
        @DisplayName("should strip newline characters")
        void shouldStripLF_whenPresent() {
            assertThat(EmailAttachmentSettings.sanitizeSubject("Line1\nLine2")).isEqualTo("Line1Line2");
        }

        @Test
        @DisplayName("should strip CRLF to prevent SMTP header injection")
        void shouldStripCRLF_whenPresent() {
            assertThat(EmailAttachmentSettings.sanitizeSubject("Subject\r\nBcc: attacker@evil.com")).isEqualTo("SubjectBcc: attacker@evil.com");
        }

        @Test
        @DisplayName("should strip Unicode NEL character")
        void shouldStripNEL_whenPresent() {
            assertThat(EmailAttachmentSettings.sanitizeSubject("Line1\u0085Line2")).isEqualTo("Line1Line2");
        }

        @Test
        @DisplayName("should strip Unicode line separator")
        void shouldStripLineSeparator_whenPresent() {
            assertThat(EmailAttachmentSettings.sanitizeSubject("Line1\u2028Line2")).isEqualTo("Line1Line2");
        }

        @Test
        @DisplayName("should strip Unicode paragraph separator")
        void shouldStripParagraphSeparator_whenPresent() {
            assertThat(EmailAttachmentSettings.sanitizeSubject("Line1\u2029Line2")).isEqualTo("Line1Line2");
        }

        @Test
        @DisplayName("should truncate when exceeding max length")
        void shouldTruncate_whenExceedingMaxLength() {
            String longSubject = "A".repeat(250);
            String result = EmailAttachmentSettings.sanitizeSubject(longSubject);
            assertThat(result).hasSize(200);
        }

        @Test
        @DisplayName("should not truncate when within max length")
        void shouldNotTruncate_whenWithinMaxLength() {
            String subject = "A".repeat(200);
            assertThat(EmailAttachmentSettings.sanitizeSubject(subject)).hasSize(200);
        }
    }

    @Nested
    @DisplayName("truncate")
    class Truncate {

        @Test
        @DisplayName("should return value when within limit")
        void shouldReturnValue_whenWithinLimit() {
            assertThat(EmailAttachmentSettings.truncate("short text", 10000)).isEqualTo("short text");
        }

        @Test
        @DisplayName("should return null when null input")
        void shouldReturnNull_whenNullInput() {
            assertThat(EmailAttachmentSettings.truncate(null, 10000)).isNull();
        }

        @Test
        @DisplayName("should truncate when exceeding limit")
        void shouldTruncate_whenExceedingLimit() {
            String longBody = "B".repeat(15000);
            String result = EmailAttachmentSettings.truncate(longBody, 10000);
            assertThat(result).hasSize(10000);
        }

        @Test
        @DisplayName("should return exact length string unchanged")
        void shouldReturnUnchanged_whenExactLength() {
            String exact = "C".repeat(10000);
            assertThat(EmailAttachmentSettings.truncate(exact, 10000)).hasSize(10000);
        }
    }

    @Nested
    @DisplayName("validateChartOption")
    class ValidateChartOption {

        @Test
        @DisplayName("should return option when doNotAddAsNote")
        void shouldReturnOption_whenDoNotAddAsNote() {
            assertThat(EmailAttachmentSettings.validateChartOption("doNotAddAsNote")).isEqualTo("doNotAddAsNote");
        }

        @Test
        @DisplayName("should return option when addFullNote")
        void shouldReturnOption_whenAddFullNote() {
            assertThat(EmailAttachmentSettings.validateChartOption("addFullNote")).isEqualTo("addFullNote");
        }

        @Test
        @DisplayName("should return null when null input")
        void shouldReturnNull_whenNullInput() {
            assertThat(EmailAttachmentSettings.validateChartOption(null)).isNull();
        }

        @Test
        @DisplayName("should return null when invalid option")
        void shouldReturnNull_whenInvalidOption() {
            assertThat(EmailAttachmentSettings.validateChartOption("maliciousValue")).isNull();
        }

        @Test
        @DisplayName("should return null when empty string")
        void shouldReturnNull_whenEmptyString() {
            assertThat(EmailAttachmentSettings.validateChartOption("")).isNull();
        }

        @Test
        @DisplayName("should return null when script injection attempt")
        void shouldReturnNull_whenScriptInjection() {
            assertThat(EmailAttachmentSettings.validateChartOption("<script>alert(1)</script>")).isNull();
        }
    }

    @Nested
    @DisplayName("attachment ID arrays")
    class AttachmentIdArrays {

        @Test
        @DisplayName("should defensively copy array inputs and accessors")
        void shouldDefensivelyCopyAttachmentArrays() {
            String[] documents = {"10"};
            EmailAttachmentSettings settings = new EmailAttachmentSettings(
                    "1",
                    "123",
                    null,
                    documents,
                    null,
                    null,
                    null,
                    true,
                    false,
                    true,
                    true,
                    false,
                    false,
                    null,
                    null,
                    null,
                    null,
                    "addFullNote");

            documents[0] = "99";
            String[] returnedDocuments = settings.attachedDocuments();
            returnedDocuments[0] = "42";

            assertThat(settings.attachedDocuments()).containsExactly("10");
            assertThat(settings.attachedEForms()).isEmpty();
            assertThat(settings.attachedLabs()).isEmpty();
            assertThat(settings.attachedHRMDocuments()).isEmpty();
            assertThat(settings.attachedForms()).isEmpty();
        }
    }
}
