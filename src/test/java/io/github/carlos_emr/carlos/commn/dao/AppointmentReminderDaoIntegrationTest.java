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
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * #5887: the reminder table on H2, built from the entity. One row per appointment and channel; the due query
 * reads only requested rows that may be due inside the window, with their appointments; a claim takes only
 * unclaimed (or abandoned) rows; rows left being sent by a stopped run are found.
 * The real SKIP LOCKED across two connections belongs to the MariaDB lane. FAKE patients only.
 */
@DisplayName("AppointmentReminderDao on H2")
@Tag("integration")
@Tag("dao")
@Tag("appointment")
@Transactional
class AppointmentReminderDaoIntegrationTest extends CarlosTestBase {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 13);

    @Autowired
    private AppointmentReminderDao reminders;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    private Date now;

    @BeforeEach
    void setUp() {
        now = at(DAY.minusDays(1), LocalTime.of(10, 0));
    }

    private static Date at(LocalDate day, LocalTime time) {
        return Date.from(day.atTime(time).atZone(ZoneId.systemDefault()).toInstant());
    }

    private Appointment appointment(LocalDate day, LocalTime start) {
        Appointment appointment = new Appointment();
        appointment.setAppointmentDate(java.sql.Date.valueOf(day));
        appointment.setStartTime(java.sql.Time.valueOf(start));
        appointment.setEndTime(java.sql.Time.valueOf(start.plusMinutes(15)));
        appointment.setProviderNo("999001");
        appointment.setDemographicNo(42);
        appointment.setStatus("t");
        appointment.setName("FAKE-PATIENT, Reminder");
        appointment.setNotes("");
        appointment.setReason("FAKE reason never sent");
        appointment.setCreator("testuser");
        appointment.setCreateDateTime(new Date());
        appointment.setUpdateDateTime(new Date());
        appointment.setProgramId(10001);
        entityManager.persist(appointment);
        entityManager.flush();
        return appointment;
    }

    private AppointmentReminder reminder(Appointment appointment, ReminderChannel channel) {
        AppointmentReminder reminder = AppointmentReminder.requested(appointment.getId(), channel, "999998", now);
        reminders.persist(reminder);
        entityManager.flush();
        return reminder;
    }

    @Test
    void shouldFindTheRow_forItsAppointmentAndChannel() {
        Appointment appointment = appointment(DAY, LocalTime.of(9, 0));
        AppointmentReminder email = reminder(appointment, ReminderChannel.EMAIL);

        assertThat(reminders.findByAppointment(appointment.getId(), ReminderChannel.EMAIL)).contains(email);
        assertThat(reminders.findByAppointment(appointment.getId(), ReminderChannel.SMS)).isEmpty();
        assertThat(reminders.findByAppointment(appointment.getId() + 1000, ReminderChannel.EMAIL)).isEmpty();
        // The SMS channel's column (#884) exists under the migration's name and stays empty for email.
        assertThat(entityManager.createNativeQuery("SELECT last_sms_transaction_id FROM appointment_reminder WHERE id = ?1")
                .setParameter(1, email.getId()).getSingleResult()).isNull();
        assertThat(email.getLastSmsTransactionId()).isNull();
    }

    @Test
    void shouldKeepOneRow_perAppointmentAndChannel() {
        Appointment appointment = appointment(DAY, LocalTime.of(9, 0));
        reminder(appointment, ReminderChannel.EMAIL);
        reminder(appointment, ReminderChannel.SMS);

        // The IDENTITY insert happens at persist, inside the DAO, which may translate the violation.
        assertThatThrownBy(() -> reminder(appointment, ReminderChannel.EMAIL))
                .isInstanceOfAny(PersistenceException.class, DataAccessException.class);
    }

    /** The reminder attempted for {@code start}, ending with {@code status}, {@code times} times. */
    private void attempted(AppointmentReminder reminder, LocalDate day, LocalTime start, AppointmentReminderStatus status,
                           int times) {
        for (int i = 0; i < times; i++) {
            reminder.startSending(at(day, start), now);
            reminder.finish(status, null, now);
        }
        reminders.merge(reminder);
    }

    @Test
    void shouldReadOnlyRowsThatMayBeDue_insideTheWindowEarliestFirst() {
        // now is DAY-1 10:00: due starts run from DAY-1 12:00 (a 2 h cutoff) to DAY 10:00 (a 24 h lead).
        LocalDateTime earliest = DAY.minusDays(1).atTime(12, 0);
        LocalDateTime latest = DAY.atTime(10, 0);
        Appointment tooSoon = appointment(DAY.minusDays(1), LocalTime.of(11, 0));
        Appointment earliestDue = appointment(DAY.minusDays(1), LocalTime.of(13, 0));
        Appointment due = appointment(DAY, LocalTime.of(8, 30));
        Appointment alreadySent = appointment(DAY, LocalTime.of(9, 0));
        Appointment moved = appointment(DAY, LocalTime.of(9, 15));
        Appointment movedDay = appointment(DAY, LocalTime.of(9, 20));
        Appointment failedOnce = appointment(DAY, LocalTime.of(9, 30));
        Appointment failedTwice = appointment(DAY, LocalTime.of(9, 45));
        Appointment tooFar = appointment(DAY, LocalTime.of(15, 0));
        Appointment withdrawnAppointment = appointment(DAY, LocalTime.of(8, 0));
        Appointment smsOnly = appointment(DAY, LocalTime.of(8, 15));
        reminder(tooSoon, ReminderChannel.EMAIL);
        AppointmentReminder earliestReminder = reminder(earliestDue, ReminderChannel.EMAIL);
        AppointmentReminder dueReminder = reminder(due, ReminderChannel.EMAIL);
        attempted(reminder(alreadySent, ReminderChannel.EMAIL), DAY, LocalTime.of(9, 0), AppointmentReminderStatus.SENT, 1);
        AppointmentReminder movedReminder = reminder(moved, ReminderChannel.EMAIL);
        attempted(movedReminder, DAY, LocalTime.of(7, 0), AppointmentReminderStatus.SENT, 1);
        // Moved to the same time on another day: the date half of the comparison.
        AppointmentReminder movedDayReminder = reminder(movedDay, ReminderChannel.EMAIL);
        attempted(movedDayReminder, DAY.minusDays(1), LocalTime.of(9, 20), AppointmentReminderStatus.SENT, 1);
        AppointmentReminder retry = reminder(failedOnce, ReminderChannel.EMAIL);
        attempted(retry, DAY, LocalTime.of(9, 30), AppointmentReminderStatus.FAILED, 1);
        attempted(reminder(failedTwice, ReminderChannel.EMAIL), DAY, LocalTime.of(9, 45), AppointmentReminderStatus.FAILED, 2);
        reminder(tooFar, ReminderChannel.EMAIL);
        AppointmentReminder withdrawn = reminder(withdrawnAppointment, ReminderChannel.EMAIL);
        withdrawn.withdraw("999998", now);
        reminders.merge(withdrawn);
        reminder(smsOnly, ReminderChannel.SMS);
        entityManager.flush();

        List<ReminderCandidate> candidates = reminders.findDue(ReminderChannel.EMAIL, earliest, latest, 2, 10);

        // Not the start already reminded, nor the second failure, nor anything outside the window: those can
        // never crowd the due ones out of the limit.
        assertThat(candidates).extracting(ReminderCandidate::reminder)
                .containsExactly(earliestReminder, dueReminder, movedReminder, movedDayReminder, retry);
        assertThat(candidates).extracting(candidate -> candidate.appointment().getId())
                .containsExactly(earliestDue.getId(), due.getId(), moved.getId(), movedDay.getId(), failedOnce.getId());
        assertThat(reminders.findDue(ReminderChannel.EMAIL, earliest, latest, 2, 1))
                .extracting(ReminderCandidate::reminder).containsExactly(earliestReminder);
        assertThat(reminders.findDue(ReminderChannel.EMAIL, latest, earliest, 2, 10)).isEmpty();
    }

    @Test
    void shouldFindRowsLeftSending_byARunThatStopped() {
        Date stale = new Date(now.getTime() - 20 * 60_000L);
        Date staleBefore = new Date(now.getTime() - 15 * 60_000L);
        AppointmentReminder abandoned = reminder(appointment(DAY, LocalTime.of(9, 0)), ReminderChannel.EMAIL);
        abandoned.claim("FAKE-stopped", stale);
        abandoned.startSending(at(DAY, LocalTime.of(9, 0)), stale);
        AppointmentReminder unclaimed = reminder(appointment(DAY, LocalTime.of(9, 15)), ReminderChannel.EMAIL);
        unclaimed.startSending(at(DAY, LocalTime.of(9, 15)), stale);
        AppointmentReminder live = reminder(appointment(DAY, LocalTime.of(9, 30)), ReminderChannel.EMAIL);
        live.claim("FAKE-running", now);
        live.startSending(at(DAY, LocalTime.of(9, 30)), now);
        AppointmentReminder finished = reminder(appointment(DAY, LocalTime.of(9, 45)), ReminderChannel.EMAIL);
        finished.claim("FAKE-done", stale);
        finished.startSending(at(DAY, LocalTime.of(9, 45)), stale);
        finished.finish(AppointmentReminderStatus.SENT, null, stale);
        AppointmentReminder sms = reminder(appointment(DAY, LocalTime.of(10, 0)), ReminderChannel.SMS);
        sms.claim("FAKE-stopped", stale);
        sms.startSending(at(DAY, LocalTime.of(10, 0)), stale);
        for (AppointmentReminder reminder : List.of(abandoned, unclaimed, live, finished, sms)) {
            reminders.merge(reminder);
        }
        entityManager.flush();

        AppointmentReminder slowBatch = reminder(appointment(DAY, LocalTime.of(10, 15)), ReminderChannel.EMAIL);
        slowBatch.claim("FAKE-slow-batch", stale);
        slowBatch.startSending(at(DAY, LocalTime.of(10, 15)), now);
        reminders.merge(slowBatch);
        entityManager.flush();

        // A slow batch's later send renews its claim, so it is not taken for abandoned.
        assertThat(reminders.findAbandonedSending(ReminderChannel.EMAIL, staleBefore))
                .containsExactly(abandoned, unclaimed);
    }

    @Test
    void shouldClaimOnlyUnclaimedOrAbandonedRows() {
        AppointmentReminder first = reminder(appointment(DAY, LocalTime.of(9, 0)), ReminderChannel.EMAIL);
        AppointmentReminder second = reminder(appointment(DAY, LocalTime.of(9, 30)), ReminderChannel.EMAIL);
        Date earlier = new Date(now.getTime() - 60_000L);

        List<AppointmentReminder> claimed = reminders.claim(List.of(first.getId(), second.getId()), "FAKE-claim-1", now, earlier);
        entityManager.flush();

        assertThat(claimed).containsExactly(first, second);
        assertThat(first.getClaimToken()).isEqualTo("FAKE-claim-1");
        assertThat(reminders.claim(List.of(first.getId()), "FAKE-claim-2", now, earlier))
                .as("a live claim is left to its run").isEmpty();
        assertThat(reminders.claim(List.of(first.getId()), "FAKE-claim-3", now, new Date(now.getTime() + 60_000L)))
                .as("an abandoned claim may be taken over").containsExactly(first);
        assertThat(first.getClaimToken()).isEqualTo("FAKE-claim-3");
    }

    /** Group Appt's bookings for the same patient at the same time: the same visit. */
    @Test
    void shouldFindTheOtherRequestedRemindersForTheSameVisit_onTheSameChannel() {
        Appointment booked = appointment(DAY, LocalTime.of(9, 0));
        AppointmentReminder mine = reminder(booked, ReminderChannel.EMAIL);
        AppointmentReminder sibling = reminder(appointment(DAY, LocalTime.of(9, 0)), ReminderChannel.EMAIL);
        reminder(appointment(DAY, LocalTime.of(9, 15)), ReminderChannel.EMAIL);
        reminder(appointment(DAY.plusDays(1), LocalTime.of(9, 0)), ReminderChannel.EMAIL);
        Appointment otherPatient = appointment(DAY, LocalTime.of(9, 0));
        otherPatient.setDemographicNo(43);
        entityManager.merge(otherPatient);
        reminder(otherPatient, ReminderChannel.EMAIL);
        AppointmentReminder withdrawn = reminder(appointment(DAY, LocalTime.of(9, 0)), ReminderChannel.EMAIL);
        withdrawn.withdraw("999998", now);
        reminders.merge(withdrawn);
        reminder(appointment(DAY, LocalTime.of(9, 0)), ReminderChannel.SMS);
        entityManager.flush();

        assertThat(reminders.findSameVisit(ReminderChannel.EMAIL, 42, DAY.atTime(9, 0), mine.getId(), HANDED_OVER))
                .extracting(found -> found.reminder().getId()).containsExactly(sibling.getId());
        assertThat(reminders.findSameVisit(ReminderChannel.EMAIL, 0, DAY.atTime(9, 0), mine.getId(), HANDED_OVER)).isEmpty();
        assertThat(reminders.findSameVisit(ReminderChannel.EMAIL, 42, null, mine.getId(), HANDED_OVER)).isEmpty();
    }

    /** What a booking's reminder did that keeps covering the visit, whatever happens to the booking after. */
    private static final java.util.Set<AppointmentReminderStatus> HANDED_OVER = java.util.EnumSet.of(
            AppointmentReminderStatus.SENT, AppointmentReminderStatus.UNCONFIRMED, AppointmentReminderStatus.SENDING);

    /** A reminder already sent for {@code start}, on a booking for {@code start}. */
    private AppointmentReminder sentFor(LocalDateTime start, Appointment appointment) {
        AppointmentReminder sent = reminder(appointment, ReminderChannel.EMAIL);
        sent.startSending(at(start.toLocalDate(), start.toLocalTime()), now);
        sent.finish(AppointmentReminderStatus.SENT, 900, now);
        reminders.merge(sent);
        entityManager.flush();
        return sent;
    }

    /**
     * A booking whose reminder was already sent for the visit still covers it after staff untick its box: the
     * patient must not get a second email when another booking's box is ticked instead.
     */
    @Test
    void shouldFindAReminderAlreadySentForTheVisit_afterItsBoxWasUnticked() {
        AppointmentReminder mine = reminder(appointment(DAY, LocalTime.of(9, 0)), ReminderChannel.EMAIL);
        AppointmentReminder unticked = sentFor(DAY.atTime(9, 0), appointment(DAY, LocalTime.of(9, 0)));
        unticked.withdraw("999998", now);
        reminders.merge(unticked);
        entityManager.flush();

        assertThat(reminders.findSameVisit(ReminderChannel.EMAIL, 42, DAY.atTime(9, 0), mine.getId(), HANDED_OVER))
                .extracting(found -> found.reminder().getId()).containsExactly(unticked.getId());
    }

    /** ...and after its booking moved to another time: it was sent for this one. */
    @Test
    void shouldFindAReminderAlreadySentForTheVisit_afterItsBookingMoved() {
        AppointmentReminder mine = reminder(appointment(DAY, LocalTime.of(9, 0)), ReminderChannel.EMAIL);
        Appointment moved = appointment(DAY, LocalTime.of(9, 0));
        AppointmentReminder sent = sentFor(DAY.atTime(9, 0), moved);
        moved.setStartTime(java.sql.Time.valueOf(LocalTime.of(11, 0)));
        moved.setEndTime(java.sql.Time.valueOf(LocalTime.of(11, 15)));
        entityManager.merge(moved);
        // A reminder sent for another start, or failed for this one, does not count from another time.
        reminder(appointment(DAY, LocalTime.of(10, 0)), ReminderChannel.EMAIL);
        AppointmentReminder failedElsewhere = reminder(appointment(DAY, LocalTime.of(10, 30)), ReminderChannel.EMAIL);
        failedElsewhere.startSending(at(DAY, LocalTime.of(9, 0)), now);
        failedElsewhere.finish(AppointmentReminderStatus.FAILED, null, now);
        reminders.merge(failedElsewhere);
        entityManager.flush();

        assertThat(reminders.findSameVisit(ReminderChannel.EMAIL, 42, DAY.atTime(9, 0), mine.getId(), HANDED_OVER))
                .extracting(found -> found.reminder().getId()).containsExactly(sent.getId());
    }

    @Test
    void shouldNotClaimAWithdrawnReminder() {
        AppointmentReminder withdrawn = reminder(appointment(DAY, LocalTime.of(9, 0)), ReminderChannel.EMAIL);
        withdrawn.withdraw("999998", now);
        reminders.merge(withdrawn);
        entityManager.flush();

        assertThat(reminders.claim(List.of(withdrawn.getId()), "FAKE-claim", now, now)).isEmpty();
        assertThat(reminders.claim(List.of(), "FAKE-claim", now, now)).isEmpty();
    }
}
