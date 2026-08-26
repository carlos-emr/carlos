/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.carlos.commn.dao.EmailConfigDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.EmailLogDaoImpl;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.EmailLog.ChartDisplayOption;
import io.github.carlos_emr.carlos.commn.model.EmailLog.EmailConsentStatus;
import io.github.carlos_emr.carlos.commn.model.EmailLog.EmailStatus;
import io.github.carlos_emr.carlos.commn.model.EmailLog.TransactionType;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.email.core.EmailConsentResolver;
import io.github.carlos_emr.carlos.email.core.EmailConsentResult;
import io.github.carlos_emr.carlos.email.core.EmailData;
import io.github.carlos_emr.carlos.email.core.EmailSender;
import io.github.carlos_emr.carlos.email.core.EmailSenderFactory;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for send-time email consent enforcement and auditing.
 *
 * @since 2026-07-06
 */
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
    private EmailConsentResolver emailConsentResolver;
    private EmailSenderFactory emailSenderFactory;
    private EmailSender emailSender;
    private LoggedInInfo loggedInInfo;

    @BeforeEach
    void setUp() {
        emailConfigDao = mock(EmailConfigDaoImpl.class);
        emailLogDao = mock(EmailLogDaoImpl.class);
        demographicManager = mock(DemographicManager.class);
        providerManager = mock(ProviderManager2.class);
        securityInfoManager = mock(SecurityInfoManager.class);
        emailConsentResolver = mock(EmailConsentResolver.class);
        emailSenderFactory = mock(EmailSenderFactory.class);
        emailSender = mock(EmailSender.class);
        loggedInInfo = new LoggedInInfo();
        emailManager = new EmailManager(emailConsentResolver, emailSenderFactory);

        injectDependency(emailManager, "emailConfigDao", emailConfigDao);
        injectDependency(emailManager, "emailLogDao", emailLogDao);
        injectDependency(emailManager, "demographicManager", demographicManager);
        injectDependency(emailManager, "providerManager", providerManager);
        injectDependency(emailManager, "securityInfoManager", securityInfoManager);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.WRITE, null)).thenReturn(true);
        when(emailConfigDao.findActiveEmailConfigById(10)).thenReturn(emailConfig());
        when(demographicManager.getDemographic(loggedInInfo, 123)).thenReturn(demographic());
        when(providerManager.getProvider(loggedInInfo, "999998")).thenReturn(provider());
    }

    @Test
    @DisplayName("should block send and skip sender when patient opted out")
    void shouldBlockSendAndSkipSender_whenPatientOptedOut() {
        EmailData emailData = emailData();
        when(emailConsentResolver.resolve(loggedInInfo, 123))
                .thenReturn(new EmailConsentResult("Email", EmailConsentStatus.OPT_OUT, 55, new Date()));

        EmailLog emailLog = emailManager.sendEmail(loggedInInfo, emailData);

        assertThat(emailLog.getStatus()).isEqualTo(EmailStatus.BLOCKED);
        assertThat(emailLog.getConsentStatus()).isEqualTo(EmailConsentStatus.OPT_OUT);
        assertThat(emailLog.getConsentId()).isEqualTo(55);
        verify(emailLogDao).persist(any(EmailLog.class));
        verify(emailLogDao).merge(emailLog);
        verify(emailLogDao).updateEmailStatus(eq(emailLog.getId()), eq(EmailStatus.BLOCKED), any(), any(Date.class));
        verifyNoInteractions(emailSenderFactory, emailSender);
    }

    @Test
    @DisplayName("should block send when patient opted out even with override")
    void shouldBlockSend_whenPatientOptedOutEvenWithOverride() {
        EmailData emailData = emailData();
        emailData.setConsentOverride(true);
        emailData.setConsentOverrideReason("Patient verbally confirmed email consent");
        when(emailConsentResolver.resolve(loggedInInfo, 123))
                .thenReturn(new EmailConsentResult("Email", EmailConsentStatus.OPT_OUT, 55, new Date()));

        EmailLog emailLog = emailManager.sendEmail(loggedInInfo, emailData);

        assertThat(emailLog.getStatus()).isEqualTo(EmailStatus.BLOCKED);
        assertThat(emailLog.getConsentStatus()).isEqualTo(EmailConsentStatus.OPT_OUT);
        assertThat(emailLog.getConsentOverride()).isFalse();
        assertThat(emailLog.getConsentOverrideReason()).isEmpty();
        verifyNoInteractions(emailSenderFactory, emailSender);
    }

    @Test
    @DisplayName("should block send when email consent is not configured even with override")
    void shouldBlockSend_whenEmailConsentNotConfiguredEvenWithOverride() {
        EmailData emailData = emailData();
        emailData.setConsentOverride(true);
        emailData.setConsentOverrideReason("Patient verbally confirmed email consent");
        when(emailConsentResolver.resolve(loggedInInfo, 123))
                .thenReturn(new EmailConsentResult("", EmailConsentStatus.NOT_CONFIGURED, null, null));

        EmailLog emailLog = emailManager.sendEmail(loggedInInfo, emailData);

        assertThat(emailLog.getStatus()).isEqualTo(EmailStatus.BLOCKED);
        assertThat(emailLog.getConsentStatus()).isEqualTo(EmailConsentStatus.NOT_CONFIGURED);
        assertThat(emailLog.getConsentOverride()).isFalse();
        assertThat(emailLog.getConsentOverrideReason()).isEmpty();
        verifyNoInteractions(emailSenderFactory, emailSender);
    }

    @Test
    @DisplayName("should block send and skip sender when consent is unknown without override")
    void shouldBlockSendAndSkipSender_whenConsentUnknownWithoutOverride() {
        EmailData emailData = emailData();
        when(emailConsentResolver.resolve(loggedInInfo, 123))
                .thenReturn(new EmailConsentResult("Email", EmailConsentStatus.UNKNOWN, null, null));

        EmailLog emailLog = emailManager.sendEmail(loggedInInfo, emailData);

        assertThat(emailLog.getStatus()).isEqualTo(EmailStatus.BLOCKED);
        assertThat(emailLog.getConsentStatus()).isEqualTo(EmailConsentStatus.UNKNOWN);
        assertThat(emailLog.getConsentOverride()).isFalse();
        verify(emailSenderFactory, never()).create(any(), any(), any());
        verifyNoInteractions(emailSender);
    }

    @Test
    @DisplayName("should block send when unknown-consent override reason is blank")
    void shouldBlockSend_whenUnknownConsentOverrideReasonIsBlank() {
        EmailData emailData = emailData();
        emailData.setConsentOverride(true);
        emailData.setConsentOverrideReason("   ");
        when(emailConsentResolver.resolve(loggedInInfo, 123))
                .thenReturn(new EmailConsentResult("Email", EmailConsentStatus.UNKNOWN, null, null));

        EmailLog emailLog = emailManager.sendEmail(loggedInInfo, emailData);

        assertThat(emailLog.getStatus()).isEqualTo(EmailStatus.BLOCKED);
        verify(emailSenderFactory, never()).create(any(), any(), any());
        verifyNoInteractions(emailSender);
    }

    @Test
    @DisplayName("should send and persist override when consent is unknown with override")
    void shouldSendAndPersistOverride_whenConsentUnknownWithOverride() throws Exception {
        EmailData emailData = emailData();
        emailData.setConsentOverride(true);
        emailData.setConsentOverrideReason("Patient verbally confirmed email consent");
        when(emailConsentResolver.resolve(loggedInInfo, 123))
                .thenReturn(new EmailConsentResult("Email", EmailConsentStatus.UNKNOWN, null, null));
        when(emailSenderFactory.create(any(), any(), any())).thenReturn(emailSender);

        EmailLog emailLog = emailManager.sendEmail(loggedInInfo, emailData);

        assertThat(emailLog.getStatus()).isEqualTo(EmailStatus.SUCCESS);
        assertThat(emailLog.getConsentStatus()).isEqualTo(EmailConsentStatus.UNKNOWN);
        assertThat(emailLog.getConsentOverride()).isTrue();
        assertThat(emailLog.getConsentOverrideReason()).isEqualTo("Patient verbally confirmed email consent");
        verify(emailSender).send();
        verify(emailLogDao).updateEmailStatus(eq(emailLog.getId()), eq(EmailStatus.SUCCESS), eq(""), any(Date.class));
    }

    private EmailData emailData() {
        EmailData emailData = new EmailData();
        emailData.setSenderConfigId(10);
        emailData.setRecipients(new String[]{"patient@example.org"});
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

    private EmailConfig emailConfig() {
        EmailConfig emailConfig = new EmailConfig();
        emailConfig.setSenderEmail("sender@example.org");
        emailConfig.setSenderFirstName("Sender");
        emailConfig.setSenderLastName("Provider");
        return emailConfig;
    }

    private Demographic demographic() {
        Demographic demographic = new Demographic();
        demographic.setDemographicNo(123);
        demographic.setFirstName("Patient");
        demographic.setLastName("Test");
        return demographic;
    }

    private Provider provider() {
        Provider provider = new Provider();
        provider.setProviderNo("999998");
        provider.setFirstName("Sender");
        provider.setLastName("Provider");
        return provider;
    }
}
