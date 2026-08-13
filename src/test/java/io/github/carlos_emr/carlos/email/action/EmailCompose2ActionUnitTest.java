/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.email.action;

import io.github.carlos_emr.carlos.commn.model.EmailLog.TransactionType;
import io.github.carlos_emr.carlos.documentManager.PdfPreviewCapabilityService;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.email.core.EmailComposeSubmissionStateService;
import io.github.carlos_emr.carlos.email.core.EmailPdfPasswordService;
import io.github.carlos_emr.carlos.managers.EmailComposeManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LogSafe;

import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import static io.github.carlos_emr.carlos.email.core.EmailComposeSubmissionStateService.DEFAULT_EMAIL_PDF_PASSWORD_DELIVERY_INSTRUCTION;
import static io.github.carlos_emr.carlos.email.core.EmailComposeSubmissionStateService.EMAIL_PDF_PASSWORD_TOKEN_PARAM;
import static io.github.carlos_emr.carlos.email.core.EmailComposeSubmissionStateService.MAX_PENDING_EMAIL_COMPOSE_STATES;
import static io.github.carlos_emr.carlos.email.core.EmailComposeSubmissionStateService.MAX_PENDING_EMAIL_COMPOSE_SUBMISSION_STATES;
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
    private static final String EXAMPLE_GENERATED_VALUE = "example-generated-value";

    private EmailComposeSubmissionStateService composeSubmissionStateService;

    @BeforeEach
    void setUpComposeSubmissionStateService() {
        composeSubmissionStateService = new EmailComposeSubmissionStateService();
        registerMock(EmailComposeSubmissionStateService.class, composeSubmissionStateService);
        // EmailCompose2Action resolves the preview-token service at construction time, so every
        // test needs it registered even when the test itself never exercises attachment previews.
        registerMock(PdfPreviewCapabilityService.class, mock(PdfPreviewCapabilityService.class));
    }

    @AfterEach
    void tearDownComposeSubmissionStateService() {
        if (composeSubmissionStateService != null) {
            composeSubmissionStateService.shutdown();
        }
    }

    @Test
    @DisplayName("should reject invalid fid and sanitize value for logging")
    void shouldRejectFid_whenInvalidValueProvided() throws Exception {
        DemographicManager demographicManager = mock(DemographicManager.class);
        EmailComposeManager emailComposeManager = mock(EmailComposeManager.class);
        EmailPdfPasswordService emailPdfPasswordService = mock(EmailPdfPasswordService.class);
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        PdfPreviewCapabilityService pdfPreviewCapabilityService = mock(PdfPreviewCapabilityService.class);
        registerMock(DemographicManager.class, demographicManager);
        registerMock(EmailComposeManager.class, emailComposeManager);
        registerMock(EmailPdfPasswordService.class, emailPdfPasswordService);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(PdfPreviewCapabilityService.class, pdfPreviewCapabilityService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/email/compose");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.getSession(true).setAttribute("demographicId", "123");
        request.getSession(false).setAttribute("emailPDFPassword", "example-existing-value");
        request.getSession(false).setAttribute("emailPDFPasswordClue", "example existing note");
        request.getSession(false).setAttribute("isEmailEncrypted", true);
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
        when(emailPdfPasswordService.generatePassphrase()).thenReturn(EXAMPLE_GENERATED_VALUE);

        try (MockedStatic<ServletActionContext> servletActionContext = mockStatic(ServletActionContext.class)) {
            servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);

            EmailCompose2Action action = new EmailCompose2Action();

            assertThat(action.prepareComposeEFormMailer()).isEqualTo("compose");
            assertThat(request.getAttribute("fid")).isNull();
            assertThat(request.getAttribute("emailPDFPassword")).isEqualTo(EXAMPLE_GENERATED_VALUE);
            assertThat(request.getSession(false).getAttribute("emailPDFPassword")).isNull();
            assertThat(request.getSession(false).getAttribute("emailComposeSubmissionStates")).isNull();
            String emailPDFPasswordToken = (String) request.getAttribute("emailPDFPasswordToken");
            assertThat(emailPDFPasswordToken).isNotBlank();
            request.setParameter(EMAIL_PDF_PASSWORD_TOKEN_PARAM, emailPDFPasswordToken);
            EmailComposeSubmissionStateService.EmailComposeSubmissionState composeState =
                    composeSubmissionStateService.consume(request);
            assertThat(composeState.emailPDFPassword()).isEqualTo(EXAMPLE_GENERATED_VALUE);
            verify(emailPdfPasswordService).generatePassphrase();
            assertThat(LogSafe.sanitize("abc\r\nforged-fid"))
                    .doesNotContain("\r")
                    .doesNotContain("\n")
                    .contains("abc\\r\\nforged-fid");
        }
    }

    @Test
    @DisplayName("should show compose error when demographic id is missing")
    void shouldShowComposeError_whenDemographicIdMissing() {
        registerMock(DemographicManager.class, mock(DemographicManager.class));
        registerMock(EmailComposeManager.class, mock(EmailComposeManager.class));
        registerMock(EmailPdfPasswordService.class, mock(EmailPdfPasswordService.class));
        registerMock(SecurityInfoManager.class, mock(SecurityInfoManager.class));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/email/compose");
        MockHttpServletResponse response = new MockHttpServletResponse();

        try (MockedStatic<ServletActionContext> servletActionContext = mockStatic(ServletActionContext.class)) {
            servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);

            EmailCompose2Action action = new EmailCompose2Action();

            assertThat(action.prepareComposeEFormMailer()).isEqualTo("eFormError");
            assertThat(request.getAttribute("errorMessage"))
                    .isEqualTo(EmailCompose2Action.EMAIL_COMPOSE_STATE_EXPIRED_MESSAGE);
        }
    }

    @Test
    @DisplayName("should show compose error when demographic id is invalid")
    void shouldShowComposeError_whenDemographicIdInvalid() {
        registerMock(DemographicManager.class, mock(DemographicManager.class));
        registerMock(EmailComposeManager.class, mock(EmailComposeManager.class));
        registerMock(EmailPdfPasswordService.class, mock(EmailPdfPasswordService.class));
        registerMock(SecurityInfoManager.class, mock(SecurityInfoManager.class));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/email/compose");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.getSession(true).setAttribute("demographicId", "not-a-number");

        try (MockedStatic<ServletActionContext> servletActionContext = mockStatic(ServletActionContext.class)) {
            servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);

            EmailCompose2Action action = new EmailCompose2Action();

            assertThat(action.prepareComposeEFormMailer()).isEqualTo("eFormError");
            assertThat(request.getAttribute("errorMessage"))
                    .isEqualTo(EmailCompose2Action.EMAIL_COMPOSE_STATE_EXPIRED_MESSAGE);
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
        when(emailPdfPasswordService.generatePassphrase()).thenReturn(EXAMPLE_GENERATED_VALUE);

        try (MockedStatic<ServletActionContext> servletActionContext = mockStatic(ServletActionContext.class)) {
            servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);

            EmailCompose2Action action = new EmailCompose2Action();

            try {
                assertThat(action.prepareComposeEFormMailer()).isEqualTo("compose");
                assertThat(request.getAttribute("isEmailAutoSend")).isEqualTo(false);
            } finally {
                composeSubmissionStateService.clear(request.getSession().getId());
            }
        }
    }

    @Test
    @DisplayName("should prepare PDF password when encryption can be enabled from compose")
    void shouldPreparePdfPassword_whenEncryptionCanBeEnabledFromCompose() throws Exception {
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
        request.getSession(false).setAttribute("fdid", "456");
        request.getSession(false).setAttribute("openEFormAfterEmail", true);
        request.getSession(false).setAttribute("deleteEFormAfterEmail", false);
        request.getSession(false).setAttribute("isEmailEncrypted", false);
        when(emailComposeManager.getEmailConsentStatus(any(), anyInt())).thenReturn(new String[]{"Consent", "Yes"});
        when(demographicManager.getDemographicFormattedName(any(), anyInt())).thenReturn("Patient One");
        when(emailComposeManager.getRecipients(any(), anyInt())).thenReturn(new List<?>[]{List.of("patient@example.com"), List.of()});
        when(emailComposeManager.getAllSenderAccounts()).thenReturn(List.of());
        when(emailComposeManager.prepareEFormAttachments(any(), any(), any())).thenReturn(List.of());
        when(emailComposeManager.prepareEDocAttachments(any(), any())).thenReturn(List.of());
        when(emailComposeManager.prepareLabAttachments(any(), any())).thenReturn(List.of());
        when(emailComposeManager.prepareHRMAttachments(any(), any())).thenReturn(List.of());
        when(emailComposeManager.prepareFormAttachments(any(), any(), any(), anyInt())).thenReturn(List.of());
        when(emailPdfPasswordService.generatePassphrase()).thenReturn(EXAMPLE_GENERATED_VALUE);

        try (MockedStatic<ServletActionContext> servletActionContext = mockStatic(ServletActionContext.class)) {
            servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);

            EmailCompose2Action action = new EmailCompose2Action();

            assertThat(action.prepareComposeEFormMailer()).isEqualTo("compose");
            assertThat(request.getAttribute("emailPDFPassword")).isEqualTo(EXAMPLE_GENERATED_VALUE);
            assertThat(request.getAttribute("emailPDFPasswordClue"))
                    .isEqualTo(DEFAULT_EMAIL_PDF_PASSWORD_DELIVERY_INSTRUCTION);
            String emailPDFPasswordToken = (String) request.getAttribute("emailPDFPasswordToken");
            assertThat(emailPDFPasswordToken).isNotBlank();
            request.setParameter(EMAIL_PDF_PASSWORD_TOKEN_PARAM, emailPDFPasswordToken);
            EmailComposeSubmissionStateService.EmailComposeSubmissionState composeState =
                    composeSubmissionStateService.consume(request);
            assertThat(composeState.emailPDFPassword()).isEqualTo(EXAMPLE_GENERATED_VALUE);
            assertThat(composeState.emailPDFPasswordClue())
                    .isEqualTo(DEFAULT_EMAIL_PDF_PASSWORD_DELIVERY_INSTRUCTION);
            assertThat(composeState.context().demographicId()).isEqualTo("123");
            assertThat(composeState.context().fdid()).isEqualTo("456");
            assertThat(composeState.context().transactionType()).isEqualTo(TransactionType.EFORM);
            assertThat(composeState.context().openEFormAfterEmail()).isTrue();
            assertThat(composeState.context().deleteEFormAfterEmail()).isFalse();
            verify(emailPdfPasswordService).generatePassphrase();
        }
    }

    @Test
    @DisplayName("should cap pending compose submission states")
    void shouldCapPendingComposeSubmissionStates_whenMaxExceeded() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/email/compose");
        String oldestToken = null;
        try {
            for (int i = 0; i <= MAX_PENDING_EMAIL_COMPOSE_STATES; i++) {
                String token = composeSubmissionStateService.store(
                        request.getSession(),
                        "example-value-" + i,
                        DEFAULT_EMAIL_PDF_PASSWORD_DELIVERY_INSTRUCTION,
                        List.of());
                if (i == 0) {
                    oldestToken = token;
                }
            }

            request.setParameter(EMAIL_PDF_PASSWORD_TOKEN_PARAM, oldestToken);

            assertThat(composeSubmissionStateService.consume(request)).isNull();
        } finally {
            composeSubmissionStateService.clear(request.getSession().getId());
        }
    }

    @Test
    @DisplayName("should reject new compose state when global cache is full")
    void shouldRejectNewComposeState_whenGlobalCacheIsFull() {
        List<MockHttpServletRequest> requests = new ArrayList<>();
        try {
            for (int i = 0; i < MAX_PENDING_EMAIL_COMPOSE_SUBMISSION_STATES; i++) {
                MockHttpServletRequest request = new MockHttpServletRequest("GET", "/email/compose");
                requests.add(request);
                composeSubmissionStateService.store(
                        request.getSession(),
                        "example-value-" + i,
                        DEFAULT_EMAIL_PDF_PASSWORD_DELIVERY_INSTRUCTION,
                        List.of());
            }

            MockHttpServletRequest overflowRequest = new MockHttpServletRequest("GET", "/email/compose");

            assertThatThrownBy(() -> composeSubmissionStateService.store(
                    overflowRequest.getSession(),
                    "example-overflow-value",
                    DEFAULT_EMAIL_PDF_PASSWORD_DELIVERY_INSTRUCTION,
                    List.of()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Email compose submission state cache is full");
        } finally {
            for (MockHttpServletRequest request : requests) {
                composeSubmissionStateService.clear(request.getSession().getId());
            }
        }
    }

    @Test
    @DisplayName("should clear pending compose submission states for session")
    void shouldClearPendingComposeSubmissionStates_forSession() {
        MockHttpServletRequest firstRequest = new MockHttpServletRequest("GET", "/email/compose");
        MockHttpServletRequest secondRequest = new MockHttpServletRequest("GET", "/email/compose");
        String firstSessionId = firstRequest.getSession().getId();
        String firstToken = composeSubmissionStateService.store(
                firstRequest.getSession(),
                "example-first-value",
                DEFAULT_EMAIL_PDF_PASSWORD_DELIVERY_INSTRUCTION,
                List.of());
        String secondToken = composeSubmissionStateService.store(
                secondRequest.getSession(),
                "example-second-value",
                DEFAULT_EMAIL_PDF_PASSWORD_DELIVERY_INSTRUCTION,
                List.of());

        assertThat(composeSubmissionStateService.clear(firstSessionId)).isEqualTo(1);
        firstRequest.setParameter(EMAIL_PDF_PASSWORD_TOKEN_PARAM, firstToken);
        secondRequest.setParameter(EMAIL_PDF_PASSWORD_TOKEN_PARAM, secondToken);

        assertThat(composeSubmissionStateService.consume(firstRequest)).isNull();
        assertThat(composeSubmissionStateService.consume(secondRequest)).isNotNull();
        composeSubmissionStateService.clear(secondRequest.getSession().getId());
    }
}
