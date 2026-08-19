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
 *   <li>{@code 404} is <b>ambiguous by design</b>, and three-way. The portal fails closed on a bad
 *       service token, missing identity headers, or a clinic mismatch, and returns the same {@code
 *       404} it returns for an unknown record and for a patient who simply has no portal account
 *       yet. An unauthenticated caller must not learn which. The last of those is the routine case,
 *       so staff-facing copy should read as "no portal account" rather than as an error, while a
 *       {@code 404} on every call points at configuration.
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

    /** Why the call failed, derived from the portal's documented status codes. */
    public enum Kind {
        /** {@code 400} — the request contradicted itself, e.g. a demographic scope mismatch. */
        BAD_REQUEST,
        /** {@code 403} — authenticated, but the provider lacks the portal permission. */
        PERMISSION_DENIED,
        /** {@code 404} — unknown record, no portal account yet, or a rejected identity. */
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
        /** The call never produced a response: connect failure, timeout, or TLS failure. */
        TRANSPORT_FAILURE
    }

    private final Kind kind;
    private final int statusCode;
    private final String detail;

    private PatientPortalException(String message, Kind kind, int statusCode, String detail) {
        super(message);
        this.kind = kind;
        this.statusCode = statusCode;
        this.detail = detail;
    }

    private PatientPortalException(String message, Kind kind, int statusCode, Throwable cause) {
        super(message, cause);
        this.kind = kind;
        this.statusCode = statusCode;
        this.detail = null;
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

    /** Builds the failure for a call that never produced a response. */
    public static PatientPortalException ofTransportFailure(String endpointTemplate, Throwable cause) {
        return new PatientPortalException(
                String.format(Locale.ROOT, TRANSPORT_MESSAGE, endpointTemplate),
                Kind.TRANSPORT_FAILURE,
                0,
                cause);
    }

    /** Builds the failure for a success status whose body CARLOS could not read. */
    public static PatientPortalException ofMalformedResponse(
            int statusCode, String endpointTemplate, Throwable cause) {
        return new PatientPortalException(
                String.format(Locale.ROOT, MALFORMED_MESSAGE, endpointTemplate, statusCode),
                Kind.MALFORMED_RESPONSE,
                statusCode,
                cause);
    }

    public Kind kind() {
        return kind;
    }

    /**
     * @return the HTTP status the portal returned, or {@code 0} when no response was received
     */
    public int statusCode() {
        return statusCode;
    }

    /**
     * The portal's {@code detail} string, when it was safe to carry.
     *
     * <p>Only a plain JSON string is kept. A {@code 422} from the portal's validation layer answers
     * with a list of objects that can echo the offending input — patient email or health card number
     * — so that shape is discarded rather than parsed. The result is that {@code 422} is the one
     * status where no detail is available, which is the correct trade for not copying PHI into an
     * error message.
     *
     * @return the detail string, or {@code null} when absent or withheld
     */
    public String detail() {
        return detail;
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
