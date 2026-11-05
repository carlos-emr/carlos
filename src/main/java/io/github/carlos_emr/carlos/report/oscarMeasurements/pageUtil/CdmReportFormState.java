/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.report.oscarMeasurements.pageUtil;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

/** Raw submitted form values for an INPUT response; JSP callers must apply output encoding. */
public final class CdmReportFormState {
    private final HttpServletRequest request;
    private final boolean submitted;

    public CdmReportFormState(HttpServletRequest request) {
        this.request = request;
        // Selecting a report also POSTs, but has no submitted report fields.
        // Restore raw values only on a validation INPUT response, including conversion errors.
        Object actionErrors = request.getAttribute("actionErrors");
        Object fieldErrors = request.getAttribute("fieldErrors");
        this.submitted = "POST".equals(request.getMethod())
                && (actionErrors instanceof Collection<?> actionMessages && !actionMessages.isEmpty()
                    || fieldErrors instanceof Map<?, ?> fieldMessages && !fieldMessages.isEmpty());
    }

    public String value(String name, int index, String initial) {
        if (!submitted) return initial;
        String[] values = request.getParameterValues(name);
        return values != null && index >= 0 && index < values.length ? values[index] : "";
    }

    public boolean selected(String name, String value, boolean initial) {
        if (!submitted) return initial;
        String[] values = request.getParameterValues(name);
        return values != null && Arrays.asList(values).contains(value);
    }
}
