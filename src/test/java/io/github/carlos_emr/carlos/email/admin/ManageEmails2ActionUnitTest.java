/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.email.admin;

import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.documentManager.PdfPreviewCapabilityService;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.EmailComposeManager;
import io.github.carlos_emr.carlos.managers.EmailManager;
import io.github.carlos_emr.carlos.managers.FormsManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("fast")
@Tag("email")
@DisplayName("ManageEmails2Action")
class ManageEmails2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private DemographicManager demographicManager;
    private EmailComposeManager emailComposeManager;
    private EmailManager emailManager;
    private DocumentAttachmentManager documentAttachmentManager;
    private FormsManager formsManager;
    private SecurityInfoManager securityInfoManager;
    private PdfPreviewCapabilityService pdfPreviewCapabilityService;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        demographicManager = mock(DemographicManager.class);
        emailComposeManager = mock(EmailComposeManager.class);
        emailManager = mock(EmailManager.class);
        documentAttachmentManager = mock(DocumentAttachmentManager.class);
        formsManager = mock(FormsManager.class);
        securityInfoManager = mock(SecurityInfoManager.class);
        // Added to ManageEmails2Action's SpringUtils-wired fields by #3193 after this test was
        // written. The test never ran in CI, so the missing registration went unnoticed.
        pdfPreviewCapabilityService = mock(PdfPreviewCapabilityService.class);

        registerMock(DemographicManager.class, demographicManager);
        registerMock(EmailComposeManager.class, emailComposeManager);
        registerMock(EmailManager.class, emailManager);
        registerMock(DocumentAttachmentManager.class, documentAttachmentManager);
        registerMock(FormsManager.class, formsManager);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(PdfPreviewCapabilityService.class, pdfPreviewCapabilityService);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);
    }

    @AfterEach
    void tearDown() {
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
    }

    @Test
    @DisplayName("should show compose error when resending email log without patient context")
    void shouldShowComposeError_whenResendingEmailLogWithoutPatientContext() {
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        request.setParameter("logId", "42");
        when(emailComposeManager.prepareEmailForResend(loggedInInfo, 42)).thenReturn(new EmailLog());

        ManageEmails2Action action = new ManageEmails2Action();
        String result = action.resendEmail();

        assertThat(result).isEqualTo("compose");
        assertThat(request.getAttribute("isEmailError")).isEqualTo(true);
        assertThat(request.getAttribute("emailErrorMessage"))
                .isEqualTo("This email cannot be copied because it is not associated with a patient. Please generate a new email instead.");
        verify(emailComposeManager).prepareEmailForResend(loggedInInfo, 42);
        verifyNoInteractions(demographicManager, documentAttachmentManager, emailManager, formsManager, securityInfoManager);
    }
    @Test
    @DisplayName("should refuse every dispatch without email read privilege")
    void shouldRefuseEveryDispatch_withoutEmailReadPrivilege() {
        // The gap this closes: resendEmail() loaded a patient EmailLog and repopulated the
        // compose page with its subject, body, encrypted message and PDF passphrase before any
        // privilege check ran. Asserting all three dispatch branches, because the check now sits
        // ahead of the branch and a future refactor could easily reinstate a per-branch gate that
        // misses one.
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.READ, null))
                .thenReturn(false);

        for (String method : new String[]{"resendEmail", "fetchEmails", null}) {
            if (method != null) {
                request.setParameter("method", method);
            }
            request.setParameter("logId", "42");

            assertThatThrownBy(() -> new ManageEmails2Action().execute())
                    .as("dispatch method=%s must be refused", method)
                    .isInstanceOf(SecurityException.class)
                    .hasMessage("missing required sec object (_email)");
        }

        // Refused before anything reads or renders patient data.
        verifyNoInteractions(emailComposeManager, demographicManager, emailManager,
                documentAttachmentManager, formsManager);
    }

    @Test
    @DisplayName("should show compose error when the resent email log has a null demographic number")
    void shouldShowComposeError_whenResentEmailLogHasNullDemographicNumber() {
        // Distinct from the no-demographic case above: here the log HAS a demographic, but its
        // number is null. Both must take the same branch, because the code path that follows
        // unboxes that number.
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        request.setParameter("logId", "42");
        EmailLog emailLog = new EmailLog();
        emailLog.setDemographic(new Demographic());
        when(emailComposeManager.prepareEmailForResend(loggedInInfo, 42)).thenReturn(emailLog);

        String result = new ManageEmails2Action().resendEmail();

        assertThat(result).isEqualTo("compose");
        assertThat(request.getAttribute("isEmailError")).isEqualTo(true);
        verify(emailComposeManager).prepareEmailForResend(loggedInInfo, 42);
        verifyNoInteractions(demographicManager, documentAttachmentManager, emailManager, formsManager);
    }
}
