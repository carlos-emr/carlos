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

import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportReviewCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsImportService;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsUploadException;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsWeb;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsImportAccessService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.Set;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/** Explicit POST controls and versioned review; a click alone can never clear a clinical blocker. */
@Component("cdsImportControl2Action") @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class CdsImportControl2Action extends ActionSupport {
    private final transient SecurityInfoManager securityInfoManager;
    private final transient CdsImportAccessService access;
    private final transient CdsImportService imports;
    private String operation, runId, kind, patientKey, unitKey, sourceXPath, replacementValue, reason;
    private Long diagnosticId;
    private long expectedVersion = -1;
    public CdsImportControl2Action(CdsImportAccessService access, CdsImportService imports, SecurityInfoManager securityInfoManager) {
        this.securityInfoManager = securityInfoManager; this.access = access; this.imports = imports; }
    @Override public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        if (!"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST"); response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED); return NONE;
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
            CdsWeb.parameters(request, Set.of("operation", "runId", "kind", "diagnosticId", "patientKey", "unitKey", "expectedVersion", "sourceXPath", "replacementValue", "reason"));
            if ("cancel".equals(operation)) imports.cancel(actor, runId);
            else if ("resume".equals(operation)) imports.resume(actor, runId);
            else if ("review".equals(operation)) imports.resolveReview(actor, runId,
                    new CdsImportReviewCommand(kind, diagnosticId, patientKey, unitKey, expectedVersion, sourceXPath, replacementValue, reason));
            else throw new CdsUploadException("IMPORT_CONTROL_INVALID", 400);
            CdsWeb.json(response, 200, Map.of("ok", true, "job", CdsWeb.job(imports.status(actor, runId))));
        } catch (Exception failure) { CdsWeb.failure(response, failure); }
        return NONE;
    }
    @StrutsParameter public void setOperation(String value) { operation = value; }
    @StrutsParameter public void setRunId(String value) { runId = value; }
    @StrutsParameter public void setKind(String value) { kind = value; }
    @StrutsParameter public void setDiagnosticId(Long value) { diagnosticId = value; }
    @StrutsParameter public void setPatientKey(String value) { patientKey = value; }
    @StrutsParameter public void setUnitKey(String value) { unitKey = value; }
    @StrutsParameter public void setExpectedVersion(long value) { expectedVersion = value; }
    @StrutsParameter public void setSourceXPath(String value) { sourceXPath = value; }
    @StrutsParameter public void setReplacementValue(String value) { replacementValue = value; }
    @StrutsParameter public void setReason(String value) { reason = value; }
}
