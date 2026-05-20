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
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.github.carlos_emr.OscarDocumentCreator;
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
import io.github.carlos_emr.carlos.utility.DbConnectionFilter;
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
 *       PAT-status billings in the date range, walk their items, and tally
 *       invoiced/paid totals into a {@link PatientEndYearStatementSummary}.</li>
 *   <li>{@link #writePdfTo} — render the JasperReports PDF to the response
 *       output stream. This is the path that previously held a
 *       {@code DbConnectionFilter.getThreadLocalDbConnection()} call inside
 *       {@code PatientEndYearStatement2Action}; the connection lifecycle now
 *       lives entirely below the web tier.</li>
 * </ul>
 *
 * @since 2026-04-26
 */
@org.springframework.stereotype.Service
@org.springframework.transaction.annotation.Transactional
public class PatientEndYearStatementService {

    private static final String JASPER_REPORT_PATH =
            "/oscar/oscarBilling/ca/on/reports/end_year_statement_report.jrxml";
    private static final String JASPER_SUBREPORT_DIR =
            "/oscar/oscarBilling/ca/on/reports/";
    private static final String PAT_BILLING_TYPE = "PAT";

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
        double totalInvoiced = 0;
        double totalPaid = 0;
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
            Map<Integer, List<BillingONItem>> itemsByInvoice = itemDao.findByCh1IdsExcludingDeletedAndSettled(invoiceIds)
                    .stream()
                    .collect(Collectors.groupingBy(BillingONItem::getCh1Id));
            for (Object[] row : rows) {
                BillingONCHeader1 header = (BillingONCHeader1) row[0];
                double paid = header.getPaid().doubleValue();
                double invoiced = header.getTotal().doubleValue();

                List<PatientEndYearStatementServiceLine> services = new ArrayList<>();
                for (BillingONItem item : itemsByInvoice.getOrDefault(header.getId(), List.of())) {
                    services.add(new PatientEndYearStatementServiceLine(
                            item.getServiceCode(), Utility.toCurrency(item.getFee())));
                }

                invoices.add(new PatientEndYearStatementInvoice(
                        header.getId(), header.getBillingDate(),
                        String.valueOf(invoiced), String.valueOf(paid),
                        services));

                totalInvoiced += invoiced;
                totalPaid += paid;
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
                .invoiced(Utility.toCurrency(totalInvoiced))
                .paid(Utility.toCurrency(totalPaid))
                .count(Integer.toString(invoiceCount))
                .fromDate(fromDate)
                .toDate(toDate)
                .build();
        return new Result(summary, invoices);
    }

    /**
     * Render the end-year-statement Jasper PDF for the given (already
     * aggregated) summary directly to {@code out}. Owns the JDBC connection
     * lifecycle: pulls the per-thread connection only inside this method
     * and lets it return to the {@code DbConnectionFilter} pool when the
     * filter chain unwinds.
     *
     * @param fromDateParam ISO date string echoed into the report header
     * @param toDateParam   ISO date string echoed into the report header
     * @throws Failure {@link Reason#DATABASE_ERROR} if no JDBC connection
     *                 can be acquired
     */
    // JasperReports needs a raw java.sql.Connection to execute the report's
    // embedded SQL queries; routing through the JPA EntityManager would
    // require rewriting the report engine's data source, not just the
    // connection acquisition. This is the only correct caller of the
    // deprecated thread-local connection in billings/ca/on/.
    @SuppressWarnings("removal")
    public void writePdfTo(OutputStream out, PatientEndYearStatementSummary summary,
                           String fromDateParam, String toDateParam) {
        OscarDocumentCreator osc = new OscarDocumentCreator();
        HashMap<String, Object> reportParams = buildReportParams(summary, fromDateParam, toDateParam);

        InputStream reportStream = osc.getDocumentStream(JASPER_REPORT_PATH);
        try {
            try (Connection dbConn = DbConnectionFilter.getThreadLocalDbConnection()) {
                if (dbConn == null) {
                    throw new Failure(Reason.DATABASE_ERROR);
                }
                osc.fillDocumentStream(reportParams, out, "pdf", reportStream, dbConn);
            } catch (SQLException ex) {
                throw new Failure(Reason.DATABASE_ERROR, ex);
            }
        } finally {
            org.apache.commons.io.IOUtils.closeQuietly(reportStream);
        }
    }

    /**
     * Convenience wrapper for the action: writes Content-Type / disposition
     * headers to {@code response} and then calls {@link #writePdfTo}.
     */
    public void writePdfResponse(jakarta.servlet.http.HttpServletResponse response,
                                 String filenameWithoutExt,
                                 PatientEndYearStatementSummary summary,
                                 String fromDateParam,
                                 String toDateParam) {
        configurePdfResponseHeaders(response, filenameWithoutExt);
        try {
            writePdfTo(response.getOutputStream(), summary, fromDateParam, toDateParam);
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
        p.put("SUBREPORT_DIR", JASPER_SUBREPORT_DIR);
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
        DATABASE_ERROR("errors.billing.ca.on.database"),
        IO_ERROR("errors.billing.ca.on.database");

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
