/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.email.action;

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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
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
        when(emailPdfPasswordService.generatePassphrase()).thenReturn("alpha-bravo-charlie-delta-echo-foxtrot");

        try (MockedStatic<ServletActionContext> servletActionContext = mockStatic(ServletActionContext.class);
             LogCapture capture = LogCapture.forLogger(EmailCompose2Action.class)) {
            servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);

            EmailCompose2Action action = new EmailCompose2Action();

            assertThat(action.prepareComposeEFormMailer()).isEqualTo("compose");
            assertThat(request.getAttribute("fid")).isNull();
            assertThat(request.getAttribute("emailPDFPassword")).isEqualTo("alpha-bravo-charlie-delta-echo-foxtrot");
            assertThat(request.getSession(false).getAttribute("emailPDFPassword")).isEqualTo("alpha-bravo-charlie-delta-echo-foxtrot");
            verify(emailComposeManager, never()).createEmailPDFPassword(any(), anyInt());
            String logged = capture.messages().stream()
                    .filter(message -> message.startsWith("Invalid fid parameter received"))
                    .findFirst()
                    .orElseThrow();
            assertThat(logged).doesNotContain("\r").doesNotContain("\n");
            assertThat(logged).contains("abc\\r\\nforged-fid");
        }
    }
}
