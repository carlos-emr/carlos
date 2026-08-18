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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Lock-in tests for the legacy {@link UtilDateUtilities} parse/format helpers, pinning
 * the lenient {@code SimpleDateFormat} behaviour (un-padded fields parse, default locale
 * used by {@link UtilDateUtilities#getDateFromString}) that the date-allocation refactor
 * must preserve.
 *
 * @since 2026-05-23
 */
@Tag("unit")
@DisplayName("UtilDateUtilities")
class UtilDateUtilitiesUnitTest {

    private static final Date FIXED = new Date(1704412800000L); // 2024-01-05T00:00:00Z

    @Test
    @DisplayName("should parse a zero-padded ISO date")
    void shouldParseDate_forIsoPattern() throws Exception {
        Date expected = new SimpleDateFormat("yyyy-MM-dd", Locale.CANADA).parse("2024-03-05");
        assertThat(UtilDateUtilities.StringToDate("2024-03-05", "yyyy-MM-dd", Locale.CANADA))
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("should parse leniently for un-padded fields")
    void shouldParseLeniently_forUnpaddedFields() throws Exception {
        Date expected = new SimpleDateFormat("yyyy-MM-dd", Locale.CANADA).parse("2024-3-5");
        assertThat(UtilDateUtilities.StringToDate("2024-3-5", "yyyy-MM-dd", Locale.CANADA))
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("should return null for unparseable input")
    void shouldReturnNull_forUnparseableInput() {
        assertThat(UtilDateUtilities.StringToDate("garbage", "yyyy-MM-dd", Locale.CANADA)).isNull();
    }

    @Test
    @DisplayName("should format a date with the given pattern and locale")
    void shouldFormatDate_forGivenPattern() {
        assertThat(UtilDateUtilities.DateToString(FIXED, "dd-MMM-yyyy", Locale.ENGLISH))
                .isEqualTo(new SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH).format(FIXED));
    }

    @Test
    @DisplayName("should extract the year via justYear")
    void shouldExtractYear_forJustYear() {
        assertThat(UtilDateUtilities.justYear(FIXED)).isEqualTo(new SimpleDateFormat("yyyy").format(FIXED));
    }

    @Test
    @DisplayName("should extract the month via justMonth")
    void shouldExtractMonth_forJustMonth() {
        assertThat(UtilDateUtilities.justMonth(FIXED)).isEqualTo(new SimpleDateFormat("MM").format(FIXED));
    }

    @Test
    @DisplayName("should extract the day via justDay")
    void shouldExtractDay_forJustDay() {
        assertThat(UtilDateUtilities.justDay(FIXED)).isEqualTo(new SimpleDateFormat("dd").format(FIXED));
    }

    @Test
    @DisplayName("should parse with the default locale via getDateFromString")
    void shouldParseWithDefaultLocale_forGetDateFromString() throws Exception {
        Date expected = new SimpleDateFormat("yyyy-MM-dd").parse("2024-03-05");
        assertThat(UtilDateUtilities.getDateFromString("2024-03-05", "yyyy-MM-dd")).isEqualTo(expected);
    }

    @Test
    @DisplayName("should format today's date for the given pattern")
    void shouldFormatToday_forGetToday() {
        assertThat(UtilDateUtilities.getToday("yyyy-MM-dd")).matches("\\d{4}-\\d{2}-\\d{2}");
    }
}
