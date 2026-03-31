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

import java.util.Calendar;

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

    @Test
    @DisplayName("should calculate adult age correctly")
    void shouldCalculateAdultAge_correctly() {
        Calendar birthDate = Calendar.getInstance();
        birthDate.add(Calendar.YEAR, -30);

        Age age = AgeCalculator.calculateAge(birthDate);

        assertThat(age.getYears()).isEqualTo(30);
        assertThat(age.getMonths()).isZero();
    }

    @Test
    @DisplayName("should calculate infant age in months")
    void shouldCalculateInfantAge_inMonths() {
        Calendar birthDate = Calendar.getInstance();
        birthDate.add(Calendar.MONTH, -6);

        Age age = AgeCalculator.calculateAge(birthDate);

        assertThat(age.getYears()).isZero();
        assertThat(age.getMonths()).isBetween(5, 6); // allow for day-of-month edge
    }

    @Test
    @DisplayName("should return zero years for newborn")
    void shouldReturnZeroYears_forNewborn() {
        Calendar birthDate = Calendar.getInstance();

        Age age = AgeCalculator.calculateAge(birthDate);

        assertThat(age.getYears()).isZero();
        assertThat(age.getMonths()).isZero();
        assertThat(age.getDays()).isBetween(0, 1);
    }

    @Test
    @DisplayName("should handle elderly patient age")
    void shouldHandleElderlyAge() {
        Calendar birthDate = Calendar.getInstance();
        birthDate.add(Calendar.YEAR, -95);

        Age age = AgeCalculator.calculateAge(birthDate);

        assertThat(age.getYears()).isEqualTo(95);
    }

    @Test
    @DisplayName("should calculate age with specific days")
    void shouldCalculateAge_withSpecificDays() {
        Calendar birthDate = Calendar.getInstance();
        birthDate.add(Calendar.YEAR, -25);
        birthDate.add(Calendar.MONTH, -3);

        Age age = AgeCalculator.calculateAge(birthDate);

        assertThat(age.getYears()).isEqualTo(25);
        assertThat(age.getMonths()).isBetween(2, 3);
    }

    @Test
    @DisplayName("should return non-negative values for all age components")
    void shouldReturnNonNegativeValues() {
        Calendar birthDate = Calendar.getInstance();
        birthDate.add(Calendar.DAY_OF_MONTH, -10);

        Age age = AgeCalculator.calculateAge(birthDate);

        assertThat(age.getYears()).isGreaterThanOrEqualTo(0);
        assertThat(age.getMonths()).isGreaterThanOrEqualTo(0);
        assertThat(age.getDays()).isGreaterThanOrEqualTo(0);
    }
}
