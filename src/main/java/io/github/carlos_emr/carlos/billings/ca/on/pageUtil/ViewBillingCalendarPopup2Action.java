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
package io.github.carlos_emr.carlos.billings.ca.on.pageUtil;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * View gate for {@code billing/CA/ON/billingCalendarPopup.jsp}. This is a
 * general-purpose date-picker popup used by multiple modules, not only billing:
 * it is also launched from the oscarReport Age/Sex report and the Tickler
 * demographic view. The gate therefore accepts any of {@code _billing},
 * {@code _report}, or {@code _tickler} read privileges — users who can reach
 * the parent screen must be able to use the date picker on it.
 *
 * @since 2026-04-13
 */
public final class ViewBillingCalendarPopup2Action extends ActionSupport {

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        boolean allowed =
                securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "r", null)
                        || securityInfoManager.hasPrivilege(loggedInInfo, "_report", "r", null)
                        || securityInfoManager.hasPrivilege(loggedInInfo, "_tickler", "r", null);
        if (!allowed) {
            throw new SecurityException("missing required sec object (_billing | _report | _tickler)");
        }

        return SUCCESS;
    }
}
