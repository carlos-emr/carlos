/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.admin.web;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.lab.service.CmlProjectionService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.*;

/** Admin preview/apply UI. Preview never changes clinical rows; apply requires its exact fingerprint.
 * @since 2026-11-09
 */
public final class CmlProjectionRepair2Action extends ActionSupport {
    @Override public String execute() throws java.io.IOException {
        var request = ServletActionContext.getRequest();
        var response = ServletActionContext.getResponse();
        var actor = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!SpringUtils.getBean(SecurityInfoManager.class).hasPrivilege(actor, "_admin", "w", null)) {
            response.sendError(403); return NONE;
        }
        String operation = request.getParameter("operation");
        if (operation == null) return SUCCESS;
        if (!"preview".equals(operation) && !"apply".equals(operation)) { response.sendError(400); return NONE; }
        if (request.getParameterValues("operation").length != 1) { response.sendError(400); return NONE; }
        if ("apply".equals(operation) && !"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST"); response.sendError(405); return NONE;
        }
        String[] values = request.getParameterValues("reportNo");
        int report;
        try {
            if (values == null || values.length != 1 || !values[0].matches("[1-9][0-9]{0,9}")) throw new IllegalArgumentException();
            report = Integer.parseInt(values[0]);
        } catch (IllegalArgumentException invalid) { response.sendError(400); return NONE; }
        var service = SpringUtils.getBean(CmlProjectionService.class);
        try {
            CmlProjectionService.Summary summary;
            if ("apply".equals(operation)) {
                String[] fingerprints = request.getParameterValues("fingerprint");
                if (fingerprints == null || fingerprints.length != 1) { response.sendError(400); return NONE; }
                summary = service.apply(report, fingerprints[0], actor, request.getRemoteAddr());
                request.setAttribute("repairApplied", Boolean.TRUE);
            } else summary = service.preview(report, actor);
            request.setAttribute("projectionPlan", summary); request.setAttribute("reportNo", report);
            return SUCCESS;
        } catch (CmlProjectionService.StalePlan stale) {
            response.setStatus(409); request.setAttribute("repairError", "clinical.cml.stale"); return SUCCESS;
        } catch (IllegalArgumentException invalid) {
            response.sendError(400); return NONE;
        } catch (SecurityException denied) { response.sendError(403); return NONE;
        } catch (RuntimeException failure) {
            MiscUtils.getLogger().error("CML projection repair failed: {}", failure.getClass().getSimpleName());
            response.setStatus(500); request.setAttribute("repairError", "clinical.cml.failed"); return SUCCESS;
        }
    }
}
