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

    /**
     * A 404 here almost always means the patient never activated, not a broken connection.
     *
     * <p>Found by running against a live portal: unlocking a patient with no account returned the
     * generic "the portal connection needs checking", which sends staff to debug infrastructure
     * over the most ordinary situation there is. Every route on this action is patient-scoped, so
     * the patient reading is the right default; a genuine configuration fault shows up as every
     * call failing, which the panel makes obvious.
     */
    private static final String NO_ACCOUNT_MESSAGE =
            """
            This patient does not have a patient portal account, so there is nothing to unlock or \
            disable. Send them an invitation first.""";

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
    String notFoundMessage() {
        return NO_ACCOUNT_MESSAGE;
    }

    @Override
    public String execute() throws IOException {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        // Before anything else: both routes here mutate.
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return methodNotAllowed(response);
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String method = request.getParameter("method");
        // Unlock is deliberately a different object from account management.
        String securityObject =
                METHOD_UNLOCK.equals(method)
                        ? PortalStaffContextResolver.OBJECT_ACCOUNT_UNLOCK
                        : PortalStaffContextResolver.OBJECT_ACCOUNT;
        requirePrivilege(securityInfoManager, loggedInInfo, securityObject);

        PatientPortalService portal = portalService();
        if (portal == null) {
            return portalNotConfigured(response);
        }

        PatientPortalStaffContext staff = staffContextResolver.resolve(loggedInInfo);
        int demographicNo = positiveInt(request.getParameter("demographicNo"));
        if (demographicNo <= 0) {
            return badRequest(response, "a patient must be selected");
        }
        try {
            return switch (method == null ? "" : method) {
                case METHOD_UNLOCK -> unlock(portal, response, demographicNo, staff);
                case METHOD_ACCESS -> access(portal, request, response, demographicNo, staff);
                default -> badRequest(response, UNKNOWN_METHOD);
            };
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
        ObjectNode payload = objectMapper().createObjectNode();
        payload.put("ok", true);
        payload.put("accountId", account.id());
        payload.put("locked", account.locked());
        payload.put("forcePasswordReset", account.forcePasswordReset());
        payload.put("note", UNLOCK_NOTE);
        return write(response, HttpServletResponse.SC_OK, payload);
    }

    /**
     * Enables or disables the account.
     *
     * <p>A reason is required when disabling. The portal records it against the account, and
     * "disabled, no stated reason" is the state a later reviewer cannot interpret; asking at the
     * point of action costs one field and saves reconstructing intent from an audit trail.
     */
    private String access(
            PatientPortalService portal,
            HttpServletRequest request,
            HttpServletResponse response,
            int demographicNo,
            PatientPortalStaffContext staff)
            throws IOException {
        boolean enabled = Boolean.parseBoolean(request.getParameter("enabled"));
        String reason = request.getParameter("reason");
        boolean reasonMissing = reason == null || reason.isBlank();
        if (!enabled && reasonMissing) {
            return badRequest(response, REASON_REQUIRED);
        }
        PatientPortalAccountAcknowledgementDto account =
                portal.setAccountAccess(
                        demographicNo, enabled, reasonMissing ? "staff_action" : reason.strip(),
                        staff);
        ObjectNode payload = objectMapper().createObjectNode();
        payload.put("ok", true);
        payload.put("accountId", account.id());
        payload.put("status", account.status());
        payload.put("enabled", enabled);
        return write(response, HttpServletResponse.SC_OK, payload);
    }
}
