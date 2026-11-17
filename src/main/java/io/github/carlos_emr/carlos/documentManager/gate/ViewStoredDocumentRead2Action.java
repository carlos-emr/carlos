/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.documentManager.gate;

import io.github.carlos_emr.carlos.documentManager.IncomingDocumentCapacityResponse;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Single stored-document metadata/revision gate; runs before the JSP reads any document bytes. */
public final class ViewStoredDocumentRead2Action extends ViewDocumentRead2Action {
    @Override
    protected String afterPrivilegeGranted(HttpServletRequest request, HttpServletResponse response,
                                           SecurityInfoManager security, LoggedInInfo info) {
        response.setHeader("Cache-Control", "no-store");
        String document = request.getParameter("segmentID");
        if (!IncomingDocumentCapacityResponse.positiveId(document)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }
        try {
            IncomingDocumentCapacityResponse.requireStoredDocumentReadAccess(security, info, Integer.parseInt(document));
        } catch (SecurityException denied) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            throw denied;
        }
        return SUCCESS;
    }
}
