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
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import java.util.Objects;

/**
 * Struts2 action to manage the bill type association for an Ontario billing service type.
 *
 * <p>Replaces {@code dbManageBillingform_billtype.jsp}. Three cases are handled:
 * <ul>
 *   <li>{@code billtype == "no"} - removes the existing bill-type entry.</li>
 *   <li>{@code billtype_old == "no"} - creates a new bill-type entry.</li>
 *   <li>Otherwise - finds the existing entry and updates its bill type value.</li>
 * </ul>
 * Returns {@code success} to forward to the confirmation view JSP.
 *
 * @since 2026-04-05
 */
public class ManageBillingFormBillTypeSave2Action extends ActionSupport {

    private static final long serialVersionUID = 1L;

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private BillingFormConfigurationService billingFormConfigurationService =
            SpringUtils.getBean(BillingFormConfigurationService.class);

    /**
     * Processes a bill type change for the given service type.
     *
     * @return {@link #SUCCESS} to forward to the confirmation view, or {@link #NONE}
     *         if the request method is not POST
     * @throws SecurityException if the user lacks {@code _admin.billing} write privilege
     */
    @Override
    public String execute() throws Exception {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin.billing", "w", null)) {
            throw new SecurityException("missing required security object: _admin.billing");
        }

        if (!BillingRequestGuards.requirePost(request, response)) {
            return NONE;
        }

        String servicetype = Objects.toString(request.getParameter("servicetype"), "");
        String billtype = Objects.toString(request.getParameter("billtype"), "");
        String billtypeOld = Objects.toString(request.getParameter("billtype_old"), "");

        if (servicetype.isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing servicetype parameter");
            return NONE;
        }

        try {
            if (!billingFormConfigurationService.updateBillingTypeAssociation(servicetype, billtype, billtypeOld)) {
                MiscUtils.getLogger().error( // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                        "ManageBillingFormBillTypeSave2Action: no billing type found for servicetype={} - update failed",
                        LogSafe.sanitize(servicetype));
                response.sendError(HttpServletResponse.SC_NOT_FOUND,
                        "Billing type entry not found for the specified service type");
                return NONE;
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Failed to update bill type for servicetype={}", LogSafe.sanitize(servicetype), e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to update bill type");
            return NONE;
        }

        return SUCCESS;
    }
}
