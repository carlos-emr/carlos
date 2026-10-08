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
package io.github.carlos_emr.carlos.email.action;

import java.util.List;

import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.github.carlos_emr.carlos.commn.model.EmailAttachment;
import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.EmailLog.EmailConsentStatus;
import io.github.carlos_emr.carlos.commn.model.EmailLog.EmailStatus;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.email.core.EmailData;
import io.github.carlos_emr.carlos.email.core.EmailFooterService;
import io.github.carlos_emr.carlos.email.core.EmailSendResult;
import io.github.carlos_emr.carlos.email.core.EmailSessionKeys;
import io.github.carlos_emr.carlos.managers.EformDataManager;
import io.github.carlos_emr.carlos.managers.EmailComposeManager;
import io.github.carlos_emr.carlos.managers.EmailManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.email.core.EmailWorkflowUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EmailSend2Action} merged-message routing, validation, and retry state.
 *
 * @since 2026-05-20
 */
@Tag("unit")
@Tag("fast")
@Tag("email")
@DisplayName("EmailSend2Action merged-message behavior")
class EmailSend2ActionMergedMessageUnitTest extends EmailWorkflowUnitTestBase {

    private static final String ENCRYPTED_BODY_NOTICE_KEY = "email.compose.msg.encryptedBodyNotice";

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private EmailManager emailManager;
    private EmailComposeManager emailComposeManager;
    private SecurityInfoManager securityInfoManager;

    @BeforeEach
    void setUp() {
        securityInfoManager = mock(SecurityInfoManager.class);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        emailManager = mock(EmailManager.class);
        registerMock(EmailManager.class, emailManager);
        emailComposeManager = mock(EmailComposeManager.class);
        registerMock(EmailComposeManager.class, emailComposeManager);
        registerMock(EformDataManager.class, mock(EformDataManager.class));
        // EmailSend2Action reads request/response from ServletActionContext in field initializers
        // (evaluated at construction), so mock the static to keep `new EmailSend2Action()` from
        // NPEing before each test assigns action.request/response explicitly.
        servletActionContextMock = mockStatic(ServletActionContext.class);
    }

    @AfterEach
    void tearDown() {
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
    }

    @Test
    @DisplayName("should route the single message into the encrypted PDF when encryption is on")
    void shouldRouteMessageToEncryptedPdf_whenEncryptionOn() {
        EmailData sent = captureSentEmail(
                "Confidential lab result for the patient.", "true", "true");

        // The clinical content goes into the encrypted-PDF channel; the visible cleartext body
        // is only the fixed, PHI-free notice.
        assertThat(sent.getEncryptedMessage()).isEqualTo("Confidential lab result for the patient.");
        assertThat(sent.getBody()).isEqualTo("SECURE_NOTICE");
        assertThat(sent.getIsEncrypted()).isTrue();
        assertThat(sent.getAttachments()).hasSize(1);
        assertThat(sent.getIsAttachmentEncrypted()).isTrue();
    }

    @Test
    @DisplayName("should route the single message into the cleartext body when encryption is off")
    void shouldRouteMessageToCleartextBody_whenEncryptionOff() {
        EmailData sent = captureSentEmail("A non-clinical reminder.", "false", "false");

        // The message is sent as the cleartext body; the encrypted-PDF channel stays empty so the
        // client can never populate both at once.
        assertThat(sent.getBody()).isEqualTo("A non-clinical reminder.");
        assertThat(sent.getEncryptedMessage()).isEmpty();
        assertThat(sent.getIsEncrypted()).isFalse();
        assertThat(sent.getAttachments()).hasSize(1);
        assertThat(sent.getIsAttachmentEncrypted()).isFalse();
    }

    @Test
    @DisplayName("should reject attachment encryption when message encryption is off")
    void shouldRejectAttachmentEncryption_whenMessageEncryptionOff() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/email/send");
        request.setParameter("method", "sendDirectEmail");
        request.setParameter("message", "Confidential note.");
        request.setParameter("isEmailEncrypted", "false");
        request.setParameter("isEmailAttachmentEncrypted", "true");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());
        when(securityInfoManager.hasPrivilege(any(), any(), any(), any())).thenReturn(true);

        EmailSend2Action action = new EmailSend2Action();
        action.request = request;
        MockHttpServletResponse response = new MockHttpServletResponse();
        action.response = response;

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        assertThat(response.getContentAsString())
                .contains("Attachment encryption requires message encryption");
        verifyNoInteractions(emailManager);
    }

    @Test
    @DisplayName("should refuse an encrypted send before consuming the draft when the portal setting is malformed")
    void shouldRefuseEncryptedSendKeepingTheDraft_whenPortalSettingIsMalformed() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/email/send");
        request.setParameter("method", "sendDirectEmail");
        request.setParameter("message", "Confidential note.");
        request.setParameter("isEmailEncrypted", "true");
        request.setParameter("isEmailAttachmentEncrypted", "true");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());
        when(securityInfoManager.hasPrivilege(any(), any(), any(), any())).thenReturn(true);

        EmailSend2Action action = spy(new EmailSend2Action());
        action.request = request;
        MockHttpServletResponse response = new MockHttpServletResponse();
        action.response = response;
        doReturn("PORTAL_SETTING_INVALID").when(action).getText("email.compose.portal.misconfigured");

        try (MockedStatic<io.github.carlos_emr.carlos.integration.patientportal.PortalEmailDeliveryService> portal =
                     mockStatic(io.github.carlos_emr.carlos.integration.patientportal.PortalEmailDeliveryService.class)) {
            portal.when(io.github.carlos_emr.carlos.integration.patientportal.PortalEmailDeliveryService::isEnabled)
                    .thenThrow(new io.github.carlos_emr.carlos.integration.patientportal
                            .PatientPortalConfigurationException("patient_portal.email.enabled must be true or false"));

            assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        }
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        assertThat(response.getContentAsString()).contains("PORTAL_SETTING_INVALID");
        verifyNoInteractions(emailManager);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("should reject missing or blank messages without consuming attachments")
    void shouldRejectSendWithoutConsumingAttachments_whenMessageMissingOrBlank(String message) {
        MockHttpServletRequest request = encryptedSendRequest(message);
        request.setMethod("POST");
        request.setParameter("method", "sendDirectEmail");
        request.setParameter("emailPDFPassword", "valid-password");
        request.setParameter("emailPDFPasswordClue", "Known to the patient");
        List<EmailAttachment> attachments = List.of(mock(EmailAttachment.class));
        request.getSession().setAttribute(EmailSessionKeys.EMAIL_ATTACHMENT_LIST, attachments);
        when(securityInfoManager.hasPrivilege(any(), any(), any(), any())).thenReturn(true);

        EmailSend2Action action = spy(new EmailSend2Action());
        action.request = request;
        MockHttpServletResponse response = new MockHttpServletResponse();
        action.response = response;

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        assertThat(request.getSession().getAttribute(EmailSessionKeys.EMAIL_ATTACHMENT_LIST)).isSameAs(attachments);
        verifyNoInteractions(emailManager);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "unknown"})
    @DisplayName("should reject unsupported POST operations before sending email")
    void shouldRejectUnsupportedOperation_beforeSendingEmail(String actionMethod) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/email/send");
        request.setParameter("method", actionMethod);
        request.setParameter("message", "This message must not be sent.");
        request.setParameter("isEmailEncrypted", "false");
        request.setParameter("senderConfigId", "1");
        request.setParameter("demographicId", "42");
        request.setParameter("transactionType", "EFORM");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());
        when(securityInfoManager.hasPrivilege(any(), any(), any(), any())).thenReturn(true);

        EmailSend2Action action = new EmailSend2Action();
        action.request = request;
        MockHttpServletResponse response = new MockHttpServletResponse();
        action.response = response;

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(emailManager);
    }

    @Test
    @DisplayName("should default message and attachment encryption on when their flags are missing")
    void shouldDefaultEncryptionOn_whenEncryptionFlagsMissing() {
        // Fail closed: a direct/malformed POST that omits either encryption toggle must protect both
        // the message channel and any attachments supplied through the compose session.
        EmailData sent = captureSentEmail("Confidential note.", null, null);

        assertThat(sent.getEncryptedMessage()).isEqualTo("Confidential note.");
        assertThat(sent.getBody()).isEqualTo("SECURE_NOTICE");
        assertThat(sent.getIsEncrypted()).isTrue();
        assertThat(sent.getAttachments()).hasSize(1);
        assertThat(sent.getIsAttachmentEncrypted()).isTrue();
    }

    @Test
    @DisplayName("should preserve the complete retry model and original attachments when delivery fails")
    void shouldPreserveCompleteRetryModelAndOriginalAttachments_whenDeliveryFails() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("message", "Draft to retry.");
        request.setParameter("isEmailEncrypted", "true");
        request.setParameter("isEmailAttachmentEncrypted", "true");
        request.setParameter("emailPDFPassword", "valid-password");
        request.setParameter("emailPDFPasswordClue", "Known to the patient");
        request.setParameter("senderConfigId", "1");
        request.setParameter("receiverEmailAddress", "patient@example.test");
        request.setParameter("subjectEmail", "Retry subject");
        request.setParameter("transactionType", "DIRECT");
        request.setParameter("patientChartOption", "addFullNote");
        request.setParameter("demographicId", "42");
        request.setParameter("emailConsentName", "Email consent");
        request.setParameter("emailConsentStatus", "OPT_IN");
        request.setParameter("consentOverride", "true");
        request.setParameter("consentOverrideReason", "Forged request value");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());

        EmailAttachment originalAttachment = new EmailAttachment(
                "result.pdf", java.nio.file.Files.writeString(emailTempDir.resolve("original.pdf"), "original").toString(), DocumentType.DOC, 7, 123L);
        originalAttachment.setPreviewToken("preview-token");
        List<EmailAttachment> originalAttachments = List.of(originalAttachment);
        request.getSession().setAttribute(EmailSessionKeys.EMAIL_ATTACHMENT_LIST, originalAttachments);

        EmailLog emailLog = mock(EmailLog.class);
        EmailConfig emailConfig = mock(EmailConfig.class);
        EmailConfig alternateEmailConfig = mock(EmailConfig.class);
        when(emailLog.getStatus()).thenReturn(EmailStatus.FAILED);
        when(emailLog.getConsentStatus()).thenReturn(EmailConsentStatus.OPT_OUT);
        when(emailLog.getEmailConfig()).thenReturn(emailConfig);
        when(emailLog.getFromEmail()).thenReturn("clinic@example.test");
        when(emailComposeManager.getAllSenderAccounts())
                .thenReturn(List.of(emailConfig, alternateEmailConfig));
        ArgumentCaptor<EmailData> sentEmail = ArgumentCaptor.forClass(EmailData.class);
        when(emailManager.sendEmailWithResult(any(LoggedInInfo.class), sentEmail.capture())).thenAnswer(invocation -> {
            EmailData emailData = invocation.getArgument(1);
            emailData.getAttachments().get(0).setFilePath("/tmp/encrypted-result.pdf");
            return sendResult(emailLog);
        });

        EmailSend2Action action = spy(new EmailSend2Action());
        doReturn("SECURE_NOTICE").when(action).getText(ENCRYPTED_BODY_NOTICE_KEY);
        action.request = request;
        action.response = new MockHttpServletResponse();

        prepareSubmission(request, originalAttachments);
        action.sendDirectEmail();

        assertThat(request.getAttribute("message")).isEqualTo("Draft to retry.");
        assertThat(request.getAttribute("isEmailEncrypted")).isEqualTo(true);
        assertThat(request.getAttribute("isEmailAttachmentEncrypted")).isEqualTo(true);
        assertThat(request.getAttribute("subjectEmail")).isEqualTo("Retry subject");
        assertThat(request.getAttribute("emailConsentName")).isEqualTo("Email consent");
        assertThat(request.getAttribute("emailConsentStatus")).isEqualTo("OPT_OUT");
        assertThat(request.getAttribute("emailConsentMessageKey"))
                .isEqualTo("email.consent.status.optOut");
        assertThat(request.getAttribute("consentOverride")).isEqualTo(false);
        assertThat(request.getAttribute("consentOverrideReason")).isEqualTo("");
        assertThat(request.getAttribute("receiverEmailList"))
                .isEqualTo(List.of("patient@example.test"));
        assertThat(request.getAttribute("senderAccounts"))
                .isEqualTo(List.of(emailConfig, alternateEmailConfig));
        assertThat(request.getSession().getAttribute(EmailSessionKeys.EMAIL_ATTACHMENT_LIST))
                .isSameAs(originalAttachments);
        assertThat(originalAttachment.getFilePath()).isEqualTo(emailTempDir.resolve("original.pdf").toString());
        assertThat(sentEmail.getValue().getAttachments().get(0)).isNotSameAs(originalAttachment);
    }

    @Test
    @DisplayName("should preserve an accepted consent override when delivery fails")
    void shouldPreserveAcceptedConsentOverride_whenDeliveryFails() {
        MockHttpServletRequest request = encryptedSendRequest();
        request.setParameter("emailPDFPassword", "valid-password");
        request.setParameter("emailPDFPasswordClue", "Known to the patient");
        request.setParameter("consentOverride", "true");
        request.setParameter("consentOverrideReason", "Provider confirmed verbal consent");

        EmailLog emailLog = mock(EmailLog.class);
        when(emailLog.getStatus()).thenReturn(EmailStatus.FAILED);
        when(emailLog.getConsentStatus()).thenReturn(EmailConsentStatus.UNKNOWN);
        when(emailLog.getConsentOverride()).thenReturn(true);
        when(emailLog.getConsentOverrideReason())
                .thenReturn("Provider confirmed verbal consent");
        when(emailManager.sendEmailWithResult(any(LoggedInInfo.class), any(EmailData.class)))
                .thenAnswer(invocation -> sendResult(emailLog));

        EmailSend2Action action = spy(new EmailSend2Action());
        doReturn("SECURE_NOTICE").when(action).getText(ENCRYPTED_BODY_NOTICE_KEY);
        action.request = request;
        action.response = new MockHttpServletResponse();

        prepareSubmission(request);
        action.sendDirectEmail();

        assertThat(request.getAttribute("consentOverride")).isEqualTo(true);
        assertThat(request.getAttribute("consentOverrideReason"))
                .isEqualTo("Provider confirmed verbal consent");
    }

    @Test
    @DisplayName("should keep the submitted footer, cleaned, on the retry form when delivery fails")
    void shouldKeepFooter_whenRetryRendersAfterDeliveryFailure() {
        MockHttpServletRequest request = encryptedSendRequest();
        request.setParameter("footerEmail", "<b>Riverside Clinic</b><br>Not monitored for urgent issues.<script>x()</script>");

        EmailLog emailLog = mock(EmailLog.class);
        when(emailLog.getStatus()).thenReturn(EmailStatus.FAILED);
        when(emailManager.sendEmailWithResult(any(LoggedInInfo.class), any(EmailData.class)))
                .thenAnswer(invocation -> sendResult(emailLog));

        EmailSend2Action action = spy(new EmailSend2Action());
        doReturn("SECURE_NOTICE").when(action).getText(ENCRYPTED_BODY_NOTICE_KEY);
        action.request = request;
        action.response = new MockHttpServletResponse();

        prepareSubmission(request);
        action.sendDirectEmail();

        assertThat(request.getAttribute("isEmailSuccessful")).isEqualTo(false);
        assertThat(request.getAttribute("footerEmail"))
                .isEqualTo("<b>Riverside Clinic</b><br>Not monitored for urgent issues.");
    }

    @Test
    @DisplayName("should keep the submitted footer when the compose window has expired")
    void shouldKeepFooter_whenComposeStateIsMissing() {
        MockHttpServletRequest request = encryptedSendRequest();
        request.setParameter("footerEmail", "Riverside Clinic");

        EmailSend2Action action = spy(new EmailSend2Action());
        doReturn("SECURE_NOTICE").when(action).getText(ENCRYPTED_BODY_NOTICE_KEY);
        action.request = request;
        action.response = new MockHttpServletResponse();

        action.sendDirectEmail();

        assertThat(request.getAttribute("isEmailComposeStateError")).isEqualTo(true);
        assertThat(request.getAttribute("footerEmail")).isEqualTo("Riverside Clinic");
        verifyNoInteractions(emailManager);
    }

    @Test
    @DisplayName("should send a copied email with the footer its log kept, below the message")
    void shouldResendLoggedFooter_whenCopiedEmailIsSent() {
        // First send: the editor leaves a trailing line break, and the log keeps the footer as it
        // was sent, cleaned (EmailManager stores getSentFooter()).
        EmailData first = captureSentEmail("A non-clinical reminder.", "false", "false",
                "<b>Riverside Clinic</b><br>Not monitored for urgent issues.<br>");
        EmailLog logged = new EmailLog();
        logged.setFooter(first.getSentFooter());

        // Copy and send again: Manage Emails fills the Footer box with the logged footer
        // (ManageEmails2ActionUnitTest), and the form posts it back unchanged.
        EmailData resent = captureSentEmail("A non-clinical reminder.", "false", "false", logged.getFooter());

        assertThat(resent.getSentFooter()).isEqualTo(first.getSentFooter())
                .isEqualTo("<b>Riverside Clinic</b><br>Not monitored for urgent issues.");
        assertThat(resent.getTransmittedBody())
                .isEqualTo("A non-clinical reminder.\n\nRiverside Clinic\nNot monitored for urgent issues.");
    }

    @Test
    @DisplayName("should retain the failed sender when refreshing sender accounts fails")
    void shouldRetainFailedSender_whenSenderAccountRefreshFails() {
        MockHttpServletRequest request = encryptedSendRequest();
        request.setParameter("emailPDFPassword", "valid-password");
        request.setParameter("emailPDFPasswordClue", "Known to the patient");

        EmailConfig failedSender = mock(EmailConfig.class);
        EmailLog emailLog = mock(EmailLog.class);
        when(emailLog.getStatus()).thenReturn(EmailStatus.FAILED);
        when(emailLog.getEmailConfig()).thenReturn(failedSender);
        when(emailManager.sendEmailWithResult(any(LoggedInInfo.class), any(EmailData.class))).thenAnswer(invocation -> sendResult(emailLog));
        when(emailComposeManager.getAllSenderAccounts())
                .thenThrow(new IllegalStateException("sender lookup unavailable"));

        EmailSend2Action action = spy(new EmailSend2Action());
        doReturn("SECURE_NOTICE").when(action).getText(ENCRYPTED_BODY_NOTICE_KEY);
        action.request = request;
        action.response = new MockHttpServletResponse();

        prepareSubmission(request);
        action.sendDirectEmail();

        assertThat(request.getAttribute("senderAccounts")).isEqualTo(List.of(failedSender));
    }

    @Test
    @DisplayName("should re-render the encryption toggle on when the flag is missing")
    void shouldReRenderEncryptionOn_whenFlagMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("message", "Draft to retry.");
        // isEmailEncrypted omitted entirely, mirroring a direct/malformed POST.
        request.setParameter("emailPDFPassword", "valid-password");
        request.setParameter("emailPDFPasswordClue", "Known to the patient");
        request.setParameter("senderConfigId", "1");
        request.setParameter("demographicId", "42");
        request.getSession().setAttribute(EmailSessionKeys.EMAIL_ATTACHMENT_LIST, List.of(
                new EmailAttachment("result.pdf", "/tmp/result.pdf", DocumentType.DOC, 7)));
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());

        EmailLog emailLog = mock(EmailLog.class);
        when(emailLog.getStatus()).thenReturn(EmailStatus.FAILED);
        when(emailManager.sendEmailWithResult(any(LoggedInInfo.class), any(EmailData.class))).thenAnswer(invocation -> sendResult(emailLog));

        EmailSend2Action action = spy(new EmailSend2Action());
        doReturn("SECURE_NOTICE").when(action).getText(ENCRYPTED_BODY_NOTICE_KEY);
        action.request = request;
        action.response = new MockHttpServletResponse();

        prepareSubmission(request);
        action.sendDirectEmail();

        // Fail closed: a missing toggle must re-render ON so a retry stays encrypted, matching the
        // fail-closed send-side routing.
        assertThat(request.getAttribute("isEmailEncrypted")).isEqualTo(true);
        assertThat(request.getAttribute("isEmailAttachmentEncrypted")).isEqualTo(true);
    }

    @Test
    @DisplayName("should clear compose attachments after successful delivery")
    void shouldClearComposeAttachments_whenDeliverySucceeds() {
        MockHttpServletRequest request = encryptedSendRequest();
        request.setParameter("emailPDFPassword", "valid-password");
        request.setParameter("emailPDFPasswordClue", "Known to the patient");
        request.getSession().setAttribute(EmailSessionKeys.EMAIL_ATTACHMENT_LIST, List.of(
                new EmailAttachment("result.pdf", "/tmp/result.pdf", DocumentType.DOC, 7)));

        EmailLog emailLog = mock(EmailLog.class);
        when(emailLog.getStatus()).thenReturn(EmailStatus.SUCCESS);
        when(emailManager.sendEmailWithResult(any(LoggedInInfo.class), any(EmailData.class))).thenAnswer(invocation -> sendResult(emailLog));

        EmailSend2Action action = spy(new EmailSend2Action());
        doReturn("SECURE_NOTICE").when(action).getText(ENCRYPTED_BODY_NOTICE_KEY);
        action.request = request;
        action.response = new MockHttpServletResponse();

        prepareSubmission(request);
        action.sendDirectEmail();

        assertThat(submissionStates.consume(request)).isNull();
    }

    @Test
    @DisplayName("should reject messages over the server-side size limit")
    void shouldRejectMessage_whenMessageExceedsSizeLimit() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/email/send");
        request.setParameter("method", "sendDirectEmail");
        request.setParameter("message", "x".repeat(10_001));
        request.setParameter("isEmailEncrypted", "false");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());
        when(securityInfoManager.hasPrivilege(any(), any(), any(), any())).thenReturn(true);

        EmailSend2Action action = new EmailSend2Action();
        action.request = request;
        MockHttpServletResponse response = new MockHttpServletResponse();
        action.response = response;

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        assertThat(response.getContentAsString()).contains("must not exceed 10000 characters");
        verifyNoInteractions(emailManager);
    }

    @Test
    @DisplayName("should reject a footer over the server-side size limit before sending")
    void shouldRejectFooter_whenFooterExceedsSizeLimit() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/email/send");
        request.setParameter("method", "sendDirectEmail");
        request.setParameter("message", "Message");
        request.setParameter("isEmailEncrypted", "false");
        request.setParameter("footerEmail", "f".repeat(EmailData.FOOTER_MAX_LENGTH + 1));
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());
        when(securityInfoManager.hasPrivilege(any(), any(), any(), any())).thenReturn(true);

        EmailSend2Action action = new EmailSend2Action();
        action.request = request;
        MockHttpServletResponse response = new MockHttpServletResponse();
        action.response = response;

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        assertThat(response.getContentAsString()).contains("Footer must not exceed 2000 characters");
        verifyNoInteractions(emailManager);
    }

    @Test
    @DisplayName("should reject a footer whose formatting is over its own limit, with a message that says so")
    void shouldRejectFooter_whenFormattingExceedsHtmlLimit() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/email/send");
        request.setParameter("method", "sendDirectEmail");
        request.setParameter("message", "Message");
        request.setParameter("isEmailEncrypted", "false");
        // 1,500 visible characters, well under 2,000, but 12,000 characters of HTML.
        request.setParameter("footerEmail", "<b>x</b>".repeat(1_500));
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());
        when(securityInfoManager.hasPrivilege(any(), any(), any(), any())).thenReturn(true);

        EmailSend2Action action = new EmailSend2Action();
        action.request = request;
        MockHttpServletResponse response = new MockHttpServletResponse();
        action.response = response;

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        assertThat(response.getContentAsString()).contains("Footer must not exceed 10000 characters of formatting");
        verifyNoInteractions(emailManager);
    }

    @Test
    @DisplayName("should count each line break once against the footer limit")
    void shouldAcceptFooter_whenLimitReachedOnlyByLineBreaks() {
        // 1,998 + one line break + 1 = exactly 2,000 characters of plain text.
        String footer = "f".repeat(EmailData.FOOTER_MAX_LENGTH - 2) + "<br>" + "g";

        EmailData sent = captureSentEmail("A non-clinical reminder.", "false", "false", footer);

        assertThat(sent.getFooter()).isEqualTo(footer);
    }

    @Test
    @DisplayName("should send the footer after the secure-message notice and outside the encrypted PDF")
    void shouldSendFooterAfterNotice_whenEncryptionOn() {
        EmailData sent = captureSentEmail(
                "Confidential lab result for the patient.", "true", "true", "Riverside Clinic");

        assertThat(sent.getBody()).isEqualTo("SECURE_NOTICE");
        assertThat(sent.getFooter()).isEqualTo("Riverside Clinic");
        assertThat(sent.getTransmittedBody()).isEqualTo("SECURE_NOTICE\n\nRiverside Clinic");
        assertThat(sent.getEncryptedMessage())
                .isEqualTo("Confidential lab result for the patient.")
                .doesNotContain("Riverside Clinic");
    }

    @Test
    @DisplayName("should reject overlong consent override reasons before sending")
    void shouldRejectConsentOverrideReason_whenLongerThanAuditColumn() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/email/send");
        request.setParameter("method", "sendDirectEmail");
        request.setParameter("message", "Message");
        request.setParameter("isEmailEncrypted", "false");
        request.setParameter("isEmailAttachmentEncrypted", "false");
        request.setParameter("consentOverrideReason", "a".repeat(256));
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());
        when(securityInfoManager.hasPrivilege(any(), any(), any(), any())).thenReturn(true);

        EmailSend2Action action = new EmailSend2Action();
        action.request = request;
        MockHttpServletResponse response = new MockHttpServletResponse();
        action.response = response;

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        assertThat(response.getContentAsString()).contains("must not exceed 255 characters");
        verifyNoInteractions(emailManager);
    }

    @Test
    @DisplayName("should reject an encrypted POST without a token without consuming attachments")
    void shouldRejectEncryptedPostWithoutConsumingAttachments_whenTokenMissing() {
        MockHttpServletRequest request = encryptedSendRequest();
        request.setMethod("POST");
        request.setParameter("method", "sendDirectEmail");
        List<EmailAttachment> attachments = List.of(mock(EmailAttachment.class));
        request.getSession().setAttribute(EmailSessionKeys.EMAIL_ATTACHMENT_LIST, attachments);
        when(securityInfoManager.hasPrivilege(any(), any(), any(), any())).thenReturn(true);

        EmailSend2Action action = spy(new EmailSend2Action());
        doReturn("SECURE_NOTICE").when(action).getText(ENCRYPTED_BODY_NOTICE_KEY);
        action.request = request;
        MockHttpServletResponse response = new MockHttpServletResponse();
        action.response = response;

        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(request.getSession().getAttribute(EmailSessionKeys.EMAIL_ATTACHMENT_LIST)).isSameAs(attachments);
        verifyNoInteractions(emailManager);
    }

    @Test
    @DisplayName("should accept server-owned passphrase without submitted password or clue")
    void shouldAcceptServerOwnedPassphrase_withoutSubmittedSecrets() {
        EmailData sent = captureSentEmail("Confidential note.", "true", "true");
        assertThat(sent.getPassword()).isEqualTo("original-server-passphrase");
        assertThat(sent.getPasswordClue()).isEqualTo("Deliver separately");
    }

    private MockHttpServletRequest encryptedSendRequest() {
        return encryptedSendRequest("Confidential note.");
    }

    private MockHttpServletRequest encryptedSendRequest(String message) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (message != null) {
            request.setParameter("message", message);
        }
        request.setParameter("isEmailEncrypted", "true");
        request.setParameter("senderConfigId", "1");
        request.setParameter("demographicId", "42");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());
        return request;
    }

    /**
     * Drives sendDirectEmail() with the given single "message" field and encryption flags, and
     * returns the EmailData the action handed to EmailManager so routing can be asserted. A null
     * flag omits its parameter entirely, mirroring a direct POST that leaves it out.
     */
    private EmailData captureSentEmail(
            String message, String isEmailEncrypted, String isEmailAttachmentEncrypted) {
        return captureSentEmail(message, isEmailEncrypted, isEmailAttachmentEncrypted, null);
    }

    /** As above, also submitting {@code footerEmail} unless it is null. */
    private EmailData captureSentEmail(
            String message, String isEmailEncrypted, String isEmailAttachmentEncrypted, String footer) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (footer != null) {
            request.setParameter("footerEmail", footer);
        }
        if (message != null) {
            request.setParameter("message", message);
        }
        if (isEmailEncrypted != null) {
            request.setParameter("isEmailEncrypted", isEmailEncrypted);
        }
        if (isEmailAttachmentEncrypted != null) {
            request.setParameter("isEmailAttachmentEncrypted", isEmailAttachmentEncrypted);
        }
        request.setParameter("senderConfigId", "1");
        request.setParameter("demographicId", "42");
        request.getSession().setAttribute(EmailSessionKeys.EMAIL_ATTACHMENT_LIST, List.of(
                new EmailAttachment("result.pdf", "/tmp/result.pdf", DocumentType.DOC, 7)));
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());

        EmailLog emailLog = mock(EmailLog.class);
        when(emailLog.getStatus()).thenReturn(EmailStatus.SUCCESS);
        ArgumentCaptor<EmailData> captor = ArgumentCaptor.forClass(EmailData.class);
        when(emailManager.sendEmailWithResult(any(LoggedInInfo.class), captor.capture())).thenAnswer(invocation -> sendResult(emailLog));

        EmailSend2Action action = spy(new EmailSend2Action());
        // getText() has no live Struts container in a unit test, so stub the notice lookup.
        doReturn("SECURE_NOTICE").when(action).getText(ENCRYPTED_BODY_NOTICE_KEY);
        action.request = request;
        action.response = new MockHttpServletResponse();

        prepareSubmission(request, List.of(
                new EmailAttachment("result.pdf", "/tmp/result.pdf", DocumentType.DOC, 7)));
        action.sendDirectEmail();

        return captor.getValue();
    }
    @Test
    @DisplayName("should make the sent footer the user's usual footer when the box is ticked and the email is accepted")
    void shouldSaveFooterAsMine_whenTickedAndAccepted() {
        EmailFooterService footers = mock(EmailFooterService.class);
        registerMock(EmailFooterService.class, footers);

        MockHttpServletRequest request = sendForFooterSave("true", EmailStatus.SUCCESS);

        verify(footers).saveOwnFooter("101", "<b>Dr A</b>");
        assertThat(request.getAttribute("footerSavedAsMine")).isEqualTo(true);
        assertThat(request.getAttribute("footerSaveAsMineFailed")).isNull();
    }

    @Test
    @DisplayName("should not touch the usual footer when the box is not ticked or the email was not accepted")
    void shouldNotSaveFooterAsMine_whenUntickedOrNotAccepted() {
        EmailFooterService footers = mock(EmailFooterService.class);
        registerMock(EmailFooterService.class, footers);

        sendForFooterSave(null, EmailStatus.SUCCESS);
        MockHttpServletRequest failed = sendForFooterSave("true", EmailStatus.FAILED);

        verifyNoInteractions(footers);
        assertThat(failed.getAttribute("footerSavedAsMine")).isNull();
    }

    @Test
    @DisplayName("should report a footer that could not be saved without failing the email already sent")
    void shouldReportFailure_whenSavingFooterAsMineFails() {
        EmailFooterService footers = mock(EmailFooterService.class);
        doThrow(new IllegalStateException("lock wait")).when(footers).saveOwnFooter(anyString(), anyString());
        registerMock(EmailFooterService.class, footers);

        MockHttpServletRequest request = sendForFooterSave("true", EmailStatus.SUCCESS);

        assertThat(request.getAttribute("isEmailSuccessful")).isEqualTo(true);
        assertThat(request.getAttribute("footerSaveAsMineFailed")).isEqualTo(true);
        assertThat(request.getAttribute("footerSavedAsMine")).isNull();
    }

    /** A direct send by provider 101 with footer {@code <b>Dr A</b>}, the tick box as given. */
    private MockHttpServletRequest sendForFooterSave(String saveFooterAsMine, EmailStatus status) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("footerEmail", "<b>Dr A</b>");
        if (saveFooterAsMine != null) {
            request.setParameter("saveFooterAsMine", saveFooterAsMine);
        }
        request.setParameter("message", "A non-clinical reminder.");
        request.setParameter("isEmailEncrypted", "false");
        request.setParameter("isEmailAttachmentEncrypted", "false");
        request.setParameter("senderConfigId", "1");
        request.setParameter("demographicId", "42");
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        Provider provider = new Provider();
        provider.setProviderNo("101");
        loggedInInfo.setLoggedInProvider(provider);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);

        EmailLog emailLog = mock(EmailLog.class);
        when(emailLog.getStatus()).thenReturn(status);
        when(emailManager.sendEmailWithResult(any(LoggedInInfo.class), any(EmailData.class)))
                .thenAnswer(invocation -> sendResult(emailLog));

        EmailSend2Action action = spy(new EmailSend2Action());
        doReturn("SECURE_NOTICE").when(action).getText(ENCRYPTED_BODY_NOTICE_KEY);
        action.request = request;
        action.response = new MockHttpServletResponse();
        prepareSubmission(request, List.of());
        action.sendDirectEmail();
        return request;
    }

    private EmailSendResult sendResult(EmailLog log) {
        return log.getStatus() == EmailLog.EmailStatus.SUCCESS
                ? EmailSendResult.accepted(log, true) : EmailSendResult.failed(log, true);
    }
}
