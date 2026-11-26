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
import io.github.carlos_emr.carlos.commn.model.AppointmentArchive;
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
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Appointment reminders (#5887): records the staff choice on Add/Edit Appointment, and sends what is due.
 *
 * <p>A run reads the reminders that may be due now ({@link ReminderWindow}) and claims them with SKIP LOCKED.
 * The claim is only a lock. Then, one reminder at a time: a short transaction marks it as being sent for the
 * appointment's current start ({@link AppointmentReminderStatus#SENDING}) and commits, the channel sends it,
 * and the outcome is recorded in its own transaction. A failure in one reminder ends that reminder only. So:
 * <ul>
 *   <li>one start is never sent twice: SENDING commits before the send, and a SENDING row is never sent
 *       again;</li>
 *   <li>a run that stops (a restart, a database error) strands at most the one reminder it was sending. Rows
 *       it claimed but had not started are released, or taken over once the claim is
 *       {@link #STALE_CLAIM} old. A SENDING row whose run stopped is recorded as
 *       {@link AppointmentReminderStatus#UNCONFIRMED} after that time: it may have gone, so it is never sent
 *       again;</li>
 *   <li>a cancelled, no-show, deleted or patient-less appointment, or an unticked box, is never reminded.</li>
 * </ul>
 * The appointment is read as it is just before the send, so moving it by editing it (Edit, drag and drop,
 * REST, HL7) makes it due again for its new start. Cut and Paste makes a new appointment, which has a reminder
 * only if its box is ticked.
 *
 * @since 2026-11-12
 */
@Service
public class AppointmentReminderService {

    /**
     * A claim older than this was abandoned by a run that stopped: another run may take the row, or record it
     * as unconfirmed if it was being sent. Far longer than one send.
     */
    static final Duration STALE_CLAIM = Duration.ofMinutes(15);
    /** Candidates read per channel and run, before the exact due check. Only rows that may be due are read. */
    static final int MAX_CANDIDATES = 500;
    /** Attempts per appointment start: the first, and one retry after a definite transport failure. */
    static final int MAX_ATTEMPTS = 2;
    /** How long a failed send waits before its one retry. */
    static final Duration RETRY_AFTER = Duration.ofMinutes(30);
    /**
     * Same-visit checks per reminder and run. A booking moved, or given to another patient, between its check and
     * its marking is checked again for what it is now; one still changing after this many is left to the next run.
     */
    static final int MAX_VISIT_CHECKS = 3;

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
     * ticked leaves no row. Joins the caller's transaction, so the choice commits with the appointment, and so
     * does its audit row: a choice that rolls back leaves no audit, and a failed audit write fails the choice.
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
            // In this transaction, not queued: the audit commits with the choice it describes.
            LogAction.addLogStrict(user, requested ? "AppointmentReminder.requested" : "AppointmentReminder.withdrawn",
                    LogConst.CON_APPT, String.valueOf(appointmentNo),
                    demographicNo > 0 ? String.valueOf(demographicNo) : null, "channel=" + channel);
        }
        return true;
    }

    /**
     * Group Update deletes each appointment and books it again under a new number (#5887). The reminder rows move
     * to the new number, so the choice, and the start a reminder was already sent for, stay with the booking. A row
     * already on the new number is kept as it is. The audit row commits with the move, as in {@link #recordRequest}.
     *
     * @return the number of rows moved
     */
    @Transactional
    public int moveReminders(LoggedInInfo user, int fromAppointmentNo, int toAppointmentNo) {
        if (fromAppointmentNo <= 0 || toAppointmentNo <= 0 || fromAppointmentNo == toAppointmentNo) {
            return 0;
        }
        String providerNo = user == null ? null : user.getLoggedInProviderNo();
        Date now = Date.from(clock.instant());
        int moved = 0;
        for (ReminderChannel channel : ReminderChannel.values()) {
            Optional<AppointmentReminder> reminder = reminders.findByAppointment(fromAppointmentNo, channel);
            if (reminder.isEmpty() || reminders.findByAppointment(toAppointmentNo, channel).isPresent()) {
                continue;
            }
            reminder.get().moveTo(toAppointmentNo, providerNo, now);
            reminders.merge(reminder.get());
            moved++;
            if (user != null) {
                LogAction.addLogStrict(user, "AppointmentReminder.moved", LogConst.CON_APPT,
                        String.valueOf(toAppointmentNo), null, "channel=" + channel + "&from=" + fromAppointmentNo);
            }
        }
        return moved;
    }

    /** Whether staff asked for a reminder of this appointment on this channel (Edit shows the box ticked). */
    public boolean isRequested(int appointmentNo, ReminderChannel channel) {
        return reminders.findByAppointment(appointmentNo, channel).map(AppointmentReminder::isRequested).orElse(false);
    }

    /**
     * What Edit Appointment shows under the Email Reminder box: what happened to the reminder for the
     * appointment's current start and patient, or when it will go. Empty when no reminder is requested, the
     * appointment is cancelled, a no-show, or it has no patient. A past appointment shows only a recorded outcome
     * for that start and patient; when nothing was attempted for it, the line is empty.
     */
    public Optional<ReminderStatusLine> statusLine(Appointment appointment, Locale locale) {
        if (appointment == null || appointment.getId() == null || appointment.getDemographicNo() <= 0
                || isCancelledOrNoShow(appointment.getStatus())) {
            return Optional.empty();
        }
        Optional<AppointmentReminder> stored = reminders.findByAppointment(appointment.getId(), ReminderChannel.EMAIL);
        if (stored.isEmpty() || !stored.get().isRequested()) {
            return Optional.empty();
        }
        AppointmentReminder reminder = stored.get();
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime start = toLocal(appointment.getStartTimeAsFullDate());
        if (start == null) {
            return Optional.empty();
        }
        DateTimeFormatter shown = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                .withLocale(locale == null ? Locale.ENGLISH : locale);
        LocalDateTime attempted = toLocal(reminder.getLastAttemptAt());
        String attemptedAt = attempted == null ? "" : shown.format(attempted);
        // An attempt for another patient (the booking was given to this one since) says nothing about this one.
        if (start.equals(attemptedStart(reminder, appointment.getDemographicNo()))
                && reminder.getLastStatus() != null) {
            return Optional.of(switch (reminder.getLastStatus()) {
                case SENT -> new ReminderStatusLine("appointment.reminder.status.sent", attemptedAt);
                // A send in progress lasts seconds; one a stopped server left becomes UNCONFIRMED when recovered.
                case SENDING -> new ReminderStatusLine("appointment.reminder.status.sending", "");
                case UNCONFIRMED -> new ReminderStatusLine("appointment.reminder.status.unconfirmed", attemptedAt);
                case FAILED -> new ReminderStatusLine("appointment.reminder.status.failed", attemptedAt);
                case BLOCKED_CONSENT -> new ReminderStatusLine("appointment.reminder.status.blockedConsent", "");
                case NO_ADDRESS, INVALID_ADDRESS -> new ReminderStatusLine("appointment.reminder.status.noAddress", "");
                case NO_SENDER, NO_FOOTER -> new ReminderStatusLine("appointment.reminder.status.notSetUp", "");
                case NOT_PERMITTED -> new ReminderStatusLine("appointment.reminder.status.notPermitted", "");
                case DUPLICATE -> new ReminderStatusLine("appointment.reminder.status.duplicate", "");
            });
        }
        if (!now.isBefore(start)) {
            return Optional.empty();
        }
        if (now.isBefore(window.opensAt(start))) {
            return Optional.of(new ReminderStatusLine("appointment.reminder.status.scheduled", shown.format(window.opensAt(start))));
        }
        return Optional.of(window.isDue(start, now, null)
                ? new ReminderStatusLine("appointment.reminder.status.due", "")
                : new ReminderStatusLine("appointment.reminder.status.tooLate", ""));
    }

    /**
     * Sends the reminders due now, at most {@code batchSize} per channel. Safe to run on several servers at
     * once: each reminder is claimed by one run only. Stops between reminders when its thread is interrupted
     * (the scheduler stopping), releasing what it had not started.
     */
    public ReminderRun processDue(int batchSize) {
        if (batchSize <= 0) {
            return ReminderRun.NONE;
        }
        int claimedCount = 0;
        int sent = 0;
        int notSent = 0;
        for (AppointmentReminderChannel channel : channels.get()) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }
            LocalDateTime now = LocalDateTime.now(clock);
            recoverAbandoned(channel.channel(), now);
            List<Long> due = new ArrayList<>();
            // Rows another run holds a fresh claim on are left out before the batch is counted: they would fill it
            // and then be skipped by the claim, leaving free due rows unread until that claim lapses.
            for (ReminderCandidate candidate : reminders.findDue(channel.channel(), window.earliestStart(now),
                    window.latestStart(now), MAX_ATTEMPTS, toDate(now.minus(STALE_CLAIM)), MAX_CANDIDATES)) {
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
            List<Long> claimed = transactions.execute(status -> claim(due, claimToken, now));
            if (claimed.isEmpty()) {
                continue;
            }
            claimedCount += claimed.size();
            Deque<Long> waiting = new ArrayDeque<>(claimed);
            try {
                while (!waiting.isEmpty() && !Thread.currentThread().isInterrupted()) {
                    Optional<ReminderOutcome> outcome = sendOne(channel, waiting.removeFirst(), claimToken);
                    if (outcome.isEmpty()) {
                        continue;
                    }
                    if (outcome.get().status() == AppointmentReminderStatus.SENT) {
                        sent++;
                    } else {
                        notSent++;
                    }
                }
            } finally {
                release(waiting, claimToken);
            }
        }
        return new ReminderRun(claimedCount, sent, notSent);
    }

    /**
     * Must run in a transaction: claims (a lock only), re-checks each row against the appointment as it is now,
     * and releases those no longer due. Nothing is marked as attempted here.
     */
    private List<Long> claim(List<Long> ids, String claimToken, LocalDateTime now) {
        List<Long> claimed = new ArrayList<>();
        for (AppointmentReminder reminder : reminders.claim(ids, claimToken, toDate(now), toDate(now.minus(STALE_CLAIM)))) {
            Appointment appointment = appointments.find(reminder.getAppointmentNo());
            if (appointment == null || !isDue(appointment, reminder, now)) {
                reminder.releaseClaim();
                continue;
            }
            claimed.add(reminder.getId());
        }
        return claimed;
    }

    /**
     * One claimed reminder: marked SENDING in its own transaction, sent, and its outcome recorded. Whatever
     * fails ends this reminder only; the others in the run carry on.
     *
     * <p>The same-visit check and the marking read the appointment in separate transactions. The check's decision
     * holds only for the patient and start it saw ({@link VisitCheck}): if the booking was moved or given to
     * another patient in between, it is checked again for what it is now, up to {@link #MAX_VISIT_CHECKS} times,
     * and otherwise left to the next run.
     *
     * @return the outcome, or empty when nothing was sent (no longer due, or it could not be started)
     */
    private Optional<ReminderOutcome> sendOne(AppointmentReminderChannel channel, long reminderId, String claimToken) {
        VisitCheck visit = checkVisit(channel.channel(), reminderId);
        Started started = startChecked(reminderId, claimToken, visit);
        for (int checks = 1; started.visitChanged() && checks < MAX_VISIT_CHECKS; checks++) {
            visit = checkVisit(channel.channel(), reminderId);
            started = startChecked(reminderId, claimToken, visit);
        }
        if (started.visitChanged()) {
            // Still changing under the check: not attempted, so the next run looks again.
            release(List.of(reminderId), claimToken);
            return Optional.empty();
        }
        if (started.claimed() == null) {
            return Optional.empty();
        }
        ClaimedReminder claimed = started.claimed();
        ReminderOutcome outcome = switch (visit.visit()) {
            case COVERED -> ReminderOutcome.of(AppointmentReminderStatus.DUPLICATE);
            // Not sent: a definite failure, so its one retry runs the check again.
            case UNCHECKED -> ReminderOutcome.because(AppointmentReminderStatus.FAILED, "sameVisitUnchecked");
            default -> sendSafely(channel, claimed);
        };
        recordSafely(claimed, claimToken, outcome);
        try {
            audit(channel.channel(), claimed, outcome);
        } catch (RuntimeException e) {
            logger.error("Appointment reminder {} could not be audited ({})", reminderId, e.getClass().getName());
        }
        return Optional.of(outcome);
    }

    /** {@link #start}, in its own transaction, for a reminder whose visit was checked as {@code visit}. */
    private Started startChecked(long reminderId, String claimToken, VisitCheck visit) {
        if (visit.visit() == SameVisit.WAIT) {
            // Another booking's reminder for this visit is being sent now, or goes first: not attempted, so the next
            // run looks again.
            release(List.of(reminderId), claimToken);
            return Started.NOT_STARTED;
        }
        try {
            Started started = transactions.execute(
                    status -> start(reminderId, claimToken, LocalDateTime.now(clock), visit));
            // The transaction's contract allows no answer: nothing was marked, so it is not started.
            return Objects.requireNonNullElse(started, Started.NOT_STARTED);
        } catch (RuntimeException e) {
            // Not marked and not sent: the claim lapses and a later run takes the row. The class only.
            logger.error("Appointment reminder {} could not be started ({})", reminderId, e.getClass().getName());
            return Started.NOT_STARTED;
        }
    }

    /**
     * What {@link #start} did: marked the reminder as being sent ({@code claimed}), or not; {@code visitChanged}
     * when it did not because the booking is no longer the one its same-visit check saw, its claim kept for the
     * check to run again.
     */
    private record Started(ClaimedReminder claimed, boolean visitChanged) {
        static final Started NOT_STARTED = new Started(null, false);
        static final Started VISIT_CHANGED = new Started(null, true);
    }

    /**
     * What a booking's reminder did for a start that keeps it in the same-visit check whatever happened to the
     * booking after: sent, or may have been, or being sent now. Only the last start a reminder went for is kept, so
     * a booking reminded again for a new time no longer covers the old one (a known limit, in the doc).
     */
    public static final Set<AppointmentReminderStatus> HANDED_OVER = Collections.unmodifiableSet(
            EnumSet.of(AppointmentReminderStatus.SENT, AppointmentReminderStatus.UNCONFIRMED,
                    AppointmentReminderStatus.SENDING));

    /** Where a reminder stands against the patient's other bookings for the same visit. */
    enum SameVisit {
        /** No other booking carries it: send. */
        SEND,
        /** Another booking carries it: recorded as {@link AppointmentReminderStatus#DUPLICATE}, not sent. */
        COVERED,
        /**
         * Another booking's reminder for this visit is being sent now, or goes first and may still go: not attempted;
         * the next run looks again.
         */
        WAIT,
        /** The check failed: not sent, recorded as failed, so its one retry checks again. */
        UNCHECKED
    }

    /**
     * A same-visit decision, and the booking it was made for: its patient and its start when checked. Marking the
     * reminder as being sent reads the booking again, and applies the decision only to that patient and start.
     * {@code checked} is false for {@link SameVisit#UNCHECKED}: no booking was read, and it is not sent anyway.
     */
    record VisitCheck(SameVisit visit, int demographicNo, LocalDateTime start, boolean checked) {

        static final VisitCheck UNCHECKED = new VisitCheck(SameVisit.UNCHECKED, 0, null, false);

        static VisitCheck of(SameVisit visit, int demographicNo, LocalDateTime start) {
            return new VisitCheck(visit, demographicNo, start, true);
        }

        /** Whether this decision was made for the booking as it is now: the same patient at the same start. */
        boolean madeFor(int patient, LocalDateTime at) {
            return !checked || (demographicNo == patient && Objects.equals(start, at));
        }
    }

    /**
     * Where this reminder stands against the patient's other bookings for the same visit (#5887): the same patient
     * at the same start, as Group Appt books one per provider, whose box can be ticked on Edit later. The patient
     * must never get two reminders for one visit, so:
     * <ul>
     *   <li>a reminder already sent to this patient, or that may have been, for this start covers it, whatever staff
     *       did to its booking after: unticked it, cancelled it, moved it, gave it to another patient or deleted
     *       it;</li>
     *   <li>while another booking's reminder for this start is being sent, this one waits for the next run, so an
     *       earlier reminder that joins the visit mid-send never goes out beside it;</li>
     *   <li>an earlier reminder still in the visit (ticked, this patient's, not cancelled or a no-show, at this
     *       start) goes first: this one waits for the next run until that one goes (and then covers it), unless it
     *       already ended this start without one going: a refusal, a failure whose one retry the window no longer
     *       allows, or a reminder itself covered ({@link AppointmentReminderStatus#DUPLICATE}), whose cover may have
     *       gone since. A booking is recorded as covered only once another's reminder has gone, or may have.</li>
     * </ul>
     * Exactly one is sent whichever server runs first. If the check fails, the reminder is not sent: a failure,
     * retried once.
     *
     * @return the decision, with the patient and start it was made for
     */
    VisitCheck checkVisit(ReminderChannel channel, long reminderId) {
        try {
            VisitCheck found = transactions.execute(status -> {
                AppointmentReminder reminder = reminders.find(reminderId);
                Appointment appointment = reminder == null ? null : appointments.find(reminder.getAppointmentNo());
                LocalDateTime start = appointment == null ? null : toLocal(appointment.getStartTimeAsFullDate());
                int patient = appointment == null ? 0 : appointment.getDemographicNo();
                if (start == null || patient <= 0) {
                    // Nothing to compare: the start step decides whether it is still due.
                    return VisitCheck.of(SameVisit.SEND, patient, start);
                }
                LocalDateTime now = LocalDateTime.now(clock);
                SameVisit visit = SameVisit.SEND;
                LocalDateTime since = null;
                boolean historyRead = false;
                for (ReminderCandidate sibling : reminders.findSameVisit(channel, patient, start, reminderId,
                        HANDED_OVER)) {
                    AppointmentReminder other = sibling.reminder();
                    // What the other booking's reminder did for this patient at this start; null when it has not
                    // been tried for them (an attempt for the booking's earlier patient says nothing here).
                    AppointmentReminderStatus done = start.equals(attemptedStart(other, patient))
                            ? other.getLastStatus() : null;
                    Appointment booking = sibling.appointment();
                    // Still in this visit: ticked, this patient's, not cancelled or a no-show, and at this start.
                    boolean live = other.isRequested() && booking != null && booking.getDemographicNo() == patient
                            && !isCancelledOrNoShow(booking.getStatus())
                            && start.equals(toLocal(booking.getStartTimeAsFullDate()));
                    // Sent, or may have been. A DUPLICATE never covers: nothing went from it.
                    if (done == AppointmentReminderStatus.SENT || done == AppointmentReminderStatus.UNCONFIRMED) {
                        // A booking still here covers the visit. One that left this start since (unticked, cancelled,
                        // moved) covers only a booking that was already here when its reminder went: a booking moved
                        // or booked into this start later is reminded for it.
                        if (live) {
                            return VisitCheck.of(SameVisit.COVERED, patient, start);
                        }
                        if (!historyRead) {
                            // Every departed sibling compares the same booking history; read it only once.
                            since = atStartSince(appointment, start);
                            historyRead = true;
                        }
                        if (hereWhenHandedOver(since, other)) {
                            return VisitCheck.of(SameVisit.COVERED, patient, start);
                        }
                        continue;
                    }
                    if (done == AppointmentReminderStatus.SENDING) {
                        visit = SameVisit.WAIT;
                        continue;
                    }
                    // Otherwise it counts only while it is still part of this visit.
                    if (!live) {
                        continue;
                    }
                    // Any outcome other than a failed attempt with its retry left ended this start without a reminder
                    // going from that booking: a refusal, a spent failure, or DUPLICATE (covered by a third booking,
                    // which may have gone since). Such a booking never covers this one.
                    boolean endedUnsent = done != null && !retryPossible(other, start, now);
                    if (other.getId() < reminderId && !endedUnsent) {
                        // The earlier booking's reminder goes first, but has not gone yet (not tried for this start,
                        // or failed with its retry left). Wait rather than end as covered: if it never goes, this one
                        // still can.
                        visit = SameVisit.WAIT;
                    }
                }
                return VisitCheck.of(visit, patient, start);
            });
            // The transaction's contract allows no answer; that is not a checked visit, so fail closed like an error.
            return Objects.requireNonNullElse(found, VisitCheck.UNCHECKED);
        } catch (RuntimeException e) {
            logger.warn("Appointment reminder {} could not be checked for the same visit ({}); not sent",
                    reminderId, e.getClass().getName());
            return VisitCheck.UNCHECKED;
        }
    }

    /**
     * Whether this booking was already at its start when {@code other}'s reminder was handed over, so that reminder
     * told the patient of this booking too. {@code since} comes from its archived versions ({@link #atStartSince}),
     * read once per same-visit check, not its last update, which saving any change (even ticking the box) moves.
     * When that cannot be told, it counts as here: one reminder rather than two.
     */
    private boolean hereWhenHandedOver(LocalDateTime since, AppointmentReminder other) {
        LocalDateTime handedOver = toLocal(other.getLastAttemptAt());
        return handedOver == null || since == null || !since.isAfter(handedOver);
    }

    /**
     * Since when {@code appointment} has been at {@code start}: the update that brought it there, read from its
     * archived versions (each change to an appointment's date or time archives the version it replaces), or its
     * creation when it was always there. Null when the history does not say (no creation time).
     */
    LocalDateTime atStartSince(Appointment appointment, LocalDateTime start) {
        LocalDateTime since = toLocal(appointment.getUpdateDateTime());
        for (AppointmentArchive version : reminders.findArchivedVersions(appointment.getId(),
                appointment.getDemographicNo())) {
            if (!start.equals(startOf(version))) {
                // The version before it moved here: the move came with the version after it.
                return since;
            }
            since = toLocal(version.getUpdateDateTime());
        }
        // Always here, as far as the history goes: since its creation. Without a creation time, unknown (null): the
        // last save time would be wrong, since saving any change moves it.
        return toLocal(appointment.getCreateDateTime());
    }

    private static LocalDateTime startOf(AppointmentArchive version) {
        if (version.getAppointmentDate() == null || version.getStartTime() == null) {
            return null;
        }
        // As Appointment.getStartTimeAsFullDate composes it: the date and the time to the minute.
        return new java.sql.Date(version.getAppointmentDate().getTime()).toLocalDate()
                .atTime(new java.sql.Time(version.getStartTime().getTime()).toLocalTime().withSecond(0).withNano(0));
    }

    /**
     * Whether a reminder that failed for {@code start} will still get its one retry: an attempt left, and a moment
     * the window allows, from {@link #RETRY_AFTER} after its last attempt (the retry rule in {@link #isDue}).
     */
    private boolean retryPossible(AppointmentReminder reminder, LocalDateTime start, LocalDateTime now) {
        if (reminder.getLastStatus() != AppointmentReminderStatus.FAILED || reminder.getAttempts() >= MAX_ATTEMPTS) {
            return false;
        }
        LocalDateTime lastAttempt = toLocal(reminder.getLastAttemptAt());
        LocalDateTime retryAt = lastAttempt == null || lastAttempt.plus(RETRY_AFTER).isBefore(now)
                ? now : lastAttempt.plus(RETRY_AFTER);
        return window.isDue(start, retryAt, null);
    }

    /**
     * Must run in a transaction: marks one claimed reminder as being sent for the appointment's start and patient
     * now, just before the send. Not started when the claim is no longer this run's, the box was unticked, or the
     * appointment is no longer due (its claim is then released), or when the appointment is no longer the booking
     * {@code visit} was decided for (moved, or given to another patient, since): its claim is kept, for the
     * same-visit check to run again.
     */
    private Started start(long reminderId, String claimToken, LocalDateTime now, VisitCheck visit) {
        AppointmentReminder reminder = reminders.find(reminderId);
        if (reminder == null || !claimToken.equals(reminder.getClaimToken())) {
            return Started.NOT_STARTED;
        }
        Appointment appointment = appointments.find(reminder.getAppointmentNo());
        if (!reminder.isRequested() || appointment == null || !isDue(appointment, reminder, now)) {
            reminder.releaseClaim();
            return Started.NOT_STARTED;
        }
        Date start = appointment.getStartTimeAsFullDate();
        if (!visit.madeFor(appointment.getDemographicNo(), toLocal(start))) {
            return Started.VISIT_CHANGED;
        }
        reminder.startSending(start, appointment.getDemographicNo(), toDate(now));
        return new Started(new ClaimedReminder(reminder.getId(), appointment.getId(), appointment.getDemographicNo(),
                toLocal(start)), false);
    }

    /**
     * Records the outcome, trying once more if the first transaction fails (a concurrent edit of the row, a
     * database blip). If both fail, the row stays SENDING and is recorded as unconfirmed once its claim is stale:
     * never sent again.
     */
    private void recordSafely(ClaimedReminder claimed, String claimToken, ReminderOutcome outcome) {
        for (int attempt = 1; ; attempt++) {
            try {
                transactions.executeWithoutResult(status -> recordOutcome(claimed, claimToken, outcome));
                return;
            } catch (RuntimeException e) {
                logger.error("Appointment reminder {} outcome could not be recorded, try {} ({})", claimed.reminderId(),
                        attempt, e.getClass().getName());
                if (attempt >= 2) {
                    return;
                }
            }
        }
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

    /** Releases this run's claim on reminders it did not start, so the next run need not wait for it to go stale. */
    private void release(Collection<Long> ids, String claimToken) {
        if (ids.isEmpty()) {
            return;
        }
        try {
            transactions.executeWithoutResult(status -> {
                for (Long id : ids) {
                    AppointmentReminder reminder = reminders.find(id);
                    if (reminder != null && claimToken.equals(reminder.getClaimToken())) {
                        reminder.releaseClaim();
                    }
                }
            });
        } catch (RuntimeException e) {
            // The claims lapse after STALE_CLAIM instead.
            logger.warn("Appointment reminder claims could not be released ({})", e.getClass().getName());
        }
    }

    /**
     * Records as unconfirmed the reminders a stopped run left SENDING: it may have sent them, so they are never
     * sent again, and Edit says its delivery could not be confirmed.
     */
    private void recoverAbandoned(ReminderChannel channel, LocalDateTime now) {
        List<ClaimedReminder> recovered;
        try {
            recovered = transactions.execute(status -> {
                List<ClaimedReminder> found = new ArrayList<>();
                for (AppointmentReminder reminder : reminders.findAbandonedSending(channel, toDate(now.minus(STALE_CLAIM)))) {
                    reminder.finish(AppointmentReminderStatus.UNCONFIRMED, reminder.getLastEmailLogId(), toDate(now));
                    // Audited under the patient that attempt was for, who may no longer be the booking's.
                    Integer patient = reminder.getSentForDemographicNo();
                    if (patient == null) {
                        Appointment appointment = appointments.find(reminder.getAppointmentNo());
                        patient = appointment == null ? 0 : appointment.getDemographicNo();
                    }
                    found.add(new ClaimedReminder(reminder.getId(), reminder.getAppointmentNo(), patient,
                            toLocal(reminder.getSentFor())));
                }
                return found;
            });
        } catch (RuntimeException e) {
            logger.error("Abandoned appointment reminders could not be recovered ({})", e.getClass().getName());
            return;
        }
        for (ClaimedReminder reminder : recovered) {
            logger.warn("Appointment reminder {} was left being sent by a run that stopped; recorded as unconfirmed",
                    reminder.reminderId());
            try {
                audit(channel, reminder, ReminderOutcome.because(AppointmentReminderStatus.UNCONFIRMED, "abandoned"));
            } catch (RuntimeException e) {
                logger.error("Appointment reminder {} could not be audited ({})", reminder.reminderId(),
                        e.getClass().getName());
            }
        }
    }

    private static ReminderOutcome sendSafely(AppointmentReminderChannel channel, ClaimedReminder reminder) {
        try {
            ReminderOutcome outcome = channel.send(reminder);
            return outcome == null ? ReminderOutcome.of(AppointmentReminderStatus.UNCONFIRMED) : outcome;
        } catch (RuntimeException e) {
            // The channel may have sent it before failing: never retried. A channel returns what it knows was
            // not sent; anything thrown is unknown. The class only, no patient data.
            logger.error("Appointment reminder channel {} failed; recorded as unconfirmed ({})", channel.channel(),
                    e.getClass().getName());
            return ReminderOutcome.because(AppointmentReminderStatus.UNCONFIRMED, e);
        }
    }

    private static void audit(ReminderChannel channel, ClaimedReminder reminder, ReminderOutcome outcome) {
        String data = "reminderId=" + reminder.reminderId()
                + (outcome.emailLogId() == null ? "" : "&emailLogId=" + outcome.emailLogId())
                + (outcome.reason() == null ? "" : "&reason=" + outcome.reason());
        LogAction.addLog(Provider.SYSTEM_PROVIDER_NO, "AppointmentReminder." + channel + "." + outcome.status(),
                LogConst.CON_APPT, String.valueOf(reminder.appointmentNo()), null,
                reminder.demographicNo() > 0 ? String.valueOf(reminder.demographicNo()) : null, data);
    }

    /**
     * Due for its current start and patient, or due for the one retry: the same start and patient, whose only
     * attempt failed definitely ({@link AppointmentReminderStatus#FAILED}) at least {@link #RETRY_AFTER} ago, while
     * the start is still inside the window. A refusal (consent, address, setup) or an unconfirmed send is never
     * retried. An attempt for another patient (the booking was given to this one since) does not count.
     */
    private boolean isDue(Appointment appointment, AppointmentReminder reminder, LocalDateTime now) {
        if (appointment == null || appointment.getDemographicNo() <= 0 || isCancelledOrNoShow(appointment.getStatus())) {
            return false;
        }
        LocalDateTime start = toLocal(appointment.getStartTimeAsFullDate());
        LocalDateTime sentFor = attemptedStart(reminder, appointment.getDemographicNo());
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

    /**
     * The start this reminder's last attempt was for, when that attempt was for patient {@code demographicNo};
     * otherwise {@code null}, as for a reminder never attempted.
     */
    private LocalDateTime attemptedStart(AppointmentReminder reminder, int demographicNo) {
        return reminder.attemptWasFor(demographicNo) ? toLocal(reminder.getSentFor()) : null;
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
