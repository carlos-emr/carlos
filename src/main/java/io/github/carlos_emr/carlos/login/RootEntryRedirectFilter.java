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
package io.github.carlos_emr.carlos.login;

import java.io.IOException;

import io.github.carlos_emr.carlos.utility.LogSanitizer;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.Logger;

/**
 * Forwards login entrypoint requests to their JSP views.
 *
 * <p>This filter owns only public, view-only entry routes that need to render WEB-INF JSPs before
 * Struts is involved. It deliberately rejects non-GET/HEAD methods for these routes so a direct
 * POST to {@code /index} or {@code /forcepasswordreset} cannot be treated as a view render.
 * Public utility pages such as {@code /loginfailed} remain in Struts so their shared method and
 * security documentation stays in one canonical action.</p>
 *
 * <p>Do not add authenticated application pages here. Provider and clinical pages should stay
 * behind their Struts actions/gates so unauthenticated requests cannot bypass the normal
 * {@code LoginFilter} and {@code SecurityInfoManager} checks.</p>
 *
 * @since 2026-04-15
 */
public class RootEntryRedirectFilter extends HttpFilter {

    private static final Logger LOGGER = MiscUtils.getLogger();
    private static final String LOGIN_JSP = "/WEB-INF/jsp/login/index.jsp";
    private static final String FORCE_PASSWORD_RESET_PATH = "/forcepasswordreset";
    private static final String FORCE_PASSWORD_RESET_JSP = "/WEB-INF/jsp/login/forcepasswordreset.jsp";

    @Override
    protected void doFilter(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws IOException, ServletException {
        String contextPath = request.getContextPath();
        String requestUri = request.getRequestURI();

        if (isLoginEntryRequest(requestUri, contextPath)) {
            if (!isViewMethod(request.getMethod())) {
                LOGGER.info("Rejected login entry view: unsupported method={}, uri={}, remote={}",
                        LogSanitizer.sanitize(request.getMethod()),
                        LogSanitizer.sanitize(requestUri),
                        request.getRemoteAddr());
                response.setHeader("Allow", "GET, HEAD");
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                return;
            }
            request.getRequestDispatcher(LOGIN_JSP).forward(request, response);
            return;
        }

        if (isForcePasswordResetRequest(requestUri, contextPath)) {
            if (!isViewMethod(request.getMethod())) {
                LOGGER.info("Rejected /forcepasswordreset: unsupported method={}, uri={}, remote={}",
                        LogSanitizer.sanitize(request.getMethod()),
                        LogSanitizer.sanitize(requestUri),
                        request.getRemoteAddr());
                response.setHeader("Allow", "GET, HEAD");
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                return;
            }
            // The reset form is not a public page. It is view-only, but it still requires the
            // short-lived credential-cache token staged by a successful password-authentication
            // step. Without this guard, direct browsing could expose the reset UI without a
            // validated old credential.
            if (!Login2Action.hasValidLoginCredentialsToken(request)) {
                LOGGER.info("Rejected /forcepasswordreset: missing or stale credential token, remote={}",
                        request.getRemoteAddr());
                response.sendRedirect(Login2Action.loginFailedRedirectUrl(request,
                        Login2Action.message(request, "provider.providerchangepassword.errorSessionExpired")));
                return;
            }
            copyForcePasswordResetError(request);
            request.getRequestDispatcher(FORCE_PASSWORD_RESET_JSP).forward(request, response);
            return;
        }

        chain.doFilter(request, response);
    }

    static boolean isLoginEntryRequest(String requestUri, String contextPath) {
        return contextPath != null
                && !contextPath.isEmpty()
                && (requestUri.equals(contextPath)
                    || requestUri.equals(contextPath + "/")
                    || requestUri.equals(contextPath + "/index"));
    }

    static boolean isForcePasswordResetRequest(String requestUri, String contextPath) {
        return contextPath != null
                && !contextPath.isEmpty()
                && requestUri.equals(contextPath + FORCE_PASSWORD_RESET_PATH);
    }

    /**
     * Moves a retryable forced-reset validation error from session scope to request scope.
     *
     * <p>The reset POST redirects back to the GET view to avoid refresh resubmission. This method
     * gives the JSP one render of the error and then clears it so stale messages do not survive a
     * later successful retry.</p>
     */
    private static void copyForcePasswordResetError(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session == null) {
            return;
        }
        Object error = session.getAttribute(Login2Action.FORCE_PASSWORD_RESET_ERROR_ATTR);
        if (error instanceof String) {
            request.setAttribute("errormsg", error);
            session.removeAttribute(Login2Action.FORCE_PASSWORD_RESET_ERROR_ATTR);
        }
    }

    private static boolean isViewMethod(String method) {
        return "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method);
    }

}
