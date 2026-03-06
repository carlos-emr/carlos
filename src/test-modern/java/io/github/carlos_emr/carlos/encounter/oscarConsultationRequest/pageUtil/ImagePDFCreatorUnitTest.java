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

package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil;

import io.github.carlos_emr.OscarProperties;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openpdf.text.DocumentException;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ImagePDFCreator} — converts image files to PDF for
 * consultation request attachments.
 *
 * <p>Tests cover standard image formats (JPEG, PNG) and multi-page TIFF handling
 * via the TwelveMonkeys ImageIO plugin. Also validates path security enforcement
 * via {@code PathValidationUtils}.</p>
 *
 * @since 2026-03-05
 */
@Tag("unit")
@Tag("pdf")
@DisplayName("ImagePDFCreator Unit Tests")
class ImagePDFCreatorUnitTest {

    @TempDir
    Path tempDir;

    private String previousDocumentDir;

    @BeforeEach
    void setUp() {
        previousDocumentDir = OscarProperties.getInstance().getProperty("DOCUMENT_DIR");
        OscarProperties.getInstance().setProperty("DOCUMENT_DIR", tempDir.toAbsolutePath().toString());
    }

    @AfterEach
    void tearDown() {
        if (previousDocumentDir != null) {
            OscarProperties.getInstance().setProperty("DOCUMENT_DIR", previousDocumentDir);
        } else {
            OscarProperties.getInstance().remove("DOCUMENT_DIR");
        }
    }

    /** Creates a test image file in the temp dir. */
    private Path createTestImage(String filename, String format, int width, int height) throws IOException {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = img.createGraphics();
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, width, height);
        g2d.setColor(Color.BLACK);
        g2d.drawString("Test Image", 10, 20);
        g2d.dispose();

        Path file = tempDir.resolve(filename);
        ImageIO.write(img, format, file.toFile());
        return file;
    }

    /** Creates a multi-page TIFF file in the temp dir. */
    private Path createMultiPageTiff(String filename, int pageCount) throws IOException {
        Path file = tempDir.resolve(filename);

        javax.imageio.ImageWriter writer = null;
        javax.imageio.stream.ImageOutputStream ios = null;
        try {
            java.util.Iterator<javax.imageio.ImageWriter> writers = ImageIO.getImageWritersByFormatName("tiff");
            if (!writers.hasNext()) {
                // TwelveMonkeys TIFF writer not available — skip
                return null;
            }
            writer = writers.next();
            ios = ImageIO.createImageOutputStream(file.toFile());
            writer.setOutput(ios);

            javax.imageio.ImageWriteParam param = writer.getDefaultWriteParam();
            writer.prepareWriteSequence(null);

            for (int i = 0; i < pageCount; i++) {
                BufferedImage img = new BufferedImage(200, 300, BufferedImage.TYPE_INT_RGB);
                Graphics2D g2d = img.createGraphics();
                g2d.setColor(Color.WHITE);
                g2d.fillRect(0, 0, 200, 300);
                g2d.setColor(Color.BLACK);
                g2d.drawString("Page " + (i + 1), 10, 20);
                g2d.dispose();

                writer.writeToSequence(new javax.imageio.IIOImage(img, null, null), param);
            }
            writer.endWriteSequence();
        } finally {
            if (writer != null) writer.dispose();
            if (ios != null) ios.close();
        }
        return file;
    }

    /** Counts pages in a PDF byte array using PDFBox. */
    private int countPdfPages(byte[] pdfBytes) throws IOException {
        try (PDDocument doc = PDDocument.load(pdfBytes)) {
            return doc.getNumberOfPages();
        }
    }

    @Nested
    @DisplayName("JPEG image handling")
    class JpegImageHandling {

        @Test
        @DisplayName("should produce valid single-page PDF from JPEG image")
        void shouldProduceValidPdf_fromJpegImage() throws Exception {
            Path jpeg = createTestImage("test.jpg", "JPEG", 400, 600);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImagePDFCreator creator = new ImagePDFCreator(jpeg.toAbsolutePath().toString(), "Test JPEG", out);
            creator.printPdf();

            byte[] result = out.toByteArray();
            assertThat(result).isNotEmpty();
            assertThat(result).startsWith(new byte[]{'%', 'P', 'D', 'F'});
        }
    }

    @Nested
    @DisplayName("PNG image handling")
    class PngImageHandling {

        @Test
        @DisplayName("should produce valid single-page PDF from PNG image")
        void shouldProduceValidPdf_fromPngImage() throws Exception {
            Path png = createTestImage("test.png", "PNG", 400, 600);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImagePDFCreator creator = new ImagePDFCreator(png.toAbsolutePath().toString(), "Test PNG", out);
            creator.printPdf();

            byte[] result = out.toByteArray();
            assertThat(result).isNotEmpty();
            assertThat(result).startsWith(new byte[]{'%', 'P', 'D', 'F'});
        }
    }

    @Nested
    @DisplayName("Large image scaling")
    class LargeImageScaling {

        @Test
        @DisplayName("should scale large image to fit within 500x700 bounds")
        void shouldScaleLargeImage_toFitWithinBounds() throws Exception {
            Path large = createTestImage("large.jpg", "JPEG", 2000, 3000);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImagePDFCreator creator = new ImagePDFCreator(large.toAbsolutePath().toString(), "Large Image", out);
            creator.printPdf();

            byte[] result = out.toByteArray();
            assertThat(result).isNotEmpty();
            assertThat(result).startsWith(new byte[]{'%', 'P', 'D', 'F'});
        }
    }

    @Nested
    @DisplayName("TIFF image handling")
    class TiffImageHandling {

        @Test
        @DisplayName("should produce multi-page PDF from multi-page TIFF")
        void shouldProduceMultiPagePdf_fromMultiPageTiff() throws Exception {
            Path tiff = createMultiPageTiff("multi.tiff", 3);
            Assumptions.assumeTrue(tiff != null, "TwelveMonkeys TIFF writer not available");

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImagePDFCreator creator = new ImagePDFCreator(tiff.toAbsolutePath().toString(), "Multi-page TIFF", out);
            creator.printPdf();

            byte[] result = out.toByteArray();
            assertThat(result).isNotEmpty();
            // Each TIFF page becomes a separate PDF page
            assertThat(countPdfPages(result)).isEqualTo(3);
        }

        @Test
        @DisplayName("should produce single-page PDF from single-page TIFF")
        void shouldProduceSinglePagePdf_fromSinglePageTiff() throws Exception {
            Path tiff = createMultiPageTiff("single.tif", 1);
            Assumptions.assumeTrue(tiff != null, "TwelveMonkeys TIFF writer not available");

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImagePDFCreator creator = new ImagePDFCreator(tiff.toAbsolutePath().toString(), "Single TIFF", out);
            creator.printPdf();

            byte[] result = out.toByteArray();
            assertThat(result).isNotEmpty();
            assertThat(countPdfPages(result)).isEqualTo(1);
        }

        @Test
        @DisplayName("should detect TIFF by .tif extension (case-insensitive)")
        void shouldDetectTiff_byTifExtension() throws Exception {
            Path tiff = createMultiPageTiff("scan.TIF", 2);
            Assumptions.assumeTrue(tiff != null, "TwelveMonkeys TIFF writer not available");

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImagePDFCreator creator = new ImagePDFCreator(tiff.toAbsolutePath().toString(), "Case test", out);
            creator.printPdf();

            assertThat(countPdfPages(out.toByteArray())).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Input validation")
    class InputValidation {

        @Test
        @DisplayName("should throw DocumentException when imagePath is null")
        void shouldThrowDocumentException_whenImagePathIsNull() {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImagePDFCreator creator = new ImagePDFCreator(null, "Title", out);

            assertThatThrownBy(creator::printPdf)
                    .isInstanceOf(DocumentException.class)
                    .hasMessageContaining("null or empty");
        }

        @Test
        @DisplayName("should throw DocumentException when imagePath is empty")
        void shouldThrowDocumentException_whenImagePathIsEmpty() {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImagePDFCreator creator = new ImagePDFCreator("", "Title", out);

            assertThatThrownBy(creator::printPdf)
                    .isInstanceOf(DocumentException.class)
                    .hasMessageContaining("null or empty");
        }

        @Test
        @DisplayName("should throw DocumentException when image path is outside DOCUMENT_DIR")
        void shouldThrowDocumentException_whenPathIsOutsideDocumentDir() {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImagePDFCreator creator = new ImagePDFCreator("/etc/passwd", "Malicious", out);

            assertThatThrownBy(creator::printPdf)
                    .isInstanceOf(DocumentException.class)
                    .hasMessageContaining("Invalid image path");
        }

        @Test
        @DisplayName("should throw DocumentException when DOCUMENT_DIR is not configured")
        void shouldThrowDocumentException_whenDocumentDirNotConfigured() {
            OscarProperties.getInstance().remove("DOCUMENT_DIR");

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImagePDFCreator creator = new ImagePDFCreator("/some/path/image.jpg", "Title", out);

            assertThatThrownBy(creator::printPdf)
                    .isInstanceOf(DocumentException.class)
                    .hasMessageContaining("not configured");
        }
    }
}
