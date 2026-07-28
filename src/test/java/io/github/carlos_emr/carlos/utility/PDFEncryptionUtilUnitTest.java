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
package io.github.carlos_emr.carlos.utility;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link PDFEncryptionUtil}, which encrypts e-mail attachment PDFs.
 *
 * <p>Guards the behaviour relied on by {@code EmailManager.encryptAttachments}: the output is a
 * real, password-protected PDF, and (since the move to {@code createSecureTempFile}) the encrypted
 * copy in the system temp dir is created with OWNER-only permissions instead of the default
 * world-readable permissions.</p>
 *
 * @since 2026-06-01
 */
@Tag("unit")
@Tag("fast")
@DisplayName("PDFEncryptionUtil")
class PDFEncryptionUtilUnitTest {

    @TempDir
    Path tempDir;

    /** Encrypted outputs land in the system temp dir (createSecureTempFile); track and clean them up. */
    private final List<Path> encryptedOutputs = new ArrayList<>();

    @AfterEach
    void cleanUp() throws IOException {
        for (Path p : encryptedOutputs) {
            Files.deleteIfExists(p);
        }
    }

    private Path writeSinglePagePdf() throws IOException {
        Path pdf = tempDir.resolve("sample.pdf");
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            doc.save(pdf.toFile());
        }
        return pdf;
    }

    private Path encrypt(Path source, String password) throws IOException {
        Path out = PDFEncryptionUtil.encryptPDF(source, password);
        encryptedOutputs.add(out);
        return out;
    }

    @Test
    @DisplayName("should produce a password-protected PDF when given a valid document")
    void shouldProducePasswordProtectedPdf_whenGivenValidDocument() throws IOException {
        Path source = writeSinglePagePdf();

        Path encrypted = encrypt(source, "s3cret");

        assertThat(encrypted).exists();
        assertThat(encrypted.toString()).endsWith(".pdf");
        // Opening without the password must fail...
        assertThatThrownBy(() -> Loader.loadPDF(encrypted.toFile()).close())
                .isInstanceOf(InvalidPasswordException.class);
        // ...and with the password the original content is preserved.
        try (PDDocument opened = Loader.loadPDF(encrypted.toFile(), "s3cret")) {
            assertThat(opened.isEncrypted()).isTrue();
            assertThat(opened.getNumberOfPages()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("should leave the source document untouched and write a separate output file")
    void shouldWriteSeparateOutput_whenEncrypting() throws IOException {
        Path source = writeSinglePagePdf();

        Path encrypted = encrypt(source, "pw");

        assertThat(encrypted).isNotEqualTo(source);
        // Source remains a normal, openable (unencrypted) PDF.
        try (PDDocument original = Loader.loadPDF(source.toFile())) {
            assertThat(original.isEncrypted()).isFalse();
        }
    }

    @Test
    @DisplayName("should restrict the encrypted file to owner-only permissions on POSIX filesystems")
    void shouldRestrictToOwnerOnly_whenFilesystemIsPosix() throws IOException {
        Path encrypted = encrypt(writeSinglePagePdf(), "pw");

        if (Files.getFileStore(encrypted).supportsFileAttributeView("posix")) {
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(encrypted);
            assertThat(perms).containsExactlyInAnyOrder(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
        }
    }

    @Test
    @DisplayName("should wrap failures as IOException when the source is not a valid PDF")
    void shouldThrowIoException_whenSourceIsNotAValidPdf() throws IOException {
        Path notAPdf = tempDir.resolve("garbage.pdf");
        Files.write(notAPdf, "this is not a pdf".getBytes());

        assertThatThrownBy(() -> PDFEncryptionUtil.encryptPDF(notAPdf, "pw"))
                .isInstanceOf(IOException.class);
    }
}
