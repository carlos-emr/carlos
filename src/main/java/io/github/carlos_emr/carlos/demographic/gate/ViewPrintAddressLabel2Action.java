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

import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * View gate for {@code demographic/printAddressLabel.jsp}. Enforces {@code _demographic}
 * {@code r} privilege before forwarding to the JSP at its
 * {@code /WEB-INF/jsp/demographic/} location. Part of the demographic
 * module security-hardening migration (2Action gate pattern from #1109,
 * #1629, #1632, #1644, #1662, #1663, #1665, #1666, #1667).
 *
 * @since 2026-04-13
 */
public final class ViewPrintAddressLabel2Action extends ActionSupport {

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", null)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }
        String demographicNo = request.getParameter("demographic_no");
        LogAction.addLog(loggedInInfo.getLoggedInProviderNo(), LogConst.READ, LogConst.CON_DEMOGRAPHIC,
                demographicNo, request.getRemoteAddr(), demographicNo);

        return SUCCESS;
    }
}
