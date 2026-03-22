/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.report.reportByTemplate.actions;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.services.security.SecurityManager;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Struts 2 action that exports a report template as a downloadable XML file.
 * Requires {@code _admin} read privilege.
 *
 * @since 2001-01-01
 */
public class ExportTemplate2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    public String execute() {
        MiscUtils.getLogger().debug("Entered manage template action");
        String roleName$ = (String) request.getSession().getAttribute("userrole") + "," + (String) request.getSession().getAttribute("user");
        if (!SecurityManager.hasPrivilege("_admin", roleName$) && !SecurityManager.hasPrivilege("_report", roleName$)) {
            throw new SecurityException("Insufficient Privileges");
        }

        String name = request.getParameter("name");
        String templateId = request.getParameter("templateid");
        String message = "Failed to export report, please contact an administrator";

        request.setAttribute("templateid", templateId);

        MiscUtils.getLogger().debug("Entered export template action with : " + name + ", id: " + templateId);
        // K2A service has been discontinued - export functionality is no longer available
        message = "The K2A export service has been discontinued and is no longer available";
        MiscUtils.getLogger().info("K2A export attempted but service is no longer available");
        request.setAttribute("message", message);
        return SUCCESS;
    }
}
