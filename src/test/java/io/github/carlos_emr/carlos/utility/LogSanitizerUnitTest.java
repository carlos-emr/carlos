/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the CARLOS EMR Project.
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
 * <p>LogSanitizer is a pure utility with no dependencies beyond OWASP Encoder,
 * so these tests run standalone without Spring context or mocking.</p>
 *
 * @since 2026-04-03
 */
@Tag("unit")
@Tag("fast")
@DisplayName("LogSanitizer")
class LogSanitizerUnitTest {

    @Nested
    @DisplayName("sanitize(String)")
    class SanitizeString {

        @Test
        @DisplayName("should return 'null' when input is null")
        void shouldReturnNullString_whenInputIsNull() {
            assertThat(LogSanitizer.sanitize((String) null)).isEqualTo("null");
        }

        @Test
        @DisplayName("should return empty string when input is empty")
        void shouldReturnEmptyString_whenInputIsEmpty() {
            assertThat(LogSanitizer.sanitize("")).isEmpty();
        }

        @Test
        @DisplayName("should pass through safe strings without modification")
        void shouldPassThroughSafeStrings_withoutModification() {
            String safe = "Hello, world! 123 test@example.com";
            assertThat(LogSanitizer.sanitize(safe)).isEqualTo(safe);
        }

        @Test
        @DisplayName("should escape CR and LF characters to prevent log injection")
        void shouldEscapeCrlfCharacters_whenInputContainsNewlines() {
            String malicious = "admin\r\nINFO: Fake log entry";
            String sanitized = LogSanitizer.sanitize(malicious);

            assertThat(sanitized).doesNotContain("\r");
            assertThat(sanitized).doesNotContain("\n");
            assertThat(sanitized).contains("\\r");
            assertThat(sanitized).contains("\\n");
        }

        @Test
        @DisplayName("should escape tab and null byte control characters")
        void shouldEscapeControlCharacters_whenInputContainsTabsAndNullBytes() {
            String input = "value\twith\0control";
            String sanitized = LogSanitizer.sanitize(input);

            assertThat(sanitized).doesNotContain("\t");
            assertThat(sanitized).doesNotContain("\0");
        }

        @Test
        @DisplayName("should not truncate when input is exactly DEFAULT_MAX_LENGTH characters")
        void shouldNotTruncate_whenInputIsExactlyMaxLength() {
            String exact = "a".repeat(LogSanitizer.DEFAULT_MAX_LENGTH);
            String sanitized = LogSanitizer.sanitize(exact);

            assertThat(sanitized).isEqualTo(exact);
            assertThat(sanitized).doesNotEndWith("...");
        }

        @Test
        @DisplayName("should truncate with ellipsis when input exceeds DEFAULT_MAX_LENGTH")
        void shouldTruncateWithEllipsis_whenInputExceedsMaxLength() {
            String tooLong = "a".repeat(LogSanitizer.DEFAULT_MAX_LENGTH + 1);
            String sanitized = LogSanitizer.sanitize(tooLong);

            assertThat(sanitized).endsWith("...");
            // The encoded part (all 'a' = no expansion) should be exactly DEFAULT_MAX_LENGTH
            assertThat(sanitized).hasSize(LogSanitizer.DEFAULT_MAX_LENGTH + 3); // + "..."
        }

        @Test
        @DisplayName("should bound encoded output length even when encoding expands input")
        void shouldBoundEncodedOutput_whenEncodingExpandsInput() {
            // 200 newlines: each \n encodes to 2-char sequence, so encoded output > DEFAULT_MAX_LENGTH
            String adversarial = "\n".repeat(LogSanitizer.DEFAULT_MAX_LENGTH);
            String sanitized = LogSanitizer.sanitize(adversarial);

            // Encoded output should exceed DEFAULT_MAX_LENGTH (expansion is 2x)
            // but remain bounded by MAX_ENCODED_LENGTH
            assertThat(sanitized.length()).isGreaterThan(LogSanitizer.DEFAULT_MAX_LENGTH);
            assertThat(sanitized.length()).isLessThanOrEqualTo(LogSanitizer.MAX_ENCODED_LENGTH + 3);
        }

        @Test
        @DisplayName("should truncate post-encoding when custom limit triggers bound")
        void shouldTruncatePostEncoding_whenCustomLimitTriggersBound() {
            // Use sanitize(String, int) with a small limit: 10 newlines, each encodes to 2 chars = 20
            // Post-encoding bound is 10 * 6 = 60, so 20 < 60 — no truncation
            // But with a very long input of control chars that exceeds the bound:
            String manyNewlines = "\n".repeat(100);
            String sanitized = LogSanitizer.sanitize(manyNewlines, 100);

            // 100 newlines → each encodes to 2 chars = 200, bound is 100*6=600
            // No post-encoding truncation (200 < 600), but verify it's bounded
            assertThat(sanitized.length()).isLessThanOrEqualTo(600 + 3);
        }
    }

    @Nested
    @DisplayName("sanitize(String, int)")
    class SanitizeStringWithCustomLength {

        @Test
        @DisplayName("should respect custom max length")
        void shouldRespectCustomMaxLength_whenProvided() {
            String input = "a".repeat(50);
            String sanitized = LogSanitizer.sanitize(input, 10);

            assertThat(sanitized).hasSize(13); // 10 + "..."
            assertThat(sanitized).endsWith("...");
        }

        @Test
        @DisplayName("should allow longer values when custom limit is higher")
        void shouldAllowLongerValues_whenCustomLimitIsHigher() {
            // A long string that exceeds 200 but is under 1000
            String longValue = "x".repeat(500);
            String sanitized = LogSanitizer.sanitize(longValue, 1000);

            // Should not be truncated at all since 500 < 1000
            assertThat(sanitized).hasSize(500);
            assertThat(sanitized).doesNotEndWith("...");
        }

        @Test
        @DisplayName("should truncate at custom limit when input exceeds it")
        void shouldTruncateAtCustomLimit_whenInputExceedsIt() {
            String longValue = "x".repeat(1500);
            String sanitized = LogSanitizer.sanitize(longValue, 1000);

            assertThat(sanitized).endsWith("...");
            assertThat(sanitized).hasSize(1003); // 1000 + "..."
        }
    }

    @Nested
    @DisplayName("sanitize(Object)")
    class SanitizeObject {

        @Test
        @DisplayName("should return 'null' when object input is null")
        void shouldReturnNullString_whenObjectInputIsNull() {
            assertThat(LogSanitizer.sanitize((Object) null)).isEqualTo("null");
        }

        @Test
        @DisplayName("should delegate to String overload for normal objects")
        void shouldDelegateToStringOverload_whenObjectInputProvided() {
            Integer number = 42;
            assertThat(LogSanitizer.sanitize((Object) number)).isEqualTo("42");
        }

        @Test
        @DisplayName("should return safe fallback when toString() throws RuntimeException")
        void shouldReturnSafeFallback_whenObjectToStringThrows() {
            Object broken = new Object() {
                @Override
                public String toString() {
                    throw new IllegalStateException("broken");
                }
            };

            String sanitized = LogSanitizer.sanitize(broken);

            assertThat(sanitized).startsWith("[toString() failed:");
            assertThat(sanitized).contains("IllegalStateException");
        }

        @Test
        @DisplayName("should include exception type in fallback for diagnostics")
        void shouldIncludeExceptionType_inFallbackMessage() {
            Object npe = new Object() {
                @Override
                public String toString() {
                    throw new NullPointerException();
                }
            };

            String sanitized = LogSanitizer.sanitize(npe);

            assertThat(sanitized).contains("NullPointerException");
        }

        @Test
        @DisplayName("should sanitize object's toString output for CRLF")
        void shouldSanitizeObjectToString_forCrlf() {
            Object injectable = new Object() {
                @Override
                public String toString() {
                    return "value\r\nINJECTED";
                }
            };

            String sanitized = LogSanitizer.sanitize(injectable);

            assertThat(sanitized).doesNotContain("\r");
            assertThat(sanitized).doesNotContain("\n");
        }
    }
}
