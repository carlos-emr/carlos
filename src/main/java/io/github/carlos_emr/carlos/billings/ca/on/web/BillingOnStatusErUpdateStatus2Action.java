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

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.billings.ca.on.service.BillingOnErrorReportService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * AJAX endpoint for the MOH error-report status toggle on
 * {@code billingONStatus.jsp}. Replaces the former
 * {@code billingONStatusERUpdateStatus.jsp} controller-in-a-JSP.
 *
 * <p>POST-only. Reads {@code id} and {@code val} request parameters,
 * delegates to {@link BillingOnErrorReportService#updateErrorReportStatus},
 * and writes {@code checked} or {@code uncheck} as plain text — matching
 * the contract callers already parse against.</p>
 *
 * @since 2026-04-26
 */
public class BillingOnStatusErUpdateStatus2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager;
    private final BillingOnErrorReportService errorReportService;

    public BillingOnStatusErUpdateStatus2Action(SecurityInfoManager securityInfoManager,
                                         BillingOnErrorReportService errorReportService) {
        this.securityInfoManager = securityInfoManager;
        this.errorReportService = errorReportService;
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    @Override
    public String execute() {
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
            response.setHeader("Allow", "POST");
            try {
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            } catch (IOException ignore) {
                // Container is shutting down or response already committed.
            }
            return NONE;
        }

        String id = request.getParameter("id");
        String val = request.getParameter("val");
        if (id == null || id.isEmpty() || val == null || val.isEmpty()) {
            try {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                                   "id and val parameters are required");
            } catch (IOException ignore) {
                // Container is shutting down or response already committed.
            }
            return NONE;
        }

        boolean updated = errorReportService.updateErrorReportStatus(id, val);
        if (!updated) {
            try {
                response.sendError(HttpServletResponse.SC_NOT_FOUND,
                                   "error-report record not found");
            } catch (IOException ignore) {
                // Container is shutting down or response already committed.
            }
            return NONE;
        }

        response.setContentType("text/plain");
        response.setCharacterEncoding("UTF-8");
        try {
            response.getWriter().print("Y".equals(val) ? "checked" : "uncheck");
        } catch (IOException e) {
            MiscUtils.getLogger().warn("Failed to write status response", e);
        }

        return NONE;
    }
}
