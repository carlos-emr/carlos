/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.appointment.reminder;

import java.util.Objects;

/**
 * How one reminder attempt ended (#5887). {@code emailLogId} names the email log row when the email channel
 * made one, so staff can follow it to Manage Emails; {@code smsTransactionId} names the SMS row the SMS channel
 * made (#884), so its text can be followed. {@code reason}, when given, says why for the audit row: a
 * fixed word or an exception's class name, never patient data.
 *
 * @since 2026-11-12
 */
public record ReminderOutcome(AppointmentReminderStatus status, Integer emailLogId, Long smsTransactionId,
                              String reason) {

    public ReminderOutcome {
        Objects.requireNonNull(status, "status");
        if (status == AppointmentReminderStatus.SENDING) {
            throw new IllegalArgumentException("SENDING is not an outcome");
        }
        // It goes into the audit row's key=value data: letters, digits, '.', '_' and '-' only.
        reason = reason == null || reason.isBlank() ? null : reason.replaceAll("[^A-Za-z0-9._-]", "");
    }

    public ReminderOutcome(AppointmentReminderStatus status, Integer emailLogId, String reason) {
        this(status, emailLogId, null, reason);
    }

    public ReminderOutcome(AppointmentReminderStatus status, Integer emailLogId) {
        this(status, emailLogId, null, null);
    }

    /** An SMS outcome (#884), naming the text's row when the SMS queue recorded one. */
    public static ReminderOutcome sms(AppointmentReminderStatus status, Long smsTransactionId, String reason) {
        return new ReminderOutcome(status, null, smsTransactionId, reason);
    }

    public static ReminderOutcome of(AppointmentReminderStatus status) {
        return new ReminderOutcome(status, null, null);
    }

    /** {@code status}, because of {@code reason} (a fixed word). */
    public static ReminderOutcome because(AppointmentReminderStatus status, String reason) {
        return new ReminderOutcome(status, null, reason);
    }

    /** {@code status}, because {@code cause} was thrown: its class name only, never its message. */
    public static ReminderOutcome because(AppointmentReminderStatus status, Throwable cause) {
        return because(status, cause == null ? null : cause.getClass().getSimpleName());
    }
}
