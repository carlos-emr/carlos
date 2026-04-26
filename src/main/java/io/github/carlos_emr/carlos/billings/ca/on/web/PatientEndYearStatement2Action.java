/**
 * Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
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
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.billings.ca.on.web;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.billings.ca.on.data.PatientEndYearStatementBean;
import io.github.carlos_emr.carlos.billings.ca.on.service.PatientEndYearStatementService;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

/**
 * View gate for the patient end-year-statement workflow at
 * {@code /billing/CA/ON/endYearStatement}. Three modes selected by the
 * presence of distinct request parameters:
 *
 * <ul>
 *   <li>{@code search} — resolve a unique demographic, aggregate their PAT
 *       billings in the date range, expose {@code summary} + {@code result}
 *       to the JSP for screen rendering.</li>
 *   <li>{@code pdf} — read the previously-stashed session {@code summary}
 *       and stream a JasperReports PDF back to the browser.</li>
 *   <li>{@code demosearch} — resolve a unique demographic and expose only
 *       the {@code summary} (no billing aggregation).</li>
 * </ul>
 *
 * <p>The DAO loops, JasperReports invocation, and JDBC connection lifecycle
 * all live in {@link PatientEndYearStatementService}; this action only
 * parses parameters, enforces {@code _billing r}, and translates the
 * service's typed failure outcomes into i18n action errors.</p>
 *
 * @since 2026-04-26 (refactor)
 */
public class PatientEndYearStatement2Action extends ActionSupport {

    private static final Logger _logger = MiscUtils.getLogger();
    private static final String RES_SUCCESS = "success";
    private static final String RES_FAILURE = "failure";
    private static final String PDF_FILENAME_BASE = "end_year_statement_report";

    private final SecurityInfoManager securityInfoManager =
            SpringUtils.getBean(SecurityInfoManager.class);
    private final PatientEndYearStatementService statementService =
            SpringUtils.getBean(PatientEndYearStatementService.class);

    private final HttpServletRequest request = ServletActionContext.getRequest();
    private final HttpServletResponse response = ServletActionContext.getResponse();

    public String execute() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "r", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        // Echo first/last-name request parameters to request scope so the JSP
        // body can render them through the carlos null-safe encoder without
        // touching ${param.*} (which the OWASP-encoding hook flags as
        // unencoded user input). The action is the canonical input gate.
        String firstName = request.getParameter("firstNameParam");
        String lastName = request.getParameter("lastNameParam");
        request.setAttribute("firstNameParamEcho", firstName == null ? "" : firstName);
        request.setAttribute("lastNameParamEcho", lastName == null ? "" : lastName);
        StringBuilder displayName = new StringBuilder();
        if (firstName != null && !firstName.isEmpty() && lastName != null && !lastName.isEmpty()) {
            displayName.append(firstName).append(' ').append(lastName);
        }
        request.setAttribute("patientNameDisplay", displayName.toString());

        if (request.getParameter("search") != null) {
            return handleSearch(loggedInInfo);
        }
        if (request.getParameter("pdf") != null) {
            return handlePdf();
        }
        if (request.getParameter("demosearch") != null) {
            return handleDemoSearch(loggedInInfo);
        }

        // No mode parameter: clear any stale summary so the JSP renders the
        // empty form. Existing behavior preserved verbatim.
        request.getSession().setAttribute("summary", null); // nosemgrep: tainted-session-from-http-request -- clearing session attribute with null
        return RES_SUCCESS;
    }

    private String handleSearch(LoggedInInfo loggedInInfo) {
        request.setAttribute("fromDateParam", getFromDateParam());
        request.setAttribute("toDateParam", getToDateParam());

        Demographic demographic;
        try {
            demographic = statementService.findUniquePatient(
                    loggedInInfo, getDemographicNoParam(),
                    getFirstNameParam(), getLastNameParam());
        } catch (PatientEndYearStatementService.Failure ex) {
            return failWithI18n(ex);
        }

        PatientEndYearStatementService.Result result;
        try {
            result = statementService.aggregateInvoices(demographic, getFromDate(), getToDate());
        } catch (PatientEndYearStatementService.Failure ex) {
            return failWithI18n(ex);
        }

        request.setAttribute("summary", result.summary());
        request.setAttribute("result", result.invoices());
        request.getSession().setAttribute("summary", result.summary()); // nosemgrep: tainted-session-from-http-request -- summary bean populated from DAO billing query results and utility-formatted totals
        return RES_SUCCESS;
    }

    private String handlePdf() {
        PatientEndYearStatementBean summary =
                (PatientEndYearStatementBean) request.getSession().getAttribute("summary");
        if (summary == null) {
            addActionError(getText("error.billingReport.invalidPatientName"));
            return RES_FAILURE;
        }
        try {
            statementService.writePdfResponse(
                    response, PDF_FILENAME_BASE, summary,
                    getFromDateParam(), getToDateParam());
        } catch (PatientEndYearStatementService.Failure ex) {
            return failWithI18n(ex);
        }
        // Bypass Struts result-rendering — the PDF body is already on the
        // wire (header configured, output stream flushed by the service).
        return null;
    }

    private String handleDemoSearch(LoggedInInfo loggedInInfo) {
        request.getSession().setAttribute("summary", null); // nosemgrep: tainted-session-from-http-request -- clearing session attribute with null
        Demographic demographic;
        try {
            demographic = statementService.findUniquePatient(
                    loggedInInfo, request.getParameter("demographic_no"),
                    getFirstNameParam(), getLastNameParam());
        } catch (PatientEndYearStatementService.Failure ex) {
            return failWithI18n(ex);
        }
        PatientEndYearStatementBean summary = new PatientEndYearStatementBean(
                "", "", 0, "", "", "", new Date(), new Date(), "", "");
        summary.setPatientNo(demographic.getChartNo());
        summary.setPatientName(demographic.getFormattedName());
        summary.setHin(demographic.getHin());
        summary.setAddress(demographic.getAddress() + " "
                + demographic.getCity() + " " + demographic.getProvince());
        summary.setPhone(demographic.getPhone() + " " + demographic.getPhone2());
        request.setAttribute("summary", summary);
        return RES_SUCCESS;
    }

    private String failWithI18n(PatientEndYearStatementService.Failure failure) {
        addActionError(getText(failure.reason().i18nKey()));
        String first = getFirstNameParam();
        String last = getLastNameParam();
        switch (failure.reason()) {
            case PATIENT_NOT_FOUND:
                _logger.error("end-year-statement: lookup returned no candidates for first={}, last={}",
                        first, last);
                break;
            case PATIENT_NOT_UNIQUE:
                _logger.error("end-year-statement: lookup returned multiple candidates for first={}, last={}",
                        first, last);
                break;
            case DATABASE_ERROR:
            case IO_ERROR:
                _logger.error("end-year-statement failure: " + failure.reason(),
                        failure.getCause());
                break;
            default:
                _logger.error("end-year-statement: unexpected reason " + failure.reason(),
                        failure.getCause());
                break;
        }
        return RES_FAILURE;
    }

    private String firstNameParam;
    private String lastNameParam;
    private String fromDateParam;
    private String toDateParam;
    private String demographicNoParam;

    public String getFirstNameParam() {
        return firstNameParam;
    }

    @StrutsParameter
    public void setFirstNameParam(String firstNameParam) {
        this.firstNameParam = firstNameParam;
    }

    public String getLastNameParam() {
        return lastNameParam;
    }

    @StrutsParameter
    public void setLastNameParam(String lastNameParam) {
        this.lastNameParam = lastNameParam;
    }

    public String getFromDateParam() {
        return fromDateParam;
    }

    @StrutsParameter
    public void setFromDateParam(String fromDateParam) {
        this.fromDateParam = fromDateParam;
    }

    public String getDemographicNoParam() {
        return demographicNoParam;
    }

    @StrutsParameter
    public void setDemographicNoParam(String demographicNoParam) {
        this.demographicNoParam = demographicNoParam;
    }

    public Date getFromDate() {
        return parseIso(fromDateParam);
    }

    public String getToDateParam() {
        return toDateParam;
    }

    @StrutsParameter
    public void setToDateParam(String toDate) {
        this.toDateParam = toDate;
    }

    public Date getToDate() {
        return parseIso(toDateParam);
    }

    private static Date parseIso(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return new SimpleDateFormat("yyyy-MM-dd").parse(value);
        } catch (ParseException ex) {
            return null;
        }
    }
}
