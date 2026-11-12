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

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.AppointmentReminderDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.AppointmentReminder;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Appointment reminders (#5887): records the staff choice on Add/Edit Appointment, and sends what is due.
 *
 * <p>A run reads requested reminders with their appointments, keeps those due now ({@link ReminderWindow}),
 * claims them with SKIP LOCKED, marks each as being sent for its current start, commits, and only then asks
 * the channel to send. The outcome is recorded in its own transaction. So a crash after the send leaves the
 * attempt {@link AppointmentReminderStatus#SENDING} but never sends one start twice, and a cancelled,
 * no-show, deleted or patient-less appointment is never reminded. The appointment is read as it is at claim
 * time, so a reschedule by any path (Edit, REST, HL7, recurring bookings) needs no hook.
 *
 * @since 2026-11-12
 */
@Service
public class AppointmentReminderService {

    /** A claim older than this was abandoned by a run that stopped; another run may take the row. */
    static final Duration STALE_CLAIM = Duration.ofMinutes(15);
    /** Candidates read per channel and run, before the due check. */
    static final int MAX_CANDIDATES = 500;
    /** Attempts per appointment start: the first, and one retry after a definite transport failure. */
    static final int MAX_ATTEMPTS = 2;
    /** How long a failed send waits before its one retry. */
    static final Duration RETRY_AFTER = Duration.ofMinutes(30);

    private static final Logger logger = MiscUtils.getLogger();

    private final AppointmentReminderDao reminders;
    private final OscarAppointmentDao appointments;
    private final Supplier<List<AppointmentReminderChannel>> channels;
    private final TransactionOperations transactions;
    private final ReminderWindow window;
    private final Clock clock;

    @Autowired
    public AppointmentReminderService(AppointmentReminderDao reminders, OscarAppointmentDao appointments,
                                      ObjectProvider<AppointmentReminderChannel> channels,
                                      PlatformTransactionManager transactionManager) {
        this(reminders, appointments, () -> channels.orderedStream().toList(), new TransactionTemplate(transactionManager),
                ReminderWindow.forHoursBefore(CarlosProperties.getInstance().getProperty(ReminderWindow.HOURS_BEFORE_PROPERTY, null)),
                Clock.systemDefaultZone());
    }

    AppointmentReminderService(AppointmentReminderDao reminders, OscarAppointmentDao appointments,
                               Supplier<List<AppointmentReminderChannel>> channels, TransactionOperations transactions,
                               ReminderWindow window, Clock clock) {
        this.reminders = Objects.requireNonNull(reminders, "reminders");
        this.appointments = Objects.requireNonNull(appointments, "appointments");
        this.channels = Objects.requireNonNull(channels, "channels");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.window = Objects.requireNonNull(window, "window");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Records the staff choice for one appointment. Unticking keeps the row and its history; a box never
     * ticked leaves no row. Joins the caller's transaction, so the choice commits with the appointment.
     *
     * @return {@code true} when the stored choice changed
     */
    @Transactional
    public boolean recordRequest(LoggedInInfo user, int appointmentNo, int demographicNo, ReminderChannel channel,
                                 boolean requested) {
        Objects.requireNonNull(channel, "channel");
        String providerNo = user == null ? null : user.getLoggedInProviderNo();
        Date now = Date.from(clock.instant());
        Optional<AppointmentReminder> existing = reminders.findByAppointment(appointmentNo, channel);
        if (existing.isEmpty()) {
            if (!requested) {
                return false;
            }
            reminders.persist(AppointmentReminder.requested(appointmentNo, channel, providerNo, now));
        } else {
            AppointmentReminder reminder = existing.get();
            if (reminder.isRequested() == requested) {
                return false;
            }
            if (requested) {
                reminder.request(providerNo, now);
            } else {
                reminder.withdraw(providerNo, now);
            }
            reminders.merge(reminder);
        }
        if (user != null) {
            LogAction.addLog(user, requested ? "AppointmentReminder.requested" : "AppointmentReminder.withdrawn",
                    LogConst.CON_APPT, String.valueOf(appointmentNo),
                    demographicNo > 0 ? String.valueOf(demographicNo) : null, "channel=" + channel);
        }
        return true;
    }

    /** Whether staff asked for a reminder of this appointment on this channel (Edit shows the box ticked). */
    public boolean isRequested(int appointmentNo, ReminderChannel channel) {
        return reminders.findByAppointment(appointmentNo, channel).map(AppointmentReminder::isRequested).orElse(false);
    }

    /**
     * Sends the reminders due now, at most {@code batchSize} per channel. Safe to run on several servers at
     * once: each reminder is claimed by one run only.
     */
    public ReminderRun processDue(int batchSize) {
        if (batchSize <= 0) {
            return ReminderRun.NONE;
        }
        int claimedCount = 0;
        int sent = 0;
        int notSent = 0;
        for (AppointmentReminderChannel channel : channels.get()) {
            LocalDateTime now = LocalDateTime.now(clock);
            List<Long> due = new ArrayList<>();
            for (ReminderCandidate candidate : reminders.findRequestedBetween(channel.channel(), window.firstDate(now),
                    window.lastDate(now), MAX_CANDIDATES)) {
                if (due.size() >= batchSize) {
                    break;
                }
                if (isDue(candidate.appointment(), candidate.reminder(), now)) {
                    due.add(candidate.reminder().getId());
                }
            }
            if (due.isEmpty()) {
                continue;
            }
            String claimToken = UUID.randomUUID().toString();
            List<ClaimedReminder> claimed = transactions.execute(status -> claim(due, claimToken, now));
            if (claimed == null) {
                continue;
            }
            claimedCount += claimed.size();
            for (ClaimedReminder reminder : claimed) {
                ReminderOutcome outcome = sendSafely(channel, reminder);
                if (outcome.status() == AppointmentReminderStatus.SENT) {
                    sent++;
                } else {
                    notSent++;
                }
                transactions.executeWithoutResult(status -> recordOutcome(reminder, claimToken, outcome));
                audit(channel.channel(), reminder, outcome);
            }
        }
        return new ReminderRun(claimedCount, sent, notSent);
    }

    /** Must run in a transaction: claims, re-checks against the appointment as it is now, and marks SENDING. */
    private List<ClaimedReminder> claim(List<Long> ids, String claimToken, LocalDateTime now) {
        Date at = toDate(now);
        List<ClaimedReminder> ready = new ArrayList<>();
        for (AppointmentReminder reminder : reminders.claim(ids, claimToken, at, toDate(now.minus(STALE_CLAIM)))) {
            Appointment appointment = appointments.find(reminder.getAppointmentNo());
            if (appointment == null || !isDue(appointment, reminder, now)) {
                reminder.releaseClaim();
                continue;
            }
            Date start = appointment.getStartTimeAsFullDate();
            reminder.startSending(start, at);
            ready.add(new ClaimedReminder(reminder.getId(), appointment.getId(), appointment.getDemographicNo(),
                    toLocal(start)));
        }
        return ready;
    }

    private void recordOutcome(ClaimedReminder claimed, String claimToken, ReminderOutcome outcome) {
        AppointmentReminder reminder = reminders.find(claimed.reminderId());
        if (reminder == null || !claimToken.equals(reminder.getClaimToken())) {
            // The row was deleted, or its claim went stale and another run took it: that run records it.
            logger.warn("Appointment reminder {} changed hands before its outcome was recorded", claimed.reminderId());
            return;
        }
        reminder.finish(outcome.status(), outcome.emailLogId(), Date.from(clock.instant()));
    }

    private static ReminderOutcome sendSafely(AppointmentReminderChannel channel, ClaimedReminder reminder) {
        try {
            ReminderOutcome outcome = channel.send(reminder);
            return outcome == null ? ReminderOutcome.of(AppointmentReminderStatus.UNCONFIRMED) : outcome;
        } catch (RuntimeException e) {
            // The channel may have sent it before failing: never retried. The class only, no patient data.
            logger.error("Appointment reminder channel {} failed; recorded as unconfirmed ({})", channel.channel(),
                    e.getClass().getName());
            return ReminderOutcome.of(AppointmentReminderStatus.UNCONFIRMED);
        }
    }

    private static void audit(ReminderChannel channel, ClaimedReminder reminder, ReminderOutcome outcome) {
        String data = "reminderId=" + reminder.reminderId()
                + (outcome.emailLogId() == null ? "" : "&emailLogId=" + outcome.emailLogId());
        LogAction.addLog(Provider.SYSTEM_PROVIDER_NO, "AppointmentReminder." + channel + "." + outcome.status(),
                LogConst.CON_APPT, String.valueOf(reminder.appointmentNo()), null,
                String.valueOf(reminder.demographicNo()), data);
    }

    /**
     * Due for its current start, or due for the one retry: the same start, whose only attempt failed
     * definitely ({@link AppointmentReminderStatus#FAILED}) at least {@link #RETRY_AFTER} ago, while the start
     * is still inside the window. A refusal (consent, address, setup) or an unconfirmed send is never retried.
     */
    private boolean isDue(Appointment appointment, AppointmentReminder reminder, LocalDateTime now) {
        if (appointment == null || appointment.getDemographicNo() <= 0 || isCancelledOrNoShow(appointment.getStatus())) {
            return false;
        }
        LocalDateTime start = toLocal(appointment.getStartTimeAsFullDate());
        LocalDateTime sentFor = toLocal(reminder.getSentFor());
        if (window.isDue(start, now, sentFor)) {
            return true;
        }
        LocalDateTime lastAttempt = toLocal(reminder.getLastAttemptAt());
        return start != null && start.equals(sentFor)
                && reminder.getLastStatus() == AppointmentReminderStatus.FAILED
                && reminder.getAttempts() < MAX_ATTEMPTS
                && lastAttempt != null && !lastAttempt.plus(RETRY_AFTER).isAfter(now)
                && window.isDue(start, now, null);
    }

    /** Cancelled ({@code C}, including its {@code S}/{@code V} variants) or no-show ({@code N}). */
    static boolean isCancelledOrNoShow(String status) {
        return status != null && (status.contains("C") || status.startsWith("N"));
    }

    private LocalDateTime toLocal(Date date) {
        return date == null ? null : LocalDateTime.ofInstant(date.toInstant(), clock.getZone());
    }

    private Date toDate(LocalDateTime time) {
        return Date.from(time.atZone(clock.getZone()).toInstant());
    }
}
