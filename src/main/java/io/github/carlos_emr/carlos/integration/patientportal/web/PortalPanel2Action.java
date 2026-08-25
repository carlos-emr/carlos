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

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalAccountDto;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalException;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalInviteDto;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalService;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalStaffContext;
import io.github.carlos_emr.carlos.integration.patientportal.PortalStaffContextResolver;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ServletActionContext;

/**
 * Read-only state for the demographic portal panel: existing invitations and account status.
 *
 * <p>Read-only, so {@code GET} is permitted — as is {@code POST}, for callers that already post
 * everything; only genuinely unsupported methods such as {@code DELETE} are refused. It exists as
 * its own action rather than as a method on the mutators so that those can be classified as
 * unconditional mutators — a class that both reads and writes needs a mutation-intent parameter to
 * tell the two apart, and that distinction is exactly the thing that gets broken later.
 *
 * <p>Each section is included only if the provider holds its object, so the panel shows what the
 * caller may actually act on rather than rendering controls that will fail. A section the caller
 * cannot read is <em>absent</em> from the payload rather than empty: an empty invite list means "no
 * invitations", and reporting that to someone merely lacking the privilege would be a lie the UI
 * would faithfully display.
 *
 * <p>Absence is therefore overloaded, and a caller has to read the error markers to disambiguate: a
 * section is also dropped when the portal read failed, in which case {@code invitesError} and
 * {@code invitesErrorKind} (or the account equivalents) are present alongside. Treating a missing
 * key as "no data" — the natural {@code payload.invites || []} idiom — would report an outage as an
 * empty list, which is why {@code ok} is the guard against that: it is {@code false} whenever a
 * section the caller asked for could not be read. It used to be hardcoded {@code true}, so the one
 * field a client would reasonably branch on was the one field that could not be wrong.
 *
 * <p>One portal failure does not blank the panel. If invitations load and the account lookup fails,
 * the invitations are still returned with an error noted against the account section — a portal
 * hiccup on one call should not make a patient appear to have no portal presence at all.
 *
 * @since 2026-08-19
 */
public class PortalPanel2Action extends PortalJsonAction {

    private static final long serialVersionUID = 1L;

    private static final String READ = "r";
    private static final String NO_ACCOUNT = "no_portal_account";
    private static final String SECTION_UNAVAILABLE = "unavailable";
    private static final String SECTION_FAILED_LOG =
            "patient portal panel section %s could not be read: kind=%s";

    private static final Logger logger = MiscUtils.getLogger();

    private final transient SecurityInfoManager securityInfoManager;
    private final transient PortalStaffContextResolver staffContextResolver;

    /** Struts instantiates actions reflectively, so the wiring happens here. */
    public PortalPanel2Action() {
        this(
                SpringUtils.getBean(SecurityInfoManager.class),
                null,
                SpringUtils.getBean(PortalStaffContextResolver.class));
    }

    PortalPanel2Action(
            SecurityInfoManager securityInfoManager,
            PatientPortalService patientPortalService,
            PortalStaffContextResolver staffContextResolver) {
        super(patientPortalService);
        this.securityInfoManager = securityInfoManager;
        this.staffContextResolver = staffContextResolver;
    }

    @Override
    String allowedMethods() {
        return "GET, POST";
    }

    @Override
    public String execute() throws IOException {
        try {
            return handle();
        } catch (SecurityException exception) {
            return forbidden(ServletActionContext.getResponse(), exception);
        }
    }

    // FindSecBugs IMPROPER_UNICODE: this compares an HTTP method token, matching
    // HttpMethodGuardFilter, which fronts these actions and uses equalsIgnoreCase for the
    // same purpose. String.equalsIgnoreCase is locale-independent, so the Turkish-I class the
    // detector is named for does not arise; and it is informational regardless of Locale, so
    // it cannot be cleared by adding Locale.ROOT. It is a gate rather than a display value, so
    // the boilerplate "not a security decision" justification would be untrue here: a
    // permissive fold would admit an oddly-cased token as the method. That grants nothing --
    // hasPrivilege runs on every path below regardless -- and the request still has to have
    // arrived as a mutation. See docs/static-analysis-workflows.md.
    @SuppressFBWarnings(
            value = "IMPROPER_UNICODE",
            justification =
                    "HTTP method token comparison, consistent with HttpMethodGuardFilter;"
                            + " equalsIgnoreCase is locale-independent and every path below"
                            + " still runs its own hasPrivilege check")
    private String handle() throws IOException {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        String method = request.getMethod();
        if (!"GET".equalsIgnoreCase(method) && !"POST".equalsIgnoreCase(method)) {
            return methodNotAllowed(response);
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        boolean mayReadInvites =
                securityInfoManager.hasPrivilege(
                        loggedInInfo, PortalStaffContextResolver.OBJECT_INVITE, READ, null);
        boolean mayReadAccount =
                securityInfoManager.hasPrivilege(
                        loggedInInfo, PortalStaffContextResolver.OBJECT_ACCOUNT, READ, null);
        if (!mayReadInvites && !mayReadAccount) {
            throw new SecurityException("missing required sec object (_portal.account)");
        }

        int demographicNo = positiveInt(request.getParameter("demographicNo"));
        if (demographicNo <= 0) {
            return badRequest(response, "a patient must be selected");
        }

        PatientPortalService portal = portalService();
        if (portal == null) {
            return portalNotConfigured(response);
        }

        // Only the sections this caller may read. A panel read has no business arriving at the
        // portal with authority over passphrases or the contact-review queue.
        Set<String> scope = new LinkedHashSet<>();
        if (mayReadInvites) {
            scope.add(PortalStaffContextResolver.OBJECT_INVITE);
        }
        if (mayReadAccount) {
            scope.add(PortalStaffContextResolver.OBJECT_ACCOUNT);
        }
        PatientPortalStaffContext staff = staffContextResolver.resolve(loggedInInfo, scope);
        ObjectNode payload = objectMapper().createObjectNode();
        boolean complete = true;
        if (mayReadInvites) {
            complete &= addInvites(portal, payload, demographicNo, staff);
        }
        if (mayReadAccount) {
            complete &= addAccount(portal, payload, demographicNo, staff);
        }
        // ok is the reliable-looking signal, so it has to be the honest one. Reporting true while
        // both sections failed let a caller doing the obvious `if (!body.ok)` render a healthy,
        // empty panel during a total portal outage — and a receptionist reading an empty panel
        // concludes the patient has no portal presence and issues an invitation.
        payload.put("ok", complete);
        return write(response, HttpServletResponse.SC_OK, payload);
    }

    /**
     * Adds the invitation list.
     *
     * <p>{@code issuedCount} and {@code lastIssuedAt} are reported under names that say
     * <em>issued</em>, not sent. The portal never delivers anything; presenting these as delivery
     * evidence is the single most likely misreading of this data.
     */
    private boolean addInvites(
            PatientPortalService portal, ObjectNode payload, int demographicNo,
            PatientPortalStaffContext staff) {
        ArrayNode invites = payload.putArray("invites");
        try {
            List<PatientPortalInviteDto> found =
                    portal.listInvites(
                            demographicNo, PatientPortalService.MAX_INVITE_PAGE_SIZE, staff);
            for (PatientPortalInviteDto invite : found) {
                ObjectNode node = invites.addObject();
                node.put("inviteId", invite.id());
                node.put("status", invite.status());
                node.put("issuedCount", invite.issuedCount());
                node.put(
                        "lastIssuedAt",
                        invite.lastIssuedAt() == null ? null : invite.lastIssuedAt().toString());
                node.put("lastIssuedBy", invite.lastIssuedBy());
                node.put(
                        "expiresAt",
                        invite.expiresAt() == null ? null : invite.expiresAt().toString());
            }
            return true;
        } catch (PatientPortalException exception) {
            payload.remove("invites");
            payload.put("invitesError", SECTION_UNAVAILABLE);
            payload.put("invitesErrorKind", exception.kind().name().toLowerCase(Locale.ROOT));
            logger.error(
                    String.format(Locale.ROOT, SECTION_FAILED_LOG, "invites", exception.kind()),
                    exception);
            return false;
        }
    }

    /**
     * Adds account status, distinguishing "no account yet" from "could not read".
     *
     * <p>A {@code 404} here is the routine case — most patients have never activated — so it is
     * reported as {@code no_portal_account} rather than as an error. Every other failure is reported
     * as unavailable, because presenting a portal outage as "this patient has no account" would
     * invite staff to issue an invitation the patient does not need.
     */
    private boolean addAccount(
            PatientPortalService portal, ObjectNode payload, int demographicNo,
            PatientPortalStaffContext staff) {
        try {
            PatientPortalAccountDto account =
                    portal.findAccount(demographicNo, staff);
            ObjectNode node = payload.putObject("account");
            node.put("accountId", account.id());
            node.put("status", account.status());
            node.put("locked", account.locked());
            node.put("forcePasswordReset", account.forcePasswordReset());
            node.put(
                    "disabledAt",
                    account.disabledAt() == null ? null : account.disabledAt().toString());
            node.put("disabledReason", account.disabledReason());
            return true;
        } catch (PatientPortalException exception) {
            if (exception.kind() == PatientPortalException.Kind.NOT_FOUND_OR_UNAUTHENTICATED) {
                payload.put("account", (String) null);
                payload.put("accountState", NO_ACCOUNT);
                // Logged even though this is the routine reading, because 404 is three-way: it is
                // also what a rejected service identity looks like. A rotated token that nobody
                // updated in carlos.properties reports every patient as having no account, and
                // without this line a clinic-wide credential outage leaves no trace at all.
                logger.info(
                        String.format(
                                Locale.ROOT, SECTION_FAILED_LOG, "account", exception.kind()));
                return true;
            }
            payload.put("accountError", SECTION_UNAVAILABLE);
            payload.put("accountErrorKind", exception.kind().name().toLowerCase(Locale.ROOT));
            logger.error(
                    String.format(Locale.ROOT, SECTION_FAILED_LOG, "account", exception.kind()),
                    exception);
            return false;
        }
    }
}
