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

import io.github.carlos_emr.carlos.billings.ca.on.data.GenRASummaryViewModel;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Mutation gate for {@code billing/CA/ON/genRASummary.jsp}, the OHIP RA
 * payment-summary report.
 *
 * <p>Enforces {@code _billing w} privilege AND POST-only (the JSP-era
 * scriptlet performed RA-header merge during render — still mutation-on-render,
 * just hoisted into {@link GenRASummaryDataAssembler}).</p>
 *
 * <p>The assembler call replaces the 5 inline {@code SpringUtils.getBean}
 * lookups (RaHeaderDao, RaDetailDao, ProviderDao, BillingDao + duplicate)
 * the JSP used to perform.</p>
 *
 * @since 2026-04-13
 */
public final class ViewGenRASummary2Action extends ActionSupport {

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

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

        GenRASummaryViewModel model = new GenRASummaryDataAssembler().assemble(request);
        request.setAttribute("raSummaryModel", model);

        return SUCCESS;
    }
}
