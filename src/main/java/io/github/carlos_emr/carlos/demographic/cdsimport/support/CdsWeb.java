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
package io.github.carlos_emr.carlos.demographic.cdsimport.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportDiagnosticDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportJobDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportPatientDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsImportAccessService;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsImportException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.Collection;
import java.time.temporal.TemporalAccessor;

/** HTTP-only safe projection. Physical storage paths and protected exception chains never enter JSON. */
public final class CdsWeb {
    private static final ObjectMapper JSON = new ObjectMapper();
    private CdsWeb() { }

    public static LoggedInInfo authorize(HttpServletRequest request, CdsImportAccessService access) {
        LoggedInInfo actor = authenticated(request);
        access.requireOperatorSession(actor);
        return actor;
    }

    public static LoggedInInfo authenticated(HttpServletRequest request) {
        if (request.getSession(false) == null) throw new CdsUploadException("AUTHENTICATION_REQUIRED", 401);
        LoggedInInfo actor = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (actor == null || actor.getLoggedInSecurity() == null || actor.getLoggedInSecurity().getId() == null
                || actor.getLoggedInProviderNo() == null) throw new CdsUploadException("AUTHENTICATION_REQUIRED", 401);
        return actor;
    }

    public static String operator(LoggedInInfo actor) { return "web-" + actor.getLoggedInSecurity().getId(); }

    public static void parameters(HttpServletRequest request, Set<String> accepted) {
        request.getParameterMap().forEach((key, values) -> {
            if (!(accepted.contains(key) || "CSRF-TOKEN".equals(key)) || values == null || values.length != 1) {
                throw new CdsUploadException("IMPORT_PARAMETERS_INVALID", 400);
            }
        });
    }

    public static void json(HttpServletResponse response, int status, Object data) throws IOException {
        response.setStatus(status); response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json"); response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        JSON.writeValue(response.getWriter(), data);
    }

    public static void failure(HttpServletResponse response, Exception failure) throws IOException {
        if (response.isCommitted()) return;
        int status = 503; String code = "IMPORT_REQUEST_UNAVAILABLE";
        if (failure instanceof CdsUploadException upload) { status = upload.status(); code = upload.code(); }
        else if (failure instanceof SecurityException) { status = 403; code = "IMPORT_ACCESS_DENIED"; }
        else if (failure instanceof IllegalArgumentException) { status = 400; code = "IMPORT_PARAMETERS_INVALID"; }
        else if (failure instanceof CdsImportException problem) {
            status = 409;
            String candidate = problem.getCode();
            if (candidate != null && candidate.matches("[A-Z][A-Z0-9_]{0,79}")) code = candidate;
        }
        json(response, status, Map.of("ok", false, "code", code,
                "message", "The request did not complete. Check the durable upload/run status before retrying. No success is implied."));
    }

    public static Map<String, Object> job(CdsImportJobDto row) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", row.id()); result.put("state", row.state()); result.put("sourceNamespace", row.sourceNamespace());
        result.put("profile", row.profile()); result.put("expectedPatients", row.expectedPatients());
        result.put("expectedUnits", row.expectedUnits()); result.put("ownerEpoch", row.ownerEpoch());
        result.put("cancelRequested", row.cancelRequested()); result.put("createdAt", String.valueOf(row.createdAt()));
        result.put("updatedAt", String.valueOf(row.updatedAt())); result.put("manifestDigest", row.manifestDigest());
        result.put("sourceDigest", row.sourceDigest()); result.put("optionsDigest", row.optionsDigest());
        result.put("rulesetDigest", row.rulesetDigest()); result.put("reportGeneration", row.reportGeneration());
        result.put("reportsAvailable", row.reportGeneration() > 0);
        return result;
    }

    public static Map<String, Object> patient(CdsImportPatientDto row) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("patientKey", row.patientKey()); result.put("sourcePatientId", row.sourcePatientId());
        result.put("copyKey", row.copyKey()); result.put("targetDemographicNo", row.targetDemographicNo());
        result.put("state", row.state()); result.put("held", row.held()); result.put("expectedUnits", row.expectedUnits());
        return result;
    }

    public static Map<String, Object> diagnostic(CdsImportDiagnosticDto row) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", row.id()); result.put("patientKey", row.patientKey()); result.put("unitKey", row.unitKey());
        result.put("severity", row.severity()); result.put("code", row.code()); result.put("stage", row.stage());
        result.put("sourceXPath", row.sourcePath()); result.put("summary", row.safeSummary());
        result.put("blocking", row.blocking()); result.put("resolved", row.resolved());
        String detail = row.protectedDetailLocation();
        result.put("hasEvidence", detail != null && detail.replace('\\', '/')
                .matches(".*/diagnostics/failure-[0-9]+-[0-9a-f-]{36}\\.json"));
        result.put("createdAt", String.valueOf(row.createdAt()));
        return result;
    }

    private static boolean sensitiveSummaryKey(String key) {
        String lower = key.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("location") || lower.contains("directory") || lower.contains("password")
                || lower.contains("secret") || lower.contains("path");
    }

    /** Bounded scalar/count projection also removes Java-time serializer dependence from JSON. */
    public static Object summary(Object value) { return summary(value, 0); }

    private static Object summary(Object value, int depth) {
        if (depth > 6) throw new CdsUploadException("IMPORT_SUMMARY_INVALID", 503);
        if (value == null || value instanceof Number || value instanceof Boolean) return value;
        if (value instanceof TemporalAccessor) return value.toString();
        if (value instanceof String string) {
            if (string.length() > 2048) throw new CdsUploadException("IMPORT_SUMMARY_INVALID", 503);
            return string;
        }
        if (value instanceof Map<?, ?> map) {
            if (map.size() > 200) throw new CdsUploadException("IMPORT_SUMMARY_INVALID", 503);
            Map<String, Object> result = new LinkedHashMap<>();
            for (var entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key) || key.length() > 128
                        || sensitiveSummaryKey(key)) {
                    throw new CdsUploadException("IMPORT_SUMMARY_INVALID", 503);
                }
                result.put(key, summary(entry.getValue(), depth + 1));
            }
            return java.util.Collections.unmodifiableMap(result);
        }
        if (value instanceof Collection<?> collection) {
            if (collection.size() > 200) throw new CdsUploadException("IMPORT_SUMMARY_INVALID", 503);
            List<Object> result = new java.util.ArrayList<>(collection.size());
            for (Object item : collection) result.add(summary(item, depth + 1));
            return java.util.Collections.unmodifiableList(result);
        }
        throw new CdsUploadException("IMPORT_SUMMARY_INVALID", 503);
    }
}
