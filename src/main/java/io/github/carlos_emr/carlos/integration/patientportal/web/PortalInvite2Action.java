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
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.PatientPortalInviteDelivery;
import io.github.carlos_emr.carlos.commn.model.PatientPortalInviteDelivery.Channel;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalException;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalInviteDto;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalService;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalStaffContext;
import io.github.carlos_emr.carlos.integration.patientportal.PortalInviteDeliveryService;
import io.github.carlos_emr.carlos.integration.patientportal.PortalInviteDeliveryService.Decision;
import io.github.carlos_emr.carlos.integration.patientportal.PortalInviteDeliveryService.InviteRequest;
import io.github.carlos_emr.carlos.integration.patientportal.PortalInviteException;
import io.github.carlos_emr.carlos.integration.patientportal.PortalStaffContextResolver;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.apache.struts2.ServletActionContext;

/**
 * Patient-scoped invitations: invite, resend, revoke, and resolve an unfinished delivery.
 *
 * <p>{@code method=create} invites the patient by email; when a pending invitation exists it requires
 * {@code confirmReplace=true} and then resends that invitation. {@code method=resend} replaces the
 * invitation named by {@code inviteId}. Both run {@link PortalInviteDeliveryService}, which commits the
 * invitation on the portal only once the email carrying it is durable. {@code method=recover} applies a
 * staff {@code decision} to the attempt named by {@code deliveryId}. {@code method=revoke} withdraws an
 * invitation.
 *
 * <p>Every route needs {@code _portal.invite} write for the patient; sending also needs {@code _email}
 * write, which the email layer enforces for every patient email.
 */
public class PortalInvite2Action extends PortalJsonAction {
    private static final long serialVersionUID = 1L;
    static final String METHOD_CREATE = "create";
    static final String METHOD_RESEND = "resend";
    static final String METHOD_RECOVER = "recover";
    static final String METHOD_REVOKE = "revoke";
    private static final int MAX_OVERRIDE_REASON_LENGTH = 255;

    private final transient SecurityInfoManager securityInfoManager;
    private final transient PortalStaffContextResolver staffContextResolver;
    private final transient DemographicManager demographicManager;

    public PortalInvite2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class), null,
                SpringUtils.getBean(PortalStaffContextResolver.class), null,
                SpringUtils.getBean(DemographicManager.class));
    }

    PortalInvite2Action(SecurityInfoManager securityInfoManager, PatientPortalService service,
            PortalStaffContextResolver staffContextResolver) {
        this(securityInfoManager, service, staffContextResolver, null, null);
    }

    PortalInvite2Action(SecurityInfoManager securityInfoManager, PatientPortalService service,
            PortalStaffContextResolver staffContextResolver, PortalInviteDeliveryService inviteService,
            DemographicManager demographicManager) {
        super(service, inviteService);
        this.securityInfoManager = securityInfoManager;
        this.staffContextResolver = staffContextResolver;
        this.demographicManager = demographicManager;
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
        requirePatientAccess(securityInfoManager, session, patient);
        requirePatientPrivilege(securityInfoManager, session,
                PortalStaffContextResolver.OBJECT_INVITE, SecurityInfoManager.WRITE, patient);
        String method = request.getParameter("method");
        if (METHOD_CREATE.equals(method) || METHOD_RESEND.equals(method) || METHOD_RECOVER.equals(method)) {
            return deliver(request, response, session, patient, method);
        }
        if (!METHOD_REVOKE.equals(method)) {
            return badRequest(response, "unsupported portal invite action");
        }
        long inviteId = positiveLong(request.getParameter("inviteId"));
        if (inviteId <= 0) {
            return badRequest(response, "an invitation must be selected");
        }
        PatientPortalService portal = portalService();
        if (portal == null) {
            return portalNotConfigured(response);
        }
        PatientPortalStaffContext staff = staffContextResolver.resolveForPatient(session,
                Set.of(PortalStaffContextResolver.OBJECT_INVITE), patient);
        try {
            if (!belongsToPatient(portal, patient, inviteId, staff)) {
                return notFound(response, "invite_not_verified",
                        "The selected invitation could not be verified for this patient. Refresh the panel.");
            }
            PatientPortalInviteDto invite = portal.revokeInvite(patient, inviteId, staff);
            ObjectNode payload = newPayload();
            payload.put("ok", true);
            payload.put("inviteId", invite.id());
            payload.put("status", invite.status());
            return write(response, HttpServletResponse.SC_OK, payload);
        } catch (PatientPortalException exception) {
            return portalFailure(response, exception);
        }
    }

    private String deliver(HttpServletRequest request, HttpServletResponse response, LoggedInInfo session,
            int patient, String method) throws IOException {
        // Every route here writes to the email outbox: sending creates a row, and resolving an
        // unfinished delivery closes one, which is the privilege ManageEmails requires to do the same.
        if (!securityInfoManager.hasPrivilege(session, "_email", SecurityInfoManager.WRITE, null)) {
            throw new SecurityException("missing required sec object (_email)");
        }
        boolean sends = !METHOD_RECOVER.equals(method);
        long inviteId = 0;
        if (METHOD_RESEND.equals(method)) {
            inviteId = positiveLong(request.getParameter("inviteId"));
            if (inviteId <= 0) {
                return badRequest(response, "an invitation must be selected");
            }
        }
        long deliveryId = 0;
        Decision decision = null;
        if (!sends) {
            deliveryId = positiveLong(request.getParameter("deliveryId"));
            decision = Decision.parse(request.getParameter("decision"));
            if (deliveryId <= 0 || decision == null) {
                return badRequest(response, "a delivery and a decision must be selected");
            }
        }
        Channel channel = channel(request.getParameter("channel"));
        if (channel == null) {
            return badRequest(response, "unsupported invitation channel");
        }
        String overrideReason = request.getParameter("consentOverrideReason");
        if (overrideReason != null && overrideReason.length() > MAX_OVERRIDE_REASON_LENGTH) {
            return badRequest(response, "the consent override reason is too long");
        }
        PortalInviteDeliveryService invites = inviteDeliveryService();
        if (invites == null) {
            return portalNotConfigured(response);
        }
        Demographic demographic = demographicManager.getDemographic(session, patient);
        if (demographic == null) {
            return notFound(response, "patient_not_found", "The patient could not be found. Refresh the page.");
        }
        PatientPortalStaffContext staff = staffContextResolver.resolveForPatient(session,
                Set.of(PortalStaffContextResolver.OBJECT_INVITE), patient);
        InviteRequest invite = new InviteRequest(channel, "true".equals(request.getParameter("confirmReplace")),
                "true".equals(request.getParameter("consentOverride")), overrideReason);
        try {
            PatientPortalInviteDelivery row = switch (method) {
                case METHOD_CREATE -> invites.invite(session, demographic, staff, invite);
                case METHOD_RESEND -> invites.resend(session, demographic, inviteId, staff, invite);
                default -> invites.recover(session, demographic, deliveryId, decision, staff);
            };
            ObjectNode payload = newPayload();
            payload.put("ok", true);
            InviteDeliveryJson.write(payload.putObject("delivery"), row, invites);
            return write(response, HttpServletResponse.SC_OK, payload);
        } catch (PortalInviteException exception) {
            return inviteRefused(response, exception);
        } catch (PatientPortalException exception) {
            return portalFailure(response, exception);
        }
    }

    /** Parses {@code channel}; absent means email. */
    private static Channel channel(String value) {
        if (value == null || value.isEmpty() || "email".equals(value)) {
            return Channel.EMAIL;
        }
        return "sms".equals(value) ? Channel.SMS : null;
    }

    private boolean belongsToPatient(PatientPortalService portal, int patient, long id,
            PatientPortalStaffContext staff) {
        // The current portal contract exposes only its latest 100 invites, without pagination.
        // Never authorize an ID absent from that patient-scoped response.
        List<PatientPortalInviteDto> invites = portal.listInvites(patient, staff);
        return invites.stream().anyMatch(invite -> invite.id() == id && invite.demographicNo() == patient);
    }
}
