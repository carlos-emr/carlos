/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.billings.ca.bc.service;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Fixed operator messages and PHI-free diagnostics shared by both export endpoints.
 * JDBC and formatter exception messages can contain patient values, so only exact,
 * audited messages cross the response/log boundary. Stack locations and cause types remain.
 * @since 2026-11-09
 */
public final class TeleplanExportErrors {
    public record Failure(int status, String message) { }
    private static final String GENERIC = "Teleplan export failed. Review the activity list before retrying.";
    private static final Map<String, Failure> MESSAGES = Map.ofEntries(
            entry("A valid billing month is required", 400),
            entry("Select a provider", 400),
            entry("Select a billing provider", 400),
            entry("No billing providers matched the selection", 400),
            entry("A provider has an invalid practitioner number", 400),
            entry("Providers share a practitioner number; correct the billing setup", 400),
            entry("A provider has an invalid billing group", 400),
            entry("A valid Teleplan claim amount is required", 400),
            entry("A valid Teleplan billing unit is required", 400),
            entry("Teleplan claim amount exceeds the wire capacity", 400),
            entry("Teleplan claim amount must use whole cents", 400),
            entry("Teleplan claim record length is invalid", 400),
            entry("A WCB invoice must have one open form and one eligible claim", 400),
            entry("WCB form and claim fee codes do not match", 400),
            entry("WCB form and billing amounts differ", 400),
            entry("Another Teleplan export is running; retry when it finishes", 409),
            entry("Monthly Teleplan batch capacity is exhausted", 409),
            entry("Teleplan output filename is already in use", 409),
            entry("Teleplan output directory is unavailable", 503),
            entry("Invalid Teleplan staging directory", 503),
            entry("Unexpected Teleplan staging entry; operator review is required", 503),
            entry("Inconsistent Teleplan batch; operator review is required", 503),
            entry("Teleplan artifact changed; operator review is required", 503),
            entry("Invalid Teleplan journal", 503),
            entry("Unsupported Teleplan journal", 503),
            entry("Invalid Teleplan journal witness", 503),
            entry("Invalid Teleplan journal activity identity", 503),
            entry("Empty Teleplan journal", 503),
            entry("Unexpected Teleplan journal content", 503));

    private TeleplanExportErrors() { }

    private static Map.Entry<String, Failure> entry(String message, int status) {
        return Map.entry(message, new Failure(status, message));
    }

    public static Failure describe(Throwable failure) {
        var seen = new IdentityHashMap<Throwable, Boolean>();
        for (Throwable cause = failure; cause != null && seen.put(cause, true) == null; cause = cause.getCause()) {
            Failure known = cause.getMessage() == null ? null : MESSAGES.get(cause.getMessage());
            if (known != null) return known;
        }
        return new Failure(500, GENERIC);
    }

    /** Keep cause/suppression topology and stack locations without copying untrusted exception text. */
    public static Throwable diagnostic(Throwable failure) {
        return diagnostic(failure, new IdentityHashMap<>());
    }

    private static Throwable diagnostic(Throwable failure, IdentityHashMap<Throwable, Throwable> seen) {
        if (seen.containsKey(failure)) return seen.get(failure);
        String message = failure.getMessage();
        var safe = new IllegalStateException(failure.getClass().getName() + ": "
                + (message != null && MESSAGES.containsKey(message) ? MESSAGES.get(message).message() : GENERIC));
        seen.put(failure, safe);
        safe.setStackTrace(failure.getStackTrace());
        if (failure.getCause() != null && !seen.containsKey(failure.getCause())) {
            safe.initCause(diagnostic(failure.getCause(), seen));
        }
        for (Throwable suppressed : failure.getSuppressed()) {
            if (!seen.containsKey(suppressed)) safe.addSuppressed(diagnostic(suppressed, seen));
        }
        return safe;
    }
}
