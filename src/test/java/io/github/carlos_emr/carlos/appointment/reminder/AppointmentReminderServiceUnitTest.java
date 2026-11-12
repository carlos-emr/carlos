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

import io.github.carlos_emr.carlos.commn.dao.AppointmentReminderDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.AppointmentReminder;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionOperations;

import java.text.MessageFormat;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * #5887: the reminder service records the Add/Edit choice and sends what is due, once per appointment start.
 * The DAOs are mocked; the claim answers as the database would. FAKE patients only.
 */
@Tag("unit")
@Tag("appointment")
@DisplayName("Appointment reminder service")
class AppointmentReminderServiceUnitTest extends CarlosUnitTestBase {

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 12, 10, 0);
    private static final int APPOINTMENT = 101;
    private static final int PATIENT = 42;

    private final AppointmentReminderDao reminders = mock(AppointmentReminderDao.class);
    private final OscarAppointmentDao appointments = mock(OscarAppointmentDao.class);
    private final List<ClaimedReminder> handed = new ArrayList<>();
    private Function<ClaimedReminder, ReminderOutcome> channelAnswer = claimed -> new ReminderOutcome(AppointmentReminderStatus.SENT, 900);
    private AppointmentReminderService service;

    @BeforeEach
    void setUpService() {
        AppointmentReminderChannel email = new AppointmentReminderChannel() {
            @Override
            public ReminderChannel channel() {
                return ReminderChannel.EMAIL;
            }

            @Override
            public ReminderOutcome send(ClaimedReminder reminder) {
                handed.add(reminder);
                return channelAnswer.apply(reminder);
            }
        };
        service = new AppointmentReminderService(reminders, appointments, () -> List.of(email),
                TransactionOperations.withoutTransaction(), new ReminderWindow(Duration.ofHours(24), Duration.ofHours(2)),
                Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE));
    }

    private static Date at(LocalDateTime time) {
        return Date.from(time.atZone(ZONE).toInstant());
    }

    private static Appointment appointment(LocalDateTime start, int patient, String status) {
        Appointment appointment = new Appointment();
        appointment.setId(APPOINTMENT);
        appointment.setAppointmentDate(at(start));
        appointment.setStartTime(at(start));
        appointment.setDemographicNo(patient);
        appointment.setStatus(status);
        return appointment;
    }

    private static AppointmentReminder requested() {
        AppointmentReminder reminder = AppointmentReminder.requested(APPOINTMENT, ReminderChannel.EMAIL, "999998", at(NOW.minusDays(3)));
        ReflectionTestUtils.setField(reminder, "id", 7L);
        return reminder;
    }

    /** The DAOs as the database would answer: the candidate, the claim, and the row read back by id. */
    private void stored(AppointmentReminder reminder, Appointment candidate, Appointment atClaim) {
        when(reminders.findDue(eq(ReminderChannel.EMAIL), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(new ReminderCandidate(reminder, candidate)));
        when(reminders.claim(any(), anyString(), any(), any())).thenAnswer(call -> {
            Collection<Long> ids = call.getArgument(0);
            if (!ids.contains(reminder.getId())) {
                return List.of();
            }
            reminder.claim(call.getArgument(1), call.getArgument(2));
            return List.of(reminder);
        });
        when(appointments.find(Integer.valueOf(APPOINTMENT))).thenReturn(atClaim);
        when(reminders.find(Long.valueOf(7L))).thenReturn(reminder);
    }

    /**
     * Group Appt books the patient with several providers at the same time. Only one booking holds the reminder,
     * but another booking's box can be ticked later on Edit: the patient still gets one reminder for the visit.
     */
    @Nested
    @DisplayName("Bookings for the same visit")
    class SameVisit {

        private final LocalDateTime start = NOW.plusHours(20);

        private ReminderCandidate sibling(long id, String status, AppointmentReminderStatus outcome) {
            Appointment other = appointment(start, PATIENT, status);
            other.setId(APPOINTMENT + 1);
            AppointmentReminder reminder = AppointmentReminder.requested(APPOINTMENT + 1, ReminderChannel.EMAIL, "999998",
                    at(NOW.minusDays(1)));
            ReflectionTestUtils.setField(reminder, "id", id);
            if (outcome != null) {
                reminder.startSending(at(start), at(NOW.minusHours(1)));
                reminder.finish(outcome, 899, at(NOW.minusHours(1)));
            }
            return new ReminderCandidate(reminder, other);
        }

        private void siblings(ReminderCandidate... found) {
            when(reminders.findSameVisit(ReminderChannel.EMAIL, PATIENT, start, 7L)).thenReturn(List.of(found));
        }

        @Test
        void shouldRecordTheReminderAsCovered_whenAnotherBookingAtTheSameTimeHoldsAnEarlierReminder() {
            Appointment appointment = appointment(start, PATIENT, "t");
            AppointmentReminder reminder = requested();
            stored(reminder, appointment, appointment);
            siblings(sibling(3L, "t", null));

            ReminderRun run = service.processDue(10);

            assertThat(handed).as("never sent twice for one visit").isEmpty();
            assertThat(run).isEqualTo(new ReminderRun(1, 0, 1));
            assertThat(reminder.getLastStatus()).isEqualTo(AppointmentReminderStatus.DUPLICATE);
            assertThat(reminder.getSentFor()).as("not due again for this start").isEqualTo(at(start));
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"SENT", "UNCONFIRMED"})
        void shouldRecordTheReminderAsCovered_whenAnotherBookingWasAlreadyRemindedForTheStart(String outcome) {
            Appointment appointment = appointment(start, PATIENT, "t");
            AppointmentReminder reminder = requested();
            stored(reminder, appointment, appointment);
            siblings(sibling(12L, "t", AppointmentReminderStatus.valueOf(outcome)));

            service.processDue(10);

            assertThat(handed).isEmpty();
            assertThat(reminder.getLastStatus()).isEqualTo(AppointmentReminderStatus.DUPLICATE);
        }

        @Test
        void shouldSend_whenTheOtherBookingIsLaterAndNotYetRemindedOrCancelled() {
            Appointment appointment = appointment(start, PATIENT, "t");
            AppointmentReminder reminder = requested();
            stored(reminder, appointment, appointment);
            siblings(sibling(12L, "t", null), sibling(3L, "C", null), sibling(14L, "t", AppointmentReminderStatus.BLOCKED_CONSENT));

            service.processDue(10);

            // A later booking waits for this one; a cancelled booking never covers it; a refusal is no reminder.
            assertThat(handed).hasSize(1);
            assertThat(reminder.getLastStatus()).isEqualTo(AppointmentReminderStatus.SENT);
        }

        @Test
        void shouldSendAndWarn_whenTheSameVisitCannotBeChecked() {
            Appointment appointment = appointment(start, PATIENT, "t");
            AppointmentReminder reminder = requested();
            stored(reminder, appointment, appointment);
            when(reminders.findSameVisit(any(), anyInt(), any(), anyLong())).thenThrow(new IllegalStateException("FAKE"));

            try (LogCapture log = LogCapture.forLogger(AppointmentReminderService.class)) {
                service.processDue(10);

                // A second email is the lesser harm: the patient is still reminded, and the log says why.
                assertThat(handed).hasSize(1);
                assertThat(log.events()).filteredOn(event -> event.getLevel() == org.apache.logging.log4j.Level.WARN)
                        .extracting(event -> event.getMessage().getFormattedMessage())
                        .singleElement().satisfies(message -> assertThat(message)
                                .contains("Appointment reminder 7 could not be checked for the same visit")
                                .doesNotContain(String.valueOf(PATIENT)).doesNotContain(String.valueOf(APPOINTMENT))
                                .doesNotContain("FAKE"));
            }
        }

        @Test
        void shouldSayAnotherBookingCarriesTheReminder_onTheStatusLine() {
            AppointmentReminder reminder = requested();
            reminder.startSending(at(start), at(NOW.minusMinutes(5)));
            reminder.finish(AppointmentReminderStatus.DUPLICATE, null, at(NOW.minusMinutes(5)));
            when(reminders.findByAppointment(APPOINTMENT, ReminderChannel.EMAIL)).thenReturn(Optional.of(reminder));

            assertThat(service.statusLine(appointment(start, PATIENT, "t"), Locale.ENGLISH))
                    .contains(new ReminderStatusLine("appointment.reminder.status.duplicate", ""));
        }
    }

    @Nested
    @DisplayName("Sending what is due")
    class Sending {

        @Test
        void shouldSendOnceAndRecordTheEmailLog_whenTheAppointmentIsDue() {
            LocalDateTime start = NOW.plusHours(20);
            Appointment appointment = appointment(start, PATIENT, "t");
            AppointmentReminder reminder = requested();
            stored(reminder, appointment, appointment);

            ReminderRun run = service.processDue(10);

            assertThat(run).isEqualTo(new ReminderRun(1, 1, 0));
            assertThat(handed).containsExactly(new ClaimedReminder(7L, APPOINTMENT, PATIENT, start));
            assertThat(reminder.getLastStatus()).isEqualTo(AppointmentReminderStatus.SENT);
            assertThat(reminder.getLastEmailLogId()).isEqualTo(900);
            assertThat(reminder.getSentFor()).isEqualTo(at(start));
            assertThat(reminder.getAttempts()).isEqualTo(1);
            assertThat(reminder.getClaimToken()).as("the claim is released").isNull();
        }

        @Test
        void shouldNotSendAgain_forTheStartAlreadyReminded() {
            LocalDateTime start = NOW.plusHours(20);
            Appointment appointment = appointment(start, PATIENT, "t");
            AppointmentReminder reminder = requested();
            reminder.startSending(at(start), at(NOW.minusHours(1)));
            reminder.finish(AppointmentReminderStatus.SENT, 899, at(NOW.minusHours(1)));
            stored(reminder, appointment, appointment);

            assertThat(service.processDue(10)).isEqualTo(ReminderRun.NONE);
            assertThat(handed).isEmpty();
            verify(reminders, never()).claim(any(), anyString(), any(), any());
        }

        @Test
        void shouldRemindAgain_whenTheAppointmentMovedAfterItsReminder() {
            LocalDateTime oldStart = NOW.plusHours(4);
            LocalDateTime newStart = NOW.plusHours(22);
            AppointmentReminder reminder = requested();
            reminder.startSending(at(oldStart), at(NOW.minusHours(2)));
            reminder.finish(AppointmentReminderStatus.SENT, 899, at(NOW.minusHours(2)));
            Appointment moved = appointment(newStart, PATIENT, "t");
            stored(reminder, moved, moved);

            service.processDue(10);

            assertThat(handed).extracting(ClaimedReminder::start).containsExactly(newStart);
            assertThat(reminder.getSentFor()).isEqualTo(at(newStart));
            assertThat(reminder.getAttempts()).as("attempts count per start").isEqualTo(1);
        }

        @ParameterizedTest(name = "status {0}")
        @ValueSource(strings = {"C", "CS", "CV", "N", "NS"})
        void shouldNotRemind_whenTheAppointmentIsCancelledOrANoShow(String status) {
            Appointment appointment = appointment(NOW.plusHours(20), PATIENT, status);
            stored(requested(), appointment, appointment);

            service.processDue(10);

            assertThat(handed).isEmpty();
        }

        @Test
        void shouldNotRemind_whenTheAppointmentHasNoPatient() {
            Appointment appointment = appointment(NOW.plusHours(20), 0, "t");
            stored(requested(), appointment, appointment);

            service.processDue(10);

            assertThat(handed).isEmpty();
        }

        @ParameterizedTest(name = "{0} hours ahead")
        @ValueSource(ints = {25, 2, 1, -1})
        void shouldNotRemind_outsideTheWindow(int hoursAhead) {
            Appointment appointment = appointment(NOW.plusHours(hoursAhead), PATIENT, "t");
            stored(requested(), appointment, appointment);

            service.processDue(10);

            assertThat(handed).isEmpty();
        }

        @Test
        void shouldReleaseTheClaimWithoutSending_whenTheAppointmentWasCancelledMeanwhile() {
            AppointmentReminder reminder = requested();
            stored(reminder, appointment(NOW.plusHours(20), PATIENT, "t"), appointment(NOW.plusHours(20), PATIENT, "C"));

            service.processDue(10);

            assertThat(handed).isEmpty();
            assertThat(reminder.getClaimToken()).isNull();
            assertThat(reminder.getSentFor()).as("nothing was attempted").isNull();
        }

        @Test
        void shouldRecordUnconfirmedAndCarryOn_whenTheChannelThrows() {
            channelAnswer = claimed -> {
                throw new IllegalStateException("FAKE transport failure");
            };
            Appointment appointment = appointment(NOW.plusHours(20), PATIENT, "t");
            AppointmentReminder reminder = requested();
            stored(reminder, appointment, appointment);

            ReminderRun run = service.processDue(10);

            assertThat(run).isEqualTo(new ReminderRun(1, 0, 1));
            assertThat(reminder.getLastStatus()).isEqualTo(AppointmentReminderStatus.UNCONFIRMED);
        }

        @Test
        void shouldSendNothing_forABatchOfZero() {
            assertThat(service.processDue(0)).isEqualTo(ReminderRun.NONE);
            verify(reminders, never()).findDue(any(), any(), any(), anyInt(), anyInt());
        }

        @Test
        void shouldAskOnlyForStartsInsideTheWindow_notYetRemindedOrDueTheirRetry() {
            service.processDue(10);

            verify(reminders).findDue(ReminderChannel.EMAIL, NOW.plusHours(2), NOW.plusHours(24),
                    AppointmentReminderService.MAX_ATTEMPTS, AppointmentReminderService.MAX_CANDIDATES);
        }
    }

    /**
     * M1 of the review: a run that fails or stops partway strands at most the reminder it was sending, and never
     * sends a start twice. The table is a small in-memory stand-in that claims and finds as the database does.
     */
    @Nested
    @DisplayName("A failure or a stop partway through a run")
    class PartwayThrough {

        private final Map<Long, AppointmentReminder> rows = new LinkedHashMap<>();
        private final Map<Integer, Appointment> booked = new HashMap<>();
        private final Map<Object, Integer> reads = new HashMap<>();
        private Long failReadsOf;
        private int failFromRead;
        private Integer failAppointmentReadsOf;

        @BeforeEach
        void table() {
            when(reminders.findDue(eq(ReminderChannel.EMAIL), any(), any(), anyInt(), anyInt())).thenAnswer(call ->
                    rows.values().stream()
                            .map(reminder -> new ReminderCandidate(reminder, booked.get(reminder.getAppointmentNo())))
                            .toList());
            when(reminders.claim(any(), anyString(), any(), any())).thenAnswer(call -> {
                Collection<Long> ids = call.getArgument(0);
                Date staleBefore = call.getArgument(3);
                List<AppointmentReminder> claimed = new ArrayList<>();
                for (Long id : ids) {
                    AppointmentReminder reminder = rows.get(id);
                    if (reminder != null && reminder.isRequested()
                            && (reminder.getClaimToken() == null || reminder.getClaimedAt().before(staleBefore))) {
                        reminder.claim(call.getArgument(1), call.getArgument(2));
                        claimed.add(reminder);
                    }
                }
                return claimed;
            });
            when(reminders.find(any(Long.class))).thenAnswer(call -> {
                Long id = call.getArgument(0);
                int read = reads.merge(id, 1, Integer::sum);
                if (id.equals(failReadsOf) && read >= failFromRead) {
                    throw new IllegalStateException("FAKE database failure");
                }
                return rows.get(id);
            });
            when(appointments.find(any(Integer.class))).thenAnswer(call -> {
                Integer number = call.getArgument(0);
                int read = reads.merge("appointment " + number, 1, Integer::sum);
                if (number.equals(failAppointmentReadsOf) && read == 2) {
                    throw new IllegalStateException("FAKE database failure");
                }
                return booked.get(number);
            });
            when(reminders.findAbandonedSending(eq(ReminderChannel.EMAIL), any())).thenAnswer(call -> {
                Date staleBefore = call.getArgument(1);
                return rows.values().stream()
                        .filter(reminder -> reminder.getLastStatus() == AppointmentReminderStatus.SENDING
                                && (reminder.getClaimToken() == null || reminder.getClaimedAt().before(staleBefore)))
                        .toList();
            });
        }

        private AppointmentReminder row(long id, LocalDateTime start) {
            int appointmentNo = (int) id + 100;
            AppointmentReminder reminder = AppointmentReminder.requested(appointmentNo, ReminderChannel.EMAIL, "999998",
                    at(NOW.minusDays(3)));
            ReflectionTestUtils.setField(reminder, "id", id);
            Appointment appointment = appointment(start, PATIENT, "t");
            appointment.setId(appointmentNo);
            rows.put(id, reminder);
            booked.put(appointmentNo, appointment);
            return reminder;
        }

        /** The same service, its clock at {@code now}: a later run. */
        private AppointmentReminderService runAt(LocalDateTime now) {
            AppointmentReminderChannel email = new AppointmentReminderChannel() {
                @Override
                public ReminderChannel channel() {
                    return ReminderChannel.EMAIL;
                }

                @Override
                public ReminderOutcome send(ClaimedReminder reminder) {
                    handed.add(reminder);
                    return channelAnswer.apply(reminder);
                }
            };
            return new AppointmentReminderService(reminders, appointments, () -> List.of(email),
                    TransactionOperations.withoutTransaction(), new ReminderWindow(Duration.ofHours(24), Duration.ofHours(2)),
                    Clock.fixed(now.atZone(ZONE).toInstant(), ZONE));
        }

        @Test
        void shouldSendTheRest_andRecordTheStrandedOneAsUnconfirmedLater_whenRecordingAnOutcomeFails() {
            AppointmentReminder first = row(7L, NOW.plusHours(20));
            AppointmentReminder second = row(8L, NOW.plusHours(21));
            // The first read is the start; the second and third are the outcome and its one retry.
            failReadsOf = 7L;
            failFromRead = 2;

            ReminderRun run = service.processDue(10);

            assertThat(run).isEqualTo(new ReminderRun(2, 2, 0));
            assertThat(handed).extracting(ClaimedReminder::reminderId).containsExactly(7L, 8L);
            assertThat(second.getLastStatus()).isEqualTo(AppointmentReminderStatus.SENT);
            assertThat(second.getClaimToken()).isNull();
            assertThat(first.getLastStatus()).as("only the reminder being sent is stranded")
                    .isEqualTo(AppointmentReminderStatus.SENDING);

            failReadsOf = null;
            runAt(NOW.plus(AppointmentReminderService.STALE_CLAIM).plusMinutes(1)).processDue(10);

            assertThat(first.getLastStatus()).as("it may have gone: unconfirmed")
                    .isEqualTo(AppointmentReminderStatus.UNCONFIRMED);
            assertThat(first.getClaimToken()).isNull();
            assertThat(handed).as("never sent twice").extracting(ClaimedReminder::reminderId).containsExactly(7L, 8L);
        }

        @Test
        void shouldSendTheRest_andLeaveTheOneThatCouldNotStartToALaterRun() {
            AppointmentReminder first = row(7L, NOW.plusHours(20));
            AppointmentReminder second = row(8L, NOW.plusHours(21));
            // Read once by the claim, then again to start it: that second read fails.
            failAppointmentReadsOf = 107;

            ReminderRun run = service.processDue(10);

            assertThat(run).isEqualTo(new ReminderRun(2, 1, 0));
            assertThat(handed).extracting(ClaimedReminder::reminderId).containsExactly(8L);
            assertThat(second.getLastStatus()).isEqualTo(AppointmentReminderStatus.SENT);
            assertThat(first.getLastStatus()).as("not marked, so not stranded").isNull();
            assertThat(first.getSentFor()).isNull();

            runAt(NOW.plus(AppointmentReminderService.STALE_CLAIM).plusMinutes(1)).processDue(10);

            assertThat(handed).extracting(ClaimedReminder::reminderId).containsExactly(8L, 7L);
            assertThat(first.getLastStatus()).isEqualTo(AppointmentReminderStatus.SENT);
            assertThat(first.getAttempts()).isEqualTo(1);
        }

        @Test
        void shouldStopBetweenReminders_andReleaseWhatItDidNotStart_whenInterrupted() {
            AppointmentReminder first = row(7L, NOW.plusHours(20));
            AppointmentReminder second = row(8L, NOW.plusHours(21));
            AppointmentReminder third = row(9L, NOW.plusHours(22));
            channelAnswer = claimed -> {
                // The scheduler stopping while the first reminder is sent.
                Thread.currentThread().interrupt();
                return new ReminderOutcome(AppointmentReminderStatus.SENT, 900);
            };
            try {
                ReminderRun run = service.processDue(10);

                assertThat(run).isEqualTo(new ReminderRun(3, 1, 0));
            } finally {
                assertThat(Thread.interrupted()).as("the interrupt is kept for the scheduler").isTrue();
            }
            assertThat(handed).extracting(ClaimedReminder::reminderId).containsExactly(7L);
            assertThat(first.getLastStatus()).isEqualTo(AppointmentReminderStatus.SENT);
            for (AppointmentReminder released : List.of(second, third)) {
                assertThat(released.getClaimToken()).as("released for the next run").isNull();
                assertThat(released.getLastStatus()).as("never marked as being sent").isNull();
            }
        }

        @Test
        void shouldRecordAnAbandonedSendAsUnconfirmed_andLeaveALiveOneToItsRun() {
            LocalDateTime start = NOW.plusHours(20);
            AppointmentReminder abandoned = row(7L, start);
            abandoned.claim("FAKE-stopped-run", at(NOW.minus(AppointmentReminderService.STALE_CLAIM).minusMinutes(1)));
            abandoned.startSending(at(start), at(NOW.minusMinutes(16)));
            AppointmentReminder live = row(8L, NOW.plusHours(21));
            live.claim("FAKE-running", at(NOW.minusMinutes(1)));
            live.startSending(at(NOW.plusHours(21)), at(NOW.minusMinutes(1)));

            ReminderRun run = service.processDue(10);

            assertThat(run).isEqualTo(ReminderRun.NONE);
            assertThat(handed).as("neither is sent again").isEmpty();
            assertThat(abandoned.getLastStatus()).isEqualTo(AppointmentReminderStatus.UNCONFIRMED);
            assertThat(abandoned.getClaimToken()).isNull();
            assertThat(live.getLastStatus()).isEqualTo(AppointmentReminderStatus.SENDING);
            assertThat(live.getClaimToken()).isEqualTo("FAKE-running");
        }

        @Test
        void shouldLeaveASlowBatchsLaterSend_toItsRun() {
            // Claimed with a batch 20 minutes ago, but its own send started a minute ago: not abandoned.
            LocalDateTime start = NOW.plusHours(20);
            AppointmentReminder slow = row(7L, start);
            slow.claim("FAKE-slow-batch", at(NOW.minusMinutes(20)));
            slow.startSending(at(start), at(NOW.minusMinutes(1)));

            service.processDue(10);

            assertThat(handed).isEmpty();
            assertThat(slow.getLastStatus()).isEqualTo(AppointmentReminderStatus.SENDING);
            assertThat(slow.getClaimToken()).isEqualTo("FAKE-slow-batch");
            assertThat(slow.getClaimedAt()).as("renewed when its send started").isEqualTo(at(NOW.minusMinutes(1)));
        }

        @Test
        void shouldNotSend_whenTheBoxWasUntickedAfterTheClaim() {
            AppointmentReminder reminder = row(7L, NOW.plusHours(20));
            channelAnswer = claimed -> new ReminderOutcome(AppointmentReminderStatus.SENT, 900);
            when(appointments.find(any(Integer.class))).thenAnswer(call -> {
                // Staff untick the box between the claim and the send.
                reminder.withdraw("999998", at(NOW));
                return booked.get(call.<Integer>getArgument(0));
            });

            service.processDue(10);

            assertThat(handed).isEmpty();
            assertThat(reminder.getClaimToken()).isNull();
            assertThat(reminder.getLastStatus()).isNull();
        }
    }

    /** One retry (#5887), for a definite transport failure only. */
    @Nested
    @DisplayName("One retry after a definite failure")
    class Retry {

        private AppointmentReminder attempted(LocalDateTime start, AppointmentReminderStatus status, LocalDateTime when) {
            AppointmentReminder reminder = requested();
            reminder.startSending(at(start), at(when));
            reminder.finish(status, null, at(when));
            return reminder;
        }

        @Test
        void shouldRetryOnce_halfAnHourAfterADefiniteFailure() {
            LocalDateTime start = NOW.plusHours(20);
            Appointment appointment = appointment(start, PATIENT, "t");
            AppointmentReminder reminder = attempted(start, AppointmentReminderStatus.FAILED, NOW.minus(AppointmentReminderService.RETRY_AFTER));
            stored(reminder, appointment, appointment);

            service.processDue(10);

            assertThat(handed).hasSize(1);
            assertThat(reminder.getAttempts()).isEqualTo(2);
            assertThat(reminder.getLastStatus()).isEqualTo(AppointmentReminderStatus.SENT);
        }

        @Test
        void shouldNotRetry_beforeHalfAnHourHasPassed() {
            LocalDateTime start = NOW.plusHours(20);
            Appointment appointment = appointment(start, PATIENT, "t");
            stored(attempted(start, AppointmentReminderStatus.FAILED, NOW.minusMinutes(29)), appointment, appointment);

            service.processDue(10);

            assertThat(handed).isEmpty();
        }

        @Test
        void shouldNotRetryASecondTime_afterTwoFailures() {
            LocalDateTime start = NOW.plusHours(20);
            Appointment appointment = appointment(start, PATIENT, "t");
            AppointmentReminder reminder = attempted(start, AppointmentReminderStatus.FAILED, NOW.minusHours(2));
            reminder.startSending(at(start), at(NOW.minusHours(1)));
            reminder.finish(AppointmentReminderStatus.FAILED, null, at(NOW.minusHours(1)));
            stored(reminder, appointment, appointment);

            service.processDue(10);

            assertThat(handed).isEmpty();
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"UNCONFIRMED", "BLOCKED_CONSENT", "NO_ADDRESS", "INVALID_ADDRESS", "NO_SENDER",
                "NO_FOOTER", "NOT_PERMITTED", "SENDING", "SENT"})
        void shouldNeverRetry_aRefusalOrAnUnconfirmedSend(String status) {
            LocalDateTime start = NOW.plusHours(20);
            Appointment appointment = appointment(start, PATIENT, "t");
            AppointmentReminder reminder = requested();
            reminder.startSending(at(start), at(NOW.minusHours(3)));
            if (!"SENDING".equals(status)) {
                reminder.finish(AppointmentReminderStatus.valueOf(status), null, at(NOW.minusHours(3)));
            }
            stored(reminder, appointment, appointment);

            service.processDue(10);

            assertThat(handed).isEmpty();
        }
    }

    @Nested
    @DisplayName("Recording the Add/Edit choice")
    class Choice {

        private final LoggedInInfo user = mock(LoggedInInfo.class);

        @BeforeEach
        void setUpUser() {
            when(user.getLoggedInProviderNo()).thenReturn("999998");
        }

        @Test
        void shouldStoreANewRequest_whenTheBoxIsTicked() {
            when(reminders.findByAppointment(APPOINTMENT, ReminderChannel.EMAIL)).thenReturn(Optional.empty());

            assertThat(service.recordRequest(user, APPOINTMENT, PATIENT, ReminderChannel.EMAIL, true)).isTrue();

            verify(reminders).persist(any(AppointmentReminder.class));
        }

        @Test
        void shouldStoreNothing_whenANewBookingLeavesTheBoxUnticked() {
            when(reminders.findByAppointment(APPOINTMENT, ReminderChannel.EMAIL)).thenReturn(Optional.empty());

            assertThat(service.recordRequest(user, APPOINTMENT, PATIENT, ReminderChannel.EMAIL, false)).isFalse();

            verify(reminders, never()).persist(any());
            verify(reminders, never()).merge(any());
        }

        @Test
        void shouldWithdrawAndKeepTheRow_whenTheBoxIsUntickedOnEdit() {
            AppointmentReminder reminder = requested();
            when(reminders.findByAppointment(APPOINTMENT, ReminderChannel.EMAIL)).thenReturn(Optional.of(reminder));

            assertThat(service.recordRequest(user, APPOINTMENT, PATIENT, ReminderChannel.EMAIL, false)).isTrue();

            assertThat(reminder.isRequested()).isFalse();
            assertThat(reminder.getUpdatedBy()).isEqualTo("999998");
            verify(reminders).merge(reminder);
            verify(reminders, never()).persist(any());
        }

        @Test
        void shouldChangeNothing_whenTheChoiceIsUnchanged() {
            when(reminders.findByAppointment(APPOINTMENT, ReminderChannel.EMAIL)).thenReturn(Optional.of(requested()));

            assertThat(service.recordRequest(user, APPOINTMENT, PATIENT, ReminderChannel.EMAIL, true)).isFalse();

            verify(reminders, never()).merge(any());
        }

        /** Group Update deletes the appointment and books it again under a new number. */
        @Test
        void shouldMoveTheReminderWithItsRemindedStart_whenGroupUpdateRebooksTheAppointment() {
            AppointmentReminder reminder = requested();
            reminder.startSending(at(NOW.plusHours(20)), at(NOW));
            when(reminders.findByAppointment(APPOINTMENT, ReminderChannel.EMAIL)).thenReturn(Optional.of(reminder));
            when(reminders.findByAppointment(201, ReminderChannel.EMAIL)).thenReturn(Optional.empty());

            assertThat(service.moveReminders(user, APPOINTMENT, 201)).isEqualTo(1);

            assertThat(reminder.getAppointmentNo()).isEqualTo(201);
            assertThat(reminder.isRequested()).isTrue();
            assertThat(reminder.getSentFor()).as("the same start is not reminded twice").isEqualTo(at(NOW.plusHours(20)));
            assertThat(reminder.getUpdatedBy()).isEqualTo("999998");
            verify(reminders).merge(reminder);
        }

        @Test
        void shouldMoveNothing_whenTheNewNumberHasItsOwnReminderOrTheNumbersAreNotTwo() {
            when(reminders.findByAppointment(APPOINTMENT, ReminderChannel.EMAIL)).thenReturn(Optional.of(requested()));
            when(reminders.findByAppointment(201, ReminderChannel.EMAIL)).thenReturn(Optional.of(requested()));

            assertThat(service.moveReminders(user, APPOINTMENT, 201)).isZero();
            assertThat(service.moveReminders(user, APPOINTMENT, APPOINTMENT)).isZero();
            assertThat(service.moveReminders(user, 0, 201)).isZero();
            assertThat(service.moveReminders(user, 202, 203)).as("nothing to move").isZero();

            verify(reminders, never()).merge(any());
        }

        @Test
        void shouldReadTheStoredChoice_forTheEditPage() {
            AppointmentReminder withdrawn = requested();
            withdrawn.withdraw("999998", at(NOW));
            when(reminders.findByAppointment(APPOINTMENT, ReminderChannel.EMAIL)).thenReturn(Optional.of(requested()));
            when(reminders.findByAppointment(102, ReminderChannel.EMAIL)).thenReturn(Optional.of(withdrawn));
            when(reminders.findByAppointment(103, ReminderChannel.EMAIL)).thenReturn(Optional.empty());

            assertThat(service.isRequested(APPOINTMENT, ReminderChannel.EMAIL)).isTrue();
            assertThat(service.isRequested(102, ReminderChannel.EMAIL)).isFalse();
            assertThat(service.isRequested(103, ReminderChannel.EMAIL)).isFalse();
        }
    }

    /** The line under the box on Edit Appointment. */
    @Nested
    @DisplayName("Status line on Edit")
    class StatusLine {

        private final DateTimeFormatter shown = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                .withLocale(Locale.ENGLISH);

        private Optional<ReminderStatusLine> line(Appointment appointment, AppointmentReminder reminder) {
            when(reminders.findByAppointment(APPOINTMENT, ReminderChannel.EMAIL)).thenReturn(Optional.ofNullable(reminder));
            return service.statusLine(appointment, Locale.ENGLISH);
        }

        @Test
        void shouldSayNothing_withoutARequestedReminder() {
            Appointment appointment = appointment(NOW.plusHours(20), PATIENT, "t");
            AppointmentReminder withdrawn = requested();
            withdrawn.withdraw("999998", at(NOW));

            assertThat(line(appointment, null)).isEmpty();
            assertThat(line(appointment, withdrawn)).isEmpty();
            assertThat(line(appointment(NOW.plusHours(20), PATIENT, "C"), requested())).isEmpty();
            assertThat(line(appointment(NOW.minusHours(1), PATIENT, "t"), requested())).as("already started").isEmpty();
        }

        @Test
        void shouldSayWhenItWillGo_beforeTheWindowOpens() {
            LocalDateTime start = NOW.plusDays(3);

            assertThat(line(appointment(start, PATIENT, "t"), requested()))
                    .contains(new ReminderStatusLine("appointment.reminder.status.scheduled", shown.format(start.minusHours(24))));
        }

        @Test
        void shouldSayItGoesSoon_insideTheWindow_andTooLate_afterTheCutoff() {
            assertThat(line(appointment(NOW.plusHours(20), PATIENT, "t"), requested()))
                    .contains(new ReminderStatusLine("appointment.reminder.status.due", ""));
            assertThat(line(appointment(NOW.plusHours(1), PATIENT, "t"), requested()))
                    .contains(new ReminderStatusLine("appointment.reminder.status.tooLate", ""));
        }

        @Test
        void shouldSayWhenItWasSent_forTheCurrentStart() {
            LocalDateTime start = NOW.plusHours(20);
            AppointmentReminder reminder = requested();
            reminder.startSending(at(start), at(NOW.minusHours(1)));
            reminder.finish(AppointmentReminderStatus.SENT, 900, at(NOW.minusHours(1)));

            assertThat(line(appointment(start, PATIENT, "t"), reminder))
                    .contains(new ReminderStatusLine("appointment.reminder.status.sent", shown.format(NOW.minusHours(1))));
        }

        @Test
        void shouldIgnoreAReminderSentForAnEarlierStart_afterTheAppointmentMoved() {
            AppointmentReminder reminder = requested();
            reminder.startSending(at(NOW.plusHours(3)), at(NOW.minusHours(1)));
            reminder.finish(AppointmentReminderStatus.SENT, 900, at(NOW.minusHours(1)));

            assertThat(line(appointment(NOW.plusDays(3), PATIENT, "t"), reminder)).map(ReminderStatusLine::messageKey)
                    .contains("appointment.reminder.status.scheduled");
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"SENDING", "UNCONFIRMED", "FAILED", "BLOCKED_CONSENT", "NO_ADDRESS", "INVALID_ADDRESS",
                "NO_SENDER", "NO_FOOTER", "NOT_PERMITTED"})
        void shouldNameWhatHappened_forEveryOutcome(String status) {
            LocalDateTime start = NOW.plusHours(20);
            AppointmentReminder reminder = requested();
            reminder.startSending(at(start), at(NOW.minusMinutes(10)));
            if (!"SENDING".equals(status)) {
                reminder.finish(AppointmentReminderStatus.valueOf(status), null, at(NOW.minusMinutes(10)));
            }
            String expected = switch (status) {
                case "SENDING", "UNCONFIRMED" -> "appointment.reminder.status.unconfirmed";
                case "FAILED" -> "appointment.reminder.status.failed";
                case "BLOCKED_CONSENT" -> "appointment.reminder.status.blockedConsent";
                case "NO_ADDRESS", "INVALID_ADDRESS" -> "appointment.reminder.status.noAddress";
                case "NO_SENDER", "NO_FOOTER" -> "appointment.reminder.status.notSetUp";
                default -> "appointment.reminder.status.notPermitted";
            };

            assertThat(line(appointment(start, PATIENT, "t"), reminder)).map(ReminderStatusLine::messageKey).contains(expected);
        }

        /**
         * The line is shown to anyone who may open the appointment (#5887), so it carries a time or nothing:
         * never an address, an email's id or text, or an error. Every outcome is checked, a stored email included.
         */
        @ParameterizedTest(name = "{0}")
        @org.junit.jupiter.params.provider.EnumSource(AppointmentReminderStatus.class)
        void shouldCarryOnlyATime_forEveryOutcome(AppointmentReminderStatus status) {
            LocalDateTime start = NOW.plusHours(20);
            AppointmentReminder reminder = requested();
            reminder.startSending(at(start), at(NOW.minusMinutes(10)));
            if (status != AppointmentReminderStatus.SENDING) {
                reminder.finish(status, 900, at(NOW.minusMinutes(10)));
            }

            Optional<ReminderStatusLine> shownLine = line(appointment(start, PATIENT, "t"), reminder);

            assertThat(shownLine).hasValueSatisfying(found -> {
                assertThat(found.messageKey()).startsWith("appointment.reminder.status.").doesNotContain("900");
                assertThat(found.argument()).isIn("", shown.format(NOW.minusMinutes(10)));
            });
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"en", "fr", "es", "pl", "pt_BR"})
        void shouldHaveEveryStatusLine_inEveryBundle(String language) {
            ResourceBundle bundle = ResourceBundle.getBundle("oscarResources", Locale.forLanguageTag(language.replace('_', '-')));
            for (String key : new String[]{"sent", "unconfirmed", "failed", "blockedConsent", "noAddress", "notSetUp",
                    "notPermitted", "scheduled", "due", "tooLate", "duplicate"}) {
                String pattern = bundle.getString("appointment.reminder.status." + key);
                // The page always passes the value, so every line is a MessageFormat pattern.
                assertThat(new MessageFormat(pattern).format(new Object[]{"FAKE-WHEN"})).as(key).doesNotContain("{", "}");
            }
        }
    }

    @Test
    void shouldTreatOnlyCancelledAndNoShowStatusesAsNotToRemind() {
        assertThat(AppointmentReminderService.isCancelledOrNoShow("t")).isFalse();
        assertThat(AppointmentReminderService.isCancelledOrNoShow("H")).isFalse();
        assertThat(AppointmentReminderService.isCancelledOrNoShow(null)).isFalse();
        assertThat(AppointmentReminderService.isCancelledOrNoShow("C")).isTrue();
        assertThat(AppointmentReminderService.isCancelledOrNoShow("N")).isTrue();
    }
}
