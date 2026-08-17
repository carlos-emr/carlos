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
package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.carlos.PMmodule.service.ProgramManager;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.commn.dao.EmailConfigDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.EmailLogDaoImpl;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.email.archive.OutboundEmailArchiveDto;
import io.github.carlos_emr.carlos.email.core.EmailData;
import io.github.carlos_emr.carlos.email.core.EmailSender;
import io.github.carlos_emr.carlos.email.helpers.SMTPEmailSender;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.EmailSendingException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("EmailManager outbound archive")
@Tag("unit")
class EmailManagerOutboundArchiveUnitTest extends CarlosUnitTestBase {

    private static final String PROVIDER_NO = "999998";

    private EmailConfigDaoImpl emailConfigDao;
    private EmailLogDaoImpl emailLogDao;
    private SecurityInfoManager securityInfoManager;
    private OutboundEmailArchiveService outboundEmailArchiveService;
    private JavaMailSender javaMailSender;
    private LoggedInInfo loggedInInfo;
    private EmailManager emailManager;

    @BeforeEach
    void setUp() {
        emailConfigDao = mock(EmailConfigDaoImpl.class);
        emailLogDao = mock(EmailLogDaoImpl.class);
        securityInfoManager = mock(SecurityInfoManager.class);
        outboundEmailArchiveService = mock(OutboundEmailArchiveService.class);
        javaMailSender = mock(JavaMailSender.class);
        loggedInInfo = mock(LoggedInInfo.class);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(NioFileManager.class, mock(NioFileManager.class));
        registerMock(JavaMailSender.class, javaMailSender);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.WRITE, null)).thenReturn(true);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn(PROVIDER_NO);

        emailManager = new EmailManager(outboundEmailArchiveService);
        injectDependency(emailManager, "emailConfigDao", emailConfigDao);
        injectDependency(emailManager, "emailLogDao", emailLogDao);
        injectDependency(emailManager, "caseManagementManager", mock(CaseManagementManager.class));
        injectDependency(emailManager, "demographicManager", mockDemographicManager());
        injectDependency(emailManager, "documentAttachmentManager", mock(DocumentAttachmentManager.class));
        injectDependency(emailManager, "programManager", mock(ProgramManager.class));
        injectDependency(emailManager, "providerManager", mockProviderManager());
        injectDependency(emailManager, "securityInfoManager", securityInfoManager);
    }

    @Test
    @DisplayName("should mark SMTP email failed when archive creation fails before send")
    void shouldMarkSmtpEmailFailed_whenArchiveCreationFailsBeforeSend() throws Exception {
        EmailConfig emailConfig = smtpEmailConfig();
        when(emailConfigDao.findActiveEmailConfigById(12)).thenReturn(emailConfig);
        doAnswer(invocation -> {
            EmailLog emailLog = invocation.getArgument(0);
            injectDependency(emailLog, "id", 44);
            return null;
        }).when(emailLogDao).persist(any(EmailLog.class));
        doThrow(new IOException("archive unavailable"))
                .when(outboundEmailArchiveService).archive(eq(loggedInInfo), any(OutboundEmailArchiveDto.class));

        EmailLog emailLog = emailManager.sendEmail(loggedInInfo, emailData());

        assertThat(emailLog.getStatus()).isEqualTo(EmailLog.EmailStatus.FAILED);
        assertThat(emailLog.getErrorMessage()).isEqualTo("Failed to archive outbound email (I/O failure)");
        ArgumentCaptor<OutboundEmailArchiveDto> archiveCaptor = ArgumentCaptor.forClass(OutboundEmailArchiveDto.class);
        verify(outboundEmailArchiveService).archive(eq(loggedInInfo), archiveCaptor.capture());
        assertThat(archiveCaptor.getValue().getContentType()).isEqualTo("message/rfc822");
        verifyNoInteractions(javaMailSender);
        verify(emailLogDao).updateEmailStatus(
                44, EmailLog.EmailStatus.FAILED, "Failed to archive outbound email (I/O failure)", emailLog.getTimestamp());
    }

    @Test
    @DisplayName("should archive SMTP email before sending prepared message")
    void shouldArchiveSmtpEmailBeforeSendingPreparedMessage() throws Exception {
        EmailConfig emailConfig = smtpEmailConfig();
        when(emailConfigDao.findActiveEmailConfigById(12)).thenReturn(emailConfig);
        doAnswer(invocation -> {
            EmailLog emailLog = invocation.getArgument(0);
            injectDependency(emailLog, "id", 45);
            return null;
        }).when(emailLogDao).persist(any(EmailLog.class));

        try (MockedConstruction<SMTPEmailSender> smtpSenders = mockConstruction(
                SMTPEmailSender.class,
                (smtpSender, context) -> when(smtpSender.prepareMessageBytes())
                        .thenReturn("prepared message".getBytes(StandardCharsets.UTF_8)))) {

            EmailLog emailLog = emailManager.sendEmail(loggedInInfo, emailData());

            assertThat(emailLog.getStatus()).isEqualTo(EmailLog.EmailStatus.SUCCESS);
            assertThat(smtpSenders.constructed()).hasSize(1);
            SMTPEmailSender smtpSender = smtpSenders.constructed().get(0);
            verify(smtpSender).prepareMessageBytes();
            org.mockito.InOrder archiveBeforeSend = inOrder(outboundEmailArchiveService, smtpSender);
            archiveBeforeSend.verify(outboundEmailArchiveService)
                    .archive(eq(loggedInInfo), any(OutboundEmailArchiveDto.class));
            archiveBeforeSend.verify(smtpSender).sendPreparedMessage();
            verify(emailLogDao).updateEmailStatus(45, EmailLog.EmailStatus.SUCCESS, "", emailLog.getTimestamp());
        }
    }

    @Test
    @DisplayName("should persist a fixed safe error when SMTP transport returns untrusted text")
    void shouldPersistFixedSafeError_whenSmtpTransportReturnsUntrustedText() throws Exception {
        EmailConfig emailConfig = smtpEmailConfig();
        when(emailConfigDao.findActiveEmailConfigById(12)).thenReturn(emailConfig);
        doAnswer(invocation -> {
            EmailLog emailLog = invocation.getArgument(0);
            injectDependency(emailLog, "id", 46);
            return null;
        }).when(emailLogDao).persist(any(EmailLog.class));
        String transportFailure = "x".repeat(5000);
        try (MockedConstruction<SMTPEmailSender> smtpSenders = mockConstruction(
                SMTPEmailSender.class,
                (smtpSender, context) -> {
                    when(smtpSender.prepareMessageBytes()).thenReturn("prepared message".getBytes(StandardCharsets.UTF_8));
                    doThrow(new EmailSendingException(transportFailure))
                            .when(smtpSender).sendPreparedMessage();
                })) {

            EmailLog emailLog = emailManager.sendEmail(loggedInInfo, emailData());

            assertThat(emailLog.getStatus()).isEqualTo(EmailLog.EmailStatus.FAILED);
            assertThat(emailLog.getErrorMessage()).isEqualTo("Failed to send email (uncategorized delivery failure)");
            verify(emailLogDao).updateEmailStatus(
                    eq(46), eq(EmailLog.EmailStatus.FAILED),
                    eq("Failed to send email (uncategorized delivery failure)"), any());
            verify(outboundEmailArchiveService).archive(eq(loggedInInfo), any(OutboundEmailArchiveDto.class));
        }
    }

    @Test
    @DisplayName("should persist a safe actionable category for SMTP authentication failures")
    void shouldPersistSafeCategory_whenSmtpAuthenticationFails() throws Exception {
        EmailConfig emailConfig = smtpEmailConfig();
        when(emailConfigDao.findActiveEmailConfigById(12)).thenReturn(emailConfig);
        doAnswer(invocation -> {
            EmailLog emailLog = invocation.getArgument(0);
            injectDependency(emailLog, "id", 47);
            return null;
        }).when(emailLogDao).persist(any(EmailLog.class));
        String untrustedProviderText = "credential for patient@example.test was rejected";
        try (MockedConstruction<SMTPEmailSender> smtpSenders = mockConstruction(
                SMTPEmailSender.class,
                (smtpSender, context) -> {
                    when(smtpSender.prepareMessageBytes()).thenReturn("prepared message".getBytes(StandardCharsets.UTF_8));
                    doThrow(new EmailSendingException(
                            "transport failed", new jakarta.mail.AuthenticationFailedException(untrustedProviderText)))
                            .when(smtpSender).sendPreparedMessage();
                })) {

            EmailLog emailLog = emailManager.sendEmail(loggedInInfo, emailData());

            assertThat(emailLog.getErrorMessage()).isEqualTo("Failed to send email (SMTP authentication failure)");
            assertThat(emailLog.getErrorMessage()).doesNotContain(untrustedProviderText);
            verify(emailLogDao).updateEmailStatus(
                    eq(47), eq(EmailLog.EmailStatus.FAILED),
                    eq("Failed to send email (SMTP authentication failure)"), any());
        }
    }

    @Test
    @DisplayName("should report the recipient failure Spring aggregates inside MailSendException")
    void shouldPersistRecipientCategory_whenMailSendExceptionAggregatesTheFailure() throws Exception {
        EmailConfig emailConfig = smtpEmailConfig();
        when(emailConfigDao.findActiveEmailConfigById(12)).thenReturn(emailConfig);
        doAnswer(invocation -> {
            EmailLog emailLog = invocation.getArgument(0);
            injectDependency(emailLog, "id", 48);
            return null;
        }).when(emailLogDao).persist(any(EmailLog.class));

        // JavaMailSenderImpl does not put per-recipient faults on the cause chain: it
        // collects them per message and throws MailSendException(failedMessages) with a
        // null cause. Walking getCause() alone therefore degraded every one of these to
        // the generic "SMTP send failure".
        String untrustedProviderText = "550 5.1.1 <patient@example.test> recipient rejected";
        java.util.Map<Object, Exception> failedMessages = new java.util.LinkedHashMap<>();
        failedMessages.put("prepared-message", new jakarta.mail.SendFailedException(untrustedProviderText));
        org.springframework.mail.MailSendException aggregated =
                new org.springframework.mail.MailSendException(failedMessages);
        assertThat(aggregated.getCause()).as("precondition: Spring leaves the cause chain empty").isNull();

        try (MockedConstruction<SMTPEmailSender> smtpSenders = mockConstruction(
                SMTPEmailSender.class,
                (smtpSender, context) -> {
                    when(smtpSender.prepareMessageBytes()).thenReturn("prepared message".getBytes(StandardCharsets.UTF_8));
                    doThrow(new EmailSendingException("transport failed", aggregated))
                            .when(smtpSender).sendPreparedMessage();
                })) {

            EmailLog emailLog = emailManager.sendEmail(loggedInInfo, emailData());

            assertThat(emailLog.getErrorMessage()).isEqualTo("Failed to send email (SMTP recipient failure)");
            assertThat(emailLog.getErrorMessage()).doesNotContain(untrustedProviderText);
            assertThat(emailLog.getErrorMessage()).doesNotContain("patient@example.test");
        }
    }

    @Test
    @DisplayName("should report the underlying connection failure a MailSendException wraps")
    void shouldPersistConnectionCategory_whenMailSendExceptionWrapsTheCause() throws Exception {
        EmailConfig emailConfig = smtpEmailConfig();
        when(emailConfigDao.findActiveEmailConfigById(12)).thenReturn(emailConfig);
        doAnswer(invocation -> {
            EmailLog emailLog = invocation.getArgument(0);
            injectDependency(emailLog, "id", 49);
            return null;
        }).when(emailLogDao).persist(any(EmailLog.class));

        // The other half of the old bug: MailSendException matched eagerly and
        // short-circuited the search, so even the cases where Spring DOES supply a cause
        // never got walked.
        org.springframework.mail.MailSendException wrapped = new org.springframework.mail.MailSendException(
                "Mail server connection failed",
                new jakarta.mail.MessagingException("connect", new java.net.ConnectException("refused")));

        try (MockedConstruction<SMTPEmailSender> smtpSenders = mockConstruction(
                SMTPEmailSender.class,
                (smtpSender, context) -> {
                    when(smtpSender.prepareMessageBytes()).thenReturn("prepared message".getBytes(StandardCharsets.UTF_8));
                    doThrow(new EmailSendingException("transport failed", wrapped))
                            .when(smtpSender).sendPreparedMessage();
                })) {

            EmailLog emailLog = emailManager.sendEmail(loggedInInfo, emailData());

            assertThat(emailLog.getErrorMessage()).isEqualTo("Failed to send email (connection failure)");
        }
    }

    @Test
    @DisplayName("should not classify a transport failure as an archive failure from provider text")
    void shouldClassifyAsSendFailure_whenTransportErrorTextMimicsTheArchiveMessage() throws Exception {
        EmailConfig emailConfig = smtpEmailConfig();
        when(emailConfigDao.findActiveEmailConfigById(12)).thenReturn(emailConfig);
        doAnswer(invocation -> {
            EmailLog emailLog = invocation.getArgument(0);
            injectDependency(emailLog, "id", 52);
            return null;
        }).when(emailLogDao).persist(any(EmailLog.class));

        // SMTPEmailSender rethrows with e.getMessage() straight from JavaMail, so this text
        // is provider-controlled. Classifying on it let a remote server decide whether its own
        // transport failure was reported as an archive failure.
        try (MockedConstruction<SMTPEmailSender> smtpSenders = mockConstruction(
                SMTPEmailSender.class,
                (smtpSender, context) -> {
                    when(smtpSender.prepareMessageBytes()).thenReturn("prepared message".getBytes(StandardCharsets.UTF_8));
                    doThrow(new EmailSendingException("Failed to archive outbound email"))
                            .when(smtpSender).sendPreparedMessage();
                })) {

            EmailLog emailLog = emailManager.sendEmail(loggedInInfo, emailData());

            assertThat(emailLog.getErrorMessage()).startsWith("Failed to send email");
            assertThat(emailLog.getErrorMessage()).doesNotStartWith("Failed to archive");
        }
    }

    @Test
    @DisplayName("should report SMTP configuration faults as a send failure, not an archive failure")
    void shouldClassifyAsSendFailure_whenMessagePreparationFails() throws Exception {
        EmailConfig emailConfig = smtpEmailConfig();
        when(emailConfigDao.findActiveEmailConfigById(12)).thenReturn(emailConfig);
        doAnswer(invocation -> {
            EmailLog emailLog = invocation.getArgument(0);
            injectDependency(emailLog, "id", 53);
            return null;
        }).when(emailLogDao).persist(any(EmailLog.class));

        // prepareMessageBytes validates SMTP host/port/credentials. A mistyped password is a
        // send-configuration fault; reporting it as an archive failure would send an operator
        // to inspect the archive subsystem instead of the mail account.
        try (MockedConstruction<SMTPEmailSender> smtpSenders = mockConstruction(
                SMTPEmailSender.class,
                (smtpSender, context) -> when(smtpSender.prepareMessageBytes())
                        .thenThrow(new EmailSendingException("Invalid SMTP credentials configured")))) {

            EmailLog emailLog = emailManager.sendEmail(loggedInInfo, emailData());

            assertThat(emailLog.getErrorMessage()).startsWith("Failed to send email");
            assertThat(emailLog.getErrorMessage()).doesNotStartWith("Failed to archive");
            // Fail-closed still holds: nothing was archived and nothing was transmitted.
            verify(outboundEmailArchiveService, never()).archive(any(), any());
            verifyNoInteractions(javaMailSender);
        }
    }

    @Test
    @DisplayName("should record the failed attempt when preparation throws an unchecked exception")
    void shouldRecordFailedAttempt_whenPreparationThrowsUncheckedException() throws Exception {
        EmailConfig emailConfig = smtpEmailConfig();
        when(emailConfigDao.findActiveEmailConfigById(12)).thenReturn(emailConfig);
        doAnswer(invocation -> {
            EmailLog emailLog = invocation.getArgument(0);
            injectDependency(emailLog, "id", 54);
            return null;
        }).when(emailLogDao).persist(any(EmailLog.class));

        // sendEmail catches EmailSendingException only. An unchecked failure during
        // preparation (config JSON parsing, MIME construction) must be converted rather
        // than rethrown, or it escapes sendEmail entirely: the FAILED status update is
        // skipped, the EmailLog keeps its placeholder text, and the caller gets a raw stack.
        try (MockedConstruction<SMTPEmailSender> smtpSenders = mockConstruction(
                SMTPEmailSender.class,
                (smtpSender, context) -> when(smtpSender.prepareMessageBytes())
                        .thenThrow(new IllegalStateException("malformed SMTP config JSON")))) {

            EmailLog emailLog = emailManager.sendEmail(loggedInInfo, emailData());

            assertThat(emailLog.getStatus()).isEqualTo(EmailLog.EmailStatus.FAILED);
            assertThat(emailLog.getErrorMessage()).startsWith("Failed to send email");
            verify(emailLogDao).updateEmailStatus(
                    eq(54), eq(EmailLog.EmailStatus.FAILED), any(String.class), any());
            verify(outboundEmailArchiveService, never()).archive(any(), any());
            verifyNoInteractions(javaMailSender);
        }
    }

    @Test
    @DisplayName("should still record the archive failure when cleanup itself throws")
    void shouldRecordArchiveFailure_whenPreparedCleanupThrows() throws Exception {
        EmailConfig emailConfig = smtpEmailConfig();
        when(emailConfigDao.findActiveEmailConfigById(12)).thenReturn(emailConfig);
        doAnswer(invocation -> {
            EmailLog emailLog = invocation.getArgument(0);
            injectDependency(emailLog, "id", 55);
            return null;
        }).when(emailLogDao).persist(any(EmailLog.class));
        doThrow(new IOException("archive unavailable"))
                .when(outboundEmailArchiveService).archive(eq(loggedInInfo), any(OutboundEmailArchiveDto.class));

        // Cleanup runs on the failure path. If it throws, it must not propagate in place of
        // the archive failure -- that would lose the real fault and skip the FAILED update,
        // leaving an operator with a snapshot-deletion error and no idea the archive broke.
        try (MockedConstruction<SMTPEmailSender> smtpSenders = mockConstruction(
                SMTPEmailSender.class,
                (smtpSender, context) -> {
                    when(smtpSender.prepareMessageBytes()).thenReturn("prepared message".getBytes(StandardCharsets.UTF_8));
                    doThrow(new IllegalStateException("snapshot handle already closed"))
                            .when(smtpSender).discardPreparedMessage();
                })) {

            EmailLog emailLog = emailManager.sendEmail(loggedInInfo, emailData());

            assertThat(emailLog.getStatus()).isEqualTo(EmailLog.EmailStatus.FAILED);
            assertThat(emailLog.getErrorMessage()).isEqualTo("Failed to archive outbound email (I/O failure)");
            verifyNoInteractions(javaMailSender);
        }
    }

    @Test
    @DisplayName("should record the attempt and still propagate when transport throws unchecked")
    void shouldRecordAttemptAndPropagate_whenTransportThrowsUnchecked() throws Exception {
        EmailConfig emailConfig = smtpEmailConfig();
        when(emailConfigDao.findActiveEmailConfigById(12)).thenReturn(emailConfig);
        doAnswer(invocation -> {
            EmailLog emailLog = invocation.getArgument(0);
            injectDependency(emailLog, "id", 56);
            return null;
        }).when(emailLogDao).persist(any(EmailLog.class));

        // A privilege revoked between sendEmail's entry check and transport. The attempt must
        // be recorded, but the SecurityException must still reach the caller -- swallowing it
        // would turn a security signal into a routine failed send.
        try (MockedConstruction<SMTPEmailSender> smtpSenders = mockConstruction(
                SMTPEmailSender.class,
                (smtpSender, context) -> {
                    when(smtpSender.prepareMessageBytes()).thenReturn("prepared message".getBytes(StandardCharsets.UTF_8));
                    doThrow(new SecurityException("missing required sec object (_email)"))
                            .when(smtpSender).sendPreparedMessage();
                })) {

            assertThatThrownBy(() -> emailManager.sendEmail(loggedInInfo, emailData()))
                    .isInstanceOf(SecurityException.class);

            verify(emailLogDao).updateEmailStatus(
                    eq(56), eq(EmailLog.EmailStatus.FAILED), eq("Failed to send email (authorization failure)"), any());
        }
    }

    @Test
    @DisplayName("should follow the Jakarta Mail next-exception chain to the underlying fault")
    void shouldPersistNetworkCategory_whenMessagingExceptionChainsViaNextException() throws Exception {
        EmailConfig emailConfig = smtpEmailConfig();
        when(emailConfigDao.findActiveEmailConfigById(12)).thenReturn(emailConfig);
        doAnswer(invocation -> {
            EmailLog emailLog = invocation.getArgument(0);
            injectDependency(emailLog, "id", 51);
            return null;
        }).when(emailLogDao).persist(any(EmailLog.class));

        // Jakarta Mail chains via setNextException, not initCause. This pins that the
        // plain getCause() walk still reaches it: MessagingException overrides getCause()
        // to return `next`, so no separate getNextException() traversal is required. If a
        // future Jakarta Mail drops that override, this test fails rather than silently
        // degrading every SMTP fault to the generic category.
        jakarta.mail.MessagingException chained = new jakarta.mail.MessagingException("send failed");
        chained.setNextException(new java.net.UnknownHostException("smtp.example.test"));
        assertThat(chained.getCause())
                .as("precondition: MessagingException.getCause() exposes the next exception")
                .isInstanceOf(java.net.UnknownHostException.class);

        try (MockedConstruction<SMTPEmailSender> smtpSenders = mockConstruction(
                SMTPEmailSender.class,
                (smtpSender, context) -> {
                    when(smtpSender.prepareMessageBytes()).thenReturn("prepared message".getBytes(StandardCharsets.UTF_8));
                    doThrow(new EmailSendingException("transport failed", chained))
                            .when(smtpSender).sendPreparedMessage();
                })) {

            EmailLog emailLog = emailManager.sendEmail(loggedInInfo, emailData());

            assertThat(emailLog.getErrorMessage()).isEqualTo("Failed to send email (host lookup failure)");
        }
    }

    @Test
    @DisplayName("should fall back to the generic label when a MailSendException carries nothing specific")
    void shouldPersistGenericCategory_whenMailSendExceptionCarriesNothingSpecific() throws Exception {
        EmailConfig emailConfig = smtpEmailConfig();
        when(emailConfigDao.findActiveEmailConfigById(12)).thenReturn(emailConfig);
        doAnswer(invocation -> {
            EmailLog emailLog = invocation.getArgument(0);
            injectDependency(emailLog, "id", 50);
            return null;
        }).when(emailLogDao).persist(any(EmailLog.class));

        try (MockedConstruction<SMTPEmailSender> smtpSenders = mockConstruction(
                SMTPEmailSender.class,
                (smtpSender, context) -> {
                    when(smtpSender.prepareMessageBytes()).thenReturn("prepared message".getBytes(StandardCharsets.UTF_8));
                    doThrow(new EmailSendingException("transport failed",
                            new org.springframework.mail.MailSendException("bare")))
                            .when(smtpSender).sendPreparedMessage();
                })) {

            EmailLog emailLog = emailManager.sendEmail(loggedInInfo, emailData());

            assertThat(emailLog.getErrorMessage()).isEqualTo("Failed to send email (SMTP send failure)");
        }
    }

    @Test
    @DisplayName("should not archive when the email configuration is missing")
    void shouldNotArchive_whenEmailConfigurationIsMissing() {
        EmailSender emailSender = new EmailSender(loggedInInfo, null, emailData());

        assertThat(emailSender.supportsOutboundArchive()).isFalse();
    }

    private EmailData emailData() {
        EmailData emailData = new EmailData();
        emailData.setSenderConfigId(12);
        emailData.setRecipients(new String[]{"patient@example.test"});
        emailData.setSubject("Test subject");
        emailData.setBody("Body text");
        emailData.setEncryptedMessage("");
        emailData.setPassword("");
        emailData.setPasswordClue("");
        emailData.setAttachments(List.of());
        emailData.setChartDisplayOption(EmailLog.ChartDisplayOption.WITHOUT_NOTE);
        emailData.setTransactionType(EmailLog.TransactionType.DIRECT);
        emailData.setDemographicNo(123);
        emailData.setProviderNo(PROVIDER_NO);
        return emailData;
    }

    private EmailConfig smtpEmailConfig() {
        EmailConfig emailConfig = new EmailConfig(EmailConfig.EmailType.SMTP, EmailConfig.EmailProvider.GMAIL, "provider@example.test");
        emailConfig.setSenderFirstName("Provider");
        emailConfig.setSenderLastName("One");
        emailConfig.setConfigDetailsJson("{\"host\":\"smtp.example.test\",\"port\":\"587\",\"username\":\"user\",\"password\":\"secret\"}");
        return emailConfig;
    }

    private DemographicManager mockDemographicManager() {
        DemographicManager demographicManager = mock(DemographicManager.class);
        when(demographicManager.getDemographic(loggedInInfo, 123)).thenReturn(new Demographic(123));
        return demographicManager;
    }

    private ProviderManager2 mockProviderManager() {
        ProviderManager2 providerManager = mock(ProviderManager2.class);
        when(providerManager.getProvider(loggedInInfo, PROVIDER_NO)).thenReturn(new Provider());
        return providerManager;
    }
}
