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
package io.github.carlos_emr.carlos.documentManager.htmlpdf.engine;

import io.github.carlos_emr.carlos.utility.PDFGenerationException;
import org.openpdf.text.pdf.BaseFont;

import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * The fonts every HTML-to-PDF render prints with, as verified files on disk, and which characters
 * each of them can draw. Immutable and shared by all renders.
 *
 * <p>There are four faces: DejaVu Sans regular and bold (every requested family prints in these,
 * so a page looks the same on every server whatever fonts are installed) and two fallbacks used
 * only for characters DejaVu Sans lacks: WenQuanYi Micro Hei for Chinese, Japanese and Korean, and
 * Symbola for symbols. The two fallbacks are optional: if one cannot be loaded, the set reports it
 * unavailable and the characters it would have drawn print as U+FFFD instead.</p>
 *
 * <p>What is shared is only the file paths and the coverage tables. Flying Saucer registers a face
 * from a font <em>file path</em> (it creates, or takes from OpenPDF's cache, the {@link BaseFont}
 * it embeds), so {@link BundledFontResolver} registers the faces by the paths this set reports. The
 * {@code BaseFont}s held here are loaded not embedded and used only to answer
 * {@link #canDraw(Face, int)}, which reads their character tables and changes nothing.</p>
 *
 * @since 2026-10-29
 */
public final class BundledFontSet {

    /** The faces of the set. */
    public enum Face {
        SANS_REGULAR, SANS_BOLD, CJK, SYMBOL
    }

    /** The CSS family name every requested family is mapped to. */
    public static final String FAMILY_SANS = "CARLOS Sans";

    /** The CSS family name of the Chinese, Japanese and Korean fallback (set only by the fallback-font step). */
    public static final String FAMILY_CJK = "CARLOS CJK";

    /** The CSS family name of the symbol fallback (set only by the fallback-font step). */
    public static final String FAMILY_SYMBOL = "CARLOS Symbol";

    private final Map<Face, String> paths;
    private final Map<Face, BaseFont> coverage;

    private BundledFontSet(Map<Face, String> paths, Map<Face, BaseFont> coverage) {
        this.paths = new EnumMap<>(paths);
        this.coverage = new EnumMap<>(coverage);
    }

    /**
     * Loads the set from four font files.
     *
     * <p>The two DejaVu Sans faces are required. The two fallbacks may be {@code null}, and a
     * fallback that cannot be loaded is left out; either way it is reported unavailable.</p>
     *
     * @param sansRegular DejaVu Sans, regular
     * @param sansBold DejaVu Sans, bold
     * @param cjk WenQuanYi Micro Hei ({@code .ttc}: the first font of the collection is used), or {@code null}
     * @param symbol Symbola, or {@code null}
     * @return the set
     * @throws PDFGenerationException if a DejaVu Sans face is missing or cannot be loaded; the
     *         message names no path
     */
    public static BundledFontSet load(Path sansRegular, Path sansBold, Path cjk, Path symbol)
            throws PDFGenerationException {
        Map<Face, String> paths = new EnumMap<>(Face.class);
        Map<Face, BaseFont> coverage = new EnumMap<>(Face.class);
        loadRequired(Face.SANS_REGULAR, sansRegular, paths, coverage);
        loadRequired(Face.SANS_BOLD, sansBold, paths, coverage);
        loadOptional(Face.CJK, cjk, paths, coverage);
        loadOptional(Face.SYMBOL, symbol, paths, coverage);
        return new BundledFontSet(paths, coverage);
    }

    private static void loadRequired(Face face, Path file, Map<Face, String> paths, Map<Face, BaseFont> coverage)
            throws PDFGenerationException {
        if (file == null) {
            throw new PDFGenerationException("The bundled font " + face + " is missing");
        }
        String fontPath = fontPathOf(file);
        try {
            coverage.put(face, BaseFont.createFont(fontPath, BaseFont.IDENTITY_H, BaseFont.NOT_EMBEDDED));
            paths.put(face, fontPath);
        } catch (IOException | RuntimeException e) {
            // OpenPDF's DocumentException is unchecked, so RuntimeException covers it.
            throw new PDFGenerationException("The bundled font " + face + " could not be loaded", e);
        }
    }

    private static void loadOptional(Face face, Path file, Map<Face, String> paths, Map<Face, BaseFont> coverage) {
        if (file == null) {
            return;
        }
        String fontPath = fontPathOf(file);
        try {
            coverage.put(face, BaseFont.createFont(fontPath, BaseFont.IDENTITY_H, BaseFont.NOT_EMBEDDED));
            paths.put(face, fontPath);
        } catch (IOException | RuntimeException e) {
            // Left out: the face is reported unavailable and its characters print as U+FFFD.
        }
    }

    /** A font collection ({@code .ttc}) is addressed by file and index: the first font of it. */
    // FindSecBugs IMPROPER_UNICODE: case-fold of a font file extension (an internal file name); not a security decision
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "IMPROPER_UNICODE",
            justification = "case-fold of a font file extension (an internal file name); not a security decision")
    private static String fontPathOf(Path file) {
        String path = Objects.requireNonNull(file, "file").toString();
        return path.toLowerCase(Locale.ROOT).endsWith(".ttc") ? path + ",0" : path;
    }

    /**
     * Whether a face was loaded.
     *
     * @param face the face
     * @return {@code true} for both DejaVu Sans faces always, and for a fallback that loaded
     */
    public boolean isAvailable(Face face) {
        return paths.containsKey(face);
    }

    /**
     * Whether a face has a glyph for a character (an actual glyph, not the .notdef box).
     *
     * @param face the face
     * @param codePoint the Unicode code point
     * @return {@code false} when the face is unavailable or lacks the character
     */
    public boolean canDraw(Face face, int codePoint) {
        BaseFont font = coverage.get(face);
        return font != null && font.charExists(codePoint);
    }

    /**
     * The path Flying Saucer registers the face from.
     *
     * @param face the face
     * @return the file path, with a {@code ,0} suffix for the font collection; never logged
     * @throws IllegalStateException if the face is unavailable (ask {@link #isAvailable(Face)} first)
     */
    public String fontPath(Face face) {
        String path = paths.get(face);
        if (path == null) {
            throw new IllegalStateException("The bundled font " + face + " is not available");
        }
        return path;
    }
}
