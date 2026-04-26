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

import io.github.carlos_emr.carlos.billings.ca.on.data.BillingDigUpdateViewModel;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Mutation gate for {@code billing/CA/ON/billingDigUpdate.jsp}, the
 * diagnostic-code description-update popup.
 *
 * <p>Enforces {@code _billing w} privilege AND POST-only. The
 * {@link BillingDxCodeDataAssembler#assembleUpdate} call performs the
 * {@code DiagnosticCodeDao.merge} mutation that the JSP scriptlet used
 * to run mid-render; the JSP just renders the resulting success/error
 * banner.</p>
 *
 * @since 2026-04-13
 */
public final class BillingDigUpdate2Action extends ActionSupport {

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

        // Cleave the last 3 chars off the "update X" submit value (legacy
        // behavior). The new description is the form input named after the
        // 3-char dx code itself.
        String submitValue = request.getParameter("update");
        String code = (submitValue == null || submitValue.length() < 3)
                ? "" : submitValue.substring(submitValue.length() - 3);
        String newDescription = request.getParameter(code);

        BillingDigUpdateViewModel model = new BillingDxCodeDataAssembler()
                .assembleUpdate(submitValue, newDescription);
        request.setAttribute("digUpdateModel", model);

        return SUCCESS;
    }
}
