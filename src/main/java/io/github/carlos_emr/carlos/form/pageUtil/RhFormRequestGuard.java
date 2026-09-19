/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.form.pageUtil;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Shared request boundary for the two RH form mutation actions. */
final class RhFormRequestGuard {
    private RhFormRequestGuard() { }

    static String authorize(HttpServletRequest request, HttpServletResponse response, SecurityInfoManager security) {
        if (!security.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_form", "w", null)) {
            throw new SecurityException("missing required sec object (_form)");
        }
        if (!"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST");
            reject(response, HttpServletResponse.SC_METHOD_NOT_ALLOWED, "Use POST to save an RH form or workflow.");
            return null;
        }
        String patient = request.getParameter("demographic_no");
        if (patient == null) patient = (String) request.getAttribute("demographic_no");
        if (patient == null || !patient.matches("[1-9][0-9]*")) {
            reject(response, HttpServletResponse.SC_BAD_REQUEST, "Select a valid patient and try again.");
            return null;
        }
        return patient;
    }
    static void reject(HttpServletResponse response, int status, String message) {
        response.setStatus(status);
        response.setContentType("text/plain;charset=UTF-8");
        try {
            response.getWriter().write(message);
        } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        }
    }

}
