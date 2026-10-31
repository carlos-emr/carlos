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
package io.github.carlos_emr.carlos.documentManager.htmlpdf.support;

import io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderDegradation;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.engine.BundledFontSet;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.engine.BundledFontSet.Face;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.apache.logging.log4j.Logger;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Wraps the characters DejaVu Sans cannot draw in spans that name a fallback font (step l of the
 * render pipeline), so a Chinese, Japanese or Korean name or a symbol in a clinical record prints
 * instead of vanishing. It mirrors the rules {@code PdfFonts} applies to text drawn with OpenPDF.
 *
 * <p>For each text node outside {@code <style>}, {@code <title>}, {@code <script>}, {@code <svg>},
 * {@code <math>} and the text of form controls, each code point is handled in this order:</p>
 *
 * <ol>
 *   <li>drawable by <em>both</em> bundled DejaVu Sans faces (regular and bold): left as it is;</li>
 *   <li>a format or variation-selector character (zero-width joiner, variation selector, tag
 *       character): left as it is. Nothing draws it, and OpenPDF leaves out a character with no
 *       glyph, so it prints as nothing, as it should;</li>
 *   <li>white space: left as it is, unless DejaVu Sans lacks it and a fallback face draws it (the
 *       ideographic space U+3000 is the one in practice), when it takes that face;</li>
 *   <li>otherwise the first face that can draw it, WenQuanYi Micro Hei and then Symbola;</li>
 *   <li>otherwise U+FFFD, which DejaVu Sans draws, so the gap is visible rather than silently
 *       closed. Only the count is logged, at DEBUG.</li>
 * </ol>
 *
 * <p>Maximal runs of characters that take the same fallback face are wrapped in
 * {@code <span style="font-family:'CARLOS CJK'; font-style:normal">} (or {@code 'CARLOS Symbol'}); the
 * fallback faces have no italic, so the span forces upright text. {@code BundledFontResolver} maps
 * those two family names to the fallback faces. The same input always gives the same spans.</p>
 *
 * <p>If a fallback face could not be loaded, the characters only it could have drawn print as
 * U+FFFD and {@link HtmlPdfRenderDegradation#FALLBACK_FONT_UNAVAILABLE} is recorded. The face's
 * coverage is unknown then, so the degradation is recorded whenever a character is replaced while
 * a fallback face is missing.</p>
 *
 * <p>This step runs last among the document steps, after the note and the placeholder boxes have
 * been added, so their text is covered too, and after right-to-left shaping, because fallback spans
 * would split the runs that shaping reorders.</p>
 *
 * @since 2026-10-29
 */
public final class FallbackFontRuns {

    private static final Logger logger = MiscUtils.getLogger();

    /** U+FFFD REPLACEMENT CHARACTER, drawn by DejaVu Sans regular and bold. */
    static final int REPLACEMENT_CHARACTER = 0xFFFD;

    /** The faces tried, in order, for a character DejaVu Sans lacks. */
    private static final List<Face> FALLBACKS = List.of(Face.CJK, Face.SYMBOL);

    /** Elements whose text is not drawn as HTML text, or cannot hold a span. */
    private static final Set<String> SKIPPED_ANCESTORS =
            Set.of("style", "title", "script", "svg", "math", "textarea", "select", "option", "noscript");

    private FallbackFontRuns() {
    }

    /**
     * Applies the fallback-font rules to the document.
     *
     * @param document the document to change
     * @param fonts the bundled fonts, for what each face can draw
     * @param degradations the set to add {@link HtmlPdfRenderDegradation#FALLBACK_FONT_UNAVAILABLE} to
     */
    public static void apply(Document document, BundledFontSet fonts, Set<HtmlPdfRenderDegradation> degradations) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(fonts, "fonts");
        Objects.requireNonNull(degradations, "degradations");
        List<TextNode> candidates = new ArrayList<>();
        document.traverse((node, depth) -> {
            if (node instanceof TextNode text && !isSkipped(text) && needsLookup(text.getWholeText())) {
                candidates.add(text);
            }
        });
        int replaced = 0;
        boolean fallbackMissing = !fonts.isAvailable(Face.CJK) || !fonts.isAvailable(Face.SYMBOL);
        boolean degraded = false;
        for (TextNode node : candidates) {
            Runs runs = new Runs(fonts);
            node.getWholeText().codePoints().forEach(runs::add);
            replaced += runs.replaced;
            degraded |= fallbackMissing && runs.replaced > 0;
            runs.applyTo(node);
        }
        if (degraded) {
            degradations.add(HtmlPdfRenderDegradation.FALLBACK_FONT_UNAVAILABLE);
        }
        if (replaced > 0) {
            logger.debug("{} character(s) that no bundled font can draw were replaced with U+FFFD", replaced);
        }
    }

    private static boolean isSkipped(TextNode text) {
        for (Element ancestor = text.parent() instanceof Element parent ? parent : null; ancestor != null;
                ancestor = ancestor.parent()) {
            if (SKIPPED_ANCESTORS.contains(ancestor.normalName())) {
                return true;
            }
        }
        return false;
    }

    /** Printable ASCII, tab and the line breaks are all DejaVu Sans can need no help with: most text stops here. */
    private static boolean needsLookup(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!((c >= 0x20 && c <= 0x7E) || c == '\t' || c == '\n' || c == '\r')) {
                return true;
            }
        }
        return false;
    }

    /** The characters of one text node, grouped into runs by face. */
    private static final class Runs {
        private final BundledFontSet fonts;
        private final List<Node> nodes = new ArrayList<>();
        private final StringBuilder run = new StringBuilder();
        private Face runFace = Face.SANS_REGULAR;
        private boolean changed;
        int replaced;

        Runs(BundledFontSet fonts) {
            this.fonts = fonts;
        }

        void add(int codePoint) {
            Face face = Face.SANS_REGULAR;
            int drawn = codePoint;
            if (!isDrawnByDejaVu(codePoint) && !isInvisible(codePoint)) {
                Face fallback = fallbackFor(codePoint);
                if (fallback != null) {
                    face = fallback;
                } else if (!isWhitespace(codePoint)) {
                    drawn = REPLACEMENT_CHARACTER;
                    replaced++;
                    changed = true;
                }
            }
            if (face != runFace) {
                flush();
                runFace = face;
            }
            run.appendCodePoint(drawn);
        }

        private boolean isDrawnByDejaVu(int codePoint) {
            return (codePoint >= 0x20 && codePoint <= 0x7E) || codePoint == '\t' || codePoint == '\n' || codePoint == '\r'
                    || (fonts.canDraw(Face.SANS_REGULAR, codePoint) && fonts.canDraw(Face.SANS_BOLD, codePoint));
        }

        private Face fallbackFor(int codePoint) {
            for (Face face : FALLBACKS) {
                if (fonts.canDraw(face, codePoint)) {
                    return face;
                }
            }
            return null;
        }

        private void flush() {
            if (run.isEmpty()) {
                return;
            }
            if (runFace == Face.SANS_REGULAR) {
                nodes.add(new TextNode(run.toString()));
            } else {
                Element span = new Element("span");
                span.attr("style", styleOf(runFace));
                span.appendText(run.toString());
                nodes.add(span);
                changed = true;
            }
            run.setLength(0);
        }

        /** Puts the runs where the text node was; a node that needs no help is left untouched. */
        void applyTo(TextNode node) {
            flush();
            if (!changed) {
                return;
            }
            if (nodes.size() == 1 && nodes.get(0) instanceof TextNode only) {
                node.text(only.getWholeText());
                return;
            }
            Element parent = node.parent() instanceof Element element ? element : null;
            if (parent == null) {
                return;
            }
            parent.insertChildren(node.siblingIndex(), nodes);
            node.remove();
        }
    }

    private static String styleOf(Face face) {
        String family = face == Face.CJK ? BundledFontSet.FAMILY_CJK : BundledFontSet.FAMILY_SYMBOL;
        return "font-family:'" + family + "'; font-style:normal";
    }

    private static boolean isWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }

    /** A format character or variation selector: it has no glyph of its own, so nothing is drawn for it. */
    private static boolean isInvisible(int codePoint) {
        return Character.getType(codePoint) == Character.FORMAT
                || (codePoint >= 0xFE00 && codePoint <= 0xFE0F)
                || (codePoint >= 0xE0100 && codePoint <= 0xE01EF);
    }
}
