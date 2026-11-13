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

import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportArtifactService;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import java.util.Set;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/** POST-only, CSRF-protected retrieval of a fixed recipient-encrypted artifact. */
@Component("cdsExportDownload2Action")
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class CdsExportDownload2Action extends ActionSupport {
    private final transient SecurityInfoManager securityInfoManager;
    private final transient CdsExportWebService web;
    private final transient CdsExportService exports;
    private final transient CdsExportArtifactService artifacts;
    private String runId, artifact;

    public CdsExportDownload2Action(
            CdsExportWebService web,
            CdsExportService exports,
            CdsExportArtifactService artifacts,
            SecurityInfoManager securityInfoManager) {
        this.securityInfoManager = securityInfoManager;
        this.web = web;
        this.exports = exports;
        this.artifacts = artifacts;
    }

    @Override
    public String execute() throws Exception {
        var request = ServletActionContext.getRequest();
        var response = ServletActionContext.getResponse();
        if (!"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.sendError(jakarta.servlet.http.HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }
        try {
            var viewer = web.authorize(request, securityInfoManager);
            web.parameters(request, Set.of("runId", "artifact"), false);
            var actor = exports.downloadActor(viewer, runId);
            try (var download = artifacts.open(actor, runId, artifact)) {
                response.setStatus(200);
                response.setContentType("application/pkcs7-mime");
                response.setHeader(
                        "Content-Disposition", "attachment; filename=\"cds-export.p7m\"");
                response.setHeader("Cache-Control", "no-store");
                response.setHeader("X-Content-Type-Options", "nosniff");
                response.setHeader(CdsExportWebService.API_HEADER, "1");
                response.setContentLengthLong(download.bytes());
                download.writeTo(response.getOutputStream());
                response.flushBuffer();
                download.complete();
            }
        } catch (Exception failed) {
            web.navigationFailure(request, response, failed);
        }
        return NONE;
    }

    @StrutsParameter
    public void setRunId(String value) {
        runId = value;
    }

    @StrutsParameter
    public void setArtifact(String value) {
        artifact = value;
    }
}
