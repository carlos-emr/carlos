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

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.billings.ca.on.service.PatientEndYearStatementService;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

/**
 * Resolves a unique demographic from the {@code firstNameParam} /
 * {@code lastNameParam} / {@code demographicNoParam} request inputs,
 * aggregates their PAT-billing invoices in the supplied date range, and
 * stashes the result on the request and session for the JSP / downstream
 * PDF print action to render.
 *
 * <p>Split out of the legacy {@code PatientEndYearStatement2Action#handleSearch}
 * so the {@code endYearStatement/search} URL has a single responsibility.</p>
 *
 * @since 2026-04-27
 */
public class SearchEndYearStatement2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager;
    private final PatientEndYearStatementService statementService;

    public SearchEndYearStatement2Action(SecurityInfoManager securityInfoManager,
                                         PatientEndYearStatementService statementService) {
        this.securityInfoManager = securityInfoManager;
        this.statementService = statementService;
    }

    @Override
    public String execute() {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "r", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        PatientEndYearStatementSupport.echoNames(request);
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
            result = statementService.aggregateInvoices(
                    demographic,
                    PatientEndYearStatementSupport.parseIso(getFromDateParam()),
                    PatientEndYearStatementSupport.parseIso(getToDateParam()));
        } catch (PatientEndYearStatementService.Failure ex) {
            return failWithI18n(ex);
        }

        request.setAttribute("summary", result.summary());
        request.setAttribute("result", result.invoices());
        request.getSession().setAttribute("summary", result.summary()); // nosemgrep: tainted-session-from-http-request -- summary bean populated from DAO billing query results and utility-formatted totals
        return SUCCESS;
    }

    private String failWithI18n(PatientEndYearStatementService.Failure failure) {
        addActionError(getText(failure.reason().i18nKey()));
        PatientEndYearStatementSupport.logFailure(failure, getFirstNameParam(), getLastNameParam());
        return "failure";
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
