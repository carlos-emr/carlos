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
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingFormConfigurationService;
import io.github.carlos_emr.carlos.commn.model.CtlBillingServicePremium;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Struts2 action to add premium billing service codes for Ontario billing.
 *
 * <p>Replaces {@code dbManageBillingform_premium.jsp}. Iterates over the ten
 * {@code service1}–{@code service10} request parameters and persists a new
 * {@link CtlBillingServicePremium} entry for each non-empty value.
 *
 * @since 2026-04-05
 */
public class ManageBillingFormPremium2Action extends ActionSupport {

    private static final long serialVersionUID = 1L;

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private BillingFormConfigurationService billingFormConfigurationService =
            SpringUtils.getBean(BillingFormConfigurationService.class);

    /**
     * Persists premium billing service entries for the given Ontario service type.
     *
     * @return {@link #NONE} after redirecting, or if the request method is not POST
     * @throws SecurityException if the user lacks {@code _admin.billing} write privilege
     */
    // FindSecBugs UNVALIDATED_REDIRECT: redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL.
    @SuppressFBWarnings(value = "UNVALIDATED_REDIRECT", justification = "redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL")
    @Override
    public String execute() throws Exception {
        if (!BillingRequestGuards.requirePost(request, response)) {
            return NONE;
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin.billing", "w", null)) {
            throw new SecurityException("missing required sec object (_admin.billing)");
        }

        List<CtlBillingServicePremium> premiums = new ArrayList<>();
        for (int i = 1; i < 11; i++) {
            String serviceCode = request.getParameter("service" + i);
            if (serviceCode == null || serviceCode.isEmpty()) {
                continue;
            }
            CtlBillingServicePremium cbsp = new CtlBillingServicePremium();
            cbsp.setServiceTypeName("Office");
            cbsp.setServiceCode(serviceCode);
            cbsp.setStatus("A");
            cbsp.setUpdateDate(new Date());
            premiums.add(cbsp);
        }

        try {
            billingFormConfigurationService.addPremiumServiceCodes(premiums);
        } catch (Exception e) {
            MiscUtils.getLogger().error("Failed to add premium service codes", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to add premium service codes");
            return NONE;
        }

        response.sendRedirect(request.getContextPath() + "/billing/CA/ON/ManageBillingform");
        return NONE;
    }
}
