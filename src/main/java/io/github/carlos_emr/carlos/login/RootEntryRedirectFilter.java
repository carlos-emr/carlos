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

import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.Logger;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Forwards login entrypoint requests to their JSP views.
 *
 * <p>This filter owns only the public login entry route that needs to render a WEB-INF JSP before
 * Struts is involved. It deliberately rejects non-GET/HEAD methods for this route so a direct
 * POST to {@code /index} cannot be treated as a view render. It also enforces the same view-method
 * guard for {@code /forcepasswordreset} before CSRFGuard runs; GET/HEAD still pass through to the
 * canonical Struts action so token validation and retry-error handoff stay in one place.</p>
 *
 * <p>Do not add authenticated application pages here. Provider and clinical pages should stay
 * behind their Struts actions/gates so unauthenticated requests cannot bypass the normal
 * {@code LoginFilter} and {@code SecurityInfoManager} checks.</p>
 *
 * @since 2026-04-15
 */
public class RootEntryRedirectFilter extends HttpFilter {

    private static final Logger LOGGER = MiscUtils.getLogger();
    private static final String FORCE_PASSWORD_RESET_PATH = "/forcepasswordreset";
    private static final String LOGIN_JSP = "/WEB-INF/jsp/login/index.jsp";

    @Override
    protected void doFilter(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws IOException, ServletException {
        String contextPath = request.getContextPath();
        String requestUri = request.getRequestURI();

        if (isLoginEntryRequest(requestUri, contextPath)) {
            if (!isViewMethod(request.getMethod())) {
                rejectNonViewMethod(request, response, requestUri, "login entry view");
                return;
            }
            request.getRequestDispatcher(LOGIN_JSP).forward(request, response);
            return;
        }

        if (isForcePasswordResetRequest(requestUri, contextPath) && !isViewMethod(request.getMethod())) {
            rejectNonViewMethod(request, response, requestUri, "force password reset view");
            return;
        }

        chain.doFilter(request, response);
    }

    static boolean isLoginEntryRequest(String requestUri, String contextPath) {
        String normalizedContextPath = contextPath == null ? "" : contextPath;
        if (normalizedContextPath.isEmpty()) {
            return "/".equals(requestUri) || "/index".equals(requestUri);
        }
        return requestUri.equals(normalizedContextPath)
                || requestUri.equals(normalizedContextPath + "/")
                || requestUri.equals(normalizedContextPath + "/index");
    }

    static boolean isForcePasswordResetRequest(String requestUri, String contextPath) {
        String normalizedContextPath = contextPath == null ? "" : contextPath;
        return requestUri.equals(normalizedContextPath + FORCE_PASSWORD_RESET_PATH);
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    private static boolean isViewMethod(String method) {
        return "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method);
    }

    private static void rejectNonViewMethod(
            HttpServletRequest request,
            HttpServletResponse response,
            String requestUri,
            String routeName) throws IOException {
        LOGGER.info("Rejected {}: unsupported method={}, uri={}, remote={}",
                routeName,
                LogSafe.sanitize(request.getMethod()),
                LogSafe.sanitizeUri(requestUri),
                LogSafe.sanitize(request.getRemoteAddr()));
        response.setHeader("Allow", "GET, HEAD");
        response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }

}
