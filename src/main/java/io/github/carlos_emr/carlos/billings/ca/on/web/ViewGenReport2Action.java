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
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.billings.ca.on.service.OhipReportGenerationService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

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
public class ViewGenReport2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager;
    private final OhipReportGenerationService service;

    public ViewGenReport2Action(SecurityInfoManager securityInfoManager,
                                OhipReportGenerationService service) {
        this.securityInfoManager = securityInfoManager;
        this.service = service;
    }
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null) {
            throw new SecurityException("missing session");
        }

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "w", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        java.util.List<OhipReportGenerationService.FailedProvider> skipped =
                service.generateReport(request, OhipReportGenerationService.Mode.SOLO_REPORT);
        // Surface per-provider rollbacks to the success page so the
        // operator can re-run for the named providers; without this the
        // page renders as if every selected provider's report shipped.
        request.setAttribute("skippedProviders", skipped);

        // Hybrid clinics need a follow-up GROUP_REPORT pass for the group
        // providers; non-hybrid skips straight to the display page.
        return service.isHybridBilling() ? "groupReport" : "ohipReport";
    }
}
