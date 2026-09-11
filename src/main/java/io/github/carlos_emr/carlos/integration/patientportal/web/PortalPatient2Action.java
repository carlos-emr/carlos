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
package io.github.carlos_emr.carlos.integration.patientportal.web;

import io.github.carlos_emr.carlos.integration.patientportal.PortalStaffContextResolver;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Patient-scoped view gate for the staff portal-account screen.
 *
 * <p>The page itself contains no account data. It receives only the selected demographic number
 * and capability flags, then obtains current state through {@link PortalPanel2Action}. The JSON
 * read and every mutation repeat their authorization checks, so keeping a stale browser page open
 * cannot preserve authority after a role change.
 *
 * @since 2026-09-10
 */
public final class PortalPatient2Action extends ActionSupport {

    private static final long serialVersionUID = 1L;

    private final transient SecurityInfoManager securityInfoManager;

    /** Struts instantiates actions reflectively. */
    public PortalPatient2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class));
    }

    PortalPatient2Action(SecurityInfoManager securityInfoManager) {
        this.securityInfoManager = securityInfoManager;
    }

    @Override
    public String execute() {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        if (!"GET".equals(request.getMethod())) {
            response.setHeader("Allow", "GET");
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        int demographicNo = PortalJsonAction.positiveInt(request.getParameter("demographicNo"));
        if (demographicNo <= 0) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        PortalJsonAction.requirePatientAccess(securityInfoManager, loggedInInfo, demographicNo);
        PortalJsonAction.requirePatientPrivilege(
                securityInfoManager,
                loggedInInfo,
                PortalStaffContextResolver.OBJECT_ACCOUNT,
                SecurityInfoManager.READ,
                demographicNo);

        String patientScope = String.valueOf(demographicNo);
        request.setAttribute("demographicNo", patientScope);
        request.setAttribute(
                "canManagePortalAccount",
                securityInfoManager.hasPrivilege(
                        loggedInInfo,
                        PortalStaffContextResolver.OBJECT_ACCOUNT,
                        SecurityInfoManager.WRITE,
                        patientScope));
        request.setAttribute(
                "canUnlockPortalAccount",
                securityInfoManager.hasPrivilege(
                        loggedInInfo,
                        PortalStaffContextResolver.OBJECT_ACCOUNT_UNLOCK,
                        SecurityInfoManager.WRITE,
                        patientScope));
        return SUCCESS;
    }
}
