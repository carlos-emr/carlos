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

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Security gate for the Provider Privilege admin page.
 *
 * <p>Requires either {@code _admin} or {@code _admin.userAdmin} write privilege.
 * The page's writes happen in {@code providerPrivilege.jsp}, keyed on parameter names
 * rather than on {@code method}, so {@code HttpMethodGuardFilter}'s generic check
 * never sees them. This gate answers 405 to any non-POST request that carries one of
 * {@link #WRITE_PARAMETERS}; a bare GET still opens the page.</p>
 *
 * @since 2026-04-05
 */
public class ProviderPrivilege2Action extends ActionSupport {

    /**
     * Parameters whose presence makes {@code providerPrivilege.jsp} write: {@code submit}
     * (Add or Delete a privilege row) and {@code buttonUpdate} (a row's Update, which merges
     * new privilege bits). The JSP acts on them whatever the request method, and a GET carries
     * no CSRF token, so a link alone could rewrite the authorization model (issue #3394).
     */
    static final List<String> WRITE_PARAMETERS = List.of("submit", "buttonUpdate");

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "w", null)
                && !securityInfoManager.hasPrivilege(loggedInInfo, "_admin.userAdmin", "w", null)) {
            throw new SecurityException("missing required sec object (_admin or _admin.userAdmin)");
        }

        // Any value counts, an empty one included: the JSP decides what to write, not this gate.
        boolean writes = WRITE_PARAMETERS.stream().anyMatch(name -> request.getParameter(name) != null);
        if (writes && !"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "POST required");
            return NONE;
        }

        return SUCCESS;
    }
}
