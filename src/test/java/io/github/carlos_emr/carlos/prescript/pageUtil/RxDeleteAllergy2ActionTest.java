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
package io.github.carlos_emr.carlos.prescript.pageUtil;

import io.github.carlos_emr.carlos.commn.model.Allergy;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.prescript.data.RxPatientData;
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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RxDeleteAllergy2Action} ID parameter validation and deletion flow.
 * Covers missing, blank, and invalid IDs returning bad request, plus successful deletion
 * with audit logging verification when the ID is valid.
 *
 * @since 2026-05-29
 */
@DisplayName("RxDeleteAllergy2Action Unit Tests")
@Tag("unit")
@Tag("rx")
class RxDeleteAllergy2ActionTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mocks;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    @Mock
    private LoggedInInfo mockLoggedInInfo;

    @Mock
    private RxPatientData.Patient mockRxPatient;

    @Mock
    private Allergy mockAllergy;

    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;
    private RxDeleteAllergy2Action action;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        mockRequest = new MockHttpServletRequest();
        mockResponse = new MockHttpServletResponse();

        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_allergy"), eq("u"), isNull()))
                .thenReturn(true);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);

        action = new RxDeleteAllergy2Action();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
        if (loggedInInfoMock != null) {
            loggedInInfoMock.close();
        }
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    @DisplayName("should return bad request when ID parameter is missing")
    void shouldReturn400BadRequest_whenIdParameterIsMissing() throws Exception {
        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        assertThat(mockResponse.getErrorMessage()).isEqualTo("Missing ID parameter");
    }

    @Test
    @DisplayName("should return bad request when ID parameter is blank")
    void shouldReturn400BadRequest_whenIdParameterIsBlank() throws Exception {
        mockRequest.setParameter("ID", " ");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        assertThat(mockResponse.getErrorMessage()).isEqualTo("Missing ID parameter");
    }

    @Test
    @DisplayName("should return bad request when ID parameter is invalid")
    void shouldReturn400BadRequest_whenIdParameterIsInvalid() throws Exception {
        mockRequest.setParameter("ID", "abc");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        assertThat(mockResponse.getErrorMessage()).isEqualTo("Invalid ID parameter");
    }

    @Test
    @DisplayName("should delete allergy when ID parameter is valid")
    void shouldDeleteAllergy_whenIdParameterIsValid() throws Exception {
        mockRequest.setParameter("ID", "42");
        mockRequest.setParameter("demographicNo", "123");
        mockRequest.getSession().setAttribute("Patient", mockRxPatient);
        when(mockRxPatient.getAllergy(42)).thenReturn(mockAllergy);
        when(mockRxPatient.deleteAllergy(42)).thenReturn(true);
        when(mockRxPatient.getDemographicNo()).thenReturn(123);
        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn("provider1");
        when(mockAllergy.getAuditString()).thenReturn("audit");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(mockRequest.getAttribute("demographicNo")).isEqualTo("123");
        verify(mockRxPatient).deleteAllergy(42);
        logActionMock.verify(() -> LogAction.addLog(
                eq("provider1"),
                eq("delete"),
                eq("allergy"),
                eq("42"),
                any(String.class),
                eq("123"),
                eq("audit")));
    }
}
