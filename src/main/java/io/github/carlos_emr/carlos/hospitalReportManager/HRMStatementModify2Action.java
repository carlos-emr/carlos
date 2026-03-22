/**
 * Copyright (c) 2008-2012 Indivica Inc.
 * <p>
 * This software is made available under the terms of the
 * GNU General Public License, Version 2, 1991 (GPLv2).
 * License details are available via "indivica.ca/gplv2"
 * and "gnu.org/licenses/gpl-2.0.html".
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.hospitalReportManager;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMProviderConfidentialityStatementDao;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMProviderConfidentialityStatement;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Struts2 action for creating or updating a provider's confidentiality statement
 * that is appended to printed HRM reports.
 *
 * <p>Reads the "statement" request parameter and persists it via
 * {@link HRMProviderConfidentialityStatementDao}. Sets a "statementSuccess" request
 * attribute indicating whether the operation succeeded.</p>
 *
 * @see HRMProviderConfidentialityStatement
 * @since 2008-11-05
 */
public class HRMStatementModify2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    HRMProviderConfidentialityStatementDao hrmProviderConfidentialityStatementDao = (HRMProviderConfidentialityStatementDao) SpringUtils.getBean(HRMProviderConfidentialityStatementDao.class);

    /**
     * Saves or updates the confidentiality statement for the currently logged-in provider.
     *
     * @return String {@link ActionSupport#SUCCESS} on completion
     */
    public String execute() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();
        String statement = request.getParameter("statement");

        HRMProviderConfidentialityStatement confStatement;
        try {
            confStatement = hrmProviderConfidentialityStatementDao.find(providerNo);
            if (confStatement == null) confStatement = new HRMProviderConfidentialityStatement();
        } catch (Exception e) {
            // Not found
            confStatement = new HRMProviderConfidentialityStatement();
        }

        confStatement.setStatement(statement);
        confStatement.setId(providerNo);
        try {
            hrmProviderConfidentialityStatementDao.merge(confStatement);
            request.setAttribute("statementSuccess", true);
        } catch (Exception e) {
            // Not merged
            request.setAttribute("statementSuccess", false);
        }

        return SUCCESS;
    }
}
