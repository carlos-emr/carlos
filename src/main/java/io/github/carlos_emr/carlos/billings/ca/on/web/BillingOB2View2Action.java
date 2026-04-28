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
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.billings.ca.on.data.BillingOB2ViewModel;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingOB2DataAssembler;

/**
 * View gate for {@code billing/CA/ON/billingOB2.jsp}, the read-only OHIP
 * billing-history popup ("OB" = OHIP Billing, not obstetric).
 *
 * <p>Enforces {@code _billing r}, validates the {@code billing_no} parameter
 * (1-9 digits), and assembles a {@link BillingOB2ViewModel} via
 * {@link BillingOB2DataAssembler} so the JSP can read pre-resolved records
 * instead of doing 6 inline {@code SpringUtils.getBean} lookups.</p>
 *
 * <p>This replaces the legacy routing via the BC-namespaced
 * {@code billing/CA/BC/billingView} action (which did no work for the ON
 * case other than returning result name {@code "ON"}). ON callers now
 * point directly at {@code billing/CA/ON/ViewBillingOB2}; the BC action
 * source is not modified.</p>
 *
 * @since 2026-04-26
 */
public class BillingOB2View2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager;

    private final BillingOB2DataAssembler billingOB2Assembler;

    public BillingOB2View2Action(SecurityInfoManager securityInfoManager,
                                  BillingOB2DataAssembler billingOB2Assembler) {
        this.securityInfoManager = securityInfoManager;
        this.billingOB2Assembler = billingOB2Assembler;
    }
    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (loggedInInfo == null) {
            throw new SecurityException("missing session");
        }
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "r", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        // Same input-validation guard the BC action applies to its private
        // path: only accept 1-9 digit billing_no; otherwise 400 Bad Request.
        String billingNoParam = request.getParameter("billing_no");
        if (billingNoParam == null || !billingNoParam.matches("\\d{1,9}")) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }

        BillingOB2ViewModel model = billingOB2Assembler.assemble(billingNoParam);
        request.setAttribute("ob2Model", model);

        return SUCCESS;
    }
}
