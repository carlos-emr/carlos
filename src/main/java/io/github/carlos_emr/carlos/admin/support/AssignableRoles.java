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
import java.util.List;

import org.apache.commons.lang3.StringUtils;

/**
 * Decides which security role names the Assign Role admin page may offer.
 *
 * <p>Multisite deployments hide the "super root" role named by the
 * {@code multioffice.admin.role.name} property from site-restricted
 * administrators, so that an administrator scoped to one office cannot hand
 * out installation-wide authority. That narrowing is presentation only — the
 * Assign Role POST handler does not re-check it.</p>
 *
 * <p>The narrowing applies <em>only</em> when multisites is enabled. Upstream
 * introduced it with the multi-office feature (2010) and granted
 * {@code _site_access_privacy} to no role at all, so on a single-office install
 * it never fired. CARLOS later seeded that object to the {@code admin} role,
 * which made the unguarded narrowing hide the administrator role from the only
 * account able to grant it — a lockout, since the installer tells operators to
 * deactivate the seeded account once real ones exist.</p>
 *
 * <p>The multisites gate restores the upstream behaviour without inventing new
 * policy: inside a genuine multisite install a site-scoped administrator still
 * does not see the super-root role. Removing the stray seed grant is the
 * complete fix and would make this gate redundant; it is tracked separately.</p>
 *
 * @since 2026-09-04
 */
public final class AssignableRoles {

    private AssignableRoles() {
    }

    /**
     * Filters the installation's role names down to the ones the acting
     * administrator may assign.
     *
     * @param allRoleNames       every configured role name, in display order;
     *                           may be {@code null}
     * @param siteAccessPrivacy  whether the acting administrator holds
     *                           {@code _site_access_privacy}
     * @param multisitesEnabled  whether the {@code multisites} property is on
     * @param protectedRoleName  the super-root role name from
     *                           {@code multioffice.admin.role.name}; blank
     *                           disables the narrowing
     * @return the assignable role names; never {@code null}
     */
    public static List<String> filter(Collection<String> allRoleNames,
                                      boolean siteAccessPrivacy,
                                      boolean multisitesEnabled,
                                      String protectedRoleName) {
        List<String> assignable = new ArrayList<String>();

        if (allRoleNames == null) {
            return assignable;
        }

        String hiddenRoleName = resolveHiddenRoleName(
                siteAccessPrivacy, multisitesEnabled, protectedRoleName);

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
                                                String protectedRoleName) {
        if (!siteAccessPrivacy || !multisitesEnabled) {
            return null;
        }
        return StringUtils.trimToNull(protectedRoleName);
    }
}
