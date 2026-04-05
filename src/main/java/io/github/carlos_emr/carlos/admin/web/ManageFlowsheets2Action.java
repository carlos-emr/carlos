/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 *
 *
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.admin.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.encounter.oscarMeasurements.MeasurementTemplateFlowSheetConfig;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Admin action for managing flowsheet enable/disable state.
 *
 * <p>Requires any of {@code _admin r}, {@code _admin.misc r}, or {@code _admin.flowsheet r}
 * privilege. On POST, routes to {@code enable} or {@code disable} via the {@code method}
 * request parameter and applies the change via {@link MeasurementTemplateFlowSheetConfig},
 * then redirects (PRG pattern). GET requests return SUCCESS to render the list view.</p>
 *
 * @since 2026-05-01
 */
public class ManageFlowsheets2Action extends ActionSupport {

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    @Override
    public String execute() throws Exception {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "r", null)
                && !securityInfoManager.hasPrivilege(loggedInInfo, "_admin.misc", "r", null)
                && !securityInfoManager.hasPrivilege(loggedInInfo, "_admin.flowsheet", "r", null)) {
            throw new SecurityException("missing required sec object (_admin, _admin.misc, or _admin.flowsheet)");
        }

        String method = request.getParameter("method");
        if ("POST".equalsIgnoreCase(request.getMethod()) && method != null) {
            String name = request.getParameter("name");
            MeasurementTemplateFlowSheetConfig config = MeasurementTemplateFlowSheetConfig.getInstance();

            if ("disable".equalsIgnoreCase(method)) {
                config.disableFlowsheet(name);
            } else if ("enable".equalsIgnoreCase(method)) {
                config.enableFlowsheet(name);
            }

            // PRG pattern: redirect after mutation to prevent duplicate submissions
            response.sendRedirect(request.getContextPath() + "/admin/ManageFlowsheets.do");
            return NONE;
        }

        return SUCCESS;
    }
}
