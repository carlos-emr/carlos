/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import io.github.carlos_emr.carlos.billings.ca.on.service.BillingInrCreationService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Struts2 action for adding an INR billing record.
 *
 * <p>Migrated from {@code billing/CA/ON/inr/dbINRbilling.jsp}. Accepts POST only,
 * enforces {@code _admin.billing} write privilege, builds a command from
 * request parameters, and delegates persistence to {@link BillingInrCreationService}.
 *
 * @since 2026-04-05
 */
public class InrBillingCreate2Action extends ActionSupport {

    private static final long serialVersionUID = 1L;

    private final HttpServletRequest request = ServletActionContext.getRequest();
    private final HttpServletResponse response = ServletActionContext.getResponse();

    private final SecurityInfoManager securityInfoManager;
    private final BillingInrCreationService billingInrCreationService;

    public InrBillingCreate2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class),
                SpringUtils.getBean(BillingInrCreationService.class));
    }

    public InrBillingCreate2Action(SecurityInfoManager securityInfoManager,
                                   BillingInrCreationService billingInrCreationService) {
        this.securityInfoManager = securityInfoManager;
        this.billingInrCreationService = billingInrCreationService;
    }

    /**
     * Validates the request, then delegates persistence of a new INR record.
     *
     * @return {@link #SUCCESS} on success, {@link #ERROR} for validation failures, or {@link #NONE} if the method is not POST
     * @throws Exception if an unexpected error occurs during persistence
     */
    @Override
    public String execute() throws Exception {
        if (!BillingRequestGuards.requirePost(request, response)) {
            return NONE;
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin.billing", "w", null)) {
            throw new SecurityException("missing required security object: _admin.billing");
        }

        String demoid = request.getParameter("demoid");
        if (demoid == null || demoid.trim().isEmpty()) {
            addActionError("Missing demographic ID.");
            return ERROR;
        }

        int demoIdInt;
        try {
            demoIdInt = Integer.parseInt(demoid.trim());
        } catch (NumberFormatException e) {
            addActionError("Invalid demographic ID — must be numeric.");
            return ERROR;
        }

        billingInrCreationService.create(new BillingInrCreationService.Command(
                demoIdInt,
                request.getParameter("demo_name"),
                request.getParameter("demo_hin"),
                request.getParameter("demo_dob"),
                request.getParameter("provider_no"),
                request.getParameter("provider_ohip_no"),
                request.getParameter("provider_rma_no"),
                request.getParameter("doccreator"),
                request.getParameter("diag_code"),
                request.getParameter("service_code"),
                request.getParameter("service_desc"),
                request.getParameter("service_amount"),
                request.getParameter("service_unit")));

        request.setAttribute("billSaved", true);
        return SUCCESS;
    }
}
