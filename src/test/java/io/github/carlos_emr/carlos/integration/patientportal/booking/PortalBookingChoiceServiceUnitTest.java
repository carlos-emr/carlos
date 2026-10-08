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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.PortalBookingOffer;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalBookingChoiceDto;
import io.github.carlos_emr.carlos.log.LogAction;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Booking a patient's pick checks again under a provider lock and never books first (#3850). */
@Tag("unit")
@Tag("patient-portal")
class PortalBookingChoiceServiceUnitTest {
    private static final ZoneId ZONE = ZoneId.of("America/Toronto");
    private static final ZonedDateTime START = ZonedDateTime.of(2026, 10, 20, 9, 30, 0, 0, ZONE);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-08T16:00:00Z"), ZONE);
    private static final PortalBookingSettings SETTINGS = new PortalBookingSettings(
            Set.of('B'), 24, "in_person", null, true, "-9", 60);
    private static final PatientPortalBookingChoiceDto CHOICE = new PatientPortalBookingChoiceDto(
            7, 11, 123, "slot-a", Instant.parse("2026-10-08T15:00:00Z"));

    private final PortalBookingOfferDao offers = mock(PortalBookingOfferDao.class);
    private final PortalOfferedSlotLoader loader = mock(PortalOfferedSlotLoader.class);
    private final OscarAppointmentDao appointments = mock(OscarAppointmentDao.class);
    private final DemographicDao demographics = mock(DemographicDao.class);
    private final PortalBookingChoiceService service = new PortalBookingChoiceService(offers, loader, appointments,
            demographics, new TransactionTemplate(mock(PlatformTransactionManager.class)), CLOCK);
    private MockedStatic<LogAction> logs;
    private PortalBookingOffer offer;

    @BeforeEach
    void offered() {
        logs = mockStatic(LogAction.class);
        offer = new PortalBookingOffer();
        offer.setSlotId("slot-a");
        offer.setOperationId("operation-1");
        offer.setPromptId(7L);
        offer.setDemographicNo(123);
        offer.setProviderNo("101");
        offer.setStartTime(Date.from(START.toInstant()));
        offer.setDurationMinutes(15);
        offer.setTemplateCode("B");
        offer.setStatus(PortalBookingOffer.OFFERED);
        when(offers.find("slot-a")).thenReturn(offer);
        when(offers.findForUpdate("slot-a")).thenReturn(offer);
        when(offers.lockProvider("101")).thenReturn(true);
        when(loader.isOpen(eq("101"), any(), eq(15), eq(SETTINGS), any())).thenReturn(true);
        doAnswer(call -> {
            call.<Appointment>getArgument(0).setId(55);
            return null;
        }).when(appointments).persist(any(Appointment.class));
    }

    @AfterEach
    void closeLogs() {
        logs.close();
    }

    @Test
    void shouldBookUnderProviderLock_whenTheTimeIsStillOpen() {
        var result = service.book(CHOICE, "-9", SETTINGS);
        assertThat(result.outcome()).isEqualTo(PortalBookingChoiceService.Outcome.BOOKED);
        var order = inOrder(offers, loader, appointments);
        order.verify(offers).lockProvider("101");
        order.verify(offers).findForUpdate("slot-a");
        order.verify(loader).isOpen(eq("101"), any(), eq(15), eq(SETTINGS), any());
        ArgumentCaptor<Appointment> saved = ArgumentCaptor.forClass(Appointment.class);
        order.verify(appointments).persist(saved.capture());
        Appointment appointment = saved.getValue();
        assertThat(appointment.getBookingSource()).isEqualTo(Appointment.BookingSource.PORTAL);
        assertThat(appointment.getProviderNo()).isEqualTo("101");
        assertThat(appointment.getDemographicNo()).isEqualTo(123);
        assertThat(appointment.getStatus()).isEqualTo("t");
        assertThat(appointment.getReason()).isEqualTo(PortalBookingChoiceService.REASON);
        assertThat(appointment.getStartTime().toInstant()).isEqualTo(START.toInstant());
        assertThat(appointment.getEndTime().toInstant()).isEqualTo(START.plusMinutes(14).toInstant());
        assertThat(offer.getStatus()).isEqualTo(PortalBookingOffer.BOOKED);
        assertThat(offer.getChoiceId()).isEqualTo(11L);
        assertThat(offer.getAppointmentNo()).isEqualTo(55);
        verify(offers).closeOthers(eq(7L), eq("slot-a"), any());
    }

    @Test
    void shouldReportBookedAgain_whenTheSamePickWasAlreadyBooked() {
        offer.setStatus(PortalBookingOffer.BOOKED);
        offer.setChoiceId(11L);
        assertThat(service.book(CHOICE, "-9", SETTINGS).outcome())
                .isEqualTo(PortalBookingChoiceService.Outcome.ALREADY_BOOKED);
        verify(appointments, never()).persist(any());
    }

    @Test
    void shouldRefuse_whenAnotherPickBookedTheTime() {
        offer.setStatus(PortalBookingOffer.BOOKED);
        offer.setChoiceId(10L);
        assertThat(service.book(CHOICE, "-9", SETTINGS).outcome())
                .isEqualTo(PortalBookingChoiceService.Outcome.UNAVAILABLE);
        verify(appointments, never()).persist(any());
    }

    @Test
    void shouldMarkUnavailable_whenTheTimeIsNoLongerOpen() {
        when(loader.isOpen(any(), any(), anyInt(), any(), any())).thenReturn(false);
        assertThat(service.book(CHOICE, "-9", SETTINGS).outcome())
                .isEqualTo(PortalBookingChoiceService.Outcome.UNAVAILABLE);
        assertThat(offer.getStatus()).isEqualTo(PortalBookingOffer.UNAVAILABLE);
        assertThat(offer.getChoiceId()).isEqualTo(11L);
        verify(appointments, never()).persist(any());
    }

    @Test
    void shouldNeverBook_whenThePickIsNotThisPatientsOffer() {
        var someoneElse = new PatientPortalBookingChoiceDto(7, 11, 456, "slot-a", Instant.now());
        assertThat(service.book(someoneElse, "-9", SETTINGS).outcome())
                .isEqualTo(PortalBookingChoiceService.Outcome.UNKNOWN);
        var otherPrompt = new PatientPortalBookingChoiceDto(8, 11, 123, "slot-a", Instant.now());
        assertThat(service.book(otherPrompt, "-9", SETTINGS).outcome())
                .isEqualTo(PortalBookingChoiceService.Outcome.UNKNOWN);
        when(offers.find("missing")).thenReturn(null);
        var missing = new PatientPortalBookingChoiceDto(7, 11, 123, "missing", Instant.now());
        assertThat(service.book(missing, "-9", SETTINGS).outcome())
                .isEqualTo(PortalBookingChoiceService.Outcome.UNKNOWN);
        verify(offers, never()).lockProvider(anyString());
        verify(appointments, never()).persist(any());
    }

    @Test
    void shouldCancelTheAppointment_whenUndoingABooking() {
        offer.setStatus(PortalBookingOffer.BOOKED);
        offer.setChoiceId(11L);
        offer.setAppointmentNo(55);
        Appointment appointment = new Appointment();
        appointment.setStatus("t");
        when(appointments.find(55)).thenReturn(appointment);
        assertThat(service.undo("slot-a", 11, "-9")).isTrue();
        assertThat(appointment.getStatus()).isEqualTo("C");
        assertThat(offer.getStatus()).isEqualTo(PortalBookingOffer.CLOSED);
        verify(appointments).merge(appointment);
        assertThat(service.undo("slot-a", 12, "-9")).isFalse();
    }
}
