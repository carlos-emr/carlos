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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalException;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalService;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalSettings;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ActionSupport;

/**
 * Shared JSON plumbing for the portal staff actions.
 *
 * <p>Two things live here rather than in each action because getting them wrong is silent:
 *
 * <ul>
 *   <li><b>Every write returns {@link #NONE}.</b> A named result would resolve to a JSP forward and
 *       append HTML to a JSON body that the browser has already begun parsing.
 *   <li><b>Portal failures are translated once.</b> The portal's own message names an endpoint
 *       template and a status, which mean nothing to a receptionist, and the {@code 404} arm must
 *       not claim the patient is unknown — that status is three-way, covering an unknown record, a
 *       patient with no portal account, and a rejected service identity.
 * </ul>
 *
 * @since 2026-08-19
 */
abstract class PortalJsonAction extends ActionSupport {

    private static final long serialVersionUID = 1L;

    private static final Logger logger = MiscUtils.getLogger();

    /** Non-null only when a test supplied the client directly. */
    private final transient PatientPortalService injectedService;

    PortalJsonAction() {
        this(null);
    }

    PortalJsonAction(PatientPortalService injectedService) {
        this.injectedService = injectedService;
    }

    private static final String JSON = "application/json;charset=UTF-8";

    /** Jakarta's HttpServletResponse predates RFC 6585 and has no constant for this. */
    private static final int TOO_MANY_REQUESTS = 429;
    private static final String MISSING_PRIVILEGE = "missing required sec object (%s)";

    private static final String CONFLICT_DEFAULT =
            "The portal rejected this change because the account state has moved on.";
    private static final String PERMISSION_DENIED =
            "This account is not permitted to manage the patient portal.";
    private static final String NOT_FOUND =
            """
            The portal did not recognise this request. If this affects every patient, the portal \
            connection needs checking.""";
    private static final String THROTTLED =
            "The portal is rate limiting requests. Try again shortly.";
    private static final String UNREACHABLE = "The patient portal could not be reached.";
    private static final String MALFORMED =
            """
            The portal replied in a form CARLOS could not read. The change may or may not have been \
            applied; check before retrying.""";
    private static final String REJECTED = "The portal rejected this request.";
    private static final String VALIDATION_REJECTED =
            """
            The portal rejected the details CARLOS sent. The field it named is in the CARLOS server \
            log; it is withheld here because it can echo patient data.""";
    private static final String PORTAL_FAULT =
            """
            The patient portal returned an error. This is a fault at the portal rather than a \
            problem with this request; it needs checking before retrying.""";
    private static final String FAILURE_LOG =
            "patient portal call failed: kind=%s, answered %d to the browser";
    private static final String WRONG_METHOD =
            "This action must be requested with POST.";
    private static final String NOT_CONFIGURED =
            """
            The patient portal is not configured on this CARLOS server. An administrator needs to \
            set the portal connection before these actions can be used.""";

    private final transient ObjectMapper objectMapper = new ObjectMapper();

    ObjectMapper objectMapper() {
        return objectMapper;
    }

    /**
     * Resolves the portal client, or {@code null} when this deployment has no portal.
     *
     * <p>Resolved here rather than in a constructor so an unconfigured portal is answerable. The
     * bean is lazy and its factory throws when the portal is unconfigured, so a constructor lookup
     * would blow up while Struts was still instantiating the action — producing a stack trace where
     * a sentence would do, and giving the action no chance to say what is actually wrong.
     *
     * <p>Absence is checked before construction is attempted. "No portal on this server" is the
     * normal state for most CARLOS deployments and must not be reported as a fault; a portal that
     * <em>is</em> configured but invalid still throws, because a half-configured portal must not
     * look like an absent one.
     */
    PatientPortalService portalService() {
        if (injectedService != null) {
            return injectedService;
        }
        if (!PatientPortalSettings.isConfigured()) {
            return null;
        }
        return SpringUtils.getBean(PatientPortalService.class);
    }

    /** Answers a request made against a CARLOS server that has no portal configured. */
    String portalNotConfigured(HttpServletResponse response) throws IOException {
        return failure(
                response,
                HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                "portal_not_configured",
                NOT_CONFIGURED);
    }

    /**
     * @throws SecurityException naming the object, in the paren form CARLOS standardises on
     */
    static void requirePrivilege(
            SecurityInfoManager securityInfoManager,
            LoggedInInfo loggedInInfo,
            String securityObject) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, securityObject, "w", null)) {
            throw new SecurityException(
                    String.format(Locale.ROOT, MISSING_PRIVILEGE, securityObject));
        }
    }

    String write(HttpServletResponse response, int status, ObjectNode payload) throws IOException {
        response.setStatus(status);
        response.setContentType(JSON);
        response.getWriter().write(objectMapper.writeValueAsString(payload));
        response.getWriter().flush();
        return NONE;
    }

    /**
     * Refuses a method this action does not support, in JSON like every other reply.
     *
     * <p>{@code sendError} was used here first, which hands the response to the container and
     * returns CARLOS's HTML error page. The status was right, so an AJAX caller reading only the
     * status saw correct behaviour — but it made these endpoints the one place whose failures are
     * sometimes JSON and sometimes a full HTML document, which any caller that reads the body has
     * to special-case.
     */
    String methodNotAllowed(HttpServletResponse response) throws IOException {
        return failure(
                response,
                HttpServletResponse.SC_METHOD_NOT_ALLOWED,
                "method_not_allowed",
                WRONG_METHOD);
    }

    String badRequest(HttpServletResponse response, String message) throws IOException {
        return failure(response, HttpServletResponse.SC_BAD_REQUEST, "bad_request", message);
    }

    String conflict(HttpServletResponse response, String reason, String message)
            throws IOException {
        return failure(response, HttpServletResponse.SC_CONFLICT, reason, message);
    }

    private String failure(
            HttpServletResponse response, int status, String reason, String message)
            throws IOException {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("ok", false);
        payload.put("reason", reason);
        payload.put("message", message);
        return write(response, status, payload);
    }

    /**
     * What a {@code 404} means for this action.
     *
     * <p>The status is three-way, and which reading is likely depends entirely on the caller. For a
     * patient-scoped action the overwhelmingly common case is that the patient simply has no portal
     * account, so an action that knows this should say so rather than send staff to check the
     * portal connection.
     */
    String notFoundMessage() {
        return NOT_FOUND;
    }

    /**
     * Translates a portal failure into something a staff member can act on, and records it.
     *
     * <p>The status is per-kind rather than a blanket {@code 409}. Answering 409 to everything made
     * a total portal outage produce no 5xx anywhere in CARLOS, so nothing in an access log or an
     * uptime rule keyed on 5xx could see it, and a rate limit was indistinguishable from a
     * permanent business conflict to any retry layer. {@code PatientPortalException} argues at
     * length that these outcomes differ; the web layer has to carry that through.
     *
     * <p>The switch is exhaustive on purpose. A new {@link PatientPortalException.Kind} must not be
     * able to inherit "the portal rejected this request" from a {@code default} arm without anyone
     * deciding it should.
     *
     * <p>These failures reach a receptionist, not a developer, so their report is the only trigger
     * anyone gets and the log is the only evidence. The exception message names an endpoint
     * template and a status — never an interpolated path — so it is safe to record in full.
     */
    String portalFailure(HttpServletResponse response, PatientPortalException exception)
            throws IOException {
        String message =
                switch (exception.kind()) {
                    case CONFLICT -> exception.detail() == null
                            ? CONFLICT_DEFAULT
                            : exception.detail();
                    case PERMISSION_DENIED -> PERMISSION_DENIED;
                    case NOT_FOUND_OR_UNAUTHENTICATED -> notFoundMessage();
                    case THROTTLED -> THROTTLED;
                    case TRANSPORT_FAILURE -> UNREACHABLE;
                    case MALFORMED_RESPONSE -> MALFORMED;
                    case VALIDATION_FAILED -> VALIDATION_REJECTED;
                    case UNEXPECTED_STATUS -> PORTAL_FAULT;
                    case BAD_REQUEST -> REJECTED;
                };
        int status =
                switch (exception.kind()) {
                    case CONFLICT -> HttpServletResponse.SC_CONFLICT;
                    case PERMISSION_DENIED -> HttpServletResponse.SC_FORBIDDEN;
                    case NOT_FOUND_OR_UNAUTHENTICATED -> HttpServletResponse.SC_NOT_FOUND;
                    case THROTTLED -> TOO_MANY_REQUESTS;
                    case TRANSPORT_FAILURE -> HttpServletResponse.SC_GATEWAY_TIMEOUT;
                    case MALFORMED_RESPONSE, UNEXPECTED_STATUS ->
                            HttpServletResponse.SC_BAD_GATEWAY;
                    case VALIDATION_FAILED, BAD_REQUEST -> HttpServletResponse.SC_BAD_REQUEST;
                };
        logger.error(
                String.format(Locale.ROOT, FAILURE_LOG, exception.kind(), status), exception);
        return failure(
                response, status, exception.kind().name().toLowerCase(Locale.ROOT), message);
    }

    static int positiveInt(String value) {
        try {
            int parsed = Integer.parseInt(value == null ? "" : value.strip());
            return parsed > 0 ? parsed : -1;
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    /**
     * Parses an identifier the portal models as a {@code long}.
     *
     * <p>Separate from {@link #positiveInt(String)} because invite identifiers are {@code long} in
     * every DTO. Parsing them as {@code int} made any identifier above {@link Integer#MAX_VALUE}
     * unaddressable, and reported as "an invitation must be selected" rather than as anything a
     * caller could act on.
     */
    static long positiveLong(String value) {
        try {
            long parsed = Long.parseLong(value == null ? "" : value.strip());
            return parsed > 0 ? parsed : -1;
        } catch (NumberFormatException exception) {
            return -1;
        }
    }
}
