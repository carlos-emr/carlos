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
import io.github.carlos_emr.carlos.email.core.EmailComposeSubmissionStateService;
import io.github.carlos_emr.carlos.email.core.EmailData;
import io.github.carlos_emr.carlos.email.core.EmailPdfPasswordService;
import io.github.carlos_emr.carlos.managers.EformDataManager;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.EmailComposeManager;
import io.github.carlos_emr.carlos.managers.EmailManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
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
    private static final String EXAMPLE_GENERATED_VALUE = "example-generated-value";
    private static final String EXAMPLE_ATTACHMENT_VALUE = "example-attachment-value";
    private static final String EXAMPLE_FIRST_VALUE = "example-first-value";
    private static final String EXAMPLE_SECOND_VALUE = "example-second-value";
    private static final String EXAMPLE_SINGLE_USE_VALUE = "example-single-use-value";

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private EmailComposeSubmissionStateService composeSubmissionStateService;

    @BeforeEach
    void setUp() {
        registerMock(SecurityInfoManager.class, mock(SecurityInfoManager.class));
        registerMock(EmailManager.class, mock(EmailManager.class));
        registerMock(EformDataManager.class, mock(EformDataManager.class));
        registerMock(EmailPdfPasswordService.class, mock(EmailPdfPasswordService.class));
        composeSubmissionStateService = new EmailComposeSubmissionStateService();
        registerMock(EmailComposeSubmissionStateService.class, composeSubmissionStateService);
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
        if (composeSubmissionStateService != null) {
            composeSubmissionStateService.shutdown();
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
    @DisplayName("should show compose state error when token is missing during send")
    void shouldShowComposeStateError_whenTokenMissingDuringSend() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("isEmailEncrypted", "true");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());

        MockHttpServletResponse response = new MockHttpServletResponse();
        EmailSend2Action action = new EmailSend2Action();
        action.request = request;
        action.response = response;

        String result = action.sendDirectEmail();

        assertThat(result).isEqualTo("success");
        assertThat(request.getAttribute("isEmailError")).isEqualTo(true);
        assertThat(request.getAttribute("isEmailComposeStateError")).isEqualTo(true);
        assertThat(request.getAttribute("emailErrorMessage"))
                .isEqualTo(EmailCompose2Action.EMAIL_COMPOSE_STATE_EXPIRED_MESSAGE);
        assertThat(request.getAttribute("emailAttachmentList")).isEqualTo(List.of());
    }

    @Test
    @DisplayName("should ignore submitted PDF password and use generated compose-state password")
    void shouldIgnorePassword_whenSubmittedPdfPasswordIsTampered() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("senderConfigId", "1");
        request.setParameter("receiverEmailAddress", "patient@example.com");
        request.setParameter("subjectEmail", "Subject");
        request.setParameter("bodyEmail", "Body");
        request.setParameter("encryptedMessage", "Encrypted message");
        request.setParameter("emailPDFPassword", "example-submitted-value");
        request.setParameter("emailPDFPasswordClue", "example delivery note");
        request.setParameter("isEmailEncrypted", "true");
        request.setParameter("isEmailAttachmentEncrypted", "false");
        request.setParameter("patientChartOption", "addFullNote");
        request.setParameter("transactionType", "DIRECT");
        request.setParameter("demographicId", "123");
        setComposeToken(request, EXAMPLE_GENERATED_VALUE);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());

        MockHttpServletResponse response = new MockHttpServletResponse();
        EmailSend2Action action = new EmailSend2Action();
        action.request = request;
        action.response = response;

        EmailData emailData = ReflectionTestUtils.invokeMethod(action, "prepareEmailFields", request);

        assertThat(emailData.getPassword()).isEqualTo(EXAMPLE_GENERATED_VALUE);
        assertThat(emailData.getPasswordClue())
                .isEqualTo(EmailCompose2Action.DEFAULT_EMAIL_PDF_PASSWORD_DELIVERY_INSTRUCTION);
        assertThat(request.getSession().getAttribute("emailPDFPassword")).isNull();
        assertThat(request.getSession().getAttribute("emailPDFPasswordClue")).isNull();
    }

    @Test
    @DisplayName("should use generated compose-state password when attachments are encrypted")
    void shouldUseGeneratedComposeStatePassword_whenAttachmentsEncrypted() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("senderConfigId", "1");
        request.setParameter("receiverEmailAddress", "patient@example.com");
        request.setParameter("subjectEmail", "Subject");
        request.setParameter("bodyEmail", "Body");
        request.setParameter("encryptedMessage", "");
        request.setParameter("emailPDFPassword", "example-submitted-value");
        request.setParameter("emailPDFPasswordClue", "example delivery note");
        request.setParameter("isEmailEncrypted", "true");
        request.setParameter("isEmailAttachmentEncrypted", "true");
        request.setParameter("patientChartOption", "addFullNote");
        request.setParameter("transactionType", "DIRECT");
        request.setParameter("demographicId", "123");
        setComposeToken(
                request,
                EXAMPLE_ATTACHMENT_VALUE,
                List.of(new EmailAttachment("lab.pdf", "/tmp/lab.pdf", DocumentType.LAB, 1)));
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());

        MockHttpServletResponse response = new MockHttpServletResponse();
        EmailSend2Action action = new EmailSend2Action();
        action.request = request;
        action.response = response;

        EmailData emailData = ReflectionTestUtils.invokeMethod(action, "prepareEmailFields", request);

        assertThat(emailData.getPassword()).isEqualTo(EXAMPLE_ATTACHMENT_VALUE);
        assertThat(emailData.getPasswordClue())
                .isEqualTo(EmailCompose2Action.DEFAULT_EMAIL_PDF_PASSWORD_DELIVERY_INSTRUCTION);
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
        request.setParameter("emailPDFPassword", "example-submitted-value");
        request.setParameter("emailPDFPasswordClue", "example delivery note");
        request.setParameter("isEmailEncrypted", "false");
        request.setParameter("isEmailAttachmentEncrypted", "true");
        request.setParameter("patientChartOption", "addFullNote");
        request.setParameter("transactionType", "DIRECT");
        request.setParameter("demographicId", "123");
        setComposeToken(
                request,
                EXAMPLE_ATTACHMENT_VALUE,
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
    @DisplayName("should use generated compose token password when encryption is enabled after compose opens")
    void shouldUseGeneratedPassword_whenEncryptionEnabledAfterComposeOpensUnencrypted() throws Exception {
        DemographicManager demographicManager = mock(DemographicManager.class);
        EmailComposeManager emailComposeManager = mock(EmailComposeManager.class);
        EmailPdfPasswordService emailPdfPasswordService = mock(EmailPdfPasswordService.class);
        registerMock(DemographicManager.class, demographicManager);
        registerMock(EmailComposeManager.class, emailComposeManager);
        registerMock(EmailPdfPasswordService.class, emailPdfPasswordService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/email/compose");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.getSession(true).setAttribute("demographicId", "123");
        request.getSession(false).setAttribute("isEmailEncrypted", false);
        when(emailComposeManager.getEmailConsentStatus(any(), anyInt())).thenReturn(new String[]{"Consent", "Yes"});
        when(demographicManager.getDemographicFormattedName(any(), anyInt())).thenReturn("Patient One");
        when(emailComposeManager.getRecipients(any(), anyInt()))
                .thenReturn(new List<?>[]{List.of("patient@example.com"), List.of()});
        when(emailComposeManager.getAllSenderAccounts()).thenReturn(List.of());
        when(emailComposeManager.prepareEFormAttachments(any(), any(), any())).thenReturn(List.of());
        when(emailComposeManager.prepareEDocAttachments(any(), any())).thenReturn(List.of());
        when(emailComposeManager.prepareLabAttachments(any(), any())).thenReturn(List.of());
        when(emailComposeManager.prepareHRMAttachments(any(), any())).thenReturn(List.of());
        when(emailComposeManager.prepareFormAttachments(any(), any(), any(), anyInt())).thenReturn(List.of());
        when(emailPdfPasswordService.generatePassphrase()).thenReturn(EXAMPLE_GENERATED_VALUE);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        EmailCompose2Action composeAction = new EmailCompose2Action();

        assertThat(composeAction.prepareComposeEFormMailer()).isEqualTo("compose");
        String token = (String) request.getAttribute(EmailCompose2Action.EMAIL_PDF_PASSWORD_TOKEN_PARAM);
        assertThat(token).isNotBlank();
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
        request.setParameter(EmailCompose2Action.EMAIL_PDF_PASSWORD_TOKEN_PARAM, token);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());
        EmailSend2Action sendAction = new EmailSend2Action();
        sendAction.request = request;
        sendAction.response = response;

        EmailData emailData = ReflectionTestUtils.invokeMethod(sendAction, "prepareEmailFields", request);

        assertThat(emailData.getPassword()).isEqualTo(EXAMPLE_GENERATED_VALUE);
        assertThat(emailData.getIsEncrypted()).isTrue();
    }

    @Test
    @DisplayName("should fail when encrypted email is missing generated compose-state password")
    void shouldFail_whenEncryptedPdfPasswordMissingFromComposeState() {
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
                .isInstanceOf(RuntimeException.class)
                .hasMessage(EmailCompose2Action.EMAIL_COMPOSE_STATE_EXPIRED_MESSAGE);
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
        String firstToken = composeSubmissionStateService.store(
                request.getSession(),
                EXAMPLE_FIRST_VALUE,
                EmailCompose2Action.DEFAULT_EMAIL_PDF_PASSWORD_DELIVERY_INSTRUCTION,
                List.of());
        composeSubmissionStateService.store(
                request.getSession(),
                EXAMPLE_SECOND_VALUE,
                EmailCompose2Action.DEFAULT_EMAIL_PDF_PASSWORD_DELIVERY_INSTRUCTION,
                List.of());
        request.setParameter(EmailCompose2Action.EMAIL_PDF_PASSWORD_TOKEN_PARAM, firstToken);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());

        MockHttpServletResponse response = new MockHttpServletResponse();
        EmailSend2Action action = new EmailSend2Action();
        action.request = request;
        action.response = response;

        EmailData emailData = ReflectionTestUtils.invokeMethod(action, "prepareEmailFields", request);

        assertThat(emailData.getPassword()).isEqualTo(EXAMPLE_FIRST_VALUE);
        composeSubmissionStateService.clear(request.getSession().getId());
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
        setComposeToken(request, EXAMPLE_SINGLE_USE_VALUE);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());

        MockHttpServletResponse response = new MockHttpServletResponse();
        EmailSend2Action action = new EmailSend2Action();
        action.request = request;
        action.response = response;

        ReflectionTestUtils.invokeMethod(action, "prepareEmailFields", request);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(action, "prepareEmailFields", request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage(EmailCompose2Action.EMAIL_COMPOSE_STATE_EXPIRED_MESSAGE);
    }

    private void setComposeToken(MockHttpServletRequest request, String emailPDFPassword) {
        setComposeToken(request, emailPDFPassword, List.of());
    }

    private void setComposeToken(
            MockHttpServletRequest request,
            String emailPDFPassword,
            List<EmailAttachment> emailAttachmentList
    ) {
        String token = composeSubmissionStateService.store(
                request.getSession(),
                emailPDFPassword,
                EmailCompose2Action.DEFAULT_EMAIL_PDF_PASSWORD_DELIVERY_INSTRUCTION,
                emailAttachmentList);
        request.setParameter(EmailCompose2Action.EMAIL_PDF_PASSWORD_TOKEN_PARAM, token);
    }
}
