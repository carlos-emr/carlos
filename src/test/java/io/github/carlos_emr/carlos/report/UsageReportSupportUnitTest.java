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
package io.github.carlos_emr.carlos.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.TimeZone;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("unit")
class UsageReportSupportUnitTest {
    @ParameterizedTest
    @CsvSource({"6,1,16.67", "6,2,33.33", "6,6,100", "6,0,0", "0,0,---", "0,1,---"})
    void shouldFormatReadablePercentage_whenGivenCounts(int total, int count, String expected) {
        assertThat(UsageReportSupport.percentage(total, count)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-05-31", "2026-03-08", "2026-11-01", "2026-12-31"})
    @ResourceLock("java.util.TimeZone.default")
    void shouldIncludeWholeCalendarDay_whenEndDateCrossesBoundary(String value) {
        TimeZone previous = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Toronto"));
            ZoneId zone = ZoneId.systemDefault();
            LocalDate date = LocalDate.parse(value);
            Date selected = Date.from(date.atStartOfDay(zone).toInstant());
            Date nextDay = Date.from(date.plusDays(1).atStartOfDay(zone).toInstant());
            assertThat(UsageReportSupport.exclusiveEnd(selected)).isEqualTo(nextDay);
            assertThat(UsageReportSupport.inclusiveEnd(selected).getTime()).isEqualTo(nextDay.getTime() - 1);
            assertThat(selected).isEqualTo(Date.from(date.atStartOfDay(zone).toInstant()));
        } finally {
            TimeZone.setDefault(previous);
        }
    }

    @Test
    void shouldPreserveNull_whenDateIsAbsent() {
        assertThat(UsageReportSupport.exclusiveEnd(null)).isNull();
        assertThat(UsageReportSupport.inclusiveEnd(null)).isNull();
    }
}
