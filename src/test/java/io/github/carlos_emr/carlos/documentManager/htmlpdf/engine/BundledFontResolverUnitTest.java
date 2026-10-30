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

import io.github.carlos_emr.carlos.documentManager.htmlpdf.TestFonts;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.engine.BundledFontSet.Face;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Unit tests for {@link BundledFontResolver}: renders small documents through Flying Saucer with
 * the resolver and reads the fonts, sizes and text matrices back out of the PDF.
 */
@Tag("unit")
@Tag("htmlpdf")
@Tag("pdf")
@DisplayName("BundledFontResolver Unit Tests")
class BundledFontResolverUnitTest {

    /**
     * U+1F600: drawn by Symbola (and by DejaVu Sans), and with an advance width. (U+1D11E, the G clef,
     * has no advance in Symbola, and a line of nothing but zero-width text is not drawn at all.)
     */
    private static final String EMOJI = new String(Character.toChars(0x1F600));

    @TempDir
    Path directory;

    private BundledFontSet fonts;

    @BeforeEach
    void loadFonts() throws Exception {
        fonts = TestFonts.extractTo(directory);
    }

    // ---- families ---------------------------------------------------------------------------

    @Test
    @DisplayName("should print every requested family in the one embedded DejaVu Sans face")
    void shouldUseCarlosSans_forEveryFamily() throws Exception {
        byte[] pdf = render(fonts, """
                <p style="font-family: serif">serif</p>
                <p style="font-family: monospace">monospace</p>
                <p style="font-family: 'Times New Roman'">times</p>
                <p style="font-family: 'Courier New', monospace">courier</p>
                <p style="font-family: Arial, sans-serif">arial</p>
                <p style="font-family: cursive">cursive</p>
                <p style="font-family: no-such-family-anywhere">unknown</p>
                <p style="font-family: 'carlos sans'">registered name, lower case</p>
                <p>no family at all</p>
                <pre>preformatted</pre>
                <code>code</code>
                """);

        List<PdfFont> used = fontsOf(pdf);

        assertThat(used).isNotEmpty();
        assertThat(used).allSatisfy(font -> {
            assertThat(font.name()).isEqualTo("DejaVuSans");
            assertThat(font.embedded()).isTrue();
        });
    }

    @Test
    @DisplayName("should use the first family of the list to decide the face")
    void shouldMapByFirstFamily_whenListHasSeveral() throws Exception {
        byte[] cjkFirst = render(fonts, "<p style=\"font-family: 'CARLOS CJK', serif\">中文</p>");
        byte[] serifFirst = render(fonts, "<p style=\"font-family: serif, 'CARLOS CJK'\">中文</p>");

        assertThat(namesOf(cjkFirst)).anyMatch(name -> name.contains("WenQuanYi"));
        assertThat(namesOf(serifFirst)).noneMatch(name -> name.contains("WenQuanYi"));
    }

    @Test
    @DisplayName("should offer only the bundled family, never Flying Saucer's built-in fonts")
    void shouldOfferOnlyBundledFamily_beforeAnyFallback() {
        BundledFontResolver resolver = new BundledFontResolver(fonts);

        assertThat(resolver.getFonts().keySet()).containsExactly(BundledFontSet.FAMILY_SANS);
    }

    // ---- weight and style -------------------------------------------------------------------

    @Test
    @DisplayName("should print bold text in the bold face and the rest in the regular face")
    void shouldUseBoldFace_forBoldText() throws Exception {
        byte[] bold = render(fonts, "<p><b>bold</b><strong>strong</strong></p><h3>heading</h3>"
                + "<p style=\"font-weight: 700\">seven hundred</p>");
        byte[] mixed = render(fonts, "<p><b>bold</b> regular</p>");

        assertThat(namesOf(bold)).allMatch(name -> name.equals("DejaVuSans-Bold"));
        assertThat(namesOf(mixed)).hasSize(2)
                .anyMatch(name -> name.equals("DejaVuSans-Bold"))
                .anyMatch(name -> name.equals("DejaVuSans"));
    }

    @Test
    @DisplayName("should slant the regular face for italic text instead of using an oblique face")
    void shouldSlantRegularFace_forItalicText() throws Exception {
        String text = "MMMM iiii WWWW «» æœ ľ";
        byte[] italic = render(fonts, "<p><i>" + text + "</i></p>");
        byte[] upright = render(fonts, "<p>" + text + "</p>");

        assertThat(namesOf(italic)).isEqualTo(namesOf(upright));
        assertThat(namesOf(italic)).allMatch(name -> name.equals("DejaVuSans"));
        assertThat(textMatrixSkews(italic)).anyMatch(skew -> skew > 0.1f && skew < 0.4f);
        assertThat(textMatrixSkews(upright)).allMatch(skew -> skew == 0f);
        // The regular face's widths are kept, so line breaks do not move.
        assertThat(textWidth(italic)).isEqualTo(textWidth(upright), Offset.offset(0.01f));
    }

    @Test
    @DisplayName("should slant the bold face for bold italic text")
    void shouldSlantBoldFace_forBoldItalicText() throws Exception {
        byte[] pdf = render(fonts, "<p><b><i>bold italic</i></b></p>");

        assertThat(namesOf(pdf)).allMatch(name -> name.equals("DejaVuSans-Bold"));
        assertThat(textMatrixSkews(pdf)).anyMatch(skew -> skew > 0.1f);
    }

    // ---- size -------------------------------------------------------------------------------

    @Test
    @DisplayName("should raise text below 10 CSS pixels to 10 pixels, which prints at 7.5 points")
    void shouldRaiseTinyText_toMinimumSize() throws Exception {
        byte[] pdf = render(fonts, """
                <p style="font-size: 8px">eight</p>
                <p style="font-size: 9px">nine</p>
                <p style="font-size: 10px">ten</p>
                <p style="font-size: 14px">fourteen</p>
                """);

        assertThat(fontSizesOf(pdf)).containsExactly(7.5f, 10.5f);
    }

    // ---- fallback families ------------------------------------------------------------------

    @Test
    @DisplayName("should print the fallback families in WenQuanYi Micro Hei and Symbola when requested")
    void shouldUseFallbackFamilies_whenRequested() throws Exception {
        byte[] cjk = render(fonts, "<p style=\"font-family: 'CARLOS CJK'\">中文</p>");
        byte[] symbol = render(fonts, "<p style=\"font-family: 'CARLOS Symbol'\">" + EMOJI + "</p>");

        assertThat(namesOf(cjk)).hasSize(1).allMatch(name -> name.contains("WenQuanYi"));
        assertThat(namesOf(symbol)).hasSize(1).allMatch(name -> name.contains("Symbola"));
        assertThat(fontsOf(cjk)).allMatch(PdfFont::embedded);
        assertThat(fontsOf(symbol)).allMatch(PdfFont::embedded);
    }

    @Test
    @DisplayName("should match the fallback family names ignoring case, quotes and surrounding spaces")
    void shouldMatchFallbackNames_ignoringCaseAndQuotes() throws Exception {
        byte[] pdf = render(fonts, "<p style=\"font-family: ' carlos cjk '\">中</p>"
                + "<p style=\"font-family: &quot;CARLOS SYMBOL&quot;\">" + EMOJI + "</p>");

        assertThat(namesOf(pdf)).anyMatch(name -> name.contains("WenQuanYi"))
                .anyMatch(name -> name.contains("Symbola"));
    }

    @Test
    @DisplayName("should register a fallback face only when the first request for it arrives")
    void shouldRegisterFallback_onlyWhenFirstRequested() throws Exception {
        BundledFontResolver plain = new BundledFontResolver(fonts);
        render(plain, "<p>only DejaVu Sans</p>");
        BundledFontResolver cjk = new BundledFontResolver(fonts);
        render(cjk, "<p style=\"font-family: 'CARLOS CJK'\">中</p>");

        assertThat(plain.getFonts().keySet()).containsExactly(BundledFontSet.FAMILY_SANS);
        assertThat(cjk.getFonts().keySet()).containsExactlyInAnyOrder(BundledFontSet.FAMILY_SANS,
                BundledFontSet.FAMILY_CJK);
    }

    @Test
    @DisplayName("should print in DejaVu Sans when a requested fallback face is not available")
    void shouldFallBackToSans_whenFallbackFaceUnavailable() throws Exception {
        BundledFontSet withoutCjk = BundledFontSet.load(directory.resolve("DejaVuSans.ttf"),
                directory.resolve("DejaVuSans-Bold.ttf"), null, directory.resolve("Symbola_hint.ttf"));
        BundledFontResolver resolver = new BundledFontResolver(withoutCjk);

        byte[] pdf = render(resolver, "<p style=\"font-family: 'CARLOS CJK'\">中文</p>");

        assertThat(withoutCjk.isAvailable(Face.CJK)).isFalse();
        assertThat(namesOf(pdf)).allMatch(name -> name.equals("DejaVuSans"));
        assertThat(resolver.getFonts().keySet()).containsExactly(BundledFontSet.FAMILY_SANS);
    }

    // ---- construction -----------------------------------------------------------------------

    @Test
    @DisplayName("should reject a null font set")
    void shouldRejectNull_whenFontSetMissing() {
        assertThatNullPointerException()
                .isThrownBy(() -> new BundledFontResolver(null));
    }

    // ---- helpers ----------------------------------------------------------------------------

    private byte[] render(BundledFontSet set, String body) throws Exception {
        return render(new BundledFontResolver(set), body);
    }

    private byte[] render(BundledFontResolver resolver, String body) throws Exception {
        String xhtml = "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>t</title></head><body>"
                + body + "</body></html>";
        ITextRenderer renderer = LocalOnlyUserAgent.createRestrictedRenderer(List.of(directory), resolver);
        renderer.setDocumentFromString(xhtml);
        renderer.layout();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        renderer.createPDF(out);
        return out.toByteArray();
    }

    private record PdfFont(String name, boolean embedded) {
    }

    private static List<PdfFont> fontsOf(byte[] pdf) throws IOException {
        List<PdfFont> fonts = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            for (PDPage page : document.getPages()) {
                PDResources resources = page.getResources();
                for (COSName name : resources.getFontNames()) {
                    PDFont font = resources.getFont(name);
                    fonts.add(new PdfFont(withoutSubsetTag(font.getName()), font.isEmbedded()));
                }
            }
        }
        return fonts;
    }

    /** An embedded subset is named {@code ABCDEF+FontName}; the tag differs from document to document. */
    private static String withoutSubsetTag(String name) {
        assertThat(name).matches("[A-Z]{6}\\+.+");
        return name.substring(name.indexOf('+') + 1);
    }

    private static Set<String> namesOf(byte[] pdf) throws IOException {
        Set<String> names = new TreeSet<>();
        for (PdfFont font : fontsOf(pdf)) {
            names.add(font.name());
        }
        return names;
    }

    /** The operands of every operator with the given name, in content-stream order. */
    private static List<List<Object>> operandsOf(byte[] pdf, String operatorName) throws IOException {
        List<List<Object>> found = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            for (PDPage page : document.getPages()) {
                List<Object> operands = new ArrayList<>();
                for (Object token : new PDFStreamParser(page).parse()) {
                    if (token instanceof Operator operator) {
                        if (operator.getName().equals(operatorName)) {
                            found.add(new ArrayList<>(operands));
                        }
                        operands.clear();
                    } else {
                        operands.add(token);
                    }
                }
            }
        }
        return found;
    }

    private static float number(Object operand) {
        return ((COSNumber) operand).floatValue();
    }

    /** The distinct font sizes, in points, of every {@code Tf} operator. */
    private static Set<Float> fontSizesOf(byte[] pdf) throws IOException {
        Set<Float> sizes = new TreeSet<>();
        for (List<Object> operands : operandsOf(pdf, "Tf")) {
            sizes.add(number(operands.get(1)));
        }
        return sizes;
    }

    /** The horizontal skew (third entry) of every {@code Tm} text matrix. */
    private static List<Float> textMatrixSkews(byte[] pdf) throws IOException {
        List<Float> skews = new ArrayList<>();
        for (List<Object> operands : operandsOf(pdf, "Tm")) {
            skews.add(number(operands.get(2)));
        }
        return skews;
    }

    /** The width, in points, of the text on the first page: from its first glyph to the end of its last. */
    private static float textWidth(byte[] pdf) throws IOException {
        List<TextPosition> positions = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
                    positions.addAll(textPositions);
                    super.writeString(text, textPositions);
                }
            };
            stripper.getText(document);
        }
        float start = Float.MAX_VALUE;
        float end = 0f;
        for (TextPosition position : positions) {
            start = Math.min(start, position.getX());
            end = Math.max(end, position.getX() + position.getWidth());
        }
        return end - start;
    }
}
