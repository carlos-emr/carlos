/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.appointment.pageUtil;

import java.util.concurrent.atomic.AtomicInteger;

import io.github.carlos_emr.carlos.commn.OtherIdManager;
import io.github.carlos_emr.carlos.commn.dao.OtherIdDAO;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.dao.WaitingListDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.event.EventService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.FormSubmissionGuard;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.waitinglist.WaitingList;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the one-booking-per-rendering guard of {@link AppointmentAddRecord2Action}
 * (issue #4225): a repeated submission of one Add Appointment form books once and ends on the
 * first booking's result page, while a new rendering, a retry after a rolled-back booking and a
 * post without a token still book.
 *
 * @since 2026-10-15
 */
@Tag("unit")
@Tag("appointment")
@DisplayName("AppointmentAddRecord2Action repeated submissions")
class AppointmentAddRecord2ActionSubmissionUnitTest extends CarlosUnitTestBase {

    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private final OscarAppointmentDao appointments = mock(OscarAppointmentDao.class);
    private final EventService events = mock(EventService.class);
    private final LoggedInInfo info = mock(LoggedInInfo.class);
    private final RecordingTransactionManager transactions = new RecordingTransactionManager();
    private final MockHttpSession session = new MockHttpSession();
    private final AtomicInteger nextId = new AtomicInteger(500);

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> sessions;
    private MockedStatic<OtherIdManager> otherIds;
    private MockedStatic<WaitingList> waitingLists;

    /** A no-op transaction manager that records how each transaction ended. */
    static final class RecordingTransactionManager extends AbstractPlatformTransactionManager {
        int commits;
        int rollbacks;

        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) { commits++; }
        @Override protected void doRollback(DefaultTransactionStatus status) { rollbacks++; }
    }

    @BeforeEach
    void setUpAction() {
        registerMock(SecurityInfoManager.class, security);
        registerMock(OscarAppointmentDao.class, appointments);
        registerMock(WaitingListDao.class, mock(WaitingListDao.class));
        registerMock(EventService.class, events);
        registerMock(PlatformTransactionManager.class, transactions);

        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenAnswer(inv -> request);
        servlet.when(ServletActionContext::getResponse).thenAnswer(inv -> response);
        sessions = mockStatic(LoggedInInfo.class);
        sessions.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class))).thenReturn(info);
        // OtherIdManager resolves its DAO when the class is first loaded.
        registerMock(OtherIdDAO.class, mock(OtherIdDAO.class));
        otherIds = mockStatic(OtherIdManager.class);
        WaitingList noWaitingList = mock(WaitingList.class);
        when(noWaitingList.getFound()).thenReturn(false);
        waitingLists = mockStatic(WaitingList.class);
        waitingLists.when(WaitingList::getInstance).thenReturn(noWaitingList);

        when(info.getLoggedInProviderNo()).thenReturn("999998");
        when(security.hasPrivilege(any(LoggedInInfo.class), eq("_appointment"), eq("w"), isNull())).thenReturn(true);
        doAnswer(inv -> {
            inv.<Appointment>getArgument(0).setId(nextId.incrementAndGet());
            return null;
        }).when(appointments).persist(any(Appointment.class));
    }

    @AfterEach
    void tearDownAction() {
        for (MockedStatic<?> mocked : new MockedStatic<?>[]{waitingLists, otherIds, sessions, servlet}) {
            if (mocked != null) mocked.close();
        }
    }

    /** One POST of the Add Appointment form, as the browser sends it. */
    private String submit(String token) throws Exception {
        request = new MockHttpServletRequest();
        request.setSession(session);
        request.setMethod("POST");
        request.setParameter("provider_no", "999998");
        request.setParameter("appointment_date", "2027-11-20");
        request.setParameter("start_time", "09:00");
        request.setParameter("end_time", "09:14");
        request.setParameter("keyword", "FAKE-PATIENT,Check");
        request.setParameter("reason", "FAKE-REASON double submit");
        request.setParameter("status", "t");
        if (token != null) {
            request.setParameter(FormSubmissionGuard.PARAMETER, token);
        }
        response = new MockHttpServletResponse();
        return new AppointmentAddRecord2Action().execute();
    }

    private String rendering() {
        return FormSubmissionGuard.issue(session, FormSubmissionGuard.Scope.APPOINTMENT_ADD, null);
    }

    @Test
    void shouldBookOnce_whenOneRenderingIsSubmittedTwice() throws Exception {
        String token = rendering();

        String first = submit(token);
        Object firstId = request.getAttribute("apptId");
        String repeat = submit(token);

        assertThat(first).isEqualTo(ActionSupport.SUCCESS);
        assertThat(repeat).isEqualTo(ActionSupport.SUCCESS);
        assertThat(request.getAttribute("success")).isEqualTo(true);
        assertThat(request.getAttribute("apptId")).isEqualTo(firstId).isEqualTo(501);
        verify(appointments, times(1)).persist(any(Appointment.class));
        verify(events, times(1)).appointmentCreated(any(), eq("501"), eq("999998"));
    }

    @Test
    void shouldBookAgain_forANewRenderingOfTheForm() throws Exception {
        submit(rendering());
        submit(rendering());

        verify(appointments, times(2)).persist(any(Appointment.class));
    }

    @Test
    void shouldRefuseWithoutBooking_forATokenThisSessionDidNotIssue() throws Exception {
        String foreign = FormSubmissionGuard.issue(new MockHttpSession(), FormSubmissionGuard.Scope.APPOINTMENT_ADD, null);

        String result = submit(foreign);

        assertThat(result).isEqualTo(FormSubmissionGuard.REFUSED_RESULT);
        assertThat(response.getStatus()).isEqualTo(409);
        verify(appointments, never()).persist(any(Appointment.class));
        verify(events, never()).appointmentCreated(any(), anyString(), anyString());
    }

    @Test
    void shouldLeaveTheFormSubmittable_whenTheBookingRolledBack() throws Exception {
        String token = rendering();
        doAnswer(inv -> {
            throw new IllegalStateException("database unavailable");
        }).doAnswer(inv -> {
            inv.<Appointment>getArgument(0).setId(777);
            return null;
        }).when(appointments).persist(any(Appointment.class));

        assertThatThrownBy(() -> submit(token)).isInstanceOf(IllegalStateException.class);
        String retry = submit(token);

        assertThat(retry).isEqualTo(ActionSupport.SUCCESS);
        assertThat(request.getAttribute("apptId")).isEqualTo(777);
        assertThat(transactions.rollbacks).isEqualTo(1);
        assertThat(transactions.commits).isEqualTo(1);
        verify(appointments, times(2)).persist(any(Appointment.class));
    }

    @Test
    void shouldBookAsBefore_whenThePostCarriesNoToken() throws Exception {
        submit(null);
        submit(null);

        verify(appointments, times(2)).persist(any(Appointment.class));
    }
}
