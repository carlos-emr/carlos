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
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
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

    @ParameterizedTest
    @ValueSource(strings = {"3-Oct-2026", "03-Oct-2026", "29-Feb-2028"})
    void shouldParseDialogDates_withValidCalendarDays(String value) {
        Calendar parsed = ChartPrintDateRange.parseDialogDate(value, java.util.Locale.ENGLISH);
        assertThat(parsed.get(Calendar.DAY_OF_MONTH)).isEqualTo(Integer.parseInt(value.split("-")[0]));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"31-Feb-2026", "29-Feb-2026", "03-Oct-2026junk", "03-Oct-2026 ", "03.-Oct-2026", "03-Oct-2026.", "03-Oct..-2026", "invalid", " "})
    void shouldRejectDialogDates_withInvalidOrIncompleteInput(String value) {
        assertThatIllegalArgumentException().isThrownBy(() -> ChartPrintDateRange.parseDialogDate(value, java.util.Locale.ENGLISH));
    }

    @ParameterizedTest
    @CsvSource({"fr,03-janv-2026,1", "fr,03-janv.-2026,1", "fr,03-févr-2026,2",
            "fr,03-août-2026,8", "pt-BR,03-Out-2026,10", "es,03-Ene-2026,1", "pl,03-Paź-2026,10", "es,03-Sep-2026,9", "fr,03-sept-2026,9",
            "en-GB,03-Sep-2026,9", "en-GB,03-Sept-2026,9"})
    void shouldParseLocalizedMonths_withTheResolvedChartLocale(String language, String value, int month) {
        Calendar parsed = ChartPrintDateRange.parseDialogDate(value, java.util.Locale.forLanguageTag(language));
        assertThat(parsed.get(Calendar.MONTH) + 1).isEqualTo(month);
        assertThat(parsed.get(Calendar.DAY_OF_MONTH)).isEqualTo(3);
        assertThat(parsed.get(Calendar.YEAR)).isEqualTo(2026);
    }

    @Test
    void shouldAcceptServerTodayFormat_whenChartLanguageDiffers() {
        java.util.Locale serverLocale = java.util.Locale.getDefault(java.util.Locale.Category.FORMAT);
        java.util.Locale pageLocale = "fr".equals(serverLocale.getLanguage())
                ? java.util.Locale.ENGLISH : java.util.Locale.FRENCH;
        String value = LocalDate.of(2026, java.time.Month.JANUARY, 3)
                .format(java.time.format.DateTimeFormatter.ofPattern("d-MMM-uuuu", serverLocale));
        Calendar parsed = ChartPrintDateRange.parseDialogDate(value, pageLocale);
        assertThat(parsed.get(Calendar.MONTH)).isEqualTo(Calendar.JANUARY);
        assertThat(parsed.get(Calendar.DAY_OF_MONTH)).isEqualTo(3);
    }

    @Test
    void shouldRejectReversedDays_whenEndPrecedesStart() {
        Calendar start = GregorianCalendar.from(LocalDate.of(2026, java.time.Month.OCTOBER, 3).atStartOfDay(ZoneId.of("UTC")));
        Calendar end = (Calendar) start.clone();
        end.add(Calendar.DATE, -1);
        assertThatIllegalArgumentException().isThrownBy(() -> ChartPrintDateRange.from(start, end));
    }
}
