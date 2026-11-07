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

import java.io.Serial;
import java.util.Locale;

/**
 * A patient portal call that did not succeed.
 *
 * <p>The {@link Kind} exists so callers can branch on the outcome without re-deriving meaning from a
 * status code. Three mappings matter and are easy to get wrong:
 *
 * <ul>
 *   <li>{@code 404} is <b>ambiguous by design</b>. The portal fails closed on a bad service token,
 *       missing or invalid staff assertion, or a clinic mismatch, and returns the same {@code 404}
 *       it returns for an unknown record. Staff-facing copy must preserve this ambiguity; a
 *       {@code 404} on every call can indicate a configuration problem. The one exception is a
 *       patient with no portal account yet: the portal says so only after authenticating the
 *       caller, and {@link #isAccountAbsent()} reports it.
 *   <li>{@code 409} is a real business outcome, not a transport error. The patient already has an
 *       account, or a contact review moved on. Retrying is wrong; re-reading state and
 *       re-presenting it to the user is right.
 *   <li>{@link Kind#MALFORMED_RESPONSE} is <b>not</b> {@link Kind#TRANSPORT_FAILURE}. The portal
 *       answered and CARLOS could not read the answer, which points at a contract change or a proxy
 *       rewriting bodies — a different system and a different fix from a network fault. Critically,
 *       a mutating request that fails this way <b>may already have taken effect</b>, so it must not
 *       be retried blindly the way a connect failure can be.
 * </ul>
 *
 * <p><b>PHI:</b> messages carry an endpoint <em>template</em> such as {@code
 * /internal/carlos/patients/{id}/invites}, never the interpolated path. Portal paths embed
 * {@code demographic_no}, which CLAUDE.md classifies as a PHI-correlating identifier that must not
 * reach a browser-visible exception message; these messages surface in logs and, through Struts
 * result resolution, on error pages. Callers needing the identifier already hold it.
 *
 * <p>The service token, invite tokens, and passphrases never appear. The portal's {@code detail}
 * string is carried when it is safe to do so — see {@link #detail()}.
 *
 * @since 2026-08-19
 */
public class PatientPortalException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String MESSAGE = "patient portal call to %s failed: %s (HTTP %d)";
    private static final String MESSAGE_WITH_DETAIL =
            "patient portal call to %s failed: %s (HTTP %d): %s";
    private static final String TRANSPORT_MESSAGE = "patient portal call to %s could not complete";
    private static final String MALFORMED_MESSAGE =
            "patient portal call to %s returned a body CARLOS could not read (HTTP %d)";

    /** The portal's {@code 404} detail for an authenticated lookup of a patient with no account. */
    public static final String ACCOUNT_NOT_FOUND_DETAIL = "portal account not found";

    /** Why the call failed, derived from the portal's documented status codes. */
    public enum Kind {
        /** {@code 400} — the request contradicted itself, e.g. a demographic scope mismatch. */
        BAD_REQUEST,
        /** {@code 403} — authenticated, but the provider lacks the portal permission. */
        PERMISSION_DENIED,
        /**
         * {@code 404} — unknown record or a rejected identity. Also a patient with no portal
         * account yet, which {@link #isAccountAbsent()} tells apart.
         */
        NOT_FOUND_OR_UNAUTHENTICATED,
        /** {@code 409} — the requested state transition conflicts with current portal state. */
        CONFLICT,
        /** {@code 422} — the request body failed portal validation. */
        VALIDATION_FAILED,
        /** {@code 429} — the portal throttled this caller. */
        THROTTLED,
        /** Any other status the portal returned. */
        UNEXPECTED_STATUS,
        /**
         * The portal answered, but the body was absent, truncated, or not the documented shape.
         *
         * <p>A mutating call that fails this way may already have taken effect.
         */
        MALFORMED_RESPONSE,
        /**
         * No complete response: connection failure, timeout, TLS failure, or interrupted body. A
         * mutation may have taken effect, unless {@link #isRequestNotSent()} says the request never
         * left CARLOS.
         */
        TRANSPORT_FAILURE
    }

    private final Kind kind;
    private final int statusCode;
    private final String detail;
    private final boolean requestNotSent;

    private PatientPortalException(String message, Kind kind, int statusCode, String detail) {
        super(message);
        this.kind = kind;
        this.statusCode = statusCode;
        this.detail = detail;
        this.requestNotSent = false;
    }

    private PatientPortalException(String message, Kind kind, int statusCode, Throwable cause) {
        super(message, cause);
        this.kind = kind;
        this.statusCode = statusCode;
        this.detail = null;
        this.requestNotSent = cause instanceof PortalRequestNotSentException;
    }

    /**
     * Builds the failure for a portal response CARLOS understood but that reported an error.
     *
     * @param statusCode status the portal returned
     * @param endpointTemplate endpoint template with placeholders, never an interpolated path
     * @param detail the portal's {@code detail} string, or {@code null} when absent or unsafe
     */
    public static PatientPortalException ofStatus(int statusCode, String endpointTemplate, String detail) {
        Kind kind = kindForStatus(statusCode);
        String message =
                detail == null
                        ? String.format(Locale.ROOT, MESSAGE, endpointTemplate, kind, statusCode)
                        : String.format(
                                Locale.ROOT,
                                MESSAGE_WITH_DETAIL,
                                endpointTemplate,
                                kind,
                                statusCode,
                                detail);
        return new PatientPortalException(message, kind, statusCode, detail);
    }

    /**
     * The transport did not complete; a mutation may already have taken effect, unless the cause
     * shows the request never left CARLOS ({@link #isRequestNotSent()}).
     */
    public static PatientPortalException ofTransportFailure(String endpointTemplate, Throwable cause) {
        return new PatientPortalException(
                String.format(Locale.ROOT, TRANSPORT_MESSAGE, endpointTemplate),
                Kind.TRANSPORT_FAILURE,
                0,
                sanitizedTransportCause(cause));
    }

    private static Throwable sanitizedTransportCause(Throwable cause) {
        if (cause == null) {
            return null;
        }
        if (cause instanceof PortalRequestNotSentException notSent) {
            // Its messages are fixed; keeping the type lets isRequestNotSent() survive logging.
            return new PortalRequestNotSentException(notSent.getMessage());
        }
        // HTTP parser exceptions can embed raw status lines/headers just as JSON errors embed values,
        // so the cause is replaced by a fixed category. The categories keep an outage, a local
        // capacity limit and a misconfiguration distinguishable in the logs.
        return new java.io.IOException("portal transport failed: " + transportCategory(cause));
    }

    private static String transportCategory(Throwable cause) {
        // Subclasses first: httpclient5's connect and pool-lease timeouts extend the JDK types below.
        if (cause instanceof org.apache.hc.client5.http.ConnectTimeoutException) return "connect timeout";
        if (cause instanceof org.apache.hc.client5.http.impl.classic.RequestFailedException) return "request cancelled";
        if (cause instanceof org.apache.hc.core5.util.DeadlineTimeoutException) return "connection pool lease timeout";
        if (cause instanceof java.net.SocketTimeoutException) {
            return PatientPortalHttpClientExchange.DEADLINE_EXCEEDED.equals(cause.getMessage())
                    ? "request deadline exceeded" : "read timeout";
        }
        if (cause instanceof javax.net.ssl.SSLException) return "TLS handshake";
        if (cause instanceof java.net.UnknownHostException) return "host lookup";
        if (cause instanceof java.net.ConnectException) return "connection refused";
        if (cause instanceof org.apache.hc.core5.http.NoHttpResponseException) return "connection closed without a response";
        if (cause instanceof java.io.InterruptedIOException) return "interrupted";
        return "HTTP exchange (" + cause.getClass().getSimpleName() + ")";
    }

    /**
     * True when the request never left CARLOS (transport busy, shut down, or an invalid URI).
     * Always a {@link Kind#TRANSPORT_FAILURE}, but one that cannot have changed anything.
     */
    public boolean isRequestNotSent() {
        return requestNotSent;
    }

    /** Builds the failure for a success status whose body CARLOS could not read. */
    public static PatientPortalException ofMalformedResponse(
            int statusCode, String endpointTemplate, Throwable cause) {
        return new PatientPortalException(
                String.format(Locale.ROOT, MALFORMED_MESSAGE, endpointTemplate, statusCode),
                Kind.MALFORMED_RESPONSE,
                statusCode,
                cause instanceof PortalContractException ? cause
                        : new PortalContractException("portal response is not valid JSON"));
    }

    public Kind kind() {
        return kind;
    }

    /**
     * @return the HTTP status the portal returned, or {@code 0} when the transport did not complete
     */
    public int statusCode() {
        return statusCode;
    }

    /** @return an allowlisted contract detail, or null when absent or withheld */
    public String detail() {
        return detail;
    }

    /**
     * Whether the portal confirmed that this patient has no portal account.
     *
     * <p>A portal {@code 404} is usually ambiguous, but this one is not: a rejected service
     * identity answers {@code "not found"}, while an authenticated lookup for a patient who has
     * not activated answers {@link #ACCOUNT_NOT_FOUND_DETAIL}. That is the normal state of every
     * invited patient, so callers must not report it as a connection fault.
     *
     * @return true only for the portal's explicit no-account answer
     */
    public boolean isAccountAbsent() {
        return kind == Kind.NOT_FOUND_OR_UNAUTHENTICATED && ACCOUNT_NOT_FOUND_DETAIL.equals(detail);
    }

    /**
     * Maps a portal HTTP status onto a {@link Kind}.
     *
     * @param statusCode status returned by the portal
     * @return the matching kind; {@link Kind#UNEXPECTED_STATUS} for anything undocumented
     */
    public static Kind kindForStatus(int statusCode) {
        return switch (statusCode) {
            case 400 -> Kind.BAD_REQUEST;
            case 403 -> Kind.PERMISSION_DENIED;
            case 404 -> Kind.NOT_FOUND_OR_UNAUTHENTICATED;
            case 409 -> Kind.CONFLICT;
            case 422 -> Kind.VALIDATION_FAILED;
            case 429 -> Kind.THROTTLED;
            default -> Kind.UNEXPECTED_STATUS;
        };
    }
}
