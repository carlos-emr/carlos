/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.report.data;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.Objects;
import java.util.regex.Pattern;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.appointment.dto.PatientAppointmentExportRow;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.OscarLog;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.sec.UnauthenticatedRejectionResolver;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.util.ConversionUtils;

public class PatientListByAppt extends HttpServlet {
    private static final Pattern PROVIDER_FILTER_PATTERN =
            Pattern.compile("[A-Za-z0-9_-]{1,6}");

    private static final Logger log = MiscUtils.getLogger();

    private static final long serialVersionUID = 1L;

    /**
     * Processes requests for both HTTP <code>GET</code> and <code>POST</code> methods.
     *
     * @param request  servlet request
     * @param response servlet response
     */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            processExportRequest(request, response);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error in PatientListByAppt", e);
            if (!response.isCommitted()) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "An internal error occurred. Please try again or contact your system administrator.");
            }
        }
    }

    /**
     * Validates and streams one export request. The response output stream is
     * container-owned and must remain open; closing it during exception unwinding
     * can commit an empty success response before {@link HttpServletResponse#sendError}
     * can replace it.
     */
    @SuppressWarnings("java:S2093")
    private void processExportRequest(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null) {
            UnauthenticatedRejectionResolver.rejectUnauthenticatedRequest(request, response);
            auditUnauthenticatedExport(request.getRemoteAddr());
            return;
        }

        SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
        if (!securityInfoManager.hasPrivilege(
                loggedInInfo, "_report,_admin.reporting", "r", null)) {
            rejectExport(response, loggedInInfo, HttpServletResponse.SC_FORBIDDEN,
                    "Report read privilege is required", "Forbidden");
            return;
        }

        String providerFilter = request.getParameter("provider_no");
        if (providerFilter == null || providerFilter.isBlank()) {
            rejectExport(response, loggedInInfo, HttpServletResponse.SC_BAD_REQUEST,
                    "provider_no is required", "MissingProvider");
            return;
        }
        if (!providerFilter.equals("all")
                && !PROVIDER_FILTER_PATTERN.matcher(providerFilter).matches()) {
            rejectExport(response, loggedInInfo, HttpServletResponse.SC_BAD_REQUEST,
                    "provider_no is invalid", "InvalidProvider");
            return;
        }
        // clear dr no value for all doc's
        String providerNo = providerFilter.equals("all") ? null : providerFilter;
        String datefrom = request.getParameter("date_from");
        String dateto = request.getParameter("date_to");

        Date from = parseRequiredDate(datefrom);
        Date to = parseRequiredDate(dateto);
        if (from == null || to == null) {
            rejectExport(response, loggedInInfo, HttpServletResponse.SC_BAD_REQUEST,
                    "date_from and date_to must use YYYY-MM-DD", "InvalidDate");
            return;
        }
        if (from.after(to)) {
            rejectExport(response, loggedInInfo, HttpServletResponse.SC_BAD_REQUEST,
                    "date_from must not be after date_to", "ReversedDateRange");
            return;
        }

        response.setContentType("text/plain; charset=UTF-8");
        response.setHeader("Content-disposition", "attachment; filename=patientlist.txt");

        int[] rowCount = {0};
        String outcome = "error";
        String errorType = null;

        try {
            OscarAppointmentDao dao = getAppointmentDao();
            PrintWriter pw = new PrintWriter(new OutputStreamWriter(
                    response.getOutputStream(), StandardCharsets.UTF_8), true);
            dao.streamPatientAppointments(providerNo, from, to, row -> {
                writeCsvRow(pw, row);
                rowCount[0]++;
            });
            if (pw.checkError()) {
                throw new IOException("Unable to complete patient appointment export response");
            }
            outcome = "success";
        } catch (IOException | RuntimeException e) {
            errorType = e.getClass().getSimpleName();
            throw e;
        } finally {
            auditExport(loggedInInfo, providerFilter, datefrom, dateto,
                    rowCount[0], outcome, errorType);
        }
    }

    /** Test seam for exercising infrastructure failures during DAO resolution. */
    protected OscarAppointmentDao getAppointmentDao() {
        return SpringUtils.getBean(OscarAppointmentDao.class);
    }

    // FindSecBugs XSS_SERVLET: this is an attachment-only CSV context and every
    // database string passes through escapeCsv(), including formula protection.
    @SuppressFBWarnings(value = "XSS_SERVLET",
            justification = "attachment-only CSV output; all database strings use escapeCsv")
    private static void writeCsvRow(PrintWriter pw, PatientAppointmentExportRow row) {
        // CSV export rows — each pw.print() carries a full-id nosemgrep because
        // Semgrep Cloud does not honor preceding-line suppressions for this rule.
        // Content-Type is set to text/plain with Content-Disposition: attachment,
        // values pass through escapeCsv(), not an HTML context.
        pw.print(escapeCsv(row.patientLastName()) + ","); // nosemgrep: java.lang.security.audit.xss.no-direct-response-writer.no-direct-response-writer -- CSV download, escapeCsv applied
        pw.print(escapeCsv(row.patientFirstName()) + ","); // nosemgrep: java.lang.security.audit.xss.no-direct-response-writer.no-direct-response-writer -- CSV download, escapeCsv applied
        pw.print(escapeCsv(row.phone()) + ","); // nosemgrep: java.lang.security.audit.xss.no-direct-response-writer.no-direct-response-writer -- CSV download, escapeCsv applied
        pw.print(escapeCsv(row.alternatePhone()) + ","); // nosemgrep: java.lang.security.audit.xss.no-direct-response-writer.no-direct-response-writer -- CSV download, escapeCsv applied
        pw.print(ConversionUtils.toTimeString(row.startTime()) + ","); // nosemgrep: java.lang.security.audit.xss.no-direct-response-writer.no-direct-response-writer -- CSV download, formatted time
        pw.print(ConversionUtils.toDateString(row.appointmentDate()) + ","); // nosemgrep: java.lang.security.audit.xss.no-direct-response-writer.no-direct-response-writer -- CSV download, formatted date
        // Appointment type is free text and optional. Strip CR and LF
        // individually — the legacy replaceAll("\r\n", "") only matched
        // CRLF pairs, so a lone newline survived, escapeCsv quoted the
        // field, and one appointment spilled across two output lines.
        // Objects.toString keeps this null-safe independently of the
        // entity getter's own null coalescing.
        String appointmentType = Objects.toString(row.appointmentType(), "")
                .replace("\r", "")
                .replace("\n", "");
        pw.print(escapeCsv(appointmentType) + ","); // nosemgrep: java.lang.security.audit.xss.no-direct-response-writer.no-direct-response-writer -- CSV download, escapeCsv applied
        pw.print(escapeCsv(formatProviderName(row.providerFirstName(), row.providerLastName())) + ","); // nosemgrep: java.lang.security.audit.xss.no-direct-response-writer.no-direct-response-writer -- CSV download, escapeCsv applied
        pw.print(escapeCsv(row.location())); // nosemgrep: java.lang.security.audit.xss.no-direct-response-writer.no-direct-response-writer -- CSV download, escapeCsv applied
        pw.print("\n"); // nosemgrep: java.lang.security.audit.xss.no-direct-response-writer.no-direct-response-writer -- CSV download literal newline
    }

    private static String formatProviderName(String firstName, String lastName) {
        if (firstName == null || firstName.isEmpty()) {
            return Objects.toString(lastName, "");
        }
        if (lastName == null || lastName.isEmpty()) {
            return firstName;
        }
        return firstName + " " + lastName;
    }

    private void rejectExport(HttpServletResponse response, LoggedInInfo loggedInInfo,
                              int status, String message, String reason) throws IOException {
        response.sendError(status, message);
        // Rejected parameters are deliberately omitted: malformed attacker-controlled
        // values must not become PHI or log-injection content in the audit record.
        auditExport(loggedInInfo, null, null, null, 0, "rejected", reason);
    }

    private void auditUnauthenticatedExport(String remoteAddress) {
        OscarLog auditLog = createExportAuditLog(null, remoteAddress, null);
        persistExportAuditResult(auditLog, null, null, 0,
                "rejected", "Unauthenticated");
    }

    private void auditExport(LoggedInInfo loggedInInfo, String providerFilter,
                             String dateFrom, String dateTo, int rowCount,
                             String outcome, String errorType) {
        OscarLog auditLog = createExportAuditLog(
                loggedInInfo, loggedInInfo.getIp(), providerFilter);
        persistExportAuditResult(
                auditLog, dateFrom, dateTo, rowCount, outcome, errorType);
    }

    private OscarLog createExportAuditLog(LoggedInInfo loggedInInfo,
                                          String remoteAddress,
                                          String providerFilter) {
        OscarLog auditLog = new OscarLog();
        if (loggedInInfo != null && loggedInInfo.getLoggedInSecurity() != null) {
            auditLog.setSecurityId(loggedInInfo.getLoggedInSecurity().getSecurityNo());
        }
        if (loggedInInfo != null && loggedInInfo.getLoggedInProvider() != null) {
            auditLog.setProviderNo(loggedInInfo.getLoggedInProviderNo());
        }
        auditLog.setAction(LogConst.EXPORT);
        auditLog.setContent("patient_list_by_appointment");
        auditLog.setContentId(providerFilter);
        auditLog.setIp(remoteAddress);
        return auditLog;
    }

    private void persistExportAuditResult(OscarLog auditLog, String dateFrom,
                                          String dateTo, int rowCount,
                                          String outcome, String errorType) {
        String data = "dateFrom=" + dateFrom + "; dateTo=" + dateTo
                + "; rows=" + rowCount + "; outcome=" + outcome;
        if (errorType != null) {
            data += "; error=" + errorType;
        }
        auditLog.setData(data);
        persistExportAudit(auditLog);
    }

    /** Test seam for verifying audit contents without persisting them. */
    protected void persistExportAudit(OscarLog auditLog) {
        LogAction.addLogSynchronous(auditLog);
    }

    private static Date parseRequiredDate(String value) {
        if (value == null || !value.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return null;
        }
        try {
            return java.sql.Date.valueOf(LocalDate.parse(value));
        } catch (DateTimeParseException | IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Escapes a value for RFC 4180 CSV output. Wraps the value in double-quotes
     * if it contains commas, double-quotes, or newlines, and escapes embedded
     * double-quotes by doubling them. Also prevents spreadsheet formula injection
     * by prefixing values that start with formula trigger characters (=, +, -, @,
     * tab, carriage return, line feed, NUL, or their full-width variants) with a
     * single quote so spreadsheet applications treat them as literal text rather
     * than formulas.
     *
     * @param value the raw field value; null is treated as an empty string
     * @return the RFC 4180 escaped field value, safe from formula injection
     */
    private static String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        // Prevent spreadsheet formula injection: values starting with these characters
        // are interpreted as formulas by Excel/Google Sheets. Prefix with single-quote
        // to force literal text treatment. Phone numbers (+1...) are a common real case.
        if (!value.isEmpty()) {
            char first = value.charAt(0);
            if (first == '=' || first == '+' || first == '-' || first == '@'
                    || first == '\t' || first == '\r' || first == '\n' || first == '\0'
                    || first == '\uFF1D' || first == '\uFF0B'
                    || first == '\uFF0D' || first == '\uFF20') {
                value = "'" + value;
            }
        }
        boolean needsQuoting = value.contains(",") || value.contains("\"")
                || value.contains("\n") || value.contains("\r");
        String escaped = value.replace("\"", "\"\"");
        return needsQuoting ? "\"" + escaped + "\"" : escaped;
    }

    // <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the + sign on the left to edit the code.">

    /**
     * Handles the HTTP <code>GET</code> method.
     *
     * @param request  servlet request
     * @param response servlet response
     */
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Handles the HTTP <code>POST</code> method.
     *
     * @param request  servlet request
     * @param response servlet response
     */
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Returns a short description of the servlet.
     */
    public String getServletInfo() {
        return "Short description";
    }
    // </editor-fold>
}
