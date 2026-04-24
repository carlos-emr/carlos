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
import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import io.github.carlos_emr.carlos.billings.ca.on.data.BillingONFormViewModel;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * View-scope gate for the Ontario billing form ({@code billingON.jsp}).
 *
 * <p>Built during the incremental scriptlet-extraction refactor that targets
 * the 1 MB JSP page-buffer overflow. Enforces {@code _billing r}, accepts GET /
 * HEAD / POST (the form self-posts), and exposes a {@link BillingONFormViewModel}
 * at request attribute {@code model} for the JSP to consume via EL.</p>
 *
 * <p>Data population is progressive. Fields migrated from scriptlets so far:
 * request parameter echoes (demographicNo, appointmentNo, apptProviderNo,
 * providerView, billReferenceDate). Remaining scriptlet blocks in
 * {@code billingON.jsp} will migrate field-by-field in follow-up commits.</p>
 *
 * @since 2026-04-24
 */
public final class BillingONView2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager =
            SpringUtils.getBean(SecurityInfoManager.class);

    private BillingONFormViewModel model;

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null) {
            MiscUtils.getLogger().warn("Denied billingON view: no session");
            throw new SecurityException("missing required sec object (_billing)");
        }
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "r", null)) {
            MiscUtils.getLogger().warn(
                    "Denied billingON view: provider={} lacks _billing r",
                    loggedInInfo.getLoggedInProviderNo());
            throw new SecurityException("missing required sec object (_billing)");
        }

        String method = request.getMethod();
        if (!"GET".equalsIgnoreCase(method)
                && !"HEAD".equalsIgnoreCase(method)
                && !"POST".equalsIgnoreCase(method)) {
            response.setHeader("Allow", "GET, HEAD, POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        this.model = buildModel(request);
        request.setAttribute("model", this.model);
        return SUCCESS;
    }

    /**
     * Builds the view model from the current request. Expand as scriptlet
     * blocks in {@code billingON.jsp} are migrated into this action.
     */
    private BillingONFormViewModel buildModel(HttpServletRequest request) {
        return BillingONFormViewModel.builder()
                .demographicNo(request.getParameter("demographic_no"))
                .appointmentNo(request.getParameter("appointment_no"))
                .apptProviderNo(request.getParameter("apptProvider_no"))
                .providerView(request.getParameter("providerview"))
                .billReferenceDate(request.getParameter("appointment_date"))
                .build();
    }

    public BillingONFormViewModel getModel() {
        return model;
    }
}
