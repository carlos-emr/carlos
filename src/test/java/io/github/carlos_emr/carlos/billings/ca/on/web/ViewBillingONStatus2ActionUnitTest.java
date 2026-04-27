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

import io.github.carlos_emr.carlos.billings.ca.on.data.BillingONStatusViewModel;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingONErrorReportService;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingONLookupService;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingStatusPrep;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ViewBillingONStatus2Action}.
 *
 * @since 2026-04-24
 */
@DisplayName("ViewBillingONStatus2Action")
@Tag("unit")
@Tag("billing")
class ViewBillingONStatus2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mockitoCloseable;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    @Mock
    private LoggedInInfo mockLoggedInInfo;

    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);

        mockRequest = new MockHttpServletRequest();
        mockResponse = new MockHttpServletResponse();
        mockRequest.setMethod("GET");

        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
        registerMock(BillingONLookupService.class, org.mockito.Mockito.mock(BillingONLookupService.class));
        registerMock(BillingStatusPrep.class, org.mockito.Mockito.mock(BillingStatusPrep.class));
        registerMock(BillingONErrorReportService.class, org.mockito.Mockito.mock(BillingONErrorReportService.class));

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);

        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("r"), isNull()))
                .thenReturn(true);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
        if (mockitoCloseable != null) mockitoCloseable.close();
    }

    @Test
    void shouldReturnSuccess_andStashStatusModelOnRequest() throws Exception {
        ViewBillingONStatus2Action action = new ViewBillingONStatus2Action();

        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);

        BillingONStatusViewModel m = action.getStatusModel();
        assertThat(m).isNotNull();
        assertThat(mockRequest.getAttribute("statusModel")).isSameAs(m);
    }

    @Test
    void shouldDefaultDatesToSumDateRange_whenParamsMissing() throws Exception {
        ViewBillingONStatus2Action action = new ViewBillingONStatus2Action();
        action.execute();
        BillingONStatusViewModel m = action.getStatusModel();

        // DateUtils.sumDate("yyyy-MM-dd","-180") and "0" — non-empty strings.
        assertThat(m.getStartDate()).isNotEmpty();
        assertThat(m.getEndDate()).isNotEmpty();
    }

    @Test
    void shouldDefaultStatusType_toCapitalO_whenParamMissing() throws Exception {
        ViewBillingONStatus2Action action = new ViewBillingONStatus2Action();
        action.execute();
        assertThat(action.getStatusModel().getStatusType()).isEqualTo("O");
    }

    @Test
    void shouldClearDemoNo_whenStatusTypeIsUnderscore() throws Exception {
        mockRequest.setParameter("statusType", "_");
        mockRequest.setParameter("demographicNo", "1");

        ViewBillingONStatus2Action action = new ViewBillingONStatus2Action();
        action.execute();

        assertThat(action.getStatusModel().getDemoNo()).isEmpty();
    }

    @Test
    void shouldUseDefaultBillTypes_whenNoBillTypeParamProvided() throws Exception {
        ViewBillingONStatus2Action action = new ViewBillingONStatus2Action();
        action.execute();

        assertThat(action.getStatusModel().getBillTypes())
                .containsExactlyElementsOf(BillingONStatusViewModel.DEFAULT_BILL_TYPES);
        assertThat(action.getStatusModel().isSearch()).isFalse();
    }

    @Test
    void shouldSetSearch_andUseProvidedBillTypes_whenBillTypeParamProvided() throws Exception {
        mockRequest.setParameter("billType", new String[]{"HCP", "PAT"});

        ViewBillingONStatus2Action action = new ViewBillingONStatus2Action();
        action.execute();

        assertThat(action.getStatusModel().getBillTypes()).containsExactly("HCP", "PAT");
        assertThat(action.getStatusModel().isSearch()).isTrue();
    }

    @Test
    void shouldDefaultServiceCode_toPercent_whenParamMissingOrEmpty() throws Exception {
        // missing
        ViewBillingONStatus2Action action = new ViewBillingONStatus2Action();
        action.execute();
        assertThat(action.getStatusModel().getServiceCode()).isEqualTo("%");

        // empty
        mockRequest.setParameter("serviceCode", "");
        action = new ViewBillingONStatus2Action();
        action.execute();
        assertThat(action.getStatusModel().getServiceCode()).isEqualTo("%");
    }

    @Test
    void shouldThrowSecurityException_whenLacksBillingRead() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("r"), isNull()))
                .thenReturn(false);

        ViewBillingONStatus2Action action = new ViewBillingONStatus2Action();
        assertThatThrownBy(action::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_billing");
    }

    /**
     * Reject sessionless requests up front rather than letting the call
     * to {@code SecurityInfoManager.hasPrivilege(null, ...)} dereference
     * null inside the manager (which emits an internal ERROR log,
     * polluting the privilege-denial signal). Regression armor for the
     * null-loggedInInfo guard in the action.
     */
    @Test
    void shouldThrowSecurityException_whenSessionMissing() {
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(null);

        ViewBillingONStatus2Action action = new ViewBillingONStatus2Action();
        assertThatThrownBy(action::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("session");
    }

    @Test
    void shouldHonorParamEchoes_forIdentityFields() throws Exception {
        mockRequest.setParameter("providerview", "999998");
        mockRequest.setParameter("provider_ohipNo", "OHIP1");
        mockRequest.setParameter("demographicNo", "23");
        mockRequest.setParameter("dx", "401");
        mockRequest.setParameter("billing_form", "GP");
        mockRequest.setParameter("xml_location", "1234");

        ViewBillingONStatus2Action action = new ViewBillingONStatus2Action();
        action.execute();
        BillingONStatusViewModel m = action.getStatusModel();

        assertThat(m.getProviderNo()).isEqualTo("999998");
        assertThat(m.getProviderOhipNo()).isEqualTo("OHIP1");
        assertThat(m.getDemoNo()).isEqualTo("23");
        assertThat(m.getDx()).isEqualTo("401");
        assertThat(m.getBillingForm()).isEqualTo("GP");
        assertThat(m.getVisitLocation()).isEqualTo("1234");
    }
}
