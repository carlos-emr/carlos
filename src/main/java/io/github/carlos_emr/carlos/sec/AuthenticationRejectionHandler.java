/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.sec;

import java.io.IOException;
import java.util.Locale;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.utility.LogSanitizer;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import org.apache.logging.log4j.Logger;

/**
 * Shared response policy for unauthenticated requests that reach protected routes.
 *
 * <p>{@link LoginFilter} is the canonical authentication gate, but migrated Struts actions keep
 * lightweight session checks as defence-in-depth. Keeping the response decision here prevents
 * those fallback checks from drifting into route-specific redirect/status-code choices.</p>
 */
public final class AuthenticationRejectionHandler {
    private static final Logger LOGGER = MiscUtils.getLogger();
    private static final String LOGOUT_PATH = "/logoutPage";
    private static final String AJAX_HEADER = "X-Requested-With";
    private static final String AJAX_VALUE = "XMLHttpRequest";

    private static final String[] STATUS_CODE_PATHS = {
            "/Download",
            "/servlet/OscarDownload",
            "/report/reportDownload",
            "/contentRenderingServlet",
            "/imageRenderingServlet",
            "/eform/createpdf",
            "/eform/createcustomedpdf",
            "/form/createpdf",
            "/form/createcustomedpdf"
    };

    private AuthenticationRejectionHandler() {
        // Utility class.
    }

    /**
     * Rejects an unauthenticated request using the route-type policy.
     *
     * <p>Interactive browser pages are redirected to the legacy logout page so users land on the
     * normal login-recovery path. AJAX, API-like, and download routes receive a status code instead
     * of login HTML.</p>
     */
    public static void rejectUnauthenticatedRequest(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        boolean statusCodeRoute = isStatusCodeRoute(request);
        LOGGER.info(
                "Rejected unauthenticated request: method={}, uri={}, routeType={}, remote={}",
                LogSanitizer.sanitize(request.getMethod()),
                LogSanitizer.sanitize(normalizedRequestUri(request)),
                statusCodeRoute ? "status-code" : "browser-page",
                LogSanitizer.sanitize(request.getRemoteAddr()));

        if (statusCodeRoute) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        response.sendRedirect(request.getContextPath() + LOGOUT_PATH);
    }

    static boolean isStatusCodeRoute(HttpServletRequest request) {
        return isAjaxRequest(request)
                || prefersStructuredResponse(request)
                || isDownloadOrGeneratedContentPath(request);
    }

    private static boolean isAjaxRequest(HttpServletRequest request) {
        return AJAX_VALUE.equalsIgnoreCase(request.getHeader(AJAX_HEADER));
    }

    private static boolean prefersStructuredResponse(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        if (accept == null || accept.isBlank()) {
            return false;
        }

        String lowerAccept = accept.toLowerCase(Locale.ROOT);
        if (lowerAccept.contains("text/html")) {
            return false;
        }

        return lowerAccept.contains("application/json")
                || lowerAccept.contains("application/xml")
                || lowerAccept.contains("text/xml")
                || lowerAccept.contains("text/javascript")
                || lowerAccept.contains("application/javascript")
                || lowerAccept.contains("application/pdf")
                || lowerAccept.contains("application/octet-stream");
    }

    private static boolean isDownloadOrGeneratedContentPath(HttpServletRequest request) {
        String path = normalizedPathWithinContext(request);
        for (String statusCodePath : STATUS_CODE_PATHS) {
            if (path.equals(statusCodePath) || path.startsWith(statusCodePath + "/")) {
                return true;
            }
        }
        return false;
    }

    private static String normalizedPathWithinContext(HttpServletRequest request) {
        String normalizedUri = normalizedRequestUri(request);
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && normalizedUri.startsWith(contextPath)) {
            String withoutContext = normalizedUri.substring(contextPath.length());
            return withoutContext.isEmpty() ? "/" : withoutContext;
        }
        return normalizedUri;
    }

    private static String normalizedRequestUri(HttpServletRequest request) {
        String normalized = LoginFilter.normalizeUri(request.getRequestURI());
        return normalized == null ? "" : normalized;
    }
}
