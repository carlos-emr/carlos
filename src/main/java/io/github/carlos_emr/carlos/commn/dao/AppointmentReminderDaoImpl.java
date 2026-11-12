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
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.AppointmentReminder;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;
import org.hibernate.Timeouts;
import org.hibernate.jpa.SpecHints;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * JPA access to {@code appointment_reminder} (#5887).
 *
 * @since 2026-11-12
 */
@Repository
public class AppointmentReminderDaoImpl extends AbstractDaoImpl<AppointmentReminder> implements AppointmentReminderDao {

    static final int MAX_LIMIT = 500;

    public AppointmentReminderDaoImpl() {
        super(AppointmentReminder.class);
    }

    @Override
    public Optional<AppointmentReminder> findByAppointment(int appointmentNo, ReminderChannel channel) {
        TypedQuery<AppointmentReminder> query = entityManager.createQuery(
                "SELECT r FROM AppointmentReminder r WHERE r.appointmentNo = :appointmentNo AND r.channel = :channel",
                AppointmentReminder.class);
        query.setParameter("appointmentNo", appointmentNo);
        query.setParameter("channel", channel);
        return query.getResultList().stream().findFirst();
    }

    @Override
    public List<ReminderCandidate> findDue(ReminderChannel channel, LocalDateTime earliestStart,
                                           LocalDateTime latestStart, int maxAttempts, int limit) {
        if (channel == null || earliestStart == null || latestStart == null || latestStart.isBefore(earliestStart)) {
            return List.of();
        }
        // A theta join: the reminder holds the appointment number, not a mapped association, so neither the
        // appointment mapping nor its archive copy changes. The appointment's start is its date and its time,
        // two columns, so the window and "already attempted for this start" compare each part; the caller
        // still checks the exact window, the retry delay and the appointment's status.
        TypedQuery<Object[]> query = entityManager.createQuery(
                "SELECT r, a FROM AppointmentReminder r, Appointment a "
                        + "WHERE a.id = r.appointmentNo AND r.channel = :channel AND r.requested = true "
                        + "AND a.appointmentDate BETWEEN :firstDate AND :lastDate "
                        + "AND (a.appointmentDate > :firstDate OR a.startTime >= :firstTime) "
                        + "AND (a.appointmentDate < :lastDate OR a.startTime <= :lastTime) "
                        + "AND (r.sentFor IS NULL "
                        + "OR extract(date from r.sentFor) <> a.appointmentDate "
                        + "OR extract(time from r.sentFor) <> a.startTime "
                        + "OR (r.lastStatus = :failed AND r.attempts < :maxAttempts)) "
                        + "ORDER BY a.appointmentDate, a.startTime, r.id",
                Object[].class);
        query.setParameter("channel", channel);
        query.setParameter("firstDate", java.sql.Date.valueOf(earliestStart.toLocalDate()));
        query.setParameter("lastDate", java.sql.Date.valueOf(latestStart.toLocalDate()));
        query.setParameter("firstTime", java.sql.Time.valueOf(earliestStart.toLocalTime().withNano(0)));
        query.setParameter("lastTime", java.sql.Time.valueOf(latestStart.toLocalTime().withNano(0)));
        query.setParameter("failed", AppointmentReminderStatus.FAILED);
        query.setParameter("maxAttempts", maxAttempts);
        query.setMaxResults(safeLimit(limit));
        List<ReminderCandidate> candidates = new ArrayList<>();
        for (Object[] row : query.getResultList()) {
            candidates.add(new ReminderCandidate((AppointmentReminder) row[0], (Appointment) row[1]));
        }
        return candidates;
    }

    @Override
    public List<ReminderCandidate> findSameVisit(ReminderChannel channel, int demographicNo, LocalDateTime start,
                                                 long excludeReminderId, Collection<AppointmentReminderStatus> handedOver) {
        if (channel == null || demographicNo <= 0 || start == null) {
            return List.of();
        }
        java.util.Map<Long, ReminderCandidate> found = new java.util.LinkedHashMap<>();
        TypedQuery<Object[]> booked = entityManager.createQuery(
                "SELECT r, a FROM AppointmentReminder r, Appointment a "
                        + "WHERE a.id = r.appointmentNo AND r.channel = :channel AND r.requested = true "
                        + "AND r.id <> :excluded AND a.demographicNo = :demographicNo "
                        + "AND a.appointmentDate = :date AND a.startTime = :time ORDER BY r.id",
                Object[].class);
        booked.setParameter("channel", channel);
        booked.setParameter("excluded", excludeReminderId);
        booked.setParameter("demographicNo", demographicNo);
        booked.setParameter("date", java.sql.Date.valueOf(start.toLocalDate()));
        booked.setParameter("time", java.sql.Time.valueOf(start.toLocalTime().withNano(0)));
        addCandidates(found, booked.getResultList());
        if (handedOver != null && !handedOver.isEmpty()) {
            // Handed over for this start, matched on the start it went for: the booking may be unticked,
            // cancelled or moved since, and still covers the visit.
            TypedQuery<Object[]> reminded = entityManager.createQuery(
                    "SELECT r, a FROM AppointmentReminder r, Appointment a "
                            + "WHERE a.id = r.appointmentNo AND r.channel = :channel AND r.id <> :excluded "
                            + "AND a.demographicNo = :demographicNo AND r.sentFor = :start "
                            + "AND r.lastStatus IN (:handedOver) ORDER BY r.id",
                    Object[].class);
            reminded.setParameter("channel", channel);
            reminded.setParameter("excluded", excludeReminderId);
            reminded.setParameter("demographicNo", demographicNo);
            reminded.setParameter("start", java.sql.Timestamp.valueOf(start.withNano(0)));
            reminded.setParameter("handedOver", handedOver);
            addCandidates(found, reminded.getResultList());
        }
        return new ArrayList<>(found.values());
    }

    private static void addCandidates(java.util.Map<Long, ReminderCandidate> found, List<Object[]> rows) {
        for (Object[] row : rows) {
            AppointmentReminder reminder = (AppointmentReminder) row[0];
            found.putIfAbsent(reminder.getId(), new ReminderCandidate(reminder, (Appointment) row[1]));
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<AppointmentReminder> claim(Collection<Long> ids, String claimToken, Date now, Date staleBefore) {
        if (ids == null || ids.isEmpty() || claimToken == null || now == null || staleBefore == null) {
            return List.of();
        }
        // Lock only the reminder rows, never the appointment, so a claim cannot hold up staff editing the
        // schedule. SKIP LOCKED, as in the SMS queue (SmsTransactionDaoImpl): a row another run holds is left
        // to it, and two runs or two servers never claim the same reminder.
        TypedQuery<AppointmentReminder> query = entityManager.createQuery(
                "SELECT r FROM AppointmentReminder r WHERE r.id IN (:ids) AND r.requested = true "
                        + "AND (r.claimToken IS NULL OR r.claimedAt < :staleBefore) ORDER BY r.id",
                AppointmentReminder.class);
        query.setParameter("ids", ids);
        query.setParameter("staleBefore", staleBefore);
        query.setLockMode(LockModeType.PESSIMISTIC_WRITE);
        query.setHint(SpecHints.HINT_SPEC_LOCK_TIMEOUT, Timeouts.SKIP_LOCKED_MILLI);
        List<AppointmentReminder> claimed = query.getResultList();
        for (AppointmentReminder reminder : claimed) {
            reminder.claim(claimToken, now);
        }
        return claimed;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<AppointmentReminder> findAbandonedSending(ReminderChannel channel, Date staleBefore) {
        if (channel == null || staleBefore == null) {
            return List.of();
        }
        // SENDING is set only under a claim, and ending the attempt clears both; a SENDING row whose claim is
        // gone or old belongs to a run that stopped between marking it and recording what happened.
        TypedQuery<AppointmentReminder> query = entityManager.createQuery(
                "SELECT r FROM AppointmentReminder r WHERE r.channel = :channel AND r.lastStatus = :sending "
                        + "AND (r.claimToken IS NULL OR r.claimedAt < :staleBefore) ORDER BY r.id",
                AppointmentReminder.class);
        query.setParameter("channel", channel);
        query.setParameter("sending", AppointmentReminderStatus.SENDING);
        query.setParameter("staleBefore", staleBefore);
        query.setLockMode(LockModeType.PESSIMISTIC_WRITE);
        query.setHint(SpecHints.HINT_SPEC_LOCK_TIMEOUT, Timeouts.SKIP_LOCKED_MILLI);
        query.setMaxResults(MAX_LIMIT);
        return query.getResultList();
    }

    private static int safeLimit(int limit) {
        return limit <= 0 ? MAX_LIMIT : Math.min(limit, MAX_LIMIT);
    }
}
