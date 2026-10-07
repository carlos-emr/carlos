/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.email.action;

import io.github.carlos_emr.carlos.commn.model.EmailLog.TransactionType;
import io.github.carlos_emr.carlos.commn.model.EmailAttachment;
import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.documentManager.PdfPreviewCapabilityService;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.email.core.EmailComposeSubmissionStateService;
import io.github.carlos_emr.carlos.email.core.EmailComposeSubmissionStateService.IssuedPreview;
import io.github.carlos_emr.carlos.email.core.EmailComposeWorkingDirectory;
import io.github.carlos_emr.carlos.email.core.EmailPdfPasswordService;
import io.github.carlos_emr.carlos.managers.EmailComposeManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.HttpSession;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static io.github.carlos_emr.carlos.email.core.EmailComposeSubmissionStateService.DEFAULT_EMAIL_PDF_PASSWORD_DELIVERY_INSTRUCTION;
import static io.github.carlos_emr.carlos.email.core.EmailComposeSubmissionStateService.EMAIL_PDF_PASSWORD_TOKEN_PARAM;
import static io.github.carlos_emr.carlos.email.core.EmailComposeSubmissionStateService.MAX_PENDING_EMAIL_COMPOSE_STATES;
import static io.github.carlos_emr.carlos.email.core.EmailComposeSubmissionStateService.MAX_PENDING_EMAIL_COMPOSE_SUBMISSION_STATES;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("security")
@DisplayName("EmailCompose2Action")
class EmailCompose2ActionUnitTest extends CarlosUnitTestBase {
    private static final String EXAMPLE_GENERATED_VALUE = "example-generated-value";

    private EmailComposeSubmissionStateService composeSubmissionStateService;

    private static void stubEmptyAttachmentPreparation(EmailComposeManager manager)
            throws Exception {
        when(manager.prepareEFormAttachments(any(), any(), any(), any())).thenReturn(List.of());
        when(manager.prepareEDocAttachments(any(), any(), any())).thenReturn(List.of());
        when(manager.prepareLabAttachments(any(), any(), any())).thenReturn(List.of());
        when(manager.prepareHRMAttachments(any(), any(), any())).thenReturn(List.of());
        when(manager.prepareFormAttachments(any(), any(), any(), anyInt(), any())).thenReturn(List.of());
    }

    /** Runs the prepare GET, asserts it redirected to a prepared view, and returns the view id. */
    private static String prepare(MockedStatic<ServletActionContext> servletActionContext,
            MockHttpServletRequest request, MockHttpServletResponse response) {
        servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);
        assertThat(new EmailCompose2Action().prepareComposeEFormMailer()).isEqualTo(ActionSupport.NONE);
        String location = response.getRedirectedUrl();
        assertThat(location).startsWith("/email/emailComposeAction?"
                + EmailCompose2Action.EMAIL_COMPOSE_VIEW_PARAM + "=");
        return URLDecoder.decode(location.substring(location.indexOf('=') + 1), StandardCharsets.UTF_8);
    }

    /** Requests a prepared view in the given session through execute() and returns that request. */
    private static MockHttpServletRequest view(MockedStatic<ServletActionContext> servletActionContext,
            HttpSession session, String viewId, MockHttpServletResponse viewResponse, String expectedResult) {
        MockHttpServletRequest viewRequest = new MockHttpServletRequest("GET", "/email/emailComposeAction");
        viewRequest.setSession(session);
        viewRequest.setParameter(EmailCompose2Action.EMAIL_COMPOSE_VIEW_PARAM, viewId);
        servletActionContext.when(ServletActionContext::getRequest).thenReturn(viewRequest);
        servletActionContext.when(ServletActionContext::getResponse).thenReturn(viewResponse);
        assertThat(new EmailCompose2Action().execute()).isEqualTo(expectedResult);
        return viewRequest;
    }

    /** Registers compose collaborators for patient 123 with no attachments and returns them. */
    private ComposeMocks registerComposeMocks() throws Exception {
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
        when(securityInfoManager.hasPrivilege(any(), anyString(), anyString(), isNull())).thenReturn(true);
        when(securityInfoManager.hasPrivilege(any(), anyString(), anyString(), anyInt())).thenReturn(true);
        when(securityInfoManager.isAllowedAccessToPatientRecord(any(), anyInt())).thenReturn(true);
        when(emailComposeManager.getEmailConsentStatus(any(), anyInt())).thenReturn(new String[]{
                "Consent", "OPT_IN", "email.consent.status.optIn"});
        when(demographicManager.getDemographicFormattedName(any(), anyInt())).thenReturn("Patient One");
        when(emailComposeManager.getRecipients(any(), anyInt()))
                .thenReturn(new List<?>[]{List.of("patient@example.com"), List.of()});
        when(emailComposeManager.getAllSenderAccounts()).thenReturn(List.of());
        stubEmptyAttachmentPreparation(emailComposeManager);
        when(emailPdfPasswordService.generatePassphrase()).thenReturn(EXAMPLE_GENERATED_VALUE);
        return new ComposeMocks(emailComposeManager, emailPdfPasswordService, pdfPreviewCapabilityService);
    }

    /**
     * Makes preparation generate one eForm PDF in the working directory, and stages the session
     * to attach it. Returns the generated file's path once prepared.
     */
    private static AtomicReference<Path> stageOneEFormAttachment(ComposeMocks mocks, MockHttpServletRequest request)
            throws Exception {
        AtomicReference<Path> ownedPdf = new AtomicReference<>();
        when(mocks.emailComposeManager().prepareEFormAttachments(any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    EmailComposeWorkingDirectory workingDirectory = invocation.getArgument(3);
                    Path owned = workingDirectory.adoptGeneratedPdf(Files.createTempFile("email-view-test-", ".pdf"));
                    ownedPdf.set(owned);
                    return new ArrayList<>(List.of(new EmailAttachment(
                            "letter.pdf", owned.toString(), DocumentType.EFORM, 1)));
                });
        request.getSession(true).setAttribute("demographicId", "123");
        request.getSession(false).setAttribute("attachEFormItSelf", true);
        request.getSession(false).setAttribute("fdid", "456");
        return ownedPdf;
    }

    private record ComposeMocks(EmailComposeManager emailComposeManager,
            EmailPdfPasswordService emailPdfPasswordService,
            PdfPreviewCapabilityService pdfPreviewCapabilityService) {
    }

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
        when(securityInfoManager.hasPrivilege(any(), anyString(), anyString(), isNull())).thenReturn(true);
        when(securityInfoManager.hasPrivilege(any(), anyString(), anyString(), anyInt())).thenReturn(true);
        when(securityInfoManager.isAllowedAccessToPatientRecord(any(), anyInt())).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/email/compose");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.getSession(true).setAttribute("demographicId", "123");
        request.getSession(false).setAttribute("emailPDFPassword", "example-existing-value");
        request.getSession(false).setAttribute("emailPDFPasswordClue", "example existing note");
        request.getSession(false).setAttribute("isEmailEncrypted", true);
        request.addParameter("fid", "abc\r\nforged-fid");
        when(emailComposeManager.getEmailConsentStatus(any(), anyInt())).thenReturn(new String[]{
                "Consent", "OPT_IN", "email.consent.status.optIn"});
        when(demographicManager.getDemographicFormattedName(any(), anyInt())).thenReturn("Patient One");
        when(emailComposeManager.getRecipients(any(), anyInt())).thenReturn(new List<?>[]{List.of(), List.of()});
        when(emailComposeManager.getAllSenderAccounts()).thenReturn(List.of());
        stubEmptyAttachmentPreparation(emailComposeManager);
        when(emailPdfPasswordService.generatePassphrase()).thenReturn(EXAMPLE_GENERATED_VALUE);

        try (MockedStatic<ServletActionContext> servletActionContext = mockStatic(ServletActionContext.class)) {
            servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);

            String viewId = prepare(servletActionContext, request, response);
            MockHttpServletRequest viewRequest = view(servletActionContext, request.getSession(), viewId,
                    new MockHttpServletResponse(), "compose");

            assertThat(viewRequest.getAttribute("fid")).isNull();
            assertThat(viewRequest.getAttribute("emailPDFPassword")).isEqualTo(EXAMPLE_GENERATED_VALUE);
            assertThat(request.getSession(false).getAttribute("emailPDFPassword")).isNull();
            assertThat(request.getSession(false).getAttribute("emailComposeSubmissionStates")).isNull();
            String emailPDFPasswordToken = (String) viewRequest.getAttribute("emailPDFPasswordToken");
            assertThat(emailPDFPasswordToken).isNotBlank();
            request.setParameter(EMAIL_PDF_PASSWORD_TOKEN_PARAM, emailPDFPasswordToken);
            EmailComposeSubmissionStateService.EmailComposeSubmissionState composeState =
                    composeSubmissionStateService.consume(request);
            assertThat(composeState.emailPDFPassword()).isEqualTo(EXAMPLE_GENERATED_VALUE);
            composeState.close();
            verify(emailPdfPasswordService).generatePassphrase();
            assertThat(LogSafe.sanitize("abc\r\nforged-fid"))
                    .doesNotContain("\r")
                    .doesNotContain("\n")
                    .contains("abc\\r\\nforged-fid");
        }
    }

    @Test
    @DisplayName("should remove partial attachment output when a later renderer fails")
    void shouldRemovePartialAttachmentOutput_whenLaterRendererFails() throws Exception {
        DemographicManager demographicManager = mock(DemographicManager.class);
        EmailComposeManager emailComposeManager = mock(EmailComposeManager.class);
        registerMock(DemographicManager.class, demographicManager);
        registerMock(EmailComposeManager.class, emailComposeManager);
        registerMock(EmailPdfPasswordService.class, mock(EmailPdfPasswordService.class));
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        when(securityInfoManager.hasPrivilege(any(), anyString(), anyString(), anyInt())).thenReturn(true);
        when(securityInfoManager.isAllowedAccessToPatientRecord(any(), anyInt())).thenReturn(true);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/email/compose");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.getSession(true).setAttribute("demographicId", "123");
        when(emailComposeManager.getEmailConsentStatus(any(), anyInt())).thenReturn(new String[]{"Consent", "OPT_IN", "email.consent.status.optIn"});
        when(demographicManager.getDemographicFormattedName(any(), anyInt())).thenReturn("Patient One");
        when(emailComposeManager.getRecipients(any(), anyInt())).thenReturn(new List<?>[]{List.of(), List.of()});
        when(emailComposeManager.getAllSenderAccounts()).thenReturn(List.of());
        AtomicReference<Path> ownedDirectory = new AtomicReference<>();
        when(emailComposeManager.prepareEFormAttachments(any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    EmailComposeWorkingDirectory workingDirectory = invocation.getArgument(3);
                    Path generatedPdf = Files.createTempFile("email-partial-test-", ".pdf");
                    Path ownedPdf = workingDirectory.adoptGeneratedPdf(generatedPdf);
                    ownedDirectory.set(ownedPdf.getParent());
                    return List.of(new EmailAttachment(
                            "attachment.pdf", ownedPdf.toString(), DocumentType.EFORM, 1));
                });
        when(emailComposeManager.prepareEDocAttachments(any(), any(), any()))
                .thenThrow(new PDFGenerationException("later renderer failed"));

        try (MockedStatic<ServletActionContext> servletActionContext = mockStatic(ServletActionContext.class)) {
            servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);

            EmailCompose2Action action = new EmailCompose2Action();

            assertThat(action.prepareComposeEFormMailer()).isEqualTo("eFormError");
            assertThat(ownedDirectory.get()).isNotNull();
            assertThat(Files.exists(ownedDirectory.get())).isFalse();
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

            assertThat(action.prepareComposeEFormMailer()).isEqualTo(EmailCompose2Action.COMPOSE_EXPIRED_RESULT);
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

            assertThat(action.prepareComposeEFormMailer()).isEqualTo(EmailCompose2Action.COMPOSE_EXPIRED_RESULT);
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
        when(securityInfoManager.hasPrivilege(any(), anyString(), anyString(), isNull())).thenReturn(true);
        when(securityInfoManager.hasPrivilege(any(), anyString(), anyString(), anyInt())).thenReturn(true);
        when(securityInfoManager.isAllowedAccessToPatientRecord(any(), anyInt())).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/email/compose");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.getSession(true).setAttribute("demographicId", "123");
        request.getSession(false).setAttribute("isEmailAutoSend", true);
        request.getSession(false).setAttribute("isEmailEncrypted", true);
        when(emailComposeManager.getEmailConsentStatus(any(), anyInt())).thenReturn(new String[]{"Consent", "OPT_IN", "email.consent.status.optIn"});
        when(demographicManager.getDemographicFormattedName(any(), anyInt())).thenReturn("Patient One");
        when(emailComposeManager.getRecipients(any(), anyInt())).thenReturn(new List<?>[]{List.of("patient@example.com"), List.of()});
        when(emailComposeManager.getAllSenderAccounts()).thenReturn(List.of());
        stubEmptyAttachmentPreparation(emailComposeManager);
        when(emailPdfPasswordService.generatePassphrase()).thenReturn(EXAMPLE_GENERATED_VALUE);

        try (MockedStatic<ServletActionContext> servletActionContext = mockStatic(ServletActionContext.class)) {
            servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);

            try {
                String viewId = prepare(servletActionContext, request, response);
                MockHttpServletRequest viewRequest = view(servletActionContext, request.getSession(), viewId,
                        new MockHttpServletResponse(), "compose");
                assertThat(viewRequest.getAttribute("isEmailAutoSend")).isEqualTo(false);
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
        when(securityInfoManager.hasPrivilege(any(), anyString(), anyString(), isNull())).thenReturn(true);
        when(securityInfoManager.hasPrivilege(any(), anyString(), anyString(), anyInt())).thenReturn(true);
        when(securityInfoManager.isAllowedAccessToPatientRecord(any(), anyInt())).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/email/compose");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.getSession(true).setAttribute("demographicId", "123");
        request.getSession(false).setAttribute("fdid", "456");
        request.getSession(false).setAttribute("openEFormAfterEmail", true);
        request.getSession(false).setAttribute("deleteEFormAfterEmail", false);
        request.getSession(false).setAttribute("isEmailEncrypted", false);
        when(emailComposeManager.getEmailConsentStatus(any(), anyInt())).thenReturn(new String[]{"Consent", "OPT_IN", "email.consent.status.optIn"});
        when(demographicManager.getDemographicFormattedName(any(), anyInt())).thenReturn("Patient One");
        when(emailComposeManager.getRecipients(any(), anyInt())).thenReturn(new List<?>[]{List.of("patient@example.com"), List.of()});
        when(emailComposeManager.getAllSenderAccounts()).thenReturn(List.of());
        stubEmptyAttachmentPreparation(emailComposeManager);
        when(emailPdfPasswordService.generatePassphrase()).thenReturn(EXAMPLE_GENERATED_VALUE);

        try (MockedStatic<ServletActionContext> servletActionContext = mockStatic(ServletActionContext.class)) {
            servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);

            String viewId = prepare(servletActionContext, request, response);
            MockHttpServletRequest viewRequest = view(servletActionContext, request.getSession(), viewId,
                    new MockHttpServletResponse(), "compose");

            assertThat(viewRequest.getAttribute("emailPDFPassword")).isEqualTo(EXAMPLE_GENERATED_VALUE);
            assertThat(viewRequest.getAttribute("emailPDFPasswordClue"))
                    .isEqualTo(DEFAULT_EMAIL_PDF_PASSWORD_DELIVERY_INSTRUCTION);
            assertThat(viewRequest.getAttribute("fdid")).isEqualTo("456");
            assertThat(viewRequest.getAttribute("openEFormAfterEmail")).isEqualTo(true);
            assertThat(viewRequest.getAttribute("isEmailEncrypted")).isEqualTo(false);
            String emailPDFPasswordToken = (String) viewRequest.getAttribute("emailPDFPasswordToken");
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
            composeState.close();
            verify(emailPdfPasswordService).generatePassphrase();
        }
    }

    @Test
    @DisplayName("should render the same prepared compose on every view request without preparing again")
    void shouldRenderSamePreparedCompose_whenViewRequestedRepeatedly() throws Exception {
        ComposeMocks mocks = registerComposeMocks();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/email/compose");
        request.getSession(true).setAttribute("demographicId", "123");
        request.getSession(false).setAttribute("subjectEmail", "Staged subject");
        request.getSession(false).setAttribute("isEmailEncrypted", false);
        request.getSession(false).setAttribute("bodyEmail", "Staged body");

        try (MockedStatic<ServletActionContext> servletActionContext = mockStatic(ServletActionContext.class)) {
            String viewId = prepare(servletActionContext, request, new MockHttpServletResponse());
            MockHttpServletResponse firstResponse = new MockHttpServletResponse();
            MockHttpServletRequest first = view(servletActionContext, request.getSession(), viewId, firstResponse, "compose");
            MockHttpServletRequest refresh = view(servletActionContext, request.getSession(), viewId,
                    new MockHttpServletResponse(), "compose");

            for (MockHttpServletRequest rendered : List.of(first, refresh)) {
                assertThat(rendered.getAttribute("subjectEmail")).isEqualTo("Staged subject");
                assertThat(rendered.getAttribute("message")).isEqualTo("Staged body");
                assertThat(rendered.getAttribute("emailPDFPassword")).isEqualTo(EXAMPLE_GENERATED_VALUE);
                assertThat(rendered.getAttribute("receiverName")).isEqualTo("Patient One");
            }
            assertThat(refresh.getAttribute(EMAIL_PDF_PASSWORD_TOKEN_PARAM))
                    .isEqualTo(first.getAttribute(EMAIL_PDF_PASSWORD_TOKEN_PARAM));
            assertThat(firstResponse.getHeader("Cache-Control")).isEqualTo("no-store");
            verify(mocks.emailComposeManager(), times(1)).prepareEFormAttachments(any(), any(), any(), any());
            verify(mocks.emailComposeManager(), times(1)).prepareFormAttachments(any(), any(), any(), anyInt(), any());
            verify(mocks.emailPdfPasswordService(), times(1)).generatePassphrase();

            // Rendering consumed nothing: the token still submits exactly once.
            request.setParameter(EMAIL_PDF_PASSWORD_TOKEN_PARAM, (String) first.getAttribute(EMAIL_PDF_PASSWORD_TOKEN_PARAM));
            try (EmailComposeSubmissionStateService.EmailComposeSubmissionState consumed =
                         composeSubmissionStateService.consume(request)) {
                assertThat(consumed).isNotNull();
            }
            assertThat(composeSubmissionStateService.consume(request)).isNull();
        }
    }

    @Test
    @DisplayName("should leave a newly staged compose in the session when a view is rendered or expired")
    void shouldLeaveStagedCompose_whenViewRequested() throws Exception {
        registerComposeMocks();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/email/compose");
        request.getSession(true).setAttribute("demographicId", "123");

        try (MockedStatic<ServletActionContext> servletActionContext = mockStatic(ServletActionContext.class)) {
            String viewId = prepare(servletActionContext, request, new MockHttpServletResponse());
            assertThat(request.getSession().getAttribute("demographicId")).isNull();
            // Another eForm in the same session stages its own compose before this window refreshes.
            request.getSession().setAttribute("demographicId", "789");
            request.getSession().setAttribute("subjectEmail", "Second compose");

            view(servletActionContext, request.getSession(), viewId, new MockHttpServletResponse(), "compose");
            view(servletActionContext, request.getSession(), "unknown-view", new MockHttpServletResponse(), EmailCompose2Action.COMPOSE_EXPIRED_RESULT);

            assertThat(request.getSession().getAttribute("demographicId")).isEqualTo("789");
            assertThat(request.getSession().getAttribute("subjectEmail")).isEqualTo("Second compose");
        } finally {
            composeSubmissionStateService.clear(request.getSession().getId());
        }
    }

    @Test
    @DisplayName("should report the window as expired once its token has been sent")
    void shouldShowExpired_whenViewTokenAlreadyConsumed() throws Exception {
        registerComposeMocks();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/email/compose");
        request.getSession(true).setAttribute("demographicId", "123");

        try (MockedStatic<ServletActionContext> servletActionContext = mockStatic(ServletActionContext.class)) {
            String viewId = prepare(servletActionContext, request, new MockHttpServletResponse());
            MockHttpServletRequest first = view(servletActionContext, request.getSession(), viewId,
                    new MockHttpServletResponse(), "compose");
            request.setParameter(EMAIL_PDF_PASSWORD_TOKEN_PARAM, (String) first.getAttribute(EMAIL_PDF_PASSWORD_TOKEN_PARAM));
            composeSubmissionStateService.consume(request).close();

            MockHttpServletRequest afterSend = view(servletActionContext, request.getSession(), viewId,
                    new MockHttpServletResponse(), EmailCompose2Action.COMPOSE_EXPIRED_RESULT);

            assertThat(afterSend.getAttribute("errorMessage"))
                    .isEqualTo(EmailCompose2Action.EMAIL_COMPOSE_STATE_EXPIRED_MESSAGE);
            assertThat(afterSend.getAttribute(EMAIL_PDF_PASSWORD_TOKEN_PARAM)).isNull();
        }
    }

    @Test
    @DisplayName("should not render a prepared view in another session")
    void shouldShowExpired_whenViewBelongsToAnotherSession() throws Exception {
        registerComposeMocks();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/email/compose");
        request.getSession(true).setAttribute("demographicId", "123");

        try (MockedStatic<ServletActionContext> servletActionContext = mockStatic(ServletActionContext.class)) {
            String viewId = prepare(servletActionContext, request, new MockHttpServletResponse());

            MockHttpServletRequest otherSession = view(servletActionContext,
                    new MockHttpServletRequest().getSession(true), viewId, new MockHttpServletResponse(), EmailCompose2Action.COMPOSE_EXPIRED_RESULT);

            assertThat(otherSession.getAttribute("emailPDFPassword")).isNull();
            assertThat(otherSession.getAttribute("errorMessage"))
                    .isEqualTo(EmailCompose2Action.EMAIL_COMPOSE_STATE_EXPIRED_MESSAGE);
        } finally {
            composeSubmissionStateService.clear(request.getSession().getId());
        }
    }

    @Test
    @DisplayName("should prepare a staged compose only once when the prepare request is repeated")
    void shouldPrepareOnce_whenStagedComposeRequestedTwice() throws Exception {
        ComposeMocks mocks = registerComposeMocks();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/email/compose");
        request.getSession(true).setAttribute("demographicId", "123");

        try (MockedStatic<ServletActionContext> servletActionContext = mockStatic(ServletActionContext.class)) {
            prepare(servletActionContext, request, new MockHttpServletResponse());
            MockHttpServletRequest repeat = new MockHttpServletRequest("GET", "/email/compose");
            repeat.setSession(request.getSession());
            servletActionContext.when(ServletActionContext::getRequest).thenReturn(repeat);
            servletActionContext.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());

            assertThat(new EmailCompose2Action().prepareComposeEFormMailer()).isEqualTo(EmailCompose2Action.COMPOSE_EXPIRED_RESULT);
            assertThat(repeat.getAttribute("errorMessage"))
                    .isEqualTo(EmailCompose2Action.EMAIL_COMPOSE_STATE_EXPIRED_MESSAGE);
            verify(mocks.emailComposeManager(), times(1)).prepareEFormAttachments(any(), any(), any(), any());
            verify(mocks.emailPdfPasswordService(), times(1)).generatePassphrase();
        } finally {
            composeSubmissionStateService.clear(request.getSession().getId());
        }
    }

    @Test
    @DisplayName("should reuse the preview capability issued at preparation and re-issue it only once it stops resolving")
    void shouldReusePreviewCapability_untilItStopsResolving() throws Exception {
        ComposeMocks mocks = registerComposeMocks();
        AtomicReference<Path> ownedPdf = new AtomicReference<>();
        when(mocks.emailComposeManager().prepareEFormAttachments(any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    EmailComposeWorkingDirectory workingDirectory = invocation.getArgument(3);
                    Path owned = workingDirectory.adoptGeneratedPdf(Files.createTempFile("email-view-test-", ".pdf"));
                    ownedPdf.set(owned);
                    return new ArrayList<>(List.of(new EmailAttachment(
                            "letter.pdf", owned.toString(), DocumentType.EFORM, 1)));
                });
        PdfPreviewCapabilityService previews = mocks.pdfPreviewCapabilityService();
        when(previews.issue(any(), any(), any())).thenReturn("preview-1", "preview-2");
        AtomicReference<Boolean> firstStillResolves = new AtomicReference<>(true);
        when(previews.resolve(any(), any(), anyString())).thenAnswer(invocation -> {
            String token = invocation.getArgument(2);
            boolean live = "preview-2".equals(token) || ("preview-1".equals(token) && firstStillResolves.get());
            return live ? ownedPdf.get() : null;
        });
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/email/compose");
        request.getSession(true).setAttribute("demographicId", "123");
        request.getSession(false).setAttribute("attachEFormItSelf", true);
        request.getSession(false).setAttribute("fdid", "456");

        try (MockedStatic<ServletActionContext> servletActionContext = mockStatic(ServletActionContext.class)) {
            String viewId = prepare(servletActionContext, request, new MockHttpServletResponse());
            verify(previews, times(1)).issue(any(), any(), any());

            MockHttpServletRequest first = view(servletActionContext, request.getSession(), viewId,
                    new MockHttpServletResponse(), "compose");
            MockHttpServletRequest refresh = view(servletActionContext, request.getSession(), viewId,
                    new MockHttpServletResponse(), "compose");
            assertThat(attachments(first)).singleElement()
                    .satisfies(a -> assertThat(a.getPreviewToken()).isEqualTo("preview-1"));
            assertThat(attachments(refresh)).singleElement()
                    .satisfies(a -> assertThat(a.getPreviewToken()).isEqualTo("preview-1"));
            verify(previews, times(1)).issue(any(), any(), any());

            firstStillResolves.set(false);
            MockHttpServletRequest afterExpiry = view(servletActionContext, request.getSession(), viewId,
                    new MockHttpServletResponse(), "compose");
            MockHttpServletRequest again = view(servletActionContext, request.getSession(), viewId,
                    new MockHttpServletResponse(), "compose");
            assertThat(attachments(afterExpiry)).singleElement()
                    .satisfies(a -> assertThat(a.getPreviewToken()).isEqualTo("preview-2"));
            assertThat(attachments(again)).singleElement()
                    .satisfies(a -> assertThat(a.getPreviewToken()).isEqualTo("preview-2"));
            verify(previews, times(2)).issue(any(), any(), any());

            request.setParameter(EMAIL_PDF_PASSWORD_TOKEN_PARAM, (String) first.getAttribute(EMAIL_PDF_PASSWORD_TOKEN_PARAM));
            try (EmailComposeSubmissionStateService.EmailComposeSubmissionState consumed =
                         composeSubmissionStateService.consume(request)) {
                assertThat(consumed.emailAttachmentList()).singleElement().satisfies(a -> {
                    assertThat(a.getFilePath()).isEqualTo(ownedPdf.get().toString());
                    assertThat(a.getPreviewToken()).isNull();
                });
            }
        }
    }

    @Test
    @DisplayName("should re-issue a preview that still resolves once less than half its lifetime remains")
    void shouldReissuePreview_whenLessThanHalfItsLifetimeRemains() throws Exception {
        ComposeMocks mocks = registerComposeMocks();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/email/compose");
        AtomicReference<Path> ownedPdf = stageOneEFormAttachment(mocks, request);
        PdfPreviewCapabilityService previews = mocks.pdfPreviewCapabilityService();
        when(previews.issue(any(), any(), any())).thenReturn("preview-1", "preview-2");
        // Every token still resolves: only its age decides.
        when(previews.resolve(any(), any(), anyString())).thenAnswer(invocation -> ownedPdf.get());

        try (MockedStatic<ServletActionContext> servletActionContext = mockStatic(ServletActionContext.class)) {
            String viewId = prepare(servletActionContext, request, new MockHttpServletResponse());
            Map<String, IssuedPreview> stored = composeSubmissionStateService.findView(request, viewId)
                    .state().view().previews();
            long halfLife = PdfPreviewCapabilityService.TTL.toMillis() / 2;

            stored.put(ownedPdf.get().toString(), new IssuedPreview("preview-1", System.currentTimeMillis() - halfLife + 5_000));
            MockHttpServletRequest young = view(servletActionContext, request.getSession(), viewId,
                    new MockHttpServletResponse(), "compose");
            assertThat(attachments(young)).singleElement()
                    .satisfies(a -> assertThat(a.getPreviewToken()).isEqualTo("preview-1"));

            stored.put(ownedPdf.get().toString(), new IssuedPreview("preview-1", System.currentTimeMillis() - halfLife - 5_000));
            MockHttpServletRequest old = view(servletActionContext, request.getSession(), viewId,
                    new MockHttpServletResponse(), "compose");
            assertThat(attachments(old)).singleElement()
                    .satisfies(a -> assertThat(a.getPreviewToken()).isEqualTo("preview-2"));
            verify(previews, times(2)).issue(any(), any(), any());
        } finally {
            composeSubmissionStateService.clear(request.getSession().getId());
        }
    }

    @Test
    @DisplayName("should show the expired page when a prepared file can no longer be previewed")
    void shouldShowExpired_whenPreparedFileIsGone() throws Exception {
        ComposeMocks mocks = registerComposeMocks();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/email/compose");
        stageOneEFormAttachment(mocks, request);
        PdfPreviewCapabilityService previews = mocks.pdfPreviewCapabilityService();
        // The stored capability no longer resolves, and a new one cannot be issued for a missing file.
        when(previews.issue(any(), any(), any()))
                .thenReturn("preview-1")
                .thenThrow(new PDFGenerationException("PDF preview is unavailable"));
        when(previews.resolve(any(), any(), anyString())).thenReturn(null);

        try (MockedStatic<ServletActionContext> servletActionContext = mockStatic(ServletActionContext.class)) {
            String viewId = prepare(servletActionContext, request, new MockHttpServletResponse());

            MockHttpServletRequest stale = view(servletActionContext, request.getSession(), viewId,
                    new MockHttpServletResponse(), EmailCompose2Action.COMPOSE_EXPIRED_RESULT);

            assertThat(stale.getAttribute("errorMessage")).isEqualTo(EmailCompose2Action.EMAIL_COMPOSE_STATE_EXPIRED_MESSAGE);
            assertThat(stale.getAttribute("emailPDFPassword")).isNull();
        } finally {
            composeSubmissionStateService.clear(request.getSession().getId());
        }
    }

    @Test
    @DisplayName("should refuse to prepare a compose for a patient the provider cannot read, before generating any PDF")
    void shouldRejectPrepare_whenPatientReadDenied() throws Exception {
        ComposeMocks mocks = registerComposeMocks();
        EmailComposeSubmissionStateService stateService = spy(composeSubmissionStateService);
        registerMock(EmailComposeSubmissionStateService.class, stateService);
        SecurityInfoManager restricted = mock(SecurityInfoManager.class);
        when(restricted.hasPrivilege(any(), anyString(), anyString(), isNull())).thenReturn(true);
        registerMock(SecurityInfoManager.class, restricted);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/email/compose");
        request.getSession(true).setAttribute("demographicId", "123");

        try (MockedStatic<ServletActionContext> servletActionContext = mockStatic(ServletActionContext.class)) {
            servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContext.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());

            assertThatThrownBy(new EmailCompose2Action()::execute)
                    .isInstanceOf(SecurityException.class)
                    .hasMessage("missing required sec object (_demographic)");
            verify(restricted).hasPrivilege(any(), eq("_demographic"), eq("r"), eq(123));
            verify(stateService, never()).createWorkingDirectory();
            verify(mocks.emailComposeManager(), never()).prepareEFormAttachments(any(), any(), any(), any());
            verify(mocks.pdfPreviewCapabilityService(), never()).issue(any(), any(), any());
        }
    }

    @Test
    @DisplayName("should refuse a prepared view for a patient the provider can no longer read, before issuing a preview")
    void shouldRejectView_whenPatientReadDenied() throws Exception {
        ComposeMocks mocks = registerComposeMocks();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/email/compose");
        stageOneEFormAttachment(mocks, request);
        when(mocks.pdfPreviewCapabilityService().issue(any(), any(), any())).thenReturn("preview-1");

        try (MockedStatic<ServletActionContext> servletActionContext = mockStatic(ServletActionContext.class)) {
            String viewId = prepare(servletActionContext, request, new MockHttpServletResponse());
            SecurityInfoManager restricted = mock(SecurityInfoManager.class);
            when(restricted.hasPrivilege(any(), anyString(), anyString(), isNull())).thenReturn(true);
            registerMock(SecurityInfoManager.class, restricted);
            MockHttpServletRequest viewRequest = new MockHttpServletRequest("GET", "/email/emailComposeAction");
            viewRequest.setSession(request.getSession());
            viewRequest.setParameter(EmailCompose2Action.EMAIL_COMPOSE_VIEW_PARAM, viewId);
            servletActionContext.when(ServletActionContext::getRequest).thenReturn(viewRequest);
            servletActionContext.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());
            EmailCompose2Action action = new EmailCompose2Action();

            assertThatThrownBy(action::execute)
                    .isInstanceOf(SecurityException.class)
                    .hasMessage("missing required sec object (_demographic)");
            assertThat(viewRequest.getAttribute("emailPDFPassword")).isNull();
            verify(restricted).hasPrivilege(any(), eq("_demographic"), eq("r"), eq(123));
            verify(mocks.pdfPreviewCapabilityService(), never()).resolve(any(), any(), any());
            verify(mocks.pdfPreviewCapabilityService(), times(1)).issue(any(), any(), any());
        } finally {
            composeSubmissionStateService.clear(request.getSession().getId());
        }
    }

    @Test
    @DisplayName("should refuse to prepare a compose for a patient whose chart is restricted, before generating any PDF")
    void shouldRejectPrepare_whenPatientRecordAccessDenied() throws Exception {
        ComposeMocks mocks = registerComposeMocks();
        EmailComposeSubmissionStateService stateService = spy(composeSubmissionStateService);
        registerMock(EmailComposeSubmissionStateService.class, stateService);
        SecurityInfoManager restricted = mock(SecurityInfoManager.class);
        when(restricted.hasPrivilege(any(), anyString(), anyString(), isNull())).thenReturn(true);
        when(restricted.hasPrivilege(any(), anyString(), anyString(), anyInt())).thenReturn(true);
        // General demographic read is granted; the chart-level restriction for this patient is not.
        when(restricted.isAllowedAccessToPatientRecord(any(), eq(123))).thenReturn(false);
        registerMock(SecurityInfoManager.class, restricted);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/email/compose");
        request.getSession(true).setAttribute("demographicId", "123");

        try (MockedStatic<ServletActionContext> servletActionContext = mockStatic(ServletActionContext.class)) {
            servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContext.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());

            assertThatThrownBy(new EmailCompose2Action()::execute)
                    .isInstanceOf(SecurityException.class)
                    .hasMessage("Access to the email patient record is denied");
            verify(stateService, never()).createWorkingDirectory();
            verify(mocks.emailComposeManager(), never()).prepareEFormAttachments(any(), any(), any(), any());
            verify(mocks.pdfPreviewCapabilityService(), never()).issue(any(), any(), any());
        }
    }

    @Test
    @DisplayName("should surface a denial while copying previews instead of showing the expired page")
    void shouldSurfaceDenial_whenPreviewCopyIsRefused() throws Exception {
        ComposeMocks mocks = registerComposeMocks();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/email/compose");
        stageOneEFormAttachment(mocks, request);
        when(mocks.pdfPreviewCapabilityService().issue(any(), any(), any())).thenReturn("preview-1");

        try (MockedStatic<ServletActionContext> servletActionContext = mockStatic(ServletActionContext.class)) {
            String viewId = prepare(servletActionContext, request, new MockHttpServletResponse());
            when(mocks.pdfPreviewCapabilityService().resolve(any(), any(), any()))
                    .thenThrow(new SecurityException("provider required"));
            MockHttpServletRequest viewRequest = new MockHttpServletRequest("GET", "/email/emailComposeAction");
            viewRequest.setSession(request.getSession());
            viewRequest.setParameter(EmailCompose2Action.EMAIL_COMPOSE_VIEW_PARAM, viewId);
            servletActionContext.when(ServletActionContext::getRequest).thenReturn(viewRequest);
            servletActionContext.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());
            EmailCompose2Action action = new EmailCompose2Action();

            assertThatThrownBy(action::execute)
                    .isInstanceOf(SecurityException.class)
                    .hasMessage("provider required");
        } finally {
            composeSubmissionStateService.clear(request.getSession().getId());
        }
    }

    @Test
    @DisplayName("should answer 405 to a HEAD and leave the staged compose for the window that asks for it")
    void shouldLeaveStagedCompose_whenRequestIsHead() throws Exception {
        ComposeMocks mocks = registerComposeMocks();
        MockHttpServletRequest request = new MockHttpServletRequest("HEAD", "/email/compose");
        stageOneEFormAttachment(mocks, request);
        MockHttpServletResponse response = new MockHttpServletResponse();

        try (MockedStatic<ServletActionContext> servletActionContext = mockStatic(ServletActionContext.class)) {
            servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);

            String result = new EmailCompose2Action().execute();

            assertThat(result).isEqualTo(EmailCompose2Action.NONE);
            assertThat(response.getStatus()).isEqualTo(405);
            assertThat(response.getHeader("Allow")).isEqualTo("GET, POST");
            assertThat(request.getSession().getAttribute("demographicId")).isNotNull();
            verify(mocks.emailComposeManager(), never()).prepareEFormAttachments(any(), any(), any(), any());
        }
    }

    @Test
    @DisplayName("should refuse a HEAD without email write privilege, not answer 405, and leave the staged compose")
    void shouldRefuseHead_whenEmailWritePrivilegeMissing() throws Exception {
        ComposeMocks mocks = registerComposeMocks();
        MockHttpServletRequest request = new MockHttpServletRequest("HEAD", "/email/compose");
        stageOneEFormAttachment(mocks, request);
        registerMock(SecurityInfoManager.class, mock(SecurityInfoManager.class));
        MockHttpServletResponse response = new MockHttpServletResponse();

        try (MockedStatic<ServletActionContext> servletActionContext = mockStatic(ServletActionContext.class)) {
            servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);

            assertThatThrownBy(new EmailCompose2Action()::execute)
                    .isInstanceOf(SecurityException.class)
                    .hasMessage("missing required sec object (_email)");
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(request.getSession().getAttribute("demographicId")).isNotNull();
        }
    }

    @Test
    @DisplayName("should surface a denial for an attachment and close the working directory, not ask staff to retry")
    void shouldSurfaceDenial_whenAttachmentPreparationIsRefused() throws Exception {
        ComposeMocks mocks = registerComposeMocks();
        EmailComposeSubmissionStateService stateService = spy(composeSubmissionStateService);
        registerMock(EmailComposeSubmissionStateService.class, stateService);
        AtomicReference<EmailComposeWorkingDirectory> created = new AtomicReference<>();
        doAnswer(invocation -> {
            EmailComposeWorkingDirectory directory = spy((EmailComposeWorkingDirectory) invocation.callRealMethod());
            created.set(directory);
            return directory;
        }).when(stateService).createWorkingDirectory();
        when(mocks.emailComposeManager().prepareEFormAttachments(any(), any(), any(), any()))
                .thenThrow(new SecurityException("missing required sec object (_eform)"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/email/compose");
        request.getSession(true).setAttribute("demographicId", "123");

        try (MockedStatic<ServletActionContext> servletActionContext = mockStatic(ServletActionContext.class)) {
            servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContext.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());

            assertThatThrownBy(new EmailCompose2Action()::execute)
                    .isInstanceOf(SecurityException.class)
                    .hasMessage("missing required sec object (_eform)");
            assertThat(request.getAttribute("errorMessage")).isNull();
            verify(created.get()).close();
        }
    }

    @Test
    @DisplayName("should refuse a prepared view without email write privilege before reading it")
    void shouldRejectView_whenEmailWritePrivilegeMissing() throws Exception {
        registerComposeMocks();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/email/compose");
        request.getSession(true).setAttribute("demographicId", "123");

        try (MockedStatic<ServletActionContext> servletActionContext = mockStatic(ServletActionContext.class)) {
            String viewId = prepare(servletActionContext, request, new MockHttpServletResponse());
            registerMock(SecurityInfoManager.class, mock(SecurityInfoManager.class));
            MockHttpServletRequest viewRequest = new MockHttpServletRequest("GET", "/email/emailComposeAction");
            viewRequest.setSession(request.getSession());
            viewRequest.setParameter(EmailCompose2Action.EMAIL_COMPOSE_VIEW_PARAM, viewId);
            servletActionContext.when(ServletActionContext::getRequest).thenReturn(viewRequest);
            servletActionContext.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());
            EmailCompose2Action action = new EmailCompose2Action();

            assertThatThrownBy(action::execute)
                    .isInstanceOf(SecurityException.class)
                    .hasMessage("missing required sec object (_email)");
            assertThat(viewRequest.getAttribute("emailPDFPassword")).isNull();
            assertThat(composeSubmissionStateService.findView(viewRequest, viewId)).isNotNull();
        } finally {
            composeSubmissionStateService.clear(request.getSession().getId());
        }
    }

    @Test
    @DisplayName("should route a request without a view id to preparation and redirect to the new view")
    void shouldRedirectToView_whenExecutedWithoutViewId() throws Exception {
        registerComposeMocks();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/email/compose");
        request.getSession(true).setAttribute("demographicId", "123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        try (MockedStatic<ServletActionContext> servletActionContext = mockStatic(ServletActionContext.class)) {
            servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);

            assertThat(new EmailCompose2Action().execute()).isEqualTo(ActionSupport.NONE);
            assertThat(response.getRedirectedUrl())
                    .startsWith("/email/emailComposeAction?" + EmailCompose2Action.EMAIL_COMPOSE_VIEW_PARAM + "=");
            assertThat(request.getSession().getAttribute("demographicId")).isNull();
        } finally {
            composeSubmissionStateService.clear(request.getSession().getId());
        }
    }

    @Test
    @DisplayName("should keep a compose another window staged when this preparation fails")
    void shouldKeepNewlyStagedCompose_whenPreparationFails() throws Exception {
        ComposeMocks mocks = registerComposeMocks();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/email/compose");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.getSession(true).setAttribute("demographicId", "123");
        when(mocks.emailComposeManager().prepareEDocAttachments(any(), any(), any())).thenAnswer(invocation -> {
            // Another eForm save stages its compose while this one is still generating PDFs.
            request.getSession().setAttribute("demographicId", "789");
            throw new PDFGenerationException("renderer failed");
        });

        try (MockedStatic<ServletActionContext> servletActionContext = mockStatic(ServletActionContext.class)) {
            servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);

            assertThat(new EmailCompose2Action().prepareComposeEFormMailer()).isEqualTo("eFormError");
            assertThat(request.getSession().getAttribute("demographicId")).isEqualTo("789");
        }
    }

    @Test
    @DisplayName("should open with the eForm's footer when the eForm supplies one")
    void shouldPrefillEFormFooter_whenEFormSuppliesOne() throws Exception {
        ComposeMocks mocks = registerComposeMocks();
        when(mocks.emailComposeManager().getAllSenderAccounts())
                .thenReturn(List.of(senderAccount("clinic@example.org")));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/email/compose");
        request.getSession(true).setAttribute("demographicId", "123");
        request.getSession(false).setAttribute("footerEmail", "eForm footer");

        try (MockedStatic<ServletActionContext> servletActionContext = mockStatic(ServletActionContext.class)) {
            String viewId = prepare(servletActionContext, request, new MockHttpServletResponse());
            assertThat(request.getSession().getAttribute("footerEmail")).isNull();
            MockHttpServletRequest rendered = view(servletActionContext, request.getSession(), viewId,
                    new MockHttpServletResponse(), "compose");

            assertThat(rendered.getAttribute("footerEmail")).isEqualTo("eForm footer");
        } finally {
            composeSubmissionStateService.clear(request.getSession().getId());
        }
    }

    @Test
    @DisplayName("should open with an empty footer when the eForm has none")
    void shouldPrefillEmptyFooter_whenNoFooterSupplied() throws Exception {
        ComposeMocks mocks = registerComposeMocks();
        when(mocks.emailComposeManager().getAllSenderAccounts())
                .thenReturn(List.of(senderAccount("clinic@example.org")));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/email/compose");
        request.getSession(true).setAttribute("demographicId", "123");

        try (MockedStatic<ServletActionContext> servletActionContext = mockStatic(ServletActionContext.class)) {
            String viewId = prepare(servletActionContext, request, new MockHttpServletResponse());
            MockHttpServletRequest rendered = view(servletActionContext, request.getSession(), viewId,
                    new MockHttpServletResponse(), "compose");

            assertThat(rendered.getAttribute("footerEmail")).isEqualTo("");
        } finally {
            composeSubmissionStateService.clear(request.getSession().getId());
        }
    }

    @Test
    @DisplayName("should treat a missing or blank eForm footer as none")
    void shouldResolveFooter_fromEFormOnly() {
        assertThat(EmailCompose2Action.resolveComposeFooter(null)).isEmpty();
        assertThat(EmailCompose2Action.resolveComposeFooter("  \n ")).isEmpty();
        assertThat(EmailCompose2Action.resolveComposeFooter("Book online\n")).isEqualTo("Book online\n");
    }

    private static EmailConfig senderAccount(String senderEmail) {
        return new EmailConfig(EmailConfig.EmailType.SMTP, EmailConfig.EmailProvider.LOCAL, senderEmail);
    }

    @SuppressWarnings("unchecked")
    private static List<EmailAttachment> attachments(MockHttpServletRequest rendered) {
        return (List<EmailAttachment>) rendered.getAttribute("emailAttachmentList");
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

            var overflowSession = overflowRequest.getSession();
            List<EmailAttachment> noAttachments = List.of();
            assertThatThrownBy(() -> composeSubmissionStateService.store(
                    overflowSession,
                    "example-overflow-value",
                    DEFAULT_EMAIL_PDF_PASSWORD_DELIVERY_INSTRUCTION,
                    noAttachments))
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
