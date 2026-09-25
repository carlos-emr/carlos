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

import java.util.ArrayList;
import java.util.List;

import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.github.carlos_emr.carlos.commn.model.EmailAttachment;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.email.core.EmailData;
import io.github.carlos_emr.carlos.email.core.EmailFieldLengthException;
import io.github.carlos_emr.carlos.email.core.EmailFieldLengthValidator;
import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.commn.model.EmailLog.TransactionType;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.EformDataManager;
import io.github.carlos_emr.carlos.managers.EmailComposeManager;
import io.github.carlos_emr.carlos.managers.EmailManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.ArgumentMatchers;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EmailSend2Action} redirect safety and over-length field rejection.
 *
 * @since 2026-05-20
 */
@Tag("unit")
@Tag("fast")
@Tag("email")
@DisplayName("EmailSend2Action")
class EmailSend2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private EmailManager emailManager;
    private EformDataManager eformDataManager;
    private EmailComposeManager emailComposeManager;
    private DemographicManager demographicManager;

    @BeforeEach
    void setUp() {
        emailManager = mock(EmailManager.class);
        eformDataManager = mock(EformDataManager.class);
        emailComposeManager = mock(EmailComposeManager.class);
        demographicManager = mock(DemographicManager.class);
        registerMock(SecurityInfoManager.class, mock(SecurityInfoManager.class));
        registerMock(EmailManager.class, emailManager);
        registerMock(EformDataManager.class, eformDataManager);
        registerMock(EmailComposeManager.class, emailComposeManager);
        registerMock(DemographicManager.class, demographicManager);
        when(emailComposeManager.getEmailConsentStatus(any(), any())).thenReturn(new String[]{"", "Unknown"});
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

    private EmailSend2Action overLengthAction(MockHttpServletRequest request) {
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());
        List<EmailFieldLengthValidator.Violation> violations = EmailFieldLengthValidator.validate(overLengthSubject());
        when(emailManager.sendEmail(any(), any())).thenThrow(new EmailFieldLengthException(violations));
        EmailSend2Action action = new EmailSend2Action();
        action.request = request;
        action.response = new MockHttpServletResponse();
        return action;
    }

    private static EmailData overLengthSubject() {
        EmailData data = new EmailData();
        data.setSubject("s".repeat(EmailFieldLengthValidator.MAX_SUBJECT_CHARS + 1));
        return data;
    }

    @Test
    @DisplayName("should show length errors and keep the eForm when a field is too long")
    void shouldRejectWithoutDeletingEForm_whenFieldTooLong() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("transactionType", "EFORM");
        request.setParameter("fdid", "42");
        request.setParameter("deleteEFormAfterEmail", "true");
        request.setParameter("openEFormAfterEmail", "true");
        EmailSend2Action action = overLengthAction(request);

        String result = action.sendEFormEmail();

        assertThat(result).isEqualTo("success");
        // Unset, so the page renders the editable form rather than the sent/failed result panel.
        assertThat(request.getAttribute("isEmailSuccessful")).isNull();
        assertThat(request.getAttribute("fdid")).isEqualTo("42");
        assertThat(request.getAttribute("deleteEFormAfterEmail")).isEqualTo(true);
        assertThat(request.getAttribute("openEFormAfterEmail")).isEqualTo(true);
        assertThat((List<?>) request.getAttribute("emailLengthViolations"))
                .extracting("messageKey")
                .containsExactly("email.compose.msg.subjectTooLong");
        verifyNoInteractions(eformDataManager);
    }

    @Test
    @DisplayName("should show length errors for a direct email when a field is too long")
    void shouldRejectDirectEmail_whenFieldTooLong() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("transactionType", "DIRECT");
        EmailSend2Action action = overLengthAction(request);

        String result = action.sendDirectEmail();

        assertThat(result).isEqualTo("success");
        assertThat(request.getAttribute("isEmailSuccessful")).isNull();
        assertThat(request.getAttribute("emailLengthViolations")).isNotNull();
        assertThat(request.getAttribute("emailLog")).isNull();
        assertThat(request.getAttribute("transactionType")).isEqualTo(TransactionType.DIRECT);
    }

    @Test
    @DisplayName("should keep the prepared attachments for a retry when a field is too long")
    void shouldRestoreAttachments_whenFieldTooLong() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("transactionType", "EFORM");
        request.setParameter("fdid", "42");
        request.setParameter("demographicId", "7");
        List<EmailAttachment> attachments = new ArrayList<>(List.of(new EmailAttachment()));
        request.getSession().setAttribute(EmailSend2Action.ATTACHMENT_LIST_SESSION_KEY, attachments);
        request.getSession().setAttribute(EmailSend2Action.ATTACHMENT_OWNER_SESSION_KEY, "7");
        EmailSend2Action action = overLengthAction(request);

        action.sendEFormEmail();

        assertThat(request.getSession().getAttribute(EmailSend2Action.ATTACHMENT_LIST_SESSION_KEY)).isSameAs(attachments);
        assertThat(request.getSession().getAttribute(EmailSend2Action.ATTACHMENT_OWNER_SESSION_KEY)).isEqualTo("7");
        // The retry form lists them too.
        assertThat(request.getAttribute(EmailSend2Action.ATTACHMENT_LIST_SESSION_KEY)).isSameAs(attachments);
    }

    @Test
    @DisplayName("should hand every entered field back to the compose page when a field is too long")
    @SuppressWarnings("unchecked")
    void shouldRestoreComposeFields_whenFieldTooLong() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("transactionType", "EFORM");
        request.setParameter("demographicId", "7");
        request.setParameter("fdid", "42");
        request.setParameter("fid", "3");
        request.setParameter("senderConfigId", "5");
        request.setParameter("receiverEmailAddress", "a@example.com", "b@example.com");
        request.setParameter("subjectEmail", "Subject");
        request.setParameter("bodyEmail", "Body");
        request.setParameter("encryptedMessage", "Secret");
        request.setParameter("emailPDFPassword", "pass12345");
        request.setParameter("emailPDFPasswordClue", "Clue");
        request.setParameter("internalComment", "Note");
        request.setParameter("patientChartOption", "doNotAddAsNote");
        request.setParameter("additionalURLParams", "a=b");
        List<EmailConfig> senders = List.of(new EmailConfig());
        when(emailComposeManager.getAllSenderAccounts()).thenReturn(senders);
        when(demographicManager.getDemographicFormattedName(any(), eq(7))).thenReturn("FAKE, Patient");
        when(emailComposeManager.getEmailConsentStatus(any(), eq(7))).thenReturn(new String[]{"Email", "Explicit Opt-In"});
        EmailSend2Action action = overLengthAction(request);

        action.sendEFormEmail();

        assertThat(request.getAttribute("transactionType")).isEqualTo(TransactionType.EFORM);
        assertThat(request.getAttribute("demographicId")).isEqualTo("7");
        assertThat(request.getAttribute("fid")).isEqualTo("3");
        assertThat(request.getAttribute("senderAccounts")).isSameAs(senders);
        assertThat(request.getAttribute("senderConfigId")).isEqualTo("5");
        assertThat((List<Object>) request.getAttribute("receiverEmailList")).containsExactly("a@example.com", "b@example.com");
        assertThat((List<?>) request.getAttribute("invalidReceiverEmailList")).isEmpty();
        assertThat(request.getAttribute("receiverName")).isEqualTo("FAKE, Patient");
        assertThat(request.getAttribute("emailConsentName")).isEqualTo("Email");
        assertThat(request.getAttribute("emailConsentStatus")).isEqualTo("Explicit Opt-In");
        assertThat(request.getAttribute("subjectEmail")).isEqualTo("Subject");
        assertThat(request.getAttribute("bodyEmail")).isEqualTo("Body");
        assertThat(request.getAttribute("encryptedMessageEmail")).isEqualTo("Secret");
        assertThat(request.getAttribute("emailPDFPassword")).isEqualTo("pass12345");
        assertThat(request.getAttribute("emailPDFPasswordClue")).isEqualTo("Clue");
        assertThat(request.getAttribute("internalComment")).isEqualTo("Note");
        assertThat(request.getAttribute("emailPatientChartOption")).isEqualTo("doNotAddAsNote");
        assertThat(request.getAttribute("emailAdditionalParams")).isEqualTo("a=b");
        assertThat(request.getAttribute("isEmailAutoSend")).isEqualTo(false);
    }

    @Test
    @DisplayName("should refuse an overlong or malformed demographicId with 400 before parsing it")
    void shouldRejectWithBadRequest_whenDemographicIdOverflows() throws Exception {
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        when(securityInfoManager.hasPrivilege(any(), eq("_email"), eq("w"), ArgumentMatchers.<String>isNull())).thenReturn(true);
        for (String demographicId : new String[]{"9999999999", "2147483648", "12345678901234567890", "7x", "-7"}) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setMethod("POST");
            request.setParameter("transactionType", "DIRECT");
            request.setParameter("method", "sendDirectEmail");
            request.setParameter("demographicId", demographicId);
            LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());
            MockHttpServletResponse response = new MockHttpServletResponse();
            EmailSend2Action action = new EmailSend2Action(securityInfoManager, emailManager, eformDataManager,
                    emailComposeManager, demographicManager);
            action.request = request;
            action.response = response;

            String result = action.execute();

            assertThat(result).as(demographicId).isEqualTo(EmailSend2Action.NONE);
            assertThat(response.getStatus()).as(demographicId).isEqualTo(400);
        }
        verifyNoInteractions(emailManager);
        verifyNoInteractions(demographicManager);
    }

    @Test
    @DisplayName("should refuse GET and HEAD with 405 and Allow: POST before any send or lookup")
    void shouldRejectWithMethodNotAllowed_whenRequestIsNotPost() throws Exception {
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        when(securityInfoManager.hasPrivilege(any(), eq("_email"), eq("w"), ArgumentMatchers.<String>isNull())).thenReturn(true);
        for (String httpMethod : new String[]{"GET", "HEAD"}) {
            for (String dispatch : new String[]{"sendDirectEmail", "cancel", null}) {
                MockHttpServletRequest request = new MockHttpServletRequest();
                request.setMethod(httpMethod);
                request.setParameter("transactionType", "DIRECT");
                request.setParameter("demographicId", "7");
                if (dispatch != null) {
                    request.setParameter("method", dispatch);
                }
                LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());
                MockHttpServletResponse response = new MockHttpServletResponse();
                EmailSend2Action action = new EmailSend2Action(securityInfoManager, emailManager, eformDataManager,
                        emailComposeManager, demographicManager);
                action.request = request;
                action.response = response;

                String result = action.execute();

                String label = httpMethod + " method=" + dispatch;
                assertThat(result).as(label).isEqualTo(EmailSend2Action.NONE);
                assertThat(response.getStatus()).as(label).isEqualTo(405);
                assertThat(response.getHeader("Allow")).as(label).isEqualTo("POST");
                assertThat(response.getRedirectedUrl()).as(label).isNull();
            }
        }
        verifyNoInteractions(emailManager);
        verifyNoInteractions(eformDataManager);
        verifyNoInteractions(demographicManager);
    }

    @Test
    @DisplayName("should dispatch the send when the request is a POST")
    void shouldDispatchSend_whenRequestIsPost() throws Exception {
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        when(securityInfoManager.hasPrivilege(any(), eq("_email"), eq("w"), ArgumentMatchers.<String>isNull())).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setParameter("transactionType", "DIRECT");
        request.setParameter("method", "sendDirectEmail");
        request.setParameter("demographicId", "7");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());
        EmailLog log = new EmailLog();
        log.setStatus(EmailLog.EmailStatus.SUCCESS);
        when(emailManager.sendEmail(any(), any())).thenReturn(log);
        MockHttpServletResponse response = new MockHttpServletResponse();
        EmailSend2Action action = new EmailSend2Action(securityInfoManager, emailManager, eformDataManager,
                emailComposeManager, demographicManager);
        action.request = request;
        action.response = response;

        String result = action.execute();

        assertThat(result).isEqualTo(EmailSend2Action.SUCCESS);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(request.getAttribute("isEmailSuccessful")).isEqualTo(true);
        verify(emailManager).sendEmail(any(), any());
    }

    @Test
    @DisplayName("should treat an overlong or malformed demographicId as no patient when restoring the form")
    void shouldParseDemographicIdOrNull_whenValueOverflowsOrIsMalformed() {
        // The retry form restores the patient from this parse; it must never throw, and a
        // ten-digit value that satisfies the digit pattern still has to fail the int range check.
        assertThat(EmailSend2Action.parseDemographicId("7")).isEqualTo(7);
        assertThat(EmailSend2Action.parseDemographicId("2147483647")).isEqualTo(Integer.MAX_VALUE);
        assertThat(EmailSend2Action.parseDemographicId("2147483648")).isNull();
        assertThat(EmailSend2Action.parseDemographicId("9999999999")).isNull();
        assertThat(EmailSend2Action.parseDemographicId("12345678901234567890")).isNull();
        assertThat(EmailSend2Action.parseDemographicId("7x")).isNull();
        assertThat(EmailSend2Action.parseDemographicId("-7")).isNull();
        assertThat(EmailSend2Action.parseDemographicId("")).isNull();
        assertThat(EmailSend2Action.parseDemographicId(null)).isNull();
        // Absent or blank means a direct email with no patient and is accepted by the routes.
        assertThat(EmailSend2Action.isValidDemographicId(null)).isTrue();
        assertThat(EmailSend2Action.isValidDemographicId("")).isTrue();
        assertThat(EmailSend2Action.isValidDemographicId("9999999999")).isFalse();
    }

    @Test
    @DisplayName("should drop echoed values the page writes unencoded unless they are well formed")
    void shouldNormaliseEchoedValues_whenFieldTooLong() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("transactionType", "EFORM\"><script>");
        request.setParameter("fdid", "42<b>");
        request.setParameter("senderConfigId", "x");
        request.setParameter("openEFormAfterEmail", "true\"><script>");
        request.setParameter("patientChartOption", "bogus");
        EmailSend2Action action = overLengthAction(request);

        action.sendEFormEmail();

        assertThat(request.getAttribute("transactionType")).isNull();
        assertThat(request.getAttribute("fdid")).isNull();
        assertThat(request.getAttribute("senderConfigId")).isNull();
        assertThat(request.getAttribute("openEFormAfterEmail")).isEqualTo(false);
        assertThat(request.getAttribute("emailPatientChartOption")).isNull();
        assertThat(request.getAttribute("receiverName")).isNull();
        verifyNoInteractions(demographicManager);
        verifyNoInteractions(eformDataManager);
    }

    @Test
    @DisplayName("should keep the submitted encryption choices when a field is too long")
    void shouldKeepEncryptionFlags_whenFieldTooLong() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("transactionType", "DIRECT");
        request.setParameter("demographicId", "7");
        request.setParameter("isEmailEncrypted", "true");
        request.setParameter("isEmailAttachmentEncrypted", "true");
        EmailSend2Action action = overLengthAction(request);

        action.sendDirectEmail();

        assertThat(request.getAttribute("isEmailEncrypted")).isEqualTo(true);
        assertThat(request.getAttribute("isEmailAttachmentEncrypted")).isEqualTo(true);
        assertThat(request.getAttribute("emailLengthViolations")).isNotNull();
    }

    @Test
    @DisplayName("should not send attachments prepared for another patient")
    void shouldDropAttachments_whenPreparedForAnotherPatient() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("transactionType", "DIRECT");
        request.setParameter("demographicId", "8");
        request.getSession().setAttribute(EmailSend2Action.ATTACHMENT_LIST_SESSION_KEY,
                new ArrayList<>(List.of(new EmailAttachment())));
        request.getSession().setAttribute(EmailSend2Action.ATTACHMENT_OWNER_SESSION_KEY, "7");
        EmailSend2Action action = sendingAction(request);

        action.sendDirectEmail();

        ArgumentCaptor<EmailData> sent = ArgumentCaptor.forClass(EmailData.class);
        verify(emailManager).sendEmail(any(), sent.capture());
        assertThat(sent.getValue().getAttachments()).isEmpty();
        assertThat(request.getSession().getAttribute(EmailSend2Action.ATTACHMENT_LIST_SESSION_KEY)).isNull();
        assertThat(request.getSession().getAttribute(EmailSend2Action.ATTACHMENT_OWNER_SESSION_KEY)).isNull();
    }

    @Test
    @DisplayName("should send attachments prepared for the same patient")
    void shouldKeepAttachments_whenPreparedForSamePatient() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("transactionType", "DIRECT");
        request.setParameter("demographicId", "7");
        List<EmailAttachment> attachments = new ArrayList<>(List.of(new EmailAttachment()));
        request.getSession().setAttribute(EmailSend2Action.ATTACHMENT_LIST_SESSION_KEY, attachments);
        request.getSession().setAttribute(EmailSend2Action.ATTACHMENT_OWNER_SESSION_KEY, "7");
        EmailSend2Action action = sendingAction(request);

        action.sendDirectEmail();

        ArgumentCaptor<EmailData> sent = ArgumentCaptor.forClass(EmailData.class);
        verify(emailManager).sendEmail(any(), sent.capture());
        assertThat(sent.getValue().getAttachments()).isSameAs(attachments);
    }

    private EmailSend2Action sendingAction(MockHttpServletRequest request) {
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());
        EmailLog log = new EmailLog();
        log.setStatus(EmailLog.EmailStatus.FAILED);
        when(emailManager.sendEmail(any(), any())).thenReturn(log);
        EmailSend2Action action = new EmailSend2Action();
        action.request = request;
        action.response = new MockHttpServletResponse();
        return action;
    }
}
