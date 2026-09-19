/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.documentManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.io.IOException;
import java.util.List;

import io.github.carlos_emr.CarlosProperties;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.openpdf.text.pdf.PdfCopy;
import org.openpdf.text.pdf.PdfStamper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;

@Tag("unit")
@Tag("document")
@DisplayName("Incoming PDF page mutation integrity")
class IncomingDocExtractionIntegrityUnitTest {
    @TempDir Path root;
    private String previousRoot;
    private String previousRecycle;
    private Path directory;
    private Path source;
    private byte[] original;

    @BeforeEach
    void setUp() throws Exception {
        previousRoot = CarlosProperties.getInstance().getProperty("INCOMINGDOCUMENT_DIR");
        previousRecycle = CarlosProperties.getInstance().getProperty("INCOMINGDOCUMENT_RECYCLEBIN");
        CarlosProperties.getInstance().setProperty("INCOMINGDOCUMENT_RECYCLEBIN", "true");
        CarlosProperties.getInstance().setProperty("INCOMINGDOCUMENT_DIR", root.toString());
        directory = Files.createDirectories(root.resolve("1/File"));
        source = directory.resolve("fixture.pdf");
        try (PDDocument pdf = new PDDocument()) {
            for (int number = 1; number <= 3; number++) {
                PDPage page = new PDPage(); pdf.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(pdf, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    content.newLineAtOffset(40, 700);
                    content.showText("Synthetic page " + number);
                    content.endText();
                }
            }
            pdf.save(source.toFile());
        }
        original = Files.readAllBytes(source);
    }

    @AfterEach
    void restoreProperties() {
        if (previousRoot == null) CarlosProperties.getInstance().remove("INCOMINGDOCUMENT_DIR");
        else CarlosProperties.getInstance().setProperty("INCOMINGDOCUMENT_DIR", previousRoot);
        if (previousRecycle == null) CarlosProperties.getInstance().remove("INCOMINGDOCUMENT_RECYCLEBIN");
        else CarlosProperties.getInstance().setProperty("INCOMINGDOCUMENT_RECYCLEBIN", previousRecycle);
    }

    private void extract(String pages) throws Exception {
        IncomingDocUtil.extractPage("1", "File", "fixture.pdf", pages);
    }

    private List<String> names() throws Exception {
        try (var files = Files.list(directory)) {
            return files.map(path -> path.getFileName().toString()).sorted().toList();
        }
    }

    @Test
    void shouldPreserveExactPageContents_whenExtractingMiddlePage() throws Exception {
        var permissions = Files.getPosixFilePermissions(source);
        extract("2");
        assertThat(Files.getPosixFilePermissions(source)).isEqualTo(permissions);
        try (PDDocument remaining = Loader.loadPDF(source.toFile());
             PDDocument extracted = Loader.loadPDF(directory.resolve("fixtureE3.pdf").toFile())) {
            assertThat(remaining.getNumberOfPages()).isEqualTo(2);
            assertThat(new PDFTextStripper().getText(remaining)).contains("Synthetic page 1", "Synthetic page 3").doesNotContain("Synthetic page 2");
            assertThat(extracted.getNumberOfPages()).isEqualTo(1);
            assertThat(new PDFTextStripper().getText(extracted)).contains("Synthetic page 2").doesNotContain("Synthetic page 1", "Synthetic page 3");
        }
        assertThat(names()).containsExactly("fixture.pdf", "fixtureE3.pdf");
    }

    @Test
    void shouldRetainOriginalAndCloseBothWriters_whenFinalizationFails() throws Exception {
        try (MockedConstruction<PdfCopy> copies = mockConstruction(PdfCopy.class, (copy, context) ->
                doThrow(new IllegalStateException("synthetic close failure")).when(copy).close())) {
            assertThatThrownBy(() -> extract("2")).isInstanceOf(IllegalStateException.class);
            assertThat(copies.constructed()).hasSize(2);
            for (PdfCopy copy : copies.constructed()) verify(copy).close();
        }
        assertThat(Files.readAllBytes(source)).isEqualTo(original);
        assertThat(names()).containsExactly("fixture.pdf");
    }

    @Test
    void shouldRemovePublishedExtractionAndRetainSource_whenSourceReplacementFails() throws Exception {
        try (MockedStatic<Files> files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            files.when(() -> Files.move(any(Path.class), eq(source),
                    eq(StandardCopyOption.ATOMIC_MOVE), eq(StandardCopyOption.REPLACE_EXISTING)))
                    .thenThrow(new IOException("synthetic replacement failure"));
            assertThatThrownBy(() -> extract("2")).isInstanceOf(IOException.class)
                    .hasMessage("synthetic replacement failure");
        }
        assertThat(Files.readAllBytes(source)).isEqualTo(original);
        assertThat(names()).containsExactly("fixture.pdf");
    }

    @Test
    void shouldPreserveExistingExtractedDocument_whenDestinationAlreadyExists() throws Exception {
        Path existing = directory.resolve("fixtureE3.pdf"); Files.write(existing, original);
        assertThatThrownBy(() -> extract("2")).isInstanceOf(java.nio.file.FileAlreadyExistsException.class);
        assertThat(Files.readAllBytes(source)).isEqualTo(original);
        assertThat(Files.readAllBytes(existing)).isEqualTo(original);
        assertThat(names()).containsExactly("fixture.pdf", "fixtureE3.pdf");
    }

    @Test
    void shouldPreserveUnrelatedQueueFile_whenLegacyTemporaryNameAlreadyExists() throws Exception {
        Path unrelated = directory.resolve("Tfixture.pdf"); Files.write(unrelated, original);
        extract("2");
        assertThat(Files.readAllBytes(unrelated)).isEqualTo(original);
        assertThat(names()).containsExactly("Tfixture.pdf", "fixture.pdf", "fixtureE3.pdf");
    }

    @Test
    void shouldLeaveSourceAndDirectoryUnchanged_whenAllPagesAreSelected() throws Exception {
        var permissions = Files.getPosixFilePermissions(source);
        assertThatThrownBy(() -> extract("1-3")).isInstanceOf(IllegalArgumentException.class);
        assertThat(Files.readAllBytes(source)).isEqualTo(original);
        assertThat(Files.getPosixFilePermissions(source)).isEqualTo(permissions);
        assertThat(names()).containsExactly("fixture.pdf");
    }
    private void mutate(String operation) throws Exception {
        switch (operation) {
            case "rotate" -> IncomingDocUtil.rotatePage("1", "File", "fixture.pdf", "2", 90);
            case "rotateAll" -> IncomingDocUtil.rotateAlPages("1", "File", "fixture.pdf", -90);
            case "delete" -> IncomingDocUtil.deletePage("1", "File", "fixture.pdf", "2");
            default -> throw new IllegalArgumentException("Unknown test operation");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"rotate", "rotateAll", "delete"})
    void shouldPreserveUnrelatedFileAndApplyExactChange_whenEditingPages(String operation) throws Exception {
        Path unrelated = directory.resolve("Tfixture.pdf"); Files.write(unrelated, original);
        var permissions = Files.getPosixFilePermissions(source);
        mutate(operation);
        assertThat(Files.readAllBytes(unrelated)).isEqualTo(original);
        assertThat(Files.getPosixFilePermissions(source)).isEqualTo(permissions);
        assertThat(names()).containsExactly("Tfixture.pdf", "fixture.pdf");
        try (PDDocument pdf = Loader.loadPDF(source.toFile())) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(operation.equals("delete") ? 2 : 3);
            if (!operation.equals("delete")) {
                for (int i = 0; i < 3; i++) {
                    assertThat(pdf.getPage(i).getRotation()).isEqualTo(operation.equals("rotateAll") ? 270 : (i == 1 ? 90 : 0));
                    // Normalize only the in-memory inspection view after checking
                    // saved rotations, so text extraction does not split vertical text.
                    pdf.getPage(i).setRotation(0);
                }
            }
            String text = new PDFTextStripper().getText(pdf);
            assertThat(text).contains("Synthetic page 1", "Synthetic page 3");
            if (operation.equals("delete")) {
                assertThat(text).doesNotContain("Synthetic page 2");
                try (PDDocument deleted = Loader.loadPDF(root.resolve("1/File_deleted/fixtured2of3.pdf").toFile())) {
                    assertThat(deleted.getNumberOfPages()).isEqualTo(1);
                    assertThat(new PDFTextStripper().getText(deleted)).contains("Synthetic page 2")
                            .doesNotContain("Synthetic page 1", "Synthetic page 3");
                }
            } else {
                assertThat(text).contains("Synthetic page 2");
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"rotate", "rotateAll", "delete"})
    void shouldRetainOriginalAndRemoveOutputs_whenPageMutationReplacementFails(String operation) throws Exception {
        try (MockedStatic<Files> files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            files.when(() -> Files.move(any(Path.class), eq(source),
                    eq(StandardCopyOption.ATOMIC_MOVE), eq(StandardCopyOption.REPLACE_EXISTING)))
                    .thenThrow(new IOException("synthetic replacement failure"));
            assertThatThrownBy(() -> mutate(operation)).isInstanceOf(IOException.class)
                    .hasMessage("synthetic replacement failure");
        }
        assertThat(Files.readAllBytes(source)).isEqualTo(original);
        assertThat(names()).containsExactly("fixture.pdf");
        assertThat(root.resolve("1/File_deleted/fixtured2of3.pdf")).doesNotExist();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "4"})
    void shouldRetainOriginalPermissionsAndFiles_whenPageIsOutsideDocument(String page) throws Exception {
        var permissions = Files.getPosixFilePermissions(source);
        assertThatThrownBy(() -> IncomingDocUtil.rotatePage("1", "File", "fixture.pdf", page, 90))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IncomingDocUtil.deletePage("1", "File", "fixture.pdf", page))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(source).hasBinaryContent(original);
        assertThat(Files.getPosixFilePermissions(source)).isEqualTo(permissions);
        assertThat(names()).containsExactly("fixture.pdf");
    }

    @Test
    void shouldRetainSourceAndCleanStaging_whenRotationFinalizationFails() throws Exception {
        try (MockedConstruction<PdfStamper> ignored = mockConstruction(PdfStamper.class, (stamper, context) ->
                doThrow(new IOException("synthetic rotation close failure")).when(stamper).close())) {
            assertThatThrownBy(() -> mutate("rotate")).isInstanceOf(IOException.class);
        }
        assertThat(Files.readAllBytes(source)).isEqualTo(original);
        assertThat(names()).containsExactly("fixture.pdf");
    }

    @Test
    void shouldPreserveRecycledDocumentAndSource_whenDeletedPageDestinationExists() throws Exception {
        Path recycled = Files.createDirectories(root.resolve("1/File_deleted")).resolve("fixtured2of3.pdf");
        Files.write(recycled, original);
        assertThatThrownBy(() -> mutate("delete")).isInstanceOf(java.nio.file.FileAlreadyExistsException.class);
        assertThat(Files.readAllBytes(source)).isEqualTo(original);
        assertThat(Files.readAllBytes(recycled)).isEqualTo(original);
        assertThat(names()).containsExactly("fixture.pdf");
    }

    @Test
    void shouldRetainOnlyRemainingPages_whenRecycleBinIsDisabled() throws Exception {
        CarlosProperties.getInstance().setProperty("INCOMINGDOCUMENT_RECYCLEBIN", "false");
        mutate("delete");
        try (PDDocument pdf = Loader.loadPDF(source.toFile())) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(2);
            assertThat(new PDFTextStripper().getText(pdf)).contains("Synthetic page 1", "Synthetic page 3")
                    .doesNotContain("Synthetic page 2");
        }
        assertThat(names()).containsExactly("fixture.pdf");
        assertThat(root.resolve("1/File_deleted/fixtured2of3.pdf")).doesNotExist();
    }

}
