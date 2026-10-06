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

    @BeforeEach
    void setUp() {
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
        when(appointments.find(42)).thenReturn(stored);
        try (MockedStatic<OtherIdManager> ids = mockStatic(OtherIdManager.class)) {
            assertThat(new AppointmentUpdateRecord2Action().execute()).isEqualTo("success");
            verify(appointments).merge(stored);
            assertThat(stored.getReason()).isEqualTo("R".repeat(78) + "\nZ");
            assertThat(stored.getNotes()).isEqualTo("N".repeat(253) + "\nZ");
            assertThat(stored.getResources()).isEqualTo("\uD83D\uDE00".repeat(255));
            assertThat(response.getStatus()).isEqualTo(200);
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
