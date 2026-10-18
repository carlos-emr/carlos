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
package io.github.carlos_emr.carlos.billings.ca.bc.pageUtil;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Rights checks shared by the read-only gates of the BC billing administration pages (billing codes,
 * private codes, referral doctors, MSP reconciliation reports).
 *
 * <p>Each gate opens a page whose forms post to a writer action. The gate requires the writer's own
 * security object at read level and, in addition, the condition the page itself (or its menu entry)
 * applies, so the gate is never looser than the page it opens. A condition lists alternatives: any one
 * of its objects with the given right satisfies it.</p>
 *
 * @since 2026-10-16
 */
final class BcBillingAdminPageAccess {

    private BcBillingAdminPageAccess() {
    }

    /**
     * Refuses a request with no signed-in user.
     *
     * @param loggedInInfo the session's signed-in user, or {@code null}
     * @throws SecurityException when there is no signed-in user
     */
    static void requireSession(LoggedInInfo loggedInInfo) {
        if (loggedInInfo == null) {
            throw new SecurityException("missing session");
        }
    }

    /**
     * Requires the given right on at least one of the given security objects.
     *
     * @param securityInfoManager the privilege lookup
     * @param loggedInInfo the signed-in user
     * @param right the right ({@code r} or {@code w})
     * @param objects the alternative security objects, any one of which suffices
     * @throws SecurityException when the user holds the right on none of them
     */
    static void requireAny(SecurityInfoManager securityInfoManager, LoggedInInfo loggedInInfo, String right,
                           String... objects) {
        for (String object : objects) {
            if (securityInfoManager.hasPrivilege(loggedInInfo, object, right, null)) {
                return;
            }
        }
        throw new SecurityException("missing required sec object (" + String.join(" | ", objects) + ")");
    }

    /**
     * Whether an optional numeric id parameter is absent or a plain unsigned integer. Gates call this only
     * after the rights checks, and never look the id up themselves.
     *
     * @param value the raw parameter value, or {@code null}
     * @return {@code true} when the value is absent or 1-9 decimal digits
     */
    static boolean isAbsentOrId(String value) {
        return value == null || value.matches("[0-9]{1,9}");
    }
}
