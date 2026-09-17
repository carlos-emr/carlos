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
package io.github.carlos_emr.carlos.demographic;

import java.io.IOException;
import jakarta.servlet.http.HttpServletResponse;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/** Authorizes the patient used by label queries before loading any report data. */
final class DemographicLabelAccess {
    private DemographicLabelAccess() { }

    /**
     * Returns the authorized canonical patient ID, or null after an authorized
     * caller supplies an invalid ID. Access denial takes precedence over HTTP 400.
     */
    static String authorizeRead(LoggedInInfo loggedInInfo, String requestedId,
            HttpServletResponse response, SecurityInfoManager securityInfoManager) throws IOException {
        requireRead(loggedInInfo, requestedId, securityInfoManager);
        final String demographicNo;
        try {
            int parsed = Integer.parseInt(requestedId);
            if (parsed <= 0) throw new NumberFormatException("Non-positive patient ID");
            demographicNo = Integer.toString(parsed);
        } catch (NumberFormatException ex) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid patient identifier");
            return null;
        }
        // JDBC numeric comparisons accept alternate spellings such as 00123. Check
        // the canonical ACL too so an alias cannot bypass a patient-specific denial.
        if (!demographicNo.equals(requestedId)) {
            requireRead(loggedInInfo, demographicNo, securityInfoManager);
        }
        return demographicNo;
    }

    private static void requireRead(LoggedInInfo loggedInInfo, String demographicNo,
            SecurityInfoManager securityInfoManager) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", demographicNo)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }
    }
}
