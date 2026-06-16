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
package io.github.carlos_emr.carlos.documentManager.gate;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * View gate for {@code documentManager/ViewDocumentReport}. Extends the shared
 * {@code _edoc r} gate with request validation and per-patient authorization
 * for the document-report view.
 *
 * <p>Because this gate is wired to the report route by class (not by sniffing
 * the request path), the report-specific checks below can never be silently
 * skipped: any request that reaches {@code ViewDocumentReport} runs them.
 *
 * <p>On a validation or authorization failure this action writes the error
 * response itself ({@code response.sendError(...)}) and returns {@link #NONE}
 * so Struts does not resolve the {@code success} JSP forward — per the
 * direct-response contract in CLAUDE.md.
 */
public final class ViewDocumentReportRead2Action extends ViewDocumentRead2Action {

    @Override
    protected String afterPrivilegeGranted(HttpServletRequest request,
                                           HttpServletResponse response,
                                           SecurityInfoManager sim,
                                           LoggedInInfo loggedInInfo) throws Exception {
        return validateDocumentReportRequest(request, response, sim, loggedInInfo) ? SUCCESS : NONE;
    }

    /**
     * Validates the report request parameters and enforces per-patient access.
     *
     * <p>A request with no {@code function} is not patient-scoped (it renders an
     * empty report context), so there is no demographic to authorize against —
     * only the optional {@code appointmentNo} is range-checked. For the
     * {@code provider}/{@code providers} functions {@code functionid} is a
     * provider number, not a demographic, so the circle-of-care patient-access
     * check does not apply; {@code _edoc r} already gated the request. Only the
     * {@code demographic} function carries a patient identifier and is checked
     * against {@link SecurityInfoManager#isAllowedAccessToPatientRecord}.
     *
     * @return {@code true} to forward to the report JSP; {@code false} after an
     *         error response has been written (caller returns {@link #NONE}).
     */
    private static boolean validateDocumentReportRequest(HttpServletRequest request,
                                                         HttpServletResponse response,
                                                         SecurityInfoManager sim,
                                                         LoggedInInfo loggedInInfo) throws Exception {
        String function = request.getParameter("function");
        if (function == null) {
            return validateOptionalNonNegativeInteger(request.getParameter("appointmentNo"), "appointmentNo", response);
        }

        String fn = function.toLowerCase(Locale.ROOT);

        if (!"demographic".equals(fn)
                && !"provider".equals(fn) 
                && !"providers".equals(fn)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "invalid function");
            return false;
        }

        Integer functionId = parsePositiveInteger(request.getParameter("functionid"));
        if ("demographic".equals(fn) && functionId == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "invalid functionid");
            return false;
        }

        if (!validateOptionalNonNegativeInteger(request.getParameter("appointmentNo"), "appointmentNo", response)) {
            return false;
        }

        if ("demographic".equals(fn) && !sim.isAllowedAccessToPatientRecord(loggedInInfo, functionId)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "unauthorized access to patient record");
            return false;
        }

        return true;
    }

    private static boolean validateOptionalNonNegativeInteger(String value,
                                                             String parameterName,
                                                             HttpServletResponse response) throws Exception {
        if (value == null || value.isEmpty()) {
            return true;
        }
        if (parseNonNegativeInteger(value) == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "invalid " + parameterName);
            return false;
        }
        return true;
    }

    private static Integer parsePositiveInteger(String value) {
        Integer parsed = parseNonNegativeInteger(value);
        return parsed != null && parsed > 0 ? parsed : null;
    }

    /**
     * Parses a non-negative {@code int}, returning {@code null} for any value
     * that is empty, contains a non-digit, or overflows {@code int}. The digit
     * pre-scan rejects signs/whitespace; the {@code catch} handles all-digit
     * strings that exceed {@link Integer#MAX_VALUE} (and non-ASCII digit code
     * points), so out-of-range ids are rejected rather than silently truncated.
     */
    private static Integer parseNonNegativeInteger(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return null;
            }
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
