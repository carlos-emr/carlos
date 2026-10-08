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

import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.PortalBookingOffer;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalBookingChoiceDto;
import io.github.carlos_emr.carlos.log.LogAction;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Books a patient's pick of an offered time (#3850), or says it is gone.
 *
 * <p>Two patients can pick the same time at once, so booking checks and inserts under a lock on the
 * provider, and never books first and cancels on a clash ({@code removeIfDoubleBooked} could cancel
 * a time a patient was just told they had). Nothing is held while a prompt is open: the time is
 * checked again here against the same rule it was offered under.
 */
@Service
public class PortalBookingChoiceService {
    /** Shown on the schedule so staff can tell these bookings apart. */
    static final String REASON = "Booked by patient via portal";
    static final String CREATOR = "patient portal";
    private static final String NEW_APPOINTMENT_STATUS = "t";
    private static final String CANCELLED = "C";

    /** What became of a pick. */
    public enum Outcome {
        /** Booked now. */
        BOOKED,
        /** Booked by an earlier attempt for this same pick: report it again. */
        ALREADY_BOOKED,
        /** Gone, or no longer on the template: report slot_unavailable. */
        UNAVAILABLE,
        /** Not a time CARLOS offered this patient: never book it. */
        UNKNOWN
    }

    public record Result(Outcome outcome, PortalBookingOffer offer) {}

    private final PortalBookingOfferDao offers;
    private final PortalOfferedSlotLoader loader;
    private final OscarAppointmentDao appointments;
    private final DemographicDao demographics;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public PortalBookingChoiceService(PortalBookingOfferDao offers, PortalOfferedSlotLoader loader,
            OscarAppointmentDao appointments, DemographicDao demographics,
            PlatformTransactionManager transactionManager) {
        this(offers, loader, appointments, demographics, new TransactionTemplate(transactionManager),
                Clock.systemDefaultZone());
    }

    PortalBookingChoiceService(PortalBookingOfferDao offers, PortalOfferedSlotLoader loader,
            OscarAppointmentDao appointments, DemographicDao demographics, TransactionTemplate transactions,
            Clock clock) {
        this.offers = offers;
        this.loader = loader;
        this.appointments = appointments;
        this.demographics = demographics;
        this.transactions = transactions;
        this.clock = clock;
    }

    /** Books the pick if its time is still open, under a per-provider lock. */
    public Result book(PatientPortalBookingChoiceDto choice, String systemProviderNo, PortalBookingSettings settings) {
        // Read the provider in its own transaction: the booking transaction must start with the lock.
        PortalBookingOffer seen = transactions.execute(status -> offers.find(choice.slotId()));
        if (seen == null || seen.getDemographicNo() != choice.demographicNo()
                || seen.getPromptId() != null && seen.getPromptId() != choice.promptId()) {
            return new Result(Outcome.UNKNOWN, seen);
        }
        String providerNo = seen.getProviderNo();
        return transactions.execute(status -> {
            if (!offers.lockProvider(providerNo)) {
                return new Result(Outcome.UNKNOWN, seen);
            }
            PortalBookingOffer offer = offers.findForUpdate(choice.slotId());
            if (offer == null || !providerNo.equals(offer.getProviderNo())) {
                return new Result(Outcome.UNKNOWN, offer);
            }
            if (PortalBookingOffer.BOOKED.equals(offer.getStatus())) {
                return new Result(Objects.equals(offer.getChoiceId(), choice.choiceId())
                        ? Outcome.ALREADY_BOOKED : Outcome.UNAVAILABLE, offer);
            }
            ZonedDateTime now = ZonedDateTime.now(clock);
            ZonedDateTime start = offer.getStartTime().toInstant().atZone(now.getZone());
            if (!PortalBookingOffer.OFFERED.equals(offer.getStatus())
                    || !loader.isOpen(providerNo, start, offer.getDurationMinutes(), settings, now)) {
                if (PortalBookingOffer.OFFERED.equals(offer.getStatus())) {
                    offer.setStatus(PortalBookingOffer.UNAVAILABLE);
                    offer.setChoiceId(choice.choiceId());
                    offer.setUpdatedAt(Date.from(now.toInstant()));
                }
                return new Result(Outcome.UNAVAILABLE, offer);
            }
            Appointment appointment = appointmentFor(offer, start, systemProviderNo, now);
            appointments.persist(appointment);
            offer.setStatus(PortalBookingOffer.BOOKED);
            offer.setChoiceId(choice.choiceId());
            offer.setPromptId(choice.promptId());
            offer.setAppointmentNo(appointment.getId());
            offer.setUpdatedAt(Date.from(now.toInstant()));
            offers.closeOthers(choice.promptId(), offer.getSlotId(), Date.from(now.toInstant()));
            LogAction.addLogSynchronous(systemProviderNo, "PortalBooking.book", "appointment",
                    String.valueOf(appointment.getId()), null);
            return new Result(Outcome.BOOKED, offer);
        });
    }

    /**
     * Undoes the booking made for a pick that the portal will never show as booked (its prompt was
     * withdrawn, the account turned off, or its time started before the result arrived).
     *
     * @return true when an appointment was cancelled now
     */
    public boolean undo(String slotId, long choiceId, String systemProviderNo) {
        PortalBookingOffer seen = transactions.execute(status -> offers.find(slotId));
        if (seen == null || !PortalBookingOffer.BOOKED.equals(seen.getStatus())
                || !Objects.equals(seen.getChoiceId(), choiceId)) {
            return false;
        }
        Boolean cancelled = transactions.execute(status -> {
            offers.lockProvider(seen.getProviderNo());
            PortalBookingOffer offer = offers.findForUpdate(slotId);
            if (offer == null || !PortalBookingOffer.BOOKED.equals(offer.getStatus())
                    || !Objects.equals(offer.getChoiceId(), choiceId)) {
                return false;
            }
            Date now = Date.from(clock.instant());
            boolean changed = false;
            Appointment appointment = offer.getAppointmentNo() == null ? null
                    : appointments.find(offer.getAppointmentNo().intValue());
            if (appointment != null && !CANCELLED.equals(appointment.getStatus())) {
                appointment.setStatus(CANCELLED);
                appointment.setLastUpdateUser(systemProviderNo);
                appointment.setUpdateDateTime(now);
                appointments.merge(appointment);
                changed = true;
            }
            offer.setStatus(PortalBookingOffer.CLOSED);
            offer.setUpdatedAt(now);
            return changed;
        });
        if (Boolean.TRUE.equals(cancelled)) {
            LogAction.addLogSynchronous(systemProviderNo, "PortalBooking.undo", "appointment",
                    String.valueOf(seen.getAppointmentNo()), null);
        }
        return Boolean.TRUE.equals(cancelled);
    }

    private Appointment appointmentFor(PortalBookingOffer offer, ZonedDateTime start, String systemProviderNo,
            ZonedDateTime now) {
        Appointment appointment = new Appointment();
        appointment.setProviderNo(offer.getProviderNo());
        appointment.setAppointmentDate(Date.from(start.toLocalDate().atStartOfDay(start.getZone()).toInstant()));
        appointment.setStartTime(Date.from(start.toInstant()));
        // CARLOS stores the last minute as the end: 09:00-09:14 for 15 minutes.
        appointment.setEndTime(Date.from(start.plusMinutes(offer.getDurationMinutes() - 1L).toInstant()));
        appointment.setDemographicNo(offer.getDemographicNo());
        Demographic patient = demographics.getDemographicById(offer.getDemographicNo());
        appointment.setName(patient == null ? "" : patient.getFormattedName());
        appointment.setReason(REASON);
        appointment.setNotes("");
        appointment.setStatus(NEW_APPOINTMENT_STATUS);
        appointment.setBookingSource(Appointment.BookingSource.PORTAL);
        appointment.setCreator(CREATOR);
        appointment.setLastUpdateUser(systemProviderNo);
        appointment.setCreateDateTime(Date.from(now.toInstant()));
        appointment.setUpdateDateTime(Date.from(now.toInstant()));
        return appointment;
    }
}
