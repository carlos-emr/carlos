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

import java.util.Collection;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import io.github.carlos_emr.carlos.commn.model.EmailAttachment;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.EmailLog.EmailStatus;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.documentManager.AttachmentOwnershipService;
import io.github.carlos_emr.carlos.email.core.EmailComposeSubmissionStateService;
import io.github.carlos_emr.carlos.email.core.EmailComposeSubmissionStateService.EmailComposeSubmissionContext;
import io.github.carlos_emr.carlos.email.core.EmailComposeSubmissionStateService.EmailComposeSubmissionState;
import io.github.carlos_emr.carlos.email.core.EmailData;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EmailSend2Action}: redirect safety, explicit dispatch
 * allowlisting, the POST-only contract (issue #3111), and sending only the attachments the
 * window's own compose state holds, for that state's patient (#4425). The action is registered in
 * {@code MutatorActionGetRejectionContractUnitTest#unconditionalMutators()}.
 *
 * @since 2026-05-20
 */
@Tag("unit")
@Tag("fast")
@Tag("email")
@DisplayName("EmailSend2Action")
class EmailSend2ActionUnitTest extends EmailWorkflowUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;

    private SecurityInfoManager securityInfoManager;
    private EmailManager emailManager;
    private EformDataManager eformDataManager;
    private AttachmentOwnershipService ownership;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        securityInfoManager = mock(SecurityInfoManager.class);
        emailManager = mock(EmailManager.class);
        eformDataManager = mock(EformDataManager.class);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(EmailManager.class, emailManager);
        registerMock(EmailComposeManager.class, mock(EmailComposeManager.class));
        registerMock(EformDataManager.class, eformDataManager);
        ownership = mock(AttachmentOwnershipService.class);
        registerMock(AttachmentOwnershipService.class, ownership);
        when(ownership.allBelongToDemographic(anyMap(), any())).thenReturn(true);
        // EmailSend2Action reads request/response from ServletActionContext in field initializers
        // (evaluated at construction), so mock the static to keep `new EmailSend2Action()` from
        // NPEing before each test assigns action.request/response explicitly.
        servletActionContextMock = mockStatic(ServletActionContext.class);

        request = new MockHttpServletRequest();
        request.setContextPath("/carlos");
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void tearDown() {
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
    }

    private EmailSend2Action newAction() {
        EmailSend2Action action = new EmailSend2Action();
        action.request = request;
        action.response = response;
        return action;
    }

    private void grantEmailWritePrivilege() {
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());
        when(securityInfoManager.hasPrivilege(
                any(LoggedInInfo.class), eq("_email"), eq("w"), nullable(String.class)))
            .thenReturn(true);
    }

    private void prepareValidUnencryptedMessage() {
        request.setParameter("message", "Appointment reminder");
        request.setParameter("isEmailEncrypted", "false");
        request.setParameter("isEmailAttachmentEncrypted", "false");
    }

    @Test
    @DisplayName("should preserve the compose draft when sender configuration fails without an outbox id")
    void shouldPreserveComposeDraft_whenSenderConfigurationFailsDuringSend() {
        grantEmailWritePrivilege();
        prepareValidUnencryptedMessage();
        request.setMethod("POST");
        request.setParameter("method", "sendDirectEmail");
        request.setParameter("receiverEmailAddress", "patient@example.invalid");
        request.setParameter("subjectEmail", "Synthetic appointment reminder");
        request.setParameter("patientChartOption", "doNotAddAsNote");
        request.setParameter("transactionType", "DIRECT");
        request.setParameter("demographicId", "123");
        LoggedInInfo.getLoggedInInfoFromSession(request).setLoggedInProvider(
                new io.github.carlos_emr.carlos.commn.model.Provider("999998"));
        EmailLog failure = new EmailLog();
        failure.setStatus(EmailStatus.FAILED);
        failure.setErrorMessage("Email sender account is not configured or is inactive.");
        when(emailManager.sendEmailWithResult(any(LoggedInInfo.class), any(EmailData.class))).thenAnswer(invocation -> sendResult(failure));

        prepareSubmission(request);
        String result = newAction().execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(failure.getId()).isNull();
        assertThat(request.getAttribute("isEmailSuccessful")).isEqualTo(false);
        assertThat(request.getAttribute("emailLog")).isSameAs(failure);
        assertThat(request.getAttribute("message")).isEqualTo("Appointment reminder");
        assertThat(request.getAttribute("isEmailEncrypted")).isEqualTo(false);
        assertThat(request.getAttribute("subjectEmail")).isEqualTo("Synthetic appointment reminder");
        assertThat(request.getAttribute("demographicId")).isEqualTo("123");
        org.mockito.ArgumentCaptor<EmailData> sent = org.mockito.ArgumentCaptor.forClass(EmailData.class);
        verify(emailManager).sendEmailWithResult(any(LoggedInInfo.class), sent.capture());
        assertThat(sent.getValue().getSenderConfigId()).isEqualTo(1);
        verifyNoInteractions(eformDataManager);
    }

    @Test
    @DisplayName("should reject GET cancel without consuming session attachments")
    void shouldRejectGetCancel_withoutConsumingSessionAttachments() {
        grantEmailWritePrivilege();
        request.setMethod("GET");
        request.setParameter("method", "cancel");
        request.setParameter("transactionType", "DIRECT");
        List<?> attachments = List.of("sentinel");
        request.getSession().setAttribute(EmailSessionKeys.EMAIL_ATTACHMENT_LIST, attachments);

        String result = newAction().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        assertThat(request.getSession().getAttribute(EmailSessionKeys.EMAIL_ATTACHMENT_LIST))
                .isSameAs(attachments);
        verifyNoInteractions(emailManager, eformDataManager);
    }

    @Test
    @DisplayName("should clear session attachments when cancel arrives via POST")
    void shouldConsumeSubmission_whenCancelArrivesViaPost() {
        grantEmailWritePrivilege();
        request.setMethod("POST");
        request.setParameter("method", "cancel");
        request.setParameter("transactionType", "DIRECT");
        String token = submissionStates.store(request.getSession(), "secret", "separate", List.of());
        request.setParameter(io.github.carlos_emr.carlos.email.core.EmailComposeSubmissionStateService.EMAIL_PDF_PASSWORD_TOKEN_PARAM, token);

        String result = newAction().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NO_CONTENT);
        assertThat(submissionStates.consume(request)).isNull();
        verifyNoInteractions(emailManager, eformDataManager);
    }

    @Test
    @DisplayName("should reject POST cancel when transaction type is missing")
    void shouldRejectPostCancel_whenTransactionTypeIsMissing() {
        grantEmailWritePrivilege();
        request.setMethod("POST");
        request.setParameter("method", "cancel");
        List<?> attachments = List.of("sentinel");
        request.getSession().setAttribute(EmailSessionKeys.EMAIL_ATTACHMENT_LIST, attachments);

        String result = newAction().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        assertThat(response.getRedirectedUrl()).isNull();
        assertThat(request.getSession().getAttribute(EmailSessionKeys.EMAIL_ATTACHMENT_LIST))
                .isSameAs(attachments);
        verifyNoInteractions(emailManager, eformDataManager);
    }

    @Test
    @DisplayName("should reject POST cancel when transaction type is misspelled")
    void shouldRejectPostCancel_whenTransactionTypeIsMisspelled() {
        grantEmailWritePrivilege();
        request.setMethod("POST");
        request.setParameter("method", "cancel");
        request.setParameter("transactionType", "EFROM");
        List<?> attachments = List.of("sentinel");
        request.getSession().setAttribute(EmailSessionKeys.EMAIL_ATTACHMENT_LIST, attachments);

        String result = newAction().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        assertThat(response.getRedirectedUrl()).isNull();
        assertThat(request.getSession().getAttribute(EmailSessionKeys.EMAIL_ATTACHMENT_LIST))
                .isSameAs(attachments);
        verifyNoInteractions(emailManager, eformDataManager);
    }

    @Test
    @DisplayName("should encode fdid when cancel redirects to eForm")
    void shouldEncodeFdid_whenCancelRedirectsToEForm() {
        grantEmailWritePrivilege();
        request.setMethod("POST");
        request.setParameter("method", "cancel");
        request.setParameter("transactionType", "EFORM");
        request.setParameter("fdid", "123&parentAjaxId=evil#fragment%25 +/");

        String result = newAction().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getRedirectedUrl()).isEqualTo(
                "/carlos/eform/efmshowform_data?fdid="
                        + "123%26parentAjaxId%3Devil%23fragment%2525%20%2B%2F&parentAjaxId=eforms");
        verifyNoInteractions(emailManager, eformDataManager);
    }

    /**
     * The action-level method and dispatch contract: non-POST requests and unknown
     * dispatch values must not send patient email, persist an {@code EmailLog},
     * delete eForm data, or consume session-scoped attachments.
     */
    @Nested
    @DisplayName("HTTP method and dispatch rejection")
    class RequestRejection {

        @Test
        @DisplayName("should send 405 without side effects when GET has no method parameter")
        void shouldSend405WithoutSideEffects_whenGetHasNoMethodParameter() {
            grantEmailWritePrivilege();
            request.setMethod("GET");
            request.setParameter("deleteEFormAfterEmail", "true");
            request.setParameter("fdid", "42");
            List<?> attachments = List.of("sentinel");
            request.getSession().setAttribute(EmailSessionKeys.EMAIL_ATTACHMENT_LIST, attachments);

            String result = newAction().execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            assertThat(response.getHeader("Allow")).isEqualTo("POST");
            assertThat(request.getSession().getAttribute(EmailSessionKeys.EMAIL_ATTACHMENT_LIST))
                    .isSameAs(attachments);
            verifyNoInteractions(emailManager, eformDataManager);
        }

        @Test
        @DisplayName("should send 405 without side effects when GET requests sendDirectEmail")
        void shouldSend405WithoutSideEffects_whenGetRequestsSendDirectEmail() {
            grantEmailWritePrivilege();
            request.setMethod("GET");
            request.setParameter("method", "sendDirectEmail");
            List<?> attachments = List.of("sentinel");
            request.getSession().setAttribute(EmailSessionKeys.EMAIL_ATTACHMENT_LIST, attachments);

            String result = newAction().execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            assertThat(response.getHeader("Allow")).isEqualTo("POST");
            assertThat(request.getSession().getAttribute(EmailSessionKeys.EMAIL_ATTACHMENT_LIST))
                    .isSameAs(attachments);
            verifyNoInteractions(emailManager, eformDataManager);
        }

        @Test
        @DisplayName("should send 405 without side effects when HEAD carries send intent")
        void shouldSend405WithoutSideEffects_whenHeadCarriesSendIntent() {
            grantEmailWritePrivilege();
            request.setMethod("HEAD");
            List<?> attachments = List.of("sentinel");
            request.getSession().setAttribute(EmailSessionKeys.EMAIL_ATTACHMENT_LIST, attachments);

            String result = newAction().execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            assertThat(response.getHeader("Allow")).isEqualTo("POST");
            assertThat(request.getSession().getAttribute(EmailSessionKeys.EMAIL_ATTACHMENT_LIST))
                    .isSameAs(attachments);
            verifyNoInteractions(emailManager, eformDataManager);
        }

        @Test
        @DisplayName("should still block mutation when 405 sendError hits a committed response")
        void shouldStillBlockMutation_whenSendErrorHitsCommittedResponse() throws Exception {
            grantEmailWritePrivilege();
            request.setMethod("GET");
            // Per the Servlet spec, sendError throws IllegalStateException once the
            // response is committed (e.g. client abort); the rejection must still hold.
            HttpServletResponse committedResponse = mock(HttpServletResponse.class);
            doThrow(new IllegalStateException("Response already committed"))
                .when(committedResponse).sendError(anyInt(), anyString());
            EmailSend2Action action = newAction();
            action.response = committedResponse;

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            verifyNoInteractions(emailManager, eformDataManager);
        }

        @Test
        @DisplayName("should reject misspelled dispatch without consuming session attachments")
        void shouldRejectMisspelledDispatch_withoutConsumingSessionAttachments() {
            grantEmailWritePrivilege();
            request.setMethod("POST");
            request.setParameter("method", "cacnel");
            List<?> attachments = List.of("sentinel");
            request.getSession().setAttribute(EmailSessionKeys.EMAIL_ATTACHMENT_LIST, attachments);

            String result = newAction().execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
            assertThat(request.getSession().getAttribute(EmailSessionKeys.EMAIL_ATTACHMENT_LIST))
                    .isSameAs(attachments);
            verifyNoInteractions(emailManager, eformDataManager);
        }

        @Test
        @DisplayName("should send eForm email when POST has no method parameter")
        void shouldSendEFormEmail_whenPostHasNoMethodParameter() {
            grantEmailWritePrivilege();
            prepareValidUnencryptedMessage();
            request.setMethod("POST");
            EmailLog emailLog = new EmailLog();
            emailLog.setStatus(EmailStatus.SUCCESS);
            when(emailManager.sendEmailWithResult(any(LoggedInInfo.class), any(EmailData.class)))
                .thenAnswer(invocation -> sendResult(emailLog));

            request.setParameter("transactionType", "EFORM");
            prepareSubmission(request);
            String result = newAction().execute();

            assertThat(result).isEqualTo(ActionSupport.SUCCESS);
            verify(emailManager).sendEmailWithResult(any(LoggedInInfo.class), any(EmailData.class));
            assertThat(request.getAttribute("isEmailSuccessful")).isEqualTo(true);
        }

        @Test
        @DisplayName("should send eForm email when POST has method sendEFormEmail")
        void shouldSendEFormEmail_whenPostHasSendEFormEmailMethod() {
            grantEmailWritePrivilege();
            prepareValidUnencryptedMessage();
            request.setMethod("POST");
            request.setParameter("method", "sendEFormEmail");
            EmailLog emailLog = new EmailLog();
            emailLog.setStatus(EmailStatus.SUCCESS);
            when(emailManager.sendEmailWithResult(any(LoggedInInfo.class), any(EmailData.class)))
                .thenAnswer(invocation -> sendResult(emailLog));

            request.setParameter("transactionType", "EFORM");
            prepareSubmission(request);
            String result = newAction().execute();

            assertThat(result).isEqualTo(ActionSupport.SUCCESS);
            verify(emailManager).sendEmailWithResult(any(LoggedInInfo.class), any(EmailData.class));
            assertThat(request.getAttribute("isEmailSuccessful")).isEqualTo(true);
        }

        @Test
        @DisplayName("should send direct email when POST has method sendDirectEmail")
        void shouldSendDirectEmail_whenPostHasSendDirectEmailMethod() {
            grantEmailWritePrivilege();
            prepareValidUnencryptedMessage();
            request.setMethod("POST");
            request.setParameter("method", "sendDirectEmail");
            EmailLog emailLog = new EmailLog();
            emailLog.setStatus(EmailStatus.SUCCESS);
            when(emailManager.sendEmailWithResult(any(LoggedInInfo.class), any(EmailData.class)))
                .thenAnswer(invocation -> sendResult(emailLog));

            request.setParameter("transactionType", "DIRECT");
            prepareSubmission(request);
            String result = newAction().execute();

            assertThat(result).isEqualTo(ActionSupport.SUCCESS);
            verify(emailManager).sendEmailWithResult(any(LoggedInInfo.class), any(EmailData.class));
            assertThat(request.getAttribute("isEmailSuccessful")).isEqualTo(true);
        }
    }

    /**
     * Each compose window's attachments live in its own one-time submission state, bound to the
     * patient it was prepared for, and the send re-checks every attachment's owner (#4425).
     */
    @Nested
    @DisplayName("attachment binding per window and patient")
    class AttachmentBinding {

        private MockHttpSession session;

        @BeforeEach
        void setUpWindows() {
            grantEmailWritePrivilege();
            session = (MockHttpSession) request.getSession();
            when(emailManager.hasActiveEmailConfig(1)).thenReturn(true);
            EmailLog sent = new EmailLog();
            sent.setStatus(EmailStatus.SUCCESS);
            when(emailManager.sendEmailWithResult(any(LoggedInInfo.class), any(EmailData.class)))
                    .thenAnswer(invocation -> sendResult(sent));
        }

        private EmailAttachment attachment(DocumentType type, int id) {
            return new EmailAttachment("FAKE-" + type + "-" + id + ".pdf", "/tmp/FAKE-" + id + ".pdf", type, id, 10L);
        }

        /** Prepares one compose window's state for {@code patient}, as the compose or resend does. */
        private String window(String patient, List<EmailAttachment> attachments) {
            return submissionStates.store(session, "FAKE-passphrase", "Deliver separately", attachments,
                    EmailComposeSubmissionContext.eform(patient, "20001", false, true));
        }

        private String directWindow(String patient, List<EmailAttachment> attachments) {
            return submissionStates.store(session, "FAKE-passphrase", "Deliver separately", attachments,
                    EmailComposeSubmissionContext.direct(patient));
        }

        /** The send the compose page posts from the window holding {@code token}. */
        private EmailSend2Action sendFrom(String token, String method, String postedPatient) {
            request = new MockHttpServletRequest("POST", "/email/emailSendAction");
            request.setContextPath("/carlos");
            request.setSession(session);
            response = new MockHttpServletResponse();
            prepareValidUnencryptedMessage();
            request.setParameter("method", method);
            request.setParameter("transactionType", "sendEFormEmail".equals(method) ? "EFORM" : "DIRECT");
            request.setParameter("senderConfigId", "1");
            request.setParameter("receiverEmailAddress", "fake-patient@example.invalid");
            request.setParameter("subjectEmail", "FAKE subject");
            request.setParameter("patientChartOption", "doNotAddAsNote");
            if (postedPatient != null) {
                request.setParameter("demographicId", postedPatient);
            }
            if (token != null) {
                request.setParameter(EmailComposeSubmissionStateService.EMAIL_PDF_PASSWORD_TOKEN_PARAM, token);
            }
            return newAction();
        }

        /** Takes the state stored under {@code token}, as the next send from that window would. */
        private EmailComposeSubmissionState consume(String token) {
            MockHttpServletRequest probe = new MockHttpServletRequest("POST", "/email/emailSendAction");
            probe.setSession(session);
            probe.setParameter(EmailComposeSubmissionStateService.EMAIL_PDF_PASSWORD_TOKEN_PARAM, token);
            return submissionStates.consume(probe);
        }

        private EmailData sentEmail() {
            ArgumentCaptor<EmailData> captor = ArgumentCaptor.forClass(EmailData.class);
            verify(emailManager).sendEmailWithResult(any(LoggedInInfo.class), captor.capture());
            return captor.getValue();
        }

        private void assertRefused(String result, String expectedMessage) {
            assertThat(result).isEqualTo(ActionSupport.SUCCESS);
            assertThat(request.getAttribute("isEmailError")).isEqualTo(true);
            assertThat(request.getAttribute("isEmailComposeStateError")).isEqualTo(true);
            assertThat(request.getAttribute("emailErrorMessage")).isEqualTo(expectedMessage);
            assertThat(request.getAttribute("isEmailSuccessful")).isNull();
            verify(emailManager, never()).sendEmailWithResult(any(), any());
            verifyNoInteractions(eformDataManager);
        }

        @Test
        @DisplayName("should send X's own eForm from X's window after Y's window composed")
        void shouldSendOwnWindowsAttachments_whenAnotherPatientsWindowComposedLater() {
            String windowX = window("10001", List.of(attachment(DocumentType.EFORM, 501)));
            window("10002", List.of(attachment(DocumentType.EFORM, 502)));

            String result = sendFrom(windowX, "sendEFormEmail", "10001").execute();

            assertThat(result).isEqualTo(ActionSupport.SUCCESS);
            EmailData sent = sentEmail();
            assertThat(sent.getDemographicNo()).isEqualTo(10001);
            assertThat(sent.getAttachments()).extracting(EmailAttachment::getDocumentId).containsExactly(501);
            verify(ownership).allBelongToDemographic(Map.of(DocumentType.EFORM, List.of(501)), 10001);
            verify(eformDataManager).removeEFormData(any(), eq("20001"));
        }

        @Test
        @DisplayName("should refuse a send without a token, with an unknown token, or with a token already used")
        void shouldRefuseSend_whenTokenMissingUnknownOrReused() {
            String windowX = window("10001", List.of(attachment(DocumentType.EFORM, 501)));
            assertThat(consume(windowX)).isNotNull();

            for (String token : new String[]{null, "not-a-token", "00000000-0000-0000-0000-000000000000", windowX}) {
                assertRefused(sendFrom(token, "sendEFormEmail", "10001").execute(),
                        EmailCompose2Action.EMAIL_COMPOSE_STATE_EXPIRED_MESSAGE);
            }
            verifyNoInteractions(ownership);
        }

        @Test
        @DisplayName("should send a window's attachments only to its own patient when another patient is posted")
        void shouldSendToBoundPatient_whenPostedDemographicIsAnotherPatient() {
            String windowX = directWindow("10001", List.of(attachment(DocumentType.EFORM, 501)));

            String result = sendFrom(windowX, "sendDirectEmail", "10002").execute();

            assertThat(result).isEqualTo(ActionSupport.SUCCESS);
            EmailData sent = sentEmail();
            assertThat(sent.getDemographicNo()).isEqualTo(10001);
            assertThat(sent.getAttachments()).extracting(EmailAttachment::getDocumentId).containsExactly(501);
            verify(ownership).allBelongToDemographic(Map.of(DocumentType.EFORM, List.of(501)), 10001);
            verify(ownership, never()).allBelongToDemographic(anyMap(), eq(10002));
        }

        @Test
        @DisplayName("should refuse, and use up, a window whose attachment record belongs to another patient")
        void shouldRefuseSend_whenAttachmentBelongsToAnotherPatient() {
            when(ownership.allBelongToDemographic(anyMap(), eq(10001))).thenReturn(false);
            String windowX = window("10001",
                    List.of(attachment(DocumentType.EFORM, 501), attachment(DocumentType.DOC, 601)));

            assertRefused(sendFrom(windowX, "sendEFormEmail", "10001").execute(),
                    EmailSend2Action.ATTACHMENTS_REFUSED_MESSAGE);
            assertThat(consume(windowX)).as("a refused window cannot be retried into sending").isNull();
        }

        @Test
        @DisplayName("should verify eForms, documents, labs and HRM reports by type, leaving forms rendered for the patient")
        void shouldVerifyEveryOwnedType_exceptPatientRenderedForms() {
            String windowX = directWindow("10001", List.of(
                    attachment(DocumentType.EFORM, 501), attachment(DocumentType.DOC, 601),
                    attachment(DocumentType.DOC, 602), attachment(DocumentType.LAB, 701),
                    attachment(DocumentType.HRM, 801), attachment(DocumentType.FORM, 901)));

            sendFrom(windowX, "sendDirectEmail", "10001").execute();

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<DocumentType, Collection<Integer>>> checked = ArgumentCaptor.forClass(Map.class);
            verify(ownership).allBelongToDemographic(checked.capture(), eq(10001));
            assertThat(checked.getValue())
                    .containsOnlyKeys(DocumentType.EFORM, DocumentType.DOC, DocumentType.LAB, DocumentType.HRM);
            assertThat(checked.getValue().get(DocumentType.DOC)).containsExactly(601, 602);
            assertThat(sentEmail().getAttachments()).hasSize(6);
        }

        @Test
        @DisplayName("should refuse a bound attachment that has no document type")
        void shouldRefuseSend_whenAttachmentHasNoType() {
            String windowX = window("10001", List.of(attachment(null, 501)));

            assertRefused(sendFrom(windowX, "sendEFormEmail", "10001").execute(),
                    EmailSend2Action.ATTACHMENTS_REFUSED_MESSAGE);
        }

        @Test
        @DisplayName("should send an email without attachments when its window prepared none")
        void shouldSendWithoutAttachments_whenWindowPreparedNone() {
            String windowX = directWindow("10001", List.of());

            sendFrom(windowX, "sendDirectEmail", "10001").execute();

            assertThat(sentEmail().getAttachments()).isEmpty();
            verifyNoInteractions(ownership);
        }

        @Test
        @DisplayName("should discard only the cancelling window's attachments")
        void shouldDiscardOwnAttachments_whenCancelled() {
            String windowX = directWindow("10001", List.of(attachment(DocumentType.EFORM, 501)));
            String windowY = directWindow("10002", List.of(attachment(DocumentType.EFORM, 502)));

            sendFrom(windowX, "cancel", "10001").execute();

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NO_CONTENT);
            assertThat(consume(windowX)).isNull();
            EmailComposeSubmissionState remaining = consume(windowY);
            assertThat(remaining).isNotNull();
            assertThat(remaining.context().demographicId()).isEqualTo("10002");
            verify(emailManager, never()).sendEmailWithResult(any(), any());
        }
    }

    private EmailSendResult sendResult(EmailLog log) {
        return log.getStatus() == EmailLog.EmailStatus.SUCCESS
                ? EmailSendResult.accepted(log, true) : EmailSendResult.failed(log, true);
    }
}
