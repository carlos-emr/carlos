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

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Forwards login entrypoint requests to their JSP views.
 *
 * @since 2026-04-15
 */
public class RootEntryRedirectFilter extends HttpFilter {

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
            request.getRequestDispatcher(LOGIN_JSP).forward(request, response);
            return;
        }

        if (isForcePasswordResetRequest(requestUri, contextPath)) {
            if (!isViewMethod(request.getMethod())) {
                response.setHeader("Allow", "GET, HEAD");
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                return;
            }
            if (!hasValidLoginCredentialsToken(request)) {
                response.sendRedirect(Login2Action.loginFailedRedirectUrl(request,
                        "Your password reset session has expired. Please log in again."));
                return;
            }
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

    private static boolean isViewMethod(String method) {
        return "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method);
    }

    private static boolean hasValidLoginCredentialsToken(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return false;
        }
        Object tokenAttr = session.getAttribute(Login2Action.LOGIN_CREDENTIALS_TOKEN_ATTR);
        return tokenAttr instanceof String token
                && LoginCredentialCache.getInstance().peek(token) != null;
    }
}
