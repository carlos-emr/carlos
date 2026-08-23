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
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

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

    private final SecurityManager securityManager = SpringUtils.getBean(SecurityManager.class);
    private final MfaManager mfaManager = SpringUtils.getBean(MfaManager.class);
    private final SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute() {
        String method = request.getParameter("method");
        if (METHOD_RESET_MFA.equals(method)) {
            return resetMfa();
        }
        return SUCCESS;
    }

    /**
     * Resets the MFA secret for the security record identified by the {@code securityId} request
     * parameter.
     *
     * <p>This is a privileged mutation of another account's authentication state. It must be reached
     * by POST only — a GET would let the operation be triggered without a CSRF token (CSRFGuard
     * validates non-GET requests) — and requires security-administration write privilege. Without
     * these checks any authenticated user could reset an arbitrary account's MFA by supplying its
     * {@code securityId}.
     *
     * @return {@link #NONE}; a 405 status is set instead when the request is not a POST.
     */
    // IMPROPER_UNICODE: case-insensitive comparison of the literal HTTP method name, not user-identity folding.
    @SuppressFBWarnings(value = "IMPROPER_UNICODE",
            justification = "case-insensitive comparison of the literal HTTP method name, not user-identity folding")
    public String resetMfa() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }
        if (loggedInInfo == null) {
            // No authenticated session — return 403 rather than NPE in the privilege check below.
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return NONE;
        }
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "w", null)
                && !securityInfoManager.hasPrivilege(loggedInInfo, "_admin.userAdmin", "w", null)) {
            throw new SecurityException("missing required sec object (_admin or _admin.userAdmin)");
        }

        // A missing/non-numeric securityId is a bad request, not a 500 (Integer.parseInt(null) and a
        // non-numeric value both throw NumberFormatException).
        int securityIdValue;
        try {
            securityIdValue = Integer.parseInt(request.getParameter("securityId"));
        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }
        Security security = this.securityManager.find(loggedInInfo, securityIdValue);
        if (security == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return NONE;
        }
        this.mfaManager.resetMfaSecret(loggedInInfo, security);
        return NONE;
    }
}