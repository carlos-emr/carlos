/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.appointment.pageUtil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

/** The stale-form comparison the group page runs under its own transaction, and the refusal it answers with. */
@Tag("unit")
@Tag("appointment")
@DisplayName("AppointmentEditConflict")
class AppointmentEditConflictUnitTest extends CarlosUnitTestBase {

    private final OscarAppointmentDao appointments = mock(OscarAppointmentDao.class);
    private MockHttpServletRequest request;

    private static Appointment at(int id, long version) {
        Appointment appointment = new Appointment();
        appointment.setId(id);
        appointment.setUpdateDateTime(new Date(version));
        return appointment;
    }

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        request.setMethod("POST");
    }

    @Test
    void shouldReportCurrent_whenFormVersionMatchesTheLockedRow() {
        request.setParameter("appointment_no", "61");
        request.setParameter(AppointmentEditConflict.PARAMETER, "5000");
        when(appointments.findForUpdate(61)).thenReturn(at(61, 5000));

        assertThat(AppointmentEditConflict.staleUnderLock(appointments, request)).isFalse();
        verify(appointments).findForUpdate(61);
    }

    @Test
    void shouldReportStale_whenAnotherUserChangedTheAppointment() {
        request.setParameter("appointment_no", "61");
        request.setParameter(AppointmentEditConflict.PARAMETER, "5000");
        when(appointments.findForUpdate(61)).thenReturn(at(61, 6000));

        assertThat(AppointmentEditConflict.staleUnderLock(appointments, request)).isTrue();
    }

    @Test
    void shouldReportStale_whenTheAppointmentWasRemoved() {
        request.setParameter("appointment_no", "61");
        request.setParameter(AppointmentEditConflict.PARAMETER, "5000");
        when(appointments.findForUpdate(61)).thenReturn(null);

        assertThat(AppointmentEditConflict.staleUnderLock(appointments, request)).isTrue();
    }

    @Test
    void shouldNotLockAnything_whenFormCarriesNoVersion() {
        request.setParameter("appointment_no", "61");
        request.setParameter(AppointmentEditConflict.PARAMETER, " ");

        assertThat(AppointmentEditConflict.submittedVersion(request)).isNull();
        assertThat(AppointmentEditConflict.staleUnderLock(appointments, request)).isFalse();
        verifyNoInteractions(appointments);
    }

    @Test
    void shouldReportStale_whenATickedGroupRowChangedSinceItWasListed() {
        // Row 2 is ticked and was listed at version 7000; someone has since changed it.
        request.setParameter("one2", "2");
        request.setParameter("appointment_no2", "72");
        request.setParameter(AppointmentEditConflict.ROW_PARAMETER_PREFIX + "2", "7000");
        when(appointments.findForUpdate(72)).thenReturn(at(72, 8000));

        assertThat(AppointmentEditConflict.staleUnderLock(appointments, request)).isTrue();
    }

    @Test
    void shouldIgnoreGroupRows_whenTheyAreNotTicked() {
        request.setParameter("appointment_no3", "73");
        request.setParameter(AppointmentEditConflict.ROW_PARAMETER_PREFIX + "3", "7000");
        when(appointments.findForUpdate(73)).thenReturn(at(73, 8000));

        assertThat(AppointmentEditConflict.staleUnderLock(appointments, request)).isFalse();
        verify(appointments, never()).findForUpdate(any());
    }

    @Test
    void shouldAnswerConflictWithoutEchoingTheForm_whenRefusing() {
        request.setParameter("appointment_no", "61");
        request.setParameter("provider_no", "999998");
        request.setParameter("reason", "<script>stale</script>");
        MockHttpServletResponse response = new MockHttpServletResponse();

        String result = AppointmentEditConflict.refuse(request, response);

        assertThat(result).isEqualTo(AppointmentEditConflict.RESULT);
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(request.getAttribute("appointmentConflictNo")).isEqualTo("61");
        assertThat(request.getAttribute("appointmentConflictProviderNo")).isEqualTo("999998");
    }

    @Test
    void shouldDropMalformedIdentifiers_whenRefusing() {
        request.setParameter("appointment_no", "61 OR 1=1");
        request.setParameter("provider_no", "\"><script>");

        AppointmentEditConflict.refuse(request, new MockHttpServletResponse());

        assertThat(request.getAttribute("appointmentConflictNo")).isNull();
        assertThat(request.getAttribute("appointmentConflictProviderNo")).isNull();
    }
}
