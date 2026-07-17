/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.managers;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.commn.dao.EmailConfigDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.EmailLogDaoImpl;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.EmailAttachment;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.EmailLog.ChartDisplayOption;
import io.github.carlos_emr.carlos.commn.model.EmailLog.EmailStatus;
import io.github.carlos_emr.carlos.commn.model.EmailLog.TransactionType;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.email.core.EmailData;
import io.github.carlos_emr.carlos.email.core.EmailSender;
import io.github.carlos_emr.carlos.email.core.EmailStatusResult;
import io.github.carlos_emr.carlos.PMmodule.service.ProgramManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("fast")
@Tag("email")
@DisplayName("EmailManager")
class EmailManagerUnitTest extends CarlosUnitTestBase {

    private EmailManager emailManager;
    private EmailConfigDaoImpl emailConfigDao;
    private EmailLogDaoImpl emailLogDao;
    private DemographicManager demographicManager;
    private ProviderManager2 providerManager;
    private SecurityInfoManager securityInfoManager;
    private LoggedInInfo loggedInInfo;
    private Demographic demographic;
    private Provider provider;

    @BeforeEach
    void setUp() {
        emailConfigDao = mock(EmailConfigDaoImpl.class);
        emailLogDao = mock(EmailLogDaoImpl.class);
        demographicManager = mock(DemographicManager.class);
        providerManager = mock(ProviderManager2.class);
        securityInfoManager = mock(SecurityInfoManager.class);
        loggedInInfo = mock(LoggedInInfo.class);
        demographic = new Demographic();
        demographic.setDemographicNo(123);
        demographic.setFirstName("Patient");
        demographic.setLastName("Example");
        provider = new Provider("999998");
        provider.setFirstName("Provider");
        provider.setLastName("Example");

        emailManager = new EmailManager();
        injectDependency(emailManager, "emailConfigDao", emailConfigDao);
        injectDependency(emailManager, "emailLogDao", emailLogDao);
        injectDependency(emailManager, "caseManagementManager", mock(CaseManagementManager.class));
        injectDependency(emailManager, "demographicManager", demographicManager);
        injectDependency(emailManager, "documentAttachmentManager", mock(DocumentAttachmentManager.class));
        injectDependency(emailManager, "programManager", mock(ProgramManager.class));
        injectDependency(emailManager, "providerManager", providerManager);
        injectDependency(emailManager, "securityInfoManager", securityInfoManager);

        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_email"), anyString(), nullable(String.class)))
                .thenReturn(true);
        when(demographicManager.getDemographic(loggedInInfo, 123)).thenReturn(demographic);
        when(providerManager.getProvider(loggedInInfo, "999998")).thenReturn(provider);
    }

    @Test
    @DisplayName("should return failed result when sender config id is missing")
    void shouldReturnFailedResult_whenSenderConfigIdIsMissing() {
        EmailData emailData = emailData(null);
        emailData.setAttachments(Collections.singletonList(new EmailAttachment("lab.pdf", "/tmp/lab.pdf", DocumentType.LAB, 99)));

        EmailLog result;
        try (MockedConstruction<EmailSender> emailSenders = mockConstruction(EmailSender.class)) {
            result = emailManager.sendEmail(loggedInInfo, emailData);

            assertMisconfiguredSenderFailure(result);
            assertThat(emailSenders.constructed()).isEmpty();
        }
        verify(emailConfigDao, never()).findActiveEmailConfigById(anyInt());
        verify(emailLogDao).persist(same(result));
        assertThat(result.getDemographic()).isSameAs(demographic);
        assertThat(result.getProvider()).isSameAs(provider);
        assertThat(result.getEmailAttachments()).singleElement().satisfies(attachment -> {
            assertThat(attachment.getEmailLog()).isSameAs(result);
            assertThat(attachment.getFileName()).isEqualTo("lab.pdf");
            assertThat(attachment.getFilePath()).isEqualTo("/tmp/lab.pdf");
            assertThat(attachment.getDocumentType()).isEqualTo(DocumentType.LAB);
            assertThat(attachment.getDocumentId()).isEqualTo(99);
        });
    }

    @Test
    @DisplayName("should return failed result when sender config is missing or inactive")
    void shouldReturnFailedResult_whenSenderConfigIsMissingOrInactive() {
        EmailData emailData = emailData(123);
        when(emailConfigDao.findActiveEmailConfigById(123)).thenReturn(null);

        EmailLog result;
        try (MockedConstruction<EmailSender> emailSenders = mockConstruction(EmailSender.class)) {
            result = emailManager.sendEmail(loggedInInfo, emailData);

            assertMisconfiguredSenderFailure(result);
            assertThat(emailSenders.constructed()).isEmpty();
        }
        verify(emailConfigDao).findActiveEmailConfigById(123);
        verify(emailLogDao).persist(same(result));
        assertThat(result.getDemographic()).isSameAs(demographic);
        assertThat(result.getProvider()).isSameAs(provider);
    }

    @Test
    @DisplayName("should not expose raw config or secrets in sender config failure")
    void shouldNotExposeRawConfigOrSecrets_whenSenderConfigFails() {
        EmailData emailData = emailData(456);
        emailData.setRecipients(new String[] {"patient@example.invalid"});
        emailData.setAdditionalParams("sendgridApiKey=SG.raw-secret-token;smtpPassword=raw-password");
        when(emailConfigDao.findActiveEmailConfigById(456)).thenReturn(null);

        EmailLog result = emailManager.sendEmail(loggedInInfo, emailData);

        assertThat(result.getErrorMessage()).isEqualTo(EmailManager.SENDER_CONFIG_MISCONFIGURATION_ERROR);
        assertThat(result.getErrorMessage())
                .doesNotContain("456")
                .doesNotContain("patient@example.invalid")
                .doesNotContain("SG.raw-secret-token")
                .doesNotContain("raw-password")
                .doesNotContain("sendgridApiKey")
                .doesNotContain("smtpPassword");
    }

    @Test
    @DisplayName("should return email status result when sender config association is missing")
    void shouldReturnEmailStatusResult_whenSenderConfigAssociationIsMissing() {
        EmailLog emailLog = new EmailLog(null, "", new String[] {"recipient@example.invalid"}, "Subject", "Body", EmailStatus.FAILED);
        emailLog.setDemographic(demographic);
        emailLog.setProvider(provider);
        emailLog.setErrorMessage(EmailManager.SENDER_CONFIG_MISCONFIGURATION_ERROR);
        when(emailLogDao.getEmailStatusByDateDemographicSenderStatus(any(), any(), nullable(String.class), nullable(String.class), nullable(String.class)))
                .thenReturn(Collections.singletonList(emailLog));

        List<EmailStatusResult> results = emailManager.getEmailStatusByDateDemographicSenderStatus(loggedInInfo, "2026-07-16", "2026-07-16", null, null, "FAILED");

        assertThat(results).hasSize(1);
        EmailStatusResult result = results.get(0);
        assertThat(result.getSenderFullName()).isEqualTo("Unknown Sender");
        assertThat(result.getSenderEmail()).isEmpty();
        assertThat(result.getStatus()).isEqualTo(EmailStatus.FAILED);
        assertThat(result.getErrorMessage()).isEqualTo(EmailManager.SENDER_CONFIG_MISCONFIGURATION_ERROR);
    }

    @Test
    @DisplayName("should use alias for email status result when demographic has no legal name")
    void shouldUseAlias_whenEmailStatusDemographicHasNoLegalName() {
        Demographic aliasOnlyDemographic = new Demographic();
        aliasOnlyDemographic.setAlias("  CJ Patient  ");
        EmailLog emailLog = new EmailLog(null, "", new String[] {"recipient@example.invalid"}, "Subject", "Body", EmailStatus.FAILED);
        emailLog.setDemographic(aliasOnlyDemographic);
        emailLog.setProvider(provider);
        emailLog.setErrorMessage(EmailManager.SENDER_CONFIG_MISCONFIGURATION_ERROR);
        when(emailLogDao.getEmailStatusByDateDemographicSenderStatus(any(), any(), nullable(String.class), nullable(String.class), nullable(String.class)))
                .thenReturn(Collections.singletonList(emailLog));

        List<EmailStatusResult> results = emailManager.getEmailStatusByDateDemographicSenderStatus(loggedInInfo, "2026-07-16", "2026-07-16", null, null, "FAILED");

        assertThat(results).hasSize(1);
        EmailStatusResult result = results.get(0);
        assertThat(result.getRecipientFirstName()).isEmpty();
        assertThat(result.getRecipientLastName()).isEqualTo("(CJ Patient)");
        assertThat(result.getRecipientFullName()).isEqualTo("(CJ Patient)");
    }

    @Test
    @DisplayName("should return transient failed result when demographic lookup fails during sender config failure")
    void shouldReturnTransientFailedResult_whenDemographicLookupFailsDuringSenderConfigFailure() {
        EmailData emailData = emailData(null);
        when(demographicManager.getDemographic(loggedInInfo, 123)).thenThrow(new RuntimeException("demographic lookup failed"));

        EmailLog result = emailManager.sendEmail(loggedInInfo, emailData);

        assertMisconfiguredSenderFailure(result);
        verify(emailLogDao, never()).persist(any(EmailLog.class));
        verifyNoMoreInteractions(providerManager);
    }

    @Test
    @DisplayName("should return transient failed result if failed log cannot be persisted")
    void shouldReturnTransientFailedResult_whenFailedLogCannotBePersisted() {
        EmailData emailData = emailData(null);
        doThrow(new RuntimeException("database unavailable")).when(emailLogDao).persist(any(EmailLog.class));

        EmailLog result;
        try (MockedConstruction<EmailSender> emailSenders = mockConstruction(EmailSender.class)) {
            result = emailManager.sendEmail(loggedInInfo, emailData);

            assertMisconfiguredSenderFailure(result);
            assertThat(emailSenders.constructed()).isEmpty();
        }
        assertThat(result.getDemographic()).isNull();
        assertThat(result.getProvider()).isNull();
        verify(emailLogDao).persist(any(EmailLog.class));
    }

    private void assertMisconfiguredSenderFailure(EmailLog result) {
        assertThat(result.getStatus()).isEqualTo(EmailStatus.FAILED);
        assertThat(result.getErrorMessage()).isEqualTo(EmailManager.SENDER_CONFIG_MISCONFIGURATION_ERROR);
        assertThat(result.getEmailConfig()).isNull();
    }

    private EmailData emailData(Integer senderConfigId) {
        EmailData emailData = new EmailData();
        emailData.setSenderConfigId(senderConfigId);
        emailData.setRecipients(new String[] {"recipient@example.invalid"});
        emailData.setSubject("Subject");
        emailData.setBody("Body");
        emailData.setEncryptedMessage("");
        emailData.setPassword("");
        emailData.setPasswordClue("");
        emailData.setIsEncrypted(false);
        emailData.setIsAttachmentEncrypted(false);
        emailData.setChartDisplayOption(ChartDisplayOption.WITHOUT_NOTE);
        emailData.setInternalComment("");
        emailData.setTransactionType(TransactionType.DIRECT);
        emailData.setDemographicNo(123);
        emailData.setProviderNo("999998");
        emailData.setAdditionalParams("");
        emailData.setAttachments(Collections.emptyList());
        return emailData;
    }
}
