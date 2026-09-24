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

import java.util.Calendar;
import java.util.GregorianCalendar;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for {@link DateUtils#yearDifference(Calendar, Calendar)} and
 * {@link DateUtils#getAge(Calendar, Calendar)}.
 *
 * <p>This is the copy of these helpers that production calls -- {@code Cds4ReportUIBean} uses
 * both, for a patient's age at the reporting date and for the years an admission spanned. The
 * identically named helpers in {@code io.github.carlos_emr.carlos.utility.DateUtils} have no
 * production callers, so a fix applied only there leaves the live path wrong; these cases pin
 * the live one.</p>
 *
 * <p>The defect was an inverted birthday comparison: a year was subtracted when the later date's
 * month was the <em>greater</em> one, so an age was reported a year low for most of the year and
 * correct only after the birthday month. February 29 had no defined answer at all.</p>
 *
 * @since 2026-09-14
 */
@Tag("unit")
@DisplayName("DateUtils year difference")
class DateUtilsYearDifferenceUnitTest {

    /** Month is 1-based here; {@link Calendar} counts from zero and that is the bug's usual source. */
    private static Calendar date(int year, int month, int day) {
        Calendar calendar = new GregorianCalendar();
        calendar.clear();
        calendar.set(year, month - 1, day);
        return calendar;
    }

    @Nested
    @DisplayName("yearDifference")
    class YearDifference {

        @Test
        @DisplayName("should count only completed years before the anniversary")
        void shouldNotCountThePartialYear_whenTheAnniversaryHasNotArrived() {
            assertThat(DateUtils.yearDifference(date(2000, 6, 15), date(2026, 6, 14))).isEqualTo(25);
        }

        @Test
        @DisplayName("should count the year on the anniversary itself")
        void shouldCountTheYear_onTheAnniversary() {
            assertThat(DateUtils.yearDifference(date(2000, 6, 15), date(2026, 6, 15))).isEqualTo(26);
        }

        /**
         * The inverted comparison showed itself here: with the later date in a greater month the
         * old code subtracted a year, answering 25 for a span that had clearly completed 26.
         */
        @Test
        @DisplayName("should not subtract a year once the anniversary has passed")
        void shouldCountTheYear_afterTheAnniversaryMonth() {
            assertThat(DateUtils.yearDifference(date(2000, 1, 10), date(2026, 11, 20))).isEqualTo(26);
        }

        @Test
        @DisplayName("should return null when either date is missing")
        void shouldReturnNull_forAMissingDate() {
            assertThat(DateUtils.yearDifference(null, date(2026, 1, 1))).isNull();
            assertThat(DateUtils.yearDifference(date(2026, 1, 1), null)).isNull();
        }
    }

    @Nested
    @DisplayName("getAge")
    class GetAge {

        @Test
        @DisplayName("should report the age reached at the reference date")
        void shouldReportCompletedYears_atTheReferenceDate() {
            assertThat(DateUtils.getAge(date(1996, 3, 1), date(2026, 3, 1))).isEqualTo(30);
            assertThat(DateUtils.getAge(date(1996, 3, 1), date(2026, 2, 28))).isEqualTo(29);
        }

        /**
         * A February 29 birth date has no anniversary in a non-leap year. {@code Period} settles
         * it on March 1 -- February 28 is still short of a full year -- and that is the answer
         * pinned here, because the behaviour is a convention rather than an arithmetic fact and
         * both copies of this helper must give the same one.
         *
         * <p>This is where the old code showed its inverted comparison most plainly: on March 1
         * it saw the later month, subtracted a year, and reported 28 for a patient who had
         * completed 29.</p>
         */
        @Test
        @DisplayName("should age a February 29 birth date on March 1 of a non-leap year")
        void shouldResolveTheMissingAnniversary_forAFebruary29BirthDate() {
            assertThat(DateUtils.getAge(date(1996, 2, 29), date(2025, 2, 27))).isEqualTo(28);
            assertThat(DateUtils.getAge(date(1996, 2, 29), date(2025, 2, 28))).isEqualTo(28);
            assertThat(DateUtils.getAge(date(1996, 2, 29), date(2025, 3, 1))).isEqualTo(29);
            // The real anniversary, in a leap year.
            assertThat(DateUtils.getAge(date(1996, 2, 29), date(2024, 2, 29))).isEqualTo(28);
        }
    }
}
