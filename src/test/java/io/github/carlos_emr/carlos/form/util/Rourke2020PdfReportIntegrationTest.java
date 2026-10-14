/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.form.util;

import java.awt.geom.Rectangle2D;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import io.github.carlos_emr.carlos.form.model.FormRourke2020;

@Tag("integration")
@Tag("report")
class Rourke2020PdfReportIntegrationTest {
    private static Properties record(String patient) {
        Properties values = new Properties();
        values.setProperty("provider_no", "999998");
        values.setProperty("demographic_no", "1");
        values.setProperty("formCreated", "14/10/2026");
        values.setProperty("c_pName", patient);
        values.setProperty("c_birthDate", "01/01/2026");
        values.setProperty("c_male", "x");
        values.setProperty("p1_date1w", "08/01/2026");
        Properties result = new FormRourke2020(values).toProperties();
        result.setProperty("c_gestationalAge", "40 weeks");
        return result;
    }

    private static byte[] print(Properties values, List<Integer> pages) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Rourke2020PdfReport.write(output, values, pages);
        return output.toByteArray();
    }

    @Test
    void shouldPrintBlankDatesAndCompactBackgrounds_withoutReusingPatientData() throws Exception {
        long coldStart = System.nanoTime();
        byte[] first = print(record("FAKE-FIRST"), List.of(1, 2, 3, 4));
        long coldMillis = (System.nanoTime() - coldStart) / 1_000_000;
        long warmStart = System.nanoTime();
        byte[] second = print(record("FAKE-SECOND"), List.of(1, 2, 3, 4));
        long warmMillis = (System.nanoTime() - warmStart) / 1_000_000;
        System.out.printf("Rourke PDF: %d bytes; cold %d ms; warm %d ms%n", second.length, coldMillis, warmMillis);
        // A document-size assertion is deterministic; wall-clock timings are diagnostics only.
        assertThat(first.length).isLessThan(6_000_000);
        try (PDDocument pdf = Loader.loadPDF(second)) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(4);
            String text = new PDFTextStripper().getText(pdf);
            assertThat(text).contains("FAKE-SECOND", "08/01/2026").doesNotContain("FAKE-FIRST", "null");
            // Inspect each visit's actual printed date box. Whole-document searches
            // cannot distinguish a blank field from a default date or placeholder.
            int[] dateRows = {340, 335, 335, 320};
            for (int page = 0; page < dateRows.length; page++) {
                PDFTextStripperByArea dates = new PDFTextStripperByArea();
                for (int visit = 0; visit < 3; visit++) {
                    dates.addRegion("visit" + visit,
                            new Rectangle2D.Float(210 + 530 * visit, dateRows[page], 165, 22));
                }
                dates.extractRegions(pdf.getPage(page));
                for (int visit = 0; visit < 3; visit++) {
                    assertThat(dates.getTextForRegion("visit" + visit).strip())
                            .as("date on page %s, visit %s", page + 1, visit + 1)
                            .isEqualTo(page == 0 && visit == 0 ? "08/01/2026" : "");
                }
            }
            assertThat(pdf.getPage(0).getMediaBox().getWidth()).isEqualTo(1700);
            assertThat(pdf.getPage(0).getMediaBox().getHeight()).isEqualTo(2200);
        }
        Files.createDirectories(Path.of("target/rourke-pdf"));
        Files.write(Path.of("target/rourke-pdf/record.pdf"), second);
    }

    @Test
    void shouldPrintAllSevenNotes_onLabelledContinuationPages() throws Exception {
        Properties values = record("FAKE-NOTES");
        List<String> fields = List.of("p2_development2m", "p2_development4m", "p2_development6m",
                "p2_physical2m", "p2_physical4m", "p2_physical6m", "p2_immunization6m");
        for (int i = 0; i < fields.size(); i++) values.setProperty(fields.get(i), "NOTE-" + i + "-PRESERVED");
        byte[] bytes = print(values, List.of(1, 2, 3, 4));
        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(5);
            String text = new PDFTextStripper().getText(pdf);
            for (int i = 0; i < fields.size(); i++) assertThat(text).contains("NOTE-" + i + "-PRESERVED");
            assertThat(text).contains("Page II notes", "2 months", "4 months", "6 months");
        }
        try (PDDocument pdf = Loader.loadPDF(print(values, List.of(1, 3, 4)))) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(3);
            assertThat(new PDFTextStripper().getText(pdf)).doesNotContain("NOTE-0-PRESERVED");
        }
        Files.createDirectories(Path.of("target/rourke-pdf"));
        Files.write(Path.of("target/rourke-pdf/notes.pdf"), bytes);
    }

    @Test
    void shouldPreserveClinicalSymbolsAndAccentedNames_whenPrintingNotes() throws Exception {
        Properties values = record("FAKE-Łucja");
        String note = "weight ≥ 5 kg; temperature ≤ 38°C; Δ weight; Na⁺";
        values.setProperty("p2_physical2m", note);
        try (PDDocument pdf = Loader.loadPDF(print(values, List.of(2)))) {
            PDFTextStripper text = new PDFTextStripper();
            text.setStartPage(2);
            assertThat(text.getText(pdf)).contains("FAKE-Łucja", note);
        }
    }

    @Test
    void shouldKeepLongNotes_whenContinuationOverflowsOnePage() throws Exception {
        Properties values = record("FAKE-LONG-NOTES");
        values.setProperty("p2_development2m", "Clinical note line\n".repeat(120) + "END-OF-LONG-NOTE");
        try (PDDocument pdf = Loader.loadPDF(print(values, List.of(2)))) {
            assertThat(pdf.getNumberOfPages()).isGreaterThan(2);
            assertThat(new PDFTextStripper().getText(pdf)).contains("END-OF-LONG-NOTE");
        }
    }

    @Test
    void shouldRejectUnknownPages_beforeLoadingResources() {
        assertThatThrownBy(() -> print(record("FAKE-INVALID"), List.of(5)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
