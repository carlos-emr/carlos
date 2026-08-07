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
package io.github.carlos_emr.carlos.integration.mcedt;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Shared privilege + HTTP-method guards for MCEDT actions.
 *
 * MCEDT endpoints access the Ontario MOH EDT service (file upload, download,
 * update, resubmit) and change passwords on behalf of the clinic. Preserve the
 * legacy {@code _admin.billing} access gate across all MCEDT entry points, and
 * require POST for state-changing methods to prevent CSRF-style triggering of
 * MOH transactions.
 *
 * @since 2026-03-20
 */
public final class McedtSecurity {

    /** Preserve the historical billing-admin write gate used by MCEDT actions. */
    public static final String PRIVILEGE = "_admin.billing";

    private McedtSecurity() {
    }

    /**
     * Assert the session holds legacy MCEDT access.
     *
     * @param request HttpServletRequest the servlet request
     * @throws SecurityException if the privilege is missing
     */
    public static void requireRead(HttpServletRequest request) {
        assertPrivilege(request, "r");
    }

    /**
     * Assert the session holds legacy MCEDT mutation access.
     *
     * @param request HttpServletRequest the servlet request
     * @throws SecurityException if the privilege is missing
     */
    public static void requireWrite(HttpServletRequest request) {
        assertPrivilege(request, "w");
    }

    /**
     * Check whether the request uses the POST method. State-changing MCEDT
     * methods (upload, download, delete, submit, change-password, resubmit)
     * must be POST.
     *
     * @param request HttpServletRequest the servlet request
     * @return boolean true if the request is POST, false otherwise
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public static boolean isPost(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod());
    }

    /**
     * Throw {@link SecurityException} unless the request is POST.
     *
     * @param request HttpServletRequest the servlet request
     * @throws SecurityException if the request is not POST
     */
    public static void requirePost(HttpServletRequest request) {
        if (!isPost(request)) {
            throw new SecurityException("MCEDT mutation requires POST");
        }
    }

    private static void assertPrivilege(HttpServletRequest request, String mode) {
        SecurityInfoManager sim = SpringUtils.getBean(SecurityInfoManager.class);
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null || !sim.hasPrivilege(loggedInInfo, PRIVILEGE, mode, null)) {
            throw new SecurityException("missing required sec object (" + PRIVILEGE + " " + mode + ")");
        }
    }
}
