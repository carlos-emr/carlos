/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.commn.printing;

import java.awt.Color;
import java.io.IOException;
import java.util.Objects;
import org.openpdf.text.Chunk;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Font;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.ColumnText;
import org.openpdf.text.pdf.PdfContentByte;

/**
 * Embedded fonts for clinical PDF text. The standard PDF Latin font families are
 * mapped to bundled Unicode faces, preserving serif, sans,
 * monospace and emphasis choices. Explicit custom fonts and symbol fonts retain
 * their caller-specified encoding. Font-loading errors propagate; falling back to
 * a Latin-only font would silently remove characters from the clinical record.
 *
 * <p>DejaVu has no Chinese, Japanese or Korean characters and few emoji. Text added through
 * {@link #addText(Phrase, String, Font)} switches, character by character, to bundled fallback
 * faces for those (WenQuanYi Micro Hei, then Symbola; see their NOTICE files under
 * {@code fonts/}), and marks a character no face can draw with U+FFFD rather than dropping it.
 */
public final class PdfFonts {
    private static final String DIRECTORY = "net/sf/jasperreports/fonts/dejavu/";
    /** Tried in order for a character the requested face cannot draw. */
    private static final String[] FALLBACK_FACES = {"fonts/wqy/wqy-microhei.ttc,0", "fonts/symbola/Symbola_hint.ttf"};
    private static final int REPLACEMENT_CHARACTER = 0xFFFD;

    private PdfFonts() { }

    /**
     * Creates a font, substituting embedded Unicode faces for the standard Latin families.
     *
     * @param name requested PDF font name or custom font resource
     * @param encoding encoding for custom/symbol fonts
     * @param embedded embedding policy for custom/symbol fonts
     * @return the requested font, with a Unicode replacement for standard Latin fonts
     * @throws IOException if the font resource cannot be read
     * @throws DocumentException if the font cannot be loaded
     */
    public static BaseFont createFont(String name, String encoding, boolean embedded)
            throws IOException, DocumentException {
        String face = switch (name) {
            case BaseFont.HELVETICA -> "DejaVuSans";
            case BaseFont.HELVETICA_BOLD -> "DejaVuSans-Bold";
            case BaseFont.HELVETICA_OBLIQUE -> "DejaVuSans-Oblique";
            case BaseFont.HELVETICA_BOLDOBLIQUE -> "DejaVuSans-BoldOblique";
            case BaseFont.TIMES_ROMAN -> "DejaVuSerif";
            case BaseFont.TIMES_BOLD -> "DejaVuSerif-Bold";
            case BaseFont.TIMES_ITALIC -> "DejaVuSerif-Italic";
            case BaseFont.TIMES_BOLDITALIC -> "DejaVuSerif-BoldItalic";
            // DejaVu Sans Mono lacks Vietnamese compound accents; retain fixed widths with Liberation.
            case BaseFont.COURIER -> "LiberationMono-Regular";
            case BaseFont.COURIER_BOLD -> "LiberationMono-Bold";
            case BaseFont.COURIER_OBLIQUE -> "LiberationMono-Italic";
            case BaseFont.COURIER_BOLDOBLIQUE -> "LiberationMono-BoldItalic";
            default -> null;
        };
        return face == null ? BaseFont.createFont(name, encoding, embedded)
                : BaseFont.createFont((face.startsWith("Liberation") ? "fonts/liberation/" : DIRECTORY)
                        + face + ".ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
    }

    /**
     * Returns an embedded clinical font, retaining emphasis and color.
     * @param name standard Latin font family or face name
     * @param size point size
     * @param style OpenPDF emphasis flags
     * @param color text color
     * @return the configured font
     * @throws IllegalStateException if the font cannot be loaded
     */
    public static Font getFont(String name, float size, int style, Color color) {
        try {
            return new Font(createFont(name, BaseFont.IDENTITY_H, BaseFont.EMBEDDED), size, style, color);
        } catch (IOException | DocumentException error) {
            throw new IllegalStateException("Clinical PDF font could not be loaded", error);
        }
    }

    /**
     * Appends text to a phrase in the given font, drawing each character the font lacks in the
     * first bundled fallback face that has it, at the same size, emphasis and color.
     *
     * <p>Use it for values read from the record (names, comments, vaccine and lot names), which
     * can be in any script. A character that no face can draw is printed as U+FFFD, so the
     * reader sees that something is missing; an invisible format character with no glyph (a
     * zero-width joiner, a variation selector, a tag character) is left out.
     *
     * @param phrase the phrase to append to
     * @param text the text, or {@code null} to append nothing
     * @param font the font for the characters it can draw, normally from {@link #getFont}
     * @throws IllegalStateException if a fallback face cannot be loaded
     */
    public static void addText(Phrase phrase, String text, Font font) {
        if (text == null || text.isEmpty()) {
            return;
        }
        if (font.getBaseFont() == null) {
            phrase.add(new Chunk(text, font));
            return;
        }
        Runs runs = new Runs(phrase, font);
        text.codePoints().forEach(runs::add);
        runs.flush();
    }

    /**
     * Returns a phrase in the given font holding text added through {@link #addText}, as a
     * drop-in for {@code new Phrase(text, font)}, which draws only the characters that font has.
     *
     * @param text String the text, or {@code null} for an empty phrase
     * @param font Font the phrase font, which later {@code Phrase.add(String)} calls also use
     * @return Phrase the new phrase
     */
    public static Phrase phrase(String text, Font font) {
        return phrase(Float.NaN, text, font);
    }

    /**
     * Returns a phrase with the given leading, as a drop-in for {@code new Phrase(leading, text, font)}.
     *
     * @param leading float the line leading, or {@code Float.NaN} for the font's default
     * @param text String the text, or {@code null} for an empty phrase
     * @param font Font the phrase font
     * @return Phrase the new phrase
     */
    public static Phrase phrase(float leading, String text, Font font) {
        Phrase phrase = new Phrase(leading, "", font);
        addText(phrase, text, font);
        return phrase;
    }

    /**
     * Returns a paragraph holding text added through {@link #addText}, as a drop-in for
     * {@code new Paragraph(text, font)}.
     *
     * @param text String the text, or {@code null} for an empty paragraph
     * @param font Font the paragraph font
     * @return Paragraph the new paragraph
     */
    public static Paragraph paragraph(String text, Font font) {
        Paragraph paragraph = new Paragraph("", font);
        addText(paragraph, text, font);
        return paragraph;
    }

    /**
     * Draws one line of text at a point, as {@code PdfContentByte.showTextAligned} does, but with
     * the fallback faces of {@link #addText}. The baseline sits at {@code y}; call it outside
     * {@code beginText()}/{@code endText()}.
     *
     * @param canvas PdfContentByte the content to draw on
     * @param alignment int {@code Element.ALIGN_LEFT}, {@code ALIGN_CENTER} or {@code ALIGN_RIGHT}
     *                  (the {@code PdfContentByte} constants share these values)
     * @param text String the text, or {@code null}/empty to draw nothing
     * @param font Font the font for the characters it can draw
     * @param x float the reference point's horizontal position
     * @param y float the baseline
     * @param rotation float the rotation in degrees
     */
    public static void showTextAligned(PdfContentByte canvas, int alignment, String text, Font font,
            float x, float y, float rotation) {
        if (text == null || text.isEmpty()) {
            return;
        }
        ColumnText.showTextAligned(canvas, alignment, phrase(text, font), x, y, rotation);
    }

    /** Groups consecutive characters drawn by the same face into one chunk. */
    private static final class Runs {
        private final Phrase phrase;
        private final Font font;
        private final StringBuilder run = new StringBuilder();
        private Font[] fallbacks;
        private Font runFont;

        Runs(Phrase phrase, Font font) {
            this.phrase = phrase;
            this.font = font;
        }

        void add(int codePoint) {
            int drawn = codePoint;
            Font face = faceFor(codePoint);
            if (face == null) {
                if (isInvisible(codePoint)) {
                    return;
                }
                drawn = REPLACEMENT_CHARACTER;
                face = Objects.requireNonNullElse(faceFor(drawn), font);
            }
            if (face != runFont) {
                flush();
                runFont = face;
            }
            run.appendCodePoint(drawn);
        }

        void flush() {
            if (!run.isEmpty()) {
                phrase.add(new Chunk(run.toString(), runFont));
                run.setLength(0);
            }
        }

        private Font faceFor(int codePoint) {
            if (codePoint == '\n' || codePoint == '\r' || codePoint == '\t' || font.getBaseFont().charExists(codePoint)) {
                return font;
            }
            if (fallbacks == null) {
                fallbacks = fallbacks(font);
            }
            for (Font fallback : fallbacks) {
                if (fallback.getBaseFont().charExists(codePoint)) {
                    return fallback;
                }
            }
            return null;
        }
    }

    private static Font[] fallbacks(Font font) {
        Font[] faces = new Font[FALLBACK_FACES.length];
        for (int i = 0; i < faces.length; i++) {
            try {
                // BaseFont caches each face, so a long printout reads the file once.
                faces[i] = new Font(BaseFont.createFont(FALLBACK_FACES[i], BaseFont.IDENTITY_H, BaseFont.EMBEDDED),
                        font.getSize(), font.getStyle(), font.getColor());
            } catch (IOException | DocumentException error) {
                throw new IllegalStateException("Clinical PDF fallback font could not be loaded", error);
            }
        }
        return faces;
    }

    private static boolean isInvisible(int codePoint) {
        return Character.getType(codePoint) == Character.FORMAT
                || (codePoint >= 0xFE00 && codePoint <= 0xFE0F)
                || (codePoint >= 0xE0100 && codePoint <= 0xE01EF);
    }

    /**
     * Returns a normal black clinical font.
     * @param name standard Latin font family or face name
     * @param size point size
     * @return the configured font
     */
    public static Font getFont(String name, float size) {
        return getFont(name, size, Font.NORMAL, Color.BLACK);
    }
}
