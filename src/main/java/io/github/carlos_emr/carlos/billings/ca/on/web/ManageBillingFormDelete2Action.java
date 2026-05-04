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
import io.github.carlos_emr.carlos.utility.LogSanitizer;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import java.util.Objects;

/**
 * Struts2 action to delete all data associated with an Ontario billing service type.
 *
 * <p>Replaces {@code dbManageBillingform_delete.jsp}. Removes all
 * {@link io.github.carlos_emr.carlos.commn.model.CtlBillingService} and
 * {@link io.github.carlos_emr.carlos.commn.model.CtlDiagCode} rows for the given service type,
 * then removes the corresponding {@link io.github.carlos_emr.carlos.commn.model.CtlBillingType}
 * entry. Returns {@code success} to forward to the confirmation view JSP.
 *
 * @since 2026-04-05
 */
public class ManageBillingFormDelete2Action extends ActionSupport {

    private static final long serialVersionUID = 1L;

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private BillingFormConfigurationService billingFormConfigurationService =
            SpringUtils.getBean(BillingFormConfigurationService.class);

    /**
     * Deletes all billing service, diagnostic code, and billing type entries for the
     * given service type.
     *
     * @return {@link #SUCCESS} to forward to the confirmation view, or {@link #NONE}
     *         if the request method is not POST
     * @throws SecurityException if the user lacks {@code _admin.billing} write privilege
     */
    @Override
    public String execute() throws Exception {
        if (!BillingRequestGuards.requirePost(request, response)) {
            return NONE;
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin.billing", "w", null)) {
            throw new SecurityException("missing required sec object (_admin.billing)");
        }

        String typeid = Objects.toString(request.getParameter("servicetype"), "");

        if (typeid.isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing servicetype parameter");
            return NONE;
        }

        try {
            billingFormConfigurationService.deleteServiceTypeAndCascade(typeid);
        } catch (Exception e) {
            MiscUtils.getLogger().error("Failed to delete billing form for servicetype={} — transaction rolled back", LogSanitizer.sanitize(typeid), e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to delete billing form");
            return NONE;
        }

        return SUCCESS;
    }
}
