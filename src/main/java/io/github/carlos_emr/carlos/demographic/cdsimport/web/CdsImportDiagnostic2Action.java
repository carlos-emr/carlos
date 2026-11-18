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
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/** Authorized bounded technical evidence by run/diagnostic identity; never a caller-selected file. */
@Component("cdsImportDiagnostic2Action")
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class CdsImportDiagnostic2Action extends ActionSupport {
    private final transient SecurityInfoManager securityInfoManager;
    private final transient CdsWebService web;
    private final transient CdsImportService imports;
    private String runId;
    private long diagnosticId;

    public CdsImportDiagnostic2Action(CdsWebService web, CdsImportService imports, SecurityInfoManager securityInfoManager) {
        this.securityInfoManager = securityInfoManager;
        this.web = web;
        this.imports = imports;
    }

    @Override
    public String execute() throws Exception {
        var request = ServletActionContext.getRequest();
        var response = ServletActionContext.getResponse();
        if (!Set.of("GET", "HEAD").contains(request.getMethod())) {
            response.setHeader("Allow", "GET, HEAD");
            response.sendError(405);
            return NONE;
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
            var actor = web.authorize(request);
            web.parameters(request, Set.of("runId", "diagnosticId"));
            if (runId == null
                    || !runId.matches(
                            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                    || diagnosticId < 1)
                throw new CdsUploadException("IMPORT_DIAGNOSTIC_INVALID", 400);
            var evidence = web.summary(imports.diagnosticEvidence(actor, runId, diagnosticId));
            if ("HEAD".equals(request.getMethod())) {
                response.setStatus(200);
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.setHeader("Cache-Control", "no-store");
                response.setHeader("X-Content-Type-Options", "nosniff");
                response.setHeader(CdsWebService.API_VERSION_HEADER, Integer.toString(CdsWebService.API_VERSION));
            } else web.json(response, 200, Map.of("ok", true, "evidence", evidence));
        } catch (Exception failure) {
            web.failure(response, failure);
        }
        return NONE;
    }

    @StrutsParameter
    public void setRunId(String value) {
        runId = value;
    }

    @StrutsParameter
    public void setDiagnosticId(long value) {
        diagnosticId = value;
    }
}
