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
package io.github.carlos_emr.carlos.billings.ca.on.pageUtil;

import io.github.carlos_emr.carlos.billings.ca.on.data.BillingONFormViewModel;
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
 * Unit tests for {@link BillingONView2Action}.
 *
 * @since 2026-04-24
 */
@DisplayName("BillingONView2Action")
@Tag("unit")
@Tag("billing")
class BillingONView2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    @Mock
    private LoggedInInfo mockLoggedInInfo;

    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        mockRequest = new MockHttpServletRequest();
        mockResponse = new MockHttpServletResponse();
        mockRequest.setMethod("GET");

        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);

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
    void tearDown() {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
    }

    @Test
    void shouldReturnSuccess_whenAuthorizedGetRequest() throws Exception {
        BillingONView2Action action = new BillingONView2Action();

        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(action.getModel()).isNotNull();
    }

    @Test
    void shouldReturnSuccess_whenAuthorizedPostRequest() throws Exception {
        mockRequest.setMethod("POST");

        BillingONView2Action action = new BillingONView2Action();

        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
    }

    @Test
    void shouldReturnSuccess_whenAuthorizedHeadRequest() throws Exception {
        mockRequest.setMethod("HEAD");

        BillingONView2Action action = new BillingONView2Action();

        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
    }

    @Test
    void shouldSendMethodNotAllowedAndReturnNone_whenDeleteRequest() throws Exception {
        mockRequest.setMethod("DELETE");

        BillingONView2Action action = new BillingONView2Action();

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(mockResponse.getHeader("Allow")).isEqualTo("GET, HEAD, POST");
    }

    @Test
    void shouldThrowSecurityException_whenSessionMissing() {
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(null);

        BillingONView2Action action = new BillingONView2Action();

        assertThatThrownBy(action::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_billing");
    }

    @Test
    void shouldThrowSecurityException_whenLacksBillingReadPrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("r"), isNull()))
                .thenReturn(false);

        BillingONView2Action action = new BillingONView2Action();

        assertThatThrownBy(action::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_billing");
    }

    @Test
    void shouldPopulateModel_fromRequestParameters() throws Exception {
        mockRequest.setParameter("demographic_no", "12345");
        mockRequest.setParameter("appointment_no", "999");
        mockRequest.setParameter("apptProvider_no", "provider7");
        mockRequest.setParameter("providerview", "provider7|1234567");
        mockRequest.setParameter("appointment_date", "2026-04-24");

        BillingONView2Action action = new BillingONView2Action();
        action.execute();

        BillingONFormViewModel model = action.getModel();
        assertThat(model.getDemographicNo()).isEqualTo("12345");
        assertThat(model.getAppointmentNo()).isEqualTo("999");
        assertThat(model.getApptProviderNo()).isEqualTo("provider7");
        assertThat(model.getProviderView()).isEqualTo("provider7|1234567");
        assertThat(model.getBillReferenceDate()).isEqualTo("2026-04-24");
    }

    @Test
    void shouldExposeModelAsRequestAttribute() throws Exception {
        BillingONView2Action action = new BillingONView2Action();
        action.execute();

        Object attr = mockRequest.getAttribute("model");
        assertThat(attr).isInstanceOf(BillingONFormViewModel.class);
        assertThat(attr).isSameAs(action.getModel());
    }
}
