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

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Derives the portal permissions a logged-in provider actually holds.
 *
 * <p>This is the single enforcement point for the invariant {@link PatientPortalStaffContext}
 * documents but cannot enforce on its own: that the permissions sent to the portal reflect the
 * caller's real CARLOS privileges rather than what the calling code chose to claim. There is exactly
 * one way to build a context for a request, and it reads {@link SecurityInfoManager}.
 *
 * <p>The temptation this class exists to remove is a caller writing {@code Set.of(all five
 * constants)} because it is convenient — which compiles, works, and silently makes the portal's
 * per-action authorization decorative. Actions still gate themselves with their own {@code
 * hasPrivilege} check; this decides what the portal is told, and the two must not drift.
 *
 * <p>Each CARLOS security object maps to exactly one portal permission:
 *
 * <ul>
 *   <li>{@code _portal.invite} → {@code portal.invite.manage}
 *   <li>{@code _portal.account} → {@code portal.account.manage}
 *   <li>{@code _portal.account.unlock} → {@code portal.account.unlock}
 *   <li>{@code _portal.secret} → {@code portal.secret.manage}
 *   <li>{@code _portal.contact.review} → {@code portal.contact.review}
 * </ul>
 *
 * @since 2026-08-19
 */
public class PortalStaffContextResolver {

    /** Configure the CARLOS-to-portal connection. Not a portal permission. */
    public static final String OBJECT_ADMIN = "_admin.portal";

    public static final String OBJECT_INVITE = "_portal.invite";
    public static final String OBJECT_ACCOUNT = "_portal.account";
    public static final String OBJECT_ACCOUNT_UNLOCK = "_portal.account.unlock";
    public static final String OBJECT_SECRET = "_portal.secret";
    public static final String OBJECT_CONTACT_REVIEW = "_portal.contact.review";

    private static final Map<String, String> PERMISSION_BY_OBJECT = permissionByObject();

    private static final String READ = "r";
    private static final String NO_PRIVILEGE =
            "provider holds no patient portal privilege; refusing to build a portal identity";

    private final SecurityInfoManager securityInfoManager;

    public PortalStaffContextResolver(SecurityInfoManager securityInfoManager) {
        this.securityInfoManager = securityInfoManager;
    }

    private static Map<String, String> permissionByObject() {
        Map<String, String> mapping = new LinkedHashMap<>();
        mapping.put(OBJECT_INVITE, PatientPortalStaffContext.PERMISSION_INVITE_MANAGE);
        mapping.put(OBJECT_ACCOUNT, PatientPortalStaffContext.PERMISSION_ACCOUNT_MANAGE);
        mapping.put(OBJECT_ACCOUNT_UNLOCK, PatientPortalStaffContext.PERMISSION_ACCOUNT_UNLOCK);
        mapping.put(OBJECT_SECRET, PatientPortalStaffContext.PERMISSION_SECRET_MANAGE);
        mapping.put(OBJECT_CONTACT_REVIEW, PatientPortalStaffContext.PERMISSION_CONTACT_REVIEW);
        return Map.copyOf(mapping);
    }

    /**
     * Builds the portal identity for the logged-in provider.
     *
     * <p>The provider number is used as {@code providerId} because the portal retains it as the
     * permanent record of who acted, so it must be the durable CARLOS identifier rather than a
     * display name or a session key.
     *
     * @param loggedInInfo the authenticated CARLOS session
     * @return a context carrying only the permissions this provider holds
     * @throws SecurityException if the provider holds none of the portal objects. Sending an empty
     *     permission set would be rejected by the portal as a malformed identity and logged there as
     *     an authentication failure, which reads as a CARLOS misconfiguration rather than as the
     *     authorization refusal it is.
     */
    public PatientPortalStaffContext resolve(LoggedInInfo loggedInInfo) {
        String providerNo = loggedInInfo.getLoggedInProviderNo();
        Set<String> granted = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry : PERMISSION_BY_OBJECT.entrySet()) {
            if (securityInfoManager.hasPrivilege(loggedInInfo, entry.getKey(), READ, null)) {
                granted.add(entry.getValue());
            }
        }
        if (granted.isEmpty()) {
            throw new SecurityException(NO_PRIVILEGE);
        }
        return new PatientPortalStaffContext(providerNo, displayName(loggedInInfo), granted);
    }

    /**
     * Falls back to the provider number when no name is on the session.
     *
     * <p>The portal requires a non-blank display name and records it against the action. A blank one
     * would be rejected as a malformed identity, so a provider with an incomplete record must still
     * be able to act — attributed by number rather than not at all.
     */
    private static String displayName(LoggedInInfo loggedInInfo) {
        if (loggedInInfo.getLoggedInProvider() == null) {
            return loggedInInfo.getLoggedInProviderNo();
        }
        String formatted = loggedInInfo.getLoggedInProvider().getFormattedName();
        return formatted == null || formatted.isBlank()
                ? loggedInInfo.getLoggedInProviderNo()
                : formatted;
    }
}
