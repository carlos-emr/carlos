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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.email.admin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.EmailAttachment;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.documentManager.PdfPreviewCapabilityService;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.EmailComposeManager;
import io.github.carlos_emr.carlos.managers.EmailManager;
import io.github.carlos_emr.carlos.managers.FormsManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.email.core.EmailComposeSubmissionStateService;
import io.github.carlos_emr.carlos.email.core.EmailComposeSubmissionStateService.EmailComposeSubmissionState;
import io.github.carlos_emr.carlos.email.core.EmailWorkflowUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("fast")
@Tag("email")
@DisplayName("ManageEmails2Action")
class ManageEmails2ActionUnitTest extends EmailWorkflowUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private DemographicManager demographicManager;
    private EmailComposeManager emailComposeManager;
    private EmailManager emailManager;
    private DocumentAttachmentManager documentAttachmentManager;
    private FormsManager formsManager;
    private SecurityInfoManager securityInfoManager;
    private PdfPreviewCapabilityService pdfPreviewCapabilityService;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest("POST", "/admin/ManageEmails");
        response = new MockHttpServletResponse();
        demographicManager = mock(DemographicManager.class);
        emailComposeManager = mock(EmailComposeManager.class);
        emailManager = mock(EmailManager.class);
        documentAttachmentManager = mock(DocumentAttachmentManager.class);
        formsManager = mock(FormsManager.class);
        securityInfoManager = mock(SecurityInfoManager.class);
        // Added to ManageEmails2Action's SpringUtils-wired fields by #3193 after this test was
        // written. The test never ran in CI, so the missing registration went unnoticed.
        pdfPreviewCapabilityService = mock(PdfPreviewCapabilityService.class);

        registerMock(DemographicManager.class, demographicManager);
        registerMock(EmailComposeManager.class, emailComposeManager);
        registerMock(EmailManager.class, emailManager);
        registerMock(DocumentAttachmentManager.class, documentAttachmentManager);
        registerMock(FormsManager.class, formsManager);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(PdfPreviewCapabilityService.class, pdfPreviewCapabilityService);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);
    }

    @AfterEach
    void tearDown() {
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"GET", "HEAD"})
    void shouldRejectCopy_withoutPost(String method) {
        request.setMethod(method);
        request.setParameter("method", "resendEmail");
        request.setParameter("logId", "42");
        assertThat(new ManageEmails2Action().execute()).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(emailComposeManager, demographicManager, documentAttachmentManager,
                emailManager, formsManager, securityInfoManager);
    }

    @Test
    @DisplayName("should show compose error when resending email log without patient context")
    void shouldShowComposeError_whenResendingEmailLogWithoutPatientContext() {
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        request.setParameter("logId", "42");
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.READ, null)).thenReturn(true);
        when(emailComposeManager.prepareEmailForResend(loggedInInfo, 42)).thenReturn(new EmailLog());

        ManageEmails2Action action = new ManageEmails2Action();
        String result = action.resendEmail();

        assertThat(result).isEqualTo("compose");
        assertThat(request.getAttribute("isEmailError")).isEqualTo(true);
        assertThat(request.getAttribute("emailErrorMessage"))
                .isEqualTo("This email cannot be copied because it is not associated with a patient. Please generate a new email instead.");
        verify(emailComposeManager).prepareEmailForResend(loggedInInfo, 42);
        verifyNoInteractions(demographicManager, documentAttachmentManager, emailManager, formsManager);
    }
    @Test
    @DisplayName("should refuse every dispatch without email read privilege")
    void shouldRefuseEveryDispatch_withoutEmailReadPrivilege() {
        // The gap this closes: resendEmail() loaded a patient EmailLog and repopulated the
        // compose page with its subject, body, encrypted message and PDF passphrase before any
        // privilege check ran. Asserting all three dispatch branches, because the check now sits
        // ahead of the branch and a future refactor could easily reinstate a per-branch gate that
        // misses one.
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.READ, null))
                .thenReturn(false);

        for (String method : new String[]{"resendEmail", "fetchEmails", "setResolved", null}) {
            if (method != null) {
                request.setParameter("method", method);
            } else {
                request.removeParameter("method");
            }
            request.setParameter("logId", "42");

            ManageEmails2Action action = new ManageEmails2Action();
            assertThatThrownBy(action::execute)
                    .as("dispatch method=%s must be refused", method)
                    .isInstanceOf(SecurityException.class)
                    .hasMessage("resendEmail".equals(method) ? "missing required sec object (_email)"
                            : "missing required sec object (_email and (_admin or _admin.email))");
        }

        // Refused before anything reads or renders patient data.
        verifyNoInteractions(emailComposeManager, demographicManager, emailManager,
                documentAttachmentManager, formsManager);
    }

    @Test
    @DisplayName("should refuse direct dispatch with email access but without administration access")
    void shouldRefuseEveryDispatch_withoutAdministrationPrivilege() {
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        when(securityInfoManager.hasPrivilege(
                loggedInInfo, "_email", SecurityInfoManager.READ, null)).thenReturn(true);
        when(securityInfoManager.hasPrivilege(
                loggedInInfo, "_admin", SecurityInfoManager.READ, null)).thenReturn(false);
        when(securityInfoManager.hasPrivilege(
                loggedInInfo, "_admin.email", SecurityInfoManager.READ, null)).thenReturn(false);

        for (String method : new String[]{"fetchEmails", "setResolved"}) {
            request.setParameter("method", method);
            request.setParameter("logId", "42");

            ManageEmails2Action action = new ManageEmails2Action();
            assertThatThrownBy(action::execute)
                    .as("dispatch method=%s must retain the admin-page authorization boundary", method)
                    .isInstanceOf(SecurityException.class)
                    .hasMessage("missing required sec object (_email and (_admin or _admin.email))");
        }

        verifyNoInteractions(emailComposeManager, demographicManager, emailManager,
                documentAttachmentManager, formsManager);
    }

    @Test
    @DisplayName("should tell results view whether resolution controls are authorized")
    void shouldHideResolutionControls_withoutEmailWritePrivilege() {
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.WRITE, null))
                .thenReturn(false);

        assertThat(new ManageEmails2Action().fetchEmails()).isEqualTo("emailstatus");

        assertThat(request.getAttribute("canResolveEmails")).isEqualTo(false);
    }

    @Test
    @DisplayName("should dispatch POST setResolved and confirm persistence with no-content response")
    void shouldDispatchSetResolved_whenPostRequestIsValid() {
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        grantManageEmailsRead(loggedInInfo);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.READ, null))
                .thenReturn(true);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.WRITE, null))
                .thenReturn(true);
        request.setMethod("POST");
        request.setParameter("method", "setResolved");
        request.setParameter("logId", "42");
        when(emailManager.resolveEmailStatus(loggedInInfo, 42))
                .thenReturn(EmailManager.EmailResolutionResult.RESOLVED);

        String result = new ManageEmails2Action().execute();

        assertThat(result).isNull();
        assertThat(response.getStatus()).isEqualTo(204);
        verify(emailManager).resolveEmailStatus(loggedInInfo, 42);
    }

    @Test
    @DisplayName("should return not found when resolving a deleted email log")
    void shouldReturnNotFound_whenResolvedEmailNoLongerExists() {
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        grantManageEmailsRead(loggedInInfo);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.READ, null))
                .thenReturn(true);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.WRITE, null))
                .thenReturn(true);
        when(emailManager.resolveEmailStatus(loggedInInfo, 42))
                .thenReturn(EmailManager.EmailResolutionResult.NOT_FOUND);
        request.setMethod("POST");
        request.setParameter("method", "setResolved");
        request.setParameter("logId", "42");

        assertThat(new ManageEmails2Action().execute()).isNull();

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getContentType()).contains("application/json");
    }

    @Test
    @DisplayName("should return conflict when an email changed before resolution")
    void shouldReturnConflict_whenResolutionLosesConcurrentTransition() {
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        grantManageEmailsRead(loggedInInfo);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.READ, null))
                .thenReturn(true);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.WRITE, null))
                .thenReturn(true);
        when(emailManager.resolveEmailStatus(loggedInInfo, 42))
                .thenReturn(EmailManager.EmailResolutionResult.CONFLICT);
        request.setMethod("POST");
        request.setParameter("method", "setResolved");
        request.setParameter("logId", "42");

        assertThat(new ManageEmails2Action().execute()).isNull();

        assertThat(response.getStatus()).isEqualTo(409);
    }

    @Test
    @DisplayName("should forbid resolution without email write privilege")
    void shouldForbidResolution_withoutEmailWritePrivilege() throws Exception {
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        grantManageEmailsRead(loggedInInfo);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.READ, null))
                .thenReturn(true);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.WRITE, null))
                .thenReturn(false);
        request.setMethod("POST");
        request.setParameter("method", "setResolved");
        request.setParameter("logId", "42");

        assertThat(new ManageEmails2Action().execute()).isNull();

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).contains("application/json");
        assertThat(response.getContentAsString()).contains("permission");
        verifyNoInteractions(emailManager);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "post", "Post", "PO\u017fT"})
    @DisplayName("should require the exact POST method before resolving an email")
    void shouldRejectSetResolved_whenRequestMethodIsNotPost(String method) {
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        grantManageEmailsRead(loggedInInfo);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.READ, null))
                .thenReturn(true);
        request.setMethod(method);
        request.setParameter("method", "setResolved");
        request.setParameter("logId", "42");

        String result = new ManageEmails2Action().execute();

        assertThat(result).isNull();
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(emailManager);
    }

    @Test
    @DisplayName("should show compose error when the resent email log has a null demographic number")
    void shouldShowComposeError_whenResentEmailLogHasNullDemographicNumber() {
        // Distinct from the no-demographic case above: here the log HAS a demographic, but its
        // number is null. Both must take the same branch, because the code path that follows
        // unboxes that number.
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        request.setParameter("logId", "42");
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.READ, null)).thenReturn(true);
        EmailLog emailLog = new EmailLog();
        emailLog.setDemographic(new Demographic());
        when(emailComposeManager.prepareEmailForResend(loggedInInfo, 42)).thenReturn(emailLog);

        String result = new ManageEmails2Action().resendEmail();

        assertThat(result).isEqualTo("compose");
        assertThat(request.getAttribute("isEmailError")).isEqualTo(true);
        verify(emailComposeManager).prepareEmailForResend(loggedInInfo, 42);
        verifyNoInteractions(demographicManager, documentAttachmentManager, emailManager, formsManager);
    }
    @Test
    @DisplayName("should warn but still compose when resending an email still recorded as pending")
    void shouldWarnButStillCompose_whenResendingPendingEmail() {
        // Warn, not block. A PENDING row has no recorded outcome, so the message may already have
        // reached the patient -- or may have died before sending. The admin decides.
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.READ, null))
                .thenReturn(true);
        request.setParameter("logId", "42");
        stubComposeLookups(loggedInInfo);
        EmailLog pending = pendingEmailLog();
        when(emailComposeManager.prepareEmailForResend(loggedInInfo, 42)).thenReturn(pending);
        when(emailManager.isManuallyResolvable(pending)).thenReturn(true);

        String result = new ManageEmails2Action().resendEmail();

        assertThat(result).isEqualTo("compose");
        assertThat(request.getAttribute("isPendingEmailResend")).isEqualTo(true);
        // Warning only: this must not take the terminal error path, which closes the window.
        assertThat(request.getAttribute("isEmailError")).isNull();
    }

    @Test
    @DisplayName("should block copying a fresh pending email for resend")
    void shouldBlockResend_whenPendingEmailMayStillBeSending() {
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.READ, null))
                .thenReturn(true);
        request.setParameter("logId", "42");
        EmailLog pending = pendingEmailLog();
        when(emailComposeManager.prepareEmailForResend(loggedInInfo, 42)).thenReturn(pending);
        when(emailManager.isManuallyResolvable(pending)).thenReturn(false);

        assertThat(new ManageEmails2Action().resendEmail()).isEqualTo("compose");

        assertThat(request.getAttribute("isEmailError")).isEqualTo(true);
        assertThat(request.getAttribute("isPendingEmailResend")).isNull();
        verifyNoInteractions(demographicManager, documentAttachmentManager, formsManager);
    }

    @Test
    @DisplayName("should send an unresolved portal email to recovery instead of the resend composer")
    void shouldRedirectToPortalRecovery_whenPortalDeliveryIsUnresolved() {
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.READ, null))
                .thenReturn(true);
        request.setParameter("logId", "42");
        EmailLog accepted = new EmailLog();
        org.springframework.test.util.ReflectionTestUtils.setField(accepted, "id", 42);
        accepted.setStatus(EmailLog.EmailStatus.SUCCESS);
        accepted.setPortalDeliveryState(EmailLog.PortalDeliveryState.SENT);
        when(emailComposeManager.prepareEmailForResend(loggedInInfo, 42)).thenReturn(accepted);

        assertThat(new ManageEmails2Action().resendEmail()).isEqualTo("none");

        assertThat(response.getRedirectedUrl()).isEqualTo("/email/portalDelivery?emailLogId=42");
        verifyNoInteractions(demographicManager, documentAttachmentManager, formsManager);
    }

    @Test
    @DisplayName("should send a fresh pending portal email to recovery, which shows its own wait state")
    void shouldRedirectToPortalRecovery_whenPortalEmailIsStillPending() {
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.READ, null))
                .thenReturn(true);
        request.setParameter("logId", "42");
        EmailLog pending = pendingEmailLog();
        org.springframework.test.util.ReflectionTestUtils.setField(pending, "id", 42);
        pending.setPortalDeliveryState(EmailLog.PortalDeliveryState.READY);
        when(emailComposeManager.prepareEmailForResend(loggedInInfo, 42)).thenReturn(pending);

        assertThat(new ManageEmails2Action().resendEmail()).isEqualTo("none");

        assertThat(response.getRedirectedUrl()).isEqualTo("/email/portalDelivery?emailLogId=42");
    }

    @Test
    @DisplayName("should send a portal invitation to the patient's portal page instead of the resend composer")
    void shouldRedirectToPortalPage_whenEmailIsAPortalInvitation() {
        // Both Manage Emails and the chart-note email viewer open this route; neither may show the
        // invitation, and neither should end on an error page.
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.READ, null))
                .thenReturn(true);
        request.setParameter("logId", "42");
        when(emailComposeManager.prepareEmailForResend(loggedInInfo, 42))
                .thenThrow(new EmailComposeManager.PortalInviteEmailException(123));

        assertThat(new ManageEmails2Action().resendEmail()).isEqualTo("none");

        assertThat(response.getRedirectedUrl()).isEqualTo("/demographic/portalManage?demographicNo=123");
        verifyNoInteractions(demographicManager, documentAttachmentManager, emailManager, formsManager);
    }

    @Test
    @DisplayName("should not warn when resending an email already recorded as failed")
    void shouldNotWarn_whenResendingFailedEmail() {
        // The whole point of PENDING is that it is distinguishable from FAILED. A genuinely failed
        // send is the normal resend case and must stay friction-free.
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.READ, null))
                .thenReturn(true);
        request.setParameter("logId", "42");
        EmailLog failed = pendingEmailLog();
        failed.setStatus(EmailLog.EmailStatus.FAILED);
        stubComposeLookups(loggedInInfo);
        when(emailComposeManager.prepareEmailForResend(loggedInInfo, 42)).thenReturn(failed);

        assertThat(new ManageEmails2Action().resendEmail()).isEqualTo("compose");
        assertThat(request.getAttribute("isPendingEmailResend")).isNull();
    }

    @Test
    @DisplayName("should bind the resend's refreshed attachments to its own compose token and the logged patient")
    void shouldBindRefreshedAttachments_toLoggedPatient() throws Exception {
        // Manage Emails resend binds its attachments the way a compose does (#4425): to a one-time
        // submission token whose trusted context is the logged email's patient, never to a
        // session-wide list another window could replace.
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.READ, null))
                .thenReturn(true);
        request.setParameter("logId", "7001");
        stubComposeLookups(loggedInInfo);
        when(emailComposeManager.prepareEmailForResend(loggedInInfo, 7001))
                .thenReturn(emailLogWithEFormAttachment(501));
        Path refreshed = Files.writeString(emailTempDir.resolve("FAKE-refreshed.pdf"), "%PDF-1.4 FAKE");
        when(documentAttachmentManager.renderDocument(loggedInInfo, DocumentType.EFORM, 501)).thenReturn(refreshed);
        request.getSession().setAttribute("emailAttachmentList", List.of());

        assertThat(new ManageEmails2Action().resendEmail()).isEqualTo("compose");

        assertThat(request.getAttribute("isEmailError")).isNull();
        assertThat(request.getSession().getAttribute("emailAttachmentList")).as("no session-wide list").isNull();
        String token = (String) request.getAttribute(EmailComposeSubmissionStateService.EMAIL_PDF_PASSWORD_TOKEN_PARAM);
        assertThat(token).isNotBlank();
        MockHttpServletRequest send = new MockHttpServletRequest("POST", "/email/emailSendAction");
        send.setSession(request.getSession());
        send.setParameter(EmailComposeSubmissionStateService.EMAIL_PDF_PASSWORD_TOKEN_PARAM, token);
        try (EmailComposeSubmissionState state = submissionStates.consume(send)) {
            assertThat(state.context().demographicId()).isEqualTo("123");
            assertThat(state.context().transactionType()).isEqualTo(EmailLog.TransactionType.DIRECT);
            assertThat(state.emailAttachmentList()).singleElement().satisfies(attachment -> {
                assertThat(attachment.getDocumentType()).isEqualTo(DocumentType.EFORM);
                assertThat(attachment.getDocumentId()).isEqualTo(501);
                assertThat(attachment.getEmailLog()).as("detached from the persistent log").isNull();
            });
        }
    }

    @Test
    @DisplayName("should bind nothing when the resend's attachments cannot be refreshed")
    void shouldBindNothing_whenRefreshFails() throws Exception {
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.READ, null))
                .thenReturn(true);
        request.setParameter("logId", "7002");
        when(emailComposeManager.prepareEmailForResend(loggedInInfo, 7002))
                .thenReturn(emailLogWithEFormAttachment(502));
        when(documentAttachmentManager.renderDocument(loggedInInfo, DocumentType.EFORM, 502))
                .thenThrow(new PDFGenerationException("FAKE render failure"));

        assertThat(new ManageEmails2Action().resendEmail()).isEqualTo("compose");

        // The error page only reports and closes; without a token any send from it is refused.
        assertThat(request.getAttribute("isEmailError")).isEqualTo(true);
        assertThat(request.getAttribute(EmailComposeSubmissionStateService.EMAIL_PDF_PASSWORD_TOKEN_PARAM)).isNull();
        assertThat(request.getAttribute("emailAttachmentList")).isNull();
        assertThat(submissionStates.clear(request.getSession().getId())).as("no compose state stored").isZero();
    }

    /** A failed send of patient 123 that carried one eForm attachment. */
    private EmailLog emailLogWithEFormAttachment(int fdid) {
        EmailLog emailLog = pendingEmailLog();
        emailLog.setStatus(EmailLog.EmailStatus.FAILED);
        emailLog.setEmailAttachments(new ArrayList<>(List.of(
                new EmailAttachment(emailLog, "FAKE-eform.pdf", "/tmp/FAKE-old.pdf", DocumentType.EFORM, fdid))));
        return emailLog;
    }

    /** Stubs the lookups resendEmail() fans out to once it has a usable log. */
    private void stubComposeLookups(LoggedInInfo loggedInInfo) {
        when(emailComposeManager.getEmailConsentStatus(loggedInInfo, 123)).thenReturn(new String[]{"consent", "OPT_IN", "email.consent.status.optIn"});
        when(emailComposeManager.getRecipients(loggedInInfo, 123))
                .thenReturn(new java.util.List<?>[]{java.util.List.of(), java.util.List.of()});
        when(emailComposeManager.getAllSenderAccounts()).thenReturn(java.util.List.of());
    }

    private void grantManageEmailsRead(LoggedInInfo loggedInInfo) {
        when(securityInfoManager.hasPrivilege(
                loggedInInfo, "_admin.email", SecurityInfoManager.READ, null)).thenReturn(true);
    }

    private EmailLog pendingEmailLog() {
        EmailLog emailLog = new EmailLog();
        emailLog.setStatus(EmailLog.EmailStatus.PENDING);
        Demographic demographic = new Demographic();
        demographic.setDemographicNo(123);
        emailLog.setDemographic(demographic);
        emailLog.setChartDisplayOption(EmailLog.ChartDisplayOption.WITHOUT_NOTE);
        emailLog.setEmailAttachments(new java.util.ArrayList<>());
        // Body and encrypted message are stored as bytes and decoded unconditionally on read.
        emailLog.setBody("body");
        emailLog.setEncryptedMessage("");
        return emailLog;
    }
}
