/**
 * Copyright (c) 2026 CARLOS EMR Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.lab.ca.all.pageUtil;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * {@link LabPDFCreator#addEmbeddedDocuments(File, java.io.OutputStream)} admits its source only
 * from an allowed temp directory. The chart print used to hand it a lab rendered under
 * DOCUMENT_DIR, which it refused: nothing was written, every lab was silently missing from the
 * printed chart, and the refusal was logged with the lab's file name, which is built from the
 * patient's name. These tests pin both halves of the contract without a database: the default
 * constructor renders nothing and touches no DAO.
 */
@DisplayName("LabPDFCreator.addEmbeddedDocuments")
@Tag("unit")
@Tag("lab")
class LabPDFCreatorEmbeddedDocumentsUnitTest {

    private static final String PATIENT_NAME_FRAGMENT = "PATIENT_FAKE_SURNAME";

    private File tempSource;
    private Path outsideDir;

    @AfterEach
    void tearDown() throws IOException {
        if (tempSource != null) {
            Files.deleteIfExists(tempSource.toPath());
        }
        if (outsideDir != null && Files.isDirectory(outsideDir)) {
            try (var children = Files.list(outsideDir)) {
                for (Path child : children.toList()) {
                    Files.deleteIfExists(child);
                }
            }
            Files.deleteIfExists(outsideDir);
        }
    }

    @Test
    @DisplayName("writes the source PDF through when it lives in an allowed temp directory")
    void shouldWriteSourcePdf_whenSourceIsInAllowedTempDirectory() throws IOException {
        tempSource = PathValidationUtils.createSecureTempFile("lab-embed-test-", ".pdf");
        writeOnePagePdf(tempSource);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        new LabPDFCreator().addEmbeddedDocuments(tempSource, out);

        assertThat(new String(out.toByteArray(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    @DisplayName("refuses a source outside the temp directories, writes nothing, and keeps the file name out of the log")
    void shouldRefuseSource_whenSourceIsOutsideTempDirectories() throws IOException {
        // target/ is the build's own scratch space: writable, project-local, and not a temp dir.
        outsideDir = Files.createDirectories(Path.of("target", "lab-embed-outside"));
        File outsideSource = outsideDir.resolve(PATIENT_NAME_FRAGMENT + "_2026-01-01_LabReport.pdf").toFile();
        writeOnePagePdf(outsideSource);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try (LogCapture logCapture = LogCapture.forLogger(LabPDFCreator.class)) {
            new LabPDFCreator().addEmbeddedDocuments(outsideSource, out);

            assertThat(out.size()).as("a refused source must not be copied to the output").isZero();
            assertThat(logCapture.messages()).anySatisfy(message -> assertThat(message).contains("rejected"));
            assertThat(logCapture.messages())
                    .as("the lab file name is built from the patient's name and must stay out of the log")
                    .noneSatisfy(message -> assertThat(message).contains(PATIENT_NAME_FRAGMENT));
        }
    }

    private static void writeOnePagePdf(File file) throws IOException {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.save(file);
        }
    }
}
