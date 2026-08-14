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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
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
        assertThat(emailLog.getErrorMessage()).isEqualTo("Failed to archive outbound email");
        ArgumentCaptor<OutboundEmailArchiveDto> archiveCaptor = ArgumentCaptor.forClass(OutboundEmailArchiveDto.class);
        verify(outboundEmailArchiveService).archive(eq(loggedInInfo), archiveCaptor.capture());
        assertThat(archiveCaptor.getValue().getContentType()).isEqualTo("message/rfc822");
        verifyNoInteractions(javaMailSender);
        verify(emailLogDao).updateEmailStatus(44, EmailLog.EmailStatus.FAILED, "Failed to archive outbound email", emailLog.getTimestamp());
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
                    doThrow(new io.github.carlos_emr.carlos.utility.EmailSendingException(transportFailure))
                            .when(smtpSender).sendPreparedMessage();
                })) {

            EmailLog emailLog = emailManager.sendEmail(loggedInInfo, emailData());

            assertThat(emailLog.getStatus()).isEqualTo(EmailLog.EmailStatus.FAILED);
            assertThat(emailLog.getErrorMessage()).isEqualTo("Failed to send email");
            verify(emailLogDao).updateEmailStatus(
                    eq(46), eq(EmailLog.EmailStatus.FAILED), eq("Failed to send email"), any());
            verify(outboundEmailArchiveService).archive(eq(loggedInInfo), any(OutboundEmailArchiveDto.class));
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
