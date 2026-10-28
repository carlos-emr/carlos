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
package io.github.carlos_emr.carlos.billings.ca.on.web;

import java.io.IOException;
import java.util.Date;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.PatientEndYearStatementSummary;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.billings.ca.on.service.PatientEndYearStatementService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

/**
 * Streams the end-year-statement PDF of the statement the page shows. Create Statement renders
 * the statement's own patient and dates into the form ({@code statementDemographicNo},
 * {@code statementFromDate}, {@code statementToDate}), and Print PDF prints exactly that
 * statement, recomputed for this request, even if the dates above it were changed since without
 * creating it again. A request without those fields prints the patient and dates the form's
 * fields name. If that is no single patient, or a date is malformed, sends HTTP 400; a
 * failure while computing or rendering the statement sends HTTP 500 before the response is
 * committed. Always returns {@code NONE} after handling the response so Struts cannot append a
 * page to the PDF.
 *
 * <p>It used to print the {@code summary} the last search had left in the session, i.e. the
 * patient last searched in any window, under this form's dates (issue #4673).</p>
 *
 * <p>Split out of the legacy {@code PatientEndYearStatement2Action#handlePdf}
 * so the {@code endYearStatement/pdf} URL has a single responsibility.</p>
 *
 * @since 2026-04-27
 */
public class PrintEndYearStatementPdf2Action extends ActionSupport {

    private static final String PDF_FILENAME_BASE = "end_year_statement_report";

    /** The shown statement's patient, as Create Statement renders it: a chart number. */
    private static final Pattern STATEMENT_PATIENT = Pattern.compile("\\d{1,9}");

    private final SecurityInfoManager securityInfoManager;
    private final PatientEndYearStatementService statementService;

    public PrintEndYearStatementPdf2Action(SecurityInfoManager securityInfoManager,
                                           PatientEndYearStatementService statementService) {
        this.securityInfoManager = securityInfoManager;
        this.statementService = statementService;
    }

    @Override
    public String execute() throws IOException {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "r", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        PatientEndYearStatements.echoNames(request);

        // The statement on the page, never the statement another window searched last. The page
        // names it in its statement* fields: its date fields can be changed in place without
        // pressing Create Statement again, and the PDF must be the statement on screen.
        boolean shown = statementDemographicNo != null && !statementDemographicNo.isEmpty();
        if (shown && !STATEMENT_PATIENT.matcher(statementDemographicNo).matches()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }
        String demographicNo = shown ? statementDemographicNo : getDemographicNoParam();
        String from = shown ? statementFromDate : getFromDateParam();
        String to = shown ? statementToDate : getToDateParam();
        PatientEndYearStatementSummary summary;
        try {
            Date fromDate = PatientEndYearStatements.parseIso(from, "from date");
            Date toDate = PatientEndYearStatements.parseIso(to, "to date");
            Demographic demographic = statementService.findUniquePatient(loggedInInfo, demographicNo,
                    shown ? null : getFirstNameParam(), shown ? null : getLastNameParam());
            summary = statementService.aggregateInvoices(demographic, fromDate, toDate).summary();
        } catch (PatientEndYearStatements.InvalidDateFilterException ex) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, ex.getMessage());
            return NONE;
        } catch (PatientEndYearStatementService.Failure ex) {
            PatientEndYearStatements.logFailure(ex, demographicNo);
            // No such patient (or more than one) is the request's; anything else, a database
            // failure while totalling the invoices, is the server's.
            boolean requestFault = ex.reason() == PatientEndYearStatementService.Reason.PATIENT_NOT_FOUND
                    || ex.reason() == PatientEndYearStatementService.Reason.PATIENT_NOT_UNIQUE;
            response.sendError(requestFault ? HttpServletResponse.SC_BAD_REQUEST
                    : HttpServletResponse.SC_INTERNAL_SERVER_ERROR, getText(ex.reason().i18nKey()));
            return NONE;
        }
        try {
            statementService.writePdfResponse(response, PDF_FILENAME_BASE, summary, from, to);
        } catch (PatientEndYearStatementService.Failure ex) {
            PatientEndYearStatements.logFailure(ex, demographicNo);
            if (!response.isCommitted()) {
                response.reset();
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, getText(ex.reason().i18nKey()));
            }
            return NONE;
        }
        // Bypass Struts result-rendering — the PDF body is already on the wire.
        return NONE;
    }

    private String firstNameParam;
    private String lastNameParam;
    private String fromDateParam;
    private String toDateParam;
    private String demographicNoParam;

    public String getFirstNameParam() { return firstNameParam; }
    @StrutsParameter
    public void setFirstNameParam(String firstNameParam) { this.firstNameParam = firstNameParam; }

    public String getLastNameParam() { return lastNameParam; }
    @StrutsParameter
    public void setLastNameParam(String lastNameParam) { this.lastNameParam = lastNameParam; }

    public String getFromDateParam() { return fromDateParam; }
    @StrutsParameter
    public void setFromDateParam(String fromDateParam) { this.fromDateParam = fromDateParam; }

    public String getToDateParam() { return toDateParam; }
    @StrutsParameter
    public void setToDateParam(String toDateParam) { this.toDateParam = toDateParam; }

    public String getDemographicNoParam() { return demographicNoParam; }
    @StrutsParameter
    public void setDemographicNoParam(String demographicNoParam) { this.demographicNoParam = demographicNoParam; }

    private String statementDemographicNo;
    private String statementFromDate;
    private String statementToDate;

    /** @return the patient of the statement the page shows, or null when it shows none */
    public String getStatementDemographicNo() { return statementDemographicNo; }
    @StrutsParameter
    public void setStatementDemographicNo(String statementDemographicNo) { this.statementDemographicNo = statementDemographicNo; }

    /** @return the start date of the statement the page shows ({@code yyyy-MM-dd}, or empty for none) */
    public String getStatementFromDate() { return statementFromDate; }
    @StrutsParameter
    public void setStatementFromDate(String statementFromDate) { this.statementFromDate = statementFromDate; }

    /** @return the end date of the statement the page shows ({@code yyyy-MM-dd}, or empty for none) */
    public String getStatementToDate() { return statementToDate; }
    @StrutsParameter
    public void setStatementToDate(String statementToDate) { this.statementToDate = statementToDate; }
}
