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
package io.github.carlos_emr.carlos.demographic.cdsexport.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportJobDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportAccessService;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportStaleControlException;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportSubmissionConflictException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Export HTTP boundary: finite errors and explicit scalar projections never serialize clinical intent. */
@Service
public class CdsExportWebService {
    public static final int API_VERSION = 1;
    public static final String API_HEADER = "X-CARLOS-CDS-Export-API-Version";
    private static final ObjectMapper JSON = new ObjectMapper();
    private final CdsExportAccessService access;
    public CdsExportWebService(CdsExportAccessService access) { this.access = access; }
    public LoggedInInfo authorize(HttpServletRequest request) {
        if (request.getSession(false) == null) throw new SecurityException("CDS_EXPORT_SESSION_REQUIRED");
        var actor = LoggedInInfo.getLoggedInInfoFromSession(request.getSession(false));
        if (actor == null || actor.getLoggedInSecurity() == null || actor.getLoggedInSecurity().getId() == null
                || actor.getLoggedInProviderNo() == null) throw new SecurityException("CDS_EXPORT_SESSION_REQUIRED");
        access.operatorSession(actor); return actor;
    }
    public void parameters(HttpServletRequest request, Set<String> accepted, boolean page) {
        String[] versions = request.getParameterValues("apiVersion");
        if (!(page && versions == null) && (versions == null || versions.length != 1 || !"1".equals(versions[0]))) {
            throw new IllegalArgumentException("CDS_EXPORT_CLIENT_REFRESH_REQUIRED");
        }
        request.getParameterMap().forEach((key, values) -> {
            if (!(accepted.contains(key) || Set.of("apiVersion", "CSRF-TOKEN").contains(key))
                    || values == null || values.length != 1 || values[0] == null || values[0].length() > 120_000) {
                throw new IllegalArgumentException("CDS_EXPORT_PARAMETERS_INVALID");
            }
        });
    }
    public void json(HttpServletResponse response, int status, Map<String, ?> values) throws IOException {
        var envelope = new LinkedHashMap<String, Object>(values); envelope.put("apiVersion", API_VERSION);
        response.setStatus(status); response.setCharacterEncoding("UTF-8"); response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store"); response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader(API_HEADER, "1"); JSON.writeValue(response.getOutputStream(), envelope);
    }
    public void failure(HttpServletResponse response, Exception failure) throws IOException {
        if (response.isCommitted()) throw new IOException("CDS_EXPORT_RESPONSE_FAILED", failure);
        response.resetBuffer(); response.setContentLengthLong(-1); response.setHeader("Content-Disposition", null);
        int status = 503; String code = "CDS_EXPORT_REQUEST_UNAVAILABLE";
        if (failure instanceof SecurityException) { status = 403; code = "CDS_EXPORT_ACCESS_DENIED"; }
        else if (failure instanceof CdsExportStaleControlException) { status = 409; code = "CDS_EXPORT_STATUS_CHANGED"; }
        else if (failure instanceof CdsExportSubmissionConflictException) { status = 409; code = "CDS_EXPORT_SUBMISSION_CONFLICT"; }
        else if (failure instanceof io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportArtifactUnavailableException) { status = 409; code = "CDS_EXPORT_ARTIFACT_UNAVAILABLE"; }
        else if (failure instanceof IllegalStateException && "CDS_EXPORT_ARTIFACT_BUSY".equals(failure.getMessage())) { status = 409; code = "CDS_EXPORT_ARTIFACT_BUSY"; }
        else if (failure instanceof IllegalArgumentException) {
            status = 400; code = "CDS_EXPORT_PARAMETERS_INVALID";
            if ("CDS_EXPORT_CLIENT_REFRESH_REQUIRED".equals(failure.getMessage())) { status = 409; code = failure.getMessage(); }
        }
        json(response, status, Map.of("ok", false, "code", code,
                "message", "The request did not complete. Check the saved submission or job status before retrying."));
    }
    public Map<String, Object> job(CdsExportJobDto row) {
        var value = new LinkedHashMap<String, Object>();
        value.put("id", row.id()); value.put("state", row.state().name()); value.put("revision", row.revision());
        value.put("totalPatients", row.totalPatients()); value.put("processedPatients", row.processedPatients());
        value.put("blockedPatients", row.blockedPatients()); value.put("cancelRequested", row.cancelRequested());
        value.put("workerOwned", row.workerOwned()); value.put("epoch", row.epoch()); value.put("safeCode", row.safeCode());
        value.put("createdAt", row.createdAt().toString()); value.put("updatedAt", row.updatedAt().toString());
        value.put("artifactAvailable", row.artifactAvailable()); value.put("waiting", row.waiting());
        value.put("complete", row.complete());
        return value;
    }
}
