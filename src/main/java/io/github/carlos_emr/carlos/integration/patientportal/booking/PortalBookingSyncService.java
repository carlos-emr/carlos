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

import io.github.carlos_emr.carlos.commn.model.PortalBookingOffer;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalBookingChoiceDto;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalConfigurationException;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalException;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalOfferedSlot;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalService;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalSettings;
import io.github.carlos_emr.carlos.integration.patientportal.PortalJob;
import io.github.carlos_emr.carlos.integration.patientportal.PortalJobAssertionSigner;
import io.github.carlos_emr.carlos.integration.patientportal.PortalJobKey;
import io.github.carlos_emr.carlos.utility.DeamonThreadFactory;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Polls the portal for patients' picks of offered times and books them (#3850).
 *
 * <p>CARLOS never accepts calls from the portal: this job asks, about once a minute. It is not a
 * person and not a CARLOS provider: it signs each call as the {@code booking-sync} job, with its own
 * key, claiming only {@code portal.booking_prompt.sync}, and the rows it writes name the built-in
 * system provider. Every step is safe to repeat, so an outage or a crash between booking and
 * reporting only delays the answer: the pick is listed again and the same booking is reported
 * again, never made twice.
 *
 * <p>Off unless the portal is on, {@code patient_portal.booking.sync.enabled=true}, and the job has
 * a valid key of its own ({@code patient_portal.job.booking-sync.private_key} and {@code .key_id}).
 * A missing or invalid key never stops CARLOS starting and never falls back to the staff key: the
 * job stays off and says why once, at startup.
 */
@Service
public class PortalBookingSyncService {
    private static final Logger LOGGER = MiscUtils.getLogger();
    /** Pages per run, so one run stays short; the rest are picked up by the next run. */
    static final int MAX_PAGES_PER_RUN = 5;
    static final PortalJob JOB = PortalJob.BOOKING_SYNC;

    private final PortalBookingChoiceService bookings;
    private final PortalBookingOfferService offers;
    private final Supplier<PatientPortalService> portal;
    private ScheduledExecutorService executor;

    /**
     * The portal client is looked up only when a run needs it, the way the staff actions do: its
     * bean is not an injection candidate, and it fails on purpose while the portal is misconfigured.
     */
    @Autowired
    public PortalBookingSyncService(PortalBookingChoiceService bookings, PortalBookingOfferService offers) {
        this(bookings, offers, () -> SpringUtils.getBean(PatientPortalService.class));
    }

    PortalBookingSyncService(PortalBookingChoiceService bookings, PortalBookingOfferService offers,
            Supplier<PatientPortalService> portal) {
        this.bookings = bookings;
        this.offers = offers;
        this.portal = portal;
    }

    @PostConstruct
    public void start() {
        PortalBookingSettings settings = PortalBookingSettings.fromCarlosProperties();
        // Keys are read once: rotating one means restarting every CARLOS node anyway.
        PortalJobAssertionSigner job = startupJob(settings, PatientPortalSettings.isConfigured(),
                PortalJobKey::fromCarlosProperties);
        if (job == null) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(
                new DeamonThreadFactory(PortalBookingSyncService.class.getSimpleName(), Thread.NORM_PRIORITY));
        executor.scheduleWithFixedDelay(() -> runSafely(job), settings.syncIntervalSeconds(),
                settings.syncIntervalSeconds(), TimeUnit.SECONDS);
    }

    /**
     * Decides at startup whether the job runs. Logs exactly one INFO line saying it is configured
     * (with its key id, never key material) or off, and one WARN as well when it was switched on but
     * its key is missing or invalid, naming the job and the setting. Never throws.
     *
     * @return the job's signer, or null when the job stays off
     */
    static PortalJobAssertionSigner startupJob(PortalBookingSettings settings, boolean portalOn,
            Function<PortalJob, Optional<PortalJobKey>> keys) {
        String name = JOB.jobName();
        if (!portalOn) {
            LOGGER.info("Portal job {} is off: the patient portal is off", name);
            return null;
        }
        if (!settings.syncEnabled()) {
            LOGGER.info("Portal job {} is off: {} is not true", name, PortalBookingSettings.SYNC_ENABLED_KEY);
            return null;
        }
        PortalJobAssertionSigner signer;
        try {
            signer = keys.apply(JOB).map(PortalJobAssertionSigner::from).orElse(null);
        } catch (PatientPortalConfigurationException invalid) {
            // The message names only the setting at fault; key material never reaches it.
            LOGGER.warn("Portal job {} is off: {}", name, invalid.getMessage());
            LOGGER.info("Portal job {} is off: its key is not configured", name);
            return null;
        } catch (RuntimeException unexpected) {
            LOGGER.warn("Portal job {} is off: its key could not be read; exceptionClass={}", name,
                    unexpected.getClass().getSimpleName());
            LOGGER.info("Portal job {} is off: its key is not configured", name);
            return null;
        }
        if (signer == null) {
            LOGGER.warn("Portal job {} is off: {} and {} are not set", name, JOB.privateKeyProperty(),
                    JOB.keyIdProperty());
            LOGGER.info("Portal job {} is off: its key is not configured", name);
            return null;
        }
        LOGGER.info("Portal job {} is configured; kid={}", name, signer.keyId());
        return signer;
    }

    @PreDestroy
    public void stop() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private void runSafely(PortalJobAssertionSigner job) {
        try {
            runOnce(PortalBookingSettings.fromCarlosProperties(), job);
        } catch (RuntimeException failure) {
            // The portal may be down; the picks wait there and the next run tries again.
            LOGGER.warn("Portal booking sync run failed; exceptionClass={}", failure.getClass().getSimpleName());
        }
    }

    /**
     * One poll: books or refuses every pick the portal lists, up to {@link #MAX_PAGES_PER_RUN} pages.
     *
     * @param job the booking-sync job's signer; null means the job is off
     * @return how many picks were answered
     */
    public int runOnce(PortalBookingSettings settings, PortalJobAssertionSigner job) {
        if (job == null || !settings.syncEnabled() || !PatientPortalSettings.isConfigured()) {
            return 0;
        }
        PatientPortalService client = portal.get();
        int answered = 0;
        for (int page = 0; page < MAX_PAGES_PER_RUN; page++) {
            var choices = client.listPendingBookingChoices(PatientPortalService.MAX_BOOKING_CHOICES_PER_POLL, job);
            int answeredOnPage = 0;
            for (PatientPortalBookingChoiceDto choice : choices.items()) {
                try {
                    if (answer(client, choice, job, settings)) {
                        answeredOnPage++;
                    }
                } catch (PatientPortalException failure) {
                    if (portalUnavailable(failure)) {
                        // Stop before booking more times the patient cannot be told about.
                        throw failure;
                    }
                    // One pick the portal keeps refusing must not hold up every later patient.
                    LOGGER.warn("Portal booking pick left for a later run; kind={}", failure.kind());
                } catch (RuntimeException failure) {
                    LOGGER.warn("Portal booking pick left for a later run; exceptionClass={}",
                            failure.getClass().getSimpleName());
                }
            }
            answered += answeredOnPage;
            // The next page would start with the same unanswered picks: stop when nothing moved.
            if (!choices.hasMore() || answeredOnPage == 0) {
                break;
            }
        }
        return answered;
    }

    /** The portal is down, overloaded or throttling: every further pick would fail the same way. */
    static boolean portalUnavailable(PatientPortalException failure) {
        return failure.kind() == PatientPortalException.Kind.TRANSPORT_FAILURE
                || failure.kind() == PatientPortalException.Kind.THROTTLED
                || failure.statusCode() >= 500;
    }

    /** Books or refuses one pick and tells the portal; false when it must be retried later. */
    boolean answer(PatientPortalService client, PatientPortalBookingChoiceDto choice,
            PortalJobAssertionSigner job, PortalBookingSettings settings) {
        var result = bookings.book(choice, job.keyId(), settings);
        boolean booked = result.outcome() == PortalBookingChoiceService.Outcome.BOOKED
                || result.outcome() == PortalBookingChoiceService.Outcome.ALREADY_BOOKED;
        List<PatientPortalOfferedSlot> replacements = List.of();
        if (result.outcome() == PortalBookingChoiceService.Outcome.UNAVAILABLE && result.offer() != null) {
            replacements = offers.replacementsFor(result.offer(), choice.promptId(), choice.choiceId(), settings);
        }
        if (result.outcome() == PortalBookingChoiceService.Outcome.UNKNOWN) {
            // Not a time CARLOS offered this patient (or its record is gone): refuse it, never book it.
            LOGGER.warn("Portal booking pick does not match an offered time; refusing it");
        }
        try {
            client.recordBookingChoiceResult(choice.promptId(), choice.choiceId(), booked, replacements, job);
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
                    undoOrFlag(result.offer(), choice, job.keyId());
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
    private void undoOrFlag(PortalBookingOffer offer, PatientPortalBookingChoiceDto choice, String jobKeyId) {
        try {
            bookings.undo(choice.slotId(), choice.choiceId(), jobKeyId);
        } catch (RuntimeException failure) {
            LOGGER.error("Portal booking could not be undone; see the audit log entry {}",
                    JOB.logAction(PortalBookingChoiceService.UNDO_FAILED));
            PortalBookingChoiceService.logJob(PortalBookingChoiceService.UNDO_FAILED,
                    offer == null ? null : offer.getAppointmentNo(), jobKeyId, choice.choiceId());
        }
    }
}
