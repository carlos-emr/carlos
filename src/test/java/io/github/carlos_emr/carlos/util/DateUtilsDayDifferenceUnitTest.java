/**
 * Copyright (c) 2026 CARLOS EMR Contributors. All Rights Reserved.
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
package io.github.carlos_emr.carlos.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for the day-count helpers in {@link DateUtils} across daylight saving changes
 * (issue #6047).
 *
 * <p>The helpers used to divide elapsed milliseconds by a fixed 24 hours. A local day is 23 hours
 * on the spring-forward date and 25 hours on the fall-back date, so any span crossing one was
 * counted a day short (spring) or could gain a day inside a single date (fall). These cases run in
 * {@code America/Toronto}, where 2026 changes on March 8 and November 1, and pin both directions
 * plus the unchanged answers away from a change.</p>
 *
 * <p>The helpers count <em>complete</em> days on the local calendar and clock: the same answer the
 * old arithmetic gave on any span without a change, so callers that pass timestamps keep their
 * existing truncation.</p>
 *
 * @since 2026-11-09
 */
@Tag("unit")
@DisplayName("DateUtils day differences across daylight saving changes")
class DateUtilsDayDifferenceUnitTest {

    private static final ZoneId TORONTO = ZoneId.of("America/Toronto");

    private TimeZone originalDefault;

    @BeforeEach
    void useTorontoAsDefaultZone() {
        originalDefault = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone(TORONTO));
    }

    @AfterEach
    void restoreDefaultZone() {
        TimeZone.setDefault(originalDefault);
    }

    /** A Toronto wall-clock time as a {@link Date}. */
    private static Date at(int year, int month, int day, int hour, int minute) {
        return Date.from(LocalDateTime.of(year, month, day, hour, minute).atZone(TORONTO).toInstant());
    }

    private static Date midnight(int year, int month, int day) {
        return at(year, month, day, 0, 0);
    }

    /** A Toronto midnight as a {@link Calendar}, built the way legacy callers build theirs. */
    private static Calendar calendarMidnight(int year, int month, int day) {
        Calendar calendar = new GregorianCalendar();
        calendar.clear();
        calendar.set(year, month - 1, day);
        return calendar;
    }

    @Nested
    @DisplayName("calculateDayDifference")
    class CalculateDayDifference {

        @Test
        @DisplayName("should count every calendar day when the span crosses spring forward")
        void shouldCountEveryDay_whenSpanCrossesSpringForward() {
            assertThat(DateUtils.calculateDayDifference(midnight(2026, 3, 1), midnight(2026, 3, 9))).isEqualTo(8);
        }

        @Test
        @DisplayName("should count every calendar day when the span crosses fall back")
        void shouldCountEveryDay_whenSpanCrossesFallBack() {
            assertThat(DateUtils.calculateDayDifference(midnight(2026, 10, 25), midnight(2026, 11, 2))).isEqualTo(8);
        }

        @Test
        @DisplayName("should count the 23-hour spring-forward day as one day")
        void shouldCountOneDay_forTheShortSpringForwardDay() {
            assertThat(DateUtils.calculateDayDifference(midnight(2026, 3, 8), midnight(2026, 3, 9))).isEqualTo(1);
        }

        @Test
        @DisplayName("should keep a negative count when the second date is earlier")
        void shouldReturnNegativeCount_whenSecondDateIsEarlier() {
            assertThat(DateUtils.calculateDayDifference(midnight(2026, 3, 9), midnight(2026, 3, 1))).isEqualTo(-8);
        }

        @Test
        @DisplayName("should agree for the Calendar, Date and mixed overloads")
        void shouldAgreeAcrossOverloads_forTheSameSpringForwardSpan() {
            Calendar from = calendarMidnight(2026, 3, 1);
            Calendar to = calendarMidnight(2026, 3, 9);

            assertThat(DateUtils.calculateDayDifference(from, to)).isEqualTo(8);
            assertThat(DateUtils.calculateDayDifference(from, to.getTime())).isEqualTo(8);
            assertThat(DateUtils.calculateDayDifference(from.getTime(), to)).isEqualTo(8);
        }

        @Test
        @DisplayName("should keep truncating to complete days for timestamps away from a change")
        void shouldTruncateToCompleteDays_forTimestampsWithoutAChange() {
            // 23:00 to 01:00 the next morning is two hours: no complete day, as before.
            assertThat(DateUtils.calculateDayDifference(at(2026, 6, 1, 23, 0), at(2026, 6, 2, 1, 0))).isZero();
            assertThat(DateUtils.calculateDayDifference(at(2026, 6, 1, 9, 0), at(2026, 6, 4, 8, 59))).isEqualTo(2);
            assertThat(DateUtils.calculateDayDifference(at(2026, 6, 1, 9, 0), at(2026, 6, 4, 9, 0))).isEqualTo(3);
        }

        @Test
        @DisplayName("should count on the first Calendar's own zone when the calendars carry one")
        void shouldUseTheCalendarZone_whenCalendarsCarryTheirOwnZone() {
            // Tokyo has no daylight saving: these midnights are exactly 192 hours apart. Read on
            // the Toronto clock the span loses the fall-back hour and would be 7 days 23 hours.
            TimeZone tokyo = TimeZone.getTimeZone("Asia/Tokyo");
            Calendar from = new GregorianCalendar(tokyo);
            from.clear();
            from.set(2026, Calendar.OCTOBER, 25);
            Calendar to = new GregorianCalendar(tokyo);
            to.clear();
            to.set(2026, Calendar.NOVEMBER, 2);

            assertThat(DateUtils.calculateDayDifference(from, to)).isEqualTo(8);
            assertThat(DateUtils.getNumberOfDaysBetweenTwoDates(to, from)).isEqualTo(8);
        }

        @Test
        @DisplayName("should return null when either date is missing")
        void shouldReturnNull_whenEitherDateIsMissing() {
            assertThat(DateUtils.calculateDayDifference((Calendar) null, calendarMidnight(2026, 3, 9))).isNull();
            assertThat(DateUtils.calculateDayDifference(midnight(2026, 3, 1), (Date) null)).isNull();
        }

        @Test
        @DisplayName("should accept java.sql.Date values from JDBC-backed entities")
        void shouldAcceptSqlDates_fromJdbcBackedEntities() {
            Date from = new java.sql.Date(midnight(2026, 3, 1).getTime());
            Date to = new java.sql.Timestamp(midnight(2026, 3, 9).getTime());

            assertThat(DateUtils.calculateDayDifference(from, to)).isEqualTo(8);
        }
    }

    @Nested
    @DisplayName("getDifDays")
    class GetDifDays {

        @Test
        @DisplayName("should report the same day for two times on the 25-hour fall-back date")
        void shouldReportSameDay_forTwoTimesOnTheFallBackDate() {
            assertThat(DateUtils.getDifDays(at(2026, 11, 1, 23, 30), midnight(2026, 11, 1))).isZero();
        }

        @Test
        @DisplayName("should report one day between consecutive midnights around spring forward")
        void shouldReportOneDay_betweenMidnightsAroundSpringForward() {
            assertThat(DateUtils.getDifDays(midnight(2026, 3, 9), midnight(2026, 3, 8))).isEqualTo(1);
        }

        @Test
        @DisplayName("should keep the bill-age answer for a span without a change")
        void shouldKeepTheBillAge_forSpanWithoutAChange() {
            assertThat(DateUtils.getDifDays(at(2026, 7, 31, 14, 0), midnight(2026, 7, 1))).isEqualTo(30);
        }

        @Test
        @DisplayName("should return a negative count when the lesser date is later")
        void shouldReturnNegativeCount_whenLesserDateIsLater() {
            assertThat(DateUtils.getDifDays(midnight(2026, 3, 1), midnight(2026, 3, 9))).isEqualTo(-8);
        }

        @Test
        @DisplayName("should still fail loudly on a missing date")
        void shouldThrow_whenADateIsMissing() {
            assertThatThrownBy(() -> DateUtils.getDifDays(null, midnight(2026, 3, 9)))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("getNumberOfDaysBetweenTwoDates")
    class GetNumberOfDaysBetweenTwoDates {

        @Test
        @DisplayName("should count every day across spring forward in either order for Dates")
        void shouldCountEveryDayInEitherOrder_forDatesAcrossSpringForward() {
            assertThat(DateUtils.getNumberOfDaysBetweenTwoDates(midnight(2026, 3, 1), midnight(2026, 3, 9))).isEqualTo(8);
            assertThat(DateUtils.getNumberOfDaysBetweenTwoDates(midnight(2026, 3, 9), midnight(2026, 3, 1))).isEqualTo(8);
        }

        @Test
        @DisplayName("should count every day across fall back in either order for Calendars")
        void shouldCountEveryDayInEitherOrder_forCalendarsAcrossFallBack() {
            Calendar from = calendarMidnight(2026, 10, 31);
            Calendar to = calendarMidnight(2026, 11, 3);

            assertThat(DateUtils.getNumberOfDaysBetweenTwoDates(from, to)).isEqualTo(3);
            assertThat(DateUtils.getNumberOfDaysBetweenTwoDates(to, from)).isEqualTo(3);
        }

        @Test
        @DisplayName("should not add a day inside the 25-hour fall-back date")
        void shouldNotAddADay_insideTheFallBackDate() {
            assertThat(DateUtils.getNumberOfDaysBetweenTwoDates(midnight(2026, 11, 1), at(2026, 11, 1, 23, 30))).isZero();
        }
    }

    @Nested
    @DisplayName("isSameLocalDate")
    class IsSameLocalDate {

        @Test
        @DisplayName("should treat two times on the 25-hour fall-back date as the same day")
        void shouldReturnTrue_forTwoTimesOnTheFallBackDate() {
            assertThat(DateUtils.isSameLocalDate(at(2026, 11, 1, 23, 30), midnight(2026, 11, 1))).isTrue();
        }

        @Test
        @DisplayName("should treat consecutive midnights around spring forward as different days")
        void shouldReturnFalse_forConsecutiveMidnightsAroundSpringForward() {
            assertThat(DateUtils.isSameLocalDate(midnight(2026, 3, 9), midnight(2026, 3, 8))).isFalse();
        }

        @Test
        @DisplayName("should treat a later date less than 24 hours ahead as a different day")
        void shouldReturnFalse_forTheNextDateLessThanADayAhead() {
            assertThat(DateUtils.isSameLocalDate(at(2026, 6, 1, 14, 0), midnight(2026, 6, 2))).isFalse();
        }

        @Test
        @DisplayName("should fail loudly on a missing date")
        void shouldThrow_whenADateIsMissing() {
            assertThatThrownBy(() -> DateUtils.isSameLocalDate(null, midnight(2026, 6, 2)))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("completeLocalDaysBetween")
    class CompleteLocalDaysBetween {

        @Test
        @DisplayName("should count complete days on the given zone's clock")
        void shouldCountCompleteDays_onTheGivenZoneClock() {
            long from = ZonedDateTime.of(2026, 3, 1, 0, 0, 0, 0, TORONTO).toInstant().toEpochMilli();
            long to = ZonedDateTime.of(2026, 3, 9, 0, 0, 0, 0, TORONTO).toInstant().toEpochMilli();

            assertThat(DateUtils.completeLocalDaysBetween(from, to, TORONTO)).isEqualTo(8);
            assertThat(DateUtils.completeLocalDaysBetween(to, from, TORONTO)).isEqualTo(-8);
            // The same instants read in UTC, which has no change, are 7 days 23 hours apart.
            assertThat(DateUtils.completeLocalDaysBetween(from, to, ZoneId.of("UTC"))).isEqualTo(7);
        }
    }
}
