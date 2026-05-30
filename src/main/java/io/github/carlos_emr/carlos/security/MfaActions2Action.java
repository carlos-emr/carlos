/**
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.security;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.managers.MfaManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.managers.SecurityManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * This class handles actions related to Multi-Factor Authentication (MFA).
 * It provides functionality to reset the MFA secret for a user.
 */
public final class MfaActions2Action extends ActionSupport {

    private HttpServletRequest request = ServletActionContext.getRequest();
    private HttpServletResponse response = ServletActionContext.getResponse();

    public static final String METHOD_RESET_MFA = "resetMfa";

    private final SecurityManager securityManager;
    private final MfaManager mfaManager;
    private final SecurityInfoManager securityInfoManager;

    public MfaActions2Action() {
        this(SpringUtils.getBean(SecurityManager.class),
                SpringUtils.getBean(MfaManager.class),
                SpringUtils.getBean(SecurityInfoManager.class));
    }

    MfaActions2Action(
            SecurityManager securityManager,
            MfaManager mfaManager,
            SecurityInfoManager securityInfoManager) {
        this.securityManager = securityManager;
        this.mfaManager = mfaManager;
        this.securityInfoManager = securityInfoManager;
    }

    @Override
    public String execute() throws IOException {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin.userSecurity", "w", null)) {
            throw new SecurityException("missing required sec object (_admin.userSecurity)");
        }

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        String method = request.getParameter("method");
        if (!METHOD_RESET_MFA.equals(method)) {
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }
        return resetMfa(loggedInInfo);
    }

    /**
     * Resets the MFA secret for a specified user.
     *
     * @return NONE, as this action does not forward to another page.
     */
    String resetMfa(LoggedInInfo loggedInInfo) throws IOException {
        String securityId = request.getParameter("securityId");
        Integer parsedSecurityId;
        try {
            parsedSecurityId = Integer.valueOf(securityId);
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid securityId");
            return NONE;
        }

        Security security = this.securityManager.find(loggedInInfo, parsedSecurityId);
        if (security == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Security record not found");
            return NONE;
        }

        this.mfaManager.resetMfaSecret(loggedInInfo, security);
        return NONE;
    }
}