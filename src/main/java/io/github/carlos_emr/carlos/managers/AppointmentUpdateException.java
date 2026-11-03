/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.managers;

/** A refused appointment edit; messages contain no patient or appointment data. */
public class AppointmentUpdateException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public enum Reason { MISSING_VERSION, NOT_FOUND, STALE_VERSION }

    private final Reason reason;

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
