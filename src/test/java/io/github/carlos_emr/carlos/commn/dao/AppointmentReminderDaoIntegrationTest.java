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
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * #5887: the reminder table on H2, built from the entity. One row per appointment and channel; the due query
 * reads requested rows with their appointments by date; a claim takes only unclaimed (or abandoned) rows.
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

    @Test
    void shouldReadRequestedRowsWithTheirAppointments_byDateEarliestFirst() {
        Appointment late = appointment(DAY, LocalTime.of(15, 0));
        Appointment early = appointment(DAY, LocalTime.of(8, 30));
        Appointment nextWeek = appointment(DAY.plusDays(7), LocalTime.of(9, 0));
        Appointment withdrawnAppointment = appointment(DAY, LocalTime.of(10, 0));
        Appointment smsOnly = appointment(DAY, LocalTime.of(11, 0));
        AppointmentReminder lateReminder = reminder(late, ReminderChannel.EMAIL);
        AppointmentReminder earlyReminder = reminder(early, ReminderChannel.EMAIL);
        reminder(nextWeek, ReminderChannel.EMAIL);
        AppointmentReminder withdrawn = reminder(withdrawnAppointment, ReminderChannel.EMAIL);
        withdrawn.withdraw("999998", now);
        reminders.merge(withdrawn);
        reminder(smsOnly, ReminderChannel.SMS);
        entityManager.flush();

        List<ReminderCandidate> due = reminders.findRequestedBetween(ReminderChannel.EMAIL, DAY, DAY.plusDays(1), 10);

        assertThat(due).extracting(ReminderCandidate::reminder).containsExactly(earlyReminder, lateReminder);
        assertThat(due).extracting(candidate -> candidate.appointment().getId())
                .containsExactly(early.getId(), late.getId());
        assertThat(reminders.findRequestedBetween(ReminderChannel.EMAIL, DAY, DAY, 1)).hasSize(1);
        assertThat(reminders.findRequestedBetween(ReminderChannel.EMAIL, DAY.plusDays(1), DAY, 10)).isEmpty();
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
