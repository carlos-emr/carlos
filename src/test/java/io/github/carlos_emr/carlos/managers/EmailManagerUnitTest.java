/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.carlos.commn.dao.EmailConfigDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.EmailLogDaoImpl;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.EmailAttachment;
import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.email.archive.OutboundEmailArchiveDto;
import io.github.carlos_emr.carlos.email.core.EmailData;
import io.github.carlos_emr.carlos.email.core.EmailSender;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import io.github.carlos_emr.carlos.utility.EmailSendingException;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("EmailManager")
@Tag("unit")
class EmailManagerUnitTest extends CarlosUnitTestBase {

    private static final String PROVIDER_NO = "999998";

    private EmailConfigDaoImpl emailConfigDao;
    private EmailLogDaoImpl emailLogDao;
    private DemographicManager demographicManager;
    private ProviderManager2 providerManager;
    private SecurityInfoManager securityInfoManager;
    private OutboundEmailArchiveService outboundEmailArchiveService;
    private LoggedInInfo loggedInInfo;
    private EmailManager emailManager;

    @BeforeEach
    void setUp() {
        emailConfigDao = mock(EmailConfigDaoImpl.class);
        emailLogDao = mock(EmailLogDaoImpl.class);
        demographicManager = mock(DemographicManager.class);
        providerManager = mock(ProviderManager2.class);
        securityInfoManager = mock(SecurityInfoManager.class);
        outboundEmailArchiveService = mock(OutboundEmailArchiveService.class);
        loggedInInfo = mock(LoggedInInfo.class);
        emailManager = new EmailManager(outboundEmailArchiveService);

        injectDependency(emailManager, "emailConfigDao", emailConfigDao);
        injectDependency(emailManager, "emailLogDao", emailLogDao);
        injectDependency(emailManager, "demographicManager", demographicManager);
        injectDependency(emailManager, "providerManager", providerManager);
        injectDependency(emailManager, "securityInfoManager", securityInfoManager);
        registerMock(SecurityInfoManager.class, securityInfoManager);

        when(loggedInInfo.getLoggedInProviderNo()).thenReturn(PROVIDER_NO);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.WRITE, null)).thenReturn(true);
    }

    @Test
    @DisplayName("should send prepared payload after archive succeeds")
    void shouldSendPreparedPayload_afterArchiveSucceeds() throws Exception {
        EmailData emailData = sendGridEmailData();
        EmailConfig emailConfig = sendGridEmailConfig();
        OutboundEmailArchiveDto archiveRequest = new OutboundEmailArchiveDto();
        stubPreparedOutbox(emailConfig);

        try (MockedConstruction<EmailSender> mockedEmailSenders = mockConstruction(EmailSender.class, (emailSender, context) -> {
            when(emailSender.supportsOutboundArchive()).thenReturn(true);
            when(emailSender.prepareOutboundArchive(any(EmailLog.class))).thenReturn(archiveRequest);
        })) {
            EmailLog emailLog = emailManager.sendEmail(loggedInInfo, emailData);

            assertThat(emailLog.getStatus()).isEqualTo(EmailLog.EmailStatus.SUCCESS);
            assertThat(emailLog.getErrorMessage()).isEmpty();
            assertThat(mockedEmailSenders.constructed()).hasSize(1);

            EmailSender emailSender = mockedEmailSenders.constructed().get(0);
            var inOrder = inOrder(emailSender, outboundEmailArchiveService);
            inOrder.verify(emailSender).supportsOutboundArchive();
            inOrder.verify(emailSender).prepareOutboundArchive(any(EmailLog.class));
            inOrder.verify(outboundEmailArchiveService).archive(loggedInInfo, archiveRequest);
            inOrder.verify(emailSender).sendPrepared();
            verify(emailSender, never()).send();
            verify(emailLogDao).updateEmailStatus(eq(44), eq(EmailLog.EmailStatus.SUCCESS), eq(""), any(Date.class));
        }
    }

    @Test
    @DisplayName("should persist sanitized archive failure when archive fails")
    void shouldPersistSanitizedArchiveFailure_whenArchiveFails() throws Exception {
        EmailData emailData = sendGridEmailData();
        EmailConfig emailConfig = sendGridEmailConfig();
        stubPreparedOutbox(emailConfig);
        when(outboundEmailArchiveService.archive(eq(loggedInInfo), any(OutboundEmailArchiveDto.class)))
                .thenThrow(new SecurityException("missing required sec object (_edoc)"));

        EmailLog emailLog = emailManager.sendEmail(loggedInInfo, emailData);

        String expectedMessage = "Failed to archive outbound email";
        assertThat(emailLog.getStatus()).isEqualTo(EmailLog.EmailStatus.FAILED);
        assertThat(emailLog.getErrorMessage()).isEqualTo(expectedMessage);
        verify(emailLogDao).updateEmailStatus(eq(44), eq(EmailLog.EmailStatus.FAILED), eq(expectedMessage), any(Date.class));
    }

    @Test
    @DisplayName("should persist sanitized archive failure when payload preparation fails")
    void shouldPersistSanitizedArchiveFailure_whenPayloadPreparationFails() throws Exception {
        EmailData emailData = sendGridEmailData();
        emailData.setAttachments(List.of(new EmailAttachment("sensitive-report.pdf", null, DocumentType.DOC, 77)));
        EmailConfig emailConfig = sendGridEmailConfig();
        stubPreparedOutbox(emailConfig);

        EmailLog emailLog = emailManager.sendEmail(loggedInInfo, emailData);

        String expectedMessage = "Failed to archive outbound email";
        assertThat(emailLog.getStatus()).isEqualTo(EmailLog.EmailStatus.FAILED);
        assertThat(emailLog.getErrorMessage()).isEqualTo(expectedMessage);
        verify(emailLogDao).updateEmailStatus(eq(44), eq(EmailLog.EmailStatus.FAILED), eq(expectedMessage), any(Date.class));
        verify(outboundEmailArchiveService, never()).archive(eq(loggedInInfo), any(OutboundEmailArchiveDto.class));
    }

    @Test
    @DisplayName("should bound stored error message when send failure detail is oversized")
    void shouldBoundStoredErrorMessage_whenSendFailureDetailIsOversized() throws Exception {
        EmailData emailData = sendGridEmailData();
        EmailConfig emailConfig = sendGridEmailConfig();
        OutboundEmailArchiveDto archiveRequest = new OutboundEmailArchiveDto();
        stubPreparedOutbox(emailConfig);
        String oversizedDetail = "x".repeat(5000);

        try (MockedConstruction<EmailSender> mockedEmailSenders = mockConstruction(EmailSender.class, (emailSender, context) -> {
            when(emailSender.supportsOutboundArchive()).thenReturn(true);
            when(emailSender.prepareOutboundArchive(any(EmailLog.class))).thenReturn(archiveRequest);
            doThrow(new EmailSendingException(oversizedDetail)).when(emailSender).sendPrepared();
        })) {
            EmailLog emailLog = emailManager.sendEmail(loggedInInfo, emailData);

            assertThat(emailLog.getStatus()).isEqualTo(EmailLog.EmailStatus.FAILED);
            assertThat(emailLog.getErrorMessage()).hasSize(1000);
            assertThat(emailLog.getErrorMessage()).isEqualTo(oversizedDetail.substring(0, 1000));
            verify(emailLogDao).updateEmailStatus(eq(44), eq(EmailLog.EmailStatus.FAILED), eq(oversizedDetail.substring(0, 1000)), any(Date.class));
        }
    }

    private void stubPreparedOutbox(EmailConfig emailConfig) {
        when(emailConfigDao.findActiveEmailConfigById(7)).thenReturn(emailConfig);
        when(demographicManager.getDemographic(loggedInInfo, 123)).thenReturn(new Demographic(123));
        when(providerManager.getProvider(loggedInInfo, PROVIDER_NO)).thenReturn(new Provider(PROVIDER_NO));
        doAnswer(invocation -> {
            EmailLog emailLog = invocation.getArgument(0);
            injectDependency(emailLog, "id", 44);
            return null;
        }).when(emailLogDao).persist(any(EmailLog.class));
    }

    private EmailData sendGridEmailData() {
        EmailData emailData = new EmailData();
        emailData.setSenderConfigId(7);
        emailData.setRecipients(new String[]{"patient@example.test"});
        emailData.setSubject("Test subject");
        emailData.setBody("Body text");
        emailData.setEncryptedMessage("");
        emailData.setPassword("");
        emailData.setPasswordClue("");
        emailData.setDemographicNo(123);
        emailData.setProviderNo(PROVIDER_NO);
        emailData.setAdditionalParams("");
        emailData.setAttachments(List.of());
        emailData.setChartDisplayOption(EmailLog.ChartDisplayOption.WITHOUT_NOTE);
        emailData.setInternalComment("");
        emailData.setTransactionType(EmailLog.TransactionType.DIRECT);
        return emailData;
    }

    private EmailConfig sendGridEmailConfig() {
        EmailConfig emailConfig = new EmailConfig(EmailConfig.EmailType.API, EmailConfig.EmailProvider.SENDGRID, "provider@example.test");
        emailConfig.setSenderFirstName("Provider");
        emailConfig.setSenderLastName("One");
        return emailConfig;
    }
}
