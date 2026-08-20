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
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalException;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalInviteDto;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalIssuedInviteDto;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalService;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalStaffContext;
import io.github.carlos_emr.carlos.integration.patientportal.PortalInviteIdentityValidator;
import io.github.carlos_emr.carlos.integration.patientportal.PortalStaffContextResolver;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ServletActionContext;

/**
 * Staff actions on a patient's portal invitations: create, resend, revoke.
 *
 * <p>Every route here mutates, so the action rejects {@code GET} and {@code HEAD} unconditionally
 * before any side effect. Reading the invite list belongs to the panel's own read action; keeping
 * the two apart is what lets this one be classified as an unconditional mutator rather than needing
 * a mutation-intent parameter to tell them apart.
 *
 * <p><b>The replace guard is the point of this class.</b> The portal's create endpoint is not
 * idempotent and not a no-op: a second create returns {@code 201}, mints a new token, and silently
 * moves every earlier pending invite to {@code revoked}. A token already emailed to the patient
 * stops working, with no error anywhere. So a create that would replace a pending invite is refused
 * unless the caller passes {@code confirmReplace=true}, which the UI sets only after telling staff
 * what they are about to invalidate. Documenting that hazard was not enough; two clicks would still
 * strand the first email.
 *
 * <p>Responses are JSON written directly, so every path returns {@link #NONE} rather than a named
 * result — a named result would resolve to a JSP forward and append HTML to the JSON body.
 *
 * @since 2026-08-19
 */
public class PortalInvite2Action extends PortalJsonAction {

    private static final long serialVersionUID = 1L;

    private static final Logger logger = MiscUtils.getLogger();

    static final String METHOD_CREATE = "create";
    static final String METHOD_RESEND = "resend";
    static final String METHOD_REVOKE = "revoke";

    private static final String NEEDS_CONFIRMATION =
            "This patient already has a pending invitation. Sending a new one immediately"
                    + " invalidates the previous link, so any email already sent will stop working.";
    private static final String INCOMPLETE_RECORD =
            "This patient's record is missing %s. A portal invitation cannot be activated without"
                    + " it.";
    private static final String CANNOT_CHECK =
            """
            CARLOS could not check this patient's existing invitations, so it cannot tell whether \
            sending a new one would invalidate a link they already have. Confirm only if you are \
            sure no invitation is outstanding.""";
    private static final String UNKNOWN_METHOD = "unsupported portal invite action";
    private static final String LIST_FAILED =
            "could not read the invite list before a create; treating the patient as at risk";

    /**
     * Statuses that mean an invitation is already finished, so replacing it costs nothing.
     *
     * <p>Deliberately a list of terminal states rather than a test for {@code "pending"}. Status is
     * optional free text that the portal is not required to keep stable, and the failure this class
     * exists to prevent is a working invitation being silently revoked. Testing for "pending" fails
     * <em>open</em>: rename the status portal-side, change its casing, or drop the field from a
     * payload revision, and every invite reads as not-pending, the guard stops firing, and the next
     * create strands a link the patient is holding. Testing for terminal states fails closed, so a
     * vocabulary change costs an extra confirmation click rather than a patient's access. Casing is
     * not normalised for the same reason — an unexpected form should count as at risk.
     */
    private static final Set<String> TERMINAL_STATUSES =
            Set.of("revoked", "accepted", "superseded");

    /** What CARLOS was able to establish about invitations a create would invalidate. */
    private enum PendingInvites {
        /** At least one invitation may still be usable, so a create would revoke it. */
        AT_RISK,
        /** Every invitation is in a terminal state; nothing is lost by creating another. */
        NONE,
        /** The list could not be read, so CARLOS cannot tell either way. */
        UNKNOWN
    }

    private final transient SecurityInfoManager securityInfoManager;
    private final transient DemographicManager demographicManager;
    private final transient PortalStaffContextResolver staffContextResolver;
    private final transient PortalInviteIdentityValidator identityValidator;

    /** Struts instantiates actions reflectively, so the wiring happens here. */
    public PortalInvite2Action() {
        this(
                SpringUtils.getBean(SecurityInfoManager.class),
                SpringUtils.getBean(DemographicManager.class),
                null,
                SpringUtils.getBean(PortalStaffContextResolver.class),
                new PortalInviteIdentityValidator());
    }

    PortalInvite2Action(
            SecurityInfoManager securityInfoManager,
            DemographicManager demographicManager,
            PatientPortalService patientPortalService,
            PortalStaffContextResolver staffContextResolver,
            PortalInviteIdentityValidator identityValidator) {
        super(patientPortalService);
        this.securityInfoManager = securityInfoManager;
        this.demographicManager = demographicManager;
        this.staffContextResolver = staffContextResolver;
        this.identityValidator = identityValidator;
    }

    @Override
    public String execute() throws IOException {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        // Before anything else: every route here mutates.
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return methodNotAllowed(response);
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        requirePrivilege(
                securityInfoManager, loggedInInfo, PortalStaffContextResolver.OBJECT_INVITE);

        PatientPortalService portal = portalService();
        if (portal == null) {
            return portalNotConfigured(response);
        }

        PatientPortalStaffContext staff =
                staffContextResolver.resolve(
                        loggedInInfo, Set.of(PortalStaffContextResolver.OBJECT_INVITE));
        String method = request.getParameter("method");
        try {
            return switch (method == null ? "" : method) {
                case METHOD_CREATE -> create(portal, request, response, loggedInInfo, staff);
                case METHOD_RESEND -> resend(portal, request, response, staff);
                case METHOD_REVOKE -> revoke(portal, request, response, staff);
                default -> badRequest(response, UNKNOWN_METHOD);
            };
        } catch (PatientPortalException exception) {
            return portalFailure(response, exception);
        }
    }

    private String create(
            PatientPortalService portal,
            HttpServletRequest request,
            HttpServletResponse response,
            LoggedInInfo loggedInInfo,
            PatientPortalStaffContext staff)
            throws IOException {
        int demographicNo = demographicNo(request);
        if (demographicNo <= 0) {
            return badRequest(response, "a patient must be selected");
        }

        Demographic demographic = demographicManager.getDemographic(loggedInInfo, demographicNo);
        PortalInviteIdentityValidator.Result identity = identityValidator.validate(demographic);
        if (!identity.isUsable()) {
            return conflict(
                    response,
                    "incomplete_record",
                    String.format(
                            Locale.ROOT,
                            INCOMPLETE_RECORD,
                            String.join(", ", identity.missingFields())));
        }

        // The replace guard. See the class notes: a second create silently revokes the first.
        if (!Boolean.parseBoolean(request.getParameter("confirmReplace"))) {
            PendingInvites pending = pendingInvites(portal, demographicNo, staff);
            if (pending == PendingInvites.AT_RISK) {
                return conflict(response, "confirm_replace", NEEDS_CONFIRMATION);
            }
            if (pending == PendingInvites.UNKNOWN) {
                return conflict(response, "pending_check_failed", CANNOT_CHECK);
            }
        }

        PatientPortalIssuedInviteDto issued =
                portal.createInvite(
                        demographicNo,
                        identity.email(),
                        identity.dateOfBirth(),
                        identity.healthCardNumber(),
                        staff);
        return issuedInvite(response, issued);
    }

    /**
     * Reports whether a create would invalidate an invitation the patient may still be holding.
     *
     * <p>A read failure is reported as {@link PendingInvites#UNKNOWN} rather than folded into
     * "there is a pending invitation". Both refuse the create, which is the right direction — a
     * portal hiccup must not be the reason a working invitation is silently revoked — but only one
     * of them is true. Answering {@code confirm_replace} when the list could not be read asserts a
     * fact about portal state at the exact moment that state is unknown, and it is wrong for every
     * patient while the list endpoint is broken. Staff learn the warning is noise, click through it
     * by reflex, and the guard is then worth nothing when a real pending invitation is at stake.
     */
    private PendingInvites pendingInvites(
            PatientPortalService portal, int demographicNo, PatientPortalStaffContext staff) {
        try {
            List<PatientPortalInviteDto> invites =
                    portal.listInvites(
                            demographicNo, PatientPortalService.MAX_INVITE_PAGE_SIZE, staff);
            return invites.stream().anyMatch(PortalInvite2Action::mayStillBeUsable)
                    ? PendingInvites.AT_RISK
                    : PendingInvites.NONE;
        } catch (PatientPortalException exception) {
            // Without this the only evidence that listInvites is broken is staff reporting that
            // every patient shows a pending invitation.
            logger.error(LIST_FAILED, exception);
            return PendingInvites.UNKNOWN;
        }
    }

    private static boolean mayStillBeUsable(PatientPortalInviteDto invite) {
        return invite.status() == null || !TERMINAL_STATUSES.contains(invite.status());
    }

    private String resend(
            PatientPortalService portal, HttpServletRequest request,
            HttpServletResponse response, PatientPortalStaffContext staff)
            throws IOException {
        long inviteId = inviteId(request);
        if (inviteId <= 0) {
            return badRequest(response, "an invitation must be selected");
        }
        return issuedInvite(response, portal.resendInvite(inviteId, staff));
    }

    private String revoke(
            PatientPortalService portal, HttpServletRequest request,
            HttpServletResponse response, PatientPortalStaffContext staff)
            throws IOException {
        long inviteId = inviteId(request);
        if (inviteId <= 0) {
            return badRequest(response, "an invitation must be selected");
        }
        PatientPortalInviteDto invite = portal.revokeInvite(inviteId, staff);
        ObjectNode payload = objectMapper().createObjectNode();
        payload.put("ok", true);
        payload.put("inviteId", invite.id());
        payload.put("status", invite.status());
        return write(response, HttpServletResponse.SC_OK, payload);
    }

    /**
     * Writes the issued invite without its token.
     *
     * <p>The activation token is a credential and is never returned to the browser. Delivery is
     * CARLOS's own workflow; putting the token in a JSON response would put it in the page, the
     * browser cache, and any proxy log between here and the workstation.
     */
    private String issuedInvite(HttpServletResponse response, PatientPortalIssuedInviteDto issued)
            throws IOException {
        ObjectNode payload = objectMapper().createObjectNode();
        payload.put("ok", true);
        payload.put("inviteId", issued.invite().id());
        payload.put("status", issued.invite().status());
        payload.put(
                "expiresAt",
                issued.invite().expiresAt() == null
                        ? null
                        : issued.invite().expiresAt().toString());
        return write(response, HttpServletResponse.SC_OK, payload);
    }

    private static int demographicNo(HttpServletRequest request) {
        return positiveInt(request.getParameter("demographicNo"));
    }

    private static long inviteId(HttpServletRequest request) {
        return positiveLong(request.getParameter("inviteId"));
    }
}
