/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.report.export;

import java.io.IOException;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;

/**
 * Parameterized JDBC reads for the tabular MSP exports; the caller owns the connection.
 *
 * @since 2026-11-02
 */
public final class BcRemittanceExportDao {
    private final Connection connection;

    /**
     * Uses the supplied connection without closing it after an export.
     *
     * @param connection Connection to the billing database, owned by the caller
     */
    public BcRemittanceExportDao(Connection connection) {
        this.connection = connection;
    }

    /**
     * Writes billed claim rows for one exact payee and remittance, with one CSV header.
     * No matching claims produces a header-only file.
     *
     * @param output OutputStream receiving UTF-8 CSV; closed when generation finishes
     * @param payee String literal Teleplan payee identifier, matched exactly
     * @param s21 String remittance S21 identifier, bound as a query parameter
     * @throws SQLException if the billing query fails
     * @throws IOException if CSV output fails
     * @throws java.time.DateTimeException if a report date is invalid
     * @throws NumberFormatException if a Teleplan amount is invalid
     * @throws ArithmeticException if a Teleplan amount contains fractional cents
     */
    public void writeBilled(OutputStream output, String payee, String s21) throws SQLException, IOException {
        String sql = """
                SELECT p.first_name, p.last_name, t.t_s00type, bm.service_date, t.t_payment,
                       t.t_datacenter, b.demographic_name, b.demographic_no, t.t_paidamt,
                       t.t_exp1, t.t_dataseq, bm.billing_code
                  FROM teleplanS00 t
                  JOIN billingmaster bm ON t.t_officeno = bm.billingmaster_no
                  JOIN billing b ON bm.billing_no = b.billing_no
                  JOIN provider p ON p.ohip_no = t.t_practitionerno
                 WHERE t.s21_id = ? AND t.t_practitionerno <> '' AND t.t_payeeno = ?
                 ORDER BY p.first_name, bm.service_date, b.demographic_name
                """;
        try (var csv = new TabularExport(output, "Account", "Type", "ServDate", "Payment Date", "Data Centre", "Patient",
                "Paid (CAD)", "Explanation", "Sequence", "Item"); var statement = connection.prepareStatement(sql)) {
            statement.setString(1, s21);
            statement.setString(2, payee);
            try (var rs = statement.executeQuery()) {
                while (rs.next()) csv.row(BcBillingCsv.initial(rs.getString(1)) + BcBillingCsv.initial(rs.getString(2)),
                        rs.getString(3), BcBillingCsv.date(rs.getString(4)), BcBillingCsv.date(rs.getString(5)),
                        rs.getString(6), text(rs.getString(7)) + "(" + text(rs.getString(8)) + ")",
                        decodedAmount(rs.getString(9)), rs.getString(10), rs.getString(11), rs.getString(12));
            }
        }
    }

    /**
     * Writes one header and discriminated S21/S22/S23/S25 rows without a Jasper layout grid.
     * Deleted remittances and practitioner rows are excluded; orphan adjustments remain included.
     *
     * @param output OutputStream receiving UTF-8 CSV; closed when generation finishes
     * @param s21 String remittance S21 identifier, bound in every query
     * @throws SQLException if a query fails or the remittance is unavailable or deleted
     * @throws IOException if CSV output fails
     * @throws java.time.DateTimeException if a report date is invalid
     * @throws NumberFormatException if a Teleplan amount is invalid
     * @throws ArithmeticException if a Teleplan amount contains fractional cents
     */
    public void writeSummary(OutputStream output, String s21) throws SQLException, IOException {
        String[] headers = {"Record Type", "Payment Date", "Payee No", "Payee Name", "Billed (CAD)", "Paid (CAD)",
                "Cheque (CAD)", "Practitioner No", "Practitioner Name", "Adjustment Code", "Adjustment Description",
                "Calculation Method", "Regular Percent", "One-time Percent", "Gross (CAD)", "Regular (CAD)", "One-time (CAD)",
                "Balance Forward (CAD)", "Adjustment Made (CAD)", "Adjustment Outstanding (CAD)", "Line Code", "Message"};
        try (var csv = new TabularExport(output, headers)) {
            String payee;
            try (var st = connection.prepareStatement("""
                    SELECT t_payment, t_payeeno, t_payeename, t_amtbilled, t_amtpaid, t_cheque
                      FROM teleplanS21 WHERE status <> 'D' AND s21_id = ? ORDER BY t_payment DESC
                    """)) {
                st.setString(1, s21);
                try (var rs = st.executeQuery()) {
                    if (!rs.next()) throw new SQLException("Remittance not available");
                    var row = row("Remittance");
                    row[1] = BcBillingCsv.date(rs.getString(1)); row[2] = rs.getString(2); row[3] = rs.getString(3);
                    payee = rs.getString(2);
                    row[4] = decodedAmount(rs.getString(4)); row[5] = decodedAmount(rs.getString(5)); row[6] = decodedAmount(rs.getString(6));
                    csv.row(row);
                }
            }
            try (var st = connection.prepareStatement("""
                    SELECT t_payment, t_payeeno, t_practitionerno, t_practitionername, t_amtbilled, t_amtpaid
                      FROM teleplanS22 WHERE s21_id = ? AND t_payeeno = ? AND t_s22type <> 'D' ORDER BY s22_id
                    """)) {
                st.setString(1, s21);
                st.setString(2, payee);
                try (var rs = st.executeQuery()) {
                    while (rs.next()) {
                        var row = row("Practitioner"); row[1] = BcBillingCsv.date(rs.getString(1)); row[2] = rs.getString(2);
                        row[7] = rs.getString(3); row[8] = rs.getString(4); row[4] = decodedAmount(rs.getString(5)); row[5] = decodedAmount(rs.getString(6));
                        csv.row(row);
                    }
                }
            }
            try (var st = connection.prepareStatement("""
                    SELECT a.t_aji, a.t_ajc, c.adj_desc, a.t_calcmethod, a.t_rpercent, a.t_opercent,
                           a.t_gamount, a.t_ramount, a.t_oamount, a.t_balancefwd, a.t_adjmade, a.t_adjoutstanding
                      FROM teleplanS23 a LEFT JOIN teleplan_adj_codes c ON a.t_ajc = c.adj_code
                     WHERE a.s21_id = ? AND a.t_s23type = 'S23' ORDER BY a.s23_id
                    """)) {
                st.setString(1, s21);
                try (var rs = st.executeQuery()) {
                    while (rs.next()) {
                        var row = row("Adjustment"); row[7] = rs.getString(1);
                        for (int i = 2; i <= 6; i++) row[i + 7] = rs.getString(i);
                        for (int i = 7; i <= 12; i++) row[i + 7] = decodedAmount(rs.getString(i));
                        csv.row(row);
                    }
                }
            }
            try (var st = connection.prepareStatement("""
                    SELECT t_practitionerno, t_linecode, t_message FROM teleplanS25 WHERE s21_id = ? ORDER BY s25_id
                    """)) {
                st.setString(1, s21);
                try (var rs = st.executeQuery()) {
                    while (rs.next()) {
                        var row = row("Message"); row[7] = rs.getString(1); row[20] = rs.getString(2); row[21] = rs.getString(3);
                        csv.row(row);
                    }
                }
            }
        }
    }

    private static Object[] row(String type) {
        Object[] row = new Object[22];
        Arrays.fill(row, "");
        row[0] = type;
        return row;
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
    /** Keeps malformed legacy fields visible; SQL/I/O failures still abort the staged download. */
    private static String decodedAmount(String value) {
        try { return TeleplanAmount.decode(value); }
        catch (NumberFormatException invalid) {
            return "Unparsed Teleplan amount: " + (value == null ? "" : value);
        }
    }

}
