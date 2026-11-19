/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.managers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import io.github.carlos_emr.carlos.commn.model.EmailAttachment;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.documentManager.ConvertToEdoc;
import io.github.carlos_emr.carlos.email.core.EmailComposeWorkingDirectory;
import io.github.carlos_emr.carlos.email.core.EmailConsentResolver;
import io.github.carlos_emr.carlos.email.core.EmailData;
import io.github.carlos_emr.carlos.email.core.EmailSenderFactory;
import io.github.carlos_emr.carlos.email.core.PdfAttachmentSigningService;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.EmailSendingException;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * Verifies that encrypted delivery does not add secrets or clues to the visible MIME body.
 *
 * @since 2026-09-11
 */
@Tag("unit")
@Tag("fast")
@Tag("email")
@Tag("security")
@DisplayName("EmailManager encrypted visible body")
class EmailManagerEncryptedBodyUnitTest extends CarlosUnitTestBase {

    @TempDir
    Path tempDir;

    @BeforeEach
    void registerConvertToEdocDependency() {
        // ConvertToEdoc resolves this static-final dependency when Mockito instruments the class.
        registerMock(NioFileManager.class, mock(NioFileManager.class));
    }

    @Test
    @DisplayName("should preserve the fixed notice when encrypting an email")
    void shouldPreserveFixedNotice_whenEncryptingEmail() throws Exception {
        Path renderedMessage = tempDir.resolve("message.pdf");
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.save(renderedMessage.toFile());
        }

        EmailData emailData = new EmailData();
        emailData.setBody("SECURE_NOTICE");
        emailData.setPassword(new io.github.carlos_emr.carlos.email.core.EmailPdfPasswordService().generatePassphrase());
        emailData.setPasswordClue("Sensitive clue");
        emailData.setEncryptedMessage("Confidential clinical message");
        emailData.setAttachments(List.of());

        Path encryptedMessage = tempDir.resolve("encrypted-message.pdf");
        try (MockedStatic<ConvertToEdoc> converter = mockStatic(ConvertToEdoc.class);
                MockedStatic<PathValidationUtils> pathValidation =
                        mockStatic(PathValidationUtils.class, CALLS_REAL_METHODS)) {
            converter.when(() -> ConvertToEdoc.saveAsTempPDF(emailData))
                    .thenReturn(renderedMessage);
            pathValidation.when(() -> PathValidationUtils.createSecureTempFile(
                            anyString(), eq(".pdf")))
                    .thenReturn(encryptedMessage.toFile());

            createEmailManager().encryptEmail(emailData);

            assertThat(emailData.getAttachments()).hasSize(1);
            Path encryptedAttachment = Path.of(emailData.getAttachments().get(0).getFilePath());
            assertThat(encryptedAttachment).exists();
            assertThat(encryptedAttachment).isNotEqualTo(renderedMessage);
            assertThat(emailData.getAttachments()).singleElement().satisfies(attachment -> {
                assertThat(attachment.getFileName()).isEqualTo("message.pdf");
                assertThat(Path.of(attachment.getFilePath())).exists();
            });
            converter.verify(() -> ConvertToEdoc.saveAsTempPDF(emailData));
            assertThatThrownBy(() -> Loader.loadPDF(encryptedAttachment.toFile()).close())
                    .isInstanceOf(InvalidPasswordException.class);
            try (PDDocument opened = Loader.loadPDF(
                    encryptedAttachment.toFile(), emailData.getPassword())) {
                assertThat(opened.isEncrypted()).isTrue();
            }

            assertThat(emailData.getBody()).isEqualTo("SECURE_NOTICE");
            assertThat(emailData.getBody()).doesNotContain(emailData.getPasswordClue(), emailData.getPassword());
        } finally {
            if (emailData.getWorkingDirectory() != null) emailData.getWorkingDirectory().close();
        }
    }

    @Test
    @DisplayName("should report a controlled send failure when message PDF rendering fails")
    void shouldReportControlledFailure_whenMessagePdfRenderingFails() {
        EmailData emailData = new EmailData();
        emailData.setBody("SECURE_NOTICE");
        emailData.setPassword("valid-password");
        emailData.setEncryptedMessage("Confidential clinical message");
        emailData.setAttachments(List.of());

        try (MockedStatic<ConvertToEdoc> converter = mockStatic(ConvertToEdoc.class)) {
            converter.when(() -> ConvertToEdoc.saveAsTempPDF(emailData)).thenReturn(null);

            assertThatThrownBy(() -> createEmailManager().encryptEmail(emailData))
                    .isInstanceOf(EmailSendingException.class)
                    .hasMessage("Failed to render encrypted message attachment");
        } finally {
            if (emailData.getWorkingDirectory() != null) emailData.getWorkingDirectory().close();
        }
    }

    @Test
    @DisplayName("should remove the encrypted copy and keep the original when the working directory cannot adopt it")
    void shouldRemoveEncryptedCopyAndKeepOriginal_whenWorkingDirectoryCannotAdoptIt() throws Exception {
        Path original = tempDir.resolve("lab-result.pdf");
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.save(original.toFile());
        }
        byte[] originalBytes = Files.readAllBytes(original);

        EmailData emailData = new EmailData();
        emailData.setBody("SECURE_NOTICE");
        emailData.setPassword("FAKE-encryption-passphrase");
        emailData.setEncryptedMessage("");
        emailData.setIsAttachmentEncrypted(true);
        emailData.setAttachments(new ArrayList<>(List.of(
                new EmailAttachment("lab-result.pdf", original.toString(), DocumentType.DOC, 1))));
        // A directory that can no longer adopt anything: encryption succeeds, adoption fails.
        EmailComposeWorkingDirectory closedDirectory = EmailComposeWorkingDirectory.create();
        closedDirectory.close();
        emailData.setWorkingDirectory(closedDirectory);

        // Pin where the encrypted copy is written, so the test can see whether it is left behind.
        Path encryptedCopy = tempDir.resolve("encrypted-copy.pdf");
        try (MockedStatic<PathValidationUtils> pathValidation =
                mockStatic(PathValidationUtils.class, CALLS_REAL_METHODS)) {
            pathValidation.when(() -> PathValidationUtils.createSecureTempFile(anyString(), eq(".pdf")))
                    .thenReturn(encryptedCopy.toFile());

            assertThatThrownBy(() -> createEmailManager().encryptEmail(emailData))
                    .isInstanceOf(EmailSendingException.class)
                    .hasMessage("Failed to create encrypted email attachments");
            pathValidation.verify(() -> PathValidationUtils.createSecureTempFile(anyString(), eq(".pdf")));
        }

        // The encrypted copy holds the patient's document and nothing adopted it: it must be gone.
        assertThat(encryptedCopy).doesNotExist();
        // The source attachment is never touched, and the attachment still names it.
        assertThat(original).exists();
        assertThat(Files.readAllBytes(original)).isEqualTo(originalBytes);
        assertThat(emailData.getAttachments()).singleElement()
                .satisfies(attachment -> assertThat(attachment.getFilePath()).isEqualTo(original.toString()));
    }

    @Test
    @DisplayName("should remove the rendered message body when the working directory cannot adopt it")
    void shouldRemoveRenderedMessageBody_whenWorkingDirectoryCannotAdoptIt() throws Exception {
        // The rendered body is the secure message in plain text, written before encryption.
        Path renderedMessage = tempDir.resolve("emailbody.pdf");
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.save(renderedMessage.toFile());
        }

        EmailData emailData = new EmailData();
        emailData.setBody("SECURE_NOTICE");
        emailData.setPassword("FAKE-encryption-passphrase");
        emailData.setEncryptedMessage("FAKE confidential clinical message");
        emailData.setAttachments(List.of());
        // A directory that can no longer adopt anything: rendering succeeds, adoption fails.
        EmailComposeWorkingDirectory closedDirectory = EmailComposeWorkingDirectory.create();
        closedDirectory.close();
        emailData.setWorkingDirectory(closedDirectory);

        try (MockedStatic<ConvertToEdoc> converter = mockStatic(ConvertToEdoc.class)) {
            converter.when(() -> ConvertToEdoc.saveAsTempPDF(emailData)).thenReturn(renderedMessage);

            assertThatThrownBy(() -> createEmailManager().encryptEmail(emailData))
                    .isInstanceOf(EmailSendingException.class)
                    .hasMessage("Failed to create encrypted email message");
            converter.verify(() -> ConvertToEdoc.saveAsTempPDF(emailData));
        }

        assertThat(renderedMessage).doesNotExist();
        assertThat(emailData.getAttachments()).isEmpty();
    }

    private EmailManager createEmailManager() {
        return new EmailManager(
                mock(EmailConsentResolver.class), mock(EmailSenderFactory.class), mock(SecurityInfoManager.class), mock(OutboundEmailArchiveService.class),
                mock(PdfAttachmentSigningService.class));
    }
}
