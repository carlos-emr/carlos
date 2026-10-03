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
    void shouldRetainSymbolEncoding_whenUsingSymbolFont() throws Exception {
        var font = PdfFonts.createFont(BaseFont.ZAPFDINGBATS, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
        assertThat(font.getPostscriptFontName()).isEqualTo(BaseFont.ZAPFDINGBATS);
        assertThat(font.getEncoding()).isEqualTo(BaseFont.CP1252);
    }

    @Test
    void shouldRejectMissingFont_whenCustomResourceIsUnavailable() {
        assertThatThrownBy(() -> PdfFonts.createFont("missing-clinical-font.ttf", BaseFont.IDENTITY_H, true))
                .isInstanceOf(java.io.IOException.class);
    }
}
