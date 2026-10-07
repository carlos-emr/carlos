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
package io.github.carlos_emr.carlos.commn.model;

import java.time.LocalDate;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.AgeCalculator;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DemographicTo1;
import io.github.carlos_emr.carlos.webserv.rest.util.SmartDateModule;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("demographic")
@Isolated("Changes the JVM default timezone and restores it after each assertion")
class DemographicBirthDayTimezoneUnitTest extends CarlosUnitTestBase {

    @ParameterizedTest
    @CsvSource({
            "America/Toronto,1980-06-15", "America/Toronto,1980-01-15",
            "Asia/Tokyo,1980-06-15", "Asia/Tokyo,1980-01-15",
            "America/St_Johns,2000-02-29", "Asia/Kathmandu,2000-02-29"
    })
    void shouldReturnLocalMidnight_whenServerUsesNonUtcTimezone(String zone, String date) {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(zone));
            Calendar birthday = demographic(date).getBirthDay();
            LocalDate expected = LocalDate.parse(date);
            assertThat(birthday.get(Calendar.YEAR)).isEqualTo(expected.getYear());
            assertThat(birthday.get(Calendar.MONTH)).isEqualTo(expected.getMonthValue() - 1);
            assertThat(birthday.get(Calendar.DAY_OF_MONTH)).isEqualTo(expected.getDayOfMonth());
            assertThat(birthday.get(Calendar.HOUR_OF_DAY)).isZero();
            assertThat(birthday.get(Calendar.MINUTE)).isZero();
            assertThat(birthday.get(Calendar.SECOND)).isZero();
            assertThat(birthday.get(Calendar.MILLISECOND)).isZero();
            assertThat(demographic(date).getFormattedDob()).isEqualTo(date);
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "America/Toronto,1980-06-15", "Asia/Tokyo,1980-06-15",
            "America/Toronto,1970-01-01", "Asia/Tokyo,1970-01-01",
            "UTC,1970-01-01", "America/Sao_Paulo,2018-11-04"
    })
    void shouldSerializeBirthDateAsDateOnly_whenLocalTimeCouldBeAmbiguous(String zone, String date) throws Exception {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(zone));
            DemographicTo1 dto = new DemographicTo1();
            // The same Date conversion used by DemographicConverter.
            dto.setDateOfBirth(demographic(date).getBirthDay().getTime());
            ObjectMapper mapper = new ObjectMapper().registerModule(new SmartDateModule());
            JsonNode value = mapper.readTree(mapper.writeValueAsString(dto)).get("dateOfBirth");
            assertThat(value.isTextual()).as("birth date must be a JSON string").isTrue();
            assertThat(value.textValue()).isEqualTo(date);
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"America/Toronto", "Asia/Tokyo"})
    void shouldPreserveAgeBoundaries_whenBirthDateTimeIsCleared(String zone) {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(zone));
            Calendar birthday = demographic("2000-06-15").getBirthDay();
            assertThat(io.github.carlos_emr.carlos.util.DateUtils.getAge(birthday,
                    new GregorianCalendar(2026, Calendar.JUNE, 14))).isEqualTo(25);
            assertThat(io.github.carlos_emr.carlos.util.DateUtils.getAge(birthday,
                    new GregorianCalendar(2026, Calendar.JUNE, 15))).isEqualTo(26);
            // AgeCalculator (the REST caller) compares the calendar date to local today.
            LocalDate today = LocalDate.now();
            assertThat(AgeCalculator.calculateAge(demographic(today.minusYears(20).toString()).getBirthDay())
                    .getYears()).isEqualTo(20);
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void shouldReturnNull_whenBirthDateComponentsAreMissing() {
        assertThat(new Demographic().getBirthDay()).isNull();
        Demographic partial = demographic("1980-06-15");
        partial.setMonthOfBirth(null);
        assertThat(partial.getBirthDay()).isNull();
    }

    @Test
    void shouldPreserveNullBirthDate_whenSerializingTransferObject() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new SmartDateModule());
        assertThat(mapper.readTree(mapper.writeValueAsString(new DemographicTo1()))
                .get("dateOfBirth").isNull()).isTrue();
    }

    private static Demographic demographic(String date) {
        String[] parts = date.split("-");
        Demographic demographic = new Demographic();
        demographic.setYearOfBirth(parts[0]);
        demographic.setMonthOfBirth(parts[1]);
        demographic.setDateOfBirth(parts[2]);
        return demographic;
    }
}
