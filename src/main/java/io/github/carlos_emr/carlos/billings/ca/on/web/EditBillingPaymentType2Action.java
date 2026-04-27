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
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.billings.ca.on.data.EditBillingPaymentTypeViewModel;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.EditBillingPaymentTypeDataAssembler;

/**
 * Mutation gate for {@code billing/CA/ON/editBillingPaymentType.jsp}. Enforces {@code _admin.billing}
 * w privilege AND POST-only before forwarding to the JSP. GET requests return
 * 405 Method Not Allowed.
 *
 * <p>Also assembles the {@link EditBillingPaymentTypeViewModel} on the request
 * as {@code paymentTypeModel} so the JSP body can render in pure EL (drained
 * from 12 scriptlets in the round-2 billing-form refactor).</p>
 *
 * @since 2026-04-13
 */
public class EditBillingPaymentType2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager;
    private final EditBillingPaymentTypeDataAssembler assembler;

    public EditBillingPaymentType2Action(SecurityInfoManager securityInfoManager,
                                  EditBillingPaymentTypeDataAssembler assembler) {
        this.securityInfoManager = securityInfoManager;
        this.assembler = assembler;
    }

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin.billing", "w", null)) {
            throw new SecurityException("missing required sec object (_admin.billing)");
        }

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        request.setAttribute("paymentTypeModel", assembler.assemble(request, loggedInInfo));
        return SUCCESS;
    }
}
