/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.email.helpers;

import io.github.carlos_emr.carlos.commn.model.EmailAttachment;
import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("SMTPEmailSender")
@Tag("unit")
class SMTPEmailSenderUnitTest extends CarlosUnitTestBase {

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
    @DisplayName("should send prepared message using attachment snapshot when source file changes")
    void shouldSendPreparedMessageUsingAttachmentSnapshot_whenSourceFileChanges() throws Exception {
        byte[] originalAttachmentBytes = "original attachment".getBytes(StandardCharsets.UTF_8);
        byte[] changedAttachmentBytes = "changed attachment".getBytes(StandardCharsets.UTF_8);
        Path attachmentPath = tempDir.resolve("attachment.txt");
        Files.write(attachmentPath, originalAttachmentBytes);
        EmailAttachment attachment = new EmailAttachment("attachment.txt", attachmentPath.toString(), DocumentType.DOC, 101);
        CapturingJavaMailSender mailSender = new CapturingJavaMailSender();
        SMTPEmailSender sender = new TestSMTPEmailSender(
                loggedInInfo,
                smtpEmailConfig(),
                new String[]{"patient@example.test"},
                "Snapshot test",
                "Body text",
                List.of(attachment),
                mailSender);

        byte[] archivedMessageBytes = sender.prepareMessageBytes();
        Files.write(attachmentPath, changedAttachmentBytes);
        sender.sendPreparedMessage();

        assertThat(sender.getPreparedAttachments()).hasSize(1);
        assertThat(sender.getPreparedAttachments().get(0).getBytes()).isEqualTo(originalAttachmentBytes);
        assertThat(firstAttachmentBytes(archivedMessageBytes)).isEqualTo(originalAttachmentBytes);
        assertThat(firstAttachmentBytes(mailSender.getSentMessageBytes())).isEqualTo(originalAttachmentBytes);
    }

    private byte[] firstAttachmentBytes(byte[] messageBytes) throws Exception {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(messageBytes));
        Object content = message.getContent();
        assertThat(content).isInstanceOf(Multipart.class);
        byte[] attachmentBytes = findFirstAttachmentBytes((Multipart) content);
        assertThat(attachmentBytes).as("MIME message attachment bytes").isNotNull();
        return attachmentBytes;
    }

    private byte[] findFirstAttachmentBytes(Multipart multipart) throws Exception {
        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart bodyPart = multipart.getBodyPart(i);
            Object content = bodyPart.getContent();
            if (Part.ATTACHMENT.equalsIgnoreCase(bodyPart.getDisposition()) || bodyPart.getFileName() != null) {
                return bodyPart.getInputStream().readAllBytes();
            }
            if (content instanceof Multipart nestedMultipart) {
                byte[] nestedAttachmentBytes = findFirstAttachmentBytes(nestedMultipart);
                if (nestedAttachmentBytes != null) {
                    return nestedAttachmentBytes;
                }
            }
        }
        return null;
    }

    private EmailConfig smtpEmailConfig() {
        EmailConfig emailConfig = new EmailConfig(EmailConfig.EmailType.SMTP, EmailConfig.EmailProvider.GMAIL, "provider@example.test");
        emailConfig.setSenderFirstName("Provider");
        emailConfig.setSenderLastName("One");
        emailConfig.setConfigDetailsJson("{\"host\":\"smtp.example.test\",\"port\":\"587\",\"username\":\"user\",\"password\":\"secret\"}");
        return emailConfig;
    }

    private static final class TestSMTPEmailSender extends SMTPEmailSender {
        private final JavaMailSender mailSender;

        private TestSMTPEmailSender(
                LoggedInInfo loggedInInfo,
                EmailConfig emailConfig,
                String[] recipients,
                String subject,
                String body,
                List<EmailAttachment> attachments,
                JavaMailSender mailSender) {
            super(loggedInInfo, emailConfig, recipients, subject, body, attachments);
            this.mailSender = mailSender;
        }

        @Override
        protected JavaMailSender createTLSMailSender(EmailConfig emailConfig) {
            return mailSender;
        }
    }

    private static final class CapturingJavaMailSender extends JavaMailSenderImpl {
        private byte[] sentMessageBytes;

        @Override
        public void send(MimeMessage... mimeMessages) throws MailException {
            try {
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                MimeMessage mimeMessage = mimeMessages[0];
                String messageId = mimeMessage.getMessageID();
                mimeMessage.saveChanges();
                if (messageId != null) {
                    mimeMessage.setHeader("Message-ID", messageId);
                }
                mimeMessage.writeTo(outputStream);
                sentMessageBytes = outputStream.toByteArray();
            } catch (Exception e) {
                throw new MailSendException("Failed to capture message", e);
            }
        }

        private byte[] getSentMessageBytes() {
            return sentMessageBytes;
        }
    }
}
