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
            assertThat(result).isEqualTo("BENZOIC ...");
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
        }

        @Test
        @DisplayName("should return false for null")
        void shouldReturnFalse_forNull() {
            assertThat(StringUtils.isInteger(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("join")
    class Join {

        @Test
        @DisplayName("should join string array with delimiter")
        void shouldJoinArray_withDelimiter() {
            assertThat(StringUtils.join(new String[]{"a", "b", "c"}, ",")).isEqualTo("a,b,c");
        }

        @Test
        @DisplayName("should return single element without delimiter")
        void shouldReturnSingleElement_withoutDelimiter() {
            assertThat(StringUtils.join(new String[]{"only"}, ",")).isEqualTo("only");
        }

        @Test
        @DisplayName("should join list with delimiter")
        void shouldJoinList_withDelimiter() {
            List<String> list = List.of("x", "y", "z");
            assertThat(StringUtils.join(list, "-")).isEqualTo("x-y-z");
        }
    }

    @Nested
    @DisplayName("split")
    class Split {

        @Test
        @DisplayName("should split string by delimiter")
        void shouldSplit_byDelimiter() {
            ArrayList<String> result = StringUtils.split("a,b,c", ",");
            assertThat(result).containsExactly("a", "b", "c");
        }

        @Test
        @DisplayName("should return single element when no delimiter found")
        void shouldReturnSingle_whenNoDelimiter() {
            ArrayList<String> result = StringUtils.split("abc", ",");
            assertThat(result).containsExactly("abc");
        }
    }

    @Nested
    @DisplayName("getCSV")
    class GetCSV {

        @Test
        @DisplayName("should convert list to CSV string")
        void shouldConvertList_toCSV() {
            List<String> list = List.of("one", "two", "three");
            assertThat(StringUtils.getCSV(list)).isEqualTo("one,two,three");
        }

        @Test
        @DisplayName("should return empty string for null list")
        void shouldReturnEmpty_forNull() {
            assertThat(StringUtils.getCSV(null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("transformNullInEmptyString")
    class TransformNull {

        @Test
        @DisplayName("should return empty string for null")
        void shouldReturnEmpty_forNull() {
            assertThat(StringUtils.transformNullInEmptyString(null)).isEmpty();
        }

        @Test
        @DisplayName("should return original for non-null")
        void shouldReturnOriginal_forNonNull() {
            assertThat(StringUtils.transformNullInEmptyString("hello")).isEqualTo("hello");
        }
    }

    @Nested
    @DisplayName("replaceChar")
    class ReplaceChar {

        @Test
        @DisplayName("should replace character in string")
        void shouldReplaceChar() {
            assertThat(StringUtils.replaceChar(' ', '_', "hello world")).isEqualTo("hello_world");
        }

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNull_forNull() {
            assertThat(StringUtils.replaceChar(' ', '_', null)).isNull();
        }
    }

    @Nested
    @DisplayName("nullSafeEquals")
    class NullSafeEquals {

        @Test
        @DisplayName("should return true for both null")
        void shouldReturnTrue_forBothNull() {
            assertThat(StringUtils.nullSafeEquals(null, null)).isTrue();
        }

        @Test
        @DisplayName("should return true for equal strings")
        void shouldReturnTrue_forEqual() {
            assertThat(StringUtils.nullSafeEquals("abc", "abc")).isTrue();
        }

        @Test
        @DisplayName("should return false for different strings")
        void shouldReturnFalse_forDifferent() {
            assertThat(StringUtils.nullSafeEquals("abc", "xyz")).isFalse();
        }

        @Test
        @DisplayName("should return false when first is null")
        void shouldReturnFalse_whenFirstNull() {
            assertThat(StringUtils.nullSafeEquals(null, "abc")).isFalse();
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
        void shouldReplaceLineBreaks() {
            String result = StringUtils.lineBreaks("hello\nworld");
            assertThat(result).isEqualTo("hello world");
        }

        @Test
        @DisplayName("should collapse multiple spaces")
        void shouldCollapseMultipleSpaces() {
            String result = StringUtils.lineBreaks("hello   world");
            assertThat(result).isEqualTo("hello world");
        }
    }

    @Nested
    @DisplayName("isValidDate")
    class IsValidDate {

        @Test
        @DisplayName("should return true for valid date")
        void shouldReturnTrue_forValidDate() {
            assertThat(StringUtils.isValidDate("2026-03-31", "yyyy-MM-dd")).isTrue();
        }

        @Test
        @DisplayName("should return false for invalid date")
        void shouldReturnFalse_forInvalidDate() {
            assertThat(StringUtils.isValidDate("not-a-date", "yyyy-MM-dd")).isFalse();
        }
    }

    @Nested
    @DisplayName("getStrIn")
    class GetStrIn {

        @Test
        @DisplayName("should join array to comma string")
        void shouldJoinArray_toCommaString() {
            assertThat(StringUtils.getStrIn(new String[]{"1", "2", "3"})).isEqualTo("1,2,3");
        }
    }
}
