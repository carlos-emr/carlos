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

import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import io.github.carlos_emr.carlos.commn.model.EmailAttachment;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.email.core.EmailData;
import io.github.carlos_emr.carlos.email.core.EmailPdfPasswordService;
import io.github.carlos_emr.carlos.managers.EformDataManager;
import io.github.carlos_emr.carlos.managers.EmailManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

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

    private MockedStatic<ServletActionContext> servletActionContextMock;

    @BeforeEach
    void setUp() {
        registerMock(SecurityInfoManager.class, mock(SecurityInfoManager.class));
        registerMock(EmailManager.class, mock(EmailManager.class));
        registerMock(EformDataManager.class, mock(EformDataManager.class));
        registerMock(EmailPdfPasswordService.class, mock(EmailPdfPasswordService.class));
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
        EmailSend2Action action = new EmailSend2Action();
        action.request = request;
        action.response = response;

        String result = action.cancel();

        assertThat(result).isEqualTo("EFORM");
        assertThat(response.getRedirectedUrl()).isEqualTo(
                "/carlos/eform/efmshowform_data?fdid="
                        + "123%26parentAjaxId%3Devil%23fragment%2525%20%2B%2F&parentAjaxId=eforms");
    }

    @Test
    @DisplayName("should ignore submitted PDF password and use generated session password")
    void shouldIgnorePassword_whenSubmittedPdfPasswordIsTampered() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("senderConfigId", "1");
        request.setParameter("receiverEmailAddress", "patient@example.com");
        request.setParameter("subjectEmail", "Subject");
        request.setParameter("bodyEmail", "Body");
        request.setParameter("encryptedMessage", "Encrypted message");
        request.setParameter("emailPDFPassword", "patient-chosen-or-tampered");
        request.setParameter("emailPDFPasswordClue", "tampered clue");
        request.setParameter("isEmailEncrypted", "true");
        request.setParameter("isEmailAttachmentEncrypted", "false");
        request.setParameter("patientChartOption", "addFullNote");
        request.setParameter("transactionType", "DIRECT");
        request.setParameter("demographicId", "123");
        setComposeToken(request, "alpha-bravo-charlie-delta-echo-foxtrot");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());

        MockHttpServletResponse response = new MockHttpServletResponse();
        EmailSend2Action action = new EmailSend2Action();
        action.request = request;
        action.response = response;

        EmailData emailData = ReflectionTestUtils.invokeMethod(action, "prepareEmailFields", request);

        assertThat(emailData.getPassword()).isEqualTo("alpha-bravo-charlie-delta-echo-foxtrot");
        assertThat(emailData.getPasswordClue()).isEqualTo(EmailPdfPasswordService.DELIVERY_INSTRUCTION);
        assertThat(request.getSession().getAttribute("emailPDFPassword")).isNull();
        assertThat(request.getSession().getAttribute("emailPDFPasswordClue")).isNull();
    }

    @Test
    @DisplayName("should use generated session password when attachments are encrypted")
    void shouldUseGeneratedSessionPassword_whenAttachmentsEncrypted() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("senderConfigId", "1");
        request.setParameter("receiverEmailAddress", "patient@example.com");
        request.setParameter("subjectEmail", "Subject");
        request.setParameter("bodyEmail", "Body");
        request.setParameter("encryptedMessage", "");
        request.setParameter("emailPDFPassword", "tampered-password");
        request.setParameter("emailPDFPasswordClue", "tampered clue");
        request.setParameter("isEmailEncrypted", "true");
        request.setParameter("isEmailAttachmentEncrypted", "true");
        request.setParameter("patientChartOption", "addFullNote");
        request.setParameter("transactionType", "DIRECT");
        request.setParameter("demographicId", "123");
        setComposeToken(
                request,
                "cedar-maple-spruce-birch-aspen-willow",
                List.of(new EmailAttachment("lab.pdf", "/tmp/lab.pdf", DocumentType.LAB, 1)));
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());

        MockHttpServletResponse response = new MockHttpServletResponse();
        EmailSend2Action action = new EmailSend2Action();
        action.request = request;
        action.response = response;

        EmailData emailData = ReflectionTestUtils.invokeMethod(action, "prepareEmailFields", request);

        assertThat(emailData.getPassword()).isEqualTo("cedar-maple-spruce-birch-aspen-willow");
        assertThat(emailData.getPasswordClue()).isEqualTo(EmailPdfPasswordService.DELIVERY_INSTRUCTION);
        assertThat(emailData.getIsEncrypted()).isTrue();
        assertThat(emailData.getIsAttachmentEncrypted()).isTrue();
        assertThat(emailData.getAttachments()).hasSize(1);
        assertThat(request.getSession().getAttribute("emailPDFPassword")).isNull();
        assertThat(request.getSession().getAttribute("emailPDFPasswordClue")).isNull();
    }

    @Test
    @DisplayName("should ignore stale attachment encryption when email encryption is disabled")
    void shouldIgnoreAttachmentEncryption_whenEmailEncryptionDisabled() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("senderConfigId", "1");
        request.setParameter("receiverEmailAddress", "patient@example.com");
        request.setParameter("subjectEmail", "Subject");
        request.setParameter("bodyEmail", "Body");
        request.setParameter("encryptedMessage", "");
        request.setParameter("emailPDFPassword", "tampered-password");
        request.setParameter("emailPDFPasswordClue", "tampered clue");
        request.setParameter("isEmailEncrypted", "false");
        request.setParameter("isEmailAttachmentEncrypted", "true");
        request.setParameter("patientChartOption", "addFullNote");
        request.setParameter("transactionType", "DIRECT");
        request.setParameter("demographicId", "123");
        setComposeToken(
                request,
                "cedar-maple-spruce-birch-aspen-willow",
                List.of(new EmailAttachment("lab.pdf", "/tmp/lab.pdf", DocumentType.LAB, 1)));
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());

        MockHttpServletResponse response = new MockHttpServletResponse();
        EmailSend2Action action = new EmailSend2Action();
        action.request = request;
        action.response = response;

        EmailData emailData = ReflectionTestUtils.invokeMethod(action, "prepareEmailFields", request);

        assertThat(emailData.getPassword()).isEmpty();
        assertThat(emailData.getPasswordClue()).isEmpty();
        assertThat(emailData.getIsEncrypted()).isFalse();
        assertThat(emailData.getIsAttachmentEncrypted()).isFalse();
        assertThat(emailData.getAttachments()).hasSize(1);
    }

    @Test
    @DisplayName("should fail when encrypted email is missing generated session password")
    void shouldFail_whenEncryptedPdfPasswordMissingFromSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("senderConfigId", "1");
        request.setParameter("receiverEmailAddress", "patient@example.com");
        request.setParameter("subjectEmail", "Subject");
        request.setParameter("bodyEmail", "Body");
        request.setParameter("encryptedMessage", "Encrypted message");
        request.setParameter("isEmailEncrypted", "true");
        request.setParameter("isEmailAttachmentEncrypted", "false");
        request.setParameter("patientChartOption", "addFullNote");
        request.setParameter("transactionType", "DIRECT");
        request.setParameter("demographicId", "123");
        setComposeToken(request, "");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());

        MockHttpServletResponse response = new MockHttpServletResponse();
        EmailSend2Action action = new EmailSend2Action();
        action.request = request;
        action.response = response;

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(action, "prepareEmailFields", request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Email PDF password is missing from session");
    }

    @Test
    @DisplayName("should use passphrase bound to submitted compose token")
    void shouldUsePassphrase_whenBoundToSubmittedComposeToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("senderConfigId", "1");
        request.setParameter("receiverEmailAddress", "patient@example.com");
        request.setParameter("subjectEmail", "Subject");
        request.setParameter("bodyEmail", "Body");
        request.setParameter("encryptedMessage", "Encrypted message");
        request.setParameter("isEmailEncrypted", "true");
        request.setParameter("isEmailAttachmentEncrypted", "false");
        request.setParameter("patientChartOption", "addFullNote");
        request.setParameter("transactionType", "DIRECT");
        request.setParameter("demographicId", "123");
        String firstToken = EmailCompose2Action.storeEmailComposeSubmissionState(
                request,
                "first-token-password",
                EmailPdfPasswordService.DELIVERY_INSTRUCTION,
                List.of());
        String secondToken = EmailCompose2Action.storeEmailComposeSubmissionState(
                request,
                "second-token-password",
                EmailPdfPasswordService.DELIVERY_INSTRUCTION,
                List.of());
        request.setParameter(EmailCompose2Action.EMAIL_PDF_PASSWORD_TOKEN_PARAM, firstToken);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());

        MockHttpServletResponse response = new MockHttpServletResponse();
        EmailSend2Action action = new EmailSend2Action();
        action.request = request;
        action.response = response;

        EmailData emailData = ReflectionTestUtils.invokeMethod(action, "prepareEmailFields", request);

        assertThat(emailData.getPassword()).isEqualTo("first-token-password");
        request.setParameter(EmailCompose2Action.EMAIL_PDF_PASSWORD_TOKEN_PARAM, secondToken);
        EmailCompose2Action.consumeEmailComposeSubmissionState(request);
    }

    @Test
    @DisplayName("should consume compose token once")
    void shouldConsumeComposeToken_onFirstUseOnly() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("senderConfigId", "1");
        request.setParameter("receiverEmailAddress", "patient@example.com");
        request.setParameter("subjectEmail", "Subject");
        request.setParameter("bodyEmail", "Body");
        request.setParameter("encryptedMessage", "Encrypted message");
        request.setParameter("isEmailEncrypted", "true");
        request.setParameter("isEmailAttachmentEncrypted", "false");
        request.setParameter("patientChartOption", "addFullNote");
        request.setParameter("transactionType", "DIRECT");
        request.setParameter("demographicId", "123");
        setComposeToken(request, "single-use-password");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());

        MockHttpServletResponse response = new MockHttpServletResponse();
        EmailSend2Action action = new EmailSend2Action();
        action.request = request;
        action.response = response;

        ReflectionTestUtils.invokeMethod(action, "prepareEmailFields", request);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(action, "prepareEmailFields", request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Email compose session is missing or expired");
    }

    private static void setComposeToken(MockHttpServletRequest request, String emailPDFPassword) {
        setComposeToken(request, emailPDFPassword, List.of());
    }

    private static void setComposeToken(
            MockHttpServletRequest request,
            String emailPDFPassword,
            List<EmailAttachment> emailAttachmentList
    ) {
        String token = EmailCompose2Action.storeEmailComposeSubmissionState(
                request,
                emailPDFPassword,
                EmailPdfPasswordService.DELIVERY_INSTRUCTION,
                emailAttachmentList);
        request.setParameter(EmailCompose2Action.EMAIL_PDF_PASSWORD_TOKEN_PARAM, token);
    }
}
