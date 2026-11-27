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
import io.github.carlos_emr.carlos.commn.model.PortalBookingOffer;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalOfferedSlot;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns open times into offers the portal can show (#3850): each gets an opaque random slot id,
 * recorded here with the provider and time it stands for.
 */
@Service
public class PortalBookingOfferService {
    /** 24 random bytes: 32 URL-safe characters, inside the portal's slot id alphabet. */
    private static final int SLOT_ID_BYTES = 24;
    /** Replacement times sent with one refused pick. */
    static final int MAX_REPLACEMENTS = 3;
    /** How far past the refused time replacements may be. */
    static final int REPLACEMENT_WINDOW_DAYS = 14;
    /** The portal refuses a time more than 366 days ahead; stay a day inside it. */
    public static final int MAX_DAYS_AHEAD = 365;
    /** {@code provider.status} of a provider who is still working. */
    private static final String ACTIVE = "1";

    private final PortalBookingOfferDao offers;
    private final PortalOfferedSlotLoader loader;
    private final ProviderDao providers;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public PortalBookingOfferService(PortalBookingOfferDao offers, PortalOfferedSlotLoader loader, ProviderDao providers) {
        this(offers, loader, providers, Clock.systemDefaultZone());
    }

    PortalBookingOfferService(PortalBookingOfferDao offers, PortalOfferedSlotLoader loader, ProviderDao providers,
            Clock clock) {
        this.offers = offers;
        this.loader = loader;
        this.providers = providers;
        this.clock = clock;
    }

    /**
     * Times to send with a new prompt. A retried operation gets exactly the times it was first
     * given, because the portal refuses a retry whose times differ; otherwise fresh open times.
     *
     * @throws IllegalArgumentException when the operation was already used for another patient, or
     *     the provider is unknown or no longer active
     */
    @Transactional
    public List<PatientPortalOfferedSlot> offer(String operationId, int demographicNo, String providerNo,
            LocalDate from, LocalDate to, int count, String offeredBy, PortalBookingSettings settings) {
        return offer(operationId, demographicNo, providerNo, from, to, count, offeredBy, settings, null);
    }

    /**
     * As above, with the visit mode these times are offered with ({@code null}: the clinic's
     * default, shown with the clinic setting as it is when the time is read). A mode must be one the
     * clinic enabled ({@link PortalBookingSettings#visitModes()}); it is stored with each offer, so
     * a replacement for a refused pick keeps it. A retried operation keeps the mode it was first
     * given and ignores this argument, like its times.
     *
     * @throws IllegalArgumentException as above, and when the mode is not enabled
     */
    @Transactional
    public List<PatientPortalOfferedSlot> offer(String operationId, int demographicNo, String providerNo,
            LocalDate from, LocalDate to, int count, String offeredBy, PortalBookingSettings settings,
            String visitMode) {
        List<PortalBookingOffer> existing = offers.findByOperation(operationId);
        if (!existing.isEmpty()) {
            if (existing.stream().anyMatch(offer -> offer.getDemographicNo() != demographicNo)) {
                throw new IllegalArgumentException("booking operation belongs to another patient");
            }
            return existing.stream().map(offer -> toSlot(offer, settings)).toList();
        }
        Provider provider = providers.getProvider(providerNo);
        if (provider == null || !ACTIVE.equals(provider.getStatus())) {
            throw new IllegalArgumentException("offered times provider is not an active provider");
        }
        if (visitMode != null && !settings.visitModes().contains(visitMode)) {
            throw new IllegalArgumentException("offered times visit mode is not enabled");
        }
        ZonedDateTime now = ZonedDateTime.now(clock);
        int wanted = Math.min(count, PatientPortalOfferedSlot.MAX_PER_PROMPT);
        List<PatientPortalOfferedSlot> slots = new ArrayList<>();
        for (var time : loader.load(providerNo, from, to, wanted, Set.of(), settings, now)) {
            slots.add(toSlot(record(time, operationId, null, demographicNo, offeredBy, null, visitMode, now),
                    settings));
        }
        return slots;
    }

    /** A provider the staff dialog may offer times from: the number the action accepts, and a name to show. */
    public record OfferableProvider(String providerNo, String name) {}

    /**
     * The active providers who have at least one time that {@link #offer} could offer between
     * {@code from} and {@code to}, for the staff dialog's provider list. Built here, on the server,
     * so the browser only ever sends back a provider number from this list (and the action still
     * checks it again). Empty when the clinic has chosen no bookable template codes.
     */
    public List<OfferableProvider> offerableProviders(LocalDate from, LocalDate to, PortalBookingSettings settings) {
        if (settings.offerableCodes().isEmpty()) {
            return List.of();
        }
        ZonedDateTime now = ZonedDateTime.now(clock);
        List<OfferableProvider> found = new ArrayList<>();
        for (Provider provider : providers.getActiveProviders(true)) {
            String number = provider.getProviderNo();
            // Only a number the action accepts can be offered from; an odd one is left out of the list.
            if (number == null || !number.matches("[A-Za-z0-9-]{1,6}") || !ACTIVE.equals(provider.getStatus())
                    || !loader.hasOpenTime(number, from, to, settings, now)) {
                continue;
            }
            found.add(new OfferableProvider(number, displayName(provider)));
        }
        found.sort((left, right) -> left.name().compareToIgnoreCase(right.name()));
        return List.copyOf(found);
    }

    private static String displayName(Provider provider) {
        String last = provider.getLastName() == null ? "" : provider.getLastName().strip();
        String first = provider.getFirstName() == null ? "" : provider.getFirstName().strip();
        String name = last.isEmpty() ? first : first.isEmpty() ? last : last + ", " + first;
        return name.isEmpty() ? provider.getProviderNo() : name;
    }

    /** The portal confirmed the prompt; its id lets a later pick be matched to its prompt. */
    @Transactional
    public void attachPrompt(String operationId, long promptId) {
        Date now = Date.from(clock.instant());
        for (PortalBookingOffer offer : offers.findByOperation(operationId)) {
            if (offer.getPromptId() == null) {
                offer.setPromptId(promptId);
                offer.setUpdatedAt(now);
            }
        }
    }

    /**
     * Fresh times after a refused pick, from the same provider, near the refused time. A retried
     * report gets the same replacements; there are never so many that more than 8 are on offer.
     *
     * <p>The booking-sync job offers these, not a person, so they are recorded as offered by the
     * built-in system provider.
     */
    @Transactional
    public List<PatientPortalOfferedSlot> replacementsFor(
            PortalBookingOffer refused, long promptId, long choiceId, PortalBookingSettings settings) {
        List<PortalBookingOffer> already = offers.findReplacements(refused.getOperationId(), choiceId);
        if (!already.isEmpty()) {
            return already.stream().map(offer -> toSlot(offer, settings)).toList();
        }
        ZonedDateTime now = ZonedDateTime.now(clock);
        List<PortalBookingOffer> open = offers.findOpenForOperation(refused.getOperationId(), Date.from(now.toInstant()));
        int room = Math.min(MAX_REPLACEMENTS, PatientPortalOfferedSlot.MAX_PER_PROMPT - open.size());
        if (room <= 0) {
            return List.of();
        }
        Set<ZonedDateTime> exclude = new HashSet<>();
        open.forEach(offer -> exclude.add(zoned(offer.getStartTime(), now)));
        ZonedDateTime refusedStart = zoned(refused.getStartTime(), now);
        exclude.add(refusedStart);
        // Around the refused time: from a week before it (not before today) to two weeks after it,
        // within a year of today.
        LocalDate today = now.toLocalDate();
        LocalDate from = refusedStart.toLocalDate().minusDays(7);
        if (from.isBefore(today)) {
            from = today;
        }
        LocalDate to = refusedStart.toLocalDate().plusDays(REPLACEMENT_WINDOW_DAYS);
        LocalDate last = today.plusDays(MAX_DAYS_AHEAD);
        if (to.isAfter(last)) {
            to = last;
        }
        List<PatientPortalOfferedSlot> slots = new ArrayList<>();
        for (var time : loader.load(refused.getProviderNo(), from, to, room, exclude, settings, now)) {
            // The mode of the time it replaces (null: the clinic's default, as for the refused one).
            slots.add(toSlot(record(time, refused.getOperationId(), promptId,
                    refused.getDemographicNo(), Provider.SYSTEM_PROVIDER_NO, choiceId, refused.getVisitMode(), now),
                    settings));
        }
        return slots;
    }

    private PortalBookingOffer record(PortalOfferedSlotLoader.OpenTime time, String operationId, Long promptId,
            int demographicNo, String offeredBy, Long refusedChoiceId, String visitMode, ZonedDateTime now) {
        PortalBookingOffer offer = new PortalBookingOffer();
        offer.setSlotId(newSlotId());
        offer.setOperationId(operationId);
        offer.setPromptId(promptId);
        offer.setDemographicNo(demographicNo);
        offer.setProviderNo(time.providerNo());
        offer.setStartTime(Date.from(time.start().toInstant()));
        offer.setDurationMinutes(time.durationMinutes());
        offer.setTemplateCode(String.valueOf(time.templateCode()));
        offer.setOfferedBy(offeredBy);
        offer.setVisitMode(visitMode);
        offer.setStatus(PortalBookingOffer.OFFERED);
        // A replacement remembers the refused pick it answers, so a retried report reuses it.
        offer.setChoiceId(refusedChoiceId);
        offer.setCreatedAt(Date.from(now.toInstant()));
        offer.setUpdatedAt(Date.from(now.toInstant()));
        offers.persist(offer);
        return offer;
    }

    private String newSlotId() {
        byte[] bytes = new byte[SLOT_ID_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private PatientPortalOfferedSlot toSlot(PortalBookingOffer offer, PortalBookingSettings settings) {
        return new PatientPortalOfferedSlot(offer.getSlotId(),
                OffsetDateTime.ofInstant(offer.getStartTime().toInstant(), clock.getZone()),
                offer.getDurationMinutes(),
                // The offer's own mode; an offer made without one (or before the column) has the
                // clinic's default.
                offer.getVisitMode() != null ? offer.getVisitMode() : settings.visitMode(),
                settings.locationCode());
    }

    private ZonedDateTime zoned(Date date, ZonedDateTime now) {
        return date.toInstant().atZone(now.getZone());
    }
}
