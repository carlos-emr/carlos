/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.managers;

import java.nio.file.Files;
import java.nio.file.Path;
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

import io.github.carlos_emr.carlos.documentManager.ConvertToEdoc;
import io.github.carlos_emr.carlos.email.core.EmailData;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/** Verifies that encrypted delivery does not add secrets or clues to the visible MIME body. */
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
        emailData.setPassword("valid-password");
        emailData.setPasswordClue("Sensitive clue");
        emailData.setEncryptedMessage("Confidential clinical message");
        emailData.setAttachments(List.of());

        Path encryptedMessage = null;
        try (MockedStatic<ConvertToEdoc> converter = mockStatic(ConvertToEdoc.class)) {
            converter.when(() -> ConvertToEdoc.saveAsTempPDF(emailData))
                    .thenReturn(renderedMessage);

            new EmailManager().encryptEmail(emailData);

            assertThat(emailData.getAttachments()).hasSize(1);
            Path encryptedAttachment = Path.of(emailData.getAttachments().get(0).getFilePath());
            encryptedMessage = encryptedAttachment;
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
            assertThat(emailData.getBody()).doesNotContain(emailData.getPasswordClue());
        } finally {
            if (encryptedMessage != null) {
                Files.deleteIfExists(encryptedMessage);
            }
        }
    }
}
