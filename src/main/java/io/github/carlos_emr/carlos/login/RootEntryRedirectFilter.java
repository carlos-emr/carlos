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
import java.util.Map;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Forwards login entrypoint requests to their JSP views.
 *
 * @since 2026-04-15
 */
public class RootEntryRedirectFilter extends HttpFilter {

    private static final String LOGIN_JSP = "/WEB-INF/jsp/login/index.jsp";
    private static final String FORCE_PASSWORD_RESET_PATH = "/forcepasswordreset";
    private static final String FORCE_PASSWORD_RESET_JSP = "/WEB-INF/jsp/login/forcepasswordreset.jsp";
    private static final Map<String, String> PUBLIC_VIEW_JSPS = Map.of(
            "/closenreload", "/WEB-INF/jsp/common/closenreload.jsp",
            "/errorpage", "/WEB-INF/jsp/error/errorpage.jsp",
            "/failure", "/WEB-INF/jsp/error/failure.jsp",
            "/loginfailed", "/WEB-INF/jsp/login/loginfailed.jsp",
            "/logoutPage", "/WEB-INF/jsp/login/logout.jsp",
            "/securityError", "/WEB-INF/jsp/error/securityError.jsp");

    @Override
    protected void doFilter(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws IOException, ServletException {
        String contextPath = request.getContextPath();
        String requestUri = request.getRequestURI();

        if (isLoginEntryRequest(requestUri, contextPath)) {
            if (!isViewMethod(request.getMethod())) {
                response.setHeader("Allow", "GET, HEAD");
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                return;
            }
            request.getRequestDispatcher(LOGIN_JSP).forward(request, response);
            return;
        }

        if (isForcePasswordResetRequest(requestUri, contextPath)) {
            if (!isViewMethod(request.getMethod())) {
                response.setHeader("Allow", "GET, HEAD");
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                return;
            }
            if (!Login2Action.hasValidLoginCredentialsToken(request)) {
                response.sendRedirect(Login2Action.loginFailedRedirectUrl(request,
                        Login2Action.message(request, "provider.providerchangepassword.errorSessionExpired")));
                return;
            }
            copyForcePasswordResetError(request);
            request.getRequestDispatcher(FORCE_PASSWORD_RESET_JSP).forward(request, response);
            return;
        }

        String publicViewJsp = publicViewJsp(requestUri, contextPath);
        if (publicViewJsp != null) {
            if (!isViewMethod(request.getMethod())) {
                response.setHeader("Allow", "GET, HEAD");
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                return;
            }
            request.getRequestDispatcher(publicViewJsp).forward(request, response);
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

    static String publicViewJsp(String requestUri, String contextPath) {
        if (contextPath == null || contextPath.isEmpty() || requestUri == null
                || !requestUri.startsWith(contextPath)) {
            return null;
        }
        String relativePath = requestUri.substring(contextPath.length());
        return PUBLIC_VIEW_JSPS.get(relativePath);
    }

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
