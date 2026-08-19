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
 * status code. Two mappings matter and are easy to get wrong:
 *
 * <ul>
 *   <li>{@code 404} is <b>ambiguous by design</b>, and three-way. The portal fails closed on a bad
 *       service token, missing identity headers, or a clinic mismatch, and returns the same {@code
 *       404} it returns for an unknown record and for a patient who simply has no portal account
 *       yet. An unauthenticated caller must not learn which. The last of those is the routine case,
 *       so staff-facing copy should read as "no portal account" rather than as an error, while a
 *       {@code 404} on every call points at configuration.
 *   <li>{@code 409} is a real business outcome, not a transport error. The patient already has an
 *       account, an invite is already pending, or a contact review moved on. Retrying is wrong;
 *       re-reading state and re-presenting it to the user is right.
 * </ul>
 *
 * <p>Messages carry the status and the endpoint path only. The service token, invite tokens, and
 * passphrases never appear, because these messages reach logs and, in some flows, staff-visible
 * error pages.
 *
 * @since 2026-08-19
 */
public class PatientPortalException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String MESSAGE = "patient portal call to %s failed: %s (HTTP %d)";
    private static final String TRANSPORT_MESSAGE = "patient portal call to %s could not complete";

    /** Why the call failed, derived from the portal's documented status codes. */
    public enum Kind {
        /** {@code 400} — the request contradicted itself, e.g. a demographic scope mismatch. */
        BAD_REQUEST,
        /** {@code 403} — authenticated, but the provider lacks the portal permission. */
        PERMISSION_DENIED,
        /** {@code 404} — unknown record, or a rejected service token / identity / clinic. */
        NOT_FOUND_OR_UNAUTHENTICATED,
        /** {@code 409} — the requested state transition conflicts with current portal state. */
        CONFLICT,
        /** {@code 422} — the request body failed portal validation. */
        VALIDATION_FAILED,
        /** {@code 429} — the portal throttled this caller. */
        THROTTLED,
        /** Any other status the portal returned. */
        UNEXPECTED_STATUS,
        /** The call never produced a response: connect failure, timeout, or TLS failure. */
        TRANSPORT_FAILURE
    }

    private final transient Kind kind;
    private final int statusCode;

    public PatientPortalException(Kind kind, int statusCode, String path) {
        super(String.format(Locale.ROOT, MESSAGE, path, kind, statusCode));
        this.kind = kind;
        this.statusCode = statusCode;
    }

    public PatientPortalException(String path, Throwable cause) {
        super(String.format(Locale.ROOT, TRANSPORT_MESSAGE, path), cause);
        this.kind = Kind.TRANSPORT_FAILURE;
        this.statusCode = 0;
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
