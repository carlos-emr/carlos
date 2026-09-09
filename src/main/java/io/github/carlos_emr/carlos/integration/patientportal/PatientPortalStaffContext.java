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
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * The authenticated CARLOS provider on whose behalf a portal call is made.
 *
 * <p>The portal records {@code providerId} in its own audit trail and authorizes each call against
 * {@code permissions}. CARLOS binds both into a short-lived signed assertion. They must be derived
 * from the caller's real CARLOS session and privileges — never from request parameters, and never
 * hardcoded to the full permission set.
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

    private static final Set<String> SUPPORTED_PERMISSIONS =
            Set.of(
                    PERMISSION_INVITE_MANAGE,
                    PERMISSION_ACCOUNT_UNLOCK,
                    PERMISSION_ACCOUNT_MANAGE,
                    PERMISSION_SECRET_MANAGE,
                    PERMISSION_CONTACT_REVIEW);

    /** Matches {@code MAX_PERMISSION_COUNT} in the portal's {@code staff_identity.py}. */
    public static final int MAX_PERMISSION_COUNT = 32;

    /** Matches {@code MAX_PERMISSION_LENGTH} in the portal's {@code staff_identity.py}. */
    public static final int MAX_PERMISSION_LENGTH = 64;

    /** Matches {@code MAX_ACTOR_LENGTH} in the portal's {@code invites.py}. */
    public static final int MAX_ACTOR_LENGTH = 128;

    private static final String BLANK_PROVIDER_ID = "portal staff context requires a provider id";
    private static final String BLANK_PROVIDER_NAME = "portal staff context requires a provider name";
    private static final String NO_PERMISSIONS = "portal staff context requires a permission";
    private static final String TOO_MANY_PERMISSIONS = "portal permits at most %d permissions";
    private static final String PERMISSION_TOO_LONG = "portal permission exceeds %d characters";
    private static final String PERMISSION_HAS_COMMA = "portal permission must not contain a comma";
    private static final String INVALID_PERMISSION =
            "portal permission may contain only lowercase ASCII letters, digits, dots, underscores,"
                    + " and hyphens";
    private static final String UNSUPPORTED_PERMISSION =
            "portal permission is not supported by this CARLOS build";
    private static final String ACTOR_TOO_LONG = "portal staff identity exceeds %d characters";
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
        if (providerId.length() > MAX_ACTOR_LENGTH || providerName.length() > MAX_ACTOR_LENGTH) {
            throw new IllegalArgumentException(
                    String.format(Locale.ROOT, ACTOR_TOO_LONG, MAX_ACTOR_LENGTH));
        }
        // The portal rejects controls after verifying the assertion. Refuse them before signing so
        // a malformed local identity is a clear CARLOS error rather than an opaque portal 404.
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
            // Keep a specific diagnostic for the most likely separator mistake; the portal's
            // signed-assertion permission validator rejects commas as well.
            if (stripped.indexOf(',') >= 0) {
                throw new IllegalArgumentException(PERMISSION_HAS_COMMA);
            }
            rejectControlCharacters(stripped);
            if (!isPortalPermission(stripped)) {
                throw new IllegalArgumentException(INVALID_PERMISSION);
            }
            if (!SUPPORTED_PERMISSIONS.contains(stripped)) {
                throw new IllegalArgumentException(UNSUPPORTED_PERMISSION);
            }
            normalized.add(stripped);
        }
        permissions = Set.copyOf(normalized);
    }

    private static boolean isPortalPermission(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if ((character < 'a' || character > 'z')
                    && (character < '0' || character > '9')
                    && character != '.'
                    && character != '_'
                    && character != '-') {
                return false;
            }
        }
        return true;
    }

    private static void rejectControlCharacters(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                throw new IllegalArgumentException(CONTROL_CHARACTER);
            }
        }
    }

    /**
     * Renders the permission set in the stable order used by the signed assertion.
     *
     * @return an immutable, sorted permission list
     */
    public List<String> sortedPermissions() {
        return List.copyOf(new TreeSet<>(permissions));
    }
}
