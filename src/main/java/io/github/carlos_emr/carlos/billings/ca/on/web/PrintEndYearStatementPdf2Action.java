/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.billings.ca.on.data.PatientEndYearStatementBean;
import io.github.carlos_emr.carlos.billings.ca.on.service.PatientEndYearStatementService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

/**
 * Streams the end-year-statement PDF for the {@code summary} previously
 * stashed by {@link SearchEndYearStatement2Action} on the session. If
 * nothing has been searched yet, raises an i18n action error and forwards
 * back to the form JSP. On success returns {@code null} so Struts skips
 * result-rendering — the PDF body is already on the wire.
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
    public String execute() {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "r", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        PatientEndYearStatementSupport.echoNames(request);

        PatientEndYearStatementBean summary =
                (PatientEndYearStatementBean) request.getSession().getAttribute("summary");
        if (summary == null) {
            addActionError(getText("error.billingReport.invalidPatientName"));
            return "failure";
        }
        try {
            statementService.writePdfResponse(
                    response, PDF_FILENAME_BASE, summary,
                    getFromDateParam(), getToDateParam());
        } catch (PatientEndYearStatementService.Failure ex) {
            addActionError(getText(ex.reason().i18nKey()));
            PatientEndYearStatementSupport.logFailure(ex, getFirstNameParam(), getLastNameParam());
            return "failure";
        }
        // Bypass Struts result-rendering — the PDF body is already on the wire.
        return null;
    }

    private String firstNameParam;
    private String lastNameParam;
    private String fromDateParam;
    private String toDateParam;

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
}
