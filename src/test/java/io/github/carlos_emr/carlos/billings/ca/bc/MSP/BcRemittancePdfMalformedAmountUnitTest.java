/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.billings.ca.bc.MSP;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.sf.jasperreports.engine.JRPrintElement;
import net.sf.jasperreports.engine.JRPrintPage;
import net.sf.jasperreports.engine.JRPrintText;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.carlos_emr.carlos.billings.ca.bc.data.BcBillingRows.RemittanceRow;

/**
 * The BC MSP remittance PDFs render when a stored Teleplan amount is not a Teleplan amount.
 *
 * <p>One row holding {@code 12.5}, {@code +123} or a stray letter used to throw from a template
 * expression and answer HTTP 500 for the whole remittance. The reports now print every row, show the
 * field's source text with the CSV export's wording, keep the totals to known amounts and say how many
 * fields were left out. Valid, blank and overpunched amounts print exactly as before.</p>
 */
@Tag("unit")
@Tag("billing")
@DisplayName("BC remittance PDFs with malformed Teleplan amounts")
class BcRemittancePdfMalformedAmountUnitTest {
    private static final String ROOT = "/oscar/oscarBilling/ca/bc/reports/";

    private static JasperReport compile(String template) throws Exception {
        try (InputStream source = BcRemittancePdfMalformedAmountUnitTest.class.getResourceAsStream(ROOT + template)) {
            assertThat(source).as(template).isNotNull();
            return JasperCompileManager.compileReport(source);
        }
    }

    /** The exported PDF's text, with runs of white space collapsed so wrapped cells still match. */
    private static String pdfText(JasperPrint print) throws Exception {
        try (var pdf = Loader.loadPDF(JasperExportManager.exportReportToPdf(print))) {
            assertThat(pdf.getNumberOfPages()).isPositive();
            return new PDFTextStripper().getText(pdf).replaceAll("\\s+", " ");
        }
    }

    private static List<JRPrintText> texts(JasperPrint print) {
        List<JRPrintText> texts = new ArrayList<>();
        for (JRPrintPage page : print.getPages()) {
            for (JRPrintElement element : page.getElements()) {
                if (element instanceof JRPrintText text) texts.add(text);
            }
        }
        return texts;
    }

    private static RemittanceRow remittance(String type, String paid, String sequence) {
        // A short patient name keeps every valid row to one printed line.
        return new RemittanceRow("00100", "FAKE", "Provider", "91234", type, "20261015", "20261016", "00001",
                "FAKE,BC", 9855, paid, null, null, null, null, null, null, sequence);
    }

    private static JasperPrint fillRemittance(List<RemittanceRow> rows) throws Exception {
        var parameters = new HashMap<String, Object>();
        parameters.put("payee", "FAKE Payee");
        parameters.put("payeeno", "81234");
        return JasperFillManager.fillReport(compile("pdf_rep_msprem.jrxml"), parameters, new BcRemittanceDataSource(rows));
    }

    @ParameterizedTest
    @ValueSource(strings = {"12.5", "+123", "00012A", "1e3", "abc", "0000 12"})
    @DisplayName("should print the remittance with the malformed row flagged and left out of the totals")
    void shouldRenderRemittanceAndFlagTheRow_whenOnePaidAmountIsMalformed(String malformed) throws Exception {
        JasperPrint print = fillRemittance(List.of(
                remittance("S01", "0001234", "0000001"),     // $12.34
                remittance("S04", "", "0000002"),            // held: Teleplan sends no amount
                remittance("S01", malformed, "0000003"),
                remittance("S02", "000050}", "0000004")));   // overpunched -$5.00

        String text = pdfText(print);
        // Every row is printed; the malformed field shows its source text in the CSV export's wording.
        assertThat(text).contains("0000001", "0000002", "0000003", "0000004");
        assertThat(text).contains("Unparsed Teleplan amount: " + malformed);
        // Its Amount cell holds a mark, never the source text alone, which could be read as dollars.
        assertThat(texts(print)).extracting(JRPrintText::getFullText).contains("?").doesNotContain(malformed);
        // Valid amounts are decoded as before, including the blank and the overpunched negative.
        assertThat(text).contains("12.34", "5.00", "0.00");
        // Totals are of known amounts only (12.34 - 5.00), and say so with a count.
        assertThat(text).contains("Total for: FAKE Provider:").contains("7.34")
                .contains("known amounts only; 1 unparsed amount field excluded");
        assertThat(text).contains("Known amounts only. Unparsed amount fields excluded: 1");
    }

    @Test
    @DisplayName("should print valid, blank and overpunched amounts with no notice")
    void shouldRenderRemittanceWithoutNotice_whenEveryAmountIsValidOrBlank() throws Exception {
        JasperPrint print = fillRemittance(List.of(
                remittance("S01", "0001234", "0000001"),
                remittance("S04", "", "0000002"),
                remittance("S04", "       ", "0000003"),
                remittance("S04", null, "0000004"),
                remittance("S02", "000050}", "0000005"),
                remittance("S00", "000123M", "0000006")));   // -$12.34

        String text = pdfText(print);
        // 12.34 - 5.00 - 12.34: a blank amount is a real zero, not an unparsed field.
        assertThat(text).contains("Total for: FAKE Provider:").contains("5.00");
        assertThat(text).doesNotContain("Unparsed").doesNotContain("nown amounts only");
    }

    @Test
    @DisplayName("should keep the row spacing of a remittance with no malformed amount")
    void shouldKeepDetailRowSpacing_whenNoAmountIsMalformed() throws Exception {
        List<RemittanceRow> rows = new ArrayList<>();
        for (int row = 1; row <= 12; row++) rows.add(remittance("S01", "0001234", String.format("%07d", row)));
        // The note under a malformed row takes no space on the other rows. A valid row was 24 points
        // high before the note was added (its dates wrap to two lines in their columns) and still is.
        List<Integer> sequenceRows = texts(fillRemittance(rows)).stream()
                .filter(text -> text.getFullText() != null && text.getFullText().matches("\\d{7}"))
                .map(JRPrintText::getY).toList();
        assertThat(sequenceRows).hasSize(12);
        for (int row = 1; row < sequenceRows.size(); row++) {
            assertThat(sequenceRows.get(row) - sequenceRows.get(row - 1)).as("row %d", row).isEqualTo(24);
        }
    }

    private static Map<String, Object> adjustment(String gross, String regular, String outstanding) {
        var row = new HashMap<String, Object>();
        for (String key : List.of("t_ajc", "adj_desc", "t_aji", "t_calcmethod", "t_rpercent", "t_opercent")) row.put(key, "0");
        row.put("t_gamount", gross);
        row.put("t_ramount", regular);
        row.put("t_oamount", "000010");
        row.put("t_balancefwd", "");
        row.put("t_adjmade", "00002}");
        row.put("t_adjoutstanding", outstanding);
        return row;
    }

    @Test
    @DisplayName("should print the adjustments with malformed fields named and left out of the totals")
    void shouldRenderAdjustmentsAndNameMalformedFields_withKnownOnlyTotals() throws Exception {
        List<Map<String, ?>> rows = List.of(adjustment("000100", "000030", "000007"),
                adjustment("12.5", "000040", "+123"), adjustment("000200", "", "000001"));

        String text = pdfText(JasperFillManager.fillReport(compile("msppremsum.s23.jrxml"), new HashMap<>(),
                new JRMapCollectionDataSource(rows)));

        // Each malformed field is named with its column under its row.
        assertThat(text).contains("Unparsed Teleplan amount: Gross Amt 12.5; Adj. Out. +123");
        // Known amounts only: gross 1.00 + 2.00, regular 0.30 + 0.40 + blank, outstanding 0.07 + 0.01.
        assertThat(text).contains("3.00", "0.70", "0.08");
        // One-time 3 x 0.10 and adjustments made 3 x -0.20 are complete.
        assertThat(text).contains("0.30", "0.60");
        assertThat(text).contains("Known amounts only. Unparsed amount fields excluded: 2");
    }

    @Test
    @DisplayName("should print the adjustments exactly as before when every amount is valid")
    void shouldRenderAdjustmentsWithoutNotice_whenEveryAmountIsValid() throws Exception {
        List<Map<String, ?>> rows = List.of(adjustment("000100", "000030", "000007"), adjustment("000200", "", "000001"));

        JasperPrint print = JasperFillManager.fillReport(compile("msppremsum.s23.jrxml"), new HashMap<>(),
                new JRMapCollectionDataSource(rows));

        assertThat(pdfText(print)).contains("3.00", "0.30", "0.08").doesNotContain("Unparsed").doesNotContain("nown amounts only");
        // Two detail rows stay one 14-point band each: the note line takes no space when it has nothing to say.
        List<Integer> detailRows = texts(print).stream().filter(text -> "(0)".equals(text.getFullText()))
                .map(JRPrintText::getY).toList();
        assertThat(detailRows).hasSize(2);
        assertThat(detailRows.get(1) - detailRows.get(0)).isEqualTo(14);
    }

    /** The summary report and its sub-reports read their own SQL, so they are filled from a real (H2) database. */
    private static String remittanceSummary(String billed, String paid, String cheque, String practitionerPaid,
            String adjustmentGross) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:remittance_" + UUID.randomUUID().toString().replace("-", "") + ";DATABASE_TO_UPPER=FALSE")) {
            try (Statement sql = connection.createStatement()) {
                sql.execute("create table teleplanS21 (s21_id int, status varchar(1), t_payment varchar(8), t_payeeno varchar(5), "
                        + "t_payeename varchar(25), t_amtbilled varchar(20), t_amtpaid varchar(20), t_cheque varchar(20))");
                sql.execute("create table teleplanS22 (s22_id int, s21_id int, t_s22type varchar(3), t_payment varchar(8), "
                        + "t_payeeno varchar(5), t_practitionerno varchar(5), t_practitionername varchar(25), "
                        + "t_amtbilled varchar(20), t_amtpaid varchar(20))");
                sql.execute("create table teleplanS23 (s23_id int, s21_id int, t_s23type varchar(3), t_aji varchar(5), t_ajc varchar(2), "
                        + "t_calcmethod varchar(1), t_rpercent varchar(5), t_opercent varchar(5), t_gamount varchar(20), "
                        + "t_ramount varchar(20), t_oamount varchar(20), t_balancefwd varchar(20), t_adjmade varchar(20), "
                        + "t_adjoutstanding varchar(20))");
                sql.execute("create table teleplan_adj_codes (adj_code varchar(2), adj_desc varchar(50))");
                sql.execute("create table teleplanS25 (s25_id int, s21_id int, t_s25type varchar(3), t_practitionerno varchar(5), "
                        + "t_linecode varchar(2), t_message varchar(80))");
            }
            try (var insert = connection.prepareStatement("insert into teleplanS21 values (7, 'N', '20261016', '81234', 'FAKE PAYEE', ?, ?, ?)")) {
                insert.setString(1, billed);
                insert.setString(2, paid);
                insert.setString(3, cheque);
                insert.execute();
            }
            try (var insert = connection.prepareStatement(
                    "insert into teleplanS22 values (1, 7, 'S22', '20261016', '81234', '91234', 'FAKE PRACTITIONER', '000150000', ?)")) {
                insert.setString(1, practitionerPaid);
                insert.execute();
            }
            try (var insert = connection.prepareStatement(
                    "insert into teleplanS23 values (1, 7, 'S23', '91234', 'AA', 'P', '0', '0', ?, '000030', '000010', '', '00002}', '000007')")) {
                insert.setString(1, adjustmentGross);
                insert.execute();
            }
            var parameters = new HashMap<String, Object>();
            parameters.put("s21id", "7");
            parameters.put("practSum", compile("msppremsum.practsum.jrxml"));
            parameters.put("adj", compile("msppremsum.s23.jrxml"));
            parameters.put("msgs", compile("broadcastmessages.jrxml"));
            return pdfText(JasperFillManager.fillReport(compile("pdf_rep_mspremsum.jrxml"), parameters, connection));
        }
    }

    @Test
    @DisplayName("should print the remittance summary with malformed header, practitioner and adjustment amounts flagged")
    void shouldRenderRemittanceSummary_whenStoredAmountsAreMalformed() throws Exception {
        String text = remittanceSummary("000150000", "12.5", "+123", "00012A", "abc");

        // The valid billed amount and the practitioner's valid billed amount are decoded as before.
        assertThat(text).contains("$1500.00");
        // Header (S21), practitioner (S22) and adjustment (S23) fields show their source text.
        assertThat(text).contains("Unparsed Teleplan amount: 12.5", "Unparsed Teleplan amount: +123",
                "Unparsed Teleplan amount: 00012A", "Unparsed Teleplan amount: Gross Amt abc");
        assertThat(text).contains("FAKE PRACTITIONER(91234)", "Adjustments", "Totals:");
        assertThat(text).contains("Known amounts only. Unparsed amount fields excluded: 1");
    }

    @Test
    @DisplayName("should print the remittance summary as before when every stored amount is valid")
    void shouldRenderRemittanceSummaryWithoutNotice_whenStoredAmountsAreValid() throws Exception {
        String text = remittanceSummary("000150000", "000140000", "00014000}", "000140000", "000100");

        assertThat(text).contains("$1500.00", "$1400.00", "$-1400.00", "FAKE PRACTITIONER(91234)", "$1.00");
        assertThat(text).doesNotContain("Unparsed").doesNotContain("nown amounts only");
    }
}
