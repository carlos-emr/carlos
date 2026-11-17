/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.lab.ca.all.pageUtil;

import java.io.ByteArrayOutputStream;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.PATHL7Handler;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
class LabPdfUnicodeIntegrationTest {
    @Test
    void shouldPreserveUnicodeResults_whenPrintingParsedHl7() throws Exception {
        var handler = new PATHL7Handler();
        handler.init(String.join("\r",
                "MSH|^~\\&|PATHL7|CW|CARLOS|TEST|20261003100000||ORU^R01|UNICODE4173|P|2.3|||ER|AL",
                "PID||9999999999|9999999999||Łukasz^Nguyễn||19800101|F",
                "ORC|RE||UNICODE4173|||||||||99999^FAKE^DOC",
                "OBR|1||UNICODE4173|CHEM^Chemistry||20261003100000|20261003100000|||||||20261003100000||99999^FAKE^DOC||||||20261003100000||CHEM4|F|||99999^FAKE^DOC",
                "OBX|1|ST|XXX-0002^Interpretation||Value ≥ 5 µg/L and ≤ 9, Łódź İstanbul||||||F|||20261003100000"));
        var output = new ByteArrayOutputStream();
        var creator = new LabPDFCreator();
        creator.setOs(output);
        creator.setHandler(handler);
        creator.printPdf();
        try (var pdf = Loader.loadPDF(output.toByteArray())) {
            for (var page : pdf.getPages()) {
                for (var name : page.getResources().getFontNames()) {
                    assertThat(page.getResources().getFont(name).isEmbedded()).as("font %s", name).isTrue();
                }
            }
            // Results wrap within table cells; reflow whitespace without altering glyphs.
            assertThat(new PDFTextStripper().getText(pdf).replaceAll("\\s+", " "))
                    .contains("Łukasz", "Nguyễn", "≥ 5 µg/L and ≤ 9, Łódź İstanbul");
        }
    }

    @Test
    void shouldPreserveChineseJapaneseAndKorean_onEveryPageOfParsedHl7() throws Exception {
        // Issue #5046: lab cells were Phrases in DejaVu alone, and the patient name heading pages 2+
        // went through PdfContentByte.showTextAligned with one base font, so CJK characters were
        // dropped from an otherwise valid printout. Sixty result rows force a second page.
        StringBuilder message = new StringBuilder(String.join("\r",
                "MSH|^~\\&|PATHL7|CW|CARLOS|TEST|20261003100000||ORU^R01|CJK5046|P|2.3|||ER|AL",
                "PID||9999999999|9999999999||FAKE-陈^小明||19800101|F",
                "ORC|RE||CJK5046|||||||||99999^FAKE-김^민준",
                "OBR|1||CJK5046|CHEM^化学检查||20261003100000|20261003100000|||||||20261003100000||99999^FAKE-김^民준||||||20261003100000||CHEM4|F|||99999^FAKE-김^민준"));
        for (int row = 1; row <= 60; row++) {
            message.append("\rOBX|").append(row).append("|ST|XXX-").append(row)
                    .append("^FAKE-血糖 ").append(row).append("||さくら ").append(row).append(" ≥ 5|mmol/L|||||F|||20261003100000");
        }
        var handler = new PATHL7Handler();
        handler.init(message.toString());
        var output = new ByteArrayOutputStream();
        var creator = new LabPDFCreator();
        creator.setOs(output);
        creator.setHandler(handler);
        creator.printPdf();
        try (var pdf = Loader.loadPDF(output.toByteArray())) {
            assertThat(pdf.getNumberOfPages()).isGreaterThan(1);
            for (var page : pdf.getPages()) {
                for (var name : page.getResources().getFontNames()) {
                    assertThat(page.getResources().getFont(name).isEmbedded()).as("font %s", name).isTrue();
                }
            }
            String text = new PDFTextStripper().getText(pdf).replaceAll("\\s+", " ");
            assertThat(text).contains("FAKE-陈", "小明", "化学检查", "FAKE-血糖 1", "さくら 60 ≥ 5", "FAKE-김");
            var stripper = new PDFTextStripper();
            stripper.setStartPage(2);
            stripper.setEndPage(2);
            // Page 2 is headed with the patient's name.
            assertThat(stripper.getText(pdf)).contains("FAKE-陈");
        }
    }
}
