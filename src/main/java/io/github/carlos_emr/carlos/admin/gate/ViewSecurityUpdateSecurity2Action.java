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

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * View gate for {@code admin/securityupdatesecurity.jsp}. Requires {@code r} on {@code _admin} or {@code _admin.userAdmin} (matches the source JSP's {@code <security:oscarSec>} OR-list) before forwarding
 * to the JSP at its {@code /WEB-INF/jsp/admin/} location. Part of the admin module
 * security-hardening migration (defense in depth; matches the 2Action
 * gate pattern from #1109, #1629, #1632, #1644, #1662, #1663).
 *
 * @since 2026-04-13
 */
public final class ViewSecurityUpdateSecurity2Action extends ActionSupport {

    private final transient SecurityInfoManager securityInfoManager;

    public ViewSecurityUpdateSecurity2Action(SecurityInfoManager securityInfoManager) {
        this.securityInfoManager = securityInfoManager;
    }

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        boolean authorized = securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "r", null)
                || securityInfoManager.hasPrivilege(loggedInInfo, "_admin.userAdmin", "r", null);

        if (!authorized) {
            throw new SecurityException("missing required sec object (_admin or _admin.userAdmin)");
        }

        return SUCCESS;
    }
}
