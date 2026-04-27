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

import io.github.carlos_emr.carlos.billings.ca.on.data.BillingON3rdInvViewModel;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingON3rdInvDataAssembler;

/**
 * View gate for {@code billing/CA/ON/billingON3rdInv.jsp}, the third-party
 * invoice print/preview. Enforces {@code _billing r} privilege and assembles
 * a {@link BillingON3rdInvViewModel} via {@link BillingON3rdInvDataAssembler}
 * so the JSP can read pre-resolved records instead of doing 9 inline
 * {@code SpringUtils.getBean} lookups.
 *
 * @since 2026-04-13
 */
public class ViewBillingON3rdInv2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager;

    public ViewBillingON3rdInv2Action(SecurityInfoManager securityInfoManager) {
        this.securityInfoManager = securityInfoManager;
    }
    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "r", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        BillingON3rdInvViewModel model = new BillingON3rdInvDataAssembler().assemble(request, loggedInInfo);
        request.setAttribute("invoiceModel", model);

        return SUCCESS;
    }
}
