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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mockStatic;

@DisplayName("ViewDocumentReportRead2Action Tests")
@Tag("unit")
@Tag("documentManager")
class ViewDocumentReportRead2ActionTest extends CarlosUnitTestBase {

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

    private ViewDocumentReportRead2Action action;

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

        action = new ViewDocumentReportRead2Action();
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
    void shouldAllowReport_whenFunctionParameterAbsent() throws Exception {
        when(mockRequest.getParameter("appointmentNo")).thenReturn("0");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        verify(mockResponse, never()).sendError(any(Integer.class), any(String.class));
        verify(mockRequest, never()).setAttribute(eq("normalizedFunction"), any());
    }

    @Test
    void shouldAllowDemographicReport_whenPatientAccessAllowed() throws Exception {
        when(mockRequest.getParameter("function")).thenReturn("demographic");
        when(mockRequest.getParameter("functionid")).thenReturn("123");
        when(mockSecurityInfoManager.isAllowedAccessToPatientRecord(mockLoggedInInfo, 123)).thenReturn(true);

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        verify(mockSecurityInfoManager).isAllowedAccessToPatientRecord(mockLoggedInInfo, 123);
    }

    @Test
    void shouldAllowProviderReport_withLegacyCurUserParameter() throws Exception {
        when(mockRequest.getParameter("function")).thenReturn("providers");
        when(mockRequest.getParameter("functionid")).thenReturn("999998");
        when(mockRequest.getParameter("curUser")).thenReturn("attacker");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        verify(mockSecurityInfoManager, never()).isAllowedAccessToPatientRecord(any(), any());
    }

    @Test
    void shouldRejectReport_whenFunctionInvalid() throws Exception {
        when(mockRequest.getParameter("function")).thenReturn("bad-module");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        verify(mockResponse).sendError(HttpServletResponse.SC_BAD_REQUEST, "invalid function");
        verify(mockRequest, never()).setAttribute(eq("normalizedFunction"), any());
    }

    @Test
    void shouldRejectReport_whenFunctionIdInvalid() throws Exception {
        when(mockRequest.getParameter("function")).thenReturn("demographic");
        when(mockRequest.getParameter("functionid")).thenReturn("123 bad");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        verify(mockResponse).sendError(HttpServletResponse.SC_BAD_REQUEST, "invalid functionid");
    }

    @Test
    void shouldRejectReport_whenAppointmentNoInvalid() throws Exception {
        when(mockRequest.getParameter("function")).thenReturn("provider");
        when(mockRequest.getParameter("functionid")).thenReturn("999998");
        when(mockRequest.getParameter("appointmentNo")).thenReturn("45&bad=true");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        verify(mockResponse).sendError(HttpServletResponse.SC_BAD_REQUEST, "invalid appointmentNo");
    }

    @Test
    void shouldRejectReport_whenAppointmentNoHasNonAsciiDigits() throws Exception {
        // function absent -> routes through the no-function appointmentNo branch; the ASCII-strict
        // pre-scan must reject non-ASCII digits (the old Character.isDigit code did not).
        when(mockRequest.getParameter("appointmentNo")).thenReturn("١٢٣"); // Arabic-Indic 123

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        verify(mockResponse).sendError(HttpServletResponse.SC_BAD_REQUEST, "invalid appointmentNo");
        verify(mockRequest, never()).setAttribute(eq("normalizedFunction"), any());
    }

    @Test
    void shouldForbidDemographicReport_whenPatientAccessDenied() throws Exception {
        when(mockRequest.getParameter("function")).thenReturn("demographic");
        when(mockRequest.getParameter("functionid")).thenReturn("123");
        when(mockSecurityInfoManager.isAllowedAccessToPatientRecord(mockLoggedInInfo, 123)).thenReturn(false);

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        verify(mockResponse).sendError(HttpServletResponse.SC_FORBIDDEN, "unauthorized access to patient record");
        verify(mockRequest, never()).setAttribute(eq("normalizedFunction"), any());
    }

    @Test
    void shouldRejectReport_whenDemographicFunctionIdOverflowsInt() throws Exception {
        when(mockRequest.getParameter("function")).thenReturn("demographic");
        when(mockRequest.getParameter("functionid")).thenReturn("99999999999"); // > Integer.MAX_VALUE

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        verify(mockResponse).sendError(HttpServletResponse.SC_BAD_REQUEST, "invalid functionid");
        verify(mockSecurityInfoManager, never()).isAllowedAccessToPatientRecord(any(), any());
    }

    @Test
    void shouldRejectReport_whenDemographicFunctionIdIsZero() throws Exception {
        when(mockRequest.getParameter("function")).thenReturn("demographic");
        when(mockRequest.getParameter("functionid")).thenReturn("0");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        verify(mockResponse).sendError(HttpServletResponse.SC_BAD_REQUEST, "invalid functionid");
        verify(mockSecurityInfoManager, never()).isAllowedAccessToPatientRecord(any(), any());
    }

    @Test
    void shouldRejectReport_whenDemographicFunctionIdHasNonAsciiDigits() throws Exception {
        when(mockRequest.getParameter("function")).thenReturn("demographic");
        when(mockRequest.getParameter("functionid")).thenReturn("١٢٣"); // Arabic-Indic 123

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        verify(mockResponse).sendError(HttpServletResponse.SC_BAD_REQUEST, "invalid functionid");
        verify(mockSecurityInfoManager, never()).isAllowedAccessToPatientRecord(any(), any());
    }

    @Test
    void shouldEnforcePatientAccess_whenDemographicFunctionMixedCase() throws Exception {
        when(mockRequest.getParameter("function")).thenReturn("Demographic");
        when(mockRequest.getParameter("functionid")).thenReturn("123");
        when(mockSecurityInfoManager.isAllowedAccessToPatientRecord(mockLoggedInInfo, 123)).thenReturn(false);

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        verify(mockSecurityInfoManager).isAllowedAccessToPatientRecord(mockLoggedInInfo, 123);
        verify(mockResponse).sendError(HttpServletResponse.SC_FORBIDDEN, "unauthorized access to patient record");
    }

    @Test
    void shouldForwardNormalizedFunction_whenDemographicReportAllowed() throws Exception {
        when(mockRequest.getParameter("function")).thenReturn("demographic");
        when(mockRequest.getParameter("functionid")).thenReturn("123");
        when(mockSecurityInfoManager.isAllowedAccessToPatientRecord(mockLoggedInInfo, 123)).thenReturn(true);

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        verify(mockRequest).setAttribute("normalizedFunction", "demographic");
    }

    @Test
    void shouldForwardLowercasedNormalizedFunction_whenProviderFunctionMixedCase() throws Exception {
        when(mockRequest.getParameter("function")).thenReturn("Providers");
        when(mockRequest.getParameter("functionid")).thenReturn("999998");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        verify(mockRequest).setAttribute("normalizedFunction", "providers");
        verify(mockSecurityInfoManager, never()).isAllowedAccessToPatientRecord(any(), any());
    }
}
