/*
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.documentManager;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedConstruction;
import org.openpdf.text.pdf.PdfCopy;
import org.openpdf.text.pdf.PdfImportedPage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.FileSystems;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.nullable;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("unit")
@Tag("document")
class IncomingDocUtilExtractionSafetyUnitTest extends CarlosUnitTestBase {
    @TempDir
    Path incomingRoot;

    @ParameterizedTest
    @ValueSource(strings = {"r--------", "r--r-----", "rw-r-----"})
    void shouldPreserveAccessModeOnBothOutputs_whenExtractingRestrictedPdf(String mode) throws Exception {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
        // Given a queued PDF whose access mode is more restrictive than ordinary file creation.
        CarlosProperties properties = CarlosProperties.getInstance();
        String previous = properties.getProperty("INCOMINGDOCUMENT_DIR");
        properties.setProperty("INCOMINGDOCUMENT_DIR", incomingRoot.toString());
        try {
            Path queue = Files.createDirectories(incomingRoot.resolve("1/Fax"));
            Path source = queue.resolve("restricted.pdf");
            try (PDDocument pdf = new PDDocument()) {
                pdf.addPage(new PDPage());
                pdf.addPage(new PDPage());
                pdf.save(source.toFile());
            }
            var permissions = PosixFilePermissions.fromString(mode);
            Files.setPosixFilePermissions(source, permissions);

            // When one page is extracted into a new document.
            IncomingDocUtil.extractPage("1", "Fax", "restricted.pdf", "2");

            // Then both completed PDFs retain the original access restrictions.
            assertThat(Files.getPosixFilePermissions(source)).isEqualTo(permissions);
            assertThat(Files.getPosixFilePermissions(queue.resolve("restrictedE2.pdf"))).isEqualTo(permissions);
            assertThat(IncomingDocUtil.getNumOfPages("1", "Fax", "restricted.pdf")).isEqualTo(1);
            assertThat(IncomingDocUtil.getNumOfPages("1", "Fax", "restrictedE2.pdf")).isEqualTo(1);
        } finally {
            if (previous == null) {
                properties.remove("INCOMINGDOCUMENT_DIR");
            } else {
                properties.setProperty("INCOMINGDOCUMENT_DIR", previous);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void shouldPreserveOriginalAndCloseBothWriters_whenExtractionWriteOrFinalizationFails(int failedWriter) throws Exception {
        // Given an actual queued PDF and either output writer failing during finalization.
        CarlosProperties properties = CarlosProperties.getInstance();
        String previous = properties.getProperty("INCOMINGDOCUMENT_DIR");
        properties.setProperty("INCOMINGDOCUMENT_DIR", incomingRoot.toString());
        try {
            Path queue = Files.createDirectories(incomingRoot.resolve("1/Fax"));
            Path source = queue.resolve("fixture.pdf");
            try (PDDocument pdf = new PDDocument()) {
                pdf.addPage(new PDPage());
                pdf.addPage(new PDPage());
                pdf.addPage(new PDPage());
                pdf.save(source.toFile());
            }
            byte[] original = Files.readAllBytes(source);
            try (MockedConstruction<PdfCopy> writers = mockConstruction(PdfCopy.class, (writer, context) -> {
                if (context.getCount() == failedWriter || (failedWriter == 0 && context.getCount() == 1)) {
                    doThrow(new IllegalStateException("private-output-path finalization failure")).when(writer).close();
                }
                if (failedWriter == 0 && context.getCount() == 1) {
                    doThrow(new IOException("Synthetic page copy failure")).when(writer).addPage(nullable(PdfImportedPage.class));
                }
            })) {
                // When the page copy reaches its final flush, publication must fail visibly.
                assertThatThrownBy(() -> IncomingDocUtil.extractPage("1", "Fax", "fixture.pdf", "2"))
                        .isInstanceOf(IOException.class)
                        .hasMessage(failedWriter == 0 ? "Synthetic page copy failure" : "Could not finish PDF page extraction")
                        .satisfies(failure -> {
                            if (failedWriter == 0) {
                                assertThat(failure.getSuppressed()).hasSize(1);
                                assertThat(failure.getSuppressed()[0]).hasMessage("Could not finish PDF page extraction");
                            }
                        });

                // Then both writers were closed, and neither partial output replaces clinical data.
                assertThat(writers.constructed()).hasSize(2);
                for (PdfCopy writer : writers.constructed()) {
                    verify(writer).close();
                }
                assertThat(Files.readAllBytes(source)).isEqualTo(original);
                assertThat(queue.resolve("fixtureE3.pdf")).doesNotExist();
                try (Stream<Path> remaining = Files.list(queue)) {
                    assertThat(remaining.toList()).containsExactly(source);
                }
            }
        } finally {
            if (previous == null) {
                properties.remove("INCOMINGDOCUMENT_DIR");
            } else {
                properties.setProperty("INCOMINGDOCUMENT_DIR", previous);
            }
        }
    }
}
