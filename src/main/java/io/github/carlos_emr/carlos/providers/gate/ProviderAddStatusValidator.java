/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.providers.gate;

import java.util.regex.Pattern;

/**
 * Validation helpers for provider appointment status updates.
 *
 * @since 2026-06-15
 */
public final class ProviderAddStatusValidator {

    private static final Pattern APPOINTMENT_STATUS = Pattern.compile("[a-zA-Z]{1,2}");

    private ProviderAddStatusValidator() {
    }

    /**
     * Builds a validated appointment status value from the submitted status fragments.
     *
     * @param status existing status prefix, which may be empty but must combine with statusch into one or two letters
     * @param statusch submitted status change value, which must be one or two letters
     * @return combined appointment status, or {@code null} when either fragment is null or invalid
     */
    public static String buildValidatedAppointmentStatus(String status, String statusch) {
        if (status == null || statusch == null || !APPOINTMENT_STATUS.matcher(statusch).matches()) {
            return null;
        }

        String appointmentStatus = status + statusch;
        return APPOINTMENT_STATUS.matcher(appointmentStatus).matches() ? appointmentStatus : null;
    }

    /**
     * Checks that the status submitted by the schedule was the status persisted when the
     * transition began.
     *
     * @param persistedStatus current appointment status loaded from the database
     * @param submittedStatus current status rendered into the schedule link
     * @return {@code true} when both statuses are valid and identical
     */
    public static boolean matchesCurrentStatus(String persistedStatus, String submittedStatus) {
        return isValidStatus(persistedStatus)
                && isValidStatus(submittedStatus)
                && persistedStatus.equals(submittedStatus);
    }

    /**
     * Checks that the requested status is the next transition calculated from server-side
     * appointment-status configuration.
     *
     * @param calculatedNextStatus next status calculated by {@code ApptStatusData}
     * @param requestedStatus status requested by the client
     * @return {@code true} when both statuses are valid and identical
     */
    public static boolean matchesCalculatedNextStatus(
            String calculatedNextStatus,
            String requestedStatus) {
        return isValidStatus(calculatedNextStatus)
                && isValidStatus(requestedStatus)
                && calculatedNextStatus.equals(requestedStatus);
    }

    private static boolean isValidStatus(String status) {
        return status != null && APPOINTMENT_STATUS.matcher(status).matches();
    }
}
