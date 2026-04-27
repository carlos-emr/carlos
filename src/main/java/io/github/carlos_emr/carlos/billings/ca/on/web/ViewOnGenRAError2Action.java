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

import io.github.carlos_emr.carlos.billings.ca.on.data.OnGenRAErrorViewModel;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.OnGenRAErrorDataAssembler;

/**
 * View gate for {@code billing/CA/ON/onGenRAError.jsp}. Enforces {@code _billing}
 * {@code r} privilege before forwarding to the JSP at its
 * {@code /WEB-INF/jsp/} location. Created as part of the ON billing migration
 * to gate direct-access paths behind Struts2 actions (same pattern as
 * PR #1632 for BC billing).
 *
 * <p>Also assembles the {@link OnGenRAErrorViewModel} the JSP renders: the
 * provider dropdown options and (when a specific provider is selected) the
 * per-provider error rows. The view model is exposed as request attribute
 * {@code onGenRAErrorModel}; the JSP body became 100% EL on 2026-04-25.</p>
 *
 * @since 2026-04-13
 */
public class ViewOnGenRAError2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager;

    public ViewOnGenRAError2Action(SecurityInfoManager securityInfoManager) {
        this.securityInfoManager = securityInfoManager;
    }
    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "r", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        OnGenRAErrorViewModel model = new OnGenRAErrorDataAssembler().assemble(
                request.getParameter("rano"),
                request.getParameter("proNo"));
        request.setAttribute("onGenRAErrorModel", model);

        return SUCCESS;
    }
}
