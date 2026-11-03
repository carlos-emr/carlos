/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.managers;

/**
 * A refused appointment edit; messages contain no patient or appointment data.
 *
 * @since 2026-11-03
 */
public class AppointmentUpdateException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /** The reason the appointment edit was refused. */
    public enum Reason { MISSING_VERSION, NOT_FOUND, STALE_VERSION }

    private final Reason reason;

    /**
     * Creates an exception with a fixed message for the given reason.
     *
     * @param reason Reason the reason the edit was refused
     */
    public AppointmentUpdateException(Reason reason) {
        super(messageFor(reason));
        this.reason = reason;
    }

    private static String messageFor(Reason reason) {
        return switch (reason) {
            case MISSING_VERSION -> "Appointment editVersion is required";
            case NOT_FOUND -> "Appointment not found";
            case STALE_VERSION -> "Appointment has changed; reload before saving";
        };
    }

    public Reason getReason() {
        return reason;
    }
}
