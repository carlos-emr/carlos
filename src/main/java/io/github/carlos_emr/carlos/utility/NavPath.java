/*
 * Copyright (c) 2026 CARLOS EMR Project. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.utility;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;

/**
 * Path-matching helpers for server-side active navigation tab detection.
 *
 * <p>Checks both the live request path and any Jakarta forward attributes so
 * that forwarded requests activate the same nav tab as direct requests.
 *
 * @since 2026-05-27
 */
public final class NavPath {

    private NavPath() {
        // static utility
    }

    /**
     * Safely coerces a Jakarta forward attribute value to a path string.
     * Returns an empty string when the attribute is absent or not a String,
     * preventing ClassCastException from non-String forward attribute types.
     */
    static String forwardAttribute(Object value) {
        return value instanceof String string ? string : "";
    }

    /**
     * Returns {@code true} when {@code path} contains {@code pattern} at a
     * path-segment boundary.
     *
     * <p>A match is accepted when the pattern appears at the end of the string
     * or is immediately followed by {@code /}, {@code ?}, {@code ;}, or
     * {@code #}, preventing partial-segment false positives (e.g.
     * {@code /administration-panel} does not match {@code /administration}).
     * Patterns that end with {@code /} are accepted anywhere they appear as a
     * substring.
     */
    static boolean pathMatches(String path, String pattern) {
        if (StringUtils.isBlank(path) || StringUtils.isBlank(pattern)) {
            return false;
        }

        int fromIndex = 0;
        while (true) {
            int index = path.indexOf(pattern, fromIndex);
            if (index < 0) {
                return false;
            }

            int boundaryIndex = index + pattern.length();
            if (boundaryIndex >= path.length() || pattern.endsWith("/")) {
                return true;
            }

            char boundary = path.charAt(boundaryIndex);
            if (boundary == '/' || boundary == '?' || boundary == ';' || boundary == '#') {
                return true;
            }

            fromIndex = index + 1;
        }
    }

    /**
     * Returns {@code true} when the current request or any Jakarta forward
     * attribute matches one of the given path patterns.
     *
     * <p>Checks {@code getRequestURI()}, {@code getServletPath()},
     * {@code jakarta.servlet.forward.request_uri}, and
     * {@code jakarta.servlet.forward.servlet_path} so both direct and forwarded
     * requests activate the correct tab.
     *
     * @param request  the current servlet request
     * @param patterns one or more path patterns or segments to test
     * @return {@code true} if any path source matches any pattern
     */
    public static boolean requestPathMatches(HttpServletRequest request, String... patterns) {
        if (request == null || patterns == null || patterns.length == 0) {
            return false;
        }

        String requestUri = StringUtils.defaultString(request.getRequestURI());
        String servletPath = StringUtils.defaultString(request.getServletPath());
        String forwardRequestUri = forwardAttribute(request.getAttribute("jakarta.servlet.forward.request_uri"));
        String forwardServletPath = forwardAttribute(request.getAttribute("jakarta.servlet.forward.servlet_path"));

        for (String pattern : patterns) {
            if (pathMatches(requestUri, pattern)
                    || pathMatches(servletPath, pattern)
                    || pathMatches(forwardRequestUri, pattern)
                    || pathMatches(forwardServletPath, pattern)) {
                return true;
            }
        }
        return false;
    }
}
