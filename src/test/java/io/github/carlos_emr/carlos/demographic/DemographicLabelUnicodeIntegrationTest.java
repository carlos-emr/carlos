/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.demographic;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
class DemographicLabelUnicodeIntegrationTest {
    @ParameterizedTest
    @ValueSource(strings = {"label.xml", "Addresslabel.xml", "Chartlabel.xml", "ClientLabLabel.xml", "SexualHealthClinicLabel.xml"})
    void shouldPreserveUnicodeNames_whenCompilingAndRenderingLabel(String name) throws Exception {
        try (var source = getClass().getResourceAsStream("/oscar/oscarDemographic/" + name)) {
            var report = JasperCompileManager.compileReport(source);
            Map<String, Object> row = new HashMap<>();
            for (var field : report.getFields()) {
                row.put(field.getName(), field.getValueClass() == Integer.class ? Integer.valueOf(123) : "123");
            }
            row.put("first_name", "Nguyễn");
            row.put("last_name", "Łukasz");
            row.put("middle_name", "İstanbul");
            row.put("year_of_birth", "2000");
            row.put("month_of_birth", "01");
            row.put("date_of_birth", "02");
            var print = JasperFillManager.fillReport(report, new HashMap<>(), new JRMapCollectionDataSource(List.of(row)));
            try (var pdf = Loader.loadPDF(JasperExportManager.exportReportToPdf(print))) {
                assertThat(new PDFTextStripper().getText(pdf)).contains("Nguyễn", "Łukasz");
                for (var page : pdf.getPages()) {
                    assertThat(page.getResources().getFontNames()).isNotEmpty();
                    for (var fontName : page.getResources().getFontNames()) {
                        assertThat(page.getResources().getFont(fontName).isEmbedded()).isTrue();
                    }
                }
            }
        }
    }
}
