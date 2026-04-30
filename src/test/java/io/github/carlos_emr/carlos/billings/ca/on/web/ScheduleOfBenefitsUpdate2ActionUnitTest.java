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
package io.github.carlos_emr.carlos.billings.ca.on.web;

import io.github.carlos_emr.carlos.billings.ca.on.dto.FeeScheduleApplyResult;
import io.github.carlos_emr.carlos.billings.ca.on.dto.FeeScheduleSelectedChange;
import io.github.carlos_emr.carlos.billings.ca.on.service.FeeScheduleImportService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ScheduleOfBenefitsUpdate2Action}. Pins
 * {@code _admin.billing/w} privilege gate; verifies the fee-schedule import
 * service is not invoked when privilege is denied.
 *
 * @since 2026-04-29
 */
@DisplayName("ScheduleOfBenefitsUpdate2Action")
@Tag("unit")
@Tag("billing")
class ScheduleOfBenefitsUpdate2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mockitoCloseable;

    @Mock private SecurityInfoManager mockSecurityInfoManager;
    @Mock private LoggedInInfo mockLoggedInInfo;
    @Mock private FeeScheduleImportService mockImportService;

    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);
        mockRequest = new MockHttpServletRequest();
        mockResponse = new MockHttpServletResponse();
        mockRequest.setMethod("POST");

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
        if (mockitoCloseable != null) mockitoCloseable.close();
    }

    @Test
    void shouldExecuteAndReturnSuccess_whenPrivilegeGranted() throws Exception {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin.billing"), eq("w"), isNull()))
                .thenReturn(true);
        // No "change" or "feeScheduleChanges" attributes so the action falls
        // into the no-op branch — privilege gate is what we're pinning here.
        ScheduleOfBenefitsUpdate2Action action =
                new ScheduleOfBenefitsUpdate2Action(mockSecurityInfoManager, mockImportService);

        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        verify(mockSecurityInfoManager).hasPrivilege(any(LoggedInInfo.class), eq("_admin.billing"), eq("w"), isNull());
    }

    @Test
    void shouldThrowSecurityException_whenPrivilegeMissing() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin.billing"), eq("w"), isNull()))
                .thenReturn(false);

        ScheduleOfBenefitsUpdate2Action action =
                new ScheduleOfBenefitsUpdate2Action(mockSecurityInfoManager, mockImportService);

        assertThatThrownBy(action::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_admin.billing");
    }

    @Test
    void shouldNotInvokeImportService_whenPrivilegeMissing() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin.billing"), eq("w"), isNull()))
                .thenReturn(false);

        ScheduleOfBenefitsUpdate2Action action =
                new ScheduleOfBenefitsUpdate2Action(mockSecurityInfoManager, mockImportService);

        assertThatThrownBy(action::execute).isInstanceOf(SecurityException.class);
        verify(mockImportService, never()).applyAll(anyList());
        verify(mockImportService, never()).applySelected(anyList());
    }

    @Test
    void shouldApplySelected_whenChangesParamProvidedAndForceUpdateFalse() throws Exception {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin.billing"), eq("w"), isNull()))
                .thenReturn(true);
        FeeScheduleApplyResult result = mock(FeeScheduleApplyResult.class);
        when(result.viewMaps()).thenReturn(List.of());
        when(result.validationErrors()).thenReturn(List.of());
        when(mockImportService.applySelected(anyList())).thenReturn(result);

        // forceUpdate is false (no attribute, no "true" param) and "change"
        // values are provided → apply-selected branch fires.
        mockRequest.setParameter("change", "A007|true|10.00|11.00");

        ScheduleOfBenefitsUpdate2Action action =
                new ScheduleOfBenefitsUpdate2Action(mockSecurityInfoManager, mockImportService);

        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        verify(mockImportService).applySelected(anyList());
        verify(mockImportService, never()).applyAll(anyList());
    }

    @Test
    void shouldReturn405WithAllowHeader_whenGet() throws Exception {
        // RFC 7231 §6.5.5 — applying a fee-schedule change is a state mutation
        // and therefore POST-only. GET must surface 405 with an Allow header
        // so a future regression that silently re-allows GET shows up here.
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin.billing"), eq("w"), isNull()))
                .thenReturn(true);
        mockRequest.setMethod("GET");

        ScheduleOfBenefitsUpdate2Action action =
                new ScheduleOfBenefitsUpdate2Action(mockSecurityInfoManager, mockImportService);

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(jakarta.servlet.http.HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(mockResponse.getHeader("Allow")).isEqualTo("POST");
        verify(mockImportService, never()).applyAll(anyList());
        verify(mockImportService, never()).applySelected(anyList());
    }
}
