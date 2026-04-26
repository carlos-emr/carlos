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

import io.github.carlos_emr.carlos.billings.ca.on.data.BillingONHistorySpecViewModel;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingONHistorySpecDataAssembler;

/**
 * View gate for {@code billing/CA/ON/billingONHistorySpec.jsp}. Enforces {@code _billing}
 * {@code r} privilege and assembles a {@link BillingONHistorySpecViewModel}
 * via {@link BillingONHistorySpecDataAssembler} so the JSP can render the
 * pre-resolved billing history rows instead of doing date-range math and
 * JDBC lookups inline.
 *
 * @since 2026-04-13
 */
public final class ViewBillingONHistorySpec2Action extends ActionSupport {

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "r", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        BillingONHistorySpecViewModel model = new BillingONHistorySpecDataAssembler().assemble(
                request.getParameter("demographic_no"),
                request.getParameter("demo_name"),
                request.getParameter("day"),
                request.getParameter("serviceCode"));
        request.setAttribute("historySpecModel", model);

        return SUCCESS;
    }
}
