/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
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
        Throwable t = explicitException;
        if (t == null && request != null) {
            t = (Throwable) request.getAttribute("jakarta.servlet.error.exception");
        }
        if (t == null) {
            return;
        }
        Object uri = request != null
                ? request.getAttribute("jakarta.servlet.error.request_uri")
                : null;
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
    }
}
