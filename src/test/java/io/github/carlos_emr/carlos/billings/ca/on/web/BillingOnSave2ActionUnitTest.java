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
import io.github.carlos_emr.carlos.utility.FormSubmissionGuard;
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
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

    private final RecordingTransactionManager transactions = new RecordingTransactionManager();

    /** A no-op transaction manager that records how each transaction ended. */
    static final class RecordingTransactionManager extends AbstractPlatformTransactionManager {
        int commits;
        int rollbacks;

        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) { commits++; }
        @Override protected void doRollback(DefaultTransactionStatus status) { rollbacks++; }
    }

    private BillingOnSave2Action newAction() {
        return new BillingOnSave2Action(
                mockSecurityInfoManager,
                mockUserPropertyDao,
                mockBillingDao,
                mockSaveService,
                mockCorrectionRecordService,
                transactions);
    }

    /** A second POST of the same review page, in the same session as {@code mockRequest}. */
    private MockHttpServletRequest repeatOf(MockHttpServletRequest first) {
        MockHttpServletRequest repeat = new MockHttpServletRequest();
        repeat.setMethod("POST");
        repeat.setSession(first.getSession());
        repeat.setParameters(first.getParameterMap());
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(repeat);
        mockResponse = new MockHttpServletResponse();
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);
        return repeat;
    }

    /** A rendered review page for patient {@code demographicNo} whose Save carries its submission token. */
    private void reviewFor(String demographicNo) {
        mockRequest.setParameter("demographic_no", demographicNo);
        mockRequest.setParameter(FormSubmissionGuard.PARAMETER, FormSubmissionGuard.issue(mockRequest.getSession(),
                FormSubmissionGuard.Scope.BILLING_ON_SAVE, demographicNo));
        mockRequest.setParameter("billingAction", "SAVE");
        mockRequest.setParameter("xml_billtype", "HCP");
        mockRequest.setParameter("payeename", "");
        mockRequest.setParameter("curBillForm", "ON");
        when(mockSaveService.getSubmission(any(HttpServletRequest.class))).thenReturn(
                new BillingClaimSubmissionService.BillingClaimSubmission(
                        new io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimHeaderDto(), java.util.List.of()));
        when(mockUserPropertyDao.getProp("999998", UserProperty.WORKLOAD_MANAGEMENT)).thenReturn(null);
    }

    // -- One claim per rendered review (issue #4225) ----------------------

    @Test
    @Tag("release-review")
    void shouldCompleteAppointmentUpdateOnRetry_whenFirstSubmissionFailsAfterClaimSave() {
        reviewFor("42");
        mockRequest.setParameter("appointment_no", "880");
        when(mockSaveService.saveBillingWithExtAndPayee(any(BillingClaimSubmissionService.BillingClaimSubmission.class),
                any(), anyString(), any())).thenReturn(new BillingClaimSubmissionService.SaveResult(true, 4321));
        when(mockSaveService.getApptStatus("880")).thenReturn("t");
        when(mockSaveService.updateApptStatus(eq("880"), any(), eq("999998")))
                .thenThrow(new IllegalStateException("Synthetic appointment write failure")).thenReturn(true);

        assertThatThrownBy(() -> newAction().execute()).isInstanceOf(IllegalStateException.class);
        repeatOf(mockRequest);

        assertThat(newAction().execute()).isEqualTo(ActionSupport.SUCCESS);
        // A retry can roll the whole submission back and save again, or resume its remaining
        // steps. It must not return a success page while leaving the appointment unmodified.
        verify(mockSaveService, times(2)).updateApptStatus(eq("880"), any(), eq("999998"));
    }

    @Test
    void shouldSaveOnce_whenOneReviewIsSubmittedTwice() {
        reviewFor("42");
        mockRequest.setParameter("appointment_no", "880");
        when(mockSaveService.saveBillingWithExtAndPayee(any(BillingClaimSubmissionService.BillingClaimSubmission.class),
                any(), anyString(), any())).thenReturn(new BillingClaimSubmissionService.SaveResult(true, 4321));
        when(mockSaveService.getApptStatus("880")).thenReturn("t");
        when(mockSaveService.updateApptStatus(eq("880"), any(), eq("999998"))).thenReturn(true);

        String first = newAction().execute();
        MockHttpServletRequest repeat = repeatOf(mockRequest);
        String second = newAction().execute();

        assertThat(first).isEqualTo(ActionSupport.SUCCESS);
        assertThat(second).isEqualTo(ActionSupport.SUCCESS);
        assertThat(repeat.getAttribute("billingNo")).isEqualTo(4321);
        verify(mockSaveService, times(1)).saveBillingWithExtAndPayee(any(BillingClaimSubmissionService.BillingClaimSubmission.class),
                any(), anyString(), any());
        verify(mockSaveService, times(1)).updateApptStatus(eq("880"), any(), eq("999998"));
        assertThat(transactions.commits).isEqualTo(1);
    }

    @Test
    void shouldRepeatTheFirstSavesRoute_forARepeatedSaveAndPrint() {
        reviewFor("42");
        mockRequest.setParameter("billingAction", "SAVE_PRINT");
        when(mockSaveService.saveBillingWithExtAndPayee(any(BillingClaimSubmissionService.BillingClaimSubmission.class),
                any(), anyString(), any())).thenReturn(new BillingClaimSubmissionService.SaveResult(true, 4321));

        newAction().execute();
        MockHttpServletRequest repeat = repeatOf(mockRequest);
        String second = newAction().execute();

        assertThat(second).isEqualTo("printInvoice");
        assertThat(repeat.getAttribute("billingNo")).isEqualTo(4321);
    }

    @Test
    void shouldLeaveTheReviewSubmittable_whenTheSaveRolledBack() {
        reviewFor("42");
        when(mockSaveService.saveBillingWithExtAndPayee(any(BillingClaimSubmissionService.BillingClaimSubmission.class),
                any(), anyString(), any()))
                .thenThrow(new io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException("payee ext write failed"))
                .thenReturn(new BillingClaimSubmissionService.SaveResult(true, 4321));

        String first = newAction().execute();
        repeatOf(mockRequest);
        String retry = newAction().execute();

        assertThat(first).isEqualTo("failure");
        assertThat(retry).isEqualTo(ActionSupport.SUCCESS);
        assertThat(transactions.rollbacks).isEqualTo(1);
        verify(mockSaveService, times(2)).saveBillingWithExtAndPayee(any(BillingClaimSubmissionService.BillingClaimSubmission.class),
                any(), anyString(), any());
    }

    @Test
    void shouldRollBackAndStaySubmittable_whenNothingWasSaved() {
        reviewFor("42");
        when(mockSaveService.saveBillingWithExtAndPayee(any(BillingClaimSubmissionService.BillingClaimSubmission.class),
                any(), anyString(), any()))
                .thenReturn(new BillingClaimSubmissionService.SaveResult(false, 0))
                .thenReturn(new BillingClaimSubmissionService.SaveResult(true, 4321));

        String first = newAction().execute();
        repeatOf(mockRequest);
        String retry = newAction().execute();

        assertThat(first).isEqualTo("failure");
        assertThat(retry).isEqualTo(ActionSupport.SUCCESS);
        assertThat(transactions.rollbacks).isEqualTo(1);
        assertThat(transactions.commits).isEqualTo(1);
    }

    @Test
    void shouldRefuseWithoutSaving_whenTheTokenWasIssuedForAnotherPatient() {
        reviewFor("42");
        mockRequest.setParameter("demographic_no", "43");

        String result = newAction().execute();

        assertThat(result).isEqualTo(FormSubmissionGuard.REFUSED_RESULT);
        assertThat(mockResponse.getStatus()).isEqualTo(409);
        verify(mockSaveService, never()).saveBillingWithExtAndPayee(any(BillingClaimSubmissionService.BillingClaimSubmission.class),
                any(), anyString(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD"})
    void shouldRejectNonPostMethods_beforeSaving(String method) {
        mockRequest.setMethod(method);
        mockRequest.setParameter("billingAction", "SAVE");

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
        mockRequest.setParameter("billingAction", "SAVE");
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
    @DisplayName("a new bill saves when the form posts its old-bill fields empty, as billingON.jsp does")
    void shouldSaveNewBill_whenOldBillFieldsPostEmpty() {
        mockRequest.setParameter("appointment_no", "");
        mockRequest.setParameter("url_back", "");
        mockRequest.setParameter("billingAction", "SAVE");
        mockRequest.setParameter("xml_billtype", "PAT");
        mockRequest.setParameter("payeename", "Acme Payee");
        mockRequest.setParameter("billNo_old", "");
        mockRequest.setParameter("billStatus_old", "");
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
        assertThat(mockRequest.getAttribute("billingFailureReason")).isNull();
    }

    @Test
    void shouldReturnFailure_whenServiceRollsBack() {
        // Service throws BillingValidationException → action treats save as
        // failed and returns "failure" with billingFailed flag set so the JSP
        // renders the error path. No billingNo set, no exception escapes.
        mockRequest.setParameter("billingAction", "SAVE");
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

    @Test
    void shouldReturnFailure_whenSubmissionBuildFailsValidation() {
        mockRequest.setParameter("billingAction", "SAVE");
        mockRequest.setParameter("xml_billtype", "HCP");
        mockRequest.setParameter("curBillForm", "ON");
        when(mockSaveService.getSubmission(mockRequest))
                .thenThrow(new io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException(
                        "Billing claim submission: malformed xml_provider [999998]"));

        String result = newAction().execute();

        assertThat(result).isEqualTo("failure");
        assertThat(mockRequest.getAttribute("billingFailed")).isEqualTo(Boolean.TRUE);
        assertThat(mockRequest.getAttribute("billingFailureReason"))
                .asString()
                .contains("xml_provider");
        verify(mockSaveService, never()).saveBillingWithExtAndPayee(any(), any(), anyString(), anyString());
    }

    // -- Security & validation -----------------------------------------

    @Test
    void shouldThrowSecurityException_whenPrivilegeMissing() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("w"), isNull()))
                .thenReturn(false);
        mockRequest.setParameter("billingAction", "SAVE");

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
        mockRequest.setParameter("url_back", maliciousUrlBack);
        // Make action validation fail-fast so we don't need to stub the whole save path.
        mockRequest.setParameter("billingAction", "UNKNOWN");

        newAction().execute();

        assertThat(mockRequest.getAttribute("safeUrlBack"))
                .as("Malicious url_back must be coerced to empty string to block open-redirect (CWE-601)")
                .isEqualTo("");
    }

    @Test
    void shouldAcceptSafeRelativeUrlBack_forValidInput() {
        mockRequest.setParameter("billingAction", "UNKNOWN");
        mockRequest.setParameter("url_back", "/billing/CA/ON/billingON");

        newAction().execute();

        assertThat(mockRequest.getAttribute("safeUrlBack")).isEqualTo("/billing/CA/ON/billingON");
    }

    // -- Submit-value branch matrix ------------------------------------

    @Test
    void shouldReturnBackToEdit_whenBillingActionBackToEdit() {
        mockRequest.setParameter("billingAction", "BACK_TO_EDIT");

        String result = newAction().execute();

        assertThat(result).isEqualTo("backToEdit");
        verifyNoInteractions(mockSaveService);
    }

    @Test
    void shouldReturnFailure_andSkipSaveWhenBillingActionIsUnknown() {
        mockRequest.setParameter("billingAction", "Unknown");

        String result = newAction().execute();

        assertThat(result).isEqualTo("failure");
        assertThat(mockRequest.getAttribute("billingFailed")).isEqualTo(Boolean.TRUE);
        assertThat(mockRequest.getAttribute("billingFailureReason"))
                .isEqualTo("Invalid billing action. Please try again.");
        verifyNoInteractions(mockSaveService);
    }

    @Test
    void shouldDelegateToSaveBillingWithExtAndPayee_onOhipPath() {
        mockRequest.setParameter("billingAction", "SAVE");
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
    void shouldReturnPrintInvoice_whenSavePrintAction() {
        mockRequest.setParameter("billingAction", "SAVE_PRINT");
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
    void shouldReturnAddAnother_whenSaveAddAnotherAction() {
        mockRequest.setParameter("billingAction", "SAVE_ADD_ANOTHER");
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
    @Test
    void shouldRollbackAndRetry_whenCorrectedBillCleanupReturnsFalse() {
        reviewFor("42");mockRequest.setParameter("billNo_old","9");mockRequest.setParameter("billStatus_old","O");
        when(mockSaveService.saveBillingWithExtAndPayee(any(BillingClaimSubmissionService.BillingClaimSubmission.class),
                any(),anyString(),any())).thenReturn(new BillingClaimSubmissionService.SaveResult(true,4321));
        when(mockCorrectionRecordService.getBillingNoStatusByBillNo("9")).thenReturn(java.util.List.of("9","O"));
        when(mockCorrectionRecordService.deleteBilling("9","D","999998")).thenReturn(false,true);
        var props=org.mockito.Mockito.mock(io.github.carlos_emr.CarlosProperties.class);
        when(props.getProperty("isNewONbilling","")).thenReturn("true");
        try(var singleton=mockStatic(io.github.carlos_emr.CarlosProperties.class)) {
            singleton.when(io.github.carlos_emr.CarlosProperties::getInstance).thenReturn(props);
            assertThat(newAction().execute()).isEqualTo("failure");assertThat(transactions.rollbacks).isEqualTo(1);
            repeatOf(mockRequest);assertThat(newAction().execute()).isEqualTo(ActionSupport.SUCCESS);
            assertThat(transactions.commits).isEqualTo(1);
            verify(mockCorrectionRecordService,times(2)).deleteBilling("9","D","999998");
        }
    }

}
