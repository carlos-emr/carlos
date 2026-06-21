/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
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
package io.github.carlos_emr.carlos.utility;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link LogSanitizer}.
 *
 * <p>Verifies that {@link LogSanitizer} correctly delegates to {@link LogSafe}
 * for all inputs, including null, long input, and inputs containing control characters.</p>
 *
 * <p>LogSanitizer is a deprecated transitional shim with no dependencies beyond LogSafe,
 * so these tests run standalone without Spring context or mocking.</p>
 *
 * @see LogSanitizer
 * @see LogSafe
 * @since 2026
 */
@Tag("unit")
@Tag("fast")
@Tag("security")
@DisplayName("LogSanitizer")
class LogSanitizerUnitTest {

    @Nested
    @DisplayName("sanitize(String)")
    class SanitizeString {

        @Test
        @DisplayName("should return literal 'null' when input is null")
        void shouldReturnLiteralNull_whenInputIsNull() {
            assertThat(LogSanitizer.sanitize(null)).isEqualTo("null");
        }

        @Test
        @DisplayName("should return empty string when input is empty")
        void shouldReturnEmptyString_whenInputIsEmpty() {
            assertThat(LogSanitizer.sanitize("")).isEmpty();
        }

        @Test
        @DisplayName("should delegate to LogSafe and return same result for normal input")
        void shouldDelegateToLogSafe_whenGivenNormalInput() {
            String input = "document.pdf";
            assertThat(LogSanitizer.sanitize(input)).isEqualTo(LogSafe.sanitize(input));
        }

        @Test
        @DisplayName("should escape CR and LF characters to prevent log injection")
        void shouldEscapeCrlfCharacters_whenInputContainsNewlines() {
            String malicious = "file\r\nINJECTED LOG LINE";
            String sanitized = LogSanitizer.sanitize(malicious);

            assertThat(sanitized).doesNotContain("\r");
            assertThat(sanitized).doesNotContain("\n");
            assertThat(sanitized).isEqualTo(LogSafe.sanitize(malicious));
        }

        @Test
        @DisplayName("should truncate with ellipsis and delegate when input exceeds default max length")
        void shouldTruncateWithEllipsis_whenInputExceedsDefaultMaxLength() {
            String longInput = "a".repeat(LogSafe.DEFAULT_MAX_LENGTH + 1);
            String sanitized = LogSanitizer.sanitize(longInput);

            assertThat(sanitized).endsWith("...");
            assertThat(sanitized).isEqualTo(LogSafe.sanitize(longInput));
        }

        @Test
        @DisplayName("should not truncate when input is exactly default max length")
        void shouldNotTruncate_whenInputIsExactlyDefaultMaxLength() {
            String exact = "a".repeat(LogSafe.DEFAULT_MAX_LENGTH);
            assertThat(LogSanitizer.sanitize(exact))
                .isEqualTo(LogSafe.sanitize(exact))
                .doesNotEndWith("...");
        }
    }

    @Nested
    @DisplayName("sanitize(String, int)")
    class SanitizeStringWithCustomLength {

        @Test
        @DisplayName("should delegate to LogSafe with custom max length")
        void shouldDelegateToLogSafe_whenGivenCustomMaxLength() {
            String input = "my-file.pdf";
            assertThat(LogSanitizer.sanitize(input, 5))
                .isEqualTo(LogSafe.sanitize(input, 5))
                .endsWith("...");
        }

        @Test
        @DisplayName("should not truncate when input is within custom max length")
        void shouldNotTruncate_whenInputIsWithinCustomMaxLength() {
            String input = "short.pdf";
            assertThat(LogSanitizer.sanitize(input, 100))
                .isEqualTo(LogSafe.sanitize(input, 100))
                .doesNotEndWith("...");
        }

        @Test
        @DisplayName("should fall back to default max length when maxLength is zero")
        void shouldFallBackToDefault_whenMaxLengthIsZero() {
            String input = "a".repeat(LogSafe.DEFAULT_MAX_LENGTH + 50);
            assertThat(LogSanitizer.sanitize(input, 0))
                .isEqualTo(LogSafe.sanitize(input, 0))
                .endsWith("...");
        }

        @Test
        @DisplayName("should fall back to default max length when maxLength is negative")
        void shouldFallBackToDefault_whenMaxLengthIsNegative() {
            String input = "a".repeat(LogSafe.DEFAULT_MAX_LENGTH + 50);
            assertThat(LogSanitizer.sanitize(input, -1))
                .isEqualTo(LogSafe.sanitize(input, -1))
                .endsWith("...");
        }
    }
}
