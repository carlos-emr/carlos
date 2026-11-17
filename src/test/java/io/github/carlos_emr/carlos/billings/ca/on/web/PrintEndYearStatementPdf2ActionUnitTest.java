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

import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.PatientEndYearStatementSummary;
import io.github.carlos_emr.carlos.billings.ca.on.service.PatientEndYearStatementService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.struts2.ActionContext;
import org.apache.struts2.ServletActionContext;
import java.util.List;
import io.github.carlos_emr.carlos.commn.model.Demographic;
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

import static org.mockito.Mockito.never;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PrintEndYearStatementPdf2Action}.
 *
 * @since 2026-04-27
 */
@DisplayName("PrintEndYearStatementPdf2Action")
@Tag("unit")
@Tag("billing")
class PrintEndYearStatementPdf2ActionUnitTest extends CarlosUnitTestBase {

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

        ActionContext.of().bind();
    }

    @AfterEach
    void tearDown() throws Exception {
        ActionContext.clear();
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
        if (mockitoCloseable != null) mockitoCloseable.close();
    }

    /** The statement page's form, as its Print PDF button posts it. */
    private PrintEndYearStatementPdf2Action formAction(PrintEndYearStatementPdf2Action action, String demographicNo) {
        action.setDemographicNoParam(demographicNo);
        action.setFromDateParam("2025-01-01");
        action.setToDateParam("2025-12-31");
        return action;
    }

    private PatientEndYearStatementSummary statementFor(String demographicNo) throws Exception {
        Demographic patient = new Demographic();
        patient.setDemographicNo(Integer.valueOf(demographicNo));
        PatientEndYearStatementSummary summary = PatientEndYearStatementSummary.builder()
                .patientNo(demographicNo).patientName("FAKE-" + demographicNo).build();
        when(mockService.findUniquePatient(any(), eq(demographicNo), any(), any())).thenReturn(patient);
        when(mockService.aggregateInvoices(eq(patient), any(), any()))
                .thenReturn(new PatientEndYearStatementService.Result(summary, List.of()));
        return summary;
    }

    @Test
    void shouldStreamThisFormsPatientsStatement_whenAnotherWindowSearchedAnotherPatient() throws Exception {
        PatientEndYearStatementSummary thisPatient = statementFor("101");
        // What the old code printed: the statement another window had searched last.
        mockRequest.getSession(true).setAttribute("summary", statementFor("202"));

        PrintEndYearStatementPdf2Action action = formAction(
                new PrintEndYearStatementPdf2Action(mockSecurityInfoManager, mockService), "101");
        // NONE terminates direct-response processing after the binary write
        assertThat(action.execute()).isEqualTo("none");

        verify(mockService, times(1)).writePdfResponse(any(), any(), eq(thisPatient), eq("2025-01-01"), eq("2025-12-31"));
    }

    @Test
    void shouldPrintTheStatementOnScreen_whenTheFieldsWereChangedSinceItWasCreated() throws Exception {
        PatientEndYearStatementSummary onScreen = statementFor("101");
        statementFor("202");
        PrintEndYearStatementPdf2Action action = new PrintEndYearStatementPdf2Action(mockSecurityInfoManager, mockService);
        // The statement Create Statement showed...
        action.setStatementDemographicNo("101");
        action.setStatementFromDate("2025-01-01");
        action.setStatementToDate("2025-12-31");
        // ...and the fields above it, changed since without creating it again.
        action.setDemographicNoParam("202");
        action.setFromDateParam("2024-01-01");
        action.setToDateParam("2024-12-31");

        assertThat(action.execute()).isEqualTo("none");

        verify(mockService).findUniquePatient(any(), eq("101"), isNull(), isNull());
        verify(mockService, times(1)).writePdfResponse(any(), any(), eq(onScreen), eq("2025-01-01"), eq("2025-12-31"));
    }

    @Test
    void shouldSendBadRequest_whenTheShownStatementNamesNoChartNumber() throws Exception {
        PrintEndYearStatementPdf2Action action = formAction(
                new PrintEndYearStatementPdf2Action(mockSecurityInfoManager, mockService), "101");
        action.setStatementDemographicNo("101 OR 1=1");

        assertThat(action.execute()).isEqualTo("none");

        assertThat(mockResponse.getStatus()).isEqualTo(400);
        verifyNoInteractions(mockService);
    }

    @Test
    void shouldSendServerError_whenTotallingTheStatementFails() throws Exception {
        Demographic patient = new Demographic();
        patient.setDemographicNo(101);
        when(mockService.findUniquePatient(any(), eq("101"), any(), any())).thenReturn(patient);
        when(mockService.aggregateInvoices(eq(patient), any(), any()))
                .thenThrow(new PatientEndYearStatementService.Failure(
                        PatientEndYearStatementService.Reason.DATABASE_ERROR));
        PrintEndYearStatementPdf2Action action = spy(formAction(
                new PrintEndYearStatementPdf2Action(mockSecurityInfoManager, mockService), "101"));
        doReturn("errors.billing.ca.on.database").when(action).getText(any(String.class));

        assertThat(action.execute()).isEqualTo("none");

        // The server's failure, not a bad request.
        assertThat(mockResponse.getStatus()).isEqualTo(500);
        verify(mockService, never()).writePdfResponse(any(), any(), any(), any(), any());
    }

    @Test
    void shouldSendBadRequest_whenTheFormNamesNoSinglePatient() throws Exception {
        when(mockService.findUniquePatient(any(), any(), any(), any()))
                .thenThrow(new PatientEndYearStatementService.Failure(
                        PatientEndYearStatementService.Reason.PATIENT_NOT_FOUND));
        mockRequest.getSession(true).setAttribute("summary",
                PatientEndYearStatementSummary.builder().patientNo("202").build());
        PrintEndYearStatementPdf2Action action = spy(formAction(
                new PrintEndYearStatementPdf2Action(mockSecurityInfoManager, mockService), ""));
        doReturn("error.billingReport.invalidPatientName").when(action).getText(any(String.class));

        assertThat(action.execute()).isEqualTo("none");
        assertThat(mockResponse.getStatus()).isEqualTo(400);
        verify(mockService, never()).writePdfResponse(any(), any(), any(), any(), any());
    }

    @Test
    void shouldSendBadRequest_whenADateIsMalformed() throws Exception {
        PrintEndYearStatementPdf2Action action = formAction(
                new PrintEndYearStatementPdf2Action(mockSecurityInfoManager, mockService), "101");
        action.setToDateParam("31/12/2025");

        assertThat(action.execute()).isEqualTo("none");
        assertThat(mockResponse.getStatus()).isEqualTo(400);
        verifyNoInteractions(mockService);
    }

    @Test
    void shouldSendServerError_whenWritePdfThrows() throws Exception {
        statementFor("101");
        doThrow(
                new PatientEndYearStatementService.Failure(
                        PatientEndYearStatementService.Reason.IO_ERROR))
                .when(mockService).writePdfResponse(any(), any(), any(), any(), any());

        PrintEndYearStatementPdf2Action action = spy(formAction(
                new PrintEndYearStatementPdf2Action(mockSecurityInfoManager, mockService), "101"));
        doReturn("errors.billing.ca.on.database").when(action).getText(any(String.class));

        mockResponse.setContentType("application/pdf");
        mockResponse.setHeader("Content-Disposition", "attachment");
        assertThat(action.execute()).isEqualTo("none");
        assertThat(mockResponse.getStatus()).isEqualTo(500);
        assertThat(mockResponse.getHeader("Content-Disposition")).isNull();
        assertThat(mockResponse.getContentType()).isNull();
    }

    @Test
    void shouldAvoidAppendingAnErrorPage_whenOutputFailureOccursAfterCommit() throws Exception {
        statementFor("101");
        mockResponse.getOutputStream().write("%PDF-partial".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        mockResponse.flushBuffer();
        doThrow(new PatientEndYearStatementService.Failure(
                PatientEndYearStatementService.Reason.IO_ERROR))
                .when(mockService).writePdfResponse(any(), any(), any(), any(), any());

        var action = formAction(new PrintEndYearStatementPdf2Action(mockSecurityInfoManager, mockService), "101");
        assertThat(action.execute()).isEqualTo("none");
        assertThat(mockResponse.getContentAsString()).isEqualTo("%PDF-partial");
        assertThat(mockResponse.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldThrowSecurityException_whenLackingBillingReadPrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("r"), isNull()))
                .thenReturn(false);

        PrintEndYearStatementPdf2Action action =
                new PrintEndYearStatementPdf2Action(mockSecurityInfoManager, mockService);
        assertThatThrownBy(action::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_billing");
    }
}
