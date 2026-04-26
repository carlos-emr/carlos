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

import io.github.carlos_emr.carlos.billings.ca.on.data.BillingEditWithApptNoViewModel;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingEditWithApptNoDataAssembler;

/**
 * Mutation gate for {@code billing/CA/ON/billingEditWithApptNo.jsp}. Enforces
 * {@code _billing} w privilege AND POST-only before forwarding to the JSP.
 * GET requests return 405 Method Not Allowed.
 *
 * <p>Also assembles a {@link BillingEditWithApptNoViewModel} via
 * {@link BillingEditWithApptNoDataAssembler}, exposing it to the JSP as
 * request attribute {@code editApptModel}. This drains the JSP body of
 * the scriptlet that pulled bill / item state inline and computed the
 * per-line-item hidden-field projection.</p>
 *
 * @since 2026-04-13
 */
public final class BillingEditWithApptNo2Action extends ActionSupport {

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

        BillingEditWithApptNoViewModel model = new BillingEditWithApptNoDataAssembler().assemble(request);
        request.setAttribute("editApptModel", model);

        return SUCCESS;
    }
}
