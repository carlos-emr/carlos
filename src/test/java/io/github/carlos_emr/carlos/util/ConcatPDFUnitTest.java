//CHECKSTYLE:OFF
/**
 * Copyright (c) 2024-2026. CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

package io.github.carlos_emr.carlos.util;

import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

/**
 * Unit tests for {@link ConcatPDF} — the PDF merge utility rewritten from
 * iText PdfCopy to Apache PDFBox PDFMergerUtility.
 *
 * <p>Tests verify that the PDFBox-based implementation correctly handles
 * single-file, multi-file, mixed-input (files + streams), encrypted PDFs,
 * and error conditions (corrupted files, missing files).</p>
 *
 * @since 2026-03-05
 */
@Tag("unit")
@Tag("pdf")
@DisplayName("ConcatPDF Unit Tests")
class ConcatPDFUnitTest {

    @TempDir
    Path tempDir;

    /** Creates a minimal valid PDF with the given number of pages, each containing pageText. */
    private byte[] createPdf(int pageCount, String pageText) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            for (int i = 0; i < pageCount; i++) {
                PDPage page = new PDPage(PDRectangle.LETTER);
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    cs.newLineAtOffset(50, 700);
                    cs.showText(pageText + " page " + (i + 1));
                    cs.endText();
                }
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    /** Saves PDF bytes to a temp file and returns the absolute path. */
    private String savePdfFile(byte[] pdfBytes, String name) throws IOException {
        Path file = tempDir.resolve(name);
        Files.write(file, pdfBytes);
        return file.toAbsolutePath().toString();
    }

    /** Counts pages in a PDF byte array. */
    private int countPages(byte[] pdfBytes) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            return doc.getNumberOfPages();
        }
    }

    @Test
    @DisplayName("required merge refuses a corrupt section before writing any partial PDF")
    void shouldRejectIncompleteRequiredMerge_beforeWritingOutput() throws Exception {
        String good = savePdfFile(createPdf(1, "Required chart"), "required.pdf");
        String corrupt = savePdfFile("not a PDF".getBytes(java.nio.charset.StandardCharsets.UTF_8), "corrupt.pdf");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> ConcatPDF.concatRequired(List.of(good, corrupt), out))
                .isInstanceOf(IllegalStateException.class);
        assertThat(out.size()).isZero();
    }

    @Test
    @DisplayName("required merge includes every page of valid sections in order")
    void shouldIncludeEveryRequiredSection_whenAllInputsAreValid() throws Exception {
        String first = savePdfFile(createPdf(2, "First"), "first-required.pdf");
        String second = savePdfFile(createPdf(3, "Second"), "second-required.pdf");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ConcatPDF.concatRequired(List.of(first, second), out);
        assertThat(countPages(out.toByteArray())).isEqualTo(5);
    }

    @Test
    void shouldWriteNothing_whenTheMergerCannotSaveItsNormalizedSource() throws Exception {
        String good = savePdfFile(createPdf(1, "Valid source"), "source.pdf");
        Path unwritable = Files.createDirectory(tempDir.resolve("not-a-file"));
        var out = new ByteArrayOutputStream();
        try (var paths = mockStatic(PathValidationUtils.class, CALLS_REAL_METHODS)) {
            paths.when(() -> PathValidationUtils.createSecureTempFile("pdf-merge-source-", ".pdf"))
                    .thenReturn(unwritable.toFile());
            assertThatThrownBy(() -> ConcatPDF.concat(List.of(good), out))
                    .isInstanceOf(RuntimeException.class);
            assertThat(out.size()).isZero();
        }
        assertThat(unwritable).doesNotExist();
    }

    @Test
    void shouldSkipOnlyTheInput_whenACallerOwnedStreamCannotBeRead() throws Exception {
        var unreadable = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("FAKE source read failed");
            }
        };
        String good = savePdfFile(createPdf(1, "Valid source"), "valid.pdf");
        var out = new ByteArrayOutputStream();
        assertThat(ConcatPDF.concat(List.of(unreadable, good), out)).isEqualTo(1);
        assertThat(countPages(out.toByteArray())).isEqualTo(1);
    }

    @Test
    void shouldAbortAndCleanUp_whenWritingAnInputSpoolFails() throws Exception {
        var input = new ByteArrayInputStream(createPdf(1, "Valid stream"));
        Path spool = Files.createTempFile(tempDir, "spool-", ".pdf");
        var failingOutput = new OutputStream() {
            @Override
            public void write(int value) throws IOException {
                throw new IOException("FAKE spool write failed");
            }

            @Override
            public void close() throws IOException {
                throw new IOException("FAKE spool close failed");
            }
        };
        var out = new ByteArrayOutputStream();
        try (var paths = mockStatic(PathValidationUtils.class, CALLS_REAL_METHODS);
             var files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            paths.when(() -> PathValidationUtils.createSecureTempFile("pdf-merge-input-", ".pdf"))
                    .thenReturn(spool.toFile());
            files.when(() -> Files.newOutputStream(spool)).thenReturn(failingOutput);
            assertThatThrownBy(() -> ConcatPDF.concat(List.of(input), out)).isInstanceOf(RuntimeException.class);
            assertThat(out.size()).isZero();
        }
        assertThat(spool).doesNotExist();
    }

    @Nested
    @DisplayName("Single file merge")
    class SingleFileMerge {

        @Test
        @DisplayName("should produce valid PDF when merging a single file")
        void shouldProduceValidPdf_whenMergingSingleFile() throws Exception {
            byte[] pdf = createPdf(2, "Doc1");
            String path = savePdfFile(pdf, "single.pdf");

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ConcatPDF.concat(new ArrayList<>(List.of((Object) path)), out);

            byte[] result = out.toByteArray();
            assertThat(result).isNotEmpty();
            assertThat(result).startsWith(new byte[]{'%', 'P', 'D', 'F'});
            assertThat(countPages(result)).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Multi-file merge")
    class MultiFileMerge {

        @Test
        @DisplayName("should merge page counts from multiple files")
        void shouldMergePageCounts_fromMultipleFiles() throws Exception {
            String file1 = savePdfFile(createPdf(2, "Doc1"), "doc1.pdf");
            String file2 = savePdfFile(createPdf(3, "Doc2"), "doc2.pdf");
            String file3 = savePdfFile(createPdf(1, "Doc3"), "doc3.pdf");

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ConcatPDF.concat(new ArrayList<>(List.of((Object) file1, file2, file3)), out);

            byte[] result = out.toByteArray();
            assertThat(result).isNotEmpty();
            assertThat(countPages(result)).isEqualTo(6);
        }
    }

    @Nested
    @DisplayName("InputStream merge")
    class InputStreamMerge {

        @Test
        @DisplayName("should merge PDFs from InputStreams")
        void shouldMergePdfs_fromInputStreams() throws Exception {
            byte[] pdf1 = createPdf(1, "Stream1");
            byte[] pdf2 = createPdf(2, "Stream2");

            ArrayList<Object> inputs = new ArrayList<>();
            inputs.add(new ByteArrayInputStream(pdf1));
            inputs.add(new ByteArrayInputStream(pdf2));

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ConcatPDF.concat(inputs, out);

            byte[] result = out.toByteArray();
            assertThat(result).isNotEmpty();
            assertThat(countPages(result)).isEqualTo(3);
        }

        @Test
        @DisplayName("should merge mix of file paths and InputStreams")
        void shouldMergeMix_ofFilePathsAndInputStreams() throws Exception {
            String filePath = savePdfFile(createPdf(2, "File"), "file.pdf");
            byte[] streamPdf = createPdf(1, "Stream");

            ArrayList<Object> inputs = new ArrayList<>();
            inputs.add(filePath);
            inputs.add(new ByteArrayInputStream(streamPdf));

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ConcatPDF.concat(inputs, out);

            assertThat(countPages(out.toByteArray())).isEqualTo(3);
        }

        @Test
        void shouldReadRequiredStreamsWithoutBufferingTheWholeSource() throws Exception {
            InputStream source = new ByteArrayInputStream(createPdf(2, "Required chart section")) {
                @Override
                public byte[] readAllBytes() {
                    throw new AssertionError("The required PDF stream must be spooled to a file");
                }
            };
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            ConcatPDF.concatRequired(List.of(source), out);

            assertThat(countPages(out.toByteArray())).isEqualTo(2);
        }

        @Test
        void shouldRemoveStagedFiles_whenARequiredSourceCannotBeRead() throws Exception {
            InputStream valid = new ByteArrayInputStream(createPdf(1, "Valid chart section"));
            InputStream corrupt = new ByteArrayInputStream(new byte[]{0});
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (var paths = mockStatic(PathValidationUtils.class)) {
                paths.when(() -> PathValidationUtils.createSecureTempFile(anyString(), anyString()))
                        .thenAnswer(call -> Files.createTempFile(tempDir, call.getArgument(0), call.getArgument(1)).toFile());

                assertThatThrownBy(() -> ConcatPDF.concatRequired(List.of(valid, corrupt), out))
                        .isInstanceOf(IllegalStateException.class);
            }
            assertThat(out.size()).isZero();
            try (var files = Files.list(tempDir)) {
                assertThat(files).isEmpty();
            }
        }

        @Test
        void shouldRejectARequiredStream_whenItsPdfHasNoPages() throws Exception {
            var valid = new ByteArrayInputStream(createPdf(1, "Lab report"));
            var zeroPagePdf = new ByteArrayInputStream(createPdf(0, ""));
            var destination = new ByteArrayOutputStream();
            List<Object> inputs = List.of(valid, zeroPagePdf);

            assertThatThrownBy(() -> ConcatPDF.concatRequired(inputs, destination))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(destination.toByteArray()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Encrypted PDF handling")
    class EncryptedPdfHandling {

        @Test
        @DisplayName("should merge encrypted PDF by removing security")
        void shouldMergeEncryptedPdf_byRemovingSecurity() throws Exception {
            // Create and encrypt a PDF
            byte[] encryptedPdf;
            try (PDDocument doc = new PDDocument()) {
                doc.addPage(new PDPage(PDRectangle.LETTER));
                doc.protect(new org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy(
                        "owner", "", new org.apache.pdfbox.pdmodel.encryption.AccessPermission()));
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                doc.save(baos);
                encryptedPdf = baos.toByteArray();
            }
            String path = savePdfFile(encryptedPdf, "encrypted.pdf");

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ConcatPDF.concat(new ArrayList<>(List.of((Object) path)), out);

            byte[] result = out.toByteArray();
            assertThat(result).isNotEmpty();
            assertThat(countPages(result)).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Error handling")
    class ErrorHandling {

        @Test
        @DisplayName("should skip corrupted file and merge remaining valid files")
        void shouldSkipCorruptedFile_andMergeRemainingValidFiles() throws Exception {
            String goodFile = savePdfFile(createPdf(1, "Good"), "good.pdf");
            String badFile = savePdfFile("not a pdf at all".getBytes(), "bad.pdf");
            String good2File = savePdfFile(createPdf(2, "Good2"), "good2.pdf");

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ConcatPDF.concat(new ArrayList<>(List.of((Object) goodFile, badFile, good2File)), out);

            byte[] result = out.toByteArray();
            assertThat(result).isNotEmpty();
            // The corrupted file is skipped; good + good2 = 3 pages
            assertThat(countPages(result)).isEqualTo(3);
        }

        @Test
        @DisplayName("should skip missing file and merge remaining valid files")
        void shouldSkipMissingFile_andMergeRemainingValidFiles() throws Exception {
            String goodFile = savePdfFile(createPdf(1, "Good"), "good.pdf");
            String missingFile = tempDir.resolve("nonexistent.pdf").toAbsolutePath().toString();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ConcatPDF.concat(new ArrayList<>(List.of((Object) goodFile, missingFile)), out);

            byte[] result = out.toByteArray();
            assertThat(result).isNotEmpty();
            assertThat(countPages(result)).isEqualTo(1);
        }

        @Test
        @DisplayName("should produce empty output when all files are corrupted")
        void shouldProduceEmptyOutput_whenAllFilesAreCorrupted() throws Exception {
            String bad1 = savePdfFile("garbage".getBytes(), "bad1.pdf");
            String bad2 = savePdfFile("also garbage".getBytes(), "bad2.pdf");

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ConcatPDF.concat(new ArrayList<>(List.of((Object) bad1, bad2)), out);

            // With all files failing, merger has no sources — output should be empty
            assertThat(out.toByteArray()).isEmpty();
        }

        @Test
        @DisplayName("should produce empty output when input list is empty")
        void shouldProduceEmptyOutput_whenInputListIsEmpty() {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ConcatPDF.concat(new ArrayList<>(), out);

            assertThat(out.toByteArray()).isEmpty();
        }

        @Test
        @DisplayName("should skip corrupted InputStream and merge valid ones")
        void shouldSkipCorruptedInputStream_andMergeValidOnes() throws Exception {
            byte[] goodPdf = createPdf(1, "Good");
            InputStream badStream = new ByteArrayInputStream("not pdf".getBytes());

            ArrayList<Object> inputs = new ArrayList<>();
            inputs.add(new ByteArrayInputStream(goodPdf));
            inputs.add(badStream);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ConcatPDF.concat(inputs, out);

            byte[] result = out.toByteArray();
            assertThat(result).isNotEmpty();
            assertThat(countPages(result)).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("File path overload")
    class FilePathOverload {

        @Test
        @DisplayName("should write merged PDF to file path via string overload")
        void shouldWriteMergedPdf_toFilePathViaStringOverload() throws Exception {
            String input = savePdfFile(createPdf(2, "FileOverload"), "input.pdf");
            Path outputPath = tempDir.resolve("output.pdf");

            ConcatPDF.concat(new ArrayList<>(List.of((Object) input)), outputPath.toAbsolutePath().toString());

            byte[] result = Files.readAllBytes(outputPath);
            assertThat(result).isNotEmpty();
            assertThat(result).startsWith(new byte[]{'%', 'P', 'D', 'F'});
            assertThat(countPages(result)).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Skipped-input count")
    class SkippedCount {

        @Test
        @DisplayName("should return 0 when every input is included")
        void shouldReturnZero_whenAllInputsIncluded() throws Exception {
            String file1 = savePdfFile(createPdf(1, "A"), "a.pdf");
            String file2 = savePdfFile(createPdf(1, "B"), "b.pdf");

            int skipped = ConcatPDF.concat(new ArrayList<>(List.of((Object) file1, file2)), new ByteArrayOutputStream());

            assertThat(skipped).isZero();
        }

        @Test
        @DisplayName("should return the number of unreadable inputs that were skipped")
        void shouldReturnSkippedCount_whenSomeInputsUnreadable() throws Exception {
            String good = savePdfFile(createPdf(1, "Good"), "good.pdf");
            String bad = savePdfFile("not a pdf".getBytes(), "bad.pdf");

            int skipped = ConcatPDF.concat(new ArrayList<>(List.of((Object) good, bad)), new ByteArrayOutputStream());

            assertThat(skipped).isEqualTo(1);
        }

        @Test
        @DisplayName("should return the full count when every input is unreadable")
        void shouldReturnFullCount_whenAllInputsUnreadable() throws Exception {
            String bad1 = savePdfFile("garbage".getBytes(), "bad1.pdf");
            String bad2 = savePdfFile("more garbage".getBytes(), "bad2.pdf");

            int skipped = ConcatPDF.concat(new ArrayList<>(List.of((Object) bad1, bad2)), new ByteArrayOutputStream());

            assertThat(skipped).isEqualTo(2);
        }
    }
}
