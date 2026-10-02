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
import org.apache.struts2.ServletActionContext;

/**
 * Shared explicit gate for moved eForm page entrypoints.
 *
 * @since 2026-04-15
 */
public class ViewEFormPage2Action extends BaseEFormView2Action {
    private static final String FIELDNOTE_SELECTION = "eform/fieldNoteReport/fieldnoteselect";

    @Override
    protected boolean isMethodAllowed(HttpServletRequest request) {
        ActionContext actionContext = ActionContext.getContext();
        if (actionContext != null && FIELDNOTE_SELECTION.equals(actionContext.getActionName())) {
            if (hasFieldNoteMutation(request)) {
                return "POST".equals(request.getMethod());
            }
            return super.isMethodAllowed(request) || "POST".equals(request.getMethod());
        }
        if (super.isMethodAllowed(request)) {
            return true;
        }

        if (!"POST".equals(request.getMethod())) {
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
    protected String allowedMethods() {
        ActionContext ctx = ActionContext.getContext();
        if (ctx != null && FIELDNOTE_SELECTION.equals(ctx.getActionName())
                && hasFieldNoteMutation(ServletActionContext.getRequest())) {
            return "POST";
        }
        if (ctx != null && ("eform/efmformmanageredit".equals(ctx.getActionName())
                || FIELDNOTE_SELECTION.equals(ctx.getActionName()))) {
            return "GET, HEAD, POST";
        }
        return super.allowedMethods();
    }

    private static boolean hasFieldNoteMutation(HttpServletRequest request) {
        return request.getParameter("selected_eform") != null || request.getParameter("unselect_eform") != null;
    }

    @Override
    protected String executeView(
            HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException {
        ActionContext ctx = ActionContext.getContext();
        if (ctx == null) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return NONE;
        }
        String actionName = ctx.getActionName();
        EFormViewRoutes.Route route = EFormViewRoutes.resolve(actionName);
        if (route == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return NONE;
        }

        requirePrivilege(request, route.privilege());
        if (route.privilege() == EFormViewRoutes.Privilege.FIELDNOTE_READ) {
            request.setAttribute("canManageFieldNotes", securityInfoManager.hasPrivilege(
                    loggedInInfo(request), "_admin.fieldnote", "w", null));
        }
        return forwardToInternalView(request, response, route.internalView());
    }
}
