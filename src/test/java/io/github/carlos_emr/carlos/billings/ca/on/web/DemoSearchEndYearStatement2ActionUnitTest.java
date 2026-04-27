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

import io.github.carlos_emr.carlos.billings.ca.on.data.PatientEndYearStatementBean;
import io.github.carlos_emr.carlos.billings.ca.on.service.PatientEndYearStatementService;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;

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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DemoSearchEndYearStatement2Action}.
 *
 * @since 2026-04-27
 */
@DisplayName("DemoSearchEndYearStatement2Action")
@Tag("unit")
@Tag("billing")
class DemoSearchEndYearStatement2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mockitoCloseable;

    @Mock private SecurityInfoManager mockSecurityInfoManager;
    @Mock private LoggedInInfo mockLoggedInInfo;
    @Mock private PatientEndYearStatementService mockService;

    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);
        mockRequest = new MockHttpServletRequest();
        mockResponse = new MockHttpServletResponse();
        mockRequest.setMethod("GET");

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);

        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("r"), isNull()))
                .thenReturn(true);

        ActionContext.of().bind();
    }

    @AfterEach
    void tearDown() throws Exception {
        ActionContext.clear();
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
        if (mockitoCloseable != null) mockitoCloseable.close();
    }

    @Test
    void shouldBuildIdentityOnlySummary_andReturnSuccess_onHappyPath() throws Exception {
        Demographic demo = new Demographic();
        demo.setDemographicNo(1);
        demo.setChartNo("CHART1");
        demo.setLastName("Doe");
        demo.setFirstName("Jane");
        demo.setHin("9876543225");
        demo.setAddress("1 Main");
        demo.setCity("Hamilton");
        demo.setProvince("ON");
        demo.setPhone("555-555-5555");
        demo.setPhone2("");
        when(mockService.findUniquePatient(any(), any(), any(), any())).thenReturn(demo);
        // Pre-stash a stale summary; the action should clear it before populating fresh.
        mockRequest.getSession(true).setAttribute("summary", "stale");

        DemoSearchEndYearStatement2Action action =
                new DemoSearchEndYearStatement2Action(mockSecurityInfoManager, mockService);
        mockRequest.setParameter("demographic_no", "1");

        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);

        Object summary = mockRequest.getAttribute("summary");
        assertThat(summary).isInstanceOf(PatientEndYearStatementBean.class);
        PatientEndYearStatementBean bean = (PatientEndYearStatementBean) summary;
        assertThat(bean.getPatientNo()).isEqualTo("CHART1");
        assertThat(bean.getHin()).isEqualTo("9876543225");
    }

    @Test
    void shouldReturnFailure_whenPatientNotUnique() throws Exception {
        when(mockService.findUniquePatient(any(), any(), any(), any()))
                .thenThrow(new PatientEndYearStatementService.Failure(
                        PatientEndYearStatementService.Reason.PATIENT_NOT_UNIQUE));

        DemoSearchEndYearStatement2Action action = spy(
                new DemoSearchEndYearStatement2Action(mockSecurityInfoManager, mockService));
        doReturn("error.billingReport.notSelectivePatientName").when(action).getText(any(String.class));

        assertThat(action.execute()).isEqualTo("failure");
        assertThat(action.getActionErrors()).isNotEmpty();
    }

    @Test
    void shouldThrowSecurityException_whenLackingBillingReadPrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("r"), isNull()))
                .thenReturn(false);

        DemoSearchEndYearStatement2Action action =
                new DemoSearchEndYearStatement2Action(mockSecurityInfoManager, mockService);
        assertThatThrownBy(action::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_billing");
    }
}
