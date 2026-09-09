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
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalException;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalInviteDto;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalService;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalStaffContext;
import io.github.carlos_emr.carlos.integration.patientportal.PortalStaffContextResolver;
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
 * Patient-scoped invitation revocation. Issuance remains unavailable until durable delivery and
 * atomic replacement are implemented together with the portal; a read-before-create guard is unsafe.
 */
public class PortalInvite2Action extends PortalJsonAction {
    private static final long serialVersionUID = 1L;
    static final String METHOD_CREATE = "create";
    static final String METHOD_RESEND = "resend";
    static final String METHOD_REVOKE = "revoke";

    private final transient SecurityInfoManager securityInfoManager;
    private final transient PortalStaffContextResolver staffContextResolver;

    public PortalInvite2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class), null,
                SpringUtils.getBean(PortalStaffContextResolver.class));
    }

    PortalInvite2Action(SecurityInfoManager securityInfoManager, PatientPortalService service,
            PortalStaffContextResolver staffContextResolver) {
        super(service);
        this.securityInfoManager = securityInfoManager;
        this.staffContextResolver = staffContextResolver;
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
        if (METHOD_CREATE.equals(method) || METHOD_RESEND.equals(method)) {
            return invitationUnavailable(response);
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
            PatientPortalInviteDto invite = portal.revokeInvite(inviteId, staff);
            if (invite.id() != inviteId || invite.demographicNo() != patient) {
                return portalFailure(response, PatientPortalException.ofMalformedResponse(200,
                        "/internal/carlos/invites/{id}/revoke", null));
            }
            ObjectNode payload = newPayload();
            payload.put("ok", true);
            payload.put("inviteId", invite.id());
            payload.put("status", invite.status());
            return write(response, HttpServletResponse.SC_OK, payload);
        } catch (PatientPortalException exception) {
            return portalFailure(response, exception);
        }
    }

    private boolean belongsToPatient(PatientPortalService portal, int patient, long id,
            PatientPortalStaffContext staff) {
        // The current portal contract exposes only its latest 100 invites, without pagination.
        // Never authorize an ID absent from that patient-scoped response.
        List<PatientPortalInviteDto> invites = portal.listInvites(patient, staff);
        return invites.stream().anyMatch(invite -> invite.id() == id && invite.demographicNo() == patient);
    }
}
