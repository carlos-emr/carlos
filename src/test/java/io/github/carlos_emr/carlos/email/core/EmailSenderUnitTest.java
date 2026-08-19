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
package io.github.carlos_emr.carlos.email.core;

import io.github.carlos_emr.carlos.commn.model.EmailAttachment;
import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.OutboundEmailArchive;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.email.archive.OutboundEmailArchiveDto;
import io.github.carlos_emr.carlos.email.helpers.APISendGridEmailSender;
import io.github.carlos_emr.carlos.email.helpers.LocalSMTPEmailSender;
import io.github.carlos_emr.carlos.email.helpers.SMTPEmailSender;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.managers.NioFileManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.EmailSendingException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.ArrayList;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockConstruction;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("EmailSender")
@Tag("unit")
class EmailSenderUnitTest extends CarlosUnitTestBase {

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
    @DisplayName("should prepare SMTP archive request from finalized MIME message")
    void shouldPrepareSmtpArchiveRequest_fromFinalizedMimeMessage() throws Exception {
        Path attachmentPath = tempDir.resolve("attachment.pdf");
        byte[] attachmentBytes = "pdf-content".getBytes(StandardCharsets.UTF_8);
        Files.write(attachmentPath, attachmentBytes);
        EmailAttachment attachment = new EmailAttachment("attachment_001.pdf", attachmentPath.toString(), DocumentType.DOC, 77);
        Path textAttachmentPath = tempDir.resolve("notes.bin");
        byte[] textAttachmentBytes = "plain-text-content".getBytes(StandardCharsets.UTF_8);
        Files.write(textAttachmentPath, textAttachmentBytes);
        EmailAttachment textAttachment = new EmailAttachment("notes.txt", textAttachmentPath.toString(), DocumentType.DOC, 78);
        EmailConfig emailConfig = smtpEmailConfig();
        EmailData emailData = emailData(List.of(attachment, textAttachment));
        EmailLog emailLog = new EmailLog(emailConfig, "provider@example.test", emailData.getRecipients(), emailData.getSubject(), emailData.getBody(), EmailLog.EmailStatus.FAILED);
        injectDependency(emailLog, "id", 44);

        EmailSender emailSender = new EmailSender(loggedInInfo, emailConfig, emailData);

        try {
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
        } finally {
            emailSender.discardPrepared();
        }
    }

    @Test
    @DisplayName("should leave archive provider unset when SMTP provider is missing")
    void shouldLeaveArchiveProviderUnset_whenSmtpProviderMissing() throws Exception {
        EmailConfig emailConfig = smtpEmailConfig();
        emailConfig.setEmailProvider(null);
        EmailData emailData = emailData(List.of());
        EmailLog emailLog = new EmailLog(emailConfig, "provider@example.test", emailData.getRecipients(), emailData.getSubject(), emailData.getBody(), EmailLog.EmailStatus.FAILED);
        injectDependency(emailLog, "id", 45);
        EmailSender emailSender = new EmailSender(loggedInInfo, emailConfig, emailData);

        try {
            OutboundEmailArchiveDto archiveRequest = emailSender.prepareOutboundArchive(emailLog);
            assertThat(archiveRequest.getProviderName()).isNull();
        } finally {
            emailSender.discardPrepared();
        }
    }

    @Test
    @DisplayName("should reject repeated SMTP archive preparation")
    void shouldRejectRepeatedSmtpArchivePreparation() throws Exception {
        EmailConfig emailConfig = smtpEmailConfig();
        EmailData emailData = emailData(List.of());
        EmailLog emailLog = new EmailLog(emailConfig, "provider@example.test", emailData.getRecipients(),
                emailData.getSubject(), emailData.getBody(), EmailLog.EmailStatus.FAILED);
        injectDependency(emailLog, "id", 46);
        EmailSender emailSender = new EmailSender(loggedInInfo, emailConfig, emailData);
        emailSender.prepareOutboundArchive(emailLog);

        assertThatThrownBy(() -> emailSender.prepareOutboundArchive(emailLog))
                .isInstanceOf(EmailSendingException.class)
                .hasMessageContaining("already been prepared");

        emailSender.discardPrepared();
    }

    @Test
    @DisplayName("should reject SMTP archive preparation when email privilege is missing")
    void shouldRejectSmtpArchivePreparation_whenEmailPrivilegeMissing() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.WRITE, null)).thenReturn(false);
        EmailConfig emailConfig = smtpEmailConfig();
        EmailData emailData = emailData(List.of());
        EmailLog emailLog = new EmailLog(emailConfig, "provider@example.test", emailData.getRecipients(), emailData.getSubject(), emailData.getBody(), EmailLog.EmailStatus.FAILED);
        EmailSender emailSender = new EmailSender(loggedInInfo, emailConfig, emailData);

        assertThatThrownBy(() -> emailSender.prepareOutboundArchive(emailLog))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("missing required sec object (_email)");
    }

    @Test
    @DisplayName("should reject prepared SMTP send when email privilege is missing")
    void shouldRejectPreparedSmtpSend_whenEmailPrivilegeMissing() throws Exception {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_email", SecurityInfoManager.WRITE, null)).thenReturn(false);
        EmailSender emailSender = new EmailSender(loggedInInfo, smtpEmailConfig(), emailData(List.of()));
        SMTPEmailSender smtpSendHelper = mock(SMTPEmailSender.class);
        injectDependency(emailSender, "preparedTransport", smtpSendHelper);

        assertThatThrownBy(emailSender::sendPrepared)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("missing required sec object (_email)");
        verify(smtpSendHelper, never()).sendPrepared();
        verify(smtpSendHelper).discardPrepared();
    }

    @Test
    @DisplayName("should reject prepared SMTP send when message is not prepared")
    void shouldRejectPreparedSmtpSend_whenMessageIsNotPrepared() {
        EmailSender emailSender = new EmailSender(loggedInInfo, smtpEmailConfig(), emailData(List.of()));

        assertThatThrownBy(emailSender::sendPrepared)
                .isInstanceOf(EmailSendingException.class)
                .hasMessageContaining("Outbound message must be prepared before sending");
    }

    private EmailData emailData(List<EmailAttachment> attachments) {
        EmailData emailData = new EmailData();
        emailData.setRecipients(new String[]{"patient@example.test"});
        emailData.setSubject("Test subject");
        emailData.setBody("Body text");
        emailData.setAttachments(attachments);
        return emailData;
    }

    @Test
    @DisplayName("should archive or refuse for every transport configuration, with no unarchived send path")
    void shouldArchiveOrRefuse_forEveryTransportConfiguration() {
        // #3222's core requirement. The risk being pinned is not that a send fails -- it is that a
        // send SUCCEEDS while leaving no archive, which is invisible at runtime and only shows up
        // later as a missing legal record. The property that rules it out is agreement: for every
        // configuration the enums permit, sending and archiving must reach the same verdict.
        //
        // Sweeping the enum product rather than listing today's providers is deliberate. Adding an
        // EmailProvider constant with no transport is exactly how a silent gap would be
        // introduced, and this loop fails the moment such a constant appears.
        List<EmailConfig> configurations = new ArrayList<>();
        for (EmailConfig.EmailType emailType : EmailConfig.EmailType.values()) {
            for (EmailConfig.EmailProvider emailProvider : EmailConfig.EmailProvider.values()) {
                EmailConfig emailConfig = new EmailConfig(emailType, emailProvider, "provider@example.test");
                emailConfig.setConfigDetailsJson("{\"host\":\"smtp.example.test\",\"port\":\"587\",\"username\":\"u\",\"password\":\"p\"}");
                configurations.add(emailConfig);
            }
        }

        // LocalSMTPEmailSender is mocked separately: it is a SUBCLASS of SMTPEmailSender, and
        // mockConstruction does not intercept subclasses. Without this the SMTP/LOCAL pair
        // silently constructs a real local sender and this test measures its failure instead of
        // the routing being asserted.
        try (MockedConstruction<SMTPEmailSender> smtpSenders = mockConstruction(SMTPEmailSender.class);
             MockedConstruction<LocalSMTPEmailSender> localSenders = mockConstruction(LocalSMTPEmailSender.class);
             MockedConstruction<APISendGridEmailSender> sendGridSenders = mockConstruction(APISendGridEmailSender.class)) {

            for (EmailConfig emailConfig : configurations) {
                String configuration = emailConfig.getEmailType() + "/" + emailConfig.getEmailProvider();

                Throwable sendFailure = catchThrowable(
                        () -> new EmailSender(loggedInInfo, emailConfig, emailData(List.of())).send());
                Throwable archiveFailure = catchThrowable(
                        () -> new EmailSender(loggedInInfo, emailConfig, emailData(List.of()))
                                .prepareOutboundArchive(new EmailLog()));

                assertThat(archiveFailure == null)
                        .as("%s: archiving and sending must agree; a configuration that can send but "
                                + "cannot archive is a silent retention gap", configuration)
                        .isEqualTo(sendFailure == null);

                if (sendFailure != null) {
                    assertThat(sendFailure)
                            .as("%s: an unsupported configuration must be refused, not sent unarchived", configuration)
                            .isInstanceOf(EmailSendingException.class)
                            .hasMessage("Invalid email configuration");
                }
            }
        }
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
