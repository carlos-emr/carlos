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

import com.ibm.icu.text.ArabicShaping;
import com.ibm.icu.text.ArabicShapingException;
import com.ibm.icu.text.Bidi;
import com.ibm.icu.text.BidiRun;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderDegradation;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Best-effort support for right-to-left text, never failing (step h of the render pipeline).
 *
 * <p>Flying Saucer lays text out from the left and does no Arabic joining, so Hebrew, Arabic, Urdu
 * and other right-to-left text would print as isolated letters in logical order, back to front. For
 * each text node that contains a right-to-left character (bidirectional class R or AL), this step:</p>
 *
 * <ol>
 *   <li>shapes Arabic-script letters to their joined presentation forms (ICU4J
 *       {@link ArabicShaping}, logical order); DejaVu Sans, regular and bold, has those forms and
 *       Hebrew;</li>
 *   <li>reorders the text into visual order (ICU4J {@link Bidi}, mirroring brackets), with the
 *       paragraph direction taken from the nearest {@code dir} attribute ({@code rtl}, {@code ltr},
 *       or {@code auto}), or from the first strong character when there is none;</li>
 *   <li>sets {@code text-align: right} on the nearest block ancestor that has {@code dir="rtl"} and no
 *       explicit {@code text-align} (or {@code align} attribute).</li>
 * </ol>
 *
 * <p>Each line of a text node is reordered on its own, and its leading and trailing white space and its
 * line breaks stay where they are, so the gap between a node and its neighbours survives the
 * reordering. The text of {@code <style>} and {@code <title>} is left alone.</p>
 *
 * <p>Limits, because the layout engine knows nothing of bidirectional text: reordering is done within
 * one text node, so a passage split across inline elements is reordered piece by piece, and tables
 * are not mirrored. A right-to-left run longer than {@value #LONG_RUN_CHARS} characters can wrap, and
 * wrapped lines print in reverse order; such a document gets
 * {@link HtmlPdfRenderDegradation#RTL_BEST_EFFORT}, which adds a note asking the reader to check
 * the line order against the original. Short right-to-left text (names, field values, short notes)
 * prints correctly.</p>
 *
 * <p>This step runs before the fallback-font step, because fallback spans would split runs.</p>
 *
 * @since 2026-10-29
 */
public final class RtlTextShaping {

    /**
     * A right-to-left run longer than this many characters may wrap (a line of the printed page
     * holds about 40 characters at the smallest usual size), where Flying Saucer prints the lines
     * in reverse order; spec §Right-to-left text.
     */
    static final int LONG_RUN_CHARS = 40;

    private static final int SHAPING_OPTIONS = ArabicShaping.LETTERS_SHAPE | ArabicShaping.TEXT_DIRECTION_LOGICAL;

    /** Any line terminator: CR LF, LF, VT, FF, CR, NEL, LS or PS. */
    private static final Pattern LINE_BREAK = Pattern.compile("\\R");

    private RtlTextShaping() {
    }

    /**
     * Shapes and reorders the right-to-left text of the document.
     *
     * @param document the document to change
     * @param degradations the set to add {@link HtmlPdfRenderDegradation#RTL_BEST_EFFORT} to when a
     *        right-to-left run is longer than {@value #LONG_RUN_CHARS} characters
     */
    public static void shape(Document document, Set<HtmlPdfRenderDegradation> degradations) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(degradations, "degradations");
        List<TextNode> candidates = new ArrayList<>();
        document.traverse((node, depth) -> {
            if (node instanceof TextNode text && containsRightToLeft(text.getWholeText()) && !isSkipped(text)) {
                candidates.add(text);
            }
        });
        if (candidates.isEmpty()) {
            return;
        }
        ArabicShaping shaping = new ArabicShaping(SHAPING_OPTIONS);
        Bidi bidi = new Bidi();
        for (TextNode node : candidates) {
            try {
                byte paragraphLevel = paragraphLevelOf(node);
                node.text(reorderLines(node.getWholeText(), shaping, bidi, paragraphLevel, degradations));
                alignRight(node);
            } catch (ArabicShapingException | RuntimeException e) {
                // Best effort: the node keeps its text as written, and the rest of the document is shaped.
            }
        }
    }

    /**
     * Reorders each line of the text on its own and keeps the line breaks where they are. A line
     * break is a paragraph separator to the bidirectional algorithm, which would move it to the
     * other end of its line; in flowing text that glues two words together, and in a {@code <pre>}
     * it moves the break itself.
     */
    private static String reorderLines(String text, ArabicShaping shaping, Bidi bidi, byte paragraphLevel,
            Set<HtmlPdfRenderDegradation> degradations) throws ArabicShapingException {
        StringBuilder visual = new StringBuilder(text.length());
        Matcher breaks = LINE_BREAK.matcher(text);
        int from = 0;
        while (breaks.find()) {
            visual.append(reorderLine(text.substring(from, breaks.start()), shaping, bidi, paragraphLevel, degradations));
            visual.append(breaks.group());
            from = breaks.end();
        }
        visual.append(reorderLine(text.substring(from), shaping, bidi, paragraphLevel, degradations));
        return visual.toString();
    }

    /** One line; its leading and trailing white space stays at the same end, so gaps next to other nodes survive. */
    private static String reorderLine(String line, ArabicShaping shaping, Bidi bidi, byte paragraphLevel,
            Set<HtmlPdfRenderDegradation> degradations) throws ArabicShapingException {
        if (!containsRightToLeft(line)) {
            return line;
        }
        int start = firstNonWhitespace(line);
        int end = lastNonWhitespaceEnd(line);
        String shaped = shaping.shape(line.substring(start, end));
        bidi.setPara(shaped, paragraphLevel, null);
        for (int index = 0, runs = bidi.countRuns(); index < runs; index++) {
            BidiRun run = bidi.getVisualRun(index);
            if (run.isOddRun() && run.getLength() > LONG_RUN_CHARS) {
                degradations.add(HtmlPdfRenderDegradation.RTL_BEST_EFFORT);
                break;
            }
        }
        return line.substring(0, start) + bidi.writeReordered(Bidi.DO_MIRRORING) + line.substring(end);
    }

    // ---- which text -------------------------------------------------------------------------

    private static boolean containsRightToLeft(String text) {
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            byte directionality = Character.getDirectionality(codePoint);
            if (directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT
                    || directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC) {
                return true;
            }
            offset += Character.charCount(codePoint);
        }
        return false;
    }

    /** Elements whose text is not drawn as HTML text. */
    private static final Set<String> NOT_HTML_TEXT = Set.of("style", "title", "script", "svg", "math");

    /**
     * Text that is not drawn as HTML text: the content of a style, title or script, and anything inside an
     * {@code <svg>} or {@code <math>}. Batik lays an SVG's {@code <text>} out with a bidirectional algorithm and
     * Arabic shaping of its own, so text already put in visual order would be put in order a second time and
     * print reversed (measured: the Hebrew of an inline SVG printed back to front, the same line in a paragraph
     * did not).
     */
    private static boolean isSkipped(TextNode text) {
        for (Element ancestor = text.parent() instanceof Element parent ? parent : null; ancestor != null;
                ancestor = ancestor.parent()) {
            if (NOT_HTML_TEXT.contains(ancestor.normalName())) {
                return true;
            }
        }
        return false;
    }

    private static int firstNonWhitespace(String text) {
        int i = 0;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return i;
    }

    private static int lastNonWhitespaceEnd(String text) {
        int end = text.length();
        while (end > 0 && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        return end;
    }

    // ---- direction --------------------------------------------------------------------------

    /** The paragraph level from the nearest {@code dir} attribute, else the first strong character decides. */
    private static byte paragraphLevelOf(TextNode node) {
        for (Element element = node.parent() instanceof Element parent ? parent : null; element != null;
                element = element.parent()) {
            switch (direction(element)) {
                case "rtl":
                    return Bidi.RTL;
                case "ltr":
                    return Bidi.LTR;
                case "auto":
                    return Bidi.LEVEL_DEFAULT_LTR;
                default:
                    break;
            }
        }
        return Bidi.LEVEL_DEFAULT_LTR;
    }

    private static String direction(Element element) {
        return element.attr("dir").strip().toLowerCase(Locale.ROOT);
    }

    /** Right-aligns the nearest block ancestor with {@code dir="rtl"} unless the author aligned it. */
    private static void alignRight(TextNode node) {
        for (Element element = node.parent() instanceof Element parent ? parent : null; element != null;
                element = element.parent()) {
            if (element.isBlock() && direction(element).equals("rtl")) {
                if (!InlineStyle.setsTextAlign(element.attr("style")) && !element.hasAttr("align")) {
                    element.attr("style", InlineStyle.append(element.attr("style"), "text-align: right"));
                }
                return;
            }
        }
    }
}
