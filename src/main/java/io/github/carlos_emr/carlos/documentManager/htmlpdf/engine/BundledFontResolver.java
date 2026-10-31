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

import io.github.carlos_emr.carlos.documentManager.htmlpdf.engine.BundledFontSet.Face;
import org.openpdf.text.pdf.BaseFont;
import org.xhtmlrenderer.css.value.FontSpecification;
import org.xhtmlrenderer.layout.SharedContext;
import org.xhtmlrenderer.pdf.FontFamily;
import org.xhtmlrenderer.pdf.ITextFontResolver;
import org.xhtmlrenderer.render.FSFont;

import java.io.IOException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Flying Saucer font resolver that prints every request in a bundled font.
 *
 * <p>With the fonts a production server has, wkhtmltopdf prints every family (generic, named or
 * unknown, and text with none) in DejaVu Sans. For output that matches it, and that does not change
 * from server to server, every request is resolved to the one registered family
 * {@value BundledFontSet#FAMILY_SANS} (regular and bold faces; italic is the regular or bold face
 * slanted by Flying Saucer, which keeps the character widths). The two exceptions are the
 * families {@value BundledFontSet#FAMILY_CJK} and {@value BundledFontSet#FAMILY_SYMBOL}, which only
 * the fallback-font step ever writes. Flying Saucer's built-in non-embedded fonts (Times,
 * Helvetica, Courier) are never available, so a font that is not embedded cannot print.</p>
 *
 * <p>Text is never smaller than {@link #MIN_FONT_SIZE_DOTS}, 10 CSS pixels, as in wkhtmltopdf
 * (an 8 px label is raised to 10 px: 7.5 pt before the page's 0.8 zoom, 6 pt on the printed page).</p>
 *
 * <p>One instance belongs to one renderer (a Flying Saucer font resolver keeps the faces it
 * resolved). It registers the fonts by the file paths of the {@link BundledFontSet}, and registers a
 * fallback face only when the first request for it arrives, because loading the Chinese font is the
 * slowest part of a render that does not need it.</p>
 *
 * @since 2026-10-29
 */
public final class BundledFontResolver extends ITextFontResolver {

    /**
     * The smallest font size, in Flying Saucer dots: 10 CSS pixels at 20 dots per pixel.
     */
    // wkhtmltopdf's --minimum-font-size 10 (spec §Output geometry)
    public static final float MIN_FONT_SIZE_DOTS = 200f;

    private final BundledFontSet fonts;
    private final Set<Face> registered = EnumSet.noneOf(Face.class);

    /**
     * Creates a resolver for one render.
     *
     * @param fonts the verified fonts
     * @throws IllegalStateException if a DejaVu Sans face cannot be registered (it was verified when
     *         the set was loaded, so this means the file changed since)
     */
    public BundledFontResolver(BundledFontSet fonts) {
        this.fonts = Objects.requireNonNull(fonts, "fonts");
        register(Face.SANS_REGULAR, BundledFontSet.FAMILY_SANS);
        register(Face.SANS_BOLD, BundledFontSet.FAMILY_SANS);
    }

    /** The built-in Times, Helvetica, Courier, Symbol and Dingbats families are not offered. */
    @Override
    protected Map<String, FontFamily> loadFonts() {
        return new HashMap<>();
    }

    @Override
    public FSFont resolveFont(SharedContext renderingContext, FontSpecification spec) {
        String family = targetFamily(spec.families());
        if (BundledFontSet.FAMILY_CJK.equals(family)) {
            ensureFallback(Face.CJK, BundledFontSet.FAMILY_CJK);
        } else if (BundledFontSet.FAMILY_SYMBOL.equals(family)) {
            ensureFallback(Face.SYMBOL, BundledFontSet.FAMILY_SYMBOL);
        }
        // An unavailable fallback is never requested (the fallback-font step checks first); if one
        // is, the text prints in DejaVu Sans, which draws U+FFFD for what it lacks.
        if (!registeredFamilies().contains(family)) {
            family = BundledFontSet.FAMILY_SANS;
        }
        FontSpecification mapped = new FontSpecification(
                Math.max(spec.size(), MIN_FONT_SIZE_DOTS), spec.fontWeight(), new String[]{family},
                spec.fontStyle(), spec.variant());
        return super.resolveFont(renderingContext, mapped);
    }

    /** The first family of the request, mapped to the registered family it prints in. */
    // FindSecBugs IMPROPER_UNICODE: case-fold of a CSS font family name; selects a font, not a security decision
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "IMPROPER_UNICODE",
            justification = "case-fold of a CSS font family name; selects a font, not a security decision")
    private static String targetFamily(String[] families) {
        if (families == null || families.length == 0 || families[0] == null) {
            return BundledFontSet.FAMILY_SANS;
        }
        String first = families[0].strip();
        while (first.length() >= 2 && (first.startsWith("\"") || first.startsWith("'"))
                && first.charAt(first.length() - 1) == first.charAt(0)) {
            first = first.substring(1, first.length() - 1).strip();
        }
        String lower = first.toLowerCase(Locale.ROOT);
        if (lower.equals("carlos cjk")) {
            return BundledFontSet.FAMILY_CJK;
        }
        if (lower.equals("carlos symbol")) {
            return BundledFontSet.FAMILY_SYMBOL;
        }
        return BundledFontSet.FAMILY_SANS;
    }

    private Set<String> registeredFamilies() {
        return getFonts().keySet();
    }

    private void ensureFallback(Face face, String family) {
        if (!registered.contains(face) && fonts.isAvailable(face)) {
            try {
                register(face, family);
            } catch (IllegalStateException e) {
                // The face stays unregistered; the request falls back to DejaVu Sans.
                registered.add(face);
            }
        }
    }

    private void register(Face face, String family) {
        registered.add(face);
        try {
            addFont(fonts.fontPath(face), family, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, null);
        } catch (IOException | RuntimeException e) {
            // OpenPDF's DocumentException is unchecked, so RuntimeException covers it.
            throw new IllegalStateException("The bundled font " + face + " could not be registered ("
                    + e.getClass().getSimpleName() + ")");
        }
    }
}
