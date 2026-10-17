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
package io.github.carlos_emr.carlos.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.text.SimpleDateFormat;
import java.util.GregorianCalendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The period an MIS report covers for the form's 1-12 month lists (issue #4846: the months went
 * straight into {@link GregorianCalendar}, which counts from 0, so every report covered the month
 * after the one chosen).
 */
@Tag("unit")
@Tag("report")
@DisplayName("MIS report period")
class MisReportUIBeanReportPeriodUnitTest {

    private static String instant(GregorianCalendar calendar) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(calendar.getTime());
    }

    @Test
    void shouldCoverTheChosenMonth_forOneMonth() {
        MisReportUIBean.ReportPeriod period = MisReportUIBean.ReportPeriod.ofMonths(2026, 8, 2026, 8);

        assertThat(instant(period.start())).isEqualTo("2026-08-01 00:00:00.000");
        assertThat(instant(period.endExclusive())).isEqualTo("2026-09-01 00:00:00.000");
        assertThat(period.firstDay()).isEqualTo("2026-08-01");
        assertThat(period.lastDay()).isEqualTo("2026-08-31");
    }

    @Test
    void shouldEndOnTheFirstOfJanuary_whenDecemberIsTheLastMonth() {
        MisReportUIBean.ReportPeriod period = MisReportUIBean.ReportPeriod.ofMonths(2025, 12, 2025, 12);

        assertThat(instant(period.start())).isEqualTo("2025-12-01 00:00:00.000");
        assertThat(instant(period.endExclusive())).isEqualTo("2026-01-01 00:00:00.000");
        assertThat(period.lastDay()).isEqualTo("2025-12-31");
    }

    @Test
    void shouldStartInJanuary_whenJanuaryIsTheFirstMonth() {
        MisReportUIBean.ReportPeriod period = MisReportUIBean.ReportPeriod.ofMonths(2026, 1, 2026, 3);

        assertThat(instant(period.start())).isEqualTo("2026-01-01 00:00:00.000");
        assertThat(instant(period.endExclusive())).isEqualTo("2026-04-01 00:00:00.000");
        assertThat(period.lastDay()).isEqualTo("2026-03-31");
    }

    @Test
    void shouldSpanTheYearEnd_whenThePeriodRunsFromDecemberToJanuary() {
        MisReportUIBean.ReportPeriod period = MisReportUIBean.ReportPeriod.ofMonths(2025, 12, 2026, 1);

        assertThat(period.firstDay()).isEqualTo("2025-12-01");
        assertThat(instant(period.endExclusive())).isEqualTo("2026-02-01 00:00:00.000");
        assertThat(period.lastDay()).isEqualTo("2026-01-31");
    }

    @Test
    void shouldEndOnTheTwentyNinth_forFebruaryOfALeapYear() {
        MisReportUIBean.ReportPeriod period = MisReportUIBean.ReportPeriod.ofMonths(2028, 2, 2028, 2);

        assertThat(period.lastDay()).isEqualTo("2028-02-29");
        assertThat(instant(period.endExclusive())).isEqualTo("2028-03-01 00:00:00.000");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 13, -1})
    void shouldRefuseTheMonth_whenItIsOutsideOneToTwelve(int month) {
        assertThatThrownBy(() -> MisReportUIBean.ReportPeriod.ofMonths(2026, month, 2026, 8))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MisReportUIBean.ReportPeriod.ofMonths(2026, 8, 2026, month))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
