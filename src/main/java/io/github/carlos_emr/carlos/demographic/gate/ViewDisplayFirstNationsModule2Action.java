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
package io.github.carlos_emr.carlos.demographic.gate;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * View gate for {@code demographic/displayFirstNationsModule.jsp}. Enforces {@code _demographic}
 * {@code r} privilege before forwarding to the JSP. This JSP is
 * both directly-linked from encounter/form callers AND used as a
 * {@code <%@ include>} target; the gate handles the direct-link case.
 *
 * @since 2026-04-13
 */
public final class ViewDisplayFirstNationsModule2Action extends ActionSupport {

    private final transient SecurityInfoManager securityInfoManager;

    public ViewDisplayFirstNationsModule2Action(SecurityInfoManager securityInfoManager) {
        this.securityInfoManager = securityInfoManager;
    }

    public ViewDisplayFirstNationsModule2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class));
    }

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", null)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }

        return SUCCESS;
    }
}
