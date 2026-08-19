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

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * The authenticated CARLOS provider on whose behalf a portal call is made.
 *
 * <p>The portal records {@code providerId} in its own audit trail and authorizes each call against
 * {@code permissions}. Both must be derived from the caller's real CARLOS session and privileges —
 * never from request parameters, and never hardcoded to the full permission set. A caller that
 * always sent every permission would make the portal's per-action authorization decorative, and
 * would attribute every action to whatever identity the client chose to claim.
 *
 * <p>{@code providerId} must be the durable CARLOS provider number rather than a display name or a
 * session identifier, because the portal keeps it as the permanent record of who acted.
 *
 * @param providerId stable CARLOS provider number
 * @param providerName provider display name, recorded as the acting staff member
 * @param permissions portal permission strings the provider actually holds
 * @since 2026-08-19
 */
public record PatientPortalStaffContext(
        String providerId, String providerName, Set<String> permissions) {

    /** Manage portal invites: create, list, resend, revoke. */
    public static final String PERMISSION_INVITE_MANAGE = "portal.invite.manage";

    /** Clear a patient lockout. */
    public static final String PERMISSION_ACCOUNT_UNLOCK = "portal.account.unlock";

    /** Read account status and enable or disable an account. */
    public static final String PERMISSION_ACCOUNT_MANAGE = "portal.account.manage";

    /** Create, publish, and revoke encrypted-message passphrases. */
    public static final String PERMISSION_SECRET_MANAGE = "portal.secret.manage";

    /** Review patient contact changes. */
    public static final String PERMISSION_CONTACT_REVIEW = "portal.contact.review";

    /** Matches {@code MAX_PERMISSION_COUNT} in the portal's {@code staff_identity.py}. */
    public static final int MAX_PERMISSION_COUNT = 32;

    /** Matches {@code MAX_PERMISSION_LENGTH} in the portal's {@code staff_identity.py}. */
    public static final int MAX_PERMISSION_LENGTH = 64;

    private static final String BLANK_PROVIDER_ID = "portal staff context requires a provider id";
    private static final String BLANK_PROVIDER_NAME = "portal staff context requires a provider name";
    private static final String NO_PERMISSIONS = "portal staff context requires a permission";
    private static final String TOO_MANY_PERMISSIONS = "portal permits at most %d permissions";
    private static final String PERMISSION_TOO_LONG = "portal permission exceeds %d characters";
    private static final String PERMISSION_HAS_COMMA = "portal permission must not contain a comma";
    private static final String CONTROL_CHARACTER =
            "portal staff identity must not contain control characters";

    /**
     * Validates the caller identity against the limits the portal itself enforces.
     *
     * <p>Rejecting here rather than on the wire means a malformed permission set surfaces as a
     * CARLOS-side programming error instead of an opaque {@code 404} from the portal's fail-closed
     * authentication.
     *
     * @throws IllegalArgumentException if the identity is incomplete or a permission is unusable
     */
    public PatientPortalStaffContext {
        if (providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException(BLANK_PROVIDER_ID);
        }
        if (providerName == null || providerName.isBlank()) {
            throw new IllegalArgumentException(BLANK_PROVIDER_NAME);
        }
        if (permissions == null || permissions.isEmpty()) {
            throw new IllegalArgumentException(NO_PERMISSIONS);
        }
        if (permissions.size() > MAX_PERMISSION_COUNT) {
            throw new IllegalArgumentException(
                    String.format(Locale.ROOT, TOO_MANY_PERMISSIONS, MAX_PERMISSION_COUNT));
        }
        providerId = providerId.strip();
        providerName = providerName.strip();
        // Every one of these three becomes an HTTP header value. A carriage return or newline
        // would let a caller append headers of its own — including a second
        // X-CARLOS-Permissions claiming privileges the provider does not hold. An earlier
        // revision checked only for commas in permissions, which is the narrower half of the
        // same problem.
        rejectControlCharacters(providerId);
        rejectControlCharacters(providerName);
        Set<String> normalized = new LinkedHashSet<>();
        for (String permission : permissions) {
            if (permission == null || permission.isBlank()) {
                throw new IllegalArgumentException(NO_PERMISSIONS);
            }
            String stripped = permission.strip();
            if (stripped.length() > MAX_PERMISSION_LENGTH) {
                throw new IllegalArgumentException(
                        String.format(Locale.ROOT, PERMISSION_TOO_LONG, MAX_PERMISSION_LENGTH));
            }
            // A comma would split into two claimed permissions inside the portal's header parser.
            if (stripped.indexOf(',') >= 0) {
                throw new IllegalArgumentException(PERMISSION_HAS_COMMA);
            }
            rejectControlCharacters(stripped);
            normalized.add(stripped);
        }
        permissions = Set.copyOf(normalized);
    }

    private static void rejectControlCharacters(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                throw new IllegalArgumentException(CONTROL_CHARACTER);
            }
        }
    }

    /**
     * Renders the permission set as the portal's {@code X-CARLOS-Permissions} header value.
     *
     * @return comma-separated permissions in a stable order
     */
    public String permissionHeaderValue() {
        return String.join(",", new TreeSet<>(permissions));
    }
}
