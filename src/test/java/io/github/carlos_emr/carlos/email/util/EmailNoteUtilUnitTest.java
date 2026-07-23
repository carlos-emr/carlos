/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.email.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.casemgmt.model.ProviderExt;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderExtDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.QueueDocumentLinkDao;
import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.EmailLog.ChartDisplayOption;
import io.github.carlos_emr.carlos.commn.model.EmailLog.EmailStatus;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.EformDataManager;
import io.github.carlos_emr.carlos.managers.FormsManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@Tag("security")
@DisplayName("EmailNoteUtil")
class EmailNoteUtilUnitTest extends CarlosUnitTestBase {

    @Test
    @DisplayName("should not include plaintext PDF password in chart note")
    void shouldNotIncludePlaintextPdfPasswordInChartNote() {
        registerEmailNoteDependencies(mock(ProviderExtDao.class));

        EmailLog emailLog = emailLog(new String[]{"patient@example.com"});
        emailLog.setIsEncrypted(true);
        emailLog.setIsAttachmentEncrypted(true);
        emailLog.setEncryptedMessage("Encrypted message body");
        emailLog.setPassword("alpha-bravo-123-charlie-delta-456");
        emailLog.setPasswordClue("secret clue");

        EmailNoteUtil emailNoteUtil = new EmailNoteUtil(new LoggedInInfo(), emailLog);

        String note = emailNoteUtil.createNote();

        assertThat(note)
                .contains("PDF attachments were encrypted")
                .doesNotContain("alpha-bravo-123-charlie-delta-456")
                .doesNotContain("secret clue")
                .doesNotContain("Password:");
    }

    @Test
    @DisplayName("should format multiple recipients and use provider signature")
    void shouldFormatMultipleRecipientsAndUseProviderSignature() {
        ProviderExtDao providerExtDao = mock(ProviderExtDao.class);
        registerEmailNoteDependencies(providerExtDao);
        ProviderExt providerExt = new ProviderExt();
        providerExt.setSignature("  Dr. Signed Name  ");
        when(providerExtDao.find("999998")).thenReturn(providerExt);
        EmailLog emailLog = emailLog(new String[]{"alpha@example.com", "beta@example.com", "gamma@example.com"});
        emailLog.setInternalComment("Internal delivery context");
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        loggedInInfo.setLoggedInProvider(provider("999998", "Ben", "Signer"));

        String note = new EmailNoteUtil(loggedInInfo, emailLog).createNote();

        assertThat(note)
                .contains("To: alpha@example.com, beta@example.com, and gamma@example.com")
                .contains("[Sent on")
                .contains(" by Dr. Signed Name]")
                .contains("***Internal Comment***")
                .contains("Internal delivery context");
    }

    @Test
    @DisplayName("should fall back to provider full name when signature is unavailable")
    void shouldFallBackToProviderFullNameWhenSignatureIsUnavailable() {
        ProviderExtDao providerExtDao = mock(ProviderExtDao.class);
        registerEmailNoteDependencies(providerExtDao);
        when(providerExtDao.find("123456")).thenReturn(null);
        EmailLog emailLog = emailLog(new String[]{"patient@example.com"});
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        loggedInInfo.setLoggedInProvider(provider("123456", "Avery", "Clinician"));

        String note = new EmailNoteUtil(loggedInInfo, emailLog).createNote();

        assertThat(note).contains(" by Avery Clinician]");
    }

    @Test
    @DisplayName("should suppress internal comment when chart option omits note")
    void shouldSuppressInternalCommentWhenChartOptionOmitsNote() {
        registerEmailNoteDependencies(mock(ProviderExtDao.class));
        EmailLog emailLog = emailLog(new String[]{"patient@example.com"});
        emailLog.setChartDisplayOption(ChartDisplayOption.WITHOUT_NOTE);
        emailLog.setInternalComment("Do not publish this comment");

        String note = new EmailNoteUtil(new LoggedInInfo(), emailLog).createNote();

        assertThat(note)
                .doesNotContain("***Internal Comment***")
                .doesNotContain("Do not publish this comment")
                .contains("***Technical Information***");
    }

    private void registerEmailNoteDependencies(ProviderExtDao providerExtDao) {
        registerMock(EformDataManager.class, mock(EformDataManager.class));
        registerMock(FormsManager.class, mock(FormsManager.class));
        registerMock(ProviderExtDao.class, providerExtDao);
        registerMock(PatientLabRoutingDao.class, mock(PatientLabRoutingDao.class));
        registerMock(ProviderLabRoutingDao.class, mock(ProviderLabRoutingDao.class));
        registerMock(QueueDocumentLinkDao.class, mock(QueueDocumentLinkDao.class));
        registerMock(SecurityInfoManager.class, mock(SecurityInfoManager.class));
    }

    private static EmailLog emailLog(String[] recipients) {
        EmailConfig emailConfig = new EmailConfig(
                EmailConfig.EmailType.SMTP,
                EmailConfig.EmailProvider.LOCAL,
                "clinic@example.com");
        EmailLog emailLog = new EmailLog(
                emailConfig,
                "clinic@example.com",
                recipients,
                "Subject",
                "Body",
                EmailStatus.SUCCESS);
        emailLog.setChartDisplayOption(ChartDisplayOption.WITH_FULL_NOTE);
        emailLog.setInternalComment("");
        return emailLog;
    }

    private static Provider provider(String providerNo, String firstName, String lastName) {
        Provider provider = new Provider(providerNo);
        provider.setFirstName(firstName);
        provider.setLastName(lastName);
        return provider;
    }
}
