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

import jakarta.servlet.DispatcherType;
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

    private static final int MAX_CAUSE_DEPTH = 16;

    private SecurityInfoManager securityInfoManager =
            SpringUtils.getBean(SecurityInfoManager.class);
    private final FormViewVersionValidator versions = new FormViewVersionValidator();

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
            throw new SecurityException("missing required sec object (_form)");
        }

        String actionName = ActionContext.getContext().getActionName();
        String internalView = FormViewRoutes.resolveInternalViewFromAction(
                actionName, request.getServletContext());
        if (internalView == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return NONE;
        }

        // A typed form page's record refusal is answered here, before the page renders.
        String view = actionName.substring(actionName.lastIndexOf('/') + 1);
        versions.requireReadable(view, loggedInInfo, demographicNo, request.getParameter("formId"));

        if (request.getDispatcherType() == DispatcherType.INCLUDE) {
            request.getRequestDispatcher(internalView).include(request, response);
        } else {
            try {
                request.getRequestDispatcher(internalView).forward(request, response);
            } catch (ServletException | RuntimeException failure) {
                // Backstop for a page whose record service refuses while it renders (issue
                // #5693) and that the check above does not know: the container would answer with
                // its generic 500 page, so hand the refusal to the package's SecurityException
                // mapping instead, which answers 403, as long as nothing has been sent yet.
                SecurityException refusal = securityRefusal(failure);
                if (refusal == null || response.isCommitted()) {
                    throw failure;
                }
                response.resetBuffer();
                throw refusal;
            }
        }
        return NONE;
    }

    /** The SecurityException that caused a failed forward, or {@code null} for any other failure. */
    static SecurityException securityRefusal(Throwable failure) {
        // Bounded: a cause chain can be made cyclic, and the wrapping is only a few levels deep.
        Throwable cause = failure;
        for (int depth = 0; cause != null && depth < MAX_CAUSE_DEPTH; depth++, cause = cause.getCause()) {
            if (cause instanceof SecurityException refusal) {
                return refusal;
            }
        }
        return null;
    }
}
