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
import org.junit.jupiter.params.provider.NullAndEmptySource;
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
                    assertThat(records.get(1)).contains(type.equals("REP_ACCOUNT_REC") ? "12.50" : "-1234.50");
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
    void shouldKeepIdentifiersAndFormulaLikeNamesAsText_whenStreamingRows() throws Exception {
        var patient = new DxRegistedPTInfo("=1+1", "Élodie,\"name\"\nline", "X", "00100101", "0012345", "0012345678",
                "icd9", "001", "2026-10-01", "2026-10-02", "A");
        var output = new ByteArrayOutputStream();
        DxResearchExcel.write(output, Collections.nCopies(205, patient));
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(output.toByteArray()))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getSheetName()).isEqualTo("Dx Research");
            assertThat(sheet.getPaneInformation().isFreezePane()).isTrue();
            assertThat(sheet.getPaneInformation().getHorizontalSplitPosition()).isEqualTo((short) 1);
            assertThat(sheet.getColumnWidth(0)).isEqualTo(22 * 256);
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
    void shouldRejectTooManyPatients_beforeWritingTheWorkbook() {
        var output = new ByteArrayOutputStream();
        assertThatThrownBy(() -> DxResearchExcel.write(output, Collections.nCopies(1_048_576, null)))
                .isInstanceOf(java.io.IOException.class).hasMessage("Export exceeds worksheet row limit");
        assertThat(output.size()).isZero();
    }

    @Test
    void shouldKeepRefundSubsetsAndNetTotals_whenPaymentsIncludeMultipleMethodsAndAdjustments() throws Exception {
        MSPBill paid = bill(); paid.setAmount("33.70"); paid.setAdjustmentCodeAmt("2.00");
        MSPBill refunded = bill(); refunded.setAmount("-10.00");
        MSPBill visaRefund = bill(); visaRefund.setPaymentMethod("3"); visaRefund.setAmount("-5.25");
        MSPBill electronic = bill(); electronic.setPaymentMethod("6"); electronic.setAmount("25.00");
        var output = new ByteArrayOutputStream();
        BcBillingCsv.write(output, "REP_PAYREF_SUM", List.of(paid, refunded, visaRefund, electronic));
        var rows = CSVFormat.DEFAULT.parse(new StringReader(output.toString(StandardCharsets.UTF_8))).getRecords();
        assertThat(rows.get(1).toList()).containsExactly("Cash", "23.70", "-10.00");
        assertThat(rows.get(3).toList()).containsExactly("Visa", "-5.25", "-5.25");
        assertThat(rows.get(6).toList()).containsExactly("Electronic", "27.00", "0.00");
        assertThat(rows.get(9).toList()).containsExactly("Total", "45.45", "-15.25");
    }

    @Test
    void shouldAssignEachClaimToOneAgingColumn_atEveryAgeBoundary() throws Exception {
        var reportDate = java.time.LocalDate.of(2026, 11, 2);
        for (var entry : java.util.Map.of(-1, 7, 0, 7, 30, 7, 31, 6, 60, 6, 61, 5, 90, 5, 91, 4).entrySet()) {
            MSPBill b = bill();
            b.setServiceDate(reportDate.minusDays(entry.getKey()).format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE));
            var output = new ByteArrayOutputStream();
            BcBillingCsv.write(output, "REP_ACCOUNT_REC", List.of(b), reportDate);
            var row = CSVFormat.DEFAULT.parse(new StringReader(output.toString(StandardCharsets.UTF_8))).getRecords().get(1);
            assertThat(row.get(entry.getValue())).as("age %s", entry.getKey()).isEqualTo("12.50");
            assertThat(row.toList().subList(4, 8)).filteredOn(v -> !v.isEmpty()).containsExactly("12.50");
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "00000000", "not a date", "20260230", "20261301"})
    void shouldRetainUnagedBalance_whenServiceDateIsUnavailable(String serviceDate) throws Exception {
        MSPBill b = bill();
        b.setServiceDate(serviceDate);
        var output = new ByteArrayOutputStream();
        BcBillingCsv.write(output, "REP_ACCOUNT_REC", List.of(b, bill()), java.time.LocalDate.of(2026, 11, 2));
        var rows = CSVFormat.DEFAULT.parse(new StringReader(output.toString(StandardCharsets.UTF_8))).getRecords();
        assertThat(rows).hasSize(3);
        assertThat(rows.getFirst().toList()).containsExactly("Insurer", "Patient", "ServDate", "Invoice#",
                "91 Days+", "61 - 90 Days", "31 - 60 Days", "0 - 30 Days", "Status", "Unknown age");
        assertThat(rows.get(1).get(2)).isEqualTo(serviceDate == null || serviceDate.isBlank() || "00000000".equals(serviceDate) ? "" : serviceDate);
        assertThat(rows.get(1).toList().subList(4, 8)).containsOnly("");
        assertThat(rows.get(1).get(9)).isEqualTo("12.50");
        assertThat(rows.get(2).get(6)).isEqualTo("12.50");
        assertThat(rows.get(2).get(9)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"REP_ACCOUNT_REC", "REP_INVOICE", "REP_REJ", "REP_WO", "REP_PAYREF"})
    void shouldPreserveMalformedValues_whenWritingDetailExports(String type) throws Exception {
        MSPBill malformed = bill();
        malformed.setServiceDate("20260230"); malformed.setRejectionDate("not a date"); malformed.setPaymentDate("20261301");
        malformed.setAmount("=bad amount"); malformed.setAmtOwing("not a decimal"); malformed.setAdjustmentCodeAmt("bad adjustment");
        var output = new ByteArrayOutputStream();
        BcBillingCsv.write(output, type, List.of(malformed, bill()));
        var rows = CSVFormat.DEFAULT.parse(new StringReader(output.toString(StandardCharsets.UTF_8))).getRecords();
        assertThat(rows).hasSize(3);
        assertThat(rows.get(1)).contains("20260230", type.equals("REP_ACCOUNT_REC") ? "not a decimal" : "\t=bad amount");
        assertThat(rows.get(2)).contains("01-Oct-26", type.equals("REP_ACCOUNT_REC") ? "12.50" : "-1234.50");
        if (type.equals("REP_INVOICE")) assertThat(rows.get(1)).contains("not a decimal");
        if (type.equals("REP_REJ")) assertThat(rows.get(1)).contains("not a date");
        if (type.equals("REP_PAYREF")) assertThat(rows.get(1)).contains("20261301", "bad adjustment");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "not a decimal", "=1+1"})
    void shouldKeepValidSummaryTotals_whenAmountsOrAdjustmentsAreUnparseable(String amount) throws Exception {
        MSPBill invalidAmount = bill(); invalidAmount.setAmount(amount); invalidAmount.setAdjustmentCodeAmt("2.25");
        MSPBill invalidAdjustment = bill(); invalidAdjustment.setAmount("12.50"); invalidAdjustment.setAdjustmentCodeAmt(amount);
        MSPBill refund = bill(); refund.setAmount("-5.00");
        var output = new ByteArrayOutputStream();
        BcBillingCsv.write(output, "REP_PAYREF_SUM", List.of(invalidAmount, invalidAdjustment, refund));
        var rows = CSVFormat.DEFAULT.parse(new StringReader(output.toString(StandardCharsets.UTF_8))).getRecords();
        assertThat(rows.get(1).toList()).containsExactly("Cash", "7.50", "-5.00");
        assertThat(rows.get(6).toList()).containsExactly("Electronic", "2.25", "0.00");
        assertThat(rows.get(9).toList()).containsExactly("Total (known amounts only)", "9.75", "-5.00");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "00000000"})
    void shouldLeaveMissingDatesAndAmountsEmpty_whenFormattingLegacyFields(String value) {
        assertThat(BcBillingCsv.date(value)).isEmpty();
        if (!"00000000".equals(value)) assertThat(BcBillingCsv.money(value)).isEmpty();
    }

    @Test
    void shouldPreserveValidFormatting_whenLegacyTextIsMalformed() {
        assertThat(BcBillingCsv.date("20261001")).isEqualTo("01-Oct-26");
        assertThat(BcBillingCsv.date("20260230")).isEqualTo("20260230");
        assertThat(BcBillingCsv.money("12.345")).isEqualTo("12.35");
        assertThat(BcBillingCsv.money("synthetic invalid amount")).isEqualTo("synthetic invalid amount");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1e309", "-1e309"})
    void shouldPreserveOutOfRangeSourceText_whenAmountsExceedBalanceRepresentation(String amount) throws Exception {
        assertThat(BcBillingCsv.money(amount)).isEqualTo(amount);
        MSPBill b = bill(); b.setAmount(amount);
        var output = new ByteArrayOutputStream();
        BcBillingCsv.write(output, "REP_INVOICE", List.of(b));
        var rows = CSVFormat.DEFAULT.parse(new StringReader(output.toString(StandardCharsets.UTF_8))).getRecords();
        assertThat(rows.get(1).get(7)).isEqualTo(amount);
    }
}
