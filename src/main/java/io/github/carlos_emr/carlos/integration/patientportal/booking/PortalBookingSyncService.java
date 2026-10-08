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

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.SecurityDao;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalBookingChoiceDto;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalException;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalOfferedSlot;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalService;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalSettings;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalStaffContext;
import io.github.carlos_emr.carlos.integration.patientportal.PortalRequestPreparationException;
import io.github.carlos_emr.carlos.utility.DeamonThreadFactory;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Polls the portal for patients' picks of offered times and books them (#3850).
 *
 * <p>CARLOS never accepts calls from the portal: this job asks, about once a minute, signed as the
 * clinic's dedicated non-login provider holding only {@code portal.booking_prompt.sync}. Every step
 * is safe to repeat, so an outage or a crash between booking and reporting only delays the answer:
 * the pick is listed again and the same booking is reported again, never made twice.
 *
 * <p>Off unless {@code patient_portal.booking.sync.enabled=true} and
 * {@code patient_portal.booking.sync.provider_no} names a provider that cannot log in.
 */
@Service
public class PortalBookingSyncService {
    private static final Logger LOGGER = MiscUtils.getLogger();
    /** Pages per run, so one run stays short; the rest are picked up by the next run. */
    static final int MAX_PAGES_PER_RUN = 5;

    private final PortalBookingChoiceService bookings;
    private final PortalBookingOfferService offers;
    private final ProviderDao providers;
    private final SecurityDao logins;
    private final Supplier<PatientPortalService> portal;
    private ScheduledExecutorService executor;

    /**
     * The portal client is looked up only when a run needs it, the way the staff actions do: its
     * bean is not an injection candidate, and it fails on purpose while the portal is misconfigured.
     */
    @Autowired
    public PortalBookingSyncService(PortalBookingChoiceService bookings, PortalBookingOfferService offers,
            ProviderDao providers, SecurityDao logins) {
        this(bookings, offers, providers, logins, () -> SpringUtils.getBean(PatientPortalService.class));
    }

    PortalBookingSyncService(PortalBookingChoiceService bookings, PortalBookingOfferService offers,
            ProviderDao providers, SecurityDao logins, Supplier<PatientPortalService> portal) {
        this.bookings = bookings;
        this.offers = offers;
        this.providers = providers;
        this.logins = logins;
        this.portal = portal;
    }

    @PostConstruct
    public void start() {
        PortalBookingSettings settings = PortalBookingSettings.fromCarlosProperties();
        if (!settings.syncEnabled()) {
            LOGGER.info("Portal booking sync is off; patients' picks of offered times are not booked automatically.");
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(
                new DeamonThreadFactory(PortalBookingSyncService.class.getSimpleName(), Thread.NORM_PRIORITY));
        executor.scheduleWithFixedDelay(this::runSafely, settings.syncIntervalSeconds(),
                settings.syncIntervalSeconds(), TimeUnit.SECONDS);
    }

    @PreDestroy
    public void stop() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private void runSafely() {
        try {
            runOnce(PortalBookingSettings.fromCarlosProperties());
        } catch (RuntimeException failure) {
            // The portal may be down; the picks wait there and the next run tries again.
            LOGGER.warn("Portal booking sync run failed; exceptionClass={}", failure.getClass().getSimpleName());
        }
    }

    /**
     * One poll: books or refuses every pick the portal lists, up to {@link #MAX_PAGES_PER_RUN} pages.
     *
     * @return how many picks were answered
     */
    public int runOnce(PortalBookingSettings settings) {
        if (!settings.syncEnabled() || !PatientPortalSettings.isConfigured()) {
            return 0;
        }
        PatientPortalStaffContext staff = syncIdentity(settings.syncProviderNo());
        PatientPortalService client = portal.get();
        int answered = 0;
        for (int page = 0; page < MAX_PAGES_PER_RUN; page++) {
            var choices = client.listPendingBookingChoices(PatientPortalService.MAX_BOOKING_CHOICES_PER_POLL, staff);
            for (PatientPortalBookingChoiceDto choice : choices.items()) {
                try {
                    if (answer(client, choice, staff, settings)) {
                        answered++;
                    }
                } catch (PatientPortalException failure) {
                    if (failure.kind() == PatientPortalException.Kind.TRANSPORT_FAILURE) {
                        throw failure;
                    }
                    // One pick the portal keeps refusing must not hold up every later patient.
                    LOGGER.warn("Portal booking pick left for a later run; kind={}", failure.kind());
                } catch (RuntimeException failure) {
                    LOGGER.warn("Portal booking pick left for a later run; exceptionClass={}",
                            failure.getClass().getSimpleName());
                }
            }
            if (!choices.hasMore()) {
                break;
            }
        }
        return answered;
    }

    /** Books or refuses one pick and tells the portal; false when it must be retried later. */
    boolean answer(PatientPortalService client, PatientPortalBookingChoiceDto choice,
            PatientPortalStaffContext staff, PortalBookingSettings settings) {
        var result = bookings.book(choice, staff.providerId(), settings);
        boolean booked = result.outcome() == PortalBookingChoiceService.Outcome.BOOKED
                || result.outcome() == PortalBookingChoiceService.Outcome.ALREADY_BOOKED;
        List<PatientPortalOfferedSlot> replacements = List.of();
        if (result.outcome() == PortalBookingChoiceService.Outcome.UNAVAILABLE && result.offer() != null) {
            replacements = offers.replacementsFor(result.offer(), choice.choiceId(), settings);
        }
        if (result.outcome() == PortalBookingChoiceService.Outcome.UNKNOWN) {
            // Not a time CARLOS offered this patient (or its record is gone): refuse it, never book it.
            LOGGER.warn("Portal booking pick does not match an offered time; refusing it");
        }
        try {
            client.recordBookingChoiceResult(choice.promptId(), choice.choiceId(), booked, replacements, staff);
            if (booked) {
                bookings.confirm(choice);
            }
            return true;
        } catch (PatientPortalException failure) {
            String detail = failure.detail();
            if (PatientPortalService.CHOICE_WITHDRAWN_DETAIL.equals(detail)
                    || PatientPortalService.CHOICE_EXPIRED_DETAIL.equals(detail)) {
                if (booked) {
                    // The portal will never show this pick as booked: take the appointment back off the schedule.
                    undoOrFlag(result.offer(), choice, staff.providerId());
                }
                return true;
            }
            if (PatientPortalService.CHOICE_NOT_PENDING_DETAIL.equals(detail)
                    || PatientPortalService.CHOICE_RESULT_CONFLICT_DETAIL.equals(detail)) {
                LOGGER.warn("Portal refused a booking result as no longer pending; detail={}", detail);
                return true;
            }
            throw failure;
        }
    }

    /**
     * The portal will not list this pick again, so a failed undo cannot be retried here: record the
     * appointment in the audit log for staff, rather than leave a booking the patient was told failed.
     */
    private void undoOrFlag(io.github.carlos_emr.carlos.commn.model.PortalBookingOffer offer,
            PatientPortalBookingChoiceDto choice, String systemProviderNo) {
        try {
            bookings.undo(choice.slotId(), choice.choiceId(), systemProviderNo);
        } catch (RuntimeException failure) {
            LOGGER.error("Portal booking could not be undone; see the audit log entry PortalBooking.undoFailed");
            io.github.carlos_emr.carlos.log.LogAction.addLogSynchronous(systemProviderNo, "PortalBooking.undoFailed",
                    "appointment", offer == null ? "" : String.valueOf(offer.getAppointmentNo()), null);
        }
    }

    /**
     * The polling identity: an existing provider that cannot log in, holding only the sync
     * permission. A provider with a login could be used by a person, so it is refused.
     */
    PatientPortalStaffContext syncIdentity(String providerNo) {
        Provider provider = providers.getProvider(providerNo);
        if (provider == null) {
            throw new PortalRequestPreparationException("portal booking sync provider does not exist");
        }
        if (!logins.findByProviderNo(providerNo).isEmpty()) {
            throw new PortalRequestPreparationException("portal booking sync provider must not be able to log in");
        }
        return new PatientPortalStaffContext(providerNo, provider.getFormattedName(),
                Set.of(PatientPortalStaffContext.PERMISSION_BOOKING_PROMPT_SYNC));
    }
}
