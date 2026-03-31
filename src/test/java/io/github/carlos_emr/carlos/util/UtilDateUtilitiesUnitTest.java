/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 *
 * Maintained by the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.util;

import org.junit.jupiter.api.*;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link UtilDateUtilities} legacy date conversion utilities.
 *
 * <p>This class is @Deprecated but still actively used throughout the codebase.</p>
 *
 * @since 2026-03-31
 */
@DisplayName("UtilDateUtilities Unit Tests")
@Tag("unit")
@Tag("fast")
@Tag("utility")
class UtilDateUtilitiesUnitTest {

    @Nested
    @DisplayName("StringToDate")
    class StringToDateTests {

        @Test
        @DisplayName("should parse date with default pattern yyyy-MM-dd")
        void shouldParseDateWithDefaultPattern() {
            Date result = UtilDateUtilities.StringToDate("2026-03-31");
            assertThat(result).isNotNull();
            Calendar cal = Calendar.getInstance();
            cal.setTime(result);
            assertThat(cal.get(Calendar.YEAR)).isEqualTo(2026);
        }

        @Test
        @DisplayName("should parse date with custom pattern")
        void shouldParseDate_withCustomPattern() {
            Date result = UtilDateUtilities.StringToDate("31/03/2026", "dd/MM/yyyy");
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should return null for invalid date string")
        void shouldReturnNull_forInvalidDate() {
            Date result = UtilDateUtilities.StringToDate("not-a-date");
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNull_forNull() {
            Date result = UtilDateUtilities.StringToDate(null);
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("DateToString")
    class DateToStringTests {

        @Test
        @DisplayName("should format date with default pattern")
        void shouldFormatDate_withDefaultPattern() {
            Calendar cal = Calendar.getInstance();
            cal.set(2026, Calendar.MARCH, 31);
            String result = UtilDateUtilities.DateToString(cal.getTime());
            assertThat(result).isEqualTo("2026-03-31");
        }

        @Test
        @DisplayName("should return empty string for null date")
        void shouldReturnEmpty_forNull() {
            assertThat(UtilDateUtilities.DateToString(null)).isEmpty();
        }

        @Test
        @DisplayName("should format with custom pattern")
        void shouldFormat_withCustomPattern() {
            Calendar cal = Calendar.getInstance();
            cal.set(2026, Calendar.MARCH, 31);
            String result = UtilDateUtilities.DateToString(cal.getTime(), "dd/MM/yyyy");
            assertThat(result).isEqualTo("31/03/2026");
        }
    }

    @Nested
    @DisplayName("justYear/justMonth/justDay")
    class DateComponents {

        @Test
        @DisplayName("should extract year")
        void shouldExtractYear() {
            Calendar cal = Calendar.getInstance();
            cal.set(2026, Calendar.MARCH, 31);
            assertThat(UtilDateUtilities.justYear(cal.getTime())).isEqualTo("2026");
        }

        @Test
        @DisplayName("should extract month")
        void shouldExtractMonth() {
            Calendar cal = Calendar.getInstance();
            cal.set(2026, Calendar.MARCH, 31);
            assertThat(UtilDateUtilities.justMonth(cal.getTime())).isEqualTo("03");
        }

        @Test
        @DisplayName("should extract day")
        void shouldExtractDay() {
            Calendar cal = Calendar.getInstance();
            cal.set(2026, Calendar.MARCH, 31);
            assertThat(UtilDateUtilities.justDay(cal.getTime())).isEqualTo("31");
        }
    }

    @Nested
    @DisplayName("calcDate")
    class CalcDate {

        @Test
        @DisplayName("should create date from year, month, day strings")
        void shouldCreateDate_fromStrings() {
            Date result = UtilDateUtilities.calcDate("2026", "3", "31");
            assertThat(result).isNotNull();
            Calendar cal = Calendar.getInstance();
            cal.setTime(result);
            assertThat(cal.get(Calendar.YEAR)).isEqualTo(2026);
            assertThat(cal.get(Calendar.MONTH)).isEqualTo(Calendar.MARCH);
        }

        @Test
        @DisplayName("should return null for null components")
        void shouldReturnNull_forNullComponents() {
            assertThat(UtilDateUtilities.calcDate(null, "3", "31")).isNull();
        }
    }

    @Nested
    @DisplayName("getToday")
    class GetToday {

        @Test
        @DisplayName("should return today in specified format")
        void shouldReturnToday_inFormat() {
            String result = UtilDateUtilities.getToday("yyyy-MM-dd");
            assertThat(result).matches("\\d{4}-\\d{2}-\\d{2}");
        }
    }

    @Nested
    @DisplayName("round-trip")
    class RoundTrip {

        @Test
        @DisplayName("should survive StringToDate then DateToString round-trip")
        void shouldSurviveRoundTrip() {
            String original = "2026-03-31";
            Date parsed = UtilDateUtilities.StringToDate(original);
            String formatted = UtilDateUtilities.DateToString(parsed);
            assertThat(formatted).isEqualTo(original);
        }
    }
}
