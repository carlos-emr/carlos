/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.utility;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Enumeration;
import java.util.Locale;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.owasp.csrfguard.CsrfGuard;

/**
 * Shared HTTP request/content negotiation predicates used by security-sensitive filters.
 *
 * <p>These checks intentionally preserve the legacy CARLOS contracts: AJAX means the
 * {@code X-Requested-With} convention (read as a list — see {@link #isAjax}), HTML means a
 * {@code text/html} content type prefix, and JSON preference means either {@code application/json} or a
 * structured {@code +json} media type appears in {@code Accept}. More sophisticated
 * content negotiation belongs in callers only when they are deliberately changing those
 * contracts.</p>
 *
 * @since 2026-05-17
 */
public final class RequestNegotiation {
    private static final String AJAX_HEADER = "X-Requested-With";
    private static final String AJAX_VALUE = "XMLHttpRequest";
    /**
     * Fallback for the marker CSRFGuard's client script stamps on every XHR it hijacks: the
     * shipped value of {@code org.owasp.csrfguard.JavascriptServlet.xRequestedWith} in
     * {@code src/main/webapp/WEB-INF/Owasp.CsrfGuard.properties}.
     *
     * <p>Used only when the running configuration cannot be read — see
     * {@link #csrfGuardAjaxMarker()}. An installation that overrides the property in
     * {@code Owasp.CsrfGuard.overlay.properties} is honoured by reading it at runtime, because a
     * hard-coded copy would stop matching and every {@code carlos-ajax.js} request on that
     * installation would be classified as a browser page request again.</p>
     */
    private static final String CSRFGUARD_DEFAULT_AJAX_VALUE = "OWASP CSRFGuard Project";
    private static final String HTML_CONTENT_TYPE = "text/html";
    private static final String JSON_ACCEPT = "application/json";

    private RequestNegotiation() {
        // Utility class.
    }

    /**
     * Detects CARLOS AJAX requests by the conventional {@code X-Requested-With} marker.
     *
     * <p>The header is treated as a LIST, not a single value, because in this application it
     * reliably arrives as one. CSRFGuard's injected client script hijacks
     * {@code XMLHttpRequest.send()} and calls {@code setRequestHeader(X-Requested-With, ...)}
     * with its own marker; per the XHR specification a repeated {@code setRequestHeader}
     * <em>combines</em> values with {@code ", "} rather than replacing them. A jQuery
     * {@code $.ajax()} call — which sets {@code XMLHttpRequest} itself before {@code send()} —
     * therefore reaches the server as {@code "XMLHttpRequest, OWASP CSRFGuard Project"}, and an
     * exact-match check treats a plainly-AJAX request as a browser page request. Requests issued
     * through {@code carlos-ajax.js} deliberately leave the header to CSRFGuard, so they arrive
     * carrying only its marker.</p>
     *
     * <p>Getting this wrong is not cosmetic: the response-decorating filters keyed off this
     * predicate ({@link io.github.carlos_emr.carlos.app.LogoutBroadcastFilter} in particular)
     * append a {@code <script>} block to {@code text/html} AJAX responses, which callers that
     * render the response body verbatim then show to the clinician as literal JavaScript.</p>
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public static boolean isAjax(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        // Resolved once per request rather than per header line: it reads a singleton's field,
        // but there is no reason to do it repeatedly.
        String csrfGuardMarker = csrfGuardAjaxMarker();

        // Repeated header lines are as legal as one combined line; check both shapes.
        Enumeration<String> headerValues = request.getHeaders(AJAX_HEADER);
        if (headerValues == null) {
            return containsAjaxMarker(request.getHeader(AJAX_HEADER), csrfGuardMarker);
        }
        boolean sawAny = false;
        while (headerValues.hasMoreElements()) {
            sawAny = true;
            if (containsAjaxMarker(headerValues.nextElement(), csrfGuardMarker)) {
                return true;
            }
        }
        return sawAny ? false : containsAjaxMarker(request.getHeader(AJAX_HEADER), csrfGuardMarker);
    }

    /**
     * The marker CSRFGuard's client script is actually configured to send.
     *
     * <p>Read from the running CSRFGuard rather than copied, because this application uses
     * {@code ConfigurationAutodetectProviderFactory} and therefore honours
     * {@code Owasp.CsrfGuard.overlay.properties}: an installation may override
     * {@code JavascriptServlet.xRequestedWith}, and {@code carlos-ajax.js} then sends only that
     * value. A hard-coded copy would miss it, and every such request would be treated as a
     * browser page request — the whole fault this predicate exists to prevent.</p>
     *
     * <p>Falls back to the shipped default when CSRFGuard is not initialised, which is the case
     * in unit tests and during early startup.</p>
     */
    private static String csrfGuardAjaxMarker() {
        try {
            String configured = CsrfGuard.getInstance().getJavascriptXrequestedWith();
            if (configured != null && !configured.isBlank()) {
                return configured;
            }
        } catch (RuntimeException | LinkageError e) {
            // Not initialised yet, or no configuration on the classpath. The default below is
            // still correct for every installation that has not overridden the property.
        }
        return CSRFGUARD_DEFAULT_AJAX_VALUE;
    }

    /**
     * Tests one {@code X-Requested-With} header line for a recognised XHR marker.
     *
     * <p>The shipped default is accepted alongside the configured marker. On an installation
     * that has overridden the property the default can no longer arrive from CSRFGuard itself,
     * and accepting it costs only the response decoration on a request that deliberately sent
     * it — never a CSRF check, which is validated separately.</p>
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    private static boolean containsAjaxMarker(String headerValue, String csrfGuardMarker) {
        if (headerValue == null) {
            return false;
        }
        for (String marker : headerValue.split(",")) {
            String trimmed = marker.trim();
            if (AJAX_VALUE.equalsIgnoreCase(trimmed)
                    || CSRFGUARD_DEFAULT_AJAX_VALUE.equalsIgnoreCase(trimmed)
                    || csrfGuardMarker.equalsIgnoreCase(trimmed)) {
                return true;
            }
        }
        return false;
    }

    /** Detects HTML response content types by prefix so charset parameters are accepted. */
    public static boolean isHtmlContentType(String contentType) {
        return contentType != null
                && contentType.toLowerCase(Locale.ROOT).startsWith(HTML_CONTENT_TYPE);
    }

    /** Detects JSON media ranges, including structured suffixes such as {@code problem+json}. */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public static boolean acceptsJson(HttpServletRequest request) {
        String accept = request == null ? null : request.getHeader("Accept");
        if (accept == null || accept.isBlank()) {
            return false;
        }
        for (String mediaRange : accept.toLowerCase(Locale.ROOT).split(",")) {
            String mediaType = mediaRange.split(";", 2)[0].trim();
            if (JSON_ACCEPT.equals(mediaType) || mediaType.endsWith("+json")) {
                return true;
            }
        }
        return false;
    }
}
