/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.appointment.service;

/**
 * Signals that an appointment status transition could not be applied because
 * one of its server-side preconditions failed.
 *
 * @since 2026-07-29
 */
public final class AppointmentStatusTransitionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public enum Reason {
        APPOINTMENT_NOT_FOUND,
        STALE_STATUS,
        INVALID_TRANSITION,
        PROVIDER_MISMATCH
    }

    private final Reason reason;

    public AppointmentStatusTransitionException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
