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
package io.github.carlos_emr.carlos.admin.web;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Security gate for the Update Demographic Provider admin page.
 *
 * <p>Requires {@code _admin.misc} read privilege before forwarding to the JSP.
 * POST mutations additionally require write privilege and run in a scoped transaction.</p>
 *
 * @since 2026-04-05
 */
public class UpdateDemographicProvider2Action extends ActionSupport {

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    private final io.github.carlos_emr.carlos.admin.service.DemographicProviderReassignmentService reassignments =
            SpringUtils.getBean(io.github.carlos_emr.carlos.admin.service.DemographicProviderReassignmentService.class);

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin.misc", "r", null)) {
            throw new SecurityException("missing required sec object (_admin.misc)");
        }

        String operation = request.getParameter("update");
        if (operation != null) {
            var response = ServletActionContext.getResponse();
            if (!"POST".equals(request.getMethod())) {
                response.setHeader("Allow", "POST");
                response.sendError(405, "POST required");
                return NONE;
            }
            // The blocking CSRFGuard filter validates all POSTs to this route.
            if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin.misc", "w", null)) {
                throw new SecurityException("missing required sec object (_admin.misc w)");
            }
            String suffix = switch (operation) {
                case "UpdateResident" -> "2";
                case "UpdateNurse" -> "1";
                case "UpdateMidwife" -> "4";
                case "UpdateMrp" -> "5";
                default -> null;
            };
            if (suffix == null) {
                response.sendError(400, "Unknown reassignment operation");
                return NONE;
            }
            try {
                int count = reassignments.reassign(loggedInInfo, operation,
                        request.getParameter("oldcust" + suffix), request.getParameter("newcust" + suffix),
                        request.getParameter("last_name_from"), request.getParameter("last_name_to"));
                request.setAttribute("reassignedProviderCount", count);
            } catch (IllegalArgumentException invalid) {
                response.sendError(400, "Invalid provider or name range");
                return NONE;
            }
        }
        return SUCCESS;
    }
}
