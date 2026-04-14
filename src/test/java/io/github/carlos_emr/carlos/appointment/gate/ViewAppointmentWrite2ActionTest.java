/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * Maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.appointment.gate;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("ViewAppointmentWrite2Action Unit Tests")
@Tag("unit")
@Tag("gate")
class ViewAppointmentWrite2ActionTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mocks;
    @Mock private SecurityInfoManager mockSecurityInfoManager;
    @Mock private LoggedInInfo mockLoggedInInfo;
    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;
    private ViewAppointmentWrite2Action action;

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
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_appointment"), eq("w"), isNull()))
                .thenReturn(true);
        action = new ViewAppointmentWrite2Action();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
        if (mocks != null) mocks.close();
    }

    @Test
    void shouldReturnSuccess_onGetWithWritePriv() throws Exception {
        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
    }

    @Test
    void shouldReturnSuccess_onPostWithWritePriv() throws Exception {
        mockRequest.setMethod("POST");
        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
    }

    @Test
    void shouldThrow_whenSessionMissing() {
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(null);
        assertThatThrownBy(() -> action.execute()).isInstanceOf(SecurityException.class)
                .hasMessageContaining("_appointment w");
    }

    @Test
    void shouldThrow_whenWritePrivilegeDenied() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_appointment"), eq("w"), isNull()))
                .thenReturn(false);
        assertThatThrownBy(() -> action.execute()).isInstanceOf(SecurityException.class)
                .hasMessageContaining("_appointment w");
    }

    @Test
    void shouldThrow_whenOnlyReadPrivilegeHeld() {
        // Guards against regression where the privilege string flips from "w" to "r".
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_appointment"), eq("w"), isNull()))
                .thenReturn(false);
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_appointment"), eq("r"), isNull()))
                .thenReturn(true);
        assertThatThrownBy(() -> action.execute()).isInstanceOf(SecurityException.class);
    }

    @Test
    void shouldSend405_onDelete() throws Exception {
        mockRequest.setMethod("DELETE");
        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(mockResponse.getHeader("Allow")).isEqualTo("GET, HEAD, POST");
    }
}
