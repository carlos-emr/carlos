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
package io.github.carlos_emr.carlos.util;

import io.github.carlos_emr.carlos.commn.model.Demographic;
import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.ChronoUnit;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Checks the age APIs used by demographic screens and the annual review planner. */
@Tag("unit")
class BirthdayAgeUnitTest {
    static Stream<Arguments> ageCalculators() {
        return Stream.of(
                Arguments.of("demographic", (ToIntFunction<LocalDate>) birth -> {
                    Demographic demographic = new Demographic();
                    demographic.setYearOfBirth(String.valueOf(birth.getYear()));
                    demographic.setMonthOfBirth(String.valueOf(birth.getMonthValue()));
                    demographic.setDateOfBirth(String.valueOf(birth.getDayOfMonth()));
                    return Integer.parseInt(demographic.getAge());
                }),
                Arguments.of("PM utility", (ToIntFunction<LocalDate>) birth ->
                        io.github.carlos_emr.carlos.PMmodule.utility.Utility.calcAge(
                                String.valueOf(birth.getYear()), String.valueOf(birth.getMonthValue()), String.valueOf(birth.getDayOfMonth()))),
                Arguments.of("PM date utility", (ToIntFunction<LocalDate>) birth ->
                        io.github.carlos_emr.carlos.PMmodule.utility.UtilDateUtilities.calcAge(
                                String.valueOf(birth.getYear()), String.valueOf(birth.getMonthValue()), String.valueOf(birth.getDayOfMonth()))),
                Arguments.of("annual review date utility", (ToIntFunction<LocalDate>) birth ->
                        UtilDateUtilities.calcAge(String.valueOf(birth.getYear()), String.valueOf(birth.getMonthValue()),
                                String.valueOf(birth.getDayOfMonth()))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("ageCalculators")
    void shouldCountBirthdayInclusively_whenAgeIsDisplayed(String name, ToIntFunction<LocalDate> calculator) {
        LocalDate today = LocalDate.now();
        LocalDate birthday = today.minusYears(24);
        assertThat(calculator.applyAsInt(birthday.plusDays(1))).as(name + " before birthday").isEqualTo(23);
        assertThat(calculator.applyAsInt(birthday)).as(name + " on birthday").isEqualTo(24);
        assertThat(calculator.applyAsInt(birthday.minusDays(1))).as(name + " after birthday").isEqualTo(24);
        assertThat(calculator.applyAsInt(today.minusYears(65))).as(name + " 65th birthday").isEqualTo(65);
        LocalDate leapBirthday = LocalDate.of(2000, Month.FEBRUARY, 29);
        assertThat(calculator.applyAsInt(leapBirthday)).as(name + " leap birthday")
                .isEqualTo((int) ChronoUnit.YEARS.between(leapBirthday, today));
    }
}
