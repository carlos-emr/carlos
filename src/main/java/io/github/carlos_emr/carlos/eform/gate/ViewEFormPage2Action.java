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
package io.github.carlos_emr.carlos.eform.gate;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionContext;

/**
 * Shared explicit gate for moved eForm page entrypoints.
 *
 * @since 2026-04-15
 */
public class ViewEFormPage2Action extends BaseEFormView2Action {

    @Override
    protected boolean isMethodAllowed(HttpServletRequest request) {
        if (super.isMethodAllowed(request)) {
            return true;
        }

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }

        ActionContext ctx = ActionContext.getContext();
        if (ctx == null) {
            return false;
        }
        String actionName = ctx.getActionName();
        return "eform/efmformmanageredit".equals(actionName)
                && request.getParameter("formHtmlG") != null;
    }

    @Override
    protected String executeView(
            HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException {
        String actionName = ActionContext.getContext().getActionName();
        EFormViewRoutes.Route route = EFormViewRoutes.resolve(actionName);
        if (route == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return NONE;
        }

        requirePrivilege(request, route.privilege());
        return forwardToInternalView(request, response, route.internalView());
    }
}
