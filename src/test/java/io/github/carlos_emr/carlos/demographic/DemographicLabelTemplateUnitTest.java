/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.demographic;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class DemographicLabelTemplateUnitTest {
    @ParameterizedTest
    @ValueSource(strings = {"label.xml", "Addresslabel.xml", "Chartlabel.xml", "ClientLabLabel.xml", "SexualHealthClinicLabel.xml"})
    void shouldRender_withPatientIdentityUsingRuntimeJasperVersion(String name) throws Exception {
        try (InputStream template = getClass().getResourceAsStream("/oscar/oscarDemographic/" + name)) {
            assertThat(template).isNotNull();
            JasperReport report = JasperCompileManager.compileReport(template);
            assertThat(report.getQuery().getText()).contains("$P{demo}");
            Map<String, Object> row = new HashMap<>();
            for (JRField field : report.getFields()) {
                row.put(field.getName(), field.getValueClass() == Integer.class ? Integer.valueOf(123) : "123");
            }
            row.put("first_name", "TestGiven");
            row.put("middle_name", name.equals("label.xml") ? null : "Łukasz");
            row.put("last_name", "TestFamily");
            row.put("year_of_birth", "2000");
            row.put("month_of_birth", "01");
            row.put("date_of_birth", "02");
            JasperPrint print = JasperFillManager.fillReport(report, new HashMap<>(),
                    new JRMapCollectionDataSource(List.of(row)));
            byte[] pdf = JasperExportManager.exportReportToPdf(print);
            try (PdfReader reader = new PdfReader(pdf)) {
                assertThat(reader.getNumberOfPages()).isEqualTo(1);
                String text = new PdfTextExtractor(reader).getTextFromPage(1);
                assertThat(text).contains("TestGiven", "TestFamily");
                if (name.equals("label.xml")) assertThat(text).doesNotContain("null");
                if (name.equals("Chartlabel.xml")) assertThat(text).contains("Łukasz");
            }
        }
    }
    @Test
    void shouldRenderBlankAge_whenBirthDateFieldsAreEmpty() throws Exception {
        try (InputStream template = getClass().getResourceAsStream("/oscar/oscarDemographic/Chartlabel.xml")) {
            JasperReport report = JasperCompileManager.compileReport(template);
            Map<String, Object> row = new HashMap<>();
            for (JRField field : report.getFields()) row.put(field.getName(), "");
            row.put("first_name", "Anne");
            row.put("last_name", "FAKE Partial");
            JasperPrint print = JasperFillManager.fillReport(report, new HashMap<>(),
                    new JRMapCollectionDataSource(List.of(row)));
            try (PdfReader reader = new PdfReader(JasperExportManager.exportReportToPdf(print))) {
                String text = new PdfTextExtractor(reader).getTextFromPage(1);
                assertThat(text).contains("Anne", "AGE:").doesNotContain("null");
                assertThat(text).doesNotContainPattern("AGE:\\s*\\d");
            }
        }
    }

}
