/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.appointment;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import net.sf.jasperreports.engine.JREmptyDataSource;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("pdf")
class AppointmentReceiptTemplateUnitTest {
    @ParameterizedTest
    @ValueSource(strings = {"en", "fr"})
    void shouldKeepFullIdentityAndAppointmentDetailsInsidePage_whenNamesWrap(String language) throws Exception {
        var values = new HashMap<String, Object>();
        values.put("clinicName", "Synthetic Community Primary Care Clinic");
        values.put("clinicAddress", "123 Long Synthetic Avenue Suite 456");
        values.put("clinicCity", "Example City");
        values.put("clinicProvince", "ON");
        values.put("clinicPostal", "A1A 1A1");
        values.put("clinicPhone", "555-010-1234");
        values.put("clinicFax", "555-010-5678");
        values.put("apptName", "FAKE-APP0123456789abcdef,Appointment");
        values.put("providerName", "O'Neil-MacDonald Marie Alexandra");
        values.put("apptDate", "2027-10-31");
        values.put("apptTime", "09:15");
        values.put("apptId", "123456");
        values.put("DOB", "1980-01-02");
        values.put("printedDateTime", "2026-09-26 12:30");
        var labels = ResourceBundle.getBundle("oscarResources", Locale.forLanguageTag(language));
        for (String label : List.of("Name", "DOB", "Date", "Time", "With", "Printed")) {
            String key = "report.appointmentReceipt." + label;
            values.put(key, labels.getString(key));
        }
        try (var template = getClass().getResourceAsStream("/oscar/oscarDemographic/AppointmentReceipt.xml")) {
            assertThat(template).isNotNull();
            var report = JasperCompileManager.compileReport(template);
            var print = JasperFillManager.fillReport(report, new HashMap<>(values), new JREmptyDataSource());
            try (var pdf = Loader.loadPDF(JasperExportManager.exportReportToPdf(print))) {
                var allText = new StringBuilder();
                for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
                    class Positions extends PDFTextStripper {
                        final List<TextPosition> glyphs = new ArrayList<>();
                        Positions() throws IOException { super(); }
                        @Override protected void processTextPosition(TextPosition position) {
                            glyphs.add(position);
                            super.processTextPosition(position);
                        }
                    }
                    var text = new Positions();
                    text.setStartPage(page);
                    text.setEndPage(page);
                    text.setSortByPosition(true);
                    allText.append(text.getText(pdf));
                    var box = pdf.getPage(page - 1).getMediaBox();
                    assertThat(box.getWidth()).isEqualTo(229f);
                    assertThat(box.getHeight()).isEqualTo(210f);
                    for (var glyph : text.glyphs) {
                        assertThat(glyph.getXDirAdj()).isGreaterThanOrEqualTo(14.9f);
                        assertThat(glyph.getXDirAdj() + glyph.getWidthDirAdj()).isLessThanOrEqualTo(215.1f);
                        assertThat(glyph.getYDirAdj()).isLessThanOrEqualTo(206.1f);
                    }
                }
                String compact = allText.toString().replaceAll("\\s+", "");
                for (Object value : values.values()) {
                    assertThat(compact).contains(value.toString().replaceAll("\\s+", ""));
                }
            }
        }
    }
}
