/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.billings.ca.on.service.OhipReportGenerationService;

/**
 * Mutation gate for {@code billing/CA/ON/genreport.jsp}. The JSP's scriptlet
 * performs billActivityDao.persist() audit writes. Enforces {@code _billing} w privilege AND POST-only
 * before forwarding to the JSP. GET requests return 405.
 * <p>
 * Class name retains the {@code View...} prefix for consistency with sibling
 * gate actions in this migration; behavior is mutation-gate.
 * Pattern matches the POST-only intent already documented by
 * {@code HttpMethodGuardFilter} for similar reconciliation JSPs
 * (ongenreport, gensimulation).
 *
 * @since 2026-04-13
 */
public final class ViewGenReport2Action extends ActionSupport {

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "w", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        OhipReportGenerationService service = SpringUtils.getBean(OhipReportGenerationService.class);
        service.generateReport(request, OhipReportGenerationService.Mode.SOLO_REPORT);

        // Hybrid clinics need a follow-up GROUP_REPORT pass for the group
        // providers; non-hybrid skips straight to the display page.
        return service.isHybridBilling() ? "groupReport" : "ohipReport";
    }
}
