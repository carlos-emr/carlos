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
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Admin-gated view endpoint for the form XML import page.
 *
 * @since 2026-08-05
 */
public final class ViewFormXmlUpload2Action extends ActionSupport {

    private final transient SecurityInfoManager securityInfoManager;

    /**
     * Creates the action with the application security manager.
     */
    public ViewFormXmlUpload2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class));
    }

    /**
     * Creates the action with a supplied security manager.
     *
     * @param securityInfoManager security manager used to authorize page access
     */
    ViewFormXmlUpload2Action(SecurityInfoManager securityInfoManager) {
        this.securityInfoManager = securityInfoManager;
    }

    /**
     * Forwards authorized GET and HEAD requests to the XML import page.
     *
     * @return {@link #NONE} after forwarding the page or returning a method error
     * @throws ServletException if the request cannot be forwarded
     * @throws IOException if the response cannot send a method error
     * @throws SecurityException if the caller lacks the required administrative privilege
     */
    @Override
    public String execute() throws ServletException, IOException {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        String method = request.getMethod();
        if (!"GET".equals(method) && !"HEAD".equals(method)) {
            response.setHeader("Allow", "GET, HEAD");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null) {
            throw new SecurityException("missing required sec object (_admin.eform or _admin)");
        }

        boolean hasEformAdminWrite = securityInfoManager.hasPrivilege(
                loggedInInfo, "_admin.eform", SecurityInfoManager.WRITE, null);
        if (!hasEformAdminWrite && !securityInfoManager.hasPrivilege(
                loggedInInfo, "_admin", SecurityInfoManager.WRITE, null)) {
            throw new SecurityException("missing required sec object (_admin.eform or _admin)");
        }

        request.getRequestDispatcher("/WEB-INF/jsp/form/formXmlUpload.jsp").forward(request, response);
        return NONE;
    }
}
