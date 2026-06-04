/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
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
 * Maintained by the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.utility;

import org.junit.jupiter.api.*;

import java.time.LocalDate;
import java.time.Month;
import java.util.Calendar;
import java.util.GregorianCalendar;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link AgeCalculator} patient age computation.
 *
 * @since 2026-03-31
 */
@DisplayName("AgeCalculator Unit Tests")
@Tag("unit")
@Tag("fast")
@Tag("utility")
@Tag("demographic")
class AgeCalculatorUnitTest {

    private static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 5, 19);

    /**
     * Creates a Calendar birth date using a 1-based month value for readability.
     * GregorianCalendar expects a 0-based month, so this helper performs that conversion.
     */
    private Calendar birthDate(int year, Month month, int dayOfMonth) {
        return new GregorianCalendar(year, month.getValue() - 1, dayOfMonth);
    }

    @Test
    @DisplayName("should calculate adult age correctly")
    void shouldCalculateAdultAge_correctly() {
        Calendar birthDate = birthDate(1996, Month.MAY, 19);

        Age age = AgeCalculator.calculateAge(birthDate, REFERENCE_DATE);

        assertThat(age.getYears()).isEqualTo(30);
        assertThat(age.getMonths()).isZero();
    }

    @Test
    @DisplayName("should calculate infant age in months")
    void shouldCalculateInfantAge_inMonths() {
        Calendar birthDate = birthDate(2025, Month.NOVEMBER, 19);

        Age age = AgeCalculator.calculateAge(birthDate, REFERENCE_DATE);

        assertThat(age.getYears()).isZero();
        assertThat(age.getMonths()).isEqualTo(6);
    }

    @Test
    @DisplayName("should return zero years for newborn")
    void shouldReturnZeroYears_forNewborn() {
        Calendar birthDate = birthDate(2026, Month.MAY, 19);

        Age age = AgeCalculator.calculateAge(birthDate, REFERENCE_DATE);

        assertThat(age.getYears()).isZero();
        assertThat(age.getMonths()).isZero();
        assertThat(age.getDays()).isZero();
    }

    @Test
    @DisplayName("should handle elderly patient age")
    void shouldHandleElderlyAge_forReferenceDate() {
        Calendar birthDate = birthDate(1931, Month.MAY, 19);

        Age age = AgeCalculator.calculateAge(birthDate, REFERENCE_DATE);

        assertThat(age.getYears()).isEqualTo(95);
    }

    @Test
    @DisplayName("should calculate age with specific days")
    void shouldCalculateAge_withSpecificDays() {
        Calendar birthDate = birthDate(2001, Month.FEBRUARY, 19);

        Age age = AgeCalculator.calculateAge(birthDate, REFERENCE_DATE);

        assertThat(age.getYears()).isEqualTo(25);
        assertThat(age.getMonths()).isEqualTo(3);
    }

    @Test
    @DisplayName("should return non-negative values for all age components")
    void shouldReturnNonNegativeValues_forRecentBirthDate() {
        Calendar birthDate = birthDate(2026, Month.MAY, 9);

        Age age = AgeCalculator.calculateAge(birthDate, REFERENCE_DATE);

        assertThat(age.getYears()).isGreaterThanOrEqualTo(0);
        assertThat(age.getMonths()).isGreaterThanOrEqualTo(0);
        assertThat(age.getDays()).isGreaterThanOrEqualTo(0);
    }
}
