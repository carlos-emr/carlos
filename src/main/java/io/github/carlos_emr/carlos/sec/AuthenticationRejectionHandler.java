/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.sec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
 *
 * <p>Browser page requests receive the legacy login redirect. AJAX and structured/download routes
 * receive direct {@code 401} responses: callers whose {@code Accept} header includes
 * {@code application/json} get a small JSON error body, while other status-code routes get plain
 * text so scripted clients do not parse a login page as data.</p>
 */
public final class AuthenticationRejectionHandler {
    private static final Logger LOGGER = MiscUtils.getLogger();
    private static final String LOGOUT_PATH = "/logoutPage";
    private static final String AJAX_HEADER = "X-Requested-With";
    private static final String AJAX_VALUE = "XMLHttpRequest";

    /**
     * Paths whose unauthenticated responses are consumed by scripts, downloads, or generated
     * content clients. A request matches an entry exactly or as a child path, for example
     * {@code /Download/file.pdf} matches {@code /Download} but {@code /Downloads} does not. The
     * {@code List.of(...)} contents are unmodifiable, and the unit test pins the exact route
     * set so future migrations cannot accidentally change the rejection contract.
     */
    static final List<String> STATUS_CODE_PATHS = List.of(
            "/Download",
            "/servlet/OscarDownload",
            "/report/reportDownload",
            "/contentRenderingServlet",
            "/imageRenderingServlet",
            "/eform/createpdf",
            "/eform/createcustomedpdf",
            "/form/createpdf",
            "/form/createcustomedpdf"
    );

    private AuthenticationRejectionHandler() {
        // Utility class.
    }

    /**
     * Rejects an unauthenticated request using the route-type policy.
     *
     * <p>Interactive browser pages are redirected to the legacy logout page so users land on the
     * normal login-recovery path. AJAX, API-like, and download routes receive a status code instead
     * of login HTML. JSON-preferring status routes receive {@code application/json}; other
     * status-code routes receive {@code text/plain}.</p>
     */
    public static void rejectUnauthenticatedRequest(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        boolean statusCodeRoute = isStatusCodeRoute(request);
        String routeType = statusCodeRoute ? "status-code" : "browser-page";
        String method = LogSanitizer.sanitize(request.getMethod());
        String uri = LogSanitizer.sanitize(normalizedRequestUri(request));
        String remote = LogSanitizer.sanitize(request.getRemoteAddr());
        String acceptHint = LogSanitizer.sanitize(request.getHeader("Accept"));

        if (response.isCommitted()) {
            LOGGER.warn(
                    "Unable to reject unauthenticated request because response is already "
                            + "committed: method={}, uri={}, routeType={}, remote={}, acceptHint={}",
                    method,
                    uri,
                    routeType,
                    remote,
                    acceptHint);
            return;
        }

        if (statusCodeRoute) {
            writeStatusCodeRejection(request, response);
        } else {
            response.sendRedirect(request.getContextPath() + LOGOUT_PATH);
        }

        LOGGER.info(
                "Rejected unauthenticated request: method={}, uri={}, routeType={}, remote={}, acceptHint={}",
                method,
                uri,
                routeType,
                remote,
                acceptHint);
    }

    /**
     * Writes the direct {@code 401} rejection body for AJAX, API-like, and generated-content paths.
     *
     * <p>The status-code route decision is separate from body format. XML, PDF, and download
     * callers still get a status code, but only callers whose {@code Accept} header mentions JSON
     * receive a JSON body. This deliberately uses {@link HttpServletResponse#setStatus(int)} plus
     * an explicit body instead of {@code sendError}; the container error-page mechanism would
     * otherwise replace the JSON/text contract with login or error-page HTML. The body writer also
     * tolerates upstream filters that already obtained the servlet output stream.</p>
     */
    private static void writeStatusCodeRejection(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        if (prefersJsonResponse(request)) {
            response.setContentType("application/json;charset=UTF-8");
            writeBody(request, response, "{\"error\":\"unauthorized\"}");
            return;
        }
        response.setContentType("text/plain;charset=UTF-8");
        writeBody(request, response, "Unauthorized");
    }

    /**
     * Writes the small direct-response body after content negotiation has already been decided.
     *
     * <p>The request is only used for PHI-safe diagnostic context if upstream wrappers have already
     * obtained the servlet output stream and the writer path is unavailable.</p>
     */
    private static void writeBody(
            HttpServletRequest request,
            HttpServletResponse response,
            String body) throws IOException {

        try {
            response.getWriter().write(body);
        } catch (IllegalStateException writerUnavailable) {
            LOGGER.warn(
                    "Response writer unavailable while writing unauthenticated rejection body; "
                            + "falling back to output stream: method={}, uri={}, remote={}",
                    LogSanitizer.sanitize(request.getMethod()),
                    LogSanitizer.sanitize(normalizedRequestUri(request)),
                    LogSanitizer.sanitize(request.getRemoteAddr()),
                    writerUnavailable);
            response.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
        }
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

        // Accept: */* is deliberately treated as ambiguous browser-style traffic. Callers that
        // need a 401 should send an explicit structured media type or the AJAX header.
        return lowerAccept.contains("application/json")
                || lowerAccept.contains("application/xml")
                || lowerAccept.contains("text/xml")
                || lowerAccept.contains("text/javascript")
                || lowerAccept.contains("application/javascript")
                || lowerAccept.contains("application/pdf")
                || lowerAccept.contains("application/octet-stream");
    }

    /**
     * Returns whether the caller explicitly accepts JSON for direct authentication failures.
     *
     * <p>Uses substring matching to support normal multi-value {@code Accept} headers such as
     * {@code application/xml, application/json;q=0.8}. This intentionally does not parse
     * {@code q=} weights: any explicit literal {@code application/json} token is treated as a
     * JSON-capable client. Structured suffixes such as {@code application/problem+json},
     * {@code application/ld+json}, and {@code application/vnd.api+json} do not match this legacy
     * check unless they also include a separate {@code application/json} value.</p>
     */
    private static boolean prefersJsonResponse(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return accept != null && accept.toLowerCase(Locale.ROOT).contains("application/json");
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
