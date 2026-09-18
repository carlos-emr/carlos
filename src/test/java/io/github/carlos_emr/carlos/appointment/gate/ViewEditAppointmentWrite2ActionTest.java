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
package io.github.carlos_emr.carlos.appointment.gate;

import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for the {@code appointment/editappointment} gate.
 *
 * <p>editappointment.jsp parses {@code appointment_no} and looks the record up unguarded, so a
 * request with no id, a non-numeric one, or one matching no row answered HTTP 500 out of the
 * compiled JSP (#3729). The gate now answers those three cases itself, and must still forward
 * the request the day sheet actually makes.
 */
@DisplayName("ViewEditAppointmentWrite2Action Unit Tests")
@Tag("unit")
@Tag("gate")
class ViewEditAppointmentWrite2ActionTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mocks;
    @Mock private SecurityInfoManager mockSecurityInfoManager;
    @Mock private LoggedInInfo mockLoggedInInfo;
    @Mock private OscarAppointmentDao mockAppointmentDao;
    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;
    private ViewEditAppointmentWrite2Action action;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        mockRequest = new MockHttpServletRequest();
        mockResponse = new MockHttpServletResponse();
        mockRequest.setMethod("GET");
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);
        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);
        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
        registerMock(OscarAppointmentDao.class, mockAppointmentDao);
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_appointment"), eq("w"), isNull()))
                .thenReturn(true);
        action = new ViewEditAppointmentWrite2Action();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
        if (mocks != null) mocks.close();
    }

    @Test
    @DisplayName("should forward when appointment_no resolves to a real appointment")
    void shouldForwardToEditForm_whenAppointmentExists() throws Exception {
        mockRequest.setParameter("appointment_no", "11");
        when(mockAppointmentDao.find(11)).thenReturn(new Appointment());

        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(mockResponse.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("should answer 400 when appointment_no is absent")
    void shouldAnswerBadRequest_whenAppointmentNoAbsent() throws Exception {
        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(400);
        verifyNoInteractions(mockAppointmentDao);
    }

    @Test
    @DisplayName("should answer 400 when appointment_no is not a number")
    void shouldAnswerBadRequest_whenAppointmentNoNonNumeric() throws Exception {
        mockRequest.setParameter("appointment_no", "abc");

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(400);
        verifyNoInteractions(mockAppointmentDao);
    }

    @Test
    @DisplayName("should answer 400 when appointment_no is not positive")
    void shouldAnswerBadRequest_whenAppointmentNoNotPositive() throws Exception {
        mockRequest.setParameter("appointment_no", "0");

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(400);
        verifyNoInteractions(mockAppointmentDao);
    }

    @Test
    @DisplayName("should answer 404 when appointment_no matches no appointment")
    void shouldAnswerNotFound_whenAppointmentMissing() throws Exception {
        mockRequest.setParameter("appointment_no", "999999");
        when(mockAppointmentDao.find(999999)).thenReturn(null);

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(404);
    }

    /**
     * The identifier is PHI-correlating, so it must not be echoed into a browser-visible
     * error message.
     */
    @Test
    @DisplayName("should not echo the appointment identifier in the error message")
    void shouldNotEchoIdentifier_inErrorMessage() throws Exception {
        mockRequest.setParameter("appointment_no", "999999");
        when(mockAppointmentDao.find(999999)).thenReturn(null);

        action.execute();

        assertThat(mockResponse.getErrorMessage()).doesNotContain("999999");
    }

    @Test
    @DisplayName("should reject before validating when _appointment write is denied")
    void shouldRejectRequest_whenAppointmentWriteDenied() {
        mockRequest.setParameter("appointment_no", "11");
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_appointment"), eq("w"), isNull()))
                .thenReturn(false);

        assertThatThrownBy(() -> action.execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_appointment");

        verifyNoInteractions(mockAppointmentDao);
    }
}
