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
package io.github.carlos_emr.carlos.form.gate;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.apache.struts2.ActionContext;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Gated wildcard view action for legacy form pages moved under WEB-INF.
 *
 * @since 2026-04-15
 */
public class ViewForm2Action extends ActionSupport {

    private SecurityInfoManager securityInfoManager =
            SpringUtils.getBean(SecurityInfoManager.class);

    @Override
    public String execute() throws ServletException, IOException {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        String demographicNo = request.getParameter("demographic_no");
        if (demographicNo == null || demographicNo.isBlank()) {
            demographicNo = request.getParameter("demographicNo");
        }

        if (!securityInfoManager.hasPrivilege(
                loggedInInfo, "_form", SecurityInfoManager.READ, demographicNo)) {
            throw new SecurityException("missing required security object: _form");
        }

        String actionName = ActionContext.getContext().getActionName();
        String internalView = FormViewRoutes.resolveInternalViewFromAction(
                actionName, request.getServletContext());
        if (internalView == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return NONE;
        }

        request.getRequestDispatcher(internalView).forward(request, response);
        return NONE;
    }
}
