/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.report;

import io.github.carlos_emr.OscarDocumentCreator;
import io.github.carlos_emr.carlos.commn.model.DxRegistedPTInfo;
import io.github.carlos_emr.carlos.report.export.DxResearchExcel;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import net.sf.jasperreports.engine.JREmptyDataSource;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionJavaScript;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** Exercises the application's Jasper PDF facade and direct JDK spreadsheet writer. */
@Tag("unit")
@Tag("report")
class JasperExporterCompatibilityUnitTest {
    private static final String TEXT = "FAKE Nguyễn Łucja Ωμέγα Жанна ≥5 µg";

    private static ByteArrayInputStream template() {
        return new ByteArrayInputStream("""
                <jasperReport name="exportCompatibility" pageWidth="400" pageHeight="100" columnWidth="400"
                    leftMargin="0" rightMargin="0" topMargin="0" bottomMargin="0">
                    <parameter name="value" class="java.lang.String"/>
                    <title height="30">
                        <element kind="textField" fontName="DejaVu Sans" x="0" y="0" width="400" height="25">
                            <expression>$P{value}</expression>
                        </element>
                    </title>
                </jasperReport>
                """.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void shouldPreserveTextFontsAndAutoPrint_whenExportingPdfThroughDocumentCreator() throws Exception {
        String script = "this.print({bUI: true, bSilent: false});";
        var bytes = new ByteArrayOutputStream();
        try (var input = template()) {
            new OscarDocumentCreator().fillDocumentStream(new HashMap<>(java.util.Map.of("value", TEXT)),
                    bytes, OscarDocumentCreator.PDF, input, new JREmptyDataSource(), script);
        }
        try (var pdf = Loader.loadPDF(bytes.toByteArray())) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(1);
            assertThat(new PDFTextStripper().getText(pdf).strip()).isEqualTo(TEXT);
            assertThat(pdf.getDocumentCatalog().getNames().getJavaScript().getNames().values())
                    .extracting(PDActionJavaScript::getAction).containsExactly(script);
            for (var name : pdf.getPage(0).getResources().getFontNames()) {
                var font = pdf.getPage(0).getResources().getFont(name);
                assertThat(font.isEmbedded()).isTrue();
                assertThat(font.getName()).contains("DejaVuSans");
            }
        }
    }

    @Test
    void shouldPreserveUnicodeCellValues_whenExportingExcelThroughTheJdkWriter() throws Exception {
        var bytes = new ByteArrayOutputStream();
        var patient = new DxRegistedPTInfo(TEXT, "FAKE-Compatibility", "X", "2000-01-01",
                "0001234", "0000000001", "icd9", "001", "2026-08-01", "2026-08-02", "A");
        DxResearchExcel.write(bytes, List.of(patient));
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes.toByteArray()))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(0).getStringCellValue()).isEqualTo(TEXT);
        }
    }
}
