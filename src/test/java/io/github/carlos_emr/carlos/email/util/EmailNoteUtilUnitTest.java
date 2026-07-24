/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.email.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderExtDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.QueueDocumentLinkDao;
import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.EmailLog.ChartDisplayOption;
import io.github.carlos_emr.carlos.commn.model.EmailLog.EmailStatus;
import io.github.carlos_emr.carlos.managers.EformDataManager;
import io.github.carlos_emr.carlos.managers.FormsManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@Tag("fast")
@Tag("security")
@DisplayName("EmailNoteUtil")
class EmailNoteUtilUnitTest extends CarlosUnitTestBase {
    private static final String EXAMPLE_REDACTED_VALUE = "example-redacted-value";
    private static final String EXAMPLE_DELIVERY_NOTE = "example delivery note";

    @Test
    @DisplayName("should not include plaintext PDF password in chart note")
    void shouldNotIncludePassword_whenGeneratingChartNote() {
        registerMock(EformDataManager.class, mock(EformDataManager.class));
        registerMock(FormsManager.class, mock(FormsManager.class));
        registerMock(ProviderExtDao.class, mock(ProviderExtDao.class));
        registerMock(PatientLabRoutingDao.class, mock(PatientLabRoutingDao.class));
        registerMock(ProviderLabRoutingDao.class, mock(ProviderLabRoutingDao.class));
        registerMock(QueueDocumentLinkDao.class, mock(QueueDocumentLinkDao.class));
        registerMock(SecurityInfoManager.class, mock(SecurityInfoManager.class));

        EmailConfig emailConfig = new EmailConfig(EmailConfig.EmailType.SMTP, EmailConfig.EmailProvider.LOCAL, "clinic@example.com");
        EmailLog emailLog = new EmailLog(emailConfig, "clinic@example.com", new String[]{"patient@example.com"}, "Subject", "Body", EmailStatus.SUCCESS);
        emailLog.setIsEncrypted(true);
        emailLog.setIsAttachmentEncrypted(true);
        emailLog.setEncryptedMessage("Encrypted message body");
        emailLog.setPassword(EXAMPLE_REDACTED_VALUE);
        emailLog.setPasswordClue(EXAMPLE_DELIVERY_NOTE);
        emailLog.setChartDisplayOption(ChartDisplayOption.WITH_FULL_NOTE);
        emailLog.setInternalComment("");

        EmailNoteUtil emailNoteUtil = new EmailNoteUtil(new LoggedInInfo(), emailLog);

        String note = emailNoteUtil.createNote();

        assertThat(note)
                .contains("PDF attachments were encrypted")
                .doesNotContain(EXAMPLE_REDACTED_VALUE)
                .doesNotContain(EXAMPLE_DELIVERY_NOTE)
                .doesNotContain("Password:");
    }

    @Test
    @DisplayName("should not claim attachments were encrypted for body-only encryption")
    void shouldDescribeEncryption_whenOnlyBodyIsEncrypted() {
        registerMock(EformDataManager.class, mock(EformDataManager.class));
        registerMock(FormsManager.class, mock(FormsManager.class));
        registerMock(ProviderExtDao.class, mock(ProviderExtDao.class));
        registerMock(PatientLabRoutingDao.class, mock(PatientLabRoutingDao.class));
        registerMock(ProviderLabRoutingDao.class, mock(ProviderLabRoutingDao.class));
        registerMock(QueueDocumentLinkDao.class, mock(QueueDocumentLinkDao.class));
        registerMock(SecurityInfoManager.class, mock(SecurityInfoManager.class));

        EmailConfig emailConfig = new EmailConfig(EmailConfig.EmailType.SMTP, EmailConfig.EmailProvider.LOCAL, "clinic@example.com");
        EmailLog emailLog = new EmailLog(emailConfig, "clinic@example.com", new String[]{"patient@example.com"}, "Subject", "Body", EmailStatus.SUCCESS);
        emailLog.setIsEncrypted(true);
        emailLog.setIsAttachmentEncrypted(false);
        emailLog.setEncryptedMessage("Encrypted message body");
        emailLog.setPassword(EXAMPLE_REDACTED_VALUE);
        emailLog.setPasswordClue(EXAMPLE_DELIVERY_NOTE);
        emailLog.setChartDisplayOption(ChartDisplayOption.WITH_FULL_NOTE);
        emailLog.setInternalComment("");

        EmailNoteUtil emailNoteUtil = new EmailNoteUtil(new LoggedInInfo(), emailLog);

        String note = emailNoteUtil.createNote();

        assertThat(note)
                .contains("Email message content was encrypted")
                .doesNotContain("PDF attachments were encrypted")
                .doesNotContain(EXAMPLE_REDACTED_VALUE)
                .doesNotContain(EXAMPLE_DELIVERY_NOTE)
                .doesNotContain("Password:");
    }
}
