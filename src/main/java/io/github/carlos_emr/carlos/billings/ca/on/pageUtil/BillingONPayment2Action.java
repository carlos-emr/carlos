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

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Conditional-POST gate for {@code billing/CA/ON/billingONPayment.jsp}. The
 * JSP renders a payment form that self-posts to the same URL to save a
 * payment record. The original JSP enforced {@code _tasks} r via an
 * {@code <security:oscarSec reverse="true">} redirect; this action mirrors
 * that check and additionally requires POST when the payment-submit button
 * parameter is present (CSRF hardening).
 *
 * @since 2026-04-13
 */
public final class BillingONPayment2Action extends ActionSupport {

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_tasks", "r", null)) {
            throw new SecurityException("missing required sec object (_tasks)");
        }

        // Self-posting form: require POST when submit-action params are present;
        // allow GET for initial form display.
        String submit = request.getParameter("save");
        if (submit == null) submit = request.getParameter("savePayment");
        if (submit != null && !"POST".equalsIgnoreCase(request.getMethod())) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        return SUCCESS;
    }
}
