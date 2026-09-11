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
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalAccountAcknowledgementDto;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalException;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalService;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalStaffContext;
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
 * Staff mutations on a patient's portal account: clear a lockout, disable, re-enable.
 *
 * <p>Both routes mutate, so {@code GET} and {@code HEAD} are rejected before any side effect.
 * Reading account status belongs to {@link PortalPanel2Action}; keeping the read elsewhere is what
 * lets this class be an unconditional mutator rather than one whose rejection depends on a
 * mutation-intent parameter.
 *
 * <p>Unlock and disable are gated on <b>different</b> security objects. Clearing a lockout forces
 * the patient through a password reset, which is a heavier act than disabling an account and belongs
 * to a narrower group; splitting the check here is what makes {@code _portal.account.unlock} mean
 * anything at all.
 *
 * @since 2026-08-19
 */
public class PortalAccount2Action extends PortalJsonAction {

    private static final long serialVersionUID = 1L;

    static final String METHOD_UNLOCK = "unlock";
    static final String METHOD_ACCESS = "access";

    /**
     * Staff-facing note on what an unlock actually does.
     *
     * <p>The portal sets {@code force_password_reset} on unlock, so a patient told only that their
     * account is "unlocked" will try their old password and fail. The note travels with the
     * response so the panel cannot forget to say it.
     */
    static final String UNLOCK_NOTE =
            """
            The lockout is cleared. The patient must complete a password reset before they can \
            sign in again.""";

    private static final String ENABLED_REQUIRED =
            "This request must state enabled=true or enabled=false.";
    private static final String UNKNOWN_METHOD = "unsupported portal account action";
    private static final String REASON_REQUIRED =
            "a reason is required when disabling a portal account";

    private final transient SecurityInfoManager securityInfoManager;
    private final transient PortalStaffContextResolver staffContextResolver;

    /** Struts instantiates actions reflectively, so the wiring happens here. */
    public PortalAccount2Action() {
        this(
                SpringUtils.getBean(SecurityInfoManager.class),
                null,
                SpringUtils.getBean(PortalStaffContextResolver.class));
    }

    PortalAccount2Action(
            SecurityInfoManager securityInfoManager,
            PatientPortalService patientPortalService,
            PortalStaffContextResolver staffContextResolver) {
        super(patientPortalService);
        this.securityInfoManager = securityInfoManager;
        this.staffContextResolver = staffContextResolver;
    }

    @Override
    protected String handleRequest() throws IOException {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        // Before anything else: both routes here mutate.
        if (!"POST".equals(request.getMethod())) {
            return methodNotAllowed(response);
        }

        String method = request.getParameter("method");
        if (!METHOD_UNLOCK.equals(method) && !METHOD_ACCESS.equals(method)) {
            return badRequest(response, UNKNOWN_METHOD);
        }
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        // Unlock is deliberately a different object from account management.
        String securityObject =
                METHOD_UNLOCK.equals(method)
                        ? PortalStaffContextResolver.OBJECT_ACCOUNT_UNLOCK
                        : PortalStaffContextResolver.OBJECT_ACCOUNT;
        int demographicNo = positiveInt(request.getParameter("demographicNo"));
        if (demographicNo <= 0) {
            return badRequest(response, "a patient must be selected");
        }
        requirePatientAccess(securityInfoManager, loggedInInfo, demographicNo);
        requirePatientPrivilege(
                securityInfoManager,
                loggedInInfo,
                securityObject,
                SecurityInfoManager.WRITE,
                demographicNo);

        PatientPortalService portal = portalService();
        if (portal == null) {
            return portalNotConfigured(response);
        }

        // Scoped to the object this route gated on, so an unlock does not also arrive at the
        // portal carrying authority to manage passphrases.
        PatientPortalStaffContext staff =
                staffContextResolver.resolveForPatient(loggedInInfo, Set.of(securityObject), demographicNo);
        try {
            if (METHOD_UNLOCK.equals(method)) {
                return unlock(portal, response, demographicNo, staff);
            }
            return access(portal, request, response, demographicNo, staff);
        } catch (PatientPortalException exception) {
            return portalFailure(response, exception);
        }
    }

    private String unlock(
            PatientPortalService portal, HttpServletResponse response, int demographicNo,
            PatientPortalStaffContext staff)
            throws IOException {
        PatientPortalAccountAcknowledgementDto account =
                portal.unlockAccount(demographicNo, staff);
        ObjectNode payload = newPayload();
        payload.put("ok", true);
        payload.put("accountId", account.id());
        payload.put("locked", account.lockedAt() != null);
        payload.put("forcePasswordReset", account.forcePasswordReset());
        payload.put("note", UNLOCK_NOTE);
        return write(response, HttpServletResponse.SC_OK, payload);
    }

    /**
     * Reads the {@code enabled} flag, or {@code null} when the caller did not state one.
     *
     * <p>{@code Boolean.parseBoolean} was used here and answers {@code false} for an absent
     * parameter, for {@code "1"}, for {@code "yes"}, and for every typo — so a malformed or dropped
     * parameter chose the destructive branch and disabled a patient's portal access, while the
     * reply reported {@code "enabled": false} quite truthfully about something nobody asked for.
     * A defaulted boolean must never pick between two opposite mutations.
     */
    private static Boolean enabledFlag(String raw) {
        String value = raw == null ? "" : raw.strip();
        if ("true".equals(value)) {
            return Boolean.TRUE;
        }
        if ("false".equals(value)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private String access(
            PatientPortalService portal,
            HttpServletRequest request,
            HttpServletResponse response,
            int demographicNo,
            PatientPortalStaffContext staff)
            throws IOException {
        Boolean enabled = enabledFlag(request.getParameter("enabled"));
        if (enabled == null) {
            return badRequest(response, ENABLED_REQUIRED);
        }
        boolean enabledValue = enabled;
        String reason = request.getParameter("reason");
        boolean reasonMissing = reason == null || reason.isBlank();
        if (!enabledValue && reasonMissing) {
            return badRequest(response, REASON_REQUIRED);
        }
        PatientPortalAccountAcknowledgementDto account =
                portal.setAccountAccess(
                        demographicNo,
                        enabledValue,
                        reasonMissing ? "staff_action" : reason.strip(),
                        staff);
        ObjectNode payload = newPayload();
        payload.put("ok", true);
        payload.put("accountId", account.id());
        payload.put("status", account.status());
        payload.put("enabled", enabledValue);
        payload.put("forcePasswordReset", account.forcePasswordReset());
        return write(response, HttpServletResponse.SC_OK, payload);
    }
}
