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
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Pins that the OCR text layer is <strong>optional</strong>.
 *
 * <p>Snap-to-text highlighting is the one annotation feature that depends on a text layer.
 * Scanned faxes usually arrive with one applied upstream, but a page can have none, and
 * that must degrade to "no snap targets" rather than to an error or a broken tool. These
 * cases cover both shapes of page, so a future change cannot quietly make the text layer a
 * requirement.
 */
@Tag("unit")
@Tag("document")
@DisplayName("DocumentWordBoxes")
class DocumentWordBoxesUnitTest {

    private static final int MAX_WORDS = 5_000;

    @Test
    @DisplayName("should return an empty list when the page has no text layer")
    void shouldReturnEmptyList_whenPageHasNoTextLayer(@TempDir Path tempDir) throws Exception {
        // Stands in for a scan that was never OCR'd, or an image-only fax page.
        File imageOnly = blankPdf(tempDir.resolve("no-text.pdf"), 1, 0);

        List<double[]> boxes = DocumentWordBoxes.extract(imageOnly, 1, MAX_WORDS);

        assertThat(boxes)
                .as("a page with no text layer yields no snap targets, and that is not an error")
                .isEmpty();
    }

    @Test
    @DisplayName("should return normalised boxes when the page carries a text layer")
    void shouldReturnNormalisedBoxes_whenPageHasTextLayer(@TempDir Path tempDir) throws Exception {
        File withText = textPdf(tempDir.resolve("with-text.pdf"), "Referral for cardiology review");

        List<double[]> boxes = DocumentWordBoxes.extract(withText, 1, MAX_WORDS);

        assertThat(boxes).as("four words were drawn on the page").hasSizeGreaterThanOrEqualTo(4);
        for (double[] box : boxes) {
            assertThat(box).hasSize(4);
            assertThat(box[0]).isBetween(0d, 1d);
            assertThat(box[1]).isBetween(0d, 1d);
            assertThat(box[2]).isGreaterThan(0d).isLessThanOrEqualTo(1d);
            assertThat(box[3]).isGreaterThan(0d).isLessThanOrEqualTo(1d);
            assertThat(box[0] + box[2]).isLessThanOrEqualTo(1.0001d);
            assertThat(box[1] + box[3]).isLessThanOrEqualTo(1.0001d);
        }
    }

    @Test
    @DisplayName("should honour the word ceiling when the page is dense")
    void shouldHonourCeiling_whenPageIsDense(@TempDir Path tempDir) throws Exception {
        File withText = textPdf(tempDir.resolve("dense.pdf"), "alpha beta gamma delta epsilon zeta");

        List<double[]> boxes = DocumentWordBoxes.extract(withText, 1, 2);

        assertThat(boxes).hasSizeLessThanOrEqualTo(2);
    }

    @Test
    @DisplayName("should return an empty list when the page number is out of range")
    void shouldReturnEmptyList_whenPageOutOfRange(@TempDir Path tempDir) throws Exception {
        File single = blankPdf(tempDir.resolve("one-page.pdf"), 1, 0);

        assertThat(DocumentWordBoxes.extract(single, 7, MAX_WORDS)).isEmpty();
        assertThat(DocumentWordBoxes.extract(single, 0, MAX_WORDS)).isEmpty();
    }

    /**
     * The snap target must land on the text the provider can SEE, at every /Rotate.
     *
     * <p>Ground truth is measured from {@link PDFRenderer} rather than written down as a
     * constant. That is deliberate: the previous version of this test pinned hand-computed
     * numbers, and because those numbers were derived from the same mistaken assumption as the
     * code, the test certified a real defect as correct behaviour for as long as it existed.
     * The renderer used here is the one that produces the page images the viewer displays
     * (see {@code ManageDocument2Action} showPage), so agreement with it is the property that
     * actually matters — a wrong box means a highlight snapping onto unrelated text, which is
     * then composed into a filed and faxed copy.
     */
    @ParameterizedTest(name = "/Rotate {0}")
    @ValueSource(ints = {0, 90, 180, 270})
    @DisplayName("should land on the rendered text for every page rotation")
    void shouldLandOnRenderedText_forEveryPageRotation(int rotation, @TempDir Path tempDir)
            throws Exception {
        File rotated = textPdf(tempDir.resolve("rotated-" + rotation + ".pdf"), "POTASSIUM", rotation);

        List<double[]> boxes = DocumentWordBoxes.extract(rotated, 1, MAX_WORDS);

        assertThat(boxes).as("a rotated page still has extractable text").isNotEmpty();
        for (double[] box : boxes) {
            assertThat(box[0] + box[2]).isLessThanOrEqualTo(1.0001d);
            assertThat(box[1] + box[3]).isLessThanOrEqualTo(1.0001d);
        }

        double[] ink = renderedInkBox(rotated);
        double[] first = boxes.get(0);
        // A glyph box is slightly larger than the ink inside it (side bearings, ascent above the
        // tallest glyph), so compare centres with a tolerance well under the size of one word.
        double boxCentreX = first[0] + first[2] / 2d;
        double boxCentreY = first[1] + first[3] / 2d;
        double inkCentreX = (ink[0] + ink[2]) / 2d;
        double inkCentreY = (ink[1] + ink[3]) / 2d;
        assertThat(boxCentreX)
                .as("snap target x must sit on the text the viewer renders at /Rotate %d", rotation)
                .isCloseTo(inkCentreX, within(0.02d));
        assertThat(boxCentreY)
                .as("snap target y must sit on the text the viewer renders at /Rotate %d", rotation)
                .isCloseTo(inkCentreY, within(0.02d));
    }

    /**
     * @return {@code {left, top, right, bottom}} of the dark pixels on page 1, normalised against
     *         the rendered image, which is already in the page's displayed orientation
     */
    private static double[] renderedInkBox(File pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            BufferedImage image = new PDFRenderer(document).renderImageWithDPI(0, 144, ImageType.RGB);
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int maxX = -1;
            int maxY = -1;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int rgb = image.getRGB(x, y);
                    int luminance = (((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF)) / 3;
                    if (luminance < 128) {
                        minX = Math.min(minX, x);
                        minY = Math.min(minY, y);
                        maxX = Math.max(maxX, x);
                        maxY = Math.max(maxY, y);
                    }
                }
            }
            assertThat(maxX).as("the rendered page must actually contain ink").isGreaterThan(-1);
            return new double[]{
                    minX / (double) image.getWidth(), minY / (double) image.getHeight(),
                    (maxX + 1) / (double) image.getWidth(), (maxY + 1) / (double) image.getHeight()};
        }
    }

    @Test
    @DisplayName("should keep boxes inside the page when the CropBox origin is not zero")
    void shouldKeepBoxesInsidePage_whenCropBoxOriginIsNotZero(@TempDir Path tempDir) throws Exception {
        // A scanner that trims margins leaves a CropBox whose lower-left corner is not (0,0).
        // Boxes are normalised against the DISPLAYED page, so they must stay in the unit square
        // and must not drift by the crop offset.
        File cropped = croppedTextPdf(tempDir.resolve("cropped.pdf"), "Cardiology consultation note");

        List<double[]> boxes = DocumentWordBoxes.extract(cropped, 1, MAX_WORDS);

        assertThat(boxes).as("the text sits inside the crop region, so it is extractable").isNotEmpty();
        for (double[] box : boxes) {
            assertThat(box[0]).isBetween(0d, 1d);
            assertThat(box[1]).isBetween(0d, 1d);
            assertThat(box[0] + box[2]).isLessThanOrEqualTo(1.0001d);
            assertThat(box[1] + box[3]).isLessThanOrEqualTo(1.0001d);
        }
        // The words were drawn 36pt inside the crop's left edge, in a 400pt-wide crop, so a
        // correctly offset box starts near 0.09. If the CropBox origin were ignored the value
        // would be computed against the media box instead and land far to the right.
        assertThat(boxes.get(0)[0]).as("left edge is measured from the CropBox, not the MediaBox")
                .isBetween(0.02d, 0.25d);
    }

    @Test
    @DisplayName("should stop parsing once the word ceiling is reached")
    void shouldStopParsing_whenCeilingReached(@TempDir Path tempDir) throws Exception {
        File dense = textPdf(tempDir.resolve("ceiling.pdf"), "one two three four five six seven eight");

        assertThat(DocumentWordBoxes.extract(dense, 1, 3)).hasSize(3);
    }

    /* ---------- helpers ---------- */

    private static File blankPdf(Path target, int pages, int rotation) throws IOException {
        try (PDDocument document = new PDDocument()) {
            for (int i = 0; i < pages; i++) {
                PDPage page = new PDPage(PDRectangle.LETTER);
                page.setRotation(rotation);
                document.addPage(page);
            }
            document.save(target.toFile());
        }
        return target.toFile();
    }

    private static File textPdf(Path target, String text) throws IOException {
        return textPdf(target, text, 0);
    }

    /** A page whose CropBox is inset from the MediaBox, as a margin-trimming scanner produces. */
    private static File croppedTextPdf(Path target, String text) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            page.setCropBox(new PDRectangle(100f, 150f, 400f, 500f));
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 14);
                // 36pt inside the crop's left edge, comfortably inside its top edge.
                content.newLineAtOffset(136, 600);
                content.showText(text);
                content.endText();
            }
            document.save(target.toFile());
        }
        return target.toFile();
    }

    private static File textPdf(Path target, String text, int rotation) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            page.setRotation(rotation);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 14);
                content.newLineAtOffset(72, 600);
                content.showText(text);
                content.endText();
            }
            document.save(target.toFile());
        }
        return target.toFile();
    }
}
