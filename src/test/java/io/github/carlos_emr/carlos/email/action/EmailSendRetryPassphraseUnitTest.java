/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.email.action;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.EmailAttachment;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.documentManager.PdfPreviewCapabilityService;
import io.github.carlos_emr.carlos.email.core.EmailComposeSubmissionStateService;
import io.github.carlos_emr.carlos.email.core.EmailComposeSubmissionStateService.EmailComposeSubmissionContext;
import io.github.carlos_emr.carlos.email.core.EmailComposeWorkingDirectory;
import io.github.carlos_emr.carlos.email.core.EmailData;
import io.github.carlos_emr.carlos.email.core.EmailPdfPasswordService;
import io.github.carlos_emr.carlos.email.core.EmailSendResult;
import io.github.carlos_emr.carlos.managers.EformDataManager;
import io.github.carlos_emr.carlos.managers.EmailComposeManager;
import io.github.carlos_emr.carlos.managers.EmailManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("email")
@DisplayName("Single-use email retry and directory ownership")
class EmailSendRetryPassphraseUnitTest extends CarlosUnitTestBase {
    private static final String TOKEN = EmailComposeSubmissionStateService.EMAIL_PDF_PASSWORD_TOKEN_PARAM;
    private static final String FIRST_PASSWORD = "velvet-orbit-123-cabin-river-456";
    private static final String RETRY_PASSWORD = "cabin-lantern-654-river-orbit-321";
    @TempDir Path tempDir;
    private EmailComposeSubmissionStateService states;
    private EmailManager manager;
    private EmailPdfPasswordService passwords;
    private PdfPreviewCapabilityService previews;
    private MockedStatic<ServletActionContext> servlet;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private Path originalPdf;
    private String originalToken;

    @BeforeEach
    void setUp() throws Exception {
        states = spy(new EmailComposeSubmissionStateService());
        doAnswer(call -> ReflectionTestUtils.invokeMethod(EmailComposeWorkingDirectory.class,
                "create", tempDir)).when(states).createWorkingDirectory();
        manager = mock(EmailManager.class);
        passwords = mock(EmailPdfPasswordService.class);
        previews = mock(PdfPreviewCapabilityService.class);
        SecurityInfoManager security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(), anyString(), anyString(), nullable(String.class))).thenReturn(true);
        when(manager.hasActiveEmailConfig(anyInt())).thenReturn(true);
        when(passwords.generatePassphrase()).thenReturn(RETRY_PASSWORD);
        when(previews.issue(any(), any(), any())).thenReturn("replacement-preview");
        registerMock(SecurityInfoManager.class, security);
        registerMock(EmailManager.class, manager);
        registerMock(EformDataManager.class, mock(EformDataManager.class));
        registerMock(EmailComposeManager.class, mock(EmailComposeManager.class));
        registerMock(EmailComposeSubmissionStateService.class, states);
        registerMock(EmailPdfPasswordService.class, passwords);
        registerMock(PdfPreviewCapabilityService.class, previews);
        request = new MockHttpServletRequest("POST", "/email/emailSendAction");
        response = new MockHttpServletResponse();
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());
        request.setParameter("method", "sendDirectEmail");
        request.setParameter("transactionType", "DIRECT");
        request.setParameter("senderConfigId", "1");
        request.setParameter("receiverEmailAddress", "patient@example.invalid");
        request.setParameter("subjectEmail", "Subject");
        request.setParameter("message", "Message to preserve");
        request.setParameter("isEmailEncrypted", "true");
        request.setParameter("isEmailAttachmentEncrypted", "true");
        request.setParameter("patientChartOption", "addFullNote");
        request.setParameter("demographicId", "999"); // Must not replace the token-bound patient.
        request.setParameter("emailPDFPassword", "tampered-password");
        EmailComposeWorkingDirectory directory = states.createWorkingDirectory();
        Path source = tempDir.resolve("source.pdf");
        Files.writeString(source, "original attachment bytes");
        originalPdf = directory.adoptGeneratedPdf(source);
        originalToken = states.store(request.getSession(), FIRST_PASSWORD, "Deliver separately",
                List.of(new EmailAttachment("attachment.pdf", originalPdf.toString(), DocumentType.DOC, 42)),
                EmailComposeSubmissionContext.direct("123"), directory);
        request.setParameter(TOKEN, originalToken);
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
    }

    @AfterEach
    void tearDown() {
        if (servlet != null) servlet.close();
        if (states != null) states.shutdown();
    }

    private EmailLog emailLog(EmailLog.EmailStatus status) {
        EmailLog log = new EmailLog();
        log.setStatus(status);
        log.setIsEncrypted(true);
        log.setChartDisplayOption(EmailLog.ChartDisplayOption.WITH_FULL_NOTE);
        Demographic patient = new Demographic();
        patient.setDemographicNo(123);
        log.setDemographic(patient);
        return log;
    }

    @Test
    @DisplayName("definite failure rotates the token and secret and preserves the original attachment")
    void shouldPrepareFreshRetry_whenSendDefinitelyFails() throws Exception {
        when(manager.sendEmailWithResult(any(), any())).thenAnswer(call -> {
            EmailData data = call.getArgument(1);
            assertThat(data.getPassword()).isEqualTo(FIRST_PASSWORD);
            assertThat(data.getDemographicNo()).isEqualTo(123);
            // Transport encryption mutates a detached attachment, never the cached original.
            data.getAttachments().getFirst().setFilePath(tempDir.resolve("encrypted.pdf").toString());
            return EmailSendResult.failed(emailLog(EmailLog.EmailStatus.FAILED), true);
        });
        assertThat(new EmailSend2Action() {
            @Override protected String encryptedBodyNotice() { return "Encrypted attachment enclosed."; }
        }.execute()).isEqualTo("success");
        assertThat(request.getAttribute("isEmailSuccessful")).isEqualTo(false);
        assertThat(request.getAttribute("message")).isEqualTo("Message to preserve");
        assertThat(request.getAttribute("demographicId")).isEqualTo("123");
        assertThat(request.getAttribute("emailPDFPassword")).isEqualTo(RETRY_PASSWORD);
        assertThat(request.getAttribute("isEmailAutoSend")).isEqualTo(false);
        assertThat(states.consume(request)).isNull();
        String nextToken = (String) request.getAttribute(TOKEN);
        assertThat(nextToken).isNotBlank().isNotEqualTo(originalToken);
        request.setParameter(TOKEN, nextToken);
        Path nextPdf;
        try (var retry = states.consume(request)) {
            assertThat(retry.emailPDFPassword()).isEqualTo(RETRY_PASSWORD);
            nextPdf = Path.of(retry.emailAttachmentList().getFirst().getFilePath());
            assertThat(Files.readString(nextPdf)).isEqualTo("original attachment bytes");
            assertThat(nextPdf).isNotEqualTo(originalPdf);
        }
        assertThat(originalPdf).doesNotExist();
        assertThat(nextPdf).doesNotExist();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("accepted and uncertain sends do not create a retry or reuse the passphrase")
    void shouldNotPrepareRetry_whenAcceptedOrUncertain(boolean accepted) {
        EmailLog log = emailLog(EmailLog.EmailStatus.PENDING);
        when(manager.sendEmailWithResult(any(), any())).thenReturn(accepted
                ? EmailSendResult.accepted(log, false) : EmailSendResult.unconfirmed(log));
        assertThat(new EmailSend2Action() {
            @Override protected String encryptedBodyNotice() { return "Encrypted attachment enclosed."; }
        }.execute()).isEqualTo("success");
        assertThat(request.getAttribute("isEmailSuccessful")).isEqualTo(accepted);
        assertThat(request.getAttribute("isEmailDeliveryUnconfirmed")).isEqualTo(!accepted);
        assertThat(states.consume(request)).isNull();
        verify(passwords, never()).generatePassphrase();
        assertThat(originalPdf).doesNotExist();
    }

    @Test
    @DisplayName("invalid input fails before consuming or deleting the compose state")
    void shouldRetainTokenAndFiles_whenMessageValidationFails() {
        request.setParameter("message", " ");
        assertThat(new EmailSend2Action() {
            @Override protected String encryptedBodyNotice() { return "Encrypted attachment enclosed."; }
        }.execute()).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(400);
        verify(manager, never()).sendEmailWithResult(any(), any());
        try (var original = states.consume(request)) {
            assertThat(original).isNotNull();
            assertThat(original.emailPDFPassword()).isEqualTo(FIRST_PASSWORD);
            assertThat(originalPdf).exists();
        }
        assertThat(originalPdf).doesNotExist();
    }
}
