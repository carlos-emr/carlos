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
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.email.core.EmailData;
import io.github.carlos_emr.carlos.email.core.EmailSessionKeys;
import io.github.carlos_emr.carlos.managers.EformDataManager;
import io.github.carlos_emr.carlos.managers.EmailManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EmailSend2Action} redirect safety.
 *
 * @since 2026-05-20
 */
@Tag("unit")
@Tag("fast")
@Tag("email")
@DisplayName("EmailSend2Action")
class EmailSend2ActionTest extends CarlosUnitTestBase {

    private static final String ENCRYPTED_BODY_NOTICE_KEY = "email.compose.msg.encryptedBodyNotice";

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private EmailManager emailManager;
    private SecurityInfoManager securityInfoManager;

    @BeforeEach
    void setUp() {
        securityInfoManager = mock(SecurityInfoManager.class);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        emailManager = mock(EmailManager.class);
        registerMock(EmailManager.class, emailManager);
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
    @DisplayName("should encode fdid when cancel redirects to eForm")
    void shouldEncodeFdid_whenCancelRedirectsToEForm() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContextPath("/carlos");
        request.setParameter("transactionType", "EFORM");
        request.setParameter("fdid", "123&parentAjaxId=evil#fragment%25 +/");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());

        MockHttpServletResponse response = new MockHttpServletResponse();
        EmailSend2Action action = spy(new EmailSend2Action());
        // cancel() builds EmailData via prepareEmailFields, which resolves the encrypted-body notice
        // (encryption now fails closed by default). getText() has no live Struts container here.
        doReturn("SECURE_NOTICE").when(action).getText(ENCRYPTED_BODY_NOTICE_KEY);
        action.request = request;
        action.response = response;

        String result = action.cancel();

        assertThat(result).isEqualTo("EFORM");
        assertThat(response.getRedirectedUrl()).isEqualTo(
                "/carlos/eform/efmshowform_data?fdid="
                        + "123%26parentAjaxId%3Devil%23fragment%2525%20%2B%2F&parentAjaxId=eforms");
    }

    @Test
    @DisplayName("should render failed result when direct send has no sender config")
    void shouldRenderFailedResult_whenDirectSendHasNoSenderConfig() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/email/send");
        request.setParameter("method", "sendDirectEmail");
        request.setParameter("receiverEmailAddress", "patient@example.invalid");
        request.setParameter("subjectEmail", "Subject");
        request.setParameter("message", "Body");
        request.setParameter("isEmailEncrypted", "false");
        request.setParameter("patientChartOption", "doNotAddAsNote");
        request.setParameter("transactionType", "DIRECT");
        request.setParameter("demographicId", "123");
        request.getSession().setAttribute(EmailSessionKeys.EMAIL_ATTACHMENT_LIST, List.of());

        LoggedInInfo loggedInInfo = new LoggedInInfo();
        loggedInInfo.setLoggedInProvider(new Provider("999998"));
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);

        EmailLog failedEmailLog = new EmailLog();
        failedEmailLog.setStatus(EmailStatus.FAILED);
        failedEmailLog.setToEmail(new String[] {"patient@example.invalid"});
        failedEmailLog.setErrorMessage("Email sender account is not configured or is inactive.");

        when(securityInfoManager.hasPrivilege(
                any(LoggedInInfo.class), eq("_email"), eq("w"), nullable(String.class)))
                .thenReturn(true);
        when(emailManager.sendEmail(eq(loggedInInfo), any(EmailData.class))).thenReturn(failedEmailLog);

        EmailSend2Action action = new EmailSend2Action();
        action.request = request;
        action.response = new MockHttpServletResponse();

        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(request.getAttribute("isEmailSuccessful")).isEqualTo(false);
        assertThat(request.getAttribute("emailLog")).isSameAs(failedEmailLog);

        ArgumentCaptor<EmailData> emailDataCaptor = ArgumentCaptor.forClass(EmailData.class);
        verify(emailManager).sendEmail(eq(loggedInInfo), emailDataCaptor.capture());
        assertThat(emailDataCaptor.getValue().getSenderConfigId()).isNull();
    }

    @Test
    @DisplayName("should route the single message into the encrypted PDF when encryption is on")
    void shouldRouteMessageToEncryptedPdf_whenEncryptionOn() {
        EmailData sent = captureSentEmail("Confidential lab result for the patient.", "true");

        // The clinical content goes into the encrypted-PDF channel; the visible cleartext body
        // is only the fixed, PHI-free notice.
        assertThat(sent.getEncryptedMessage()).isEqualTo("Confidential lab result for the patient.");
        assertThat(sent.getBody()).isEqualTo("SECURE_NOTICE");
        assertThat(sent.getIsEncrypted()).isTrue();
    }

    @Test
    @DisplayName("should route the single message into the cleartext body when encryption is off")
    void shouldRouteMessageToCleartextBody_whenEncryptionOff() {
        EmailData sent = captureSentEmail("A non-clinical reminder.", "false");

        // The message is sent as the cleartext body; the encrypted-PDF channel stays empty so the
        // client can never populate both at once.
        assertThat(sent.getBody()).isEqualTo("A non-clinical reminder.");
        assertThat(sent.getEncryptedMessage()).isEmpty();
        assertThat(sent.getIsEncrypted()).isFalse();
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
        assertThat(request.getSession().getAttribute(EmailSessionKeys.EMAIL_ATTACHMENT_LIST))
                .isSameAs(attachments);
        verifyNoInteractions(emailManager);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"unknown"})
    @DisplayName("should reject missing or unknown POST operations before sending email")
    void shouldRejectMissingOrUnknownOperation_beforeSendingEmail(String actionMethod) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/email/send");
        if (actionMethod != null) {
            request.setParameter("method", actionMethod);
        }
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
    @DisplayName("should encrypt by default when the encryption flag is missing")
    void shouldEncryptByDefault_whenEncryptionFlagMissing() {
        // Fail closed: a direct/malformed POST that omits the isEmailEncrypted toggle must route the
        // message into the encrypted-PDF channel, never the cleartext body.
        EmailData sent = captureSentEmail("Confidential note.", null);

        assertThat(sent.getEncryptedMessage()).isEqualTo("Confidential note.");
        assertThat(sent.getBody()).isEqualTo("SECURE_NOTICE");
        assertThat(sent.getIsEncrypted()).isTrue();
    }

    @Test
    @DisplayName("should preserve the complete retry model and original attachments when delivery fails")
    void shouldPreserveCompleteRetryModelAndOriginalAttachments_whenDeliveryFails() {
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
        request.setParameter("emailConsentStatus", "OPT_IN");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());

        EmailAttachment originalAttachment = new EmailAttachment(
                "result.pdf", "/tmp/original-result.pdf", DocumentType.DOC, 7, 123L);
        originalAttachment.setPreviewToken("preview-token");
        List<EmailAttachment> originalAttachments = List.of(originalAttachment);
        request.getSession().setAttribute(EmailSessionKeys.EMAIL_ATTACHMENT_LIST, originalAttachments);

        EmailLog emailLog = mock(EmailLog.class);
        EmailConfig emailConfig = mock(EmailConfig.class);
        when(emailLog.getStatus()).thenReturn(EmailStatus.FAILED);
        when(emailLog.getConsentStatus()).thenReturn(EmailConsentStatus.OPT_OUT);
        when(emailLog.getEmailConfig()).thenReturn(emailConfig);
        when(emailLog.getFromEmail()).thenReturn("clinic@example.test");
        ArgumentCaptor<EmailData> sentEmail = ArgumentCaptor.forClass(EmailData.class);
        when(emailManager.sendEmail(any(LoggedInInfo.class), sentEmail.capture())).thenAnswer(invocation -> {
            EmailData emailData = invocation.getArgument(1);
            emailData.getAttachments().get(0).setFilePath("/tmp/encrypted-result.pdf");
            return emailLog;
        });

        EmailSend2Action action = spy(new EmailSend2Action());
        doReturn("SECURE_NOTICE").when(action).getText(ENCRYPTED_BODY_NOTICE_KEY);
        action.request = request;
        action.response = new MockHttpServletResponse();

        action.sendDirectEmail();

        assertThat(request.getAttribute("message")).isEqualTo("Draft to retry.");
        assertThat(request.getAttribute("isEmailEncrypted")).isEqualTo(true);
        assertThat(request.getAttribute("isEmailAttachmentEncrypted")).isEqualTo(true);
        assertThat(request.getAttribute("subjectEmail")).isEqualTo("Retry subject");
        assertThat(request.getAttribute("emailConsentStatus")).isEqualTo("OPT_OUT");
        assertThat(request.getAttribute("emailConsentMessageKey"))
                .isEqualTo("email.consent.status.optOut");
        assertThat(request.getAttribute("receiverEmailList"))
                .isEqualTo(List.of("patient@example.test"));
        assertThat(request.getAttribute("senderAccounts")).isEqualTo(List.of(emailConfig));
        assertThat(request.getSession().getAttribute(EmailSessionKeys.EMAIL_ATTACHMENT_LIST))
                .isSameAs(originalAttachments);
        assertThat(originalAttachment.getFilePath()).isEqualTo("/tmp/original-result.pdf");
        assertThat(sentEmail.getValue().getAttachments().get(0)).isNotSameAs(originalAttachment);
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
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());

        EmailLog emailLog = mock(EmailLog.class);
        when(emailLog.getStatus()).thenReturn(EmailStatus.FAILED);
        when(emailManager.sendEmail(any(LoggedInInfo.class), any(EmailData.class))).thenReturn(emailLog);

        EmailSend2Action action = spy(new EmailSend2Action());
        doReturn("SECURE_NOTICE").when(action).getText(ENCRYPTED_BODY_NOTICE_KEY);
        action.request = request;
        action.response = new MockHttpServletResponse();

        action.sendDirectEmail();

        // Fail closed: a missing toggle must re-render ON so a retry stays encrypted, matching the
        // fail-closed send-side routing.
        assertThat(request.getAttribute("isEmailEncrypted")).isEqualTo(true);
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
        when(emailManager.sendEmail(any(LoggedInInfo.class), any(EmailData.class))).thenReturn(emailLog);

        EmailSend2Action action = spy(new EmailSend2Action());
        doReturn("SECURE_NOTICE").when(action).getText(ENCRYPTED_BODY_NOTICE_KEY);
        action.request = request;
        action.response = new MockHttpServletResponse();

        action.sendDirectEmail();

        assertThat(request.getSession().getAttribute(EmailSessionKeys.EMAIL_ATTACHMENT_LIST)).isNull();
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
    @DisplayName("should reject overlong consent override reasons before sending")
    void shouldRejectConsentOverrideReason_whenLongerThanAuditColumn() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/email/send");
        request.setParameter("method", "sendDirectEmail");
        request.setParameter("message", "Message");
        request.setParameter("isEmailEncrypted", "false");
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
    @DisplayName("should reject an invalid encrypted POST without consuming attachments")
    void shouldRejectEncryptedPostWithoutConsumingAttachments_whenPasswordMissing() {
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

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        assertThat(request.getSession().getAttribute(EmailSessionKeys.EMAIL_ATTACHMENT_LIST))
                .isSameAs(attachments);
        verifyNoInteractions(emailManager);
    }

    @Test
    @DisplayName("should reject encrypted sends without a PDF password clue")
    void shouldRejectEncryptedSend_whenPasswordClueMissing() {
        MockHttpServletRequest request = encryptedSendRequest();
        request.setParameter("emailPDFPassword", "valid-password");

        EmailSend2Action action = spy(new EmailSend2Action());
        doReturn("SECURE_NOTICE").when(action).getText(ENCRYPTED_BODY_NOTICE_KEY);
        action.request = request;
        action.response = new MockHttpServletResponse();

        assertThatThrownBy(action::sendDirectEmail)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A PDF password clue is required for encrypted email");
        verifyNoInteractions(emailManager);
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
     * Drives sendDirectEmail() with the given single "message" field and encryption flag, and
     * returns the EmailData the action handed to EmailManager so routing can be asserted. A null
     * {@code isEmailEncrypted} omits that parameter entirely, mirroring a direct POST that leaves it
     * out.
     */
    private EmailData captureSentEmail(String message, String isEmailEncrypted) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (message != null) {
            request.setParameter("message", message);
        }
        if (isEmailEncrypted != null) {
            request.setParameter("isEmailEncrypted", isEmailEncrypted);
        }
        if (!"false".equals(isEmailEncrypted)) {
            request.setParameter("emailPDFPassword", "valid-password");
            request.setParameter("emailPDFPasswordClue", "Known to the patient");
        }
        request.setParameter("senderConfigId", "1");
        request.setParameter("demographicId", "42");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());

        EmailLog emailLog = mock(EmailLog.class);
        when(emailLog.getStatus()).thenReturn(EmailStatus.SUCCESS);
        ArgumentCaptor<EmailData> captor = ArgumentCaptor.forClass(EmailData.class);
        when(emailManager.sendEmail(any(LoggedInInfo.class), captor.capture())).thenReturn(emailLog);

        EmailSend2Action action = spy(new EmailSend2Action());
        // getText() has no live Struts container in a unit test, so stub the notice lookup.
        doReturn("SECURE_NOTICE").when(action).getText(ENCRYPTED_BODY_NOTICE_KEY);
        action.request = request;
        action.response = new MockHttpServletResponse();

        action.sendDirectEmail();

        return captor.getValue();
    }
}
