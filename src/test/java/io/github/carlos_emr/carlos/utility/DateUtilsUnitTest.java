/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 *
 * Maintained by the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.utility;

import org.junit.jupiter.api.*;

import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link DateUtils} ISO date parsing and formatting utilities.
 *
 * <p>Focuses on ISO-format methods that don't depend on CarlosProperties.</p>
 *
 * @since 2026-03-31
 */
@DisplayName("DateUtils Unit Tests")
@Tag("unit")
@Tag("fast")
@Tag("utility")
class DateUtilsUnitTest {

    private Calendar cal(int year, int month, int day, int hour, int minute, int second) {
        Calendar c = new GregorianCalendar();
        c.set(year, month - 1, day, hour, minute, second);
        c.set(Calendar.MILLISECOND, 0);
        return c;
    }

    @Nested
    @DisplayName("getIsoDate")
    class GetIsoDate {

        @Test
        @DisplayName("should format calendar to ISO date string")
        void shouldFormatCalendar_toIsoDate() {
            String result = DateUtils.getIsoDate(cal(2026, 3, 31, 0, 0, 0));
            assertThat(result).startsWith("2026-03-31");
        }

        @Test
        @DisplayName("should return empty string for null calendar")
        void shouldReturnEmpty_forNull() {
            assertThat(DateUtils.getIsoDate(null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("getIsoDateTime")
    class GetIsoDateTime {

        @Test
        @DisplayName("should format calendar to ISO datetime with T separator")
        void shouldFormatCalendar_withTSeparator() {
            String result = DateUtils.getIsoDateTime(cal(2026, 3, 31, 14, 30, 0));
            assertThat(result).contains("2026-03-31");
            assertThat(result).contains("T");
            assertThat(result).contains("14:30");
        }

        @Test
        @DisplayName("should return empty string for null")
        void shouldReturnEmpty_forNull() {
            assertThat(DateUtils.getIsoDateTime(null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("getIsoDateTimeNoT")
    class GetIsoDateTimeNoT {

        @Test
        @DisplayName("should format without T separator")
        void shouldFormat_withoutTSeparator() {
            String result = DateUtils.getIsoDateTimeNoT(cal(2026, 3, 31, 14, 30, 45));
            assertThat(result).doesNotContain("T");
            assertThat(result).contains("2026-03-31");
            assertThat(result).contains("14:30:45");
        }

        @Test
        @DisplayName("should return empty string for null")
        void shouldReturnEmpty_forNull() {
            assertThat(DateUtils.getIsoDateTimeNoT(null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("getIsoDateTimeNoTNoSeconds")
    class GetIsoDateTimeNoTNoSeconds {

        @Test
        @DisplayName("should format without seconds")
        void shouldFormat_withoutSeconds() {
            String result = DateUtils.getIsoDateTimeNoTNoSeconds(cal(2026, 3, 31, 14, 30, 45));
            assertThat(result).contains("2026-03-31");
            assertThat(result).contains("14:30");
            assertThat(result).doesNotContain(":45");
        }

        @Test
        @DisplayName("should return empty string for null")
        void shouldReturnEmpty_forNull() {
            assertThat(DateUtils.getIsoDateTimeNoTNoSeconds(null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("parseIsoDate")
    class ParseIsoDate {

        @Test
        @DisplayName("should parse valid ISO date string")
        void shouldParseValidIsoDate() throws ParseException {
            Date result = DateUtils.parseIsoDate("2026-03-31");
            assertThat(result).isNotNull();
            Calendar cal = Calendar.getInstance();
            cal.setTime(result);
            assertThat(cal.get(Calendar.YEAR)).isEqualTo(2026);
            assertThat(cal.get(Calendar.MONTH)).isEqualTo(Calendar.MARCH);
            assertThat(cal.get(Calendar.DAY_OF_MONTH)).isEqualTo(31);
        }

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNull_forNull() throws ParseException {
            assertThat(DateUtils.parseIsoDate(null)).isNull();
        }

        @Test
        @DisplayName("should return null for empty input")
        void shouldReturnNull_forEmpty() throws ParseException {
            assertThat(DateUtils.parseIsoDate("  ")).isNull();
        }
    }

    @Nested
    @DisplayName("parseIsoDateAsCalendar")
    class ParseIsoDateAsCalendar {

        @Test
        @DisplayName("should return GregorianCalendar for valid date")
        void shouldReturnCalendar_forValidDate() throws ParseException {
            GregorianCalendar result = DateUtils.parseIsoDateAsCalendar("2026-03-31");
            assertThat(result).isNotNull();
            assertThat(result.get(Calendar.YEAR)).isEqualTo(2026);
        }

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNull_forNull() throws ParseException {
            assertThat(DateUtils.parseIsoDateAsCalendar(null)).isNull();
        }
    }

    @Nested
    @DisplayName("yearDifference")
    class YearDifference {

        @Test
        @DisplayName("should calculate positive year difference")
        void shouldCalculatePositive_yearDifference() {
            Integer result = DateUtils.yearDifference(cal(2000, 1, 1, 0, 0, 0), cal(2026, 1, 1, 0, 0, 0));
            assertThat(result).isEqualTo(26);
        }

        @Test
        @DisplayName("should return null when either date is null")
        void shouldReturnNull_whenEitherNull() {
            assertThat(DateUtils.yearDifference(null, cal(2026, 1, 1, 0, 0, 0))).isNull();
            assertThat(DateUtils.yearDifference(cal(2026, 1, 1, 0, 0, 0), null)).isNull();
        }
    }

    @Nested
    @DisplayName("getAge")
    class GetAge {

        @Test
        @DisplayName("should calculate age from birth date")
        void shouldCalculateAge() {
            Integer age = DateUtils.getAge(cal(1996, 1, 1, 0, 0, 0), cal(2026, 6, 15, 0, 0, 0));
            assertThat(age).isEqualTo(30);
        }
    }

    @Nested
    @DisplayName("setToBeginningOfDay")
    class SetToBeginningOfDay {

        @Test
        @DisplayName("should clear time components")
        void shouldClearTimeComponents() {
            Calendar cal = cal(2026, 3, 31, 14, 30, 45);
            Calendar result = DateUtils.setToBeginningOfDay(cal);
            assertThat(result.get(Calendar.HOUR_OF_DAY)).isZero();
            assertThat(result.get(Calendar.MINUTE)).isZero();
            assertThat(result.get(Calendar.SECOND)).isZero();
            assertThat(result.get(Calendar.MILLISECOND)).isZero();
        }
    }

    @Nested
    @DisplayName("format")
    class Format {

        @Test
        @DisplayName("should return empty string for null date")
        void shouldReturnEmpty_forNullDate() {
            assertThat(DateUtils.format("yyyy-MM-dd", null, null)).isEmpty();
        }

        @Test
        @DisplayName("should format date with given pattern")
        void shouldFormatDate_withPattern() {
            Date date = cal(2026, 3, 31, 0, 0, 0).getTime();
            assertThat(DateUtils.format("yyyy-MM-dd", date, null)).isEqualTo("2026-03-31");
        }
    }

    @Nested
    @DisplayName("parseJsIsoDateTimeNoTNoSeconds")
    class ParseJsIso {

        @Test
        @DisplayName("should parse JS ISO format without seconds")
        void shouldParseJsIsoFormat() throws ParseException {
            Date result = DateUtils.parseJsIsoDateTimeNoTNoSeconds("2026-03-31 14:30");
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNull_forNull() throws ParseException {
            assertThat(DateUtils.parseJsIsoDateTimeNoTNoSeconds(null)).isNull();
        }
    }
}
