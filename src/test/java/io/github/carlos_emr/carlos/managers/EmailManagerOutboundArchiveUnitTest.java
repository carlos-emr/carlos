/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
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
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("EmailManager outbound archive")
@Tag("unit")
class EmailManagerOutboundArchiveUnitTest extends CarlosUnitTestBase {

    private static final String PROVIDER_NO = "999998";

    private EmailConfigDaoImpl emailConfigDao;
    private EmailLogDaoImpl emailLogDao;
    private SecurityInfoManager securityInfoManager;
    private OutboundEmailArchiveService outboundEmailArchiveService;
    private LoggedInInfo loggedInInfo;
    private EmailManager emailManager;

    @BeforeEach
    void setUp() {
        emailConfigDao = mock(EmailConfigDaoImpl.class);
        emailLogDao = mock(EmailLogDaoImpl.class);
        securityInfoManager = mock(SecurityInfoManager.class);
        outboundEmailArchiveService = mock(OutboundEmailArchiveService.class);
        loggedInInfo = mock(LoggedInInfo.class);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(JavaMailSender.class, mock(JavaMailSender.class));
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.WRITE, null)).thenReturn(true);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn(PROVIDER_NO);

        emailManager = new EmailManager();
        injectDependency(emailManager, "emailConfigDao", emailConfigDao);
        injectDependency(emailManager, "emailLogDao", emailLogDao);
        injectDependency(emailManager, "caseManagementManager", mock(CaseManagementManager.class));
        injectDependency(emailManager, "demographicManager", mockDemographicManager());
        injectDependency(emailManager, "documentAttachmentManager", mock(DocumentAttachmentManager.class));
        injectDependency(emailManager, "programManager", mock(ProgramManager.class));
        injectDependency(emailManager, "providerManager", mockProviderManager());
        injectDependency(emailManager, "securityInfoManager", securityInfoManager);
        injectDependency(emailManager, "outboundEmailArchiveService", outboundEmailArchiveService);
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
        verify(emailLogDao).updateEmailStatus(44, EmailLog.EmailStatus.FAILED, "Failed to archive outbound email", emailLog.getTimestamp());
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
