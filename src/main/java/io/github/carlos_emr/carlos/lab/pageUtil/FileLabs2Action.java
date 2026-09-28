/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
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


package io.github.carlos_emr.carlos.lab.pageUtil;

import java.io.IOException;
import java.util.ArrayList;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;


import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.lab.ca.on.CommonLabResultData;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class FileLabs2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private ObjectMapper objectMapper = new ObjectMapper();

    public FileLabs2Action() {
    }

    public String execute() {
        if ("fileLabAjax".equals(request.getParameter("method"))) return fileLabAjax();
        if (!requirePost()) return NONE;
        ArrayNode files;
        ArrayList<String[]> selection = new ArrayList<>();
        try {
            String payload = singleParameter("flaggedLabs");
            var input = payload == null ? null : objectMapper.readTree(payload);
            if (input == null || !input.isObject() || !input.path("files").isArray() || input.path("files").isEmpty()) {
                throw new IllegalArgumentException("Missing filing selection");
            }
            files = (ArrayNode) input.path("files");
            for (var entry : files) {
                if (!entry.isTextual()) throw new IllegalArgumentException("Invalid filing selection");
                selection.add(entry.asText().split(":", -1));
            }
        } catch (IOException | RuntimeException invalid) {
            writeOutcome(400, false, false, null, null); return NONE;
        }
        boolean success;
        try {
            success = CommonLabResultData.fileLabs(selection, LoggedInInfo.getLoggedInInfoFromSession(request));
        } catch (RuntimeException failure) { writeFailure(failure, null); return NONE; }
        writeOutcome(success ? 200 : 500, success, true, null, files);
        return NONE;
    }

    public String fileLabAjax() {
        if (!requirePost()) return NONE;
        Integer document = null;
        boolean success;
        try {
            String id = singleParameter("flaggedLabId");
            String type = singleParameter("labType");
            if (!io.github.carlos_emr.carlos.documentManager.IncomingDocumentCapacityResponse.positiveId(id)) {
                throw new IllegalArgumentException("Invalid filing selection");
            }
            if ("DOC".equals(type)) document = Integer.valueOf(id);
            ArrayList<String[]> selection = new ArrayList<>();
            selection.add(new String[]{id, type});
            success = CommonLabResultData.fileLabs(selection, LoggedInInfo.getLoggedInInfoFromSession(request));
        } catch (RuntimeException failure) { writeFailure(failure, document); return NONE; }
        writeOutcome(success ? 200 : 500, success, true, document, null);
        return NONE;
    }

    private String singleParameter(String name) {
        String[] values = request.getParameterValues(name);
        if (values == null || values.length != 1) throw new IllegalArgumentException("Missing or ambiguous filing selection");
        return values[0];
    }

    private boolean requirePost() {
        if ("POST".equals(request.getMethod())) return true;
        response.setHeader("Allow", "POST");
        writeOutcome(405, false, false, null, null);
        return false;
    }

    private void writeFailure(RuntimeException failure, Integer document) {
        boolean accepted = failure instanceof CommonLabResultData.FilingFailure filing && filing.accepted();
        Throwable cause = failure;
        while (cause instanceof CommonLabResultData.FilingFailure && cause.getCause() != null) cause = cause.getCause();
        int status = accepted ? 500 : cause instanceof SecurityException ? 403 : cause instanceof IllegalArgumentException ? 400 : 500;
        MiscUtils.getLogger().warn("Inbox filing was not confirmed", failure);
        writeOutcome(status, false, accepted, document, null);
    }

    // Response errors never turn an already accepted filing into a safely retryable operation.
    private void writeOutcome(int status, boolean success, boolean accepted, Integer document, ArrayNode files) {
        ObjectNode result = objectMapper.createObjectNode().put("success", success).put("accepted", accepted).put("retryable", false);
        if (document != null) result.put("document", document);
        if (files != null) result.set("files", files);
        if (!success) result.put("error", accepted ? "Filing outcome is unconfirmed; do not submit again" : "Filing was refused");
        response.setStatus(status); response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        try { response.getOutputStream().write(result.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
        catch (IOException failure) {
            MiscUtils.getLogger().error("Could not report inbox filing outcome", failure);
            if (!response.isCommitted()) response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
}
