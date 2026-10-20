/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.documentManager;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
@Tag("document")
class IncomingDocumentRevisionUnitTest extends CarlosUnitTestBase {
    @TempDir Path root;

    @ParameterizedTest
    @ValueSource(strings = {"Rotate90", "RotateAll180", "DeletePage", "DeletePDF", "ExtractPagePDF"})
    void staleSelectionCannotMutateChangedQueuedPdf(String action) throws Exception {
        withSource(source -> {
            String observed = StoredDocumentRevision.sha256(source);
            IncomingDocUtil.doPagesAction("Rotate90", "1", "Fax", "source.pdf", "1", "2", Locale.ENGLISH, observed);
            byte[] changed = Files.readAllBytes(source);
            assertThat(StoredDocumentRevision.sha256(source)).isNotEqualTo(observed);
            assertThatThrownBy(() -> IncomingDocUtil.doPagesAction(action, "1", "Fax", "source.pdf", "1", "2", Locale.ENGLISH, observed))
                    .isInstanceOf(StoredDocumentRevision.ConflictException.class);
            assertThat(Files.readAllBytes(source)).isEqualTo(changed);
            try (var files = Files.list(source.getParent())) {
                assertThat(files.map(path -> path.getFileName().toString()).toList()).containsExactly("source.pdf");
            }
        });
    }

    @Test
    void sharedParserRefusalIsTypedBeforeAnyMutation() throws Exception {
        withSource(source -> {
            String revision = StoredDocumentRevision.sha256(source);
            byte[] before = Files.readAllBytes(source);
            try (var budget = org.mockito.Mockito.mockStatic(io.github.carlos_emr.carlos.documentManager.annotation.BoundedPdfTask.class)) {
                budget.when(io.github.carlos_emr.carlos.documentManager.annotation.BoundedPdfTask::acquireSynchronousAdmission)
                        .thenThrow(new io.github.carlos_emr.carlos.documentManager.annotation.BoundedPdfTask.BusyException());
                assertThatThrownBy(() -> IncomingDocUtil.doPagesAction("DeletePDF", "1", "Fax", "source.pdf", "1", "2", Locale.ENGLISH, revision))
                        .isInstanceOf(IncomingDocUtil.PageEditAdmissionBusyException.class);
            }
            assertThat(Files.readAllBytes(source)).isEqualTo(before);
        });
    }

    @Test
    void onlyOuterAdmissionRefusalIsTypedAsSafeToReplay() throws Exception {
        withSource(source -> {
            String revision = StoredDocumentRevision.sha256(source);
            byte[] before = Files.readAllBytes(source);
            try (var locks = org.mockito.Mockito.mockStatic(IncomingDocumentMutationLock.class)) {
                locks.when(() -> IncomingDocumentMutationLock.acquire(org.mockito.ArgumentMatchers.any(java.io.File.class), org.mockito.ArgumentMatchers.any(java.io.File.class)))
                        .thenThrow(new io.github.carlos_emr.carlos.documentManager.annotation.BoundedPdfTask.BusyException());
                assertThatThrownBy(() -> IncomingDocUtil.doPagesAction("DeletePDF", "1", "Fax", "source.pdf", "1", "2", Locale.ENGLISH, revision))
                        .isInstanceOf(IncomingDocUtil.PageEditAdmissionBusyException.class);
            }
            assertThat(Files.readAllBytes(source)).isEqualTo(before);
        });
    }

    @Test
    void missingRevisionIsRejectedButAnExplicitlyRefreshedIntentMayProceed() throws Exception {
        withSource(source -> {
            byte[] original = Files.readAllBytes(source);
            assertThatThrownBy(() -> IncomingDocUtil.doPagesAction("DeletePage", "1", "Fax", "source.pdf", "1", "2", Locale.ENGLISH, null))
                    .isInstanceOf(StoredDocumentRevision.ConflictException.class);
            assertThat(Files.readAllBytes(source)).isEqualTo(original);
            IncomingDocUtil.doPagesAction("Rotate90", "1", "Fax", "source.pdf", "1", "2", Locale.ENGLISH, StoredDocumentRevision.sha256(source));
            IncomingDocUtil.doPagesAction("Rotate90", "1", "Fax", "source.pdf", "1", "2", Locale.ENGLISH, StoredDocumentRevision.sha256(source));
            try (PDDocument pdf = org.apache.pdfbox.Loader.loadPDF(source.toFile())) {
                assertThat(pdf.getPage(0).getRotation()).isEqualTo(180);
                assertThat(pdf.getNumberOfPages()).isEqualTo(3);
            }
        });
    }

    @FunctionalInterface interface Work { void run(Path source) throws Exception; }
    private void withSource(Work work) throws Exception {
        CarlosProperties properties = CarlosProperties.getInstance();
        String previous = properties.getProperty("INCOMINGDOCUMENT_DIR");
        properties.setProperty("INCOMINGDOCUMENT_DIR", root.toString());
        try {
            Path source = Files.createDirectories(root.resolve("1/Fax")).resolve("source.pdf");
            try (PDDocument pdf = new PDDocument()) {
                for (int page = 0; page < 3; page++) pdf.addPage(new PDPage());
                pdf.save(source.toFile());
            }
            work.run(source);
        } finally {
            if (previous == null) properties.remove("INCOMINGDOCUMENT_DIR");
            else properties.setProperty("INCOMINGDOCUMENT_DIR", previous);
        }
    }
}
