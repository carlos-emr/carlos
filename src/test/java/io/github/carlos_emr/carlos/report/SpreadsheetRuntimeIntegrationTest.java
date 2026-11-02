package io.github.carlos_emr.carlos.report;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.Month;

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
        var date = LocalDateTime.of(2026, Month.AUGUST, 31, 23, 45, 0);
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
    void shouldPreserveReportText_whenDxWriterExportsXlsx() throws Exception {
        var output = new ByteArrayOutputStream();
        var patient = new io.github.carlos_emr.carlos.commn.model.DxRegistedPTInfo(TEXT, "Synthetic", "X", "00100101",
                "0012345", "0012345678", "icd9", "001", "2026-10-01", "2026-10-02", "A");
        io.github.carlos_emr.carlos.report.export.DxResearchExcelWriter.write(output, java.util.List.of(patient));
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(output.toByteArray()))) {
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(0).getStringCellValue()).isEqualTo(TEXT);
        }
    }
}
