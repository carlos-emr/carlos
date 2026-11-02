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
 * Maintained by the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.util;

import org.junit.jupiter.api.*;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link StringUtils} string manipulation utilities.
 *
 * @since 2026-03-31
 */
@DisplayName("StringUtils Unit Tests")
@Tag("unit")
@Tag("fast")
@Tag("utility")
class StringUtilsUnitTest {

    @Nested
    @DisplayName("maxLenString")
    class MaxLenString {

        @Test
        @DisplayName("should truncate long string with added suffix")
        void shouldTruncate_withSuffix() {
            String result = StringUtils.maxLenString("BENZOICUM ACIDUM 1CH - 30CH", 13, 8, "...");
            assertThat(result).isEqualTo("BENZOICU...");
        }

        @Test
        @DisplayName("should return original when shorter than max")
        void shouldReturnOriginal_whenShorterThanMax() {
            String result = StringUtils.maxLenString("short", 20, 10, "...");
            assertThat(result).isEqualTo("short");
        }

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNull_forNullInput() {
            assertThat(StringUtils.maxLenString(null, 10, 5, "...")).isNull();
        }
    }

    @Nested
    @DisplayName("isNullOrEmpty")
    class IsNullOrEmpty {

        @Test
        @DisplayName("should return true for null")
        void shouldReturnTrue_forNull() {
            assertThat(StringUtils.isNullOrEmpty(null)).isTrue();
        }

        @Test
        @DisplayName("should return true for empty string")
        void shouldReturnTrue_forEmpty() {
            assertThat(StringUtils.isNullOrEmpty("")).isTrue();
        }

        @Test
        @DisplayName("should return true for whitespace only")
        void shouldReturnTrue_forWhitespace() {
            assertThat(StringUtils.isNullOrEmpty("   ")).isTrue();
        }

        @Test
        @DisplayName("should return true for literal 'null' string")
        void shouldReturnTrue_forNullLiteral() {
            assertThat(StringUtils.isNullOrEmpty("null")).isTrue();
            assertThat(StringUtils.isNullOrEmpty("NULL")).isTrue();
        }

        @Test
        @DisplayName("should return false for valid string")
        void shouldReturnFalse_forValidString() {
            assertThat(StringUtils.isNullOrEmpty("hello")).isFalse();
        }
    }

    @Nested
    @DisplayName("isNumeric")
    class IsNumeric {

        @Test
        @DisplayName("should return true for integer string")
        void shouldReturnTrue_forInteger() {
            assertThat(StringUtils.isNumeric("42")).isTrue();
        }

        @Test
        @DisplayName("should return true for decimal string")
        void shouldReturnTrue_forDecimal() {
            assertThat(StringUtils.isNumeric("3.14")).isTrue();
        }

        @Test
        @DisplayName("should return true for negative number")
        void shouldReturnTrue_forNegative() {
            assertThat(StringUtils.isNumeric("-7")).isTrue();
        }

        @Test
        @DisplayName("should return false for non-numeric")
        void shouldReturnFalse_forNonNumeric() {
            assertThat(StringUtils.isNumeric("abc")).isFalse();
        }

        @Test
        @DisplayName("should return false for null")
        void shouldReturnFalse_forNull() {
            assertThat(StringUtils.isNumeric(null)).isFalse();
        }
    }

    @Test
    void shouldAcceptDoubleSyntax_forLegacyNumericValues() {
        for (String value : List.of("1e5", "NaN", "Infinity", "-Infinity", "0x1p3", " 42 ")) {
            assertThat(StringUtils.isNumeric(value)).as(value).isTrue();
        }
        for (String value : List.of("", " ", "null", " NULL ", "１２３")) {
            assertThat(StringUtils.isNumeric(value)).as(value).isFalse();
        }
    }

    @Test
    void shouldRetainLegacyBlankSemantics_forNullTextAndUnicodeWhitespace() {
        for (String value : Arrays.asList(null, "", " ", "\t\r\n", "null", " NULL ", " NuLl ")) {
            assertThat(StringUtils.isNullOrEmpty(value)).isTrue();
            assertThat(StringUtils.empty(value)).isTrue();
            assertThat(StringUtils.filled(value)).isFalse();
            assertThat(StringUtils.noNull(value)).isEmpty();
        }
        for (String value : List.of(" nullish ", "\u2003", "\u00a0")) {
            assertThat(StringUtils.empty(value)).isFalse();
            assertThat(StringUtils.filled(value)).isTrue();
            assertThat(StringUtils.noNull(value)).isEqualTo(value);
        }
    }

    @Test
    void shouldDropEmptyTokens_withTokenizerDelimiters() {
        assertThat(StringUtils.splitToStringArray(",a,,b;c;", ",;")).containsExactly("a", "b", "c");
    }

    @Test
    void shouldPreserveDefaultLocaleCaseExpansion_withLegacySubstringMatching() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMAN);
            assertThat(StringUtils.containsIgnoreCase("Straße", "STRASSE")).isTrue();
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThat(StringUtils.containsIgnoreCase("i", "I")).isFalse();
        } finally {
            Locale.setDefault(original);
        }
    }

    @Nested
    @DisplayName("isInteger")
    class IsInteger {

        @Test
        @DisplayName("should return true for valid integer")
        void shouldReturnTrue_forValidInteger() {
            assertThat(StringUtils.isInteger("42")).isTrue();
        }

        @Test
        @DisplayName("should return false for decimal")
        void shouldReturnFalse_forDecimal() {
            assertThat(StringUtils.isInteger("3.14")).isFalse();
            assertThat(StringUtils.isInteger(" 42 ")).isFalse();
            assertThat(StringUtils.isInteger("null")).isFalse();
            assertThat(StringUtils.isInteger("2147483648")).isFalse();
        }

        @Test
        @DisplayName("should return false for null")
        void shouldReturnFalse_forNull() {
            assertThat(StringUtils.isInteger(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("containsIgnoreCase")
    class ContainsIgnoreCase {

        @Test
        @DisplayName("should find substring ignoring case")
        void shouldFind_ignoringCase() {
            assertThat(StringUtils.containsIgnoreCase("Hello World", "hello")).isTrue();
        }

        @Test
        @DisplayName("should return false for non-matching")
        void shouldReturnFalse_forNonMatch() {
            assertThat(StringUtils.containsIgnoreCase("Hello", "xyz")).isFalse();
        }

        @Test
        @DisplayName("should return false for null inputs")
        void shouldReturnFalse_forNull() {
            assertThat(StringUtils.containsIgnoreCase(null, "test")).isFalse();
            assertThat(StringUtils.containsIgnoreCase("test", null)).isFalse();
        }
    }

    @Nested
    @DisplayName("filled and empty")
    class FilledAndEmpty {

        @Test
        @DisplayName("should return true for filled string")
        void shouldReturnTrue_forFilled() {
            assertThat(StringUtils.filled("hello")).isTrue();
        }

        @Test
        @DisplayName("should return false for empty string")
        void shouldReturnFalse_forEmpty() {
            assertThat(StringUtils.filled("")).isFalse();
        }

        @Test
        @DisplayName("should return true for empty null string")
        void shouldReturnTrue_forEmptyNull() {
            assertThat(StringUtils.empty(null)).isTrue();
        }
    }

    @Nested
    @DisplayName("noNull")
    class NoNull {

        @Test
        @DisplayName("should return empty for null")
        void shouldReturnEmpty_forNull() {
            assertThat(StringUtils.noNull(null)).isEmpty();
        }

        @Test
        @DisplayName("should return original for non-null")
        void shouldReturnOriginal_forNonNull() {
            assertThat(StringUtils.noNull("hello")).isEqualTo("hello");
        }
    }

    @Nested
    @DisplayName("lineBreaks")
    class LineBreaks {

        @Test
        @DisplayName("should replace line breaks with single space")
        void shouldReplaceLineBreaks_withSingleSpaces() {
            String result = StringUtils.lineBreaks("hello\nworld");
            assertThat(result).isEqualTo("hello world");
        }

        @Test
        @DisplayName("should collapse multiple spaces")
        void shouldCollapseMultipleSpaces_withSingleSpace() {
            String result = StringUtils.lineBreaks("hello   world");
            assertThat(result).isEqualTo("hello world");
        }
    }

    @Test
    void shouldReturnEmptyString_whenNoNullReceivesNullBlankOrLiteralNull() {
        assertThat(StringUtils.noNull(null)).isEmpty();
        assertThat(StringUtils.noNull("")).isEmpty();
        assertThat(StringUtils.noNull("   ")).isEmpty();
        assertThat(StringUtils.noNull("null")).isEmpty();
        assertThat(StringUtils.noNull(" NULL ")).isEmpty();
    }

    @Test
    void shouldPreserveOriginalText_whenNoNullReceivesNonBlankValue() {
        assertThat(StringUtils.noNull("abc")).isEqualTo("abc");
        assertThat(StringUtils.noNull(" abc ")).isEqualTo(" abc ");
    }
}
