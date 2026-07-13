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
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class HRMStatementModify2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    HRMProviderConfidentialityStatementDao hrmProviderConfidentialityStatementDao = (HRMProviderConfidentialityStatementDao) SpringUtils.getBean(HRMProviderConfidentialityStatementDao.class);

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    private boolean hasAdminMiscPrivilege(LoggedInInfo loggedInInfo, String privilege) {
        return securityInfoManager.hasPrivilege(loggedInInfo, "_admin", privilege, null)
                || securityInfoManager.hasPrivilege(loggedInInfo, "_admin.misc", privilege, null);
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    @Override
    public String execute() throws java.io.IOException {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null) {
            throw new SecurityException("missing logged in session");
        }
        if (!hasAdminMiscPrivilege(loggedInInfo, "r")) {
            throw new SecurityException("missing required sec object (_admin or _admin.misc r)");
        }

        String providerNo = loggedInInfo.getLoggedInProviderNo();
        String statement = request.getParameter("statement");

        // If the request carries a statement param, it is the self-posting
        // admin form committing an update. Require POST + admin write access
        // so the confidentiality statement cannot be overwritten by a crafted
        // GET link.
        if (statement != null) {
            if (!"POST".equalsIgnoreCase(request.getMethod())) {
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                return NONE;
            }
            if (!hasAdminMiscPrivilege(loggedInInfo, "w")) {
                throw new SecurityException("missing required sec object (_admin or _admin.misc w)");
            }

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
        }

        return SUCCESS;
    }
}
