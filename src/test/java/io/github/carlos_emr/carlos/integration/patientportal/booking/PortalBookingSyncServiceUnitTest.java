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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.SecurityDao;
import io.github.carlos_emr.carlos.commn.model.PortalBookingOffer;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalBookingChoiceDto;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalException;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalOfferedSlot;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalService;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalStaffContext;
import io.github.carlos_emr.carlos.integration.patientportal.PortalRequestPreparationException;
import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** The polling job answers every pick once, undoes bookings the portal will never show, and retries safely (#3850). */
@Tag("unit")
@Tag("patient-portal")
class PortalBookingSyncServiceUnitTest {
    private static final PortalBookingSettings SETTINGS = new PortalBookingSettings(
            Set.of('B'), 24, "in_person", null, true, "-9", 60);
    private static final PatientPortalStaffContext SYNC = new PatientPortalStaffContext(
            "-9", "Portal booking sync", Set.of(PatientPortalStaffContext.PERMISSION_BOOKING_PROMPT_SYNC));
    private static final PatientPortalBookingChoiceDto CHOICE = new PatientPortalBookingChoiceDto(
            7, 11, 123, "slot-a", Instant.parse("2026-10-08T15:00:00Z"));

    private final PortalBookingChoiceService bookings = mock(PortalBookingChoiceService.class);
    private final PortalBookingOfferService offers = mock(PortalBookingOfferService.class);
    private final ProviderDao providers = mock(ProviderDao.class);
    private final SecurityDao logins = mock(SecurityDao.class);
    private final PatientPortalService portal = mock(PatientPortalService.class);
    private final PortalBookingSyncService sync = new PortalBookingSyncService(bookings, offers, providers, logins,
            () -> portal);

    private void outcome(PortalBookingChoiceService.Outcome outcome, PortalBookingOffer offer) {
        when(bookings.book(CHOICE, "-9", SETTINGS)).thenReturn(new PortalBookingChoiceService.Result(outcome, offer));
    }

    @Test
    void shouldReportBooked_whenThePickWasBooked() {
        outcome(PortalBookingChoiceService.Outcome.BOOKED, new PortalBookingOffer());
        assertThat(sync.answer(portal, CHOICE, SYNC, SETTINGS)).isTrue();
        verify(portal).recordBookingChoiceResult(7, 11, true, List.of(), SYNC);
        verify(bookings).confirm(CHOICE);
    }

    @Test
    void shouldAnswerLaterPicks_whenOnePickKeepsFailing() {
        var stuck = new PatientPortalBookingChoiceDto(7, 10, 123, "slot-x", Instant.parse("2026-10-08T14:00:00Z"));
        Provider provider = new Provider();
        provider.setFirstName("Booking");
        provider.setLastName("Portal");
        when(providers.getProvider("-9")).thenReturn(provider);
        when(logins.findByProviderNo("-9")).thenReturn(List.of());
        when(portal.listPendingBookingChoices(eq(100), any()))
                .thenReturn(new PatientPortalBookingChoiceDto.Page(List.of(stuck, CHOICE), false));
        when(bookings.book(eq(stuck), any(), any())).thenThrow(new IllegalStateException("database refused"));
        when(bookings.book(eq(CHOICE), any(), any())).thenReturn(
                new PortalBookingChoiceService.Result(PortalBookingChoiceService.Outcome.BOOKED, new PortalBookingOffer()));
        try (var configured = org.mockito.Mockito.mockStatic(
                io.github.carlos_emr.carlos.integration.patientportal.PatientPortalSettings.class)) {
            configured.when(io.github.carlos_emr.carlos.integration.patientportal.PatientPortalSettings::isConfigured)
                    .thenReturn(true);
            assertThat(sync.runOnce(SETTINGS)).isEqualTo(1);
            // A portal outage stops the run instead: the picks wait there for the next one.
            when(portal.listPendingBookingChoices(eq(100), any())).thenThrow(
                    PatientPortalException.ofTransportFailure("/x", new IOException("down")));
            assertThatThrownBy(() -> sync.runOnce(SETTINGS)).isInstanceOf(PatientPortalException.class);
        }
        verify(portal).recordBookingChoiceResult(eq(7L), eq(11L), eq(true), any(), any());
    }

    @Test
    void shouldReportBookedAgain_whenAnEarlierRunBookedIt() {
        outcome(PortalBookingChoiceService.Outcome.ALREADY_BOOKED, new PortalBookingOffer());
        sync.answer(portal, CHOICE, SYNC, SETTINGS);
        verify(portal).recordBookingChoiceResult(7, 11, true, List.of(), SYNC);
    }

    @Test
    void shouldOfferFreshTimes_whenThePickIsGone() {
        PortalBookingOffer refused = new PortalBookingOffer();
        outcome(PortalBookingChoiceService.Outcome.UNAVAILABLE, refused);
        var fresh = List.of(new PatientPortalOfferedSlot("slot-b", OffsetDateTime.parse("2026-10-21T10:00:00-04:00"),
                15, "in_person", null));
        when(offers.replacementsFor(refused, 11, SETTINGS)).thenReturn(fresh);
        sync.answer(portal, CHOICE, SYNC, SETTINGS);
        verify(portal).recordBookingChoiceResult(7, 11, false, fresh, SYNC);
    }

    @Test
    void shouldRefuseWithoutBooking_whenThePickIsNotAnOfferedTime() {
        outcome(PortalBookingChoiceService.Outcome.UNKNOWN, null);
        sync.answer(portal, CHOICE, SYNC, SETTINGS);
        verify(portal).recordBookingChoiceResult(7, 11, false, List.of(), SYNC);
        verify(offers, never()).replacementsFor(any(), anyLong(), any());
    }

    @Test
    void shouldUndoTheBooking_whenThePortalSaysWithdrawnOrExpired() {
        outcome(PortalBookingChoiceService.Outcome.BOOKED, new PortalBookingOffer());
        for (String detail : List.of(PatientPortalService.CHOICE_WITHDRAWN_DETAIL, PatientPortalService.CHOICE_EXPIRED_DETAIL)) {
            org.mockito.Mockito.doThrow(PatientPortalException.ofStatus(409, "/internal/carlos/booking-prompts/{id}/choice-result", detail))
                    .when(portal).recordBookingChoiceResult(anyLong(), anyLong(), anyBoolean(), any(), any());
            assertThat(sync.answer(portal, CHOICE, SYNC, SETTINGS)).isTrue();
        }
        verify(bookings, org.mockito.Mockito.times(2)).undo("slot-a", 11, "-9");
    }

    @Test
    void shouldNotUndo_whenTheChoiceIsNoLongerPendingForAnotherReason() {
        outcome(PortalBookingChoiceService.Outcome.BOOKED, new PortalBookingOffer());
        when(portal.recordBookingChoiceResult(anyLong(), anyLong(), anyBoolean(), any(), any())).thenThrow(
                PatientPortalException.ofStatus(409, "/x", PatientPortalService.CHOICE_NOT_PENDING_DETAIL));
        assertThat(sync.answer(portal, CHOICE, SYNC, SETTINGS)).isTrue();
        verify(bookings, never()).undo(any(), anyLong(), any());
    }

    @Test
    void shouldLeaveThePickForTheNextRun_whenThePortalIsUnreachable() {
        outcome(PortalBookingChoiceService.Outcome.BOOKED, new PortalBookingOffer());
        when(portal.recordBookingChoiceResult(anyLong(), anyLong(), anyBoolean(), any(), any())).thenThrow(
                PatientPortalException.ofTransportFailure("/x", new IOException("down")));
        assertThatThrownBy(() -> sync.answer(portal, CHOICE, SYNC, SETTINGS)).isInstanceOf(PatientPortalException.class);
        verify(bookings, never()).undo(any(), anyLong(), any());
    }

    @Test
    void shouldSignWithSyncAlone_forAProviderThatCannotLogIn() {
        Provider provider = new Provider();
        provider.setFirstName("Booking");
        provider.setLastName("Portal");
        when(providers.getProvider("-9")).thenReturn(provider);
        when(logins.findByProviderNo("-9")).thenReturn(List.of());
        assertThat(sync.syncIdentity("-9").permissions())
                .containsExactly(PatientPortalStaffContext.PERMISSION_BOOKING_PROMPT_SYNC);
        when(logins.findByProviderNo("-9")).thenReturn(List.of(new Security()));
        assertThatThrownBy(() -> sync.syncIdentity("-9")).isInstanceOf(PortalRequestPreparationException.class)
                .hasMessageContaining("must not be able to log in");
        when(providers.getProvider("-8")).thenReturn(null);
        assertThatThrownBy(() -> sync.syncIdentity("-8")).isInstanceOf(PortalRequestPreparationException.class);
    }

    @Test
    void shouldDoNothing_whenSyncIsOff() {
        var off = new PortalBookingSettings(Set.of('B'), 24, "in_person", null, false, null, 60);
        assertThat(sync.runOnce(off)).isZero();
        verify(bookings, never()).book(any(), any(), eq(off));
    }
}
