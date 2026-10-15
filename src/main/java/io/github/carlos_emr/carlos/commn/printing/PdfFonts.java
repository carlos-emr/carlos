/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.commn.printing;

import java.awt.Color;
import java.io.IOException;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Font;
import org.openpdf.text.pdf.BaseFont;

/**
 * Embedded fonts for clinical PDF text. The standard PDF Latin font families are
 * mapped to bundled Unicode faces, preserving serif, sans,
 * monospace and emphasis choices. Explicit custom fonts and symbol fonts retain
 * their caller-specified encoding. Font-loading errors propagate; falling back to
 * a Latin-only font would silently remove characters from the clinical record.
 */
public final class PdfFonts {
    private static final String DIRECTORY = "net/sf/jasperreports/fonts/dejavu/";

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
     * Returns a normal black clinical font.
     * @param name standard Latin font family or face name
     * @param size point size
     * @return the configured font
     */
    public static Font getFont(String name, float size) {
        return getFont(name, size, Font.NORMAL, Color.BLACK);
    }
}
