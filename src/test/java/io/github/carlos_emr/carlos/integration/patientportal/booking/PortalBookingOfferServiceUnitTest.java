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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.model.PortalBookingOffer;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Offers get opaque ids, a retried prompt gets the same times, and replacements never exceed 8 on offer (#3850). */
@Tag("unit")
@Tag("patient-portal")
class PortalBookingOfferServiceUnitTest {
    private static final ZoneId ZONE = ZoneId.of("America/Toronto");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-08T16:00:00Z"), ZONE);
    private static final PortalBookingSettings SETTINGS = new PortalBookingSettings(
            Set.of('B'), 24, "phone", "main", false, null, 60);
    private static final LocalDate FROM = LocalDate.of(2026, 10, 19);

    private final PortalBookingOfferDao offers = mock(PortalBookingOfferDao.class);
    private final PortalOfferedSlotLoader loader = mock(PortalOfferedSlotLoader.class);
    private final PortalBookingOfferService service = new PortalBookingOfferService(offers, loader, CLOCK);

    private static PortalOfferedSlotLoader.OpenTime time(int day, int hour) {
        return new PortalOfferedSlotLoader.OpenTime("101", ZonedDateTime.of(2026, 10, day, hour, 0, 0, 0, ZONE), 15, 'B');
    }

    private static PortalBookingOffer stored(String slotId, int demographicNo, int day, int hour) {
        PortalBookingOffer offer = new PortalBookingOffer();
        offer.setSlotId(slotId);
        offer.setDemographicNo(demographicNo);
        offer.setProviderNo("101");
        offer.setOperationId("operation-1");
        offer.setPromptId(7L);
        offer.setOfferedBy("999998");
        offer.setStartTime(Date.from(ZonedDateTime.of(2026, 10, day, hour, 0, 0, 0, ZONE).toInstant()));
        offer.setDurationMinutes(15);
        offer.setStatus(PortalBookingOffer.OFFERED);
        return offer;
    }

    @Test
    void shouldRecordOpaqueOffers_whenOfferingFreshTimes() {
        when(offers.findByOperation("operation-1")).thenReturn(List.of());
        when(loader.load(eq("101"), eq(FROM), eq(FROM.plusDays(13)), eq(4), anySet(), eq(SETTINGS), any()))
                .thenReturn(List.of(time(19, 9), time(20, 14)));
        var slots = service.offer("operation-1", 123, "101", FROM, FROM.plusDays(13), 4, "999998", SETTINGS);
        assertThat(slots).hasSize(2);
        assertThat(slots).allSatisfy(slot -> {
            // 32 random URL-safe characters: nothing readable about the provider, patient or time.
            assertThat(slot.slotId()).matches("[A-Za-z0-9_-]{32}");
            assertThat(slot.visitMode()).isEqualTo("phone");
            assertThat(slot.locationCode()).isEqualTo("main");
        });
        assertThat(slots.get(0).slotId()).isNotEqualTo(slots.get(1).slotId());
        assertThat(slots.get(0).startsAt().toString()).isEqualTo("2026-10-19T09:00-04:00");
        ArgumentCaptor<PortalBookingOffer> saved = ArgumentCaptor.forClass(PortalBookingOffer.class);
        verify(offers, org.mockito.Mockito.times(2)).persist(saved.capture());
        assertThat(saved.getAllValues()).allSatisfy(offer -> {
            assertThat(offer.getDemographicNo()).isEqualTo(123);
            assertThat(offer.getProviderNo()).isEqualTo("101");
            assertThat(offer.getOfferedBy()).isEqualTo("999998");
            assertThat(offer.getStatus()).isEqualTo(PortalBookingOffer.OFFERED);
            assertThat(offer.getChoiceId()).isNull();
        });
    }

    @Test
    void shouldResendTheSameTimes_whenThePromptIsRetried() {
        when(offers.findByOperation("operation-1")).thenReturn(List.of(stored("slot-a", 123, 19, 9)));
        var slots = service.offer("operation-1", 123, "101", FROM, FROM.plusDays(13), 4, "999998", SETTINGS);
        assertThat(slots).extracting(slot -> slot.slotId()).containsExactly("slot-a");
        verify(loader, never()).load(any(), any(), any(), anyInt(), anySet(), any(), any());
        verify(offers, never()).persist(any());
    }

    @Test
    void shouldRefuse_whenTheOperationWasUsedForAnotherPatient() {
        when(offers.findByOperation("operation-1")).thenReturn(List.of(stored("slot-a", 456, 19, 9)));
        assertThatThrownBy(() -> service.offer("operation-1", 123, "101", FROM, FROM, 4, "999998", SETTINGS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldKeepAtMostEightOnOffer_whenReplacingARefusedPick() {
        PortalBookingOffer refused = stored("slot-a", 123, 19, 9);
        List<PortalBookingOffer> stillOpen = new ArrayList<>();
        for (int index = 0; index < 6; index++) {
            stillOpen.add(stored("open-" + index, 123, 21 + index, 10));
        }
        when(offers.findReplacements(7, 11)).thenReturn(List.of());
        when(offers.findOpenForPrompt(eq(7L), any())).thenReturn(stillOpen);
        when(loader.load(eq("101"), any(), any(), eq(2), anySet(), eq(SETTINGS), any()))
                .thenReturn(List.of(time(20, 9), time(20, 14)));
        var replacements = service.replacementsFor(refused, 11, SETTINGS);
        assertThat(replacements).hasSize(2);
        ArgumentCaptor<PortalBookingOffer> saved = ArgumentCaptor.forClass(PortalBookingOffer.class);
        verify(offers, org.mockito.Mockito.times(2)).persist(saved.capture());
        // Each replacement remembers the refused pick, so a retried report sends the same ones.
        assertThat(saved.getAllValues()).allSatisfy(offer -> {
            assertThat(offer.getChoiceId()).isEqualTo(11L);
            assertThat(offer.getPromptId()).isEqualTo(7L);
        });
    }

    @Test
    void shouldReuseReplacements_whenTheReportIsRetried() {
        when(offers.findReplacements(7, 11)).thenReturn(List.of(stored("slot-b", 123, 20, 9)));
        assertThat(service.replacementsFor(stored("slot-a", 123, 19, 9), 11, SETTINGS))
                .extracting(slot -> slot.slotId()).containsExactly("slot-b");
        verify(loader, never()).load(any(), any(), any(), anyInt(), anySet(), any(), any());
    }

    @Test
    void shouldOfferNoReplacement_whenEightAreAlreadyOnOffer() {
        List<PortalBookingOffer> full = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            full.add(stored("open-" + index, 123, 20 + index, 10));
        }
        when(offers.findReplacements(7, 11)).thenReturn(List.of());
        when(offers.findOpenForPrompt(eq(7L), any())).thenReturn(full);
        assertThat(service.replacementsFor(stored("slot-a", 123, 19, 9), 11, SETTINGS)).isEmpty();
        verify(offers, never()).persist(any());
    }
}
