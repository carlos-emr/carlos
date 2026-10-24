/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.casemgmt.common;

import io.github.carlos_emr.carlos.encounter.pageUtil.EncounterPatientContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * Keeps a chart's CAISI program independent of the program selected in another PMM window.
 *
 * @since 2026-10-24
 */
public final class ChartProgramContext {
    private static final String PREFIX = ChartProgramContext.class.getName() + ".";
    private ChartProgramContext() { }

    /**
     * Captures the program at chart entry. Later chart requests use this patient's captured value.
     *
     * @param request HttpServletRequest naming the chart patient
     * @throws IllegalArgumentException if the request does not identify a valid patient
     */
    public static void capture(HttpServletRequest request) {
        remember(request, (String) request.getSession().getAttribute("case_program_id"));
    }

    /**
     * Remembers a server-selected program for the explicitly named patient.
     * Invalid selections clear the captured context. Zero retains the existing no-program sentinel.
     *
     * @param request HttpServletRequest naming the chart patient
     * @param program String containing a non-negative Integer program identifier, or null
     * @throws IllegalArgumentException if the request does not identify a valid patient
     */
    public static void remember(HttpServletRequest request, String program) {
        String patient = EncounterPatientContext.requireDemographicNo(request);
        HttpSession session = request.getSession();
        String key = PREFIX + patient;
        if (validProgram(program)) {
            session.setAttribute(key, program);
        } else {
            session.removeAttribute(key);
        }
        request.setAttribute("case_program_id", resolve(request));
    }

    /**
     * Returns this patient's captured program without consulting another window's selection.
     *
     * @param request HttpServletRequest naming the chart patient
     * @return String captured program identifier, or null for absent patient/program context
     */
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

    /**
     * Validates canonical program identifiers against the Integer range used by chart DAOs.
     *
     * @param program String identifier to validate
     * @return boolean true for zero or a positive identifier representable as an Integer
     */
    private static boolean validProgram(String program) {
        if (program == null || !program.matches("0|[1-9][0-9]*")) return false;
        try {
            Integer.parseInt(program);
            return true;
        } catch (NumberFormatException invalidProgram) {
            return false;
        }
    }
}
