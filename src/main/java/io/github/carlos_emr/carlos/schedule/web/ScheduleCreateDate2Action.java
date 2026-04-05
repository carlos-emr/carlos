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
package io.github.carlos_emr.carlos.schedule.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Gate action for the bulk schedule date creation page (schedulecreatedate.jsp).
 * Enforces schedule admin write privilege before forwarding to the JSP.
 * Bulk operations (bFirstDisp absent or "1") require POST to prevent CSRF on write operations.
 *
 * @since 2026-04-05
 */
public final class ScheduleCreateDate2Action extends ActionSupport {

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin.schedule", "w", null)) {
            throw new SecurityException("missing required sec object (_admin.schedule)");
        }

        // Bulk date generation (bFirstDisp absent or "1") writes to DB — require POST.
        // Month-navigation reloads (bFirstDisp="0") are GET-only display operations.
        String bFirstDisp = request.getParameter("bFirstDisp");
        boolean isMutation = bFirstDisp == null || "1".equals(bFirstDisp);
        if (isMutation && !"POST".equalsIgnoreCase(request.getMethod())) {
            ServletActionContext.getResponse().sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        return SUCCESS;
    }
}
