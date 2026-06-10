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

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Shared gate for admin security mutator 2Actions.
 *
 * <p>Requests require either {@code _admin} or {@code _admin.userAdmin} write
 * privilege. Authorized requests must use POST; non-POST requests receive
 * HTTP 405 with {@code Allow: POST} and return {@link #NONE}. Failed
 * authorization throws {@link SecurityException}. Subclasses expose only the
 * Struts action name; the JSP mutation runs after this gate succeeds.</p>
 */
abstract class AdminSecurityMutator2Action extends ActionSupport {

    private final transient SecurityInfoManager securityInfoManager;

    protected AdminSecurityMutator2Action(SecurityInfoManager securityInfoManager) {
        this.securityInfoManager = securityInfoManager;
    }

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!hasAdminWritePrivilege(loggedInInfo)) {
            throw new SecurityException("missing required sec object (_admin or _admin.userAdmin)");
        }

        if (!isPost(request)) {
            HttpServletResponse response = ServletActionContext.getResponse();
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "POST required");
            return NONE;
        }

        return SUCCESS;
    }

    private boolean hasAdminWritePrivilege(LoggedInInfo loggedInInfo) {
        return securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "w", null)
                || securityInfoManager.hasPrivilege(loggedInInfo, "_admin.userAdmin", "w", null);
    }

    private static boolean isPost(HttpServletRequest request) {
        return "POST".equals(request.getMethod());
    }
}
