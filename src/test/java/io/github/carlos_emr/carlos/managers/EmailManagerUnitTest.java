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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Date;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.github.carlos_emr.carlos.commn.dao.EmailConfigDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.EmailLogDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.Consent;
import io.github.carlos_emr.carlos.commn.model.ConsentType;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.EmailLog.ChartDisplayOption;
import io.github.carlos_emr.carlos.commn.model.EmailLog.EmailConsentStatus;
import io.github.carlos_emr.carlos.commn.model.EmailLog.EmailStatus;
import io.github.carlos_emr.carlos.commn.model.EmailLog.TransactionType;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.email.core.EmailConfigSecrets;
import io.github.carlos_emr.carlos.email.core.EmailConsentResolver;
import io.github.carlos_emr.carlos.email.core.EmailConsentResult;
import io.github.carlos_emr.carlos.email.core.EmailData;
import io.github.carlos_emr.carlos.email.core.EmailSender;
import io.github.carlos_emr.carlos.email.core.EmailSenderFactory;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.test.util.EncryptionKeyTestSupport;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Unit tests for send-time consent enforcement and email credential migration.
 *
 * @since 2026-07-06
 */
@Tag("unit")
@Tag("fast")
@Tag("email")
@Tag("security")
@DisplayName("EmailManager")
class EmailManagerUnitTest extends CarlosUnitTestBase {

    private static final ObjectMapper MAPPER = new ObjectMapper();

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
    private String originalKey;

    @BeforeEach
    void setUp() throws Exception {
        originalKey = EncryptionKeyTestSupport.seedFreshKey();
        emailConfigDao = mock(EmailConfigDaoImpl.class);
        emailLogDao = mock(EmailLogDaoImpl.class);
        demographicManager = mock(DemographicManager.class);
        providerManager = mock(ProviderManager2.class);
        securityInfoManager = mock(SecurityInfoManager.class);
        emailConsentResolver = mock(EmailConsentResolver.class);
        emailSenderFactory = mock(EmailSenderFactory.class);
        emailSender = mock(EmailSender.class);
        loggedInInfo = new LoggedInInfo();
        initializeEmailManager(emailConsentResolver);
        when(securityInfoManager.hasPrivilege(
                loggedInInfo, "_email", SecurityInfoManager.WRITE, null)).thenReturn(true);
        when(emailConfigDao.findActiveEmailConfigById(10)).thenReturn(emailConfig());
        when(demographicManager.getDemographic(loggedInInfo, 123)).thenReturn(demographic());
        when(providerManager.getProvider(loggedInInfo, "999998")).thenReturn(provider());
    }

    @AfterEach
    void restoreEncryptionKey() {
        EncryptionKeyTestSupport.restoreKey(originalKey);
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
        verify(emailLogDao).updateEmailStatus(
                eq(emailLog.getId()), eq(EmailStatus.BLOCKED), any(), any(Date.class));
        verifyNoInteractions(emailSenderFactory, emailSender);
    }

    @Test
    @DisplayName("should write distinct enriched audit entries when consent blocks a send")
    void shouldWriteDistinctEnrichedAuditEntries_whenConsentBlocksSend() {
        EmailData emailData = emailData();
        when(emailConsentResolver.resolve(loggedInInfo, 123))
                .thenReturn(new EmailConsentResult("Email", EmailConsentStatus.OPT_OUT, 55, new Date()));

        emailManager.sendEmail(loggedInInfo, emailData);

        logActionMock.verify(() -> LogAction.addLog(
                eq(loggedInInfo), eq("EmailManager.prepareEmailForOutbox"), eq("Email"),
                contains("consentStatus=OPT_OUT&override=false"), eq("123"), eq("")));
        logActionMock.verify(() -> LogAction.addLog(
                eq(loggedInInfo), eq("EmailManager.sendEmail.blocked"), eq("Email"),
                contains("consentStatus=OPT_OUT"), eq("123"), eq("")));
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
        assertThat(emailLog.getConsentOverrideReason())
                .isEqualTo("Patient verbally confirmed email consent");
        verify(emailSender).send();
        verify(emailLogDao).updateEmailStatus(
                eq(emailLog.getId()), eq(EmailStatus.SUCCESS), eq(""), any(Date.class));
    }

    @Test
    @DisplayName("should re-evaluate current consent before a repeated send")
    void shouldReEvaluateCurrentConsent_beforeRepeatedSend() throws Exception {
        EmailData emailData = emailData();
        when(emailConsentResolver.resolve(loggedInInfo, 123))
                .thenReturn(new EmailConsentResult(
                        "Email", EmailConsentStatus.OPT_IN, 55, new Date()))
                .thenReturn(new EmailConsentResult(
                        "Email", EmailConsentStatus.OPT_OUT, 55, new Date()));
        when(emailSenderFactory.create(any(), any(), any())).thenReturn(emailSender);

        EmailLog firstAttempt = emailManager.sendEmail(loggedInInfo, emailData);
        EmailLog repeatedAttempt = emailManager.sendEmail(loggedInInfo, emailData);

        assertThat(firstAttempt.getStatus()).isEqualTo(EmailStatus.SUCCESS);
        assertThat(repeatedAttempt.getStatus()).isEqualTo(EmailStatus.BLOCKED);
        assertThat(repeatedAttempt.getConsentStatus()).isEqualTo(EmailConsentStatus.OPT_OUT);
        verify(emailConsentResolver, times(2)).resolve(loggedInInfo, 123);
        verify(emailSenderFactory).create(any(), any(), any());
        verify(emailSender).send();
    }

    @Test
    @Tag("update")
    @DisplayName("should migrate plaintext credentials before an allowed sender is created")
    void shouldMigrateCredentials_beforeAllowedSend() throws Exception {
        EmailConfig config = emailConfig();
        config.setConfigDetailsJson("{\"password\":\"smtp-secret\"}");
        when(emailConfigDao.encryptCredentialsIfUnchanged(eq(10), eq(config.getConfigDetailsJson()), any()))
                .thenReturn(true);
        when(emailConfigDao.findActiveEmailConfigById(10)).thenReturn(config);
        when(emailConsentResolver.resolve(loggedInInfo, 123))
                .thenReturn(new EmailConsentResult(
                        "Email", EmailConsentStatus.OPT_IN, 55, new Date()));
        when(emailSenderFactory.create(any(), same(config), any())).thenReturn(emailSender);

        EmailLog emailLog = emailManager.sendEmail(loggedInInfo, emailData());

        assertThat(emailLog.getStatus()).isEqualTo(EmailStatus.SUCCESS);
        verify(emailConfigDao).encryptCredentialsIfUnchanged(eq(10), eq("{\"password\":\"smtp-secret\"}"), any());
        verify(emailSenderFactory).create(any(), same(config), any());
        verify(emailSender).send();
        assertPasswordDecrypts(config, "smtp-secret");
    }

    @Test
    @Tag("update")
    @DisplayName("should encrypt and persist plaintext transport credentials on first use")
    void shouldPersistEncryptedCredentials_whenConfigIsPlaintext() throws Exception {
        EmailConfig config = config("{\"host\":\"smtp.example.com\",\"password\":\"smtp-secret\"}");
        String original = config.getConfigDetailsJson();
        when(emailConfigDao.encryptCredentialsIfUnchanged(eq(10), eq(original), any())).thenReturn(true);

        emailManager.upgradeConfigCredentialsAtRest(config);

        verify(emailConfigDao).encryptCredentialsIfUnchanged(10, original, config.getConfigDetailsJson());
        assertPasswordDecrypts(config, "smtp-secret");
    }

    @Test
    @Tag("read")
    @DisplayName("should not rewrite a configuration whose credentials are already encrypted")
    void shouldNotPersistConfig_whenCredentialAlreadyEncrypted() throws Exception {
        EmailConfig config = config(EmailConfigSecrets.encryptSecrets("{\"password\":\"smtp-secret\"}"));

        emailManager.upgradeConfigCredentialsAtRest(config);

        verifyNoInteractions(emailConfigDao);
        assertPasswordDecrypts(config, "smtp-secret");
    }

    @Test
    @Tag("update")
    @DisplayName("should restore the usable plaintext value when persistence fails")
    void shouldRestoreOriginalConfig_whenPersistenceFails() {
        String original = "{\"password\":\"smtp-secret\"}";
        EmailConfig config = config(original);
        when(emailConfigDao.encryptCredentialsIfUnchanged(eq(10), eq(original), any()))
                .thenThrow(new RuntimeException("database unavailable"));

        emailManager.upgradeConfigCredentialsAtRest(config);

        assertThat(config.getConfigDetailsJson()).isEqualTo(original);
    }

    @Test
    @Tag("update")
    @DisplayName("should preserve the send configuration when another writer wins the migration race")
    void shouldKeepOriginalConfig_whenMigrationLosesRace() {
        String original = "{\"password\":\"smtp-secret\"}";
        EmailConfig config = config(original);
        when(emailConfigDao.encryptCredentialsIfUnchanged(eq(10), eq(original), any())).thenReturn(false);

        emailManager.upgradeConfigCredentialsAtRest(config);

        assertThat(config.getConfigDetailsJson()).isEqualTo(original);
        verify(emailConfigDao, never()).merge(any());
    }

    @Test
    @DisplayName("should block implied consent without confirmation through the real resolver")
    void shouldBlockSend_whenImpliedConsentHasNoOverride() {
        useImpliedConsentRecord(false);

        EmailLog emailLog = emailManager.sendEmail(loggedInInfo, emailData());

        assertThat(emailLog.getStatus()).isEqualTo(EmailStatus.BLOCKED);
        assertThat(emailLog.getConsentStatus()).isEqualTo(EmailConsentStatus.UNKNOWN);
        assertThat(emailLog.getConsentId()).isEqualTo(55);
        assertThat(emailLog.getConsentLastUpdateDate()).isEqualTo(new Date(1_000L));
        assertThat(emailLog.getConsentOverride()).isFalse();
        verify(emailLogDao).merge(emailLog);
        verifyNoInteractions(emailSenderFactory, emailSender);
    }

    @Test
    @DisplayName("should send implied consent with a documented override through the real resolver")
    void shouldSendWithAuditSnapshot_whenImpliedConsentHasDocumentedOverride() throws Exception {
        useImpliedConsentRecord(false);
        EmailData emailData = emailData();
        emailData.setConsentOverride(true);
        emailData.setConsentOverrideReason("Patient verbally confirmed email consent");
        when(emailSenderFactory.create(any(), any(), any())).thenReturn(emailSender);

        EmailLog emailLog = emailManager.sendEmail(loggedInInfo, emailData);

        assertThat(emailLog.getStatus()).isEqualTo(EmailStatus.SUCCESS);
        assertThat(emailLog.getConsentStatus()).isEqualTo(EmailConsentStatus.UNKNOWN);
        assertThat(emailLog.getConsentId()).isEqualTo(55);
        assertThat(emailLog.getConsentLastUpdateDate()).isEqualTo(new Date(1_000L));
        assertThat(emailLog.getConsentOverride()).isTrue();
        assertThat(emailLog.getConsentOverrideReason()).isEqualTo("Patient verbally confirmed email consent");
        verify(emailLogDao).merge(emailLog);
        verify(emailSender).send();
    }

    @Test
    @DisplayName("should block opted out implied consent even with an override through the real resolver")
    void shouldBlockSend_whenImpliedConsentIsOptedOutEvenWithOverride() {
        useImpliedConsentRecord(true);
        EmailData emailData = emailData();
        emailData.setConsentOverride(true);
        emailData.setConsentOverrideReason("Patient verbally confirmed email consent");

        EmailLog emailLog = emailManager.sendEmail(loggedInInfo, emailData);

        assertThat(emailLog.getStatus()).isEqualTo(EmailStatus.BLOCKED);
        assertThat(emailLog.getConsentStatus()).isEqualTo(EmailConsentStatus.OPT_OUT);
        assertThat(emailLog.getConsentOverride()).isFalse();
        assertThat(emailLog.getConsentOverrideReason()).isEmpty();
        verifyNoInteractions(emailSenderFactory, emailSender);
    }

    private void initializeEmailManager(EmailConsentResolver resolver) {
        emailManager = new EmailManager(resolver, emailSenderFactory);
        injectDependency(emailManager, "emailConfigDao", emailConfigDao);
        injectDependency(emailManager, "emailLogDao", emailLogDao);
        injectDependency(emailManager, "demographicManager", demographicManager);
        injectDependency(emailManager, "providerManager", providerManager);
        injectDependency(emailManager, "securityInfoManager", securityInfoManager);
    }

    private void useImpliedConsentRecord(boolean optout) {
        UserPropertyDAO userPropertyDAO = mock(UserPropertyDAO.class);
        PatientConsentManager patientConsentManager = mock(PatientConsentManager.class);
        UserProperty property = new UserProperty();
        property.setValue("EmailConsent");
        ConsentType consentType = new ConsentType();
        consentType.setName("EmailConsent");
        consentType.setActive(true);
        Consent consent = new Consent();
        ReflectionTestUtils.setField(consent, "id", 55);
        consent.setExplicit(false);
        consent.setOptout(optout);
        consent.setEditDate(new Date(1_000L));
        when(userPropertyDAO.getProp(UserProperty.EMAIL_COMMUNICATION)).thenReturn(property);
        when(patientConsentManager.getConsentType("EmailConsent")).thenReturn(consentType);
        when(patientConsentManager.getConsentByDemographicAndConsentType(loggedInInfo, 123, consentType))
                .thenReturn(consent);
        initializeEmailManager(new EmailConsentResolver(userPropertyDAO, patientConsentManager));
    }

    private EmailData emailData() {
        EmailData emailData = new EmailData();
        emailData.setSenderConfigId(10);
        emailData.setRecipients(new String[] {"patient@example.org"});
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
        injectDependency(emailConfig, "id", 10);
        emailConfig.setSenderEmail("sender@example.org");
        emailConfig.setSenderFirstName("Sender");
        emailConfig.setSenderLastName("Provider");
        return emailConfig;
    }

    private EmailConfig config(String details) {
        EmailConfig config = emailConfig();
        config.setConfigDetailsJson(details);
        return config;
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

    private void assertPasswordDecrypts(EmailConfig config, String plaintext) throws Exception {
        JsonNode password = MAPPER.readTree(config.getConfigDetailsJson()).get("password");
        assertThat(password.asText()).startsWith("{ENC}");
        assertThat(EmailConfigSecrets.decryptSecret(password.asText())).isEqualTo(plaintext);
    }
}
