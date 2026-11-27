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

import io.github.carlos_emr.carlos.commn.model.AppointmentReminder;
import java.util.Optional;

/**
 * One way of reminding a patient of an appointment (#5887): email now, SMS (#3843) later. A channel checks
 * its own consent, reads the patient's address at send time, and puts no clinical detail in the message.
 *
 * <p>{@link #send} must not throw for an ordinary refusal; it returns the status instead. An exception is
 * recorded as {@link AppointmentReminderStatus#UNCONFIRMED}, because the channel may have sent it.
 *
 * @since 2026-11-12
 */
public interface AppointmentReminderChannel {

    ReminderChannel channel();

    ReminderOutcome send(ClaimedReminder reminder);

    /** Whether the clinic has this channel on (#884). A channel that is off sends nothing, not even old rows. */
    default boolean enabled() {
        return true;
    }

    /** This channel's window, when it has its own lead time or quiet hours (#884); empty uses the service's. */
    default Optional<ReminderWindow> window() {
        return Optional.empty();
    }

    /**
     * Attempts per appointment start: {@link AppointmentReminderService#MAX_ATTEMPTS} allows one retry after a
     * definite failure. A channel that hands its message to a queue with retries of its own (SMS, #884) never hands
     * a message over twice once the queue took it (its status is then not a failure); only a message that never
     * reached the queue is tried again.
     */
    default int maxAttempts() {
        return AppointmentReminderService.MAX_ATTEMPTS;
    }

    /**
     * The reminder's outcome as it stands now, for the status line. A channel that hands its message on (SMS: the
     * queue sends it later) reads where the message got to; the default is the recorded outcome.
     */
    default AppointmentReminderStatus currentStatus(AppointmentReminder reminder) {
        return reminder.getLastStatus();
    }
}
