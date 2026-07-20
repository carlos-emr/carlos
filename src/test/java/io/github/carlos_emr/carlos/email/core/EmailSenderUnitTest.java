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
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mail.javamail.JavaMailSender;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("EmailSender")
@Tag("unit")
class EmailSenderUnitTest extends CarlosUnitTestBase {

    @TempDir
    private Path tempDir;

    private SecurityInfoManager securityInfoManager;
    private LoggedInInfo loggedInInfo;

    @BeforeEach
    void setUp() {
        securityInfoManager = mock(SecurityInfoManager.class);
        loggedInInfo = mock(LoggedInInfo.class);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(JavaMailSender.class, mock(JavaMailSender.class));
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.WRITE, null)).thenReturn(true);
    }

    @Test
    @DisplayName("should prepare SMTP archive request from finalized MIME message")
    void shouldPrepareSmtpArchiveRequest_fromFinalizedMimeMessage() throws Exception {
        Path attachmentPath = tempDir.resolve("attachment.pdf");
        byte[] attachmentBytes = "pdf-content".getBytes(StandardCharsets.UTF_8);
        Files.write(attachmentPath, attachmentBytes);
        EmailAttachment attachment = new EmailAttachment("attachment_001.pdf", attachmentPath.toString(), DocumentType.DOC, 77);
        Path textAttachmentPath = tempDir.resolve("notes.txt");
        byte[] textAttachmentBytes = "plain-text-content".getBytes(StandardCharsets.UTF_8);
        Files.write(textAttachmentPath, textAttachmentBytes);
        EmailAttachment textAttachment = new EmailAttachment("notes.txt", textAttachmentPath.toString(), DocumentType.DOC, 78);
        EmailConfig emailConfig = smtpEmailConfig();
        EmailData emailData = emailData(List.of(attachment, textAttachment));
        EmailLog emailLog = new EmailLog(emailConfig, "provider@example.test", emailData.getRecipients(), emailData.getSubject(), emailData.getBody(), EmailLog.EmailStatus.FAILED);
        injectDependency(emailLog, "id", 44);

        EmailSender emailSender = new EmailSender(loggedInInfo, emailConfig, emailData);

        OutboundEmailArchiveDto archiveRequest = emailSender.prepareOutboundArchive(emailLog);

        String eml = new String(archiveRequest.getArtifactBytes(), StandardCharsets.UTF_8);
        assertThat(archiveRequest.getEmailLog()).isSameAs(emailLog);
        assertThat(archiveRequest.getFileName()).isEqualTo("outbound-email-44.eml");
        assertThat(archiveRequest.getContentType()).isEqualTo("message/rfc822");
        assertThat(archiveRequest.getArtifactType()).isEqualTo(OutboundEmailArchive.ARTIFACT_TYPE_SMTP_RFC822);
        assertThat(archiveRequest.getTransportType()).isEqualTo("SMTP");
        assertThat(archiveRequest.getProviderName()).isEqualTo("GMAIL");
        assertThat(eml).contains("Subject: Test subject").contains("patient@example.test");
        assertThat(archiveRequest.getAttachments()).hasSize(2);
        assertThat(archiveRequest.getAttachments().get(0)).satisfies(attachmentDto -> {
            assertThat(attachmentDto.getFileName()).isEqualTo("attachment_001.pdf");
            assertThat(attachmentDto.getContentType()).isEqualTo("application/pdf");
            assertThat(attachmentDto.getSourceDocumentType()).isEqualTo("DOC");
            assertThat(attachmentDto.getSourceDocumentId()).isEqualTo(77);
            assertThat(attachmentDto.getByteSize()).isEqualTo((long) attachmentBytes.length);
            assertThat(attachmentDto.getSha256Hash()).isEqualTo(sha256Hex(attachmentBytes));
        });
        assertThat(archiveRequest.getAttachments().get(1)).satisfies(attachmentDto -> {
            assertThat(attachmentDto.getFileName()).isEqualTo("notes.txt");
            assertThat(attachmentDto.getContentType()).isEqualTo("text/plain");
            assertThat(attachmentDto.getSourceDocumentType()).isEqualTo("DOC");
            assertThat(attachmentDto.getSourceDocumentId()).isEqualTo(78);
            assertThat(attachmentDto.getByteSize()).isEqualTo((long) textAttachmentBytes.length);
            assertThat(attachmentDto.getSha256Hash()).isEqualTo(sha256Hex(textAttachmentBytes));
        });
    }

    private EmailData emailData(List<EmailAttachment> attachments) {
        EmailData emailData = new EmailData();
        emailData.setRecipients(new String[]{"patient@example.test"});
        emailData.setSubject("Test subject");
        emailData.setBody("Body text");
        emailData.setAttachments(attachments);
        return emailData;
    }

    private EmailConfig smtpEmailConfig() {
        EmailConfig emailConfig = new EmailConfig(EmailConfig.EmailType.SMTP, EmailConfig.EmailProvider.GMAIL, "provider@example.test");
        emailConfig.setSenderFirstName("Provider");
        emailConfig.setSenderLastName("One");
        emailConfig.setConfigDetailsJson("{\"host\":\"smtp.example.test\",\"port\":\"587\",\"username\":\"user\",\"password\":\"secret\"}");
        return emailConfig;
    }

    private String sha256Hex(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
