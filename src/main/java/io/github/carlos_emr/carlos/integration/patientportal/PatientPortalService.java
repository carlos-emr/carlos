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
package io.github.carlos_emr.carlos.integration.patientportal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;

/**
 * Outbound channel to the patient portal's {@code /internal/carlos/**} API.
 *
 * <p>This owns the authenticated envelope every portal call shares: the service bearer token, the
 * four {@code X-CARLOS-*} identity headers, request timeouts, and the mapping from the portal's
 * documented status codes onto {@link PatientPortalException.Kind}.
 *
 * <p><b>Security boundaries this class holds:</b>
 *
 * <ul>
 *   <li>The destination comes from {@link PatientPortalSettings} only. No method accepts a host, so
 *       no CARLOS request parameter can point portal traffic somewhere else.
 *   <li>The clinic id is read from configuration, not from the caller, so a caller cannot claim to
 *       act for another clinic.
 *   <li>Path identifiers are numeric and rendered as such, so nothing a caller supplies can escape
 *       into the path and reach an endpoint it was not authorized for.
 *   <li>Nothing here logs. The service token, invite tokens, and passphrases pass through this
 *       class, and the surest way not to leak them is to have no statement that could.
 * </ul>
 *
 * <p><b>What this class deliberately does not do:</b> it does not deliver invites, and it does not
 * decide when a passphrase may be published. Those orderings belong to the CARLOS workflow that
 * owns the outbound message — see issue #3475.
 *
 * @since 2026-08-19
 */
public class PatientPortalService {

    static final String AUTHORIZATION_HEADER = "Authorization";
    static final String PROVIDER_ID_HEADER = "X-CARLOS-Provider-ID";
    static final String PROVIDER_NAME_HEADER = "X-CARLOS-Provider-Name";
    static final String CLINIC_ID_HEADER = "X-CARLOS-Clinic-ID";
    static final String PERMISSIONS_HEADER = "X-CARLOS-Permissions";

    private static final String BEARER_PREFIX = "Bearer %s";
    private static final String INVALID_PATH = "portal endpoint path is not a valid URI: %s";
    private static final String UNREADABLE_BODY = "portal returned an unreadable body";

    private static final String INVITES_PATH = "/internal/carlos/patients/%d/invites";
    private static final String INVITE_RESEND_PATH = "/internal/carlos/invites/%d/resend";
    private static final String INVITE_REVOKE_PATH = "/internal/carlos/invites/%d/revoke";
    private static final String INVITE_LIST_PATH = "/internal/carlos/patients/%d/invites?limit=%d";

    private static final String GET = "GET";
    private static final String POST = "POST";

    /** The portal caps an invite listing at 100 records per request. */
    static final int MAX_INVITE_PAGE_SIZE = 100;

    private final PatientPortalSettings settings;
    private final PatientPortalHttpExchange exchange;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PatientPortalService(PatientPortalSettings settings) {
        this(settings, settings == null ? null : new PatientPortalHttpClientExchange(settings));
    }

    /** Test seam: lets the authenticated envelope be asserted without a socket. */
    PatientPortalService(PatientPortalSettings settings, PatientPortalHttpExchange exchange) {
        if (settings == null) {
            throw new PatientPortalConfigurationException("patient portal settings are required");
        }
        this.settings = settings;
        this.exchange = exchange;
    }

    /**
     * Creates a portal invite and returns the one-time activation token.
     *
     * <p>The identity proof must come from the CARLOS demographic record. The portal keeps only
     * salted keyed hashes of these values and requires the patient to reproduce them at activation,
     * so staff-entered values would produce invites nobody can activate.
     *
     * <p>The returned token is issued once. CARLOS owns delivery and must record the delivery
     * outcome in its own durable messaging workflow.
     *
     * <p><b>Calling this twice for one patient is not idempotent and not a no-op.</b> Verified
     * against the portal: a second create returns {@code 201} with a new invite and silently moves
     * every earlier pending invite to {@code revoked}, so a token already emailed to the patient
     * stops working. It does not return a conflict. A UI that lets staff click "invite" twice will
     * strand the first email, so callers should list existing invites and confirm before creating.
     *
     * @param demographicNo CARLOS demographic number
     * @param email patient email from the demographic record
     * @param dateOfBirth patient date of birth from the demographic record
     * @param healthCardNumber patient HIN/HCN from the demographic record
     * @param staff the authenticated provider, holding {@code portal.invite.manage}
     * @return the invite and its one-time token
     * @throws PatientPortalException with {@link PatientPortalException.Kind#CONFLICT} if the
     *     patient already has a portal account, or {@link
     *     PatientPortalException.Kind#BAD_REQUEST} if {@code demographicNo} disagrees with the body
     */
    public PatientPortalIssuedInviteDto createInvite(
            int demographicNo,
            String email,
            LocalDate dateOfBirth,
            String healthCardNumber,
            PatientPortalStaffContext staff) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("demographic_no", demographicNo);
        body.put("email", email);
        body.put("date_of_birth", dateOfBirth == null ? null : dateOfBirth.toString());
        body.put("health_card_number", healthCardNumber);
        String path = String.format(Locale.ROOT, INVITES_PATH, demographicNo);
        return PatientPortalIssuedInviteDto.fromJson(send(POST, path, body.toString(), staff));
    }

    /**
     * Lists a patient's invites, newest first as the portal orders them.
     *
     * @param limit records to request; the portal caps this at {@value #MAX_INVITE_PAGE_SIZE}
     */
    public List<PatientPortalInviteDto> listInvites(
            int demographicNo, int limit, PatientPortalStaffContext staff) {
        int requested = Math.min(Math.max(limit, 1), MAX_INVITE_PAGE_SIZE);
        String path = String.format(Locale.ROOT, INVITE_LIST_PATH, demographicNo, requested);
        JsonNode payload = send(GET, path, null, staff);
        List<PatientPortalInviteDto> invites = new ArrayList<>();
        for (JsonNode node : payload) {
            invites.add(PatientPortalInviteDto.fromJson(node));
        }
        return List.copyOf(invites);
    }

    /**
     * Reissues an invite, returning a fresh one-time token.
     *
     * <p><b>The previous token stops working immediately.</b> A resend whose delivery then fails
     * leaves the patient with no usable token, so callers must treat delivery failure as an error
     * worth surfacing rather than a retry that can be dropped.
     */
    public PatientPortalIssuedInviteDto resendInvite(long inviteId, PatientPortalStaffContext staff) {
        String path = String.format(Locale.ROOT, INVITE_RESEND_PATH, inviteId);
        return PatientPortalIssuedInviteDto.fromJson(send(POST, path, null, staff));
    }

    /** Revokes a pending invite. */
    public PatientPortalInviteDto revokeInvite(long inviteId, PatientPortalStaffContext staff) {
        String path = String.format(Locale.ROOT, INVITE_REVOKE_PATH, inviteId);
        return PatientPortalInviteDto.fromJson(send(POST, path, null, staff));
    }

    /**
     * Sends an authenticated request and returns the parsed success body.
     *
     * @throws PatientPortalException mapped from the portal's status, or wrapping a transport
     *     failure; never carrying a credential in its message
     */
    private JsonNode send(
            String method, String path, String jsonBody, PatientPortalStaffContext staff) {
        PatientPortalHttpResponse response;
        try {
            response = exchange.send(buildRequest(method, path, jsonBody, staff));
        } catch (IOException exception) {
            throw new PatientPortalException(path, exception);
        }
        if (!response.isSuccess()) {
            throw new PatientPortalException(
                    PatientPortalException.kindForStatus(response.statusCode()),
                    response.statusCode(),
                    path);
        }
        try {
            return objectMapper.readTree(response.body());
        } catch (IOException exception) {
            throw new PatientPortalException(path, new IOException(UNREADABLE_BODY, exception));
        }
    }

    /**
     * Builds an authenticated request against a portal endpoint.
     *
     * <p>Package-private so the header envelope can be asserted directly, without a socket. The
     * envelope is the security-relevant part of this class, and it should be provable in a unit
     * test rather than only in an integration environment.
     *
     * @param method HTTP method, e.g. {@code GET} or {@code POST}
     * @param path portal endpoint path beginning with {@code /internal/carlos/}
     * @param jsonBody request body, or {@code null} for a request without one
     * @param staff the authenticated CARLOS provider this call acts for
     * @return a request carrying the bearer token and all four identity headers
     */
    ClassicHttpRequest buildRequest(
            String method, String path, String jsonBody, PatientPortalStaffContext staff) {
        URI uri = resolve(path);
        ClassicRequestBuilder builder =
                ClassicRequestBuilder.create(method)
                        .setUri(uri)
                        .setHeader(
                                AUTHORIZATION_HEADER,
                                String.format(Locale.ROOT, BEARER_PREFIX, settings.serviceToken()))
                        .setHeader(PROVIDER_ID_HEADER, staff.providerId())
                        .setHeader(PROVIDER_NAME_HEADER, staff.providerName())
                        // From configuration, never from the caller: a caller must not be able to
                        // claim it is acting for a different clinic.
                        .setHeader(CLINIC_ID_HEADER, settings.clinicId())
                        .setHeader(PERMISSIONS_HEADER, staff.permissionHeaderValue());
        if (jsonBody != null) {
            builder.setEntity(new StringEntity(jsonBody, ContentType.APPLICATION_JSON));
        }
        return builder.build();
    }

    private URI resolve(String path) {
        try {
            return new URI(settings.baseUrl() + path);
        } catch (URISyntaxException exception) {
            throw new PatientPortalConfigurationException(
                    String.format(Locale.ROOT, INVALID_PATH, path), exception);
        }
    }

    PatientPortalSettings settings() {
        return settings;
    }
}
