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
 * <p>Verifies log injection prevention, truncation behaviour, null safety, and the
 * {@link LogSanitizer#sanitizeObject(Object)} fallback for non-String inputs.
 *
 * @see LogSanitizer
 * @since 2026-04-10
 */
@DisplayName("LogSanitizer")
@Tag("unit")
@Tag("fast")
@Tag("security")
public class LogSanitizerUnitTest {

    @Nested
    @DisplayName("sanitize(String) — null and blank inputs")
    class NullAndBlankInputs {

        @Test
        @DisplayName("should return literal 'null' string when input is null")
        void shouldReturnLiteralNull_whenInputIsNull() {
            assertThat(LogSanitizer.sanitize(null)).isEqualTo("null");
        }

        @Test
        @DisplayName("should return empty string when input is empty")
        void shouldReturnEmptyString_whenInputIsEmpty() {
            assertThat(LogSanitizer.sanitize("")).isEqualTo("");
        }

        @Test
        @DisplayName("should return original value when input contains only spaces")
        void shouldReturnSpaces_whenInputIsWhitespaceOnly() {
            assertThat(LogSanitizer.sanitize("   ")).isEqualTo("   ");
        }
    }

    @Nested
    @DisplayName("sanitize(String) — log injection prevention")
    class LogInjectionPrevention {

        @Test
        @DisplayName("should escape newline characters to prevent log injection")
        void shouldEscapeNewlines_toPreventLogInjection() {
            String result = LogSanitizer.sanitize("safe\nINJECTED LOG LINE");
            assertThat(result).doesNotContain("\n");
            assertThat(result).contains("\\n");
        }

        @Test
        @DisplayName("should escape carriage return characters")
        void shouldEscapeCarriageReturn_whenPresent() {
            String result = LogSanitizer.sanitize("value\r\ninjected");
            assertThat(result).doesNotContain("\r").doesNotContain("\n");
        }

        @Test
        @DisplayName("should escape tab characters")
        void shouldEscapeTabCharacters_whenPresent() {
            String result = LogSanitizer.sanitize("col1\tcol2");
            assertThat(result).doesNotContain("\t");
        }

        @Test
        @DisplayName("should not modify safe alphanumeric input")
        void shouldNotModifySafeInput_whenAlphanumeric() {
            String safe = "safeFormName123";
            assertThat(LogSanitizer.sanitize(safe)).isEqualTo(safe);
        }

        @Test
        @DisplayName("should not modify safe file path input")
        void shouldNotModifyFilePath_whenNoControlChars() {
            String path = "/var/oscar/eforms/template.pdf";
            assertThat(LogSanitizer.sanitize(path)).isEqualTo(path);
        }
    }

    @Nested
    @DisplayName("sanitize(String) — truncation")
    class Truncation {

        @Test
        @DisplayName("should not truncate when input is exactly at the default limit")
        void shouldNotTruncate_whenInputEqualsDefaultMaxLength() {
            String input = "a".repeat(LogSanitizer.DEFAULT_MAX_LENGTH);
            String result = LogSanitizer.sanitize(input);
            assertThat(result).doesNotEndWith("...");
            assertThat(result).hasSize(LogSanitizer.DEFAULT_MAX_LENGTH);
        }

        @Test
        @DisplayName("should truncate and append '...' when input exceeds the default limit")
        void shouldTruncateWithEllipsis_whenInputExceedsDefaultMaxLength() {
            String input = "a".repeat(LogSanitizer.DEFAULT_MAX_LENGTH + 50);
            String result = LogSanitizer.sanitize(input);
            assertThat(result).endsWith("...");
            assertThat(result.length()).isLessThanOrEqualTo(LogSanitizer.MAX_ENCODED_LENGTH + 3);
        }

        @Test
        @DisplayName("should truncate to custom length when custom maxLength is provided")
        void shouldTruncateToCustomLength_whenCustomMaxLengthProvided() {
            String input = "a".repeat(50);
            String result = LogSanitizer.sanitize(input, 10);
            assertThat(result).endsWith("...");
            // encoded 10 'a' chars = "aaaaaaaaaa", plus "..." = 13 chars
            assertThat(result).isEqualTo("aaaaaaaaaa...");
        }

        @Test
        @DisplayName("should use default max length when custom maxLength is zero or negative")
        void shouldUseDefaultMaxLength_whenCustomMaxLengthIsNonPositive() {
            String shortInput = "hello";
            // Should not truncate a short value with invalid maxLength fallback
            assertThat(LogSanitizer.sanitize(shortInput, 0)).isEqualTo("hello");
            assertThat(LogSanitizer.sanitize(shortInput, -1)).isEqualTo("hello");
        }

        @Test
        @DisplayName("should cap post-encoding output to prevent log flooding from adversarial input")
        void shouldCapEncodedOutput_whenAdversarialInputExpandsExcessively() {
            // Non-ASCII chars encoded as \uXXXX expand up to 6x.
            // Feed 200 non-ASCII chars → raw input is truncated to 200, but encoding
            // could produce up to 200*6=1200 chars. The output must be capped.
            String adversarial = "\u00e9".repeat(LogSanitizer.DEFAULT_MAX_LENGTH);
            String result = LogSanitizer.sanitize(adversarial);
            assertThat(result.length()).isLessThanOrEqualTo(LogSanitizer.MAX_ENCODED_LENGTH + 3);
        }
    }

    @Nested
    @DisplayName("sanitizeObject(Object)")
    class SanitizeObject {

        @Test
        @DisplayName("should return literal 'null' when input object is null")
        void shouldReturnLiteralNull_whenObjectIsNull() {
            assertThat(LogSanitizer.sanitizeObject(null)).isEqualTo("null");
        }

        @Test
        @DisplayName("should sanitize the toString() result of a plain object")
        void shouldSanitizeToStringResult_forPlainObject() {
            Object obj = new Object() {
                @Override
                public String toString() {
                    return "safe-value";
                }
            };
            assertThat(LogSanitizer.sanitizeObject(obj)).isEqualTo("safe-value");
        }

        @Test
        @DisplayName("should escape control chars in toString() output")
        void shouldEscapeControlChars_inObjectToStringResult() {
            Object obj = new Object() {
                @Override
                public String toString() {
                    return "value\nwith\nnewlines";
                }
            };
            String result = LogSanitizer.sanitizeObject(obj);
            assertThat(result).doesNotContain("\n");
        }

        @Test
        @DisplayName("should return safe fallback when toString() throws an exception")
        void shouldReturnSafeFallback_whenToStringThrowsException() {
            Object obj = new Object() {
                @Override
                public String toString() {
                    throw new RuntimeException("toString() exploded");
                }
            };
            String result = LogSanitizer.sanitizeObject(obj);
            assertThat(result).startsWith("[toString() failed:");
            assertThat(result).contains("RuntimeException");
        }

        @Test
        @DisplayName("should return safe fallback when toString() causes StackOverflowError")
        void shouldReturnSafeFallback_whenToStringCausesStackOverflow() {
            Object obj = new Object() {
                @Override
                public String toString() {
                    // Simulate recursive toString
                    throw new StackOverflowError();
                }
            };
            String result = LogSanitizer.sanitizeObject(obj);
            assertThat(result).startsWith("[toString() failed:");
            assertThat(result).contains("StackOverflowError");
        }

        @Test
        @DisplayName("should sanitize Integer toString representation")
        void shouldSanitizeInteger_whenPassedAsObject() {
            assertThat(LogSanitizer.sanitizeObject(42)).isEqualTo("42");
        }
    }
}
