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
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/** Authorized metadata observation remains available when export admission is disabled. */
@Component("cdsExportStatus2Action")
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class CdsExportStatus2Action extends ActionSupport {
    private final transient SecurityInfoManager securityInfoManager;
    private final transient CdsExportWebService web;
    private final transient CdsExportService exports;
    private String runId, submissionKey, after;
    private int limit = 25;

    public CdsExportStatus2Action(
            CdsExportWebService web,
            CdsExportService exports,
            SecurityInfoManager securityInfoManager) {
        this.web = web;
        this.exports = exports;
        this.securityInfoManager = securityInfoManager;
    }

    @Override
    public String execute() throws Exception {
        var request = ServletActionContext.getRequest();
        var response = ServletActionContext.getResponse();
        if (!"GET".equals(request.getMethod())) {
            response.setHeader("Allow", "GET");
            response.sendError(jakarta.servlet.http.HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }
        try {
            var actor = web.authorize(request, securityInfoManager);
            web.parameters(
                    request,
                    Set.of("runId", "submissionKey", "after", "limit", "autoRefresh"),
                    false);
            String background = request.getParameter("autoRefresh");
            if (background != null && !Set.of("true", "false").contains(background)) {
                throw new IllegalArgumentException("CDS_EXPORT_PARAMETERS_INVALID");
            }
            if (runId != null || submissionKey != null) {
                if (runId != null && submissionKey != null
                        || after != null
                        || request.getParameterMap().containsKey("limit")) {
                    throw new IllegalArgumentException("CDS_EXPORT_PARAMETERS_INVALID");
                }
                var job =
                        runId != null
                                ? exports.observe(actor, runId)
                                : exports.submission(actor, submissionKey);
                web.json(response, 200, Map.of("ok", true, "job", web.job(job)));
            } else {
                if (limit < 1 || limit > 100)
                    throw new IllegalArgumentException("CDS_EXPORT_PARAMETERS_INVALID");
                var jobs = exports.history(actor, after, limit);
                web.json(
                        response,
                        200,
                        Map.of(
                                "ok",
                                true,
                                "jobs",
                                jobs.stream().map(web::job).toList(),
                                "nextAfter",
                                jobs.isEmpty() ? "" : jobs.getLast().id(),
                                "mayHaveMore",
                                jobs.size() == limit));
            }
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
    public void setSubmissionKey(String value) {
        submissionKey = value;
    }

    @StrutsParameter
    public void setAfter(String value) {
        after = value;
    }

    @StrutsParameter
    public void setLimit(int value) {
        limit = value;
    }
}
