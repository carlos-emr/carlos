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
 * View gate for {@code documentManager/ViewDocumentBrowser}. Extends the shared
 * {@code _edoc r} gate with the one request check the browser JSP cannot make
 * for itself.
 *
 * <p>{@code documentBrowser.jsp} selects the document set by branching on
 * {@code categorykey} ({@code indexOf("Private")} / {@code indexOf("Public")}),
 * and dereferences the parameter without a null check. A request that omits it
 * — a bookmark, a hand-typed support link, anything that is not the browser's
 * own form — therefore answered HTTP 500 with a {@code NullPointerException}
 * out of the compiled JSP (issue #3730).
 *
 * <p>Deciding this in the view is what CLAUDE.md's direct-response guidance
 * warns against, so the gate rejects the request instead: a missing or blank
 * {@code categorykey} is a malformed request and gets 400. Every real caller
 * already supplies it — the browser's own {@code DisplayDoc} form posts it as a
 * hidden field, {@code documentReport.jsp} and {@code demographic/edit.jsp}
 * put it in the link, and the delete / undelete / refile actions carry it
 * through their redirects — so nothing that works today starts failing.
 *
 * <p>A present-but-unrecognized value is deliberately still forwarded: the JSP
 * has an existing branch that renders "Remote documents not supported" for it,
 * and that behaviour is left alone.
 *
 * <p>On rejection this action writes the error response itself and returns
 * {@link #NONE} so Struts does not also resolve the {@code success} JSP
 * forward, per the direct-response contract in CLAUDE.md.
 */
public final class ViewDocumentBrowserRead2Action extends ViewDocumentRead2Action {

    @Override
    protected String afterPrivilegeGranted(HttpServletRequest request,
                                           HttpServletResponse response,
                                           SecurityInfoManager sim,
                                           LoggedInInfo loggedInInfo) throws Exception {
        String categoryKey = request.getParameter("categorykey");
        if (categoryKey == null || categoryKey.isBlank()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "missing categorykey");
            return NONE;
        }
        return SUCCESS;
    }
}
