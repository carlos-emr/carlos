/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.managers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.List;

import io.github.carlos_emr.carlos.commn.dao.EmailConfigDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.EmailLogDaoImpl;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.EmailLog.ChartDisplayOption;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.email.core.EmailData;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.EmailSendingException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@Tag("unit")
@Tag("security")
@DisplayName("EmailManager")
class EmailManagerUnitTest extends CarlosUnitTestBase {

    @Test
    @DisplayName("should not persist plaintext PDF password to email log")
    void shouldNotPersistPlaintextPdfPasswordToEmailLog() {
        EmailConfigDaoImpl emailConfigDao = mock(EmailConfigDaoImpl.class);
        EmailLogDaoImpl emailLogDao = mock(EmailLogDaoImpl.class);
        DemographicManager demographicManager = mock(DemographicManager.class);
        ProviderManager2 providerManager = mock(ProviderManager2.class);
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);

        EmailManager emailManager = new EmailManager();
        injectDependency(emailManager, "emailConfigDao", emailConfigDao);
        injectDependency(emailManager, "emailLogDao", emailLogDao);
        injectDependency(emailManager, "demographicManager", demographicManager);
        injectDependency(emailManager, "providerManager", providerManager);
        injectDependency(emailManager, "securityInfoManager", securityInfoManager);

        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        EmailConfig emailConfig = new EmailConfig(EmailConfig.EmailType.SMTP, EmailConfig.EmailProvider.LOCAL, "clinic@example.com");
        Demographic demographic = new Demographic();
        demographic.setDemographicNo(123);
        Provider provider = new Provider();
        provider.setProviderNo("999998");

        when(securityInfoManager.hasPrivilege(any(), eq("_email"), eq(SecurityInfoManager.WRITE), any())).thenReturn(true);
        when(emailConfigDao.findActiveEmailConfigById(1)).thenReturn(emailConfig);
        when(demographicManager.getDemographic(loggedInInfo, 123)).thenReturn(demographic);
        when(providerManager.getProvider(loggedInInfo, "999998")).thenReturn(provider);

        EmailData emailData = new EmailData();
        emailData.setSenderConfigId("1");
        emailData.setRecipients(new String[]{"patient@example.com"});
        emailData.setSubject("Subject");
        emailData.setBody("Body");
        emailData.setEncryptedMessage("Encrypted body");
        emailData.setPassword("alpha-bravo-123-charlie-delta-456");
        emailData.setPasswordClue("Do not persist this instruction with a password");
        emailData.setIsEncrypted(true);
        emailData.setIsAttachmentEncrypted(false);
        emailData.setChartDisplayOption(ChartDisplayOption.WITH_FULL_NOTE);
        emailData.setInternalComment("");
        emailData.setTransactionType(EmailLog.TransactionType.DIRECT);
        emailData.setDemographicNo(123);
        emailData.setProviderNo("999998");
        emailData.setAttachments(List.of());

        emailManager.prepareEmailForOutbox(loggedInInfo, emailData);

        ArgumentCaptor<EmailLog> emailLogCaptor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogDao).persist(emailLogCaptor.capture());
        EmailLog persistedLog = emailLogCaptor.getValue();
        assertThat(persistedLog.getPassword()).isEmpty();
        assertThat(persistedLog.getPasswordClue()).isEmpty();
    }

    @Test
    @DisplayName("should fail closed when PDF encryption password is missing")
    void shouldFailClosed_whenPdfEncryptionPasswordIsMissing() throws Exception {
        EmailManager emailManager = new EmailManager();
        EmailData emailData = new EmailData();
        emailData.setEncryptedMessage("Protected message");
        emailData.setPassword("");

        Method encryptEmail = EmailManager.class.getDeclaredMethod("encryptEmail", EmailData.class);
        encryptEmail.setAccessible(true);

        assertThatThrownBy(() -> encryptEmail.invoke(emailManager, emailData))
                .isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(EmailSendingException.class)
                .hasRootCauseMessage("PDF encryption password is required");
    }

    @Test
    @DisplayName("should parse valid dates and sanitize invalid date logs")
    void shouldParseValidDatesAndSanitizeInvalidDateLogs() throws Exception {
        EmailManager emailManager = new EmailManager();
        Method parseDate = EmailManager.class.getDeclaredMethod(
                "parseDate", String.class, String.class, String.class);
        parseDate.setAccessible(true);

        assertThat(parseDate.invoke(emailManager, null, "yyyy-MM-dd", null)).isNull();
        assertThat((Date) parseDate.invoke(emailManager, "2026-07-22", "yyyy-MM-dd", null)).isNotNull();
        assertThat((Date) parseDate.invoke(emailManager, "2026-07-22", "yyyy-MM-dd", "09:30:00")).isNotNull();

        try (LogCapture capture = LogCapture.forLogger(EmailManager.class)) {
            assertThat(parseDate.invoke(emailManager, "2026-99-99\r\nforged", "yyyy-MM-dd", null)).isNull();

            String logged = capture.messages().stream()
                    .filter(message -> message.startsWith("UNPARSEABLE DATE"))
                    .findFirst()
                    .orElseThrow();
            assertThat(logged).doesNotContain("\r").doesNotContain("\n");
            assertThat(logged).contains("2026-99-99\\r\\nforged");
        }
    }
}
