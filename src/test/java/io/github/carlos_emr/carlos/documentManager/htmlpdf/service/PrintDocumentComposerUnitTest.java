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
import io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderDegradation;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.engine.BundledFontResolver;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.engine.BundledFontSet;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.engine.LocalOnlyUserAgent;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.support.PrintStylesheet;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.xhtmlrenderer.pdf.ITextRenderer;
import org.xhtmlrenderer.resource.XMLResource;

import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Unit tests for {@link PrintDocumentComposer}: the order of the document rules, the injected style
 * sheet and note, and the safe and text-only modes. The composer is pure, so it is built with
 * {@code new} and no collaborators.
 */
@Tag("unit")
@Tag("htmlpdf")
@DisplayName("PrintDocumentComposer Unit Tests")
class PrintDocumentComposerUnitTest {

    private static final String SAFE_NOTE =
            "This document was printed with simplified formatting because its layout could not be rendered.";
    private static final String TEXT_ONLY_NOTE =
            "This document was printed as plain text because its layout could not be rendered.";
    private static final String IMAGES_NOTE = "Images were omitted because the server was busy. Print again to include them.";
    private static final String RTL_NOTE = "Right-to-left text was converted for printing. Check the line order of longer"
            + " right-to-left passages against the original.";

    /** SHALOM (shin, lamed, vav, final mem) in logical order, and in the visual order it prints in. */
    private static final String SHALOM = chars(0x5E9, 0x5DC, 0x5D5, 0x5DD);
    private static final String SHALOM_VISUAL = chars(0x5DD, 0x5D5, 0x5DC, 0x5E9);

    private final Path directory = TestFonts.sharedDirectory();
    private final PrintDocumentComposer composer = new PrintDocumentComposer();
    private final BundledFontSet fonts = TestFonts.sharedFonts();

    private static String chars(int... codePoints) {
        return new String(codePoints, 0, codePoints.length);
    }

    private static Set<HtmlPdfRenderDegradation> degradations(HtmlPdfRenderDegradation... values) {
        Set<HtmlPdfRenderDegradation> set = EnumSet.noneOf(HtmlPdfRenderDegradation.class);
        set.addAll(List.of(values));
        return set;
    }

    private Document prepared(String bodyHtml, PrintMode mode) {
        return composer.prepare("<html><head><title>T</title></head><body>" + bodyHtml + "</body></html>", mode,
                degradations());
    }

    // ---- prepare: order of the steps --------------------------------------------------------

    @Test
    @DisplayName("should remove invisible and bidi characters before it reorders right-to-left text")
    void shouldApplyStepsInSpecOrder_forPrepare() {
        // A left-to-right mark (U+200E) is a strong left-to-right character; left in, it would change
        // how the neutral space and the digits are placed. Removed first (step b before h), the
        // result is that of the text without it.
        String lrm = chars(0x200E);
        Document withMark = prepared("<p>" + SHALOM + lrm + " 123</p>", PrintMode.NORMAL);
        Document without = prepared("<p>" + SHALOM + " 123</p>", PrintMode.NORMAL);

        assertThat(withMark.select("p").first().wholeText()).isEqualTo(without.select("p").first().wholeText());
        assertThat(without.select("p").first().wholeText()).isEqualTo("123 " + SHALOM_VISUAL);
    }

    @Test
    @DisplayName("should replace form controls before right-to-left text is reordered, so a value is reordered too")
    void shouldReplaceControlsBeforeReordering_inPrepare() {
        Document document = prepared("<input type=\"text\" value=\"" + SHALOM + "\">", PrintMode.NORMAL);

        assertThat(document.select("span.carlos-pdf-text").first().text()).isEqualTo(SHALOM_VISUAL);
    }

    @Test
    @DisplayName("should clamp spans before it marks tables for pagination")
    void shouldClampSpansBeforeMarkingTables_inPrepare() {
        Document document = prepared("<table id=\"t\"><thead><tr><th>H</th></tr></thead>"
                + "<tbody><tr><td rowspan=\"0\" colspan=\"99999999999\">A</td></tr></tbody></table>", PrintMode.NORMAL);

        assertThat(document.getElementById("t").hasClass("carlos-pdf-paginate")).isTrue();
        assertThat(document.select("td").attr("rowspan")).isEqualTo("1");
        assertThat(document.select("td").attr("colspan")).isEqualTo("1000");
    }

    @Test
    @DisplayName("should mark the block that holds a very long word, in every mode and so in the plain text too")
    void shouldMarkLongWords_inEveryMode() {
        String word = "u".repeat(60);
        for (PrintMode mode : PrintMode.values()) {
            Document document = prepared("<table><tr><td id=\"long\">" + word + "</td><td id=\"short\">short</td></tr></table>"
                    + "<p>fine</p>", mode);

            assertThat(document.select(".carlos-pdf-wrap").text()).as(mode.name()).contains(word);
            assertThat(document.select(".carlos-pdf-wrap").text()).as(mode.name()).doesNotContain("fine");
        }
        Document normal = prepared("<table><tr><td id=\"long\">" + word + "</td><td id=\"short\">short</td></tr></table>",
                PrintMode.NORMAL);
        assertThat(normal.getElementById("long").hasClass("carlos-pdf-wrap")).isTrue();
        assertThat(normal.getElementById("short").hasClass("carlos-pdf-wrap")).isFalse();
    }

    @Test
    @DisplayName("should turn the system colour keywords Word writes into colours, in the style attributes and the style sheets")
    void shouldReplaceSystemColours_inNormalMode() {
        Document document = composer.prepare("<html><head><title>T</title><style>td { border: solid windowtext 1pt }</style></head>"
                + "<body><table><tr><td id=\"c\" style=\"border-top: none; background: window\">x</td></tr></table></body></html>",
                PrintMode.NORMAL, degradations());

        assertThat(document.select("style").first().data()).isEqualTo("td { border: solid #000000 1pt }");
        assertThat(document.getElementById("c").attr("style")).isEqualTo("border-top: none; background: #ffffff");
    }

    @Test
    @DisplayName("should apply every document rule in normal mode and keep the author's styles")
    void shouldApplyEveryRule_inNormalMode() {
        Document document = composer.prepare("<html><head><title>T</title><style>p { color: red }</style>"
                + "<script>x()</script></head><body><!-- c -- d --><div style=\"position:fixed\">hy" + chars(0xAD) + "phen</div>"
                + "<ol type=\"a\"><li>x</li></ol><img src=\"i.png\"><input type=\"checkbox\" checked></body></html>",
                PrintMode.NORMAL, degradations());

        assertThat(document.select("script")).isEmpty();
        assertThat(document.select("style")).hasSize(1);
        assertThat(document.select("div").attr("style")).isEqualTo("position:absolute");
        assertThat(document.select("div").text()).isEqualTo("hyphen");
        assertThat(document.select("ol").attr("style")).isEqualTo("list-style-type: lower-alpha");
        assertThat(document.select("img").attr("alt")).isEmpty();
        assertThat(document.select("input")).isEmpty();
        assertThat(document.select("span.carlos-pdf-checkbox")).hasSize(1);
    }

    @Test
    @DisplayName("should ask for the right-to-left note when a long run is found")
    void shouldAddBestEffortDegradation_forLongRightToLeftRun() {
        Set<HtmlPdfRenderDegradation> set = degradations();

        composer.prepare("<p>" + chars(0x5D0).repeat(41) + "</p>", PrintMode.NORMAL, set);

        assertThat(set).containsExactly(HtmlPdfRenderDegradation.RTL_BEST_EFFORT);
    }

    // ---- prepare: safe mode -----------------------------------------------------------------

    @Test
    @DisplayName("should drop author styles and box inline SVG in safe mode, keeping what the rules add")
    void shouldDropAuthorStylesAndBoxSvg_inSafeMode() {
        Document document = composer.prepare("<html><head><style>p{color:red}</style><link rel=\"stylesheet\" href=\"a.css\">"
                + "</head><body><p style=\"color:blue\">text</p><svg><rect/></svg>"
                + "<input type=\"text\" size=\"10\" style=\"color: red\" value=\"v\"><div dir=\"rtl\">" + SHALOM + "</div></body></html>",
                PrintMode.SAFE, degradations());

        assertThat(document.select("link, svg")).isEmpty();
        assertThat(document.select("style")).isEmpty();
        assertThat(document.select("p").attr("style")).isEmpty();
        assertThat(document.select("span.carlos-pdf-image-unavailable").text()).isEqualTo("[image unavailable]");
        // The author's color is gone, the minimum width the control rule adds is not.
        assertThat(document.select("span.carlos-pdf-text").attr("style")).isEqualTo("min-width: 5.5em");
        assertThat(document.select("div[dir]").attr("style")).isEqualTo("text-align: right");
    }

    // ---- prepare: text-only mode ------------------------------------------------------------

    @Test
    @DisplayName("should reduce the document to its text in reading order in text-only mode")
    void shouldBuildReadingOrderText_inTextOnlyMode() {
        Document document = composer.prepare("<html><head><title>FAKE title</title><style>p{}</style></head><body>"
                + "<h1>Heading</h1><table><tr><td>FAKE-A</td><td>FAKE-B</td></tr></table><img src=\"x.png\">"
                + "<p>Name: <input type=\"text\" value=\"FAKE-Name\"> <input type=\"checkbox\" checked></p>"
                + "<script>x()</script></body></html>", PrintMode.TEXT_ONLY, degradations());

        List<String> lines = document.select("body > p").stream().map(Element::text).toList();
        assertThat(lines).containsExactly("Heading", "FAKE-A | FAKE-B", "Name: FAKE-Name " + chars(0x2612));
        assertThat(document.select("img, style, script, table")).isEmpty();
        assertThat(document.title()).isEqualTo("FAKE title");
    }

    @Test
    @DisplayName("should still reorder right-to-left text in text-only mode")
    void shouldReorderRightToLeftText_inTextOnlyMode() {
        Document document = prepared("<p>" + SHALOM + "</p>", PrintMode.TEXT_ONLY);

        assertThat(document.select("p").text()).isEqualTo(SHALOM_VISUAL);
    }

    @Test
    @DisplayName("should handle empty and fragment-only input in every mode")
    void shouldHandleEmptyAndFragmentInput_inEveryMode() {
        for (PrintMode mode : PrintMode.values()) {
            assertThat(composer.prepare("", mode, degradations()).body()).as(mode.name()).isNotNull();
            assertThat(composer.prepare("just text", mode, degradations()).text()).as(mode.name()).isEqualTo("just text");
        }
    }

    // ---- finish: style sheet and note -------------------------------------------------------

    @Test
    @DisplayName("should place the defaults first in the head, before the author's styles")
    void shouldPlaceDefaultsFirst_inHead() {
        Document document = Jsoup.parse("<html><head><title>T</title><style>.x { color: red }</style></head><body><p>x</p></body></html>");

        composer.finish(document, PrintMode.NORMAL, fonts, degradations());

        Element first = document.head().child(0);
        assertThat(first.normalName()).isEqualTo("style");
        assertThat(first.id()).isEqualTo("carlos-pdf-defaults");
        assertThat(first.data()).startsWith("@page { size: 765pt 990pt !important; margin: 35.4331pt !important }");
        assertThat(document.head().children().stream().map(Element::normalName).toList()).containsExactly("style", "title", "style");
    }

    @Test
    @DisplayName("should remove the engine's own CSS properties in every mode, so a style cannot set a span the clamp never sees")
    void shouldRemoveEngineProperties_inEveryMode() {
        String html = "<html><head><style>td { -fs-table-cell-colspan: 99999999999; color: red }</style></head><body>"
                + "<table><tr><td id=\"a\" style=\"-fs-table-cell-rowspan: 99999999999; width: 3px\">FAKE-a</td></tr></table></body></html>";

        for (PrintMode mode : PrintMode.values()) {
            Document document = composer.prepare(html, mode, degradations());
            composer.finish(document, mode, fonts, degradations());

            assertThat(document.html()).as(mode.name()).doesNotContain("99999999999").doesNotContain("-fs-table-cell");
            assertThat(document.text()).as(mode.name()).contains("FAKE-a");
        }
    }

    @Test
    @DisplayName("should keep the bars of a progress and a meter in safe mode, without the author's size, and leave them out of text-only mode")
    void shouldKeepBarsInSafeMode_andLeaveThemOutOfTextOnlyMode() {
        String html = "<html><head></head><body><progress value=\"30\" max=\"100\" style=\"width:300px\"></progress>"
                + "<meter value=\"0.6\"></meter><p>FAKE-text</p></body></html>";

        Document safe = composer.prepare(html, PrintMode.SAFE, degradations());
        Document textOnly = composer.prepare(html, PrintMode.TEXT_ONLY, degradations());
        composer.finish(safe, PrintMode.SAFE, fonts, degradations());

        // The author's width goes with the author's styles; the width of the fill, which the rule adds, stays.
        assertThat(safe.select("span.carlos-pdf-progress").attr("style")).isEmpty();
        assertThat(safe.select("span.carlos-pdf-progress > span.carlos-pdf-gauge-fill").attr("style")).isEqualTo("width: 30%");
        assertThat(safe.select("span.carlos-pdf-meter > span.carlos-pdf-gauge-fill").attr("style")).isEqualTo("width: 60%");
        assertThat(safe.head().child(0).data()).contains(".carlos-pdf-gauge-fill {");
        // A bar has no text, and text-only mode keeps only text.
        assertThat(textOnly.body().text()).isEqualTo("FAKE-text");
        assertThat(textOnly.select("[class], [style]")).isEmpty();
    }

    @Test
    @DisplayName("should keep what the markup hid hidden in safe and text-only modes, which drop the styles that hide it")
    void shouldRemoveHiddenElements_inSafeAndTextOnlyModes() {
        String html = "<html><head></head><body><p>FAKE-Shown</p><div style=\"display:none\">FAKE-Screen copy</div><p hidden>FAKE-Scratch</p>"
                + "<input type=\"text\" hidden value=\"FAKE-Value\"><table><tr><td style=\"visibility:hidden\">FAKE-Cell</td><td>FAKE-Kept</td></tr></table></body></html>";

        // Normal mode retains inline CSS for Flying Saucer, but markup-hidden content is already gone.
        Document normal = composer.prepare(html, PrintMode.NORMAL, degradations());
        assertThat(normal.html()).contains("FAKE-Screen copy").doesNotContain("FAKE-Scratch", "FAKE-Value");

        for (PrintMode mode : List.of(PrintMode.SAFE, PrintMode.TEXT_ONLY)) {
            Document document = composer.prepare(html, mode, degradations());

            assertThat(document.text()).as(mode.name()).contains("FAKE-Shown").contains("FAKE-Kept")
                    .doesNotContain("FAKE-Screen copy").doesNotContain("FAKE-Scratch").doesNotContain("FAKE-Value").doesNotContain("FAKE-Cell");
        }
    }

    @Test
    @DisplayName("should follow the defaults with a rule for the body's legacy margin attributes, ahead of the author's styles")
    void shouldAddBodyMarginRule_afterTheDefaults() {
        Document document = composer.prepare("<html><head><title>T</title><style>body { margin: 20px }</style></head>"
                + "<body topmargin=\"0\" leftmargin=\"0\" rightmargin=\"0\"><p>x</p></body></html>", PrintMode.NORMAL, degradations());

        composer.finish(document, PrintMode.NORMAL, fonts, degradations());

        Element defaults = document.head().child(0);
        assertThat(defaults.id()).isEqualTo("carlos-pdf-defaults");
        assertThat(defaults.data()).isEqualTo(PrintStylesheet.css()
                + "body { margin-left: 0px; margin-right: 0px; margin-top: 0px; margin-bottom: 0px; }\n");
        assertThat(document.head().children().stream().map(Element::normalName).toList()).containsExactly("style", "title", "style");
    }

    @Test
    @DisplayName("should keep the legacy body margins in safe mode, which removes only styles, and leave them out of text-only mode")
    void shouldAddBodyMarginRule_inSafeModeButNotInTextOnlyMode() {
        String html = "<html><head><title>T</title></head><body leftmargin=\"2\"><p>x</p></body></html>";
        String rule = "body { margin-left: 2px; margin-right: 2px; }\n";

        Document safe = composer.prepare(html, PrintMode.SAFE, degradations());
        composer.finish(safe, PrintMode.SAFE, fonts, degradations());
        Document textOnly = composer.prepare(html, PrintMode.TEXT_ONLY, degradations());
        composer.finish(textOnly, PrintMode.TEXT_ONLY, fonts, degradations());

        assertThat(safe.head().child(0).data()).endsWith(rule);
        assertThat(textOnly.head().child(0).data()).isEqualTo(PrintStylesheet.css());
    }

    @Test
    @DisplayName("should add nothing to the defaults when the body has no margin attributes")
    void shouldAddNoBodyMarginRule_whenBodyHasNoMarginAttributes() {
        Document document = composer.prepare("<html><head><title>T</title></head><body bgcolor=\"#eeeeee\"><p>x</p></body></html>",
                PrintMode.NORMAL, degradations());

        composer.finish(document, PrintMode.NORMAL, fonts, degradations());

        assertThat(document.head().child(0).data()).isEqualTo(PrintStylesheet.css());
    }

    @Test
    @DisplayName("should create a head and a body when the document has none")
    void shouldCreateHeadAndBody_whenMissing() throws Exception {
        Document document = new Document("");

        String xhtml = composer.finish(document, PrintMode.NORMAL, fonts, degradations(HtmlPdfRenderDegradation.SAFE_MODE));

        assertThat(xhtml).contains("<style id=\"carlos-pdf-defaults\">").contains(SAFE_NOTE);
        XMLResource.load(new StringReader(xhtml));
    }

    @Test
    @DisplayName("should prepend the note as the first child of the body")
    void shouldPrependNote_forSafeMode() {
        Document document = Jsoup.parse("<html><body><p>first</p></body></html>");

        composer.finish(document, PrintMode.SAFE, fonts, degradations(HtmlPdfRenderDegradation.SAFE_MODE));

        Element note = document.body().child(0);
        assertThat(note.hasClass("carlos-pdf-note")).isTrue();
        assertThat(note.children().stream().map(Element::text).toList()).containsExactly(SAFE_NOTE);
        assertThat(document.body().child(1).text()).isEqualTo("first");
    }

    @Test
    @DisplayName("should list every reason that needs a note, in a fixed order, in one box")
    void shouldListAllReasons_inOneBoxInFixedOrder() {
        Document document = Jsoup.parse("<html><body><p>x</p></body></html>");

        composer.finish(document, PrintMode.TEXT_ONLY, fonts, degradations(HtmlPdfRenderDegradation.RTL_BEST_EFFORT,
                HtmlPdfRenderDegradation.TEXT_ONLY, HtmlPdfRenderDegradation.IMAGES_OMITTED_FOR_MEMORY,
                HtmlPdfRenderDegradation.SAFE_MODE));

        assertThat(document.select(".carlos-pdf-note")).hasSize(1);
        assertThat(document.select(".carlos-pdf-note > div").stream().map(Element::text).toList())
                .containsExactly(SAFE_NOTE, TEXT_ONLY_NOTE, IMAGES_NOTE, RTL_NOTE);
    }

    @Test
    @DisplayName("should add no note for degradations that need none")
    void shouldAddNoNote_forContentPreservingDegradations() {
        Document document = Jsoup.parse("<html><body><p>x</p></body></html>");

        composer.finish(document, PrintMode.NORMAL, fonts, degradations(HtmlPdfRenderDegradation.IMAGE_CONVERTED,
                HtmlPdfRenderDegradation.MARGINS_REDUCED, HtmlPdfRenderDegradation.IMAGE_UNAVAILABLE));

        assertThat(document.select(".carlos-pdf-note")).isEmpty();
        assertThat(document.body().child(0).text()).isEqualTo("x");
    }

    @Test
    @DisplayName("should write the note in English whatever the default locale")
    void shouldWriteEnglishNote_underFrenchDefaultLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.FRENCH);
            Document document = Jsoup.parse("<html><body><p>x</p></body></html>");

            composer.finish(document, PrintMode.SAFE, fonts, degradations(HtmlPdfRenderDegradation.SAFE_MODE));

            assertThat(document.select(".carlos-pdf-note > div").text()).isEqualTo(SAFE_NOTE);
        } finally {
            Locale.setDefault(original);
        }
    }

    // ---- finish: fallback fonts and serialisation -------------------------------------------

    @Test
    @DisplayName("should run the fallback-font step after the note, so the note is covered too")
    void shouldApplyFallbackFonts_afterNoteIsAdded() {
        Document document = Jsoup.parse("<html><body><p>" + chars(0x6587, 0x5B57) + "</p></body></html>");

        String xhtml = composer.finish(document, PrintMode.SAFE, fonts, degradations(HtmlPdfRenderDegradation.SAFE_MODE));

        assertThat(xhtml).contains("<span style=\"font-family:'CARLOS CJK'; font-style:normal\">"
                + chars(0x6587, 0x5B57) + "</span>");
    }

    @Test
    @DisplayName("should report a missing fallback face through the degradations")
    void shouldRecordMissingFallbackFace_whenFinishing() throws Exception {
        BundledFontSet noCjk = BundledFontSet.load(directory.resolve("DejaVuSans.ttf"), directory.resolve("DejaVuSans-Bold.ttf"),
                null, directory.resolve("Symbola_hint.ttf"));
        Set<HtmlPdfRenderDegradation> set = degradations();

        composer.finish(Jsoup.parse("<html><body><p>" + chars(0x6587) + "</p></body></html>"), PrintMode.NORMAL, noCjk, set);

        assertThat(set).containsExactly(HtmlPdfRenderDegradation.FALLBACK_FONT_UNAVAILABLE);
    }

    @Test
    @DisplayName("should write XHTML that Flying Saucer loads, from hostile input in every mode")
    void shouldWriteLoadableXhtml_forHostileInputInEveryMode() throws Exception {
        String hostile = "<!DOCTYPE html><html><head><script>x()</script><!-- -- --></head><body><o:p>w</o:p>"
                + "<p style=\"position:fixed\">t" + chars(0x1, 0xAD) + "</p><svg><use xlink:href=\"#a\"/></svg>"
                + "<table><tr><td rowspan=\"9\" colspan=\"99999999999\">c</td></tr></table></body></html>";

        for (PrintMode mode : PrintMode.values()) {
            Set<HtmlPdfRenderDegradation> set = degradations();
            Document document = composer.prepare(hostile, mode, set);

            String xhtml = composer.finish(document, mode, fonts, set);

            XMLResource.load(new StringReader(xhtml));
            assertThat(xhtml).as(mode.name()).startsWith("<!DOCTYPE html>");
        }
    }

    @Test
    @DisplayName("should let an author class rule beat a default of the same specificity in the printed PDF")
    void shouldLetAuthorRuleBeatDefault_whenRendered() throws Exception {
        Document document = composer.prepare("<html><head><style>.carlos-pdf-control { font-size: 14pt }</style></head>"
                + "<body><input type=\"text\" value=\"author-sized\"><input type=\"checkbox\"></body></html>",
                PrintMode.NORMAL, degradations());
        String xhtml = composer.finish(document, PrintMode.NORMAL, fonts, degradations());

        ITextRenderer renderer = LocalOnlyUserAgent.createRestrictedRenderer(List.of(directory), new BundledFontResolver(fonts));
        renderer.setDocumentFromString(xhtml);
        renderer.layout();
        ByteArrayOutputStream pdf = new ByteArrayOutputStream();
        renderer.createPDF(pdf);

        // Both controls take the author's 14 pt: the default .carlos-pdf-control (10 pt) and
        // .carlos-pdf-checkbox (13 px) rules have the same specificity and come first. (Flying Saucer
        // reports 14 pt as 13.99.)
        assertThat(fontSizesIn(pdf.toByteArray())).isNotEmpty().allMatch(size -> Math.abs(size - 14f) < 0.05f);
    }

    @Test
    @DisplayName("should reject null arguments")
    void shouldRejectNull_whenArgumentMissing() {
        Document document = Jsoup.parse("<p>x</p>");

        assertThatNullPointerException().isThrownBy(() -> composer.prepare(null, PrintMode.NORMAL, degradations()));
        assertThatNullPointerException().isThrownBy(() -> composer.prepare("x", null, degradations()));
        assertThatNullPointerException().isThrownBy(() -> composer.prepare("x", PrintMode.NORMAL, null));
        assertThatNullPointerException().isThrownBy(() -> composer.finish(null, PrintMode.NORMAL, fonts, degradations()));
        assertThatNullPointerException().isThrownBy(() -> composer.finish(document, null, fonts, degradations()));
        assertThatNullPointerException().isThrownBy(() -> composer.finish(document, PrintMode.NORMAL, null, degradations()));
        assertThatNullPointerException().isThrownBy(() -> composer.finish(document, PrintMode.NORMAL, fonts, null));
    }

    private static List<Float> fontSizesIn(byte[] pdf) throws Exception {
        List<Float> sizes = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            for (PDPage page : document.getPages()) {
                List<Object> operands = new ArrayList<>();
                for (Object token : new PDFStreamParser(page).parse()) {
                    if (token instanceof Operator operator) {
                        if (operator.getName().equals("Tf")) {
                            sizes.add(((COSNumber) operands.get(1)).floatValue());
                        }
                        operands.clear();
                    } else {
                        operands.add(token);
                    }
                }
            }
        }
        return sizes;
    }
}
