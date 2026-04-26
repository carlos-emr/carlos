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

import io.github.carlos_emr.carlos.billings.ca.on.data.BillingCodeSearchViewModel;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingCodeSearchDataAssembler;

/**
 * View gate for {@code billing/CA/ON/billingCodeSearch.jsp}, the OHIP
 * service-code search popup.
 *
 * <p>Enforces {@code _billing r}, then assembles a
 * {@link BillingCodeSearchViewModel} via
 * {@link BillingCodeSearchDataAssembler} so the JSP reads pre-resolved
 * code rows + auto-select state instead of doing the inline
 * {@code SpringUtils.getBean(BillingServiceDao)} lookup.</p>
 *
 * @since 2026-04-13
 */
public final class ViewBillingCodeSearch2Action extends ActionSupport {

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "r", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        BillingCodeSearchViewModel model = new BillingCodeSearchDataAssembler().assembleService(
                request.getParameter("name"),
                request.getParameter("name1"),
                request.getParameter("name2"),
                request.getParameter("nameF"));
        request.setAttribute("codeSearchModel", model);

        return SUCCESS;
    }
}
