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
package io.github.carlos_emr.carlos.documentManager.htmlpdf.service;

import io.github.carlos_emr.carlos.documentManager.htmlpdf.TestFonts;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.TestPdfs;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderDegradation;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.engine.BundledFontSet;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.engine.LocalOnlyUserAgent;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.awt.Rectangle;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for {@link PrintLayoutService}. Documents go through {@link PrintDocumentComposer}, so the
 * injected style sheet is in them as it is in production; the pages are read back with PDFBox.
 */
@Tag("unit")
@Tag("htmlpdf")
@DisplayName("PrintLayoutService Unit Tests")
class PrintLayoutServiceUnitTest {

    @TempDir
    Path baseDirectory;

    private final PrintDocumentComposer composer = new PrintDocumentComposer();
    private final BundledFontSet fonts = TestFonts.sharedFonts();
    private final PrintLayoutService service = new PrintLayoutService();

    private static Set<HtmlPdfRenderDegradation> degradations() {
        return EnumSet.noneOf(HtmlPdfRenderDegradation.class);
    }

    /** A page of one black block, {@code widthPx} wide, at the very start of the body. */
    private static String blockPage(int widthPx) {
        return "<html><head><title>FAKE-Title</title><style>body { margin: 0 } "
                + ".block { height: 40px; width: " + widthPx + "px; background: #000 }</style></head>"
                + "<body><div class=\"block\"></div></body></html>";
    }

    private String xhtml(String html) {
        Set<HtmlPdfRenderDegradation> none = degradations();
        return composer.finish(composer.prepare(html, PrintMode.NORMAL, none), PrintMode.NORMAL, fonts, none);
    }

    private byte[] render(String html) {
        return render(html, degradations());
    }

    private byte[] render(String html, Set<HtmlPdfRenderDegradation> degradations) {
        return service.render(PrintMode.NORMAL, xhtml(html), baseDirectory, List.of(baseDirectory), fonts, degradations);
    }

    // ---- geometry ---------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(ints = {400, 850, 1000, 1400})
    @DisplayName("should place any page width on US Letter with 10 mm margins")
    void shouldPlaceOnLetter_withTenMillimetreMargins(int widthPx) throws Exception {
        byte[] pdf = render(blockPage(widthPx));

        assertThat(TestPdfs.pageSizes(pdf)).hasSize(1).allSatisfy(size -> {
            assertThat(size[0]).isEqualTo(612f);
            assertThat(size[1]).isEqualTo(792f);
        });
        Rectangle block = TestPdfs.darkBounds(TestPdfs.image(pdf, 1));
        assertThat(block.x).isBetween(27, 29);
        assertThat(block.y).isBetween(27, 29);
        // Up to 925 px the content fits at the default zoom and the block stops short of the right margin;
        // a wider block fills the width to the right margin: nothing is clipped.
        if (widthPx >= 1000) {
            assertThat(block.x + block.width).isBetween(583, 586);
        } else {
            assertThat(block.x + block.width).isCloseTo(Math.round(28.3465f + widthPx * 0.75f * 0.8f), within(1));
        }
    }

    @Test
    @DisplayName("should use the default zoom for a page that fits and a smaller one for a wide page")
    void shouldZoomOnlyWidePages() throws Exception {
        // Exactly 0.8: a page that fits is never sent through the pass-2 arithmetic, which would give 0.7995.
        assertThat(TestPdfs.zoomOf(render(blockPage(400)))).isEqualTo(0.8f);
        assertThat(TestPdfs.zoomOf(render(blockPage(850)))).isEqualTo(0.8f);
        // 926 px overflows the 925.5 px content area by half a point: pass 1 widens the page to 765.4 pt,
        // inside the slack that keeps the default zoom. 940 px widens it to 775.9 pt: pass 2.
        assertThat(TestPdfs.zoomOf(render(blockPage(926)))).isEqualTo(0.8f);
        assertThat(TestPdfs.zoomOf(render(blockPage(940)))).isLessThan(0.8f).isCloseTo(0.789f, within(0.002f));
        assertThat(TestPdfs.zoomOf(render(blockPage(1000)))).isCloseTo(0.7404f, within(0.002f));
        assertThat(TestPdfs.zoomOf(render(blockPage(1400)))).isCloseTo(0.5289f, within(0.002f));
    }

    @Test
    @DisplayName("should shrink every page when only a later page is wide")
    void shouldShrinkAllPages_whenOnlyALaterPageIsWide() throws Exception {
        String html = "<html><head><style>body { margin: 0 } .small { height: 40px; width: 300px; background: #000 } "
                + ".wide { page-break-before: always; height: 40px; width: 1200px; background: #000 }</style></head>"
                + "<body><div class=\"small\"></div><div class=\"wide\"></div></body></html>";

        byte[] pdf = render(html);

        assertThat(TestPdfs.pageSizes(pdf)).hasSize(2).allSatisfy(size -> assertThat(size).containsExactly(612f, 792f));
        assertThat(TestPdfs.zoomOf(pdf)).isLessThan(0.7f);
        Rectangle first = TestPdfs.darkBounds(TestPdfs.image(pdf, 1));
        Rectangle second = TestPdfs.darkBounds(TestPdfs.image(pdf, 2));
        assertThat(second.width).isBetween(550, 560);
        assertThat(first.width).isLessThan(second.width / 3);
    }

    @Test
    @DisplayName("should ignore the page size and margins an author sets")
    void shouldIgnoreAuthorPageRule() throws Exception {
        String html = "<html><head><style>@page { size: A4 landscape; margin: 5mm } body { margin: 0 } "
                + ".block { height: 40px; width: 300px; background: #000 }</style></head>"
                + "<body><div class=\"block\"></div></body></html>";

        byte[] pdf = render(html);

        assertThat(TestPdfs.pageSizes(pdf)).singleElement().satisfies(size -> assertThat(size).containsExactly(612f, 792f));
        assertThat(TestPdfs.darkBounds(TestPdfs.image(pdf, 1)).x).isBetween(27, 29);
        assertThat(TestPdfs.zoomOf(pdf)).isCloseTo(0.8f, within(0.001f));
    }

    @Test
    @DisplayName("should keep the default zoom when a very long word wraps instead of widening the page")
    void shouldKeepDefaultZoom_forLongUrl() throws Exception {
        String html = "<html><head><style>body { margin: 0 }</style></head><body><p>"
                + "https://example.invalid/" + "a".repeat(600) + "</p></body></html>";

        byte[] pdf = render(html);

        assertThat(TestPdfs.zoomOf(pdf)).isCloseTo(0.8f, within(0.001f));
        assertThat(TestPdfs.pageTexts(pdf).get(0)).contains("aaaaaaaaaaaaaaaa");
    }

    @Test
    @DisplayName("should keep a table cell's label on one line instead of squeezing its column to one letter")
    void shouldKeepLabelColumns_atTheirNaturalWidth() throws Exception {
        // The shape of the Annual form's lifestyle block: a narrow cell holding a table of labels and
        // fixed-width fields. With word-wrap on the whole body Flying Saucer takes every cell's
        // minimum width to be one character and prints the labels as vertical strips of letters.
        String html = "<html><head><style>body { margin: 0 }</style></head><body>"
                + "<table width=\"100%\"><tr><td style=\"width: 25%\"><table><tr><td>Smoking:</td>"
                + "<td><input type=\"text\" style=\"width: 230px\" value=\"never\"></td></tr>"
                + "<tr><td>Alcohol:</td><td><input type=\"text\" style=\"width: 230px\" value=\"weekly\"></td></tr></table></td>"
                + "<td>Screening</td></tr></table></body></html>";

        byte[] pdf = render(html);

        String text = TestPdfs.pageTexts(pdf).get(0);
        assertThat(text).contains("Smoking:").contains("Alcohol:").contains("Screening");
        assertThat(TestPdfs.pageSizes(pdf)).hasSize(1);
    }

    @Test
    @DisplayName("should keep the default zoom when the very long word is in a table cell, and break it there")
    void shouldKeepDefaultZoom_forLongUrlInTableCell() throws Exception {
        String html = "<html><head><style>body { margin: 0 }</style></head><body><table width=\"100%\"><tr>"
                + "<td>label</td><td><a href=\"x\">https://example.invalid/" + "a".repeat(600) + "</a></td></tr></table></body></html>";

        byte[] pdf = render(html);

        assertThat(TestPdfs.zoomOf(pdf)).isCloseTo(0.8f, within(0.001f));
        assertThat(TestPdfs.pageTexts(pdf).get(0)).contains("label").contains("aaaaaaaaaaaaaaaa");
    }

    @Test
    @DisplayName("should print 8 px text at 6 points: the 10 px minimum, scaled by the default zoom")
    void shouldPrintTinyText_atSixPoints() throws Exception {
        byte[] pdf = render("<html><body><p style=\"font-size: 8px\">tiny text</p></body></html>");

        List<TextPosition> glyphs = TestPdfs.glyphs(pdf);

        assertThat(glyphs).isNotEmpty().allSatisfy(glyph -> assertThat(TestPdfs.sizeOnPage(glyph)).isCloseTo(6f, within(0.05f)));
    }

    @Test
    @DisplayName("should apply print media rules and not screen media rules")
    void shouldApplyPrintMediaOnly() throws Exception {
        byte[] pdf = render("<html><head><style>.x { font-size: 20px } @media print { .x { font-size: 30px } }"
                + " @media screen { .x { font-size: 50px } }</style></head><body><p class=\"x\">media</p></body></html>");

        assertThat(TestPdfs.glyphs(pdf)).isNotEmpty()
                .allSatisfy(glyph -> assertThat(TestPdfs.sizeOnPage(glyph)).isCloseTo(30f * 0.75f * 0.8f, within(0.05f)));
    }

    // ---- content ----------------------------------------------------------------------------

    @Test
    @DisplayName("should copy the document title")
    void shouldCopyTitle_toThePlacedPdf() throws Exception {
        byte[] pdf = render(blockPage(300));

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getDocumentInformation().getTitle()).isEqualTo("FAKE-Title");
        }
    }

    @Test
    @DisplayName("should repeat the header of a paginated table on every page")
    void shouldRepeatTableHeader_onEveryPage() throws Exception {
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            rows.append("<tr><td>FAKE-row-").append(i).append("</td></tr>");
        }

        byte[] pdf = render("<html><body><table><thead><tr><th>FAKE-HEADER</th></tr></thead><tbody>" + rows + "</tbody></table></body></html>");

        List<String> pages = TestPdfs.pageTexts(pdf);
        assertThat(pages.size()).isGreaterThan(2);
        // Booleans, not the text, so a failure does not print four pages.
        assertThat(pages.stream().allMatch(text -> text.contains("FAKE-HEADER"))).isTrue();
        String all = String.join("\n", pages);
        assertThat(all.contains("FAKE-row-0\n") && all.contains("FAKE-row-199")).isTrue();
    }

    @Test
    @DisplayName("should flatten form fields into page content before placing the pages")
    void shouldFlattenAcroForm_beforePlacement() throws Exception {
        // Not through PrintFormControls: a raw <input> makes Flying Saucer write a form field.
        String xhtml = composer.finish(org.jsoup.Jsoup.parse("<html><body><p>label</p><input type=\"text\" value=\"FAKE-VALUE\" "
                + "name=\"field\"></body></html>"), PrintMode.NORMAL, fonts, degradations());

        byte[] pdf = service.render(PrintMode.NORMAL, xhtml, baseDirectory, List.of(baseDirectory), fonts, degradations());

        assertThat(new RenderedPdfValidator().validate(pdf).valid()).isTrue();
        assertThat(TestPdfs.pageTexts(pdf).get(0)).contains("label").contains("FAKE-VALUE");
    }

    @ParameterizedTest
    @ValueSource(strings = {"en-GB", "en", "tr-TR", "fr-CA", "de-DE"})
    @DisplayName("should produce the same pages under any default locale")
    void shouldRenderIdentically_underAnyDefaultLocale(String tag) throws Exception {
        String html = "<html><head><title>T</title><style>.a { text-transform: uppercase } h1 { font-variant: small-caps }"
                + "</style></head><body><h1>Insert Title Heading</h1><p class=\"a\">info ice index</p>"
                + "<table border=\"1\"><tr><td>1,234.50</td><td>Total</td></tr></table><ol><li>one</li><li>two</li></ol></body></html>";
        Locale original = Locale.getDefault();
        List<String> reference = glyphSignature(render(html));
        try {
            Locale.setDefault(Locale.forLanguageTag(tag));

            assertThat(glyphSignature(render(html))).isEqualTo(reference);
        } finally {
            Locale.setDefault(original);
        }
    }

    /** Text and rounded position of every glyph: what the page looks like, without dates and ids. */
    private static List<String> glyphSignature(byte[] pdf) throws Exception {
        List<String> signature = new ArrayList<>();
        for (TextPosition glyph : TestPdfs.glyphs(pdf)) {
            signature.add(glyph.getUnicode() + "@" + Math.round(glyph.getX() * 100) + "," + Math.round(glyph.getY() * 100)
                    + "/" + Math.round(glyph.getFontSizeInPt() * 100));
        }
        return signature;
    }

    // ---- failures ---------------------------------------------------------------------------

    @Test
    @DisplayName("should use pass 1 at the tighter zoom when pass 2 fails, and say the margins are smaller")
    void shouldUsePassOne_whenPassTwoFails() throws Exception {
        List<ITextRenderer> renderers = new ArrayList<>();
        PrintLayoutService failingSecondPass = new PrintLayoutService((directories, resolver) -> {
            ITextRenderer spy = Mockito.spy(LocalOnlyUserAgent.createRestrictedRenderer(directories, resolver));
            if (renderers.size() == 1) {
                Mockito.doThrow(new IllegalStateException("FAKE-Patient layout failure")).when(spy).layout();
            }
            renderers.add(spy);
            return spy;
        });
        Set<HtmlPdfRenderDegradation> degradations = degradations();

        byte[] pdf = failingSecondPass.render(PrintMode.NORMAL, xhtml(blockPage(1200)), baseDirectory,
                List.of(baseDirectory), fonts, degradations);

        assertThat(renderers).hasSize(2);
        // Pass 1 widens pages to fit the content; pass 2 has a page made for the content and does not.
        Mockito.verify(renderers.get(0)).setScaleToFit(true);
        Mockito.verify(renderers.get(1)).setScaleToFit(false);
        assertThat(degradations).containsExactly(HtmlPdfRenderDegradation.MARGINS_REDUCED);
        assertThat(new RenderedPdfValidator().validate(pdf).valid()).isTrue();
        // Pass 1 widened to 1200 px + 70.87 pt of margins, so the zoom is 612 / W and the block fills
        // the width, but the pass-1 margins (35.43 pt) shrink with it: smaller than 10 mm (28.35 pt).
        // The page is as high as it is wide, shrunk, so it is placed at the top left, not the bottom.
        float width = 1200f * 0.75f + 2 * 35.4331f;
        float zoom = 612f / width;
        assertThat(TestPdfs.zoomOf(pdf)).isCloseTo(zoom, within(0.002f));
        Rectangle block = TestPdfs.darkBounds(TestPdfs.image(pdf, 1));
        assertThat(block.x).isCloseTo(Math.round(35.4331f * zoom), within(1)).isLessThan(27);
        assertThat(block.y).isCloseTo(Math.round(35.4331f * zoom), within(1)).isLessThan(27);
        assertThat(block.x + block.width).isCloseTo(Math.round(612f - 35.4331f * zoom), within(1));
    }

    @Test
    @DisplayName("should report a failing first pass with its phase and no document text")
    void shouldFailWithPhase_whenPassOneFails() {
        PrintLayoutService failing = new PrintLayoutService((directories, resolver) -> {
            ITextRenderer spy = Mockito.spy(LocalOnlyUserAgent.createRestrictedRenderer(directories, resolver));
            Mockito.doThrow(new IllegalStateException("FAKE-Patient layout failure")).when(spy).layout();
            return spy;
        });

        assertThatExceptionOfType(HtmlPdfRenderException.class).isThrownBy(() -> failing.render(PrintMode.SAFE,
                        xhtml(blockPage(300)), baseDirectory, List.of(baseDirectory), fonts, degradations()))
                .satisfies(e -> {
                    assertThat(e.phase()).isEqualTo(HtmlPdfRenderException.Phase.LAYOUT_PASS_1);
                    assertThat(e.mode()).isEqualTo(PrintMode.SAFE);
                    assertThat(e.getMessage()).doesNotContain("FAKE-");
                });
    }

    @Test
    @DisplayName("should treat a linkage error in a pass like any other failure of that pass")
    void shouldCatchLinkageError_inAPass() {
        PrintLayoutService failing = new PrintLayoutService((directories, resolver) -> {
            throw new NoClassDefFoundError("org/apache/batik/FAKE");
        });

        assertThatExceptionOfType(HtmlPdfRenderException.class).isThrownBy(() -> failing.render(PrintMode.NORMAL,
                        xhtml(blockPage(300)), baseDirectory, List.of(baseDirectory), fonts, degradations()))
                .satisfies(e -> assertThat(e.getMessage()).contains("java.lang.NoClassDefFoundError").doesNotContain("batik"));
    }

    @Test
    @DisplayName("should report a failing placement with its phase")
    void shouldFailWithPhase_whenPlacementFails() {
        // Flying Saucer is asked to write a document with no pages at all: nothing can be placed.
        PrintLayoutService failing = new PrintLayoutService((directories, resolver) -> {
            ITextRenderer spy = Mockito.spy(LocalOnlyUserAgent.createRestrictedRenderer(directories, resolver));
            try {
                Mockito.doAnswer(invocation -> {
                    java.io.OutputStream out = invocation.getArgument(0);
                    out.write("%PDF-1.4 FAKE not a document".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    return null;
                }).when(spy).createPDF(Mockito.any(java.io.OutputStream.class), Mockito.eq(true));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return spy;
        });

        assertThatExceptionOfType(HtmlPdfRenderException.class).isThrownBy(() -> failing.render(PrintMode.NORMAL,
                        xhtml(blockPage(300)), baseDirectory, List.of(baseDirectory), fonts, degradations()))
                .satisfies(e -> assertThat(e.phase()).isEqualTo(HtmlPdfRenderException.Phase.LAYOUT_PASS_1));
    }

    @Test
    @DisplayName("should fall back to pass 1 when the pass-1 page rule is missing from the document")
    void shouldFallBackToPassOne_whenPageRuleMissing() throws Exception {
        String withoutRule = xhtml(blockPage(1200)).replace(
                io.github.carlos_emr.carlos.documentManager.htmlpdf.support.PrintStylesheet.PASS_1_PAGE_RULE, "");
        Set<HtmlPdfRenderDegradation> degradations = degradations();

        byte[] pdf = service.render(PrintMode.NORMAL, withoutRule, baseDirectory, List.of(baseDirectory), fonts, degradations);

        // Without the rule, Flying Saucer lays pass 1 out on its own default page (Letter, 0.75 in margins),
        // widened to the content: wide enough for pass 2, which then cannot be built.
        assertThat(degradations).containsExactly(HtmlPdfRenderDegradation.MARGINS_REDUCED);
        assertThat(new RenderedPdfValidator().validate(pdf).valid()).isTrue();
    }

    // ---- logging ----------------------------------------------------------------------------

    @Test
    @DisplayName("should log a warning with the zoom and nothing else when the zoom is below 0.4")
    void shouldWarnWithZoomOnly_whenZoomBelowPointFour() throws Exception {
        try (LogCapture capture = LogCapture.forLogger(PrintLayoutService.class)) {
            byte[] pdf = render(blockPage(2200));

            assertThat(TestPdfs.zoomOf(pdf)).isLessThan(0.4f);
            assertThat(capture.messages()).singleElement().asString().contains("low zoom of 0.3")
                    .doesNotContain(baseDirectory.toString()).doesNotContain("FAKE");
        }
    }

    @Test
    @DisplayName("should log nothing for an ordinary page")
    void shouldLogNothing_forOrdinaryPage() throws Exception {
        try (LogCapture capture = LogCapture.forLogger(PrintLayoutService.class)) {
            render(blockPage(500));

            assertThat(capture.messages()).isEmpty();
        }
    }

    @Test
    @DisplayName("should log the failure of pass 2 by phase and class only")
    void shouldLogPassTwoFailure_withoutMessage() {
        PrintLayoutService failingSecondPass = failingOnCall(2);
        try (LogCapture capture = LogCapture.forLogger(PrintLayoutService.class)) {
            failingSecondPass.render(PrintMode.NORMAL, xhtml(blockPage(1200)), baseDirectory, List.of(baseDirectory), fonts,
                    degradations());

            assertThat(capture.messages()).singleElement().asString().contains("pass 2 failed").contains("LAYOUT_PASS_2")
                    .doesNotContain("FAKE-");
        }
    }

    private PrintLayoutService failingOnCall(int failingCall) {
        AtomicInteger calls = new AtomicInteger();
        return new PrintLayoutService((directories, resolver) -> {
            ITextRenderer renderer = LocalOnlyUserAgent.createRestrictedRenderer(directories, resolver);
            if (calls.incrementAndGet() == failingCall) {
                ITextRenderer spy = Mockito.spy(renderer);
                Mockito.doThrow(new IllegalStateException("FAKE-Patient layout failure")).when(spy).layout();
                return spy;
            }
            return renderer;
        });
    }
}
