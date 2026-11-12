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
 * made one, so staff can follow it to Manage Emails.
 *
 * @since 2026-11-12
 */
public record ReminderOutcome(AppointmentReminderStatus status, Integer emailLogId) {

    public ReminderOutcome {
        Objects.requireNonNull(status, "status");
        if (status == AppointmentReminderStatus.SENDING) {
            throw new IllegalArgumentException("SENDING is not an outcome");
        }
    }

    public static ReminderOutcome of(AppointmentReminderStatus status) {
        return new ReminderOutcome(status, null);
    }
}
