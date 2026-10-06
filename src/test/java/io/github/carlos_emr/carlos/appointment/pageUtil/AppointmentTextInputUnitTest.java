/*
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.appointment.pageUtil;

import io.github.carlos_emr.carlos.commn.OtherIdManager;
import io.github.carlos_emr.carlos.commn.dao.AppointmentArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.dao.OtherIdDAO;
import io.github.carlos_emr.carlos.commn.dao.WaitingListDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.event.EventService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AppointmentTextInputUnitTest extends CarlosUnitTestBase {
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> login;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private OscarAppointmentDao appointments;
    private AppointmentArchiveDao archives;
    private EventService events;
    private boolean transactionCommitted;

    @BeforeEach
    void setUp() {
        transactionCommitted = false;
        request = new MockHttpServletRequest("POST", "/appointment/UpdateRecord");
        response = new MockHttpServletResponse();
        request.getSession().setAttribute("user", "7");
        request.setParameter("appointment_no", "42");
        request.setParameter("appointment_date", "2026-08-01");
        request.setParameter("start_time", "09:00");
        request.setParameter("end_time", "09:15");
        request.setParameter("demographic_no", "");
        request.setParameter("keyword", "Owned booking");
        request.setParameter("status", "t");
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        login = mockStatic(LoggedInInfo.class);
        LoggedInInfo info = mock(LoggedInInfo.class);
        login.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(info);
        SecurityInfoManager security = createAndRegisterMock(SecurityInfoManager.class);
        when(security.hasPrivilege(info, "_appointment", "w", null)).thenReturn(true);
        appointments = createAndRegisterMock(OscarAppointmentDao.class);
        archives = createAndRegisterMock(AppointmentArchiveDao.class);
        events = createAndRegisterMock(EventService.class);
        createAndRegisterMock(WaitingListDao.class);
        createAndRegisterMock(OtherIdDAO.class);
        registerMock(PlatformTransactionManager.class, new AbstractPlatformTransactionManager() {
            @Override protected Object doGetTransaction() { return new Object(); }
            @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
            @Override protected void doCommit(DefaultTransactionStatus status) { transactionCommitted = true; }
            @Override protected void doRollback(DefaultTransactionStatus status) { }
        });
    }

    @AfterEach
    void tearDown() {
        login.close();
        servlet.close();
    }

    @ParameterizedTest
    @CsvSource({"add,keyword,51", "add,reason,81", "add,notes,256", "add,resources,256",
            "edit,keyword,51", "edit,reason,81", "edit,notes,256", "edit,resources,256"})
    void oversizedTextIsRefusedBeforeAnyAppointmentArchiveOrEventWrite(String mode, String field, int length) throws Exception {
        request.setParameter(field, "\uD83D\uDE00".repeat(length));
        String result = "add".equals(mode) ? new AppointmentAddRecord2Action().execute()
                : new AppointmentUpdateRecord2Action().execute();
        assertThat(result).isEqualTo("input");
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(request.getAttribute("appointmentValidationErrors").toString()).contains("maximum length");
        verifyNoInteractions(appointments, archives, events);
        logActionMock.verifyNoInteractions();
    }

    @Test
    void fullLengthBrowserLineBreaksAreNormalizedBeforeValidationAndPersistence() throws Exception {
        String reason = "R".repeat(78) + "\r\nZ";
        String notes = "N".repeat(253) + "\r\nZ";
        request.setParameter("reason", reason);
        request.setParameter("notes", notes);
        request.setParameter("resources", "\uD83D\uDE00".repeat(255));
        Appointment stored = new Appointment();
        stored.setId(42); stored.setStatus("t");
        when(appointments.findForUpdate(42)).thenReturn(stored);
        request.setParameter(AppointmentEditVersion.PARAMETER, AppointmentEditVersion.of(stored, null));
        try (MockedStatic<OtherIdManager> ids = mockStatic(OtherIdManager.class)) {
            assertThat(new AppointmentUpdateRecord2Action().execute()).isEqualTo("success");
            verify(appointments).merge(stored);
            assertThat(stored.getReason()).isEqualTo("R".repeat(78) + "\nZ");
            assertThat(stored.getNotes()).isEqualTo("N".repeat(253) + "\nZ");
            assertThat(stored.getResources()).isEqualTo("\uD83D\uDE00".repeat(255));
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"status", "reason", "metadata", "missing", "malformed", "provider", "cancel", "noShow"})
    void staleOrMissingOriginalStateRetainsTheDraftWithoutWrites(String change) throws Exception {
        Appointment stored = new Appointment();
        stored.setId(42); stored.setStatus("t"); stored.setReason("Original reason");
        request.setParameter("reason", "Typed pending reason");
        request.setParameter(AppointmentEditVersion.PARAMETER, AppointmentEditVersion.of(stored, null));
        if ("status".equals(change)) stored.setStatus("C");
        if ("reason".equals(change)) stored.setReason("A concurrent reason");
        if ("provider".equals(change)) stored.setProviderNo("8");
        if ("cancel".equals(change) || "noShow".equals(change)) {
            stored.setStatus("C");
            request.setParameter("buttoncancel", "cancel".equals(change) ? "Cancel Appt" : "No Show");
        }
        if ("missing".equals(change)) request.removeParameter(AppointmentEditVersion.PARAMETER);
        if ("malformed".equals(change)) request.setParameter(AppointmentEditVersion.PARAMETER, "invalid");
        when(appointments.findForUpdate(42)).thenReturn(stored);
        try (MockedStatic<OtherIdManager> ids = mockStatic(OtherIdManager.class)) {
            if ("metadata".equals(change)) ids.when(() -> OtherIdManager.getApptOtherId("42", "appt_mc_number")).thenReturn("new metadata");
            assertThat(new AppointmentUpdateRecord2Action().execute()).isEqualTo("input");
            assertThat(response.getStatus()).isEqualTo(409);
            assertThat(request.getParameter("reason")).isEqualTo("Typed pending reason");
            assertThat(request.getAttribute("appointmentValidationErrors").toString()).contains("nothing was saved");
            assertThat(request.getAttribute("appointmentReviewRequired")).isEqualTo(true);
            verifyNoInteractions(archives, events);
            verify(appointments, never()).merge(any());
            ids.verify(() -> OtherIdManager.saveIdAppointment(anyString(), anyString(), any()), never());
        }
    }

    @Test
    void aDeletedAppointmentRetainsTheDraftAndIsNeverRecreated() throws Exception {
        request.setParameter("reason", "Typed pending reason");
        assertThat(new AppointmentUpdateRecord2Action().execute()).isEqualTo("input");
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(request.getParameter("reason")).isEqualTo("Typed pending reason");
        assertThat(request.getAttribute("appointmentReviewRequired")).isEqualTo(false);
        verifyNoInteractions(archives, events);
        verify(appointments, never()).merge(any());
    }

    @ParameterizedTest
    @CsvSource({"Cancel Appt,C", "No Show,N"})
    void aCurrentStatusOnlyActionPreservesTextAndPublishesAfterCommit(String button, String status) throws Exception {
        Appointment stored = new Appointment();
        stored.setId(42); stored.setStatus("t"); stored.setReason("Original reason"); stored.setNotes("Original notes");
        stored.setUpdateDateTime(new java.util.Date(1000));
        request.setParameter(AppointmentEditVersion.PARAMETER, AppointmentEditVersion.of(stored, null));
        request.setParameter("buttoncancel", button);
        request.setParameter("notes", "x".repeat(256));
        when(appointments.findForUpdate(42)).thenReturn(stored);
        try (MockedStatic<OtherIdManager> ids = mockStatic(OtherIdManager.class)) {
            doAnswer(call -> {
                assertThat(transactionCommitted).isTrue();
                return null;
            }).when(events).appointmentStatusChanged(any(), eq("42"), isNull(), eq(status));
            assertThat(new AppointmentUpdateRecord2Action().execute()).isEqualTo("success");
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(stored.getStatus()).isEqualTo(status);
            assertThat(stored.getReason()).isEqualTo("Original reason");
            assertThat(stored.getNotes()).isEqualTo("Original notes");
            assertThat(stored.getUpdateDateTime().getTime()).isGreaterThan(1000);
            verify(archives).archiveAppointment(stored);
            verify(appointments).merge(stored);
            verify(events).appointmentStatusChanged(any(), eq("42"), isNull(), eq(status));
        }
    }

    @Test
    void nullTextRemainsNullAndExactUnicodeLimitsAreAccepted() {
        AppointmentTextInput absent = AppointmentTextInput.from(request, null);
        assertThat(absent.reason()).isNull();
        assertThat(absent.notes()).isNull();
        assertThat(absent.rejectIfTooLong(request, response)).isFalse();
        AppointmentTextInput exact = new AppointmentTextInput("\uD83D\uDE00".repeat(50),
                "\uD83D\uDE00".repeat(80), "\uD83D\uDE00".repeat(255), "\uD83D\uDE00".repeat(255));
        assertThat(exact.rejectIfTooLong(request, response)).isFalse();
    }
}
