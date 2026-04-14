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
package io.github.carlos_emr.carlos.billings.ca.bc.pageUtil;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * View gate for {@code billing/CA/BC/billingTA.jsp}. Enforces
 * {@code _report} {@code r} privilege (matching the JSP's original
 * {@code security:oscarSec} taglib, which accepts {@code _report},
 * {@code _admin.reporting}, or {@code _admin} at read level) before
 * forwarding to the JSP at its {@code /WEB-INF/jsp/} location. Created
 * as part of the BC billing migration to gate direct-access paths
 * behind Struts2 actions.
 *
 * @since 2026-04-13
 */
public final class ViewBillingTA2Action extends ActionSupport {

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Enforces the gate contract (privilege and POST-only where applicable) and
     * forwards to the JSP configured as the Struts {@code success} result. See the
     * class-level Javadoc for the exact privilege requirement.
     *
     * @return the Struts result string
     * @throws Exception if the underlying Struts framework signals an error
     * @since 2026-04-13
     */
    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        // JSP taglib allows _report OR _admin.reporting OR _admin; mirror the report privilege.
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_report", "r", null)) {
            throw new SecurityException("missing required sec object (_report)");
        }

        return SUCCESS;
    }
}
