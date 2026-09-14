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

import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.OnRaSummaryViewModel;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.OnRaSummaryViewModelAssembler;
import io.github.carlos_emr.carlos.billings.ca.on.service.OnRaSummaryTotalsService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Mutation gate for {@code billing/CA/ON/onGenRASummary.jsp}. The legacy JSP
 * performed multiple BillingRaReportService DAO lookups, computed running totals, and
 * called {@code raHeaderDao.merge(...)} to write the recalculated totals back
 * into the RA header content XML. The data assembly + audit merge now live in
 * {@link OnRaSummaryViewModelAssembler}; this action enforces {@code _billing}
 * {@code w} + POST and stashes the {@link OnRaSummaryViewModel} on the
 * request as {@code model} for the JSP to render.
 *
 * @since 2026-04-13
 */
public class ViewOnGenRaSummary2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager;

    private final OnRaSummaryViewModelAssembler onGenRASummaryAssembler;
    private final OnRaSummaryTotalsService totalsService;

    public ViewOnGenRaSummary2Action(SecurityInfoManager securityInfoManager,
                                      OnRaSummaryViewModelAssembler onGenRASummaryAssembler,
                                      OnRaSummaryTotalsService totalsService) {
        this.securityInfoManager = securityInfoManager;
        this.onGenRASummaryAssembler = onGenRASummaryAssembler;
        this.totalsService = totalsService;
    }
    private OnRaSummaryViewModel model;

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
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

        model = onGenRASummaryAssembler.assemble(
                request.getParameter("rano"),
                request.getParameter("proNo"));
        totalsService.mergeTotals(model);
        request.setAttribute("model", model);

        return SUCCESS;
    }

    public OnRaSummaryViewModel getModel() {
        return model;
    }
}
