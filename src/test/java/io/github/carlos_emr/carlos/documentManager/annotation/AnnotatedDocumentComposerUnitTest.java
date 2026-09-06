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
package io.github.carlos_emr.carlos.documentManager.annotation;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the composer's two load-bearing guarantees: the source document is never touched,
 * and a mark drawn at a normalised coordinate lands at the corresponding place on the
 * rendered page for every page rotation.
 *
 * <p>The rotation cases matter clinically. A highlight that drifts to the wrong quadrant on
 * a rotated scan draws a provider's attention to the wrong line of a report, so each of the
 * four {@code /Rotate} values is rendered and inspected rather than asserted structurally.
 */
@Tag("unit")
@Tag("document")
@DisplayName("AnnotatedDocumentComposer")
class AnnotatedDocumentComposerUnitTest {

    private final AnnotatedDocumentComposer composer = new AnnotatedDocumentComposer();

    @Test
    @DisplayName("should report the page count the file actually has")
    void shouldReportPageCount_fromTheFile(@TempDir Path tempDir) throws Exception {
        // The document row carries its own numberofpages, and it lies: legacy rows hold zero and
        // a row can drift from the file it names. Both the view gate and the save path bound the
        // annotation page range with this number, so if it came from the row instead of the file
        // the viewer would offer pages the save path then refuses — losing the provider's work
        // with a message that makes no sense ("page 7, outside the document's 1 to 1").
        Path source = blankPdf(tempDir.resolve("ten-pages.pdf"), 10, 0);

        assertThat(composer.pageCount(source)).isEqualTo(10);
    }

    @Test
    @DisplayName("should leave the source file untouched when composing")
    void shouldLeaveSourceUntouched_whenComposing(@TempDir Path tempDir) throws Exception {
        Path source = blankPdf(tempDir.resolve("source.pdf"), 2, 0);
        byte[] before = Files.readAllBytes(source);

        composer.compose(source, List.of(highlight(1)), null, null);

        assertThat(Files.readAllBytes(source))
                .as("the received document is a clinical record and must not be modified")
                .isEqualTo(before);
    }

    @Test
    @DisplayName("should return a valid PDF with the source page count")
    void shouldReturnValidPdf_withSamePageCount(@TempDir Path tempDir) throws Exception {
        Path source = blankPdf(tempDir.resolve("source.pdf"), 3, 0);

        byte[] composed = composer.compose(source, List.of(highlight(2)), null, null);

        assertThat(new String(composed, 0, 4)).isEqualTo("%PDF");
        try (PDDocument document = Loader.loadPDF(composed)) {
            assertThat(document.getNumberOfPages()).isEqualTo(3);
        }
    }

    @Test
    @DisplayName("should mark only the page the annotation names")
    void shouldMarkOnlyNamedPage_whenComposing(@TempDir Path tempDir) throws Exception {
        Path source = blankPdf(tempDir.resolve("source.pdf"), 2, 0);

        byte[] composed = composer.compose(source, List.of(highlight(2)), null, null);

        assertThat(hasInk(composed, 1)).as("page 1 carried no annotation").isFalse();
        assertThat(hasInk(composed, 2)).as("page 2 carried the highlight").isTrue();
    }

    /**
     * The viewer places marks on a raster of the page as displayed, so a mark in the
     * top-left quarter of the display must render in the top-left quarter of the output at
     * every rotation. This drives the transform in
     * {@code AnnotatedDocumentComposer.displayToUserSpace}.
     */
    @ParameterizedTest(name = "rotation {0}")
    @ValueSource(ints = {0, 90, 180, 270})
    @DisplayName("should place a mark in the displayed top-left quadrant for every rotation")
    void shouldPlaceMarkInTopLeftQuadrant_forEveryRotation(int rotation, @TempDir Path tempDir) throws Exception {
        Path source = blankPdf(tempDir.resolve("rot" + rotation + ".pdf"), 1, rotation);

        DocumentAnnotationDto topLeft = new DocumentAnnotationDto(
                DocumentAnnotationDto.Type.HIGHLIGHT, 1, 0.05, 0.05, 0.30, 0.30,
                List.of(), null, "red", 2, 11);

        byte[] composed = composer.compose(source, List.of(topLeft), null, null);

        BufferedImage rendered = renderPage(composed, 1);
        assertThat(inkedQuadrant(rendered))
                .as("a mark drawn at the top left of the display must render at the top left")
                .isEqualTo("top-left");
    }

    @Test
    @DisplayName("should reject a signature mark when the provider has no stamp")
    void shouldReject_whenSignatureHasNoStamp(@TempDir Path tempDir) throws Exception {
        Path source = blankPdf(tempDir.resolve("source.pdf"), 1, 0);
        DocumentAnnotationDto signature = new DocumentAnnotationDto(
                DocumentAnnotationDto.Type.SIGNATURE, 1, 0.5, 0.8, 0.3, 0.06,
                List.of(), null, "black", 2, 11);

        List<DocumentAnnotationDto> marks = List.of(signature);

        assertThatThrownBy(() -> composer.compose(source, marks, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("signature stamp");
    }

    @Test
    @DisplayName("should reject a text mark when no font is available")
    void shouldReject_whenTextHasNoFont(@TempDir Path tempDir) throws Exception {
        Path source = blankPdf(tempDir.resolve("source.pdf"), 1, 0);
        DocumentAnnotationDto text = new DocumentAnnotationDto(
                DocumentAnnotationDto.Type.TEXT, 1, 0.1, 0.1, 0.5, 0.05,
                List.of(), "please review", "black", 2, 11);

        List<DocumentAnnotationDto> marks = List.of(text);

        assertThatThrownBy(() -> composer.compose(source, marks, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("font");
    }

    @Test
    @DisplayName("should reject an empty annotation model")
    void shouldReject_whenNoAnnotations(@TempDir Path tempDir) throws Exception {
        Path source = blankPdf(tempDir.resolve("source.pdf"), 1, 0);

        assertThatThrownBy(() -> composer.compose(source, List.of(), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            // rotation, cropX, cropY, cropW, cropH, markX, markY
            "  0,   0,   0, 612, 792, 0.10, 0.10",
            " 90,   0,   0, 612, 792, 0.10, 0.10",
            "180,   0,   0, 612, 792, 0.10, 0.10",
            "270,   0,   0, 612, 792, 0.10, 0.10",
            "  0,   0,   0, 612, 792, 0.70, 0.10",
            " 90,   0,   0, 612, 792, 0.70, 0.10",
            "180,   0,   0, 612, 792, 0.70, 0.10",
            "270,   0,   0, 612, 792, 0.70, 0.10",
            "  0,   0,   0, 612, 792, 0.10, 0.75",
            " 90,   0,   0, 612, 792, 0.10, 0.75",
            "180,   0,   0, 612, 792, 0.10, 0.75",
            "270,   0,   0, 612, 792, 0.10, 0.75",
            // A margin-trimming scanner leaves a CropBox with a non-zero lower-left origin.
            "  0, 100, 150, 400, 500, 0.10, 0.10",
            " 90, 100, 150, 400, 500, 0.10, 0.10",
            "180, 100, 150, 400, 500, 0.10, 0.10",
            "270, 100, 150, 400, 500, 0.10, 0.10",
            "  0, 100, 150, 400, 500, 0.70, 0.10",
            " 90, 100, 150, 400, 500, 0.70, 0.10",
            "180, 100, 150, 400, 500, 0.70, 0.10",
            "270, 100, 150, 400, 500, 0.70, 0.10",
    })
    @DisplayName("should land the mark where it was placed, for every rotation and crop box")
    void shouldLandMarkWherePlaced_forEveryRotationAndCropBox(
            int rotation, float cropX, float cropY, float cropW, float cropH,
            double markX, double markY, @TempDir Path tempDir) throws Exception {

        // This is the feature's central clinical invariant, and the only way to check it is to
        // RENDER the composed page and look at the pixels. A quadrant assertion is not enough:
        // several wrong matrices put a top-left mark somewhere in the top-left quadrant. Here the
        // measured position must match the requested position to within a pixel.
        Path source = tempDir.resolve("r" + rotation + "-" + markX + "-" + markY + ".pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(0, 0, 612, 792));
            page.setCropBox(new PDRectangle(cropX, cropY, cropW, cropH));
            page.setRotation(rotation);
            document.addPage(page);
            document.save(source.toFile());
        }

        String json = String.format(java.util.Locale.ROOT,
                "{\"annotations\":[{\"type\":\"highlight\",\"page\":1,\"x\":%f,\"y\":%f,"
                        + "\"w\":0.2,\"h\":0.1,\"color\":\"yellow\"}]}", markX, markY);
        byte[] output = composer.compose(
                source, new DocumentAnnotationParser().parse(json, 1), null, null);

        Path composed = tempDir.resolve("out-r" + rotation + "-" + markX + "-" + markY + ".pdf");
        java.nio.file.Files.write(composed, output);
        try (PDDocument rendered = org.apache.pdfbox.Loader.loadPDF(composed.toFile())) {
            java.awt.image.BufferedImage image =
                    new org.apache.pdfbox.rendering.PDFRenderer(rendered)
                            .renderImageWithDPI(0, 96, org.apache.pdfbox.rendering.ImageType.RGB);

            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    if ((image.getRGB(x, y) & 0xFFFFFF) != 0xFFFFFF) {
                        minX = Math.min(minX, x);
                        minY = Math.min(minY, y);
                    }
                }
            }
            assertThat(minX).as("the mark must actually render").isLessThan(Integer.MAX_VALUE);

            double gotX = (double) minX / image.getWidth();
            double gotY = (double) minY / image.getHeight();
            assertThat(gotX)
                    .as("rotation %d: mark left edge, measured on the rendered page", rotation)
                    .isCloseTo(markX, org.assertj.core.data.Offset.offset(0.02));
            assertThat(gotY)
                    .as("rotation %d: mark top edge, measured on the rendered page", rotation)
                    .isCloseTo(markY, org.assertj.core.data.Offset.offset(0.02));
        }
    }

    /* ---------- helpers ---------- */

    private static DocumentAnnotationDto highlight(int page) {
        return new DocumentAnnotationDto(DocumentAnnotationDto.Type.HIGHLIGHT, page,
                0.1, 0.1, 0.4, 0.1, List.of(), null, "yellow", 2, 11);
    }

    private static Path blankPdf(Path target, int pages, int rotation) throws IOException {
        try (PDDocument document = new PDDocument()) {
            for (int i = 0; i < pages; i++) {
                PDPage page = new PDPage(PDRectangle.LETTER);
                page.setRotation(rotation);
                document.addPage(page);
            }
            document.save(target.toFile());
        }
        return target;
    }

    private static BufferedImage renderPage(byte[] pdf, int page) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFRenderer(document).renderImageWithDPI(page - 1, 72, ImageType.RGB);
        }
    }

    private static boolean hasInk(byte[] pdf, int page) throws IOException {
        return countNonWhite(renderPage(pdf, page)) > 0;
    }

    private static long countNonWhite(BufferedImage image) {
        long count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) & 0xFFFFFF) != 0xFFFFFF) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * @return which quadrant of the rendered page holds the most non-white pixels. The page
     *         is otherwise blank, so the answer is where the single mark landed.
     */
    private static String inkedQuadrant(BufferedImage image) {
        int midX = image.getWidth() / 2;
        int midY = image.getHeight() / 2;
        long topLeft = 0;
        long topRight = 0;
        long bottomLeft = 0;
        long bottomRight = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) & 0xFFFFFF) == 0xFFFFFF) {
                    continue;
                }
                if (y < midY) {
                    if (x < midX) {
                        topLeft++;
                    } else {
                        topRight++;
                    }
                } else {
                    if (x < midX) {
                        bottomLeft++;
                    } else {
                        bottomRight++;
                    }
                }
            }
        }
        long best = Math.max(Math.max(topLeft, topRight), Math.max(bottomLeft, bottomRight));
        if (best == 0) {
            return "none";
        }
        if (best == topLeft) {
            return "top-left";
        }
        if (best == topRight) {
            return "top-right";
        }
        return best == bottomLeft ? "bottom-left" : "bottom-right";
    }
}
