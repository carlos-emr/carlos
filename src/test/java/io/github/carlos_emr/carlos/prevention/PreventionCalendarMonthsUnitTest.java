/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
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
package io.github.carlos_emr.carlos.prevention;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * {@link Prevention#calendarMonthsBetween(LocalDate, LocalDate)}: whole calendar months with no drift after a
 * short month, for a start on the 29th, 30th and 31st and on 29 February (#5048). The legacy month count adds a
 * month at a time to the same date, so after February the day stays at the 28th; this count reaches month {@code n}
 * on {@code from.plusMonths(n)}, a short month's last day when it lacks the day. Both dates are fixed here, so the
 * edges do not depend on the day the tests run.
 *
 * @since 2026-11-27
 */
@Tag("unit")
@Tag("prevention")
@DisplayName("Prevention.calendarMonthsBetween")
class PreventionCalendarMonthsUnitTest {

    private static int months(String from, String to) {
        return Prevention.calendarMonthsBetween(LocalDate.parse(from), LocalDate.parse(to));
    }

    @ParameterizedTest(name = "from {0} to {1}: {2} months")
    @CsvSource({
            // 31 January: a month on the last day of February, never early, no drift afterwards.
            "2026-01-31, 2026-02-27, 0",
            "2026-01-31, 2026-02-28, 1",
            "2026-01-31, 2026-03-30, 1",
            "2026-01-31, 2026-03-31, 2",
            "2025-01-31, 2025-12-30, 10",
            "2025-01-31, 2025-12-31, 11",
            // 31 January in a leap year.
            "2016-01-31, 2016-02-28, 0",
            "2016-01-31, 2016-02-29, 1",
            // 30 and 29 January.
            "2026-01-30, 2026-02-27, 0",
            "2026-01-30, 2026-02-28, 1",
            "2026-01-29, 2026-02-28, 1",
            "2026-01-29, 2026-02-27, 0",
            "2026-01-30, 2026-03-29, 1",
            "2026-01-30, 2026-03-30, 2",
            // 31st into the 30-day months.
            "2026-08-31, 2026-09-29, 0",
            "2026-08-31, 2026-09-30, 1",
            "2026-10-31, 2026-11-30, 1",
            "2026-03-31, 2026-04-29, 0",
            "2026-03-31, 2026-04-30, 1",
            // 29 February: a year on 28 February of a common year, 29 February of the next leap year.
            "2016-02-29, 2017-02-27, 11",
            "2016-02-29, 2017-02-28, 12",
            "2016-02-29, 2020-02-28, 47",
            "2016-02-29, 2020-02-29, 48",
            // The ten-year edge (119 versus 120 months), from each kind of day.
            "2016-01-31, 2026-01-30, 119",
            "2016-01-31, 2026-01-31, 120",
            "2016-03-30, 2026-03-29, 119",
            "2016-03-30, 2026-03-30, 120",
            "2016-02-29, 2026-02-27, 119",
            "2016-02-29, 2026-02-28, 120",
            "2016-06-15, 2026-06-14, 119",
            "2016-06-15, 2026-06-15, 120",
            // The same day, and a start after the end.
            "2026-05-31, 2026-05-31, 0",
            "2026-05-31, 2026-05-30, 0",
    })
    void shouldCountWholeCalendarMonths_withoutDriftAfterAShortMonth(String from, String to, int expected) {
        assertThat(months(from, to)).isEqualTo(expected);
    }

    @Test
    void shouldNeverReachAMonthBeforeItsDay_unlikeTheLegacyDriftingCount() {
        // Starting on 31 January 2016, the legacy count (one month at a time on the same date) is at the 28th from
        // February 2017 on, so it counts 120 months on 28 January 2026, three days before the tenth anniversary.
        assertThat(months("2016-01-31", "2026-01-28")).isEqualTo(119);
        assertThat(months("2016-01-31", "2026-01-29")).isEqualTo(119);
        assertThat(months("2016-01-31", "2026-01-30")).isEqualTo(119);
        assertThat(months("2016-01-31", "2026-01-31")).isEqualTo(120);
    }

    @Test
    void shouldRoundTripEveryDayOfTheMonth_throughPlusMonths() {
        // For any start date, the month count at from.plusMonths(n) is n (never n - 1: no drift, never early).
        for (LocalDate from = LocalDate.of(2015, 12, 25); from.isBefore(LocalDate.of(2016, 3, 5)); from = from.plusDays(1)) {
            for (int n : new int[] {1, 2, 11, 12, 13, 120, 121}) {
                assertThat(Prevention.calendarMonthsBetween(from, from.plusMonths(n)))
                        .as("%s plus %d months", from, n).isEqualTo(n);
                // The day before is never n months old, whatever the month lengths: at most n - 1.
                assertThat(Prevention.calendarMonthsBetween(from, from.plusMonths(n).minusDays(1)))
                        .as("%s plus %d months, the day before", from, n).isLessThanOrEqualTo(n);
            }
        }
    }
}
