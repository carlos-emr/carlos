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

import java.sql.Timestamp;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link ConversionUtils} date/time/type conversion utilities.
 *
 * @since 2026-03-31
 */
@DisplayName("ConversionUtils Unit Tests")
@Tag("unit")
@Tag("fast")
@Tag("utility")
class ConversionUtilsUnitTest {

    // -----------------------------------------------------------------------
    // fromDateString / toDateString
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("fromDateString (default pattern)")
    class FromDateStringDefault {

        @Test
        @DisplayName("should parse valid yyyy-MM-dd date string")
        void shouldParseValidDate_withDefaultPattern() {
            Date result = ConversionUtils.fromDateString("2026-03-31");

            assertThat(result).isNotNull();
            Calendar cal = Calendar.getInstance();
            cal.setTime(result);
            assertThat(cal.get(Calendar.YEAR)).isEqualTo(2026);
            assertThat(cal.get(Calendar.MONTH)).isEqualTo(Calendar.MARCH);
            assertThat(cal.get(Calendar.DAY_OF_MONTH)).isEqualTo(31);
        }

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNull_forNullInput() {
            assertThat(ConversionUtils.fromDateString(null)).isNull();
        }

        @Test
        @DisplayName("should return null for empty string")
        void shouldReturnNull_forEmptyString() {
            assertThat(ConversionUtils.fromDateString("")).isNull();
        }

        @Test
        @DisplayName("should return null for whitespace-only string")
        void shouldReturnNull_forWhitespaceString() {
            assertThat(ConversionUtils.fromDateString("   ")).isNull();
        }

        @Test
        @DisplayName("should return null for invalid date format")
        void shouldReturnNull_forInvalidFormat() {
            assertThat(ConversionUtils.fromDateString("not-a-date")).isNull();
        }
    }

    @Nested
    @DisplayName("fromDateString (custom pattern)")
    class FromDateStringCustom {

        @Test
        @DisplayName("should parse date with custom format pattern")
        void shouldParseDate_withCustomPattern() {
            Date result = ConversionUtils.fromDateString("31/03/2026", "dd/MM/yyyy");

            assertThat(result).isNotNull();
            Calendar cal = Calendar.getInstance();
            cal.setTime(result);
            assertThat(cal.get(Calendar.YEAR)).isEqualTo(2026);
            assertThat(cal.get(Calendar.MONTH)).isEqualTo(Calendar.MARCH);
        }
    }

    @Nested
    @DisplayName("toDateString")
    class ToDateString {

        @Test
        @DisplayName("should format date with default pattern")
        void shouldFormatDate_withDefaultPattern() {
            Calendar cal = Calendar.getInstance();
            cal.set(2026, Calendar.MARCH, 31, 0, 0, 0);

            String result = ConversionUtils.toDateString(cal.getTime());

            assertThat(result).isEqualTo("2026-03-31");
        }

        @Test
        @DisplayName("should return empty string for null date")
        void shouldReturnEmptyString_forNullDate() {
            assertThat(ConversionUtils.toDateString((Date) null)).isEmpty();
        }

        @Test
        @DisplayName("should format date with custom pattern")
        void shouldFormatDate_withCustomPattern() {
            Calendar cal = Calendar.getInstance();
            cal.set(2026, Calendar.MARCH, 31, 0, 0, 0);

            String result = ConversionUtils.toDateString(cal.getTime(), "dd/MM/yyyy");

            assertThat(result).isEqualTo("31/03/2026");
        }
    }

    @Nested
    @DisplayName("toDateString from Timestamp")
    class ToDateStringTimestamp {

        @Test
        @DisplayName("should format SQL Timestamp to date string")
        void shouldFormatTimestamp_toDateString() {
            Calendar cal = Calendar.getInstance();
            cal.set(2026, Calendar.MARCH, 31, 10, 30, 0);
            Timestamp ts = new Timestamp(cal.getTimeInMillis());

            String result = ConversionUtils.toDateString(ts);

            assertThat(result).isEqualTo("2026-03-31");
        }
    }

    // -----------------------------------------------------------------------
    // fromDateString / toDateString round-trip
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("date round-trip")
    class DateRoundTrip {

        @Test
        @DisplayName("should survive round-trip parse then format")
        void shouldSurviveRoundTrip_parseThenFormat() {
            String original = "2026-03-31";
            Date parsed = ConversionUtils.fromDateString(original);
            String formatted = ConversionUtils.toDateString(parsed);

            assertThat(formatted).isEqualTo(original);
        }
    }

    // -----------------------------------------------------------------------
    // fromTimestampString / toTimestampString
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("timestamp string conversion")
    class TimestampStringConversion {

        @Test
        @DisplayName("should parse timestamp string with date and time")
        void shouldParseTimestamp_withDateAndTime() {
            Date result = ConversionUtils.fromTimestampString("2026-03-31 14:30:00");

            assertThat(result).isNotNull();
            Calendar cal = Calendar.getInstance();
            cal.setTime(result);
            assertThat(cal.get(Calendar.HOUR_OF_DAY)).isEqualTo(14);
            assertThat(cal.get(Calendar.MINUTE)).isEqualTo(30);
        }

        @Test
        @DisplayName("should format date to timestamp string")
        void shouldFormatDate_toTimestampString() {
            Calendar cal = Calendar.getInstance();
            cal.set(2026, Calendar.MARCH, 31, 14, 30, 0);

            String result = ConversionUtils.toTimestampString(cal.getTime());

            assertThat(result).isEqualTo("2026-03-31 14:30:00");
        }
    }

    // -----------------------------------------------------------------------
    // Time string conversions
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("time string conversions")
    class TimeStringConversions {

        @Test
        @DisplayName("should parse time string with seconds")
        void shouldParseTimeString_withSeconds() {
            Date result = ConversionUtils.fromTimeString("14:30:45");
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should parse time string without seconds")
        void shouldParseTimeString_withoutSeconds() {
            Date result = ConversionUtils.fromTimeStringNoSeconds("14:30");
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should format date to time string with seconds")
        void shouldFormatDate_toTimeString() {
            Calendar cal = Calendar.getInstance();
            cal.set(2026, Calendar.JANUARY, 1, 14, 30, 45);
            String result = ConversionUtils.toTimeString(cal.getTime());
            assertThat(result).isEqualTo("14:30:45");
        }

        @Test
        @DisplayName("should format date to time string without seconds")
        void shouldFormatDate_toTimeStringNoSeconds() {
            Calendar cal = Calendar.getInstance();
            cal.set(2026, Calendar.JANUARY, 1, 14, 30, 0);
            String result = ConversionUtils.toTimeStringNoSeconds(cal.getTime());
            assertThat(result).isEqualTo("14:30");
        }
    }

    // -----------------------------------------------------------------------
    // toIntList
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("toIntList")
    class ToIntList {

        @Test
        @DisplayName("should convert string list to integer list")
        void shouldConvertStringList_toIntegerList() {
            List<Integer> result = ConversionUtils.toIntList(List.of("1", "2", "3"));
            assertThat(result).containsExactly(1, 2, 3);
        }

        @Test
        @DisplayName("should return zeros for non-numeric strings")
        void shouldReturnZeros_forNonNumericStrings() {
            List<Integer> result = ConversionUtils.toIntList(List.of("abc", "", "5"));
            assertThat(result).containsExactly(0, 0, 5);
        }
    }

    // -----------------------------------------------------------------------
    // fromIntString / toIntString
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("fromIntString")
    class FromIntString {

        @Test
        @DisplayName("should parse valid integer string")
        void shouldParseValidInteger() {
            assertThat(ConversionUtils.fromIntString("42")).isEqualTo(42);
        }

        @Test
        @DisplayName("should return zero for null")
        void shouldReturnZero_forNull() {
            assertThat(ConversionUtils.fromIntString(null)).isZero();
        }

        @Test
        @DisplayName("should return zero for empty string")
        void shouldReturnZero_forEmptyString() {
            assertThat(ConversionUtils.fromIntString("")).isZero();
        }

        @Test
        @DisplayName("should return zero for non-numeric string")
        void shouldReturnZero_forNonNumeric() {
            assertThat(ConversionUtils.fromIntString("abc")).isZero();
        }

        @Test
        @DisplayName("should accept Integer object directly")
        void shouldAcceptIntegerObject() {
            assertThat(ConversionUtils.fromIntString(Integer.valueOf(99))).isEqualTo(99);
        }
    }

    @Nested
    @DisplayName("toIntString")
    class ToIntString {

        @Test
        @DisplayName("should format integer to string")
        void shouldFormatInteger_toString() {
            assertThat(ConversionUtils.toIntString(42)).isEqualTo("42");
        }

        @Test
        @DisplayName("should return '0' for null")
        void shouldReturnZero_forNull() {
            assertThat(ConversionUtils.toIntString(null)).isEqualTo("0");
        }
    }

    // -----------------------------------------------------------------------
    // fromLongString
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("fromLongString")
    class FromLongString {

        @Test
        @DisplayName("should parse valid long string")
        void shouldParseValidLong() {
            assertThat(ConversionUtils.fromLongString("123456789")).isEqualTo(123456789L);
        }

        @Test
        @DisplayName("should return zero for null")
        void shouldReturnZero_forNull() {
            assertThat(ConversionUtils.fromLongString(null)).isZero();
        }

        @Test
        @DisplayName("should return zero for non-numeric")
        void shouldReturnZero_forNonNumeric() {
            assertThat(ConversionUtils.fromLongString("abc")).isZero();
        }
    }

    // -----------------------------------------------------------------------
    // Bool conversions
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("boolean conversions")
    class BoolConversions {

        @Test
        @DisplayName("should convert true to '1'")
        void shouldConvertTrue_toOne() {
            assertThat(ConversionUtils.toBoolString(Boolean.TRUE)).isEqualTo("1");
        }

        @Test
        @DisplayName("should convert false to '0'")
        void shouldConvertFalse_toZero() {
            assertThat(ConversionUtils.toBoolString(Boolean.FALSE)).isEqualTo("0");
        }

        @Test
        @DisplayName("should convert null to '0'")
        void shouldConvertNull_toZero() {
            assertThat(ConversionUtils.toBoolString(null)).isEqualTo("0");
        }

        @Test
        @DisplayName("should parse '1' as true")
        void shouldParseOne_asTrue() {
            assertThat(ConversionUtils.fromBoolString("1")).isTrue();
        }

        @Test
        @DisplayName("should parse '0' as false")
        void shouldParseZero_asFalse() {
            assertThat(ConversionUtils.fromBoolString("0")).isFalse();
        }

        @Test
        @DisplayName("should parse null as false")
        void shouldParseNull_asFalse() {
            assertThat(ConversionUtils.fromBoolString(null)).isFalse();
        }

        @Test
        @DisplayName("should parse any non-zero string as true")
        void shouldParseNonZero_asTrue() {
            assertThat(ConversionUtils.fromBoolString("yes")).isTrue();
        }
    }

    // -----------------------------------------------------------------------
    // Double conversions
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("double conversions")
    class DoubleConversions {

        @Test
        @DisplayName("should parse valid double string")
        void shouldParseValidDouble() {
            assertThat(ConversionUtils.fromDoubleString("3.14")).isEqualTo(3.14);
        }

        @Test
        @DisplayName("should return zero for null")
        void shouldReturnZero_forNull() {
            assertThat(ConversionUtils.fromDoubleString(null)).isZero();
        }

        @Test
        @DisplayName("should format double to string")
        void shouldFormatDouble_toString() {
            assertThat(ConversionUtils.toDoubleString(3.14)).isEqualTo("3.14");
        }

        @Test
        @DisplayName("should return '0' for null double")
        void shouldReturnZero_forNullDouble() {
            assertThat(ConversionUtils.toDoubleString(null)).isEqualTo("0");
        }
    }

    // -----------------------------------------------------------------------
    // toDays
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("toDays")
    class ToDays {

        @Test
        @DisplayName("should compute day count from Date")
        void shouldComputeDayCount_fromDate() {
            // Epoch = day 0; 86400000ms = 1 day
            Date oneDay = new Date(86400000L);
            assertThat(ConversionUtils.toDays(oneDay)).isEqualTo(1);
        }

        @Test
        @DisplayName("should compute day count from long timestamp")
        void shouldComputeDayCount_fromLong() {
            assertThat(ConversionUtils.toDays(86400000L * 365)).isEqualTo(365);
        }
    }
}
