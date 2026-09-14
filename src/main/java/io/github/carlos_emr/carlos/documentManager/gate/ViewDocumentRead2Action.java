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
package io.github.carlos_emr.carlos.documentManager.gate;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Read-only view gate for document-manager JSPs that render document lists,
 * browsers, and single-document display. Requires {@code _edoc r} before
 * forwarding to the JSP. Replaces direct-GET access that relied only on the
 * in-JSP {@code <security:oscarSec>} taglib for defense.
 *
 * <p>This base gate enforces only the shared {@code _edoc r} check and then
 * forwards. Routes that need additional request validation or per-patient
 * authorization use a dedicated subclass and override
 * {@link #afterPrivilegeGranted}; see {@link ViewDocumentReportRead2Action}.
 * Detection is therefore by mapped class, not by sniffing the request path —
 * a route can never silently fail open to the base "forward" behavior.
 */
public class ViewDocumentRead2Action extends ActionSupport {

    @Override
    public final String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        SecurityInfoManager sim = SpringUtils.getBean(SecurityInfoManager.class);
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null || !sim.hasPrivilege(loggedInInfo, "_edoc", "r", null)) {
            throw new SecurityException("missing required sec object (_edoc r)");
        }
        return afterPrivilegeGranted(request, response, sim, loggedInInfo);
    }

    /**
     * Hook invoked after the shared {@code _edoc r} check passes. The base gate
     * forwards to its mapped JSP ({@link #SUCCESS}). Subclasses that gate a route
     * with extra validation or per-patient authorization override this and may
     * write an error response and return {@link #NONE}.
     */
    protected String afterPrivilegeGranted(HttpServletRequest request,
                                           HttpServletResponse response,
                                           SecurityInfoManager sim,
                                           LoggedInInfo loggedInInfo) throws Exception {
        return SUCCESS;
    }
}
