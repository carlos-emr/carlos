/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.casemgmt.common;

import io.github.carlos_emr.carlos.encounter.pageUtil.EncounterPatientContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/** Keeps a chart's CAISI program independent of the program selected in another PMM window. */
public final class ChartProgramContext {
    private static final String PREFIX = ChartProgramContext.class.getName() + ".";
    private ChartProgramContext() { }

    /** Captures the program at chart entry. Subsequent chart requests never consult PMM's selection. */
    public static void capture(HttpServletRequest request) {
        remember(request, (String) request.getSession().getAttribute("case_program_id"));
    }

    /** Remembers a server-selected program for the explicitly named patient. */
    public static void remember(HttpServletRequest request, String program) {
        String patient = EncounterPatientContext.requireDemographicNo(request);
        HttpSession session = request.getSession();
        String key = PREFIX + patient;
        if (program != null && program.matches("0|[1-9][0-9]{0,8}")) {
            session.setAttribute(key, program);
        } else {
            session.removeAttribute(key);
        }
        request.setAttribute("case_program_id", resolve(request));
    }

    /** Returns this patient's captured program, or null when no program has been selected. */
    public static String resolve(HttpServletRequest request) {
        String patient;
        try {
            patient = EncounterPatientContext.requireDemographicNo(request);
        } catch (IllegalArgumentException missingPatient) {
            return null;
        }
        HttpSession session = request.getSession(false);
        Object value = session == null ? null : session.getAttribute(PREFIX + patient);
        return value instanceof String program ? program : null;
    }
}
