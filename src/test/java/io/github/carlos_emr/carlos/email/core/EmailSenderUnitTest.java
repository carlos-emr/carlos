/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.email.core;

import io.github.carlos_emr.carlos.commn.model.EmailAttachment;
import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.OutboundEmailArchive;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.email.archive.OutboundEmailArchiveDto;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.EmailSendingException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("EmailSender")
@Tag("unit")
class EmailSenderUnitTest extends CarlosUnitTestBase {

    private static final String API_KEY = "SG.secret-token";

    @TempDir
    private Path tempDir;

    private SecurityInfoManager securityInfoManager;
    private LoggedInInfo loggedInInfo;

    @BeforeEach
    void setUp() {
        securityInfoManager = mock(SecurityInfoManager.class);
        loggedInInfo = mock(LoggedInInfo.class);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.WRITE, null)).thenReturn(true);
    }

    @Test
    @DisplayName("should reject prepared SendGrid send without prepared archive")
    void shouldRejectPreparedSend_withoutPreparedArchive() {
        EmailSender emailSender = new EmailSender(loggedInInfo, sendGridEmailConfig(), emailData(List.of()));

        assertThatThrownBy(emailSender::sendPrepared)
                .isInstanceOf(EmailSendingException.class)
                .hasMessageContaining("Prepared SendGrid payload is required");
    }

    @Test
    @DisplayName("should reject prepared SendGrid send when write privilege is missing")
    void shouldRejectPreparedSend_whenWritePrivilegeMissing() throws Exception {
        EmailConfig emailConfig = sendGridEmailConfig();
        EmailData emailData = emailData(List.of());
        EmailLog emailLog = new EmailLog(emailConfig, "provider@example.test", emailData.getRecipients(), emailData.getSubject(), emailData.getBody(), EmailLog.EmailStatus.FAILED);
        injectDependency(emailLog, "id", 45);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.WRITE, null)).thenReturn(true, true, false);
        EmailSender emailSender = new EmailSender(loggedInInfo, emailConfig, emailData);
        emailSender.prepareOutboundArchive(emailLog);

        assertThatThrownBy(emailSender::sendPrepared)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("missing required sec object (_email)");
    }

    @Test
    @DisplayName("should reject outbound archive preparation without persisted email log")
    void shouldRejectOutboundArchivePreparation_withoutPersistedEmailLog() {
        EmailConfig emailConfig = sendGridEmailConfig();
        EmailData emailData = emailData(List.of());
        EmailLog emailLog = new EmailLog(emailConfig, "provider@example.test", emailData.getRecipients(), emailData.getSubject(), emailData.getBody(), EmailLog.EmailStatus.FAILED);
        EmailSender emailSender = new EmailSender(loggedInInfo, emailConfig, emailData);

        assertThatThrownBy(() -> emailSender.prepareOutboundArchive(emailLog))
                .isInstanceOf(EmailSendingException.class)
                .hasMessage("Persisted EmailLog is required for outbound email archive");
    }

    @Test
    @DisplayName("should report unsupported outbound archive when email config is missing")
    void shouldReportUnsupportedOutboundArchive_whenEmailConfigMissing() {
        EmailSender emailSender = new EmailSender(loggedInInfo, null, new String[]{"patient@example.test"}, "Test subject", "Body text", List.of());

        assertThat(emailSender.supportsOutboundArchive()).isFalse();
    }

    @Test
    @DisplayName("should prepare SendGrid archive request from submitted JSON payload")
    void shouldPrepareSendGridArchiveRequest_fromSubmittedJsonPayload() throws Exception {
        Path attachmentPath = tempDir.resolve("attachment.pdf");
        byte[] attachmentBytes = "pdf-content".getBytes(StandardCharsets.UTF_8);
        Files.write(attachmentPath, attachmentBytes);
        EmailAttachment attachment = new EmailAttachment("attachment_001.pdf", attachmentPath.toString(), DocumentType.DOC, 77);
        EmailConfig emailConfig = sendGridEmailConfig();
        EmailData emailData = emailData(List.of(attachment));
        EmailLog emailLog = new EmailLog(emailConfig, "provider@example.test", emailData.getRecipients(), emailData.getSubject(), emailData.getBody(), EmailLog.EmailStatus.FAILED);
        injectDependency(emailLog, "id", 44);

        EmailSender emailSender = new EmailSender(loggedInInfo, emailConfig, emailData);

        OutboundEmailArchiveDto archiveRequest = emailSender.prepareOutboundArchive(emailLog);

        String payload = new String(archiveRequest.getArtifactBytes(), StandardCharsets.UTF_8);
        assertThat(archiveRequest.getEmailLog()).isSameAs(emailLog);
        assertThat(archiveRequest.getFileName()).isEqualTo("outbound-email-44-sendgrid.json");
        assertThat(archiveRequest.getContentType()).isEqualTo("application/json");
        assertThat(archiveRequest.getArtifactType()).isEqualTo(OutboundEmailArchive.ARTIFACT_TYPE_API_PAYLOAD);
        assertThat(archiveRequest.getTransportType()).isEqualTo("API");
        assertThat(archiveRequest.getProviderName()).isEqualTo("SENDGRID");
        assertThat(payload)
                .contains("\"subject\":\"Test subject\"")
                .doesNotContain(API_KEY)
                .doesNotContain("additionalParams");
        assertThat(archiveRequest.getAttachments()).singleElement().satisfies(attachmentDto -> {
            assertThat(attachmentDto.getFileName()).isEqualTo("attachment_001.pdf");
            assertThat(attachmentDto.getContentType()).isEqualTo("application/pdf");
            assertThat(attachmentDto.getSourceDocumentType()).isEqualTo("DOC");
            assertThat(attachmentDto.getSourceDocumentId()).isEqualTo(77);
            assertThat(attachmentDto.getByteSize()).isEqualTo((long) attachmentBytes.length);
            assertThat(attachmentDto.getSha256Hash()).isEqualTo(sha256Hex(attachmentBytes));
        });
    }

    @Test
    @DisplayName("should record actual attachment content type when attachment is not a PDF")
    void shouldRecordActualAttachmentContentType_whenAttachmentIsNotAPdf() throws Exception {
        // The declared type is copied into the archived payload and becomes a durable claim in the
        // outbound email archive, so it must not report every artifact as application/pdf.
        Path attachmentPath = tempDir.resolve("results.txt");
        Files.write(attachmentPath, "plain text results".getBytes(StandardCharsets.UTF_8));
        EmailAttachment attachment = new EmailAttachment("results.txt", attachmentPath.toString(), DocumentType.DOC, 78);
        EmailConfig emailConfig = sendGridEmailConfig();
        EmailData emailData = emailData(List.of(attachment));
        EmailLog emailLog = new EmailLog(emailConfig, "provider@example.test", emailData.getRecipients(), emailData.getSubject(), emailData.getBody(), EmailLog.EmailStatus.FAILED);
        injectDependency(emailLog, "id", 46);

        EmailSender emailSender = new EmailSender(loggedInInfo, emailConfig, emailData);

        OutboundEmailArchiveDto archiveRequest = emailSender.prepareOutboundArchive(emailLog);

        String payload = new String(archiveRequest.getArtifactBytes(), StandardCharsets.UTF_8);
        assertThat(payload).contains("\"type\":\"text/plain\"");
        assertThat(archiveRequest.getAttachments()).singleElement().satisfies(attachmentDto ->
                assertThat(attachmentDto.getContentType()).isEqualTo("text/plain"));
    }

    private EmailData emailData(List<EmailAttachment> attachments) {
        EmailData emailData = new EmailData();
        emailData.setRecipients(new String[]{"patient@example.test"});
        emailData.setSubject("Test subject");
        emailData.setBody("Body text");
        emailData.setAdditionalParams(null);
        emailData.setAttachments(attachments);
        return emailData;
    }

    private EmailConfig sendGridEmailConfig() {
        EmailConfig emailConfig = new EmailConfig(EmailConfig.EmailType.API, EmailConfig.EmailProvider.SENDGRID, "provider@example.test");
        emailConfig.setSenderFirstName("Provider");
        emailConfig.setSenderLastName("One");
        emailConfig.setConfigDetailsJson("{\"api_key\":\"" + API_KEY + "\",\"end_point\":\"https://api.sendgrid.test/v3/mail/send\"}");
        return emailConfig;
    }

    private String sha256Hex(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
