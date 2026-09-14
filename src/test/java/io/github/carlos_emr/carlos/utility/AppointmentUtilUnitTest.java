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
package io.github.carlos_emr.carlos.utility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Calendar;
import java.util.Date;

import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link AppointmentUtil}, the next-appointment lookup behind the patient
 * autocomplete's "next appointment" column.
 *
 * <p>The sentinel is asserted as the literal {@code "(none)"} rather than through a shared
 * constant: it is what the JSON and the page actually show, so the literal is the contract
 * this test pins.</p>
 *
 * @since 2026-09-14
 */
@Tag("unit")
@DisplayName("AppointmentUtil")
class AppointmentUtilUnitTest extends CarlosUnitTestBase {

    private static final String NONE = "(none)";
    private static final int DEMOGRAPHIC_NO = 123;

    private OscarAppointmentDao appointmentDao;

    @BeforeEach
    void registerAppointmentDao() {
        appointmentDao = mock(OscarAppointmentDao.class);
        registerMock(OscarAppointmentDao.class, appointmentDao);
    }

    /**
     * Input that identifies no patient must fail closed: issue #2651 was an inverted guard that
     * sent exactly these values to the database and returned the sentinel for real ones.
     */
    @Nested
    @DisplayName("input that identifies no patient")
    class RejectedInput {

        @ParameterizedTest(name = "[{index}] demographicNo={0}")
        @NullSource
        @ValueSource(strings = {
            "", "   ", "null", "abc", "12a", "1.5", "-1", "0",
            // Over int range. ConversionUtils.fromIntString() maps these to 0, which would look
            // up whichever patient holds demographic 0 rather than rejecting the input.
            "2147483648", "99999999999"})
        @DisplayName("should return the sentinel without a lookup")
        void shouldReturnNone_forInputThatIdentifiesNoPatient(String demographicNo) {
            assertThat(AppointmentUtil.getNextAppointment(demographicNo)).isEqualTo(NONE);

            verifyNoInteractions(appointmentDao);
        }
    }

    @Nested
    @DisplayName("input that identifies a patient")
    class AcceptedInput {

        @Test
        @DisplayName("should return the next appointment date")
        void shouldReturnFormattedDate_whenNextAppointmentExists() {
            Appointment appointment = new Appointment();
            appointment.setAppointmentDate(date(2026, Calendar.JULY, 15));
            when(appointmentDao.findNextAppointment(DEMOGRAPHIC_NO)).thenReturn(appointment);

            assertThat(AppointmentUtil.getNextAppointment(String.valueOf(DEMOGRAPHIC_NO)))
                .isEqualTo("2026-07-15");
            verify(appointmentDao).findNextAppointment(DEMOGRAPHIC_NO);
        }

        @Test
        @DisplayName("should ignore surrounding whitespace")
        void shouldReturnFormattedDate_whenDemographicNoIsPadded() {
            Appointment appointment = new Appointment();
            appointment.setAppointmentDate(date(2026, Calendar.JULY, 15));
            when(appointmentDao.findNextAppointment(DEMOGRAPHIC_NO)).thenReturn(appointment);

            assertThat(AppointmentUtil.getNextAppointment("  " + DEMOGRAPHIC_NO + "  "))
                .isEqualTo("2026-07-15");
            verify(appointmentDao).findNextAppointment(DEMOGRAPHIC_NO);
        }

        @Test
        @DisplayName("should return the sentinel when the patient has no next appointment")
        void shouldReturnNone_whenNoAppointmentExists() {
            when(appointmentDao.findNextAppointment(DEMOGRAPHIC_NO)).thenReturn(null);

            assertThat(AppointmentUtil.getNextAppointment(String.valueOf(DEMOGRAPHIC_NO)))
                .isEqualTo(NONE);
            verify(appointmentDao).findNextAppointment(DEMOGRAPHIC_NO);
        }

        @Test
        @DisplayName("should return the sentinel when the next appointment carries no date")
        void shouldReturnNone_whenAppointmentDateIsMissing() {
            when(appointmentDao.findNextAppointment(DEMOGRAPHIC_NO)).thenReturn(new Appointment());

            assertThat(AppointmentUtil.getNextAppointment(String.valueOf(DEMOGRAPHIC_NO)))
                .isEqualTo(NONE);
            verify(appointmentDao).findNextAppointment(DEMOGRAPHIC_NO);
        }
    }

    private static Date date(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, month, day);
        return calendar.getTime();
    }
}
