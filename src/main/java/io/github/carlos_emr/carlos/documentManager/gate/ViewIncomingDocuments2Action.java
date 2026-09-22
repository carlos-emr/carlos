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

import java.util.Set;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Gates the incoming-document view and the PDF mutations its JSP dispatches.
 * Read navigation retains the shared _edoc read check; document mutations also
 * require POST (covered by CSRFGuard) and _edoc write access before forwarding.
 */
public final class ViewIncomingDocuments2Action extends ViewDocumentRead2Action {
    private static final Set<String> PDF_ACTIONS = Set.of(
            "Rotate90", "Rotate180", "RotateM90", "RotateAll90", "RotateAll180", "RotateAllM90",
            "DeletePage", "DeletePDF", "ExtractPagePDF");

    @Override
    protected String afterPrivilegeGranted(HttpServletRequest request, HttpServletResponse response,
                                           SecurityInfoManager security, LoggedInInfo loggedInInfo) throws Exception {
        String action = request.getParameter("pdfAction");
        if (action == null || action.isBlank()) return SUCCESS;
        if (!"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "PDF changes require POST");
            return NONE;
        }
        if (!security.hasPrivilege(loggedInInfo, "_edoc", "w", null)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Document write access required");
            return NONE;
        }
        if (!PDF_ACTIONS.contains(action)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unsupported PDF action");
            return NONE;
        }
        return SUCCESS;
    }
}
