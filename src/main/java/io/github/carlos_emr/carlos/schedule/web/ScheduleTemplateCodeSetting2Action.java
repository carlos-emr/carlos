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
 * Gate action for the appointment template code settings page.
 * Enforces schedule admin write privilege before forwarding to the JSP.
 * Write operations require POST; GET is permitted for initial popup display and Edit reads.
 *
 * @since 2026-04-05
 */
public final class ScheduleTemplateCodeSetting2Action extends ActionSupport {

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin.schedule", "w", null)) {
            throw new SecurityException("missing required sec object (_admin.schedule)");
        }

        String dboperation = request.getParameter("dboperation");
        boolean isMutation = dboperation != null
                && (dboperation.trim().equals("Save") || dboperation.trim().equals("Delete")
                    || dboperation.compareTo(" Save ") == 0);
        if (isMutation && !"POST".equalsIgnoreCase(request.getMethod())) {
            ServletActionContext.getResponse().sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        return SUCCESS;
    }
}
