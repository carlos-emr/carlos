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
package io.github.carlos_emr.carlos.form.pageUtil;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.form.gate.FormViewRoutes;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Replaces the legacy public {@code forwardname.jsp} helper.
 *
 * @since 2026-04-15
 */
public class FormForwardNamed2Action extends ActionSupport {

    private SecurityInfoManager securityInfoManager =
            SpringUtils.getBean(SecurityInfoManager.class);

    @Override
    public String execute() throws ServletException, IOException {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(
                loggedInInfo, "_eChart", SecurityInfoManager.READ, null)) {
            throw new SecurityException("missing required sec object (_eChart)");
        }

        String formLink = request.getParameter("form_link");
        String demographicNo = request.getParameter("demographic_no");
        String internalView = FormViewRoutes.resolveInternalViewFromFormLink(formLink);
        if (internalView == null) {
            MiscUtils.getLogger().warn( // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                    "form/forwardname: blocked invalid form_link parameter: {}",
                    LogSafe.sanitize(formLink));
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid form link");
            return NONE;
        }
        if (demographicNo == null || !demographicNo.matches("\\d+")) {
            MiscUtils.getLogger().warn( // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                    "form/forwardname: blocked invalid demographic_no parameter: {}",
                    LogSafe.sanitize(demographicNo));
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid demographic number");
            return NONE;
        }

        request.getRequestDispatcher(internalView + "?demographic_no=" + demographicNo)
                .include(request, response);
        return NONE;
    }
}
