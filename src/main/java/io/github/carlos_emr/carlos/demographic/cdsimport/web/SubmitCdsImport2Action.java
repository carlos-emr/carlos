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

import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsUploadImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsUploadSubmissionService;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsWeb;
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

/** Converts only an owner-scoped complete upload token into shared import options; no server path parameter exists. */
@Component("submitCdsImport2Action") @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class SubmitCdsImport2Action extends ActionSupport {
    private final transient CdsImportAccessService access;
    private final transient CdsUploadSubmissionService submissions;
    private String uploadId, providerNo, sourceNamespace, sourceProfile;
    private Integer programId, facilityId, courseId;
    private boolean mergeExisting, dryRun, synthetic;
    private boolean matchProviderNames = true;
    private int timeshiftInDays;
    public SubmitCdsImport2Action(CdsImportAccessService access, CdsUploadSubmissionService submissions) {
        this.access = access; this.submissions = submissions;
    }
    @Override public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        if (!"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST"); response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED); return NONE;
        }
        try {
            var actor = CdsWeb.authorize(request, access);
            CdsWeb.parameters(request, Set.of("uploadId", "providerNo", "programId", "facilityId", "sourceNamespace", "sourceProfile",
                    "mergeExisting", "dryRun", "matchProviderNames", "timeshiftInDays", "courseId", "synthetic"));
            var command = new CdsUploadImportCommand(uploadId, providerNo, programId, facilityId,
                    sourceNamespace, sourceProfile, mergeExisting, dryRun, matchProviderNames, timeshiftInDays, courseId, synthetic);
            var job = submissions.submit(actor, command);
            CdsWeb.json(response, 202, Map.of("ok", true, "accepted", true, "job", CdsWeb.job(job)));
        } catch (Exception failure) { CdsWeb.failure(response, failure); }
        return NONE;
    }
    @StrutsParameter public void setUploadId(String value) { uploadId = value; }
    @StrutsParameter public void setProviderNo(String value) { providerNo = value; }
    @StrutsParameter public void setProgramId(Integer value) { programId = value; }
    @StrutsParameter public void setFacilityId(Integer value) { facilityId = value; }
    @StrutsParameter public void setSourceNamespace(String value) { sourceNamespace = value; }
    @StrutsParameter public void setSourceProfile(String value) { sourceProfile = value; }
    @StrutsParameter public void setMergeExisting(boolean value) { mergeExisting = value; }
    @StrutsParameter public void setDryRun(boolean value) { dryRun = value; }
    @StrutsParameter public void setMatchProviderNames(boolean value) { matchProviderNames = value; }
    @StrutsParameter public void setTimeshiftInDays(int value) { timeshiftInDays = value; }
    @StrutsParameter public void setCourseId(Integer value) { courseId = value; }
    @StrutsParameter public void setSynthetic(boolean value) { synthetic = value; }
}
