/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.email.admin;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.EmailAttachment;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.EmailLog.ChartDisplayOption;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.documentManager.PdfPreviewCapabilityService;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.EmailComposeManager;
import io.github.carlos_emr.carlos.managers.EmailManager;
import io.github.carlos_emr.carlos.managers.FormsManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * The Manage Emails resend when a sent email's attachments cannot be prepared again: staff get a
 * generic message in their language with a reference, and the log the failure's type and the same
 * reference, never the error's own text. A missing email privilege is still a refusal.
 *
 * @since 2026-10-08
 */
@Tag("unit")
@Tag("email")
@DisplayName("ManageEmails2Action resend")
class ManageEmails2ActionResendUnitTest extends CarlosUnitTestBase {

    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    private EmailComposeManager emailComposeManager;
    private DocumentAttachmentManager documentAttachmentManager;
    private SecurityInfoManager securityInfoManager;
    private MockHttpServletRequest request;

    /** A resend of email log 5, for FAKE patient 10001, whose one eForm attachment is 7. */
    private void prepareResend(boolean allowed) {
        emailComposeManager = mock(EmailComposeManager.class);
        documentAttachmentManager = mock(DocumentAttachmentManager.class);
        securityInfoManager = mock(SecurityInfoManager.class);
        registerMock(DemographicManager.class, mock(DemographicManager.class));
        registerMock(EmailComposeManager.class, emailComposeManager);
        registerMock(EmailManager.class, mock(EmailManager.class));
        registerMock(DocumentAttachmentManager.class, documentAttachmentManager);
        registerMock(FormsManager.class, mock(FormsManager.class));
        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(PdfPreviewCapabilityService.class, mock(PdfPreviewCapabilityService.class));
        when(securityInfoManager.hasPrivilege(any(), anyString(), anyString(), nullable(String.class))).thenReturn(allowed);

        Demographic patient = new Demographic();
        patient.setDemographicNo(10001);
        EmailLog emailLog = mock(EmailLog.class);
        when(emailLog.getEmailAttachments())
                .thenReturn(List.of(new EmailAttachment("FAKE-letter.pdf", null, DocumentType.EFORM, 7)));
        when(emailLog.getDemographic()).thenReturn(patient);
        when(emailLog.getChartDisplayOption()).thenReturn(ChartDisplayOption.WITH_FULL_NOTE);
        when(emailComposeManager.prepareEmailForResend(any(), eq(5))).thenReturn(emailLog);
        when(emailComposeManager.getEmailConsentStatus(any(), anyInt())).thenReturn(new String[]{"Consent", "Yes"});
        when(emailComposeManager.getRecipients(any(), anyInt())).thenReturn(new List<?>[]{List.of(), List.of()});
        when(emailComposeManager.getAllSenderAccounts()).thenReturn(List.of());

        request = new MockHttpServletRequest("GET", "/admin/ManageEmails");
        request.setSession(new MockHttpSession());
        request.addParameter("logId", "5");
        request.addHeader("Accept-Language", "fr-CA,fr;q=0.9");
    }

    /** Runs the resend, returning its result and the action's log lines (and whether any had an exception). */
    private String resend(List<String> log, boolean[] anyThrowableLogged) {
        try (MockedStatic<ServletActionContext> servletActionContext = mockStatic(ServletActionContext.class);
                LogCapture capture = LogCapture.forLogger(ManageEmails2Action.class)) {
            servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContext.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());
            try {
                return new ManageEmails2Action().resendEmail();
            } finally {
                log.addAll(capture.messages());
                anyThrowableLogged[0] = capture.events().stream().map(LogEvent::getThrown).anyMatch(thrown -> thrown != null);
            }
        }
    }

    private void assertGenericMessageAndQuietLog(String result, List<String> log, boolean anyThrowableLogged,
            Class<?> causeType) {
        String message = (String) request.getAttribute("emailErrorMessage");
        assertThat(result).isEqualTo("compose");
        assertThat(request.getAttribute("isEmailError")).isEqualTo(true);
        // In the reader's language (French here), ending with the reference.
        assertThat(message).startsWith("Ce courriel d\u00e9j\u00e0 envoy\u00e9 ne peut pas \u00eatre rouvert")
                .doesNotContain("FAKE").matches("(?s).*: " + UUID_PATTERN);
        String reference = message.substring(message.lastIndexOf(' ') + 1);
        assertThat(log).noneSatisfy(line -> assertThat(line).contains("FAKE"));
        assertThat(log).anySatisfy(line -> assertThat(line)
                .contains("causeType=" + causeType.getName())
                .contains("reference=" + reference));
        assertThat(anyThrowableLogged).as("no exception attached to the log line").isFalse();
    }

    @Test
    @DisplayName("should show a generic message and log only the failure's type and reference when attachments fail")
    void shouldShowGenericMessageAndLogNoDetail_whenResendAttachmentsCannotBePrepared() throws Exception {
        prepareResend(true);
        // Stands for an error whose text names a file and repeats document content.
        when(documentAttachmentManager.renderDocument(any(), eq(DocumentType.EFORM), eq(7)))
                .thenThrow(new PDFGenerationException("FAKE-Smith /var/FAKE-path/report.pdf"));
        List<String> log = new ArrayList<>();
        boolean[] anyThrowableLogged = new boolean[1];

        String result = resend(log, anyThrowableLogged);

        assertGenericMessageAndQuietLog(result, log, anyThrowableLogged[0], PDFGenerationException.class);
    }

    @Test
    @DisplayName("should show the same generic message when preparing the attachments fails unexpectedly")
    void shouldShowGenericMessageAndLogNoDetail_whenResendFailsUnexpectedly() throws Exception {
        prepareResend(true);
        when(documentAttachmentManager.renderDocument(any(), eq(DocumentType.EFORM), eq(7)))
                .thenThrow(new IllegalStateException("FAKE-Smith /var/FAKE-path/report.pdf"));
        List<String> log = new ArrayList<>();
        boolean[] anyThrowableLogged = new boolean[1];

        String result = resend(log, anyThrowableLogged);

        assertGenericMessageAndQuietLog(result, log, anyThrowableLogged[0], IllegalStateException.class);
    }

    @Test
    @DisplayName("should refuse, not show the attachment message, when the user may not read emails")
    void shouldRefuse_whenEmailReadPrivilegeMissing() {
        prepareResend(false);

        assertThatThrownBy(() -> resend(new ArrayList<>(), new boolean[1]))
                .isInstanceOf(SecurityException.class);
        assertThat(request.getAttribute("emailErrorMessage")).isNull();
    }
}
