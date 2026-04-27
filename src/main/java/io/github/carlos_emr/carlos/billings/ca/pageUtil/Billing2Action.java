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
package io.github.carlos_emr.carlos.billings.ca.pageUtil;

import java.util.Properties;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.util.plugin.CarlosProperties;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Cross-province billing entry router. Decides whether a request is heading
 * into the BC or ON billing flow and returns the matching result name; the
 * struts mapping then chains into the province-specific setup action.
 *
 * <p>This class lives at the {@code ca} parent level on purpose — it has no
 * BC-specific or ON-specific imports. Province-specific bean population,
 * decision-support evaluation, and form binding live behind the chain in
 * {@code ca.bc.pageUtil.BillingBCSetup2Action} and (for ON)
 * {@code ca.on.web.ViewBillingON2Action}, respectively.</p>
 *
 * <p>Region resolution: prefer the {@code billRegion} request parameter; fall
 * back to the deployment-wide {@code billregion} property. Anything not
 * exactly {@code "ON"} is treated as BC, preserving the historical default.</p>
 *
 * @since 2026-04-27
 */
public final class Billing2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager =
            SpringUtils.getBean(SecurityInfoManager.class);

    @Override
    public String execute() {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "r", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        String region = request.getParameter("billRegion");
        if (region == null || region.isEmpty()) {
            Properties props = CarlosProperties.getProperties();
            region = props == null ? null : props.getProperty("billregion");
        }
        return "ON".equals(region) ? "ON" : "BC";
    }
}
