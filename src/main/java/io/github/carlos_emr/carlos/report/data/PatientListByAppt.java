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

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
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
    private static final ExportScope EMPTY_EXPORT_SCOPE =
            new ExportScope(null, null, null);

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
     * Validates and processes one export request. Rows are streamed from the DAO
     * into an owner-only spool file before the response is committed, preventing a
     * database failure from returning a successful but truncated export. The
     * container-owned response stream deliberately remains open.
     */
    private void processExportRequest(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null) {
            UnauthenticatedRejectionResolver.rejectUnauthenticatedRequest(request, response);
            // The shared resolver emits a sanitized rejection event. Do not add a
            // synchronous database insert here: this public route would otherwise
            // let anonymous traffic amplify writes to the clinical audit database.
            return;
        }

        if (!hasReportPrivilege(loggedInInfo)) {
            rejectExport(response, loggedInInfo, HttpServletResponse.SC_FORBIDDEN,
                    "Report read privilege is required", "Forbidden", EMPTY_EXPORT_SCOPE);
            return;
        }

        String providerFilter = request.getParameter("provider_no");
        if (providerFilter == null || providerFilter.isBlank()) {
            rejectExport(response, loggedInInfo, HttpServletResponse.SC_BAD_REQUEST,
                    "provider_no is required", "MissingProvider", EMPTY_EXPORT_SCOPE);
            return;
        }
        if (!providerFilter.equals("all")
                && !PROVIDER_FILTER_PATTERN.matcher(providerFilter).matches()) {
            rejectExport(response, loggedInInfo, HttpServletResponse.SC_BAD_REQUEST,
                    "provider_no is invalid", "InvalidProvider", EMPTY_EXPORT_SCOPE);
            return;
        }
        // clear dr no value for all doc's
        String providerNo = providerFilter.equals("all") ? null : providerFilter;
        String datefrom = request.getParameter("date_from");
        String dateto = request.getParameter("date_to");

        Date from = parseRequiredDate(datefrom);
        Date to = parseRequiredDate(dateto);
        ExportScope validatedScope = new ExportScope(
                providerFilter, canonicalDate(from), canonicalDate(to));
        if (from == null || to == null) {
            rejectExport(response, loggedInInfo, HttpServletResponse.SC_BAD_REQUEST,
                    "date_from and date_to must use YYYY-MM-DD", "InvalidDate",
                    validatedScope);
            return;
        }
        if (from.after(to)) {
            rejectExport(response, loggedInInfo, HttpServletResponse.SC_BAD_REQUEST,
                    "date_from must not be after date_to", "ReversedDateRange",
                    validatedScope);
            return;
        }

        int[] rowCount = {0};
        String outcome = "error";
        String errorType = null;
        Path spoolFile = null;

        try {
            spoolFile = createExportSpoolFile();
            OscarAppointmentDao dao = getAppointmentDao();
            try (BufferedWriter writer = Files.newBufferedWriter(
                    spoolFile, StandardCharsets.UTF_8)) {
                dao.streamPatientAppointments(providerNo, from, to, row -> {
                    try {
                        writer.write(formatCsvRow(row));
                        rowCount[0]++;
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }

            // Commit the response only after the DAO cursor and spool write have
            // completed, so a database failure cannot look like a valid truncated
            // HTTP 200 export to the caller.
            response.setContentType("text/plain; charset=UTF-8");
            response.setHeader("Content-disposition", "attachment; filename=patientlist.txt");
            Files.copy(spoolFile, response.getOutputStream());
            outcome = "success";
        } catch (IOException | RuntimeException e) {
            errorType = e.getClass().getSimpleName();
            throw e;
        } finally {
            deleteExportSpoolFile(spoolFile);
            auditExport(loggedInInfo, validatedScope.providerFilter(),
                    validatedScope.dateFrom(), validatedScope.dateTo(),
                    rowCount[0], outcome, errorType);
        }
    }

    /** Test seam for exercising infrastructure failures during DAO resolution. */
    protected OscarAppointmentDao getAppointmentDao() {
        return SpringUtils.getBean(OscarAppointmentDao.class);
    }

    /** Test seam for exercising authorization infrastructure failures. */
    protected SecurityInfoManager getSecurityInfoManager() {
        return SpringUtils.getBean(SecurityInfoManager.class);
    }

    private boolean hasReportPrivilege(LoggedInInfo loggedInInfo) {
        try {
            return getSecurityInfoManager().hasPrivilege(
                    loggedInInfo, "_report,_admin.reporting", "r", null);
        } catch (RuntimeException e) {
            auditExport(loggedInInfo, null, null, null,
                    0, "error", e.getClass().getSimpleName());
            throw e;
        }
    }

    private static String formatCsvRow(PatientAppointmentExportRow row) {
        StringBuilder csv = new StringBuilder();
        csv.append(escapeCsv(row.patientLastName())).append(',');
        csv.append(escapeCsv(row.patientFirstName())).append(',');
        csv.append(escapeCsv(row.phone())).append(',');
        csv.append(escapeCsv(row.alternatePhone())).append(',');
        csv.append(ConversionUtils.toTimeString(row.startTime())).append(',');
        csv.append(ConversionUtils.toDateString(row.appointmentDate())).append(',');
        // Appointment type is free text and optional. Strip CR and LF
        // individually — the legacy replaceAll("\r\n", "") only matched
        // CRLF pairs, so a lone newline survived, escapeCsv quoted the
        // field, and one appointment spilled across two output lines.
        // Objects.toString keeps this null-safe independently of the
        // entity getter's own null coalescing.
        String appointmentType = Objects.toString(row.appointmentType(), "")
                .replace("\r", "")
                .replace("\n", "");
        csv.append(escapeCsv(appointmentType)).append(',');
        csv.append(escapeCsv(formatProviderName(
                row.providerFirstName(), row.providerLastName()))).append(',');
        csv.append(escapeCsv(row.location())).append('\n');
        return csv.toString();
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
                              int status, String message, String reason,
                              ExportScope validatedScope) throws IOException {
        response.sendError(status, message);
        auditExport(loggedInInfo, validatedScope.providerFilter(),
                validatedScope.dateFrom(), validatedScope.dateTo(),
                0, "rejected", reason);
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

    private static String canonicalDate(Date value) {
        return value == null ? null : ConversionUtils.toDateString(value);
    }

    private static Path createExportSpoolFile() throws IOException {
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            return Files.createTempFile("carlos-patient-export-", ".csv",
                    PosixFilePermissions.asFileAttribute(
                            PosixFilePermissions.fromString("rw-------")));
        }
        return Files.createTempFile("carlos-patient-export-", ".csv");
    }

    private static void deleteExportSpoolFile(Path spoolFile) {
        if (spoolFile == null) {
            return;
        }
        try {
            Files.deleteIfExists(spoolFile);
        } catch (IOException e) {
            log.error("Unable to delete patient export spool file", e);
        }
    }

    private record ExportScope(String providerFilter, String dateFrom, String dateTo) {
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
