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
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Extracts per-word bounding boxes from one page, so the annotation viewer can square a
 * highlight to the words it covers.
 *
 * <p><strong>The text layer is optional.</strong> A page may have none: a scan that was
 * never run through OCR, a photographed page, or an image-only fax. That is an ordinary
 * state, not a failure, and it yields an empty list. Every consumer must treat an empty
 * result as "no snap targets here" and fall back to the geometry the provider drew.
 * Highlighting and every other annotation tool work identically without it.
 *
 * <p>Boxes come back in the same normalised, top-left, rotation-applied space as
 * {@link DocumentAnnotationDto}, so the viewer can compare them against pointer positions
 * without knowing the DPI a page was rendered at.
 *
 * <p>The extracted <em>text</em> is deliberately discarded. Only geometry leaves this class;
 * the words themselves are PHI and the viewer has no need for them.
 *
 * @since 2026-09
 */
public final class DocumentWordBoxes {

    /** Sentinel for a run of glyphs that encloses no area, so has no snap geometry. */
    private static final double[] NO_BOX = new double[0];

    private DocumentWordBoxes() {
    }

    /**
     * @param pdf      the source document, opened read-only
     * @param page     1-based page number; out-of-range yields an empty list
     * @param maxWords ceiling on the returned boxes, so a dense page cannot produce an
     *                 unbounded payload
     * @return normalised {@code {x, y, w, h}} boxes, one per word; empty when the page has
     *         no text layer, which is a normal outcome
     * @throws IOException if the document cannot be opened or parsed
     */
    public static List<double[]> extract(File pdf, int page, int maxWords) throws IOException {
        List<double[]> boxes = new ArrayList<>();

        try (PDDocument document = Loader.loadPDF(pdf, IOUtils.createTempFileOnlyStreamCache())) {
            if (page < 1 || page > document.getNumberOfPages()) {
                return boxes;
            }
            PDPage pdPage = document.getPage(page - 1);
            PDRectangle box = pdPage.getCropBox();
            int rotation = ((pdPage.getRotation() % 360) + 360) % 360;
            boolean quarterTurn = rotation == 90 || rotation == 270;
            final float displayW = quarterTurn ? box.getHeight() : box.getWidth();
            final float displayH = quarterTurn ? box.getWidth() : box.getHeight();
            if (displayW <= 0f || displayH <= 0f) {
                return boxes;
            }

            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void writeString(String text, List<TextPosition> positions) {
                    for (List<TextPosition> word : splitWords(positions)) {
                        if (boxes.size() >= maxWords) {
                            return;
                        }
                        double[] wordBox = boundingBox(word, displayW, displayH);
                        if (wordBox.length == 4) {
                            boxes.add(wordBox);
                        }
                    }
                }
            };
            stripper.setStartPage(page);
            stripper.setEndPage(page);
            stripper.setSortByPosition(true);
            // Return value ignored on purpose: the text is PHI and only its geometry is wanted.
            stripper.getText(document);
        }

        return boxes;
    }

    /** Splits a run of glyphs into words on whitespace, keeping each glyph's position. */
    private static List<List<TextPosition>> splitWords(List<TextPosition> positions) {
        List<List<TextPosition>> words = new ArrayList<>();
        List<TextPosition> current = new ArrayList<>();
        for (TextPosition position : positions) {
            String unicode = position.getUnicode();
            if (unicode == null || unicode.isBlank()) {
                if (!current.isEmpty()) {
                    words.add(current);
                    current = new ArrayList<>();
                }
                continue;
            }
            current.add(position);
        }
        if (!current.isEmpty()) {
            words.add(current);
        }
        return words;
    }

    private static double[] boundingBox(List<TextPosition> word, float displayW, float displayH) {
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (TextPosition position : word) {
            float x = position.getXDirAdj();
            float y = position.getYDirAdj() - position.getHeightDir();
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x + position.getWidthDirAdj());
            maxY = Math.max(maxY, y + position.getHeightDir());
        }
        if (maxX <= minX || maxY <= minY) {
            // A zero-area run (a stray control glyph, say) is not a snap target.
            return NO_BOX;
        }
        return new double[]{
                clamp(minX / displayW),
                clamp(minY / displayH),
                clamp((maxX - minX) / displayW),
                clamp((maxY - minY) / displayH)
        };
    }

    private static double clamp(double value) {
        if (!Double.isFinite(value)) {
            return 0d;
        }
        return Math.clamp(value, 0d, 1d);
    }
}
