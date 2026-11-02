/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.report.export;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.apache.commons.csv.CSVFormat;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import io.github.carlos_emr.carlos.commn.model.DxRegistedPTInfo;
import io.github.carlos_emr.carlos.entities.MSPBill;
import static org.assertj.core.api.Assertions.*;

@Tag("unit")
@Tag("report")
class SpreadsheetExportsUnitTest {
    private MSPBill bill() {
        MSPBill b = new MSPBill();
        b.setDemoName("=HYPERLINK(\"synthetic\",\"Élodie,\nquoted\")");
        b.setDemoNo("001"); b.setAmount("-1234.50"); b.setAmtOwing("12.50");
        b.setServiceDate("20261001"); b.setRejectionDate("20261002"); b.setPaymentDate("20261003");
        b.setDemoDOB("00100101"); b.setHin("0012345678"); b.setDemoPhone("0012345");
        b.setBilling_no("10"); b.setBillMasterNo("20"); b.setBillingUnit("1"); b.setBillingtype("MSP");
        b.setPaymentMethod("1"); b.setReason("quoted, \"reason\"\nnext line");
        return b;
    }

    @ParameterizedTest
    @ValueSource(strings = {"REP_ACCOUNT_REC", "REP_INVOICE", "REP_REJ", "REP_WO", "REP_PAYREF", "REP_PAYREF_SUM"})
    void shouldWriteStableColumnsAndSafeText_forEachAccountingExport(String type) throws Exception {
        Locale previous = Locale.getDefault();
        byte[] expected = null;
        try {
            for (Locale locale : List.of(Locale.ENGLISH, Locale.US, Locale.CANADA)) {
                Locale.setDefault(locale);
                var output = new ByteArrayOutputStream();
                BcBillingCsv.write(output, type, List.of(bill()));
                if (expected == null) expected = output.toByteArray();
                assertThat(output.toByteArray()).isEqualTo(expected);
                var records = CSVFormat.DEFAULT.parse(new StringReader(output.toString(StandardCharsets.UTF_8))).getRecords();
                assertThat(records).hasSize(type.equals("REP_PAYREF_SUM") ? 10 : 2);
                assertThat(records).allSatisfy(r -> assertThat(r.size()).isEqualTo(records.getFirst().size()));
                if (!type.equals("REP_PAYREF_SUM")) {
                    assertThat(records.get(1)).contains("-1234.50");
                    assertThat(records.get(1).toList()).anySatisfy(v -> assertThat(v).startsWith("\t=HYPERLINK"));
                }
                if (type.equals("REP_REJ")) {
                    assertThat(records.get(1)).contains("00100101", "0012345678", "0012345", "01-Oct-26", "02-Oct-26");
                }
                if (type.equals("REP_INVOICE")) assertThat(records.get(1)).contains("12.50", "quoted, \"reason\"\nnext line");
            }
        } finally {
            Locale.setDefault(previous);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"=1+1", "+SUM(A1)", "-cmd", "@name", "\tname", "\rname", "\nname", " \uFEFF＝1+1"})
    void shouldQuoteAndGuardFormulaText_whenWritingCsv(String value) throws Exception {
        var output = new ByteArrayOutputStream();
        try (var csv = new TabularExport(output, "Header")) { csv.row(value, "-12.50", "Élodie,\"quote\"\r\nline"); }
        var row = CSVFormat.DEFAULT.parse(new StringReader(output.toString(StandardCharsets.UTF_8))).getRecords().get(1);
        assertThat(row.get(0)).isEqualTo("\t" + value);
        assertThat(row.get(1)).isEqualTo("-12.50");
        assertThat(row.get(2)).isEqualTo("Élodie,\"quote\"\r\nline");
    }

    @Test
    void shouldKeepIdentifiersAndFormulaLikeNamesAsText_whenRowsAreFlushedToDisk() throws Exception {
        var patient = new DxRegistedPTInfo("=1+1", "Élodie,\"name\"\nline", "X", "00100101", "0012345", "0012345678",
                "icd9", "001", "2026-10-01", "2026-10-02", "A");
        var output = new ByteArrayOutputStream();
        DxResearchExcel.write(output, Collections.nCopies(205, patient));
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(output.toByteArray()))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isEqualTo(205);
            for (int index : List.of(1, 205)) {
                var row = sheet.getRow(index);
                for (var cell : row) assertThat(cell.getCellType()).isEqualTo(CellType.STRING);
                assertThat(row.getCell(0).getStringCellValue()).isEqualTo("=1+1");
                assertThat(row.getCell(3).getStringCellValue()).isEqualTo("00100101");
                assertThat(row.getCell(4).getStringCellValue()).isEqualTo("0012345");
                assertThat(row.getCell(5).getStringCellValue()).isEqualTo("0012345678");
            }
        }
    }

    @Test
    void shouldRejectMalformedAmountsAndDates_beforeDownload() {
        assertThatThrownBy(() -> BcBillingCsv.money("synthetic invalid amount")).isInstanceOf(NumberFormatException.class);
        assertThatThrownBy(() -> BcBillingCsv.date("20260230")).isInstanceOf(java.time.DateTimeException.class);
        assertThat(BcBillingCsv.date("00000000")).isEmpty();
    }
}
