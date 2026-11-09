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

import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportService;
import java.util.Map;
import java.util.Set;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/** POST records immutable disclosure intent before the worker can capture clinical data. */
@Component("submitCdsExport2Action")
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class SubmitCdsExport2Action extends ActionSupport {
    private final transient SecurityInfoManager securityInfoManager;
    private final transient CdsExportWebService web;
    private final transient CdsExportWebCommandParser parser;
    private final transient CdsExportService exports;
    private String submissionKey,
            providerNo,
            facilityId,
            profile,
            selection,
            physicianNos,
            patientNos,
            categories,
            recipientId,
            auditPlanId;

    public SubmitCdsExport2Action(
            CdsExportWebService web,
            CdsExportWebCommandParser parser,
            CdsExportService exports,
            SecurityInfoManager securityInfoManager) {
        this.web = web;
        this.parser = parser;
        this.exports = exports;
        this.securityInfoManager = securityInfoManager;
    }

    @Override
    public String execute() throws Exception {
        var request = ServletActionContext.getRequest();
        var response = ServletActionContext.getResponse();
        if (!"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.sendError(405);
            return NONE;
        }
        try {
            var session = request.getSession(false);
            var viewer = session == null ? null : LoggedInInfo.getLoggedInInfoFromSession(session);
            if (!securityInfoManager.hasPrivilege(viewer, "_demographic", "r", (String) null)
                    || !(securityInfoManager.hasPrivilege(viewer, "_admin", "w", (String) null)
                            || securityInfoManager.hasPrivilege(
                                    viewer, "_cds_export", "w", (String) null))) {
                throw new SecurityException("CDS_EXPORT_ACCESS_DENIED");
            }
            var actor = web.authorize(request);
            web.parameters(
                    request,
                    Set.of(
                            "submissionKey",
                            "providerNo",
                            "facilityId",
                            "profile",
                            "selection",
                            "physicianNos",
                            "patientNos",
                            "categories",
                            "recipientId",
                            "auditPlanId"),
                    false);
            var command =
                    parser.parse(
                            submissionKey,
                            providerNo,
                            facilityId,
                            profile,
                            selection,
                            physicianNos,
                            patientNos,
                            categories,
                            recipientId,
                            auditPlanId);
            var accepted = exports.submit(actor, command);
            web.json(
                    response,
                    202,
                    Map.of(
                            "ok",
                            true,
                            "submissionKey",
                            command.submissionKey(),
                            "job",
                            web.job(accepted)));
        } catch (Exception failure) {
            web.failure(response, failure);
        }
        return NONE;
    }

    @StrutsParameter
    public void setSubmissionKey(String value) {
        submissionKey = value;
    }

    @StrutsParameter
    public void setProviderNo(String value) {
        providerNo = value;
    }

    @StrutsParameter
    public void setFacilityId(String value) {
        facilityId = value;
    }

    @StrutsParameter
    public void setProfile(String value) {
        profile = value;
    }

    @StrutsParameter
    public void setSelection(String value) {
        selection = value;
    }

    @StrutsParameter
    public void setPhysicianNos(String value) {
        physicianNos = value;
    }

    @StrutsParameter
    public void setPatientNos(String value) {
        patientNos = value;
    }

    @StrutsParameter
    public void setCategories(String value) {
        categories = value;
    }

    @StrutsParameter
    public void setRecipientId(String value) {
        recipientId = value;
    }

    @StrutsParameter
    public void setAuditPlanId(String value) {
        auditPlanId = value;
    }
}
