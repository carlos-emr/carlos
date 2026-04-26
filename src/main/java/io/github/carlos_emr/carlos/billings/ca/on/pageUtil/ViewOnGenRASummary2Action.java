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
package io.github.carlos_emr.carlos.billings.ca.on.pageUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.billings.ca.on.data.OnGenRASummaryViewModel;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Mutation gate for {@code billing/CA/ON/onGenRASummary.jsp}. The legacy JSP
 * performed multiple BillingRAPrep DAO lookups, computed running totals, and
 * called {@code raHeaderDao.merge(...)} to write the recalculated totals back
 * into the RA header content XML. The data assembly + audit merge now live in
 * {@link OnGenRASummaryDataAssembler}; this action enforces {@code _billing}
 * {@code w} + POST and stashes the {@link OnGenRASummaryViewModel} on the
 * request as {@code model} for the JSP to render.
 *
 * @since 2026-04-13
 */
public final class ViewOnGenRASummary2Action extends ActionSupport {

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private OnGenRASummaryViewModel model;

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "w", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        model = new OnGenRASummaryDataAssembler().assemble(
                request.getParameter("rano"),
                request.getParameter("proNo"));
        request.setAttribute("model", model);

        return SUCCESS;
    }

    public OnGenRASummaryViewModel getModel() {
        return model;
    }
}
