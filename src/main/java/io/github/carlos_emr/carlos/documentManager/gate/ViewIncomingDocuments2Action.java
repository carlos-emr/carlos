/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
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
