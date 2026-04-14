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
package io.github.carlos_emr.carlos.login.gate;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * View gate for the facility-picker page shown mid-login when a provider
 * has access to multiple facilities. Reachable pre-full-session (session
 * holds {@code userName} but facility is not yet chosen) so the normal
 * hasPrivilege check cannot be applied. The gate verifies that the caller
 * has at least passed login-form authentication ({@code session.getAttribute("userName") != null})
 * and returns SUCCESS; the container forwards to
 * {@code /WEB-INF/jsp/login/select_facility.jsp}.
 *
 * <p>GET/HEAD only. Scriptlet inside the JSP handles the POST that commits
 * the facility selection by re-posting to {@code login.do}.
 */
public final class ViewSelectFacility2Action extends ActionSupport {

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

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userName") == null) {
            throw new SecurityException("missing partial-auth session (userName)");
        }
        return SUCCESS;
    }
}
