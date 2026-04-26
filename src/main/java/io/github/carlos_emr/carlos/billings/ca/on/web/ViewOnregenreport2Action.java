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
import io.github.carlos_emr.carlos.billings.ca.on.pageUtil.BillingDiskCreatePrep;
import io.github.carlos_emr.carlos.billings.ca.on.service.OnBillingDiskService;

/**
 * Mutation gate for {@code billing/CA/ON/onregenreport.jsp}. The legacy JSP
 * regenerated an existing MOH disk (keyed by {@code diskId}) by calling
 * {@link BillingDiskCreatePrep} and
 * {@link io.github.carlos_emr.carlos.billings.ca.on.service.OhipClaimFileService}
 * to rewrite the OHIP claim file + HTML preview, then forwarded to
 * {@code ViewBillingONMRI}. The regeneration logic now lives in
 * {@link OnBillingDiskService#regenerateDisk}; this action enforces
 * {@code _billing} {@code w} + POST and chains to the display action.
 *
 * @since 2026-04-13
 */
public final class ViewOnregenreport2Action extends ActionSupport {

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

        new OnBillingDiskService().regenerateDisk(request);
        return SUCCESS;
    }
}
