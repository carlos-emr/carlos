/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.form.pageUtil;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Shared request boundary for the two RH form mutation actions.
 *
 * <p>Security note: every rejection body this class can emit is one of the private literal
 * constants below, and {@link #reject} is the only writer sink. Request data (patient id,
 * workflow id, state) is never echoed back, so the plain-text bodies carry no reflected input.
 * Keep it that way — a caller-supplied message would turn this into a reflected-XSS sink.
 */
final class RhFormRequestGuard {
    private static final String POST_REQUIRED = "Use POST to save an RH form or workflow.";
    private static final String PATIENT_REQUIRED = "Select a valid patient and try again.";
    private static final String INVALID_WORKFLOW =
            "Invalid RH workflow selection. Reload the patient chart and try again.";
    private static final String SAVE_FAILED =
            "The RH form or workflow was not saved. Reload the patient chart before retrying.";

    private RhFormRequestGuard() { }

    static String authorize(HttpServletRequest request, HttpServletResponse response, SecurityInfoManager security) {
        if (!security.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_form", "w", null)) {
            throw new SecurityException("missing required sec object (_form)");
        }
        if (!"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST");
            reject(response, HttpServletResponse.SC_METHOD_NOT_ALLOWED, POST_REQUIRED);
            return null;
        }
        String patient = request.getParameter("demographic_no");
        if (patient == null) patient = (String) request.getAttribute("demographic_no");
        if (patient == null || !patient.matches("[1-9][0-9]*")) {
            reject(response, HttpServletResponse.SC_BAD_REQUEST, PATIENT_REQUIRED);
            return null;
        }
        return patient;
    }

    /** Rejects a save whose workflow id is not one of this patient's active RH workflows. */
    static void rejectInvalidWorkflow(HttpServletResponse response) {
        reject(response, HttpServletResponse.SC_BAD_REQUEST, INVALID_WORKFLOW);
    }

    /** Reports a rolled-back RH save so the failure is visible instead of silently swallowed. */
    static void rejectSaveFailure(HttpServletResponse response) {
        reject(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, SAVE_FAILED);
    }

    // XSS_SERVLET: the only values that reach this writer are the private literal constants
    // above; no request parameter or attribute is echoed, and the body is served as
    // text/plain rather than markup, so there is nothing for a browser to execute.
    @SuppressFBWarnings(value = "XSS_SERVLET",
            justification = "message is always a private literal constant of this class; no request data reaches the writer and the body is text/plain")
    private static void reject(HttpServletResponse response, int status, String message) {
        response.setStatus(status);
        response.setContentType("text/plain;charset=UTF-8");
        try {
            response.getWriter().write(message);
        } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        }
    }

}
