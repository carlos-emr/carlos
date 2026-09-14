/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.email.admin;

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

import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Verifies authorization boundaries for administrative email resend operations.
 *
 * @since 2026-08-24
 */
@Tag("unit")
@Tag("fast")
@Tag("email")
@Tag("security")
@DisplayName("Manage email resend authorization")
class ManageEmails2ActionAuthorizationUnitTest extends CarlosUnitTestBase {

    @Test
    @DisplayName("should require a new password when copying an encrypted email")
    void shouldNotExposeStoredPassword_whenCopyingEncryptedEmail() {
        SecurityInfoManager securityInfoManager = createAndRegisterMock(SecurityInfoManager.class);
        EmailComposeManager emailComposeManager = createAndRegisterMock(EmailComposeManager.class);
        createAndRegisterMock(DemographicManager.class);
        createAndRegisterMock(EmailManager.class);
        createAndRegisterMock(DocumentAttachmentManager.class);
        createAndRegisterMock(FormsManager.class);
        createAndRegisterMock(PdfPreviewCapabilityService.class);
        when(securityInfoManager.hasPrivilege(any(), eq("_email"), eq(SecurityInfoManager.READ), isNull()))
                .thenReturn(true);

        EmailLog log = new EmailLog();
        Demographic demographic = new Demographic();
        demographic.setDemographicNo(123);
        log.setDemographic(demographic);
        log.setEmailAttachments(List.of());
        log.setIsEncrypted(true);
        log.setIsAttachmentEncrypted(true);
        log.setBody("");
        log.setEncryptedMessage("Message to copy");
        log.setPassword("historical-pdf-fixture");
        log.setPasswordClue("Use the separately provided clue");
        log.setChartDisplayOption(EmailLog.ChartDisplayOption.WITHOUT_NOTE);
        when(emailComposeManager.prepareEmailForResend(any(), eq(42))).thenReturn(log);
        when(emailComposeManager.getEmailConsentStatus(any(), eq(123)))
                .thenReturn(new String[]{"Email", "OPT_IN", "email.consent.status.optIn"});
        when(emailComposeManager.getRecipients(any(), eq(123)))
                .thenReturn(new List<?>[]{List.of(), List.of()});
        when(emailComposeManager.getAllSenderAccounts()).thenReturn(List.of());

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/email/resend");
        request.setParameter("logId", "42");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());
        request.getSession().setAttribute("emailPDFPassword", "stale-session-fixture");
        MockHttpServletResponse response = new MockHttpServletResponse();

        try (MockedStatic<ServletActionContext> servletActionContext = mockStatic(ServletActionContext.class)) {
            servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);

            assertThat(new ManageEmails2Action().resendEmail()).isEqualTo("compose");
        }

        assertThat(request.getAttribute("emailPDFPassword")).isEqualTo("");
        assertThat(request.getAttribute("emailPDFPasswordClue")).isEqualTo(log.getPasswordClue());
        assertThat(request.getAttribute("message")).isEqualTo("Message to copy");
        assertThat(request.getAttribute("isEmailEncrypted")).isEqualTo(true);
        assertThat(request.getAttribute("isEmailAttachmentEncrypted")).isEqualTo(true);
        assertThat(log.getPassword()).isEqualTo("historical-pdf-fixture");
    }

    @Test
    @DisplayName("should authorize before loading an email for resend")
    void shouldAuthorize_beforeLoadingEmailForResend() {
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        EmailComposeManager emailComposeManager = mock(EmailComposeManager.class);
        registerMock(DemographicManager.class, mock(DemographicManager.class));
        registerMock(EmailComposeManager.class, emailComposeManager);
        registerMock(EmailManager.class, mock(EmailManager.class));
        registerMock(DocumentAttachmentManager.class, mock(DocumentAttachmentManager.class));
        registerMock(FormsManager.class, mock(FormsManager.class));
        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(PdfPreviewCapabilityService.class, mock(PdfPreviewCapabilityService.class));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/email/resend");
        MockHttpServletResponse response = new MockHttpServletResponse();
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());

        try (MockedStatic<ServletActionContext> servletActionContext = mockStatic(ServletActionContext.class)) {
            servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);

            ManageEmails2Action action = new ManageEmails2Action();

            assertThatThrownBy(action::resendEmail)
                    .isInstanceOf(SecurityException.class)
                    .hasMessage("missing required sec object (_email)");
        }

        verify(securityInfoManager).hasPrivilege(
                any(), eq("_email"), eq(SecurityInfoManager.READ), isNull());
        verifyNoInteractions(emailComposeManager);
    }
}
