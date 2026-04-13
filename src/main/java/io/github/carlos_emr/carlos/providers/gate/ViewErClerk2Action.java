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
package io.github.carlos_emr.carlos.providers.gate;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * View gate for {@code provider/er_clerk.jsp}. Enforces {@code _appointment}
 * {@code r} privilege before forwarding to the JSP at its
 * {@code /WEB-INF/jsp/provider/} location. Part of the provider module
 * security-hardening migration (2Action gate pattern from #1109, #1629,
 * #1632, #1644, #1662, #1663, #1665, #1666, #1667, #1668, #1669, #1670).
 *
 * @since 2026-04-13
 */
public final class ViewErClerk2Action extends ActionSupport {

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_appointment", "r", null)) {
            throw new SecurityException("missing required sec object (_appointment)");
        }

        return SUCCESS;
    }
}
