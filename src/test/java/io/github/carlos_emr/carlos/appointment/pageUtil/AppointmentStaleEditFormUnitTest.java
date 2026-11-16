/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.appointment.pageUtil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.function.Consumer;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import io.github.carlos_emr.carlos.appt.ApptUtil;
import io.github.carlos_emr.carlos.commn.OtherIdManager;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.dao.OtherIdDAO;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.event.EventService;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.managers.AppointmentUpdateException;
import io.github.carlos_emr.carlos.managers.ScheduleManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * The classic Edit Appointment form's write routes hand the version the form was rendered from to
 * the locked manager path and refuse a stale form with HTTP 409 and no side effect.
 */
@Tag("unit")
@Tag("appointment")
@DisplayName("Edit Appointment form: stale form refusal")
class AppointmentStaleEditFormUnitTest extends CarlosUnitTestBase {

    private static final String RENDERED_VERSION = "1700000000000";

    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private final OscarAppointmentDao appointments = mock(OscarAppointmentDao.class);
    private final EventService events = mock(EventService.class);
    private final ScheduleManager schedule = mock(ScheduleManager.class);
    private final LoggedInInfo info = mock(LoggedInInfo.class);
    private final Appointment existing = new Appointment();
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> sessions;
    private MockedStatic<OtherIdManager> otherIds;

    @BeforeEach
    void setUp() {
        registerMock(SecurityInfoManager.class, security);
        registerMock(OscarAppointmentDao.class, appointments);
        registerMock(EventService.class, events);
        registerMock(ScheduleManager.class, schedule);
        // OtherIdManager resolves its DAO in a static initializer.
        registerMock(OtherIdDAO.class, mock(OtherIdDAO.class));
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenAnswer(call -> request);
        servlet.when(ServletActionContext::getResponse).thenAnswer(call -> response);
        sessions = mockStatic(LoggedInInfo.class);
        sessions.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class))).thenReturn(info);
        otherIds = mockStatic(OtherIdManager.class);
        when(info.getLoggedInProviderNo()).thenReturn("999998");
        when(security.hasPrivilege(eq(info), eq("_appointment"), any(), isNull())).thenReturn(true);

        existing.setId(61);
        existing.setProviderNo("999998");
        existing.setName("FAKE-PATIENT,Check");
        existing.setReason("prior");
        existing.setStatus("t");
        existing.setUpdateDateTime(new Date(Long.parseLong(RENDERED_VERSION)));
        when(appointments.find(61)).thenReturn(existing);

        request = new MockHttpServletRequest();
        request.setSession(new MockHttpSession());
        request.getSession().setAttribute("user", "999998");
        request.setMethod("POST");
        for (String[] field : new String[][] {{"provider_no", "999998"}, {"appointment_no", "61"},
                {"appointment_date", "2027-10-21"}, {"start_time", "09:00"}, {"end_time", "09:14"},
                {"keyword", "FAKE-PATIENT,Check"}, {"reason", "edited by a stale form"}, {"notes", ""},
                {"resources", ""}, {"demographic_no", ""}, {"status", "t"}}) {
            request.setParameter(field[0], field[1]);
        }
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void closeStatics() {
        for (MockedStatic<?> mocked : new MockedStatic<?>[] {otherIds, sessions, servlet}) {
            if (mocked != null) mocked.close();
        }
    }

    private void managerRefusesUpdate(AppointmentUpdateException.Reason reason) {
        when(schedule.updateAppointment(eq(info), eq(61), any(), any()))
                .thenThrow(new AppointmentUpdateException(reason));
    }

    @Test
    void shouldRefuseUpdateWithConflict_whenFormVersionIsStale() throws Exception {
        request.setParameter(AppointmentEditConflict.PARAMETER, RENDERED_VERSION);
        managerRefusesUpdate(AppointmentUpdateException.Reason.STALE_VERSION);

        String result = new AppointmentUpdateRecord2Action().execute();

        assertThat(result).isEqualTo(AppointmentEditConflict.RESULT);
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(request.getAttribute("success")).isNull();
        assertThat(request.getAttribute("appointmentConflictNo")).isEqualTo("61");
        assertThat(request.getAttribute("appointmentConflictProviderNo")).isEqualTo("999998");
        // Nothing follows a refused save: no MC number, no status event, no direct DAO write.
        otherIds.verifyNoInteractions();
        verifyNoInteractions(events);
        verify(appointments, never()).merge(any());
    }

    @Test
    void shouldEndAsSavedWithoutWriting_whenStaleFormAlreadyMatchesTheStoredAppointment() throws Exception {
        // A second click on a form whose first submission was saved: the row now holds exactly
        // these fields under a newer version.
        request.setParameter(AppointmentEditConflict.PARAMETER, RENDERED_VERSION);
        request.setParameter("printReceipt", "1");
        existing.setAppointmentDate(java.sql.Date.valueOf("2027-10-21"));
        existing.setStartTime(java.sql.Time.valueOf("09:00:00"));
        existing.setEndTime(java.sql.Time.valueOf("09:14:00"));
        existing.setReason("edited by a stale form");
        existing.setNotes("");
        existing.setResources("");
        existing.setUrgency("");
        existing.setUpdateDateTime(new Date(Long.parseLong(RENDERED_VERSION) + 1000));
        managerRefusesUpdate(AppointmentUpdateException.Reason.STALE_VERSION);

        String result = new AppointmentUpdateRecord2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(request.getAttribute("success")).isEqualTo(true);
        assertThat(request.getAttribute("printReceipt")).isEqualTo(true);
        // The first submission already stored the MC number and published the status change.
        otherIds.verifyNoInteractions();
        verifyNoInteractions(events);
        verify(appointments, never()).merge(any());
    }

    @Test
    void shouldEndAsSaved_whenStaleCancelFindsTheAppointmentAlreadyCancelled() throws Exception {
        request.setParameter(AppointmentEditConflict.PARAMETER, RENDERED_VERSION);
        request.setParameter("buttoncancel", "Cancel Appt");
        existing.setStatus("C");
        managerRefusesUpdate(AppointmentUpdateException.Reason.STALE_VERSION);

        String result = new AppointmentUpdateRecord2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        verifyNoInteractions(events);
    }

    @Test
    void shouldPassRenderedVersionToLockedUpdate_whenFormCarriesOne() throws Exception {
        request.setParameter(AppointmentEditConflict.PARAMETER, RENDERED_VERSION);
        when(schedule.updateAppointment(eq(info), eq(61), eq(RENDERED_VERSION), any())).thenAnswer(call -> {
            Consumer<Appointment> edit = call.getArgument(3);
            edit.accept(existing);
            return existing;
        });

        String result = new AppointmentUpdateRecord2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(existing.getReason()).isEqualTo("edited by a stale form");
        assertThat(request.getAttribute("success")).isEqualTo(true);
        verify(schedule).updateAppointment(eq(info), eq(61), eq(RENDERED_VERSION), any());
    }

    @Test
    void shouldKeepUnversionedBehaviour_whenFormCarriesNoVersion() throws Exception {
        when(schedule.updateAppointment(eq(info), eq(61), isNull(), any())).thenReturn(existing);

        String result = new AppointmentUpdateRecord2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        verify(schedule).updateAppointment(eq(info), eq(61), isNull(), any());
    }

    @Test
    void shouldRefuseStatusOnlyButtons_whenFormVersionIsStale() throws Exception {
        request.setParameter(AppointmentEditConflict.PARAMETER, RENDERED_VERSION);
        request.setParameter("buttoncancel", "No Show");
        managerRefusesUpdate(AppointmentUpdateException.Reason.STALE_VERSION);

        String result = new AppointmentUpdateRecord2Action().execute();

        assertThat(result).isEqualTo(AppointmentEditConflict.RESULT);
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(existing.getStatus()).isEqualTo("t");
        verifyNoInteractions(events);
    }

    @Test
    void shouldAnswerNotFound_whenAppointmentIsDeletedBeforeTheLock() throws Exception {
        request.setParameter(AppointmentEditConflict.PARAMETER, RENDERED_VERSION);
        managerRefusesUpdate(AppointmentUpdateException.Reason.NOT_FOUND);

        String result = new AppointmentUpdateRecord2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(404);
        otherIds.verifyNoInteractions();
        verifyNoInteractions(events);
    }

    @Test
    void shouldReportPatientDenialAsSecurityException_whenManagerForbidsThePatient() {
        when(schedule.updateAppointment(eq(info), eq(61), any(), any()))
                .thenThrow(new jakarta.ws.rs.ForbiddenException("Patient record access denied"));

        assertThatThrownBy(() -> new AppointmentUpdateRecord2Action().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_appointment)");
    }

    @Test
    void shouldRefuseDeleteWithConflict_whenFormVersionIsStale() throws Exception {
        request.setParameter(AppointmentEditConflict.PARAMETER, RENDERED_VERSION);
        when(schedule.removeAppointment(info, 61, RENDERED_VERSION))
                .thenThrow(new AppointmentUpdateException(AppointmentUpdateException.Reason.STALE_VERSION));

        String result = new AppointmentDeleteRecord2Action().execute();

        assertThat(result).isEqualTo(AppointmentEditConflict.RESULT);
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(request.getAttribute("success")).isNull();
        logActionMock.verify(() -> LogAction.addLogSynchronous(any(LoggedInInfo.class), anyString(), anyString(),
                anyString(), nullable(String.class), nullable(String.class)), never());
    }

    @Test
    void shouldDeleteAndAudit_whenFormVersionIsCurrent() throws Exception {
        request.setParameter(AppointmentEditConflict.PARAMETER, RENDERED_VERSION);
        when(schedule.removeAppointment(info, 61, RENDERED_VERSION)).thenReturn(existing);

        String result = new AppointmentDeleteRecord2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(request.getAttribute("success")).isEqualTo(true);
        // The fixture appointment names no patient, so the row carries no demographic number.
        logActionMock.verify(() -> LogAction.addLogSynchronous(info, LogConst.DELETE, LogConst.CON_APPT, "61", null, null));
    }

    @Test
    void shouldAnswerNotFoundOnDelete_whenAppointmentIsAlreadyGone() throws Exception {
        when(schedule.removeAppointment(info, 61, null))
                .thenThrow(new AppointmentUpdateException(AppointmentUpdateException.Reason.NOT_FOUND));

        String result = new AppointmentDeleteRecord2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(404);
    }

    @Test
    void shouldRefuseCutWithoutCopyingTheForm_whenFormVersionIsStale() throws Exception {
        request.setParameter(AppointmentEditConflict.PARAMETER, RENDERED_VERSION);
        when(schedule.removeAppointment(info, 61, RENDERED_VERSION))
                .thenThrow(new AppointmentUpdateException(AppointmentUpdateException.Reason.STALE_VERSION));

        String result = new AppointmentCutRecord2Action().execute();

        assertThat(result).isEqualTo(AppointmentEditConflict.RESULT);
        assertThat(response.getStatus()).isEqualTo(409);
        // The stale fields must not be left in the session for Paste to book as a new appointment.
        assertThat(ApptUtil.getAppointmentFromSession(request)).isNull();
    }

    @Test
    void shouldCutAndKeepTheFormForPaste_whenFormVersionIsCurrent() throws Exception {
        request.setParameter(AppointmentEditConflict.PARAMETER, RENDERED_VERSION);
        when(schedule.removeAppointment(info, 61, RENDERED_VERSION)).thenReturn(existing);

        String result = new AppointmentCutRecord2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(ApptUtil.getAppointmentFromSession(request)).isNotNull();
        assertThat(ApptUtil.getAppointmentFromSession(request).getReason()).isEqualTo("edited by a stale form");
        logActionMock.verify(() -> LogAction.addLogSynchronous(info, "Appointment.cut", LogConst.CON_APPT, "61", null,
                "id=61"));
    }
}
