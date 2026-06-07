/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.lab.gate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.carlos_emr.carlos.lab.ca.on.CommonLabTestValues;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Hashtable;

/**
 * JSON endpoint returning up to 10 most-recent measurement values for a given
 * demographic and LOINC code, ordered newest-first. Used by labDisplay.jsp to
 * show prior results alongside the current OBX value with a hover tooltip.
 *
 * <p>Response is a JSON array:
 * {@code [{"result":"65","date":"2025-11-20","abn":"N"}, ...]}
 *
 * <p>Values come from the measurements table via
 * {@link CommonLabTestValues#findValuesByLoinc}, which is populated when HL7
 * lab messages are processed and the OBX identifier has a matching
 * {@code MeasurementMap} entry with a LOINC code.
 *
 * @since 2026-06-07
 */
public final class LabHistoryByLoinc2Action extends ActionSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Reads {@code demographicNo} and {@code loincCode} from the query string, verifies the
     * caller holds the {@code _lab} read privilege for that patient, then streams the 10
     * most-recent measurement values for the patient/LOINC pair as a JSON array.
     *
     * <p>Request parameters:
     * <ul>
     *   <li>{@code demographicNo} – the patient's demographic number (digits only)</li>
     *   <li>{@code loincCode} – a valid LOINC code (digits hyphen digits, e.g. {@code 33914-3})</li>
     * </ul>
     *
     * <p>Response body on HTTP 200: a JSON array ordered newest-first, e.g.
     * {@code [{"result":"65","date":"2025-11-20","abn":"N"}, ...]}.
     * Returns HTTP 400 for missing or malformed parameters; HTTP 403 when the caller lacks
     * the required lab read privilege for the requested patient.
     *
     * @return {@link #NONE} in all paths; this action writes directly to the servlet response
     * @throws Exception if JSON serialization or response writing fails
     */
    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        // Parse and validate inputs before the privilege check so demographicNo
        // can be passed to hasPrivilege for patient-scoped authorization.
        String demographicNo = StringUtils.trimToEmpty(request.getParameter("demographicNo"));
        String loincCode = StringUtils.trimToEmpty(request.getParameter("loincCode"));

        if (!demographicNo.matches("\\d+") || !loincCode.matches("[0-9]+-[0-9]+")) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_lab", "r", demographicNo)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return NONE;
        }

        ArrayList<Hashtable<String, Serializable>> history =
                CommonLabTestValues.findValuesByLoinc(demographicNo, loincCode);

        ArrayNode arr = MAPPER.createArrayNode();
        int limit = Math.min(history.size(), 10);
        for (int i = 0; i < limit; i++) {
            Hashtable<String, Serializable> h = history.get(i);
            ObjectNode entry = MAPPER.createObjectNode();
            entry.put("result", String.valueOf(h.getOrDefault("result", "")));
            // dateObserved from java.sql.Date/Timestamp toString starts with YYYY-MM-DD
            String dateStr = String.valueOf(h.getOrDefault("date", ""));
            if (dateStr.length() > 10) {
                dateStr = dateStr.substring(0, 10);
            }
            entry.put("date", dateStr);
            entry.put("abn", String.valueOf(h.getOrDefault("abn", "")));
            arr.add(entry);
        }

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        MAPPER.writeValue(response.getWriter(), arr);
        return NONE;
    }
}
