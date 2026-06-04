/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 *
 * Maintained by the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.utility;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link PDFEncryptionUtil} PDF encryption behavior.
 *
 * @since 2026-03-31
 */
@DisplayName("PDFEncryptionUtil Unit Tests")
@Tag("unit") @Tag("fast") @Tag("utility") @Tag("security")
class PDFEncryptionUtilUnitTest {

    @TempDir
    private Path tempDir;

    @Test
    @DisplayName("should encrypt PDF with password protection")
    void shouldEncryptPdf_withPasswordProtection() throws IOException {
        Path plainPdf = tempDir.resolve("plain.pdf");
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.save(plainPdf.toFile());
        }

        Path encryptedPdf = PDFEncryptionUtil.encryptPDF(plainPdf, "secret");

        try {
            assertThat(encryptedPdf).exists();
            assertThat(encryptedPdf).isNotEqualTo(plainPdf);
            assertThatThrownBy(() -> Loader.loadPDF(encryptedPdf.toFile()))
                    .isInstanceOf(IOException.class);
        } finally {
            Files.deleteIfExists(encryptedPdf);
        }
    }
}
