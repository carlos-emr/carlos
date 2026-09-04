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
package io.github.carlos_emr.carlos.admin.support;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

/**
 * Decides which security role names the Assign Role admin page may offer.
 *
 * <p>Multisite deployments hide the "super root" role named by the
 * {@code multioffice.admin.role.name} property from site-restricted
 * administrators, so that an administrator scoped to one office cannot hand
 * out installation-wide authority. That narrowing is presentation only — the
 * Assign Role POST handler does not re-check it — and it is meaningless
 * outside a multisite install, where every administrator is already
 * installation-wide.</p>
 *
 * <p>Two guards keep the narrowing from locking an installation out of its own
 * administrator role (the seeded {@code admin} role itself holds
 * {@code _site_access_privacy}, so it would otherwise hide itself from the only
 * account able to grant it):</p>
 * <ul>
 *   <li>it applies only when multisites is enabled, and</li>
 *   <li>it never hides a role the acting administrator already holds — offering
 *       a role you already have is not a privilege escalation.</li>
 * </ul>
 *
 * @since 2026-09-04
 */
public final class AssignableRoles {

    private AssignableRoles() {
    }

    /**
     * Splits the comma-separated role list carried in the {@code userrole}
     * session attribute into individual role names.
     *
     * @param commaSeparatedRoleNames raw session value; may be {@code null}
     * @return the distinct, trimmed role names in their original order; never
     *         {@code null}
     */
    public static Set<String> parseRoleNames(String commaSeparatedRoleNames) {
        Set<String> roleNames = new LinkedHashSet<String>();

        if (commaSeparatedRoleNames == null) {
            return roleNames;
        }

        for (String candidate : commaSeparatedRoleNames.split(",")) {
            String roleName = StringUtils.trimToNull(candidate);
            if (roleName != null) {
                roleNames.add(roleName);
            }
        }

        return roleNames;
    }

    /**
     * Filters the installation's role names down to the ones the acting
     * administrator may assign.
     *
     * @param allRoleNames        every configured role name, in display order;
     *                            may be {@code null}
     * @param siteAccessPrivacy   whether the acting administrator holds
     *                            {@code _site_access_privacy}
     * @param multisitesEnabled   whether the {@code multisites} property is on
     * @param protectedRoleName   the super-root role name from
     *                            {@code multioffice.admin.role.name}; blank
     *                            disables the narrowing
     * @param callerRoleNames     roles the acting administrator already holds;
     *                            may be {@code null}
     * @return the assignable role names; never {@code null}
     */
    public static List<String> filter(Collection<String> allRoleNames,
                                      boolean siteAccessPrivacy,
                                      boolean multisitesEnabled,
                                      String protectedRoleName,
                                      Collection<String> callerRoleNames) {
        List<String> assignable = new ArrayList<String>();

        if (allRoleNames == null) {
            return assignable;
        }

        String hiddenRoleName = resolveHiddenRoleName(
                siteAccessPrivacy, multisitesEnabled, protectedRoleName, callerRoleNames);

        for (String roleName : allRoleNames) {
            if (roleName == null || roleName.equals(hiddenRoleName)) {
                continue;
            }
            assignable.add(roleName);
        }

        return assignable;
    }

    /**
     * @return the single role name to withhold, or {@code null} when nothing is
     *         withheld
     */
    private static String resolveHiddenRoleName(boolean siteAccessPrivacy,
                                                boolean multisitesEnabled,
                                                String protectedRoleName,
                                                Collection<String> callerRoleNames) {
        if (!siteAccessPrivacy || !multisitesEnabled) {
            return null;
        }

        String hiddenRoleName = StringUtils.trimToNull(protectedRoleName);
        if (hiddenRoleName == null) {
            return null;
        }

        // Never hide a role the acting administrator already holds, otherwise the
        // last holder of the super-root role can never pass it on.
        if (callerRoleNames != null && callerRoleNames.contains(hiddenRoleName)) {
            return null;
        }

        return hiddenRoleName;
    }
}
