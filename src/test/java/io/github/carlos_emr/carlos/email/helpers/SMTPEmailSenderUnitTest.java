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
package io.github.carlos_emr.carlos.email.helpers;

import io.github.carlos_emr.carlos.commn.model.EmailAttachment;
import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.managers.NioFileManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.EmailSendingException;
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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@DisplayName("SMTPEmailSender")
@Tag("unit")
class SMTPEmailSenderUnitTest extends CarlosUnitTestBase {

    @TempDir
    private Path tempDir;

    private SecurityInfoManager securityInfoManager;
    private NioFileManager nioFileManager;
    private LoggedInInfo loggedInInfo;

    @BeforeEach
    void setUp() throws IOException {
        securityInfoManager = mock(SecurityInfoManager.class);
        nioFileManager = mock(NioFileManager.class);
        loggedInInfo = mock(LoggedInInfo.class);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(NioFileManager.class, nioFileManager);
        registerMock(JavaMailSender.class, mock(JavaMailSender.class));
        when(nioFileManager.createManagedTempFile(anyString(), anyString()))
                .thenAnswer(invocation -> Files.createTempFile(
                        tempDir, invocation.getArgument(0), invocation.getArgument(1)));
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

        byte[] archivedMessageBytes = sender.prepareArtifactBytes();
        assertThat(sender.getPreparedAttachments()).hasSize(1);
        long preparedByteSize = sender.getPreparedAttachments().get(0).getByteSize();
        String preparedSha256Hash = sender.getPreparedAttachments().get(0).getSha256Hash();
        Files.write(attachmentPath, changedAttachmentBytes);
        sender.sendPrepared();

        assertThat(preparedByteSize).isEqualTo((long) originalAttachmentBytes.length);
        assertThat(preparedSha256Hash).isEqualTo(sha256Hex(originalAttachmentBytes));
        assertThat(firstAttachmentBytes(archivedMessageBytes)).isEqualTo(originalAttachmentBytes);
        assertThat(firstAttachmentBytes(mailSender.getSentMessageBytes())).isEqualTo(originalAttachmentBytes);
    }

    @Test
    @DisplayName("should reject prepared send when email privilege is missing")
    void shouldRejectPreparedSend_whenEmailPrivilegeMissing() throws Exception {
        Path attachmentPath = tempDir.resolve("privilege-check.txt");
        Files.writeString(attachmentPath, "attachment content");
        EmailAttachment attachment = new EmailAttachment(
                "privilege-check.txt", attachmentPath.toString(), DocumentType.DOC, 102);
        CapturingJavaMailSender mailSender = new CapturingJavaMailSender();
        SMTPEmailSender sender = new TestSMTPEmailSender(
                loggedInInfo,
                smtpEmailConfig(),
                new String[]{"patient@example.test"},
                "Snapshot test",
                "Body text",
                List.of(attachment),
                mailSender);
        sender.prepareArtifactBytes();
        assertThat(sender.getPreparedAttachments()).hasSize(1);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.WRITE, null)).thenReturn(false);

        assertThatThrownBy(sender::sendPrepared)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("missing required sec object (_email)");
        assertThat(mailSender.getSentMessageBytes()).isNull();
        assertThat(sender.getPreparedAttachments()).isEmpty();
    }

    @Test
    @DisplayName("should reject prepared send when message was not prepared")
    void shouldRejectPreparedSend_whenMessageWasNotPrepared() {
        SMTPEmailSender sender = new TestSMTPEmailSender(
                loggedInInfo,
                smtpEmailConfig(),
                new String[]{"patient@example.test"},
                "Snapshot test",
                "Body text",
                List.of(),
                new CapturingJavaMailSender());

        assertThatThrownBy(sender::sendPrepared)
                .isInstanceOf(EmailSendingException.class)
                .hasMessageContaining("SMTP message must be prepared before sending");
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

    @Test
    @DisplayName("should fail with sending exception when required SMTP config field is absent")
    void shouldFailWithSendingException_whenRequiredSmtpConfigFieldIsAbsent() {
        // A durable archive artifact is written between preparation and transport, so a config that
        // could never have sent must fail here rather than raise a raw NPE past the caller's
        // EmailSendingException handling.
        SMTPEmailSender sender = senderWithConfigJson("{\"port\":\"587\",\"username\":\"user\",\"password\":\"secret\"}");

        assertThatThrownBy(sender::prepareArtifactBytes)
                .isInstanceOf(EmailSendingException.class)
                .hasMessageContaining("Invalid credentials configured for");
    }

    @Test
    @DisplayName("should fail with sending exception when required SMTP config field is blank")
    void shouldFailWithSendingException_whenRequiredSmtpConfigFieldIsBlank() {
        SMTPEmailSender sender = senderWithConfigJson("{\"host\":\"  \",\"port\":\"587\",\"username\":\"user\",\"password\":\"secret\"}");

        assertThatThrownBy(sender::prepareArtifactBytes)
                .isInstanceOf(EmailSendingException.class)
                .hasMessageContaining("Invalid credentials configured for");
    }

    @Test
    @DisplayName("should fail with sending exception when SMTP port is not a valid port number")
    void shouldFailWithSendingException_whenSmtpPortIsNotAValidPortNumber() {
        SMTPEmailSender sender = senderWithConfigJson("{\"host\":\"smtp.example.test\",\"port\":\"not-a-port\",\"username\":\"user\",\"password\":\"secret\"}");

        assertThatThrownBy(sender::prepareArtifactBytes)
                .isInstanceOf(EmailSendingException.class)
                .hasMessageContaining("Invalid credentials configured for");
    }

    @Test
    @DisplayName("should fail with sending exception when SMTP port is out of range")
    void shouldFailWithSendingException_whenSmtpPortIsOutOfRange() {
        SMTPEmailSender sender = senderWithConfigJson("{\"host\":\"smtp.example.test\",\"port\":\"70000\",\"username\":\"user\",\"password\":\"secret\"}");

        assertThatThrownBy(sender::prepareArtifactBytes)
                .isInstanceOf(EmailSendingException.class)
                .hasMessageContaining("Invalid credentials configured for");
    }

    @Test
    @DisplayName("should preserve surrounding whitespace in SMTP passwords")
    void shouldPreserveSurroundingWhitespace_inSmtpPassword() throws Exception {
        EmailConfig emailConfig = smtpEmailConfig();
        emailConfig.setConfigDetailsJson("""
                {"host":" smtp.example.test ","port":" 587 ","username":" user ","password":" secret "}
                """);
        SMTPEmailSender sender = new SMTPEmailSender(
                loggedInInfo, emailConfig, new String[]{"patient@example.test"}, "Subject", "Body", List.of());

        JavaMailSenderImpl mailSender = (JavaMailSenderImpl) sender.createTLSMailSender(emailConfig);

        assertThat(mailSender.getHost()).isEqualTo("smtp.example.test");
        assertThat(mailSender.getPort()).isEqualTo(587);
        assertThat(mailSender.getUsername()).isEqualTo("user");
        assertThat(mailSender.getPassword()).isEqualTo(" secret ");
    }

    private SMTPEmailSender senderWithConfigJson(String configDetailsJson) {
        EmailConfig emailConfig = smtpEmailConfig();
        emailConfig.setConfigDetailsJson(configDetailsJson);
        return new SMTPEmailSender(
                loggedInInfo,
                emailConfig,
                new String[]{"patient@example.test"},
                "Subject",
                "Body text",
                List.of());
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
