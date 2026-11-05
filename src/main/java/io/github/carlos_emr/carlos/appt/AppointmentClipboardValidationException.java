/*
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.appt;

/** Invalid clipboard fields must not replace an earlier copy or permit Cut to remove its source. */
public final class AppointmentClipboardValidationException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    public static final String MESSAGE_KEY = "appointment.clipboard.invalidClipboard";

    public AppointmentClipboardValidationException() {
        super("The appointment details could not be copied. Reopen the source appointment and check its fields. Nothing was copied or cut.");
    }
}
