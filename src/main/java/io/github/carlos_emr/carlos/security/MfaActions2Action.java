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
import io.github.carlos_emr.carlos.managers.SecurityManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;

import org.apache.logging.log4j.Logger;

/**
 * This class handles actions related to Multi-Factor Authentication (MFA).
 * It provides functionality to reset the MFA secret for a user.
 *
 * <p>This is a direct-response action: {@link #resetMfa()} writes a JSON body
 * (or an error status) to the servlet response and returns {@link #NONE}, so no
 * {@code <result>} is mapped for the {@code securityRecord/mfa} route.</p>
 */
public final class MfaActions2Action extends ActionSupport {

    private static final Logger logger = MiscUtils.getLogger();

    public static final String METHOD_RESET_MFA = "resetMfa";

    private final transient SecurityManager securityManager;
    private final transient MfaManager mfaManager;
    private final transient CarlosMethodSecurity methodSecurity;

    /**
     * Creates the Spring-managed action.
     *
     * <p>Dependencies are supplied through constructor injection instead of the
     * older {@code SpringUtils} service-locator style so the authorization and
     * persistence wiring stays explicit and verifiable in tests.</p>
     *
     * @param securityManager the manager used to load the target security record
     * @param mfaManager the manager that clears the stored MFA secret
     * @param methodSecurity the helper that evaluates the shared admin write policy
     */
    public MfaActions2Action(SecurityManager securityManager, MfaManager mfaManager,
            CarlosMethodSecurity methodSecurity) {
        this.securityManager = securityManager;
        this.mfaManager = mfaManager;
        this.methodSecurity = methodSecurity;
    }

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        String method = request.getParameter("method");
        if (METHOD_RESET_MFA.equals(method)) {
            return resetMfa();
        }
        return SUCCESS;
    }

    /**
     * Resets the MFA secret for the security record identified by the
     * {@code securityId} request parameter.
     *
     * <p>Requires a POST request and {@code _admin w} or {@code _admin.userAdmin w}
     * privilege. A missing, blank, or non-numeric {@code securityId} yields a
     * {@code 400 Bad Request}; an unknown record yields a {@code 404 Not Found}.
     * On success a {@code {"success":true}} JSON body is written.</p>
     *
     * @return {@link #NONE} after writing the response
     * @throws Exception if writing the response fails
     */
    public String resetMfa() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "POST required");
            return NONE;
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!methodSecurity.hasAdminWrite()) {
            throw new SecurityException("missing required sec object (_admin or _admin.userAdmin)");
        }

        String securityId = request.getParameter("securityId");
        if (securityId == null || securityId.isBlank()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "securityId is required");
            return NONE;
        }

        int id;
        try {
            id = Integer.parseInt(securityId.trim());
        } catch (NumberFormatException e) {
            logger.warn("Rejected MFA reset request with non-numeric securityId");
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "securityId must be numeric");
            return NONE;
        }

        Security security = this.securityManager.find(loggedInInfo, id);
        if (security == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "security record not found");
            return NONE;
        }

        this.mfaManager.resetMfaSecret(loggedInInfo, security);

        response.setContentType("application/json;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.write("{\"success\":true}");
        }
        return NONE;
    }
}