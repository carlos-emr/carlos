/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.email.action;

import io.github.carlos_emr.carlos.documentManager.PdfPreviewCapabilityService;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.EmailComposeManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import java.util.List;

import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Verifies that the compose screen fails closed when restoring its encryption state.
 *
 * @since 2026-08-24
 */
@Tag("unit")
@Tag("fast")
@Tag("email")
@Tag("security")
@DisplayName("Email compose encryption default")
class EmailComposeEncryptionDefaultUnitTest extends CarlosUnitTestBase {

    @Test
    @DisplayName("should default encryption on when the session flag is missing")
    void shouldDefaultEncryptionOn_whenSessionFlagMissing() throws Exception {
        MockHttpServletRequest request = prepareComposer(null);

        assertThat(request.getAttribute("isEmailEncrypted")).isEqualTo(true);
    }

    @Test
    @DisplayName("should preserve encryption off when the session flag is explicitly false")
    void shouldPreserveEncryptionOff_whenSessionFlagExplicitlyFalse() throws Exception {
        MockHttpServletRequest request = prepareComposer(false);

        assertThat(request.getAttribute("isEmailEncrypted")).isEqualTo(false);
    }

    @Test
    @DisplayName("should force encryption on when legacy protected content is the only message")
    void shouldForceEncryptionOn_whenLegacyProtectedContentIsOnlyMessage() throws Exception {
        MockHttpServletRequest request = prepareComposer(false, null, "Protected clinical content");

        assertThat(request.getAttribute("isEmailEncrypted")).isEqualTo(true);
        assertThat(request.getAttribute("message")).isEqualTo("Protected clinical content");
    }

    private MockHttpServletRequest prepareComposer(Boolean encryptionFlag) throws Exception {
        return prepareComposer(encryptionFlag, null, null);
    }

    private MockHttpServletRequest prepareComposer(
            Boolean encryptionFlag, String bodyEmail, String encryptedMessageEmail) throws Exception {
        DemographicManager demographicManager = mock(DemographicManager.class);
        EmailComposeManager emailComposeManager = mock(EmailComposeManager.class);
        registerMock(DemographicManager.class, demographicManager);
        registerMock(EmailComposeManager.class, emailComposeManager);
        registerMock(SecurityInfoManager.class, mock(SecurityInfoManager.class));
        registerMock(PdfPreviewCapabilityService.class, mock(PdfPreviewCapabilityService.class));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/email/compose");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.getSession(true).setAttribute("demographicId", "123");
        request.getSession(false).setAttribute("emailPDFPassword", "existing-password");
        request.getSession(false).setAttribute("emailPDFPasswordClue", "existing-clue");
        if (encryptionFlag != null) {
            request.getSession(false).setAttribute("isEmailEncrypted", encryptionFlag);
        }
        request.getSession(false).setAttribute("bodyEmail", bodyEmail);
        request.getSession(false).setAttribute("encryptedMessageEmail", encryptedMessageEmail);

        when(emailComposeManager.getEmailConsentStatus(any(), anyInt()))
                .thenReturn(new String[]{"Consent", "Yes"});
        when(demographicManager.getDemographicFormattedName(any(), anyInt()))
                .thenReturn("Patient One");
        when(emailComposeManager.getRecipients(any(), anyInt()))
                .thenReturn(new List<?>[]{List.of(), List.of()});
        when(emailComposeManager.getAllSenderAccounts()).thenReturn(List.of());
        when(emailComposeManager.prepareEFormAttachments(any(), any(), any())).thenReturn(List.of());
        when(emailComposeManager.prepareEDocAttachments(any(), any())).thenReturn(List.of());
        when(emailComposeManager.prepareLabAttachments(any(), any())).thenReturn(List.of());
        when(emailComposeManager.prepareHRMAttachments(any(), any())).thenReturn(List.of());
        when(emailComposeManager.prepareFormAttachments(any(), any(), any(), anyInt())).thenReturn(List.of());

        try (MockedStatic<ServletActionContext> servletActionContext = mockStatic(ServletActionContext.class)) {
            servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);

            EmailCompose2Action action = new EmailCompose2Action();
            assertThat(action.prepareComposeEFormMailer()).isEqualTo("compose");
        }

        return request;
    }
}
