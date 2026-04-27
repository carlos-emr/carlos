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
package io.github.carlos_emr.carlos.billings.ca.on.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.billings.ca.on.service.OnGenRAsettleService;

/**
 * Mutation gate for {@code billing/CA/ON/onGenRAsettle.jsp}, the standard
 * RA settlement popup (settles all non-error bills, marks RA header
 * status = "S").
 *
 * <p>Enforces {@code _billing w} privilege AND POST-only.
 * {@link OnGenRAsettleService#settle} runs the mutation that the JSP
 * scriptlet used to run inline (3 inline DAO lookups: RaHeaderDao,
 * BillingDao, RaDetailDao); the JSP just emits the close-popup script.</p>
 *
 * @since 2026-04-13
 */
public class ViewOnGenRAsettle2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager;

    public ViewOnGenRAsettle2Action(SecurityInfoManager securityInfoManager) {
        this.securityInfoManager = securityInfoManager;
    }
    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "w", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        SpringUtils.getBean(OnGenRAsettleService.class).settle(request.getParameter("rano"),
                OnGenRAsettleService.Mode.STANDARD);

        return SUCCESS;
    }
}
