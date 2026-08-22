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
 * <p>This is the intended sole builder of a request's portal identity: the permissions sent to the
 * portal come from {@link SecurityInfoManager} rather than from whatever the calling code chose to
 * claim. It is a convention, not an enforced invariant — {@link PatientPortalStaffContext} is a
 * public record with a public canonical constructor, so {@code Set.of(all five constants)} compiles
 * from anywhere. Nothing in the build would catch that today.
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
 * <p><b>The portal's permissions are resource-scoped, not level-scoped.</b> One permission gates
 * both the read and the mutations on a resource there — {@code portal.invite.manage} authorises
 * listing invitations as well as creating and revoking them — so a provider who may only read still
 * has to be sent a permission whose name says "manage". CARLOS's own {@code hasPrivilege} check is
 * what keeps a read-only provider from reaching a mutating route; the header cannot express the
 * distinction because the portal has no vocabulary for it. The privilege level is therefore read at
 * {@code "r"} deliberately, and this is the reason.
 *
 * <p>What the caller <em>can</em> control is scope: {@link #resolve(LoggedInInfo, Set)} takes the
 * objects the current call actually needs, so a panel read no longer asserts
 * {@code portal.secret.manage} on the provider's behalf merely because they hold that object.
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
     * Builds the portal identity for the logged-in provider, limited to the objects a call needs.
     *
     * <p>The provider number is used as {@code providerId} because the portal retains it as the
     * permanent record of who acted, so it must be the durable CARLOS identifier rather than a
     * display name or a session key.
     *
     * <p>Scoping matters because the portal authorises on this header alone. Sending every
     * permission a provider happens to hold makes a read of the demographic panel arrive at the
     * portal carrying authority to reveal message passphrases, which no part of that request needs.
     *
     * @param loggedInInfo the authenticated CARLOS session
     * @param objects the portal security objects this call depends on
     * @return a context carrying only the permissions this provider holds among {@code objects}
     * @throws SecurityException if the provider holds none of them. Sending an empty permission set
     *     would be rejected by the portal as a malformed identity and logged there as an
     *     authentication failure, which reads as a CARLOS misconfiguration rather than as the
     *     authorization refusal it is.
     */
    public PatientPortalStaffContext resolve(LoggedInInfo loggedInInfo, Set<String> objects) {
        String providerNo = loggedInInfo.getLoggedInProviderNo();
        Set<String> granted = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry : PERMISSION_BY_OBJECT.entrySet()) {
            if (objects.contains(entry.getKey())
                    && securityInfoManager.hasPrivilege(loggedInInfo, entry.getKey(), READ, null)) {
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
