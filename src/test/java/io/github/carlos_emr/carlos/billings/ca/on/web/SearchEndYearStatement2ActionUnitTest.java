/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.web;

import java.util.Date;
import java.util.List;

import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.PatientEndYearStatementSummary;
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
 * Unit tests for {@link SearchEndYearStatement2Action}.
 *
 * @since 2026-04-27
 */
@DisplayName("SearchEndYearStatement2Action")
@Tag("unit")
@Tag("billing")
class SearchEndYearStatement2ActionUnitTest extends CarlosUnitTestBase {

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
        mockRequest.setMethod("POST");

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);

        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("r"), isNull()))
                .thenReturn(true);

        // ActionSupport.getText() reads from ActionContext.getContext(); bind
        // a minimal one so the failure-path tests can call addActionError(getText(...))
        // without an NPE.
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
    void shouldStashSummaryAndResultAndReturnSuccess_onHappyPath() throws Exception {
        Demographic demo = new Demographic();
        demo.setDemographicNo(1);
        when(mockService.findUniquePatient(any(), any(), any(), any())).thenReturn(demo);

        PatientEndYearStatementSummary summary = PatientEndYearStatementSummary.builder()
                .patientName("1")
                .patientNo("Doe, Jane")
                .hin("9876543225")
                .address("1 Main")
                .phone("555-555-5555")
                .fromDate(new Date())
                .toDate(new Date())
                .fromDateParam("0.00")
                .todateParam("0.00")
                .build();
        PatientEndYearStatementService.Result result = new PatientEndYearStatementService.Result(
                summary, List.of());
        when(mockService.aggregateInvoices(any(Demographic.class), any(), any())).thenReturn(result);

        SearchEndYearStatement2Action action =
                new SearchEndYearStatement2Action(mockSecurityInfoManager, mockService);
        action.setFromDateParam("2025-01-01");
        action.setToDateParam("2025-12-31");

        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(mockRequest.getAttribute("summary")).isSameAs(summary);
        assertThat(mockRequest.getAttribute("result")).isEqualTo(List.of());
        // session state for the PDF action to read.
        assertThat(mockRequest.getSession().getAttribute("summary")).isSameAs(summary);
    }

    @Test
    void shouldReturnFailure_whenPatientNotFound() throws Exception {
        when(mockService.findUniquePatient(any(), any(), any(), any()))
                .thenThrow(new PatientEndYearStatementService.Failure(
                        PatientEndYearStatementService.Reason.PATIENT_NOT_FOUND));

        // ActionSupport.getText() needs a Struts Container that isn't
        // wired in unit context; spy and short-circuit it to the i18n key.
        SearchEndYearStatement2Action action = spy(
                new SearchEndYearStatement2Action(mockSecurityInfoManager, mockService));
        doReturn("error.billingReport.invalidPatientName").when(action).getText(any(String.class));

        assertThat(action.execute()).isEqualTo("failure");
        assertThat(action.getActionErrors()).isNotEmpty();
    }

    @Test
    void shouldReturnFailure_whenAggregateInvoicesThrows() throws Exception {
        Demographic demo = new Demographic();
        when(mockService.findUniquePatient(any(), any(), any(), any())).thenReturn(demo);
        when(mockService.aggregateInvoices(any(Demographic.class), any(), any()))
                .thenThrow(new PatientEndYearStatementService.Failure(
                        PatientEndYearStatementService.Reason.DATABASE_ERROR));

        SearchEndYearStatement2Action action = spy(
                new SearchEndYearStatement2Action(mockSecurityInfoManager, mockService));
        doReturn("errors.billing.ca.on.database").when(action).getText(any(String.class));

        assertThat(action.execute()).isEqualTo("failure");
        assertThat(action.getActionErrors()).isNotEmpty();
    }

    @Test
    void shouldThrowSecurityException_whenLackingBillingReadPrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("r"), isNull()))
                .thenReturn(false);

        SearchEndYearStatement2Action action =
                new SearchEndYearStatement2Action(mockSecurityInfoManager, mockService);
        assertThatThrownBy(action::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_billing");
    }
}
