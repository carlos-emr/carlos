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
import org.springframework.beans.factory.annotation.Autowired;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.billings.ca.on.data.BillingONNewReportViewModel;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingONNewReportDataAssembler;

/**
 * View action for {@code billing/CA/ON/billingONNewReport.jsp}. The legacy JSP
 * ran four inline JDBC queries (unbilled / billed / paid / unpaid) and built
 * provider/site dropdown options inline. The data assembly now lives in
 * {@link BillingONNewReportDataAssembler}; this action enforces {@code _billing}
 * {@code r} and stashes the {@link BillingONNewReportViewModel} on the request
 * as {@code model} for the JSP to render via EL.
 *
 * <p>This page is read-only (no DB writes), so GET / POST are both accepted.
 *
 * @since 2026-04-13
 */
public class ViewBillingONNewReport2Action extends ActionSupport {
    private final SecurityInfoManager securityInfoManager;
    private BillingONNewReportViewModel model;
    /** Constructor injection used by Spring + Struts2's SpringObjectFactory. */
    @Autowired
    public ViewBillingONNewReport2Action(SecurityInfoManager securityInfoManager) {
        this.securityInfoManager = securityInfoManager;
    }

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        // Reject sessionless requests up front. SecurityInfoManagerImpl.hasPrivilege
        // dereferences loggedInInfo and emits an internal ERROR log on null, which
        // pollutes the log signal for real privilege denials.
        if (loggedInInfo == null) {
            throw new SecurityException("missing session");
        }
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "r", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        model = new BillingONNewReportDataAssembler().assemble(request, loggedInInfo);
        request.setAttribute("model", model);

        return SUCCESS;
    }

    public BillingONNewReportViewModel getModel() {
        return model;
    }
}
