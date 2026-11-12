/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.appointment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.Date;
import java.sql.Time;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.appointment.pageUtil.AppointmentEditConflict;
import io.github.carlos_emr.carlos.commn.dao.AppointmentArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.managers.AppointmentUpdateException;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/** The repeat page carries the Edit Appointment form's version; a stale form must not rewrite the series. */
@Tag("unit")
@Tag("appointment")
@DisplayName("RecurringAppointmentService: stale edit form")
class RecurringAppointmentServiceStaleFormUnitTest extends CarlosUnitTestBase {

    private final OscarAppointmentDao appointments = mock(OscarAppointmentDao.class);
    private final AppointmentArchiveDao archives = mock(AppointmentArchiveDao.class);
    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private final LoggedInInfo user = mock(LoggedInInfo.class);
    private final Appointment anchor = new Appointment();
    private RecurringAppointmentService service;
    private Map<String, String> values;

    @BeforeEach
    void setUp() {
        when(user.getLoggedInProviderNo()).thenReturn("999998");
        when(security.hasPrivilege(eq(user), eq("_appointment"), anyString(), isNull())).thenReturn(true);
        anchor.setId(61);
        anchor.setProviderNo("999998");
        anchor.setAppointmentDate(Date.valueOf("2027-01-31"));
        anchor.setStartTime(Time.valueOf("10:00:00"));
        anchor.setEndTime(Time.valueOf("10:14:00"));
        anchor.setStatus("C");
        anchor.setUpdateDateTime(new java.util.Date(6000));
        when(appointments.findForUpdate(61)).thenReturn(anchor);
        when(appointments.findRecurringSeries(eq(anchor), any())).thenReturn(List.of(anchor));
        service = new RecurringAppointmentService(appointments, archives, security);
        values = new HashMap<>(Map.of("appointment_no", "61", "provider_no", "999998",
                "appointment_date", "2027-01-31", "start_time", "10:00", "end_time", "10:14",
                "keyword", "FAKE-PATIENT,Check", "status", "t", "groupappt", "Group Update",
                "everyNum", "1", "everyUnit", "week"));
        values.put("endDate", "14/02/2027");
    }

    @Test
    void shouldUseTheEditFormsVersionField_forTypeContract() {
        assertThat(RecurringAppointmentService.APPOINTMENT_VERSION).isEqualTo(AppointmentEditConflict.PARAMETER);
    }

    @Test
    void shouldRefuseGroupUpdate_whenTheFormWasRenderedBeforeAnotherUsersChange() {
        // The form was rendered at version 5000 (status "t"); the appointment has since been cancelled.
        values.put(RecurringAppointmentService.APPOINTMENT_VERSION, "5000");

        assertThatThrownBy(() -> service.apply(user, values, 0))
                .isInstanceOfSatisfying(AppointmentUpdateException.class, refused ->
                        assertThat(refused.getReason()).isEqualTo(AppointmentUpdateException.Reason.STALE_VERSION));

        assertThat(anchor.getStatus()).isEqualTo("C");
        verifyNoInteractions(archives);
        verify(appointments, never()).merge(any());
        verify(appointments, never()).remove(any(Integer.class));
    }

    @Test
    void shouldApplyGroupUpdate_whenTheFormVersionIsCurrent() {
        values.put(RecurringAppointmentService.APPOINTMENT_VERSION, "6000");

        int updated = service.apply(user, values, 0);

        assertThat(updated).isEqualTo(1);
        assertThat(anchor.getStatus()).isEqualTo("t");
        verify(archives).archiveAppointment(anchor);
        verify(appointments).merge(anchor);
    }

    @Test
    void shouldKeepUnversionedBehaviour_whenThePageCarriesNoVersion() {
        int updated = service.apply(user, values, 0);

        assertThat(updated).isEqualTo(1);
        verify(appointments).merge(anchor);
    }

    @Test
    void shouldAuditEachAppointmentOfTheSeries_whenAGroupUpdateIsApplied() {
        anchor.setDemographicNo(42);
        // A Group Update writes the form's fields, the patient among them.
        values.put("demographic_no", "42");

        service.apply(user, values, 0);

        // Under the appointment and its patient, like the single-appointment popup; never its text.
        logActionMock.verify(() -> io.github.carlos_emr.carlos.log.LogAction.addLog(user,
                io.github.carlos_emr.carlos.log.LogConst.UPDATE, io.github.carlos_emr.carlos.log.LogConst.CON_APPT,
                "61", "42", null));
    }

    @Test
    void shouldNameTheCancellation_whenAGroupCancelIsApplied() {
        anchor.setDemographicNo(42);
        values.put("groupappt", "Group Cancel");

        service.apply(user, values, 0);

        assertThat(anchor.getStatus()).isEqualTo("C");
        logActionMock.verify(() -> io.github.carlos_emr.carlos.log.LogAction.addLog(user,
                io.github.carlos_emr.carlos.log.LogConst.UPDATE, io.github.carlos_emr.carlos.log.LogConst.CON_APPT,
                "61", "42", "status=C"));
    }

    @Test
    void shouldAuditTheRemoval_whenAGroupDeleteIsApplied() {
        anchor.setDemographicNo(42);
        values.put("groupappt", "Group Delete");

        service.apply(user, values, 0);

        verify(appointments).remove(61);
        logActionMock.verify(() -> io.github.carlos_emr.carlos.log.LogAction.addLog(user,
                io.github.carlos_emr.carlos.log.LogConst.DELETE, io.github.carlos_emr.carlos.log.LogConst.CON_APPT,
                "61", "42", null));
    }

    @Test
    void shouldNotAudit_whenAStaleFormIsRefused() {
        anchor.setDemographicNo(42);
        values.put(RecurringAppointmentService.APPOINTMENT_VERSION, "5000");

        assertThatThrownBy(() -> service.apply(user, values, 0));

        logActionMock.verify(() -> io.github.carlos_emr.carlos.log.LogAction.addLog(any(LoggedInInfo.class), anyString(),
                anyString(), anyString(), org.mockito.ArgumentMatchers.nullable(String.class),
                org.mockito.ArgumentMatchers.nullable(String.class)), never());
    }
}
