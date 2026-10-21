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
package io.github.carlos_emr.carlos.documentManager.gate;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for the {@code documentManager/ViewDocumentBrowser} gate.
 *
 * <p>{@code documentBrowser.jsp} branches on {@code categorykey} and dereferences it
 * unguarded, so a request without the parameter used to reach the JSP and answer HTTP 500
 * from a {@code NullPointerException} (issue #3730). The gate now rejects that request with
 * 400 before the forward, and must not disturb the requests that do carry the parameter.
 */
@DisplayName("ViewDocumentBrowserRead2Action Tests")
@Tag("unit")
@Tag("documentManager")
class ViewDocumentBrowserRead2ActionUnitTest extends CarlosUnitTestBase {

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

    private ViewDocumentBrowserRead2Action action;

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

        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_edoc"), eq("r"), isNull()))
                .thenReturn(true);

        action = new ViewDocumentBrowserRead2Action();
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
    }

    @Test
    @DisplayName("should answer 400 without forwarding when categorykey is absent")
    void shouldAnswerBadRequest_whenCategoryKeyAbsent() throws Exception {
        when(mockRequest.getParameter("categorykey")).thenReturn(null);

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        verify(mockResponse).sendError(HttpServletResponse.SC_BAD_REQUEST, "missing categorykey");
    }

    @Test
    @DisplayName("should answer 400 when categorykey is blank")
    void shouldAnswerBadRequest_whenCategoryKeyBlank() throws Exception {
        when(mockRequest.getParameter("categorykey")).thenReturn("   ");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        verify(mockResponse).sendError(HttpServletResponse.SC_BAD_REQUEST, "missing categorykey");
    }

    @Test
    @DisplayName("should forward to the browser for the private category")
    void shouldForwardToBrowser_forPrivateCategoryKey() throws Exception {
        when(mockRequest.getParameter("categorykey")).thenReturn("Private Documents");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        verify(mockResponse, never()).sendError(anyInt(), anyString());
    }

    @Test
    @DisplayName("should forward to the browser for the public category")
    void shouldForwardToBrowser_forPublicCategoryKey() throws Exception {
        when(mockRequest.getParameter("categorykey")).thenReturn("Public Documents");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        verify(mockResponse, never()).sendError(anyInt(), anyString());
    }

    /**
     * The JSP renders "Remote documents not supported" for a category it does not recognise.
     * That branch is existing behaviour and the gate deliberately leaves it reachable.
     */
    @Test
    @DisplayName("should still forward an unrecognized category to the JSP")
    void shouldForwardToBrowser_forUnrecognizedCategoryKey() throws Exception {
        when(mockRequest.getParameter("categorykey")).thenReturn("Remote Documents");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        verify(mockResponse, never()).sendError(anyInt(), anyString());
    }

    @Test
    @DisplayName("should reject the request before validating when _edoc read is denied")
    void shouldRejectRequest_whenEdocReadDenied() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_edoc"), eq("r"), isNull()))
                .thenReturn(false);

        assertThatThrownBy(() -> action.execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_edoc");

        verify(mockRequest, never()).getParameter("categorykey");
    }
}
