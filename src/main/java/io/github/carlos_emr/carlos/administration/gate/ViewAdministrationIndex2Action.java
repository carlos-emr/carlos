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
package io.github.carlos_emr.carlos.administration.gate;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * View gate for {@code administration/index.jsp} — the admin dashboard home
 * page. Mirrors the JSP's internal {@code <security:oscarSec>} OR-list:
 * passes if the caller holds any ONE of the admin sub-privileges, since the
 * page itself hides / shows each card based on individual taglib checks.
 * GET/HEAD only.
 */
public final class ViewAdministrationIndex2Action extends ActionSupport {

    private static final String[] ADMIN_PRIVS = {
            "_admin",
            "_admin.userAdmin",
            "_admin.schedule",
            "_admin.billing",
            "_admin.resource",
            "_admin.reporting",
            "_admin.misc",
    };

    private final SecurityInfoManager securityInfoManager =
            SpringUtils.getBean(SecurityInfoManager.class);

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        String method = request.getMethod();
        if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
            response.setHeader("Allow", "GET, HEAD");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null) {
            throw new SecurityException("missing session");
        }
        for (String p : ADMIN_PRIVS) {
            if (securityInfoManager.hasPrivilege(loggedInInfo, p, "r", null)) {
                return SUCCESS;
            }
        }
        throw new SecurityException("missing required sec object (any of _admin*)");
    }
}
