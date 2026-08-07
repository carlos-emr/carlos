/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * Maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.common.gate;

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

@DisplayName("ViewOntarioMDRedirect2Action Unit Tests")
@Tag("unit")
@Tag("gate")
class ViewOntarioMDRedirect2ActionTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    @Mock private SecurityInfoManager mockSecurityInfoManager;
    @Mock private LoggedInInfo mockLoggedInInfo;
    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;
    private ViewOntarioMDRedirect2Action action;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
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
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin"), eq("r"), isNull()))
                .thenReturn(true);
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_eChart"), eq("r"), isNull()))
                .thenReturn(true);
        action = new ViewOntarioMDRedirect2Action();
    }

    @AfterEach
    void tearDown() {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
    }

    @Test
    @DisplayName("should return SUCCESS when both _admin r and _eChart r granted")
    void shouldReturnSuccess_whenBothPrivsGranted() throws Exception {
        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
    }

    @Test
    @DisplayName("should throw when session is missing")
    void shouldThrow_whenSessionMissing() {
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(null);
        assertThatThrownBy(() -> action.execute()).isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("should throw when _admin r denied")
    void shouldThrow_whenAdminDenied() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin"), eq("r"), isNull()))
                .thenReturn(false);
        assertThatThrownBy(() -> action.execute()).isInstanceOf(SecurityException.class)
                .hasMessageContaining("_admin");
    }

    @Test
    @DisplayName("should throw when _eChart r denied")
    void shouldThrow_whenEchartDenied() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_eChart"), eq("r"), isNull()))
                .thenReturn(false);
        assertThatThrownBy(() -> action.execute()).isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("should send 405 on POST")
    void shouldSend405_onPost() throws Exception {
        mockRequest.setMethod("POST");
        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }
}
