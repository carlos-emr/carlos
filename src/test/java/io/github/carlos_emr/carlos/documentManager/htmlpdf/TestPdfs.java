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
package io.github.carlos_emr.carlos.documentManager.htmlpdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Test helper: reads geometry and text back out of a rendered PDF. A PDF page is 72 dots per inch, so
 * rendering at 72 dpi gives one pixel per point.
 */
public final class TestPdfs {

    private TestPdfs() {
    }

    /** Renders one page (1-based) at one pixel per point. */
    public static BufferedImage image(byte[] pdf, int page) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFRenderer(document).renderImageWithDPI(page - 1, 72f, ImageType.RGB);
        }
    }

    /**
     * The bounding box, in points from the top left, of the dark pixels of a rendered image.
     *
     * @return the box, or {@code null} if the page is blank
     */
    public static Rectangle darkBounds(BufferedImage image) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);
                int brightness = ((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF);
                if (brightness < 3 * 96) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        return maxX < 0 ? null : new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    /** The page sizes, as {width, height} in points, in page order. */
    public static List<float[]> pageSizes(byte[] pdf) throws IOException {
        List<float[]> sizes = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            for (PDPage page : document.getPages()) {
                sizes.add(new float[]{page.getMediaBox().getWidth(), page.getMediaBox().getHeight()});
            }
        }
        return sizes;
    }

    /** The scale the first page was placed with: the first operand of the first {@code cm} operator. */
    public static float zoomOf(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            List<Object> operands = new ArrayList<>();
            for (Object token : new PDFStreamParser(document.getPage(0)).parse()) {
                if (token instanceof Operator operator) {
                    if (operator.getName().equals("cm") && operands.size() == 6) {
                        return ((COSNumber) operands.get(0)).floatValue();
                    }
                    operands.clear();
                } else {
                    operands.add(token);
                }
            }
        }
        throw new IllegalStateException("no cm operator on the first page");
    }

    /** The text of each page, in page order, read by position. */
    public static List<String> pageTexts(byte[] pdf) throws IOException {
        List<String> texts = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                texts.add(stripper.getText(document));
            }
        }
        return texts;
    }

    /**
     * The size, in points, a glyph has on the page: its font size times every scale between its text
     * space and the page, so the placement zoom is included. ({@code TextPosition.getFontSizeInPt()}
     * is the size in text space and does not include it.)
     */
    public static float sizeOnPage(TextPosition glyph) {
        return glyph.getYScale();
    }

    /** Every glyph of the document with its position and size, in the order the stripper reads them. */
    public static List<TextPosition> glyphs(byte[] pdf) throws IOException {
        List<TextPosition> positions = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
                    positions.addAll(textPositions);
                    super.writeString(text, textPositions);
                }
            };
            stripper.setSortByPosition(true);
            stripper.getText(document);
        }
        return positions;
    }
}
