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

import io.github.carlos_emr.carlos.billings.ca.on.data.BillingOHIPSimulationViewModel;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingOHIPSimulationDataAssembler;

/**
 * View gate for {@code billing/CA/ON/billingOHIPsimulation.jsp}. Enforces
 * {@code _billing r} privilege, evaluates the privacy filter trio
 * ({@code _team_billing_only}, {@code _site_access_privacy},
 * {@code _team_access_privacy}) the legacy JSP did via
 * {@code <security:oscarSec>} scriptlets, and assembles a
 * {@link BillingOHIPSimulationViewModel} via
 * {@link BillingOHIPSimulationDataAssembler} so the JSP body becomes
 * pure presentation.
 *
 * @since 2026-04-13
 */
public class ViewBillingOHIPsimulation2Action extends ActionSupport {
    private final SecurityInfoManager securityInfoManager;
    public ViewBillingOHIPsimulation2Action(SecurityInfoManager securityInfoManager) {
        this.securityInfoManager = securityInfoManager;
    }

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        // Reject sessionless requests up front. SecurityInfoManagerImpl.hasPrivilege
        // dereferences loggedInInfo and emits an internal ERROR log on null, which
        // pollutes the log signal for real privilege denials.
        if (loggedInInfo == null) {
            throw new SecurityException("missing session");
        }
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "r", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        boolean teamBillingOnly = securityInfoManager.hasPrivilege(
                loggedInInfo, "_team_billing_only", "r", null);
        boolean siteAccessPrivacy = securityInfoManager.hasPrivilege(
                loggedInInfo, "_site_access_privacy", "r", null);
        boolean teamAccessPrivacy = securityInfoManager.hasPrivilege(
                loggedInInfo, "_team_access_privacy", "r", null);

        BillingOHIPSimulationViewModel model = new BillingOHIPSimulationDataAssembler()
                .assemble(request, loggedInInfo, teamBillingOnly,
                        siteAccessPrivacy, teamAccessPrivacy);
        request.setAttribute("simulationModel", model);

        return SUCCESS;
    }
}
