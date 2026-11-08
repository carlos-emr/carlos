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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import io.github.carlos_emr.carlos.commn.dao.FixedJasperReportDao;
import io.github.carlos_emr.carlos.commn.dao.FixedJasperReportDao.InvoiceRow;
import io.github.carlos_emr.carlos.report.FixedJasperDataSources;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperReport;
import io.github.carlos_emr.carlos.PMmodule.utility.Utility;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.PatientEndYearStatementSummary;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.PatientEndYearStatementInvoice;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.PatientEndYearStatementServiceLine;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.managers.DemographicManager;
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
 *       JDBC call inside
 *       {@code PatientEndYearStatement2Action}; the renderer now consumes detached rows.</li>
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

    private final FixedJasperReportDao reportDao;
    private final DemographicManager demographicManager;

    PatientEndYearStatementService(FixedJasperReportDao reportDao,
                                   DemographicManager demographicManager) {
        this.reportDao = reportDao;
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
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Result aggregateInvoices(Demographic demographic, Date fromDate, Date toDate) {
        List<PatientEndYearStatementInvoice> invoices = new ArrayList<>();
        BigDecimal totalInvoiced = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;
        int invoiceCount = 0;

        List<InvoiceRow> rows;
        try {
            rows = reportDao.statement(demographic.getDemographicNo(), sqlDate(fromDate), sqlDate(toDate));
            for (InvoiceRow header : rows) {
                BigDecimal paid = header.paid();
                BigDecimal invoiced = header.total();

                List<PatientEndYearStatementServiceLine> services = new ArrayList<>();
                for (var item : header.items()) {
                    services.add(new PatientEndYearStatementServiceLine(
                            item.serviceCode(), Utility.toCurrency(item.fee())));
                }

                invoices.add(new PatientEndYearStatementInvoice(
                        header.id(), header.billingDate(),
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
        return new Result(summary, invoices, rows);
    }

    /** Render only detached data. Suspending an ambient transaction keeps JDBC out of PDF generation. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void writePdfTo(OutputStream out, Result result,
                           String fromDateParam, String toDateParam) {
        HashMap<String, Object> reportParams = buildReportParams(result.summary(), fromDateParam, toDateParam);
        try {
            JasperReport report = compileReport(JASPER_REPORT_PATH);
            reportParams.put("SUBREPORT", compileReport(JASPER_SUBREPORT_PATH));
            var filledReport = JasperFillManager.fillReport(report, reportParams,
                    FixedJasperDataSources.invoices(result.reportRows()));
            JasperExportManager.exportReportToPdfStream(filledReport, out);
        } catch (JRException | IOException | RuntimeException ex) {
            throw new Failure(Reason.PDF_ERROR, ex);
        }
    }

    private static java.sql.Date sqlDate(Date value) {
        return value == null ? null : new java.sql.Date(value.getTime());
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
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void writePdfResponse(jakarta.servlet.http.HttpServletResponse response,
                                 String filenameWithoutExt,
                                 Result result,
                                 String fromDateParam,
                                 String toDateParam) {
        try {
            io.github.carlos_emr.carlos.report.ReportPdfResponse.write(response,
                    "attachment;filename=" + filenameWithoutExt + ".pdf",
                    output -> writePdfTo(output, result, fromDateParam, toDateParam));
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

    /**
     * Aggregated result of {@link #aggregateInvoices}.
     */
    public record Result(PatientEndYearStatementSummary summary,
                         List<PatientEndYearStatementInvoice> invoices,
                         List<InvoiceRow> reportRows) {
        /** Defensive copy on the way in — the assembler hands us its live
         *  ArrayList and we must not let a JSP mutate the persisted snapshot. */
        public Result {
            reportRows = List.copyOf(reportRows);
            invoices = List.copyOf(invoices);
            if (invoices.size() != reportRows.size()) {
                throw new IllegalArgumentException("Statement detail count does not match its snapshot");
            }
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
