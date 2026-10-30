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
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsUploadException;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsWeb;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsImportAccessService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.util.Set;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/** Authorized durable run-ID download; no temporary basename or caller-controlled path is accepted. */
@Component("cdsImportReport2Action") @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class CdsImportReport2Action extends ActionSupport {
    private final transient SecurityInfoManager securityInfoManager;
    private final transient CdsImportAccessService access;
    private final transient CdsImportService imports;
    private String runId, type;
    public CdsImportReport2Action(CdsImportAccessService access, CdsImportService imports, SecurityInfoManager securityInfoManager) {
        this.securityInfoManager = securityInfoManager; this.access = access; this.imports = imports; }
    @Override public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        if (!("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod()))) {
            response.setHeader("Allow", "GET, HEAD"); response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED); return NONE;
        }
        LoggedInInfo loggedInInfo = request.getSession(false) == null ? null
                : LoggedInInfo.getLoggedInInfoFromSession(request.getSession(false));
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "w", (String) null)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "w", (String) null)
                && !securityInfoManager.hasPrivilege(loggedInInfo, "_cds_import", "w", (String) null)) {
            throw new SecurityException("missing required sec object (_cds_import)");
        }
        try {
            var actor = CdsWeb.authorize(request, access);
            CdsWeb.readOnlyParameters(request, Set.of("runId", "type"));
            if (runId == null || !runId.matches("[0-9a-f-]{36}") || type == null || !Set.of("event", "error", "receipt", "units").contains(type)) throw new CdsUploadException("IMPORT_REPORT_INVALID", 400);
            var path = imports.report(actor, runId, type);
            response.setContentType("application/octet-stream"); response.setHeader("Cache-Control", "no-store");
            response.setHeader("X-Content-Type-Options", "nosniff"); response.setHeader("Content-Security-Policy", "sandbox");
            response.setHeader("Content-Disposition", "attachment; filename=\"cds-import-" + runId + "-" + type + ".log\"");
            response.setContentLengthLong(Files.size(path));
            if (!"HEAD".equals(request.getMethod())) {
                try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
                    input.transferTo(response.getOutputStream());
                }
            }
        } catch (Exception failure) { CdsWeb.failure(response, failure); }
        return NONE;
    }
    @StrutsParameter public void setRunId(String value) { runId = value; }
    @StrutsParameter public void setType(String value) { type = value; }
}
