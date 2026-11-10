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
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
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
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Unit tests for {@link FallbackFontRuns}. Characters that are hard to read or to type in a source
 * file are written as numeric code points.
 */
@Tag("unit")
@Tag("htmlpdf")
@DisplayName("FallbackFontRuns Unit Tests")
class FallbackFontRunsUnitTest {

    private static final String CJK_STYLE = "font-family:'CARLOS CJK'; font-style:normal";
    private static final String SYMBOL_STYLE = "font-family:'CARLOS Symbol'; font-style:normal";

    /**
     * Two Han characters (wen, zi), which WenQuanYi draws and neither Symbola nor DejaVu Sans does.
     * (Symbola has a few Han characters, such as U+4E2D, so a test of a missing CJK face must not use those.)
     */
    private static final String HAN = chars(0x6587, 0x5B57);
    /** U+1D11E MUSICAL SYMBOL G CLEF: Symbola has it, WenQuanYi and DejaVu Sans do not. */
    private static final String G_CLEF = chars(0x1D11E);
    /** U+1F000 MAHJONG TILE EAST WIND: Symbola only; unlike the G clef it has a width, so a line of it is drawn. */
    private static final String MAHJONG = chars(0x1F000);
    /** U+0A15 GURMUKHI LETTER KA: no bundled font has it. */
    private static final String GURMUKHI = chars(0xA15);
    private static final String REPLACEMENT = chars(0xFFFD);

    private final Path directory = TestFonts.sharedDirectory();
    private final BundledFontSet fonts = TestFonts.sharedFonts();

    private static String chars(int... codePoints) {
        return new String(codePoints, 0, codePoints.length);
    }

    private static Set<HtmlPdfRenderDegradation> noDegradations() {
        return EnumSet.noneOf(HtmlPdfRenderDegradation.class);
    }

    private Document applied(String bodyHtml, BundledFontSet set, Set<HtmlPdfRenderDegradation> degradations) {
        Document document = Jsoup.parse("<html><head></head><body>" + bodyHtml + "</body></html>");
        FallbackFontRuns.apply(document, set, degradations);
        return document;
    }

    private Document applied(String bodyHtml) {
        return applied(bodyHtml, fonts, noDegradations());
    }

    private static BundledFontSet withoutFace(Path directory, boolean cjk, boolean symbol) throws Exception {
        return BundledFontSet.load(directory.resolve("DejaVuSans.ttf"), directory.resolve("DejaVuSans-Bold.ttf"),
                cjk ? directory.resolve("wqy-microhei.ttc") : null, symbol ? directory.resolve("Symbola_hint.ttf") : null);
    }

    // ---- wrapping ---------------------------------------------------------------------------

    @Test
    @DisplayName("should wrap a Chinese run in a span that names the CJK family")
    void shouldWrapCjkRun_inCjkSpan() {
        Document document = applied("<p>FAKE " + HAN + " x</p>");

        assertThat(document.select("p span")).hasSize(1);
        Element span = document.select("p span").first();
        assertThat(span.attr("style")).isEqualTo(CJK_STYLE);
        assertThat(span.text()).isEqualTo(HAN);
        assertThat(document.select("p").first().wholeText()).isEqualTo("FAKE " + HAN + " x");
    }

    @Test
    @DisplayName("should use Symbola for a musical symbol that only Symbola can draw")
    void shouldUseSymbola_forMusicalSymbol() {
        Document document = applied("<p>a" + G_CLEF + "b</p>");

        Element span = document.select("p span").first();
        assertThat(span.attr("style")).isEqualTo(SYMBOL_STYLE);
        assertThat(span.text()).isEqualTo(G_CLEF);
    }

    @Test
    @DisplayName("should keep adjacent characters of one face in a single span, and split where the face changes")
    void shouldMakeMaximalRuns_perFace() {
        Document document = applied("<p>" + HAN + HAN + "x" + HAN + G_CLEF + MAHJONG + HAN + "</p>");

        List<Element> spans = document.select("p span");
        assertThat(spans.stream().map(Element::text).toList()).containsExactly(HAN + HAN, HAN, G_CLEF + MAHJONG, HAN);
        assertThat(spans.stream().map(span -> span.attr("style")).toList())
                .containsExactly(CJK_STYLE, CJK_STYLE, SYMBOL_STYLE, CJK_STYLE);
        assertThat(document.select("p").first().wholeText()).isEqualTo(HAN + HAN + "x" + HAN + G_CLEF + MAHJONG + HAN);
    }

    @Test
    @DisplayName("should force upright text in the span, because the fallback faces have no italic")
    void shouldForceUpright_inFallbackSpans() {
        Document document = applied("<p><i>" + HAN + "</i> <b>" + G_CLEF + "</b></p>");

        assertThat(document.select("i span").attr("style")).contains("font-style:normal");
        assertThat(document.select("b span").attr("style")).contains("font-style:normal");
    }

    @Test
    @DisplayName("should let the CJK face draw the ideographic space between Chinese characters")
    void shouldTreatIdeographicSpace_asPartOfCjkRun() {
        Document document = applied("<p>" + chars(0x6587, 0x3000, 0x5B57) + "</p>");

        assertThat(document.select("p span")).hasSize(1);
        assertThat(document.select("p span").first().wholeText()).isEqualTo(chars(0x6587, 0x3000, 0x5B57));
    }

    // ---- replacement ------------------------------------------------------------------------

    @Test
    @DisplayName("should replace a character no bundled font can draw with U+FFFD and log only the count")
    void shouldReplaceUndrawable_withReplacementCharacter() {
        Set<HtmlPdfRenderDegradation> degradations = noDegradations();
        try (LogCapture capture = LogCapture.forLogger(FallbackFontRuns.class)) {
            Document document = applied("<p>a" + GURMUKHI + GURMUKHI + "b</p>", fonts, degradations);

            assertThat(document.select("p").first().wholeText()).isEqualTo("a" + REPLACEMENT + REPLACEMENT + "b");
            assertThat(document.select("span")).isEmpty();
            assertThat(degradations).isEmpty();
            assertThat(capture.messages()).hasSize(1);
            assertThat(capture.messages().get(0)).contains("2 character(s)").doesNotContain(GURMUKHI);
        }
    }

    @Test
    @DisplayName("should try WenQuanYi before Symbola for a character both can draw")
    void shouldPreferCjkFace_whenBothFallbacksCanDraw() {
        // U+2312 ARC is in both fallback faces and in neither DejaVu Sans face.
        Document document = applied("<p>" + chars(0x2312) + "</p>");

        assertThat(document.select("p span").attr("style")).isEqualTo(CJK_STYLE);
    }

    @Test
    @DisplayName("should treat a character that only the regular DejaVu Sans face has as needing a fallback")
    void shouldRequireBothDejaVuFaces_beforeLeavingCharacterAlone() {
        // U+F000 is in DejaVu Sans regular but not in DejaVu Sans Bold: bold text would lose it.
        Document document = applied("<p>" + chars(0xF000) + "</p>");

        assertThat(document.select("p").first().wholeText()).isNotEqualTo(chars(0xF000));
    }

    @Test
    @DisplayName("should replace controls and unpaired surrogates, which no font draws")
    void shouldReplaceControlsAndSurrogates_withReplacementCharacter() {
        Document document = applied("<p>" + chars(0x7F, 0x85, 0x9F) + "x" + chars(0xD800) + "</p>");

        assertThat(document.select("p").first().wholeText()).isEqualTo(REPLACEMENT.repeat(3) + "x" + REPLACEMENT);
    }

    @Test
    @DisplayName("should say that a fallback face is unavailable when the CJK font did not load")
    void shouldReportMissingFallback_whenCjkUnavailable() throws Exception {
        Set<HtmlPdfRenderDegradation> degradations = noDegradations();
        BundledFontSet noCjk = withoutFace(directory, false, true);

        Document document = applied("<p>" + HAN + "</p>", noCjk, degradations);

        assertThat(document.select("p").first().wholeText()).isEqualTo(REPLACEMENT + REPLACEMENT);
        assertThat(document.select("span")).isEmpty();
        assertThat(degradations).containsExactly(HtmlPdfRenderDegradation.FALLBACK_FONT_UNAVAILABLE);
    }

    @Test
    @DisplayName("should say that a fallback face is unavailable when Symbola did not load")
    void shouldReportMissingFallback_whenSymbolUnavailable() throws Exception {
        Set<HtmlPdfRenderDegradation> degradations = noDegradations();
        BundledFontSet noSymbol = withoutFace(directory, true, false);

        Document document = applied("<p>" + G_CLEF + HAN + "</p>", noSymbol, degradations);

        assertThat(document.select("p").first().wholeText()).isEqualTo(REPLACEMENT + HAN);
        assertThat(document.select("span").first().text()).isEqualTo(HAN);
        assertThat(degradations).containsExactly(HtmlPdfRenderDegradation.FALLBACK_FONT_UNAVAILABLE);
    }

    @Test
    @DisplayName("should not report a missing face when nothing needed it")
    void shouldNotReportMissingFallback_whenNothingNeedsIt() throws Exception {
        Set<HtmlPdfRenderDegradation> degradations = noDegradations();
        BundledFontSet noSymbol = withoutFace(directory, true, false);

        applied("<p>FAKE " + HAN + " caf" + chars(0xE9) + "</p>", noSymbol, degradations);

        assertThat(degradations).isEmpty();
    }

    // ---- what is left alone -----------------------------------------------------------------

    @Test
    @DisplayName("should leave alone every character DejaVu Sans draws, including the marks form controls use")
    void shouldLeaveText_whenDejaVuDrawsIt() {
        String drawable = "Caf" + chars(0xE9) + " " + chars(0x3A9, 0x44F, 0x5D0, 0xFE8E) + " " + chars(0x2265, 0xB5, 0x2192)
                + " " + chars(0x2713, 0x2612, 0x2610, 0x25C9, 0x25CB, 0x2022, 0xFFFD) + " " + chars(0x1EA1, 0x301);
        Set<HtmlPdfRenderDegradation> degradations = noDegradations();
        Document document = Jsoup.parse("<html><body><p>" + drawable + "</p></body></html>");
        String before = document.html();

        FallbackFontRuns.apply(document, fonts, degradations);

        assertThat(document.html()).isEqualTo(before);
        assertThat(degradations).isEmpty();
    }

    @Test
    @DisplayName("should leave white space, format characters and variation selectors in place")
    void shouldLeaveWhitespaceFormatAndVariationSelectors_inPlace() {
        String text = "a b\tc\nd" + chars(0x200D, 0xFE0F, 0xE0067, 0xE0100) + "e" + chars(0xA0, 0x2003, 0xB, 0xC, 0x1C);
        Document document = applied("<pre>" + text + "</pre>");

        assertThat(document.select("span")).isEmpty();
        assertThat(document.select("pre").first().wholeText()).isEqualTo(text);
    }

    @Test
    @DisplayName("should leave the text of style, title, script, SVG and form controls alone")
    void shouldNotTouch_nonHtmlText() {
        Document document = Jsoup.parse("<html><head><title>" + HAN + "</title><style>p::after { content: \"" + HAN
                + "\" }</style></head><body><svg><text>" + HAN + "</text></svg><select><option>" + HAN
                + "</option></select><textarea>" + HAN + "</textarea><p>x</p></body></html>");
        String before = document.html();

        FallbackFontRuns.apply(document, fonts, noDegradations());

        assertThat(document.html()).isEqualTo(before);
    }

    @Test
    @DisplayName("should cover text in nested elements, tables and replaced controls")
    void shouldCoverNestedText_everywhere() {
        Document document = Jsoup.parse("<html><body><table><tr><td><b>" + HAN + "</b></td></tr></table>"
                + "<input type=\"text\" value=\"" + HAN + "\"></body></html>");
        PrintFormControls.replaceControls(document);

        FallbackFontRuns.apply(document, fonts, noDegradations());

        assertThat(document.select("td b span")).hasSize(1);
        assertThat(document.select("span.carlos-pdf-text span").first().attr("style")).isEqualTo(CJK_STYLE);
    }

    @Test
    @DisplayName("should give the same spans every time for the same input")
    void shouldProduceIdenticalSpans_acrossRuns() {
        String body = "<p>FAKE " + HAN + " " + G_CLEF + " " + GURMUKHI + " <i>" + MAHJONG + "</i></p>";

        String first = applied(body).html();
        String second = applied(body).html();

        assertThat(second).isEqualTo(first);
    }

    @Test
    @DisplayName("should handle a very large text node in reasonable time")
    void shouldHandleLargeText_inReasonableTime() {
        String unit = "FAKE patient " + HAN + " " + chars(0xE9) + " " + G_CLEF + " ";
        Document document = Jsoup.parse("<html><body><p>" + unit.repeat(50_000) + "</p></body></html>");

        assertTimeoutPreemptively(Duration.ofSeconds(30), () -> FallbackFontRuns.apply(document, fonts, noDegradations()));

        assertThat(document.select("p > span")).hasSize(100_000);
    }

    @Test
    @DisplayName("should reject null arguments")
    void shouldRejectNull_whenArgumentMissing() {
        Document document = Jsoup.parse("<p>x</p>");

        assertThatNullPointerException().isThrownBy(() -> FallbackFontRuns.apply(null, fonts, noDegradations()));
        assertThatNullPointerException().isThrownBy(() -> FallbackFontRuns.apply(document, null, noDegradations()));
        assertThatNullPointerException().isThrownBy(() -> FallbackFontRuns.apply(document, fonts, null));
    }

    // ---- in a real render -------------------------------------------------------------------

    @Test
    @DisplayName("should print the fallback characters in the fallback fonts, read back from the PDF")
    void shouldPrintInFallbackFonts_whenRenderedByFlyingSaucer() throws Exception {
        Document document = applied("<p>FAKE " + HAN + " <b>" + MAHJONG + "</b> " + GURMUKHI + " caf" + chars(0xE9) + "</p>");
        String xhtml = XhtmlCleanup.serialize(document);

        ITextRenderer renderer = LocalOnlyUserAgent.createRestrictedRenderer(List.of(directory), new BundledFontResolver(fonts));
        renderer.setDocumentFromString(xhtml);
        renderer.layout();
        ByteArrayOutputStream pdf = new ByteArrayOutputStream();
        renderer.createPDF(pdf);

        try (PDDocument loaded = Loader.loadPDF(pdf.toByteArray())) {
            Set<String> fontNames = new TreeSet<>();
            PDResources resources = loaded.getPage(0).getResources();
            for (COSName name : resources.getFontNames()) {
                fontNames.add(resources.getFont(name).getName().replaceFirst("^[A-Z]{6}\\+", ""));
            }
            assertThat(fontNames).contains("DejaVuSans", "WenQuanYiMicroHei", "Symbola");
            // Flying Saucer paints an element's own text before its children's, so the stream order differs
            // from the page order; sorting by position reads the line as it looks.
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String extracted = stripper.getText(loaded);
            assertThat(extracted).contains("FAKE " + HAN).contains(MAHJONG).contains("caf" + chars(0xE9)).contains(REPLACEMENT);
        }
    }
}
