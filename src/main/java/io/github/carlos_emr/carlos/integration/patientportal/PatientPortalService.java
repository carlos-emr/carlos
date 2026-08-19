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
import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;

/**
 * Outbound channel to the patient portal's {@code /internal/carlos/**} API.
 *
 * <p>This owns the authenticated envelope every portal call shares: the service bearer token and
 * the four {@code X-CARLOS-*} identity headers. Timeouts and redirect policy belong to {@link
 * PatientPortalHttpClientExchange}; the status-to-outcome mapping belongs to {@link
 * PatientPortalException}.
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
public class PatientPortalService implements Closeable {

    static final String AUTHORIZATION_HEADER = "Authorization";
    static final String PROVIDER_ID_HEADER = "X-CARLOS-Provider-ID";
    static final String PROVIDER_NAME_HEADER = "X-CARLOS-Provider-Name";
    static final String CLINIC_ID_HEADER = "X-CARLOS-Clinic-ID";
    static final String PERMISSIONS_HEADER = "X-CARLOS-Permissions";

    private static final String BEARER_PREFIX = "Bearer %s";
    private static final String INVALID_PATH = "portal endpoint path is not a valid URI: %s";
    private static final String EMPTY_BODY = "portal returned an empty or non-JSON body";
    private static final int MAX_DETAIL_LENGTH = 200;
    private static final String NOT_AN_ARRAY = "portal returned a non-array invite listing";

    private static final String INVITES_PATH = "/internal/carlos/patients/%d/invites";
    private static final String INVITE_RESEND_PATH = "/internal/carlos/invites/%d/resend";
    private static final String INVITE_REVOKE_PATH = "/internal/carlos/invites/%d/revoke";
    private static final String INVITE_LIST_PATH = "/internal/carlos/patients/%d/invites?limit=%d";
    private static final String UNLOCK_PATH = "/internal/carlos/patients/%d/unlock";
    private static final String ACCOUNT_PATH = "/internal/carlos/patients/%d/portal-account";
    private static final String ACCESS_PATH = "/internal/carlos/patients/%d/portal-account/access";
    private static final String SECRETS_PATH = "/internal/carlos/patients/%d/unlock-secrets";
    private static final String SECRET_PUBLISH_PATH = "/internal/carlos/unlock-secrets/%d/publish";
    private static final String SECRET_REVOKE_PATH = "/internal/carlos/unlock-secrets/%d/revoke";
    private static final String REVIEWS_PATH = "/internal/carlos/contact-reviews?limit=%d&offset=%d";
    private static final String REVIEW_DECISION_PATH =
            "/internal/carlos/contact-reviews/%d/decision";

    private static final String GET = "GET";
    private static final String POST = "POST";

    /** The portal caps an invite listing at 100 records per request. */
    static final int MAX_INVITE_PAGE_SIZE = 100;

    /** The portal caps a contact-review page at 100 records per request. */
    static final int MAX_REVIEW_PAGE_SIZE = 100;

    /** The only secret type the portal currently mints. */
    static final String SECRET_TYPE_EMAIL = "email";

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
        if (exchange == null) {
            throw new PatientPortalConfigurationException("patient portal transport is required");
        }
        this.settings = settings;
        this.exchange = exchange;
    }

    /** Releases the pooled connections held by the transport, when it owns any. */
    @Override
    public void close() throws IOException {
        if (exchange instanceof Closeable closeable) {
            closeable.close();
        }
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
     *     patient already has a portal account
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
        return fetch(
                POST,
                INVITES_PATH,
                body.toString(),
                staff,
                PatientPortalIssuedInviteDto::fromJson,
                demographicNo);
    }

    /**
     * Lists a patient's invites, newest first as the portal orders them.
     *
     * @param limit records to request; the portal caps this at {@value #MAX_INVITE_PAGE_SIZE}
     */
    public List<PatientPortalInviteDto> listInvites(
            int demographicNo, int limit, PatientPortalStaffContext staff) {
        int requested = Math.min(Math.max(limit, 1), MAX_INVITE_PAGE_SIZE);
        return fetch(
                GET,
                INVITE_LIST_PATH,
                null,
                staff,
                PatientPortalService::inviteList,
                demographicNo,
                requested);
    }

    /**
     * Reissues an invite, returning a fresh one-time token.
     *
     * <p><b>The previous token stops working immediately.</b> A resend whose delivery then fails
     * leaves the patient with no usable token, so callers must treat delivery failure as an error
     * worth surfacing rather than a retry that can be dropped.
     */
    public PatientPortalIssuedInviteDto resendInvite(long inviteId, PatientPortalStaffContext staff) {
        return fetch(
                POST, INVITE_RESEND_PATH, null, staff,
                PatientPortalIssuedInviteDto::fromJson, inviteId);
    }

    /** Revokes a pending invite. */
    public PatientPortalInviteDto revokeInvite(long inviteId, PatientPortalStaffContext staff) {
        return fetch(
                POST, INVITE_REVOKE_PATH, null, staff,
                PatientPortalInviteDto::fromJson, inviteId);
    }


    /**
     * Clears a patient lockout.
     *
     * <p>This also revokes active sessions and MFA challenges and sets {@code forcePasswordReset}.
     * The patient must complete the reset flow before signing in, so staff-facing copy must not say
     * the account is simply usable again.
     *
     * @param staff the authenticated provider, holding {@code portal.account.unlock}
     */
    public PatientPortalAccountAcknowledgementDto unlockAccount(
            int demographicNo, PatientPortalStaffContext staff) {
        return fetch(
                POST, UNLOCK_PATH, null, staff,
                PatientPortalAccountAcknowledgementDto::fromJson, demographicNo);
    }

    /**
     * Reads a patient's portal account status.
     *
     * @param staff the authenticated provider, holding {@code portal.account.manage}
     */
    public PatientPortalAccountDto findAccount(int demographicNo, PatientPortalStaffContext staff) {
        return fetch(
                GET, ACCOUNT_PATH, null, staff,
                PatientPortalAccountDto::fromJson, demographicNo);
    }

    /**
     * Enables or disables a patient's portal account.
     *
     * @param enabled {@code false} to disable the account
     * @param reason short operator-supplied reason, recorded in the portal audit trail
     * @param staff the authenticated provider, holding {@code portal.account.manage}
     */
    public PatientPortalAccountAcknowledgementDto setAccountAccess(
            int demographicNo, boolean enabled, String reason, PatientPortalStaffContext staff) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("enabled", enabled);
        body.put("reason", reason);
        return fetch(
                POST,
                ACCESS_PATH,
                body.toString(),
                staff,
                PatientPortalAccountAcknowledgementDto::fromJson,
                demographicNo);
    }

    /**
     * Generates a passphrase for an encrypted message, in {@code pending} state.
     *
     * <p><b>Step one of three.</b> The patient cannot see this until {@link #publishUnlockSecret}
     * runs, and it must only run once the send is confirmed; a failed send calls {@link
     * #revokeUnlockSecret} instead.
     *
     * <p>Idempotent on {@code sourceReference}: a repeat call returns the existing record with
     * {@code created() == false} and the same passphrase, so a retry cannot mint a second one for
     * the same message. Pass a stable, unique reference per outbound message.
     *
     * @param sourceReference identifies the CARLOS message; 1 to 128 characters
     * @param label optional operator-facing label, at most 128 characters
     * @param staff the authenticated provider, holding {@code portal.secret.manage}
     * @throws PatientPortalException with {@link PatientPortalException.Kind#CONFLICT} if the
     *     source reference belongs to a revoked record
     */
    public PatientPortalUnlockSecretDto createUnlockSecret(
            int demographicNo, String sourceReference, String label,
            PatientPortalStaffContext staff) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("source_reference", sourceReference);
        body.put("secret_type", SECRET_TYPE_EMAIL);
        if (label != null) {
            body.put("label", label);
        }
        return fetch(
                POST,
                SECRETS_PATH,
                body.toString(),
                staff,
                PatientPortalUnlockSecretDto::fromJson,
                demographicNo);
    }

    /**
     * Makes a passphrase visible to the patient.
     *
     * <p><b>Call this only after the message send is confirmed.</b> Publishing first shows the
     * patient a passphrase for correspondence that may never arrive, and nothing downstream will
     * flag it.
     *
     * @param staff the authenticated provider, holding {@code portal.secret.manage}
     */
    public PatientPortalUnlockSecretStatusDto publishUnlockSecret(
            long unlockSecretId, PatientPortalStaffContext staff) {
        return fetch(
                POST, SECRET_PUBLISH_PATH, null, staff,
                PatientPortalUnlockSecretStatusDto::fromJson, unlockSecretId);
    }

    /**
     * Retires a passphrase whose message was never sent.
     *
     * @param reason short operator-supplied reason, at most 64 characters, or {@code null}
     * @param staff the authenticated provider, holding {@code portal.secret.manage}
     */
    public PatientPortalUnlockSecretStatusDto revokeUnlockSecret(
            long unlockSecretId, String reason, PatientPortalStaffContext staff) {
        ObjectNode body = objectMapper.createObjectNode();
        if (reason != null) {
            body.put("reason", reason);
        }
        return fetch(
                POST,
                SECRET_REVOKE_PATH,
                body.toString(),
                staff,
                PatientPortalUnlockSecretStatusDto::fromJson,
                unlockSecretId);
    }

    /**
     * Reads a page of the pending contact-review queue.
     *
     * <p>This is a clinic-wide work queue rather than a per-patient view.
     *
     * @param limit page size; the portal caps this at {@value #MAX_REVIEW_PAGE_SIZE}
     * @param offset page offset
     * @param staff the authenticated provider, holding {@code portal.contact.review}
     */
    public PatientPortalContactReviewPageDto listContactReviews(
            int limit, int offset, PatientPortalStaffContext staff) {
        int requested = Math.min(Math.max(limit, 1), MAX_REVIEW_PAGE_SIZE);
        int from = Math.max(offset, 0);
        return fetch(
                GET,
                REVIEWS_PATH,
                null,
                staff,
                PatientPortalContactReviewPageDto::fromJson,
                requested,
                from);
    }

    /**
     * Records the clinic's decision on a contact change.
     *
     * <p><b>Update the eChart before calling this.</b> The portal treats the decision as the point
     * at which the clinic has taken the change into its record of truth; confirming first and
     * failing to update afterwards leaves the two permanently disagreeing with nothing to detect it.
     *
     * <p>Repeat confirmations of the same revision are idempotent. A {@link
     * PatientPortalException.Kind#CONFLICT} means the revision is stale — the request changed
     * underneath the reviewer — so re-read the queue and re-present it rather than retrying.
     *
     * @param revision the exact {@code revision} from the review item
     * @param staff the authenticated provider, holding {@code portal.contact.review}
     */
    public PatientPortalContactReviewDecision decideContactReview(
            long reviewRequestId, boolean approve, String revision,
            PatientPortalStaffContext staff) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("approve", approve);
        body.put("revision", revision);
        return fetch(
                POST,
                REVIEW_DECISION_PATH,
                body.toString(),
                staff,
                PatientPortalService::contactReviewDecision,
                reviewRequestId);
    }

    /**
     * Outcome of a contact-review decision.
     *
     * @param id review request id
     * @param status portal review status after the decision
     * @param decision the recorded decision, or {@code null} if the portal did not report one
     */
    public record PatientPortalContactReviewDecision(long id, String status, String decision) {}

    /**
     * Sends a request and maps the success body, translating a contract violation into the
     * package's own exception type.
     *
     * <p>The mapping is done here rather than at each call site so a malformed field — a missing
     * identifier, a timestamp with no offset — surfaces as {@link
     * PatientPortalException.Kind#MALFORMED_RESPONSE} instead of escaping as a raw runtime
     * exception past every caller that catches {@link PatientPortalException}.
     */
    private <T> T fetch(
            String method,
            String pathFormat,
            String jsonBody,
            PatientPortalStaffContext staff,
            Function<JsonNode, T> factory,
            Object... args) {
        Parsed parsed = send(method, pathFormat, jsonBody, staff, args);
        try {
            return factory.apply(parsed.payload());
        } catch (PortalContractException exception) {
            throw PatientPortalException.ofMalformedResponse(
                    parsed.statusCode(), templateOf(pathFormat), exception);
        }
    }

    private static List<PatientPortalInviteDto> inviteList(JsonNode payload) {
        if (!payload.isArray()) {
            throw new PortalContractException(NOT_AN_ARRAY);
        }
        List<PatientPortalInviteDto> invites = new ArrayList<>();
        for (JsonNode node : payload) {
            invites.add(PatientPortalInviteDto.fromJson(node));
        }
        return List.copyOf(invites);
    }

    private static PatientPortalContactReviewDecision contactReviewDecision(JsonNode payload) {
        return new PatientPortalContactReviewDecision(
                PortalJson.requiredLong(payload, "id"),
                PortalJson.text(payload, "status"),
                PortalJson.text(payload, "decision"));
    }

    /** A parsed success body together with the status it arrived with. */
    private record Parsed(JsonNode payload, int statusCode) {}

    /**
     * Sends an authenticated request and returns the parsed success body.
     *
     * <p>Takes the path <em>format</em> plus its arguments rather than an interpolated path, so the
     * failure path can name the endpoint without the interpolated {@code demographic_no}. Portal
     * paths embed that identifier, and CLAUDE.md forbids putting it in a browser-visible exception
     * message.
     *
     * @throws PatientPortalException mapped from the portal's status, or reporting a transport or
     *     contract failure; never carrying a credential or a patient identifier in its message
     */
    private Parsed send(
            String method,
            String pathFormat,
            String jsonBody,
            PatientPortalStaffContext staff,
            Object... args) {
        String path = String.format(Locale.ROOT, pathFormat, args);
        String template = templateOf(pathFormat);
        PatientPortalHttpResponse response;
        try {
            response = exchange.send(buildRequest(method, path, jsonBody, staff));
        } catch (IOException exception) {
            throw PatientPortalException.ofTransportFailure(template, exception);
        }
        if (!response.isSuccess()) {
            throw PatientPortalException.ofStatus(
                    response.statusCode(), template, safeDetail(response.body()));
        }
        return new Parsed(parsed(response, template), response.statusCode());
    }

    /**
     * Renders a path format as a PHI-free endpoint template, e.g. {@code
     * /internal/carlos/patients/{id}/invites}.
     */
    private static String templateOf(String pathFormat) {
        return pathFormat.replace("%d", "{id}");
    }

    /**
     * Parses a success body, rejecting anything that is not a JSON object or array.
     *
     * <p>An empty body is the case that matters. {@code readTree("")} returns a missing node rather
     * than throwing, so without this guard a {@code 204}, or a proxy that stripped the body, would
     * flow into the DTO factories and produce a record of zeros and nulls — "the account is not
     * locked", "this patient has no invites", "the review queue is empty". The configuration path in
     * this package fails closed; the response path must not fail open.
     */
    private JsonNode parsed(PatientPortalHttpResponse response, String template) {
        JsonNode payload;
        try {
            payload = objectMapper.readTree(response.body());
        } catch (IOException exception) {
            throw PatientPortalException.ofMalformedResponse(
                    response.statusCode(), template, exception);
        }
        if (payload == null || !payload.isContainerNode()) {
            throw PatientPortalException.ofMalformedResponse(
                    response.statusCode(),
                    template,
                    new PortalContractException(EMPTY_BODY));
        }
        return payload;
    }

    /**
     * Extracts the portal's {@code detail} string when it is safe to carry.
     *
     * <p>Only a plain JSON string is kept. The portal's validation layer answers a {@code 422} with
     * a list of objects that can echo the offending input — a patient email or health card number —
     * so that shape is dropped rather than parsed. Losing the detail on {@code 422} is the correct
     * trade for never copying PHI into an error message that reaches logs and error pages.
     */
    private String safeDetail(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            JsonNode detail = node == null ? null : node.get("detail");
            if (detail == null || !detail.isTextual()) {
                return null;
            }
            String text = detail.asText();
            return text.length() > MAX_DETAIL_LENGTH
                    ? text.substring(0, MAX_DETAIL_LENGTH)
                    : text;
        } catch (IOException exception) {
            return null;
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

}
