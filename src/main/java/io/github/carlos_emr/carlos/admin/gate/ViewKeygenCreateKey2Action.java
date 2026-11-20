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
package io.github.carlos_emr.carlos.admin.gate;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * View gate for {@code admin/keygen/createKey.jsp}. Enforces {@code _admin}
 * {@code w} privilege before forwarding to the JSP at its
 * {@code /WEB-INF/jsp/admin/} location. Part of the admin module
 * security-hardening migration (defense in depth; matches the 2Action
 * gate pattern from #1109, #1629, #1632, #1644, #1662, #1663).
 *
 * @since 2026-04-13
 */
public final class ViewKeygenCreateKey2Action extends ActionSupport {

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "w", null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }

        // createKey.jsp generates and stores a key pair for any request that names a service, so
        // a request carrying `name` is a write and must arrive as the page's own POST. A plain GET
        // without `name` still opens the empty form from the Key Manager's Create New Key button
        // (#6321, app-findings-log finding 163).
        if (request.getParameter("name") != null && !"POST".equals(request.getMethod())) {
            HttpServletResponse response = ServletActionContext.getResponse();
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            response.setHeader("Allow", "POST");
            return NONE;
        }

        return SUCCESS;
    }
}
