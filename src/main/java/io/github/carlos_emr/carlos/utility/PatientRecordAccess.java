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
package io.github.carlos_emr.carlos.utility;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;

/**
 * Patient-specific privilege and record-access boundary for chart and registry actions.
 *
 * @since 2026-10-24
 */
public final class PatientRecordAccess {
    private PatientRecordAccess() { }

    /**
     * Requires both the operation's privilege and access to this patient's record. Callers
     * validate and canonicalize the patient identifier before calling this method.
     *
     * @param security SecurityInfoManager the authorization service
     * @param login LoggedInInfo the authenticated provider; null is refused
     * @param patient String the canonical positive Integer patient identifier
     * @param object String the security object required for the operation
     * @param right String the required privilege, such as {@code r} or {@code w}
     * @throws SecurityException if the login, privilege or patient record access is missing
     * @throws NumberFormatException if patient is not a valid Integer
     */
    public static void require(SecurityInfoManager security, LoggedInInfo login, String patient,
                               String object, String right) {
        if (login == null || !security.hasPrivilege(login, object, right, patient)) {
            throw new SecurityException("missing required sec object (" + object + ")");
        }
        if (!security.isAllowedAccessToPatientRecord(login, Integer.valueOf(patient))) {
            throw new SecurityException("Patient record access denied");
        }
    }
}
