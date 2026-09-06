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
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.blend.BlendMode;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Draws a validated annotation model onto a copy of a PDF and returns the composed
 * bytes. This is the only place in the annotation feature that writes PDF content.
 *
 * <p><strong>The source is never modified.</strong> The document is opened, marks are
 * appended to a page content stream, and the result is written to memory. The caller
 * decides where those bytes land. A failure part-way through therefore cannot leave a
 * damaged file behind, which matters because the source is a received clinical record.
 *
 * <p><strong>Coordinate handling.</strong> The viewer works on a raster of the page as
 * displayed, so its normalised coordinates have a top-left origin in <em>rotated</em>
 * space. PDF user space has a bottom-left origin in <em>unrotated</em> space. Each page
 * is therefore given a transform that maps the former to the latter, derived from the
 * page's {@code /Rotate} and CropBox, and every mark is drawn through it. Getting this
 * wrong puts a highlight on the wrong part of a clinical document, so
 * {@code AnnotatedDocumentComposerUnitTest} pins all four rotations.
 *
 * <p><strong>Hardening.</strong> PDFBox parses untrusted input here — inbound faxes are
 * attacker-controllable — so the document is loaded with a temp-file stream cache to
 * bound heap use, and the composed output is verified to start with {@code %PDF} and to
 * carry the same page count as the source before it is returned. Timeouts and input size
 * limits are the caller's responsibility; see {@link AnnotatedDocumentService}.
 *
 * <p>This class holds no DAO or session state and performs no authorisation. Callers
 * must check privileges before invoking it.
 *
 * @since 2026-09
 */
public class AnnotatedDocumentComposer {

    /** Highlight opacity. Low enough that OCR text under the mark stays readable on a fax. */
    private static final float HIGHLIGHT_ALPHA = 0.38f;

    /**
     * Fraction of a text mark's box height that sits above the baseline.
     *
     * <p>Not a page width — the previous wording said so and described nothing this constant does.
     * The composer has no font metrics for an arbitrary box, so it drops the baseline to this
     * fraction of the box height and draws from there. {@code documentAnnotate.js} carries the
     * same constant for the on-screen preview; the two must move together or a note lands at a
     * different height in the filed copy than the provider saw.
     */
    private static final double TEXT_BASELINE_RATIO = 0.78d;

    private static final Map<String, Color> COLORS = Map.of(
            "yellow", new Color(0xFF, 0xF1, 0x76),
            "green", new Color(0x7B, 0xE8, 0xB8),
            "blue", new Color(0x8F, 0xD3, 0xF4),
            "pink", new Color(0xFF, 0xC2, 0xDD),
            "red", new Color(0xE0, 0x3B, 0x3B),
            "black", new Color(0x1A, 0x1A, 0x1A));

    /**
     * Reads the true page count from the file.
     *
     * <p>The {@code document} row carries a page count too, but it is metadata: legacy rows hold
     * zero, and a row can drift from the file it names. Any limit that exists to bound work on an
     * untrusted document has to be measured against the document, so this is the number that
     * decides whether composition may run at all.
     *
     * <p>Untrusted input: call this through {@link BoundedPdfTask}, never directly on a request
     * thread.
     */
    public int pageCount(Path source) throws IOException {
        try (PDDocument document = Loader.loadPDF(source.toFile(), IOUtils.createTempFileOnlyStreamCache())) {
            return document.getNumberOfPages();
        }
    }

    /**
     * Composes the annotated PDF.
     *
     * @param source        the stored document; opened read-only and never written to
     * @param annotations   validated marks from {@link DocumentAnnotationParser}
     * @param signaturePng  the saving provider's stamp, or {@code null} when they have none.
     *                      Required only if the model contains a
     *                      {@link DocumentAnnotationDto.Type#SIGNATURE} mark.
     * @param unicodeFont   a TrueType font covering the locales CARLOS ships; required only
     *                      if the model contains text or date marks
     * @return the composed PDF bytes, verified as a PDF with the source's page count
     * @throws IOException              if the source cannot be read or the output cannot be built
     * @throws IllegalArgumentException if a signature mark is present without a stamp, or a
     *                                  text mark without a font
     * @throws IllegalStateException    if the composed bytes fail the PDF or page-count check
     */
    public byte[] compose(Path source, List<DocumentAnnotationDto> annotations,
                          Path signaturePng, Path unicodeFont) throws IOException {
        if (source == null) {
            throw new IllegalArgumentException("A source document is required.");
        }
        if (annotations == null || annotations.isEmpty()) {
            throw new IllegalArgumentException("At least one annotation is required.");
        }
        requireResources(annotations, signaturePng, unicodeFont);

        try (PDDocument document = Loader.loadPDF(source.toFile(), IOUtils.createTempFileOnlyStreamCache())) {
            int pageCount = document.getNumberOfPages();

            // Loaded lazily and once: embedding a font subset or an image per page would
            // bloat the output and slow composition on a long fax.
            PDType0Font font = null;
            PDImageXObject signature = null;

            for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
                List<DocumentAnnotationDto> onPage = forPage(annotations, pageIndex + 1);
                if (onPage.isEmpty()) {
                    continue;
                }
                PDPage page = document.getPage(pageIndex);

                if (font == null && needs(onPage, DocumentAnnotationDto.Type.TEXT,
                        DocumentAnnotationDto.Type.DATE)) {
                    try (InputStream fontStream = Files.newInputStream(unicodeFont)) {
                        font = PDType0Font.load(document, fontStream, true);
                    }
                }
                if (signature == null && needs(onPage, DocumentAnnotationDto.Type.SIGNATURE)) {
                    signature = PDImageXObject.createFromFileByContent(signaturePng.toFile(), document);
                }

                drawPage(document, page, onPage, font, signature);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            byte[] composed = out.toByteArray();
            verify(composed, pageCount);
            return composed;
        }
    }

    private void drawPage(PDDocument document, PDPage page, List<DocumentAnnotationDto> marks,
                          PDType0Font font, PDImageXObject signature) throws IOException {
        PDRectangle box = page.getCropBox();
        int rotation = ((page.getRotation() % 360) + 360) % 360;

        // Displayed page dimensions: at 90 and 270 the viewer sees the box transposed.
        boolean quarterTurn = rotation == 90 || rotation == 270;
        float displayW = quarterTurn ? box.getHeight() : box.getWidth();
        float displayH = quarterTurn ? box.getWidth() : box.getHeight();

        try (PDPageContentStream content =
                     new PDPageContentStream(document, page, AppendMode.APPEND, true, true)) {

            // One transform per page carries every mark from displayed top-left space into
            // the page's own user space, so each drawing routine below works in plain
            // display coordinates and stays readable.
            content.saveGraphicsState();
            content.transform(displayToUserSpace(box, rotation));

            for (DocumentAnnotationDto mark : marks) {
                switch (mark.getType()) {
                    case HIGHLIGHT -> drawHighlight(content, mark, displayW, displayH);
                    case INK -> drawInk(content, mark, displayW, displayH);
                    case TEXT, DATE -> drawText(content, mark, displayW, displayH, font);
                    case SIGNATURE -> drawSignature(content, mark, displayW, displayH, signature);
                    // Unreachable while the parser is the only construction path, but a new
                    // enum constant must not silently compose a document with a mark missing.
                    default -> throw new IllegalStateException(
                            "No drawing routine for annotation type " + mark.getType());
                }
            }

            content.restoreGraphicsState();
        }
    }

    /**
     * Builds the matrix from displayed top-left space to PDF user space.
     *
     * <p>Each case is derived by asking where the CropBox corners land once the renderer has
     * applied {@code /Rotate}, then inverting. Taking rotation 90 (a quarter turn clockwise
     * for display): the page's bottom-left corner appears at the top left of the rendered
     * image, its top-left corner at the top right, and its bottom-right corner at the bottom
     * left. So display x tracks user y and display y tracks user x, giving {@code (0,1,1,0)}.
     *
     * <p>Every case has determinant -1, because display space is y-down and user space is
     * y-up. That reflection is why {@link #drawText} and {@link #drawSignature} re-flip
     * locally; glyphs and images drawn straight through this transform would be mirrored.
     *
     * <p>{@code ox} and {@code oy} carry the CropBox origin, which is non-zero on cropped
     * scans. Folding it into the translation components keeps marks aligned to the visible
     * page rather than to the media box corner.
     */
    private static Matrix displayToUserSpace(PDRectangle box, int rotation) {
        float w = box.getWidth();
        float h = box.getHeight();
        float ox = box.getLowerLeftX();
        float oy = box.getLowerLeftY();

        return switch (rotation) {
            case 90 -> new Matrix(0, 1, 1, 0, ox, oy);
            case 180 -> new Matrix(-1, 0, 0, 1, ox + w, oy);
            case 270 -> new Matrix(0, -1, -1, 0, ox + w, oy + h);
            default -> new Matrix(1, 0, 0, -1, ox, oy + h);
        };
    }

    private void drawHighlight(PDPageContentStream content, DocumentAnnotationDto mark,
                               float pageW, float pageH) throws IOException {
        // Multiply keeps the glyphs beneath the mark visible. A plain opaque fill would
        // obscure the very text the provider is drawing attention to.
        PDExtendedGraphicsState state = new PDExtendedGraphicsState();
        state.setBlendMode(BlendMode.MULTIPLY);
        state.setNonStrokingAlphaConstant(HIGHLIGHT_ALPHA);

        content.saveGraphicsState();
        content.setGraphicsStateParameters(state);
        content.setNonStrokingColor(color(mark.getColor()));
        content.addRect((float) (mark.getX() * pageW), (float) (mark.getY() * pageH),
                (float) (mark.getW() * pageW), (float) (mark.getH() * pageH));
        content.fill();
        content.restoreGraphicsState();
    }

    private void drawInk(PDPageContentStream content, DocumentAnnotationDto mark,
                         float pageW, float pageH) throws IOException {
        List<double[]> points = mark.getPoints();
        if (points.isEmpty()) {
            return;
        }
        content.saveGraphicsState();
        content.setStrokingColor(color(mark.getColor()));
        content.setLineWidth((float) mark.getStrokeWidth());
        content.setLineCapStyle(1);
        content.setLineJoinStyle(1);
        double[] first = points.get(0);
        content.moveTo((float) (first[0] * pageW), (float) (first[1] * pageH));
        for (int i = 1; i < points.size(); i++) {
            double[] p = points.get(i);
            content.lineTo((float) (p[0] * pageW), (float) (p[1] * pageH));
        }
        content.stroke();
        content.restoreGraphicsState();
    }

    private void drawText(PDPageContentStream content, DocumentAnnotationDto mark,
                          float pageW, float pageH, PDType0Font font) throws IOException {
        float size = (float) mark.getFontSize();
        float x = (float) (mark.getX() * pageW);
        // The mark's y is the top of its box in display space; text is placed on a baseline,
        // so drop by roughly the font's ascent to keep the glyphs inside the box the provider drew.
        float baseline = (float) ((mark.getY() * pageH) + size * TEXT_BASELINE_RATIO);

        content.saveGraphicsState();
        content.setNonStrokingColor(color(mark.getColor()));
        content.beginText();
        // The page transform has a negative y scale, so text drawn under it would appear
        // mirrored. This text matrix re-flips just the glyphs while keeping the position.
        content.setTextMatrix(new Matrix(size, 0, 0, -size, x, baseline));
        content.setFont(font, 1f);
        content.showText(sanitize(mark.getText(), font));
        content.endText();
        content.restoreGraphicsState();
    }

    private void drawSignature(PDPageContentStream content, DocumentAnnotationDto mark,
                               float pageW, float pageH, PDImageXObject signature) throws IOException {
        float boxW = (float) (mark.getW() * pageW);
        float boxH = (float) (mark.getH() * pageH);
        float x = (float) (mark.getX() * pageW);
        float y = (float) (mark.getY() * pageH);

        // Preserve aspect: a stretched signature is a legally meaningful distortion.
        float scale = Math.min(boxW / signature.getWidth(), boxH / signature.getHeight());
        float drawW = signature.getWidth() * scale;
        float drawH = signature.getHeight() * scale;
        float cx = x + (boxW - drawW) / 2f;
        float cy = y + (boxH - drawH) / 2f;

        content.saveGraphicsState();
        // Same re-flip as text: the image would otherwise render upside down under the
        // page transform's negative y scale.
        content.transform(new Matrix(1, 0, 0, -1, cx, cy + drawH));
        content.drawImage(signature, 0, 0, drawW, drawH);
        content.restoreGraphicsState();
    }

    /**
     * Drops characters the embedded font cannot encode. PDFBox throws on an unmappable
     * glyph, and a provider pasting an exotic character must not fail the whole save.
     */
    private static String sanitize(String text, PDType0Font font) {
        StringBuilder safe = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            int codePoint = text.codePointAt(i);
            String piece = new String(Character.toChars(codePoint));
            i += Character.charCount(codePoint);
            if (codePoint == '\n' || codePoint == '\r' || codePoint == '\t') {
                safe.append(' ');
                continue;
            }
            try {
                font.encode(piece);
                safe.append(piece);
            } catch (IOException | IllegalArgumentException e) {
                safe.append(' ');
            }
        }
        return safe.toString();
    }

    private static void requireResources(List<DocumentAnnotationDto> annotations,
                                         Path signaturePng, Path unicodeFont) {
        if (needs(annotations, DocumentAnnotationDto.Type.SIGNATURE)
                && (signaturePng == null || !Files.isReadable(signaturePng))) {
            throw new IllegalArgumentException(
                    "A signature was placed but no signature stamp is available for this provider.");
        }
        if (needs(annotations, DocumentAnnotationDto.Type.TEXT, DocumentAnnotationDto.Type.DATE)
                && (unicodeFont == null || !Files.isReadable(unicodeFont))) {
            throw new IllegalArgumentException("The font required to draw text is not available.");
        }
    }

    private static boolean needs(List<DocumentAnnotationDto> marks, DocumentAnnotationDto.Type... types) {
        for (DocumentAnnotationDto mark : marks) {
            for (DocumentAnnotationDto.Type type : types) {
                if (mark.getType() == type) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<DocumentAnnotationDto> forPage(List<DocumentAnnotationDto> marks, int page) {
        List<DocumentAnnotationDto> onPage = new ArrayList<>();
        for (DocumentAnnotationDto mark : marks) {
            if (mark.getPage() == page) {
                onPage.add(mark);
            }
        }
        return onPage;
    }

    private static Color color(String name) {
        Color c = COLORS.get(name == null ? "" : name.toLowerCase(Locale.ROOT));
        return c == null ? COLORS.get("black") : c;
    }

    /**
     * Confirms the bytes about to be written are a PDF with the expected page count.
     * A composition bug that produced a truncated or empty file would otherwise be filed
     * as a clinical document and faxed.
     */
    private static void verify(byte[] composed, int expectedPages) throws IOException {
        if (composed.length < 4
                || composed[0] != '%' || composed[1] != 'P' || composed[2] != 'D' || composed[3] != 'F') {
            throw new IllegalStateException("The composed document is not a valid PDF.");
        }
        // Re-parsed from memory on purpose. Staging it through a temp file would put a
        // patient's document into a world-readable directory for the length of the check,
        // and the byte array is already bounded by the size ceiling the service enforces
        // before composition starts.
        try (RandomAccessReadBuffer buffer = new RandomAccessReadBuffer(composed);
             PDDocument check = Loader.loadPDF(buffer, IOUtils.createTempFileOnlyStreamCache())) {
            if (check.getNumberOfPages() != expectedPages) {
                throw new IllegalStateException("The composed document changed the page count.");
            }
        }
    }
}
