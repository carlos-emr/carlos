/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.utility;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Shared HTTP request/content negotiation predicates used by security-sensitive filters.
 *
 * <p>These checks intentionally preserve the legacy CARLOS contracts: AJAX means the
 * {@code X-Requested-With: XMLHttpRequest} convention, HTML means a {@code text/html}
 * content type prefix, and JSON preference means either {@code application/json} or a
 * structured {@code +json} media type appears in {@code Accept}. More sophisticated
 * content negotiation belongs in callers only when they are deliberately changing those
 * contracts.</p>
 *
 * @since 2026-05-17
 */
public final class RequestNegotiation {
    private static final String AJAX_HEADER = "X-Requested-With";
    private static final String AJAX_VALUE = "XMLHttpRequest";
    private static final String HTML_CONTENT_TYPE = "text/html";
    private static final String JSON_ACCEPT = "application/json";

    private RequestNegotiation() {
        // Utility class.
    }

    /** Detects CARLOS AJAX requests by the conventional {@code X-Requested-With} marker. */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public static boolean isAjax(HttpServletRequest request) {
        return request != null && AJAX_VALUE.equalsIgnoreCase(request.getHeader(AJAX_HEADER));
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
