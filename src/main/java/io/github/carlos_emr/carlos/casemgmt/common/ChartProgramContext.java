/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.casemgmt.common;

import io.github.carlos_emr.carlos.encounter.data.EctProgram;
import io.github.carlos_emr.carlos.encounter.pageUtil.EncounterPatientContext;
import io.github.carlos_emr.carlos.utility.BoundedSessionAttributes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.web.util.WebUtils;

/**
 * Keeps each chart window's CAISI program bound to its explicitly named patient.
 * Server-issued contexts survive other charts and PMM program selections. Existing callers
 * without a context retain their per-patient program, never another patient's selection.
 *
 * @since 2026-10-24
 */
public final class ChartProgramContext {
    public static final String PARAMETER = "chartContext";
    private static final String PREFIX = ChartProgramContext.class.getName() + ".";
    private static final String SIGNING_KEY = PREFIX + "signingKey";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final String REQUEST_CONTEXT = PREFIX + "requestContext";
    private ChartProgramContext() { }

    /**
     * Selects a new chart's program before either the CAISI or ordinary chart redirect.
     * An explicit PMM program must be among the authenticated provider's available programs;
     * an ordinary entry uses the provider's default instead of the last PMM window's selection.
     * A reload carrying an existing window context retains that context's program.
     *
     * @param request HttpServletRequest naming the chart patient and optional PMM program
     * @param programs EctProgram supplying the provider's available and default programs
     * @param providerNo String authenticated provider identifier
     * @throws SecurityException if the selected program is invalid or unavailable to the provider
     */
    public static void initialize(HttpServletRequest request, EctProgram programs, String providerNo) {
        initialize(request, programs, providerNo, providerNo);
    }

    /**
     * Initializes a chart opened on another provider's schedule, preserving that provider's
     * default while checking membership against the authenticated caller.
     *
     * @param request HttpServletRequest naming the chart patient and optional PMM program
     * @param programs EctProgram supplying available and default programs
     * @param providerNo String authenticated provider identifier for membership checks
     * @param defaultProviderNo String chart entry provider whose program default is preserved
     * @throws SecurityException if the selected/default program is unavailable to the caller
     */
    public static void initialize(HttpServletRequest request, EctProgram programs, String providerNo,
                                  String defaultProviderNo) {
        if (request.getParameterValues(PARAMETER) != null) {
            token(request);
            return;
        }
        String selected = null;
        for (String alias : new String[]{"programId", "case_program_id"}) {
            String[] values = request.getParameterValues(alias);
            if (values == null) continue;
            for (String value : values) {
                if (!validProgram(value) || (selected != null && !selected.equals(value))) {
                    throw new SecurityException("Invalid chart program selection");
                }
                selected = value;
            }
        }
        if (selected == null) {
            selected = programs.getProgram(defaultProviderNo);
        }
        if (!validProgram(selected)) throw new SecurityException("Invalid chart program selection");
        if (!"0".equals(selected)) {
            String chosen = selected;
            boolean available = programs.getEctProgramManager().getProgramBeans(providerNo, null).stream()
                    .anyMatch(program -> chosen.equals(program.getValue()));
            if (!available) throw new SecurityException("Unavailable chart program selection");
        }
        remember(request, selected);
    }

    /**
     * Remembers a server-selected program for the named patient and the current chart context.
     * Invalid selections clear the program. Zero retains the existing no-program sentinel.
     * Tokens are authenticated with one session key, without retaining per-window session entries.
     * They stay valid for the login because another window may still contain an unsaved note.
     *
     * @param request HttpServletRequest naming the chart patient
     * @param program String containing a non-negative Integer program identifier, or null
     * @throws IllegalArgumentException if the request does not identify a valid patient
     * @throws SecurityException if a supplied context is invalid or belongs to another patient
     */
    public static void remember(HttpServletRequest request, String program) {
        String patient = EncounterPatientContext.requireDemographicNo(request);
        HttpSession session = request.getSession();
        token(request); // Never replace a supplied forged or patient-mismatched context.
        String chosen = validProgram(program) ? program : null;
        byte[] payload = (patient + ":" + (chosen == null ? "" : chosen)).getBytes(StandardCharsets.US_ASCII);
        String current;
        synchronized (WebUtils.getSessionMutex(session)) {
            Object existing = session.getAttribute(SIGNING_KEY);
            byte[] key;
            if (existing instanceof byte[] bytes) key = bytes;
            else {
                key = new byte[32];
                RANDOM.nextBytes(key);
                session.setAttribute(SIGNING_KEY, key);
            }
            current = ENCODER.encodeToString(payload) + "." + ENCODER.encodeToString(sign(payload, key));
            if (chosen == null) session.removeAttribute(PREFIX + patient);
            else BoundedSessionAttributes.put(session, "chartPrograms", PREFIX + patient, chosen);
        }
        request.setAttribute(REQUEST_CONTEXT, current);
        request.setAttribute("case_program_id", chosen);
    }

    /**
     * Returns this chart window's program, or the per-patient legacy capture without a token.
     *
     * @param request HttpServletRequest naming the chart patient
     * @return String captured program identifier, or null for absent patient/program context
     * @throws SecurityException if a supplied context is invalid or belongs to another patient
     */
    public static String resolve(HttpServletRequest request) {
        String current = token(request);
        String patient;
        try {
            patient = EncounterPatientContext.requireDemographicNo(request);
        } catch (IllegalArgumentException missingPatient) {
            if (current != null) throw new SecurityException("Invalid chart patient context");
            return null;
        }
        HttpSession session = request.getSession(false);
        if (current != null) return captured(session, current).program();
        Object value = session == null ? null : session.getAttribute(PREFIX + patient);
        return value instanceof String program ? program : null;
    }

    /**
     * Names the unsaved editor form for this patient's authenticated program context.
     * The legacy patient-only key remains available to callers without a window token.
     *
     * @param request HttpServletRequest carrying the chart's optional authenticated context
     * @param patient String containing the explicitly authorized patient identifier
     * @return String session key independent of another program window for the same patient
     * @throws SecurityException if the context or supplied patient is invalid or mismatched
     */
    public static String formKey(HttpServletRequest request, String patient) {
        String canonical;
        try {
            if (patient == null || !patient.matches("[0-9]+") || Integer.parseInt(patient) <= 0) {
                throw new IllegalArgumentException("Invalid patient");
            }
            canonical = Integer.toString(Integer.parseInt(patient));
        } catch (IllegalArgumentException invalidPatient) {
            throw new SecurityException("Invalid chart patient context");
        }
        String current = token(request);
        if (current != null && !captured(request.getSession(false), current).patient().equals(canonical)) {
            throw new SecurityException("Chart patient context mismatch");
        }
        return "caseManagementEntryForm" + canonical + (current == null ? "" : "." + current);
    }

    /**
     * Returns the current window's authenticated context for forms, AJAX, redirects and Rx handoffs.
     * Supplied tokens are validated against the login and any explicitly named patient.
     *
     * @param request HttpServletRequest carrying an optional context
     * @return String session-authenticated context token, or null for a legacy request
     * @throws SecurityException if a supplied context is unknown, ambiguous or patient-mismatched
     */
    public static String token(HttpServletRequest request) {
        String[] values = request.getParameterValues(PARAMETER);
        String current = null;
        if (values != null) {
            if (values.length == 0) throw new SecurityException("Missing chart context");
            for (String value : values) {
                if (value == null || value.length() > 128 || !value.matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]{43}")
                        || (current != null && !current.equals(value))) {
                    throw new SecurityException("Invalid chart context");
                }
                current = value;
            }
        } else {
            Object saved = request.getAttribute(REQUEST_CONTEXT);
            if (saved instanceof String context) current = context;
        }
        if (current == null) return null;
        CapturedProgram captured = captured(request.getSession(false), current);
        if (request.getParameterValues("demographicNo") != null
                || request.getParameterValues("demographic_no") != null) {
            String patient;
            try {
                patient = EncounterPatientContext.requireDemographicNo(request);
            } catch (IllegalArgumentException invalidPatient) {
                throw new SecurityException("Invalid chart patient context");
            }
            if (!captured.patient().equals(patient)) throw new SecurityException("Chart patient context mismatch");
        }
        return current;
    }

    private static CapturedProgram captured(HttpSession session, String token) {
        if (session == null) throw new SecurityException("Unknown chart context");
        Object existing = session.getAttribute(SIGNING_KEY);
        if (!(existing instanceof byte[] key)) throw new SecurityException("Unknown chart context");
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 2) throw new SecurityException("Invalid chart context");
            byte[] payload = DECODER.decode(parts[0]);
            byte[] supplied = DECODER.decode(parts[1]);
            if (!MessageDigest.isEqual(sign(payload, key), supplied)) {
                throw new SecurityException("Invalid chart context");
            }
            String[] fields = new String(payload, StandardCharsets.US_ASCII).split(":", -1);
            if (fields.length != 2 || !fields[0].matches("[1-9][0-9]{0,9}")
                    || Integer.parseInt(fields[0]) <= 0
                    || (!fields[1].isEmpty() && !validProgram(fields[1]))) {
                throw new SecurityException("Invalid chart context");
            }
            return new CapturedProgram(fields[0], fields[1].isEmpty() ? null : fields[1]);
        } catch (IllegalArgumentException invalidEncoding) {
            throw new SecurityException("Invalid chart context");
        }
    }

    private static byte[] sign(byte[] payload, byte[] key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(payload);
        } catch (GeneralSecurityException unavailableAlgorithm) {
            throw new IllegalStateException("Chart context authentication unavailable", unavailableAlgorithm);
        }
    }

    private record CapturedProgram(String patient, String program) { }

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
