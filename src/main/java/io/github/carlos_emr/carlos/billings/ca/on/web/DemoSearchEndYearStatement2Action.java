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

import java.util.Date;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.PatientEndYearStatementSummary;
import io.github.carlos_emr.carlos.billings.ca.on.service.PatientEndYearStatementService;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

/**
 * Demographic-only search invoked when the demographic-search popup
 * returns control to {@code endYearStatement/demosearch?demographic_no=...}.
 * Resolves the unique patient and exposes a partial {@link PatientEndYearStatementSummary}
 * with their identity fields populated (no billing aggregation) so the JSP
 * can show the patient banner above an empty invoice table.
 *
 * <p>Split out of the legacy {@code PatientEndYearStatement2Action#handleDemoSearch}
 * so the {@code endYearStatement/demosearch} URL has a single responsibility.</p>
 *
 * @since 2026-04-27
 */
public class DemoSearchEndYearStatement2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager;
    private final PatientEndYearStatementService statementService;

    public DemoSearchEndYearStatement2Action(SecurityInfoManager securityInfoManager,
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
        request.getSession().setAttribute("summary", null); // nosemgrep: tainted-session-from-http-request -- clearing session attribute with null

        Demographic demographic;
        try {
            demographic = statementService.findUniquePatient(
                    loggedInInfo, request.getParameter("demographic_no"),
                    getFirstNameParam(), getLastNameParam());
        } catch (PatientEndYearStatementService.Failure ex) {
            addActionError(getText(ex.reason().i18nKey()));
            PatientEndYearStatementSupport.logFailure(ex, getFirstNameParam(), getLastNameParam());
            return "failure";
        }

        PatientEndYearStatementSummary summary = new PatientEndYearStatementSummary(
                "", "", 0, "", "", "", new Date(), new Date(), "", "");
        summary.setPatientNo(demographic.getChartNo());
        summary.setPatientName(demographic.getFormattedName());
        summary.setHin(demographic.getHin());
        summary.setAddress(demographic.getAddress() + " "
                + demographic.getCity() + " " + demographic.getProvince());
        summary.setPhone(demographic.getPhone() + " " + demographic.getPhone2());
        request.setAttribute("summary", summary);
        return SUCCESS;
    }

    private String firstNameParam;
    private String lastNameParam;

    public String getFirstNameParam() { return firstNameParam; }
    @StrutsParameter
    public void setFirstNameParam(String firstNameParam) { this.firstNameParam = firstNameParam; }

    public String getLastNameParam() { return lastNameParam; }
    @StrutsParameter
    public void setLastNameParam(String lastNameParam) { this.lastNameParam = lastNameParam; }
}
