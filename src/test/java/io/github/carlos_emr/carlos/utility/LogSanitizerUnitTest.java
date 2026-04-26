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

import io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException;
/**
 * Unit tests for {@link LogSanitizer}.
 *
 * <p>Verifies log injection prevention, truncation behaviour, null safety, and the
 * {@link LogSanitizer#sanitizeObject(Object)} fallback for non-String inputs.
 *
 * <p>LogSanitizer is a pure utility with no dependencies beyond OWASP Encoder,
 * so these tests run standalone without Spring context or mocking.</p>
 *
 * @see LogSanitizer
 * @since 2026-04-03
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
        @DisplayName("should return literal 'null' string when input is null")
        void shouldReturnLiteralNull_whenInputIsNull() {
            assertThat(LogSanitizer.sanitize((String) null)).isEqualTo("null");
        }

        @Test
        @DisplayName("should return empty string when input is empty")
        void shouldReturnEmptyString_whenInputIsEmpty() {
            assertThat(LogSanitizer.sanitize("")).isEmpty();
        }

        @Test
        @DisplayName("should return original value when input contains only spaces")
        void shouldReturnSpaces_whenInputIsWhitespaceOnly() {
            assertThat(LogSanitizer.sanitize("   ")).isEqualTo("   ");
        }

        @Test
        @DisplayName("should pass through safe strings without modification")
        void shouldPassThroughSafeStrings_withoutModification() {
            String safe = "Hello, world! 123 test@example.com";
            assertThat(LogSanitizer.sanitize(safe)).isEqualTo(safe);
        }

        @Test
        @DisplayName("should not modify safe file path input")
        void shouldNotModifyFilePath_whenNoControlChars() {
            String path = "/var/oscar/eforms/template.pdf";
            assertThat(LogSanitizer.sanitize(path)).isEqualTo(path);
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
        @DisplayName("should stay within encoded bound when control chars expand input")
        void shouldStayWithinEncodedBound_whenControlCharsExpandInput() {
            // 100 newlines: each \n encodes to \\n (2 chars) = 200 encoded chars
            // Post-encoding bound is 100 * 6 = 600, so 200 < 600 — no post-encoding truncation
            String manyNewlines = "\n".repeat(100);
            String sanitized = LogSanitizer.sanitize(manyNewlines, 100);

            assertThat(sanitized.length()).isEqualTo(200);
            assertThat(sanitized).doesNotEndWith("...");
        }

        @Test
        @DisplayName("should cap post-encoding output to prevent log flooding from adversarial input")
        void shouldCapEncodedOutput_whenAdversarialInputExpandsExcessively() {
            // Non-ASCII chars encoded as \\uXXXX expand up to 6x.
            // Feed 200 non-ASCII chars → raw input is truncated to 200, but encoding
            // could produce up to 200*6=1200 chars. The output must be capped.
            String adversarial = "\u00e9".repeat(LogSanitizer.DEFAULT_MAX_LENGTH);
            String result = LogSanitizer.sanitize(adversarial);
            assertThat(result.length()).isLessThanOrEqualTo(LogSanitizer.MAX_ENCODED_LENGTH + 3);
        }

        @Test
        @DisplayName("should encode accented characters used in bilingual Canadian names")
        void shouldEncodeAccentedCharacters_whenInputContainsFrenchNames() {
            String french = "Ren\u00e9 B\u00e9langer";
            String sanitized = LogSanitizer.sanitize(french);

            // Encode.forJava() encodes Latin-1 non-ASCII chars to octal escapes (e.g. \351)
            assertThat(sanitized).doesNotContain("\u00e9");
            assertThat(sanitized).contains("\\351");
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

        @Test
        @DisplayName("should fall back to default max length when maxLength is zero")
        void shouldFallBackToDefault_whenMaxLengthIsZero() {
            String input = "a".repeat(LogSanitizer.DEFAULT_MAX_LENGTH + 50);
            String sanitized = LogSanitizer.sanitize(input, 0);

            // Should use DEFAULT_MAX_LENGTH as fallback
            assertThat(sanitized).endsWith("...");
            assertThat(sanitized).hasSize(LogSanitizer.DEFAULT_MAX_LENGTH + 3);
        }

        @Test
        @DisplayName("should fall back to default max length when maxLength is negative")
        void shouldFallBackToDefault_whenMaxLengthIsNegative() {
            String input = "a".repeat(LogSanitizer.DEFAULT_MAX_LENGTH + 50);
            String sanitized = LogSanitizer.sanitize(input, -1);

            // Should use DEFAULT_MAX_LENGTH as fallback, not throw
            assertThat(sanitized).endsWith("...");
            assertThat(sanitized).hasSize(LogSanitizer.DEFAULT_MAX_LENGTH + 3);
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
        @DisplayName("should delegate to String overload for normal objects")
        void shouldDelegateToStringOverload_whenObjectInputProvided() {
            Integer number = 42;
            assertThat(LogSanitizer.sanitizeObject(number)).isEqualTo("42");
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
        @DisplayName("should sanitize object's toString output for CRLF")
        void shouldSanitizeObjectToString_forCrlf() {
            Object injectable = new Object() {
                @Override
                public String toString() {
                    return "value\r\nINJECTED";
                }
            };

            String sanitized = LogSanitizer.sanitizeObject(injectable);

            assertThat(sanitized).doesNotContain("\r");
            assertThat(sanitized).doesNotContain("\n");
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
        @DisplayName("should include exception type in fallback for diagnostics")
        void shouldIncludeExceptionType_inFallbackMessage() {
            Object npe = new Object() {
                @Override
                public String toString() {
                    throw new NullPointerException();
                }
            };

            String sanitized = LogSanitizer.sanitizeObject(npe);

            assertThat(sanitized).contains("NullPointerException");
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

    /**
     * Tests for {@link LogSanitizer#sanitizeForDisplay(String)} — the
     * variant used in user-facing {@link io.github.carlos_emr.carlos
     * .billings.ca.on.validator.BillingValidationException} messages.
     *
     * <p>Contract distinguishing it from {@code sanitize(...)}:
     * <ul>
     *   <li>printable characters appear as themselves (no Java escaping)</li>
     *   <li>quotes / backslashes / non-ASCII pass through unchanged</li>
     *   <li>ASCII control chars are stripped (CRLF / NUL etc.)</li>
     *   <li>truncates beyond the default length with a {@code "..."} suffix</li>
     *   <li>null returns the literal {@code "null"}, never null itself</li>
     * </ul>
     */
    @Nested
    @DisplayName("sanitizeForDisplay()")
    class SanitizeForDisplayTests {

        @Test
        @DisplayName("should return literal \"null\" when input is null")
        void shouldReturnNullLiteral_whenInputIsNull() {
            assertThat(LogSanitizer.sanitizeForDisplay(null)).isEqualTo("null");
        }

        @Test
        @DisplayName("should return empty string when input is empty")
        void shouldReturnEmpty_whenInputIsEmpty() {
            assertThat(LogSanitizer.sanitizeForDisplay("")).isEmpty();
        }

        @Test
        @DisplayName("should preserve printable ASCII unchanged")
        void shouldPreservePrintableAscii_unchanged() {
            assertThat(LogSanitizer.sanitizeForDisplay("Bill #12345 rejected"))
                    .isEqualTo("Bill #12345 rejected");
        }

        @Test
        @DisplayName("should preserve quotes and backslashes literally (unlike sanitize)")
        void shouldPreserveQuotesAndBackslashes_unescaped() {
            // sanitize() would Java-escape these to \" and \\ — sanitizeForDisplay
            // explicitly does not, so the operator sees the literal characters
            // they typed.
            assertThat(LogSanitizer.sanitizeForDisplay("foo \"bar\" \\baz"))
                    .isEqualTo("foo \"bar\" \\baz");
        }

        @Test
        @DisplayName("should preserve non-ASCII characters as themselves")
        void shouldPreserveNonAscii_asThemselves() {
            assertThat(LogSanitizer.sanitizeForDisplay("café résumé naïve 日本語"))
                    .isEqualTo("café résumé naïve 日本語");
        }

        @Test
        @DisplayName("should strip CRLF / NUL / other ASCII control characters")
        void shouldStripControlCharacters_fromInput() {
            String input = "before\r\nafter\u0000\u0001end";
            assertThat(LogSanitizer.sanitizeForDisplay(input)).isEqualTo("beforeafterend");
        }

        @Test
        @DisplayName("should truncate with ellipsis when input exceeds default length")
        void shouldTruncate_whenInputExceedsLimit() {
            // Build a 300-char ASCII payload; default truncation is
            // DEFAULT_MAX_LENGTH (200), then "..." is appended.
            String big = "A".repeat(300);
            String out = LogSanitizer.sanitizeForDisplay(big);
            assertThat(out).hasSize(LogSanitizer.DEFAULT_MAX_LENGTH + 3);
            assertThat(out).startsWith("AAA");
            assertThat(out).endsWith("...");
        }

        @Test
        @DisplayName("should not truncate input at exactly default-length boundary")
        void shouldNotTruncate_whenInputAtBoundary() {
            String exact = "A".repeat(LogSanitizer.DEFAULT_MAX_LENGTH);
            assertThat(LogSanitizer.sanitizeForDisplay(exact)).isEqualTo(exact);
        }
    }
}
