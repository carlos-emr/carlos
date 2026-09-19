/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */


package io.github.carlos_emr.carlos.scratch;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.carlos_emr.carlos.commn.dao.ScratchPadDao;
import io.github.carlos_emr.carlos.commn.model.JSONAction;
import io.github.carlos_emr.carlos.commn.model.ScratchPad;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Displays and saves the session provider's scratchpad and manages owned versions.
 * Named operations are dispatched explicitly so invalid requests cannot become saves.
 * Scratchpad is a personal session-provider feature, exposed without a separate
 * module privilege in the user-settings menu. Every entry point requires a session
 * provider; version operations additionally enforce the stored provider's ownership.
 *
 * @author jay
 */
public class Scratch2Action extends JSONAction {

    private static final String SESSION_PROVIDER_REQUIRED = "Session provider required";

    private final ScratchPadDao scratchPadDao = SpringUtils.getBean(ScratchPadDao.class);

    /**
     * Displays an owned scratchpad version on GET or HEAD.
     *
     * @return {@code scratchPadVersion} for an owned record, or {@link #NONE} after
     *         HTTP 401 (no session provider), 405 (wrong verb), 400 (invalid ID), or
     *         404 (missing or foreign version)
     * @throws Exception if version lookup or response generation fails
     */
    public String showVersion() throws Exception {
        if (sessionProviderNo() == null) return rejectRequest(HttpServletResponse.SC_UNAUTHORIZED, SESSION_PROVIDER_REQUIRED);
        if (!"GET".equals(request.getMethod()) && !"HEAD".equals(request.getMethod())) {
            return rejectRequest(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "GET or HEAD required");
        }
        ScratchPad scratch = findOwnedVersion();
        if (scratch == null) return NONE;
        request.setAttribute("ScratchPad", scratch);
        return "scratchPadVersion";
    }

    private ScratchPad findOwnedVersion() {
        String providerNo = sessionProviderNo();
        if (providerNo == null || providerNo.isBlank()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return null;
        }
        int id;
        try {
            id = Integer.parseInt(request.getParameter("id"));
            if (id <= 0) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }
        ScratchPad scratch = scratchPadDao.find(id);
        // Use the stored owner, never the caller's providerNo parameter.
        if (scratch == null || !providerNo.equals(scratch.getProviderNo())) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }
        return scratch;
    }

    /**
     * Routes version reads and deletes explicitly. A POST without an operation, or
     * with method=save, saves the session provider's scratchpad. Unknown operations
     * return HTTP 400 instead of falling through to the save path. The text parameter
     * is required, but an empty string remains a valid deliberate clear.
     *
     * @return a view result, or {@link #NONE} after a direct response; errors use
     *         HTTP 400 for invalid input, 401 for no session provider, 403 for a
     *         provider mismatch, 404 for a missing/foreign version, 405 for a wrong
     *         verb, 409 for a stale save revision, or 500 for invalid stored data or deletion failure
     * @throws Exception if scratchpad storage or response generation fails
     */
    @Override
    public String execute() throws Exception {
        String providerNo = sessionProviderNo();
        if (providerNo == null) return rejectRequest(HttpServletResponse.SC_UNAUTHORIZED, SESSION_PROVIDER_REQUIRED);
        String method = request.getParameter("method");
        if ("showVersion".equals(method)) return showVersion();
        if ("delete".equals(method)) return delete();
        if (method != null && !method.isBlank() && !"save".equals(method)) {
            return rejectRequest(HttpServletResponse.SC_BAD_REQUEST, "Unknown scratchpad operation");
        }
        if (!"POST".equals(request.getMethod())) {
            return "save".equals(method)
                    ? rejectRequest(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "POST required")
                    : SUCCESS;
        }

        if (!isRequestForSessionProvider(providerNo, request.getParameter("providerNo"))) {
            MiscUtils.getLogger().error("Scratch pad provider mismatch; request and session provider values omitted from log");
            return rejectRequest(HttpServletResponse.SC_FORBIDDEN, "Provider mismatch");
        }
        String text = request.getParameter("scratchpad");
        if (text == null) return rejectRequest(HttpServletResponse.SC_BAD_REQUEST, "Scratchpad text is required");
        int expectedId;
        try {
            expectedId = Integer.parseInt(request.getParameter("id"));
            if (expectedId < 0) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            return rejectRequest(HttpServletResponse.SC_BAD_REQUEST, "Valid scratchpad revision required");
        }
        try {
            ScratchPadDao.SaveResult saved = scratchPadDao.saveIfCurrent(providerNo, expectedId, text);
            if (saved.conflict()) {
                return rejectRequest(HttpServletResponse.SC_CONFLICT,
                        "Another window changed this scratchpad. Your unsaved text has been kept. Open the current version and reconcile your changes before saving.");
            }
            ObjectNode result = objectMapper.createObjectNode();
            result.put("success", true);
            result.put("id", saved.version().getId().toString());
            // JSON is a data response, not HTML. The editor assigns text to .value;
            // encoding here would corrupt literal entities, plus signs and percent sequences.
            result.put("text", saved.version().getText());
            jsonResponse(result);
        } catch (RuntimeException ex) {
            MiscUtils.getLogger().error("Unable to save scratchpad ({})", ex.getClass().getSimpleName());
            return rejectRequest(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Scratchpad could not be saved. Your unsaved text has been kept; please retry.");
        }
        return NONE;
    }

    /**
     * Soft-deletes a session provider's owned version on POST and writes JSON.
     *
     * @return {@link #NONE} after success or HTTP 401 (no session provider),
     *         405 (wrong verb), 400 (invalid ID), 404 (missing/foreign version), or
     *         500 (persistence failure); failures contain {@code success:false}
     */
    public String delete() {
        if (sessionProviderNo() == null) return rejectRequest(HttpServletResponse.SC_UNAUTHORIZED, SESSION_PROVIDER_REQUIRED);
        ObjectNode result = objectMapper.createObjectNode();
        result.put("success", false);
        if (!"POST".equals(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            jsonResponse(result);
            return NONE;
        }
        ScratchPad scratch = findOwnedVersion();
        if (scratch == null) {
            jsonResponse(result);
            return NONE;
        }
        try {
            scratch.setStatus(false);
            scratchPadDao.merge(scratch);
            result.put("id", scratch.getId().toString());
            result.put("version", scratch.getDateTime() != null
                    ? java.time.Instant.ofEpochMilli(scratch.getDateTime().getTime()).toString() : null);
            result.put("success", true);
        } catch (RuntimeException ex) {
            MiscUtils.getLogger().error("Unable to delete scratchpad version ({})", ex.getClass().getSimpleName());
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
        jsonResponse(result);
        return NONE;
    }

    private String sessionProviderNo() {
        HttpSession session = request.getSession(false);
        Object provider = session == null ? null : session.getAttribute("user");
        return provider instanceof String value && !value.isBlank() ? value : null;
    }

    private String rejectRequest(int status, String message) {
        response.setStatus(status);
        ObjectNode result = objectMapper.createObjectNode();
        result.put("success", false);
        result.put("message", message);
        jsonResponse(result);
        return NONE;
    }

	static boolean isRequestForSessionProvider(String sessionProviderNo, String requestProviderNo) {
		return sessionProviderNo != null
			&& !sessionProviderNo.trim().isEmpty()
			&& (requestProviderNo == null
				|| requestProviderNo.trim().isEmpty()
				|| sessionProviderNo.equals(requestProviderNo));
	}
}
