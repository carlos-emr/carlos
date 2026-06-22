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
package io.github.carlos_emr.carlos.webserv.rest.util;

import java.io.IOException;
import java.time.Instant;
import java.util.Calendar;
import java.util.Date;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.webserv.rest.to.model.AppointmentTo1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies that {@link SmartDateModule} can round-trip a {@link Date} through the JSON
 * shapes {@link SmartDateSerializer} emits — the gap that caused {@code updateAppointment}
 * to fail with {@code InvalidFormatException} when a client POSTed back a {@code "HH:mm:ss"}
 * startTime the API had just returned (issue #2957).
 *
 * @since 2026-06-22
 */
@DisplayName("SmartDateModule round-trip")
@Tag("unit")
@Tag("fast")
class SmartDateModuleRoundTripUnitTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new SmartDateModule());

    private static Date dateOf(int y, int mo, int d, int h, int mi, int s) {
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(y, mo, d, h, mi, s);
        return cal.getTime();
    }

    @Test
    @DisplayName("should serialize a time-only Date as HH:mm:ss and read it back unchanged")
    void shouldRoundTripTimeOnlyDate_withHmsString() throws Exception {
        Date time = dateOf(1970, Calendar.JANUARY, 1, 9, 30, 0);

        String json = mapper.writeValueAsString(time);
        Date back = mapper.readValue(json, Date.class);

        assertThat(json).isEqualTo("\"09:30:00\"");
        assertThat(back).isEqualTo(time);
    }

    @Test
    @DisplayName("should serialize a midnight Date as yyyy-MM-dd and read it back unchanged")
    void shouldRoundTripDateOnly_withIsoDateString() throws Exception {
        Date date = dateOf(2026, Calendar.JUNE, 22, 0, 0, 0);

        String json = mapper.writeValueAsString(date);
        Date back = mapper.readValue(json, Date.class);

        assertThat(json).isEqualTo("\"2026-06-22\"");
        assertThat(back).isEqualTo(date);
    }

    @Test
    @DisplayName("should serialize a date-and-time Date as epoch millis and read it back unchanged")
    void shouldRoundTripDateTime_withEpochMillis() throws Exception {
        Date dateTime = dateOf(2026, Calendar.JUNE, 22, 9, 30, 15);

        String json = mapper.writeValueAsString(dateTime);
        Date back = mapper.readValue(json, Date.class);

        assertThat(json).isEqualTo(String.valueOf(dateTime.getTime()));
        assertThat(back).isEqualTo(dateTime);
    }

    @Test
    @DisplayName("should treat null and empty string as null on deserialize")
    void shouldReturnNull_forNullAndEmptyString() throws Exception {
        assertThat(mapper.readValue("null", Date.class)).isNull();
        assertThat(mapper.readValue("\"\"", Date.class)).isNull();
    }

    @Test
    @DisplayName("should deserialize an epoch-millis value encoded as a string")
    void shouldDeserializeEpochMillis_fromNumericString() throws Exception {
        assertThat(mapper.readValue("\"1234567890000\"", Date.class))
                .isEqualTo(new Date(1234567890000L));
    }

    @Test
    @DisplayName("should deserialize an ISO-8601 datetime string for backward compatibility")
    void shouldDeserializeIso8601_fromDatetimeString() throws Exception {
        // Not a shape the serializer emits, but the default Jackson deserializer accepted it
        // before this module registered one; the StdDateFormat fallback must keep parsing it.
        assertThat(mapper.readValue("\"2026-06-22T09:30:00Z\"", Date.class))
                .isEqualTo(Date.from(Instant.parse("2026-06-22T09:30:00Z")));
    }

    @Test
    @DisplayName("should fail with an IOException for an unparseable date string")
    void shouldThrowIoException_forUnparseableString() {
        assertThatThrownBy(() -> mapper.readValue("\"not-a-date\"", Date.class))
                .isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("should round-trip an appointment's startTime/endTime/appointmentDate through the DTO")
    void shouldRoundTripAppointmentDtoDates_throughSerializer() throws Exception {
        AppointmentTo1 appt = new AppointmentTo1();
        appt.setId(42);
        appt.setProviderNo("999");
        appt.setStartTime(dateOf(1970, Calendar.JANUARY, 1, 9, 30, 0));
        appt.setEndTime(dateOf(1970, Calendar.JANUARY, 1, 9, 45, 0));
        appt.setAppointmentDate(dateOf(2026, Calendar.JUNE, 22, 0, 0, 0));

        String json = mapper.writeValueAsString(appt);
        AppointmentTo1 back = mapper.readValue(json, AppointmentTo1.class);

        assertThat(back.getId()).isEqualTo(42);
        assertThat(back.getStartTime()).isEqualTo(appt.getStartTime());
        assertThat(back.getEndTime()).isEqualTo(appt.getEndTime());
        assertThat(back.getAppointmentDate()).isEqualTo(appt.getAppointmentDate());
    }
}
