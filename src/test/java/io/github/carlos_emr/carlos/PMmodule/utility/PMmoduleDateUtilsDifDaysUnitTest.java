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
package io.github.carlos_emr.carlos.PMmodule.utility;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.TimeZone;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for {@link DateUtils#getDifDays(long, long)} across daylight saving changes
 * (issue #6047).
 *
 * <p>Ontario billing reads a claim's age through this helper ({@code BillingONCHeader1DaoImpl}
 * days since billed and since paid, which decision support compares with code intervals), and the
 * intake forms compare entered dates with it. It divided elapsed milliseconds by 24 hours, so a
 * span crossing spring forward came up a day short. Runs in {@code America/Toronto}.</p>
 *
 * @since 2026-11-09
 */
@Tag("unit")
@DisplayName("PMmodule DateUtils.getDifDays across daylight saving changes")
class PMmoduleDateUtilsDifDaysUnitTest {

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

    private static long at(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute).atZone(TORONTO).toInstant().toEpochMilli();
    }

    @Test
    @DisplayName("should count every calendar day when the span crosses spring forward")
    void shouldCountEveryDay_whenSpanCrossesSpringForward() {
        assertThat(DateUtils.getDifDays(at(2026, 3, 9, 0, 0), at(2026, 3, 1, 0, 0))).isEqualTo(8);
    }

    @Test
    @DisplayName("should not add a day inside the 25-hour fall-back date")
    void shouldNotAddADay_insideTheFallBackDate() {
        assertThat(DateUtils.getDifDays(at(2026, 11, 1, 23, 30), at(2026, 11, 1, 0, 0))).isZero();
    }

    @Test
    @DisplayName("should keep the claim age for a span without a change")
    void shouldKeepTheClaimAge_forSpanWithoutAChange() {
        assertThat(DateUtils.getDifDays(at(2026, 7, 31, 14, 0), at(2026, 7, 1, 0, 0))).isEqualTo(30);
        assertThat(DateUtils.getDifDays(at(2026, 6, 2, 1, 0), at(2026, 6, 1, 23, 0))).isZero();
    }

    @Test
    @DisplayName("should return a negative count when the lesser time is later")
    void shouldReturnNegativeCount_whenLesserTimeIsLater() {
        assertThat(DateUtils.getDifDays(at(2026, 3, 1, 0, 0), at(2026, 3, 9, 0, 0))).isEqualTo(-8);
    }

    @Test
    @DisplayName("should compare intake dates across spring forward by calendar day")
    void shouldCompareIntakeDates_acrossSpringForward() {
        // compareDateForIntakeB parses both dates to local midnights before comparing.
        assertThat(DateUtils.compareDateForIntakeB("2026/03/09", "2026/03/08")).isEqualTo(1);
    }
}
