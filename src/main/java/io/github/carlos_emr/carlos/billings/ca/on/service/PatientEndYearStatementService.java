/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperReport;
import io.github.carlos_emr.carlos.PMmodule.utility.Utility;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.PatientEndYearStatementSummary;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.PatientEndYearStatementInvoice;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.PatientEndYearStatementServiceLine;
import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.BillingONItemDao;
import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.BillingONItem;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
// NOTE: tx is writable (not readOnly = true). This service's reads are
// dominant, but DemographicManager.searchDemographic — called from
// findUniquePatient — writes an audit row via LogAction. Marking the
// outer tx readOnly = true caused that audit insert to fail with
// "Connection is read-only", silently dropping a PHI-access audit
// (regression caught 2026-04-28 during Playwright sweep).
/**
 * Side-effect operations behind the patient end-year-statement workflow.
 *
 * <p>Three responsibilities, separated so the web tier never sees DAO loops
 * or {@code java.sql.*} types:</p>
 *
 * <ul>
 *   <li>{@link #findUniquePatient} — resolve a single demographic from
 *       request input (either an explicit {@code demographic_no} or a
 *       {@code lastName,firstName} pair). Throws {@link Failure} when the
 *       lookup yields zero or many candidates so the action can surface a
 *       specific i18n error.</li>
 *   <li>{@link #aggregateInvoices} — for the resolved patient, iterate the
 *       non-deleted PAT billings in the date range, walk their items, and tally
 *       invoiced/paid totals into a {@link PatientEndYearStatementSummary}.</li>
 *   <li>{@link #writePdfTo} — render the JasperReports PDF to the response
 *       output stream. This is the path that previously held a
 *       {@code LegacyJdbcQuery.getConnection()} call inside
 *       {@code PatientEndYearStatement2Action}; the connection lifecycle now
 *       lives entirely below the web tier.</li>
 * </ul>
 *
 * @since 2026-04-26
 */
@org.springframework.stereotype.Service
@org.springframework.transaction.annotation.Transactional
public class PatientEndYearStatementService {

    // These are bundled classpath resources, not configurable external filesystem locations.
    @SuppressWarnings("java:S1075")
    private static final String JASPER_REPORT_PATH =
            "/oscar/oscarBilling/ca/on/reports/end_year_statement_report.jrxml";
    @SuppressWarnings("java:S1075")
    private static final String JASPER_SUBREPORT_PATH =
            "/oscar/oscarBilling/ca/on/reports/end_year_statement_subreport.jrxml";
    private static final String PAT_BILLING_TYPE = "PAT";
    private static final String REPORT_ERROR_KEY = "errors.billing.ca.on.database";

    private final BillingONCHeader1Dao headerDao;
    private final BillingONItemDao itemDao;
    private final DemographicManager demographicManager;

    PatientEndYearStatementService(BillingONCHeader1Dao headerDao,
                                   BillingONItemDao itemDao,
                                   DemographicManager demographicManager) {
        this.headerDao = headerDao;
        this.itemDao = itemDao;
        this.demographicManager = demographicManager;
    }

    /**
     * Resolve the single demographic the report is for.
     *
     * @param demographicNo explicit chart number — preferred when present
     * @param firstName     first-name fragment (used only when {@code demographicNo} is blank)
     * @param lastName      last-name fragment (used only when {@code demographicNo} is blank)
     * @throws Failure with reason {@link Reason#PATIENT_NOT_FOUND} when no
     *                 candidate matches, or {@link Reason#PATIENT_NOT_UNIQUE}
     *                 when the name search returns more than one row
     */
    public Demographic findUniquePatient(LoggedInInfo loggedInInfo,
                                         String demographicNo,
                                         String firstName,
                                         String lastName) {
        List<Demographic> candidates = new ArrayList<>();
        if (demographicNo != null && !demographicNo.isEmpty()) {
            Demographic d = demographicManager.getDemographic(loggedInInfo, demographicNo);
            if (d != null) {
                candidates.add(d);
            }
        } else {
            // Without demographicNo we need at least a non-empty last name
            // (DemographicManager.searchDemographic("," + something) is a
            // wildcard match that hits every patient — we want a clear
            // "patient not found" instead of a crash or a "not unique").
            String safeLast = lastName == null ? "" : lastName.trim();
            String safeFirst = firstName == null ? "" : firstName.trim();
            if (safeLast.isEmpty() && safeFirst.isEmpty()) {
                throw new Failure(Reason.PATIENT_NOT_FOUND);
            }
            List<Demographic> matches = demographicManager.searchDemographic(
                    loggedInInfo, safeLast + "," + safeFirst);
            if (matches != null) {
                candidates.addAll(matches);
            }
        }
        if (candidates.isEmpty()) {
            throw new Failure(Reason.PATIENT_NOT_FOUND);
        }
        if (candidates.size() > 1) {
            throw new Failure(Reason.PATIENT_NOT_UNIQUE);
        }
        return candidates.get(0);
    }

    /**
     * Iterate the patient's PAT-billed invoices in the given date range,
     * walk their service-code items, and tally invoiced/paid totals.
     *
     * <p>Returned {@link Result#summary} carries demographic identity
     * (already populated) plus the running totals; {@link Result#invoices}
     * is the per-invoice list rendered into the JSP table body.</p>
     *
     * @throws Failure with {@link Reason#DATABASE_ERROR} if any DAO call
     *                 throws — wraps the cause for logging on the action.
     */
    public Result aggregateInvoices(Demographic demographic, Date fromDate, Date toDate) {
        List<PatientEndYearStatementInvoice> invoices = new ArrayList<>();
        BigDecimal totalInvoiced = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;
        int invoiceCount = 0;

        try {
            List<Object[]> rows = headerDao.findBillingsAndDemographicsByDemoIdAndDates(
                    demographic.getDemographicNo(), PAT_BILLING_TYPE, fromDate, toDate);
            List<BillingONCHeader1> headers = rows.stream()
                    .map(row -> (BillingONCHeader1) row[0])
                    .toList();
            List<Integer> invoiceIds = headers.stream()
                    .map(BillingONCHeader1::getId)
                    .toList();
            Map<Integer, List<BillingONItem>> itemsByInvoice = itemDao.findByCh1IdsExcludingDeleted(invoiceIds)
                    .stream()
                    .collect(Collectors.groupingBy(BillingONItem::getCh1Id));
            for (Object[] row : rows) {
                BillingONCHeader1 header = (BillingONCHeader1) row[0];
                BigDecimal paid = header.getPaid();
                BigDecimal invoiced = header.getTotal();

                List<PatientEndYearStatementServiceLine> services = new ArrayList<>();
                for (BillingONItem item : itemsByInvoice.getOrDefault(header.getId(), List.of())) {
                    services.add(new PatientEndYearStatementServiceLine(
                            item.getServiceCode(), Utility.toCurrency(item.getFee())));
                }

                invoices.add(new PatientEndYearStatementInvoice(
                        header.getId(), header.getBillingDate(),
                        formatMoney(invoiced), formatMoney(paid),
                        services));

                totalInvoiced = totalInvoiced.add(invoiced);
                totalPaid = totalPaid.add(paid);
                invoiceCount++;
            }
        } catch (RuntimeException e) {
            throw new Failure(Reason.DATABASE_ERROR, e);
        }

        // Build the summary in one shot now that we have all the totals.
        PatientEndYearStatementSummary summary = PatientEndYearStatementSummary.builder()
                .patientNo(demographic.getDemographicNo().toString())
                .patientName(demographic.getFormattedName())
                .hin(demographic.getHin())
                .address(demographic.getAddress() + " "
                        + demographic.getCity() + " " + demographic.getProvince())
                .phone(demographic.getPhone() + " " + demographic.getPhone2())
                .invoiced(formatMoney(totalInvoiced))
                .paid(formatMoney(totalPaid))
                .count(Integer.toString(invoiceCount))
                .fromDate(fromDate)
                .toDate(toDate)
                .build();
        return new Result(summary, invoices);
    }

    /**
     * Render the end-year-statement Jasper PDF for the given (already
     * aggregated) summary directly to {@code out}. Owns the JDBC connection
     * lifecycle: obtains the report connection only inside this method
     * and releases it through the legacy Spring-managed JDBC boundary when the
     * report has been filled.
     *
     * @param fromDateParam ISO date string echoed into the report header
     * @param toDateParam   ISO date string echoed into the report header
     * @throws Failure {@link Reason#DATABASE_ERROR} if no JDBC connection can be acquired,
     *                 or {@link Reason#PDF_ERROR} if compilation, filling or export fails
     */
    // JasperReports needs a raw java.sql.Connection to execute the report's
    // embedded SQL queries; routing through the JPA EntityManager would
    // require rewriting the report engine's data source, not just the
    // connection acquisition.
    public void writePdfTo(OutputStream out, PatientEndYearStatementSummary summary,
                           String fromDateParam, String toDateParam) {
        HashMap<String, Object> reportParams = buildReportParams(summary, fromDateParam, toDateParam);
        try {
            JasperReport report = compileReport(JASPER_REPORT_PATH);
            reportParams.put("SUBREPORT", compileReport(JASPER_SUBREPORT_PATH));
            try (Connection dbConn = LegacyJdbcQuery.getConnection()) {
                var filledReport = JasperFillManager.fillReport(report, reportParams, dbConn);
                JasperExportManager.exportReportToPdfStream(filledReport, out);
            }
        } catch (SQLException ex) {
            throw new Failure(Reason.DATABASE_ERROR, ex);
        } catch (JRException | IOException | RuntimeException ex) {
            throw new Failure(Reason.PDF_ERROR, ex);
        }
    }

    private static JasperReport compileReport(String path) throws IOException, JRException {
        try (InputStream report = PatientEndYearStatementService.class.getResourceAsStream(path)) {
            if (report == null) {
                throw new IOException("Missing statement template: " + path);
            }
            return JasperCompileManager.compileReport(report);
        }
    }

    private static String formatMoney(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * Render and validate the complete PDF before setting download headers or
     * touching the servlet output stream. Generation failures leave the response clean.
     */
    public void writePdfResponse(jakarta.servlet.http.HttpServletResponse response,
                                 String filenameWithoutExt,
                                 PatientEndYearStatementSummary summary,
                                 String fromDateParam,
                                 String toDateParam) {
        var buffer = new ByteArrayOutputStream();
        writePdfTo(buffer, summary, fromDateParam, toDateParam);
        byte[] pdf = buffer.toByteArray();
        if (pdf.length < 5 || !"%PDF-".equals(new String(pdf, 0, 5, StandardCharsets.US_ASCII))) {
            throw new Failure(Reason.PDF_ERROR, new IOException("Statement renderer returned invalid PDF data"));
        }
        configurePdfResponseHeaders(response, filenameWithoutExt);
        response.setContentLength(pdf.length);
        try {
            response.getOutputStream().write(pdf);
        } catch (IOException e) {
            throw new Failure(Reason.IO_ERROR, e);
        }
    }

    private HashMap<String, Object> buildReportParams(PatientEndYearStatementSummary summary,
                                                      String fromDateParam, String toDateParam) {
        HashMap<String, Object> p = new HashMap<>();
        p.put("patientId", summary.getPatientNo());
        p.put("patientName", summary.getPatientName());
        p.put("hin", summary.getHin());
        p.put("address", summary.getAddress());
        p.put("phone", summary.getPhone());
        p.put("fromDate", fromDateParam);
        p.put("toDate", toDateParam);
        p.put("invoiceCount", summary.getCount());
        p.put("totalInvoiced", summary.getInvoiced());
        p.put("totalPaid", summary.getPaid());
        return p;
    }

    private static void configurePdfResponseHeaders(jakarta.servlet.http.HttpServletResponse response,
                                                    String filenameWithoutExt) {
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition",
                "attachment;filename=" + filenameWithoutExt + ".pdf");
    }

    /**
     * Aggregated result of {@link #aggregateInvoices}.
     */
    public record Result(PatientEndYearStatementSummary summary,
                         List<PatientEndYearStatementInvoice> invoices) {
        /** Defensive copy on the way in — the assembler hands us its live
         *  ArrayList and we must not let a JSP mutate the persisted snapshot. */
        public Result {
            invoices = invoices == null ? List.of() : List.copyOf(invoices);
        }
    }

    /**
     * Reasons {@link Failure} is thrown. Each maps to an i18n key the
     * action surfaces via {@code addActionError(getText(...))}.
     */
    public enum Reason {
        PATIENT_NOT_FOUND("error.billingReport.invalidPatientName"),
        PATIENT_NOT_UNIQUE("error.billingReport.notSelectivePatientName"),
        DATABASE_ERROR(REPORT_ERROR_KEY),
        IO_ERROR(REPORT_ERROR_KEY),
        PDF_ERROR(REPORT_ERROR_KEY);

        private final String i18nKey;

        Reason(String i18nKey) {
            this.i18nKey = i18nKey;
        }

        public String i18nKey() {
            return i18nKey;
        }
    }

    /**
     * Checked-style RuntimeException carrying a typed reason. Distinct
     * from {@code BillingValidationException} so this report's lookup
     * outcomes (not-found / not-unique / DB / IO) get their own i18n
     * routing in the action's catch block.
     */
    public static final class Failure extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final Reason reason;

        public Failure(Reason reason) {
            super(reason.name());
            this.reason = reason;
        }

        public Failure(Reason reason, Throwable cause) {
            super(reason.name(), cause);
            this.reason = reason;
        }

        public Reason reason() {
            return reason;
        }
    }
}
