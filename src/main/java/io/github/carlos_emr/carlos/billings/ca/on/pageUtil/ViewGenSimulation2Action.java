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
package io.github.carlos_emr.carlos.billings.ca.on.pageUtil;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Mutation gate for {@code billing/CA/ON/genSimulation.jsp}. The legacy JSP
 * iterated active providers, ran {@link io.github.carlos_emr.carlos.billings.ca.on.OHIP.ExtractBean}
 * with {@code eFlag="0"} (dry run, no persist), and produced an HTML preview
 * before forwarding to {@code ViewBillingOHIPsimulation}. The provider iteration
 * + HTML build now lives in {@link OhipReportGenerationService#generateSimulation};
 * the simulation result is stashed on the request as {@code html} for the
 * downstream display action to read.
 *
 * <p>Enforces {@code _billing} {@code r} privilege (read-only — this is a
 * dry-run preview, no DB writes).
 *
 * @since 2026-04-13
 */
public final class ViewGenSimulation2Action extends ActionSupport {

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "r", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        OhipReportGenerationService.SimulationResult sim =
                new OhipReportGenerationService().generateSimulation(request);
        String htmlOut = sim.errorMsg().isEmpty()
                ? sim.htmlPreview()
                : "<font color='red'>" + sim.errorMsg() + "</font>" + sim.htmlPreview();
        request.setAttribute("html", htmlOut);

        return SUCCESS;
    }
}
