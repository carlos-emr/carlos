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
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalException;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalService;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalStaffContext;
import io.github.carlos_emr.carlos.integration.patientportal.PortalRequestPreparationException;
import io.github.carlos_emr.carlos.integration.patientportal.PortalStaffContextResolver;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.apache.struts2.ServletActionContext;

/**
 * Staff booking-prompt JSON contract. Lists need patient-specific read; changes need write.
 * Creating also needs account read, because the patient must have an active portal account.
 * The CSRFGuard filter protects every POST, including the read operation.
 */
public class PortalBookingPrompt2Action extends PortalJsonAction {
    private static final long serialVersionUID = 1L;
    private final transient SecurityInfoManager security;
    private final transient PortalStaffContextResolver resolver;

    public PortalBookingPrompt2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class), null,
                SpringUtils.getBean(PortalStaffContextResolver.class));
    }

    PortalBookingPrompt2Action(SecurityInfoManager security, PatientPortalService service,
            PortalStaffContextResolver resolver) {
        super(service);
        this.security = security;
        this.resolver = resolver;
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
        if (!Set.of("create", "list", "panel", "withdraw").contains(method == null ? "" : method)) {
            return badRequest(response, "unsupported booking prompt action");
        }
        requirePatientPrivilege(security, session, PortalStaffContextResolver.OBJECT_BOOKING_PROMPT,
                "list".equals(method) || "panel".equals(method)
                        ? SecurityInfoManager.READ : SecurityInfoManager.WRITE, patient);
        boolean mayReadAccount = "panel".equals(method) && security.hasPrivilege(session,
                PortalStaffContextResolver.OBJECT_ACCOUNT, SecurityInfoManager.READ, String.valueOf(patient));
        PatientPortalBookingPromptRequest creation = null;
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
                "create".equals(method) || mayReadAccount
                        ? Set.of(PortalStaffContextResolver.OBJECT_BOOKING_PROMPT,
                                PortalStaffContextResolver.OBJECT_ACCOUNT)
                        : Set.of(PortalStaffContextResolver.OBJECT_BOOKING_PROMPT), patient);
        boolean mutationAttempted = false;
        try {
            ObjectNode payload = newPayload();
            payload.put("ok", true);
            if ("panel".equals(method)) {
                boolean mayWrite = security.hasPrivilege(session, PortalStaffContextResolver.OBJECT_BOOKING_PROMPT,
                        SecurityInfoManager.WRITE, String.valueOf(patient));
                payload.put("mayWithdraw", mayWrite);
                payload.put("mayCreate", mayWrite && mayReadAccount);
                if (mayReadAccount) {
                    try {
                        payload.put("accountActive", "active".equals(portal.findAccount(patient, staff).status()));
                    } catch (PatientPortalException exception) {
                        if (!exception.isAccountAbsent()) {
                            throw exception;
                        }
                        payload.put("accountActive", false);
                    }
                }
            }
            if ("create".equals(method)) {
                if (!"active".equals(portal.findAccount(patient, staff).status())) {
                    return notFound(response, "portal_account_inactive",
                            "This patient does not have an active portal account. Contact the patient directly.");
                }
                mutationAttempted = true;
                PatientPortalBookingPromptDto.Creation result =
                        portal.createBookingPrompt(patient, creation, staff);
                audit(session, result.created() ? "PortalBookingPrompt2Action.create"
                        : "PortalBookingPrompt2Action.create.confirmed", result.prompt().id(), patient,
                        result.created() ? "" : "retry");
                payload.set("prompt", promptJson(result.prompt()));
                payload.put("created", result.created());
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
