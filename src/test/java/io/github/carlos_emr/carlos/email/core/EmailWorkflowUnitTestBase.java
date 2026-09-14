package io.github.carlos_emr.carlos.email.core;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import io.github.carlos_emr.carlos.commn.model.EmailAttachment;
import io.github.carlos_emr.carlos.managers.EmailManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.documentManager.PdfPreviewCapabilityService;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/** Real token storage and isolated filesystem for email action contract tests. */
public abstract class EmailWorkflowUnitTestBase extends CarlosUnitTestBase {
    @TempDir protected Path emailTempDir;
    protected EmailComposeSubmissionStateService submissionStates;

    @BeforeEach
    void registerSubmissionServices() throws Exception {
        submissionStates = spy(new EmailComposeSubmissionStateService());
        doAnswer(call -> ReflectionTestUtils.invokeMethod(EmailComposeWorkingDirectory.class,
                "create", emailTempDir)).when(submissionStates).createWorkingDirectory();
        registerMock(EmailComposeSubmissionStateService.class, submissionStates);
        EmailPdfPasswordService passwords = mock(EmailPdfPasswordService.class);
        when(passwords.generatePassphrase()).thenReturn("velvet-orbit-123-cabin-river-456");
        registerMock(EmailPdfPasswordService.class, passwords);
        registerMock(PdfPreviewCapabilityService.class, mock(PdfPreviewCapabilityService.class));
    }

    @AfterEach
    void closeSubmissionServices() {
        submissionStates.shutdown();
    }

    /** Explicitly creates server-side compose state for a valid send fixture. */
    protected String prepareSubmission(MockHttpServletRequest request) {
        return prepareSubmission(request, List.of());
    }

    protected String prepareSubmission(MockHttpServletRequest request, List<EmailAttachment> attachments) {
        var context = "EFORM".equals(request.getParameter("transactionType"))
                ? EmailComposeSubmissionStateService.EmailComposeSubmissionContext.eform(
                    request.getParameter("demographicId"), request.getParameter("fdid"),
                    Boolean.parseBoolean(request.getParameter("openEFormAfterEmail")),
                    Boolean.parseBoolean(request.getParameter("deleteEFormAfterEmail")))
                : EmailComposeSubmissionStateService.EmailComposeSubmissionContext.direct(
                    request.getParameter("demographicId"));
        String token = submissionStates.store(request.getSession(), "original-server-passphrase",
                "Deliver separately", attachments, context);
        request.setParameter(EmailComposeSubmissionStateService.EMAIL_PDF_PASSWORD_TOKEN_PARAM, token);
        request.setParameter("senderConfigId", "1");
        EmailManager manager = (EmailManager) mockedBeans.get(EmailManager.class);
        when(manager.hasActiveEmailConfig(1)).thenReturn(true);
        return token;
    }
}
