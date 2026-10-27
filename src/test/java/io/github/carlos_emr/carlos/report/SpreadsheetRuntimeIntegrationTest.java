package io.github.carlos_emr.carlos.report;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;

import io.github.carlos_emr.OscarDocumentCreator;
import net.sf.jasperreports.engine.design.JRDesignBand;
import net.sf.jasperreports.engine.design.JRDesignSection;
import net.sf.jasperreports.engine.design.JRDesignStaticText;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.xml.JRXmlWriter;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

/** Real workbook and application-export round trips for the POI/XMLBeans dependency set. */
@Tag("integration")
@Tag("report")
class SpreadsheetRuntimeIntegrationTest {
    private static final String TEXT = "Synthetic Élodie 李 😀 & <report>";

    @Test
    void shouldPreserveUnicodeAndIdentifiers_whenXlsxIsWrittenAndRead() throws Exception {
        var output = new ByteArrayOutputStream();
        try (var workbook = new XSSFWorkbook()) {
            var row = workbook.createSheet("Report").createRow(0);
            row.createCell(0).setCellValue(TEXT);
            row.createCell(1).setCellValue("00123");
            row.createCell(2).setCellValue("12345678901234567");
            workbook.write(output);
        }
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(output.toByteArray()))) {
            var row = workbook.getSheetAt(0).getRow(0);
            assertThat(row.getCell(0).getStringCellValue()).isEqualTo(TEXT);
            assertThat(row.getCell(1).getCellType()).isEqualTo(CellType.STRING);
            assertThat(row.getCell(1).getStringCellValue()).isEqualTo("00123");
            assertThat(row.getCell(2).getStringCellValue()).isEqualTo("12345678901234567");
        }
    }

    @Test
    void shouldPreserveDatesNumbersAndFormulas_whenXlsxIsWrittenAndRead() throws Exception {
        var date = LocalDateTime.of(2026, 8, 31, 23, 45, 0);
        var output = new ByteArrayOutputStream();
        try (var workbook = new XSSFWorkbook()) {
            var row = workbook.createSheet("Values").createRow(0);
            row.createCell(0).setCellValue(1234567.89);
            var dateCell = row.createCell(1);
            dateCell.setCellValue(date);
            var style = workbook.createCellStyle();
            style.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd hh:mm:ss"));
            dateCell.setCellStyle(style);
            var formula = row.createCell(2);
            formula.setCellFormula("A1*2");
            workbook.getCreationHelper().createFormulaEvaluator().evaluateFormulaCell(formula);
            workbook.write(output);
        }
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(output.toByteArray()))) {
            var row = workbook.getSheetAt(0).getRow(0);
            assertThat(row.getCell(0).getNumericCellValue()).isCloseTo(1234567.89, offset(0.000001));
            assertThat(row.getCell(1).getLocalDateTimeCellValue()).isEqualTo(date);
            assertThat(row.getCell(2).getCellType()).isEqualTo(CellType.FORMULA);
            assertThat(row.getCell(2).getCellFormula()).isEqualTo("A1*2");
            assertThat(row.getCell(2).getNumericCellValue()).isCloseTo(2469135.78, offset(0.000001));
        }
    }

    @Test
    void shouldRetainFlushedRows_whenStreamingXlsxExceedsItsMemoryWindow() throws Exception {
        var output = new ByteArrayOutputStream();
        try (var workbook = new SXSSFWorkbook(10)) {
            var sheet = workbook.createSheet("Stream");
            for (int i = 0; i < 100; i++) {
                var row = sheet.createRow(i);
                row.createCell(0).setCellValue(i);
                row.createCell(1).setCellValue(TEXT + i);
            }
            workbook.write(output);
        }
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(output.toByteArray()))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(100);
            assertThat(sheet.getRow(0).getCell(1).getStringCellValue()).isEqualTo(TEXT + 0);
            assertThat(sheet.getRow(99).getCell(1).getStringCellValue()).isEqualTo(TEXT + 99);
        }
    }

    @Test
    void shouldPreserveReportText_whenPublicJasperExportWritesXlsx() throws Exception {
        var design = new JasperDesign();
        design.setName("SyntheticXlsxRegression");
        design.setPageWidth(595);
        design.setPageHeight(842);
        design.setColumnWidth(555);
        var band = new JRDesignBand();
        band.setHeight(40);
        var text = new JRDesignStaticText();
        text.setX(0);
        text.setY(0);
        text.setWidth(500);
        text.setHeight(30);
        text.setFontName("SansSerif");
        text.setFontSize(12f);
        text.setText(TEXT);
        band.addElement(text);
        ((JRDesignSection) design.getDetailSection()).addBand(band);
        String xml = JRXmlWriter.writeReport(design, "UTF-8");
        var output = new ByteArrayOutputStream();
        new OscarDocumentCreator().fillDocumentStream(new HashMap<>(), output, OscarDocumentCreator.EXCEL,
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), null);
        assertThat(output.size()).isPositive();
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(output.toByteArray()))) {
            boolean found = false;
            for (var sheet : workbook) {
                for (var row : sheet) {
                    for (var cell : row) {
                        found |= cell.getCellType() == CellType.STRING && TEXT.equals(cell.getStringCellValue());
                    }
                }
            }
            assertThat(found).as("Jasper's XLSX contains the original Unicode report text").isTrue();
        }
    }
}
