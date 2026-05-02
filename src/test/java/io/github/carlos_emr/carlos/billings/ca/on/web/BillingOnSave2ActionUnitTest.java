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

import java.util.ArrayList;

import io.github.carlos_emr.carlos.billings.ca.on.service.BillingCorrectionRecordService;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingClaimSubmissionService;
import io.github.carlos_emr.carlos.commn.dao.BillingDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Unit coverage for {@code BillingOnSave2Action} save guards, validation, and result handling. */
@DisplayName("BillingOnSave2Action")
@Tag("unit")
@Tag("billing")
class BillingOnSave2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mockitoCloseable;

    @Mock private SecurityInfoManager mockSecurityInfoManager;
    @Mock private UserPropertyDAO mockUserPropertyDao;
    @Mock private BillingDao mockBillingDao;
    @Mock private BillingClaimSubmissionService mockSaveService;
    @Mock private BillingCorrectionRecordService mockCorrectionRecordService;
    @Mock private LoggedInInfo mockLoggedInInfo;

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

        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("w"), isNull()))
                .thenReturn(true);
        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
        if (mockitoCloseable != null) mockitoCloseable.close();
    }

    private BillingOnSave2Action newAction() {
        return new BillingOnSave2Action(
                mockSecurityInfoManager,
                mockUserPropertyDao,
                mockBillingDao,
                mockSaveService,
                mockCorrectionRecordService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD"})
    void shouldRejectNonPostMethods_beforeSaving(String method) {
        mockRequest.setMethod(method);
        mockRequest.setParameter("submit", "Save");

        String result = newAction().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(mockResponse.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(mockSaveService, mockUserPropertyDao,
                mockBillingDao, mockCorrectionRecordService);
    }

    @Test
    void shouldDelegateToSaveBillingWithExtAndPayee_onPrivateBillingPath() {
        mockRequest.setParameter("appointment_no", "");
        mockRequest.setParameter("url_back", "");
        mockRequest.setParameter("submit", "Save");
        mockRequest.setParameter("xml_billtype", "PAT");
        mockRequest.setParameter("payeename", "Acme Payee");
        mockRequest.setParameter("billNo_old", "");
        mockRequest.setParameter("billStatus_old", "O");
        mockRequest.setParameter("curBillForm", "ON");

        BillingClaimSubmissionService.BillingClaimSubmission submission =
                new BillingClaimSubmissionService.BillingClaimSubmission(
                        new io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimHeaderDto(),
                        java.util.List.of());
        when(mockSaveService.getSubmission(mockRequest)).thenReturn(submission);
        when(mockSaveService.saveBillingWithExtAndPayee(any(BillingClaimSubmissionService.BillingClaimSubmission.class),
                eq(mockRequest), eq("PAT"), eq("Acme Payee")))
                .thenReturn(new BillingClaimSubmissionService.SaveResult(true, 4321));
        when(mockUserPropertyDao.getProp("999998", UserProperty.WORKLOAD_MANAGEMENT)).thenReturn(null);

        String result = newAction().execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(mockRequest.getAttribute("billingNo")).isEqualTo(4321);
        // Single transactional call now drives header + items + ext + payee.
        verify(mockSaveService).saveBillingWithExtAndPayee(any(BillingClaimSubmissionService.BillingClaimSubmission.class),
                eq(mockRequest), eq("PAT"), eq("Acme Payee"));
    }

    @Test
    void shouldReturnFailure_whenServiceRollsBack() {
        // Service throws BillingValidationException → action treats save as
        // failed and returns "failure" with billingFailed flag set so the JSP
        // renders the error path. No billingNo set, no exception escapes.
        mockRequest.setParameter("submit", "Save");
        mockRequest.setParameter("xml_billtype", "PAT");
        mockRequest.setParameter("payeename", "Acme Payee");
        mockRequest.setParameter("curBillForm", "ON");

        BillingClaimSubmissionService.BillingClaimSubmission submission =
                new BillingClaimSubmissionService.BillingClaimSubmission(
                        new io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimHeaderDto(),
                        java.util.List.of());
        when(mockSaveService.getSubmission(mockRequest)).thenReturn(submission);
        when(mockSaveService.saveBillingWithExtAndPayee(any(BillingClaimSubmissionService.BillingClaimSubmission.class),
                any(), anyString(), anyString()))
                .thenThrow(new io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException(
                        "Save rejected: third-party ext write failed; transaction rolled back"));

        String result = newAction().execute();

        assertThat(result).isEqualTo("failure");
        assertThat(mockRequest.getAttribute("billingNo")).isNull();
        assertThat(mockRequest.getAttribute("billingFailed")).isEqualTo(Boolean.TRUE);
        // Pin the cause message round-trips to the request so billingONSave.jsp
        // can render it inside the failure banner. Without this assertion a
        // future refactor that drops the setAttribute call would silently
        // turn the failure page back into a generic "billing has failed".
        assertThat(mockRequest.getAttribute("billingFailureReason"))
                .asString()
                .contains("third-party ext write failed");
    }

    // -- Security & validation -----------------------------------------

    @Test
    void shouldThrowSecurityException_whenPrivilegeMissing() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("w"), isNull()))
                .thenReturn(false);
        mockRequest.setParameter("submit", "Save");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> newAction().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_billing");

        // No persistence on a security failure.
        verifyNoInteractions(mockSaveService);
    }

    // -- Open-redirect validation (CWE-601) ----------------------------

    @org.junit.jupiter.params.ParameterizedTest(name = "url_back={0} should be rejected")
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "//evil.com/phish",        // protocol-relative escapes the host
            "https://evil.com",        // absolute URL
            "/path/../../etc/passwd",  // traversal
            "/path\\with\\backslash",  // backslash injection
            "/path\rwith\rcr",         // CR injection
            "/path\nwith\nlf"          // LF injection
    })
    void shouldRejectMaliciousUrlBack_andSetEmptySafeUrlBack(String maliciousUrlBack) {
        mockRequest.setParameter("submit", "Save");
        mockRequest.setParameter("url_back", maliciousUrlBack);
        // Make submit fail-fast so we don't need to stub the whole save path.
        mockRequest.setParameter("submit", "");

        newAction().execute();

        assertThat(mockRequest.getAttribute("safeUrlBack"))
                .as("Malicious url_back must be coerced to empty string to block open-redirect (CWE-601)")
                .isEqualTo("");
    }

    @Test
    void shouldAcceptSafeRelativeUrlBack_forValidInput() {
        mockRequest.setParameter("submit", "");
        mockRequest.setParameter("url_back", "/billing/CA/ON/billingON");

        newAction().execute();

        assertThat(mockRequest.getAttribute("safeUrlBack")).isEqualTo("/billing/CA/ON/billingON");
    }

    // -- Submit-value branch matrix ------------------------------------

    @Test
    void shouldReturnBackToEdit_onBackButton() {
        mockRequest.setParameter("button", "Back to Edit");

        String result = newAction().execute();

        assertThat(result).isEqualTo("backToEdit");
        verifyNoInteractions(mockSaveService);
    }

    @Test
    void shouldReturnSuccess_andSkipSaveWhenSubmitParameterIsUnknown() {
        // Empty/unknown submit value → action renders the form again
        // without saving. Pins the contract that a stray POST without a
        // recognized submit-button value never persists.
        mockRequest.setParameter("submit", "Unknown");

        String result = newAction().execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        verifyNoInteractions(mockSaveService);
    }

    @Test
    void shouldDelegateToSaveBillingWithExtAndPayee_onOhipPath() {
        mockRequest.setParameter("submit", "Save");
        mockRequest.setParameter("xml_billtype", "HCP");  // not 3rd-party
        mockRequest.setParameter("payeename", "");
        mockRequest.setParameter("curBillForm", "ON");

        BillingClaimSubmissionService.BillingClaimSubmission submission =
                new BillingClaimSubmissionService.BillingClaimSubmission(
                        new io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimHeaderDto(),
                        java.util.List.of());
        when(mockSaveService.getSubmission(mockRequest)).thenReturn(submission);
        when(mockSaveService.saveBillingWithExtAndPayee(any(BillingClaimSubmissionService.BillingClaimSubmission.class),
                eq(mockRequest), eq("HCP"), any()))
                .thenReturn(new BillingClaimSubmissionService.SaveResult(true, 4321));
        when(mockUserPropertyDao.getProp("999998", UserProperty.WORKLOAD_MANAGEMENT)).thenReturn(null);

        String result = newAction().execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        // The single service call is responsible for choosing the OHIP-trans
        // vs 3rd-party-ext branch internally now; the action just delegates.
        verify(mockSaveService).saveBillingWithExtAndPayee(any(BillingClaimSubmissionService.BillingClaimSubmission.class),
                eq(mockRequest), eq("HCP"), any());
    }

    @Test
    void shouldReturnPrintInvoice_onSaveAndPrintInvoiceButton() {
        mockRequest.setParameter("submit", "Save & Print Invoice");
        mockRequest.setParameter("xml_billtype", "HCP");
        mockRequest.setParameter("payeename", "");
        mockRequest.setParameter("curBillForm", "ON");

        BillingClaimSubmissionService.BillingClaimSubmission submission =
                new BillingClaimSubmissionService.BillingClaimSubmission(
                        new io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimHeaderDto(),
                        java.util.List.of());
        when(mockSaveService.getSubmission(mockRequest)).thenReturn(submission);
        when(mockSaveService.saveBillingWithExtAndPayee(any(BillingClaimSubmissionService.BillingClaimSubmission.class),
                any(), anyString(), any()))
                .thenReturn(new BillingClaimSubmissionService.SaveResult(true, 4321));
        when(mockUserPropertyDao.getProp("999998", UserProperty.WORKLOAD_MANAGEMENT)).thenReturn(null);

        String result = newAction().execute();

        assertThat(result).isEqualTo("printInvoice");
    }

    @Test
    void shouldReturnAddAnother_onSaveAndAddAnotherBillButton() {
        mockRequest.setParameter("submit", "Save & Add Another Bill");
        mockRequest.setParameter("xml_billtype", "HCP");
        mockRequest.setParameter("payeename", "");
        mockRequest.setParameter("curBillForm", "ON");

        BillingClaimSubmissionService.BillingClaimSubmission submission =
                new BillingClaimSubmissionService.BillingClaimSubmission(
                        new io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimHeaderDto(),
                        java.util.List.of());
        when(mockSaveService.getSubmission(mockRequest)).thenReturn(submission);
        when(mockSaveService.saveBillingWithExtAndPayee(any(BillingClaimSubmissionService.BillingClaimSubmission.class),
                any(), anyString(), any()))
                .thenReturn(new BillingClaimSubmissionService.SaveResult(true, 4321));
        when(mockUserPropertyDao.getProp("999998", UserProperty.WORKLOAD_MANAGEMENT)).thenReturn(null);

        String result = newAction().execute();

        assertThat(result).isEqualTo("addAnother");
        assertThat(mockRequest.getAttribute("addAnotherBill")).isEqualTo(Boolean.TRUE);
    }
}
