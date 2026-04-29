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
package io.github.carlos_emr.carlos.billings.ca.on.web;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SafeEncode;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.billings.ca.on.service.OhipReportGenerationService;

/**
 * Mutation gate for {@code billing/CA/ON/genSimulation.jsp}. The legacy JSP
 * iterated active providers, ran {@link io.github.carlos_emr.carlos.billings.ca.on.service.OhipClaimExtractService}
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
public class ViewGenSimulation2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager;
    private final OhipReportGenerationService ohipReportGenerationService;

    public ViewGenSimulation2Action(SecurityInfoManager securityInfoManager,
                                    OhipReportGenerationService ohipReportGenerationService) {
        this.securityInfoManager = securityInfoManager;
        this.ohipReportGenerationService = ohipReportGenerationService;
    }
    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "r", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        OhipReportGenerationService.SimulationResult sim =
                ohipReportGenerationService.generateSimulation(request);
        request.setAttribute("html", formatSimulationHtml(sim));

        return SUCCESS;
    }

    static String formatSimulationHtml(OhipReportGenerationService.SimulationResult sim) {
        String preview = nullToEmpty(sim.htmlPreview());
        String error = nullToEmpty(sim.errorMsg());
        if (error.isEmpty()) {
            return preview;
        }
        return "<font color='red'>" + encodeErrorHtml(error) + "</font>" + preview;
    }

    private static String encodeErrorHtml(String error) {
        String[] lines = error.replaceAll("(?i)<br\\s*/?>", "\n").split("\n", -1);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            out.append(SafeEncode.forHtml(lines[i]));
            if (i < lines.length - 1) {
                out.append("<br>");
            }
        }
        return out.toString();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
