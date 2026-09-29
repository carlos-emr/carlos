/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 *
 * Maintained by the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.utils;

import java.util.Calendar;
import java.util.Date;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the legacy date-filter helpers in {@link Utility}: the case-insensitive
 * {@code "TODAY"} sentinel, the blank-input sentinel dates, and the failure modes.
 *
 * @since 2026-09-17
 */
@DisplayName("Utility Unit Tests")
@Tag("unit")
@Tag("fast")
@Tag("utility")
class UtilityUnitTest {

    private static final long ONE_MINUTE_MS = 60_000L;

    private static int yearOf(Date date) {
        Calendar c = Calendar.getInstance();
        c.setTime(date);
        return c.get(Calendar.YEAR);
    }

    private static void assertIsNow(Date date) {
        assertThat(date).isNotNull();
        assertThat(Math.abs(date.getTime() - System.currentTimeMillis())).isLessThan(ONE_MINUTE_MS);
    }

    @Nested
    @DisplayName("GetSysDate")
    class GetSysDate {

        @Test
        @DisplayName("should return now for the TODAY keyword regardless of case")
        void shouldReturnNow_forTodayKeywordInAnyCase() {
            assertIsNow(Utility.GetSysDate("TODAY"));
            assertIsNow(Utility.GetSysDate("today"));
            assertIsNow(Utility.GetSysDate("ToDaY"));
        }

        @Test
        @DisplayName("should return a far-future sentinel for null or blank input")
        void shouldReturnFarFutureSentinel_forBlankInput() {
            assertThat(yearOf(Utility.GetSysDate(null))).isGreaterThanOrEqualTo(2999);
            assertThat(yearOf(Utility.GetSysDate("   "))).isGreaterThanOrEqualTo(2999);
        }

        @Test
        @DisplayName("should parse day/month/year separated by slashes")
        void shouldParseDayMonthYear_forSlashDelimitedInput() {
            Date parsed = Utility.GetSysDate("5/3/2024");

            Calendar c = Calendar.getInstance();
            c.setTime(parsed);
            assertThat(c.get(Calendar.YEAR)).isEqualTo(2024);
            assertThat(c.get(Calendar.MONTH)).isEqualTo(Calendar.MARCH);
            assertThat(c.get(Calendar.DAY_OF_MONTH)).isEqualTo(5);
        }

        @Test
        @DisplayName("should return null rather than throw for unparseable input")
        void shouldReturnNull_forUnparseableInput() {
            assertThat(Utility.GetSysDate("not-a-date")).isNull();
            assertThat(Utility.GetSysDate("TODAYISH")).isNull();
        }
    }

    @Nested
    @DisplayName("GetSysDateMin / GetSysDateMax")
    class GetSysDateMinMax {

        @Test
        @DisplayName("should return now for the TODAY keyword regardless of case")
        void shouldReturnNow_forTodayKeywordInAnyCase() throws Exception {
            assertIsNow(Utility.GetSysDateMin("today"));
            assertIsNow(Utility.GetSysDateMax("Today"));
        }

        @Test
        @DisplayName("should return far-past and far-future sentinels for blank input")
        void shouldReturnBoundarySentinels_forBlankInput() throws Exception {
            assertThat(yearOf(Utility.GetSysDateMin(null))).isEqualTo(1900);
            assertThat(yearOf(Utility.GetSysDateMax(""))).isGreaterThanOrEqualTo(2999);
        }

        @Test
        @DisplayName("should throw rather than return null for unparseable input")
        void shouldThrow_forUnparseableInput() {
            assertThatThrownBy(() -> Utility.GetSysDateMin("not-a-date"))
                    .isInstanceOf(Exception.class)
                    .hasMessageContaining("Invalid Date");
            assertThatThrownBy(() -> Utility.GetSysDateMax("not-a-date"))
                    .isInstanceOf(Exception.class)
                    .hasMessageContaining("Invalid Date");
        }
    }
}
