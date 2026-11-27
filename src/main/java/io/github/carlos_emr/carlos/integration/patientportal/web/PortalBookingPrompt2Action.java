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
package io.github.carlos_emr.carlos.integration.patientportal.web;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalBookingPromptDto;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalBookingPromptRequest;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalOfferedSlot;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalException;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalService;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalStaffContext;
import io.github.carlos_emr.carlos.integration.patientportal.PortalRequestPreparationException;
import io.github.carlos_emr.carlos.integration.patientportal.PortalStaffContextResolver;
import io.github.carlos_emr.carlos.integration.patientportal.booking.PortalBookingOfferService;
import io.github.carlos_emr.carlos.integration.patientportal.booking.PortalBookingSettings;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ServletActionContext;

/**
 * Staff booking-prompt JSON contract. Lists need patient-specific read; changes need write.
 * Creating checks a narrow eligibility endpoint using booking permission alone.
 * Offering open times with a prompt (#3850) also needs schedule write, because the patient's pick
 * becomes a booking on that provider's schedule. The CSRFGuard filter protects every POST, including the read operation.
 */
public class PortalBookingPrompt2Action extends PortalJsonAction {
    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = MiscUtils.getLogger();
    private final transient SecurityInfoManager security;
    private final transient PortalStaffContextResolver resolver;
    private final transient PortalBookingOfferService injectedOffers;
    private final transient Supplier<PortalBookingSettings> settings;
    /** Schedule write: an offered time the patient picks becomes a booking. */
    static final String OBJECT_APPOINTMENT = "_appointment";

    public PortalBookingPrompt2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class), null,
                SpringUtils.getBean(PortalStaffContextResolver.class));
    }

    PortalBookingPrompt2Action(SecurityInfoManager security, PatientPortalService service,
            PortalStaffContextResolver resolver) {
        this(security, service, resolver, null);
    }

    PortalBookingPrompt2Action(SecurityInfoManager security, PatientPortalService service,
            PortalStaffContextResolver resolver, PortalBookingOfferService offers) {
        this(security, service, resolver, offers, PortalBookingSettings::fromCarlosProperties);
    }

    /** The clinic's booking settings come from {@code settings}, so a test can say what they are. */
    PortalBookingPrompt2Action(SecurityInfoManager security, PatientPortalService service,
            PortalStaffContextResolver resolver, PortalBookingOfferService offers,
            Supplier<PortalBookingSettings> settings) {
        super(service);
        this.security = security;
        this.resolver = resolver;
        this.injectedOffers = offers;
        this.settings = settings;
    }

    /** Looked up only when times are offered, so a prompt without times needs no schedule beans. */
    private PortalBookingOfferService offers() {
        return injectedOffers != null ? injectedOffers : SpringUtils.getBean(PortalBookingOfferService.class);
    }

    /** The first and last day to look for open times. */
    record Window(LocalDate from, LocalDate to) {}

    /** Which provider's schedule to offer from, and the window; null when no times are offered. */
    record OfferRequest(String providerNo, LocalDate from, LocalDate to, int count) {
        static final int DEFAULT_COUNT = 4;
        static final int DEFAULT_WITHIN_DAYS = 14;

        /** @throws IllegalArgumentException for a malformed provider or window */
        static OfferRequest parse(HttpServletRequest request, LocalDate today) {
            String provider = request.getParameter("offerFrom");
            if (provider == null || provider.isBlank()) {
                return null;
            }
            if (!provider.matches("[A-Za-z0-9-]{1,6}")) {
                throw new IllegalArgumentException("offered times provider is invalid");
            }
            Window window = window(request, today);
            int count = bounded(request.getParameter("offerCount"), DEFAULT_COUNT, 1,
                    PatientPortalOfferedSlot.MAX_PER_PROMPT);
            return new OfferRequest(provider, window.from(), window.to(), count);
        }

        /**
         * The days to offer from: {@code offerAfterDays} from today, for {@code offerWithinDays}
         * (default 14, at most 92), never past the portal's limit. Also what the staff dialog's
         * provider list is built for, so the list and the offer agree.
         *
         * @throws IllegalArgumentException for a malformed or out-of-range window
         */
        static Window window(HttpServletRequest request, LocalDate today) {
            int after = bounded(request.getParameter("offerAfterDays"), 0, 0, PortalBookingOfferService.MAX_DAYS_AHEAD);
            int within = bounded(request.getParameter("offerWithinDays"), DEFAULT_WITHIN_DAYS, 1, 92);
            LocalDate from = today.plusDays(after);
            // The portal refuses a time more than 366 days ahead.
            LocalDate last = today.plusDays(PortalBookingOfferService.MAX_DAYS_AHEAD);
            LocalDate to = from.plusDays(within - 1L);
            return new Window(from, to.isAfter(last) ? last : to);
        }

        private static int bounded(String value, int fallback, int min, int max) {
            if (value == null || value.isBlank()) {
                return fallback;
            }
            int parsed;
            try {
                parsed = Integer.parseInt(value.strip());
            } catch (NumberFormatException invalid) {
                throw new IllegalArgumentException("offered times window is invalid", invalid);
            }
            if (parsed < min || parsed > max) {
                throw new IllegalArgumentException("offered times window is invalid");
            }
            return parsed;
        }
    }

    @Override
    protected String handleRequest() throws IOException {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        if (!"POST".equals(request.getMethod())) {
            return methodNotAllowed(response);
        }
        LoggedInInfo session = LoggedInInfo.getLoggedInInfoFromSession(request);
        int patient = positiveInt(request.getParameter("demographicNo"));
        if (patient <= 0) {
            return badRequest(response, "a patient must be selected");
        }
        requirePatientAccess(security, session, patient);
        String method = request.getParameter("method");
        if (!Set.of("create", "list", "panel", "withdraw", "providers").contains(method == null ? "" : method)) {
            return badRequest(response, "unsupported booking prompt action");
        }
        requirePatientPrivilege(security, session, PortalStaffContextResolver.OBJECT_BOOKING_PROMPT,
                "list".equals(method) || "panel".equals(method)
                        ? SecurityInfoManager.READ : SecurityInfoManager.WRITE, patient);
        if ("providers".equals(method)) {
            return providers(request, response, session, patient);
        }
        PatientPortalBookingPromptRequest creation = null;
        OfferRequest offer = null;
        long promptId = 0;
        if ("create".equals(method)) {
            try {
                // Provider attribution needs a server-verified provider selection in the staff UI.
                // Until that exists, omit the optional name instead of accepting browser free text.
                creation = new PatientPortalBookingPromptRequest(request.getParameter("operationId"),
                        request.getParameter("urgency"), request.getParameter("appointmentType"), null);
            } catch (PortalRequestPreparationException invalid) {
                return badRequest(response, "booking prompt request is invalid");
            }
            try {
                offer = OfferRequest.parse(request, LocalDate.now());
            } catch (IllegalArgumentException invalid) {
                return badRequest(response, "offered times request is invalid");
            }
            if (offer != null) {
                requirePatientPrivilege(security, session, OBJECT_APPOINTMENT, SecurityInfoManager.WRITE, patient);
            }
        } else if ("withdraw".equals(method)) {
            promptId = positiveLong(request.getParameter("promptId"));
            if (promptId <= 0) {
                return badRequest(response, "a booking prompt must be selected");
            }
        }
        PatientPortalService portal = portalService();
        if (portal == null) {
            return portalNotConfigured(response);
        }
        PatientPortalStaffContext staff = resolver.resolveForPatient(session,
                Set.of(PortalStaffContextResolver.OBJECT_BOOKING_PROMPT), patient);
        boolean mutationAttempted = false;
        try {
            ObjectNode payload = newPayload();
            payload.put("ok", true);
            if ("panel".equals(method)) {
                boolean mayWrite = security.hasPrivilege(session, PortalStaffContextResolver.OBJECT_BOOKING_PROMPT,
                        SecurityInfoManager.WRITE, String.valueOf(patient));
                payload.put("mayWithdraw", mayWrite);
                payload.put("mayCreate", mayWrite);
                if (mayWrite) {
                    payload.put("accountActive", portal.isBookingEligible(patient, staff));
                    describeOffering(payload.putObject("offering"), session, patient);
                }
            }
            if ("create".equals(method)) {
                if (!portal.isBookingEligible(patient, staff)) {
                    return notFound(response, "portal_account_inactive",
                            "This patient does not have an active portal account. Contact the patient directly.");
                }
                if (offer != null) {
                    PortalBookingSettings clinic = settings.get();
                    List<PatientPortalOfferedSlot> times;
                    try {
                        times = offers().offer(creation.operationId(), patient, offer.providerNo(), offer.from(),
                                offer.to(), offer.count(), session.getLoggedInProviderNo(), clinic);
                    } catch (IllegalArgumentException reused) {
                        return badRequest(response, "booking prompt request is invalid");
                    }
                    if (times.isEmpty()) {
                        // Told apart: no codes chosen (offering is not set up) or no open time in the window.
                        // (An operation already offered times keeps them, so a retry never reaches here.)
                        if (clinic.offerableCodes().isEmpty()) {
                            return conflict(response, "offering_off",
                                    "Offering times is not set up: the clinic has not chosen which schedule template codes patients may book.");
                        }
                        return conflict(response, "no_open_times",
                                "No bookable times were found in that window. Choose another window, or ask the patient to call.");
                    }
                    creation = new PatientPortalBookingPromptRequest(creation.operationId(), creation.urgency(),
                            creation.appointmentType(), creation.suggestedBy(), times);
                }
                mutationAttempted = true;
                PatientPortalBookingPromptDto.Creation result =
                        portal.createBookingPrompt(patient, creation, staff);
                if (offer != null) {
                    try {
                        offers().attachPrompt(creation.operationId(), result.prompt().id());
                    } catch (RuntimeException unattached) {
                        // The prompt exists; its times still match by patient, so carry on and audit it.
                        LOGGER.warn("Offered times not linked to their portal prompt; exceptionClass={}",
                                unattached.getClass().getSimpleName());
                    }
                }
                audit(session, result.created() ? "PortalBookingPrompt2Action.create"
                        : "PortalBookingPrompt2Action.create.confirmed", result.prompt().id(), patient,
                        (result.created() ? "" : "retry")
                                + (creation.offeredSlots().isEmpty() ? "" : " offered:" + creation.offeredSlots().size()));
                payload.set("prompt", promptJson(result.prompt()));
                payload.put("created", result.created());
                payload.put("offeredTimes", creation.offeredSlots().size());
                return write(response, result.created() ? HttpServletResponse.SC_CREATED
                        : HttpServletResponse.SC_OK, payload);
            }
            var prompts = portal.listBookingPrompts(patient, staff);
            if ("list".equals(method) || "panel".equals(method)) {
                var items = payload.putArray("prompts");
                for (PatientPortalBookingPromptDto prompt : prompts) {
                    items.add(promptJson(prompt));
                }
            } else {
                long selectedId = promptId;
                boolean verified = prompts.stream().anyMatch(
                        prompt -> prompt.id() == selectedId && prompt.demographicNo() == patient);
                if (!verified) {
                    return notFound(response, "booking_prompt_not_verified",
                            "The selected booking prompt could not be verified for this patient. Refresh the panel.");
                }
                mutationAttempted = true;
                PatientPortalBookingPromptDto prompt = portal.withdrawBookingPrompt(patient, promptId, staff);
                audit(session, "PortalBookingPrompt2Action.withdraw", prompt.id(), patient, "");
                payload.set("prompt", promptJson(prompt));
            }
            return write(response, HttpServletResponse.SC_OK, payload);
        } catch (PatientPortalException exception) {
            if (mutationAttempted) {
                auditIfUnconfirmed(session, "PortalBookingPrompt2Action." + method, patient, exception);
            }
            return portalFailure(response, exception);
        }
    }

    /**
     * What the dialog needs to show the "offer open times" controls honestly: whether this user may
     * offer (schedule write as well as booking-prompt write), whether the clinic has chosen any
     * bookable template codes, how the times are shown to the patient (the clinic's one visit mode),
     * and the limits the controls enforce. Nothing patient-specific.
     */
    private void describeOffering(ObjectNode offering, LoggedInInfo session, int patient) {
        PortalBookingSettings clinic = settings.get();
        offering.put("mayOffer", security.hasPrivilege(session, OBJECT_APPOINTMENT, SecurityInfoManager.WRITE,
                String.valueOf(patient)));
        offering.put("codesConfigured", !clinic.offerableCodes().isEmpty());
        offering.put("visitMode", clinic.visitMode());
        offering.put("defaultWithinDays", OfferRequest.DEFAULT_WITHIN_DAYS);
        offering.put("maxWithinDays", 92);
        offering.put("maxAfterDays", PortalBookingOfferService.MAX_DAYS_AHEAD);
        offering.put("defaultCount", OfferRequest.DEFAULT_COUNT);
        offering.put("maxCount", PatientPortalOfferedSlot.MAX_PER_PROMPT);
    }

    /**
     * The providers with bookable time in the window, for the dialog's provider select: built here,
     * so the browser only sends back a number from this list. Read-only, but it follows the same
     * rule as offering (booking-prompt write, checked by the caller, and schedule write), because it
     * tells a user where bookable time is.
     */
    private String providers(HttpServletRequest request, HttpServletResponse response, LoggedInInfo session,
            int patient) throws IOException {
        requirePatientPrivilege(security, session, OBJECT_APPOINTMENT, SecurityInfoManager.WRITE, patient);
        Window window;
        try {
            window = OfferRequest.window(request, LocalDate.now());
        } catch (IllegalArgumentException invalid) {
            return badRequest(response, "offered times request is invalid");
        }
        PortalBookingSettings clinic = settings.get();
        ObjectNode payload = newPayload();
        payload.put("ok", true);
        payload.put("codesConfigured", !clinic.offerableCodes().isEmpty());
        var items = payload.putArray("providers");
        for (PortalBookingOfferService.OfferableProvider provider
                : offers().offerableProviders(window.from(), window.to(), clinic)) {
            ObjectNode item = items.addObject();
            item.put("providerNo", provider.providerNo());
            item.put("name", provider.name());
        }
        return write(response, HttpServletResponse.SC_OK, payload);
    }

    private ObjectNode promptJson(PatientPortalBookingPromptDto prompt) {
        ObjectNode node = newPayload();
        node.put("id", prompt.id());
        node.put("urgency", prompt.urgency());
        node.put("appointmentType", prompt.appointmentType());
        node.put("suggestedBy", prompt.suggestedBy());
        node.put("state", prompt.state());
        node.put("createdAt", prompt.createdAt().toString());
        node.put("expiresAt", prompt.expiresAt().toString());
        node.put("notifiedAt", prompt.notifiedAt() == null ? null : prompt.notifiedAt().toString());
        node.put("readAt", prompt.readAt() == null ? null : prompt.readAt().toString());
        node.put("withdrawnAt", prompt.withdrawnAt() == null ? null : prompt.withdrawnAt().toString());
        return node;
    }

}
