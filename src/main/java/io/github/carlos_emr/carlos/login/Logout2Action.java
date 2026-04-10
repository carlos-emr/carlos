/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.login;

import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Struts2 action class that handles user logout and session cleanup for CARLOS EMR.
 *
 * <p>This action manages three logout scenarios:
 * <ul>
 *   <li>Standard logout - User explicitly logs out</li>
 *   <li>Session timeout - User session expires due to inactivity</li>
 *   <li>Login failure - Logout triggered after failed authentication</li>
 * </ul>
 *
 * <p>Logout operations performed:
 * <ol>
 *   <li>Invalidate HTTP session to clear all session attributes</li>
 *   <li>Log logout event with user ID and IP address for audit trail</li>
 *   <li>Clear all browser cookies by setting maxAge to 0</li>
 *   <li>Return SUCCESS to redirect user to login page</li>
 * </ol>
 *
 * <p>Security considerations:
 * <ul>
 *   <li>Session invalidation prevents session fixation attacks</li>
 *   <li>Cookie clearing ensures complete logout across browser tabs</li>
 *   <li>Audit logging provides PHI-compliant access tracking</li>
 *   <li>Safe handling of null sessions (already logged out)</li>
 * </ul>
 *
 * <p>Usage from JSP/URL:
 * <pre>
 * /logout.do                     # Standard logout
 * /logout.do?method=sessionTimeout  # Session timeout
 * /logout.do?method=failure         # Login failure
 * </pre>
 *
 * <p>Struts configuration (struts.xml):
 * <pre>
 * &lt;action name="logout" class="io.github.carlos_emr.carlos.login.Logout2Action"&gt;
 *     &lt;result name="success"&gt;/logout.jsp&lt;/result&gt;
 * &lt;/action&gt;
 * </pre>
 *
 * @see Login2Action for authentication and session creation
 * @see LogAction for audit logging
 * @since 2026-02-10
 */
public class Logout2Action extends ActionSupport {
    /** Servlet request from Struts2 context */
    HttpServletRequest request = ServletActionContext.getRequest();

    /** Servlet response from Struts2 context */
    HttpServletResponse response = ServletActionContext.getResponse();

    /**
     * Main execution method that routes to specific logout handler based on method parameter.
     *
     * <p>This method examines the "method" request parameter to determine which
     * logout scenario applies:
     * <ul>
     *   <li>method=sessionTimeout → {@link #sessionTimeout()}</li>
     *   <li>method=failure → {@link #failure()}</li>
     *   <li>no method parameter → {@link #logout()} (standard logout)</li>
     * </ul>
     *
     * <p>All three methods ultimately perform the same logout operations,
     * but are kept separate to allow for future differentiation (e.g., different
     * redirect targets or additional cleanup steps).
     *
     * @return String Struts2 result name (always SUCCESS, redirects to logout.jsp)
     */
    public String execute() {
        String method = request.getParameter("method");

        // Route to appropriate logout handler based on method parameter
        if ("sessionTimeout".equals(method)) {
            return sessionTimeout();
        } else if ("failure".equals(method)) {
            return failure();
        }

        // Default to standard logout if no method specified
        return logout();
    }

    /**
     * Handles logout due to session timeout.
     *
     * <p>Called when user's session expires due to inactivity (default 2 hours).
     * Currently delegates to {@link #logout()} for session cleanup.
     *
     * @return String Struts2 result name (SUCCESS)
     */
    public String sessionTimeout() {
        return logout();
    }

    /**
     * Handles logout after login failure.
     *
     * <p>Called when authentication fails and user is redirected to logout page.
     * Currently delegates to {@link #logout()} for session cleanup.
     *
     * @return String Struts2 result name (SUCCESS)
     */
    public String failure() {
        return logout();
    }

    /**
     * Performs complete logout operation including session invalidation, audit logging, and cookie cleanup.
     *
     * <p>Logout operations performed:
     * <ol>
     *   <li>Retrieve existing session (if any) - does not create new session</li>
     *   <li>Extract user ID from session for audit logging</li>
     *   <li>Invalidate session to clear all attributes and prevent reuse</li>
     *   <li>Log logout event with user ID and client IP address</li>
     *   <li>Clear all browser cookies by setting maxAge to 0</li>
     * </ol>
     *
     * <p>Cookie cleanup creates fresh deletion cookies (empty value, maxAge=0) for each
     * cookie name found in the request, avoiding reflection of attacker-controlled cookie
     * values into Set-Cookie response headers:
     * <ul>
     *   <li>Creates a new Cookie per name with an empty value</li>
     *   <li>Copies only identity attributes (path, domain) from the original cookie</li>
     *   <li>Sets maxAge to 0 for immediate browser deletion</li>
     *   <li>Sets Secure (conditional on HTTPS), HttpOnly, and SameSite=Strict</li>
     *   <li>Adds the fresh deletion cookie to the response</li>
     * </ul>
     *
     * <p>Audit logging:
     * <ul>
     *   <li>Logs logout event only if user ID is available in session</li>
     *   <li>Records user ID, action type (LOGOUT), context (LOGIN), and IP address</li>
     *   <li>Provides PHI-compliant audit trail for security and compliance</li>
     * </ul>
     *
     * @return String Struts2 result name (always SUCCESS, redirects to logout.jsp)
     * @see HttpSession#invalidate() for session cleanup
     * @see Cookie#setMaxAge(int) for cookie expiration
     * @see LogAction#addLog for audit logging
     */
    public String logout() {

        // Retrieve existing session without creating new one
        HttpSession session = request.getSession(false);

        // Invalidate session and log logout event if session exists
        if (session != null) {
            String user = (String) session.getAttribute("user");
            // Invalidate session to prevent session fixation attacks
            session.invalidate();
            // Log logout event for audit trail (only if user was logged in)
            if (user != null) {
                LogAction.addLog(user, LogConst.LOGOUT, LogConst.CON_LOGIN, "", request.getRemoteAddr());
            }
        }

        // Clear all browser cookies to ensure complete logout
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                // Create a fresh deletion cookie to avoid reflecting attacker-controlled values
                Cookie deletion = new Cookie(cookie.getName(), "");
                deletion.setMaxAge(0);
                deletion.setPath("/");
                // Preserve domain if it was set on the original cookie
                if (cookie.getDomain() != null) {
                    deletion.setDomain(cookie.getDomain());
                }
                deletion.setSecure(request.isSecure());
                deletion.setHttpOnly(true);
                deletion.setAttribute("SameSite", "Strict");
                response.addCookie(deletion);
            }
        }
        return SUCCESS;
    }
}
