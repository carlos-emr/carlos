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
package io.github.carlos_emr.carlos.managers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import io.github.carlos_emr.carlos.commn.model.EmailAttachment;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A PDF document renders in place (its stored file), but the compose window's attachment preview
 * serves only CARLOS application temp files. An emailed PDF document is therefore copied into the
 * application temp tree, and the stored file is never handed on.
 *
 * @since 2026-10-14
 */
@ExtendWith(MockitoExtension.class)
@Tag("unit")
@Tag("email")
@DisplayName("EmailComposeManager document attachments")
class EmailComposeManagerDocumentAttachmentUnitTest {

    @Mock
    private SecurityInfoManager securityInfoManager;

    @Mock
    private DocumentAttachmentManager documentAttachmentManager;

    /** The real file manager, so the copy really lands where the preview check looks. */
    @Spy
    private NioFileManager nioFileManager = new NioFileManagerImpl();

    private EmailComposeManager emailComposeManager;

    @TempDir
    Path documentDir;

    private Path created;

    /** The manager takes SecurityInfoManager through its constructor; the collaborators used here are fields. */
    @BeforeEach
    void createManager() {
        emailComposeManager = new EmailComposeManager(securityInfoManager);
        ReflectionTestUtils.setField(emailComposeManager, "documentAttachmentManager", documentAttachmentManager);
        ReflectionTestUtils.setField(emailComposeManager, "nioFileManager", nioFileManager);
    }

    @AfterEach
    void removeCreatedTempFile() throws Exception {
        if (created != null) {
            Files.deleteIfExists(created);
            Files.deleteIfExists(created.getParent());
        }
    }

    @Test
    @DisplayName("should attach an application temp copy of a PDF document that renders in place, leaving the stored file alone")
    void shouldAttachApplicationTempCopy_whenPdfDocumentRendersInPlace() throws Exception {
        Path stored = Files.write(documentDir.resolve("FAKE-stored-letter.pdf"),
                "%PDF-1.4 FAKE stored letter".getBytes(StandardCharsets.US_ASCII));
        when(securityInfoManager.hasPrivilege(any(), eq("_edoc"), anyString(), nullable(String.class))).thenReturn(true);
        when(documentAttachmentManager.renderDocument(any(), eq(DocumentType.DOC), eq(9401))).thenReturn(stored);

        List<EmailAttachment> attachments = emailComposeManager.prepareEDocAttachments(null, new String[]{"9401"});

        assertThat(attachments).hasSize(1);
        created = Path.of(attachments.get(0).getFilePath());
        assertThat(PathValidationUtils.isInApplicationTempDirectory(created.toFile()))
                .as("the attachment preview serves only application temp files").isTrue();
        assertThat(created).isNotEqualTo(stored).hasSameBinaryContentAs(stored);
        assertThat(stored).as("the stored document is untouched").exists()
                .hasBinaryContent("%PDF-1.4 FAKE stored letter".getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    @DisplayName("should fail the attachment without naming the file when the copy cannot be made")
    void shouldFailWithoutFileName_whenCopyCannotBeMade() throws Exception {
        Path stored = Files.write(documentDir.resolve("FAKE-stored-letter.pdf"),
                "%PDF-1.4 FAKE stored letter".getBytes(StandardCharsets.US_ASCII));
        doThrow(new IOException("FAKE no space left for " + stored)).when(nioFileManager).createTempFileFrom(any(), any());

        assertThatThrownBy(() -> emailComposeManager.applicationTempFile(stored))
                .isInstanceOf(PDFGenerationException.class)
                .hasMessageNotContaining("FAKE")
                .hasCauseInstanceOf(IOException.class);
        assertThat(stored).exists();
    }

    @Test
    @DisplayName("should use a rendered application temp file as it is, without another copy")
    void shouldKeepRenderedFile_whenAlreadyInApplicationTemp() throws Exception {
        ByteArrayOutputStream pdf = new ByteArrayOutputStream();
        pdf.writeBytes("%PDF-1.4 FAKE rendered scan".getBytes(StandardCharsets.US_ASCII));
        created = new NioFileManagerImpl().saveTempFile("FAKE-rendered-scan", pdf, "pdf");

        assertThat(emailComposeManager.applicationTempFile(created)).isEqualTo(created);
        verify(nioFileManager, never()).createTempFileFrom(any(), any());
    }
}
