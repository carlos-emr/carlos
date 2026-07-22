/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.email.action;

import io.github.carlos_emr.carlos.commn.model.EmailAttachment;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.email.core.EmailPdfPasswordService;
import io.github.carlos_emr.carlos.managers.EmailComposeManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("security")
@DisplayName("EmailCompose2Action")
class EmailCompose2ActionUnitTest extends CarlosUnitTestBase {

    @Test
    @DisplayName("should sanitize fid before logging invalid value")
    void shouldSanitizeFid_whenInvalidValueIsLogged() throws Exception {
        DemographicManager demographicManager = mock(DemographicManager.class);
        EmailComposeManager emailComposeManager = mock(EmailComposeManager.class);
        EmailPdfPasswordService emailPdfPasswordService = mock(EmailPdfPasswordService.class);
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        registerMock(DemographicManager.class, demographicManager);
        registerMock(EmailComposeManager.class, emailComposeManager);
        registerMock(EmailPdfPasswordService.class, emailPdfPasswordService);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/email/compose");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.getSession(true).setAttribute("demographicId", "123");
        request.getSession(false).setAttribute("emailPDFPassword", "existing-password");
        request.getSession(false).setAttribute("emailPDFPasswordClue", "existing-clue");
        request.addParameter("fid", "abc\r\nforged-fid");
        when(emailComposeManager.getEmailConsentStatus(any(), anyInt())).thenReturn(new String[]{"Consent", "Yes"});
        when(demographicManager.getDemographicFormattedName(any(), anyInt())).thenReturn("Patient One");
        when(emailComposeManager.getRecipients(any(), anyInt())).thenReturn(new List<?>[]{List.of(), List.of()});
        when(emailComposeManager.getAllSenderAccounts()).thenReturn(List.of());
        when(emailComposeManager.prepareEFormAttachments(any(), any(), any())).thenReturn(List.of());
        when(emailComposeManager.prepareEDocAttachments(any(), any())).thenReturn(List.of());
        when(emailComposeManager.prepareLabAttachments(any(), any())).thenReturn(List.of());
        when(emailComposeManager.prepareHRMAttachments(any(), any())).thenReturn(List.of());
        when(emailComposeManager.prepareFormAttachments(any(), any(), any(), anyInt())).thenReturn(List.of());
        when(emailPdfPasswordService.generatePassphrase()).thenReturn("alpha-bravo-123-charlie-delta-456");

        try (MockedStatic<ServletActionContext> servletActionContext = mockStatic(ServletActionContext.class);
             LogCapture capture = LogCapture.forLogger(EmailCompose2Action.class)) {
            servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);

            EmailCompose2Action action = new EmailCompose2Action();

            assertThat(action.prepareComposeEFormMailer()).isEqualTo("compose");
            assertThat(request.getAttribute("fid")).isNull();
            assertThat(request.getAttribute("emailPDFPassword")).isEqualTo("alpha-bravo-123-charlie-delta-456");
            assertThat(request.getSession(false).getAttribute("emailPDFPassword")).isNull();
            assertThat(request.getSession(false).getAttribute("emailComposeSubmissionStates")).isNull();
            String emailPDFPasswordToken = (String) request.getAttribute("emailPDFPasswordToken");
            assertThat(emailPDFPasswordToken).isNotBlank();
            request.setParameter(EmailCompose2Action.EMAIL_PDF_PASSWORD_TOKEN_PARAM, emailPDFPasswordToken);
            EmailCompose2Action.EmailComposeSubmissionState composeState =
                    EmailCompose2Action.consumeEmailComposeSubmissionState(request);
            assertThat(composeState.emailPDFPassword()).isEqualTo("alpha-bravo-123-charlie-delta-456");
            verify(emailPdfPasswordService).generatePassphrase();
            String logged = capture.messages().stream()
                    .filter(message -> message.startsWith("Invalid fid parameter received"))
                    .findFirst()
                    .orElseThrow();
            assertThat(logged).doesNotContain("\r").doesNotContain("\n");
            assertThat(logged).contains("abc\\r\\nforged-fid");
        }
    }

    @Test
    @DisplayName("should disable auto-send when encryption requires separate password delivery")
    void shouldDisableAutoSend_whenEmailEncryptionEnabled() throws Exception {
        DemographicManager demographicManager = mock(DemographicManager.class);
        EmailComposeManager emailComposeManager = mock(EmailComposeManager.class);
        EmailPdfPasswordService emailPdfPasswordService = mock(EmailPdfPasswordService.class);
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        registerMock(DemographicManager.class, demographicManager);
        registerMock(EmailComposeManager.class, emailComposeManager);
        registerMock(EmailPdfPasswordService.class, emailPdfPasswordService);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/email/compose");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.getSession(true).setAttribute("demographicId", "123");
        request.getSession(false).setAttribute("isEmailAutoSend", true);
        request.getSession(false).setAttribute("isEmailEncrypted", true);
        when(emailComposeManager.getEmailConsentStatus(any(), anyInt())).thenReturn(new String[]{"Consent", "Yes"});
        when(demographicManager.getDemographicFormattedName(any(), anyInt())).thenReturn("Patient One");
        when(emailComposeManager.getRecipients(any(), anyInt())).thenReturn(new List<?>[]{List.of("patient@example.com"), List.of()});
        when(emailComposeManager.getAllSenderAccounts()).thenReturn(List.of());
        when(emailComposeManager.prepareEFormAttachments(any(), any(), any())).thenReturn(List.of());
        when(emailComposeManager.prepareEDocAttachments(any(), any())).thenReturn(List.of());
        when(emailComposeManager.prepareLabAttachments(any(), any())).thenReturn(List.of());
        when(emailComposeManager.prepareHRMAttachments(any(), any())).thenReturn(List.of());
        when(emailComposeManager.prepareFormAttachments(any(), any(), any(), anyInt())).thenReturn(List.of());
        when(emailPdfPasswordService.generatePassphrase()).thenReturn("alpha-bravo-123-charlie-delta-456");

        try (MockedStatic<ServletActionContext> servletActionContext = mockStatic(ServletActionContext.class)) {
            servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);

            EmailCompose2Action action = new EmailCompose2Action();

            assertThat(action.prepareComposeEFormMailer()).isEqualTo("compose");
            assertThat(request.getAttribute("isEmailAutoSend")).isEqualTo(false);
            EmailCompose2Action.clearEmailComposeSubmissionStates(request.getSession().getId());
        }
    }

    @Test
    @DisplayName("should cap pending compose submission states")
    void shouldCapPendingComposeSubmissionStates_whenMaxExceeded() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/email/compose");
        String oldestToken = null;
        try {
            for (int i = 0; i <= EmailCompose2Action.MAX_PENDING_EMAIL_COMPOSE_STATES; i++) {
                String token = EmailCompose2Action.storeEmailComposeSubmissionState(
                        request,
                        "password" + i,
                        EmailPdfPasswordService.DELIVERY_INSTRUCTION,
                        List.of());
                if (i == 0) {
                    oldestToken = token;
                }
            }

            request.setParameter(EmailCompose2Action.EMAIL_PDF_PASSWORD_TOKEN_PARAM, oldestToken);

            assertThat(EmailCompose2Action.consumeEmailComposeSubmissionState(request)).isNull();
        } finally {
            EmailCompose2Action.clearEmailComposeSubmissionStates(request.getSession().getId());
        }
    }

    @Test
    @DisplayName("should reject new compose state when global cache is full")
    void shouldRejectNewComposeState_whenGlobalCacheIsFull() {
        List<MockHttpServletRequest> requests = new ArrayList<>();
        try {
            for (int i = 0; i < EmailCompose2Action.MAX_PENDING_EMAIL_COMPOSE_SUBMISSION_STATES; i++) {
                MockHttpServletRequest request = new MockHttpServletRequest("GET", "/email/compose");
                requests.add(request);
                EmailCompose2Action.storeEmailComposeSubmissionState(
                        request,
                        "password" + i,
                        EmailPdfPasswordService.DELIVERY_INSTRUCTION,
                        List.of());
            }

            MockHttpServletRequest overflowRequest = new MockHttpServletRequest("GET", "/email/compose");

            assertThatThrownBy(() -> EmailCompose2Action.storeEmailComposeSubmissionState(
                    overflowRequest,
                    "overflow-password",
                    EmailPdfPasswordService.DELIVERY_INSTRUCTION,
                    List.of()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Email compose submission state cache is full");
        } finally {
            for (MockHttpServletRequest request : requests) {
                EmailCompose2Action.clearEmailComposeSubmissionStates(request.getSession().getId());
            }
        }
    }

    @Test
    @DisplayName("should expire pending compose submission states")
    void shouldExpirePendingComposeSubmissionStates_whenMaxAgeExceeded() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/email/compose");
        String expiredToken = EmailCompose2Action.storeEmailComposeSubmissionState(
                request,
                "expired-password",
                EmailPdfPasswordService.DELIVERY_INSTRUCTION,
                List.of(),
                System.currentTimeMillis()
                        - EmailCompose2Action.PENDING_EMAIL_COMPOSE_STATE_MAX_AGE_MILLIS
                        - 1);
        request.setParameter(EmailCompose2Action.EMAIL_PDF_PASSWORD_TOKEN_PARAM, expiredToken);

        assertThat(EmailCompose2Action.consumeEmailComposeSubmissionState(request)).isNull();
    }

    @Test
    @DisplayName("should clear pending compose submission states for session")
    void shouldClearPendingComposeSubmissionStates_forSession() {
        MockHttpServletRequest firstRequest = new MockHttpServletRequest("GET", "/email/compose");
        MockHttpServletRequest secondRequest = new MockHttpServletRequest("GET", "/email/compose");
        String firstSessionId = firstRequest.getSession().getId();
        String firstToken = EmailCompose2Action.storeEmailComposeSubmissionState(
                firstRequest,
                "first-password",
                EmailPdfPasswordService.DELIVERY_INSTRUCTION,
                List.of());
        String secondToken = EmailCompose2Action.storeEmailComposeSubmissionState(
                secondRequest,
                "second-password",
                EmailPdfPasswordService.DELIVERY_INSTRUCTION,
                List.of());

        assertThat(EmailCompose2Action.clearEmailComposeSubmissionStates(firstSessionId)).isEqualTo(1);
        firstRequest.setParameter(EmailCompose2Action.EMAIL_PDF_PASSWORD_TOKEN_PARAM, firstToken);
        secondRequest.setParameter(EmailCompose2Action.EMAIL_PDF_PASSWORD_TOKEN_PARAM, secondToken);

        assertThat(EmailCompose2Action.consumeEmailComposeSubmissionState(firstRequest)).isNull();
        assertThat(EmailCompose2Action.consumeEmailComposeSubmissionState(secondRequest)).isNotNull();
        EmailCompose2Action.clearEmailComposeSubmissionStates(secondRequest.getSession().getId());
    }

    @Test
    @DisplayName("should defensively copy compose submission attachment list")
    void shouldDefensivelyCopyAttachmentList_whenComposeSubmissionStateCreated() {
        List<EmailAttachment> attachments = new ArrayList<>();
        EmailCompose2Action.EmailComposeSubmissionState state =
                new EmailCompose2Action.EmailComposeSubmissionState(
                        "password",
                        EmailPdfPasswordService.DELIVERY_INSTRUCTION,
                        attachments,
                        System.currentTimeMillis());

        attachments.add(mock(EmailAttachment.class));

        assertThat(state.emailAttachmentList()).isEmpty();
        assertThatThrownBy(() -> state.emailAttachmentList().add(mock(EmailAttachment.class)))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
