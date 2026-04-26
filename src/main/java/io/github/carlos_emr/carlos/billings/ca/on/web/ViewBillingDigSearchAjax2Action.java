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

import io.github.carlos_emr.carlos.billings.ca.on.data.BillingDigSearchAjaxViewModel;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingDxCodeDataAssembler;

/**
 * View gate for {@code billing/CA/ON/billingDigSearchAjax.jsp}, the
 * jQuery-UI Autocomplete endpoint for ICD-9 diagnostic codes.
 *
 * <p>Enforces {@code _billing r}, then assembles a
 * {@link BillingDigSearchAjaxViewModel} via
 * {@link BillingDxCodeDataAssembler#assembleAjax}.</p>
 *
 * @since 2026-04-13
 */
public final class ViewBillingDigSearchAjax2Action extends ActionSupport {

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "r", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        BillingDigSearchAjaxViewModel model = new BillingDxCodeDataAssembler()
                .assembleAjax(request.getParameter("term"));
        request.setAttribute("ajaxModel", model);

        return SUCCESS;
    }
}
