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
 */
package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.PMmodule.service.ProgramManager;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.commn.dao.EmailConfigDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.EmailLogDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.OscarLogDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.email.core.EmailConfigSecrets;
import io.github.carlos_emr.carlos.email.core.EmailConsentResolver;
import io.github.carlos_emr.carlos.email.core.EmailConsentResult;
import io.github.carlos_emr.carlos.email.core.EmailData;
import io.github.carlos_emr.carlos.email.core.EmailSendResult;
import io.github.carlos_emr.carlos.email.core.EmailSenderFactory;
import io.github.carlos_emr.carlos.email.helpers.SMTPEmailSender;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.test.util.EncryptionKeyTestSupport;
import io.github.carlos_emr.carlos.utility.EncryptionUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedConstruction;
import org.springframework.mail.javamail.JavaMailSender;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the #3673 policy that credentialed email needs the application encryption key: warn by
 * default, refuse before any transport when {@code email.credentials.require_encryption_key=true}.
 *
 * @since 2026-09-24
 */
@DisplayName("EmailManager credential encryption key policy")
@Tag("unit")
@Tag("email")
class EmailManagerCredentialKeyUnitTest extends CarlosUnitTestBase {

    private static final String PROVIDER_NO = "999998";
    private static final String PLAINTEXT_SMTP =
            "{\"host\":\"smtp.example.test\",\"port\":\"587\",\"username\":\"user\",\"password\":\"plain-secret\"}";

    private String originalKey;
    private String originalSetting;
    private EmailConfigDaoImpl emailConfigDao;
    private EmailLogDaoImpl emailLogDao;
    private LoggedInInfo loggedInInfo;
    private OutboundEmailArchiveService archiveService;
    private EmailManager emailManager;

    @BeforeEach
    void setUp() {
        CarlosProperties properties = CarlosProperties.getInstance();
        originalKey = properties.getProperty(EncryptionUtils.SECRET_KEY_ENV_VAR);
        originalSetting = properties.getProperty(EmailManager.REQUIRE_CREDENTIAL_KEY_PROPERTY);

        emailConfigDao = mock(EmailConfigDaoImpl.class);
        emailLogDao = mock(EmailLogDaoImpl.class);
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        loggedInInfo = mock(LoggedInInfo.class);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(NioFileManager.class, mock(NioFileManager.class));
        registerMock(JavaMailSender.class, mock(JavaMailSender.class));
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.WRITE, null)).thenReturn(true);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn(PROVIDER_NO);

        EmailConsentResolver consentResolver = mock(EmailConsentResolver.class);
        when(consentResolver.resolve(any(), any())).thenReturn(
                new EmailConsentResult("Email", EmailLog.EmailConsentStatus.OPT_IN, null, null));
        archiveService = mock(OutboundEmailArchiveService.class);
        emailManager = new EmailManager(consentResolver, new EmailSenderFactory(), securityInfoManager, archiveService);
        injectDependency(emailManager, "oscarLogDao", mock(OscarLogDao.class));
        injectDependency(emailManager, "emailConfigDao", emailConfigDao);
        injectDependency(emailManager, "emailLogDao", emailLogDao);
        injectDependency(emailManager, "caseManagementManager", mock(CaseManagementManager.class));
        DemographicManager demographicManager = mock(DemographicManager.class);
        when(demographicManager.getDemographic(loggedInInfo, 123)).thenReturn(new Demographic(123));
        injectDependency(emailManager, "demographicManager", demographicManager);
        injectDependency(emailManager, "documentAttachmentManager", mock(DocumentAttachmentManager.class));
        injectDependency(emailManager, "programManager", mock(ProgramManager.class));
        ProviderManager2 providerManager = mock(ProviderManager2.class);
        when(providerManager.getProvider(loggedInInfo, PROVIDER_NO)).thenReturn(new Provider());
        injectDependency(emailManager, "providerManager", providerManager);
        injectDependency(emailManager, "securityInfoManager", securityInfoManager);
        when(emailLogDao.transitionEmailStatus(any(), any(), any(), any(), any())).thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        CarlosProperties properties = CarlosProperties.getInstance();
        if (originalSetting != null) {
            properties.setProperty(EmailManager.REQUIRE_CREDENTIAL_KEY_PROPERTY, originalSetting);
        } else {
            properties.remove(EmailManager.REQUIRE_CREDENTIAL_KEY_PROPERTY);
        }
        EncryptionKeyTestSupport.restoreKey(originalKey);
    }

    private static void removeKey() {
        CarlosProperties.getInstance().remove(EncryptionUtils.SECRET_KEY_ENV_VAR);
        EncryptionUtils.prepareSecretKeySpec();
    }

    private static void requireKey(boolean enforced) {
        if (enforced) {
            CarlosProperties.getInstance().setProperty(EmailManager.REQUIRE_CREDENTIAL_KEY_PROPERTY, "true");
        } else {
            CarlosProperties.getInstance().remove(EmailManager.REQUIRE_CREDENTIAL_KEY_PROPERTY);
        }
    }

    private static EmailConfig config(EmailConfig.EmailType type, EmailConfig.EmailProvider provider, String details) {
        EmailConfig config = new EmailConfig(type, provider, "provider@example.test");
        config.setSenderFirstName("Provider");
        config.setSenderLastName("One");
        config.setConfigDetailsJson(details);
        return config;
    }

    private EmailConfig plaintextSmtp() {
        EmailConfig config = config(EmailConfig.EmailType.SMTP, EmailConfig.EmailProvider.GMAIL, PLAINTEXT_SMTP);
        injectDependency(config, "id", 12);
        return config;
    }

    @Nested
    @DisplayName("credentialKeyRefusal")
    class Policy {

        @Test
        @DisplayName("should allow plaintext credentials when the key is configured, even with enforcement on")
        void shouldAllowPlaintextCredentials_whenKeyConfigured() throws Exception {
            EncryptionKeyTestSupport.seedFreshKey();
            requireKey(true);

            assertThat(emailManager.credentialKeyRefusal(plaintextSmtp())).isNull();
        }

        @Test
        @DisplayName("should allow encrypted credentials that decrypt with the current key")
        void shouldAllowEncryptedCredentials_whenKeyMatches() throws Exception {
            EncryptionKeyTestSupport.seedFreshKey();
            requireKey(true);
            EmailConfig config = config(EmailConfig.EmailType.SMTP, EmailConfig.EmailProvider.GMAIL,
                    EmailConfigSecrets.encryptSecrets(PLAINTEXT_SMTP));

            assertThat(emailManager.credentialKeyRefusal(config)).isNull();
        }

        @Test
        @DisplayName("should refuse encrypted credentials after the key was replaced, whatever the setting")
        void shouldRefuseEncryptedCredentials_whenKeyWasReplaced() throws Exception {
            EncryptionKeyTestSupport.seedFreshKey();
            String encrypted = EmailConfigSecrets.encryptSecrets(PLAINTEXT_SMTP);
            EncryptionKeyTestSupport.seedFreshKey();
            requireKey(false);
            EmailConfig config = config(EmailConfig.EmailType.SMTP, EmailConfig.EmailProvider.GMAIL, encrypted);
            injectDependency(config, "id", 14);

            try (LogCapture capture = LogCapture.forLogger(EmailManager.class)) {
                assertThat(emailManager.credentialKeyRefusal(config)).isEqualTo(EmailManager.CREDENTIAL_KEY_MISMATCH_ERROR);

                assertThat(capture.messages()).anySatisfy(message -> assertThat(message)
                        .contains("config id=14").contains("Restore the original key"));
                assertThat(capture.messages()).noneSatisfy(message -> assertThat(message)
                        .containsAnyOf("plain-secret", "{ENC}", "{\""));
            }
        }

        @Test
        @DisplayName("should refuse encrypted credentials when no key is available")
        void shouldRefuseEncryptedCredentials_whenKeyMissing() throws Exception {
            EncryptionKeyTestSupport.seedFreshKey();
            String encrypted = EmailConfigSecrets.encryptSecrets(PLAINTEXT_SMTP);
            removeKey();
            requireKey(false);
            EmailConfig config = config(EmailConfig.EmailType.SMTP, EmailConfig.EmailProvider.GMAIL, encrypted);

            assertThat(emailManager.credentialKeyRefusal(config)).isEqualTo(EmailManager.CREDENTIAL_KEY_MISMATCH_ERROR);
        }

        @Test
        @DisplayName("should never refuse an unauthenticated LOCAL relay")
        void shouldAllowLocalRelay_whenKeyMissingAndEnforced() {
            removeKey();
            requireKey(true);
            EmailConfig relay = config(EmailConfig.EmailType.SMTP, EmailConfig.EmailProvider.LOCAL,
                    "{\"host\":\"127.0.0.1\",\"port\":\"25\"}");

            assertThat(emailManager.credentialKeyRefusal(relay)).isNull();
        }

        @Test
        @DisplayName("should not refuse a LOCAL relay that carries an unused leftover password")
        void shouldAllowLocalRelay_whenItHoldsUnusedPassword() {
            removeKey();
            requireKey(true);
            EmailConfig relay = config(EmailConfig.EmailType.SMTP, EmailConfig.EmailProvider.LOCAL,
                    "{\"host\":\"127.0.0.1\",\"port\":\"25\",\"password\":\"stale-secret\"}");
            injectDependency(relay, "id", 15);

            try (LogCapture capture = LogCapture.forLogger(EmailManager.class)) {
                assertThat(emailManager.credentialKeyRefusal(relay)).isNull();

                assertThat(capture.messages()).anySatisfy(message -> assertThat(message)
                        .contains("config id=15").contains("never uses").doesNotContain("stale-secret"));
            }
        }

        @Test
        @DisplayName("should refuse plaintext SMTP credentials when the key is missing and enforcement is on")
        void shouldRefusePlaintextCredentials_whenKeyMissingAndEnforced() {
            removeKey();
            requireKey(true);

            try (LogCapture capture = LogCapture.forLogger(EmailManager.class)) {
                assertThat(emailManager.credentialKeyRefusal(plaintextSmtp())).isEqualTo(EmailManager.CREDENTIAL_KEY_REQUIRED_ERROR);

                assertThat(capture.messages()).anySatisfy(message -> assertThat(message)
                        .contains("refused").contains("config id=12").contains(EncryptionUtils.SECRET_KEY_ENV_VAR));
                assertThat(capture.messages()).noneSatisfy(message -> assertThat(message)
                        .containsAnyOf("plain-secret", "smtp.example.test", "{\""));
            }
        }

        @Test
        @DisplayName("should refuse an API key when the key is missing and enforcement is on")
        void shouldRefuseApiKey_whenKeyMissingAndEnforced() {
            removeKey();
            requireKey(true);
            EmailConfig sendGrid = config(EmailConfig.EmailType.API, EmailConfig.EmailProvider.SENDGRID,
                    "{\"api_key\":\"sg-secret\"}");

            assertThat(emailManager.credentialKeyRefusal(sendGrid)).isEqualTo(EmailManager.CREDENTIAL_KEY_REQUIRED_ERROR);
        }

        @Test
        @DisplayName("should treat a blank key as missing")
        void shouldRefuse_whenKeyIsBlank() {
            CarlosProperties.getInstance().setProperty(EncryptionUtils.SECRET_KEY_ENV_VAR, "   ");
            EncryptionUtils.prepareSecretKeySpec();
            requireKey(true);

            assertThat(emailManager.credentialKeyRefusal(plaintextSmtp())).isEqualTo(EmailManager.CREDENTIAL_KEY_REQUIRED_ERROR);
        }

        @Test
        @DisplayName("should treat an invalid key as missing")
        void shouldRefuse_whenKeyIsInvalid() {
            CarlosProperties.getInstance().setProperty(EncryptionUtils.SECRET_KEY_ENV_VAR, "not-a-base64-aes-key!");
            assertThatThrownBy(EncryptionUtils::prepareSecretKeySpec).isInstanceOf(IllegalArgumentException.class);
            requireKey(true);

            assertThat(emailManager.credentialKeyRefusal(plaintextSmtp())).isEqualTo(EmailManager.CREDENTIAL_KEY_REQUIRED_ERROR);
        }

        @Test
        @DisplayName("should warn once per account and allow the send when enforcement is off")
        void shouldWarnOnceAndAllow_whenKeyMissingAndNotEnforced() {
            removeKey();
            requireKey(false);

            try (LogCapture capture = LogCapture.forLogger(EmailManager.class)) {
                assertThat(emailManager.credentialKeyRefusal(plaintextSmtp())).isNull();
                assertThat(emailManager.credentialKeyRefusal(plaintextSmtp())).isNull();

                List<String> warnings = capture.messages().stream()
                        .filter(message -> message.contains("stay unencrypted")).toList();
                assertThat(warnings).hasSize(1);
                assertThat(warnings.get(0)).contains("config id=12")
                        .contains(EmailManager.REQUIRE_CREDENTIAL_KEY_PROPERTY)
                        .doesNotContain("plain-secret");
            }
        }

        @ParameterizedTest
        @ValueSource(strings = {"true", "yes", "on", " TRUE "})
        @DisplayName("should enforce for any value CARLOS treats as on")
        void shouldEnforce_whenSettingIsActive(String value) {
            removeKey();
            CarlosProperties.getInstance().setProperty(EmailManager.REQUIRE_CREDENTIAL_KEY_PROPERTY, value);

            assertThat(emailManager.credentialKeyRefusal(plaintextSmtp())).isNotNull();
        }

        @ParameterizedTest
        @ValueSource(strings = {"false", "no", "off", "", "1"})
        @DisplayName("should only warn for any value CARLOS does not treat as on")
        void shouldNotEnforce_whenSettingIsInactive(String value) {
            removeKey();
            CarlosProperties.getInstance().setProperty(EmailManager.REQUIRE_CREDENTIAL_KEY_PROPERTY, value);

            assertThat(emailManager.credentialKeyRefusal(plaintextSmtp())).isNull();
        }

        @Test
        @DisplayName("should warn at construction when the setting has an unrecognised value")
        void shouldWarnAtStartup_whenSettingUnrecognised() {
            CarlosProperties.getInstance().setProperty(EmailManager.REQUIRE_CREDENTIAL_KEY_PROPERTY, "ture");

            try (LogCapture capture = LogCapture.forLogger(EmailManager.class)) {
                new EmailManager(mock(EmailConsentResolver.class), new EmailSenderFactory(),
                        mock(SecurityInfoManager.class), mock(OutboundEmailArchiveService.class));

                assertThat(capture.messages()).anySatisfy(message -> assertThat(message)
                        .contains("unrecognised value").contains("OFF"));
            }
        }
    }

    @Nested
    @DisplayName("sendEmailWithResult")
    class SendPath {

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

        @BeforeEach
        void persistLogsWithId() {
            when(emailConfigDao.findActiveEmailConfigById(12)).thenReturn(plaintextSmtp());
            doAnswer(invocation -> {
                injectDependency(invocation.getArgument(0), "id", 81);
                return null;
            }).when(emailLogDao).persist(any(EmailLog.class));
        }

        @Test
        @DisplayName("should fail before any transport, with an actionable reason, when enforcement refuses")
        void shouldFailBeforeTransport_whenEnforcementRefuses() throws Exception {
            removeKey();
            requireKey(true);

            try (MockedConstruction<SMTPEmailSender> transports = mockConstruction(SMTPEmailSender.class)) {
                EmailSendResult result = emailManager.sendEmailWithResult(loggedInInfo, emailData());

                assertThat(result.getTransportOutcome()).isEqualTo(EmailSendResult.TransportOutcome.FAILED);
                assertThat(transports.constructed()).isEmpty();
                verify(emailLogDao).transitionEmailStatus(eq(81), eq(EmailLog.EmailStatus.PENDING),
                        eq(EmailLog.EmailStatus.FAILED), eq(EmailManager.CREDENTIAL_KEY_REQUIRED_ERROR), any());
                verify(emailConfigDao, never()).encryptCredentialsIfUnchanged(anyInt(), any(), any());
                verify(archiveService, never()).archive(any(), any());
            }
        }

        @Test
        @DisplayName("should still send, as before, when the key is missing and enforcement is off")
        void shouldSend_whenKeyMissingAndNotEnforced() throws Exception {
            removeKey();
            requireKey(false);

            try (MockedConstruction<SMTPEmailSender> transports = mockConstruction(SMTPEmailSender.class,
                    (transport, context) -> when(transport.prepareArtifactBytes())
                            .thenReturn("message".getBytes(StandardCharsets.UTF_8)))) {
                EmailSendResult result = emailManager.sendEmailWithResult(loggedInInfo, emailData());

                assertThat(result.getTransportOutcome()).isEqualTo(EmailSendResult.TransportOutcome.ACCEPTED);
                verify(transports.constructed().get(0)).sendPrepared();
                verify(emailConfigDao, never()).encryptCredentialsIfUnchanged(anyInt(), any(), any());
            }
        }

        @Test
        @DisplayName("should migrate plaintext credentials and send when the key is configured")
        void shouldMigrateAndSend_whenKeyConfigured() throws Exception {
            EncryptionKeyTestSupport.seedFreshKey();
            requireKey(true);
            when(emailConfigDao.encryptCredentialsIfUnchanged(eq(12), eq(PLAINTEXT_SMTP), any())).thenReturn(true);

            try (MockedConstruction<SMTPEmailSender> transports = mockConstruction(SMTPEmailSender.class,
                    (transport, context) -> when(transport.prepareArtifactBytes())
                            .thenReturn("message".getBytes(StandardCharsets.UTF_8)))) {
                EmailSendResult result = emailManager.sendEmailWithResult(loggedInInfo, emailData());

                assertThat(result.getTransportOutcome()).isEqualTo(EmailSendResult.TransportOutcome.ACCEPTED);
                verify(emailConfigDao).encryptCredentialsIfUnchanged(eq(12), eq(PLAINTEXT_SMTP), any());
            }
        }
    }
}
