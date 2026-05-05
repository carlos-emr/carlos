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
package io.github.carlos_emr.carlos.utility;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Logs JSP-render exceptions captured by the global {@code <error-page>}
 * handler. Extracted from the {@code errorpage.jsp} scriptlet so the logic
 * is unit-testable without a JSP container.
 *
 * <p>JSP-render exceptions used to vanish into a generic "CARLOS Error 500"
 * page with no stack trace in catalina.out — operations could not diagnose
 * anything that lived in JSP-render. This helper makes every JSP a free
 * post-mortem trail without per-page try/catch.</p>
 *
 * @since 2026-04-25
 */
public final class ErrorPageLogger {

    private ErrorPageLogger() {
        // utility
    }

    /**
     * Logs the captured exception (if present) at ERROR with URI and status
     * context. Safe to call when no exception is present (no-op).
     *
     * @param explicitException the exception bound to the JSP page-context
     *                          (i.e., {@code pageContext.exception}); may be
     *                          {@code null}
     * @param request           the request from which to read the
     *                          {@code jakarta.servlet.error.*} attributes
     */
    public static void logIfPresent(Throwable explicitException, ServletRequest request) {
        // The error page itself MUST NEVER throw — if it did, the user would
        // see a different (more confusing) error page than the one the JSP
        // hierarchy is supposed to produce, and a stack-trace log loop is
        // possible if errorpage.jsp is itself the next page Tomcat tries to
        // render. Wrap the entire body in a defensive try/catch and swallow
        // anything that escapes the log call (corrupted servlet attribute
        // bound to a non-Throwable, log4j2 ConfigurationException at runtime,
        // ClassCastException on a buggy filter that stashed a String under
        // jakarta.servlet.error.exception, etc.).
        try {
            Throwable t = explicitException;
            if (t == null && request != null) {
                Object attr = request.getAttribute("jakarta.servlet.error.exception");
                if (attr instanceof Throwable) {
                    t = (Throwable) attr;
                }
            }
            if (t == null) {
                return;
            }
            // Strip the query string AND any path parameters (`;jsessionid=...`,
            // etc.) off the captured request_uri before logging:
            //  - billing flows pass mixed identifiers in the query; some are
            //    clinical/accounting identifiers even though demographic_no
            //    itself is not PHI per CLAUDE.md
            //  - Tomcat can include `;jsessionid=...` as a path parameter on
            //    cookieless requests, which is sensitive and would let an
            //    operator with log access hijack the session.
            // Path-only is enough to diagnose the failure site.
            Object rawUri = request != null
                    ? request.getAttribute("jakarta.servlet.error.request_uri")
                    : null;
            Object uri = rawUri;
            if (rawUri instanceof String) {
                String s = (String) rawUri;
                int q = s.indexOf('?');
                if (q >= 0) {
                    s = s.substring(0, q);
                }
                int sc = s.indexOf(';');
                if (sc >= 0) {
                    s = s.substring(0, sc);
                }
                uri = s;
            }
            Object status = request != null
                    ? request.getAttribute("jakarta.servlet.error.status_code")
                    : null;
            // For HttpServletRequest, also attempt to log the original request
            // method when available — useful for distinguishing GET vs POST
            // failures on the same URI.
            Object method = (request instanceof HttpServletRequest)
                    ? ((HttpServletRequest) request).getMethod()
                    : null;
            MiscUtils.getLogger().error(
                    "errorpage.jsp captured exception (method={}, uri={}, status={})",
                    method, uri, status, t);
        } catch (Throwable suppressed) { // NOSONAR — error page must never throw
            // Last-ditch: best-effort write to System.err so a logging-config
            // failure doesn't leave operations entirely blind.
            try {
                String suppressedType = suppressed == null ? "unknown" : suppressed.getClass().getName();
                System.err.println("ErrorPageLogger: suppressed exception during error logging (" + suppressedType + ")");
            } catch (Throwable ignored) {
                // truly nothing more we can do
            }
        }
    }
}
