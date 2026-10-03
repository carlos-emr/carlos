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
package io.github.carlos_emr.carlos.casemgmt.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.*;

/** Whole-day printing boundaries, including both daylight-saving transitions. */
@Tag("unit")
class ChartPrintDateRangeUnitTest {
    @ParameterizedTest
    @CsvSource({"2026-10-03,24", "2026-03-08,23", "2026-11-01,25", "2028-02-29,24"})
    void shouldIncludeEntireDay_withLocalClockChanges(String day, long hours) {
        Calendar date = GregorianCalendar.from(LocalDate.parse(day).atStartOfDay(ZoneId.of("America/Toronto")));
        Calendar original = (Calendar) date.clone();
        ChartPrintDateRange range = ChartPrintDateRange.from(date, date);
        assertThat(range.startInclusive()).isEqualTo(LocalDate.parse(day)
                .atStartOfDay(ZoneId.of("America/Toronto")).toInstant());
        assertThat(range.endExclusive()).isEqualTo(LocalDate.parse(day).plusDays(1)
                .atStartOfDay(ZoneId.of("America/Toronto")).toInstant());
        assertThat(Duration.between(range.startInclusive(), range.endExclusive()).toHours()).isEqualTo(hours);
        assertThat(range.contains(Date.from(range.startInclusive()))).isTrue();
        assertThat(range.contains(Date.from(range.endExclusive().minusMillis(1)))).isTrue();
        assertThat(range.contains(Date.from(range.startInclusive().minusMillis(1)))).isFalse();
        assertThat(range.contains(Date.from(range.endExclusive()))).isFalse();
        assertThat(range.contains(null)).isFalse();
        assertThat(date).isEqualTo(original);
    }

    @Test
    void shouldRejectReversedDays_whenEndPrecedesStart() {
        Calendar start = GregorianCalendar.from(LocalDate.of(2026, 10, 3).atStartOfDay(ZoneId.of("UTC")));
        Calendar end = (Calendar) start.clone();
        end.add(Calendar.DATE, -1);
        assertThatIllegalArgumentException().isThrownBy(() -> ChartPrintDateRange.from(start, end));
    }
}
