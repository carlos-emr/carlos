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
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.appointment.reminder.AppointmentReminderStatus;
import io.github.carlos_emr.carlos.appointment.reminder.ReminderCandidate;
import io.github.carlos_emr.carlos.appointment.reminder.ReminderChannel;
import io.github.carlos_emr.carlos.commn.model.AppointmentReminder;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Appointment reminders (#5887): the staff choice per appointment and channel, and the state of its send.
 *
 * @since 2026-11-12
 */
public interface AppointmentReminderDao extends AbstractDao<AppointmentReminder> {

    /** The reminder row for {@code appointmentNo} on {@code channel}, if one was ever requested. */
    Optional<AppointmentReminder> findByAppointment(int appointmentNo, ReminderChannel channel);

    /**
     * Requested reminders on {@code channel} that may be due now, with their appointments, earliest start first:
     * the appointment starts from {@code earliestStart} to {@code latestStart}, and the reminder was never
     * attempted for that start, or its attempt for it failed with fewer than {@code maxAttempts} attempts.
     * Rows already reminded for their current start are left out in the query, so they never crowd out the due
     * ones. Read without locks: the caller checks what is due exactly and claims it.
     */
    List<ReminderCandidate> findDue(ReminderChannel channel, LocalDateTime earliestStart, LocalDateTime latestStart,
                                    int maxAttempts, int limit);

    /**
     * The other requested reminders on {@code channel} for the same visit: the same patient's other bookings at
     * {@code start} (as Group Appt books one per provider), with their appointments, whatever their status. Read
     * without locks; empty without a patient or a start.
     *
     * @param excludeReminderId the reminder asking, left out
     */
    List<ReminderCandidate> findSameVisit(ReminderChannel channel, int demographicNo, LocalDateTime start,
                                          long excludeReminderId);

    /**
     * Rows on {@code channel} left {@link AppointmentReminderStatus#SENDING} by a run that stopped: their claim
     * is gone or older than {@code staleBefore}. Locked, skipping rows locked elsewhere; must run in the
     * caller's transaction.
     */
    List<AppointmentReminder> findAbandonedSending(ReminderChannel channel, Date staleBefore);

    /**
     * Claims the requested rows among {@code ids} that no other run holds, skipping rows locked elsewhere
     * ({@code FOR UPDATE SKIP LOCKED}). A claim older than {@code staleBefore} counts as abandoned. Must run in
     * the caller's transaction; the claimed rows are returned managed.
     */
    List<AppointmentReminder> claim(Collection<Long> ids, String claimToken, Date now, Date staleBefore);
}
