//CHECKSTYLE:OFF
/**
 * Copyright (c) 2008-2012 Indivica Inc.
 * <p>
 * This software is made available under the terms of the
 * GNU General Public License, Version 2, 1991 (GPLv2).
 * License details are available via "indivica.ca/gplv2"
 * and "gnu.org/licenses/gpl-2.0.html".
 */
package ca.openosp.openo.hospitalReportManager;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.hospitalReportManager.dao.HRMProviderConfidentialityStatementDao;
import ca.openosp.openo.hospitalReportManager.model.HRMProviderConfidentialityStatement;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class HRMStatementModify2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    HRMProviderConfidentialityStatementDao hrmProviderConfidentialityStatementDao = (HRMProviderConfidentialityStatementDao) SpringUtils.getBean(HRMProviderConfidentialityStatementDao.class);

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
