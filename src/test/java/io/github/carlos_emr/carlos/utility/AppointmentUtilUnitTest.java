/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 *
 * Maintained by the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.utility;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Covers patient lookup, missing appointments and invalid patient identifiers. */
@Tag("unit") @Tag("fast") @Tag("utility")
class AppointmentUtilUnitTest extends CarlosUnitTestBase {
    private OscarAppointmentDao dao;

    @BeforeEach
    void registerDao() {
        dao = mock(OscarAppointmentDao.class);
        registerMock(OscarAppointmentDao.class, dao);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"null", "NULL"})
    void shouldSkipLookup_whenIdentifierIsAbsent(String identifier) {
        assertThat(AppointmentUtil.getNextAppointment(identifier)).isEqualTo("(none)");
        verifyNoInteractions(dao);
    }

    @Test
    void shouldReturnAppointmentDate_whenPatientHasNextAppointment() {
        Appointment appointment = new Appointment();
        appointment.setAppointmentDate(java.sql.Date.valueOf("2026-06-15"));
        when(dao.findNextAppointment(12345)).thenReturn(appointment);
        assertThat(AppointmentUtil.getNextAppointment("12345")).isEqualTo("2026-06-15");
        verify(dao).findNextAppointment(12345);
    }

    @Test
    void shouldReturnNone_whenPatientHasNoNextAppointment() {
        assertThat(AppointmentUtil.getNextAppointment("12345")).isEqualTo("(none)");
        verify(dao).findNextAppointment(12345);
    }

    @Test
    void shouldPropagateLookupFailure_insteadOfReportingNoAppointment() {
        when(dao.findNextAppointment(12345)).thenThrow(new IllegalStateException("lookup failed"));
        assertThatThrownBy(() -> AppointmentUtil.getNextAppointment("12345"))
                .isInstanceOf(IllegalStateException.class).hasMessage("lookup failed");
    }
}
