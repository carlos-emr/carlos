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
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.apache.struts2.ServletActionContext;

/**
 * Staff booking-prompt JSON contract. Lists need patient-specific read; changes need write.
 * Creating also needs account read, because the patient must have an active portal account.
 * Offering open times with a prompt (#3850) also needs schedule read, because they come from a
 * provider's schedule. The CSRFGuard filter protects every POST, including the read operation.
 */
public class PortalBookingPrompt2Action extends PortalJsonAction {
    private static final long serialVersionUID = 1L;
    private final transient SecurityInfoManager security;
    private final transient PortalStaffContextResolver resolver;
    private final transient PortalBookingOfferService injectedOffers;
    /** Schedule read: offered times come from a provider's schedule. */
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
        super(service);
        this.security = security;
        this.resolver = resolver;
        this.injectedOffers = offers;
    }

    /** Looked up only when times are offered, so a prompt without times needs no schedule beans. */
    private PortalBookingOfferService offers() {
        return injectedOffers != null ? injectedOffers : SpringUtils.getBean(PortalBookingOfferService.class);
    }

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
            int after = bounded(request.getParameter("offerAfterDays"), 0, 0, 365);
            int within = bounded(request.getParameter("offerWithinDays"), DEFAULT_WITHIN_DAYS, 1, 92);
            int count = bounded(request.getParameter("offerCount"), DEFAULT_COUNT, 1,
                    PatientPortalOfferedSlot.MAX_PER_PROMPT);
            LocalDate from = today.plusDays(after);
            return new OfferRequest(provider, from, from.plusDays(within - 1L), count);
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
        if (!Set.of("create", "list", "withdraw").contains(method == null ? "" : method)) {
            return badRequest(response, "unsupported booking prompt action");
        }
        requirePatientPrivilege(security, session, PortalStaffContextResolver.OBJECT_BOOKING_PROMPT,
                "list".equals(method) ? SecurityInfoManager.READ : SecurityInfoManager.WRITE, patient);
        PatientPortalBookingPromptRequest creation = null;
        OfferRequest offer = null;
        long promptId = 0;
        if ("create".equals(method)) {
            requirePatientPrivilege(security, session, PortalStaffContextResolver.OBJECT_ACCOUNT,
                    SecurityInfoManager.READ, patient);
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
                requirePatientPrivilege(security, session, OBJECT_APPOINTMENT, SecurityInfoManager.READ, patient);
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
                "create".equals(method)
                        ? Set.of(PortalStaffContextResolver.OBJECT_BOOKING_PROMPT,
                                PortalStaffContextResolver.OBJECT_ACCOUNT)
                        : Set.of(PortalStaffContextResolver.OBJECT_BOOKING_PROMPT), patient);
        boolean mutationAttempted = false;
        try {
            ObjectNode payload = newPayload();
            payload.put("ok", true);
            if ("create".equals(method)) {
                if (!"active".equals(portal.findAccount(patient, staff).status())) {
                    return notFound(response, "portal_account_inactive",
                            "This patient does not have an active portal account. Contact the patient directly.");
                }
                if (offer != null) {
                    List<PatientPortalOfferedSlot> times;
                    try {
                        times = offers().offer(creation.operationId(), patient, offer.providerNo(), offer.from(),
                                offer.to(), offer.count(), session.getLoggedInProviderNo(),
                                PortalBookingSettings.fromCarlosProperties());
                    } catch (IllegalArgumentException reused) {
                        return badRequest(response, "booking prompt request is invalid");
                    }
                    if (times.isEmpty()) {
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
                    offers().attachPrompt(creation.operationId(), result.prompt().id());
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
            if ("list".equals(method)) {
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
