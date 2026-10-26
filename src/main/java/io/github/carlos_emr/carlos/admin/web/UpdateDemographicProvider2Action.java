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
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Security gate for the Update Demographic Provider admin page.
 *
 * <p>Requires {@code _admin.misc} read privilege before forwarding to the JSP.
 * Reassignment additionally requires POST and an administrative write privilege
 * before any update logic in the JSP can run.</p>
 *
 * @since 2026-04-05
 */
public class UpdateDemographicProvider2Action extends ActionSupport {

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    @Override
    public String execute() {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin.misc", "r", null)) {
            throw new SecurityException("missing required sec object (_admin.misc)");
        }

        if (request.getParameter("update") != null) {
            if (!"POST".equals(request.getMethod())) {
                HttpServletResponse response = ServletActionContext.getResponse();
                response.setHeader("Allow", "POST");
                response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                return NONE;
            }
            if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin.misc", "w", null)
                    && !securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "w", null)) {
                throw new SecurityException("missing required write privilege (_admin.misc or _admin)");
            }
        }

        return SUCCESS;
    }
}
