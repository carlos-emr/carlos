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
package io.github.carlos_emr.carlos.eform.gate;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ActionContext;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ViewEFormPage2Action Tests")
@Tag("unit")
@Tag("eform")
class ViewEFormPage2ActionTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mocks;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;
    @Mock
    private LoggedInInfo mockLoggedInInfo;
    @Mock
    private HttpServletRequest mockRequest;
    @Mock
    private HttpServletResponse mockResponse;
    @Mock
    private RequestDispatcher mockDispatcher;

    private ViewEFormPage2Action action;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);

        when(mockRequest.getMethod()).thenReturn("GET");
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_eform"), eq("w"), isNull()))
                .thenReturn(true);
        when(mockRequest.getRequestDispatcher("/WEB-INF/jsp/eform/efmformmanager.jsp"))
                .thenReturn(mockDispatcher);

        action = new ViewEFormPage2Action();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (loggedInInfoMock != null) {
            loggedInInfoMock.close();
        }
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
        if (mocks != null) {
            mocks.close();
        }
        ActionContext.clear();
    }

    @Test
    void shouldForwardResolvedView() throws Exception {
        ActionContext.of().withActionName("eform/efmformmanager").bind();

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        verify(mockDispatcher).forward(mockRequest, mockResponse);
        verify(mockSecurityInfoManager)
                .hasPrivilege(any(LoggedInInfo.class), eq("_eform"), eq("w"), isNull());
    }

    @Test
    void shouldReturn404WhenRouteIsUnknown() throws Exception {
        ActionContext.of().withActionName("eform/doesNotExist").bind();

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        verify(mockResponse).sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    @Test
    void shouldReturn405ForDisallowedMethod() throws Exception {
        when(mockRequest.getMethod()).thenReturn("POST");
        ActionContext.of().withActionName("eform/efmformmanager").bind();

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        verify(mockResponse).setHeader("Allow", "GET, HEAD");
        verify(mockResponse).sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }

    @Test
    void shouldForwardGeneratorBridge_whenManagerEditPostsGeneratedHtml() throws Exception {
        when(mockRequest.getMethod()).thenReturn("POST");
        when(mockRequest.getParameter("formHtmlG")).thenReturn("<html></html>");
        when(mockRequest.getRequestDispatcher("/WEB-INF/jsp/eform/efmformmanageredit.jsp"))
                .thenReturn(mockDispatcher);
        ActionContext.of().withActionName("eform/efmformmanageredit").bind();

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        verify(mockDispatcher).forward(mockRequest, mockResponse);
    }

    @Test
    void shouldThrowWhenReadPrivilegeDenied() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_eform"), eq("r"), isNull()))
                .thenReturn(false);
        ActionContext.of().withActionName("eform/efmpatientformlist").bind();

        assertThatThrownBy(() -> action.execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_eform");
    }

    @Test
    void shouldCheckAdminPrivilegeForAdminOnlyView() throws Exception {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin.eform"), eq("w"), isNull()))
                .thenReturn(true);
        when(mockRequest.getRequestDispatcher("/WEB-INF/jsp/eform/eformGenerator.jsp"))
                .thenReturn(mockDispatcher);
        ActionContext.of().withActionName("eform/eformGenerator").bind();

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        verify(mockSecurityInfoManager)
                .hasPrivilege(any(LoggedInInfo.class), eq("_admin.eform"), eq("w"), isNull());
        verify(mockDispatcher).forward(mockRequest, mockResponse);
    }

    @Test
    void shouldThrowWhenAdminPrivilegeDenied() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin.eform"), eq("w"), isNull()))
                .thenReturn(false);
        ActionContext.of().withActionName("eform/eformGenerator").bind();

        assertThatThrownBy(() -> action.execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_admin.eform");
    }

    @Test
    void shouldReturn500WhenActionContextIsNull() throws Exception {
        // No ActionContext bound — getContext() returns null outside Struts request scope
        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        verify(mockResponse).sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
}
