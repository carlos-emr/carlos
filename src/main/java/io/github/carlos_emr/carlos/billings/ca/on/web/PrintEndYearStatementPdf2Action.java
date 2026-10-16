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
 * Streams the end-year-statement PDF for the patient and date range the statement page's form
 * posts (its "Print PDF" button submits the same fields as "Create Statement"). The statement is
 * computed for this request, as the search computes it. If the form names no single patient or
 * a malformed date, sends HTTP 400. Rendering failures send HTTP 500 before the response is
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

        // This form's own patient and dates, never the statement another window searched last.
        PatientEndYearStatementSummary summary;
        try {
            Date fromDate = PatientEndYearStatements.parseIso(getFromDateParam(), "from date");
            Date toDate = PatientEndYearStatements.parseIso(getToDateParam(), "to date");
            Demographic demographic = statementService.findUniquePatient(
                    loggedInInfo, getDemographicNoParam(), getFirstNameParam(), getLastNameParam());
            summary = statementService.aggregateInvoices(demographic, fromDate, toDate).summary();
        } catch (PatientEndYearStatements.InvalidDateFilterException ex) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, ex.getMessage());
            return NONE;
        } catch (PatientEndYearStatementService.Failure ex) {
            PatientEndYearStatements.logFailure(ex, getDemographicNoParam());
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, getText(ex.reason().i18nKey()));
            return NONE;
        }
        try {
            statementService.writePdfResponse(
                    response, PDF_FILENAME_BASE, summary,
                    getFromDateParam(), getToDateParam());
        } catch (PatientEndYearStatementService.Failure ex) {
            PatientEndYearStatements.logFailure(ex, getDemographicNoParam());
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
}
