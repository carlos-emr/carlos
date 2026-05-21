/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.web;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingOhipSimulationViewModel;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingOhipSimulationViewModelAssembler;

/**
 * View gate for {@code billing/CA/ON/billingOHIPsimulation.jsp}. Enforces
 * {@code _billing r} privilege, evaluates the privacy filter trio
 * ({@code _team_billing_only}, {@code _site_access_privacy},
 * {@code _team_access_privacy}) the legacy JSP did via
 * {@code <security:oscarSec>} scriptlets, and assembles a
 * {@link BillingOhipSimulationViewModel} via
 * {@link BillingOhipSimulationViewModelAssembler} so the JSP body becomes
 * pure presentation.
 *
 * @since 2026-04-13
 */
public class ViewBillingOhipSimulation2Action extends ActionSupport {
    private final SecurityInfoManager securityInfoManager;
    private final BillingOhipSimulationViewModelAssembler billingOHIPSimulationAssembler;
    public ViewBillingOhipSimulation2Action(SecurityInfoManager securityInfoManager,
                                             BillingOhipSimulationViewModelAssembler billingOHIPSimulationAssembler) {
        this.securityInfoManager = securityInfoManager;
        this.billingOHIPSimulationAssembler = billingOHIPSimulationAssembler;
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
            throw new SecurityException("missing required security object: _billing");
        }

        boolean teamBillingOnly = securityInfoManager.hasPrivilege(
                loggedInInfo, "_team_billing_only", "r", null);
        boolean siteAccessPrivacy = securityInfoManager.hasPrivilege(
                loggedInInfo, "_site_access_privacy", "r", null);
        boolean teamAccessPrivacy = securityInfoManager.hasPrivilege(
                loggedInInfo, "_team_access_privacy", "r", null);

        BillingOhipSimulationViewModel model = billingOHIPSimulationAssembler
                .assemble(request, loggedInInfo, teamBillingOnly,
                        siteAccessPrivacy, teamAccessPrivacy);
        request.setAttribute("simulationModel", model);

        return SUCCESS;
    }
}
