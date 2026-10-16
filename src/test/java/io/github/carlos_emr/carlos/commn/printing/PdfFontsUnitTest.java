/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.commn.printing;

import java.io.ByteArrayOutputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openpdf.text.Document;
import org.openpdf.text.Font;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.PdfWriter;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
class PdfFontsUnitTest {
    private static final String TEXT = "Nguyễn Thị Hương Łukasz Żółć İstanbul şğı ≥ 5 ≤ 9 µg/L";

    @ParameterizedTest
    @ValueSource(strings = {"Helvetica", "Helvetica-Bold", "Helvetica-Oblique", "Helvetica-BoldOblique",
            "Times-Roman", "Times-Bold", "Times-Italic", "Times-BoldItalic",
            "Courier", "Courier-Bold", "Courier-Oblique", "Courier-BoldOblique"})
    void shouldEmbedAndPreserveClinicalText_whenUsingStandardLatinFamilies(String family) throws Exception {
        var base = PdfFonts.createFont(family, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
        var output = new ByteArrayOutputStream();
        var document = new Document();
        PdfWriter.getInstance(document, output);
        document.open();
        document.add(new Paragraph(TEXT, new Font(base, 10)));
        document.close();
        try (var pdf = Loader.loadPDF(output.toByteArray())) {
            assertThat(new PDFTextStripper().getText(pdf).strip()).isEqualTo(TEXT);
            for (var name : pdf.getPage(0).getResources().getFontNames()) {
                assertThat(pdf.getPage(0).getResources().getFont(name).isEmbedded()).isTrue();
            }
        }
    }

    @Test
    void shouldDrawCjkAndEmoji_withBundledFallbackFaces() throws Exception {
        // Chinese, Japanese kana, Korean Hangul, Latin with accents, and emoji from Unicode 6 to 9.
        String text = "FAKE-陈小明 さくら 김민준 Nguyễn – tolerated well 👍 ❤ 😀 🤒";

        try (var pdf = Loader.loadPDF(render(text))) {
            assertThat(new PDFTextStripper().getText(pdf).strip()).isEqualTo(text);
            for (var name : pdf.getPage(0).getResources().getFontNames()) {
                assertThat(pdf.getPage(0).getResources().getFont(name).isEmbedded()).isTrue();
            }
        }
    }

    @Test
    void shouldMarkUndrawableCharacters_withReplacementCharacter() throws Exception {
        // A private-use character and an emoji newer than the bundled emoji face: no face draws
        // them, so the reader sees a mark where they were instead of text silently closing up.
        try (var pdf = Loader.loadPDF(render("Lot \uE000 7 \uD83E\uDDEA"))) {
            assertThat(new PDFTextStripper().getText(pdf).strip()).isEqualTo("Lot \uFFFD 7 \uFFFD");
        }
    }

    @Test
    void shouldDropInvisibleFormatCharacters_whenNoFaceDrawsThem() throws Exception {
        // Tag characters (subdivision flags) have no glyph anywhere and nothing to show.
        try (var pdf = Loader.loadPDF(render("Flag \uDB40\uDC67\uDB40\uDC62 done"))) {
            assertThat(new PDFTextStripper().getText(pdf).strip()).isEqualTo("Flag  done");
        }
    }

    @Test
    void shouldAddNothing_whenTextIsNullOrEmpty() {
        var phrase = new Phrase();
        var font = PdfFonts.getFont(BaseFont.HELVETICA, 10);

        PdfFonts.addText(phrase, null, font);
        PdfFonts.addText(phrase, "", font);

        assertThat(phrase).isEmpty();
    }

    @Test
    void shouldRetainSymbolEncoding_whenUsingSymbolFont() throws Exception {
        var font = PdfFonts.createFont(BaseFont.ZAPFDINGBATS, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
        assertThat(font.getPostscriptFontName()).isEqualTo(BaseFont.ZAPFDINGBATS);
        assertThat(font.getEncoding()).isEqualTo(BaseFont.CP1252);
    }

    private static byte[] render(String text) throws Exception {
        var output = new ByteArrayOutputStream();
        var document = new Document();
        PdfWriter.getInstance(document, output);
        document.open();
        var paragraph = new Paragraph();
        PdfFonts.addText(paragraph, text, PdfFonts.getFont(BaseFont.HELVETICA, 10));
        document.add(paragraph);
        document.close();
        return output.toByteArray();
    }

    @Test
    void shouldRejectMissingFont_whenCustomResourceIsUnavailable() {
        assertThatThrownBy(() -> PdfFonts.createFont("missing-clinical-font.ttf", BaseFont.IDENTITY_H, true))
                .isInstanceOf(java.io.IOException.class);
    }
}
