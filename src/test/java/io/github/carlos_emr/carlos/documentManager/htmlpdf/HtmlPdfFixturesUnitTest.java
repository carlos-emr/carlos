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
package io.github.carlos_emr.carlos.documentManager.htmlpdf;

import io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderDegradation;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderResult;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.service.HtmlPdfRenderService;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.service.HtmlPdfTestServices;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.support.PrintMessages;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;
import org.apache.logging.log4j.Level;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.TextPosition;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.awt.Rectangle;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.within;

/**
 * The module end to end on real and realistic documents, through the production beans: the Annual forms
 * (derived from the form JSPs, with the application's own style sheets), a page of every form control, a
 * hospital report as HTML, a report that began as a Word document, an encrypted email body and a Messenger
 * letter, then the geometry, script and edge-markup fixtures.
 *
 * <p>The central assertion is the spec's: every real document renders with no WARN- or ERROR-level
 * degradation, so a slide into safe mode or a lost image fails the build.</p>
 */
@Tag("unit")
@Tag("htmlpdf")
@DisplayName("HTML-to-PDF fixtures")
class HtmlPdfFixturesUnitTest {

    /** The four bundled faces, the only fonts a page may use. */
    private static final Set<String> BUNDLED_FONTS = Set.of("DejaVuSans", "DejaVuSans-Bold", "WenQuanYiMicroHei", "Symbola");

    private static final HtmlPdfRenderService SERVICE = HtmlPdfTestServices.newService();

    @TempDir
    Path directory;

    private Path webRoot;

    @BeforeEach
    void setUp() {
        webRoot = HtmlPdfFixtures.webRoot(directory);
    }

    private HtmlPdfRenderResult render(String fixture) throws PDFGenerationException {
        return HtmlPdfFixtures.render(SERVICE, fixture, webRoot);
    }

    private static String text(HtmlPdfRenderResult result) throws IOException {
        return String.join("\n", TestPdfs.pageTexts(result.pdf()));
    }

    /** The text with every run of white space made one space, so a phrase is found whatever its line breaks. */
    private static String flat(HtmlPdfRenderResult result) throws IOException {
        return text(result).replaceAll("\\s+", " ");
    }

    /** The distance from the top of the page, in points, of the first glyph of a phrase. */
    private static float topOf(HtmlPdfRenderResult result, String phrase) throws IOException {
        List<TextPosition> glyphs = TestPdfs.glyphs(result.pdf());
        StringBuilder all = new StringBuilder();
        List<Integer> glyphOfChar = new ArrayList<>();
        for (int i = 0; i < glyphs.size(); i++) {
            String unicode = glyphs.get(i).getUnicode();
            for (int c = 0; c < unicode.length(); c++) {
                glyphOfChar.add(i);
            }
            all.append(unicode);
        }
        int at = all.indexOf(phrase);
        assertThat(at).as("phrase %s in the page", phrase).isGreaterThanOrEqualTo(0);
        return glyphs.get(glyphOfChar.get(at)).getYDirAdj();
    }

    private static void assertEveryPageIsLetter(HtmlPdfRenderResult result) throws IOException {
        assertThat(TestPdfs.pageSizes(result.pdf())).isNotEmpty().allSatisfy(size -> assertThat(size).containsExactly(612f, 792f));
    }

    // ---- real documents ---------------------------------------------------------------------

    @ParameterizedTest(name = "{0}")
    @CsvSource({"annual/female.html, 2", "annual/male.html, 2", "forms/all-controls.html, 1", "hrm/demo-text-as-html.html, 1",
            "hrm/table-heavy.html, 2", "hrm/inline-styles.html, 1", "email/body.html, 1", "messenger/letter.html, 1"})
    @DisplayName("should render a real document with no WARN or ERROR degradation, on Letter pages, in the bundled fonts")
    void shouldRenderRealDocument_withoutWarnOrErrorDegradation(String fixture, int pages) throws Exception {
        HtmlPdfRenderResult result = render(fixture);

        assertThat(result.degradations()).as("degradations of %s", fixture).allMatch(d -> d.logLevel() == Level.INFO);
        assertThat(TestPdfs.pageSizes(result.pdf())).hasSize(pages);
        assertEveryPageIsLetter(result);
        assertThat(TestPdfs.fonts(result.pdf())).isNotEmpty().isSubsetOf(BUNDLED_FONTS);
        try (PDDocument document = Loader.loadPDF(result.pdf())) {
            assertThat(document.getDocumentCatalog().getAcroForm()).isNull();
            assertThat(document.getDocumentInformation().getTitle()).isEqualTo(Jsoup.parse(HtmlPdfFixtures.html(fixture)).title());
        }
        assertThat(text(result)).doesNotContain("font-family", "border-collapse", "margin:", "<style", "{");
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {"hrm/inline-styles.html|FAKE-Word style report", "email/body.html|FAKE-Secure message",
            "messenger/letter.html|FAKE-Messenger letter", "hrm/demo-text-as-html.html|FAKE-Hospital Report"})
    @DisplayName("should not print the title of the document")
    void shouldNotPrintTitle_forEveryDocumentType(String fixture, String title) throws Exception {
        assertThat(flat(render(fixture))).doesNotContain(title);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"annual/female.html", "annual/male.html"})
    @DisplayName("should never print the hidden field names or values of the Annual form")
    void shouldNeverPrintHiddenFieldNames_onAnnualForm(String fixture) throws Exception {
        String text = flat(render(fixture));

        assertThat(text).doesNotContain("demographic_no", "provider_no", "formCreated", "form_class", "form_link", "formannual",
                "999998", "type=\"hidden\"");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"annual/female.html", "annual/male.html"})
    @DisplayName("should print one marked box for every ticked checkbox of the Annual form, its values and its two-line text")
    void shouldPrintMarkedBoxesAndValues_ofTheAnnualForm(String fixture) throws Exception {
        // The ticked boxes the form really has: the ones in the page, not in a comment.
        int ticked = Jsoup.parse(HtmlPdfFixtures.html(fixture)).select("input[type=checkbox][checked]").size();
        assertThat(ticked).isGreaterThanOrEqualTo(10);
        HtmlPdfRenderResult result = render(fixture);
        String text = text(result);
        String flat = flat(result);

        assertThat(text.chars().filter(c -> c == '☒').count()).as("ticked boxes").isEqualTo(ticked);
        assertThat(text.chars().filter(c -> c == '☐').count()).as("empty boxes").isGreaterThan(10);
        assertThat(flat).contains("FAKE-Patient, Alice", "2026/10/29", "Dr. FAKE-Provider", "normal exam", "≥ normal range");
        // A text area keeps its two lines: the second is printed below the first, not run on after it.
        assertThat(flat).contains("FAKE-concern one: fatigue since August", "FAKE-concern two: knee pain, left side");
        assertThat(topOf(result, "FAKE-concern two")).isGreaterThan(topOf(result, "FAKE-concern one") + 5);
        assertThat(flat).contains("FAKE-plan line one", "FAKE-plan line two (follow up in 3 months)");
        assertThat(topOf(result, "FAKE-plan line two")).isGreaterThan(topOf(result, "FAKE-plan line one") + 5);
        // The buttons of the top bar do not print; the link text does.
        assertThat(flat).doesNotContain("Save and Exit", "Print Page").contains("Annual Review Planner");
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"annual/female.html", "annual/male.html"})
    @DisplayName("should lay the Annual form out as the form does: label columns at their natural width")
    void shouldKeepLabelColumns_atNaturalWidthOnTheAnnualForm(String fixture) throws Exception {
        String text = flat(render(fixture));

        // Vertical strips of letters ("S m o k i n g") were the symptom of a table squeezed to one-character columns.
        assertThat(text).contains("Smoking:", "Alcohol:", "Head & Neck:", "Dental Hygiene:");
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"annual/female.html", "annual/male.html"})
    @DisplayName("should start the Annual form at the 10 mm margin, as wkhtmltopdf did: its body says leftmargin=0 and topmargin=0")
    void shouldStartAnnualForm_atTheTenMillimetreMargin(String fixture) throws Exception {
        Rectangle ink = TestPdfs.inkBounds(TestPdfs.image(render(fixture).pdf(), 1));

        // Without the legacy body margin attributes the form started at 32.8 pt, the 8 px default body margin further in.
        assertThat((double) ink.x).isCloseTo(28.35, within(1.0));
        assertThat((double) ink.y).isCloseTo(28.35, within(1.5));
        // And, with the same margin on the right, it ends where the printable area does.
        assertThat((double) (ink.x + ink.width)).isCloseTo(612 - 28.35, within(1.5));
    }

    // ---- scripts and fonts ------------------------------------------------------------------

    @Test
    @DisplayName("should print accents, symbols, Chinese, Hebrew, Arabic and Vietnamese, and U+FFFD for Hindi, Punjabi, Tamil and Thai")
    void shouldPrintScripts_ofManyLanguages() throws Exception {
        HtmlPdfRenderResult result = render("scripts/all-scripts.html");
        String text = text(result);

        assertThat(text).contains("Café déjà vu", "≥ 5", "✓ ✔ ☑", "中文 日本語 한국어");
        assertThat(text).contains("שלום עולם", "مرحبا بالعالم", "اردو زبان");
        assertThat(text).contains("Tiếng Việt").contains("𝄞");
        for (String script : List.of("Hindi", "Punjabi", "Tamil", "Thai")) {
            assertThat(text).as(script).containsPattern(script + " �+");
        }
        assertThat(TestPdfs.fonts(result.pdf())).containsExactlyInAnyOrder("DejaVuSans", "WenQuanYiMicroHei", "Symbola");
        // A long Hebrew line makes the document note ask the reader to check it; that is reported, not an error.
        assertThat(result.degradations()).containsExactly(HtmlPdfRenderDegradation.RTL_BEST_EFFORT);
        assertThat(flat(result)).startsWith(PrintMessages.noteFor(HtmlPdfRenderDegradation.RTL_BEST_EFFORT).orElseThrow());
    }

    // ---- geometry ---------------------------------------------------------------------------

    @ParameterizedTest(name = "{0} px")
    @CsvSource({"400, 0.8", "850, 0.8", "1000, 0.74", "1400, 0.529"})
    @DisplayName("should put a page of any width on Letter, its content from the left margin, with the zoom the width calls for")
    void shouldPlaceWidePages_onLetter(int width, float zoom) throws Exception {
        HtmlPdfRenderResult result = render("geometry/wide-" + width + ".html");

        assertEveryPageIsLetter(result);
        assertThat(result.degradations()).isEmpty();
        assertThat(TestPdfs.zoomOf(result.pdf())).isCloseTo(zoom, within(0.002f));
        Rectangle bar = TestPdfs.darkBounds(TestPdfs.image(result.pdf(), 1));
        assertThat(bar.x).isBetween(27, 29);
        assertThat(bar.y).isBetween(27, 29);
        // The bar is 'width' CSS pixels wide: that many 0.75 points, times the zoom.
        assertThat((double) bar.width).isCloseTo(width * 0.75 * zoom, within(1.5));
    }

    @Test
    @DisplayName("should shrink a narrow first page that is followed by a wide one, and keep both pages complete")
    void shouldShrinkNarrowFirstPage_whenALaterPageIsWide() throws Exception {
        HtmlPdfRenderResult result = render("geometry/narrow-then-wide.html");

        assertThat(TestPdfs.pageSizes(result.pdf())).hasSize(2);
        assertEveryPageIsLetter(result);
        assertThat(TestPdfs.zoomOf(result.pdf())).isLessThan(0.8f);
        assertThat(TestPdfs.pageTexts(result.pdf()).get(0)).contains("FAKE-narrow first page");
        assertThat(TestPdfs.pageTexts(result.pdf()).get(1)).contains("FAKE-wide second page");
    }

    @Test
    @DisplayName("should ignore the page size and margins the author's @page rule asks for")
    void shouldIgnoreAuthorPageRule_forSizeAndMargins() throws Exception {
        HtmlPdfRenderResult result = render("geometry/author-page.html");

        assertThat(TestPdfs.pageSizes(result.pdf())).singleElement().satisfies(size -> assertThat(size).containsExactly(612f, 792f));
        assertThat(TestPdfs.darkBounds(TestPdfs.image(result.pdf(), 1)).x).isBetween(27, 29);
    }

    @Test
    @DisplayName("should print 8 px text at 6 points, the 10 px minimum scaled by the default zoom, and 12 px text at 7.2")
    void shouldPrintSmallText_atTheMinimum() throws Exception {
        HtmlPdfRenderResult result = render("geometry/small-text.html");

        List<TextPosition> glyphs = TestPdfs.glyphs(result.pdf());
        float tinyTop = topOf(result, "FAKE-tiny");
        float twelveTop = topOf(result, "FAKE-twelve");
        float tiny = glyphs.stream().filter(g -> Math.abs(g.getYDirAdj() - tinyTop) < 1).findFirst().map(TestPdfs::sizeOnPage).orElseThrow();
        float twelve = glyphs.stream().filter(g -> Math.abs(g.getYDirAdj() - twelveTop) < 1).findFirst().map(TestPdfs::sizeOnPage).orElseThrow();
        assertThat(tiny).isCloseTo(6f, within(0.1f));
        assertThat(twelve).isCloseTo(7.2f, within(0.1f));
    }

    @Test
    @DisplayName("should apply print media rules and not screen media rules")
    void shouldApplyPrintMediaRules_andNotScreenOnes() throws Exception {
        HtmlPdfRenderResult result = render("geometry/print-screen-media.html");

        assertThat(TestPdfs.glyphs(result.pdf())).isNotEmpty()
                .allSatisfy(glyph -> assertThat(TestPdfs.sizeOnPage(glyph)).isCloseTo(30f * 0.75f * 0.8f, within(0.05f)));
    }

    @Test
    @DisplayName("should keep the default zoom for a 600-character URL, in a paragraph and in a table cell, and break it")
    void shouldKeepDefaultZoom_forLongUrls() throws Exception {
        HtmlPdfRenderResult result = render("geometry/long-url.html");

        assertThat(TestPdfs.zoomOf(result.pdf())).isCloseTo(0.8f, within(0.001f));
        assertThat(text(result)).contains("aaaaaaaaaaaaaaaa", "bbbbbbbbbbbbbbbb", "FAKE-details");
        assertThat(result.degradations()).isEmpty();
    }

    @Test
    @DisplayName("should break a long run of characters but keep the words of a table beside it whole, though word-wrap is inherited")
    void shouldKeepWordsWhole_inATableBesideALongRun() throws Exception {
        HtmlPdfRenderResult result = render("geometry/long-run-beside-table.html");

        String lines = String.join(" ", TestPdfs.pageTexts(result.pdf()));
        assertThat(lines).contains("Smoking", "history", "Alcohol", "consumption", "Medications", "taken");
        assertThat(result.degradations()).isEmpty();
    }

    @Test
    @DisplayName("should repeat the header of a table on every page it runs over")
    void shouldRepeatTableHeader_onEveryPage() throws Exception {
        HtmlPdfRenderResult result = render("geometry/thead-pages.html");

        List<String> pages = TestPdfs.pageTexts(result.pdf());
        assertThat(pages).hasSizeGreaterThanOrEqualTo(3);
        assertThat(pages).allSatisfy(page -> assertThat(page).contains("FAKE-Header A", "FAKE-Header B"));
        assertThat(String.join("\n", pages)).contains("FAKE-row 1", "FAKE-row 150");
    }

    @Test
    @DisplayName("should print an element with position: fixed once, not on every page")
    void shouldPrintFixedElement_once() throws Exception {
        HtmlPdfRenderResult result = render("geometry/position-fixed.html");

        assertThat(TestPdfs.pageSizes(result.pdf())).hasSizeGreaterThanOrEqualTo(2);
        assertThat(text(result).split("FAKE-fixed banner", -1)).hasSize(2);
        assertThat(text(result)).contains("FAKE-paragraph 1 ", "FAKE-paragraph 120 ");
    }

    @Test
    @DisplayName("should print soft hyphens and bidi marks as nothing")
    void shouldDropInvisibleCharacters_fromThePrintedText() throws Exception {
        String text = text(render("geometry/soft-hyphens.html"));

        assertThat(text).contains("Softhyphenated", "invisible", "left", "right");
        assertThat(text.chars().filter(c -> c == 0xAD || c == 0x200E || c == 0x200F)).isEmpty();
    }

    @Test
    @DisplayName("should number ordered lists in the style their type asks for")
    void shouldNumberOrderedLists_inTheStyleTheirTypeAsksFor() throws Exception {
        String flat = flat(render("geometry/ol-type.html"));

        assertThat(flat).contains("a. alpha one", "b. alpha two", "A. upper one", "i. roman one", "ii. roman two", "I. upper roman", "5. five");
    }

    @Test
    @DisplayName("should clamp a row span to the rows it has, so nothing overprints the text that follows the table")
    void shouldNotOverlap_afterRowSpans() throws Exception {
        HtmlPdfRenderResult result = render("geometry/long-rowspan.html");

        assertThat(topOf(result, "row 3")).isLessThan(topOf(result, "FAKE-text after the table"));
        assertThat(topOf(result, "FAKE-text after the table")).isLessThan(topOf(result, "FAKE-head span"));
        assertThat(topOf(result, "head c")).isLessThan(topOf(result, "body 1"));
        assertThat(topOf(result, "body 1")).isLessThan(topOf(result, "FAKE-text after the second table"));
    }

    // ---- edge markup ------------------------------------------------------------------------

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "edge/empty-elements.html|FAKE-only text",
            "edge/self-closing-source.html|FAKE-before after the break [image unavailable] FAKE-value FAKE-end",
            "edge/misnested.html|FAKE-bold italic italic only FAKE-paragraph FAKE-div in paragraph FAKE-cell without row FAKE-second FAKE-one FAKE-two FAKE-unclosed paragraph FAKE-another",
            "edge/fragment-only.html|FAKE-just a fragment with a second paragraph and bold text",
            "edge/text-only.html|FAKE-Just some plain text with no tags at all. Second line of plain text."})
    @DisplayName("should print the text of unusual markup: empty and self-closing elements, misnesting, a fragment, plain text")
    void shouldPrintEdgeMarkup_forUnusualInput(String fixture, String expected) throws Exception {
        HtmlPdfRenderResult result = render(fixture);

        assertThat(TestPdfs.pageSizes(result.pdf())).hasSize(1);
        assertEveryPageIsLetter(result);
        assertThat(flat(result)).contains(expected);
        assertThat(result.degradations()).allMatch(d -> d.logLevel() != Level.ERROR);
    }

    @Test
    @DisplayName("should print the character an entity stands for, and an unknown entity as it was written")
    void shouldPrintEntities_asTheCharactersTheyStandFor() throws Exception {
        String flat = flat(render("edge/entities.html"));

        assertThat(flat).contains("copy:©", "emoji:😀", "A:A", "unknown:&unknown;", "amp:&amp;", "tag:<tag>", "bare & ampersand",
                "quote:\"x\"");
    }

    // ---- images -----------------------------------------------------------------------------

    @Test
    @DisplayName("should draw the images it can, convert what OpenPDF cannot embed, and box the rest with the reason logged once")
    void shouldDrawConvertAndBoxImages_ofTheImagesReport() throws Exception {
        String html = HtmlPdfFixtures.imagesReport(webRoot);

        HtmlPdfRenderResult result = SERVICE.render(HtmlPdfFixtures.request(html, webRoot));

        String flat = flat(result);
        long boxes = flat.split("\\[image unavailable\\]", -1).length - 1;
        assertThat(result.degradations()).containsExactlyInAnyOrder(HtmlPdfRenderDegradation.IMAGE_CONVERTED,
                HtmlPdfRenderDegradation.IMAGE_UNAVAILABLE, HtmlPdfRenderDegradation.SVG_REFERENCES_REMOVED);
        // Remote, missing, corrupt header, corrupt body (a PNG ImageIO cannot read half of), WebP, HEIC, a PDF and no
        // source at all: eight boxes. A truncated JPEG is read by ImageIO's tolerant reader and converted.
        assertThat(boxes).isEqualTo(8);
        // Drawn: photo, scan, logo three times, bitmap, wireless bitmap, GIF, inline data, truncated JPEG, phone
        // photo and the cleaned SVG.
        assertThat(TestPdfs.imageDraws(result.pdf())).isGreaterThanOrEqualTo(11);
        assertThat(flat).contains("End of the images.");
        assertEveryPageIsLetter(result);
    }

    @Test
    @DisplayName("should fail with a PDFGenerationException for white space and print one blank page for no visible content")
    void shouldFailOrPrintBlankPage_forEmptyInput() throws Exception {
        assertThatExceptionOfType(PDFGenerationException.class).isThrownBy(() -> SERVICE.render(HtmlPdfFixtures.request("  \n ", webRoot)));

        HtmlPdfRenderResult blank = SERVICE.render(HtmlPdfFixtures.request("<html><body></body></html>", webRoot));
        assertThat(TestPdfs.pageSizes(blank.pdf())).hasSize(1);
        assertThat(blank.degradations()).isEmpty();
        assertThat(new String(blank.pdf(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }
}
