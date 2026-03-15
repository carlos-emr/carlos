/*
 * Copyright (c) 2026. CARLOS EMR Project.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 */
package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.config.pageUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts2.ServletActionContext;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opensymphony.xwork2.ActionSupport;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Server-side proxy for CPSO (College of Physicians and Surgeons of Ontario) physician search.
 *
 * <p>Proxies search requests to the CPSO public register API at
 * {@code https://register.cpso.on.ca/Get-Search-Results/} to avoid browser CORS restrictions.
 * Used by the Add Specialist page to look up Ontario physicians by name and auto-populate
 * contact details (address, phone, fax).</p>
 *
 * <p>Endpoint: {@code CpsoSearch2Action.do?lastName=Smith&firstName=Jo}</p>
 *
 * @since 2026-03-15
 */
public class CpsoSearch2Action extends ActionSupport {

    private static final String CPSO_SEARCH_URL = "https://register.cpso.on.ca/Get-Search-Results/";
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 10000;
    private static final int MAX_RESPONSE_BYTES = 512 * 1024;

    private static final String EMPTY_RESPONSE = "{\"totalcount\":0,\"results\":[]}";

    private HttpServletRequest request = ServletActionContext.getRequest();
    private HttpServletResponse response = ServletActionContext.getResponse();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Proxies a physician search request to the CPSO register API.
     *
     * <p>Accepts {@code lastName} and {@code firstName} query parameters, forwards them
     * to the CPSO API, validates the response as JSON, and returns it to the client.</p>
     *
     * @return null (response written directly to output stream)
     */
    @Override
    public String execute() {
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_admin,_admin.consult", "r", null)) {
            throw new SecurityException("missing required security object _admin.consult");
        }

        String lastName = request.getParameter("lastName");
        String firstName = request.getParameter("firstName");

        if ((lastName == null || lastName.trim().isEmpty()) && (firstName == null || firstName.trim().isEmpty())) {
            writeJsonResponse(EMPTY_RESPONSE);
            return null;
        }

        try {
            String cpsoResponse = callCpsoApi(lastName, firstName);
            String validatedJson = validateAndSanitizeJson(cpsoResponse);
            writeJsonResponse(validatedJson);
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error calling CPSO search API", e);
            writeJsonResponse("{\"totalcount\":0,\"results\":[],\"error\":\"CPSO service unavailable\"}");
        }

        return null;
    }

    /**
     * Validates that the response is valid JSON and re-serializes it to prevent pass-through attacks.
     *
     * @param rawResponse String raw response from CPSO API
     * @return String validated and re-serialized JSON
     * @throws IOException if the response is not valid JSON
     */
    private String validateAndSanitizeJson(String rawResponse) throws IOException {
        JsonNode node = objectMapper.readTree(rawResponse);
        return objectMapper.writeValueAsString(node);
    }

    /**
     * Calls the CPSO search API with the given name parameters.
     *
     * @param lastName  physician last name (may be partial)
     * @param firstName physician first name (may be partial)
     * @return String JSON response from the CPSO API
     * @throws IOException if the HTTP connection fails or response exceeds size limit
     */
    private String callCpsoApi(String lastName, String firstName) throws IOException {
        StringBuilder postData = new StringBuilder();
        postData.append("doctorType=Any");

        if (lastName != null && !lastName.trim().isEmpty()) {
            postData.append("&lastName=").append(URLEncoder.encode(lastName.trim(), StandardCharsets.UTF_8.name()));
        }
        if (firstName != null && !firstName.trim().isEmpty()) {
            postData.append("&firstName=").append(URLEncoder.encode(firstName.trim(), StandardCharsets.UTF_8.name()));
        }

        HttpURLConnection conn = (HttpURLConnection) URI.create(CPSO_SEARCH_URL).toURL().openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("User-Agent", "CARLOS-EMR/1.0");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(postData.toString().getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                MiscUtils.getLogger().warn("CPSO API returned HTTP {}", responseCode);
                return EMPTY_RESPONSE;
            }

            StringBuilder responseBody = new StringBuilder();
            int totalChars = 0;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    totalChars += line.length();
                    if (totalChars > MAX_RESPONSE_BYTES) {
                        MiscUtils.getLogger().warn("CPSO API response exceeded {} byte limit", MAX_RESPONSE_BYTES);
                        return EMPTY_RESPONSE;
                    }
                    responseBody.append(line);
                }
            }

            return responseBody.toString().trim();
        } finally {
            conn.disconnect();
        }
    }

    /**
     * Writes a JSON string directly to the HTTP response.
     *
     * @param json JSON string to write
     */
    private void writeJsonResponse(String json) {
        try {
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(json);
        } catch (IOException e) {
            MiscUtils.getLogger().error("Error writing CPSO search response", e);
        }
    }
}
