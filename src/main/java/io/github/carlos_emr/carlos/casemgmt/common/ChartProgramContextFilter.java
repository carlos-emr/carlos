/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.casemgmt.common;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Refuses forged or patient-mismatched chart contexts before a chart or prescribing action runs.
 * Requests without a window context preserve the legacy standalone entry points.
 *
 * @since 2026-10-24
 */
public final class ChartProgramContextFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest http = (HttpServletRequest) request;
        if (http.getParameterValues(ChartProgramContext.PARAMETER) != null) {
            try {
                ChartProgramContext.token(http);
            } catch (SecurityException invalidContext) {
                ((HttpServletResponse) response).sendError(HttpServletResponse.SC_FORBIDDEN,
                        "Invalid chart context");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
