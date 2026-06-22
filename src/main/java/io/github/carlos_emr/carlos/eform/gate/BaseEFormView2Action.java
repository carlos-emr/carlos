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

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Shared GET/HEAD gate for moved eForm JSP entrypoints.
 *
 * @since 2026-04-15
 */
public abstract class BaseEFormView2Action extends ActionSupport {

    protected SecurityInfoManager securityInfoManager =
            SpringUtils.getBean(SecurityInfoManager.class);

    @Override
    public final String execute() throws ServletException, IOException {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        if (!isMethodAllowed(request)) {
            response.setHeader("Allow", "GET, HEAD");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        return executeView(request, response);
    }

    protected abstract String executeView(
            HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException;

    protected final LoggedInInfo loggedInInfo(HttpServletRequest request) {
        return LoggedInInfo.getLoggedInInfoFromSession(request);
    }

    protected final void requirePrivilege(
            HttpServletRequest request,
            EFormViewRoutes.Privilege privilege) {
        LoggedInInfo loggedInInfo = loggedInInfo(request);
        boolean allowed;
        String objectName;
        switch (privilege) {
            case EFORM_READ:
                objectName = "_eform";
                allowed = securityInfoManager.hasPrivilege(
                        loggedInInfo, objectName, SecurityInfoManager.READ, null);
                break;
            case EFORM_WRITE:
                objectName = "_eform";
                allowed = securityInfoManager.hasPrivilege(
                        loggedInInfo, objectName, SecurityInfoManager.WRITE, null);
                break;
            case ADMIN_EFORM_WRITE:
                objectName = "_admin.eform";
                allowed = securityInfoManager.hasPrivilege(
                        loggedInInfo, objectName, SecurityInfoManager.WRITE, null);
                break;
            default:
                objectName = "_eform";
                allowed = false;
        }

        if (!allowed) {
            throw new SecurityException("missing required sec object (" + objectName + ")");
        }
    }

    protected final String forwardToInternalView(
            HttpServletRequest request,
            HttpServletResponse response,
            String internalView) throws ServletException, IOException {
        request.getRequestDispatcher(internalView).forward(request, response);
        return NONE;
    }

    /**
     * Returns whether the current request method is allowed for this view route.
     * Subclasses may allow narrowly scoped POST bridges for non-mutating legacy flows.
     */
    protected boolean isMethodAllowed(HttpServletRequest request) {
        return isReadMethod(request.getMethod());
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    private boolean isReadMethod(String method) {
        return "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method);
    }
}
