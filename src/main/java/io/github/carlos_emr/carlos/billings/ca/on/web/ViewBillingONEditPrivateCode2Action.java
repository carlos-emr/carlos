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

import io.github.carlos_emr.carlos.billings.ca.on.data.BillingONEditPrivateCodeViewModel;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingONEditPrivateCodeDataAssembler;

/**
 * View gate for {@code billing/CA/ON/billingONEditPrivateCode.jsp}. Enforces
 * {@code _admin.billing w} privilege and assembles a
 * {@link BillingONEditPrivateCodeViewModel} via
 * {@link BillingONEditPrivateCodeDataAssembler} so the JSP body is pure
 * presentation.
 *
 * @since 2026-04-13
 */
public class ViewBillingONEditPrivateCode2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager;

    public ViewBillingONEditPrivateCode2Action(SecurityInfoManager securityInfoManager) {
        this.securityInfoManager = securityInfoManager;
    }
    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin.billing", "w", null)) {
            throw new SecurityException("missing required sec object (_admin.billing)");
        }

        BillingONEditPrivateCodeViewModel model = new BillingONEditPrivateCodeDataAssembler()
                .assemble(request, loggedInInfo);
        request.setAttribute("privateCodeModel", model);

        return SUCCESS;
    }
}
