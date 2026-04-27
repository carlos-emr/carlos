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
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.billings.ca.on.service.BillingONErrorReportService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * AJAX endpoint for the MOH error-report status toggle on
 * {@code billingONStatus.jsp}. Replaces the former
 * {@code billingONStatusERUpdateStatus.jsp} controller-in-a-JSP.
 *
 * <p>POST-only. Reads {@code id} and {@code val} request parameters,
 * delegates to {@link BillingONErrorReportService#updateErrorReportStatus},
 * and writes {@code checked} or {@code uncheck} as plain text — matching
 * the contract callers already parse against.</p>
 *
 * @since 2026-04-26
 */
public class BillingONStatusERUpdateStatus2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager;
    private final BillingONErrorReportService errorReportService;

    /** Constructor injection used by Spring + Struts2's SpringObjectFactory. */
    @Autowired
    public BillingONStatusERUpdateStatus2Action(SecurityInfoManager securityInfoManager,
                                         BillingONErrorReportService errorReportService) {
        this.securityInfoManager = securityInfoManager;
        this.errorReportService = errorReportService;
    }

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

        errorReportService.updateErrorReportStatus(id, val);

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
