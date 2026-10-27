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
package io.github.carlos_emr.carlos.demographic.cdsimport.web;

import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsImportService;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsUploadService;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsUploadException;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsWeb;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsImportAccessService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/** Bounded owner-authorized reads; DTO projections deliberately exclude physical report/detail locations. */
@Component("cdsImportStatus2Action") @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class CdsImportStatus2Action extends ActionSupport {
    private final transient CdsImportAccessService access;
    private final transient CdsImportService imports;
    private final transient CdsUploadService uploads;
    private String section = "job", runId, uploadId, afterKey = "";
    private long afterId;
    private int limit = 50;
    public CdsImportStatus2Action(CdsImportAccessService access, CdsImportService imports, CdsUploadService uploads) {
        this.access = access; this.imports = imports; this.uploads = uploads;
    }
    @Override public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        if (!("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod()))) {
            response.setHeader("Allow", "GET, HEAD"); response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED); return NONE;
        }
        try {
            var actor = CdsWeb.authorize(request, access);
            CdsWeb.parameters(request, Set.of("section", "runId", "uploadId", "afterKey", "afterId", "limit"));
            if (limit < 1 || limit > 100 || afterId < 0) throw new CdsUploadException("IMPORT_PAGE_INVALID", 400);
            Map<String,Object> result = new LinkedHashMap<>(); result.put("ok", true);
            switch (section) {
                case "job" -> { result.put("job", CdsWeb.job(imports.status(actor, runId))); result.put("summary", CdsWeb.summary(imports.summary(actor, runId))); }
                case "patients" -> {
                    var rows = imports.patients(actor, runId, afterKey, limit);
                    result.put("rows", rows.stream().map(CdsWeb::patient).toList());
                    result.put("nextCursor", rows.isEmpty() ? null : rows.get(rows.size() - 1).patientKey());
                    result.put("pageLimit", limit);
                }
                case "diagnostics" -> {
                    var rows = imports.diagnostics(actor, runId, afterId, limit);
                    result.put("rows", rows.stream().map(CdsWeb::diagnostic).toList());
                    result.put("nextCursor", rows.isEmpty() ? null : rows.get(rows.size() - 1).id());
                    result.put("pageLimit", limit);
                }
                case "uploads" -> result.put("rows", uploads.pending(CdsWeb.operator(actor), afterKey, limit));
                case "upload" -> result.put("upload", uploads.status(CdsWeb.operator(actor), uploadId));
                case "chunks" -> result.put("rows", uploads.chunks(CdsWeb.operator(actor), uploadId, afterId, limit));
                default -> throw new CdsUploadException("IMPORT_SECTION_INVALID", 400);
            }
            CdsWeb.json(response, 200, result);
        } catch (Exception failure) { CdsWeb.failure(response, failure); }
        return NONE;
    }
    @StrutsParameter public void setSection(String value) { section = value; }
    @StrutsParameter public void setRunId(String value) { runId = value; }
    @StrutsParameter public void setUploadId(String value) { uploadId = value; }
    @StrutsParameter public void setAfterKey(String value) { afterKey = value; }
    @StrutsParameter public void setAfterId(long value) { afterId = value; }
    @StrutsParameter public void setLimit(int value) { limit = value; }
}
