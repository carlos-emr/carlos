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
package io.github.carlos_emr.carlos.webserv.rest;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Date;

import jakarta.ws.rs.POST;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.webserv.rest.to.model.AppointmentTo1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression guards for the appointment endpoints repaired under issue #2957:
 * the missing {@code @POST} on {@code updateAppointmentUrgency} (routed as 404), and the
 * JDBC value coercion in {@code fetchDays} (java.time / String values that previously threw
 * ClassCastException).
 *
 * @since 2026-06-22
 */
@DisplayName("ScheduleService appointment endpoint guards")
@Tag("unit")
@Tag("fast")
class ScheduleServiceAppointmentEndpointsUnitTest {

    @Test
    @DisplayName("should annotate updateAppointmentUrgency with @POST so JAX-RS can route it")
    void shouldAnnotateUpdateUrgency_withPost() throws Exception {
        Method method = ScheduleService.class.getMethod(
                "updateAppointmentUrgency", Integer.class, AppointmentTo1.class);

        assertThat(method.isAnnotationPresent(POST.class))
                .as("updateAppointmentUrgency must be a @POST endpoint")
                .isTrue();
    }

    @Test
    @DisplayName("should coerce java.time date/time/datetime values to java.util.Date")
    void shouldCoerceJavaTimeValues_toDate() throws Exception {
        Method toDate = ScheduleService.class.getDeclaredMethod("toDate", Object.class);
        toDate.setAccessible(true);

        assertThat(toDate.invoke(null, LocalDate.of(2026, 6, 22)))
                .isEqualTo(java.sql.Date.valueOf(LocalDate.of(2026, 6, 22)));
        assertThat(toDate.invoke(null, LocalTime.of(9, 30, 0)))
                .isEqualTo(java.sql.Time.valueOf(LocalTime.of(9, 30, 0)));
        assertThat(toDate.invoke(null, LocalDateTime.of(2026, 6, 22, 9, 30, 0)))
                .isEqualTo(java.sql.Timestamp.valueOf(LocalDateTime.of(2026, 6, 22, 9, 30, 0)));

        Date util = new Date(0);
        assertThat(toDate.invoke(null, util)).isSameAs(util);
        assertThat(toDate.invoke(null, new Object[]{null})).isNull();
    }

    @Test
    @DisplayName("should throw IllegalArgumentException when toDate is given an unsupported type")
    void shouldThrow_whenToDateGivenUnsupportedType() throws Exception {
        Method toDate = ScheduleService.class.getDeclaredMethod("toDate", Object.class);
        toDate.setAccessible(true);

        assertThatThrownBy(() -> toDate.invoke(null, new Object()))
                .isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("should coerce CHAR column values (String or Character) to Character")
    void shouldCoerceCharColumn_toCharacter() throws Exception {
        Method toCharacter = ScheduleService.class.getDeclaredMethod("toCharacter", Object.class);
        toCharacter.setAccessible(true);

        assertThat(toCharacter.invoke(null, "N")).isEqualTo('N');
        assertThat(toCharacter.invoke(null, Character.valueOf('B'))).isEqualTo('B');
        assertThat(toCharacter.invoke(null, "")).isNull();
        assertThat(toCharacter.invoke(null, "   ")).isNull();
        assertThat(toCharacter.invoke(null, new Object[]{null})).isNull();
    }
}
