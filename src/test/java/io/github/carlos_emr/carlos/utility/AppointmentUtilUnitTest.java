/**
 * Copyright (c) 2026 CARLOS EMR Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.utility;

import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("AppointmentUtil Unit Tests")
class AppointmentUtilUnitTest extends CarlosUnitTestBase {

    @Test
    @DisplayName("getNextAppointment returns none without DAO lookup for missing demographic numbers")
    void getNextAppointmentReturnsNoneForMissingDemographicNo() {
        OscarAppointmentDao appointmentDao = mock(OscarAppointmentDao.class);
        registerMock(OscarAppointmentDao.class, appointmentDao);

        assertEquals(AppointmentUtil.NONE, AppointmentUtil.getNextAppointment(null));
        assertEquals(AppointmentUtil.NONE, AppointmentUtil.getNextAppointment(""));
        assertEquals(AppointmentUtil.NONE, AppointmentUtil.getNextAppointment("null"));
        assertEquals(AppointmentUtil.NONE, AppointmentUtil.getNextAppointment("   "));
        assertEquals(AppointmentUtil.NONE, AppointmentUtil.getNextAppointment("abc"));

        verifyNoInteractions(appointmentDao);
    }

    @Test
    @DisplayName("getNextAppointment looks up and formats the next appointment date for valid demographic numbers")
    void getNextAppointmentReturnsNextAppointmentDateForValidDemographicNo() {
        OscarAppointmentDao appointmentDao = mock(OscarAppointmentDao.class);
        registerMock(OscarAppointmentDao.class, appointmentDao);

        Appointment appointment = new Appointment();
        appointment.setAppointmentDate(date(2026, Calendar.JULY, 15));
        when(appointmentDao.findNextAppointment(123)).thenReturn(appointment);

        assertEquals("2026-07-15", AppointmentUtil.getNextAppointment(" 123 "));
        verify(appointmentDao).findNextAppointment(123);
    }

    @Test
    @DisplayName("getNextAppointment returns none when no future appointment exists")
    void getNextAppointmentReturnsNoneWhenNoAppointmentExists() {
        OscarAppointmentDao appointmentDao = mock(OscarAppointmentDao.class);
        registerMock(OscarAppointmentDao.class, appointmentDao);
        when(appointmentDao.findNextAppointment(123)).thenReturn(null);

        assertEquals(AppointmentUtil.NONE, AppointmentUtil.getNextAppointment("123"));
        verify(appointmentDao).findNextAppointment(123);
    }

    @Test
    @DisplayName("getNextAppointment returns none when the next appointment date is missing")
    void getNextAppointmentReturnsNoneWhenAppointmentDateIsMissing() {
        OscarAppointmentDao appointmentDao = mock(OscarAppointmentDao.class);
        registerMock(OscarAppointmentDao.class, appointmentDao);

        Appointment appointment = new Appointment();
        when(appointmentDao.findNextAppointment(123)).thenReturn(appointment);

        assertEquals(AppointmentUtil.NONE, AppointmentUtil.getNextAppointment("123"));
        verify(appointmentDao).findNextAppointment(123);
    }

    private static Date date(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, month, day);
        return calendar.getTime();
    }
}
