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

import io.github.carlos_emr.carlos.documentManager.htmlpdf.TestFonts;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderDegradation;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.engine.BundledFontResolver;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.engine.BundledFontSet;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.engine.LocalOnlyUserAgent;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Unit tests for {@link RtlTextShaping}. Right-to-left letters are written as numeric code points
 * (a source file mixing scripts reads backwards in an editor); the expected strings are the visual
 * order that results from the Unicode bidirectional algorithm, worked out in the comments.
 */
@Tag("unit")
@Tag("htmlpdf")
@DisplayName("RtlTextShaping Unit Tests")
class RtlTextShapingUnitTest {

    /** SHALOM: shin, lamed, vav, final mem, in logical order. */
    private static final String SHALOM = chars(0x5E9, 0x5DC, 0x5D5, 0x5DD);
    /** The same word in visual order (reversed). */
    private static final String SHALOM_VISUAL = chars(0x5DD, 0x5D5, 0x5DC, 0x5E9);
    /** SALAM: seen, lam, alef, meem, in logical order. */
    private static final String SALAM = chars(0x633, 0x644, 0x627, 0x645);
    private static final String ALEF = chars(0x5D0);
    private static final String BET = chars(0x5D1);

    private static String chars(int... codePoints) {
        return new String(codePoints, 0, codePoints.length);
    }

    private static Set<HtmlPdfRenderDegradation> noDegradations() {
        return EnumSet.noneOf(HtmlPdfRenderDegradation.class);
    }

    private static Document shaped(String bodyHtml, Set<HtmlPdfRenderDegradation> degradations) {
        Document document = Jsoup.parse("<html><head></head><body>" + bodyHtml + "</body></html>");
        RtlTextShaping.shape(document, degradations);
        return document;
    }

    private static Document shaped(String bodyHtml) {
        return shaped(bodyHtml, noDegradations());
    }

    private static String textOf(Document document, String selector) {
        return document.select(selector).first().wholeText();
    }

    // ---- reordering and shaping -------------------------------------------------------------

    @Test
    @DisplayName("should reorder Hebrew into visual order")
    void shouldReorderHebrew_toVisualOrder() {
        Document document = shaped("<p>" + SHALOM + "</p>");

        assertThat(textOf(document, "p")).isEqualTo(SHALOM_VISUAL);
    }

    @Test
    @DisplayName("should leave the text of an SVG or a formula alone, because Batik puts SVG text in order itself")
    void shouldLeaveSvgAndMathText_asItIs() {
        Document document = shaped("<svg><text>" + SHALOM + "</text><g><text dir=\"rtl\">" + SALAM + "</text></g></svg>"
                + "<math><mtext>" + SHALOM + "</mtext></math><p>" + SHALOM + "</p>");

        assertThat(document.select("svg > text").first().wholeText()).isEqualTo(SHALOM);
        assertThat(document.select("svg g text").first().wholeText()).isEqualTo(SALAM);
        assertThat(textOf(document, "mtext")).isEqualTo(SHALOM);
        assertThat(textOf(document, "p")).isEqualTo(SHALOM_VISUAL);
    }

    @Test
    @DisplayName("should reorder the text of an element called svg-like but not inside an SVG")
    void shouldReorderText_ofAnElementThatIsNotSvg() {
        Document document = shaped("<div class=\"svg\"><svgish>" + SHALOM + "</svgish></div>");

        assertThat(textOf(document, "svgish")).isEqualTo(SHALOM_VISUAL);
    }

    @Test
    @DisplayName("should join Arabic letters into presentation forms and reorder them")
    void shouldJoinArabic_toPresentationForms() {
        Document document = shaped("<p>" + SALAM + "</p>");

        // seen initial FEB3, lam + alef final ligature FEFC, meem isolated FEE1; reversed for display.
        assertThat(textOf(document, "p")).isEqualTo(chars(0xFEE1, 0xFEFC, 0xFEB3));
        assertThat(textOf(document, "p").codePoints()).allMatch(c -> c >= 0xFE70 && c <= 0xFEFF);
    }

    @Test
    @DisplayName("should mirror brackets in a right-to-left paragraph")
    void shouldMirrorBrackets_inRtlParagraph() {
        Document document = shaped("<p dir=\"rtl\">(" + ALEF + BET + ")</p>");

        // Reversed, the text would start with ')' and end with '('; mirroring swaps them back.
        assertThat(textOf(document, "p")).isEqualTo("(" + BET + ALEF + ")");
    }

    @Test
    @DisplayName("should keep Latin text and digits readable in mixed text, in the order the paragraph direction gives")
    void shouldKeepLatinAndDigitOrder_inMixedText() {
        String mixed = SHALOM + " 123 FAKE";

        // No dir: the first strong character is Hebrew, so the paragraph is right to left and the
        // Latin text ends up on the left; "123" and "FAKE" are never reversed.
        assertThat(textOf(shaped("<p>" + mixed + "</p>"), "p")).isEqualTo("FAKE 123 " + SHALOM_VISUAL);
        assertThat(textOf(shaped("<p dir=\"rtl\">" + mixed + "</p>"), "p")).isEqualTo("FAKE 123 " + SHALOM_VISUAL);
        // Left to right: the number follows a right-to-left word, so it joins its run and comes first.
        assertThat(textOf(shaped("<p dir=\"ltr\">" + mixed + "</p>"), "p")).isEqualTo("123 " + SHALOM_VISUAL + " FAKE");
    }

    @Test
    @DisplayName("should take the paragraph direction from the first strong character when there is no dir")
    void shouldUseFirstStrongCharacter_whenNoDirAttribute() {
        assertThat(textOf(shaped("<p>FAKE " + SHALOM + "</p>"), "p")).isEqualTo("FAKE " + SHALOM_VISUAL);
        assertThat(textOf(shaped("<p>" + SHALOM + " FAKE</p>"), "p")).isEqualTo("FAKE " + SHALOM_VISUAL);
        assertThat(textOf(shaped("<p dir=\"auto\">FAKE " + SHALOM + "</p>"), "p")).isEqualTo("FAKE " + SHALOM_VISUAL);
    }

    @Test
    @DisplayName("should take the paragraph direction from the nearest dir attribute")
    void shouldUseNearestDirAttribute_forParagraphDirection() {
        String text = "FAKE " + SHALOM;

        assertThat(textOf(shaped("<p dir=\"rtl\">" + text + "</p>"), "p")).isEqualTo(SHALOM_VISUAL + " FAKE");
        assertThat(textOf(shaped("<div dir=\"rtl\"><p>" + text + "</p></div>"), "p")).isEqualTo(SHALOM_VISUAL + " FAKE");
        assertThat(textOf(shaped("<div dir=\"rtl\"><p dir=\"ltr\">" + text + "</p></div>"), "p")).isEqualTo("FAKE " + SHALOM_VISUAL);
        assertThat(textOf(shaped("<div dir=\"rtl\"><span dir=\"auto\">" + text + "</span></div>"), "span")).isEqualTo("FAKE " + SHALOM_VISUAL);
        assertThat(textOf(shaped("<div dir=\"rtl\"><p dir=\"bogus\">" + text + "</p></div>"), "p")).isEqualTo(SHALOM_VISUAL + " FAKE");
    }

    @Test
    @DisplayName("should keep supplementary characters whole when it reorders around them")
    void shouldKeepSupplementaryCharacters_whenReordering() {
        String emoji = chars(0x1F600);

        Document document = shaped("<p>" + emoji + SHALOM + "</p>");

        assertThat(textOf(document, "p")).isEqualTo(SHALOM_VISUAL + emoji);
    }

    // ---- pieces, lines and white space ------------------------------------------------------

    @Test
    @DisplayName("should reorder each text node on its own and keep the gap between nodes")
    void shouldKeepWhitespaceBetweenNodes_whenReorderingPieceByPiece() {
        Document document = shaped("<p dir=\"rtl\">" + SHALOM + " <b>" + SHALOM + "</b> </p>");

        assertThat(document.select("p").first().wholeText()).isEqualTo(SHALOM_VISUAL + " " + SHALOM_VISUAL + " ");
        assertThat(textOf(document, "b")).isEqualTo(SHALOM_VISUAL);
    }

    @Test
    @DisplayName("should keep indentation and line breaks where they are in preformatted text")
    void shouldKeepLineBreaks_inPreformattedText() {
        String text = "  " + SHALOM + "\nabc\r\n" + SALAM + "  ";

        Document document = shaped("<pre>" + text + "</pre>");

        assertThat(textOf(document, "pre")).isEqualTo("  " + SHALOM_VISUAL + "\nabc\r\n" + chars(0xFEE1, 0xFEFC, 0xFEB3) + "  ");
    }

    @Test
    @DisplayName("should not glue the words of two source lines together")
    void shouldNotGlueWords_acrossSourceLines() {
        Document document = shaped("<p>" + SHALOM + "\nworld</p>");

        assertThat(textOf(document, "p")).isEqualTo(SHALOM_VISUAL + "\nworld");
    }

    // ---- block alignment --------------------------------------------------------------------

    @Test
    @DisplayName("should right-align a right-to-left block that has no text-align")
    void shouldAlignRight_forRtlBlockWithoutTextAlign() {
        Document document = shaped("<div id=\"a\" dir=\"rtl\">" + SHALOM + "</div>"
                + "<div id=\"b\" dir=\"rtl\" style=\"color: red\">" + SHALOM + "</div>"
                + "<table><tr><td id=\"c\" dir=\"RTL\">" + SHALOM + "</td></tr></table>"
                + "<div dir=\"rtl\"><p id=\"d\">" + SHALOM + "</p></div>");

        assertThat(document.getElementById("a").attr("style")).isEqualTo("text-align: right");
        assertThat(document.getElementById("b").attr("style")).isEqualTo("color: red; text-align: right");
        assertThat(document.getElementById("c").attr("style")).isEqualTo("text-align: right");
        // The block with dir="rtl" is the div, not the paragraph inside it.
        assertThat(document.getElementById("d").attr("style")).isEmpty();
        assertThat(document.select("div[dir]").last().attr("style")).isEqualTo("text-align: right");
    }

    @Test
    @DisplayName("should leave a block alone that the author aligned")
    void shouldLeaveAlignment_whenAuthorSetOne() {
        Document document = shaped("<div id=\"a\" dir=\"rtl\" style=\"text-align:center\">" + SHALOM + "</div>"
                + "<div id=\"b\" dir=\"rtl\" align=\"left\">" + SHALOM + "</div>"
                + "<div id=\"c\" dir=\"rtl\" style=\"TEXT-ALIGN : left\">" + SHALOM + "</div>");

        assertThat(document.getElementById("a").attr("style")).isEqualTo("text-align:center");
        assertThat(document.getElementById("b").attr("style")).isEmpty();
        assertThat(document.getElementById("c").attr("style")).isEqualTo("TEXT-ALIGN : left");
    }

    @Test
    @DisplayName("should not align a paragraph whose only right-to-left ancestor is inline")
    void shouldNotAlign_whenOnlyInlineAncestorIsRtl() {
        Document document = shaped("<p id=\"a\"><span id=\"b\" dir=\"rtl\">" + SHALOM + "</span></p>");

        assertThat(document.getElementById("a").attr("style")).isEmpty();
        assertThat(document.getElementById("b").attr("style")).isEmpty();
    }

    @Test
    @DisplayName("should not align a right-to-left block that has no right-to-left text")
    void shouldNotAlign_whenBlockHasNoRightToLeftText() {
        Document document = shaped("<div id=\"a\" dir=\"rtl\">FAKE 123</div>");

        assertThat(document.getElementById("a").attr("style")).isEmpty();
    }

    // ---- the best-effort note ---------------------------------------------------------------

    @Test
    @DisplayName("should ask for the best-effort note when a right-to-left run is over forty characters")
    void shouldAddBestEffortNote_forRunOverFortyChars() {
        Set<HtmlPdfRenderDegradation> forty = noDegradations();
        Set<HtmlPdfRenderDegradation> fortyOne = noDegradations();

        shaped("<p>" + ALEF.repeat(40) + "</p>", forty);
        shaped("<p>" + ALEF.repeat(41) + "</p>", fortyOne);

        assertThat(forty).isEmpty();
        assertThat(fortyOne).containsExactly(HtmlPdfRenderDegradation.RTL_BEST_EFFORT);
    }

    @Test
    @DisplayName("should count the spaces between right-to-left words in a run")
    void shouldCountSpaces_inRightToLeftRun() {
        String twentyWords = (ALEF + BET + " ").repeat(14).strip();
        Set<HtmlPdfRenderDegradation> shortRun = noDegradations();
        Set<HtmlPdfRenderDegradation> longRun = noDegradations();

        shaped("<p>" + twentyWords.substring(0, 40) + "</p>", shortRun);
        shaped("<p>" + twentyWords.substring(0, 41) + "</p>", longRun);

        assertThat(shortRun).isEmpty();
        assertThat(longRun).containsExactly(HtmlPdfRenderDegradation.RTL_BEST_EFFORT);
    }

    @Test
    @DisplayName("should measure a long run on its own line, not across line breaks")
    void shouldMeasureRunPerLine_notAcrossLines() {
        Set<HtmlPdfRenderDegradation> shortLines = noDegradations();
        Set<HtmlPdfRenderDegradation> oneLongLine = noDegradations();

        shaped("<pre>" + ALEF.repeat(30) + "\n" + ALEF.repeat(30) + "</pre>", shortLines);
        shaped("<pre>" + ALEF.repeat(10) + "\n" + ALEF.repeat(41) + "</pre>", oneLongLine);

        assertThat(shortLines).isEmpty();
        assertThat(oneLongLine).containsExactly(HtmlPdfRenderDegradation.RTL_BEST_EFFORT);
    }

    // ---- what is left alone -----------------------------------------------------------------

    @Test
    @DisplayName("should leave the text of style and title elements alone")
    void shouldLeaveStyleAndTitle_untouched() {
        String title = SHALOM + ALEF.repeat(50);
        Set<HtmlPdfRenderDegradation> degradations = noDegradations();
        Document document = Jsoup.parse("<html><head><title>" + title + "</title><style>p::after { content: \"" + SHALOM
                + "\" }</style></head><body><p>x</p></body></html>");

        RtlTextShaping.shape(document, degradations);

        assertThat(document.title()).isEqualTo(title);
        assertThat(document.select("style").first().data()).contains(SHALOM);
        assertThat(degradations).isEmpty();
    }

    @Test
    @DisplayName("should change nothing in a document without right-to-left text")
    void shouldChangeNothing_whenNoRightToLeftText() {
        Document document = Jsoup.parse("<html><body><div dir=\"rtl\"><p>Plain FAKE text 123 中文 (x)</p></div>"
                + "<pre>a\nb</pre></body></html>");
        String before = document.html();
        Set<HtmlPdfRenderDegradation> degradations = noDegradations();

        RtlTextShaping.shape(document, degradations);

        assertThat(document.html()).isEqualTo(before);
        assertThat(degradations).isEmpty();
    }

    @Test
    @DisplayName("should handle a very long right-to-left text node in reasonable time")
    void shouldHandleVeryLongText_inReasonableTime() {
        String longText = (SHALOM + " " + SALAM + " ").repeat(8_000);
        Document document = Jsoup.parse("<html><body><p>" + longText + "</p></body></html>");

        assertTimeoutPreemptively(Duration.ofSeconds(20), () -> RtlTextShaping.shape(document, noDegradations()));

        // One right-to-left paragraph: the whole line is reversed, so its last word comes first. Each SALAM
        // shrinks from four letters to three (lam + alef become one ligature); assertions on the length and
        // the ends keep a failure message short.
        String visual = document.select("p").first().wholeText();
        assertThat(visual.length()).isEqualTo(longText.length() - 8_000);
        assertThat(visual.startsWith(chars(0xFEE1, 0xFEFC, 0xFEB3) + " " + SHALOM_VISUAL)).isTrue();
        assertThat(visual.endsWith(SHALOM_VISUAL + " ")).isTrue();
    }

    @Test
    @DisplayName("should reject null arguments")
    void shouldRejectNull_whenArgumentMissing() {
        Document document = Jsoup.parse("<p>x</p>");

        assertThatNullPointerException().isThrownBy(() -> RtlTextShaping.shape(null, noDegradations()));
        assertThatNullPointerException().isThrownBy(() -> RtlTextShaping.shape(document, null));
    }

    // ---- in a real render -------------------------------------------------------------------

    @Test
    @DisplayName("should print right-to-left text in the right visual order, read back from the PDF")
    void shouldPrintInVisualOrder_whenRenderedByFlyingSaucer() throws Exception {
        Path fontDirectory = TestFonts.sharedDirectory();
        BundledFontSet fonts = TestFonts.sharedFonts();
        Document document = shaped("<p dir=\"rtl\">" + SHALOM + " 123</p><p dir=\"rtl\">" + SALAM + "</p>");
        String xhtml = XhtmlCleanup.serialize(document);

        ITextRenderer renderer = LocalOnlyUserAgent.createRestrictedRenderer(List.of(fontDirectory), new BundledFontResolver(fonts));
        renderer.setDocumentFromString(xhtml);
        renderer.layout();
        ByteArrayOutputStream pdf = new ByteArrayOutputStream();
        renderer.createPDF(pdf);

        // PDFBox puts right-to-left glyphs back into logical order when it extracts text, so a
        // correct visual layout reads as the original, and an unreordered one would not.
        try (PDDocument loaded = Loader.loadPDF(pdf.toByteArray())) {
            String extracted = new PDFTextStripper().getText(loaded);
            assertThat(extracted).contains(SHALOM).contains(SALAM);
            assertThat(extracted).doesNotContain(SHALOM_VISUAL);
        }
    }
}
