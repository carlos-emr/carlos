/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.utility;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;

/**
 * Shared HTTP request/content negotiation predicates used by security-sensitive filters.
 *
 * <p>These checks intentionally preserve the legacy CARLOS contracts: AJAX means the
 * {@code X-Requested-With: XMLHttpRequest} convention, HTML means a {@code text/html}
 * content type prefix, and JSON preference means the literal {@code application/json}
 * media type appears in {@code Accept}. More sophisticated media-type parsing belongs in
 * callers only when they are deliberately changing those contracts.</p>
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

    /**
     * @return true when the request uses the conventional CARLOS AJAX marker
     */
    public static boolean isAjax(HttpServletRequest request) {
        return request != null && AJAX_VALUE.equalsIgnoreCase(request.getHeader(AJAX_HEADER));
    }

    /**
     * @return true when the response content type is HTML
     */
    public static boolean isHtmlContentType(String contentType) {
        return contentType != null
                && contentType.toLowerCase(Locale.ROOT).startsWith(HTML_CONTENT_TYPE);
    }

    /**
     * @return true when the request explicitly includes the literal JSON media type
     */
    public static boolean acceptsJson(HttpServletRequest request) {
        String accept = request == null ? null : request.getHeader("Accept");
        return accept != null && accept.toLowerCase(Locale.ROOT).contains(JSON_ACCEPT);
    }
}
