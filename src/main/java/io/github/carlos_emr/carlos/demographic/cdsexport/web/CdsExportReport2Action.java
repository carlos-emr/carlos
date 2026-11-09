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
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/** Authorized metadata diagnostics for every saved outcome, including failed and review-required jobs. */
@Component("cdsExportReport2Action")
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class CdsExportReport2Action extends ActionSupport {
    private final transient CdsExportWebService web;
    private final transient CdsExportService exports;
    private String runId;

    public CdsExportReport2Action(CdsExportWebService web, CdsExportService exports) {
        this.web = web;
        this.exports = exports;
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
            var viewer = web.authorize(request);
            web.parameters(request, Set.of("runId"), false);
            var report = exports.report(viewer, runId);
            response.setHeader(
                    "Content-Disposition", "attachment; filename=\"cds-export-diagnostic.json\"");
            web.json(response, 200, Map.of("ok", true, "report", report));
        } catch (Exception failed) {
            web.failure(response, failed);
        }
        return NONE;
    }

    @StrutsParameter
    public void setRunId(String value) {
        runId = value;
    }
}
