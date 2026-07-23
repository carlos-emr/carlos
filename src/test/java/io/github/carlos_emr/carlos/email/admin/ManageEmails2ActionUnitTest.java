package io.github.carlos_emr.carlos.email.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

import io.github.carlos_emr.carlos.commn.model.EmailAttachment;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.email.action.EmailCompose2Action;
import io.github.carlos_emr.carlos.email.core.EmailComposeSubmissionStateService;
import io.github.carlos_emr.carlos.email.core.EmailPdfPasswordService;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.EmailComposeManager;
import io.github.carlos_emr.carlos.managers.EmailManager;
import io.github.carlos_emr.carlos.managers.FormsManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;

@Tag("unit")
@Tag("security")
@DisplayName("ManageEmails2Action")
class ManageEmails2ActionUnitTest extends CarlosUnitTestBase {
    private MockedStatic<ServletActionContext> servletActionContext;
    private EmailComposeSubmissionStateService composeSubmissionStateService;

    @BeforeEach
    void setUp() {
        registerMock(DemographicManager.class, mock(DemographicManager.class));
        registerMock(EmailManager.class, mock(EmailManager.class));
        registerMock(FormsManager.class, mock(FormsManager.class));
        composeSubmissionStateService = new EmailComposeSubmissionStateService();
        registerMock(EmailComposeSubmissionStateService.class, composeSubmissionStateService);
        servletActionContext = mockStatic(ServletActionContext.class);
    }

    @AfterEach
    void tearDown() {
        if (servletActionContext != null) {
            servletActionContext.close();
        }
        if (composeSubmissionStateService != null) {
            composeSubmissionStateService.shutdown();
        }
    }

    @Test
    @DisplayName("should stop resend when PDF refresh fails")
    void shouldStopResend_whenPdfRefreshFails() throws Exception {
        EmailComposeManager emailComposeManager = mock(EmailComposeManager.class);
        DocumentAttachmentManager documentAttachmentManager = mock(DocumentAttachmentManager.class);
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        EmailPdfPasswordService emailPdfPasswordService = mock(EmailPdfPasswordService.class);
        registerMock(EmailComposeManager.class, emailComposeManager);
        registerMock(DocumentAttachmentManager.class, documentAttachmentManager);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(EmailPdfPasswordService.class, emailPdfPasswordService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/email/manage");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setParameter("logId", "42");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());
        EmailLog emailLog = new EmailLog();
        emailLog.setEmailAttachments(List.of(new EmailAttachment("patient-lab.pdf", "/tmp/patient-lab.pdf", DocumentType.DOC, 7)));
        when(emailComposeManager.prepareEmailForResend(any(), anyInt())).thenReturn(emailLog);
        when(securityInfoManager.hasPrivilege(any(), any(), any(), any())).thenReturn(true);
        when(documentAttachmentManager.renderDocument(any(), any(), anyInt()))
                .thenThrow(new PDFGenerationException("render failed for Patient Jane"));
        servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);

        ManageEmails2Action action = new ManageEmails2Action();
        action.request = request;
        action.response = response;

        String result = action.resendEmail();

        assertThat(result).isEqualTo("compose");
        assertThat(request.getAttribute("isEmailError")).isEqualTo(true);
        assertThat((String) request.getAttribute("emailErrorMessage"))
                .isEqualTo("This previously sent email cannot be re-opened for editing/resending. "
                        + "Please generate a new email instead.")
                .doesNotContain("Patient Jane")
                .doesNotContain("render failed");
        assertThat(request.getAttribute(EmailCompose2Action.EMAIL_PDF_PASSWORD_TOKEN_PARAM)).isNull();
        verify(emailPdfPasswordService, never()).generatePassphrase();
    }
}
