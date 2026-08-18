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
import io.github.carlos_emr.carlos.commn.model.ClinicLocation;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import java.util.ArrayList;
import java.util.List;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Struts2 action to add clinic billing locations for Ontario billing.
 *
 * <p>Replaces {@code dbManageBillingLocation.jsp}. Iterates over up to five location
 * parameters ({@code location1}–{@code location5}) and their corresponding description
 * parameters ({@code location1desc}–{@code location5desc}), persisting a new
 * {@link ClinicLocation} for each pair where both the location code is non-null/non-empty
 * and the description is non-empty.
 *
 * <p><strong>Bug fix:</strong> The original JSP used {@code location1 != ""}, a reference
 * comparison that is not reliable for string values in Java. This action uses a
 * null-safe emptiness check ({@code !location1.isEmpty()} after a null guard) instead.
 *
 * <p>Single-quote characters do not require manual sanitization because the
 * DAO layer uses JPA/Hibernate parameterized queries, which handle quoting safely.
 *
 * @since 2026-04-05
 */
public class ManageBillingLocationSave2Action extends ActionSupport {

    private static final long serialVersionUID = 1L;

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private BillingFormConfigurationService billingFormConfigurationService =
            SpringUtils.getBean(BillingFormConfigurationService.class);

    /**
     * Adds clinic location entries from the submitted form parameters. Despite
     * the {@code Save} verb, the underlying service-layer call is an additive
     * persist — pre-existing rows are not deleted first. This matches the
     * legacy JSP contract.
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

        List<ClinicLocation> locations = new ArrayList<>();
        for (int i = 1; i < 6; i++) {
            String location = request.getParameter("location" + i);
            String locationDesc = request.getParameter("location" + i + "desc");

            if (locationDesc == null) {
                locationDesc = "";
            }

            if (location == null || location.isEmpty()) {
                continue;
            }

            if (!locationDesc.isEmpty()) {
                ClinicLocation clinicLocation = new ClinicLocation();
                clinicLocation.setClinicLocationNo(location);
                clinicLocation.setClinicNo(1);
                clinicLocation.setClinicLocationName(locationDesc);
                locations.add(clinicLocation);
            }
        }

        try {
            billingFormConfigurationService.saveLocations(locations);
        } catch (Exception e) {
            MiscUtils.getLogger().error("Failed to update billing locations", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to update billing locations");
            return NONE;
        }

        response.sendRedirect(request.getContextPath() + "/billing/CA/ON/ManageBillingLocation");
        return NONE;
    }

}
