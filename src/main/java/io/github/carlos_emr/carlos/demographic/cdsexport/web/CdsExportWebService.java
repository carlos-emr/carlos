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
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Export HTTP boundary: finite errors and explicit scalar projections never serialize clinical
 * intent.
 */
@Service
public class CdsExportWebService {
    private static final Logger LOG = LogManager.getLogger(CdsExportWebService.class);
    public static final int API_VERSION = 1;
    public static final String API_HEADER = "X-CARLOS-CDS-Export-API-Version";
    private static final ObjectMapper JSON = new ObjectMapper();
    private final CdsExportAccessService access;

    public CdsExportWebService(CdsExportAccessService access) {
        this.access = access;
    }

    public LoggedInInfo authorize(HttpServletRequest request, SecurityInfoManager security) {
        var session = request.getSession(false);
        if (session == null) throw new SecurityException("CDS_EXPORT_SESSION_REQUIRED");
        var actor = LoggedInInfo.getLoggedInInfoFromSession(session);
        if (actor == null
                || actor.getLoggedInSecurity() == null
                || actor.getLoggedInSecurity().getId() == null
                || actor.getLoggedInProviderNo() == null)
            throw new SecurityException("CDS_EXPORT_SESSION_REQUIRED");
        if (!security.hasPrivilege(actor, "_demographic", "r", (String) null))
            throw new SecurityException("missing required sec object (_demographic)");
        if (!exportRight(actor, security))
            throw new SecurityException("missing required sec object (_cds_export)");
        // The request gate uses the captured session; the service independently rebuilds
        // the current account/provider and read-only grants to detect revocation.
        access.operatorSession(actor);
        return actor;
    }

    /**
     * Whether pages that link to the export page should show the link: the same rights
     * {@link #authorize} requires, so no user is offered a page that refuses them. The page
     * itself still authorizes every request.
     */
    public static boolean mayOpen(LoggedInInfo actor, SecurityInfoManager security) {
        return actor != null
                && security.hasPrivilege(actor, "_demographic", "r", (String) null)
                && exportRight(actor, security);
    }

    private static boolean exportRight(LoggedInInfo actor, SecurityInfoManager security) {
        return security.hasPrivilege(actor, "_admin", "w", (String) null)
                || security.hasPrivilege(actor, "_cds_export", "w", (String) null);
    }

    public void parameters(HttpServletRequest request, Set<String> accepted, boolean page) {
        String[] versions = request.getParameterValues("apiVersion");
        if (!(page && versions == null)
                && (versions == null || versions.length != 1 || !"1".equals(versions[0]))) {
            throw new IllegalArgumentException("CDS_EXPORT_CLIENT_REFRESH_REQUIRED");
        }
        request.getParameterMap()
                .forEach(
                        (key, values) -> {
                            if (!(accepted.contains(key)
                                            || Set.of("apiVersion", "CSRF-TOKEN").contains(key))
                                    || values == null
                                    || values.length != 1
                                    || values[0] == null
                                    || values[0].length() > 120_000) {
                                throw new IllegalArgumentException("CDS_EXPORT_PARAMETERS_INVALID");
                            }
                        });
    }

    public void json(HttpServletResponse response, int status, Map<String, ?> values)
            throws IOException {
        var envelope = new LinkedHashMap<String, Object>(values);
        envelope.put("apiVersion", API_VERSION);
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader(API_HEADER, "1");
        JSON.writeValue(response.getOutputStream(), envelope);
    }

    public void failure(HttpServletResponse response, Exception failure) throws IOException {
        LOG.error("CDS_EXPORT_REQUEST_FAILED {}", LogSafe.exceptionTrace(failure));
        // A partial or flushed binary/JSON response must never enter Struts error rendering.
        if (response.isCommitted()) return;
        clearForError(response);
        Refusal refusal = refusal(failure);
        try {
            var body = new LinkedHashMap<String, Object>();
            body.put("ok", false);
            body.put("code", refusal.code());
            body.put(
                    "message",
                    "The request did not complete. Check the saved submission or job status"
                            + " before retrying.");
            // A fixed security-object name only, so the operator knows which right to grant.
            if (refusal.missingRight() != null) body.put("missingRight", refusal.missingRight());
            json(response, refusal.status(), body);
        } catch (IOException | IllegalStateException writeFailed) {
            LOG.error("CDS_EXPORT_ERROR_RESPONSE_FAILED {}", LogSafe.exceptionTrace(writeFailed));
        }
    }

    /**
     * The failure answer of a route the page opens in its own window (the package download and
     * the diagnostic report): a short page in the viewer's language, because a person reads it,
     * where the API's JSON would show as raw text. A script caller still receives the JSON.
     */
    public void navigationFailure(
            HttpServletRequest request, HttpServletResponse response, Exception failure)
            throws IOException {
        if (io.github.carlos_emr.carlos.utility.RequestNegotiation.isAjax(request)) {
            failure(response, failure);
            return;
        }
        LOG.error("CDS_EXPORT_REQUEST_FAILED {}", LogSafe.exceptionTrace(failure));
        if (response.isCommitted()) return;
        clearForError(response);
        Refusal refusal = refusal(failure);
        try {
            java.util.ResourceBundle messages =
                    java.util.ResourceBundle.getBundle(
                            "cdsExportMessages",
                            request.getLocale() == null ? java.util.Locale.ROOT : request.getLocale(),
                            java.util.ResourceBundle.Control.getNoFallbackControl(
                                    java.util.ResourceBundle.Control.FORMAT_PROPERTIES));
            String key = "page.failure." + refusal.code();
            String message =
                    messages.containsKey(key)
                            ? messages.getString(key)
                            : messages.getString("page.failure.default");
            String language = messages.getLocale().getLanguage().isEmpty()
                    ? "en"
                    : messages.getLocale().getLanguage();
            response.setStatus(refusal.status());
            response.setCharacterEncoding("UTF-8");
            response.setContentType("text/html");
            response.setHeader("Cache-Control", "no-store");
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader(API_HEADER, "1");
            response.getWriter()
                    .write(
                            "<!DOCTYPE html><html lang=\""
                                    + io.github.carlos_emr.carlos.utility.SafeEncode
                                            .forHtmlAttribute(language)
                                    + "\"><head><meta charset=\"UTF-8\"><title>"
                                    + io.github.carlos_emr.carlos.utility.SafeEncode.forHtmlContent(
                                            messages.getString("page.failure.title"))
                                    + "</title></head><body><p>"
                                    + io.github.carlos_emr.carlos.utility.SafeEncode.forHtmlContent(
                                            message)
                                    + "</p><p><code>"
                                    + io.github.carlos_emr.carlos.utility.SafeEncode.forHtmlContent(
                                            refusal.code())
                                    + "</code></p></body></html>");
        } catch (IOException | RuntimeException writeFailed) {
            LOG.error("CDS_EXPORT_ERROR_RESPONSE_FAILED {}", LogSafe.exceptionTrace(writeFailed));
        }
    }

    private static void clearForError(HttpServletResponse response) {
        response.resetBuffer();
        response.setContentLengthLong(-1);
        // A download that failed before its first byte has already named an attachment. Whether a
        // null value removes a header differs between containers and servlet versions, so the
        // error body explicitly replaces it rather than arriving as a saved file.
        if (response.containsHeader("Content-Disposition")) {
            response.setHeader("Content-Disposition", "inline");
        }
    }

    /** The HTTP status, fixed code and any named missing right a failure is answered with. */
    private record Refusal(int status, String code, String missingRight) {}

    private static final java.util.regex.Pattern FIXED_CODE =
            java.util.regex.Pattern.compile("CDS_EXPORT_[A-Z0-9_]{1,85}");

    private static Refusal refusal(Exception failure) {
        int status = 503;
        String code = "CDS_EXPORT_REQUEST_UNAVAILABLE";
        String missingRight = null;
        if (failure instanceof SecurityException) {
            status = 403;
            code = "CDS_EXPORT_ACCESS_DENIED";
            missingRight =
                    io.github.carlos_emr.carlos.demographic.cdsexport.service
                            .CdsExportAccessService.missingObject(failure);
        } else if (failure instanceof CdsExportStaleControlException) {
            status = 409;
            code = "CDS_EXPORT_STATUS_CHANGED";
        } else if (failure instanceof CdsExportSubmissionConflictException) {
            status = 409;
            code = "CDS_EXPORT_SUBMISSION_CONFLICT";
        } else if (failure
                instanceof
                io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportReviewException
                        review) {
            status = 409;
            code = review.reason().code();
        } else if (failure
                instanceof
                io.github.carlos_emr.carlos.demographic.cdsexport.support
                        .CdsExportArtifactUnavailableException) {
            status = 409;
            code = "CDS_EXPORT_ARTIFACT_UNAVAILABLE";
        } else if (failure instanceof IllegalStateException) {
            String known = failure.getMessage();
            if ("CDS_EXPORT_RUN_NOT_FOUND".equals(known)) {
                status = 404;
                code = known;
            } else if (Set.of(
                            "CDS_EXPORT_ARTIFACT_BUSY",
                            "CDS_EXPORT_CANCEL_INVALID",
                            "CDS_EXPORT_WORKER_BUSY",
                            "CDS_EXPORT_RESUME_INVALID",
                            "CDS_EXPORT_RUNTIME_CHANGED",
                            // Definitive refusals, not an unconfirmed outcome (review W5).
                            "CDS_EXPORT_ADMISSION_DISABLED",
                            "CDS_EXPORT_PROFILE_NOT_QUALIFIED")
                    .contains(known == null ? "" : known)) {
                status = 409;
                code = known;
            }
        } else if (failure instanceof IllegalArgumentException) {
            status = 400;
            code = "CDS_EXPORT_PARAMETERS_INVALID";
            String reason = failure.getMessage();
            if ("CDS_EXPORT_CLIENT_REFRESH_REQUIRED".equals(reason)) {
                status = 409;
                code = reason;
            } else if (reason != null && FIXED_CODE.matcher(reason).matches()) {
                // A fixed refusal code says what to correct (for example the selection); a free
                // text message, such as a number parser's, never reaches the page.
                code = reason;
            }
        }
        return new Refusal(status, code, missingRight);
    }

    public Map<String, Object> job(CdsExportJobDto row) {
        var value = new LinkedHashMap<String, Object>();
        value.put("id", row.id());
        value.put("state", row.state().name());
        value.put("revision", row.revision());
        value.put("totalPatients", row.totalPatients());
        value.put("processedPatients", row.processedPatients());
        value.put("blockedPatients", row.blockedPatients());
        value.put("cancelRequested", row.cancelRequested());
        value.put("workerOwned", row.workerOwned());
        value.put("epoch", row.epoch());
        value.put("safeCode", row.safeCode());
        value.put("createdAt", row.createdAt().toString());
        value.put("updatedAt", row.updatedAt().toString());
        value.put("artifactAvailable", row.artifactAvailable());
        value.put("waiting", row.waiting());
        value.put("complete", row.complete());
        value.put("heldBack", row.heldBack());
        return value;
    }
}
