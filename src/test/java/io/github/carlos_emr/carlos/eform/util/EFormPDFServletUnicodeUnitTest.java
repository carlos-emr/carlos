/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.eform.util;

import java.io.ByteArrayOutputStream;
import java.util.Properties;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openpdf.text.Document;
import org.openpdf.text.PageSize;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Draws eForm print-configuration overlays through the real {@code EFormPDFServlet.writeContent}
 * and checks they keep text in any script (issue #5046). Static {@code __} text and field values
 * were drawn with {@code PdfContentByte.showTextAligned} in one base font, and boxed fields as a
 * DejaVu-only Phrase, so Chinese, Japanese and Korean characters were left out. Values are synthetic.
 */
@Tag("unit")
@Tag("report")
class EFormPDFServletUnicodeUnitTest {
    @Test
    @DisplayName("should keep CJK text in eForm fields, static text and boxes, and check marks as symbols")
    void shouldPrintEveryOverlayKind_inChineseJapaneseAndKorean() throws Exception {
        Properties config = new Properties();
        config.setProperty("patientName", "left, 50, 100, 0, BaseFont.HELVETICA, 12");
        config.setProperty("__clinic", "left, 50, 130, 0, BaseFont.HELVETICA, 10, FAKE-诊所 Łukasz");
        config.setProperty("notes", "left, 50, 200, 0, BaseFont.HELVETICA, 10, _, 400, 260, 12");
        config.setProperty("consent", "left, 300, 100, 0, BaseFont.ZAPFDINGBATS, 12, \u2714");
        Properties values = new Properties();
        values.setProperty("patientName", "FAKE-陈 小明");
        values.setProperty("notes", "FAKE-さくら 김민준 ≥5");
        values.setProperty("consent", "X");

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Document document = new Document(PageSize.LETTER);
        PdfWriter writer = PdfWriter.getInstance(document, output);
        document.open();
        writer.setPageEmpty(false);
        ReflectionTestUtils.invokeMethod(new EFormPDFServlet(), "writeContent", config, values, new Properties(),
                PageSize.LETTER.getHeight(), writer.getDirectContent());
        document.close();

        try (var pdf = Loader.loadPDF(output.toByteArray())) {
            assertThat(new PDFTextStripper().getText(pdf))
                    .contains("FAKE-陈 小明", "FAKE-诊所 Łukasz", "FAKE-さくら 김민준 ≥5", "✔");
            for (var name : pdf.getPage(0).getResources().getFontNames()) {
                var font = pdf.getPage(0).getResources().getFont(name);
                assertThat(font.isEmbedded() || font.getName().contains("ZapfDingbats")).as("%s embedded", font.getName()).isTrue();
            }
        }
    }
}
