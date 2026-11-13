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
package io.github.carlos_emr.carlos.integration.patientportal.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.SecurityDao;
import io.github.carlos_emr.carlos.commn.model.OscarLog;
import io.github.carlos_emr.carlos.commn.model.PortalBookingOffer;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalBookingChoiceDto;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalException;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalOfferedSlot;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalService;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalSettings;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalStaffContext;
import io.github.carlos_emr.carlos.integration.patientportal.PortalJob;
import io.github.carlos_emr.carlos.integration.patientportal.PortalJobAssertionSigner;
import io.github.carlos_emr.carlos.integration.patientportal.PortalJobKey;
import io.github.carlos_emr.carlos.integration.patientportal.PortalSecret;
import io.github.carlos_emr.carlos.integration.patientportal.PortalTestKeys;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * The polling job signs as the booking-sync job, answers every pick once, undoes bookings the
 * portal will never show, and retries safely (#3850).
 */
@Tag("unit")
@Tag("patient-portal")
class PortalBookingSyncServiceUnitTest {
    private static final PortalBookingSettings SETTINGS = new PortalBookingSettings(
            Set.of('B'), 24, "in_person", null, true, 60);
    private static final String KID = "booking-sync-20261020-a1b2c3";
    private static final PortalJobAssertionSigner JOB = PortalJobAssertionSigner.from(new PortalJobKey(
            PortalJob.BOOKING_SYNC, PortalSecret.of(PortalTestKeys.JOB_PRIVATE_KEY), KID));
    private static final PatientPortalBookingChoiceDto CHOICE = new PatientPortalBookingChoiceDto(
            7, 11, 123, "slot-a", Instant.parse("2026-10-08T15:00:00Z"));

    private final PortalBookingChoiceService bookings = mock(PortalBookingChoiceService.class);
    private final PortalBookingOfferService offers = mock(PortalBookingOfferService.class);
    private final PatientPortalService portal = mock(PatientPortalService.class);
    private final PortalBookingSyncService sync = new PortalBookingSyncService(bookings, offers, () -> portal);

    private void outcome(PortalBookingChoiceService.Outcome outcome, PortalBookingOffer offer) {
        when(bookings.book(CHOICE, KID, SETTINGS)).thenReturn(new PortalBookingChoiceService.Result(outcome, offer));
    }

    private static Map<String, String> properties(String privateKey, String keyId) {
        Map<String, String> properties = new HashMap<>();
        properties.put(PatientPortalSettings.STAFF_ASSERTION_KEY, PortalTestKeys.PRIVATE_KEY);
        properties.put(PatientPortalSettings.STAFF_ASSERTION_KEY_ID, "primary");
        if (privateKey != null) {
            properties.put("patient_portal.job.booking-sync.private_key", privateKey);
        }
        if (keyId != null) {
            properties.put("patient_portal.job.booking-sync.key_id", keyId);
        }
        return properties;
    }

    private static Function<PortalJob, Optional<PortalJobKey>> keys(Map<String, String> properties) {
        return job -> PortalJobKey.fromProperties(job, properties::get);
    }

    private static List<LogEvent> at(LogCapture capture, Level level) {
        return capture.events().stream().filter(event -> event.getLevel() == level).toList();
    }

    @Test
    void shouldReportBooked_whenThePickWasBooked() {
        outcome(PortalBookingChoiceService.Outcome.BOOKED, new PortalBookingOffer());
        assertThat(sync.answer(portal, CHOICE, JOB, SETTINGS)).isTrue();
        verify(portal).recordBookingChoiceResult(7, 11, true, List.of(), JOB);
        verify(bookings).confirm(CHOICE);
    }

    @Test
    void shouldSignAsBookingSync_withoutAStaffContextOrAProvider() throws Exception {
        when(portal.listPendingBookingChoices(eq(100), any()))
                .thenReturn(new PatientPortalBookingChoiceDto.Page(List.of(CHOICE), false));
        outcome(PortalBookingChoiceService.Outcome.BOOKED, new PortalBookingOffer());
        try (var configured = mockStatic(PatientPortalSettings.class);
                var staff = mockConstruction(PatientPortalStaffContext.class)) {
            configured.when(PatientPortalSettings::isConfigured).thenReturn(true);
            assertThat(sync.runOnce(SETTINGS, JOB)).isEqualTo(1);
            assertThat(staff.constructed()).isEmpty();
        }
        verify(portal).listPendingBookingChoices(eq(100), same(JOB));
        verify(portal).recordBookingChoiceResult(eq(7L), eq(11L), eq(true), any(), same(JOB));
        // The rows the booking writes carry the job's key id, never a provider of its own.
        verify(bookings).book(CHOICE, KID, SETTINGS);
        // Nothing to look a provider up with: no provider or login DAO is wired in at all.
        for (Constructor<?> constructor : PortalBookingSyncService.class.getDeclaredConstructors()) {
            assertThat(Arrays.asList(constructor.getParameterTypes()))
                    .doesNotContain(ProviderDao.class, SecurityDao.class, PatientPortalStaffContext.class);
        }
    }

    @Test
    void shouldAnswerLaterPicks_whenOnePickKeepsFailing() {
        var stuck = new PatientPortalBookingChoiceDto(7, 10, 123, "slot-x", Instant.parse("2026-10-08T14:00:00Z"));
        when(portal.listPendingBookingChoices(eq(100), any()))
                .thenReturn(new PatientPortalBookingChoiceDto.Page(List.of(stuck, CHOICE), false));
        when(bookings.book(eq(stuck), any(), any())).thenThrow(new IllegalStateException("database refused"));
        when(bookings.book(eq(CHOICE), any(), any())).thenReturn(
                new PortalBookingChoiceService.Result(PortalBookingChoiceService.Outcome.BOOKED, new PortalBookingOffer()));
        try (var configured = mockStatic(PatientPortalSettings.class)) {
            configured.when(PatientPortalSettings::isConfigured).thenReturn(true);
            assertThat(sync.runOnce(SETTINGS, JOB)).isEqualTo(1);
            // A portal outage stops the run instead: the picks wait there for the next one.
            when(portal.listPendingBookingChoices(eq(100), any())).thenThrow(
                    PatientPortalException.ofTransportFailure("/x", new IOException("down")));
            assertThatThrownBy(() -> sync.runOnce(SETTINGS, JOB)).isInstanceOf(PatientPortalException.class);
        }
        verify(portal).recordBookingChoiceResult(eq(7L), eq(11L), eq(true), any(), any());
    }

    @Test
    void shouldReportBookedAgain_whenAnEarlierRunBookedIt() {
        outcome(PortalBookingChoiceService.Outcome.ALREADY_BOOKED, new PortalBookingOffer());
        sync.answer(portal, CHOICE, JOB, SETTINGS);
        verify(portal).recordBookingChoiceResult(7, 11, true, List.of(), JOB);
    }

    @Test
    void shouldOfferFreshTimes_whenThePickIsGone() {
        PortalBookingOffer refused = new PortalBookingOffer();
        outcome(PortalBookingChoiceService.Outcome.UNAVAILABLE, refused);
        var fresh = List.of(new PatientPortalOfferedSlot("slot-b", OffsetDateTime.parse("2026-10-21T10:00:00-04:00"),
                15, "in_person", null));
        when(offers.replacementsFor(refused, 7, 11, SETTINGS)).thenReturn(fresh);
        sync.answer(portal, CHOICE, JOB, SETTINGS);
        verify(portal).recordBookingChoiceResult(7, 11, false, fresh, JOB);
    }

    @Test
    void shouldRefuseWithoutBooking_whenThePickIsNotAnOfferedTime() {
        outcome(PortalBookingChoiceService.Outcome.UNKNOWN, null);
        sync.answer(portal, CHOICE, JOB, SETTINGS);
        verify(portal).recordBookingChoiceResult(7, 11, false, List.of(), JOB);
        verify(offers, never()).replacementsFor(any(), anyLong(), anyLong(), any());
    }

    @Test
    void shouldUndoTheBooking_whenThePortalSaysWithdrawnOrExpired() {
        outcome(PortalBookingChoiceService.Outcome.BOOKED, new PortalBookingOffer());
        for (String detail : List.of(PatientPortalService.CHOICE_WITHDRAWN_DETAIL, PatientPortalService.CHOICE_EXPIRED_DETAIL)) {
            org.mockito.Mockito.doThrow(PatientPortalException.ofStatus(409, "/internal/carlos/booking-prompts/{id}/choice-result", detail))
                    .when(portal).recordBookingChoiceResult(anyLong(), anyLong(), anyBoolean(), any(), any());
            assertThat(sync.answer(portal, CHOICE, JOB, SETTINGS)).isTrue();
        }
        verify(bookings, org.mockito.Mockito.times(2)).undo("slot-a", 11, KID);
        verify(bookings, never()).confirm(any());
    }

    @Test
    void shouldLogTheJob_whenAnUndoFails() {
        PortalBookingOffer offer = new PortalBookingOffer();
        offer.setAppointmentNo(55);
        outcome(PortalBookingChoiceService.Outcome.BOOKED, offer);
        org.mockito.Mockito.doThrow(PatientPortalException.ofStatus(409, "/x", PatientPortalService.CHOICE_EXPIRED_DETAIL))
                .when(portal).recordBookingChoiceResult(anyLong(), anyLong(), anyBoolean(), any(), any());
        when(bookings.undo("slot-a", 11, KID)).thenThrow(new IllegalStateException("database refused"));
        try (var logs = mockStatic(LogAction.class)) {
            assertThat(sync.answer(portal, CHOICE, JOB, SETTINGS)).isTrue();
            ArgumentCaptor<OscarLog> row = ArgumentCaptor.forClass(OscarLog.class);
            logs.verify(() -> LogAction.addLogSynchronous(row.capture()));
            assertThat(row.getValue().getProviderNo()).isEqualTo("-1");
            assertThat(row.getValue().getAction()).isEqualTo("PortalJob.booking-sync.undoFailed");
            assertThat(row.getValue().getContentId()).isEqualTo("55");
            assertThat(row.getValue().getData()).isEqualTo("kid=" + KID + " choice=11");
        }
    }

    @Test
    void shouldStopTheRun_whenThePortalAnswersAsDownOrThrottling() {
        var second = new PatientPortalBookingChoiceDto(7, 12, 124, "slot-b", Instant.parse("2026-10-08T15:30:00Z"));
        when(portal.listPendingBookingChoices(eq(100), any()))
                .thenReturn(new PatientPortalBookingChoiceDto.Page(List.of(CHOICE, second), false));
        outcome(PortalBookingChoiceService.Outcome.BOOKED, new PortalBookingOffer());
        for (int status : new int[] {503, 429}) {
            org.mockito.Mockito.reset(bookings);
            outcome(PortalBookingChoiceService.Outcome.BOOKED, new PortalBookingOffer());
            org.mockito.Mockito.doThrow(PatientPortalException.ofStatus(status, "/x", null))
                    .when(portal).recordBookingChoiceResult(anyLong(), anyLong(), anyBoolean(), any(), any());
            try (var configured = mockStatic(PatientPortalSettings.class)) {
                configured.when(PatientPortalSettings::isConfigured).thenReturn(true);
                assertThatThrownBy(() -> sync.runOnce(SETTINGS, JOB)).isInstanceOf(PatientPortalException.class);
            }
            // The second patient's time is not booked while the portal cannot be told.
            verify(bookings, never()).book(eq(second), any(), any());
            verify(bookings, never()).confirm(any());
        }
    }

    @Test
    void shouldNotUndo_whenTheChoiceIsNoLongerPendingForAnotherReason() {
        outcome(PortalBookingChoiceService.Outcome.BOOKED, new PortalBookingOffer());
        when(portal.recordBookingChoiceResult(anyLong(), anyLong(), anyBoolean(), any(), any())).thenThrow(
                PatientPortalException.ofStatus(409, "/x", PatientPortalService.CHOICE_NOT_PENDING_DETAIL));
        assertThat(sync.answer(portal, CHOICE, JOB, SETTINGS)).isTrue();
        verify(bookings, never()).undo(any(), anyLong(), any());
    }

    @Test
    void shouldLeaveThePickForTheNextRun_whenThePortalIsUnreachable() {
        outcome(PortalBookingChoiceService.Outcome.BOOKED, new PortalBookingOffer());
        when(portal.recordBookingChoiceResult(anyLong(), anyLong(), anyBoolean(), any(), any())).thenThrow(
                PatientPortalException.ofTransportFailure("/x", new IOException("down")));
        assertThatThrownBy(() -> sync.answer(portal, CHOICE, JOB, SETTINGS)).isInstanceOf(PatientPortalException.class);
        verify(bookings, never()).undo(any(), anyLong(), any());
        verify(bookings, never()).confirm(any());
    }

    @Test
    void shouldDoNothing_whenSyncIsOffOrTheJobHasNoKey() {
        var off = new PortalBookingSettings(Set.of('B'), 24, "in_person", null, false, 60);
        assertThat(sync.runOnce(off, JOB)).isZero();
        assertThat(sync.runOnce(SETTINGS, null)).isZero();
        verify(bookings, never()).book(any(), any(), any());
        verify(portal, never()).listPendingBookingChoices(org.mockito.ArgumentMatchers.anyInt(), any());
    }

    @Test
    void shouldStartTheJob_withOneInfoLineNamingItsKeyId() {
        try (LogCapture capture = LogCapture.forLogger(PortalBookingSyncService.class)) {
            PortalJobAssertionSigner job = PortalBookingSyncService.startupJob(SETTINGS, true,
                    keys(properties(PortalTestKeys.JOB_PRIVATE_KEY, KID)));
            assertThat(job.job()).isEqualTo(PortalJob.BOOKING_SYNC);
            assertThat(job.keyId()).isEqualTo(KID);
            assertThat(at(capture, Level.WARN)).isEmpty();
            assertThat(at(capture, Level.INFO)).singleElement().satisfies(line -> assertThat(
                    line.getMessage().getFormattedMessage()).isEqualTo(
                            "Portal job booking-sync is configured; kid=" + KID));
            assertThat(capture.messages()).noneMatch(line -> line.contains(PortalTestKeys.JOB_PRIVATE_KEY));
        }
    }

    @Test
    void shouldTurnTheJobOff_withOneWarnNamingTheSetting_whenTheKeyIsMissing() {
        try (LogCapture capture = LogCapture.forLogger(PortalBookingSyncService.class)) {
            // The staff key is configured: the job must not fall back to it.
            assertThat(PortalBookingSyncService.startupJob(SETTINGS, true, keys(properties(null, null)))).isNull();
            assertThat(at(capture, Level.WARN)).singleElement().satisfies(line -> assertThat(
                    line.getMessage().getFormattedMessage())
                    .contains("booking-sync", "patient_portal.job.booking-sync.private_key"));
            assertThat(at(capture, Level.INFO)).singleElement().satisfies(line -> assertThat(
                    line.getMessage().getFormattedMessage()).contains("booking-sync", "off"));
        }
    }

    @Test
    void shouldTurnTheJobOff_withOneWarnNamingTheSetting_whenTheKeyIsInvalid() {
        try (LogCapture capture = LogCapture.forLogger(PortalBookingSyncService.class)) {
            assertThat(PortalBookingSyncService.startupJob(SETTINGS, true,
                    keys(properties("FAKE-not-a-real-key-value", KID)))).isNull();
            assertThat(at(capture, Level.WARN)).singleElement().satisfies(line -> assertThat(
                    line.getMessage().getFormattedMessage())
                    .contains("booking-sync", "patient_portal.job.booking-sync.private_key")
                    .doesNotContain("FAKE-not-a-real-key-value"));
            assertThat(at(capture, Level.INFO)).hasSize(1);
        }
    }

    @Test
    void shouldTurnTheJobOff_whenItsKeyIdIsTheStaffKeyId() {
        try (LogCapture capture = LogCapture.forLogger(PortalBookingSyncService.class)) {
            assertThat(PortalBookingSyncService.startupJob(SETTINGS, true,
                    keys(properties(PortalTestKeys.JOB_PRIVATE_KEY, "primary")))).isNull();
            assertThat(at(capture, Level.WARN)).singleElement().satisfies(line -> assertThat(
                    line.getMessage().getFormattedMessage())
                    .contains("booking-sync", "patient_portal.job.booking-sync.key_id",
                            "patient_portal.staff_assertion.key_id"));
        }
    }

    @Test
    void shouldNeverFailStartup_whenReadingTheKeyThrows() {
        try (LogCapture capture = LogCapture.forLogger(PortalBookingSyncService.class)) {
            assertThat(PortalBookingSyncService.startupJob(SETTINGS, true, job -> {
                throw new IllegalStateException("FAKE-unexpected");
            })).isNull();
            assertThat(at(capture, Level.WARN)).singleElement().satisfies(line -> assertThat(
                    line.getMessage().getFormattedMessage()).contains("booking-sync").doesNotContain("FAKE-unexpected"));
            assertThat(at(capture, Level.INFO)).hasSize(1);
        }
    }

    @Test
    void shouldSayOffWithoutAWarn_whenSyncOrThePortalIsSwitchedOff() {
        var off = new PortalBookingSettings(Set.of('B'), 24, "in_person", null, false, 60);
        AtomicInteger reads = new AtomicInteger();
        Function<PortalJob, Optional<PortalJobKey>> counted = job -> {
            reads.incrementAndGet();
            return Optional.empty();
        };
        try (LogCapture capture = LogCapture.forLogger(PortalBookingSyncService.class)) {
            assertThat(PortalBookingSyncService.startupJob(off, true, counted)).isNull();
            assertThat(PortalBookingSyncService.startupJob(SETTINGS, false, counted)).isNull();
            assertThat(at(capture, Level.WARN)).isEmpty();
            assertThat(at(capture, Level.INFO)).hasSize(2).allSatisfy(line -> assertThat(
                    line.getMessage().getFormattedMessage()).startsWith("Portal job booking-sync is off"));
        }
        assertThat(reads).hasValue(0);
    }
}
