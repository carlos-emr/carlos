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
    @DisplayName("sanitizePassword")
    class SanitizePassword {

        @Test
        @DisplayName("should return password when valid input")
        void shouldReturnPassword_whenValidInput() {
            assertThat(EmailAttachmentSettings.sanitizePassword("MyP@ssw0rd!")).isEqualTo("MyP@ssw0rd!");
        }

        @Test
        @DisplayName("should return null when null input")
        void shouldReturnNull_whenNullInput() {
            assertThat(EmailAttachmentSettings.sanitizePassword(null)).isNull();
        }

        @Test
        @DisplayName("should strip control characters")
        void shouldStripControlChars_whenPresent() {
            assertThat(EmailAttachmentSettings.sanitizePassword("pass\u0000word\u0007")).isEqualTo("password");
        }

        @Test
        @DisplayName("should strip tab and newline control characters")
        void shouldStripTabAndNewline_whenPresent() {
            assertThat(EmailAttachmentSettings.sanitizePassword("pass\tword\n")).isEqualTo("password");
        }

        @Test
        @DisplayName("should truncate when exceeding max length")
        void shouldTruncate_whenExceedingMaxLength() {
            String longPassword = "A".repeat(150);
            String result = EmailAttachmentSettings.sanitizePassword(longPassword);
            assertThat(result).hasSize(100);
        }

        @Test
        @DisplayName("should not truncate when within max length")
        void shouldNotTruncate_whenWithinMaxLength() {
            String password = "A".repeat(100);
            assertThat(EmailAttachmentSettings.sanitizePassword(password)).hasSize(100);
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
}
