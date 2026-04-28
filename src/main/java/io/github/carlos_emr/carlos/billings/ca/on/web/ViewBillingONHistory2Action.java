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

import io.github.carlos_emr.carlos.billings.ca.on.data.BillingONHistoryViewModel;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingONHistoryDataAssembler;

/**
 * View gate for {@code billing/CA/ON/billingONHistory.jsp}. Enforces
 * {@code _billing r} privilege and assembles a
 * {@link BillingONHistoryViewModel} via {@link BillingONHistoryDataAssembler}
 * so the JSP can render pre-resolved billing-history rows instead of doing
 * inline Spring lookups, balance arithmetic, and per-row privilege checks.
 *
 * @since 2026-04-13
 */
public class ViewBillingONHistory2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager;

    private final BillingONHistoryDataAssembler billingONHistoryAssembler;

    public ViewBillingONHistory2Action(SecurityInfoManager securityInfoManager,
                                        BillingONHistoryDataAssembler billingONHistoryAssembler) {
        this.securityInfoManager = securityInfoManager;
        this.billingONHistoryAssembler = billingONHistoryAssembler;
    }
    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "r", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        BillingONHistoryViewModel model = billingONHistoryAssembler
                .assemble(loggedInInfo, request.getParameter("demographic_no"));
        request.setAttribute("historyModel", model);

        return SUCCESS;
    }
}
